import { mkdirSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { chromium } from 'playwright'

const outDir = fileURLToPath(new URL('../../docs/03-测试文档/screenshots/vehicle-3d/', import.meta.url))
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
  const probe = await fetch('http://127.0.0.1:8088/api/actuator/health', { signal: AbortSignal.timeout(2000) })
  if (!probe.ok) throw new Error('8088 down')
  base = live
} catch {
  base = preview
}

const mockConfigurator = {
  code: 0,
  data: {
    productId: 62,
    productName: '新一代小米 SU7（课程演示）',
    defaultSkuId: 62,
    defaultOptionValueIds: [1, 2, 3],
    skus: [
      { id: 62, skuCode: 'COURSE-XMEV-SU7-STD', versionName: '标准版', driveType: '后驱', basePrice: 219900, stock: 12, status: 'ENABLED' },
    ],
    groups: [
      { id: 1, code: 'COLOR', name: '外观颜色', selectionType: 'SINGLE', values: [
        { id: 1, code: 'WHITE', name: '演示白', priceDelta: 0, colorHex: '#F4F1EA' },
        { id: 4, code: 'BLACK', name: '演示黑', priceDelta: 0, colorHex: '#1F2328' },
        { id: 10, code: 'GREEN', name: '演示绿', priceDelta: 8000, colorHex: '#5E7A62' },
      ] },
      { id: 2, code: 'WHEEL', name: '轮毂', selectionType: 'SINGLE', values: [
        { id: 2, code: 'W19', name: '19英寸演示轮毂', priceDelta: 0 },
        { id: 5, code: 'W20', name: '20英寸演示轮毂', priceDelta: 8000 },
        { id: 8, code: 'W21', name: '21英寸演示轮毂', priceDelta: 15000 },
      ] },
      { id: 3, code: 'INTERIOR', name: '内饰', selectionType: 'SINGLE', values: [
        { id: 3, code: 'BLACK', name: '深色织呢演示内饰', priceDelta: 0 },
      ] },
      { id: 4, code: 'PACK', name: '选装包', selectionType: 'MULTI', values: [
        { id: 13, code: 'TRACK', name: '赛道套装（课程演示）', priceDelta: 20000 },
      ] },
    ],
    rules: [],
  },
}

const browser = await chromium.launch()
const page = await browser.newPage()
page.setDefaultTimeout(60000)
if (base === preview) {
  await page.route('**/api/**', async (route) => {
    const url = route.request().url()
    if (url.includes('/configurator/quote')) {
      await route.fulfill({
        contentType: 'application/json',
        body: JSON.stringify({
          code: 0,
          data: {
            basePrice: 219900,
            optionAmount: 0,
            unitPrice: 219900,
            configurationHash: 'preview-3d',
            versionName: '标准版',
            selectedOptions: [],
            purchasable: true,
          },
        }),
      })
      return
    }
    if (url.includes('/configurator')) {
      await route.fulfill({ contentType: 'application/json', body: JSON.stringify(mockConfigurator) })
      return
    }
    await route.fulfill({ contentType: 'application/json', body: JSON.stringify({ code: 0, data: null }) })
  })
}

const wait3d = async () => {
  await page.waitForSelector('canvas.vehicle-webgl', { timeout: 20000 })
  await page.waitForFunction(() => {
    const canvas = document.querySelector('canvas.vehicle-webgl')
    return canvas && canvas.width > 8 && canvas.height > 8 && !document.querySelector('.vehicle-3d-loading')
  }, null, { timeout: 45000 })
  await page.waitForTimeout(800)
}

for (const viewport of viewports) {
  await page.setViewportSize({ width: viewport.width, height: viewport.height })
  await page.goto(base, { waitUntil: 'networkidle' })
  await wait3d()
  const target = `${outDir}${viewport.name}.png`
  await page.screenshot({ path: target, fullPage: false })
  console.log(`SHOT ${target} via ${base}`)
}

const glbRequests = []
page.on('request', (request) => {
  if (request.url().includes('.glb')) glbRequests.push(request.url())
})

await page.setViewportSize({ width: 1920, height: 1080 })
await page.goto(base, { waitUntil: 'networkidle' })
await wait3d()
glbRequests.length = 0
const before = await page.locator('canvas.vehicle-webgl').count()
await page.getByRole('button', { name: '演示绿' }).click()
await page.waitForTimeout(600)
await page.getByRole('button', { name: /21英寸/ }).click().catch(async () => {
  await page.locator('.tabs button', { hasText: '车轮' }).click()
  await page.getByRole('button', { name: /21英寸/ }).click()
})
await page.waitForTimeout(600)
const after = await page.locator('canvas.vehicle-webgl').count()
const box = await page.locator('canvas.vehicle-webgl').boundingBox()
console.log(`VERIFY canvas=${after} sameHost=${before === after} glbAfterColor=${glbRequests.length} box=${JSON.stringify(box)}`)

await browser.close()
