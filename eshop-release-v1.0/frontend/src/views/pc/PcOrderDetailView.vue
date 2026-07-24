<script setup>
import { computed, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'
import {
  cancelOrder,
  confirmOrder,
  getOrder,
  getOrderLogs,
} from '../../api/order'
import {
  formatDateTime,
  formatMoney,
  orderStatusInfo,
  specsText,
} from '../../utils/shop'

const route = useRoute()
const router = useRouter()
const order = ref(null)
const logs = ref([])
const loading = ref(false)
const operating = ref(false)
const errorMessage = ref('')
const status = computed(() => orderStatusInfo(order.value?.status))

const loadDetail = async () => {
  loading.value = true
  errorMessage.value = ''
  try {
    const id = Number(route.params.id)
    if (!Number.isInteger(id) || id <= 0) throw new Error('订单编号不正确')
    const [orderData, logData] = await Promise.all([getOrder(id), getOrderLogs(id)])
    order.value = orderData
    logs.value = logData
  } catch (error) {
    errorMessage.value = error.message || '订单详情加载失败'
  } finally {
    loading.value = false
  }
}

const cancel = async () => {
  try {
    await ElMessageBox.confirm(
      `确定取消订单 ${order.value.orderNo} 吗？`,
      '取消订单',
      { type: 'warning', confirmButtonText: '确认取消', cancelButtonText: '暂不取消' },
    )
    operating.value = true
    await cancelOrder(order.value.id)
    ElMessage.success('订单已取消')
    await loadDetail()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      ElMessage.error(error.message || '取消订单失败')
    }
  } finally {
    operating.value = false
  }
}

const confirmReceipt = async () => {
  try {
    await ElMessageBox.confirm(
      '请确认已收到订单中的全部商品。',
      '确认收货',
      { type: 'success', confirmButtonText: '确认收货', cancelButtonText: '稍后再说' },
    )
    operating.value = true
    await confirmOrder(order.value.id)
    ElMessage.success('订单已完成')
    await loadDetail()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      ElMessage.error(error.message || '确认收货失败')
    }
  } finally {
    operating.value = false
  }
}

watch(() => route.params.id, loadDetail, { immediate: true })
</script>

<template>
  <div v-loading="loading" class="detail-page">
    <el-breadcrumb separator="›" class="breadcrumb">
      <el-breadcrumb-item :to="{ name: 'pc-orders' }">我的订单</el-breadcrumb-item>
      <el-breadcrumb-item>订单详情</el-breadcrumb-item>
    </el-breadcrumb>

    <el-alert
      v-if="errorMessage"
      :title="errorMessage"
      type="error"
      show-icon
      :closable="false"
    />

    <template v-if="order">
      <section class="status-card">
        <div>
          <p>ORDER STATUS</p>
          <h1>{{ status.label }}</h1>
          <span>订单号：{{ order.orderNo }}</span>
        </div>
        <div class="status-actions">
          <el-button
            v-if="order.status === 'PENDING_PAYMENT'"
            type="primary"
            size="large"
            @click="router.push({ name: 'pc-order-payment', params: { id: order.id } })"
          >
            立即支付
          </el-button>
          <el-button
            v-if="order.status === 'PENDING_PAYMENT'"
            size="large"
            :loading="operating"
            @click="cancel"
          >
            取消订单
          </el-button>
          <el-button
            v-if="order.status === 'SHIPPED'"
            type="success"
            size="large"
            :loading="operating"
            @click="confirmReceipt"
          >
            确认收货
          </el-button>
        </div>
      </section>

      <div class="detail-grid">
        <main>
          <section class="detail-card">
            <h2>商品信息</h2>
            <article v-for="item in order.items" :key="item.id" class="order-item">
              <div class="product-image">
                <img v-if="item.productImage" :src="item.productImage" :alt="item.productName" />
                <span v-else>E-Shop</span>
              </div>
              <div class="product-info">
                <RouterLink :to="`/pc/products/${item.productId}`">{{ item.productName }}</RouterLink>
                <p>{{ specsText(item.skuSpecs) || '默认规格' }}</p>
              </div>
              <span>{{ formatMoney(item.price) }} × {{ item.quantity }}</span>
              <strong>{{ formatMoney(item.subtotal) }}</strong>
            </article>
            <footer class="amount-row">
              <span>订单总额</span>
              <strong>{{ formatMoney(order.totalAmount) }}</strong>
            </footer>
          </section>

          <section class="detail-card info-card">
            <h2>配送与备注</h2>
            <dl>
              <dt>收货人</dt><dd>{{ order.receiverName }}</dd>
              <dt>联系电话</dt><dd>{{ order.receiverPhone }}</dd>
              <dt>收货地址</dt><dd>{{ order.receiverAddress }}</dd>
              <dt>订单备注</dt><dd>{{ order.remark || '无' }}</dd>
            </dl>
          </section>
        </main>

        <aside class="detail-card timeline-card">
          <h2>订单进度</h2>
          <el-timeline>
            <el-timeline-item
              v-for="log in [...logs].reverse()"
              :key="log.id"
              :timestamp="formatDateTime(log.createdAt)"
              placement="top"
              type="primary"
            >
              <b>{{ orderStatusInfo(log.toStatus).label }}</b>
              <p>{{ log.remark }}</p>
              <span v-if="log.operatorName">操作人：{{ log.operatorName }}</span>
            </el-timeline-item>
          </el-timeline>
          <div class="time-summary">
            <div><span>创建时间</span><b>{{ formatDateTime(order.createdAt) }}</b></div>
            <div v-if="order.paidAt"><span>支付时间</span><b>{{ formatDateTime(order.paidAt) }}</b></div>
            <div v-if="order.shippedAt"><span>发货时间</span><b>{{ formatDateTime(order.shippedAt) }}</b></div>
            <div v-if="order.completedAt"><span>完成时间</span><b>{{ formatDateTime(order.completedAt) }}</b></div>
            <div v-if="order.canceledAt"><span>取消时间</span><b>{{ formatDateTime(order.canceledAt) }}</b></div>
          </div>
        </aside>
      </div>
    </template>
  </div>
