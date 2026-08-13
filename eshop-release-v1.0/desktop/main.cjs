const { app, BrowserWindow, Menu, session, shell } = require('electron')
const path = require('node:path')
const { fileURLToPath, pathToFileURL } = require('node:url')

const DEFAULT_START_URL = 'https://eshop.zhuyiyuan9.top/pc/products'
const DESKTOP_PARTITION = 'persist:eshop-pc'
const APP_USER_MODEL_ID = 'top.zhuyiyuan9.eshop.pc'
const MIN_SPLASH_DURATION_MS = 2200
const MAX_SPLASH_DURATION_MS = 10000
const startUrl = resolveStartUrl(process.env.ESHOP_DESKTOP_URL)
const serviceOrigin = new URL(startUrl).origin
const offlineFile = path.join(__dirname, 'offline.html')
const offlineUrl = pathToFileURL(offlineFile).toString()
const splashFile = path.join(__dirname, 'splash.html')
const appIconIco = path.join(__dirname, 'assets', 'app-icon.ico')

let mainWindow = null
let splashWindow = null
let splashShownAt = 0
let splashRevealTimer = null
let splashFallbackTimer = null
let mainWindowRevealed = false
let mainWindowRevealScheduled = false
let lastInternalUrl = startUrl

function resolveStartUrl(configuredUrl) {
  const candidate = configuredUrl?.trim() || DEFAULT_START_URL

  try {
    const parsed = new URL(candidate)
    if (!['http:', 'https:'].includes(parsed.protocol)) {
      throw new Error('仅允许 HTTP 或 HTTPS 地址')
    }
    return parsed.toString()
  } catch (error) {
    console.error(`ESHOP_DESKTOP_URL 无效，已使用默认地址：${error.message}`)
    return DEFAULT_START_URL
  }
}

function appPage(pathname) {
  return new URL(pathname, `${serviceOrigin}/`).toString()
}

function isInternalUrl(rawUrl) {
  try {
    const parsed = new URL(rawUrl)
    return parsed.origin === serviceOrigin && ['http:', 'https:'].includes(parsed.protocol)
  } catch {
    return false
  }
}

function isOfflineUrl(rawUrl) {
  try {
    const parsed = new URL(rawUrl)
    if (parsed.protocol !== 'file:') return false

    const requestedPath = path.normalize(fileURLToPath(parsed)).toLowerCase()
    const expectedPath = path.normalize(offlineFile).toLowerCase()
    return requestedPath === expectedPath
  } catch {
    return false
  }
}

function logLoadFailure(context, error) {
  console.error(`[E-Shop PC] ${context}：${error?.message || error}`)
}

function loadUrlSafely(window, url, context = '页面加载失败') {
  if (!window || window.isDestroyed()) return Promise.resolve(false)

  return window
    .loadURL(url)
    .then(() => true)
    .catch((error) => {
      logLoadFailure(context, error)
      return false
    })
}

function loadFileSafely(window, file, options, context = '本地页面加载失败') {
  if (!window || window.isDestroyed()) return Promise.resolve(false)

  return window
    .loadFile(file, options)
    .then(() => true)
    .catch((error) => {
      logLoadFailure(context, error)
      return false
    })
}

async function openExternalUrl(rawUrl) {
  try {
    const parsed = new URL(rawUrl)
    if (['http:', 'https:', 'mailto:', 'tel:'].includes(parsed.protocol)) {
      await shell.openExternal(parsed.toString())
    }
  } catch (error) {
    logLoadFailure('无法打开外部链接', error)
  }
}

function loadPage(pathname) {
  if (!mainWindow || mainWindow.isDestroyed()) return

  const targetUrl = appPage(pathname)
  lastInternalUrl = targetUrl
  void loadUrlSafely(mainWindow, targetUrl)
}

function showOfflinePage(window, errorCode, errorDescription, failedUrl = lastInternalUrl) {
  if (!window || window.isDestroyed()) return

  const retryTarget = isInternalUrl(failedUrl)
    ? failedUrl
    : isInternalUrl(lastInternalUrl)
      ? lastInternalUrl
      : startUrl

  void loadFileSafely(
    window,
    offlineFile,
    {
      query: {
        target: retryTarget,
        code: String(errorCode),
        message: errorDescription || '无法连接到 E-Shop 服务',
      },
    },
    '离线提示页加载失败',
  ).then((loaded) => {
    if (!loaded) revealMainWindow(window)
  })
}

