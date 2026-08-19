<script setup>
import { computed } from 'vue'
import { formatMoney } from '../../utils/shop'
import { VEHICLE_COLOR_MATERIAL } from '../../utils/vehicle'
import { VEHICLE_3D_MANIFEST } from '../vehicle3d/vehicleModelManifest'

const props = defineProps({
  folder: { type: String, default: 'su7' },
  groupCode: { type: String, default: '' },
  value: { type: Object, required: true },
  active: { type: Boolean, default: false },
  disabled: { type: Boolean, default: false },
  reason: { type: String, default: '' },
  included: { type: Boolean, default: false },
})

const emit = defineEmits(['select'])

const paintHex = computed(() => {
  if (props.groupCode !== 'COLOR') return ''
  return VEHICLE_3D_MANIFEST.paints[props.value.code] || props.value.colorHex || '#d8d8d8'
})

const wheelSpec = computed(() => (
  props.groupCode === 'WHEEL'
    ? (VEHICLE_3D_MANIFEST.wheels[props.value.code] || VEHICLE_3D_MANIFEST.wheels.W19)
    : null
))

const interiorHex = computed(() => (
  props.groupCode === 'INTERIOR'
    ? (VEHICLE_3D_MANIFEST.interiors[props.value.code] || '#2a2a2a')
    : ''
))

const visualKind = computed(() => {
  if (props.groupCode === 'COLOR') return 'paint'
  if (props.groupCode === 'WHEEL') return 'wheel'
  if (props.groupCode === 'INTERIOR') return 'interior'
  return 'pack'
})

const material = computed(() => {
  if (props.groupCode === 'COLOR') return VEHICLE_COLOR_MATERIAL[props.value.code] || '金属漆'
  if (props.groupCode === 'WHEEL') return '演示轮毂'
  if (props.groupCode === 'INTERIOR') return '演示内饰'
  return props.value.driveType || ''
})

const priceText = computed(() => {
  if (props.disabled) return props.reason || '当前版本不可选'
  if (props.included) return '已含'
  if (Number(props.value.priceDelta) > 0) return `+${formatMoney(props.value.priceDelta)}`
  return '包含'
})
</script>

<template>
  <button
    type="button"
    class="option-card"
    :class="{ active, disabled, [visualKind]: true }"
    :disabled="disabled"
    :title="reason"
    @click="emit('select')"
  >
    <div
      class="thumb"
      :class="visualKind"
      :style="visualKind === 'paint' ? { '--paint': paintHex } : visualKind === 'interior' ? { '--paint': interiorHex } : visualKind === 'wheel' ? { '--rim': wheelSpec.rim } : null"
    >
      <i v-if="visualKind === 'paint'" class="paint-orb" />
      <i v-else-if="visualKind === 'wheel'" class="wheel-orb" />
      <i v-else-if="visualKind === 'interior'" class="interior-swatch" />
      <span v-else class="pack-mark">选装</span>
    </div>
    <div class="copy">
      <strong>{{ value.name }}</strong>
      <small v-if="material">{{ material }}</small>
      <em>{{ priceText }}</em>
    </div>
  </button>
</template>

<style scoped>
.option-card {
  display: grid;
  gap: 8px;
  padding: 0 0 10px;
  overflow: hidden;
  text-align: left;
  color: #fff;
  background: #1b1b1b;
  border: 2px solid transparent;
  border-radius: 10px;
  cursor: pointer;
}

.option-card:hover {
  border-color: #5a5a5a;
  background: #202020;
}

.option-card:focus-visible {
  outline: 2px solid #fff;
  outline-offset: 2px;
}

.option-card:active {
  background: #262626;
  transform: translateY(1px);
}

.option-card.active {
  border-color: #fff;
  background: #242424;
}
.option-card.disabled { opacity: .38; cursor: not-allowed; }

.thumb {
  position: relative;
  display: grid;
  place-items: center;
  aspect-ratio: 2.4 / 1;
  overflow: hidden;
  background: #101010;
}

.paint-orb,
.wheel-orb {
  width: 54px;
  height: 54px;
  border-radius: 50%;
}

.paint-orb {
  background:
    radial-gradient(circle at 30% 28%, rgba(255,255,255,.72), transparent 36%),
    radial-gradient(circle at 70% 78%, rgba(0,0,0,.28), transparent 42%),
    linear-gradient(160deg, color-mix(in srgb, var(--paint) 78%, #fff), var(--paint) 46%, color-mix(in srgb, var(--paint) 70%, #111));
  box-shadow: inset 0 0 0 1px rgba(255,255,255,.18), 0 8px 16px rgba(0,0,0,.28);
}

.wheel-orb {
  background:
    radial-gradient(circle at 50% 50%, #111 0 28%, transparent 29%),
    repeating-conic-gradient(from 0deg, var(--rim, #c8ccd1) 0 12deg, #2a2e33 12deg 24deg);
  box-shadow: inset 0 0 0 6px #1a1d21, 0 8px 16px rgba(0,0,0,.28);
}

.interior-swatch {
  width: 78%;
  height: 58%;
  border-radius: 8px;
  background:
    linear-gradient(180deg, rgba(255,255,255,.16), transparent 40%),
    var(--paint, #2a2a2a);
  box-shadow: inset 0 0 0 1px rgba(255,255,255,.12);
}

.pack-mark {
  color: rgba(255,255,255,.62);
  font-size: 13px;
  letter-spacing: .12em;
}

.copy {
  display: grid;
  gap: 2px;
  padding: 0 10px;
}

.copy strong { font-size: 15px; font-weight: 500; }
.copy small,
.copy em { color: rgba(255,255,255,.55); font-size: 12px; font-style: normal; }
</style>
