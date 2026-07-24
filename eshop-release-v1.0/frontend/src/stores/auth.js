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
  }),
  getters: {
    isLoggedIn: (state) => Boolean(state.token),
    isAdmin: (state) => state.user?.role === 'ADMIN',
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
    async signOut() {
      let remoteLogoutSucceeded = true
      try {
        if (this.token) await authApi.logout()
      } catch {
        remoteLogoutSucceeded = false
      } finally {
        this.token = ''
        this.user = null
        localStorage.removeItem('eshop_token')
        localStorage.removeItem('eshop_user')
      }
      return remoteLogoutSucceeded
    },
  },
})
