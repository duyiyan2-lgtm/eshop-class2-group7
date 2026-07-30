<script setup>
import { computed, ref } from 'vue'
import { ElMessageBox } from 'element-plus'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'

const router = useRouter()
const auth = useAuthStore()
const switchingKey = ref('')

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
    try {
      await ElMessageBox.confirm(
        `当前登录的是${roleLabel.value}账号。进入${workspace.title}需要更换账号，是否退出当前账号？`,
        '切换账号',
        {
          confirmButtonText: '退出并继续',
          cancelButtonText: '取消',
          type: 'warning',
        },
      )
    } catch {
      return
    }
  }

  switchingKey.value = workspace.key
  try {
    if (workspace.accountSwitch) await auth.signOut()
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
  </main>
</template>

<style scoped>
.portal-page {
  min-height: 100vh;
  padding: 0 5vw 36px;
  color: #e2e8f0;
  background:
    radial-gradient(circle at 15% 18%, rgba(37, 99, 235, .25), transparent 32%),
    radial-gradient(circle at 85% 8%, rgba(14, 165, 233, .15), transparent 28%),
    #07111f;
}

.portal-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 78px;
  border-bottom: 1px solid rgba(148, 163, 184, .18);
}

.portal-brand {
  color: #fff;
  font-size: 24px;
  font-weight: 900;
  letter-spacing: -.03em;
}

.portal-brand span { color: #60a5fa; }

.portal-account {
  display: flex;
  align-items: center;
  gap: 12px;
  color: #94a3b8;
  font-size: 13px;
}

.portal-account > div { display: grid; text-align: right; }
.portal-account strong { color: #f8fafc; font-size: 14px; }
.portal-account em {
  padding: 6px 10px;
  color: #bfdbfe;
  background: rgba(37, 99, 235, .2);
  border: 1px solid rgba(96, 165, 250, .25);
  border-radius: 999px;
  font-style: normal;
}

.portal-account button,
.portal-account a {
  padding: 8px 12px;
  color: #e2e8f0;
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
  color: #60a5fa;
  font-size: 12px;
  font-weight: 800;
  letter-spacing: .16em;
}

.hero-copy h1 {
  max-width: 760px;
  margin: 0 0 18px;
  color: #fff;
  font-size: clamp(38px, 5vw, 64px);
  line-height: 1.08;
  letter-spacing: -.045em;
}

.hero-copy > span {
  display: block;
  max-width: 720px;
  color: #94a3b8;
  font-size: 17px;
  line-height: 1.8;
}

.hero-status {
  display: grid;
  gap: 5px;
  padding: 20px;
  background: rgba(15, 23, 42, .72);
  border: 1px solid rgba(148, 163, 184, .2);
  border-radius: 16px;
  backdrop-filter: blur(12px);
}

.hero-status small,
.hero-status span { color: #94a3b8; }
.hero-status strong { color: #fff; font-size: 24px; }
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
  background: rgba(15, 23, 42, .78);
  border: 1px solid rgba(148, 163, 184, .18);
  border-radius: 20px;
  box-shadow: 0 18px 60px rgba(0, 0, 0, .2);
  transition: transform .2s ease, border-color .2s ease;
}

.workspace-card:hover {
  border-color: rgba(96, 165, 250, .5);
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
  color: #f8fafc;
  font-size: 23px;
  line-height: 1.25;
}

.workspace-card > span {
  min-height: 72px;
  color: #94a3b8;
  font-size: 14px;
  line-height: 1.7;
}

.workspace-access {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 20px 0;
  color: #cbd5e1;
  font-size: 12px;
}

.workspace-access i {
  width: 7px;
  height: 7px;
  background: #60a5fa;
  border-radius: 50%;
  box-shadow: 0 0 0 5px rgba(96, 165, 250, .12);
}

.workspace-card button {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  margin-top: auto;
  padding: 12px 14px;
  color: #fff;
  background: #2563eb;
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
.workspace-card.is-purple button { background: #7c3aed; }
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
}
</style>
