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
    tone: ['red', 'orange', 'blue', 'green'][index],
  }))
  return [
    { label: '全部分类', icon: 'apps-o', tone: 'red' },
    { label: '汽车选配', icon: 'logistics', routeName: 'mobile-vehicles', tone: 'orange' },
    ...categoryEntries,
    { label: '我的订单', icon: 'orders-o', routeName: 'mobile-orders', tone: 'blue' },
    { label: '我的收藏', icon: 'like-o', routeName: 'mobile-favorites', tone: 'pink' },
    { label: '浏览足迹', icon: 'clock-o', routeName: 'mobile-history', tone: 'gray' },
  ].slice(0, 8)
})

const marketBanners = [
  { kicker: '今日必买', title: '精选好物低价开抢', desc: '官方发货 · 售后无忧', tone: 'red' },
  { kicker: '品质自营', title: '把好生活带回家', desc: '正品保障 · 极速发货', tone: 'orange' },
  { kicker: '热销榜单', title: '大家都在买这些', desc: '口碑爆款 · 限时优惠', tone: 'gold' },
]

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

const openProduct = (productOrId) => {
  const product = typeof productOrId === 'object' ? productOrId : { id: productOrId }
  if (product.productKind === 'VEHICLE') {
    router.push({ name: 'mobile-vehicle-configurator', params: { id: product.id } })
    return
  }
  router.push({ name: 'mobile-product-detail', params: { id: product.id } })
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
    <header class="market-top">
      <div class="market-search-row">
        <RouterLink class="market-logo" to="/m/products" aria-label="回到首页">E</RouterLink>
        <van-search
          v-model="keywordInput"
          placeholder="搜索淘宝商品"
          shape="round"
          background="transparent"
          clearable
          @search="search"
          @clear="search"
        />
        <RouterLink class="market-user" to="/m/profile" aria-label="进入个人中心">
          <van-icon name="user-o" />
        </RouterLink>
      </div>
      <nav class="channel-tabs" aria-label="商品频道">
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
    </header>

    <section class="shortcut-grid" aria-label="商城快捷入口">
      <button
        v-for="entry in nativeShortcuts"
        :key="`${entry.label}-${entry.id || entry.routeName || 'all'}`"
        type="button"
        :class="`tone-${entry.tone || 'red'}`"
        @click="openShortcut(entry)"
      >
        <span><van-icon :name="entry.icon" /></span>
        <small>{{ entry.label }}</small>
      </button>
    </section>

    <section class="banner-wrap" aria-label="商城活动">
      <van-swipe :autoplay="3800" lazy-render indicator-color="#fff">
        <van-swipe-item v-for="banner in marketBanners" :key="banner.title">
          <article class="market-banner" :class="`is-${banner.tone}`">
            <div>
              <span>{{ banner.kicker }}</span>
              <h2>{{ banner.title }}</h2>
              <p>{{ banner.desc }}</p>
            </div>
            <em>购</em>
          </article>
        </van-swipe-item>
      </van-swipe>
    </section>

    <div class="service-strip" aria-label="购物保障">
      <span><van-icon name="passed" /> 正品保障</span>
      <span><van-icon name="logistics" /> 极速发货</span>
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
        <span v-if="keyword"> · “{{ keyword }}”</span>
      </div>
      <button type="button" @click="resetFilters">重置</button>
    </div>

    <div v-else class="recommend-heading">
      <div>
        <strong>猜你喜欢</strong>
        <span>精选好货 · 每日更新</span>
      </div>
      <small>{{ total }} 件</small>
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
            @click="openProduct(product)"
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
              <strong class="product-name">{{ product.name }}</strong>
              <span class="product-subtitle">{{ product.subtitle || '品质好物，放心选购' }}</span>
              <span class="product-meta">
                <strong>{{ formatMoney(product.minPrice) }}</strong>
                <em v-if="product.totalStock > 0">包邮</em>
                <em v-else class="sold-out-text">缺货</em>
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
  background: #f5f5f5;
}

