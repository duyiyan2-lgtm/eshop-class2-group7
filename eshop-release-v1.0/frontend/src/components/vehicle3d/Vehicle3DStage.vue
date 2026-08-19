<script setup>
import { onMounted, onUnmounted, ref, watch } from 'vue'
import { VEHICLE_DISCLAIMER, VEHICLE_VIEWS } from '../../utils/vehicle'
import Vehicle3DFallback from './Vehicle3DFallback.vue'
import Vehicle3DLoading from './Vehicle3DLoading.vue'
import { VEHICLE_3D_MANIFEST } from './vehicleModelManifest'

const props = defineProps({
  folder: { type: String, required: true },
  colorCode: { type: String, default: '' },
  colorName: { type: String, default: '' },
  productName: { type: String, default: '课程演示车型' },
  wheelCode: { type: String, default: 'W19' },
  interiorCode: { type: String, default: 'BLACK' },
  packCodes: { type: Array, default: () => [] },
})

const emit = defineEmits(['back'])

const canvasRef = ref(null)
const progress = ref(8)
const loading = ref(true)
const failed = ref(false)
const failReason = ref('当前设备无法显示三维预览，已切换到多角度图片。')
const mode = ref('3d')
const interiorView = ref(false)
const autoRotate = ref(false)
const activePreset = ref('front45')
const fullscreen = ref(false)
const unsupported = ref([])
const reduceMotion = ref(false)
const infoOpen = ref(false)
const viewSheet = ref(false)
const angleThumbs = ref({})

const captureThumbs = async () => {
  if (!sceneApi?.captureAngleThumbs) return
  try {
    const shots = await sceneApi.captureAngleThumbs()
    if (shots && Object.keys(shots).length) angleThumbs.value = shots
  } catch {
    /* 保留线稿回退 */
  }
}

let sceneApi = null
let loadToken = 0

const applyConfig = async () => {
  if (!sceneApi) return
  await sceneApi.setBodyColor(props.colorCode)
  await sceneApi.setWheel(props.wheelCode)
  await sceneApi.setInterior(props.interiorCode)
  unsupported.value = sceneApi.setPacks(props.packCodes) || []
}

const boot = async () => {
  const token = ++loadToken
  loading.value = true
  failed.value = false
  try {
    const { createVehicleScene, detectWebGL } = await import('./useVehicleScene.js')
    if (!detectWebGL()) throw new Error('WEBGL_UNAVAILABLE')
    if (!canvasRef.value) throw new Error('NO_CANVAS')
    sceneApi = await createVehicleScene(canvasRef.value, {
      reduceMotion: reduceMotion.value,
      onProgress: (event) => {
        if (token !== loadToken) return
        progress.value = event.percent || progress.value
      },
      onLost: () => {
        failReason.value = 'WebGL 上下文已丢失，已切换到多角度图片。'
        failed.value = true
        mode.value = '2d'
      },
    })
    if (token !== loadToken) {
      sceneApi.dispose()
      sceneApi = null
      return
    }
    await applyConfig()
    await captureThumbs()
    window.__vehicle3d = {
      getBounds: () => sceneApi?.getVehicleScreenBounds?.() || null,
      resetCamera: () => {
        interiorView.value = false
        activePreset.value = 'front45'
        sceneApi?.resetCamera?.()
      },
      isLoopRunning: () => Boolean(sceneApi?.isLoopRunning?.()),
      getRenderCount: () => sceneApi?.getRenderCount?.() ?? 0,
      sampleExposure: () => sceneApi?.sampleExposure?.() || null,
      isInteriorView: () => Boolean(sceneApi?.isInteriorView?.()),
      setCameraPreset: (id, immediate) => {
        interiorView.value = id === 'interior' || id === 'cabinWide'
        activePreset.value = id || 'front45'
        sceneApi?.setCameraPreset?.(id, immediate)
      },
      getSceneDebug: () => sceneApi?.getSceneDebug?.() || null,
      getInteriorDebug: () => sceneApi?.getInteriorDebug?.() || null,
      getRestoreSnapshot: () => sceneApi?.getRestoreSnapshot?.() || null,
      frameInstrumentCloseup: () => sceneApi?.frameInstrumentCloseup?.() || null,
      setBadgeVisible: (visible) => sceneApi?.setBadgeVisible?.(visible) || [],
    }
    loading.value = false
  } catch (error) {
    if (token !== loadToken) return
    failReason.value = error.message === 'WEBGL_UNAVAILABLE'
      ? '当前浏览器不支持 WebGL，已切换到多角度图片。'
      : '三维模型加载失败，已切换到多角度图片。'
    failed.value = true
    mode.value = '2d'
    loading.value = false
  }
}

