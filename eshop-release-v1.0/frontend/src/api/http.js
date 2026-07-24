import axios from 'axios'

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
  const isMobile = window.location.pathname.startsWith('/m')
  const loginPath = isAdmin ? '/admin/login' : isMobile ? '/m/login' : '/pc/login'

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
    return body.data
  },
  (error) => {
    const requestUrl = error.config?.url || ''
    const isAuthenticationRequest = requestUrl.includes('/auth/login')
      || requestUrl.includes('/auth/logout')
    if (error.response?.status === 401 && !isAuthenticationRequest) {
      redirectToLogin()
    }
    const message = error.response?.data?.message || error.message || '网络异常，请稍后重试'
    return Promise.reject(new Error(message))
  },
)

export default http
