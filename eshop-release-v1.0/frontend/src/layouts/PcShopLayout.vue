<script setup>
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'

const router = useRouter()
const auth = useAuthStore()

const logout = async () => {
  await auth.signOut()
  router.push('/pc/login')
}
</script>

<template>
  <el-container class="pc-shell">
    <el-header class="pc-header">
      <RouterLink class="brand" to="/pc"><span>E</span>-Shop<small>PC STORE</small></RouterLink>
      <nav>
        <RouterLink to="/pc/products">商品商城</RouterLink>
        <RouterLink to="/pc/cart">购物车</RouterLink>
        <RouterLink to="/pc/favorites">我的收藏</RouterLink>
        <RouterLink to="/pc/history">浏览历史</RouterLink>
        <RouterLink to="/pc/addresses">收货地址</RouterLink>
        <RouterLink to="/pc/orders">我的订单</RouterLink>
        <RouterLink to="/pc/reviews">我的评价</RouterLink>
        <RouterLink to="/pc/profile">个人资料</RouterLink>
      </nav>
      <div class="pc-user-actions">
        <RouterLink class="workspace-switch-link" to="/">切换入口</RouterLink>
        <RouterLink v-if="!auth.isLoggedIn" to="/pc/login">登录 / 注册</RouterLink>
        <template v-else>
          <span>你好，{{ auth.user?.nickname }}</span>
          <el-button link type="primary" @click="logout">退出</el-button>
        </template>
      </div>
    </el-header>
    <el-main class="pc-main"><RouterView /></el-main>
  </el-container>
</template>
