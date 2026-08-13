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

const openOrders = (status) => {
  router.push({ name: 'mobile-orders', query: status ? { status } : undefined })
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

    <section class="profile-hero">
      <header class="profile-quick-actions">
        <button type="button" @click="router.push({ name: 'mobile-addresses' })"><van-icon name="location-o" /><small>地址</small></button>
        <button type="button" @click="showToast('如需售后，请从对应订单进入处理')"><van-icon name="service-o" /><small>客服</small></button>
        <button type="button" @click="openEdit"><van-icon name="setting-o" /><small>设置</small></button>
      </header>

      <div class="profile-card">
        <div class="profile-avatar">{{ avatarText }}</div>
        <div class="profile-info">
          <div class="profile-name">{{ auth.user?.nickname || '未设置昵称' }}</div>
          <div class="profile-username">账号：{{ auth.user?.username || 'unknown' }}</div>
          <div class="profile-phone">{{ auth.user?.phone || '完善联系电话，收货更顺利' }}</div>
        </div>
        <van-loading v-if="loading" color="#2563eb" size="22" />
      </div>

      <div class="member-summary">
        <div><span>账户安全</span><strong>已登录</strong></div>
        <div><span>购物权益</span><strong>品质保障</strong></div>
        <div><span>售后服务</span><strong>订单可查</strong></div>
      </div>
    </section>

    <section class="profile-panel order-panel">
      <header>
        <strong>我的订单</strong>
        <button type="button" @click="openOrders()">全部订单 <van-icon name="arrow" /></button>
      </header>
      <div class="order-shortcuts">
        <button type="button" @click="openOrders('PENDING_PAYMENT')"><van-icon name="balance-pay" /><span>待付款</span></button>
        <button type="button" @click="openOrders('PAID')"><van-icon name="gift-o" /><span>待发货</span></button>
        <button type="button" @click="openOrders('SHIPPED')"><van-icon name="logistics" /><span>待收货</span></button>
        <button type="button" @click="router.push({ name: 'mobile-reviews' })"><van-icon name="comment-o" /><span>待评价</span></button>
      </div>
    </section>

    <section class="profile-panel service-panel">
      <header><strong>常用服务</strong><small>管理你的商城资料</small></header>
      <div class="service-grid">
        <button type="button" @click="router.push({ name: 'mobile-addresses' })"><span><van-icon name="location-o" /></span><small>收货地址</small></button>
        <button type="button" @click="router.push({ name: 'mobile-favorites' })"><span><van-icon name="like-o" /></span><small>我的收藏</small></button>
        <button type="button" @click="router.push({ name: 'mobile-history' })"><span><van-icon name="clock-o" /></span><small>浏览足迹</small></button>
        <button type="button" @click="router.push({ name: 'mobile-reviews' })"><span><van-icon name="comment-circle-o" /></span><small>我的评价</small></button>
        <button type="button" @click="router.push({ name: 'mobile-cart' })"><span><van-icon name="cart-o" /></span><small>购物车</small></button>
        <button type="button" @click="openEdit"><span><van-icon name="edit" /></span><small>编辑资料</small></button>
      </div>
    </section>

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
  padding: 0 12px 24px;
  background: #07111f;
}

.profile-hero {
  margin: 0 -12px 14px;
  padding: calc(8px + var(--app-safe-top)) 14px 16px;
  color: #f8fafc;
  background:
    radial-gradient(circle at 92% 6%, rgba(96, 165, 250, .2), transparent 32%),
    linear-gradient(145deg, #0a1729, #10254a 58%, #1e1b4b);
  border-radius: 0 0 26px 26px;
  box-shadow: 0 16px 38px rgba(2, 8, 23, .3);
}

.profile-quick-actions { display: flex; justify-content: flex-end; gap: 8px; margin-bottom: 6px; }
.profile-quick-actions button { display: grid; min-width: 48px; justify-items: center; gap: 2px; padding: 5px; color: #cbd5e1; font: inherit; background: rgba(255, 255, 255, .045); border: 1px solid rgba(148, 163, 184, .12); border-radius: 12px; }
.profile-quick-actions :deep(.van-icon) { font-size: 21px; }
.profile-quick-actions small { font-size: 9px; }

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
  margin-bottom: 14px;
  padding: 4px 4px 10px;
  color: #f8fafc;
  background: rgba(255, 255, 255, .08);
  border: 1px solid rgba(147, 197, 253, .22);
  border-radius: 18px;
  box-shadow: 0 12px 28px rgba(2, 8, 23, .22);
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
  color: #fff;
  background: linear-gradient(145deg, #3b82f6, #7c3aed);
  border: 2px solid rgba(219, 234, 254, .55);
  box-shadow: 0 10px 25px rgba(37, 99, 235, .28);
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
  color: #a8b8ce;
}

.member-summary {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  padding: 14px 8px;
  background: rgba(255, 255, 255, .94);
  border: 1px solid rgba(219, 234, 254, .32);
  border-radius: 18px;
  box-shadow: 0 10px 25px rgba(37, 99, 235, .08);
}

.member-summary > div { display: grid; gap: 4px; padding: 0 8px; text-align: center; }
.member-summary > div + div { border-left: 1px solid #e5eaf1; }
.member-summary span { color: #64748b; font-size: 10px; }
.member-summary strong { color: #1e3a8a; font-size: 12px; }

.profile-panel { margin-bottom: 12px; padding: 17px 14px; background: #f8fafc; border: 1px solid rgba(147, 197, 253, .2); border-radius: 20px; box-shadow: 0 12px 30px rgba(2, 8, 23, .22); }
.profile-panel > header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 16px; }
.profile-panel > header strong { color: #0f172a; font-size: 17px; }
.profile-panel > header button { padding: 4px; color: #94a3b8; font: inherit; font-size: 11px; background: transparent; border: 0; }
.profile-panel > header small { color: #94a3b8; font-size: 10px; }

.order-shortcuts { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 6px; }
.order-shortcuts button { display: grid; justify-items: center; gap: 7px; padding: 5px 0; color: #334155; font: inherit; background: transparent; border: 0; }
.order-shortcuts :deep(.van-icon) { color: #2563eb; font-size: 27px; }
.order-shortcuts span { font-size: 11px; font-weight: 600; }

.service-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 18px 8px; }
.service-grid button { display: grid; justify-items: center; gap: 7px; padding: 0; color: #475569; font: inherit; background: transparent; border: 0; }
.service-grid button > span { display: grid; width: 43px; height: 43px; place-items: center; color: #2563eb; background: #eff6ff; border-radius: 15px; font-size: 21px; }
.service-grid button:nth-child(2n) > span { color: #7c3aed; background: #f5f3ff; }
.service-grid small { font-size: 11px; font-weight: 600; }

.profile-logout {
  padding: 6px 16px 0;
}

.profile-logout :deep(.van-button) {
  color: #fda4af;
  background: rgba(136, 19, 55, .12);
  border-color: rgba(251, 113, 133, .5);
}

.form-body {
  padding: 8px 12px 4px;
}
</style>