const teardown = () => {
  loadToken += 1
  sceneApi?.dispose()
  sceneApi = null
  if (window.__vehicle3d) delete window.__vehicle3d
}

const resetView = () => {
  interiorView.value = false
  activePreset.value = 'front45'
  sceneApi?.resetCamera?.()
}

const toggleAutoRotate = () => {
  autoRotate.value = !autoRotate.value
  sceneApi?.setAutoRotate?.(autoRotate.value)
}

const toggleViewSheet = () => {
  viewSheet.value = !viewSheet.value
}

const use2d = () => {
  mode.value = '2d'
  teardown()
}

const use3d = () => {
  mode.value = '3d'
  failed.value = false
  boot()
}

const selectPreset = (id) => {
  interiorView.value = id === 'interior' || id === 'cabinWide'
  activePreset.value = id
  sceneApi?.setCameraPreset(id)
}

const toggleInterior = () => {
  selectPreset(interiorView.value ? 'front45' : 'interior')
}

const toggleFullscreen = async () => {
  try {
    if (!document.fullscreenElement) {
      await document.documentElement.requestFullscreen?.()
      fullscreen.value = true
    } else {
      await document.exitFullscreen?.()
      fullscreen.value = false
    }
  } catch {
    fullscreen.value = Boolean(document.fullscreenElement)
  }
}

watch(
  () => [props.colorCode, props.wheelCode, props.interiorCode, props.packCodes.join(',')],
  async (next, prev) => {
    if (mode.value !== '3d') return
    await applyConfig()
    if (prev && next[0] !== prev[0]) await captureThumbs()
  },
)

onMounted(() => {
  const motion = window.matchMedia('(prefers-reduced-motion: reduce)')
  reduceMotion.value = motion.matches
  const onFs = () => {
    fullscreen.value = Boolean(document.fullscreenElement)
  }
  document.addEventListener('fullscreenchange', onFs)
  if (new URLSearchParams(window.location.search).get('fallback') === '2d') {
    failed.value = true
    mode.value = '2d'
    failReason.value = '已按调试参数切换到二维备用舞台。'
  } else {
    boot()
  }
  onUnmounted(() => {
    document.removeEventListener('fullscreenchange', onFs)
    teardown()
  })
})
</script>

