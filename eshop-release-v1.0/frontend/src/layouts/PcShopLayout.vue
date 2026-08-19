<script setup>
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getCart } from '../api/cart'
import { getCategories } from '../api/catalog'
import { useAuthStore } from '../stores/auth'
import { CART_UPDATED_EVENT } from '../utils/cartBadge'

const router = useRouter()
const route = useRoute()
const auth = useAuthStore()
const keywordInput = ref('')
const categories = ref([])
const cartCount = ref(0)
let cartRequestSequence = 0

const flattenCategories = (list, depth = 0) => {
  const result = []
  ;(list || []).forEach((item) => {
    result.push({ id: item.id, name: item.name, depth })
    result.push(...flattenCategories(item.children, depth + 1))
  })
  return result
}

const categoryNav = computed(() => flattenCategories(categories.value).slice(0, 7))

const isFullscreen = computed(() => Boolean(route.meta.fullscreen))

const cartBadge = computed(() => {
  if (cartCount.value <= 0) return ''
  return cartCount.value > 99 ? '99+' : String(cartCount.value)
})

const activeCategoryId = computed(() => {
  const value = Number(route.query.categoryId)
  return Number.isInteger(value) && value > 0 ? value : undefined
})

const loadCategories = async () => {
  try {
    categories.value = await getCategories()
  } catch {
    categories.value = []
  }
}

const loadCartCount = async () => {
  const requestId = ++cartRequestSequence
  if (!auth.isLoggedIn) {
    cartCount.value = 0
    return
  }
  try {
    const items = await getCart()
    if (requestId !== cartRequestSequence || !auth.isLoggedIn) return
    cartCount.value = (Array.isArray(items) ? items : []).reduce(
      (total, item) => total + Math.max(0, Number(item.quantity) || 0),
      0,
    )
  } catch {
    if (requestId === cartRequestSequence) cartCount.value = 0
  }
}

const search = () => {
  const keyword = keywordInput.value.trim()
  router.push({
    name: 'pc-products',
    query: {
      ...(keyword ? { keyword } : {}),
      ...(activeCategoryId.value ? { categoryId: activeCategoryId.value } : {}),
    },
  })
}

const selectCategory = (id) => {
  const keyword = typeof route.query.keyword === 'string' ? route.query.keyword.trim() : ''
  router.push({
    name: 'pc-products',
    query: {
      ...(id ? { categoryId: id } : {}),
      ...(keyword ? { keyword } : {}),
    },
  })
}

const logout = async () => {
  await auth.signOut()
  router.push('/pc/login')
}

onMounted(() => {
  void loadCategories()
  void loadCartCount()
  window.addEventListener(CART_UPDATED_EVENT, loadCartCount)
})

onUnmounted(() => {
  cartRequestSequence += 1
  window.removeEventListener(CART_UPDATED_EVENT, loadCartCount)
})

watch(
  [() => auth.token, () => route.fullPath],
  () => {
    if (typeof route.query.keyword === 'string') {
      keywordInput.value = route.query.keyword
    } else if (route.name === 'pc-products') {
      keywordInput.value = ''
    }
    void loadCartCount()
  },
  { immediate: true },
)
</script>

