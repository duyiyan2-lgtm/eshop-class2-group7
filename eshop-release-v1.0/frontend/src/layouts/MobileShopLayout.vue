<script setup>
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getCart } from '../api/cart'
import { useAuthStore } from '../stores/auth'
import { CART_UPDATED_EVENT } from '../utils/cartBadge'
import { nativeState } from '../native/runtime'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const loggingOut = ref(false)
const cartCount = ref(0)
let cartRequestSequence = 0

const pageTitle = computed(() => (
  route.name === 'mobile-products' ? 'E-Shop' : route.meta.title || 'E-Shop'
))
const showTabbar = computed(() => Boolean(route.meta.mobileTabbar))
const hideNavBar = computed(() => (
  ['mobile-products', 'mobile-cart', 'mobile-profile', 'mobile-orders'].includes(String(route.name || ''))
))
const cartBadge = computed(() => {
  if (cartCount.value <= 0) return ''
  return cartCount.value > 99 ? '99+' : cartCount.value
})
const loginTarget = computed(() => ({
  name: 'mobile-login',
  query: { redirect: route.fullPath },
}))

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

const handleCartUpdated = () => {
  void loadCartCount()
}

const goBack = () => {
  if (window.history.state?.back) {
    router.back()
    return
  }
  router.replace({ name: 'mobile-products' })
}

const logout = async () => {
  if (loggingOut.value) return
  loggingOut.value = true
  try {
    await auth.signOut()
    await router.replace({ name: 'mobile-login' })
  } finally {
    loggingOut.value = false
  }
}

onMounted(() => {
  void loadCartCount()
  window.addEventListener(CART_UPDATED_EVENT, handleCartUpdated)
})

onUnmounted(() => {
  cartRequestSequence += 1
  window.removeEventListener(CART_UPDATED_EVENT, handleCartUpdated)
})

watch(
  [() => auth.token, () => route.fullPath],
  () => {
    void loadCartCount()
  },
)
</script>

<template>
  <div class="mobile-shell">
    <div v-if="nativeState.isNative" class="native-status-scrim" aria-hidden="true" />
    <van-nav-bar
      v-if="!hideNavBar"
      :title="pageTitle"
      :left-arrow="!showTabbar"
      fixed
      placeholder
      safe-area-inset-top
      @click-left="goBack"
    >
      <template #right>
        <div class="mobile-nav-actions">
          <RouterLink v-if="!nativeState.isNative" class="workspace-switch-link" to="/">切换</RouterLink>
          <van-button
            v-if="auth.isLoggedIn"
            :loading="loggingOut"
            size="small"
            type="primary"
            plain
            hairline
            @click="logout"
          >
            退出
          </van-button>
          <RouterLink v-else class="mobile-login-link" :to="loginTarget">登录</RouterLink>
        </div>
      </template>
    </van-nav-bar>
    <main class="mobile-main"><RouterView /></main>
    <van-tabbar
      v-if="showTabbar"
      class="mobile-tabbar"
      route
      fixed
      :placeholder="!nativeState.isNative"
      :safe-area-inset-bottom="!nativeState.isNative"
    >
      <van-tabbar-item replace to="/m/products" icon="home-o">首页</van-tabbar-item>
      <van-tabbar-item replace to="/m/cart" icon="cart-o" :badge="cartBadge">购物车</van-tabbar-item>
      <van-tabbar-item replace to="/m/orders" icon="orders-o">订单</van-tabbar-item>
      <van-tabbar-item replace to="/m/profile" icon="user-o">我的</van-tabbar-item>
    </van-tabbar>
  </div>
</template>
