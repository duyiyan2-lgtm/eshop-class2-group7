<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  cancelAdminOrder,
  getAdminOrder,
  getAdminOrderLogs,
  getAdminOrders,
  shipAdminOrder,
} from '../../api/adminOrder'
import {
  ORDER_STATUS,
  formatDateTime,
  formatMoney,
  orderStatusInfo,
  specsText,
} from '../../utils/shop'

const orders = ref([])
const current = ref(1)
const size = 10
const total = ref(0)
const orderNo = ref('')
const statusFilter = ref('')
const loading = ref(false)
const operatingId = ref()

const detailVisible = ref(false)
const detailLoading = ref(false)
const detailOrder = ref(null)
const detailLogs = ref([])

const statusOptions = computed(() => (
  Object.entries(ORDER_STATUS).map(([value, info]) => ({ value, label: info.label }))
))

const itemCount = (order) => (
  (order.items || []).reduce((count, item) => count + Number(item.quantity || 0), 0)
)

const loadOrders = async () => {
  loading.value = true
  try {
    const page = await getAdminOrders({
      current: current.value,
      size,
      status: statusFilter.value || undefined,
      orderNo: orderNo.value.trim() || undefined,
    })
    orders.value = page.records || []
    total.value = page.total || 0
  } catch (error) {
    ElMessage.error(error.message || '订单加载失败')
  } finally {
    loading.value = false
  }
}

const search = () => {
  current.value = 1
  loadOrders()
}

const resetSearch = () => {
  orderNo.value = ''
  statusFilter.value = ''
  current.value = 1
  loadOrders()
}

const changePage = (page) => {
  current.value = page
  loadOrders()
}

const loadDetail = async (id) => {
  detailLoading.value = true
  try {
    const [order, logs] = await Promise.all([
      getAdminOrder(id),
      getAdminOrderLogs(id),
    ])
    detailOrder.value = order
    detailLogs.value = logs || []
  } catch (error) {
    ElMessage.error(error.message || '订单详情加载失败')
  } finally {
    detailLoading.value = false
  }
}

const openDetail = async (row) => {
  detailOrder.value = null
  detailLogs.value = []
  detailVisible.value = true
  await loadDetail(row.id)
}

const refreshAfterAction = async (id) => {
  await loadOrders()
  if (detailVisible.value && detailOrder.value?.id === id) {
    await loadDetail(id)
  }
}

const ship = async (order) => {
  try {
    await ElMessageBox.confirm(
      `确定订单 ${order.orderNo} 已完成出库并发货吗？`,
      '确认发货',
      {
        type: 'success',
        confirmButtonText: '确认发货',
        cancelButtonText: '暂不发货',
      },
    )
    operatingId.value = order.id
    await shipAdminOrder(order.id)
    ElMessage.success('订单已发货，等待消费者确认收货')
    await refreshAfterAction(order.id)
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      ElMessage.error(error.message || '订单发货失败')
    }
  } finally {
    operatingId.value = undefined
  }
}

const cancel = async (order) => {
  try {
    await ElMessageBox.confirm(
      `确定取消订单 ${order.orderNo} 吗？取消后将自动恢复商品库存。`,
      '取消订单',
      {
        type: 'warning',
        confirmButtonText: '确认取消',
        cancelButtonText: '保留订单',
      },
    )
    operatingId.value = order.id
    await cancelAdminOrder(order.id)
    ElMessage.success('订单已取消，库存已恢复')
    await refreshAfterAction(order.id)
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      ElMessage.error(error.message || '订单取消失败')
    }
  } finally {
    operatingId.value = undefined
  }
}

onMounted(loadOrders)
</script>

