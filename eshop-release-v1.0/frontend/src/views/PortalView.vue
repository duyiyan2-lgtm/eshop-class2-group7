<script setup>
import { computed, onUnmounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'

const router = useRouter()
const auth = useAuthStore()
const switchingKey = ref('')
const pendingWorkspace = ref(null)

const roleLabel = computed(() => ({
  USER: '买家',
  SELLER: '商家',
  ADMIN: '平台管理员',
})[auth.user?.role] || '访客')

const workspaces = computed(() => [
  {
    key: 'pc',
    index: '01',
    eyebrow: 'PC STORE',
    title: 'PC 消费者商城',
    description: '适合电脑浏览商品、购物车、下单、支付、订单和评价。',
    target: '/pc/products',
    access: '所有人可浏览',
    action: '进入 PC 商城',
    tone: 'blue',
    accountSwitch: false,
  },
  {
    key: 'mobile',
    index: '02',
    eyebrow: 'MOBILE H5',
    title: '手机 H5 商城',
    description: '适合手机访问，与 PC 端共享账号、购物车和订单数据。',
    target: '/m/products',
    access: '所有人可浏览',
    action: '进入手机商城',
    tone: 'green',
    accountSwitch: false,
  },
  {
    key: 'seller',
    index: '03',
    eyebrow: 'SELLER CONSOLE',
    title: '商家运营工作台',
    description: '管理商品、SKU、库存、订单发货、评价和经营数据。',
    target: auth.canManageStore ? '/seller' : '/seller/login',
    access: auth.canManageStore ? '当前账号可进入' : '需要已启用的商家账号',
    action: auth.canManageStore
      ? '进入商家工作台'
      : auth.isLoggedIn ? '退出并切换商家账号' : '商家登录',
    tone: 'orange',
    accountSwitch: auth.isLoggedIn && !auth.canManageStore,
  },
  {
    key: 'admin',
    index: '04',
    eyebrow: 'PLATFORM ADMIN',
    title: '平台账号管理',
    description: '审核商家，管理买家、卖家的角色与账号启停状态。',
    target: auth.isAdmin ? '/admin/users' : '/admin/login',
    access: auth.isAdmin ? '当前账号可进入' : '仅平台管理员可进入',
    action: auth.isAdmin
      ? '进入平台管理'
      : auth.isLoggedIn ? '退出并切换管理员账号' : '管理员登录',
    tone: 'purple',
    accountSwitch: auth.isLoggedIn && !auth.isAdmin,
  },
])

const openWorkspace = async (workspace) => {
  if (switchingKey.value) return
  if (workspace.accountSwitch) {
    pendingWorkspace.value = workspace
    return
  }

  switchingKey.value = workspace.key
  try {
    await router.push(workspace.target)
  } finally {
    switchingKey.value = ''
  }
}

const closeSwitchDialog = () => {
  if (switchingKey.value) return
  pendingWorkspace.value = null
}

const confirmWorkspaceSwitch = async () => {
  const workspace = pendingWorkspace.value
  if (!workspace || switchingKey.value) return
  switchingKey.value = workspace.key
  pendingWorkspace.value = null
  try {
    await auth.signOut()
    await router.push(workspace.target)
  } finally {
    switchingKey.value = ''
  }
}

const logout = async () => {
  if (switchingKey.value) return
  switchingKey.value = 'logout'
  try {
    await auth.signOut()
  } finally {
    switchingKey.value = ''
  }
}

const handleEscape = (event) => {
  if (event.key === 'Escape') closeSwitchDialog()
}

watch(pendingWorkspace, (workspace) => {
  document.body.style.overflow = workspace ? 'hidden' : ''
  if (workspace) {
    window.addEventListener('keydown', handleEscape)
  } else {
    window.removeEventListener('keydown', handleEscape)
  }
})

onUnmounted(() => {
  document.body.style.overflow = ''
  window.removeEventListener('keydown', handleEscape)
})
</script>

<template>
  <main class="portal-page">
    <header class="portal-header">
      <RouterLink class="portal-brand" to="/">
        <span>E</span>-Shop
      </RouterLink>
      <div class="portal-account">
        <template v-if="auth.isLoggedIn">
          <div>
            <small>当前账号</small>
            <strong>{{ auth.user?.nickname || auth.user?.username }}</strong>
          </div>
          <em>{{ roleLabel }}</em>
          <button :disabled="Boolean(switchingKey)" type="button" @click="logout">退出</button>
        </template>
        <template v-else>
          <span>当前以访客身份浏览</span>
          <RouterLink to="/pc/login">登录 / 注册</RouterLink>
        </template>
      </div>
    </header>

    <section class="portal-hero">
      <div class="hero-copy">
        <p>E-SHOP UNIFIED PORTAL</p>
        <h1>一个页面，切换所有商城入口</h1>
        <span>消费者、商家与平台管理员共用统一导航，系统会根据当前账号角色提供正确入口。</span>
      </div>
      <div class="hero-status">
        <small>当前身份</small>
        <strong>{{ roleLabel }}</strong>
        <span>{{ auth.isLoggedIn ? `账号：${auth.user?.username}` : '登录后可使用更多工作台' }}</span>
      </div>
    </section>

    <section class="workspace-grid">
      <article
        v-for="workspace in workspaces"
        :key="workspace.key"
        class="workspace-card"
        :class="`is-${workspace.tone}`"
      >
        <div class="card-number">{{ workspace.index }}</div>
        <p>{{ workspace.eyebrow }}</p>
        <h2>{{ workspace.title }}</h2>
        <span>{{ workspace.description }}</span>
        <div class="workspace-access">
          <i />
          {{ workspace.access }}
        </div>
        <button
          :disabled="Boolean(switchingKey)"
          type="button"
          @click="openWorkspace(workspace)"
        >
          {{ switchingKey === workspace.key ? '正在切换…' : workspace.action }}
          <b>→</b>
        </button>
      </article>
    </section>

    <footer>
      <span>PC 消费者端 · 手机 H5 端 · Web 商家/平台管理端</span>
      <span>角色权限保持隔离，切换入口不会绕过后端鉴权。</span>
    </footer>

    <Teleport to="body">
      <Transition name="switch-dialog">
        <div
          v-if="pendingWorkspace"
          class="switch-dialog-backdrop"
          @click.self="closeSwitchDialog"
        >
          <section
            class="switch-dialog-card"
            role="dialog"
            aria-modal="true"
            aria-labelledby="switch-dialog-title"
          >
            <button
              class="dialog-close"
              type="button"
              aria-label="关闭"
              @click="closeSwitchDialog"
            >
              ×
            </button>
            <div class="dialog-icon"><span>↗</span></div>
            <p>WORKSPACE SWITCH</p>
            <h2 id="switch-dialog-title">需要切换登录账号</h2>
            <span class="dialog-description">
              当前{{ roleLabel }}账号没有进入“{{ pendingWorkspace.title }}”的权限，
              退出后将带你前往对应的登录页面。
            </span>

            <div class="identity-flow">
              <article>
                <small>当前身份</small>
                <strong>{{ roleLabel }}</strong>
                <span>{{ auth.user?.nickname || auth.user?.username }}</span>
              </article>
              <b>→</b>
              <article class="target-identity">
                <small>目标入口</small>
                <strong>{{ pendingWorkspace.title }}</strong>
                <span>{{ pendingWorkspace.access }}</span>
              </article>
            </div>

            <div class="dialog-notice">
              <i>i</i>
              <span>退出只会清除当前浏览器的登录状态，不会删除账号或业务数据。</span>
            </div>

            <div class="dialog-actions">
              <button class="cancel-button" type="button" @click="closeSwitchDialog">暂不切换</button>
              <button
                class="confirm-button"
                type="button"
                :disabled="Boolean(switchingKey)"
                @click="confirmWorkspaceSwitch"
              >
                {{ switchingKey ? '正在切换…' : '退出并前往登录' }}
                <span>→</span>
              </button>
            </div>
          </section>
        </div>
      </Transition>
    </Teleport>
  </main>
</template>

<style scoped>
.portal-page {
  min-height: 100vh;
  padding: 0 5vw 36px;
  color: #333;
  background:
    radial-gradient(circle at 15% 18%, rgba(225, 37, 27, .25), transparent 32%),
    radial-gradient(circle at 85% 8%, rgba(14, 165, 233, .15), transparent 28%),
    #f5f5f5;
}

.portal-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 78px;
  border-bottom: 1px solid rgba(148, 163, 184, .18);
}

