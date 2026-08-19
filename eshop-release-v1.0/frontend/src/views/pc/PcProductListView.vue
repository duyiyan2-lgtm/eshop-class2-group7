<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getCategories, getProducts } from '../../api/catalog'
import HotProductRanking from '../../components/catalog/HotProductRanking.vue'
import { isVehicleProduct, vehicleCoverSrc } from '../../utils/vehicle'

const route = useRoute()
const router = useRouter()

const categories = ref([])
const records = ref([])
const total = ref(0)
const loading = ref(false)
const categoryLoading = ref(false)
const errorMessage = ref('')

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
const isHomeLanding = computed(() => !categoryId.value && !keyword.value && current.value === 1)
const topCategories = computed(() => categories.value.slice(0, 9))
const featuredProduct = computed(() => records.value[0])
const promotionProducts = computed(() => records.value.slice(1, 4))

const findCategory = (list, id) => {
  for (const item of list || []) {
    if (item.id === id) return item
    const child = findCategory(item.children, id)
    if (child) return child
  }
  return null
}

const activeCategoryName = computed(() => (
  findCategory(categories.value, categoryId.value)?.name || '全部商品'
))

const productImage = (product) => (
  isVehicleProduct(product) ? vehicleCoverSrc(product) : product.mainImage
)

