<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getCategories, getProducts } from '../../api/catalog'

const route = useRoute()
const router = useRouter()

const categories = ref([])
const records = ref([])
const total = ref(0)
const loading = ref(false)
const categoryLoading = ref(false)
const errorMessage = ref('')
const keywordInput = ref('')

const current = computed(() => {
  const value = Number(route.query.current)
  return Number.isInteger(value) && value > 0 ? value : 1
})
const size = 12
const categoryId = computed(() => {
  const value = Number(route.query.categoryId)
  return Number.isInteger(value) && value > 0 ? value : undefined
})
const keyword = computed(() => (
  typeof route.query.keyword === 'string' ? route.query.keyword.trim() : ''
))

const activeCategoryName = computed(() => (
  categories.value.find((item) => item.id === categoryId.value)?.name || '全部商品'
))

const formatMoney = (value) => {
  if (value === null || value === undefined) return '暂无报价'
  return `¥${Number(value).toFixed(2)}`
}

const loadCategories = async () => {
  categoryLoading.value = true
  try {
    categories.value = await getCategories()
  } catch (error) {
    errorMessage.value = error.message || '商品分类加载失败'
  } finally {
    categoryLoading.value = false
  }
}

const loadProducts = async () => {
  loading.value = true
  errorMessage.value = ''
  keywordInput.value = keyword.value
  try {
    const page = await getProducts({
      current: current.value,
      size,
      categoryId: categoryId.value,
      keyword: keyword.value || undefined,
    })
    records.value = page.records.map((product) => ({ ...product, imageFailed: false }))
    total.value = page.total
  } catch (error) {
    records.value = []
    total.value = 0
    errorMessage.value = error.message || '商品加载失败，请稍后重试'
  } finally {
    loading.value = false
  }
}

const replaceQuery = (changes) => {
  const query = { ...route.query, ...changes }
  Object.keys(query).forEach((key) => {
    if (query[key] === undefined || query[key] === null || query[key] === '') delete query[key]
  })
  router.push({ name: 'pc-products', query })
}

const selectCategory = (id) => {
  replaceQuery({ categoryId: id || undefined, current: undefined })
}

const search = () => {
  replaceQuery({ keyword: keywordInput.value.trim() || undefined, current: undefined })
}

const resetFilters = () => {
  keywordInput.value = ''
  router.push({ name: 'pc-products' })
}

