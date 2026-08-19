<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import VehicleOptionCard from './VehicleOptionCard.vue'
import VehiclePriceBar from './VehiclePriceBar.vue'
import { PANEL_TABS } from '../../utils/vehicle'
import { formatMoney } from '../../utils/shop'

const props = defineProps({
  folder: { type: String, default: 'su7' },
  configurator: { type: Object, required: true },
  selectedSkuId: { type: [Number, String], default: undefined },
  selectedByGroup: { type: Object, default: () => ({}) },
  activeTab: { type: String, default: 'COLOR' },
  quote: { type: Object, default: null },
  quoting: { type: Boolean, default: false },
  submitting: { type: Boolean, default: false },
  canPurchase: { type: Boolean, default: false },
  nextLabel: { type: String, default: '下一步' },
  isLastTab: { type: Boolean, default: false },
})

const emit = defineEmits([
  'update:activeTab',
  'select-sku',
  'select-option',
  'toggle-option',
  'next',
  'summary',
  'reset',
  'add-cart',
  'buy-now',
])

const selectedSku = computed(() => (
  props.configurator.skus.find((sku) => sku.id === props.selectedSkuId) || null
))

const title = computed(() => {
  const raw = props.configurator.productName || '课程演示车型'
  const short = raw.replace('（课程演示）', '').replace('新一代', '').replace('小米', '').trim()
  return `${short} ${selectedSku.value?.versionName || ''}`.trim()
})

const activeGroup = computed(() => (
  props.configurator.groups.find((group) => group.code === props.activeTab) || null
))

const isActive = (group, value) => {
  if (group.selectionType === 'MULTI') {
    return (props.selectedByGroup[group.id] || []).includes(value.id)
  }
  return props.selectedByGroup[group.id] === value.id
}

const isAvailable = (valueId) => {
  const rule = props.configurator.rules.find((item) => (
    item.skuId === props.selectedSkuId && item.optionValueId === valueId
  ))
  return !rule || rule.available
}

const skuMenuOpen = ref(false)
const skuSelectEl = ref(null)

const pickSku = (sku) => {
  skuMenuOpen.value = false
  emit('select-sku', sku)
}

const onDocumentPointer = (event) => {
  if (!skuMenuOpen.value) return
  if (skuSelectEl.value && !skuSelectEl.value.contains(event.target)) {
    skuMenuOpen.value = false
  }
}

const onDocumentKey = (event) => {
  if (event.key === 'Escape') skuMenuOpen.value = false
}

onMounted(() => {
  document.addEventListener('pointerdown', onDocumentPointer)
  document.addEventListener('keydown', onDocumentKey)
})

onUnmounted(() => {
  document.removeEventListener('pointerdown', onDocumentPointer)
  document.removeEventListener('keydown', onDocumentKey)
})

const isIncluded = (valueId) => {
  const rule = props.configurator.rules.find((item) => (
    item.skuId === props.selectedSkuId && item.optionValueId === valueId
  ))
  return Boolean(rule?.included)
}
</script>

