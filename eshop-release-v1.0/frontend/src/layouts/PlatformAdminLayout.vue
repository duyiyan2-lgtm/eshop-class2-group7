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
      <h1><span>E</span>-Shop 平台</h1>
      <el-menu router :default-active="route.path">
        <el-menu-item index="/">统一入口</el-menu-item>
        <el-menu-item index="/admin/users">买家与卖家</el-menu-item>
      </el-menu>
      <div class="merchant-entry">
        <p>需要查看商城运营功能？</p>
        <RouterLink to="/seller">进入商家工作台 →</RouterLink>
      </div>
    </el-aside>
    <el-container>
      <el-header class="admin-header">
        <div>
          <b>{{ route.meta.title || '平台账号管理' }}</b>
          <span>平台管理员：{{ auth.user?.nickname }}</span>
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

<style scoped>
.merchant-entry {
  margin: 24px 16px;
  padding: 16px;
  color: #94a3b8;
  background: rgba(255, 255, 255, 0.06);
  border-radius: 12px;
}

.merchant-entry p {
  margin: 0 0 10px;
  font-size: 12px;
  line-height: 1.5;
}

.merchant-entry a {
  color: #93c5fd;
  font-size: 13px;
  font-weight: 700;
}
</style>