const formatPrice = (value) => {
  if (value === null || value === undefined) return '暂无报价'
  const number = Number(value)
  return Number.isFinite(number) ? number.toFixed(2) : '暂无报价'
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
  try {
    const page = await getProducts({
      current: current.value,
      size,
      categoryId: categoryId.value,
      keyword: keyword.value || undefined,
    })
    records.value = (page.records || []).map((product) => ({ ...product, imageFailed: false }))
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

const selectCategory = (id) => replaceQuery({ categoryId: id || undefined, current: undefined })
const resetFilters = () => router.push({ name: 'pc-products' })
const openProduct = (product) => {
  if (!product?.id) return
  if (isVehicleProduct(product)) {
    router.push({ name: 'pc-vehicle-configurator', params: { id: product.id } })
    return
  }
  router.push({ name: 'pc-product-detail', params: { id: product.id } })
}
const changePage = (page) => {
  replaceQuery({ current: page === 1 ? undefined : page })
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

onMounted(loadCategories)
watch(
  () => [route.query.current, route.query.categoryId, route.query.keyword],
  loadProducts,
  { immediate: true },
)
</script>

<template>
  <div class="mi-page">
    <template v-if="isHomeLanding">
      <section class="mi-hero-stage" v-loading="categoryLoading || loading">
        <aside class="mi-hero-categories" aria-label="商品分类">
          <button
            v-for="category in topCategories"
            :key="category.id"
            type="button"
            @click="selectCategory(category.id)"
          >
            <span>{{ category.name }}</span>
            <small v-if="category.children?.length">
              {{ category.children.slice(0, 2).map((item) => item.name).join(' · ') }}
            </small>
            <b aria-hidden="true">›</b>
          </button>
          <button type="button" class="all-category" @click="selectCategory()">
            <span>全部商品</span><b aria-hidden="true">›</b>
          </button>
        </aside>

        <div class="mi-hero-banner">
          <div class="mi-hero-copy">
            <span class="mi-kicker">E-SHOP SELECT</span>
            <h1>好物上新，<br />让生活更简单</h1>
            <p>{{ featuredProduct?.subtitle || '精选品质商品，价格与库存实时同步' }}</p>
            <div class="mi-hero-actions">
              <button type="button" @click="featuredProduct ? openProduct(featuredProduct) : selectCategory()">
                {{ isVehicleProduct(featuredProduct) ? '开始选配' : '立即选购' }}
              </button>
              <button type="button" class="ghost" @click="selectCategory()">浏览全部</button>
            </div>
          </div>
          <div class="mi-hero-visual" aria-hidden="true">
            <span class="mi-orbit one" />
            <span class="mi-orbit two" />
            <img
              v-if="productImage(featuredProduct) && !featuredProduct.imageFailed"
              :src="productImage(featuredProduct)"
              :alt="featuredProduct.name"
              @error="featuredProduct.imageFailed = true"
            />
            <div v-else class="mi-hero-mark">E</div>
          </div>
          <div v-if="featuredProduct" class="mi-hero-price">
            <small>本期推荐</small>
            <strong>¥{{ formatPrice(featuredProduct.minPrice) }} 起</strong>
          </div>
        </div>
      </section>

      <section class="mi-promo-row">
        <div class="mi-shortcuts">
          <RouterLink to="/pc/vehicles"><span>▣</span>汽车选配</RouterLink>
          <RouterLink to="/pc/orders"><span>▤</span>我的订单</RouterLink>
          <RouterLink to="/pc/cart"><span>🛒</span>购物车</RouterLink>
          <RouterLink to="/pc/favorites"><span>♡</span>我的收藏</RouterLink>
          <RouterLink to="/pc/history"><span>◷</span>浏览历史</RouterLink>
          <RouterLink to="/pc/addresses"><span>⌖</span>收货地址</RouterLink>
          <RouterLink to="/pc/profile"><span>♙</span>个人中心</RouterLink>
        </div>
        <button
          v-for="(product, index) in promotionProducts"
          :key="product.id"
          type="button"
          class="mi-promo-card"
          @click="openProduct(product)"
        >
          <span>
            <small>{{ ['新品尝鲜', '品质生活', '人气推荐'][index] }}</small>
            <strong>{{ product.name }}</strong>
            <em>¥{{ formatPrice(product.minPrice) }} 起</em>
          </span>
          <img
            v-if="productImage(product) && !product.imageFailed"
            :src="productImage(product)"
            :alt="product.name"
            loading="lazy"
            @error="product.imageFailed = true"
          />
          <img v-else src="/product-placeholder.svg" alt="商品暂无图片" />
        </button>
      </section>

      <el-alert
        v-if="errorMessage"
        :title="errorMessage"
        type="error"
        show-icon
        :closable="false"
        class="result-alert"
      />

      <HotProductRanking />

      <section class="mi-product-section">
        <header class="mi-section-head">
          <div>
            <span>POPULAR PICKS</span>
            <h2>为你推荐</h2>
          </div>
          <button type="button" @click="selectCategory()">查看全部 <b>›</b></button>
        </header>
        <div v-loading="loading" class="mi-home-grid">
          <button
            v-for="product in records"
            :key="product.id"
            type="button"
            class="mi-card mi-home-card"
            @click="openProduct(product)"
          >
            <span v-if="product.totalStock <= 0" class="mi-card-tag sold-out">暂时缺货</span>
            <span v-else-if="product.totalStock < 10" class="mi-card-tag">库存紧张</span>
            <div class="mi-card-image">
              <img
                v-if="productImage(product) && !product.imageFailed"
                :src="productImage(product)"
                :alt="product.name"
                loading="lazy"
                @error="product.imageFailed = true"
              />
              <img v-else src="/product-placeholder.svg" alt="商品暂无图片" />
            </div>
            <h3>{{ product.name }}</h3>
            <p>{{ product.subtitle || '官方精选 · 品质保障' }}</p>
            <strong><small>¥</small>{{ formatPrice(product.minPrice) }}</strong>
            <em v-if="isVehicleProduct(product)" class="mi-config-cta">开始选配</em>
          </button>
        </div>
      </section>
    </template>

    <template v-else>
      <nav class="mi-breadcrumb">
        <RouterLink to="/pc">首页</RouterLink>
        <span>/</span>
        <button type="button" @click="resetFilters">全部商品</button>
        <template v-if="categoryId">
          <span>/</span><em>{{ activeCategoryName }}</em>
        </template>
        <template v-if="keyword">
          <span>/</span><em>“{{ keyword }}”</em>
        </template>
      </nav>

      <div class="mi-layout">
        <aside class="mi-sidebar" v-loading="categoryLoading">
          <h2>商品分类</h2>
          <button
            type="button"
            class="mi-side-item"
            :class="{ active: !categoryId }"
            @click="resetFilters"
          >全部商品</button>
          <section v-for="category in categories" :key="category.id" class="mi-side-group">
            <button
              type="button"
              class="mi-side-item is-parent"
              :class="{ active: categoryId === category.id }"
              @click="selectCategory(category.id)"
            >{{ category.name }}</button>
            <button
              v-for="child in category.children || []"
              :key="child.id"
              type="button"
              class="mi-side-item is-child"
              :class="{ active: categoryId === child.id }"
              @click="selectCategory(child.id)"
            >{{ child.name }}</button>
          </section>
        </aside>

        <section class="mi-content">
          <header class="mi-content-head">
            <div>
              <span>PRODUCTS</span>
              <h1>{{ activeCategoryName }}</h1>
              <p v-if="keyword">搜索“{{ keyword }}”的结果</p>
            </div>
            <small>共 {{ total }} 件商品</small>
          </header>

          <el-alert
            v-if="errorMessage"
            :title="errorMessage"
            type="error"
            show-icon
            :closable="false"
            class="result-alert"
          />

          <div v-loading="loading" class="mi-grid">
            <button
              v-for="product in records"
              :key="product.id"
              type="button"
              class="mi-card"
              @click="openProduct(product)"
            >
              <div class="mi-card-image">
                <img
                  v-if="productImage(product) && !product.imageFailed"
                  :src="productImage(product)"
                  :alt="product.name"
                  loading="lazy"
                  @error="product.imageFailed = true"
                />
                <img v-else src="/product-placeholder.svg" alt="商品暂无图片" />
              </div>
              <h3>{{ product.name }}</h3>
              <p>{{ product.subtitle || '官方精选 · 品质保障' }}</p>
              <strong><small>¥</small>{{ formatPrice(product.minPrice) }}</strong>
              <em v-if="isVehicleProduct(product)" class="mi-config-cta">开始选配</em>
            </button>
          </div>

          <el-empty
            v-if="!loading && !errorMessage && records.length === 0"
            description="该分类下暂无商品"
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
        </section>
      </div>
    </template>
  </div>
</template>

<style scoped>
.mi-page {
  width: min(1226px, calc(100% - 32px));
  margin: 0 auto;
  padding: 0 0 48px;
}

.mi-hero-stage {
  display: grid;
  grid-template-columns: 234px minmax(0, 1fr);
  min-height: 460px;
  margin-bottom: 14px;
  overflow: hidden;
  background: #fff;
}

.mi-hero-categories {
  z-index: 2;
  display: flex;
  flex-direction: column;
  justify-content: center;
  padding: 18px 0;
  color: #fff;
  background: rgba(26, 26, 26, .92);
}

.mi-hero-categories button {
  display: grid;
  grid-template-columns: minmax(0, auto) minmax(0, 1fr) 16px;
  align-items: center;
  gap: 7px;
  min-height: 42px;
  padding: 0 18px 0 26px;
  color: #fff;
  text-align: left;
  background: transparent;
  border: 0;
  cursor: pointer;
}

.mi-hero-categories button:hover { background: #ff6700; }
.mi-hero-categories button span { font-size: 14px; white-space: nowrap; }
.mi-hero-categories button small { overflow: hidden; color: rgba(255, 255, 255, .58); font-size: 11px; text-overflow: ellipsis; white-space: nowrap; }
.mi-hero-categories button b { justify-self: end; font-size: 22px; font-weight: 300; }
.mi-hero-categories .all-category { margin-top: 2px; color: #ffd2b7; }

.mi-hero-banner {
  position: relative;
  min-width: 0;
  overflow: hidden;
  color: #fff;
  background: radial-gradient(circle at 88% 16%, rgba(255, 255, 255, .24), transparent 28%), linear-gradient(118deg, #ff7a00 0%, #ff6700 38%, #ef3c22 100%);
}

.mi-hero-banner::before { position: absolute; right: -190px; bottom: -310px; width: 520px; height: 520px; background: rgba(255, 255, 255, .1); border-radius: 50%; content: ''; }
.mi-hero-copy { position: relative; z-index: 2; width: 52%; padding: 72px 0 0 72px; }
.mi-kicker,
.mi-section-head span,
.mi-content-head > div > span { display: block; margin-bottom: 9px; color: #ff6700; font-size: 12px; font-weight: 700; letter-spacing: .18em; }
.mi-hero-copy .mi-kicker { color: rgba(255, 255, 255, .82); }
.mi-hero-copy h1 { margin: 0; font-size: clamp(38px, 4vw, 58px); line-height: 1.14; letter-spacing: -.04em; }
.mi-hero-copy p { margin: 19px 0 26px; color: rgba(255, 255, 255, .82); font-size: 15px; }
.mi-hero-actions { display: flex; gap: 10px; }
.mi-hero-actions button { min-width: 116px; height: 42px; padding: 0 20px; color: #ff6700; background: #fff; border: 1px solid #fff; cursor: pointer; font-weight: 600; }
.mi-hero-actions button.ghost { color: #fff; background: transparent; }
.mi-hero-visual { position: absolute; z-index: 1; top: 35px; right: 36px; display: grid; width: 390px; height: 390px; place-items: center; }
.mi-hero-visual img { position: relative; z-index: 2; width: 82%; height: 82%; object-fit: contain; filter: drop-shadow(0 28px 35px rgba(88, 20, 0, .26)); }
.mi-hero-mark { position: relative; z-index: 2; display: grid; width: 190px; height: 190px; place-items: center; color: #ff6700; background: #fff; border-radius: 42px; box-shadow: 0 30px 60px rgba(100, 24, 0, .28); font-size: 96px; font-weight: 800; transform: rotate(-7deg); }
.mi-orbit { position: absolute; border: 1px solid rgba(255, 255, 255, .26); border-radius: 50%; }
.mi-orbit.one { inset: 12px; }
.mi-orbit.two { inset: 58px; }
.mi-hero-price { position: absolute; z-index: 3; right: 34px; bottom: 28px; display: flex; align-items: center; gap: 12px; padding: 10px 14px; background: rgba(82, 16, 0, .18); backdrop-filter: blur(10px); }
.mi-hero-price small { color: rgba(255, 255, 255, .72); }
.mi-hero-price strong { font-size: 16px; }

.mi-promo-row { display: grid; grid-template-columns: 234px repeat(3, minmax(0, 1fr)); gap: 14px; margin-bottom: 28px; }
.mi-shortcuts { display: grid; grid-template-columns: repeat(3, 1fr); background: #5f5750; }
.mi-shortcuts a { position: relative; display: flex; min-height: 82px; align-items: center; justify-content: center; flex-direction: column; gap: 6px; color: rgba(255, 255, 255, .68); font-size: 11px; }
.mi-shortcuts a::before,
.mi-shortcuts a::after { position: absolute; background: rgba(255, 255, 255, .08); content: ''; }
.mi-shortcuts a::before { top: 9px; bottom: 9px; left: 0; width: 1px; }
.mi-shortcuts a::after { top: 0; right: 9px; left: 9px; height: 1px; }
.mi-shortcuts a:hover { color: #fff; }
.mi-shortcuts a span { font-size: 20px; line-height: 1; }
.mi-promo-card { display: flex; min-width: 0; height: 164px; align-items: center; justify-content: space-between; gap: 10px; overflow: hidden; padding: 20px 16px 20px 22px; text-align: left; background: #fff; border: 0; cursor: pointer; transition: box-shadow .2s ease, transform .2s ease; }
.mi-promo-card:hover { transform: translateY(-2px); box-shadow: 0 14px 28px rgba(0, 0, 0, .1); }
.mi-promo-card > span { display: flex; min-width: 0; flex: 1; flex-direction: column; }
.mi-promo-card small { color: #ff6700; font-size: 11px; }
.mi-promo-card strong { display: -webkit-box; margin: 8px 0; overflow: hidden; color: #333; font-size: 16px; line-height: 1.35; -webkit-box-orient: vertical; -webkit-line-clamp: 2; }
.mi-promo-card em { color: #757575; font-size: 12px; font-style: normal; }
.mi-promo-card img { width: 116px; height: 116px; object-fit: contain; }

.mi-product-section { margin-top: 24px; }
.mi-section-head,
.mi-content-head { display: flex; align-items: flex-end; justify-content: space-between; margin-bottom: 16px; }
.mi-section-head h2,
.mi-content-head h1 { margin: 0; color: #333; font-size: 26px; font-weight: 400; }
.mi-section-head button { display: flex; align-items: center; gap: 8px; padding: 0; color: #424242; background: transparent; border: 0; cursor: pointer; font-size: 15px; }
.mi-section-head button:hover { color: #ff6700; }
.mi-section-head button b { display: grid; width: 20px; height: 20px; place-items: center; color: #fff; background: #b0b0b0; border-radius: 50%; font-size: 18px; font-weight: 300; }
.mi-section-head button:hover b { background: #ff6700; }
.mi-home-grid { display: grid; grid-template-columns: repeat(5, minmax(0, 1fr)); gap: 14px; min-height: 300px; }
.mi-grid { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 14px; min-height: 240px; }
.mi-card { position: relative; display: flex; min-width: 0; min-height: 330px; flex-direction: column; padding: 22px 16px 25px; text-align: center; background: #fff; border: 0; cursor: pointer; transition: box-shadow .2s ease, transform .2s ease; }
.mi-card:hover { transform: translateY(-3px); box-shadow: 0 15px 30px rgba(0, 0, 0, .1); }
.mi-card-image { display: grid; aspect-ratio: 1 / 1; height: 190px; place-items: center; overflow: hidden; background: #fff; }
.mi-card-image img { width: 100%; height: 100%; object-fit: contain; }
.mi-card h3 { display: -webkit-box; min-height: 40px; margin: 14px 0 4px; overflow: hidden; color: #333; font-size: 14px; font-weight: 400; line-height: 1.45; -webkit-box-orient: vertical; -webkit-line-clamp: 2; }
.mi-card p { overflow: hidden; margin: 0 0 10px; color: #b0b0b0; font-size: 12px; text-overflow: ellipsis; white-space: nowrap; }
.mi-card > strong { margin-top: auto; color: #ff6700; font-size: 16px; font-weight: 500; }
.mi-card > strong small { margin-right: 2px; font-size: 12px; }
.mi-card-tag { position: absolute; z-index: 2; top: 0; left: 50%; min-width: 64px; padding: 4px 8px; color: #fff; background: #ffac13; font-size: 11px; transform: translateX(-50%); }
.mi-card-tag.sold-out { background: #b0b0b0; }

.mi-breadcrumb { display: flex; align-items: center; flex-wrap: wrap; gap: 8px; padding: 18px 0; color: #757575; font-size: 12px; }
.mi-breadcrumb a,
.mi-breadcrumb button { padding: 0; color: #757575; background: none; border: 0; cursor: pointer; font: inherit; }
.mi-breadcrumb a:hover,
.mi-breadcrumb button:hover { color: #ff6700; }
.mi-breadcrumb em { color: #333; font-style: normal; }
.mi-layout { display: grid; grid-template-columns: 234px minmax(0, 1fr); gap: 14px; align-items: start; }
.mi-sidebar { position: sticky; top: 16px; padding: 18px 0 12px; background: #fff; }
.mi-sidebar h2 { margin: 0 20px 12px; color: #333; font-size: 17px; font-weight: 500; }
.mi-side-group { padding: 4px 0; }
.mi-side-item { display: block; width: 100%; padding: 8px 20px; color: #424242; text-align: left; background: transparent; border: 0; cursor: pointer; font: inherit; font-size: 14px; line-height: 1.5; }
.mi-side-item.is-parent { color: #333; font-weight: 600; }
.mi-side-item.is-child { padding-left: 32px; color: #757575; font-size: 13px; }
.mi-side-item:hover,
.mi-side-item.active { color: #ff6700; background: #fff8f2; }
.mi-content-head p,
.mi-content-head small { margin: 5px 0 0; color: #8c8c8c; font-size: 13px; }
.result-alert { margin: 0 0 16px; }
.product-pagination { display: flex; justify-content: center; margin-top: 28px; }

.mi-config-cta {
  display: inline-block;
  margin-top: 6px;
  color: #ff6700;
  font-size: 13px;
  font-style: normal;
}

@media (max-width: 1120px) {
  .mi-home-grid { grid-template-columns: repeat(4, minmax(0, 1fr)); }
  .mi-promo-row { grid-template-columns: 234px repeat(2, minmax(0, 1fr)); }
  .mi-promo-card:last-child { display: none; }
  .mi-hero-visual { right: 6px; width: 340px; }
}

@media (max-width: 900px) {
  .mi-hero-stage { grid-template-columns: 200px minmax(0, 1fr); }
  .mi-hero-copy { width: 70%; padding-left: 38px; }
  .mi-hero-visual { opacity: .42; }
  .mi-promo-row { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .mi-shortcuts { grid-row: span 2; }
  .mi-layout { grid-template-columns: 1fr; }
  .mi-sidebar { position: static; }
  .mi-grid { grid-template-columns: repeat(3, minmax(0, 1fr)); }
}

@media (max-width: 680px) {
  .mi-page { width: calc(100% - 20px); }
  .mi-hero-stage { grid-template-columns: 1fr; min-height: 380px; }
  .mi-hero-categories { display: none; }
  .mi-hero-copy { width: 90%; padding: 52px 24px; }
  .mi-promo-row { grid-template-columns: 1fr; }
  .mi-shortcuts { min-height: 150px; }
  .mi-promo-card { display: none; }
  .mi-home-grid,
  .mi-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .mi-card-image { height: 150px; }
}
</style>