<template>
  <aside class="configurator-panel">
    <header class="panel-head">
      <div>
        <p>COURSE DEMO</p>
        <h1>课程车辆配置演示</h1>
        <small class="model-note">商城商品数据 · {{ title }}。3D 舞台为第三方授权外形模型技术演示，不代表实际销售车辆，亦不代表 Bugatti 或小米官方参与。</small>
      </div>
      <div ref="skuSelectEl" class="sku-select">
        <span>版本</span>
        <button
          type="button"
          class="sku-trigger"
          :aria-expanded="skuMenuOpen"
          aria-haspopup="listbox"
          @click="skuMenuOpen = !skuMenuOpen"
        >
          <em>
            <strong>{{ selectedSku?.versionName || '选择版本' }}</strong>
            <small v-if="selectedSku">{{ formatMoney(selectedSku.basePrice) }}</small>
          </em>
          <i aria-hidden="true">{{ skuMenuOpen ? '▴' : '▾' }}</i>
        </button>
        <div v-if="skuMenuOpen" class="sku-menu" role="listbox">
          <button
            v-for="sku in configurator.skus"
            :key="sku.id"
            type="button"
            role="option"
            :aria-selected="sku.id === selectedSkuId"
            :class="{ active: sku.id === selectedSkuId }"
            @click="pickSku(sku)"
          >
            <span>
              <strong>{{ sku.versionName }}</strong>
              <small>{{ sku.driveType }}</small>
            </span>
            <small>{{ formatMoney(sku.basePrice) }}</small>
          </button>
        </div>
      </div>
    </header>

    <nav class="tabs" aria-label="配置分组">
      <button
        v-for="tab in PANEL_TABS"
        :key="tab.code"
        type="button"
        :class="{ active: activeTab === tab.code }"
        @click="emit('update:activeTab', tab.code)"
      >
        {{ tab.label }}
      </button>
    </nav>

    <div class="panel-scroll">
      <section v-if="activeGroup">
        <div class="cards">
          <VehicleOptionCard
            v-for="value in activeGroup.values"
            :key="value.id"
            :folder="folder"
            :group-code="activeGroup.code"
            :value="value"
            :active="isActive(activeGroup, value)"
            :disabled="!isAvailable(value.id)"
            :reason="isAvailable(value.id) ? '' : '当前版本不提供该选项'"
            :included="isIncluded(value.id)"
            @select="activeGroup.selectionType === 'MULTI'
              ? emit('toggle-option', activeGroup, value)
              : emit('select-option', activeGroup, value)"
          />
        </div>
      </section>
    </div>

    <VehiclePriceBar
      :quote="quote"
      :base-price="selectedSku?.basePrice"
      :quoting="quoting"
      :submitting="submitting"
      :can-purchase="canPurchase"
      :next-label="nextLabel"
      :is-last-tab="isLastTab"
      @next="emit('next')"
      @summary="emit('summary')"
      @reset="emit('reset')"
      @add-cart="emit('add-cart')"
      @buy-now="emit('buy-now')"
    />
  </aside>
</template>

<style scoped>
.configurator-panel {
  position: relative;
  display: flex;
  flex-direction: column;
  width: 100%;
  height: 100%;
  min-width: 0;
  min-height: 0;
  overflow: hidden;
  color: #fff;
  background: #151515;
}

.panel-head {
  flex: 0 0 auto;
  display: flex;
  justify-content: space-between;
  gap: 12px;
  padding: 22px 22px 8px;
}

.panel-head p {
  margin: 0 0 4px;
  color: rgba(255,255,255,.46);
  font-size: 12px;
  letter-spacing: .16em;
}

.panel-head h1 {
  margin: 0;
  font-size: 26px;
  font-weight: 600;
  line-height: 1.25;
}

.model-note {
  display: block;
  margin-top: 4px;
  color: rgba(255,255,255,.42);
  font-size: 11px;
  letter-spacing: 0;
  line-height: 1.4;
}

.sku-select {
  position: relative;
  display: grid;
  gap: 6px;
  min-width: 168px;
  color: rgba(255,255,255,.46);
  font-size: 11px;
  letter-spacing: .08em;
}

.sku-trigger {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  min-width: 176px;
  min-height: 44px;
  padding: 9px 12px;
  color: #fff;
  text-align: left;
  background: #1c1c1c;
  border: 1px solid #3a3a3a;
  border-radius: 10px;
  cursor: pointer;
}

.sku-trigger em {
  display: grid;
  gap: 2px;
  font-style: normal;
}

.sku-trigger strong {
  font-size: 15px;
  font-weight: 500;
}

.sku-trigger small,
.sku-menu small {
  color: rgba(255,255,255,.46);
  font-size: 11px;
  letter-spacing: 0;
}