</template>

<style scoped>
.detail-page { width: min(1180px, 100%); min-height: 500px; margin: 0 auto; padding: 12px 0 60px; }
.breadcrumb { margin-bottom: 20px; }
.status-card { display: flex; align-items: center; justify-content: space-between; margin-bottom: 18px; padding: 30px 34px; color: #fff; background: linear-gradient(120deg, #0f172a, #1e3a8a); border-radius: 20px; box-shadow: 0 18px 45px rgba(15, 23, 42, .18); }
.status-card p { margin: 0 0 7px; color: #93c5fd; font-size: 12px; font-weight: 800; letter-spacing: .12em; }
.status-card h1 { margin: 0 0 9px; font-size: 30px; }
.status-card span { color: #cbd5e1; font-size: 13px; }
.status-actions { display: flex; gap: 10px; }
.detail-grid { display: grid; grid-template-columns: minmax(0, 1fr) 340px; gap: 18px; }
.detail-card { margin-bottom: 18px; padding: 28px; background: #fff; border: 1px solid #e2e8f0; border-radius: 18px; box-shadow: 0 10px 32px rgba(15, 23, 42, .05); }
.detail-card h2 { margin: 0 0 22px; color: #0f172a; font-size: 20px; }
.order-item { display: grid; grid-template-columns: 72px minmax(0, 1fr) 145px 105px; gap: 15px; align-items: center; padding: 14px 0; border-bottom: 1px solid #eef2f7; }
.product-image { display: grid; width: 68px; height: 68px; place-items: center; overflow: hidden; color: #93c5fd; background: #eff6ff; border-radius: 10px; font-size: 11px; font-weight: 800; }
.product-image img { width: 100%; height: 100%; object-fit: contain; }
.product-info a { color: #0f172a; font-weight: 700; }
.product-info p { margin: 7px 0 0; color: #64748b; font-size: 12px; }
.order-item > span { color: #64748b; }
.order-item > strong { color: #dc2626; text-align: right; }
.amount-row { display: flex; align-items: baseline; justify-content: flex-end; gap: 18px; padding-top: 22px; color: #64748b; }
.amount-row strong { color: #dc2626; font-size: 28px; }
.info-card dl { display: grid; grid-template-columns: 90px 1fr; gap: 15px; margin: 0; }
.info-card dt { color: #94a3b8; }
.info-card dd { margin: 0; color: #334155; }
.timeline-card { align-self: start; margin-bottom: 0; }
.timeline-card :deep(.el-timeline) { padding-left: 6px; }
.timeline-card b { color: #0f172a; }
.timeline-card p { margin: 6px 0; color: #64748b; font-size: 13px; }
.timeline-card span { color: #94a3b8; font-size: 12px; }
.time-summary { padding-top: 18px; border-top: 1px solid #eef2f7; }
.time-summary div { display: grid; gap: 5px; margin: 12px 0; }
.time-summary b { color: #475569; font-size: 13px; }
@media (max-width: 900px) {
  .detail-grid { grid-template-columns: 1fr; }
  .status-card { align-items: start; flex-direction: column; gap: 22px; }
}
@media (max-width: 640px) {
  .order-item { grid-template-columns: 60px 1fr; }
  .order-item > span, .order-item > strong { grid-column: 2; text-align: left; }
  .status-actions { align-items: stretch; flex-direction: column; width: 100%; }
}
</style>