const changePage = (page) => {
  replaceQuery({ current: page === 1 ? undefined : page })
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

const openProduct = (id) => {
  router.push({ name: 'pc-product-detail', params: { id } })
}

onMounted(loadCategories)
watch(
  () => [route.query.current, route.query.categoryId, route.query.keyword],
  loadProducts,
  { immediate: true },
)
</script>

<template>
  <div class="product-page">
    <section class="product-hero">
      <div>
        <p class="eyebrow">E-SHOP 精选商城</p>
        <h1>发现今天的好物</h1>
        <p>浏览分类、搜索商品并查看每个 SKU 的实时价格和库存。</p>
      </div>
      <div class="hero-stat">
        <strong>{{ total }}</strong>
        <span>件在售商品</span>
      </div>
    </section>

    <section class="filter-panel">
      <form class="search-row" @submit.prevent="search">
        <el-input
          v-model="keywordInput"
          clearable
          maxlength="120"
          placeholder="搜索商品名称"
          size="large"
          @clear="search"
        />
        <el-button type="primary" size="large" native-type="submit">搜索</el-button>
        <el-button size="large" @click="resetFilters">重置</el-button>
      </form>

      <div v-loading="categoryLoading" class="category-row">
        <span class="filter-label">商品分类</span>
        <button
          type="button"
          class="category-chip"
          :class="{ active: !categoryId }"
          @click="selectCategory()"
        >
          全部
        </button>
        <button
          v-for="category in categories"
          :key="category.id"
          type="button"
          class="category-chip"
          :class="{ active: categoryId === category.id }"
          @click="selectCategory(category.id)"
        >
          {{ category.name }}
        </button>
      </div>
    </section>

    <div class="result-heading">
      <div>
        <h2>{{ activeCategoryName }}</h2>
        <p v-if="keyword">“{{ keyword }}”的搜索结果</p>
      </div>
      <span>共 {{ total }} 件</span>
    </div>

    <el-alert
      v-if="errorMessage"
      :title="errorMessage"
      type="error"
      show-icon
      :closable="false"
      class="result-alert"
    />

    <div v-loading="loading" class="product-grid">
      <button
        v-for="product in records"
        :key="product.id"
        type="button"
        class="product-card"
        @click="openProduct(product.id)"
      >
        <div class="product-image">
          <img
            v-if="product.mainImage && !product.imageFailed"
            :src="product.mainImage"
            :alt="product.name"
            loading="lazy"
            @error="product.imageFailed = true"
          />
          <div v-else class="image-placeholder">E-Shop</div>
          <span v-if="product.totalStock <= 0" class="stock-badge sold-out">暂时缺货</span>
          <span v-else class="stock-badge">库存 {{ product.totalStock }}</span>
        </div>
        <div class="product-content">
          <h3>{{ product.name }}</h3>
          <p>{{ product.subtitle || '品质商品，放心选购' }}</p>
          <div class="product-meta">
            <strong>{{ formatMoney(product.minPrice) }}</strong>
            <span>查看详情 →</span>
          </div>
        </div>
      </button>
    </div>

    <el-empty
      v-if="!loading && !errorMessage && records.length === 0"
      description="没有找到符合条件的商品"
    >
      <el-button type="primary" @click="resetFilters">查看全部商品</el-button>
    </el-empty>

    <div v-if="total > size" class="product-pagination">
      <el-pagination
        background
        layout="prev, pager, next, total"
        :current-page="current"
        :page-size="size"
        :total="total"
        @current-change="changePage"
      />
    </div>
  </div>
</template>

<style scoped>
.product-page {
  width: min(1180px, 100%);
  margin: 0 auto;
  padding: 18px 0 48px;
}

.product-hero {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 210px;
  padding: 40px 48px;
  overflow: hidden;
  color: #fff;
  background:
    radial-gradient(circle at 86% 15%, rgba(96, 165, 250, .52), transparent 30%),
    linear-gradient(125deg, #0f172a 0%, #1d4ed8 62%, #2563eb 100%);
  border-radius: 24px;
  box-shadow: 0 22px 55px rgba(30, 64, 175, .2);
}

.product-hero .eyebrow {
  color: #bfdbfe;
}

.product-hero h1 {
  margin: 8px 0 12px;
  font-size: clamp(34px, 5vw, 54px);
  letter-spacing: -.04em;
}

.product-hero p:not(.eyebrow) {
  margin: 0;
  color: #dbeafe;
  font-size: 16px;
}

.hero-stat {
  display: grid;
  min-width: 132px;
  padding: 24px;
  text-align: center;
  background: rgba(255, 255, 255, .12);
  border: 1px solid rgba(255, 255, 255, .2);
  border-radius: 18px;
  backdrop-filter: blur(10px);
}

.hero-stat strong {
  font-size: 40px;
}

.hero-stat span {
  color: #dbeafe;
  font-size: 13px;
}

.filter-panel {
  display: grid;
  gap: 20px;
  margin: 24px 0 30px;
  padding: 24px;
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 18px;
  box-shadow: 0 8px 28px rgba(15, 23, 42, .05);
}

.search-row {
  display: grid;
  grid-template-columns: minmax(240px, 1fr) auto auto;
  gap: 12px;
}

.category-row {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
  min-height: 34px;
}

.filter-label {
  margin-right: 6px;
  color: #64748b;
  font-size: 14px;
  font-weight: 700;
}

.category-chip {
  padding: 8px 13px;
  color: #475569;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 999px;
  cursor: pointer;
  transition: .2s ease;
}

.category-chip:hover,
.category-chip.active {
  color: #fff;
  background: #2563eb;
  border-color: #2563eb;
}

.result-heading {
  display: flex;
  align-items: end;
  justify-content: space-between;
  margin-bottom: 16px;
}

.result-heading h2,
.result-heading p {
  margin: 0;
}

.result-heading h2 {
  font-size: 25px;
}

.result-heading p,
.result-heading > span {
  margin-top: 5px;
  color: #64748b;
  font-size: 14px;
}

.result-alert {
  margin-bottom: 18px;
}

.product-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 20px;
  min-height: 260px;
}

.product-card {
  overflow: hidden;
  padding: 0;
  text-align: left;
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 18px;
  cursor: pointer;
  box-shadow: 0 7px 22px rgba(15, 23, 42, .05);
  transition: transform .2s ease, box-shadow .2s ease, border-color .2s ease;
}

.product-card:hover {
  transform: translateY(-5px);
  border-color: #bfdbfe;
  box-shadow: 0 18px 38px rgba(37, 99, 235, .13);
}

.product-image {
  position: relative;
  display: grid;
  height: 210px;
  place-items: center;
  overflow: hidden;
  background: linear-gradient(145deg, #eff6ff, #f8fafc);
}

.product-image img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  transition: transform .3s ease;
}

.product-card:hover .product-image img {
  transform: scale(1.035);
}

.image-placeholder {
  color: #93c5fd;
  font-size: 24px;
  font-weight: 800;
}

.stock-badge {
  position: absolute;
  right: 12px;
  bottom: 12px;
  padding: 5px 9px;
  color: #166534;
  background: rgba(220, 252, 231, .92);
  border-radius: 999px;
  font-size: 12px;
}

.stock-badge.sold-out {
  color: #991b1b;
  background: rgba(254, 226, 226, .94);
}

.product-content {
  padding: 18px;
}

.product-content h3 {
  display: -webkit-box;
  min-height: 48px;
  margin: 0;
  overflow: hidden;
  color: #0f172a;
  font-size: 17px;
  line-height: 1.45;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.product-content p {
  overflow: hidden;
  margin: 8px 0 18px;
  color: #64748b;
  font-size: 13px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.product-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.product-meta strong {
  color: #dc2626;
  font-size: 20px;
}

.product-meta span {
  color: #2563eb;
  font-size: 13px;
}

.product-pagination {
  display: flex;
  justify-content: center;
  margin-top: 32px;
}

@media (max-width: 1000px) {
  .product-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (max-width: 760px) {
  .product-page {
    padding-top: 0;
  }

  .product-hero {
    min-height: 180px;
    padding: 28px;
    border-radius: 18px;
  }

  .hero-stat {
    display: none;
  }

  .search-row {
    grid-template-columns: 1fr auto;
  }

  .search-row .el-button:last-child {
    display: none;
  }

  .product-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 12px;
  }

  .product-image {
    height: 150px;
  }
}
</style>