<template>
  <Vehicle3DFallback
    v-if="mode === '2d'"
    :folder="folder"
    :color-code="colorCode"
    :color-name="colorName"
    :product-name="productName"
    :reason="failReason"
    @back="emit('back')"
  />

  <section v-else class="vehicle-stage vehicle-3d-stage" aria-label="车辆三维展示舞台">
    <header class="stage-brand">
      <button type="button" class="brand" @click="emit('back')">
        <b>E</b>
        <span>E-Shop</span>
      </button>
      <div class="stage-copy">
        <strong>课程车辆配置演示</strong>
        <small>商品数据：{{ productName }} · 3D 为第三方授权外形模型，不代表实际销售车辆</small>
      </div>
      <button type="button" class="info-btn" @click="infoOpen = true">模型信息</button>
    </header>

    <div class="canvas-wrap">
      <canvas ref="canvasRef" class="vehicle-webgl" aria-label="可拖动三维车辆" />
      <Vehicle3DLoading v-if="loading" :progress="progress" />
      <p v-if="unsupported.length" class="unsupported">{{ unsupported[0] }}</p>
    </div>

    <div class="stage-dock" :class="{ 'is-open': viewSheet }">
      <div v-if="interiorView" class="interior-view-panel" role="toolbar" aria-label="内饰视角">
        <button
          type="button"
          class="tool"
          :class="{ active: activePreset === 'interior' }"
          @click="selectPreset('interior')"
        >
          驾驶位
        </button>
        <button
          type="button"
          class="tool"
          :class="{ active: activePreset === 'cabinWide' }"
          @click="selectPreset('cabinWide')"
        >
          座舱全景
        </button>
      </div>
      <div class="stage-tools" role="toolbar" aria-label="舞台控件">
        <button type="button" class="tool extra" @click="use2d">2D</button>
        <button type="button" class="tool" :class="{ active: mode === '3d' }" @click="use3d">3D</button>
        <button type="button" class="tool extra" :class="{ active: autoRotate }" @click="toggleAutoRotate">自动旋转</button>
        <button type="button" class="tool interior-toggle" :class="{ active: interiorView }" @click="toggleInterior">
          {{ interiorView ? '查看外观' : '查看内饰' }}
        </button>
        <button
          v-if="interiorView"
          type="button"
          class="tool interior-view-btn"
          :class="{ active: activePreset === 'interior' }"
          @click="selectPreset('interior')"
        >
          驾驶位
        </button>
        <button
          v-if="interiorView"
          type="button"
          class="tool interior-view-btn"
          :class="{ active: activePreset === 'cabinWide' }"
          @click="selectPreset('cabinWide')"
        >
          座舱全景
        </button>
        <button type="button" class="tool extra" @click="resetView">重置视角</button>
        <button type="button" class="tool extra" @click="toggleFullscreen">
          {{ fullscreen ? '退出全屏' : '全屏' }}
        </button>
        <button
          type="button"
          class="tool view-toggle"
          :class="{ active: viewSheet }"
          :aria-expanded="viewSheet"
          aria-controls="stage-view-sheet"
          @click="toggleViewSheet"
        >
          视角
        </button>
      </div>
      <div id="stage-view-sheet" class="presets" role="toolbar" aria-label="相机预设">
        <button
          v-for="view in VEHICLE_VIEWS"
          :key="view.id"
          type="button"
          :class="{ active: activePreset === view.id }"
          @click="selectPreset(view.id)"
        >
          <img
            v-if="angleThumbs[view.id]"
            :src="angleThumbs[view.id]"
            :alt="view.label"
            width="160"
            height="90"
          />
          <span v-else class="angle-mark" :data-angle="view.id" aria-hidden="true">
            <svg viewBox="0 0 80 45" fill="none">
              <rect x="1" y="1" width="78" height="43" rx="3" stroke="#9eb0c0" stroke-width="1.4" />
              <path
                v-if="view.id === 'front45'"
                d="M18 30h44l-6-8-8-3H32l-8 3-6 8Zm8-8 4-9h12l4 9M28 30v4h6v-4m12 0v4h6v-4"
                stroke="#f2f6fa"
                stroke-width="1.8"
                stroke-linejoin="round"
              />
              <path
                v-else-if="view.id === 'side'"
                d="M12 29h56l-7-9-10-4H31l-9 4-10 9Zm16-9 3-8h14l4 8M22 29v5h7v-5m22 0v5h7v-5"
                stroke="#f2f6fa"
                stroke-width="1.8"
                stroke-linejoin="round"
              />
              <path
                v-else-if="view.id === 'rear45'"
                d="M18 30h44l-5-7-9-4H32l-9 4-5 7Zm10-7 3-8h14l4 8M28 30v4h6v-4m12 0v4h6v-4"
                stroke="#f2f6fa"
                stroke-width="1.8"
                stroke-linejoin="round"
              />
              <path
                v-else-if="view.id === 'rear'"
                d="M22 29h36l-4-8H26l-4 8Zm8-8v-6h20v6M30 29v5h6v-5m14 0v5h6v-5"
                stroke="#f2f6fa"
                stroke-width="1.8"
                stroke-linejoin="round"
              />
              <path
                v-else
                d="M22 30h36l-5-9H27l-5 9Zm10-9 3-8h10l3 8M30 30v4h6v-4m14 0v4h6v-4"
                stroke="#f2f6fa"
                stroke-width="1.8"
                stroke-linejoin="round"
              />
            </svg>
          </span>
          <span>{{ view.label }}</span>
        </button>
      </div>
    </div>

    <dialog
      v-if="infoOpen"
      class="model-dialog"
      open
      aria-label="模型授权信息"
      @click.self="infoOpen = false"
    >
      <div class="model-sheet">
        <h2>模型信息</h2>
        <p>{{ VEHICLE_DISCLAIMER }}</p>
        <p>商城中的车型名称、版本、价格和配置属于课程项目演示数据。3D舞台使用第三方授权的2022 Bugatti La Voiture Noire模型，仅用于WebGL技术展示，模型外形不代表实际在售车型。仪表界面为课程项目自行制作，不属于Bugatti或小米官方界面。本项目不代表相关品牌参与、认可或授权销售。</p>
        <p>方向盘 EB 徽标属于第三方模型原始网格（节点 SteeringWheelBadge / 材质 BadgeA），课程配置模式下已运行时隐藏。如仍有极淡残留，属于第三方模型残留几何，不是课程代码新添加的 Logo，也不代表商城商品品牌。</p>
        <ul>
          <li>
            模型名称：
            <a :href="VEHICLE_3D_MANIFEST.sourceUrl" target="_blank" rel="noopener noreferrer">
              2022 Bugatti La Voiture Noire
            </a>
          </li>
          <li>
            作者：
            <a :href="VEHICLE_3D_MANIFEST.authorUrl" target="_blank" rel="noopener noreferrer">OUTPISTON</a>
          </li>
          <li>
            来源链接：
            <a :href="VEHICLE_3D_MANIFEST.sourceUrl" target="_blank" rel="noopener noreferrer">
              Sketchfab
            </a>
          </li>
          <li>
            许可证名称：
            <a :href="VEHICLE_3D_MANIFEST.licenseUrl" target="_blank" rel="noopener noreferrer">CC BY-NC-SA 4.0</a>
          </li>
          <li>是否允许商业使用：不允许。许可证含 NC 条款，仅限非商业课程演示。</li>
          <li>本项目使用目的：课程 WebGL 车辆配置技术展示，不用于商业销售或官方品牌宣传。</li>
        </ul>
        <p class="tiny">已 Draco 压缩。环境贴图：Poly Haven studio_small_09（CC0）。</p>
        <button type="button" class="tool" @click="infoOpen = false">关闭</button>
      </div>
    </dialog>
  </section>
