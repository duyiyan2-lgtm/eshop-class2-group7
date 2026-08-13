<script setup>
import { computed, onActivated, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showToast } from 'vant'
import { getCategories, getProducts } from '../../api/catalog'
import { formatMoney } from '../../utils/shop'
import HotProductRanking from '../../components/catalog/HotProductRanking.vue'

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

const nativeDiscoveryTabs = computed(() => [
  { id: undefined, label: '推荐' },
  ...categoryOptions.value.slice(0, 4).map((item) => ({ id: item.id, label: item.name })),
])

const nativeShortcuts = computed(() => {
  const categoryEntries = categoryOptions.value.slice(0, 4).map((item, index) => ({
    id: item.id,
    label: item.name,
    icon: ['phone-o', 'desktop-o', 'shop-o', 'gift-o'][index],
  }))
  return [
    { label: '全部分类', icon: 'apps-o' },
    ...categoryEntries,
    { label: '我的订单', icon: 'orders-o', routeName: 'mobile-orders' },
    { label: '我的收藏', icon: 'like-o', routeName: 'mobile-favorites' },
    { label: '浏览足迹', icon: 'clock-o', routeName: 'mobile-history' },
  ].slice(0, 8)
})

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

const openShortcut = (entry) => {
  if (entry.routeName) {
    router.push({ name: entry.routeName })
    return
  }
  selectCategory(entry.id)
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
    <header class="native-home-header android-only">
      <div>
        <span>E-SHOP SELECT</span>
        <strong>发现今日好物</strong>
      </div>
      <RouterLink to="/m/profile" aria-label="进入个人中心"><van-icon name="user-circle-o" /></RouterLink>
    </header>

    <nav class="native-discovery-tabs android-only" aria-label="商品频道">
      <button
        v-for="tab in nativeDiscoveryTabs"
        :key="tab.id || 'recommend'"
        type="button"
        :class="{ active: categoryId === tab.id || (!categoryId && tab.id === undefined) }"
        @click="selectCategory(tab.id)"
      >
        {{ tab.label }}
      </button>
    </nav>

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

    <section class="native-shortcuts android-only" aria-label="商城快捷入口">
      <button
        v-for="entry in nativeShortcuts"
        :key="`${entry.label}-${entry.id || entry.routeName || 'all'}`"
        type="button"
        @click="openShortcut(entry)"
      >
        <span><van-icon :name="entry.icon" /></span>
        <small>{{ entry.label }}</small>
      </button>
    </section>

    <section class="market-hero" aria-label="商城活动">
      <div>
        <span class="hero-kicker">E-SHOP SELECT</span>
        <h1>把好生活，装进口袋</h1>
        <p>每日精选品质好物 · 下单更省心</p>
      </div>
      <span class="hero-mark">E</span>
    </section>

    <div class="service-strip" aria-label="购物保障">
      <span><van-icon name="passed" /> 品质保障</span>
      <span><van-icon name="logistics" /> 快速发货</span>
      <span><van-icon name="shield-o" /> 售后无忧</span>
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

    <HotProductRanking mobile />

    <div v-if="keyword || categoryId" class="result-heading">
      <div>
        <strong>{{ activeCategoryName }}</strong>
        <span v-if="keyword"> · 关键词“{{ keyword }}”</span>
      </div>
      <button type="button" @click="resetFilters">重置筛选</button>
    </div>

    <div v-else class="recommend-heading">
      <div>
        <span>JUST FOR YOU</span>
        <strong>为你推荐</strong>
      </div>
      <small>共 {{ total }} 件好物</small>
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
        <div class="product-grid">
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
              <img v-else class="placeholder-image" src="/product-placeholder.svg" alt="商品暂无图片" />
              <span v-if="product.totalStock <= 0" class="stock-badge sold-out">暂时缺货</span>
              <span v-else class="stock-badge">库存 {{ product.totalStock }}</span>
            </span>
            <span class="product-content">
              <span class="quality-tag">商城精选</span>
              <strong class="product-name">{{ product.name }}</strong>
              <span class="product-subtitle">{{ product.subtitle || '品质商品，放心选购' }}</span>
              <span class="product-meta">
                <strong>{{ formatMoney(product.minPrice) }}</strong>
                <van-icon v-if="product.totalStock > 0" name="arrow" />
                <span v-else class="sold-out-text">缺货</span>
              </span>
            </span>
          </button>
        </div>

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
  background: #f3f5f9;
}

