<script setup>
import { computed, onMounted, ref } from 'vue'
import {
  getDashboardSalesTrend,
  getDashboardSummary,
  getDashboardTopProducts,
} from '../../api/admin'
import { getHotProducts } from '../../api/catalog'
import { formatDateTime, formatMoney } from '../../utils/shop'

const loading = ref(false)
const errorMessage = ref('')
const summary = ref(null)
const hotLoading = ref(false)
const hotProducts = ref([])
const hotError = ref('')
const trendLoading = ref(false)
const salesTrend = ref([])
const trendError = ref('')
const topLoading = ref(false)
const topProducts = ref([])
const topError = ref('')

const maxTrendOrders = computed(() => (
  Math.max(1, ...salesTrend.value.map((point) => Number(point.orderCount) || 0), 1)
))

const loadSummary = async () => {
  if (loading.value) return
  loading.value = true
  errorMessage.value = ''
  try {
    summary.value = await getDashboardSummary()
  } catch (error) {
    errorMessage.value = error.message || '运营数据加载失败'
  } finally {
    loading.value = false
  }
}

const loadSalesTrend = async () => {
  if (trendLoading.value) return
  trendLoading.value = true
  trendError.value = ''
  try {
    const data = await getDashboardSalesTrend(7)
    salesTrend.value = Array.isArray(data?.points) ? data.points : []
  } catch (error) {
    salesTrend.value = []
    trendError.value = error.message || '销售趋势加载失败'
  } finally {
    trendLoading.value = false
  }
}

const loadTopProducts = async () => {
  if (topLoading.value) return
  topLoading.value = true
  topError.value = ''
  try {
    const data = await getDashboardTopProducts(10)
    topProducts.value = Array.isArray(data?.items) ? data.items : []
  } catch (error) {
    topProducts.value = []
    topError.value = error.message || '销量排行加载失败'
  } finally {
    topLoading.value = false
  }
}

const loadHotProducts = async () => {
  if (hotLoading.value) return
  hotLoading.value = true
  hotError.value = ''
  try {
    hotProducts.value = await getHotProducts({ days: 30, limit: 5 })
  } catch (error) {
    hotProducts.value = []
    hotError.value = error.message || '热销商品加载失败'
  } finally {
    hotLoading.value = false
  }
}

const overviewCards = computed(() => [
  {
    label: '买家总数',
    value: summary.value?.userCount ?? 0,
    note: '当前商城消费者账号',
    tone: 'blue',
  },
  {
    label: '商品总数',
    value: summary.value?.productCount ?? 0,
    note: `其中 ${summary.value?.onSaleProductCount ?? 0} 件正在销售`,
    tone: 'violet',
  },
  {
    label: '订单总数',
    value: summary.value?.orderCount ?? 0,
    note: '包含全部订单状态',
    tone: 'amber',
  },
  {
    label: '低库存 SKU',
    value: summary.value?.lowStockSkuCount ?? 0,
    note: '在售且库存不高于 10 件',
    tone: 'red',
  },
  {
    label: '有效销售额',
    value: formatMoney(summary.value?.paidSalesAmount),
    note: '已支付、已发货和已完成订单',
    tone: 'emerald',
  },
])

const orderStatuses = computed(() => [
  {
    label: '待支付',
    value: summary.value?.pendingPaymentOrderCount ?? 0,
    className: 'warning',
  },
  {
    label: '待发货',
    value: summary.value?.paidOrderCount ?? 0,
    className: 'primary',
  },
  {
    label: '待收货',
    value: summary.value?.shippedOrderCount ?? 0,
    className: 'success',
  },
  {
    label: '已完成',
    value: summary.value?.completedOrderCount ?? 0,
    className: 'completed',
  },
  {
    label: '已取消',
    value: summary.value?.canceledOrderCount ?? 0,
    className: 'muted',
  },
])

const refreshAll = () => {
  loadSummary()
  loadSalesTrend()
  loadTopProducts()
  loadHotProducts()
}

onMounted(refreshAll)
</script>

