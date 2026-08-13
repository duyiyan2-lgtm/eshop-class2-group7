<script setup>
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import { nativeState } from './native/runtime'
</script>

<template>
  <el-config-provider :locale="zhCn">
    <Transition name="network-status">
      <div
        v-if="nativeState.isNative && !nativeState.connected"
        class="native-network-banner"
        role="status"
        aria-live="polite"
      >
        <span class="native-network-banner__dot" aria-hidden="true"></span>
        当前网络不可用，请检查 Wi-Fi 或移动数据
      </div>
    </Transition>
    <RouterView />
  </el-config-provider>
</template>

<style>
.native-network-banner {
  position: fixed;
  z-index: 5000;
  right: 16px;
  bottom: calc(76px + var(--app-safe-bottom, env(safe-area-inset-bottom, 0px)));
  left: 16px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 9px;
  min-height: 44px;
  padding: 8px 16px;
  border: 1px solid rgb(251 146 60 / 45%);
  border-radius: 14px;
  color: #ffedd5;
  background: rgb(124 45 18 / 94%);
  box-shadow: 0 14px 32px rgb(0 0 0 / 28%);
  font-size: 14px;
  backdrop-filter: blur(14px);
}

.native-network-banner__dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #fb923c;
  box-shadow: 0 0 0 5px rgb(251 146 60 / 16%);
}

.network-status-enter-active,
.network-status-leave-active {
  transition: opacity 180ms ease, transform 180ms ease;
}

.network-status-enter-from,
.network-status-leave-to {
  opacity: 0;
  transform: translateY(12px);
}

@media (min-width: 720px) {
  .native-network-banner {
    right: 24px;
    left: auto;
    width: min(390px, calc(100vw - 48px));
  }
}
</style>
