import { mkdirSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { chromium } from 'playwright'

const outDir = fileURLToPath(new URL('../../docs/03-测试文档/screenshots/vehicle-configurator/', import.meta.url))
mkdirSync(outDir, { recursive: true })
const origin = 'http://127.0.0.1:8088'
const pageUrl = `${origin}/pc/vehicles/62/configurator`

const fail = (name, detail) => {
  console.log(`FAIL ${name} :: ${detail}`)
  process.exitCode = 1
}
const pass = (name, detail = '') => console.log(`PASS ${name}${detail ? ` :: ${detail}` : ''}`)

const browser = await chromium.launch()
const page = await browser.newPage()
page.setDefaultTimeout(45000)

const waitReady = async () => {
  await page.waitForSelector('.vehicle-configurator')
  await page.waitForSelector('.configurator-panel')
  await page.waitForTimeout(800)
}

const inspect = async (label, width, height) => {
  await page.setViewportSize({ width, height })
  await page.waitForTimeout(500)
  const data = await page.evaluate(() => {
    const root = document.documentElement
    const body = document.body
    const stage = document.querySelector('.vehicle-3d-stage, .vehicle-stage')
    const panel = document.querySelector('.configurator-panel')
    const bar = document.querySelector('.price-bar')
    const thumbs = document.querySelector('.presets, .gallery')
    const stageBox = stage?.getBoundingClientRect()
    const panelBox = panel?.getBoundingClientRect()
    const barBox = bar?.getBoundingClientRect()
    return {
      pageScrollX: Math.max(0, root.scrollWidth - root.clientWidth, body.scrollWidth - body.clientWidth),
      pageScrollY: Math.max(0, root.scrollHeight - root.clientHeight),
      overflowX: getComputedStyle(root).overflowX,
      stage: stageBox ? {
        top: Math.round(stageBox.top),
        height: Math.round(stageBox.height),
        width: Math.round(stageBox.width),
        visible: stageBox.height > 80 && stageBox.width > 80,
      } : null,
      panel: panelBox ? {
        top: Math.round(panelBox.top),
        height: Math.round(panelBox.height),
        width: Math.round(panelBox.width),
        visible: panelBox.height > 110 && panelBox.width > 200,
      } : null,
      bar: barBox ? {
        bottom: Math.round(barBox.bottom),
        height: Math.round(barBox.height),
        inView: barBox.top >= 0 && barBox.bottom <= window.innerHeight + 1,
      } : null,
      overlap: Boolean(stageBox && panelBox && !(
        stageBox.bottom <= panelBox.top + 2
        || panelBox.bottom <= stageBox.top + 2
        || stageBox.right <= panelBox.left + 2
        || panelBox.right <= stageBox.left + 2
      )),
      thumbsOverflow: thumbs ? Math.max(0, Math.round(thumbs.scrollWidth - thumbs.clientWidth)) : 0,
      thumbsCanScroll: thumbs ? getComputedStyle(thumbs).overflowX : '',
    }
  })
  await page.screenshot({ path: `${outDir}responsive-${label}.png`, fullPage: false })
  console.log(`SHOT responsive-${label}.png`)

  const stacked = Boolean(data.stage && data.panel && data.panel.top >= data.stage.top + data.stage.height - 2)
  const noPageX = data.pageScrollX <= 1
  const stageOk = Boolean(data.stage?.visible)
  const panelOk = Boolean(data.panel?.visible)
  const barOk = Boolean(data.bar?.inView)
  const noOverlap = !data.overlap
  const stageFits = stacked
    ? data.stage && data.panel && data.stage.top >= 0 && data.stage.height + data.panel.height <= height + 2
    : data.stage && data.panel && data.stage.width + data.panel.width <= width + 2
  const thumbsOk = data.thumbsOverflow <= 1 || data.thumbsCanScroll === 'auto' || data.thumbsCanScroll === 'scroll' || data.thumbsCanScroll === 'visible'
  const detail = JSON.stringify(data)
  if (noPageX && stageOk && panelOk && barOk && stageFits && thumbsOk && noOverlap) pass(`layout-${label}`, detail)
  else fail(`layout-${label}`, `noPageX=${noPageX} stageOk=${stageOk} panelOk=${panelOk} barOk=${barOk} stageFits=${stageFits} thumbsOk=${thumbsOk} noOverlap=${noOverlap} ${detail}`)
}

await page.goto(pageUrl, { waitUntil: 'networkidle' })
await waitReady()

for (const viewport of [
  { name: '390x844', width: 390, height: 844 },
  { name: '412x915', width: 412, height: 915 },
  { name: '768x1024', width: 768, height: 1024 },
  { name: '844x390', width: 844, height: 390 },
  { name: '900x1200', width: 900, height: 1200 },
  { name: '1024x768', width: 1024, height: 768 },
  { name: '1099x800', width: 1099, height: 800 },
  { name: '1100x800', width: 1100, height: 800 },
  { name: '1101x800', width: 1101, height: 800 },
  { name: '1440x900', width: 1440, height: 900 },
  { name: '1920x1080', width: 1920, height: 1080 },
  { name: '2560x1440', width: 2560, height: 1440 },
]) {
  await inspect(viewport.name, viewport.width, viewport.height)
}

await browser.close()
if (process.exitCode) console.log('LAYOUT_VERIFY_FAILED')
else console.log('LAYOUT_VERIFY_OK')
