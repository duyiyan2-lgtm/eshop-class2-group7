<script setup>
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'

const props = defineProps({
  terminal: { type: String, required: true },
  allowedRoles: { type: Array, default: () => [] },
  registrationRoles: { type: Array, default: () => [] },
  title: { type: String, default: 'E-Shop 电子商城' },
  loginSubtitle: { type: String, default: '登录后继续使用' },
})

const registrationRoleMeta = {
  USER: {
    label: '买家',
    description: '浏览商品、购物、下单和评价',
  },
  SELLER: {
    label: '商家',
    description: '注册后等待平台管理员审核启用',
  },
}

const router = useRouter()
const route = useRoute()
const auth = useAuthStore()
const mode = ref('login')
const username = ref('')
const password = ref('')
const nickname = ref('')
const registrationRole = ref('USER')
const loading = ref(false)
const errorMessage = ref('')
const messageType = ref('error')
const restrictedTerminal = computed(() => props.allowedRoles.length > 0)
const registrationOptions = computed(() =>
  props.registrationRoles
    .filter((role) => registrationRoleMeta[role])
    .map((role) => ({ role, ...registrationRoleMeta[role] })),
)
const canRegister = computed(() => registrationOptions.value.length > 0)
const selectedRegistration = computed(() =>
  registrationRoleMeta[registrationRole.value] || registrationRoleMeta.USER,
)
const registerSubtitle = computed(() =>
  registrationRole.value === 'SELLER'
    ? '创建商家账号，审核通过后进入工作台'
    : '创建买家账号，立即开始购物',
)

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

const switchMode = () => {
  errorMessage.value = ''
  messageType.value = 'error'
  if (mode.value === 'login') {
    if (!canRegister.value) return
    registrationRole.value = registrationOptions.value[0].role
    mode.value = 'register'
    return
  }
  mode.value = 'login'
}

const submit = async () => {
  errorMessage.value = ''
  messageType.value = 'error'
  loading.value = true
  try {
    if (mode.value === 'register') {
      await auth.signUp({
        username: username.value,
        password: password.value,
        nickname: nickname.value,
        role: registrationRole.value,
      })
      mode.value = 'login'
      password.value = ''
      messageType.value = 'success'
      errorMessage.value = registrationRole.value === 'SELLER'
        ? '商家注册成功，请等待平台管理员审核启用后登录'
        : '买家注册成功，请登录'
      return
    }
    const user = await auth.signIn({ username: username.value, password: password.value })
    if (restrictedTerminal.value && !props.allowedRoles.includes(user.role)) {
      await auth.signOut()
      throw new Error('该账号没有进入当前工作台的权限')
    }
    if (!restrictedTerminal.value && user.role === 'SELLER') {
      await router.replace('/seller')
      return
    }
    if (!restrictedTerminal.value && user.role === 'ADMIN') {
      await router.replace('/admin')
      return
    }
    await router.replace(redirectPath.value)
  } catch (error) {
    messageType.value = 'error'
    errorMessage.value = error.message || '操作失败，请重试'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <form class="auth-form" @submit.prevent="submit">
    <h1>{{ title }}</h1>
    <p class="auth-subtitle">{{ mode === 'login' ? loginSubtitle : registerSubtitle }}</p>

    <label>
      用户名
      <input v-model.trim="username" autocomplete="username" minlength="3" maxlength="50" required />
    </label>
    <fieldset v-if="mode === 'register' && registrationOptions.length > 1" class="registration-roles">
      <legend>注册身份</legend>
      <label
        v-for="option in registrationOptions"
        :key="option.role"
        class="registration-role"
        :class="{ selected: registrationRole === option.role }"
      >
        <input v-model="registrationRole" type="radio" name="registrationRole" :value="option.role" />
        <span>
          <strong>{{ option.label }}</strong>
          <small>{{ option.description }}</small>
        </span>
      </label>
    </fieldset>
    <div v-else-if="mode === 'register'" class="registration-single-role">
      注册类型：<strong>{{ selectedRegistration.label }}</strong>
      <span>{{ selectedRegistration.description }}</span>
    </div>
    <label v-if="mode === 'register'">
      昵称
      <input v-model.trim="nickname" maxlength="50" required />
    </label>
    <label>
      密码
      <input
        v-model="password"
        type="password"
        :autocomplete="mode === 'register' ? 'new-password' : 'current-password'"
        minlength="6"
        maxlength="72"
        required
      />
    </label>

    <p v-if="errorMessage" class="auth-message" :class="{ 'is-success': messageType === 'success' }">
      {{ errorMessage }}
    </p>
    <button :disabled="loading" type="submit">
      {{ loading ? '处理中…' : mode === 'login' ? '登录' : `注册${selectedRegistration.label}账号` }}
    </button>
    <button v-if="canRegister" class="auth-link" type="button" @click="switchMode">
      {{ mode === 'login' ? '没有账号？选择身份注册' : '已有账号？返回登录' }}
    </button>
  </form>
</template>