<template>
  <RouterView v-if="isFullscreen" />
  <div v-else class="pc-shell">
    <div class="pc-topbar">
      <div class="pc-topbar-inner">
        <div class="pc-top-links">
          <RouterLink to="/pc/products">E-Shop 商城</RouterLink>
          <RouterLink to="/m/products">手机商城</RouterLink>
          <RouterLink to="/seller">商家工作台</RouterLink>
          <RouterLink class="workspace-switch-link" to="/">切换入口</RouterLink>
        </div>
        <div class="pc-user-actions">
          <RouterLink to="/pc/orders">我的订单</RouterLink>
          <RouterLink to="/pc/favorites">收藏</RouterLink>
          <RouterLink v-if="!auth.isLoggedIn" to="/pc/login">登录 / 注册</RouterLink>
          <template v-else>
            <RouterLink to="/pc/profile">你好，{{ auth.user?.nickname || auth.user?.username }}</RouterLink>
            <button type="button" @click="logout">退出</button>
          </template>
        </div>
      </div>
    </div>

    <header class="pc-header">
      <div class="pc-header-inner">
        <RouterLink class="brand" to="/pc">
          <span>E</span>
          <div>
            E-Shop
            <small>精选商城</small>
          </div>
        </RouterLink>
        <nav class="pc-primary-nav" aria-label="商城主导航">
          <RouterLink to="/pc/products">商城首页</RouterLink>
          <RouterLink to="/pc/vehicles">汽车选配</RouterLink>
          <button
            v-for="category in categoryNav"
            :key="category.id"
            type="button"
            :class="{ active: activeCategoryId === category.id }"
            @click="selectCategory(category.id)"
          >
            {{ category.name }}
          </button>
        </nav>
        <div class="pc-header-tools">
          <form class="pc-search" @submit.prevent="search">
            <input
              v-model="keywordInput"
              maxlength="120"
              placeholder="搜索商品"
            />
            <button type="submit" aria-label="搜索商品">
              <svg viewBox="0 0 24 24" width="21" height="21" aria-hidden="true">
                <path fill="currentColor" d="m20.5 19-4.1-4.1a7 7 0 1 0-1.5 1.5l4.1 4.1zM5 11a6 6 0 1 1 12 0 6 6 0 0 1-12 0"/>
              </svg>
            </button>
          </form>
          <RouterLink class="pc-cart-entry" to="/pc/cart">
            <svg viewBox="0 0 24 24" width="19" height="19" aria-hidden="true">
              <path fill="currentColor" d="M7 18c-1.1 0-1.99.9-1.99 2S5.9 22 7 22s2-.9 2-2-.9-2-2-2m10 0c-1.1 0-1.99.9-1.99 2S15.9 22 17 22s2-.9 2-2-.9-2-2-2M7.2 14.8l.1.2c.2.5.7.8 1.2.8h9.4c.5 0 .9-.3 1.1-.8l2.4-6.2A1 1 0 0 0 20.5 7H6.2L5.3 4.6A1 1 0 0 0 4.4 4H2v2h1.7l3.6 8.6-.9 1.6C5.7 17 6.3 18 7.3 18h12.2v-2H7.4z"/>
            </svg>
            <span>购物车</span>
            <em v-if="cartBadge" class="pc-cart-badge">{{ cartBadge }}</em>
          </RouterLink>
        </div>
      </div>
    </header>

    <main class="pc-main"><RouterView /></main>

    <section class="pc-service-strip" aria-label="商城服务">
      <div class="pc-service-inner">
        <button
          type="button"
          @click="router.push('/pc/products')"
        >
          <span>✓</span> 品质商品
        </button>
        <button
          type="button"
          @click="router.push('/pc/cart')"
        >
          <span>◎</span> 便捷购物车
        </button>
        <button type="button" @click="router.push('/pc/orders')"><span>◇</span> 订单可追踪</button>
        <button type="button" @click="router.push('/pc/addresses')"><span>⌖</span> 收货地址管理</button>
        <button type="button" @click="router.push('/m/products')"><span>▣</span> PC 与手机共享</button>
      </div>
    </section>

    <footer class="pc-footer">
      <div class="pc-footer-inner">
        <div class="pc-footer-grid">
          <section>
            <h3>购物指南</h3>
            <RouterLink to="/pc/products">浏览商品</RouterLink>
            <RouterLink to="/pc/vehicles">汽车选配</RouterLink>
            <RouterLink to="/pc/cart">购物车</RouterLink>
            <RouterLink to="/pc/checkout">结算下单</RouterLink>
          </section>
          <section>
            <h3>订单服务</h3>
            <RouterLink to="/pc/orders">我的订单</RouterLink>
            <RouterLink to="/pc/reviews">我的评价</RouterLink>
            <RouterLink to="/pc/addresses">收货地址</RouterLink>
          </section>
          <section>
            <h3>个人中心</h3>
            <RouterLink to="/pc/profile">个人资料</RouterLink>
            <RouterLink to="/pc/favorites">我的收藏</RouterLink>
            <RouterLink to="/pc/history">浏览历史</RouterLink>
          </section>
          <section>
            <h3>商城入口</h3>
            <RouterLink to="/">统一入口</RouterLink>
            <RouterLink to="/m/products">手机商城</RouterLink>
            <RouterLink to="/pc/login">登录 / 注册</RouterLink>
          </section>
        </div>
        <div class="pc-footer-copy">E-Shop 精选商城 · 课程项目演示系统 · 商品、购物车与订单数据多端共享</div>
      </div>
    </footer>
  </div>
</template>
