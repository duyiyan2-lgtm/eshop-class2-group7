<script setup>
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const loggingOut = ref(false)

const pageTitle = computed(() => route.meta.title || 'E-Shop')
const showTabbar = computed(() => Boolean(route.meta.mobileTabbar))
const loginTarget = computed(() => ({
  name: 'mobile-login',
  query: { redirect: route.fullPath },
}))

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
</script>

<template>
  <div class="mobile-shell">
    <van-nav-bar
      :title="pageTitle"
      :left-arrow="!showTabbar"
      fixed
      placeholder
      safe-area-inset-top
      @click-left="goBack"
    >
      <template #right>
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
      </template>
    </van-nav-bar>
    <main class="mobile-main"><RouterView /></main>
    <van-tabbar
      v-if="showTabbar"
      class="mobile-tabbar"
      route
      fixed
      placeholder
      safe-area-inset-bottom
    >
      <van-tabbar-item replace to="/m/products" icon="home-o">首页</van-tabbar-item>
      <van-tabbar-item replace to="/m/cart" icon="cart-o">购物车</van-tabbar-item>
      <van-tabbar-item replace to="/m/orders" icon="orders-o">订单</van-tabbar-item>
      <van-tabbar-item replace to="/m/profile" icon="user-o">我的</van-tabbar-item>
    </van-tabbar>
  </div>
</template>
