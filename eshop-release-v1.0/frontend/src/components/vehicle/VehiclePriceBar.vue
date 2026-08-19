<script setup>
import { computed } from 'vue'
import { formatMoney } from '../../utils/shop'

const props = defineProps({
  quote: { type: Object, default: null },
  basePrice: { type: [Number, String], default: 0 },
  quoting: { type: Boolean, default: false },
  submitting: { type: Boolean, default: false },
  canPurchase: { type: Boolean, default: false },
  nextLabel: { type: String, default: '下一步' },
  isLastTab: { type: Boolean, default: false },
})

defineEmits(['next', 'summary', 'reset', 'add-cart', 'buy-now'])

const unit = computed(() => props.quote?.unitPrice ?? props.basePrice)
const option = computed(() => props.quote?.optionAmount ?? 0)
</script>

<template>
  <footer class="price-bar">
    <div class="price">
      <strong>{{ formatMoney(unit) }}</strong>
      <span>基础价 {{ formatMoney(basePrice) }}</span>
      <span v-if="Number(option) > 0">选装 {{ formatMoney(option) }}</span>
      <small>{{ quoting ? '正在计算…' : '课程演示价 · 预计交付以订单确认页为准' }}</small>
    </div>
    <div class="actions">
      <button type="button" class="ghost" @click="$emit('summary')">配置清单</button>
      <button type="button" class="ghost" @click="$emit('reset')">重置</button>
      <template v-if="isLastTab">
        <button type="button" class="ghost" :disabled="!canPurchase || submitting" @click="$emit('add-cart')">
          加入购物车
        </button>
        <button type="button" class="primary" :disabled="!canPurchase || submitting" @click="$emit('buy-now')">
          立即购买
        </button>
      </template>
      <button v-else type="button" class="primary" @click="$emit('next')">
        {{ nextLabel }}
      </button>
    </div>
  </footer>
</template>

<style scoped>
.price-bar {
  flex: 0 0 auto;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 14px 18px max(16px, env(safe-area-inset-bottom));
  z-index: 2;
  background: #121212;
  border-top: 1px solid #2a2a2a;
}

.price {
  display: grid;
  gap: 1px;
}

.price strong { font-size: 32px; font-weight: 600; letter-spacing: -.02em; }
.price span,
.price small { color: rgba(255,255,255,.52); font-size: 12px; }

@media (min-width: 1920px) {
  .price strong { font-size: 34px; }
  .price span,
  .price small { font-size: 13px; }
}

.actions { display: flex; flex-wrap: wrap; justify-content: flex-end; gap: 8px; }

.ghost,
.primary {
  min-width: 44px;
  min-height: 44px;
  padding: 9px 14px;
  border-radius: 8px;
  cursor: pointer;
}

.ghost:hover,
.primary:hover { filter: brightness(1.08); }

.ghost:focus-visible,
.primary:focus-visible {
  outline: 2px solid #fff;
  outline-offset: 2px;
}

.ghost:active,
.primary:active { transform: translateY(1px); }

.ghost {
  color: #fff;
  background: transparent;
  border: 1px solid #3a3a3a;
}

.primary {
  color: #fff;
  background: #2f6bff;
  border: 0;
}

.primary:disabled,
.ghost:disabled { opacity: .4; cursor: not-allowed; }

@media (max-width: 1099px) {
  .price-bar {
    flex-wrap: wrap;
    align-items: flex-end;
    gap: 10px;
    padding: 10px 14px 12px;
  }

  .price { flex: 1 1 140px; }
  .price strong { font-size: 24px; }
  .actions { justify-content: flex-start; }
}

@media (max-height: 500px) {
  .price-bar {
    flex-wrap: nowrap;
    gap: 8px;
    padding: 6px 10px;
  }

  .price strong { font-size: 18px; }
  .price span { display: none; }
  .price small { font-size: 11px; }

  .ghost,
  .primary {
    min-height: 44px;
    padding: 6px 8px;
    font-size: 12px;
  }
}

@media (max-width: 720px) {
  .price-bar {
    padding: 10px 12px max(12px, env(safe-area-inset-bottom));
  }

  .price { flex: 1 1 100%; }
  .price strong { font-size: 22px; }

  .actions {
    width: 100%;
  }

  .ghost,
  .primary {
    flex: 1 1 auto;
    min-height: 44px;
    padding: 8px 10px;
    font-size: 13px;
  }
}
</style>
