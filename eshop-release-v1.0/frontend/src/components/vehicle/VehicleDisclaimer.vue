<script setup>
import { VEHICLE_DISCLAIMER } from '../../utils/vehicle'

defineProps({
  compact: { type: Boolean, default: false },
})
</script>

<template>
  <p class="vehicle-disclaimer" :class="{ compact }">{{ VEHICLE_DISCLAIMER }}</p>
</template>

<style scoped>
.vehicle-disclaimer {
  margin: 0;
  padding: 8px 12px;
  color: #8a5a12;
  font-size: 13px;
  line-height: 1.5;
  background: #fff6e8;
  border: 1px solid #f3d7a6;
  border-radius: 6px;
}

.vehicle-disclaimer.compact {
  padding: 4px 10px;
  font-size: 12px;
}
</style>
