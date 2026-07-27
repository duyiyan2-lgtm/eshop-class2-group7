<script setup>
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const logout = async () => {
  await auth.signOut()
  router.push('/admin/login')
}
</script>

<template>
  <el-container class="admin-shell">
    <el-aside width="220px" class="admin-aside">
      <h1><span>E</span>-Shop 后台</h1>
      <el-menu router :default-active="route.path">
        <el-menu-item index="/admin">控制台</el-menu-item>
        <el-menu-item index="/admin/categories">分类管理</el-menu-item>
        <el-menu-item index="/admin/products">商品与 SKU</el-menu-item>
        <el-menu-item index="/admin/orders">订单管理</el-menu-item>
        <el-menu-item index="/admin/users">用户管理</el-menu-item>
        <el-menu-item index="/admin/logs">操作日志</el-menu-item>
      </el-menu>
    </el-aside>
    <el-container>
      <el-header class="admin-header">
        <div>
          <b>{{ route.meta.title || '商城运营后台' }}</b>
          <span>管理员：{{ auth.user?.nickname }}</span>
        </div>
        <el-button type="primary" plain @click="logout">退出登录</el-button>
      </el-header>
      <el-main class="admin-main"><RouterView /></el-main>
    </el-container>
  </el-container>
</template>
