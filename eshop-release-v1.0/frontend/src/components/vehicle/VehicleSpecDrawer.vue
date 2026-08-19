<script setup>
import { formatMoney } from '../../utils/shop'

defineProps({
  modelValue: { type: Boolean, default: false },
  productName: { type: String, default: '' },
  quote: { type: Object, default: null },
})

const emit = defineEmits(['update:modelValue', 'reset', 'confirm'])
</script>

<template>
  <el-drawer
    :model-value="modelValue"
    title="配置清单"
    size="420px"
    destroy-on-close
    @update:model-value="emit('update:modelValue', $event)"
  >
    <p class="drawer-name">{{ productName }}</p>
    <dl v-if="quote" class="spec-list">
      <div>
        <dt>版本</dt>
        <dd>{{ quote.versionName }}</dd>
      </div>
      <div v-for="option in quote.selectedOptions || []" :key="option.valueId">
        <dt>{{ option.groupName }}</dt>
        <dd>
          {{ option.valueName }}
          <small v-if="option.included">已含</small>
          <small v-else-if="Number(option.priceDelta) > 0">+{{ formatMoney(option.priceDelta) }}</small>
        </dd>
      </div>
      <div>
        <dt>基础价</dt>
        <dd>{{ formatMoney(quote.basePrice) }}</dd>
      </div>
      <div>
        <dt>选装价</dt>
        <dd>{{ formatMoney(quote.optionAmount) }}</dd>
      </div>
      <div class="total">
        <dt>课程演示总价</dt>
        <dd>{{ formatMoney(quote.unitPrice) }}</dd>
      </div>
    </dl>
    <p v-else>请先完成版本与必选配置。</p>
    <template #footer>
      <el-button @click="emit('reset')">重置配置</el-button>
      <el-button type="primary" @click="emit('confirm')">确认配置</el-button>
    </template>
  </el-drawer>
</template>

<style scoped>
.drawer-name {
  margin: 0 0 16px;
  font-size: 16px;
  font-weight: 600;
}

.spec-list {
  display: grid;
  gap: 12px;
  margin: 0;
}

.spec-list > div {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  padding-bottom: 10px;
  border-bottom: 1px solid #f0f2f5;
}

dt { color: #8a8f96; }
dd { margin: 0; text-align: right; }
dd small { margin-left: 8px; color: #ff6700; }
.total dd { color: #ff6700; font-size: 18px; font-weight: 700; }
</style>
