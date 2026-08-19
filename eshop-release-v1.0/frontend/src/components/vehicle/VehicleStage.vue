<script setup>
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import VehicleAngleGallery from './VehicleAngleGallery.vue'
import {
  VEHICLE_DISCLAIMER,
  VEHICLE_VIEWS,
  probeVehicleImage,
  vehicleImageSrc,
  vehiclePlaceholderSrc,
} from '../../utils/vehicle'

const props = defineProps({
  folder: { type: String, required: true },
  colorCode: { type: String, default: '' },
  colorName: { type: String, default: '' },
  productName: { type: String, default: '课程演示车型' },
})

const emit = defineEmits(['back'])

const viewIndex = ref(0)
const displaySrc = ref('')
const loading = ref(true)
const failed = ref(false)
const fading = ref(false)
const fullscreen = ref(false)
const dragging = ref(false)
const reduceMotion = ref(false)

let decodeToken = 0
let dragStartX = 0
let dragAccX = 0
let dragFrame = 0
const prefetched = new Set()
let prefetchInflight = 0

const availableViews = ref({})
const currentView = computed(() => VEHICLE_VIEWS[viewIndex.value] || VEHICLE_VIEWS[0])
const currentSrc = computed(() => {
  if (!props.folder || !props.colorCode) return ''
  return vehicleImageSrc(props.folder, props.colorCode, currentView.value.id)
})
const durationMs = computed(() => (reduceMotion.value ? 0 : 220))
const viewReady = computed(() => availableViews.value[currentView.value.id] === true)

const decodeExactSource = async (url) => {
  if (!url) throw new Error('empty')
  const image = new Image()
  image.decoding = 'async'
  image.src = url
  if (image.decode) await image.decode()
  else await new Promise((resolve, reject) => {
    image.onload = resolve
    image.onerror = reject
  })
  return url
}

const refreshAvailability = async () => {
  if (!props.folder || !props.colorCode) {
    availableViews.value = {}
    return
  }
  const entries = await Promise.all(VEHICLE_VIEWS.map(async (view) => {
    const src = vehicleImageSrc(props.folder, props.colorCode, view.id)
    return [view.id, await probeVehicleImage(src)]
  }))
  availableViews.value = Object.fromEntries(entries)
  if (!availableViews.value[currentView.value.id]) {
    const firstReady = VEHICLE_VIEWS.findIndex((view) => availableViews.value[view.id])
    if (firstReady >= 0) viewIndex.value = firstReady
  }
}

const prefetch = (src) => {
  if (!src || prefetched.has(src) || prefetchInflight >= 2) return
  prefetchInflight += 1
  const image = new Image()
  image.decoding = 'async'
  image.src = src
  const done = () => {
    prefetchInflight = Math.max(0, prefetchInflight - 1)
    prefetched.add(src)
  }
  if (image.decode) image.decode().then(done).catch(done)
  else {
    image.onload = done
    image.onerror = done
  }
}

const prefetchNeighbors = () => {
  const prev = VEHICLE_VIEWS[(viewIndex.value + VEHICLE_VIEWS.length - 1) % VEHICLE_VIEWS.length]
  const next = VEHICLE_VIEWS[(viewIndex.value + 1) % VEHICLE_VIEWS.length]
  const idle = window.requestIdleCallback || ((fn) => window.setTimeout(fn, 180))
  idle(() => {
    if (availableViews.value[prev.id]) prefetch(vehicleImageSrc(props.folder, props.colorCode, prev.id))
    if (availableViews.value[next.id]) prefetch(vehicleImageSrc(props.folder, props.colorCode, next.id))
  })
}

const showCurrent = async () => {
  const token = ++decodeToken
  failed.value = false
  if (!displaySrc.value) loading.value = true
  const exact = currentSrc.value
  if (!exact) {
    if (token !== decodeToken) return
    failed.value = true
    loading.value = false
    displaySrc.value = ''
    return
  }
  let src = ''
  try {
    src = await decodeExactSource(exact)
  } catch {
    src = ''
  }
  if (token !== decodeToken) return
  if (!src) {
    failed.value = true
    loading.value = false
    displaySrc.value = ''
    return
  }
  if (displaySrc.value === src) {
    loading.value = false
    prefetchNeighbors()
    return
  }
  if (!reduceMotion.value && displaySrc.value) fading.value = true
  displaySrc.value = src
  loading.value = false
  window.requestAnimationFrame(() => {
    if (token !== decodeToken) return
    fading.value = false
  })
  prefetchNeighbors()
}

const changeView = (delta) => {
  const total = VEHICLE_VIEWS.length
  for (let step = 1; step <= total; step += 1) {
    const next = (viewIndex.value + delta * step + total * 8) % total
    if (availableViews.value[VEHICLE_VIEWS[next].id]) {
      viewIndex.value = next
      return
    }
  }
}

