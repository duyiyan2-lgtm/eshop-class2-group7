<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { showConfirmDialog, showToast } from 'vant'
import { useRouter } from 'vue-router'
import { updateProfile } from '../../api/auth'
import { useAuthStore } from '../../stores/auth'

const router = useRouter()
const auth = useAuthStore()
const loading = ref(false)
const errorMessage = ref('')
const showEdit = ref(false)
const saving = ref(false)
const form = reactive({
  nickname: '',
  phone: '',
})

const avatarText = computed(() => (
  String(auth.user?.nickname || auth.user?.username || '用').trim().slice(0, 1)
))
const phoneValid = computed(() => (
  !form.phone.trim() || /^[0-9+\- ]{6,20}$/.test(form.phone.trim())
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

const openEdit = () => {
  form.nickname = auth.user?.nickname || ''
  form.phone = auth.user?.phone || ''
  showEdit.value = true
}

const saveProfile = async () => {
  if (saving.value) return false
  const nickname = form.nickname.trim()
  const phone = form.phone.trim()
  if (!nickname || nickname.length > 50) {
    showToast({
      type: 'fail',
      message: nickname ? '昵称不能超过 50 个字符' : '昵称不能为空',
    })
    return false
  }
  if (!phoneValid.value) {
    showToast({ type: 'fail', message: '联系电话格式不正确' })
    return false
  }

  saving.value = true
  try {
    const user = await updateProfile({ nickname, phone })
    auth.user = user
    localStorage.setItem('eshop_user', JSON.stringify(user))
    showToast('资料已更新')
    return true
  } catch (error) {
    showToast({ type: 'fail', message: error.message || '保存失败' })
    return false
  } finally {
    saving.value = false
  }
}

const beforeEditClose = (action) => {
  if (saving.value) return false
  if (action !== 'confirm') return true
  return saveProfile()
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
      <van-cell title="我的评价" is-link to="/m/reviews" icon="comment-o" />
      <van-cell title="我的收藏" is-link to="/m/favorites" icon="like-o" />
      <van-cell title="浏览历史" is-link to="/m/history" icon="clock-o" />
      <van-cell title="收货地址" is-link to="/m/addresses" icon="location-o" />
      <van-cell title="购物车" is-link to="/m/cart" icon="cart-o" />
      <van-cell title="编辑资料" is-link icon="edit" @click="openEdit" />
    </van-cell-group>

    <div class="profile-logout">
      <van-button round block type="danger" plain @click="handleLogout">退出登录</van-button>
    </div>

    <van-dialog
      v-model:show="showEdit"
      title="编辑资料"
      show-cancel-button
      :confirm-button-loading="saving"
      :before-close="beforeEditClose"
      close-on-click-overlay
    >
      <div class="form-body">
        <van-field
          v-model="form.nickname"
          label="昵称"
          placeholder="请输入昵称"
          maxlength="50"
          clearable
          required
        />
        <van-field
          v-model="form.phone"
          type="tel"
          label="联系电话"
          placeholder="6—20 位数字、空格、+ 或 -"
          maxlength="20"
          clearable
          :error-message="form.phone && !phoneValid ? '联系电话格式不正确' : ''"
        />
      </div>
    </van-dialog>
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

.form-body {
  padding: 8px 12px 4px;
}
</style>
