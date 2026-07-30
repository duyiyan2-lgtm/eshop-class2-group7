<script setup>
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'

const props = defineProps({
  terminal: { type: String, required: true },
  allowedRoles: { type: Array, default: () => [] },
  title: { type: String, default: 'E-Shop 电子商城' },
  loginSubtitle: { type: String, default: '登录后继续使用' },
})

const router = useRouter()
const route = useRoute()
const auth = useAuthStore()
const mode = ref('login')
const username = ref('')
const password = ref('')
const nickname = ref('')
const loading = ref(false)
const errorMessage = ref('')
const restrictedTerminal = computed(() => props.allowedRoles.length > 0)

const redirectPath = computed(() => {
  const fallback = ({ pc: '/pc', mobile: '/m', admin: '/admin', seller: '/seller' })[props.terminal]
  const requested = route.query.redirect
  const terminalPrefix = ({ pc: '/pc', mobile: '/m', admin: '/admin', seller: '/seller' })[
    props.terminal
  ]
  if (
    typeof requested === 'string'
    && requested.startsWith('/')
    && !requested.startsWith('//')
    && (!restrictedTerminal.value || requested.startsWith(terminalPrefix))
  ) {
    return requested
  }
  return fallback
})

const submit = async () => {
  errorMessage.value = ''
  loading.value = true
  try {
    if (mode.value === 'register') {
      await auth.signUp({ username: username.value, password: password.value, nickname: nickname.value })
      mode.value = 'login'
      password.value = ''
      errorMessage.value = '注册成功，请登录'
      return
    }
    const user = await auth.signIn({ username: username.value, password: password.value })
    if (restrictedTerminal.value && !props.allowedRoles.includes(user.role)) {
      await auth.signOut()
      throw new Error('该账号没有进入当前工作台的权限')
    }
    await router.replace(redirectPath.value)
  } catch (error) {
    errorMessage.value = error.message || '操作失败，请重试'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <form class="auth-form" @submit.prevent="submit">
    <h1>{{ title }}</h1>
    <p class="auth-subtitle">{{ mode === 'login' ? loginSubtitle : '创建消费者账号' }}</p>

    <label>
      用户名
      <input v-model.trim="username" autocomplete="username" minlength="3" maxlength="50" required />
    </label>
    <label v-if="mode === 'register'">
      昵称
      <input v-model.trim="nickname" maxlength="50" required />
    </label>
    <label>
      密码
      <input v-model="password" type="password" autocomplete="current-password" minlength="6" maxlength="72" required />
    </label>

    <p v-if="errorMessage" class="auth-message">{{ errorMessage }}</p>
    <button :disabled="loading" type="submit">{{ loading ? '处理中…' : mode === 'login' ? '登录' : '注册' }}</button>
    <button v-if="!restrictedTerminal" class="auth-link" type="button" @click="mode = mode === 'login' ? 'register' : 'login'">
      {{ mode === 'login' ? '没有账号？立即注册' : '已有账号？返回登录' }}
    </button>
  </form>
</template>
