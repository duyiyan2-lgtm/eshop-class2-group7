<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRouter } from 'vue-router'
import { cancelOrder, confirmOrder, getOrders } from '../../api/order'
import {
  ORDER_STATUS,
  formatDateTime,
  formatMoney,
  orderStatusInfo,
  specsText,
} from '../../utils/shop'

const router = useRouter()
const orders = ref([])
const current = ref(1)
const size = 8
const total = ref(0)
const statusFilter = ref('')
const loading = ref(false)
const operatingId = ref()
const errorMessage = ref('')

const statusOptions = computed(() => [
  { value: '', label: '全部订单' },
  ...Object.entries(ORDER_STATUS).map(([value, info]) => ({ value, label: info.label })),
])

const loadOrders = async () => {
  loading.value = true
  errorMessage.value = ''
  try {
    const data = await getOrders({
      current: current.value,
      size,
      status: statusFilter.value || undefined,
    })
    orders.value = data.records
    total.value = data.total
  } catch (error) {
    errorMessage.value = error.message || '订单加载失败'
  } finally {
    loading.value = false
  }
}

const changeStatus = () => {
  current.value = 1
  loadOrders()
}

const changePage = (page) => {
  current.value = page
  loadOrders()
}

const cancel = async (order) => {
  try {
    await ElMessageBox.confirm(
      `确定取消订单 ${order.orderNo} 吗？取消后库存会自动恢复。`,
      '取消订单',
      { type: 'warning', confirmButtonText: '确认取消', cancelButtonText: '暂不取消' },
    )
    operatingId.value = order.id
    await cancelOrder(order.id)
    ElMessage.success('订单已取消')
    await loadOrders()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      ElMessage.error(error.message || '取消订单失败')
    }
  } finally {
    operatingId.value = undefined
  }
}

const confirmReceipt = async (order) => {
  try {
    await ElMessageBox.confirm(
      `确认已收到订单 ${order.orderNo} 的全部商品吗？`,
      '确认收货',
      { type: 'success', confirmButtonText: '确认收货', cancelButtonText: '稍后再说' },
    )
    operatingId.value = order.id
    await confirmOrder(order.id)
    ElMessage.success('已确认收货，订单完成')
    await loadOrders()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      ElMessage.error(error.message || '确认收货失败')
    }
  } finally {
    operatingId.value = undefined
  }
}

onMounted(loadOrders)
</script>

<template>
  <div class="orders-page">
    <header class="page-heading">
      <div>
        <p>MY ORDERS</p>
        <h1>我的订单</h1>
        <span>查看订单进度并完成支付、取消或确认收货</span>
      </div>
      <el-button type="primary" @click="router.push({ name: 'pc-products' })">继续购物</el-button>
    </header>

    <nav class="status-tabs">
      <button
        v-for="option in statusOptions"
        :key="option.value"
        type="button"
        :class="{ active: statusFilter === option.value }"
        @click="statusFilter = option.value; changeStatus()"
      >
        {{ option.label }}
      </button>
    </nav>

    <el-alert
      v-if="errorMessage"
      :title="errorMessage"
      type="error"
      show-icon
      :closable="false"
      class="orders-alert"
    />

    <section v-loading="loading" class="orders-list">
      <article v-for="order in orders" :key="order.id" class="order-card">
        <header>
          <div>
            <span>{{ formatDateTime(order.createdAt) }}</span>
            <b>订单号：{{ order.orderNo }}</b>
          </div>
          <el-tag :type="orderStatusInfo(order.status).type" effect="light">
            {{ orderStatusInfo(order.status).label }}
          </el-tag>
        </header>

        <div class="order-body">
          <div class="order-products">
            <div v-for="item in order.items.slice(0, 3)" :key="item.id" class="order-product">
              <div class="product-image">
                <img v-if="item.productImage" :src="item.productImage" :alt="item.productName" />
                <span v-else>E-Shop</span>
              </div>
              <div>
                <RouterLink :to="`/pc/products/${item.productId}`">{{ item.productName }}</RouterLink>
                <p>{{ specsText(item.skuSpecs) || '默认规格' }} · × {{ item.quantity }}</p>
              </div>
            </div>
            <p v-if="order.items.length > 3" class="more-items">
              还有 {{ order.items.length - 3 }} 种商品，请查看详情
            </p>
          </div>

          <div class="receiver">
            <span>收货人</span>
            <strong>{{ order.receiverName }}</strong>
            <p>{{ order.receiverPhone }}</p>
          </div>

          <div class="order-total">
            <span>订单金额</span>
            <strong>{{ formatMoney(order.totalAmount) }}</strong>
            <p>共 {{ order.items.reduce((count, item) => count + item.quantity, 0) }} 件</p>
          </div>

          <div class="order-actions">
            <el-button
              v-if="order.status === 'PENDING_PAYMENT'"
              type="primary"
              :loading="operatingId === order.id"
              @click="router.push({ name: 'pc-order-payment', params: { id: order.id } })"
            >
              立即支付
            </el-button>
            <el-button
              v-if="order.status === 'PENDING_PAYMENT'"
              :loading="operatingId === order.id"
              @click="cancel(order)"
            >
              取消订单
            </el-button>
            <el-button
              v-if="order.status === 'SHIPPED'"
              type="success"
              :loading="operatingId === order.id"
              @click="confirmReceipt(order)"
            >
              确认收货
            </el-button>
            <el-button
              v-if="order.status === 'COMPLETED'"
              type="primary"
              plain
              @click="router.push({ name: 'pc-order-detail', params: { id: order.id } })"
            >
              评价商品
            </el-button>
            <el-button
              @click="router.push({ name: 'pc-order-detail', params: { id: order.id } })"
            >
              订单详情
            </el-button>
          </div>
        </div>
      </article>

      <el-empty v-if="!loading && !orders.length" description="该分类下还没有订单">
        <el-button type="primary" @click="router.push({ name: 'pc-products' })">去商城逛逛</el-button>
      </el-empty>
    </section>

    <div v-if="total > size" class="pagination">
      <el-pagination
        background
        layout="prev, pager, next"
        :current-page="current"
        :page-size="size"
        :total="total"
        @current-change="changePage"
      />
    </div>
  </div>
