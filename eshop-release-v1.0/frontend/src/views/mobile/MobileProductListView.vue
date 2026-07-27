<script setup>
import { computed, onActivated, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showToast } from 'vant'
import { getCategories, getProducts } from '../../api/catalog'
import { formatMoney } from '../../utils/shop'

const route = useRoute()
const router = useRouter()

const categories = ref([])
const records = ref([])
const total = ref(0)
const loading = ref(false)
const refreshing = ref(false)
const errorMessage = ref('')
const failedPage = ref(null)
const keywordInput = ref('')
const size = 10
const current = ref(1)
const finished = ref(false)

const categoryId = computed(() => {
  const value = Number(route.query.categoryId)
  return Number.isInteger(value) && value > 0 ? value : undefined
})

const keyword = computed(() => (
  typeof route.query.keyword === 'string' ? route.query.keyword.trim() : ''
))

const categoryOptions = computed(() => {
  const result = []
  const append = (list, depth = 0) => {
    ;(list || []).forEach((item) => {
      result.push({
        ...item,
        displayName: `${depth ? '—'.repeat(depth) + ' ' : ''}${item.name}`,
      })
      append(item.children, depth + 1)
    })
  }
  append(categories.value)
  return result
})

const activeCategoryName = computed(() => (
  categoryOptions.value.find((item) => item.id === categoryId.value)?.name || '全部商品'
))

let requestSequence = 0

const loadCategories = async () => {
  try {
    categories.value = await getCategories()
  } catch (error) {
    showToast({ type: 'fail', message: error.message || '商品分类加载失败' })
  }
}

const loadProducts = async ({ reset = false, page = reset ? 1 : current.value } = {}) => {
  if (loading.value && !reset) return false
  const requestId = ++requestSequence
  loading.value = true
  if (reset) errorMessage.value = ''

  try {
    const data = await getProducts({
      current: page,
      size,
      categoryId: categoryId.value,
      keyword: keyword.value || undefined,
    })
    if (requestId !== requestSequence) return false

    const list = (data.records || []).map((product) => ({ ...product, imageFailed: false }))
    records.value = reset ? list : records.value.concat(list)
    current.value = page
    total.value = data.total ?? records.value.length
    finished.value = list.length < size || records.value.length >= total.value
    failedPage.value = null
    errorMessage.value = ''
    return true
  } catch (error) {
    if (requestId !== requestSequence) return false
    errorMessage.value = error.message || '商品加载失败，请稍后重试'
    failedPage.value = page
    if (reset) records.value = []
    finished.value = true
    return false
  } finally {
    if (requestId === requestSequence) loading.value = false
  }
}

const replaceQuery = (changes) => {
  const query = { ...route.query, ...changes }
  Object.keys(query).forEach((key) => {
    if (query[key] === undefined || query[key] === null || query[key] === '') {
      delete query[key]
    }
  })
  router.push({ name: 'mobile-products', query })
}

const selectCategory = (id) => {
  replaceQuery({ categoryId: id || undefined })
}

const search = () => {
  const value = keywordInput.value.trim()
  replaceQuery({ keyword: value || undefined })
}

const resetFilters = () => {
  keywordInput.value = ''
  router.push({ name: 'mobile-products' })
}

const loadMore = () => {
  if (finished.value || loading.value || errorMessage.value) return
  loadProducts({ page: current.value + 1 })
}

const retryLoad = () => {
  const page = failedPage.value || 1
  finished.value = false
  loadProducts({ reset: page === 1, page })
}

const onRefresh = async () => {
  finished.value = false
  await loadProducts({ reset: true, page: 1 })
  refreshing.value = false
  if (!errorMessage.value) showToast('已刷新')
}

const openProduct = (id) => {
  router.push({ name: 'mobile-product-detail', params: { id } })
}

const handleImageError = (product) => {
  product.imageFailed = true
}

watch(
  () => [route.query.categoryId, route.query.keyword],
  () => {
    keywordInput.value = keyword.value
    finished.value = false
    loadProducts({ reset: true, page: 1 })
  },
)

onMounted(async () => {
  keywordInput.value = keyword.value
  await loadCategories()
  await loadProducts({ reset: true, page: 1 })
})

onActivated(() => {
  if (!records.value.length && !loading.value) {
    loadProducts({ reset: true, page: 1 })
  }
})
</script>

