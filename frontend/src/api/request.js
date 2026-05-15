import axios from 'axios'

const BASE_URL = import.meta.env.VITE_API_BASE_URL || '/api'

const cache = new Map()
const CACHE_TTL = 30000

function getCacheKey(config) {
  return config.method + ':' + config.url + ':' + JSON.stringify(config.params || {})
}

function getCached(key) {
  const entry = cache.get(key)
  if (!entry) return null
  if (Date.now() - entry.time > CACHE_TTL) {
    cache.delete(key)
    return null
  }
  return entry.data
}

function setCache(key, data) {
  if (cache.size > 50) {
    const oldest = cache.keys().next().value
    if (oldest) cache.delete(oldest)
  }
  cache.set(key, { data, time: Date.now() })
}

// 创建 axios 实例
const request = axios.create({
  baseURL: BASE_URL,
  timeout: 30000,
  headers: {
    'Content-Type': 'application/json',
  },
})

// 请求拦截器
request.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('token')
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    if (config.method === 'get' || config.method === 'GET') {
      const key = getCacheKey(config)
      const cached = getCached(key)
      if (cached) {
        config.adapter = () => Promise.resolve({
          data: cached,
          status: 200,
          statusText: 'OK',
          headers: {},
          config,
        })
      }
    }
    return config
  },
  (error) => {
    return Promise.reject(error)
  }
)

// 401 跳转登录
function redirectLogin() {
  localStorage.removeItem('token')
  localStorage.removeItem('user')
  window.location.href = '/login'
}

// 响应拦截器
request.interceptors.response.use(
  (response) => {
    const res = response.data
    if (res.code !== 0) {
      if (res.code === 401) redirectLogin()
      return Promise.reject(new Error(res.message || '请求失败'))
    }
    if (response.config.method === 'get' || response.config.method === 'GET') {
      const key = getCacheKey(response.config)
      setCache(key, res)
    }
    return res
  },
  (error) => {
    if (error.response && error.response.status === 401) {
      redirectLogin()
    }
    console.error('API 请求错误:', error)
    return Promise.reject(error)
  }
)

export default request