.market-top {
  position: sticky;
  z-index: 20;
  top: 0;
  padding: calc(6px + var(--app-safe-top, 0px)) 12px 0;
  background: linear-gradient(180deg, #ff5000 0%, #ff9000 100%);
}

.market-search-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.market-logo,
.market-user {
  display: grid;
  flex: 0 0 auto;
  width: 34px;
  height: 34px;
  place-items: center;
  color: #fff;
  background: rgba(255, 255, 255, .16);
  border: 1px solid rgba(255, 255, 255, .22);
  border-radius: 10px;
  font-size: 18px;
  font-weight: 900;
}

.market-search-row :deep(.van-search) {
  flex: 1;
  padding: 0;
}

.market-search-row :deep(.van-search__content) {
  height: 36px;
  background: #fff;
  border-radius: 999px;
}

.channel-tabs {
  display: flex;
  gap: 18px;
  padding: 8px 4px 10px;
  overflow-x: auto;
  scrollbar-width: none;
}

.channel-tabs::-webkit-scrollbar { display: none; }

.channel-tabs button {
  position: relative;
  flex: 0 0 auto;
  padding: 4px 0 8px;
  color: rgba(255, 255, 255, .78);
  font: inherit;
  font-size: 14px;
  font-weight: 600;
  background: transparent;
  border: 0;
}

.channel-tabs button.active {
  color: #fff;
  font-weight: 800;
}

.channel-tabs button.active::after {
  position: absolute;
  right: 18%;
  bottom: 2px;
  left: 18%;
  height: 3px;
  background: #fff;
  border-radius: 99px;
  content: '';
}

.shortcut-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px 6px;
  margin: 10px 12px 0;
  padding: 14px 8px 12px;
  background: #fff;
  border-radius: 14px;
  box-shadow: 0 4px 16px rgba(0, 0, 0, .04);
}

.shortcut-grid button {
  display: grid;
  min-width: 0;
  justify-items: center;
  gap: 6px;
  padding: 0;
  color: #333;
  font: inherit;
  background: transparent;
  border: 0;
}

.shortcut-grid button > span {
  display: grid;
  width: 44px;
  height: 44px;
  place-items: center;
  color: #fff;
  border-radius: 14px;
  font-size: 21px;
}

