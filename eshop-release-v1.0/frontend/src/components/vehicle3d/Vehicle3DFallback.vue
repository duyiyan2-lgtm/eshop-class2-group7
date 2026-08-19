<script setup>
import VehicleStage from '../vehicle/VehicleStage.vue'

defineProps({
  folder: { type: String, required: true },
  colorCode: { type: String, default: '' },
  colorName: { type: String, default: '' },
  productName: { type: String, default: '课程演示车型' },
  reason: { type: String, default: '当前设备无法显示三维预览，已切换到多角度图片。' },
})

const emit = defineEmits(['back'])
</script>

<template>
  <div class="vehicle-3d-fallback">
    <p class="note">{{ reason }}</p>
    <VehicleStage
      :folder="folder"
      :color-code="colorCode"
      :color-name="colorName"
      :product-name="productName"
      @back="emit('back')"
    />
  </div>
</template>

<style scoped>
.vehicle-3d-fallback {
  position: relative;
  min-width: 0;
  height: 100%;
}

.note {
  position: absolute;
  top: 52px;
  left: 12px;
  right: 12px;
  max-width: calc(100% - 24px);
  z-index: 5;
  margin: 0;
  padding: 6px 10px;
  color: rgba(255, 255, 255, .78);
  font-size: 12px;
  background: rgba(18, 24, 32, .55);
  border: 1px solid rgba(255, 255, 255, .14);
  border-radius: 999px;
}
</style>
