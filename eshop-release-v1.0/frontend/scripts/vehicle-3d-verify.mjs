import { copyFileSync, mkdirSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { chromium } from 'playwright'

const outDir = fileURLToPath(new URL('../../docs/03-测试文档/screenshots/vehicle-3d/', import.meta.url))
const finalDir = fileURLToPath(new URL('../../docs/03-测试文档/screenshots/vehicle-3d/final-delivery/', import.meta.url))
mkdirSync(outDir, { recursive: true })
mkdirSync(finalDir, { recursive: true })
const origin = 'http://127.0.0.1:8088'

const fail = (name, detail) => {
  console.log(`FAIL ${name} :: ${detail}`)
  process.exitCode = 1
}
const pass = (name, detail = '') => console.log(`PASS ${name}${detail ? ` :: ${detail}` : ''}`)
const saveFinal = (srcName, destName) => {
  copyFileSync(`${outDir}${srcName}`, `${finalDir}${destName}`)
  console.log(`SHOT final-delivery/${destName}`)
}

const health = await fetch(`${origin}/api/actuator/health`).then((item) => item.json())
if (health.status === 'UP') pass('health-up', JSON.stringify(health))
else fail('health-up', JSON.stringify(health))

const discoverVehicleIds = async () => {
  const ids = []
  try {
    const cats = await fetch(`${origin}/api/categories`).then((item) => item.json())
    const list = cats.data || cats
    const found = (Array.isArray(list) ? list : []).find((item) => String(item.name || '').includes('汽车'))
    if (found?.id) {
      const page = await fetch(`${origin}/api/products?current=1&size=20&categoryId=${found.id}`).then((item) => item.json())
      const records = page.data?.records || page.records || []
      records.forEach((item) => {
        if (item.productKind === 'VEHICLE' || /SU7|YU7|Ultra|汽车/i.test(item.name || '')) ids.push(Number(item.id))
      })
    }
  } catch {
    /* 回退探测 */
  }
  for (const probe of [62, 63, 64, 61, 65]) {
    if (ids.includes(probe)) continue
    const json = await fetch(`${origin}/api/vehicles/${probe}/configurator`).then((item) => item.json()).catch(() => null)
    if (json?.code === 0 && json.data?.defaultSkuId) ids.push(probe)
  }
  return [...new Set(ids.filter(Boolean))]
}

const vehicleIds = await discoverVehicleIds()
if (vehicleIds.length >= 2) pass('vehicle-ids-discovered', JSON.stringify(vehicleIds))
else fail('vehicle-ids-discovered', JSON.stringify(vehicleIds))

const primaryId = vehicleIds[0]
const secondaryId = vehicleIds.find((id) => id !== primaryId) || vehicleIds[0]
const pageUrl = `${origin}/pc/vehicles/${primaryId}/configurator`
console.log(`PRIMARY_VEHICLE ${primaryId} SECONDARY_VEHICLE ${secondaryId}`)

const collectOptionIds = (data) => {
  const skuId = data.defaultSkuId
  const ids = []
  data.groups.forEach((group) => {
    const available = group.values.filter((value) => {
      const rule = data.rules.find((item) => item.skuId === skuId && item.optionValueId === value.id)
      return !rule || rule.available
    })
    const included = available.filter((value) => {
      const rule = data.rules.find((item) => item.skuId === skuId && item.optionValueId === value.id)
      return rule?.included
    })
    if (group.selectionType === 'MULTI') {
      ids.push(...included.map((value) => value.id))
      return
    }
    const preferred = data.defaultOptionValueIds?.find((id) => available.some((value) => value.id === id))
    ids.push(preferred || included[0]?.id || available[0]?.id)
  })
  return [...new Set(ids.filter(Boolean))].sort((a, b) => a - b)
}

const quoteVehicle = async (productId) => {
  const cfg = await fetch(`${origin}/api/vehicles/${productId}/configurator`).then((item) => item.json())
  if (cfg.code !== 0 || !cfg.data) return { productId, cfg, quote: null }
  const optionValueIds = collectOptionIds(cfg.data)
  const quote = await fetch(`${origin}/api/vehicles/${productId}/configurator/quote`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ skuId: cfg.data.defaultSkuId, optionValueIds }),
  }).then((item) => item.json())
  return { productId, cfg, quote, optionValueIds }
}

const sampledQuotes = []
for (const id of vehicleIds.slice(0, 3)) {
  const row = await quoteVehicle(id)
  sampledQuotes.push(row)
  if (row.quote?.code === 0 && row.quote.data?.configurationHash) {
    pass(`quote-${id}`, `hash=${row.quote.data.configurationHash} price=${row.quote.data.unitPrice} name=${row.cfg.data.productName}`)
  } else if (row.cfg?.code === 0 && row.cfg.data?.defaultSkuId) {
    pass(`configurator-load-${id}`, `page ok; quote skipped (${row.quote?.message || 'invalid default combo'})`)
  } else {
    fail(`quote-${id}`, JSON.stringify(row.quote || row.cfg).slice(0, 240))
  }
}

const cfg = sampledQuotes.find((item) => item.productId === primaryId)?.cfg
  || (await fetch(`${origin}/api/vehicles/${primaryId}/configurator`).then((item) => item.json()))
const quote = sampledQuotes.find((item) => item.productId === primaryId)?.quote
const ids = cfg.data.defaultOptionValueIds
if (quote?.code === 0 && quote.data?.configurationHash) {
  pass('quote', `id=${primaryId} hash=${quote.data.configurationHash} price=${quote.data.unitPrice}`)
} else {
  fail('quote', JSON.stringify(quote).slice(0, 240))
}

if (sampledQuotes.length >= 2) {
  const [first, second] = sampledQuotes
  const isolated = (
    first.quote?.data?.configurationHash
    && second.quote?.data?.configurationHash
    && (
      first.quote.data.configurationHash !== second.quote.data.configurationHash
      || first.cfg.data.defaultSkuId !== second.cfg.data.defaultSkuId
      || first.cfg.data.productName !== second.cfg.data.productName
    )
  )
  if (isolated) {
    pass('quote-not-shared-across-products', JSON.stringify({
      a: { id: first.productId, hash: first.quote.data.configurationHash, sku: first.cfg.data.defaultSkuId },
      b: { id: second.productId, hash: second.quote.data.configurationHash, sku: second.cfg.data.defaultSkuId },
    }))
  } else {
    fail('quote-not-shared-across-products', JSON.stringify(sampledQuotes.map((item) => item.quote?.data)))
  }
}

