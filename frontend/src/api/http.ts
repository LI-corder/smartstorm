import axios from 'axios'
import router from '@/router'

/**
 * Axios 实例：统一 /api 前缀。
 * 开发环境经 vite proxy 转发到后端 8080，生产环境可改为完整后端地址。
 */
const http = axios.create({
  baseURL: '/api',
  timeout: 15000
})

// 请求拦截：登录后自动携带 Authorization: Bearer token
http.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('sc_token')
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  (err) => Promise.reject(err)
)

// 响应拦截：统一错误提示；401 时清除登录态并跳登录页
http.interceptors.response.use(
  (res) => res,
  (err) => {
    const msg = err?.response?.data?.message || err.message || '网络错误'
    if (err?.response?.status === 401) {
      localStorage.removeItem('sc_token')
      if (router.currentRoute.value.path !== '/login') {
        router.push('/login')
      }
    }
    console.error('[API Error]', msg, err)
    return Promise.reject(err)
  }
)

export default http
