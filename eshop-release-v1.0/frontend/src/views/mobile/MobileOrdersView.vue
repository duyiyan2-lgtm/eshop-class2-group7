<script setup>
import { computed, onActivated, onMounted, ref } from 'vue'
import { showConfirmDialog, showToast } from 'vant'
import { useRouter } from 'vue-router'
import { cancelOrder, confirmOrder, getOrders } from '../../api/order'
import { getReviewedOrderItemIds } from '../../api/review'
import { formatDateTime, formatMoney, orderStatusInfo } from '../../utils/shop'

const router = useRouter()
const orders = ref([])
const current = ref(1)
const size = 10
const total = ref(0)
const statusFilter = ref('')
const finished = ref(false)
const loading = ref(false)
const refreshing = ref(false)
const errorMessage = ref('')
const failedPage = ref(null)
const reviewedItemIds = ref(new Set())
const reviewLookupLoading = ref(false)
let requestSequence = 0
let reviewRequestSequence = 0

const statusOptions = [
  { value: '', label: '全部' },
  { value: 'PENDING_PAYMENT', label: '待支付' },
  { value: 'PAID', label: '待发货' },
  { value: 'SHIPPED', label: '待收货' },
  { value: 'COMPLETED', label: '已完成' },
  { value: 'CANCELED', label: '已取消' },
]

const summary = computed(() => {
  const map = { total: orders.value.length, PENDING_PAYMENT: 0, PAID: 0, SHIPPED: 0, COMPLETED: 0, CANCELED: 0 }
  for (const order of orders.value) {
    if (map[order.status] !== undefined) map[order.status] += 1
  }
  return map
})

const loadReviewStatus = async () => {
  const requestId = ++reviewRequestSequence
  const itemIds = orders.value
    .filter((order) => order.status === 'COMPLETED')
    .flatMap((order) => order.items || [])
    .map((item) => item.id)

  reviewedItemIds.value = new Set()
  reviewLookupLoading.value = false
  if (!itemIds.length) return

  reviewLookupLoading.value = true
  try {
    const ids = await getReviewedOrderItemIds(itemIds)
    if (requestId === reviewRequestSequence) reviewedItemIds.value = ids
  } catch {
    // 评价状态失败不影响订单主流程，进入详情页后仍可再次查询。
  } finally {
    if (requestId === reviewRequestSequence) reviewLookupLoading.value = false
  }
}

const reviewActionLabel = (order) => {
  const items = order.items || []
  if (reviewLookupLoading.value) return '检查评价状态…'
  if (!items.length) return '查看订单'
  const reviewedCount = items.filter((item) => reviewedItemIds.value.has(item.id)).length
  if (reviewedCount >= items.length) return '已评价'
  if (reviewedCount > 0) return '继续评价'
  return '评价商品'
}

const loadOrders = async ({ reset = false, page = reset ? 1 : current.value } = {}) => {
  const requestId = ++requestSequence
  const requestedStatus = statusFilter.value
  loading.value = true
  errorMessage.value = ''
  try {
    const data = await getOrders({
      current: page,
      size,
      status: requestedStatus || undefined,
    })
    if (requestId !== requestSequence) return false

    const records = data.records || []
    if (reset) {
      orders.value = records
    } else {
      orders.value = orders.value.concat(records)
    }
    current.value = page
    total.value = data.total ?? orders.value.length
    finished.value = records.length < size || orders.value.length >= total.value
    failedPage.value = null
    void loadReviewStatus()
    return true
  } catch (error) {
    if (requestId !== requestSequence) return false
    errorMessage.value = error.message || '订单加载失败'
    failedPage.value = page
    return false
  } finally {
    if (requestId === requestSequence) loading.value = false
  }
}

const changeStatus = (value) => {
  statusFilter.value = value
  current.value = 1
  orders.value = []
  total.value = 0
  finished.value = false
  loadOrders({ reset: true, page: 1 })
}

const loadMore = () => {
  if (finished.value || loading.value) return
  loadOrders({ page: current.value + 1 })
}

const retryLoad = () => {
  const page = failedPage.value || 1
  loadOrders({ reset: page === 1, page })
}

const cancel = async (order) => {
  try {
    await showConfirmDialog({
      title: '取消订单',
      message: `确定取消订单 ${order.orderNo} 吗？取消后库存会自动恢复。`,
      confirmButtonText: '确认取消',
      cancelButtonText: '暂不取消',
    })
    await cancelOrder(order.id)
    showToast('订单已取消')
    await loadOrders({ reset: true })
  } catch (error) {
    if (error === 'cancel' || error === 'close') return
    showToast({ type: 'fail', message: error.message || '取消订单失败' })
  }
}

const confirmReceipt = async (order) => {
  try {
    await showConfirmDialog({
      title: '确认收货',
      message: `确认已收到订单 ${order.orderNo} 的全部商品吗？`,
      confirmButtonText: '确认收货',
      cancelButtonText: '稍后再说',
    })
    await confirmOrder(order.id)
    showToast('已确认收货，订单完成')
    await loadOrders({ reset: true })
  } catch (error) {
    if (error === 'cancel' || error === 'close') return
    showToast({ type: 'fail', message: error.message || '确认收货失败' })
  }
}

const goPay = (order) => {
  router.push({ name: 'mobile-order-payment', params: { id: order.id } })
}

