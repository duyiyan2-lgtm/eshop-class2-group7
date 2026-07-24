<template>
  <van-nav-bar title="我的订单" fixed placeholder />

  <van-tabs v-model:active="activeTab" @change="loadOrders" sticky>
    <van-tab title="全部" name="" />
    <van-tab title="待支付" name="PENDING_PAYMENT" />
    <van-tab title="已支付" name="PAID" />
    <van-tab title="已发货" name="SHIPPED" />
  </van-tabs>

  <van-list
    v-model:loading="loading"
    :finished="finished"
    finished-text="没有更多了"
    @load="loadOrders"
  >
    <div
      v-for="order in orders"
      :key="order.id"
      class="order-card"
      @click="goDetail(order.id)"
    >
      <div class="order-header">
        <span class="order-no">订单号：{{ order.orderNo }}</span>
        <van-tag :type="statusType(order.status)">
          {{ statusText(order.status) }}
        </van-tag>
      </div>

      <van-card
        v-for="item in order.items"
        :key="item.id"
        :title="item.productName"
        :desc="`单价 ¥${item.unitPrice}`"
        :num="`x ${item.quantity}`"
        :price="item.subtotalAmount"
        :thumb="item.productImage"
      />

      <div class="order-footer">
        <span class="total">合计：¥{{ order.payableAmount }}</span>
        <div class="actions">
          <van-button
            v-if="order.status === 'PENDING_PAYMENT'"
            plain type="danger" size="small"
            @click.stop="handleCancel(order)"
          >取消</van-button>
          <van-button
            v-if="order.status === 'PENDING_PAYMENT'"
            type="primary" size="small"
            @click.stop="handlePay(order)"
          >去支付</van-button>
        </div>
      </div>
    </div>

    <van-empty
      v-if="!loading && orders.length === 0"
      description="暂无订单"
    />
  </van-list>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { showConfirmDialog, showToast } from 'vant'
import { getMyOrders, cancelOrder, statusText, statusType } from '@/api/order.js'

const router = useRouter()
const orders = ref([])
const loading = ref(false)
const finished = ref(false)
const activeTab = ref('')

async function loadOrders() {
  loading.value = true
  try {
    const res = await getMyOrders({ status: activeTab.value })
    if (res.code === 200) {
      orders.value = res.data.list
      finished.value = true
    } else {
      showToast(res.message || '加载失败')
    }
  } catch (e) {
    showToast('网络错误')
  } finally {
    loading.value = false
  }
}

async function handleCancel(order) {
  try {
    await showConfirmDialog({
      title: '确认取消订单？',
      message: `订单 ${order.orderNo} 取消后将无法恢复`
    })
    const res = await cancelOrder(order.id)
    if (res.code === 200) {
      showToast('已取消')
      order.status = 'CANCELLED'
    } else {
      showToast(res.message || '取消失败')
    }
  } catch (e) {
    // 用户取消
  }
}

function handlePay(order) {
  showToast('支付功能开发中')
}

function goDetail(id) {
  router.push(`/m/orders/${id}`)
}

onMounted(() => {
  loadOrders()
})
</script>

<style scoped>
.order-card {
  background: white;
  margin: 10px;
  border-radius: 8px;
  overflow: hidden;
}
.order-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 16px;
  border-bottom: 1px solid #f5f5f5;
  font-size: 13px;
  color: #666;
}
.order-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 16px;
  border-top: 1px solid #f5f5f5;
}
.total {
  font-weight: bold;
  color: #ee0a24;
  font-size: 15px;
}
.actions {
  display: flex;
  gap: 8px;
}
</style>