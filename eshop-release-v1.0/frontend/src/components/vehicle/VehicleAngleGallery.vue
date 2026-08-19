<script setup>
import { VEHICLE_VIEWS, vehicleImageSrc } from '../../utils/vehicle'

const props = defineProps({
  folder: { type: String, required: true },
  colorCode: { type: String, default: '' },
  activeId: { type: String, default: 'front45' },
  availableViews: { type: Object, default: () => ({}) },
})

const emit = defineEmits(['select'])

const isReady = (viewId) => props.availableViews[viewId] === true
</script>

<template>
  <div class="gallery" role="tablist" aria-label="车辆视角">
    <button
      v-for="(view, index) in VEHICLE_VIEWS"
      :key="view.id"
      type="button"
      role="tab"
      :aria-selected="activeId === view.id"
      :disabled="!isReady(view.id)"
      :title="isReady(view.id) ? view.label : '该视角素材制作中'"
      :class="{ active: activeId === view.id, missing: !isReady(view.id) }"
      @click="emit('select', index)"
    >
      <img
        v-if="isReady(view.id) && vehicleImageSrc(folder, colorCode, view.id)"
        :src="vehicleImageSrc(folder, colorCode, view.id)"
        :alt="view.label"
        width="160"
        height="90"
        loading="lazy"
        decoding="async"
      />
      <span v-else class="pending">该视角素材制作中</span>
      <span>{{ view.label }}</span>
    </button>
  </div>
</template>

<style scoped>
.gallery {
  display: flex;
  justify-content: center;
  gap: 10px;
  padding: 0 24px 22px;
}

.gallery button {
  width: 92px;
  padding: 0;
  overflow: hidden;
  color: rgba(243, 246, 249, .82);
  background: rgba(18, 24, 32, .42);
  border: 2px solid transparent;
  border-radius: 8px;
  cursor: pointer;
}

.gallery button:hover:not(:disabled),
.gallery button:focus-visible {
  border-color: rgba(255, 255, 255, .45);
}

.gallery button:focus-visible {
  outline: 2px solid rgba(255, 255, 255, .55);
  outline-offset: 2px;
}

.gallery button.active { border-color: #fff; }
.gallery button.missing {
  opacity: .55;
  cursor: not-allowed;
}

.pending {
  display: grid;
  place-items: center;
  aspect-ratio: 16 / 9;
  padding: 6px;
  color: rgba(255,255,255,.8);
  font-size: 10px;
  line-height: 1.3;
  background: rgba(18, 24, 32, .55);
}

.gallery img {
  display: block;
  width: 100%;
  aspect-ratio: 16 / 9;
  height: auto;
  object-fit: cover;
}

.gallery span {
  display: block;
  padding: 4px 0 5px;
  font-size: 11px;
}

@media (max-width: 1099px) {
  .gallery {
    justify-content: flex-start;
    gap: 6px;
    padding: 0 10px 10px;
    overflow-x: auto;
    scrollbar-width: none;
  }

  .gallery::-webkit-scrollbar { display: none; }
  .gallery button { flex: 0 0 auto; width: 68px; }
}

@media (max-width: 720px) {
  .gallery button { width: 58px; }
  .gallery span { font-size: 10px; }
}
</style>
