<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRouter } from 'vue-router'
import {
  clearBrowseHistory,
  getBrowseHistory,
  removeBrowseHistory,
} from '../../api/browseHistory'
import { formatDateTime, formatMoney } from '../../utils/shop'

const router = useRouter()
const records = ref([])
const current = ref(1)
const size = ref(12)
const total = ref(0)
const loading = ref(false)
const clearing = ref(false)
const errorMessage = ref('')
const removingIds = ref(new Set())
const failedImages = ref(new Set())
let requestSequence = 0

const loadHistory = async () => {
  const requestId = ++requestSequence
  loading.value = true
  errorMessage.value = ''
  try {
    const page = await getBrowseHistory({ current: current.value, size: size.value })
    if (requestId !== requestSequence) return
    records.value = page.records || []
    total.value = Number(page.total || 0)
    if (!records.value.length && total.value > 0 && current.value > 1) {
      current.value -= 1
      await loadHistory()
    }
  } catch (error) {
    if (requestId === requestSequence) {
      errorMessage.value = error.message || '浏览历史加载失败'
    }
  } finally {
    if (requestId === requestSequence) loading.value = false
  }
}

const changePage = (page) => {
  current.value = page
  loadHistory()
}

const changeSize = (pageSize) => {
  size.value = pageSize
  current.value = 1
  loadHistory()
}

const remove = async (product) => {
  try {
    await ElMessageBox.confirm(
      `确定删除“${product.name}”的浏览记录吗？`,
      '删除浏览记录',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' },
    )
  } catch {
    return
  }

  const productId = product.productId
  if (removingIds.value.has(productId)) return
  removingIds.value = new Set(removingIds.value).add(productId)
  try {
    await removeBrowseHistory(productId)
    ElMessage.success('已删除浏览记录')
    await loadHistory()
  } catch (error) {
    ElMessage.error(error.message || '删除失败')
  } finally {
    const next = new Set(removingIds.value)
    next.delete(productId)
    removingIds.value = next
  }
}

const clearAll = async () => {
  try {
    await ElMessageBox.confirm(
      '确定清空全部浏览历史吗？',
      '清空浏览历史',
      { type: 'warning', confirmButtonText: '清空', cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  clearing.value = true
  try {
    await clearBrowseHistory()
    ElMessage.success('浏览历史已清空')
    current.value = 1
    await loadHistory()
  } catch (error) {
    ElMessage.error(error.message || '清空失败')
  } finally {
    clearing.value = false
  }
}

const markImageFailed = (productId) => {
  failedImages.value = new Set(failedImages.value).add(productId)
}

onMounted(loadHistory)
</script>

<template>
  <section class="history-page">
    <header class="page-heading">
      <div>
        <span>BROWSE HISTORY</span>
        <h1>浏览历史</h1>
        <p>记录你最近看过的在售商品，方便快速回访。</p>
      </div>
      <div class="heading-actions">
        <el-button :loading="clearing" plain @click="clearAll">清空历史</el-button>
        <el-button type="primary" plain @click="router.push({ name: 'pc-products' })">
          继续逛商城
        </el-button>
      </div>
    </header>

    <el-alert
      v-if="errorMessage"
      :title="errorMessage"
      type="error"
      show-icon
      :closable="false"
      class="error-alert"
    >
      <template #default>
        <el-button link type="primary" @click="loadHistory">重新加载</el-button>
      </template>
    </el-alert>

    <div v-loading="loading" class="history-content">
      <el-empty
        v-if="!loading && !records.length && !errorMessage"
        description="还没有浏览记录"
      >
        <el-button type="primary" @click="router.push({ name: 'pc-products' })">
          去逛逛
        </el-button>
      </el-empty>

      <div v-else class="product-grid">
        <article v-for="product in records" :key="product.productId" class="product-card">
          <button
            type="button"
            class="product-image"
            @click="router.push({ name: 'pc-product-detail', params: { id: product.productId } })"
          >
            <img
              v-if="product.mainImage && !failedImages.has(product.productId)"
              :src="product.mainImage"
              :alt="product.name"
              @error="markImageFailed(product.productId)"
            />
            <span v-else>E-Shop</span>
          </button>
          <div class="product-body">
            <h2 @click="router.push({ name: 'pc-product-detail', params: { id: product.productId } })">
              {{ product.name }}
            </h2>
            <p>{{ product.subtitle || '品质商品，放心选购' }}</p>
            <div class="price-row">
              <strong>{{ formatMoney(product.minPrice) }}</strong>
              <span>库存 {{ product.totalStock }}</span>
            </div>
            <small>浏览于 {{ formatDateTime(product.browsedAt) }}</small>
            <div class="card-actions">
              <el-button
                type="primary"
                @click="router.push({ name: 'pc-product-detail', params: { id: product.productId } })"
              >
                查看详情
              </el-button>
              <el-button
                :loading="removingIds.has(product.productId)"
                @click="remove(product)"
              >
                删除
              </el-button>
            </div>
          </div>
        </article>
      </div>
    </div>

    <div v-if="total > size" class="pagination">
      <el-pagination
        background
        layout="total, sizes, prev, pager, next"
        :total="total"
        :current-page="current"
        :page-size="size"
        :page-sizes="[12, 24, 36]"
        @current-change="changePage"
        @size-change="changeSize"
      />
    </div>
  </section>
</template>

<style scoped>
.history-page { width: min(1180px, 100%); margin: 0 auto; padding: 12px 0 48px; }
.page-heading { display: flex; align-items: flex-end; justify-content: space-between; gap: 20px; margin-bottom: 22px; }
.page-heading span { color: #93c5fd; font-size: 12px; font-weight: 800; letter-spacing: .12em; }
.page-heading h1 { margin: 8px 0; color: #0f172a; font-size: 30px; }
.page-heading p { margin: 0; color: #64748b; }
.heading-actions { display: flex; gap: 10px; }
.error-alert { margin-bottom: 16px; }
.product-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(250px, 1fr)); gap: 18px; }
.product-card { overflow: hidden; background: #fff; border: 1px solid #e2e8f0; border-radius: 18px; box-shadow: 0 10px 28px rgba(15, 23, 42, .05); }
.product-image { display: grid; width: 100%; height: 180px; place-items: center; padding: 0; border: 0; background: #f8fafc; cursor: pointer; }
.product-image img { width: 100%; height: 100%; object-fit: contain; }
.product-image span { color: #93c5fd; font-weight: 800; }
.product-body { display: grid; gap: 10px; padding: 16px; }
.product-body h2 { margin: 0; color: #0f172a; font-size: 16px; cursor: pointer; }
.product-body p { margin: 0; color: #64748b; font-size: 13px; min-height: 36px; }
.price-row { display: flex; align-items: baseline; justify-content: space-between; }
.price-row strong { color: #dc2626; font-size: 20px; }
.price-row span, .product-body small { color: #94a3b8; font-size: 12px; }
.card-actions { display: flex; gap: 8px; }
.pagination { display: flex; justify-content: center; margin-top: 24px; }
</style>
