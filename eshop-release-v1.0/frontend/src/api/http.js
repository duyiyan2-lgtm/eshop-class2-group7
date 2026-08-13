import axios from 'axios'
import { normalizeMediaPayload } from '../utils/media'

const TOKEN_KEY = 'eshop_token'
const USER_KEY = 'eshop_user'

const http = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api',
  timeout: 10000,
})

http.interceptors.request.use((config) => {
  const token = localStorage.getItem(TOKEN_KEY)
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

const redirectToLogin = () => {
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(USER_KEY)

  const currentPath = `${window.location.pathname}${window.location.search}${window.location.hash}`
  const isAdmin = window.location.pathname.startsWith('/admin')
  const isSeller = window.location.pathname.startsWith('/seller')
  const isMobile = window.location.pathname.startsWith('/m')
  const loginPath = isAdmin
    ? '/admin/login'
    : isSeller
      ? '/seller/login'
      : isMobile
        ? '/m/login'
        : '/pc/login'

  if (window.location.pathname === loginPath) return
  const redirect = isAdmin ? '' : `?redirect=${encodeURIComponent(currentPath)}`
  window.location.replace(`${loginPath}${redirect}`)
}

http.interceptors.response.use(
  (response) => {
    const body = response.data
    if (body?.code !== 0) {
      return Promise.reject(new Error(body?.message || '请求失败'))
    }
    return normalizeMediaPayload(body.data)
  },
  (error) => {
    const requestUrl = error.config?.url || ''
    const isAuthenticationRequest = requestUrl.includes('/auth/login')
      || requestUrl.includes('/auth/logout')
    if (error.response?.status === 401 && !isAuthenticationRequest && !error.config?.skipAuthRedirect) {
      redirectToLogin()
    }
    const status = error.response?.status
    let message = error.response?.data?.message
    if (!message && !error.response) {
      message = error.code === 'ECONNABORTED' || String(error.message).toLowerCase().includes('timeout')
        ? '请求超时，请检查网络后重试'
        : '网络连接失败，请检查网络后重试'
    }
    if (!message && status === 403) message = '当前账号没有执行此操作的权限'
    if (!message && status === 429) message = '操作过于频繁，请稍后再试'
    if (!message && status === 413) message = '上传文件过大，请选择不超过 5 MB 的图片'
    if (!message && [502, 503, 504].includes(status)) message = '服务暂时不可用，请稍后重试'
    message ||= error.message || '请求失败，请稍后重试'
    return Promise.reject(new Error(message))
  },
)

export default http
