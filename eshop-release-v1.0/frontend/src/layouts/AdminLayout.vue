<script setup>
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const logout = async () => {
  await auth.signOut()
  router.push('/seller/login')
}
</script>

<template>
  <el-container class="admin-shell seller-shell">
    <el-aside width="220px" class="admin-aside">
      <h1><span>E</span>-Shop 商家</h1>
      <el-menu router :default-active="route.path">
        <el-menu-item index="/">统一入口</el-menu-item>
        <el-menu-item v-if="auth.isAdmin" index="/admin/users">平台账号管理</el-menu-item>
        <el-menu-item index="/seller">经营看板</el-menu-item>
        <el-menu-item index="/seller/categories">分类管理</el-menu-item>
        <el-menu-item index="/seller/products">商品与 SKU</el-menu-item>
        <el-menu-item index="/seller/inventory">库存预警</el-menu-item>
        <el-menu-item index="/seller/orders">订单管理</el-menu-item>
        <el-menu-item index="/seller/reviews">评价管理</el-menu-item>
        <el-menu-item index="/seller/logs">操作日志</el-menu-item>
      </el-menu>
    </el-aside>
    <el-container class="admin-workspace">
      <el-header class="admin-header">
        <div>
          <b>{{ route.meta.title || '商家运营工作台' }}</b>
          <span>商家账号：{{ auth.user?.nickname }}</span>
        </div>
        <div class="admin-header-actions">
          <el-button @click="router.push('/')">切换入口</el-button>
          <el-button type="primary" plain @click="logout">退出登录</el-button>
        </div>
      </el-header>
      <el-main class="admin-main"><RouterView /></el-main>
    </el-container>
  </el-container>
</template>