function createApplicationMenu() {
  const template = [
    {
      label: 'E-Shop',
      submenu: [
        {
          label: 'PC 商城首页',
          accelerator: 'CmdOrCtrl+H',
          click: () => loadPage('/pc/products'),
        },
        {
          label: '统一入口',
          click: () => loadPage('/'),
        },
        { type: 'separator' },
        { label: '重新加载', accelerator: 'CmdOrCtrl+R', role: 'reload' },
        { label: '强制刷新', accelerator: 'CmdOrCtrl+Shift+R', role: 'forceReload' },
        { type: 'separator' },
        { label: '退出', role: 'quit' },
      ],
    },
    {
      label: '显示',
      submenu: [
        { label: '恢复缩放', accelerator: 'CmdOrCtrl+0', role: 'resetZoom' },
        { label: '放大', accelerator: 'CmdOrCtrl+Plus', role: 'zoomIn' },
        { label: '缩小', accelerator: 'CmdOrCtrl+-', role: 'zoomOut' },
        { type: 'separator' },
        { label: '全屏', accelerator: 'F11', role: 'togglefullscreen' },
      ],
    },
    {
      label: '帮助',
      submenu: [
        {
          label: '在浏览器中打开商城',
          click: () => void openExternalUrl(startUrl),
        },
      ],
    },
  ]

  Menu.setApplicationMenu(Menu.buildFromTemplate(template))
}

function createSplashWindow() {
  const window = new BrowserWindow({
    title: 'E-Shop PC 正在启动',
    width: 720,
    height: 440,
    frame: false,
    transparent: true,
    resizable: false,
    movable: true,
    alwaysOnTop: true,
    skipTaskbar: true,
    show: false,
    center: true,
    icon: appIconIco,
    webPreferences: {
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: true,
      webSecurity: true,
      devTools: false,
    },
  })

  splashShownAt = Date.now()
  window.once('ready-to-show', () => {
    if (!window.isDestroyed()) window.show()
  })
  window.on('closed', () => {
    if (splashWindow === window) splashWindow = null
  })
  void loadFileSafely(window, splashFile, undefined, '启动页加载失败').then((loaded) => {
    if (!loaded && !window.isDestroyed()) window.close()
  })
  return window
}

function clearStartupTimers() {
  clearTimeout(splashRevealTimer)
  clearTimeout(splashFallbackTimer)
  splashRevealTimer = null
  splashFallbackTimer = null
  mainWindowRevealScheduled = false
}

function closeSplashWindow() {
  if (splashWindow && !splashWindow.isDestroyed()) splashWindow.close()
  splashWindow = null
}

function revealMainWindow(window) {
  if (
    !window ||
    window.isDestroyed() ||
    mainWindowRevealed ||
    mainWindowRevealScheduled
  ) {
    return
  }

  mainWindowRevealScheduled = true
  clearTimeout(splashFallbackTimer)
  splashFallbackTimer = null
  const elapsed = Date.now() - splashShownAt
  const remaining = Math.max(0, MIN_SPLASH_DURATION_MS - elapsed)

  splashRevealTimer = setTimeout(() => {
    splashRevealTimer = null
    mainWindowRevealScheduled = false
    closeSplashWindow()

    if (!window.isDestroyed()) {
      mainWindowRevealed = true
      window.show()
      window.focus()
    }
  }, remaining)
}

function handleStartupTimeout(window) {
  if (!window || window.isDestroyed() || mainWindowRevealed) return

  window.webContents.stop()
  showOfflinePage(
    window,
    'STARTUP_TIMEOUT',
    '连接商城超时，请检查网络后重试',
    lastInternalUrl,
  )
}

