import { reactive } from 'vue'
import { Capacitor, SystemBars, SystemBarsStyle } from '@capacitor/core'
import { App } from '@capacitor/app'
import { Network } from '@capacitor/network'
import { SplashScreen } from '@capacitor/splash-screen'

const MOBILE_HOME = '/m/products'

export const nativeState = reactive({
  isNative: Capacitor.isNativePlatform(),
  connected: true,
  connectionType: 'unknown',
})

let initialized = false

const updateNetworkState = ({ connected, connectionType }) => {
  nativeState.connected = Boolean(connected)
  nativeState.connectionType = connectionType || 'unknown'
}

const normalizeInternalPath = (candidate) => {
  if (!candidate) return null

  try {
    const url = new URL(candidate)
    const path = `${url.pathname}${url.search}${url.hash}`
    return path.startsWith('/m') ? path : null
  } catch {
    return candidate.startsWith('/m') ? candidate : null
  }
}

export async function initializeNativeRuntime(router) {
  if (!nativeState.isNative || initialized) return
  initialized = true
  document.documentElement.classList.add('is-native-app')

  try {
    if (!router.currentRoute.value.path.startsWith('/m')) {
      await router.replace(MOBILE_HOME)
    }

    await Promise.allSettled([
      SystemBars.setStyle({ style: SystemBarsStyle.Dark }),
    ])

    try {
      updateNetworkState(await Network.getStatus())
      await Network.addListener('networkStatusChange', updateNetworkState)
    } catch (error) {
      console.warn('无法读取原生网络状态', error)
    }

    await Promise.allSettled([
      App.addListener('backButton', async ({ canGoBack }) => {
        const currentPath = router.currentRoute.value.path
        if (canGoBack && currentPath !== MOBILE_HOME) {
          router.back()
          return
        }
        await App.minimizeApp()
      }),
      App.addListener('appUrlOpen', ({ url }) => {
        const path = normalizeInternalPath(url)
        if (path && path !== router.currentRoute.value.fullPath) {
          router.push(path)
        }
      }),
    ])
  } catch (error) {
    console.error('原生运行环境初始化失败', error)
  } finally {
    await SplashScreen.hide().catch(() => undefined)
  }
}