.native-home-header {
  align-items: center;
  gap: 10px;
  padding: calc(8px + var(--app-safe-top)) 16px 6px;
  color: #f8fafc;
  background: linear-gradient(145deg, #0a1729, #10254a);
}

.native-home-header > div { display: grid; flex: 1; gap: 2px; }
.native-home-header span { color: #2563eb; font-size: 9px; font-weight: 900; letter-spacing: .14em; }
.native-home-header strong { font-size: 20px; letter-spacing: -.02em; }
.native-home-header > a { display: grid; width: 36px; height: 36px; place-items: center; color: #dbeafe; background: rgba(255, 255, 255, .08); border: 1px solid rgba(147, 197, 253, .25); border-radius: 12px; font-size: 19px; }

.native-discovery-tabs {
  gap: 22px;
  padding: 0 16px 7px;
  overflow-x: auto;
  background: linear-gradient(145deg, #0a1729, #10254a);
  scrollbar-width: none;
}

.native-discovery-tabs::-webkit-scrollbar { display: none; }
.native-discovery-tabs button { position: relative; flex: 0 0 auto; padding: 6px 0; color: #94a3b8; font: inherit; font-size: 14px; font-weight: 700; background: transparent; border: 0; }
.native-discovery-tabs button.active { color: #f8fafc; }
.native-discovery-tabs button.active::after { position: absolute; right: 25%; bottom: 0; left: 25%; height: 3px; background: linear-gradient(90deg, #2563eb, #7c3aed); border-radius: 999px; content: ''; }

.native-shortcuts {
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px 8px;
  margin: 10px 12px 12px;
  padding: 15px 10px;
  background: #fff;
  border: 1px solid #e8edf5;
  border-radius: 20px;
  box-shadow: 0 8px 24px rgba(15, 23, 42, .06);
}

.native-shortcuts button { display: grid; min-width: 0; justify-items: center; gap: 7px; padding: 0; color: #334155; font: inherit; background: transparent; border: 0; }
.native-shortcuts button > span { display: grid; width: 45px; height: 45px; place-items: center; color: #fff; background: linear-gradient(145deg, #2563eb, #7c3aed); border-radius: 15px; box-shadow: 0 7px 14px rgba(37, 99, 235, .2); font-size: 22px; }
.native-shortcuts button:nth-child(2n) > span { background: linear-gradient(145deg, #0ea5e9, #2563eb); }
.native-shortcuts button:nth-child(3n) > span { background: linear-gradient(145deg, #10b981, #0ea5e9); }
.native-shortcuts small { max-width: 100%; overflow: hidden; font-size: 11px; font-weight: 600; text-overflow: ellipsis; white-space: nowrap; }

.search-bar {
  position: sticky;
  z-index: 8;
  top: calc(var(--van-nav-bar-height, 46px) + var(--app-safe-top));
  padding: 9px 12px;
  background: rgba(7, 17, 31, .96);
  box-shadow: 0 8px 18px rgba(2, 8, 23, .2);
  backdrop-filter: blur(12px);
}

.search-bar :deep(.van-search) {
  width: 100%;
  max-width: 760px;
  margin: 0 auto;
  padding: 0;
}

.search-bar :deep(.van-search__content) {
  height: 44px;
  background: #fff;
  border: 1px solid rgba(203, 213, 225, .75);
  border-radius: 14px;
  box-shadow: 0 8px 24px rgba(2, 8, 23, .14);
}

.market-hero {
  position: relative;
  display: flex;
  min-height: 146px;
  align-items: center;
  justify-content: space-between;
  margin: 4px 12px 0;
  padding: 24px 22px;
  overflow: hidden;
  color: #fff;
  background: linear-gradient(135deg, #0f3b82 0%, #2563eb 52%, #7c3aed 100%);
  border-radius: 22px;
  box-shadow: 0 16px 34px rgba(37, 99, 235, .24);
}

.market-hero::after {
  position: absolute;
  right: -42px;
  bottom: -68px;
  width: 190px;
  height: 190px;
  background: rgba(255, 255, 255, .09);
  border-radius: 50%;
  content: '';
}

.market-hero > div { position: relative; z-index: 1; }
.market-hero h1 { margin: 8px 0 7px; font-size: 24px; letter-spacing: -.02em; }
.market-hero p { margin: 0; color: rgba(255, 255, 255, .78); font-size: 12px; }
.hero-kicker { font-size: 10px; font-weight: 800; letter-spacing: .16em; opacity: .8; }
.hero-mark { position: relative; z-index: 1; display: grid; width: 66px; height: 66px; place-items: center; color: #dbeafe; background: rgba(255, 255, 255, .14); border: 1px solid rgba(255, 255, 255, .24); border-radius: 20px; font-size: 36px; font-weight: 900; transform: rotate(8deg); }

.service-strip {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  margin: 10px 12px 2px;
  padding: 11px 8px;
  color: #475569;
  background: #fff;
  border: 1px solid #eef2f7;
  border-radius: 14px;
  box-shadow: 0 6px 18px rgba(15, 23, 42, .04);
  font-size: 11px;
  text-align: center;
}

.service-strip span { display: flex; align-items: center; justify-content: center; gap: 4px; }
.service-strip :deep(.van-icon) { color: #2563eb; font-size: 14px; }

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

.product-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.product-card {
  display: flex;
  min-width: 0;
  flex-direction: column;
  width: 100%;
  padding: 0;
  color: inherit;
  font: inherit;
  text-align: left;
  background: #fff;
  border: 1px solid #edf1f6;
  border-radius: 16px;
  box-shadow: 0 8px 20px rgba(15, 23, 42, .06);
  overflow: hidden;
}

.product-thumb {
  position: relative;
  display: grid;
  width: 100%;
  height: auto;
  aspect-ratio: 1 / .9;
  place-items: center;
  overflow: hidden;
  background: linear-gradient(145deg, #eff6ff, #f8fafc);
  border-radius: 0;
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
  min-height: 120px;
  padding: 11px 12px 12px;
}

.placeholder-image {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.quality-tag { align-self: flex-start; margin-bottom: 6px; padding: 2px 6px; color: #1d4ed8; background: #eff6ff; border-radius: 5px; font-size: 9px; font-weight: 700; }

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
  -webkit-line-clamp: 2;
}

.product-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.product-meta strong {
  color: #e11d48;
  font-size: 17px;
}

.product-meta > span {
  color: #1d4ed8;
  font-size: 12px;
}

.product-meta :deep(.van-icon) { display: grid; width: 24px; height: 24px; place-items: center; color: #fff; background: #2563eb; border-radius: 50%; font-size: 12px; }

.recommend-heading {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  padding: 13px 14px 0;
}

.recommend-heading > div { display: grid; gap: 3px; }
.recommend-heading span { color: #2563eb; font-size: 9px; font-weight: 800; letter-spacing: .14em; }
.recommend-heading strong { color: #0f172a; font-size: 18px; }
.recommend-heading small { color: #94a3b8; font-size: 11px; }

.product-meta .sold-out-text {
  color: #94a3b8;
}

.empty-block {
  padding: 40px 0;
}

@media (min-width: 769px) {
  .mobile-products {
    padding-bottom: 36px;
  }

  .search-bar {
    padding: 14px 28px 16px;
  }

  .market-hero {
    min-height: 190px;
    margin: 8px 20px 0;
    padding: 34px 40px;
    border-radius: 26px;
  }

  .market-hero h1 {
    margin-top: 10px;
    font-size: 34px;
  }

  .market-hero p {
    font-size: 14px;
  }

  .hero-mark {
    width: 92px;
    height: 92px;
    border-radius: 26px;
    font-size: 48px;
  }

  .service-strip {
    margin: 14px 20px 4px;
    padding: 14px 12px;
    font-size: 13px;
  }

  .category-bar {
    flex-wrap: wrap;
    padding: 16px 20px 10px;
    overflow: visible;
    white-space: normal;
  }

  .recommend-heading,
  .result-heading {
    padding-right: 22px;
    padding-left: 22px;
  }

  .product-list {
    padding: 12px 20px 28px;
  }

  .product-grid {
    grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
    gap: 16px;
  }

  .product-card {
    border-radius: 18px;
  }

  .product-thumb {
    aspect-ratio: 1 / .82;
  }

  .product-content {
    min-height: 132px;
    padding: 14px;
  }

  .product-name {
    font-size: 15px;
  }

  .product-subtitle {
    font-size: 12px;
  }
}
</style>
