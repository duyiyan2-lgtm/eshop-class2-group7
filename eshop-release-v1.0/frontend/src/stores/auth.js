import { defineStore } from 'pinia'
import * as authApi from '../api/auth'

const readUser = () => {
  try {
    return JSON.parse(localStorage.getItem('eshop_user') || 'null')
  } catch {
    return null
  }
}

export const useAuthStore = defineStore('auth', {
  state: () => ({
    token: localStorage.getItem('eshop_token') || '',
    user: readUser(),
    initialized: false,
  }),
  getters: {
    isLoggedIn: (state) => Boolean(state.token),
    isAdmin: (state) => state.user?.role === 'ADMIN',
    isSeller: (state) => state.user?.role === 'SELLER',
    canManageStore: (state) => ['ADMIN', 'SELLER'].includes(state.user?.role),
  },
  actions: {
    async signIn(payload) {
      const data = await authApi.login(payload)
      this.token = data.token
      this.user = {
        userId: data.userId,
        username: data.username,
        nickname: data.nickname,
        role: data.role,
      }
      localStorage.setItem('eshop_token', this.token)
      localStorage.setItem('eshop_user', JSON.stringify(this.user))
      this.initialized = true
      return data
    },
    async signUp(payload) {
      await authApi.register(payload)
    },
    async refreshCurrentUser() {
      const data = await authApi.getCurrentUser()
      this.user = data
      localStorage.setItem('eshop_user', JSON.stringify(data))
      return data
    },
    clearSession() {
      this.token = ''
      this.user = null
      localStorage.removeItem('eshop_token')
      localStorage.removeItem('eshop_user')
    },
    async initialize() {
      if (this.initialized) return this.user
      try {
        if (!this.token) return null
        const data = await authApi.getCurrentUser({ skipAuthRedirect: true })
        this.user = data
        localStorage.setItem('eshop_user', JSON.stringify(data))
        return data
      } catch {
        this.clearSession()
        return null
      } finally {
        this.initialized = true
      }
    },
    async signOut() {
      let remoteLogoutSucceeded = true
      try {
        if (this.token) await authApi.logout()
      } catch {
        remoteLogoutSucceeded = false
      } finally {
        this.clearSession()
        this.initialized = true
      }
      return remoteLogoutSucceeded
    },
  },
})
