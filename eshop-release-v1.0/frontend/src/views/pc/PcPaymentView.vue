<script setup>
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'
import { getOrder, payOrder } from '../../api/order'
import { formatDateTime, formatMoney, orderStatusInfo } from '../../utils/shop'

const route = useRoute()
const router = useRouter()
const order = ref(null)
const payment = ref(null)
const loading = ref(false)
const paying = ref(false)
const errorMessage = ref('')

const status = computed(() => orderStatusInfo(order.value?.status))
const isPending = computed(() => order.value?.status === 'PENDING_PAYMENT')

const loadOrder = async () => {
  loading.value = true
  errorMessage.value = ''
  payment.value = null
  try {
    const id = Number(route.params.id)
    if (!Number.isInteger(id) || id <= 0) throw new Error('订单编号不正确')
    order.value = await getOrder(id)
  } catch (error) {
    errorMessage.value = error.message || '订单加载失败'
  } finally {
    loading.value = false
  }
}

const pay = async () => {
  if (!isPending.value) return
  paying.value = true
  try {
    const result = await payOrder(order.value.id)
    await loadOrder()
    payment.value = {
      ...result,
      paidAt: result.paidAt || order.value.paidAt,
    }
    ElMessage.success('模拟支付成功')
  } catch (error) {
    ElMessage.error(error.message || '支付失败')
    await loadOrder()
  } finally {
    paying.value = false
  }
}

watch(() => route.params.id, loadOrder, { immediate: true })
</script>

<template>
  <div v-loading="loading" class="payment-page">
    <el-alert
      v-if="errorMessage"
      :title="errorMessage"
      type="error"
      show-icon
      :closable="false"
    />

    <template v-if="order">
      <section class="payment-card" :class="{ success: order.status === 'PAID' }">
        <div class="payment-icon">{{ order.status === 'PAID' ? '✓' : '¥' }}</div>
        <p>{{ order.status === 'PAID' ? 'PAYMENT SUCCESS' : 'SIMULATED PAYMENT' }}</p>
        <h1>{{ order.status === 'PAID' ? '支付成功' : '订单已创建，请完成支付' }}</h1>
        <span class="order-number">订单号：{{ order.orderNo }}</span>

        <div class="payment-amount">
          <span>{{ order.status === 'PAID' ? '实付金额' : '待支付金额' }}</span>
          <strong>{{ formatMoney(order.totalAmount) }}</strong>
        </div>

        <div class="payment-meta">
          <span>当前状态</span>
          <el-tag :type="status.type" size="large">{{ status.label }}</el-tag>
          <template v-if="order.paidAt">
            <span>支付时间</span>
            <b>{{ formatDateTime(order.paidAt) }}</b>
          </template>
        </div>

        <el-alert
          v-if="isPending"
          title="这是课程项目的模拟支付，不会产生真实扣款。待支付订单会在 30 分钟后自动取消。"
          type="info"
          show-icon
          :closable="false"
        />
        <el-alert
          v-else-if="order.status !== 'PAID'"
          :title="`当前订单状态为“${status.label}”，不能继续支付。`"
          type="warning"
          show-icon
          :closable="false"
        />

        <div class="payment-actions">
          <el-button
            v-if="isPending"
            type="primary"
            size="large"
            :loading="paying"
            @click="pay"
          >
            确认模拟支付
          </el-button>
          <el-button
            v-if="order.status === 'PAID'"
            type="primary"
            size="large"
            @click="router.push({ name: 'pc-orders' })"
          >
            查看我的订单
          </el-button>
          <el-button size="large" @click="router.push({ name: 'pc-order-detail', params: { id: order.id } })">
            查看订单详情
          </el-button>
        </div>
      </section>

      <section v-if="payment" class="payment-receipt">
        <h2>支付凭证</h2>
        <div><span>支付流水号</span><b>{{ payment.paymentNo }}</b></div>
        <div><span>支付状态</span><b>支付成功</b></div>
        <div><span>支付时间</span><b>{{ formatDateTime(payment.paidAt) }}</b></div>
      </section>
    </template>
  </div>
</template>

<style scoped>
.payment-page { width: min(760px, 100%); min-height: 500px; margin: 0 auto; padding: 38px 0 70px; }
.payment-card { padding: 46px 54px; text-align: center; background: #fff; border: 1px solid #dbeafe; border-radius: 24px; box-shadow: 0 22px 60px rgba(30, 64, 175, .12); }
.payment-card.success { border-color: #bbf7d0; box-shadow: 0 22px 60px rgba(22, 163, 74, .10); }
.payment-icon { display: grid; width: 68px; height: 68px; margin: 0 auto 18px; place-items: center; color: #fff; background: linear-gradient(135deg, #1d4ed8, #60a5fa); border-radius: 50%; font-size: 31px; font-weight: 800; }
.success .payment-icon { background: linear-gradient(135deg, #15803d, #4ade80); }
.payment-card > p { margin: 0 0 7px; color: #2563eb; font-size: 12px; font-weight: 800; letter-spacing: .12em; }
.success > p { color: #15803d; }
.payment-card h1 { margin: 0; color: #0f172a; font-size: 30px; }
.order-number { display: block; margin: 12px 0 26px; color: #64748b; font-size: 13px; }
.payment-amount { margin-bottom: 22px; padding: 22px; background: #f8fafc; border-radius: 14px; }
.payment-amount span { display: block; margin-bottom: 8px; color: #64748b; }
.payment-amount strong { color: #dc2626; font-size: 38px; }
.payment-meta { display: grid; grid-template-columns: 110px 1fr; gap: 13px; align-items: center; margin-bottom: 24px; text-align: left; }
.payment-meta > span { color: #64748b; }
.payment-meta b { color: #334155; }
.payment-meta .el-tag { justify-self: start; }
.payment-actions { display: flex; justify-content: center; gap: 12px; margin-top: 26px; }
.payment-actions .el-button { min-width: 150px; }
.payment-receipt { margin-top: 18px; padding: 24px 30px; background: #fff; border: 1px solid #e2e8f0; border-radius: 16px; }
.payment-receipt h2 { margin: 0 0 18px; font-size: 18px; }
.payment-receipt div { display: flex; justify-content: space-between; padding: 9px 0; color: #64748b; }
.payment-receipt b { color: #334155; }
@media (max-width: 640px) {
  .payment-card { padding: 34px 20px; }
  .payment-actions { align-items: stretch; flex-direction: column; }
}
</style>
