<script setup>
import { computed, onMounted, ref } from 'vue'
import { showConfirmDialog, showToast } from 'vant'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../../stores/auth'

const router = useRouter()
const auth = useAuthStore()
const loading = ref(false)
const errorMessage = ref('')

const avatarText = computed(() => (
  String(auth.user?.nickname || auth.user?.username || '用').trim().slice(0, 1)
))

const refreshProfile = async () => {
  if (loading.value) return
  loading.value = true
  errorMessage.value = ''
  try {
    await auth.refreshCurrentUser()
  } catch (error) {
    errorMessage.value = error.message || '个人资料加载失败'
  } finally {
    loading.value = false
  }
}

const handleLogout = async () => {
  try {
    await showConfirmDialog({
      title: '退出登录',
      message: '确定要退出当前账号吗？',
      confirmButtonText: '退出',
      cancelButtonText: '取消',
    })
    await auth.signOut()
    await router.replace({ name: 'mobile-login' })
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      showToast({ type: 'fail', message: error.message || '退出失败' })
    }
  }
}

onMounted(refreshProfile)
</script>

<template>
  <section class="mobile-profile">
    <van-notice-bar
      v-if="errorMessage"
      color="#dc2626"
      background="#fef2f2"
      left-icon="warning-o"
    >
      <span>{{ errorMessage }}</span>
      <button class="notice-action" type="button" @click="refreshProfile">重试</button>
    </van-notice-bar>

    <div class="profile-card">
      <div class="profile-avatar">{{ avatarText }}</div>
      <div class="profile-info">
        <div class="profile-name">{{ auth.user?.nickname || '未设置昵称' }}</div>
        <div class="profile-username">@{{ auth.user?.username || 'unknown' }}</div>
        <div class="profile-phone">{{ auth.user?.phone || '未填写联系电话' }}</div>
      </div>
      <van-loading v-if="loading" color="#ffffff" size="22" />
    </div>

    <van-cell-group inset class="profile-menu">
      <van-cell title="我的订单" is-link to="/m/orders" icon="orders-o" />
      <van-cell title="收货地址" is-link to="/m/addresses" icon="location-o" />
      <van-cell title="购物车" is-link to="/m/cart" icon="cart-o" />
    </van-cell-group>

    <div class="profile-logout">
      <van-button round block type="danger" plain @click="handleLogout">退出登录</van-button>
    </div>
  </section>
</template>

<style scoped>
.mobile-profile {
  box-sizing: border-box;
  min-height: 100%;
  padding: 16px;
  background: #f7f8fa;
}

.notice-action {
  margin-left: 8px;
  padding: 0;
  border: 0;
  color: #2563eb;
  background: transparent;
}

.profile-card {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 16px;
  padding: 24px 20px;
  color: #fff;
  background: linear-gradient(135deg, #2563eb 0%, #6d28d9 100%);
  border-radius: 14px;
  box-shadow: 0 12px 30px rgba(37, 99, 235, 0.2);
}

.profile-avatar {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  justify-content: center;
  width: 60px;
  height: 60px;
  font-size: 26px;
  font-weight: 700;
  background: rgba(255, 255, 255, 0.24);
  border: 1px solid rgba(255, 255, 255, 0.3);
  border-radius: 50%;
}

.profile-info {
  flex: 1;
  min-width: 0;
}

.profile-name {
  overflow: hidden;
  font-size: 20px;
  font-weight: 700;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.profile-username,
.profile-phone {
  margin-top: 4px;
  overflow: hidden;
  font-size: 13px;
  text-overflow: ellipsis;
  white-space: nowrap;
  opacity: 0.84;
}

.profile-menu {
  margin-bottom: 24px;
}

.profile-logout {
  padding: 0 16px;
}
</style>
