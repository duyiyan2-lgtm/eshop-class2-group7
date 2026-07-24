import axios from 'axios'

/**
 * 公共 axios 实例
 * - baseURL 指向后端 Spring Boot（context-path=/api）
 * - 启动时自动从 localStorage 读取 JWT 加到 Authorization header
 */
const http = axios.create({
  baseURL: 'http://localhost:8080/api',
  timeout: 10000,
})

http.interceptors.request.use((config) => {
  // 从 localStorage 取真实 JWT（登录后由 autoLogin 写入）
  const token = typeof window !== 'undefined'
    ? window.localStorage?.getItem('eshop-jwt-token')
    : null
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

export default http