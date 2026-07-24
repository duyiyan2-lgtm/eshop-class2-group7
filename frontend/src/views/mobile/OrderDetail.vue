<template>
  <van-nav-bar title="订单详情" left-arrow @click-left="back" fixed placeholder />

  <div v-if="order" class="detail">
    <!-- 状态卡片 -->
    <div class="status-card" :class="`status-${order.status}`">
      <div class="status-text">{{ statusText(order.status) }}</div>
      <div v-if="order.status === 'PENDING_PAYMENT'" class="countdown">
        剩余支付时间：{{ countdownText }}
      </div>
    </div>

    <!-- 收货地址 -->
    <van-cell-group title="收货地址" inset>
      <van-cell
        :title="order.address.receiver"
        :label="`${order.address.phone}\n${order.address.detail}`"
      />
    </van-cell-group>

    <!-- 商品列表 -->
    <van-cell-group title="商品列表" inset>
      <van-card
        v-for="item in order.items"
        :key="item.id"
        :title="item.productName"
        :desc="`单价 ¥${item.unitPrice}`"
        :num="`x ${item.quantity}`"
        :price="item.subtotalAmount"
        :thumb="item.productImage"
      />
    </van-cell-group>

    <!-- 费用明细 -->
    <van-cell-group title="费用明细" inset>
      <van-cell title="商品总额" :value="`¥${order.originalAmount}`" />
      <van-cell title="优惠" :value="`-¥${order.discountAmount}`" />
      <van-cell
        title="实付"
        :value="`¥${order.payableAmount}`"
        value-class="total-amount"
      />
    </van-cell-group>

    <!-- 订单信息 -->
    <van-cell-group title="订单信息" inset>
      <van-cell title="订单号" :value="order.orderNo" />
      <van-cell title="下单时间" :value="order.createdAt" />
    </van-cell-group>
  </div>

  <van-empty v-else-if="!loading" description="订单不存在" />

  <!-- 底部操作栏 -->
  <div v-if="order && order.status === 'PENDING_PAYMENT'" class="bottom-bar">
    <van-button plain type="danger" @click="handleCancel">取消订单</van-button>
    <van-button type="primary" @click="handlePay">去支付</van-button>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showConfirmDialog, showToast } from 'vant'
import { getOrderDetail, cancelOrder, statusText } from '@/api/order.js'

const route = useRoute()
const router = useRouter()
const order = ref(null)
const loading = ref(true)
const now = ref(Date.now())
let timer = null

// 倒计时文案
const countdownText = computed(() => {
  if (!order.value || !order.value.expireAt) return ''
  const expire = new Date(order.value.expireAt).getTime()
  const diff = Math.floor((expire - now.value) / 1000)
  if (diff <= 0) return '已过期'
  const m = Math.floor(diff / 60)
  const s = diff % 60
  return `${m}分${s.toString().padStart(2, '0')}秒`
})

async function loadDetail() {
  loading.value = true
  try {
    const res = await getOrderDetail(route.params.id)
    if (res.code === 200) {
      order.value = res.data
      if (order.value.status === 'PENDING_PAYMENT') {
        startCountdown()
      }
    } else {
      showToast(res.message || '加载失败')
    }
  } catch (e) {
    showToast('网络错误')
  } finally {
    loading.value = false
  }
}

function startCountdown() {
  timer = setInterval(() => {
    now.value = Date.now()
  }, 1000)
}

async function handleCancel() {
  try {
    await showConfirmDialog({
      title: '确认取消订单？',
      message: '订单取消后将无法恢复'
    })
    const res = await cancelOrder(order.value.id)
    if (res.code === 200) {
      showToast('已取消')
      order.value.status = 'CANCELLED'
    } else {
      showToast(res.message || '取消失败')
    }
  } catch (e) {
    // 用户取消
  }
}

function handlePay() {
  showToast('支付功能开发中')
}

function back() {
  if (window.history.length > 1) {
    router.back()
  } else {
    router.push('/m/orders')
  }
}

onMounted(() => {
  loadDetail()
})

onUnmounted(() => {
  if (timer) clearInterval(timer)
})
</script>

<style scoped>
.detail {
  padding-bottom: 80px;
}

.status-card {
  background: linear-gradient(135deg, #ff7e5f, #ee0a24);
  color: white;
  padding: 30px 16px;
  text-align: center;
}
.status-card.status-PAID,
.status-card.status-SHIPPED,
.status-card.status-COMPLETED {
  background: linear-gradient(135deg, #4facfe, #00f2fe);
}
.status-card.status-CANCELLED {
  background: #999;
}
.status-text {
  font-size: 22px;
  font-weight: bold;
}
.countdown {
  margin-top: 8px;
  font-size: 13px;
  opacity: 0.9;
}

.total-amount {
  color: #ee0a24;
  font-weight: bold;
  font-size: 18px;
}

.bottom-bar {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  max-width: 750px;
  margin: 0 auto;
  background: white;
  padding: 10px 16px;
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  box-shadow: 0 -2px 8px rgba(0, 0, 0, 0.05);
  z-index: 100;
}
</style>