<template>
  <section class="admin-dashboard">
    <header class="dashboard-hero">
      <div>
        <p>SELLER CENTER · LIVE OVERVIEW</p>
        <h1>商家运营控制台</h1>
        <span>实时查看买家、商品、订单与有效销售额，快速进入日常经营模块。</span>
      </div>
      <div class="hero-actions">
        <small v-if="summary?.generatedAt">
          更新于 {{ formatDateTime(summary.generatedAt) }}
        </small>
        <el-button
          type="primary"
          plain
          :loading="loading || trendLoading || topLoading"
          @click="refreshAll"
        >
          刷新数据
        </el-button>
      </div>
    </header>

    <el-alert
      v-if="errorMessage"
      class="dashboard-error"
      type="error"
      :title="errorMessage"
      :closable="false"
      show-icon
    >
      <template #default>
        <el-button link type="primary" @click="loadSummary">重新加载</el-button>
      </template>
    </el-alert>

    <div class="overview-grid" :aria-busy="loading">
      <article
        v-for="card in overviewCards"
        :key="card.label"
        class="overview-card"
        :class="`tone-${card.tone}`"
      >
        <span>{{ card.label }}</span>
        <strong>{{ loading && !summary ? '—' : card.value }}</strong>
        <small>{{ card.note }}</small>
      </article>
    </div>

    <section class="order-overview">
      <div class="section-heading">
        <div>
          <p>ORDER PIPELINE</p>
          <h2>订单状态分布</h2>
        </div>
        <RouterLink to="/seller/orders">处理订单 →</RouterLink>
      </div>
      <div class="status-grid">
        <div
          v-for="status in orderStatuses"
          :key="status.label"
          class="status-item"
          :class="status.className"
        >
          <span>{{ status.label }}</span>
          <strong>{{ loading && !summary ? '—' : status.value }}</strong>
        </div>
      </div>
    </section>

    <section class="trend-overview">
      <div class="section-heading">
        <div>
          <p>SALES TREND</p>
          <h2>近 7 日销售趋势</h2>
        </div>
      </div>
      <div v-loading="trendLoading" class="trend-table">
        <div v-if="trendError" class="panel-error" role="alert">
          <span>{{ trendError }}</span>
          <el-button text type="primary" @click="loadSalesTrend">重试</el-button>
        </div>
        <div v-for="point in salesTrend" :key="point.date" class="trend-row">
          <span class="trend-date">{{ point.date }}</span>
          <div class="trend-bar-wrap" :title="`订单 ${point.orderCount}`">
            <i :style="{ width: `${Math.max(6, (Number(point.orderCount) / maxTrendOrders) * 100)}%` }" />
          </div>
          <span><small>订单</small><strong>{{ point.orderCount }}</strong></span>
          <span><small>销售额</small><strong>{{ formatMoney(point.salesAmount) }}</strong></span>
        </div>
        <el-empty
          v-if="!trendLoading && !trendError && !salesTrend.length"
          description="暂无趋势数据"
          :image-size="70"
        />
      </div>
    </section>

    <section class="hot-overview">
      <div class="section-heading">
        <div>
          <p>TOP PRODUCTS</p>
          <h2>销量 Top 商品</h2>
        </div>
        <RouterLink to="/seller/products">管理商品 →</RouterLink>
      </div>
      <div v-loading="topLoading" class="hot-table">
        <div v-if="topError" class="panel-error" role="alert">
          <span>{{ topError }}</span>
          <el-button text type="primary" @click="loadTopProducts">重试</el-button>
        </div>
        <div v-for="(product, index) in topProducts" :key="product.productId" class="hot-row hot-row--top">
          <b>{{ index + 1 }}</b>
          <span>
            <strong>{{ product.productName }}</strong>
            <small>商品编号 {{ product.productId }}</small>
          </span>
          <span><small>销量</small><strong>{{ product.soldQuantity }} 件</strong></span>
          <span><small>销售额</small><strong>{{ formatMoney(product.salesAmount) }}</strong></span>
        </div>
        <el-empty
          v-if="!topLoading && !topError && !topProducts.length"
          description="暂无销量排行"
          :image-size="70"
        />
      </div>
    </section>

    <section class="hot-overview">
      <div class="section-heading">
        <div>
          <p>TOP SELLING</p>
          <h2>近 30 天热销商品</h2>
        </div>
        <RouterLink to="/seller/products">管理商品 →</RouterLink>
      </div>
      <div v-loading="hotLoading" class="hot-table">
        <div v-if="hotError" class="panel-error" role="alert">
          <span>{{ hotError }}</span>
          <el-button text type="primary" @click="loadHotProducts">重试</el-button>
        </div>
        <div v-for="(product, index) in hotProducts" :key="product.productId" class="hot-row hot-row--selling">
          <b>{{ index + 1 }}</b>
          <span>
            <strong>{{ product.name }}</strong>
            <small>{{ product.subtitle || '暂无副标题' }}</small>
          </span>
          <span><small>有效销量</small><strong>{{ product.salesQuantity }} 件</strong></span>
          <span><small>销售额</small><strong>{{ formatMoney(product.salesAmount) }}</strong></span>
          <span><small>可售库存</small><strong>{{ product.totalStock }}</strong></span>
        </div>
        <el-empty
          v-if="!hotLoading && !hotError && !hotProducts.length"
          description="暂无有效销售数据"
          :image-size="70"
        />
      </div>
    </section>

    <section class="quick-entry">
      <div class="section-heading">
        <div>
          <p>QUICK ENTRY</p>
          <h2>快捷管理</h2>
        </div>
      </div>
      <div class="entry-grid">
        <RouterLink to="/seller/categories">
          <b>01</b><h3>分类管理</h3><p>维护分类结构、排序与启用状态。</p><span>进入管理 →</span>
        </RouterLink>
        <RouterLink to="/seller/products">
          <b>02</b><h3>商品与 SKU</h3><p>维护商品、规格、价格与库存。</p><span>进入管理 →</span>
        </RouterLink>
        <RouterLink to="/seller/inventory">
          <b>03</b><h3>库存预警</h3><p>定位在售商品的低库存和缺货 SKU。</p><span>查看预警 →</span>
        </RouterLink>
        <RouterLink to="/seller/orders">
          <b>04</b><h3>订单管理</h3><p>查询订单并完成发货与取消操作。</p><span>进入管理 →</span>
        </RouterLink>
        <RouterLink to="/seller/logs">
          <b>05</b><h3>操作日志</h3><p>审计商家工作台关键业务操作结果。</p><span>查看日志 →</span>
        </RouterLink>
        <RouterLink to="/seller/reviews">
          <b>06</b><h3>评价管理</h3><p>查看消费者评价并处理不适合公开展示的内容。</p><span>审核评价 →</span>
        </RouterLink>
      </div>
    </section>
  </section>
