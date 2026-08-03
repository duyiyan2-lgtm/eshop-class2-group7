<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getInventoryAlerts } from '../../api/admin'
import { formatMoney, specsText } from '../../utils/shop'

const alerts = ref([])
const current = ref(1)
const size = 20
const total = ref(0)
const threshold = ref(10)
const keyword = ref('')
const loading = ref(false)
const failedImages = ref(new Set())
let requestSequence = 0

const loadAlerts = async () => {
  const requestId = ++requestSequence
  loading.value = true
  try {
    const page = await getInventoryAlerts({
      current: current.value,
      size,
      threshold: threshold.value,
      keyword: keyword.value.trim() || undefined,
    })
    if (requestId !== requestSequence) return
    alerts.value = Array.isArray(page?.records) ? page.records : []
    total.value = Number(page?.total) || 0
  } catch (error) {
    if (requestId !== requestSequence) return
    alerts.value = []
    total.value = 0
    ElMessage.error(error.message || '库存预警加载失败')
  } finally {
    if (requestId === requestSequence) loading.value = false
  }
}

const search = () => {
  current.value = 1
  void loadAlerts()
}

const reset = () => {
  keyword.value = ''
  threshold.value = 10
  current.value = 1
  void loadAlerts()
}

const changePage = (page) => {
  current.value = page
  void loadAlerts()
}

const stockTagType = (stock) => (Number(stock) <= 0 ? 'danger' : 'warning')

const markImageFailed = (skuId) => {
  failedImages.value = new Set([...failedImages.value, skuId])
}

onMounted(loadAlerts)
</script>

<template>
  <section class="inventory-page">
    <header class="page-heading">
      <div>
        <p>INVENTORY ALERT</p>
        <h1>库存预警</h1>
        <span>查看当前在售商品中库存不足的启用 SKU，及时安排补货。</span>
      </div>
      <div class="heading-summary">
        <strong>{{ total }}</strong>
        <span>个低库存 SKU</span>
      </div>
    </header>

    <el-alert
      title="预警范围只包含“在售商品 + 启用 SKU”，不会把草稿或已下架商品计入紧急补货。"
      type="warning"
      show-icon
      :closable="false"
      class="scope-tip"
    />

    <section class="filter-card">
      <el-input
        v-model="keyword"
        clearable
        placeholder="搜索商品名称或 SKU 编码"
        @keyup.enter="search"
      />
      <label>
        <span>库存不高于</span>
        <el-input-number
          v-model="threshold"
          :min="0"
          :max="100000"
          :step="1"
          controls-position="right"
        />
      </label>
      <el-button type="primary" @click="search">查询</el-button>
      <el-button @click="reset">重置</el-button>
      <el-button :loading="loading" @click="loadAlerts">刷新</el-button>
    </section>

    <section class="table-card">
      <el-table
        v-loading="loading"
        :data="alerts"
        stripe
        empty-text="当前筛选范围内没有低库存 SKU"
      >
        <el-table-column label="商品" min-width="260">
          <template #default="{ row }">
            <div class="product-cell">
              <div class="product-image">
                <img
                  v-if="row.mainImage && !failedImages.has(row.skuId)"
                  :src="row.mainImage"
                  :alt="row.productName"
                  @error="markImageFailed(row.skuId)"
                />
                <span v-else>E-Shop</span>
              </div>
              <div>
                <b>{{ row.productName }}</b>
                <span>商品 ID：{{ row.productId }}</span>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="SKU" min-width="220">
          <template #default="{ row }">
            <div class="sku-cell">
              <b>{{ row.skuCode }}</b>
              <span>{{ specsText(row.specsJson) || '默认规格' }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="销售价" width="130">
          <template #default="{ row }">
            <strong class="price">{{ formatMoney(row.price) }}</strong>
          </template>
        </el-table-column>
        <el-table-column label="当前库存" width="120" align="center">
          <template #default="{ row }">
            <el-tag :type="stockTagType(row.stock)" effect="dark">
              {{ Number(row.stock) <= 0 ? '已缺货' : `${row.stock} 件` }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="110" align="center">
          <template #default>
            <el-tag type="success">在售 / 启用</el-tag>
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
  </section>
</template>

<style scoped>
.inventory-page { max-width: 1280px; margin: 0 auto; }
.page-heading { display: flex; align-items: end; justify-content: space-between; gap: 24px; margin-bottom: 18px; }
.page-heading p { margin: 0 0 6px; color: #d97706; font-size: 12px; font-weight: 800; letter-spacing: .14em; }
.page-heading h1 { margin: 0; color: #0f172a; font-size: 30px; }
.page-heading > div:first-child > span { display: block; margin-top: 8px; color: #64748b; }
.heading-summary { display: flex; align-items: baseline; gap: 7px; padding: 12px 18px; color: #64748b; background: #fff; border: 1px solid #fde68a; border-radius: 12px; }
.heading-summary strong { color: #d97706; font-size: 25px; }
.scope-tip { margin-bottom: 16px; }
.filter-card { display: grid; grid-template-columns: minmax(260px, 1fr) auto auto auto auto; gap: 10px; align-items: center; margin-bottom: 16px; padding: 16px; background: #fff; border: 1px solid #e2e8f0; border-radius: 14px; }
.filter-card label { display: flex; align-items: center; gap: 10px; color: #64748b; font-size: 13px; white-space: nowrap; }
.filter-card :deep(.el-input-number) { width: 140px; }
.table-card { overflow: hidden; background: #fff; border: 1px solid #e2e8f0; border-radius: 16px; box-shadow: 0 10px 30px rgba(15, 23, 42, .04); }
.product-cell { display: flex; align-items: center; gap: 12px; min-width: 0; }
.product-image { display: grid; flex: 0 0 auto; width: 54px; height: 54px; place-items: center; overflow: hidden; color: #93c5fd; background: #eff6ff; border-radius: 10px; font-size: 9px; font-weight: 800; }
.product-image img { width: 100%; height: 100%; object-fit: contain; }
.product-cell > div:last-child, .sku-cell { display: grid; min-width: 0; gap: 6px; }
.product-cell b, .sku-cell b { overflow: hidden; color: #0f172a; text-overflow: ellipsis; white-space: nowrap; }
.product-cell span, .sku-cell span { color: #94a3b8; font-size: 12px; }
.price { color: #dc2626; font-size: 15px; }
.pagination { display: flex; justify-content: flex-end; padding: 18px 20px; }
@media (max-width: 820px) {
  .page-heading { align-items: flex-start; flex-direction: column; }
  .filter-card { grid-template-columns: 1fr 1fr; }
}
</style>