<template>
  <section class="admin-page">
    <header class="admin-page-heading">
      <div>
        <p>ORDER FULFILLMENT</p>
        <h1>订单管理</h1>
        <span>查询全站订单，核对收货信息，并处理订单取消与发货。</span>
      </div>
      <div class="heading-summary">
        <strong>{{ total }}</strong>
        <span>笔订单</span>
      </div>
    </header>

    <section class="filter-card">
      <el-input
        v-model="orderNo"
        clearable
        placeholder="输入完整或部分订单号"
        @keyup.enter="search"
      />
      <el-select v-model="statusFilter" clearable placeholder="全部状态">
        <el-option
          v-for="option in statusOptions"
          :key="option.value"
          :label="option.label"
          :value="option.value"
        />
      </el-select>
      <el-button type="primary" @click="search">查询</el-button>
      <el-button @click="resetSearch">重置</el-button>
      <el-button @click="loadOrders">刷新</el-button>
    </section>

    <section class="table-card">
      <el-table
        :data="orders"
        v-loading="loading"
        stripe
        empty-text="没有符合条件的订单"
      >
        <el-table-column label="订单信息" min-width="235">
          <template #default="{ row }">
            <div class="order-number">
              <b>{{ row.orderNo }}</b>
              <span>订单 ID：{{ row.id }} · 用户 ID：{{ row.userId }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="收货人" min-width="190">
          <template #default="{ row }">
            <div class="receiver">
              <b>{{ row.receiverName }}</b>
              <span>{{ row.receiverPhone }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="商品" width="95" align="center">
          <template #default="{ row }">{{ itemCount(row) }} 件</template>
        </el-table-column>
        <el-table-column label="订单金额" width="125">
          <template #default="{ row }">
            <strong class="price">{{ formatMoney(row.totalAmount) }}</strong>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="105">
          <template #default="{ row }">
            <el-tag :type="orderStatusInfo(row.status).type">
              {{ orderStatusInfo(row.status).label }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="下单时间" width="175">
          <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="205" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">查看详情</el-button>
            <el-button
              v-if="row.status === 'PAID'"
              link
              type="success"
              :loading="operatingId === row.id"
              @click="ship(row)"
            >
              发货
            </el-button>
            <el-button
              v-if="row.status === 'PENDING_PAYMENT'"
              link
              type="danger"
              :loading="operatingId === row.id"
              @click="cancel(row)"
            >
              取消
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination">
        <el-pagination
          background
          layout="total, prev, pager, next"
          :current-page="current"
          :page-size="size"
          :total="total"
          @current-change="changePage"
        />
      </div>
    </section>

    <el-drawer
      v-model="detailVisible"
      size="760px"
      destroy-on-close
      class="order-detail-drawer"
    >
      <template #header>
        <div class="drawer-heading">
          <div>
            <p>ORDER DETAIL</p>
            <h2>{{ detailOrder?.orderNo || '订单详情' }}</h2>
          </div>
          <el-tag
            v-if="detailOrder"
            :type="orderStatusInfo(detailOrder.status).type"
            size="large"
          >
            {{ orderStatusInfo(detailOrder.status).label }}
          </el-tag>
        </div>
      </template>

      <div v-loading="detailLoading" class="detail-content">
        <template v-if="detailOrder">
          <div class="detail-actions">
            <el-button
              v-if="detailOrder.status === 'PAID'"
              type="success"
              :loading="operatingId === detailOrder.id"
              @click="ship(detailOrder)"
            >
              确认发货
            </el-button>
            <el-button
              v-if="detailOrder.status === 'PENDING_PAYMENT'"
              type="danger"
              plain
              :loading="operatingId === detailOrder.id"
              @click="cancel(detailOrder)"
            >
              取消订单
            </el-button>
            <span v-if="detailOrder.status === 'SHIPPED'">已发货，等待消费者确认收货。</span>
            <span v-else-if="detailOrder.status === 'COMPLETED'">订单交易已完成。</span>
            <span v-else-if="detailOrder.status === 'CANCELED'">订单已取消，无需继续处理。</span>
          </div>

          <section class="detail-card">
            <h3>商品明细</h3>
            <article
              v-for="item in detailOrder.items"
              :key="item.id"
              class="order-item"
            >
              <div class="product-image">
                <img v-if="item.productImage" :src="item.productImage" :alt="item.productName" />
                <span v-else>E-Shop</span>
              </div>
              <div class="product-info">
                <b>{{ item.productName }}</b>
                <span>{{ specsText(item.skuSpecs) || '默认规格' }}</span>
              </div>
              <span>{{ formatMoney(item.price) }} × {{ item.quantity }}</span>
              <strong>{{ formatMoney(item.subtotal) }}</strong>
            </article>
            <footer class="amount-row">
              <span>共 {{ itemCount(detailOrder) }} 件，订单总额</span>
              <strong>{{ formatMoney(detailOrder.totalAmount) }}</strong>
            </footer>
          </section>

          <div class="detail-columns">
            <section class="detail-card">
              <h3>收货与订单信息</h3>
              <dl class="info-list">
                <dt>收货人</dt><dd>{{ detailOrder.receiverName }}</dd>
                <dt>联系电话</dt><dd>{{ detailOrder.receiverPhone }}</dd>
                <dt>收货地址</dt><dd>{{ detailOrder.receiverAddress }}</dd>
                <dt>订单备注</dt><dd>{{ detailOrder.remark || '无' }}</dd>
                <dt>下单时间</dt><dd>{{ formatDateTime(detailOrder.createdAt) }}</dd>
                <dt v-if="detailOrder.paidAt">支付时间</dt>
                <dd v-if="detailOrder.paidAt">{{ formatDateTime(detailOrder.paidAt) }}</dd>
                <dt v-if="detailOrder.shippedAt">发货时间</dt>
                <dd v-if="detailOrder.shippedAt">{{ formatDateTime(detailOrder.shippedAt) }}</dd>
                <dt v-if="detailOrder.completedAt">完成时间</dt>
                <dd v-if="detailOrder.completedAt">{{ formatDateTime(detailOrder.completedAt) }}</dd>
                <dt v-if="detailOrder.canceledAt">取消时间</dt>
                <dd v-if="detailOrder.canceledAt">{{ formatDateTime(detailOrder.canceledAt) }}</dd>
              </dl>
            </section>

            <section class="detail-card timeline-card">
              <h3>状态日志</h3>
              <el-timeline>
                <el-timeline-item
                  v-for="log in [...detailLogs].reverse()"
                  :key="log.id"
                  :timestamp="formatDateTime(log.createdAt)"
                  placement="top"
                  type="primary"
                >
                  <b>{{ orderStatusInfo(log.toStatus).label }}</b>
                  <p>{{ log.remark || '状态发生变化' }}</p>
                  <span v-if="log.operatorName">操作人：{{ log.operatorName }}</span>
                </el-timeline-item>
              </el-timeline>
              <el-empty v-if="!detailLogs.length" description="暂无状态日志" :image-size="70" />
            </section>
          </div>
        </template>
      </div>
    </el-drawer>
  </section>
</template>

<style scoped>
.admin-page { max-width: 1380px; margin: 0 auto; }
.admin-page-heading { display: flex; align-items: end; justify-content: space-between; margin-bottom: 20px; }
.admin-page-heading p, .drawer-heading p { margin: 0 0 6px; color: #2563eb; font-size: 12px; font-weight: 800; letter-spacing: .13em; }
.admin-page-heading h1 { margin: 0; color: #0f172a; font-size: 30px; }
.admin-page-heading > div:first-child > span { display: block; margin-top: 8px; color: #64748b; }
.heading-summary { display: flex; align-items: baseline; gap: 7px; padding: 12px 18px; color: #64748b; background: #fff; border: 1px solid #e2e8f0; border-radius: 12px; }
.heading-summary strong { color: #2563eb; font-size: 25px; }
.filter-card { display: grid; grid-template-columns: minmax(260px, 1fr) 190px auto auto auto 1fr; gap: 10px; align-items: center; margin-bottom: 16px; padding: 16px; background: #fff; border: 1px solid #e2e8f0; border-radius: 14px; }
.table-card { overflow: hidden; background: #fff; border: 1px solid #e2e8f0; border-radius: 16px; box-shadow: 0 10px 30px rgba(15, 23, 42, .04); }
.order-number, .receiver { display: grid; gap: 6px; }
.order-number b { color: #0f172a; font-size: 13px; }
.order-number span, .receiver span { color: #94a3b8; font-size: 12px; }
.receiver b { color: #334155; }
.price { color: #dc2626; font-size: 16px; }
.pagination { display: flex; justify-content: flex-end; padding: 18px 20px; }
.drawer-heading { display: flex; align-items: end; justify-content: space-between; width: 100%; padding-right: 16px; }
.drawer-heading h2 { margin: 0; color: #0f172a; font-size: 20px; }
.detail-content { min-height: 420px; }
.detail-actions { display: flex; align-items: center; min-height: 34px; margin-bottom: 16px; padding: 12px 15px; color: #64748b; background: #eff6ff; border: 1px solid #dbeafe; border-radius: 11px; font-size: 13px; }
.detail-actions span { margin-left: 4px; }
.detail-card { margin-bottom: 16px; padding: 20px; background: #fff; border: 1px solid #e2e8f0; border-radius: 14px; }
.detail-card h3 { margin: 0 0 17px; color: #0f172a; font-size: 16px; }
.order-item { display: grid; grid-template-columns: 58px minmax(0, 1fr) 130px 90px; gap: 12px; align-items: center; padding: 12px 0; border-bottom: 1px solid #eef2f7; }
.product-image { display: grid; width: 54px; height: 54px; place-items: center; overflow: hidden; color: #93c5fd; background: #eff6ff; border-radius: 9px; font-size: 9px; font-weight: 800; }
.product-image img { width: 100%; height: 100%; object-fit: contain; }
.product-info { display: grid; min-width: 0; gap: 6px; }
.product-info b { overflow: hidden; color: #0f172a; text-overflow: ellipsis; white-space: nowrap; }
.product-info span, .order-item > span { color: #64748b; font-size: 12px; }
.order-item > strong { color: #dc2626; text-align: right; }
.amount-row { display: flex; align-items: baseline; justify-content: flex-end; gap: 14px; padding-top: 18px; color: #64748b; }
.amount-row strong { color: #dc2626; font-size: 24px; }
.detail-columns { display: grid; grid-template-columns: 1.06fr .94fr; gap: 16px; align-items: start; }
.info-list { display: grid; grid-template-columns: 78px minmax(0, 1fr); gap: 12px; margin: 0; }
.info-list dt { color: #94a3b8; font-size: 13px; }
.info-list dd { margin: 0; color: #334155; font-size: 13px; line-height: 1.5; word-break: break-all; }
.timeline-card :deep(.el-timeline) { padding-left: 7px; }
.timeline-card b { color: #0f172a; font-size: 13px; }
.timeline-card p { margin: 5px 0; color: #64748b; font-size: 12px; }
.timeline-card span { color: #94a3b8; font-size: 11px; }
@media (max-width: 1000px) {
  .filter-card { grid-template-columns: 1fr 1fr auto auto auto; }
}
@media (max-width: 760px) {
  .admin-page-heading { align-items: start; flex-direction: column; gap: 14px; }
  .filter-card { grid-template-columns: 1fr 1fr; }
  .detail-columns { grid-template-columns: 1fr; }
  .order-item { grid-template-columns: 58px 1fr; }
  .order-item > span, .order-item > strong { grid-column: 2; text-align: left; }
}
</style>