</template>

<style scoped>
.admin-dashboard {
  max-width: 1240px;
  margin: 0 auto;
}

.trend-overview {
  margin: 22px 0;
  padding: 24px;
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 18px;
}

.trend-table {
  display: grid;
  gap: 12px;
}

.panel-error {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 14px;
  color: #b45309;
  background: #fffbeb;
  border: 1px solid #fde68a;
  border-radius: 10px;
  font-size: 13px;
}

.trend-row {
  display: grid;
  grid-template-columns: 110px minmax(120px, 1fr) 90px 120px;
  gap: 14px;
  align-items: center;
}

.trend-date {
  color: #475569;
  font-size: 13px;
  font-weight: 600;
}

.trend-bar-wrap {
  height: 10px;
  overflow: hidden;
  background: #e2e8f0;
  border-radius: 999px;
}

.trend-bar-wrap i {
  display: block;
  height: 100%;
  background: linear-gradient(90deg, #2563eb, #60a5fa);
  border-radius: inherit;
}

.trend-row small {
  display: block;
  color: #94a3b8;
  font-size: 11px;
}

.trend-row strong {
  color: #0f172a;
  font-size: 14px;
}

.dashboard-hero {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 32px;
  padding: 32px 36px;
  color: #fff;
  background:
    radial-gradient(circle at 88% 12%, rgba(96, 165, 250, 0.35), transparent 28%),
    linear-gradient(125deg, #0f172a, #1e3a8a 65%, #2563eb);
  border-radius: 20px;
  box-shadow: 0 18px 45px rgba(15, 23, 42, 0.18);
}

.dashboard-hero p,
.section-heading p {
  margin: 0 0 8px;
  color: #93c5fd;
  font-size: 12px;
  font-weight: 800;
  letter-spacing: 0.14em;
}

.dashboard-hero h1 {
  margin: 0 0 10px;
  font-size: 32px;
}

.dashboard-hero span {
  color: #cbd5e1;
}

.hero-actions {
  display: flex;
  flex: 0 0 auto;
  flex-direction: column;
  align-items: flex-end;
  gap: 10px;
}

.hero-actions small {
  color: #bfdbfe;
}

.dashboard-error {
  margin-top: 18px;
}

.overview-grid {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 16px;
  margin-top: 20px;
}

.overview-card {
  position: relative;
  overflow: hidden;
  padding: 22px;
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 16px;
  box-shadow: 0 10px 28px rgba(15, 23, 42, 0.05);
}

.overview-card::after {
  position: absolute;
  top: -30px;
  right: -30px;
  width: 90px;
  height: 90px;
  background: currentColor;
  border-radius: 50%;
  content: "";
  opacity: 0.08;
}

.overview-card > span {
  color: #64748b;
  font-size: 13px;
  font-weight: 700;
}

.overview-card strong {
  display: block;
  margin: 12px 0 8px;
  color: #0f172a;
  font-size: 30px;
  line-height: 1;
}

.overview-card small {
  color: #94a3b8;
  line-height: 1.5;
}

.tone-blue { color: #2563eb; }
.tone-violet { color: #7c3aed; }
.tone-amber { color: #d97706; }
.tone-red { color: #dc2626; }
.tone-emerald { color: #059669; }

.order-overview,
.hot-overview,
.quick-entry {
  margin-top: 20px;
  padding: 24px;
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 18px;
  box-shadow: 0 10px 30px rgba(15, 23, 42, 0.04);
}

.hot-overview {
  container-type: inline-size;
  overflow: hidden;
}

.hot-table {
  min-height: 90px;
}

.hot-row {
  display: grid;
  align-items: center;
  gap: 16px;
  padding: 13px 14px;
  border-bottom: 1px solid #f1f5f9;
}

.hot-row--top {
  grid-template-columns: 42px minmax(150px, 1fr) minmax(76px, 96px) minmax(105px, 128px);
}

.hot-row--selling {
  grid-template-columns: 42px minmax(160px, 1fr) minmax(78px, 100px) minmax(110px, 132px) minmax(72px, 92px);
}

.hot-row:last-child {
  border-bottom: 0;
}

.hot-row > b {
  display: grid;
  width: 30px;
  height: 30px;
  color: #fff;
  background: #f97316;
  border-radius: 50%;
  place-items: center;
}

.hot-row > span {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 3px;
}

.hot-row > span:nth-child(n + 3) {
  align-items: flex-end;
  text-align: right;
}

.hot-row > span:nth-child(n + 3) strong {
  font-variant-numeric: tabular-nums;
}

.hot-row strong {
  overflow: hidden;
  color: #0f172a;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.hot-row small {
  overflow: hidden;
  color: #94a3b8;
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.section-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  margin-bottom: 18px;
}

.section-heading p {
  color: #3b82f6;
}

.section-heading h2 {
  margin: 0;
  color: #0f172a;
  font-size: 21px;
}

.section-heading a {
  flex: 0 0 auto;
  color: #2563eb;
  font-weight: 700;
  white-space: nowrap;
}

.status-grid {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 12px;
}

.status-item {
  padding: 16px;
  background: #f8fafc;
  border-left: 4px solid currentColor;
  border-radius: 10px;
}

.status-item span {
  color: #64748b;
  font-size: 13px;
}

.status-item strong {
  display: block;
  margin-top: 8px;
  color: #0f172a;
  font-size: 24px;
}

.status-item.warning { color: #f59e0b; }
.status-item.primary { color: #3b82f6; }
.status-item.success { color: #10b981; }
.status-item.completed { color: #0f766e; }
.status-item.muted { color: #94a3b8; }

.entry-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px;
}

.entry-grid a {
  min-height: 170px;
  padding: 20px;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 14px;
  transition: 0.2s ease;
}

.entry-grid a:hover {
  background: #fff;
  border-color: #60a5fa;
  box-shadow: 0 12px 26px rgba(37, 99, 235, 0.1);
  transform: translateY(-2px);
}

.entry-grid b {
  color: #bfdbfe;
  font-size: 32px;
}

.entry-grid h3 {
  margin: 10px 0 6px;
  color: #0f172a;
}

.entry-grid p {
  min-height: 42px;
  color: #64748b;
  font-size: 13px;
  line-height: 1.55;
}

.entry-grid span {
  color: #2563eb;
  font-size: 13px;
  font-weight: 700;
}

@media (max-width: 1100px) {
  .overview-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .entry-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 760px) {
  .dashboard-hero {
    align-items: flex-start;
    flex-direction: column;
  }

  .hero-actions {
    align-items: flex-start;
  }

  .overview-grid,
  .entry-grid {
    grid-template-columns: 1fr;
  }

  .status-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

}

@container (max-width: 680px) {
  .hot-row--top,
  .hot-row--selling {
    grid-template-columns: 36px minmax(0, 1fr) minmax(80px, 100px);
    gap: 10px;
    padding-inline: 12px;
  }

  .hot-row--top > span:nth-child(4),
  .hot-row--selling > span:nth-child(n + 4) {
    display: none;
  }
}

@container (max-width: 460px) {
  .hot-row--top,
  .hot-row--selling {
    grid-template-columns: 34px minmax(0, 1fr);
  }

  .hot-row--top > span:nth-child(n + 3),
  .hot-row--selling > span:nth-child(n + 3) {
    display: none;
  }
}
</style>