<template>
  <section class="mobile-products">
    <div class="search-bar">
      <van-search
        v-model="keywordInput"
        placeholder="搜索商品名称"
        shape="round"
        background="#f7f8fa"
        clearable
        @search="search"
        @clear="search"
      />
    </div>

    <div v-if="categoryOptions.length" class="category-bar">
      <button
        type="button"
        class="category-chip"
        :class="{ active: !categoryId }"
        @click="selectCategory()"
      >
        全部
      </button>
      <button
        v-for="category in categoryOptions"
        :key="category.id"
        type="button"
        class="category-chip"
        :class="{ active: categoryId === category.id }"
        @click="selectCategory(category.id)"
      >
        {{ category.displayName }}
      </button>
    </div>

    <div v-if="keyword || categoryId" class="result-heading">
      <div>
        <strong>{{ activeCategoryName }}</strong>
        <span v-if="keyword"> · 关键词“{{ keyword }}”</span>
      </div>
      <button type="button" @click="resetFilters">重置筛选</button>
    </div>

    <van-pull-refresh v-model="refreshing" @refresh="onRefresh">
      <van-list
        v-model:loading="loading"
        :finished="finished"
        :immediate-check="false"
        :finished-text="records.length ? '没有更多商品了' : ''"
        :error="Boolean(errorMessage)"
        :error-text="errorMessage || '请求失败，点击重试'"
        class="product-list"
        @load="loadMore"
        @click-error-text="retryLoad"
      >
        <button
          v-for="product in records"
          :key="product.id"
          type="button"
          class="product-card"
          @click="openProduct(product.id)"
        >
          <span class="product-thumb">
            <img
              v-if="product.mainImage && !product.imageFailed"
              :src="product.mainImage"
              :alt="product.name"
              loading="lazy"
              @error="handleImageError(product)"
            />
            <span v-else class="image-placeholder">E-Shop</span>
            <span v-if="product.totalStock <= 0" class="stock-badge sold-out">暂时缺货</span>
            <span v-else class="stock-badge">库存 {{ product.totalStock }}</span>
          </span>
          <span class="product-content">
            <strong class="product-name">{{ product.name }}</strong>
            <span class="product-subtitle">{{ product.subtitle || '品质商品，放心选购' }}</span>
            <span class="product-meta">
              <strong>{{ formatMoney(product.minPrice) }}</strong>
              <span v-if="product.totalStock > 0">查看详情 →</span>
              <span v-else class="sold-out-text">暂时缺货</span>
            </span>
          </span>
        </button>

        <van-empty
          v-if="!loading && !errorMessage && records.length === 0"
          description="没有找到符合条件的商品"
          class="empty-block"
        >
          <van-button round type="primary" size="small" @click="resetFilters">
            查看全部商品
          </van-button>
        </van-empty>
      </van-list>
    </van-pull-refresh>
  </section>
</template>

<style scoped>
.mobile-products {
  min-height: 100%;
  padding: 0 0 24px;
  background: #f7f8fa;
}

.search-bar {
  padding: 8px 12px 0;
  background: #f7f8fa;
}

.search-bar :deep(.van-search) {
  padding: 0;
}

.category-bar {
  display: flex;
  gap: 8px;
  padding: 12px;
  overflow-x: auto;
  white-space: nowrap;
  scrollbar-width: none;
}

.category-bar::-webkit-scrollbar {
  display: none;
}

.category-chip {
  flex-shrink: 0;
  padding: 6px 14px;
  color: #475569;
  font: inherit;
  font-size: 13px;
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 999px;
}

.category-chip.active {
  color: #fff;
  background: #1d4ed8;
  border-color: #1d4ed8;
}

.result-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 16px 4px;
  color: #64748b;
  font-size: 13px;
}

.result-heading strong {
  margin-right: 4px;
  color: #0f172a;
  font-size: 15px;
}

.result-heading button {
  padding: 4px;
  color: #1d4ed8;
  font: inherit;
  background: transparent;
  border: 0;
}

.product-list {
  min-height: 200px;
  padding: 8px 12px 20px;
}

.product-card {
  display: flex;
  width: 100%;
  gap: 12px;
  margin-bottom: 12px;
  padding: 12px;
  color: inherit;
  font: inherit;
  text-align: left;
  background: #fff;
  border: 0;
  border-radius: 12px;
  box-shadow: 0 4px 14px rgba(15, 23, 42, .04);
}

.product-thumb {
  position: relative;
  display: grid;
  flex-shrink: 0;
  width: 100px;
  height: 100px;
  place-items: center;
  overflow: hidden;
  background: linear-gradient(145deg, #eff6ff, #f8fafc);
  border-radius: 10px;
}

.product-thumb img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.image-placeholder {
  color: #93c5fd;
  font-size: 11px;
  font-weight: 800;
}

.stock-badge {
  position: absolute;
  right: 4px;
  bottom: 4px;
  padding: 2px 6px;
  color: #166534;
  background: rgba(220, 252, 231, .94);
  border-radius: 6px;
  font-size: 10px;
}

.stock-badge.sold-out {
  color: #991b1b;
  background: rgba(254, 226, 226, .94);
}

.product-content {
  display: flex;
  flex: 1;
  flex-direction: column;
  justify-content: space-between;
  min-width: 0;
}

.product-name {
  display: -webkit-box;
  overflow: hidden;
  color: #0f172a;
  font-size: 14px;
  line-height: 1.4;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.product-subtitle {
  display: -webkit-box;
  margin: 4px 0 6px;
  overflow: hidden;
  color: #94a3b8;
  font-size: 11px;
  line-height: 1.4;
  text-overflow: ellipsis;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 1;
}

.product-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.product-meta strong {
  color: #dc2626;
  font-size: 16px;
}

.product-meta > span {
  color: #1d4ed8;
  font-size: 12px;
}

.product-meta .sold-out-text {
  color: #94a3b8;
}

.empty-block {
  padding: 40px 0;
}
</style>
