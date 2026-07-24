<script setup>
import { computed, ref, watch } from 'vue'
import { showToast } from 'vant'
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
const isPaid = computed(() => order.value?.status === 'PAID')

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
    showToast({ type: 'success', message: '模拟支付成功' })
  } catch (error) {
    showToast({ type: 'fail', message: error.message || '支付失败' })
    await loadOrder()
  } finally {
    paying.value = false
  }
}

watch(() => route.params.id, loadOrder, { immediate: true })
</script>

<template>
  <section class="mobile-payment">
    <div v-if="errorMessage" class="error-banner">{{ errorMessage }}</div>

    <template v-if="order">
      <div class="payment-card" :class="{ success: isPaid }">
        <div class="payment-icon">{{ isPaid ? '✓' : '¥' }}</div>
        <p>{{ isPaid ? 'PAYMENT SUCCESS' : 'SIMULATED PAYMENT' }}</p>
        <h1>{{ isPaid ? '支付成功' : '订单已创建，请完成支付' }}</h1>
        <span class="order-number">订单号：{{ order.orderNo }}</span>

        <div class="payment-amount">
          <span>{{ isPaid ? '实付金额' : '待支付金额' }}</span>
          <strong>{{ formatMoney(order.totalAmount) }}</strong>
        </div>

        <div class="payment-meta">
          <span>当前状态</span>
          <van-tag :type="status.type" round size="medium">{{ status.label }}</van-tag>
          <template v-if="order.paidAt">
            <span>支付时间</span>
            <b>{{ formatDateTime(order.paidAt) }}</b>
          </template>
        </div>

        <van-notice-bar
          v-if="isPending"
          left-icon="info-o"
          text="这是课程项目的模拟支付，不会产生真实扣款。待支付订单会在 30 分钟后自动取消。"
          color="#1d4ed8"
          background="#eff6ff"
        />
        <van-notice-bar
          v-else-if="!isPaid"
          :text="`当前订单状态为「${status.label}」，不能继续支付。`"
          left-icon="warning-o"
          color="#b45309"
          background="#fef3c7"
        />
      </div>

      <section v-if="payment" class="payment-receipt">
        <h2>支付凭证</h2>
        <van-cell title="支付流水号" :value="payment.paymentNo" />
        <van-cell title="支付状态" value="支付成功" />
        <van-cell title="支付时间" :value="formatDateTime(payment.paidAt)" />
      </section>

      <div class="action-bar">
        <van-button
          v-if="isPending"
          type="primary"
          block
          round
          :loading="paying"
          @click="pay"
        >
          确认模拟支付
        </van-button>
        <van-button
          v-if="isPaid"
          type="primary"
          block
          round
          @click="router.push({ name: 'mobile-orders' })"
        >
          查看我的订单
        </van-button>
        <van-button
          block
          round
          @click="router.push({ name: 'mobile-order-detail', params: { id: order.id } })"
        >
          查看订单详情
        </van-button>
      </div>
    </template>

    <van-loading v-if="loading" size="24" class="loading" />
  </section>
</template>

<style scoped>
.mobile-payment { padding: 0 0 24px; background: #f7f8fa; min-height: 100%; }
.error-banner { padding: 12px 16px; color: #b91c1c; background: #fee2e2; font-size: 13px; }

.payment-card {
  margin: 16px 12px;
  padding: 32px 22px 24px;
  text-align: center;
  background: #fff;
  border: 1px solid #dbeafe;
  border-radius: 16px;
  box-shadow: 0 12px 32px rgba(30, 64, 175, .10);
}
.payment-card.success { border-color: #bbf7d0; box-shadow: 0 12px 32px rgba(22, 163, 74, .08); }
.payment-icon {
  display: grid;
  width: 64px;
  height: 64px;
  margin: 0 auto 14px;
  place-items: center;
  color: #fff;
  background: linear-gradient(135deg, #1d4ed8, #60a5fa);
  border-radius: 50%;
  font-size: 28px;
  font-weight: 800;
}
.success .payment-icon { background: linear-gradient(135deg, #15803d, #4ade80); }
.payment-card > p { margin: 0 0 6px; color: #2563eb; font-size: 12px; font-weight: 800; letter-spacing: .12em; }
.success > p { color: #15803d; }
.payment-card h1 { margin: 0; color: #0f172a; font-size: 22px; }
.order-number { display: block; margin: 10px 0 22px; color: #64748b; font-size: 12px; }

.payment-amount { margin-bottom: 20px; padding: 20px 16px; background: #f8fafc; border-radius: 12px; }
.payment-amount span { display: block; margin-bottom: 6px; color: #64748b; font-size: 13px; }
.payment-amount strong { color: #dc2626; font-size: 32px; }

.payment-meta {
  display: grid;
  grid-template-columns: 1fr auto;
  gap: 10px 14px;
  align-items: center;
  margin-bottom: 18px;
  text-align: left;
}
.payment-meta > span { color: #64748b; font-size: 13px; }
.payment-meta b { color: #334155; font-size: 13px; }

.payment-receipt {
  margin: 0 12px 12px;
  padding: 4px 0;
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  overflow: hidden;
}
.payment-receipt h2 {
  margin: 0;
  padding: 14px 16px 8px;
  color: #0f172a;
  font-size: 15px;
  font-weight: 600;
}

.action-bar { padding: 16px 12px; display: grid; gap: 10px; background: #fff; }
.action-bar .van-button + .van-button { margin-top: 0; }

.loading { display: block; margin: 32px auto; }
</style>