</template>

<style scoped>
.orders-page { width: min(1200px, 100%); margin: 0 auto; padding: 16px 0 56px; }
.page-heading { display: flex; align-items: end; justify-content: space-between; margin-bottom: 24px; }
.page-heading p { margin: 0 0 6px; color: #2563eb; font-size: 12px; font-weight: 800; letter-spacing: .12em; }
.page-heading h1 { margin: 0; color: #0f172a; font-size: 32px; }
.page-heading span { display: block; margin-top: 8px; color: #64748b; }
.status-tabs { display: flex; gap: 8px; margin-bottom: 20px; padding: 8px; overflow-x: auto; background: #fff; border: 1px solid #e2e8f0; border-radius: 14px; }
.status-tabs button { padding: 10px 18px; color: #64748b; background: transparent; border: 0; border-radius: 9px; cursor: pointer; font: inherit; white-space: nowrap; }
.status-tabs button.active { color: #fff; background: #2563eb; font-weight: 700; }
.orders-alert { margin-bottom: 18px; }
.orders-list { min-height: 300px; }
.order-card { margin-bottom: 16px; overflow: hidden; background: #fff; border: 1px solid #e2e8f0; border-radius: 16px; box-shadow: 0 10px 32px rgba(15, 23, 42, .05); }
.order-card > header { display: flex; align-items: center; justify-content: space-between; padding: 15px 22px; color: #64748b; background: #f8fafc; border-bottom: 1px solid #eef2f7; font-size: 13px; }
.order-card > header div { display: flex; gap: 22px; }
.order-card > header b { color: #334155; font-weight: 600; }
.order-body { display: grid; grid-template-columns: minmax(380px, 1fr) 155px 135px 130px; gap: 22px; align-items: center; padding: 22px; }
.order-product { display: flex; align-items: center; gap: 13px; padding: 8px 0; }
.product-image { display: grid; flex: 0 0 58px; width: 58px; height: 58px; place-items: center; overflow: hidden; color: #93c5fd; background: #eff6ff; border-radius: 9px; font-size: 10px; font-weight: 800; }
.product-image img { width: 100%; height: 100%; object-fit: contain; }
.order-product a { color: #0f172a; font-size: 14px; font-weight: 700; }
.order-product p, .receiver p, .order-total p { margin: 6px 0 0; color: #94a3b8; font-size: 12px; }
.more-items { margin: 8px 0 0; color: #64748b; font-size: 12px; }
.receiver, .order-total { padding-left: 18px; border-left: 1px solid #eef2f7; }
.receiver > span, .order-total > span { display: block; margin-bottom: 8px; color: #94a3b8; font-size: 12px; }
.receiver strong { color: #334155; }
.order-total strong { color: #dc2626; font-size: 18px; }
.order-actions { display: grid; gap: 8px; }
.order-actions .el-button { margin-left: 0; }
@media (max-width: 920px) {
  .order-body { grid-template-columns: 1fr 1fr; }
  .order-products { grid-column: 1 / 3; }
  .order-actions { grid-column: 1 / 3; grid-template-columns: repeat(2, 1fr); }
}
@media (max-width: 640px) {
  .page-heading { align-items: stretch; flex-direction: column; gap: 16px; }
  .order-card > header, .order-card > header div { align-items: start; flex-direction: column; gap: 8px; }
  .order-body { grid-template-columns: 1fr; }
  .order-products, .order-actions { grid-column: auto; }
  .receiver, .order-total { padding: 12px 0 0; border-top: 1px solid #eef2f7; border-left: 0; }
}
</style>