const selectView = (index) => {
  if (!availableViews.value[VEHICLE_VIEWS[index]?.id]) return
  viewIndex.value = index
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

const onPointerDown = (event) => {
  if (reduceMotion.value || event.button !== 0) return
  dragging.value = true
  dragStartX = event.clientX
  dragAccX = 0
  event.currentTarget.setPointerCapture?.(event.pointerId)
}

const flushDrag = () => {
  dragFrame = 0
  if (Math.abs(dragAccX) < 56) return
  changeView(dragAccX > 0 ? -1 : 1)
  dragAccX = 0
}

const onPointerMove = (event) => {
  if (!dragging.value) return
  dragAccX += event.clientX - dragStartX
  dragStartX = event.clientX
  if (!dragFrame) dragFrame = window.requestAnimationFrame(flushDrag)
}

const endDrag = () => {
  dragging.value = false
  if (dragFrame) {
    window.cancelAnimationFrame(dragFrame)
    dragFrame = 0
  }
}

watch(
  () => [props.folder, props.colorCode],
  refreshAvailability,
  { immediate: true },
)

watch(
  () => [props.folder, props.colorCode, viewIndex.value],
  showCurrent,
  { immediate: true },
)

onMounted(() => {
  const motion = window.matchMedia('(prefers-reduced-motion: reduce)')
  const onMotion = () => {
    reduceMotion.value = motion.matches
  }
  onMotion()
  motion.addEventListener?.('change', onMotion)
  const onFs = () => {
    fullscreen.value = Boolean(document.fullscreenElement)
  }
  document.addEventListener('fullscreenchange', onFs)
  onUnmounted(() => {
    motion.removeEventListener?.('change', onMotion)
    document.removeEventListener('fullscreenchange', onFs)
  })
})

onUnmounted(() => {
  decodeToken += 1
  if (dragFrame) window.cancelAnimationFrame(dragFrame)
})
</script>

<template>
  <section class="vehicle-stage" aria-label="车辆展示舞台">
    <header class="stage-brand">
      <button type="button" class="brand" @click="emit('back')">
        <b>E</b>
        <span>E-Shop</span>
      </button>
      <small>课程演示</small>
    </header>

    <div
      class="canvas"
      :class="{ dragging, loading }"
      :style="{ '--duration': `${durationMs}ms` }"
      @pointerdown="onPointerDown"
      @pointermove="onPointerMove"
      @pointerup="endDrag"
      @pointercancel="endDrag"
    >
      <img
        v-if="displaySrc && !failed"
        class="backdrop"
        :src="displaySrc"
        alt=""
        aria-hidden="true"
        draggable="false"
      />
      <div v-if="loading && !displaySrc" class="skeleton" aria-hidden="true" />
      <img
        v-if="displaySrc && !failed"
        class="hero"
        :src="displaySrc"
        :alt="`${productName} ${currentView.label} ${colorName}`"
        width="1920"
        height="1080"
        decoding="async"
        fetchpriority="high"
        draggable="false"
        :class="{ fading }"
        @error="failed = true"
      />
      <div v-if="failed || (!loading && !viewReady && !displaySrc)" class="empty">
        <img :src="vehiclePlaceholderSrc" alt="课程演示车辆剪影" width="640" height="360" />
        <p>该视角素材制作中</p>
        <button v-if="failed" type="button" @click="showCurrent">重新加载</button>
      </div>
      <span class="vignette" aria-hidden="true" />
      <span class="floor-shadow" aria-hidden="true" />
    </div>

    <div class="stage-tools">
      <button type="button" class="tool active">2D</button>
      <button type="button" class="tool" @click="changeView(1)">多角度</button>
      <button type="button" class="tool" @click="toggleFullscreen">{{ fullscreen ? '退出全屏' : '全屏' }}</button>
    </div>

    <p class="disclaimer">{{ VEHICLE_DISCLAIMER }}</p>

    <VehicleAngleGallery
      :folder="folder"
      :color-code="colorCode"
      :active-id="currentView.id"
      :available-views="availableViews"
      @select="selectView"
    />
  </section>
</template>

<style scoped>
.vehicle-stage {
  position: relative;
  display: flex;
  flex-direction: column;
  min-width: 0;
  min-height: 0;
  width: 100%;
  height: 100%;
  overflow: hidden;
  color: #e8eef4;
  background:
    radial-gradient(ellipse 68% 52% at 46% 38%, #3a5166 0%, transparent 58%),
    linear-gradient(180deg, #243140 0%, #1a2430 46%, #10161c 100%);
}

.stage-brand {
  position: absolute;
  top: 0;
  left: 0;
  z-index: 3;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 18px 28px 0;
  pointer-events: none;
}

.brand {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 0;
  color: #f3f6f9;
  background: none;
  border: 0;
  cursor: pointer;
  pointer-events: auto;
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

.brand span { font-size: 15px; letter-spacing: .04em; }
.stage-brand small { color: rgba(243, 246, 249, .52); font-size: 12px; }

.canvas {
  position: relative;
  flex: 1 1 auto;
  min-height: 0;
  overflow: hidden;
  touch-action: pan-y;
  user-select: none;
}

.skeleton,
.empty,
.canvas .backdrop {
  position: absolute;
  inset: 0;
  display: block;
  width: 100%;
  height: 100%;
  max-width: none;
}

.canvas .backdrop {
  object-fit: cover;
  object-position: 46% 40%;
  filter: blur(80px) saturate(1.04) brightness(.62);
  transform: scale(1.55);
  opacity: .9;
  pointer-events: none;
}

.canvas .hero {
  position: absolute;
  left: 5%;
  top: 8%;
  display: block;
  width: 90%;
  max-width: none;
  height: auto;
  object-fit: contain;
  object-position: center 46%;
  filter: drop-shadow(0 32px 36px rgba(8, 12, 18, .4));
  mask-image:
    linear-gradient(to right, transparent 0%, #000 10%, #000 90%, transparent 100%),
    linear-gradient(to bottom, transparent 0%, #000 14%, #000 84%, transparent 100%);
  mask-composite: intersect;
  -webkit-mask-image:
    linear-gradient(to right, transparent 0%, #000 10%, #000 90%, transparent 100%),
    linear-gradient(to bottom, transparent 0%, #000 14%, #000 84%, transparent 100%);
  -webkit-mask-composite: source-in;
  opacity: 1;
  transform: translateY(-10%);
  transition: opacity var(--duration) ease, transform var(--duration) ease;
}

.canvas .hero.fading {
  opacity: 0.08;
  transform: translateY(-4%) scale(0.985);
}

.skeleton {
  border-radius: 0;
  background: linear-gradient(90deg, rgba(255,255,255,.06), rgba(255,255,255,.16), rgba(255,255,255,.06));
  background-size: 200% 100%;
  animation: shimmer 1.2s linear infinite;
}

.empty {
  display: grid;
  place-items: center;
  align-content: center;
  gap: 10px;
  color: rgba(255,255,255,.62);
  font-size: 13px;
}

.empty img {
  width: min(52%, 420px);
  height: auto;
  opacity: .55;
}

.empty button {
  padding: 7px 14px;
  color: #fff;
  background: rgba(18, 24, 32, .55);
  border: 1px solid rgba(255, 255, 255, .18);
  border-radius: 999px;
  cursor: pointer;
}

.vignette {
  position: absolute;
  inset: 0;
  pointer-events: none;
  background:
    radial-gradient(ellipse 78% 70% at 46% 40%, transparent 46%, rgba(12, 16, 22, .42) 100%);
}

.floor-shadow {
  position: absolute;
  left: 16%;
  right: 16%;
  bottom: 14%;
  height: 56px;
  background: radial-gradient(ellipse, rgba(6, 10, 16, .42), transparent 72%);
  pointer-events: none;
}

.stage-tools {
  position: absolute;
  left: 28px;
  bottom: 126px;
  z-index: 3;
  display: flex;
  gap: 8px;
}

.tool {
  padding: 7px 13px;
  color: rgba(255, 255, 255, .9);
  font-size: 12px;
  line-height: 1;
  background: rgba(18, 24, 32, .55);
  border: 1px solid rgba(255, 255, 255, .16);
  border-radius: 999px;
  cursor: pointer;
  transition: background .16s ease, border-color .16s ease, color .16s ease;
}

.tool:hover,
.tool:focus-visible {
  color: #fff;
  background: rgba(18, 24, 32, .78);
  border-color: rgba(255, 255, 255, .38);
}

.tool:focus-visible {
  outline: 2px solid rgba(255, 255, 255, .62);
  outline-offset: 2px;
}

.tool.active { background: rgba(18, 24, 32, .78); }

.disclaimer {
  position: absolute;
  left: 28px;
  bottom: 100px;
  z-index: 3;
  margin: 0;
  color: rgba(255, 255, 255, .62);
  font-size: 11px;
  letter-spacing: .01em;
}

@keyframes shimmer {
  from { background-position: 200% 0; }
  to { background-position: -200% 0; }
}

@media (prefers-reduced-motion: reduce) {
  .canvas .hero, .skeleton { animation: none; transition: none; }
}

@media (max-width: 1099px) {
  .canvas .hero {
    width: 94%;
    left: 3%;
    top: 10%;
    height: auto;
  }

  .stage-tools {
    top: 56px;
    left: 10px;
    right: auto;
    bottom: auto;
    width: fit-content;
    max-width: calc(100% - 20px);
    flex-wrap: wrap;
  }

  .disclaimer { display: none; }
}

@media (max-width: 720px) {
  .stage-tools {
    top: 50px;
    bottom: auto;
  }
  .tool { padding: 5px 9px; font-size: 11px; }
}
</style>