.portal-brand {
  color: #1a1a1a;
  font-size: 24px;
  font-weight: 900;
  letter-spacing: -.03em;
}

.portal-brand span { color: #e1251b; }

.portal-account {
  display: flex;
  align-items: center;
  gap: 12px;
  color: #666;
  font-size: 13px;
}

.portal-account > div { display: grid; text-align: right; }
.portal-account strong { color: #1a1a1a; font-size: 14px; }
.portal-account em {
  padding: 6px 10px;
  color: #c8161d;
  background: #fff1f0;
  border: 1px solid rgba(255, 143, 31, .25);
  border-radius: 999px;
  font-style: normal;
}

.portal-account button,
.portal-account a {
  padding: 8px 12px;
  color: #333;
  background: transparent;
  border: 1px solid rgba(148, 163, 184, .35);
  border-radius: 9px;
  cursor: pointer;
}

.portal-hero {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 220px;
  gap: 40px;
  align-items: end;
  max-width: 1240px;
  margin: 0 auto;
  padding: 72px 0 48px;
}

.hero-copy p,
.workspace-card > p {
  margin: 0 0 10px;
  color: #ff8f1f;
  font-size: 12px;
  font-weight: 800;
  letter-spacing: .16em;
}

.hero-copy h1 {
  max-width: 920px;
  margin: 0 0 18px;
  color: #1a1a1a;
  font-size: clamp(38px, 4.5vw, 58px);
  line-height: 1.08;
  letter-spacing: -.045em;
}

.hero-copy > span {
  display: block;
  max-width: 720px;
  color: #666;
  font-size: 17px;
  line-height: 1.8;
}

.hero-status {
  display: grid;
  gap: 5px;
  padding: 20px;
  background: #ffffff;
  border: 1px solid rgba(148, 163, 184, .2);
  border-radius: 16px;
  backdrop-filter: blur(12px);
}

.hero-status small,
.hero-status span { color: #94a3b8; }
.hero-status strong { color: #1a1a1a; font-size: 24px; }
.hero-status span { font-size: 12px; }

.workspace-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
  max-width: 1240px;
  margin: 0 auto;
}

.workspace-card {
  position: relative;
  display: flex;
  min-height: 340px;
  flex-direction: column;
  padding: 26px;
  overflow: hidden;
  background: #ffffff;
  border: 1px solid rgba(148, 163, 184, .18);
  border-radius: 20px;
  box-shadow: 0 14px 36px rgba(200, 22, 29, .08);
  transition: transform .2s ease, border-color .2s ease;
}

.workspace-card:hover {
  border-color: rgba(255, 143, 31, .5);
  transform: translateY(-4px);
}

.card-number {
  position: absolute;
  top: 16px;
  right: 20px;
  color: rgba(148, 163, 184, .16);
  font-size: 48px;
  font-weight: 900;
}

.workspace-card h2 {
  max-width: 210px;
  margin: 8px 0 14px;
  color: #1a1a1a;
  font-size: 23px;
  line-height: 1.25;
}

.workspace-card > span {
  min-height: 72px;
  color: #666;
  font-size: 14px;
  line-height: 1.7;
}

.workspace-access {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 20px 0;
  color: #666;
  font-size: 12px;
}

.workspace-access i {
  width: 7px;
  height: 7px;
  background: #ff8f1f;
  border-radius: 50%;
  box-shadow: 0 0 0 5px rgba(255, 143, 31, .12);
}

.workspace-card button {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  margin-top: auto;
  padding: 12px 14px;
  color: #fff;
  background: #e1251b;
  border: 0;
  border-radius: 10px;
  cursor: pointer;
  font: inherit;
  font-weight: 700;
}

.workspace-card button:disabled { cursor: wait; opacity: .7; }
.workspace-card button b { font-size: 18px; }
.workspace-card.is-green button { background: #059669; }
.workspace-card.is-orange button { background: #d97706; }
.workspace-card.is-purple button { background: #ff6a00; }
.workspace-card.is-green .workspace-access i { background: #34d399; }
.workspace-card.is-orange .workspace-access i { background: #fbbf24; }
.workspace-card.is-purple .workspace-access i { background: #a78bfa; }

footer {
  display: flex;
  justify-content: space-between;
  gap: 20px;
  max-width: 1240px;
  margin: 32px auto 0;
  color: #64748b;
  font-size: 12px;
}

.switch-dialog-backdrop {
  position: fixed;
  z-index: 3000;
  inset: 0;
  display: grid;
  padding: 24px;
  background: rgba(0, 0, 0, .45);
  backdrop-filter: blur(10px);
  place-items: center;
}

.switch-dialog-card {
  position: relative;
  width: min(100%, 520px);
  padding: 34px;
  overflow: hidden;
  color: #333;
  background:
    radial-gradient(circle at 100% 0, rgba(255, 106, 0, .2), transparent 42%),
    #ffffff;
  border: 1px solid rgba(167, 139, 250, .28);
  border-radius: 24px;
  box-shadow: 0 24px 60px rgba(0, 0, 0, .18);
}

.switch-dialog-card::before {
  position: absolute;
  top: 0;
  right: 0;
  left: 0;
  height: 3px;
  background: linear-gradient(90deg, #e1251b, #ff6a00, #ec4899);
  content: "";
}

.dialog-close {
  position: absolute;
  top: 18px;
  right: 18px;
  display: grid;
  width: 34px;
  height: 34px;
  padding: 0;
  color: #94a3b8;
  background: rgba(148, 163, 184, .09);
  border: 1px solid rgba(148, 163, 184, .16);
  border-radius: 50%;
  cursor: pointer;
  font-size: 23px;
  line-height: 1;
  place-items: center;
}

.dialog-close:hover { color: #1a1a1a; background: rgba(148, 163, 184, .16); }

.dialog-icon {
  display: grid;
  width: 54px;
  height: 54px;
  margin-bottom: 20px;
  color: #fff;
  background: linear-gradient(135deg, #e1251b, #ff6a00);
  border-radius: 16px;
  box-shadow: 0 12px 30px rgba(99, 102, 241, .32);
  font-size: 26px;
  place-items: center;
}

.switch-dialog-card > p {
  margin: 0 0 8px;
  color: #a78bfa;
  font-size: 11px;
  font-weight: 900;
  letter-spacing: .18em;
}

.switch-dialog-card h2 {
  margin: 0 0 12px;
  color: #1a1a1a;
  font-size: 28px;
  letter-spacing: -.025em;
}

.dialog-description {
  display: block;
  color: #94a3b8;
  font-size: 14px;
  line-height: 1.75;
}

.identity-flow {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto minmax(0, 1fr);
  gap: 12px;
  align-items: center;
  margin: 24px 0 16px;
}

.identity-flow article {
  display: grid;
  gap: 5px;
  min-height: 98px;
  padding: 15px;
  background: #fff7f4;
  border: 1px solid rgba(148, 163, 184, .16);
  border-radius: 14px;
}

.identity-flow > b { color: #64748b; font-size: 20px; }
.identity-flow small { color: #64748b; }
.identity-flow strong { color: #1a1a1a; font-size: 17px; }
.identity-flow span { color: #94a3b8; font-size: 12px; line-height: 1.45; }
.identity-flow .target-identity {
  background: rgba(255, 106, 0, .1);
  border-color: rgba(167, 139, 250, .3);
}

.dialog-notice {
  display: flex;
  align-items: flex-start;
  gap: 9px;
  padding: 12px 14px;
  color: #9a3412;
  background: rgba(225, 37, 27, .09);
  border: 1px solid rgba(255, 143, 31, .17);
  border-radius: 12px;
  font-size: 12px;
  line-height: 1.6;
}

.dialog-notice i {
  display: grid;
  flex: 0 0 auto;
  width: 18px;
  height: 18px;
  color: #c8161d;
  border: 1px solid #e1251b;
  border-radius: 50%;
  font-size: 11px;
  font-style: normal;
  place-items: center;
}

.dialog-actions {
  display: grid;
  grid-template-columns: 1fr 1.55fr;
  gap: 12px;
  margin-top: 24px;
}

.dialog-actions button {
  min-height: 46px;
  padding: 0 16px;
  border-radius: 11px;
  cursor: pointer;
  font: inherit;
  font-weight: 800;
}

.cancel-button {
  color: #666;
  background: transparent;
  border: 1px solid rgba(148, 163, 184, .3);
}

.confirm-button {
  display: flex;
  align-items: center;
  justify-content: space-between;
  color: #fff;
  background: linear-gradient(100deg, #e1251b, #ff6a00);
  border: 0;
  box-shadow: 0 12px 26px rgba(99, 102, 241, .25);
}

.dialog-actions button:hover { transform: translateY(-1px); }
.dialog-actions button:disabled { cursor: wait; opacity: .65; transform: none; }
.confirm-button span { font-size: 18px; }

.switch-dialog-enter-active,
.switch-dialog-leave-active { transition: opacity .2s ease; }
.switch-dialog-enter-active .switch-dialog-card,
.switch-dialog-leave-active .switch-dialog-card { transition: transform .22s ease, opacity .2s ease; }
.switch-dialog-enter-from,
.switch-dialog-leave-to { opacity: 0; }
.switch-dialog-enter-from .switch-dialog-card,
.switch-dialog-leave-to .switch-dialog-card { opacity: 0; transform: translateY(18px) scale(.97); }

@media (max-width: 1050px) {
  .workspace-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
}

@media (max-width: 700px) {
  .portal-page { padding: 0 18px 28px; }
  .portal-header { align-items: flex-start; flex-direction: column; gap: 12px; padding: 18px 0; }
  .portal-account { width: 100%; flex-wrap: wrap; }
  .portal-account > div { text-align: left; }
  .portal-hero { grid-template-columns: 1fr; gap: 22px; padding: 44px 0 30px; }
  .hero-copy h1 { font-size: 39px; }
  .workspace-grid { grid-template-columns: 1fr; }
  .workspace-card { min-height: 300px; }
  footer { align-items: flex-start; flex-direction: column; }
  .switch-dialog-backdrop { padding: 14px; }
  .switch-dialog-card { padding: 28px 20px 22px; border-radius: 20px; }
  .switch-dialog-card h2 { padding-right: 34px; font-size: 24px; }
  .identity-flow { grid-template-columns: 1fr; }
  .identity-flow > b { transform: rotate(90deg); text-align: center; }
  .identity-flow article { min-height: auto; }
  .dialog-actions { grid-template-columns: 1fr; }
}
</style>