</template>

<style scoped>
.vehicle-3d-stage {
  position: relative;
  display: flex;
  flex-direction: column;
  min-width: 0;
  min-height: 0;
  width: 100%;
  height: 100%;
  overflow: hidden;
  color: #1d2833;
  background: linear-gradient(180deg, #b9cbdc 0%, #8fa8be 48%, #657f98 100%);
}

.stage-brand {
  position: absolute;
  top: 16px;
  left: 16px;
  z-index: 4;
  display: flex;
  align-items: center;
  gap: 8px;
  max-width: min(560px, 58%);
  min-height: 44px;
  padding: 6px 8px 6px 6px;
  background: rgba(255, 255, 255, .42);
  border: 1px solid rgba(255, 255, 255, .46);
  border-radius: 12px;
  backdrop-filter: blur(14px);
}

.brand,
.info-btn,
.tool,
.presets button {
  min-width: 44px;
  min-height: 44px;
  cursor: pointer;
}

.brand {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 0 4px;
  color: #1b2430;
  background: none;
  border: 0;
}

.brand b {
  display: grid;
  width: 26px;
  height: 26px;
  place-items: center;
  border-radius: 6px;
  background: #11161c;
  color: #fff;
  font-size: 14px;
}

.stage-copy { display: grid; gap: 2px; min-width: 0; }
.stage-copy strong {
  overflow: hidden;
  font-size: 12px;
  font-weight: 600;
  line-height: 1.3;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.stage-copy small { color: rgba(27, 36, 48, .62); font-size: 11px; line-height: 1.35; }

.info-btn {
  flex: 0 0 auto;
  padding: 0 10px;
  color: #1b2430;
  font-size: 11px;
  background: rgba(255,255,255,.5);
  border: 1px solid rgba(27,36,48,.16);
  border-radius: 8px;
}

.canvas-wrap { position: relative; flex: 1 1 auto; min-height: 0; }
.vehicle-webgl { display: block; width: 100%; height: 100%; touch-action: none; }

.unsupported {
  position: absolute;
  left: 50%;
  bottom: 22%;
  z-index: 3;
  margin: 0;
  padding: 7px 12px;
  color: rgba(255, 255, 255, .82);
  font-size: 12px;
  background: rgba(18, 24, 32, .62);
  border-radius: 999px;
  transform: translateX(-50%);
}

.stage-dock {
  position: absolute;
  left: 12px;
  right: 12px;
  bottom: 12px;
  z-index: 4;
  display: flex;
  align-items: flex-end;
  gap: 10px;
  pointer-events: none;
}

.stage-tools,
.presets { pointer-events: auto; }

.stage-tools {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  max-width: min(420px, 46%);
  padding: 6px;
  background: rgba(18, 24, 32, .55);
  border: 1px solid rgba(255, 255, 255, .14);
  border-radius: 16px;
  backdrop-filter: blur(12px);
}

.tool {
  padding: 0 12px;
  color: rgba(255, 255, 255, .92);
  font-size: 12px;
  background: rgba(18, 24, 32, .55);
  border: 1px solid rgba(255, 255, 255, .16);
  border-radius: 999px;
}

.tool:hover,
.info-btn:hover,
.brand:hover { filter: brightness(1.12); }

.tool:active,
.info-btn:active,
.brand:active,
.presets button:active {
  transform: translateY(1px);
  filter: brightness(1.18);
}

.tool:focus-visible,
.info-btn:focus-visible,
.brand:focus-visible,
.presets button:focus-visible {
  outline: 2px solid #fff;
  outline-offset: 2px;
}

.tool.active { background: rgba(255,255,255,.18); border-color: #fff; }
.view-toggle { display: none; }
.interior-view-panel { display: none; }

.presets {
  display: flex;
  flex: 1 1 auto;
  justify-content: center;
  gap: 8px;
  min-width: 0;
  overflow-x: auto;
  overflow-y: hidden;
  scrollbar-width: none;
  -webkit-overflow-scrolling: touch;
}

.presets::-webkit-scrollbar { display: none; }

.presets button {
  flex: 0 0 auto;
  width: 88px;
  padding: 0;
  overflow: hidden;
  color: #1b2430;
  background: rgba(18, 24, 32, .62);
  border: 2px solid rgba(255,255,255,.35);
  border-radius: 8px;
}

.presets button.active { border-color: #fff; box-shadow: 0 0 0 1px #fff; }
.presets button:hover { border-color: rgba(255,255,255,.8); }

.presets img,
.angle-mark {
  display: grid;
  place-items: center;
  width: 100%;
  aspect-ratio: 16 / 9;
  object-fit: cover;
  background: #152028;
}

.angle-mark svg {
  width: 86%;
  height: 86%;
}

.presets span {
  display: block;
  padding: 4px 0 6px;
  color: #fff;
  font-size: 11px;
}

.model-dialog {
  position: absolute;
  inset: 0;
  z-index: 8;
  display: grid;
  place-items: center;
  width: 100%;
  height: 100%;
  background: rgba(12,16,22,.42);
  border: 0;
}

.model-sheet {
  width: min(460px, calc(100% - 32px));
  max-height: min(86%, 640px);
  overflow: auto;
  padding: 18px;
  color: #fff;
  background: #171717;
  border-radius: 14px;
}

.model-sheet h2 { margin: 0 0 10px; font-size: 18px; }
.model-sheet p,
.model-sheet li { margin: 0 0 8px; font-size: 13px; line-height: 1.5; color: rgba(255,255,255,.78); }
.model-sheet a { color: #9ec4ff; }
.model-sheet .tiny { color: rgba(255,255,255,.48); font-size: 12px; }

@media (max-width: 1099px) {
  .stage-brand {
    top: 8px;
    left: 8px;
    right: 8px;
    max-width: none;
    min-height: 44px;
    padding: 4px 6px 4px 4px;
  }

  .stage-copy small { display: none; }

  .stage-dock {
    left: 8px;
    right: 8px;
    bottom: 8px;
    align-items: flex-end;
  }

  .stage-tools {
    max-width: min(390px, 46%);
    flex-wrap: wrap;
  }

  .presets {
    flex: 1 1 0;
    justify-content: flex-start;
  }

  .presets button { width: 76px; }
}

@media (max-width: 767px) {
  .view-toggle {
    display: inline-flex;
    align-items: center;
    justify-content: center;
  }

  .stage-tools .extra,
  .presets { display: none; }

  .stage-dock {
    left: 8px;
    right: auto;
    bottom: 8px;
    width: auto;
    max-width: calc(100% - 16px);
  }

  .stage-tools {
    max-width: none;
    flex-wrap: nowrap;
  }

  .interior-view-panel,
  .interior-view-btn {
    display: none;
  }

  .stage-dock.is-open .interior-view-btn {
    display: inline-flex;
  }

  .stage-dock.is-open {
    right: 8px;
    width: auto;
    max-width: none;
    flex-wrap: wrap;
  }

  .stage-dock.is-open .stage-tools {
    max-width: 100%;
    flex-wrap: wrap;
  }

  .stage-dock.is-open .stage-tools .extra,
  .stage-dock.is-open .presets { display: flex; }

  .stage-dock.is-open .presets {
    flex-basis: 100%;
    max-width: none;
    justify-content: flex-start;
  }
}

@media (max-width: 720px) {
  .stage-copy strong { font-size: 11px; }
  .info-btn { padding: 0 8px; font-size: 10px; }
  .presets button { width: 70px; }
}

@media (max-width: 640px) {
  .stage-dock.is-open { flex-wrap: wrap; }
  .stage-dock.is-open .stage-tools { max-width: 100%; }
  .stage-dock.is-open .presets { flex-basis: 100%; max-width: none; }
}

@media (max-height: 500px) {
  .stage-brand { max-width: min(520px, 62%); right: auto; }
  .stage-copy small { display: none; }
  .stage-dock { gap: 6px; flex-wrap: nowrap; }
  .stage-tools { max-width: min(360px, 44%); }
  .presets { flex-basis: auto; max-width: 54%; }
  .presets button { width: 64px; }
  .tool { min-height: 44px; font-size: 11px; }
}
</style>