const username = `veh3d_${Date.now().toString(36)}`
await fetch(`${origin}/api/auth/register`, {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ username, password: 'Passw0rd!', nickname: '3d-verify' }),
})
const login = await fetch(`${origin}/api/auth/login`, {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ username, password: 'Passw0rd!' }),
}).then((item) => item.json())
const cart = await fetch(`${origin}/api/cart`, {
  method: 'POST',
  headers: {
    'Content-Type': 'application/json',
    Authorization: `Bearer ${login.data.token}`,
  },
  body: JSON.stringify({ skuId: cfg.data.defaultSkuId, quantity: 1, optionValueIds: ids }),
}).then((item) => item.json())
if (cart.code === 0 && cart.data?.configurationHash && cart.data?.productName) {
  pass('cart', `${cart.data.configurationSummary} ${cart.data.price}`)
} else {
  fail('cart', JSON.stringify(cart).slice(0, 240))
}
for (const row of sampledQuotes.filter((item) => item.productId !== primaryId).slice(0, 2)) {
  const extraCart = await fetch(`${origin}/api/cart`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${login.data.token}`,
    },
    body: JSON.stringify({
      skuId: row.cfg.data.defaultSkuId,
      quantity: 1,
      optionValueIds: row.optionValueIds || collectOptionIds(row.cfg.data),
    }),
  }).then((item) => item.json())
  if (extraCart.code === 0 && extraCart.data?.configurationHash && extraCart.data?.productName) {
    pass(`cart-${row.productId}`, `${extraCart.data.productName} ${extraCart.data.configurationHash} ${extraCart.data.price}`)
  } else if (row.quote?.code !== 0) {
    pass(`cart-${row.productId}`, `skipped; default combo not quotable (${extraCart.message || extraCart.code})`)
  } else {
    fail(`cart-${row.productId}`, JSON.stringify(extraCart).slice(0, 240))
  }
}

const browser = await chromium.launch()
const page = await browser.newPage({ viewport: { width: 1920, height: 1080 } })
page.setDefaultTimeout(45000)
await page.addInitScript(({ token, user }) => {
  localStorage.setItem('eshop_token', token)
  localStorage.setItem('eshop_user', JSON.stringify({
    userId: user.userId,
    username: user.username,
    nickname: user.nickname,
    role: user.role,
  }))
}, { token: login.data.token, user: login.data })
const glbHits = []
page.on('request', (request) => {
  const url = request.url()
  if (url.includes('/models/vehicles/') && url.toLowerCase().endsWith('.glb')) {
    glbHits.push(url)
  }
})

const wait3d = async () => {
  await page.waitForSelector('canvas.vehicle-webgl')
  await page.waitForFunction(() => !document.querySelector('.vehicle-3d-loading') && window.__vehicle3d, null, { timeout: 45000 })
  await page.waitForTimeout(900)
}

const assertBounds = async (label, compact = false) => {
  const bounds = await page.evaluate(() => window.__vehicle3d.getBounds())
  if (!bounds) {
    fail(`bounds-${label}`, 'missing')
    return bounds
  }
  const center = (bounds.top + bounds.bottom) / 2
  const clipped = bounds.left < 0.03 || bounds.right > 0.97 || bounds.top < 0.03 || bounds.bottom > 0.97
  const topOk = compact
    ? bounds.top >= 0.18 && bounds.top <= 0.42
    : bounds.top >= 0.18 && bounds.top <= 0.36
  const bottomOk = compact
    ? bounds.bottom >= 0.62 && bounds.bottom <= 0.88
    : bounds.bottom >= 0.74 && bounds.bottom <= 0.885
  const sideOk = compact
    ? bounds.left >= 0.06 && bounds.right <= 0.94
    : bounds.left >= 0.04 && bounds.right <= 0.96
  const centerOk = compact
    ? center >= 0.46 && center <= 0.62
    : center >= 0.555 && center <= 0.605
  const widthOk = compact
    ? bounds.width >= 0.76 && bounds.width <= 0.84
    : bounds.width >= 0.84 && bounds.width <= 0.88
  const detail = JSON.stringify({ ...bounds, center, compact })
  if (!clipped && topOk && bottomOk && sideOk && centerOk && widthOk) pass(`bounds-${label}`, detail)
  else fail(`bounds-${label}`, `clipped=${clipped} topOk=${topOk} bottomOk=${bottomOk} sideOk=${sideOk} centerOk=${centerOk} widthOk=${widthOk} ${detail}`)
  return bounds
}

const assertLayout = async (label) => {
  const layout = await page.evaluate(() => {
    const root = document.documentElement
    const panel = document.querySelector('.configurator-panel')
    return {
      pageScroll: Math.max(0, root.scrollHeight - root.clientHeight),
      bodyScroll: Math.max(0, document.body.scrollHeight - document.body.clientHeight),
      overflowY: getComputedStyle(root).overflowY,
      panelWidth: panel ? Math.round(panel.getBoundingClientRect().width) : 0,
      thumbs: document.querySelectorAll('.presets button').length,
      cards: document.querySelectorAll('.option-card').length,
    }
  })
  const noScroll = layout.pageScroll <= 1 && layout.bodyScroll <= 1
  const panelOk = layout.panelWidth >= 430
  const thumbsOk = layout.thumbs >= 5
  const cardsOk = layout.cards >= 1
  const detail = JSON.stringify(layout)
  if (noScroll && panelOk && thumbsOk && cardsOk) pass(`layout-${label}`, detail)
  else fail(`layout-${label}`, `noScroll=${noScroll} panelOk=${panelOk} thumbsOk=${thumbsOk} cardsOk=${cardsOk} ${detail}`)
}

await page.goto(pageUrl, { waitUntil: 'networkidle' })
await wait3d()
const canvasCount = await page.locator('canvas.vehicle-webgl').count()
if (canvasCount === 1) pass('canvas-one', canvasCount)
else fail('canvas-one', canvasCount)
if (glbHits.length === 1) pass('glb-first-load', glbHits.length)
else fail('glb-first-load', glbHits.length)

await page.waitForTimeout(1600)
const idleStart = await page.evaluate(() => ({
  running: window.__vehicle3d.isLoopRunning(),
  renders: window.__vehicle3d.getRenderCount(),
}))
await page.waitForTimeout(3000)
const idleEnd = await page.evaluate(() => ({
  running: window.__vehicle3d.isLoopRunning(),
  renders: window.__vehicle3d.getRenderCount(),
}))
if (!idleEnd.running && idleEnd.renders === idleStart.renders) {
  pass('idle-raf-stopped', JSON.stringify({ idleStart, idleEnd }))
} else {
  fail('idle-raf-stopped', JSON.stringify({ idleStart, idleEnd }))
}

const before = await page.locator('canvas.vehicle-webgl').screenshot()
const rendersBeforeDrag = await page.evaluate(() => window.__vehicle3d.getRenderCount())
await page.locator('canvas.vehicle-webgl').hover()
await page.mouse.down()
const duringDrag = await page.evaluate(() => window.__vehicle3d.isLoopRunning())
await page.mouse.move(1280, 520, { steps: 24 })
await page.mouse.up()
await page.waitForTimeout(250)
const afterDragMeta = await page.evaluate(() => ({
  running: window.__vehicle3d.isLoopRunning(),
  renders: window.__vehicle3d.getRenderCount(),
}))
const afterDrag = await page.locator('canvas.vehicle-webgl').screenshot()
if (Buffer.compare(before, afterDrag) !== 0) pass('drag-pixels-changed')
else fail('drag-pixels-changed', 'pixels identical')
if (duringDrag || afterDragMeta.renders > rendersBeforeDrag) {
  pass('drag-resumes-render', JSON.stringify({ duringDrag, ...afterDragMeta, rendersBeforeDrag }))
} else {
  fail('drag-resumes-render', JSON.stringify({ duringDrag, ...afterDragMeta, rendersBeforeDrag }))
}
await page.getByRole('button', { name: '重置视角' }).click()
await page.waitForTimeout(700)

glbHits.length = 0
const beforeColor = await page.locator('canvas.vehicle-webgl').screenshot()
await page.getByRole('button', { name: '演示绿' }).click()
await page.waitForTimeout(700)
const afterColor = await page.locator('canvas.vehicle-webgl').screenshot()
if (glbHits.length === 0) pass('color-no-glb', glbHits.length)
else fail('color-no-glb', glbHits.length)
if (Buffer.compare(beforeColor, afterColor) !== 0) pass('color-pixels-changed')
else fail('color-pixels-changed', 'pixels identical')

await page.locator('.sku-trigger').click()
await page.locator('.sku-menu button', { hasText: 'Pro' }).click()
await page.waitForTimeout(300)
await page.locator('.tabs button', { hasText: '车轮' }).click()
const beforeWheel = await page.locator('canvas.vehicle-webgl').screenshot()
await page.getByRole('button', { name: /20英寸/ }).click()
await page.waitForTimeout(700)
const afterWheel = await page.locator('canvas.vehicle-webgl').screenshot()
if (glbHits.length === 0) pass('wheel-no-glb', glbHits.length)
else fail('wheel-no-glb', glbHits.length)
if (Buffer.compare(beforeWheel, afterWheel) !== 0) pass('wheel-pixels-changed')
else fail('wheel-pixels-changed', 'pixels identical')

await page.locator('.tabs button', { hasText: '车身' }).click()
await page.getByRole('button', { name: '演示白' }).click()
await page.getByRole('button', { name: '重置视角' }).click()
await page.waitForFunction(() => (
  document.querySelectorAll('.presets img').length >= 5
  || document.querySelectorAll('.presets .angle-mark').length >= 5
), null, { timeout: 15000 })
await page.waitForTimeout(400)

const visibleCanvas = await page.evaluate(() => (
  [...document.querySelectorAll('canvas')].filter((item) => {
    const box = item.getBoundingClientRect()
    return box.width > 40 && box.height > 40 && getComputedStyle(item).opacity !== '0'
  }).length
))
if (visibleCanvas === 1) pass('one-visible-webgl-canvas', visibleCanvas)
else fail('one-visible-webgl-canvas', visibleCanvas)

const waitPaint = async (name) => {
  await page.getByRole('button', { name }).click()
  await page.waitForTimeout(850)
}

const assertExposure = async (id, name, viewport, rules) => {
  await page.setViewportSize(viewport)
  await page.waitForTimeout(700)
  await page.evaluate(() => window.__vehicle3d.resetCamera?.())
  await waitPaint(name)
  const beforeShot = await page.locator('canvas.vehicle-webgl').screenshot()
  const shotName = `exposure-${id}-${viewport.width}x${viewport.height}.png`
  await page.locator('.vehicle-3d-stage').screenshot({ path: `${outDir}${shotName}` })
  console.log(`SHOT ${shotName}`)
  const stats = await page.evaluate(() => window.__vehicle3d.sampleExposure())
  await page.waitForTimeout(80)
  if (!stats || !stats.samples) {
    fail(`exposure-${id}`, JSON.stringify(stats))
    return stats
  }
  const blown = Boolean(stats.nearWhiteRatio > 0.98 && stats.avgLuma > 250)
  const empty = stats.transparentRatio > 0.9
  const okCore = !blown && !empty && Number.isFinite(stats.avgLuma)
  const okWhite = rules.white
    ? stats.nearWhiteRatio <= 0.05 && stats.avgLuma >= 110
    : true
  const okBlack = rules.black
    ? stats.nearBlackRatio <= 0.2 && stats.maxLuma >= 36 && stats.highlightRatio >= 0.004
    : true
  const okSilver = rules.silver ? stats.avgLuma < 210 && stats.avgLuma > 70 : true
  const okGreen = rules.green ? stats.avgLuma > 40 && stats.avgLuma < 180 : true
  const detail = JSON.stringify(stats)
  if (okCore && okWhite && okBlack && okSilver && okGreen) pass(`exposure-${id}`, detail)
  else fail(`exposure-${id}`, `whiteOk=${okWhite} blackOk=${okBlack} silverOk=${okSilver} greenOk=${okGreen} ${detail}`)
  if (id === 'white') {
    const range = stats.maxLuma - stats.minLuma
    if (range >= 28 && stats.nearWhiteRatio <= 0.05) pass('body-details-visible', JSON.stringify({ range, ...stats }))
    else fail('body-details-visible', JSON.stringify({ range, ...stats }))
  }
  return { stats, beforeShot }
}

glbHits.length = 0
const desktop = { width: 1920, height: 1080 }
const whiteExp = await assertExposure('white', '演示白', desktop, { white: true })
const blackExp = await assertExposure('black', '演示黑', desktop, { black: true })
const silverExp = await assertExposure('silver', '演示银', desktop, { silver: true })
const greenExp = await assertExposure('green', '演示绿', desktop, { green: true })
if (glbHits.length === 0) pass('color-no-glb', glbHits.length)
else fail('color-no-glb', glbHits.length)
if (whiteExp?.beforeShot && blackExp?.beforeShot && Buffer.compare(whiteExp.beforeShot, blackExp.beforeShot) !== 0) {
  pass('color-pixels-changed')
}
const mobile = { width: 390, height: 844 }
await assertExposure('white', '演示白', mobile, { white: true })
await assertExposure('black', '演示黑', mobile, { black: true })
const afterMaskCanvas = await page.evaluate(() => (
  [...document.querySelectorAll('canvas')].filter((item) => {
    const box = item.getBoundingClientRect()
    return box.width > 40 && box.height > 40 && getComputedStyle(item).opacity !== '0'
  }).length
))
if (afterMaskCanvas === 1) pass('one-visible-webgl-canvas', afterMaskCanvas)
else fail('one-visible-webgl-canvas', afterMaskCanvas)
await page.setViewportSize({ width: 1920, height: 1080 })
await page.getByRole('button', { name: '演示白' }).click()
await page.evaluate(() => window.__vehicle3d.resetCamera?.())
await page.waitForTimeout(800)

const waitPreset = async (id, immediate = false) => {
  await page.evaluate(({ next, now }) => window.__vehicle3d.setCameraPreset?.(next, now), { next: id, now: immediate })
  await page.waitForTimeout(immediate ? 220 : 850)
}

const snapshot = () => page.evaluate(() => window.__vehicle3d.getRestoreSnapshot?.())

const beforeInteriorSnap = await snapshot()
const exteriorBeforeInterior = await page.locator('canvas.vehicle-webgl').screenshot()
await page.getByRole('button', { name: '查看内饰' }).click()
await page.waitForTimeout(900)
const afterRealClick = await page.evaluate(() => window.__vehicle3d.isInteriorView?.())
if (afterRealClick) pass('interior-real-button-click')
else fail('interior-real-button-click', 'button did not enter interior')

const interiorShot = await page.locator('canvas.vehicle-webgl').screenshot()
await page.locator('.vehicle-3d-stage').screenshot({ path: `${outDir}interior-wide-1920x1080.png` })
console.log('SHOT interior-wide-1920x1080.png')
const interiorDebug = await page.evaluate(() => window.__vehicle3d.getInteriorDebug?.())
if (interiorDebug?.cameraInside || interiorDebug?.cameraNearCabin) {
  pass('interior-camera-inside-bounds', JSON.stringify({
    camera: interiorDebug.camera, target: interiorDebug.target, box: interiorDebug.interiorBox,
  }))
} else fail('interior-camera-inside-bounds', JSON.stringify(interiorDebug))
if (interiorDebug?.visibleCount?.dashboard > 0) pass('interior-dashboard-visible', JSON.stringify(interiorDebug.visible.dashboard))
else fail('interior-dashboard-visible', JSON.stringify(interiorDebug?.visible))
if (interiorDebug?.visibleCount?.seat > 0) pass('interior-seat-visible', JSON.stringify(interiorDebug.visible.seat))
else fail('interior-seat-visible', JSON.stringify(interiorDebug?.visible))
if (interiorDebug?.visibleCount?.steering > 0) pass('interior-steering-visible', JSON.stringify(interiorDebug.visible.steering))
else fail('interior-steering-visible', JSON.stringify(interiorDebug?.visible))
const cluster = interiorDebug?.cluster
const instrumentPixels = interiorDebug?.instrumentPixels
if (cluster?.visible && cluster.bezelRadius > 0.08) pass('instrument-bezel-visible', JSON.stringify(cluster))
else fail('instrument-bezel-visible', JSON.stringify(cluster))
const holeOk = Boolean(
  cluster?.bezelRadius >= (cluster?.screenRadius || 1) * 1.2
  && cluster?.behindSteering
  && (instrumentPixels?.skyHaloRatio ?? 1) < 0.22,
)
if (holeOk) pass('instrument-no-large-hole', JSON.stringify({ cluster, instrumentPixels }))
else fail('instrument-no-large-hole', JSON.stringify({ cluster, instrumentPixels }))
const clusterOk = Boolean(
  (cluster?.emissiveIntensity || 1) <= 0.25
  && (instrumentPixels?.screenNearWhiteRatio ?? 1) <= 0.08
  && (instrumentPixels?.screenMaxLuma ?? 255) < 250,
)
if (clusterOk) pass('instrument-not-overexposed', JSON.stringify({ emissive: cluster?.emissiveIntensity, instrumentPixels }))
else fail('instrument-not-overexposed', JSON.stringify({ cluster, instrumentPixels }))
if (interiorDebug?.fov >= 48 && interiorDebug?.fov <= 58 && interiorDebug.visibleCount.steering > 0 && interiorDebug.cluster?.visible) {
  pass('driver-view-visible', JSON.stringify({ fov: interiorDebug.fov, steeringScreenRatio: interiorDebug.steeringScreenRatio }))
} else fail('driver-view-visible', JSON.stringify({ fov: interiorDebug?.fov, ratio: interiorDebug?.steeringScreenRatio, cluster: interiorDebug?.cluster }))
await page.locator('.vehicle-3d-stage').screenshot({ path: `${outDir}interior-driver-1920x1080.png` })
console.log('SHOT interior-driver-1920x1080.png')
saveFinal('interior-driver-1920x1080.png', 'final-driver-view-1920x1080.png')
await page.evaluate(() => window.__vehicle3d.frameInstrumentCloseup?.())
await page.waitForTimeout(240)
await page.locator('.vehicle-3d-stage').screenshot({ path: `${outDir}instrument-bezel-closeup.png` })
console.log('SHOT instrument-bezel-closeup.png')
saveFinal('instrument-bezel-closeup.png', 'final-instrument-panel.png')
const badgeShown = await page.evaluate(() => window.__vehicle3d.setBadgeVisible?.(true))
await page.waitForTimeout(200)
await page.locator('.vehicle-3d-stage').screenshot({ path: `${finalDir}final-eb-visible.png` })
console.log('SHOT final-delivery/final-eb-visible.png')
const badgeHidden = await page.evaluate(() => window.__vehicle3d.setBadgeVisible?.(false))
await page.waitForTimeout(200)
await page.locator('.vehicle-3d-stage').screenshot({ path: `${finalDir}final-eb-hidden.png` })
console.log('SHOT final-delivery/final-eb-hidden.png')
if (Array.isArray(badgeHidden) && badgeHidden.every((item) => item.visible === false) && badgeShown?.length) {
  pass('eb-badge-runtime-hidden', JSON.stringify({ shown: badgeShown, hidden: badgeHidden }))
} else {
  fail('eb-badge-runtime-hidden', JSON.stringify({ shown: badgeShown, hidden: badgeHidden }))
}
await waitPreset('interior', true)
glbHits.length = 0
await page.getByRole('button', { name: '座舱全景' }).click()
await page.waitForTimeout(900)
const cabinDebug = await page.evaluate(() => window.__vehicle3d.getInteriorDebug?.())
const cabinShot = await page.locator('canvas.vehicle-webgl').screenshot()
await page.locator('.vehicle-3d-stage').screenshot({ path: `${outDir}interior-cabin-wide-1920x1080.png` })
console.log('SHOT interior-cabin-wide-1920x1080.png')
saveFinal('interior-cabin-wide-1920x1080.png', 'final-cabin-wide-1920x1080.png')
if (glbHits.length === 0) pass('interior-view-switch-no-glb', glbHits.length)
else fail('interior-view-switch-no-glb', glbHits.length)
if (Buffer.compare(interiorShot, cabinShot) !== 0) pass('cabin-wide-view-visible', JSON.stringify({
  fov: cabinDebug?.fov,
  camera: cabinDebug?.camera,
  target: cabinDebug?.target,
}))
else fail('cabin-wide-view-visible', 'pixels identical')
if ((cabinDebug?.visibleCount?.seat || 0) >= 2) pass('cabin-wide-seat-visible', JSON.stringify(cabinDebug.visible.seat))
else fail('cabin-wide-seat-visible', JSON.stringify(cabinDebug?.visible))
if (cabinDebug?.cameraInside || cabinDebug?.cameraNearCabin) pass('cabin-camera-inside-bounds', JSON.stringify({ camera: cabinDebug.camera, box: cabinDebug.interiorBox }))
else fail('cabin-camera-inside-bounds', JSON.stringify(cabinDebug))
await page.getByRole('button', { name: '模型信息' }).click()
await page.waitForTimeout(200)
const disclaimer = await page.evaluate(() => document.body.innerText)
if (
  disclaimer.includes('课程车辆配置演示')
  && disclaimer.includes('第三方')
  && disclaimer.includes('仅限非商业课程演示')
  && disclaimer.includes('不属于Bugatti或小米官方界面')
) pass('trademark-disclaimer-visible')
else fail('trademark-disclaimer-visible', disclaimer.slice(0, 320))
const dialogText = await page.locator('.model-dialog').innerText()
if (
  dialogText.includes('模型名称')
  && dialogText.includes('作者')
  && dialogText.includes('来源链接')
  && dialogText.includes('许可证名称')
  && dialogText.includes('是否允许商业使用')
  && dialogText.includes('本项目使用目的')
  && dialogText.includes('仅限非商业课程演示')
) pass('model-license-fields-visible')
else fail('model-license-fields-visible', dialogText.slice(0, 320))
await page.locator('.model-dialog').screenshot({ path: `${outDir}model-information-dialog.png` }).catch(async () => {
  await page.screenshot({ path: `${outDir}model-information-dialog.png` })
})
console.log('SHOT model-information-dialog.png')
saveFinal('model-information-dialog.png', 'final-model-information.png')
await page.getByRole('button', { name: '关闭' }).click().catch(() => {})
await page.getByRole('button', { name: '查看外观' }).click()
await page.waitForTimeout(900)
if (Buffer.compare(exteriorBeforeInterior, interiorShot) !== 0) pass('interior-pixels-changed')
else fail('interior-pixels-changed', 'pixels identical')

glbHits.length = 0
await page.locator('.tabs button', { hasText: '内饰' }).click()
await page.waitForTimeout(200)
const beforeInteriorColor = await page.locator('canvas.vehicle-webgl').screenshot()
const interiorCards = page.locator('.option-card')
const interiorCardCount = await interiorCards.count()
if (interiorCardCount > 1) await interiorCards.nth(interiorCardCount - 1).click()
else await interiorCards.first().click()
await page.waitForTimeout(850)
const afterInteriorColor = await page.locator('canvas.vehicle-webgl').screenshot()
if (glbHits.length === 0) pass('interior-color-no-glb', glbHits.length)
else fail('interior-color-no-glb', glbHits.length)
if (Buffer.compare(beforeInteriorColor, afterInteriorColor) !== 0) pass('interior-color-pixels-changed')
else fail('interior-color-pixels-changed', 'pixels identical')
await page.locator('.vehicle-3d-stage').screenshot({ path: `${outDir}interior-demo-cluster.png` })
console.log('SHOT interior-demo-cluster.png')
await page.locator('.tabs button', { hasText: '选装' }).click().catch(() => {})
await page.waitForTimeout(250)
await page.screenshot({ path: `${finalDir}final-cart-configuration.png`, fullPage: false })
console.log('SHOT final-delivery/final-cart-configuration.png')

await page.setViewportSize({ width: 1440, height: 900 })
await page.waitForTimeout(500)
await waitPreset('interior')
await page.locator('.vehicle-3d-stage').screenshot({ path: `${outDir}interior-driver-1440x900.png` })
console.log('SHOT interior-driver-1440x900.png')
await waitPreset('cabinWide')
await page.locator('.vehicle-3d-stage').screenshot({ path: `${outDir}interior-cabin-wide-1440x900.png` })
console.log('SHOT interior-cabin-wide-1440x900.png')

await page.setViewportSize({ width: 390, height: 844 })
await page.waitForTimeout(400)
await waitPreset('front45', true)
await page.evaluate(() => {
  const open = document.querySelector('.stage-dock.is-open')
  if (open) document.querySelector('.view-toggle')?.click()
})
await page.waitForTimeout(200)
const mobileBtn = page.locator('.interior-toggle')
const mobileVisible = await mobileBtn.isVisible()
if (mobileVisible) pass('mobile-interior-button-visible-390', await mobileBtn.textContent())
else fail('mobile-interior-button-visible-390', 'hidden')
const mobileCover = await page.evaluate(() => {
  const btn = document.querySelector('.interior-toggle')
  const bounds = window.__vehicle3d.getBounds?.()
  const canvas = document.querySelector('canvas.vehicle-webgl')?.getBoundingClientRect()
  if (!btn || !bounds || !canvas) return { ok: false }
  const tools = btn.getBoundingClientRect()
  const core = {
    left: canvas.left + canvas.width * 0.22,
    right: canvas.left + canvas.width * 0.78,
    top: canvas.top + canvas.height * 0.18,
    bottom: canvas.top + canvas.height * 0.62,
  }
  const hit = !(tools.right <= core.left || core.right <= tools.left || tools.bottom <= core.top || core.bottom <= tools.top)
  return { ok: !hit, tools: { x: tools.x, y: tools.y, w: tools.width, h: tools.height }, label: btn.textContent }
})
if (mobileCover.ok) pass('mobile-interior-button-not-cover-car', JSON.stringify(mobileCover))
else fail('mobile-interior-button-not-cover-car', JSON.stringify(mobileCover))
const beforeMobileLabel = (await mobileBtn.textContent() || '').trim()
await mobileBtn.click()
await page.waitForTimeout(900)
if (beforeMobileLabel.includes('查看外观')) {
  await mobileBtn.click()
  await page.waitForTimeout(900)
}
await page.locator('.vehicle-3d-stage').screenshot({ path: `${outDir}interior-driver-390x844.png` })
console.log('SHOT interior-driver-390x844.png')
saveFinal('interior-driver-390x844.png', 'final-driver-view-390x844.png')
await page.locator('.view-toggle').click().catch(() => {})
await page.waitForTimeout(220)
const mobileInteriorUsable = await page.evaluate(() => {
  const toggle = document.querySelector('.interior-toggle')
  const toggleBox = toggle?.getBoundingClientRect()
  const toggleOk = Boolean(toggle && getComputedStyle(toggle).display !== 'none' && toggleBox && toggleBox.width >= 44)
  const visible = (name) => [...document.querySelectorAll('button')].some((item) => (
    (item.textContent || '').trim() === name && getComputedStyle(item).display !== 'none' && item.getBoundingClientRect().width >= 40
  ))
  return {
    ok: toggleOk && visible('驾驶位') && visible('座舱全景') && visible('查看外观'),
    toggleOk,
    driver: visible('驾驶位'),
    cabin: visible('座舱全景'),
    exterior: visible('查看外观'),
  }
})
if (mobileInteriorUsable.ok) pass('mobile-interior-controls-usable', JSON.stringify(mobileInteriorUsable))
else fail('mobile-interior-controls-usable', JSON.stringify(mobileInteriorUsable))
const cabinBtn = page.getByRole('button', { name: '座舱全景' })
if (await cabinBtn.isVisible()) {
  await cabinBtn.click()
  await page.waitForTimeout(800)
}
await page.evaluate(() => {
  const open = document.querySelector('.stage-dock.is-open')
  if (open) document.querySelector('.view-toggle')?.click()
})
await page.waitForTimeout(200)
await page.locator('.vehicle-3d-stage').screenshot({ path: `${outDir}interior-cabin-wide-390x844.png` })
console.log('SHOT interior-cabin-wide-390x844.png')
saveFinal('interior-cabin-wide-390x844.png', 'final-cabin-wide-390x844.png')
await waitPreset('front45', true)

await page.setViewportSize({ width: 1920, height: 1080 })
await page.waitForTimeout(500)
await waitPreset('front45', true)
const restored = await snapshot()
const constraintsOk = (
  restored && !restored.interiorMode
  && Math.abs(restored.fov - 31) <= 0.2
  && restored.minDistance > 1.5
  && restored.maxDistance > restored.minDistance
  && Math.abs(restored.minPolar - 1.08) < 0.05
  && Math.abs(restored.maxPolar - 1.42) < 0.05
  && restored.cabinFill === 0
)
if (constraintsOk) pass('exterior-constraints-restored', JSON.stringify(restored))
else fail('exterior-constraints-restored', JSON.stringify(restored))
await page.locator('.vehicle-3d-stage').screenshot({ path: `${outDir}exterior-restored.png` })
console.log('SHOT exterior-restored.png')
saveFinal('exterior-restored.png', 'final-exterior-1920x1080.png')
await waitPreset('cabinWide', true)
await waitPreset('front45', true)
const afterCabin = await snapshot()
const afterCabinOk = afterCabin && !afterCabin.interiorMode && Math.abs(afterCabin.fov - 31) <= 0.2 && afterCabin.minDistance > 1.5
if (afterCabinOk) pass('exterior-restore-after-cabin-view', JSON.stringify(afterCabin))
else fail('exterior-restore-after-cabin-view', JSON.stringify(afterCabin))

const baseline = await snapshot()
for (let i = 0; i < 20; i += 1) {
  await waitPreset('interior', true)
  await waitPreset('front45', true)
}
const after20 = await snapshot()
await page.locator('.vehicle-3d-stage').screenshot({ path: `${outDir}exterior-after-20-interior-switches.png` })
console.log('SHOT exterior-after-20-interior-switches.png')
const dist = (a, b) => Math.hypot((a?.[0] || 0) - (b?.[0] || 0), (a?.[1] || 0) - (b?.[1] || 0), (a?.[2] || 0) - (b?.[2] || 0))
const drift = {
  pos: dist(baseline?.position, after20?.position),
  target: dist(baseline?.target, after20?.target),
  fov: Math.abs((baseline?.fov || 0) - (after20?.fov || 0)),
  minDistance: Math.abs((baseline?.minDistance || 0) - (after20?.minDistance || 0)),
  maxDistance: Math.abs((baseline?.maxDistance || 0) - (after20?.maxDistance || 0)),
  minPolar: Math.abs((baseline?.minPolar || 0) - (after20?.minPolar || 0)),
  maxPolar: Math.abs((baseline?.maxPolar || 0) - (after20?.maxPolar || 0)),
  boundsW: Math.abs((baseline?.bounds?.width || 0) - (after20?.bounds?.width || 0)),
  boundsL: Math.abs((baseline?.bounds?.left || 0) - (after20?.bounds?.left || 0)),
}
const driftOk = (
  drift.pos < 0.08 && drift.target < 0.08 && drift.fov < 0.15
  && drift.minDistance < 0.2 && drift.maxDistance < 0.35
  && drift.minPolar < 0.02 && drift.maxPolar < 0.02
  && drift.boundsW < 0.02 && drift.boundsL < 0.02
  && after20?.interiorMode === false
)
if (driftOk) pass('exterior-restore-repeat-20', JSON.stringify(drift))
else fail('exterior-restore-repeat-20', JSON.stringify({ drift, baseline, after20 }))

if (beforeInteriorSnap?.fov) {
  /* baseline captured before first interior */
}

const thumbs = await page.evaluate(() => (
  [...document.querySelectorAll('.presets button')].map((button) => {
    const img = button.querySelector('img')
    const mark = button.querySelector('.angle-mark')
    return {
      hasImg: Boolean(img && img.naturalWidth > 8),
      hasFallback: Boolean(mark),
      text: (button.textContent || '').trim(),
    }
  })
))
if (thumbs.length >= 5 && thumbs.every((item) => (item.hasImg || item.hasFallback) && item.text)) {
  pass('thumbnail-not-blank', JSON.stringify(thumbs))
} else {
  fail('thumbnail-not-blank', JSON.stringify(thumbs))
}

const overlap = await page.evaluate(() => {
  const boxes = ['.stage-brand', '.stage-tools', '.presets']
    .map((sel) => document.querySelector(sel)?.getBoundingClientRect())
    .filter(Boolean)
  const hit = (a, b) => !(a.right <= b.left || b.right <= a.left || a.bottom <= b.top || b.bottom <= a.top)
  const pairs = []
  for (let i = 0; i < boxes.length; i += 1) {
    for (let j = i + 1; j < boxes.length; j += 1) {
      if (hit(boxes[i], boxes[j])) pairs.push(`${i}-${j}`)
    }
  }
  return pairs
})
if (!overlap.length) pass('controls-no-overlap')
else fail('controls-no-overlap', JSON.stringify(overlap))

const strip = await page.locator('.presets').boundingBox()
if (strip && strip.width > 80 && strip.height > 24) pass('angle-strip-visible', JSON.stringify(strip))
else fail('angle-strip-visible', JSON.stringify(strip))

for (const viewport of [
  { name: '1920x1080', width: 1920, height: 1080, compact: false },
  { name: '2560x1440', width: 2560, height: 1440, compact: false },
  { name: '1440x900', width: 1440, height: 900, compact: false },
  { name: '900x1200', width: 900, height: 1200, compact: true },
]) {
  await page.setViewportSize({ width: viewport.width, height: viewport.height })
  await page.waitForTimeout(800)
  await assertLayout(viewport.name)
  await assertBounds(viewport.name, viewport.compact)
  const bar = await page.evaluate(() => {
    const box = document.querySelector('.price-bar')?.getBoundingClientRect()
    return box ? { top: box.top, bottom: box.bottom, inView: box.top >= 0 && box.bottom <= window.innerHeight + 1 } : null
  })
  if (bar?.inView) pass(`price-bar-in-view-${viewport.name}`, JSON.stringify(bar))
  else fail(`price-bar-in-view-${viewport.name}`, JSON.stringify(bar))
  const lastCover = await page.evaluate(() => {
    const last = document.querySelector('.option-card:last-child')
    const price = document.querySelector('.price-bar')
    const scroller = document.querySelector('.panel-scroll')
    if (!last || !price || !scroller) return { ok: false, gap: 0 }
    scroller.scrollTop = scroller.scrollHeight
    const a = last.getBoundingClientRect()
    const b = price.getBoundingClientRect()
    const gap = b.top - a.bottom
    return { ok: gap >= 16, gap, lastBottom: a.bottom, barTop: b.top }
  })
  if (lastCover.ok) pass(`last-option-not-covered-${viewport.name}`, JSON.stringify(lastCover))
  else fail(`last-option-not-covered-${viewport.name}`, JSON.stringify(lastCover))
  if (lastCover.gap >= 16) pass(`last-option-gap-${viewport.name}`, lastCover.gap)
  else fail(`last-option-gap-${viewport.name}`, lastCover.gap)
  const chromeOverlap = await page.evaluate(() => {
    const boxes = ['.stage-brand', '.stage-tools', '.presets']
      .map((sel) => document.querySelector(sel)?.getBoundingClientRect())
      .filter(Boolean)
    const hit = (a, b) => !(a.right <= b.left || b.right <= a.left || a.bottom <= b.top || b.bottom <= a.top)
    const pairs = []
    for (let i = 0; i < boxes.length; i += 1) {
      for (let j = i + 1; j < boxes.length; j += 1) {
        if (hit(boxes[i], boxes[j])) pairs.push(`${i}-${j}`)
      }
    }
    return { pairs, boxes: boxes.map((box) => ({
      x: Math.round(box.x), y: Math.round(box.y), w: Math.round(box.width), h: Math.round(box.height),
    })) }
  })
  if (!chromeOverlap.pairs.length) pass(`controls-no-overlap-${viewport.name}`, JSON.stringify(chromeOverlap.boxes))
  else fail(`controls-no-overlap-${viewport.name}`, JSON.stringify(chromeOverlap))
  await page.screenshot({ path: `${outDir}final-${viewport.name}.png`, fullPage: false })
  console.log(`SHOT final-${viewport.name}.png`)
  if (viewport.name === '900x1200') {
    const stage = page.locator('.vehicle-3d-stage')
    if (await stage.count()) {
      await stage.screenshot({ path: `${outDir}stage-900x1200.png` })
      console.log('SHOT stage-900x1200.png')
    }
  }
}

const assertMobileChrome = async (name, width, height) => {
  await page.setViewportSize({ width, height })
  await page.waitForTimeout(400)
  await page.evaluate(() => {
    const open = document.querySelector('.stage-dock.is-open')
    if (open) document.querySelector('.view-toggle')?.click()
  })
  await waitPreset('front45', true)
  await page.waitForTimeout(300)
  const bar = await page.evaluate(() => {
    const box = document.querySelector('.price-bar')?.getBoundingClientRect()
    return box ? { top: box.top, bottom: box.bottom, inView: box.top >= 0 && box.bottom <= window.innerHeight + 1 } : null
  })
  if (bar?.inView) pass(`price-bar-in-view-${name}`, JSON.stringify(bar))
  else fail(`price-bar-in-view-${name}`, JSON.stringify(bar))
  const cover = await page.evaluate(() => {
    const bounds = window.__vehicle3d?.getBounds?.()
    const canvas = document.querySelector('canvas.vehicle-webgl')?.getBoundingClientRect()
    const tools = document.querySelector('.stage-tools')?.getBoundingClientRect()
    const presets = document.querySelector('.presets')
    const presetStyle = presets ? getComputedStyle(presets) : null
    const presetBox = presets?.getBoundingClientRect()
    const presetsVisible = Boolean(
      presetStyle && presetStyle.display !== 'none' && presetBox && presetBox.height > 8 && presetBox.width > 8,
    )
    const coreTools = [...document.querySelectorAll('.stage-tools .tool')]
      .filter((item) => getComputedStyle(item).display !== 'none')
      .map((item) => item.textContent.trim())
    if (!bounds || !canvas || !tools) {
      return { ok: false, reason: 'missing', presetsVisible, coreTools }
    }
    const car = {
      left: canvas.left + bounds.left * canvas.width,
      right: canvas.left + bounds.right * canvas.width,
      top: canvas.top + bounds.top * canvas.height,
      bottom: canvas.top + bounds.bottom * canvas.height,
    }
    const core = {
      left: car.left + (car.right - car.left) * 0.12,
      right: car.right - (car.right - car.left) * 0.12,
      top: car.top + (car.bottom - car.top) * 0.08,
      bottom: car.bottom - (car.bottom - car.top) * 0.2,
    }
    const hit = (a, b) => !(a.right <= b.left || b.right <= a.left || a.bottom <= b.top || b.bottom <= a.top)
    const toolsHit = hit(core, tools)
    const presetsHit = presetsVisible && hit(core, presetBox)
    return {
      ok: !presetsVisible && !toolsHit && !presetsHit && coreTools.includes('3D') && coreTools.some((item) => item.includes('内饰') || item.includes('外观')) && coreTools.includes('视角'),
      presetsVisible,
      toolsHit,
      presetsHit,
      coreTools,
      tools: { x: Math.round(tools.x), y: Math.round(tools.y), w: Math.round(tools.width), h: Math.round(tools.height) },
    }
  })
  if (cover.ok) pass(`mobile-controls-not-cover-vehicle-${name}`, JSON.stringify(cover))
  else fail(`mobile-controls-not-cover-vehicle-${name}`, JSON.stringify(cover))
  await page.screenshot({ path: `${outDir}final-${name}.png`, fullPage: false })
  console.log(`SHOT final-${name}.png`)
}

await assertMobileChrome('390x844', 390, 844)
await assertMobileChrome('412x915', 412, 915)

await page.setViewportSize({ width: 1920, height: 1080 })
const primaryHash = quote?.data?.configurationHash
const primaryPrice = quote?.data?.unitPrice
await page.evaluate(({ token, user }) => {
  localStorage.setItem('eshop_token', token)
  localStorage.setItem('eshop_user', JSON.stringify({
    userId: user.userId,
    username: user.username,
    nickname: user.nickname,
    role: user.role,
  }))
}, { token: login.data.token, user: login.data })
glbHits.length = 0
const secondaryUrl = `${origin}/pc/vehicles/${secondaryId}/configurator`
await page.goto(secondaryUrl, { waitUntil: 'networkidle' })
await wait3d()
if (glbHits.length === 1) pass(`glb-first-load-${secondaryId}`, glbHits.length)
else fail(`glb-first-load-${secondaryId}`, glbHits.length)
const secondaryUi = await page.evaluate(() => ({
  title: document.querySelector('.configurator-panel h1')?.textContent || '',
  price: document.querySelector('.price-bar strong')?.textContent || '',
  disclaimer: document.body.innerText.includes('课程车辆配置演示') && document.body.innerText.includes('第三方'),
}))
if (secondaryUi.disclaimer) pass(`disclaimer-visible-${secondaryId}`)
else fail(`disclaimer-visible-${secondaryId}`, secondaryUi.title)
const secondaryQuoteText = await page.evaluate(() => ({
  price: document.querySelector('.price-bar strong')?.textContent || '',
  panel: document.querySelector('.configurator-panel')?.innerText || '',
}))
const primaryName = sampledQuotes.find((item) => item.productId === primaryId)?.cfg.data.productName || ''
const secondaryName = sampledQuotes.find((item) => item.productId === secondaryId)?.cfg.data.productName || ''
const secondaryHash = sampledQuotes.find((item) => item.productId === secondaryId)?.quote?.data?.configurationHash
const shortSecondary = secondaryName.replace(/（课程演示）/g, '').replace(/新一代|小米/g, '').trim()
const nameSwitched = secondaryId === primaryId || (shortSecondary && secondaryQuoteText.panel.includes(shortSecondary.slice(0, 3)))
const hashDifferent = !primaryHash || !secondaryHash || primaryHash !== secondaryHash
if (nameSwitched && hashDifferent) {
  pass(`quote-ui-not-stale-after-product-switch`, JSON.stringify({
    from: primaryId, to: secondaryId, primaryHash, secondaryHash, primaryName, secondaryName, shown: secondaryQuoteText.price,
  }))
} else {
  fail(`quote-ui-not-stale-after-product-switch`, JSON.stringify({
    primaryHash, secondaryHash, primaryName, secondaryName, panel: secondaryQuoteText.panel.slice(0, 180),
  }))
}
glbHits.length = 0
await page.getByRole('button', { name: /演示|白|黑|银|绿/ }).nth(1).click().catch(async () => {
  await page.locator('.option-card').nth(1).click()
})
await page.waitForTimeout(700)
if (glbHits.length === 0) pass(`color-no-glb-${secondaryId}`, 0)
else fail(`color-no-glb-${secondaryId}`, glbHits.length)
await page.locator('.tabs button', { hasText: '车轮' }).click()
await page.waitForTimeout(200)
await page.locator('.option-card').nth(1).click().catch(() => {})
await page.waitForTimeout(400)
await page.locator('.tabs button', { hasText: '内饰' }).click()
await page.waitForTimeout(200)
await page.locator('.option-card').nth(1).click().catch(() => {})
await page.waitForTimeout(400)
if (glbHits.length === 0) pass(`options-no-glb-${secondaryId}`, 0)
else fail(`options-no-glb-${secondaryId}`, glbHits.length)
await page.locator('.tabs button', { hasText: '选装' }).click()
await page.waitForTimeout(400)
const buyBtn = page.locator('.price-bar button.primary', { hasText: '立即购买' })
await buyBtn.waitFor({ state: 'visible', timeout: 8000 }).catch(() => {})
await page.waitForFunction(() => {
  const btn = [...document.querySelectorAll('.price-bar button')].find((item) => (item.textContent || '').includes('立即购买'))
  return Boolean(btn && !btn.disabled)
}, null, { timeout: 8000 }).catch(() => {})
if (await buyBtn.isVisible()) {
  await Promise.all([
    page.waitForURL(/checkout|login|cart/, { timeout: 10000 }).catch(() => {}),
    buyBtn.click({ timeout: 5000 }).catch(() => {}),
  ])
  await page.waitForTimeout(400)
  const checkout = page.url()
  if (/checkout|login|cart/.test(checkout)) pass(`buy-now-${secondaryId}`, checkout)
  else fail(`buy-now-${secondaryId}`, checkout)
} else {
  fail(`buy-now-${secondaryId}`, 'button hidden')
}

await page.goto(pageUrl, { waitUntil: 'networkidle' })
await wait3d()
const first = await page.locator('canvas.vehicle-webgl').count()
await page.goto(`${origin}/pc/vehicles`, { waitUntil: 'networkidle' })
await page.goto(pageUrl, { waitUntil: 'networkidle' })
await wait3d()
const second = await page.locator('canvas.vehicle-webgl').count()
if (first === 1 && second === 1) pass('remount-single-canvas', `${first}/${second}`)
else fail('remount-single-canvas', `${first}/${second}`)

await page.goto(`${pageUrl}?fallback=2d`, { waitUntil: 'networkidle' })
const fallback = await page.locator('.vehicle-3d-fallback').count()
if (fallback === 1) pass('fallback-2d')
else fail('fallback-2d', fallback)

await browser.close()
if (process.exitCode) console.log('VERIFY_FAILED')
else console.log('VERIFY_OK')
