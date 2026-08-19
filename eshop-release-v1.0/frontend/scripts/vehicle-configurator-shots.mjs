import { mkdirSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { chromium } from 'playwright'

const outDir = fileURLToPath(new URL('../../docs/03-测试文档/screenshots/vehicle-configurator/', import.meta.url))
mkdirSync(outDir, { recursive: true })

const viewports = [
  { name: 'final-2560x1440', width: 2560, height: 1440 },
  { name: 'final-1920x1080', width: 1920, height: 1080 },
  { name: 'final-1440x900', width: 1440, height: 900 },
]

const live = 'http://127.0.0.1:8088/pc/vehicles/62/configurator'
const preview = 'http://127.0.0.1:4173/pc/vehicles/62/configurator'
let base = live
try {
  const probe = await fetch('http://127.0.0.1:8088/api/actuator/health', { signal: AbortSignal.timeout(1500) })
  if (!probe.ok) throw new Error('8088 down')
} catch {
  base = preview
}

const mockConfigurator = {
  code: 0,
  message: 'success',
  data: {
    productId: 62,
    productName: '新一代小米 SU7（课程演示）',
    disclaimer: '非小米汽车官方页面，仅用于课程项目演示',
    defaultSkuId: 62,
    defaultOptionValueIds: [1, 2, 3],
    skus: [
      { id: 62, skuCode: 'COURSE-XMEV-SU7-STD', versionName: '标准版', driveType: '后驱', basePrice: 219900, stock: 12, status: 'ENABLED' },
      { id: 63, skuCode: 'COURSE-XMEV-SU7-PRO', versionName: 'Pro', driveType: '后驱', basePrice: 249900, stock: 10, status: 'ENABLED' },
      { id: 64, skuCode: 'COURSE-XMEV-SU7-MAX', versionName: 'Max', driveType: '四驱', basePrice: 303900, stock: 6, status: 'ENABLED' },
    ],
    groups: [
      { id: 1, code: 'COLOR', name: '外观颜色', selectionType: 'SINGLE', values: [
        { id: 1, code: 'WHITE', name: '演示白', priceDelta: 0, colorHex: '#F4F1EA' },
        { id: 4, code: 'BLACK', name: '演示黑', priceDelta: 0, colorHex: '#1F2328' },
        { id: 7, code: 'SILVER', name: '演示银', priceDelta: 0, colorHex: '#B8C0C8' },
        { id: 10, code: 'GREEN', name: '演示绿', priceDelta: 8000, colorHex: '#5E7A62' },
      ] },
      { id: 2, code: 'WHEEL', name: '轮毂', selectionType: 'SINGLE', values: [
        { id: 2, code: 'W19', name: '19英寸演示轮毂', priceDelta: 0 },
        { id: 5, code: 'W20', name: '20英寸演示轮毂', priceDelta: 8000 },
        { id: 8, code: 'W21', name: '21英寸演示轮毂', priceDelta: 15000 },
      ] },
      { id: 3, code: 'INTERIOR', name: '内饰', selectionType: 'SINGLE', values: [
        { id: 3, code: 'BLACK', name: '深色织呢演示内饰', priceDelta: 0, colorHex: '#2A2A2A' },
        { id: 6, code: 'BEIGE', name: '浅色皮革演示内饰', priceDelta: 6000, colorHex: '#D8C7B0' },
        { id: 9, code: 'SPORT', name: '运动双色演示内饰', priceDelta: 8000, colorHex: '#7A1F1F' },
      ] },
      { id: 4, code: 'PACK', name: '选装包', selectionType: 'MULTI', values: [
        { id: 11, code: 'WINTER', name: '冬季套装（课程演示）', priceDelta: 3500 },
        { id: 12, code: 'COMFORT', name: '舒适套装（课程演示）', priceDelta: 8000 },
        { id: 13, code: 'TRACK', name: '赛道套装（课程演示）', priceDelta: 20000 },
      ] },
    ],
    rules: [],
  },
}

const mockQuote = {
  code: 0,
  data: {
    basePrice: 219900,
    optionAmount: 8000,
    unitPrice: 227900,
    configurationHash: 'preview-green',
    versionName: '标准版',
    selectedOptions: [
      { valueId: 10, groupName: '外观颜色', valueName: '演示绿', priceDelta: 8000, included: false },
      { valueId: 2, groupName: '轮毂', valueName: '19英寸演示轮毂', priceDelta: 0, included: true },
      { valueId: 3, groupName: '内饰', valueName: '深色织呢演示内饰', priceDelta: 0, included: true },
    ],
    purchasable: true,
  },
}

const browser = await chromium.launch()
const page = await browser.newPage()
page.setDefaultTimeout(25000)
if (base === preview) {
  await page.route('**/api/**', async (route) => {
    const url = route.request().url()
    if (url.includes('/configurator/quote')) {
      await route.fulfill({ contentType: 'application/json', body: JSON.stringify(mockQuote) })
      return
    }
    if (url.includes('/configurator')) {
      await route.fulfill({ contentType: 'application/json', body: JSON.stringify(mockConfigurator) })
      return
    }
    await route.fulfill({ contentType: 'application/json', body: JSON.stringify({ code: 0, data: null }) })
  })
}

const waitStage = async () => {
  await page.waitForSelector('.vehicle-stage img.hero')
  await page.waitForFunction(() => {
    const hero = document.querySelector('.vehicle-stage img.hero')
    return Boolean(hero && hero.complete && hero.naturalWidth > 0)
  })
  await page.waitForTimeout(400)
}

for (const viewport of viewports) {
  await page.setViewportSize({ width: viewport.width, height: viewport.height })
  await page.goto(base, { waitUntil: 'networkidle' })
  await waitStage()
  const target = `${outDir}${viewport.name}.png`
  await page.screenshot({ path: target, fullPage: false })
  console.log(`SHOT ${target} via ${base}`)
}

await page.setViewportSize({ width: 1920, height: 1080 })
await page.goto(base, { waitUntil: 'networkidle' })
await waitStage()

const results = []
const heroSrc = async () => page.locator('.vehicle-stage img.hero').getAttribute('src')

const initialSrc = await heroSrc()
results.push({ name: 'initial-src', ok: Boolean(initialSrc && initialSrc.includes('/su7/white/front45.webp')), detail: initialSrc })

await page.getByRole('button', { name: '演示绿' }).click()
await page.waitForTimeout(500)
const greenSrc = await heroSrc()
results.push({
  name: 'click-green-keeps-color',
  ok: Boolean(greenSrc && greenSrc.includes('/su7/green/') && !greenSrc.includes('/white/')),
  detail: greenSrc,
})

const sideThumb = page.locator('.gallery button', { hasText: '正侧' })
const sideDisabled = await sideThumb.isDisabled()
if (sideDisabled) {
  results.push({ name: 'side-available', ok: false, detail: '正侧按钮被禁用' })
} else {
  await sideThumb.click()
  await page.waitForTimeout(500)
  const sideSrc = await heroSrc()
  results.push({
    name: 'green-then-side-not-white',
    ok: Boolean(sideSrc && sideSrc.includes('/su7/green/side.webp') && !sideSrc.includes('/white/')),
    detail: sideSrc,
  })
}

for (const tab of ['车轮', '内饰', '选装', '车身']) {
  await page.locator('.tabs button', { hasText: tab }).click()
  await page.waitForTimeout(200)
  const cards = await page.locator('.option-card img').count()
  results.push({ name: `tab-${tab}-thumbs`, ok: cards > 0, detail: `cards=${cards}` })
}

await page.locator('.tabs button', { hasText: '选装' }).click()
await page.waitForTimeout(200)
const lastActions = await page.locator('.price-bar').innerText()
results.push({
  name: 'last-step-actions',
  ok: ['配置清单', '重置', '加入购物车', '立即购买'].every((label) => lastActions.includes(label)),
  detail: lastActions.replace(/\s+/g, ' ').trim(),
})

await page.locator('.sku-trigger').click()
await page.waitForTimeout(200)
const skuMenu = await page.locator('.sku-menu button').count()
results.push({ name: 'custom-sku-menu', ok: skuMenu >= 2, detail: `options=${skuMenu}` })

const aspect = await page.evaluate(() => {
  const hero = document.querySelector('.vehicle-stage img.hero')
  if (!hero) return null
  const style = getComputedStyle(hero)
  return {
    aspectRatio: style.aspectRatio,
    widthPct: Math.round((hero.getBoundingClientRect().width / document.querySelector('.vehicle-stage').getBoundingClientRect().width) * 100),
    top: Math.round(hero.getBoundingClientRect().top),
  }
})
results.push({
  name: 'hero-no-css-16-9-and-width',
  ok: Boolean(aspect && !String(aspect.aspectRatio).includes('16 / 9') && aspect.widthPct >= 75 && aspect.widthPct <= 94),
  detail: JSON.stringify(aspect),
})

const disclaimerColor = await page.evaluate(() => getComputedStyle(document.querySelector('.disclaimer')).color)
results.push({
  name: 'disclaimer-color',
  ok: disclaimerColor.includes('255') || disclaimerColor.includes('rgba'),
  detail: disclaimerColor,
})

console.log('VERIFY_START')
for (const item of results) {
  console.log(`${item.ok ? 'PASS' : 'FAIL'} ${item.name} :: ${item.detail}`)
}
console.log('VERIFY_END')
if (results.some((item) => !item.ok)) process.exitCode = 1

await browser.close()
