import http from '../api/http'

/**
 * 联调期间临时方案：页面加载时自动用 admin 登录获取 JWT，
 * 写入 localStorage，让 order API 等需要鉴权的接口能调通。
 *
 * TODO: 等组长集成登录页后，本文件可删除。
 */
const TOKEN_KEY = 'eshop-jwt-token'

export async function ensureAuthToken() {
  if (typeof window === 'undefined') return null
  const cached = window.localStorage.getItem(TOKEN_KEY)
  if (cached) return cached

  try {
    const { data } = await http.post('/auth/login', {
      username: 'admin',
      password: 'admin123',
    })
    const token = data?.data?.token
    if (token) {
      window.localStorage.setItem(TOKEN_KEY, token)
      return token
    }
  } catch (error) {
    console.warn('[auto-login] failed:', error.message)
  }
  return null
}