function guardMainFrameNavigation(window, event, url, isRedirect = false) {
  if (isInternalUrl(url)) {
    lastInternalUrl = url
    return
  }
  if (isOfflineUrl(url)) return

  event.preventDefault()
  if (isRedirect) {
    showOfflinePage(
      window,
      'BLOCKED_REDIRECT',
      '已阻止跳转到非 E-Shop 网站',
      lastInternalUrl,
    )
    return
  }
  void openExternalUrl(url)
}

function createWindow() {
  const window = new BrowserWindow({
    title: 'E-Shop PC',
    width: 1440,
    height: 900,
    minWidth: 1024,
    minHeight: 720,
    show: false,
    backgroundColor: '#07111f',
    autoHideMenuBar: true,
    icon: appIconIco,
    webPreferences: {
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: true,
      webSecurity: true,
      allowRunningInsecureContent: false,
      webviewTag: false,
      spellcheck: false,
      safeDialogs: true,
      partition: DESKTOP_PARTITION,
      devTools: !app.isPackaged || process.env.ESHOP_DESKTOP_DEVTOOLS === '1',
    },
  })

  window.webContents.on('did-finish-load', () => revealMainWindow(window))
  window.webContents.on('did-navigate', (_event, url) => {
    if (isInternalUrl(url)) lastInternalUrl = url
  })
  window.webContents.on('did-navigate-in-page', (_event, url) => {
    if (isInternalUrl(url)) lastInternalUrl = url
  })

  window.webContents.setWindowOpenHandler(({ url }) => {
    if (isInternalUrl(url)) {
      lastInternalUrl = url
      void loadUrlSafely(window, url)
    } else {
      void openExternalUrl(url)
    }
    return { action: 'deny' }
  })

  window.webContents.on('will-navigate', (event, url) => {
    guardMainFrameNavigation(window, event, url)
  })
  window.webContents.on(
    'will-redirect',
    (event, url, _isInPlace, isMainFrame) => {
      if (isMainFrame) guardMainFrameNavigation(window, event, url, true)
    },
  )

  window.webContents.on(
    'did-fail-load',
    (_event, errorCode, errorDescription, validatedUrl, isMainFrame) => {
      if (!isMainFrame || errorCode === -3 || isOfflineUrl(validatedUrl)) return
      showOfflinePage(window, errorCode, errorDescription, validatedUrl)
    },
  )

  window.on('closed', () => {
    if (mainWindow === window) {
      mainWindow = null
      clearStartupTimers()
      closeSplashWindow()
    }
  })

  lastInternalUrl = startUrl
  void loadUrlSafely(window, startUrl, '商城首页加载失败')
  return window
}

function launchApplicationWindows() {
  clearStartupTimers()
  closeSplashWindow()
  mainWindowRevealed = false
  mainWindowRevealScheduled = false
  splashWindow = createSplashWindow()
  mainWindow = createWindow()
  splashFallbackTimer = setTimeout(
    () => handleStartupTimeout(mainWindow),
    MAX_SPLASH_DURATION_MS,
  )
}

const hasSingleInstanceLock = app.requestSingleInstanceLock()

if (!hasSingleInstanceLock) {
  app.quit()
} else {
  app.on('second-instance', () => {
    if (splashWindow && !splashWindow.isDestroyed() && !mainWindowRevealed) {
      splashWindow.show()
      splashWindow.focus()
      return
    }
    if (!mainWindow) return
    if (mainWindow.isMinimized()) mainWindow.restore()
    mainWindow.show()
    mainWindow.focus()
  })

  app.whenReady().then(() => {
    app.setName('E-Shop PC')
    app.setAppUserModelId(APP_USER_MODEL_ID)
    const desktopSession = session.fromPartition(DESKTOP_PARTITION)
    desktopSession.setPermissionCheckHandler(() => false)
    desktopSession.setPermissionRequestHandler((_webContents, _permission, callback) => {
      callback(false)
    })

    createApplicationMenu()
    launchApplicationWindows()

    app.on('activate', () => {
      if (BrowserWindow.getAllWindows().length === 0) launchApplicationWindows()
    })
  })
}

app.on('before-quit', () => clearStartupTimers())
app.on('window-all-closed', () => {
  if (process.platform !== 'darwin') app.quit()
})