const goDetail = (order) => {
  router.push({ name: 'mobile-order-detail', params: { id: order.id } })
}

const onRefresh = async () => {
  finished.value = false
  const succeeded = await loadOrders({ reset: true, page: 1 })
  refreshing.value = false
  if (succeeded) showToast('已刷新')
}

onMounted(() => {
  loadOrders({ reset: true, page: 1 })
})

onActivated(() => {
  // 从详情/支付页返回时刷新列表
  if (orders.value.length) {
    loadOrders({ reset: true, page: 1 })
  }
})
</script>

<template>
  <section class="mobile-orders">
    <van-tabs
      :active="statusFilter"
      @click-tab="(tab) => changeStatus(tab.name)"
      sticky
      offset-top="46px"
      color="#1d4ed8"
    >
      <van-tab
        v-for="option in statusOptions"
        :key="option.value"
        :title="option.label"
        :name="option.value"
      />
    </van-tabs>

    <div v-if="orders.length" class="orders-summary">
      共 {{ total }} 单 · 当前展示 {{ summary.total }} 条
    </div>

    <van-pull-refresh v-model="refreshing" @refresh="onRefresh">
      <van-list
        v-model:loading="loading"
        :finished="finished"
        :immediate-check="false"
        :finished-text="orders.length ? '没有更多了' : ''"
        :error="Boolean(errorMessage)"
        :error-text="errorMessage || '请求失败，点击重试'"
        @load="loadMore"
        @click-error-text="retryLoad"
        class="orders-list"
      >
        <van-empty v-if="!loading && !orders.length" description="该分类下还没有订单">
          <van-button round type="primary" size="small" @click="router.push({ name: 'mobile-products' })">
            去商城逛逛
          </van-button>
        </van-empty>

        <van-card
          v-for="order in orders"
          :key="order.id"
          :title="`订单号：${order.orderNo}`"
          :desc="formatDateTime(order.createdAt)"
          class="order-card"
          @click="goDetail(order)"
        >
          <template #price>
            <span class="order-amount">实付 {{ formatMoney(order.totalAmount) }}</span>
          </template>
          <template #num>
            共 {{ (order.items || []).reduce((count, item) => count + item.quantity, 0) }} 件
          </template>
          <template #thumb>
            <div class="thumb">
              <img v-if="order.items?.[0]?.productImage" :src="order.items[0].productImage" :alt="order.items[0].productName" />
              <span v-else>E-Shop</span>
            </div>
          </template>
          <template #tags>
            <van-tag plain :type="orderStatusInfo(order.status).type" class="status-tag">
              {{ orderStatusInfo(order.status).label }}
            </van-tag>
            <p v-if="order.items?.length > 1" class="more-items">
              还有 {{ order.items.length - 1 }} 种商品
            </p>
          </template>
          <template #footer>
            <van-button
              v-if="order.status === 'PENDING_PAYMENT'"
              size="small"
              type="primary"
              @click.stop="goPay(order)"
            >
              去支付
            </van-button>
            <van-button
              v-if="order.status === 'PENDING_PAYMENT'"
              size="small"
              plain
              @click.stop="cancel(order)"
            >
              取消
            </van-button>
            <van-button
              v-if="order.status === 'SHIPPED'"
              size="small"
              type="success"
              @click.stop="confirmReceipt(order)"
            >
              确认收货
            </van-button>
            <van-button
              v-if="order.status === 'COMPLETED'"
              size="small"
              type="primary"
              plain
              @click.stop="goDetail(order)"
            >
              {{ reviewActionLabel(order) }}
            </van-button>
            <van-button size="small" plain @click.stop="goDetail(order)">
              订单详情
            </van-button>
          </template>
        </van-card>
      </van-list>
    </van-pull-refresh>
  </section>
</template>

<style scoped>
.mobile-orders { padding-bottom: 20px; background: #f7f8fa; min-height: 100%; }
.orders-summary { padding: 10px 16px 0; color: #969799; font-size: 12px; }
.orders-list { padding: 12px 12px 20px; }
.order-card { margin-bottom: 12px; background: #fff; border-radius: 12px; overflow: hidden; }
.order-card :deep(.van-card__header) { align-items: center; }
.order-card :deep(.van-card__title) { font-size: 14px; font-weight: 600; color: #323233; }
.order-card :deep(.van-card__desc) { color: #969799; font-size: 12px; }
.order-card :deep(.van-card__price) { color: #dc2626; font-weight: 700; font-size: 16px; }
.order-card :deep(.van-card__num) { color: #969799; font-size: 12px; }
.order-card :deep(.van-card__thumb) { width: 64px; height: 64px; }
.thumb {
  display: grid;
  width: 64px;
  height: 64px;
  place-items: center;
  overflow: hidden;
  color: #93c5fd;
  background: #eff6ff;
  border-radius: 8px;
  font-size: 10px;
  font-weight: 800;
}
.thumb img { width: 100%; height: 100%; object-fit: contain; }
.order-amount { color: #dc2626; font-weight: 700; font-size: 16px; }
.status-tag { margin-bottom: 6px; }
.more-items { margin: 4px 0 0; color: #969799; font-size: 12px; }
.order-card :deep(.van-card__footer) { padding-top: 10px; }
.order-card :deep(.van-button) { height: 30px; }
</style>
