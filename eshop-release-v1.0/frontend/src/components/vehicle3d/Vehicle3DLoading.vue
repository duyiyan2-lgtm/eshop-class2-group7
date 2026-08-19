<script setup>
defineProps({
  progress: { type: Number, default: 0 },
  label: { type: String, default: '正在加载三维预览…' },
})
</script>

<template>
  <div class="vehicle-3d-loading" role="status" aria-live="polite">
    <div class="skel" aria-hidden="true">
      <i class="skel-glow" />
      <i class="skel-car" />
      <i class="skel-shadow" />
    </div>
    <p>{{ label }}</p>
    <div class="bar" aria-hidden="true">
      <i :style="{ width: `${Math.max(6, Math.min(100, progress))}%` }" />
    </div>
    <small>{{ Math.round(progress) }}%</small>
  </div>
</template>

<style scoped>
.vehicle-3d-loading {
  position: absolute;
  inset: 0;
  z-index: 4;
  display: grid;
  place-content: center;
  justify-items: center;
  gap: 12px;
  color: #2a3642;
  background: linear-gradient(180deg, #b9cbdc 0%, #8fa8be 48%, #657f98 100%);
  pointer-events: none;
}

.skel {
  position: relative;
  width: min(62%, 640px);
  height: 180px;
}

.skel-glow,
.skel-car,
.skel-shadow {
  position: absolute;
  left: 50%;
  transform: translateX(-50%);
}

.skel-glow {
  top: 18px;
  width: 78%;
  height: 92px;
  border-radius: 50%;
  background: radial-gradient(ellipse at center, rgba(255,255,255,.42), transparent 70%);
}

.skel-car {
  top: 36px;
  width: 72%;
  height: 78px;
  border-radius: 46% 46% 18% 18% / 70% 70% 30% 30%;
  background: linear-gradient(110deg, #d5e0ea 12%, #f4f7fa 38%, #c4d2de 58%, #e8eef3 82%);
  background-size: 200% 100%;
  animation: sheen 1.4s ease-in-out infinite;
}

.skel-shadow {
  bottom: 18px;
  width: 58%;
  height: 18px;
  border-radius: 50%;
  background: radial-gradient(ellipse at center, rgba(24,36,48,.28), transparent 72%);
}

.bar {
  width: min(280px, 46vw);
  height: 4px;
  overflow: hidden;
  background: rgba(255, 255, 255, .28);
  border-radius: 999px;
}

.bar i {
  display: block;
  height: 100%;
  background: #1d2833;
}

small,
p {
  margin: 0;
  color: rgba(29, 40, 51, .7);
  font-size: 13px;
}

small { font-size: 12px; }

@keyframes sheen {
  0% { background-position: 100% 0; }
  100% { background-position: 0 0; }
}

@media (prefers-reduced-motion: reduce) {
  .skel-car { animation: none; }
}
</style>