.shortcut-grid .tone-red > span { background: linear-gradient(145deg, #ff7a3d, #ff5000); }
.shortcut-grid .tone-orange > span { background: linear-gradient(145deg, #ffb020, #ff6a00); }
.shortcut-grid .tone-blue > span { background: linear-gradient(145deg, #5b9dff, #2f6fed); }
.shortcut-grid .tone-green > span { background: linear-gradient(145deg, #34d399, #059669); }
.shortcut-grid .tone-pink > span { background: linear-gradient(145deg, #fb7185, #e11d48); }
.shortcut-grid .tone-gray > span { background: linear-gradient(145deg, #94a3b8, #64748b); }

.shortcut-grid small {
  max-width: 100%;
  overflow: hidden;
  font-size: 11px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.banner-wrap {
  margin: 10px 12px 0;
  overflow: hidden;
  border-radius: 14px;
}

.market-banner {
  position: relative;
  display: flex;
  min-height: 128px;
  align-items: center;
  justify-content: space-between;
  padding: 22px 20px;
  overflow: hidden;
  color: #fff;
  background: linear-gradient(135deg, #ff5000, #ff9000);
}

.market-banner.is-orange { background: linear-gradient(135deg, #ff7a18, #ffb347); }
.market-banner.is-gold { background: linear-gradient(135deg, #b45309, #f59e0b); }

.market-banner h2 { margin: 6px 0 4px; font-size: 22px; letter-spacing: -.02em; }
.market-banner p { margin: 0; color: rgba(255, 255, 255, .86); font-size: 12px; }
.market-banner span { font-size: 11px; font-weight: 700; opacity: .9; }
.market-banner em {
  display: grid;
  width: 58px;
  height: 58px;
  place-items: center;
  color: rgba(255, 255, 255, .92);
  background: rgba(255, 255, 255, .16);
  border-radius: 16px;
  font-size: 28px;
  font-style: normal;
  font-weight: 900;
}

.service-strip {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  margin: 10px 12px 0;
  padding: 10px 6px;
  color: #666;
  background: #fff;
  border-radius: 12px;
  font-size: 11px;
  text-align: center;
}

.service-strip span { display: flex; align-items: center; justify-content: center; gap: 4px; }
.service-strip :deep(.van-icon) { color: #e1251b; font-size: 14px; }

.category-bar {
  display: flex;
  gap: 8px;
  padding: 12px;
  overflow-x: auto;
  white-space: nowrap;
  scrollbar-width: none;
}

.category-bar::-webkit-scrollbar { display: none; }

.category-chip {
  flex-shrink: 0;
  padding: 6px 14px;
  color: #555;
  font: inherit;
  font-size: 13px;
  background: #fff;
  border: 0;
  border-radius: 999px;
}

.category-chip.active {
  color: #ff5000;
  background: #fff4eb;
  font-weight: 700;
}

.result-heading,
.recommend-heading {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  padding: 12px 14px 2px;
}

.result-heading strong,
.recommend-heading strong {
  color: #1a1a1a;
  font-size: 17px;
}

.result-heading span,
.recommend-heading span,
.recommend-heading small {
  color: #999;
  font-size: 12px;
}

.recommend-heading > div { display: grid; gap: 2px; }

.result-heading button {
  padding: 0;
  color: #ff5000;
  font: inherit;
  background: transparent;
  border: 0;
}

.product-list {
  min-height: 200px;
  padding: 8px 8px 20px;
}

.product-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
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
  border: 0;
  border-radius: 10px;
  overflow: hidden;
}

.product-thumb {
  position: relative;
  display: grid;
  aspect-ratio: 1;
  place-items: center;
  overflow: hidden;
  background: #fafafa;
}

.product-thumb img,
.placeholder-image {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.stock-badge {
  position: absolute;
  right: 6px;
  bottom: 6px;
  padding: 2px 6px;
  color: #666;
  background: rgba(255, 255, 255, .92);
  border-radius: 4px;
  font-size: 10px;
}

.stock-badge.sold-out {
  color: #fff;
  background: rgba(0, 0, 0, .45);
}

.product-content {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-width: 0;
  padding: 8px 10px 10px;
}

.product-name {
  display: -webkit-box;
  overflow: hidden;
  color: #1a1a1a;
  font-size: 13px;
  font-weight: 600;
  line-height: 1.4;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.product-subtitle {
  display: -webkit-box;
  margin: 4px 0 8px;
  overflow: hidden;
  color: #999;
  font-size: 11px;
  line-height: 1.4;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 1;
}

.product-meta {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  margin-top: auto;
}

.product-meta strong {
  color: #ff5000;
  font-size: 16px;
  font-weight: 800;
  letter-spacing: -.02em;
}

.product-meta em {
  color: #999;
  font-size: 11px;
  font-style: normal;
}

.product-meta .sold-out-text { color: #bbb; }

.empty-block { padding: 40px 0; }

@media (min-width: 769px) {
  .mobile-products { padding-bottom: 36px; }
  .market-top { padding-right: 24px; padding-left: 24px; }
  .shortcut-grid,
  .banner-wrap,
  .service-strip { margin-right: 20px; margin-left: 20px; }
  .market-banner { min-height: 180px; padding: 32px 36px; }
  .market-banner h2 { font-size: 32px; }
  .category-bar {
    flex-wrap: wrap;
    padding: 16px 20px 10px;
    overflow: visible;
    white-space: normal;
  }
  .recommend-heading,
  .result-heading { padding-right: 22px; padding-left: 22px; }
  .product-list { padding: 12px 20px 28px; }
  .product-grid {
    grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
    gap: 14px;
  }
}
</style>
