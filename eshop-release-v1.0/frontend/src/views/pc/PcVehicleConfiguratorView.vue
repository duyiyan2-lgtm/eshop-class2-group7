<script setup>
import { defineAsyncComponent, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import VehicleConfiguratorPanel from '../../components/vehicle/VehicleConfiguratorPanel.vue'
import { useVehicleConfigurator } from '../../composables/useVehicleConfigurator'

const Vehicle3DStage = defineAsyncComponent(() => (
  import('../../components/vehicle3d/Vehicle3DStage.vue')
))
const VehicleConfigSummary = defineAsyncComponent(() => (
  import('../../components/vehicle/VehicleConfigSummary.vue')
))

const router = useRouter()
const {
  loading,
  quoting,
  submitting,
  errorMessage,
  configurator,
  selectedSkuId,
  selectedByGroup,
  quote,
  activeTab,
  drawerVisible,
  folder,
  selectedColor,
  selectedWheel,
  selectedInterior,
  selectedPacks,
  isLastTab,
  nextLabel,
  canPurchase,
  listRouteName,
  loadConfigurator,
  selectSku,
  selectSingle,
  toggleMulti,
  goNext,
  resetConfig,
  addConfiguredVehicle,
  buyNow,
} = useVehicleConfigurator()

onMounted(() => {
  document.documentElement.classList.add('vehicle-immersive-lock')
})

onUnmounted(() => {
  document.documentElement.classList.remove('vehicle-immersive-lock')
})
</script>

<template>
  <div class="vehicle-configurator" :class="{ loading }">
    <div v-if="errorMessage" class="boot-error">
      <p>{{ errorMessage }}</p>
      <button type="button" @click="loadConfigurator">重新加载</button>
    </div>

    <template v-else-if="configurator">
      <Vehicle3DStage
        :folder="folder"
        :color-code="selectedColor?.code || ''"
        :color-name="selectedColor?.name || ''"
        :product-name="configurator.productName"
        :wheel-code="selectedWheel?.code || 'W19'"
        :interior-code="selectedInterior?.code || 'BLACK'"
        :pack-codes="selectedPacks.map((item) => item.code)"
        @back="router.push({ name: listRouteName })"
      />
      <VehicleConfiguratorPanel
        :folder="folder"
        :configurator="configurator"
        :selected-sku-id="selectedSkuId"
        :selected-by-group="selectedByGroup"
        :active-tab="activeTab"
        :quote="quote"
        :quoting="quoting"
        :submitting="submitting"
        :can-purchase="canPurchase"
        :next-label="nextLabel"
        :is-last-tab="isLastTab"
        @update:active-tab="activeTab = $event"
        @select-sku="selectSku"
        @select-option="selectSingle"
        @toggle-option="toggleMulti"
        @next="goNext"
        @summary="drawerVisible = true"
        @reset="resetConfig"
        @add-cart="addConfiguredVehicle"
        @buy-now="buyNow"
      />
    </template>

    <VehicleConfigSummary
      v-if="drawerVisible"
      v-model="drawerVisible"
      :product-name="configurator?.productName"
      :quote="quote"
      @reset="resetConfig"
      @confirm="drawerVisible = false"
    />
  </div>
</template>

<style scoped>
.vehicle-configurator {
  width: 100%;
  max-width: 100vw;
  height: 100dvh;
  max-height: 100dvh;
  display: grid;
  grid-template-columns: minmax(0, 1fr) clamp(430px, 30vw, 560px);
  grid-template-rows: minmax(0, 1fr);
  overflow: hidden;
  background: #151515;
}

.boot-error {
  display: grid;
  place-content: center;
  gap: 12px;
  color: #fff;
}

.boot-error button {
  justify-self: center;
  padding: 8px 14px;
  color: #fff;
  background: #2f6bff;
  border: 0;
  border-radius: 8px;
}

@media (max-width: 1099px) {
  .vehicle-configurator {
    grid-template-columns: minmax(0, 1fr);
    grid-template-rows: minmax(260px, 44dvh) minmax(0, 1fr);
  }
}

@media (max-width: 720px) {
  .vehicle-configurator {
    grid-template-rows: minmax(220px, 40dvh) minmax(0, 1fr);
  }
}

@media (max-height: 500px) and (min-width: 700px) {
  .vehicle-configurator {
    grid-template-columns: minmax(0, 1fr) minmax(300px, 36vw);
    grid-template-rows: minmax(0, 1fr);
  }
}
</style>