.sku-trigger i { color: rgba(255,255,255,.55); font-style: normal; }

.sku-trigger:hover,
.sku-trigger:focus-visible {
  border-color: #fff;
  background: #222;
}

.sku-trigger:focus-visible {
  outline: 2px solid rgba(255,255,255,.55);
  outline-offset: 2px;
}

.sku-menu {
  position: absolute;
  top: calc(100% + 6px);
  right: 0;
  z-index: 4;
  display: grid;
  min-width: 220px;
  padding: 6px;
  background: #161616;
  border: 1px solid #333;
  border-radius: 12px;
  box-shadow: 0 16px 32px rgba(0,0,0,.36);
}

.sku-menu button {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 12px;
  color: #fff;
  background: transparent;
  border: 0;
  border-radius: 8px;
  cursor: pointer;
}

.sku-menu button span { display: grid; gap: 2px; text-align: left; }
.sku-menu button.active,
.sku-menu button:hover,
.sku-menu button:focus-visible { background: #2a2a2a; }

.tabs {
  flex: 0 0 auto;
  display: flex;
  gap: 18px;
  padding: 8px 22px 0;
  border-bottom: 1px solid #2a2a2a;
}

.tabs button {
  min-height: 44px;
  padding: 10px 0 12px;
  color: rgba(255,255,255,.42);
  font-size: 15px;
  background: none;
  border: 0;
  border-bottom: 2px solid transparent;
  cursor: pointer;
}

.tabs button:hover { color: rgba(255,255,255,.78); }
.tabs button:focus-visible {
  outline: 2px solid #fff;
  outline-offset: 2px;
}
.tabs button:active { color: #fff; }

.tabs button.active {
  color: #fff;
  border-bottom-color: #fff;
}

.panel-scroll {
  flex: 1;
  min-height: 0;
  padding: 18px 22px 24px;
  overflow: auto;
  scroll-padding-bottom: 16px;
  scrollbar-width: none;
}

.panel-scroll::-webkit-scrollbar { width: 0; height: 0; }

.cards {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
  padding-bottom: 16px;
}

@media (min-width: 1920px) {
  .panel-head h1 { font-size: 28px; }
  .tabs button { font-size: 16px; }
  .sku-trigger strong { font-size: 16px; }
}

@media (max-width: 1099px) {
  .panel-head {
    flex-wrap: wrap;
    gap: 10px;
    padding: 14px 14px 6px;
  }

  .panel-head h1 { font-size: 22px; }

  .sku-select {
    min-width: 160px;
    flex: 1 1 180px;
  }

  .sku-trigger {
    min-width: 0;
    width: 100%;
  }

  .sku-menu {
    left: 0;
    right: 0;
    min-width: 0;
  }

  .tabs {
    gap: 14px;
    padding: 4px 14px 0;
    overflow-x: auto;
    scrollbar-width: none;
  }

  .tabs::-webkit-scrollbar { display: none; }

  .tabs button {
    flex: 0 0 auto;
    font-size: 14px;
    padding: 8px 0 10px;
  }

  .panel-scroll { padding: 12px 14px 24px; }

  .cards { gap: 8px; }
}

@media (max-width: 520px) {
  .cards { grid-template-columns: 1fr; }
}

@media (max-width: 720px) {
  .panel-head { padding: 12px 12px 4px; }
  .panel-head h1 { font-size: 20px; }
  .sku-select { flex-basis: 100%; width: 100%; }
  .tabs { padding: 2px 12px 0; gap: 12px; }
  .panel-scroll { padding: 10px 12px 24px; }
}

@media (max-height: 500px) {
  .panel-head { padding: 8px 12px 2px; gap: 6px; }
  .panel-head h1 { font-size: 18px; }
  .model-note { display: none; }
  .tabs { padding: 0 12px; }
  .tabs button { padding: 6px 0 8px; font-size: 13px; }
  .panel-scroll { padding: 8px 12px 16px; }
}
</style>
