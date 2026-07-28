import axios from 'axios'
import { getToken, getRefreshToken, setToken, clearAuth } from '@/utils/auth'
import { ElMessage } from 'element-plus'

const request = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api',
  timeout: 15000
})

let refreshPromise = null

function refreshAccessToken() {
  const refresh = getRefreshToken()
  if (!refresh) return Promise.reject(new Error('缺少刷新令牌'))
  if (!refreshPromise) {
    refreshPromise = axios
      .post(`${request.defaults.baseURL}/token/refresh/`, { refresh })
      .then((response) => {
        const access = response.data?.access
        if (!access) throw new Error('刷新令牌响应无效')
        setToken(access)
        return access
      })
      .finally(() => {
        refreshPromise = null
      })
  }
  return refreshPromise
}


// 请求拦截器
request.interceptors.request.use(
  (config) => {
    const token = getToken()
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  (error) => Promise.reject(error)
)

// 响应拦截器
request.interceptors.response.use(
  (response) => response.data,
  async (error) => {
    const { response, config } = error

    if (response) {
      // 登录和注册接口不自动跳转
      const requestUrl = config?.url || ''
      const isAuthRequest = requestUrl.includes('/users/login') ||
                            requestUrl.includes('/users/register') || requestUrl.includes('/token/refresh')
      

      if (response.status === 401 && !isAuthRequest && !config._retry && getRefreshToken()) {
        config._retry = true
        try {
          const access = await refreshAccessToken()
          config.headers.Authorization = `Bearer ${access}`
          return request(config)
        } catch {
          clearAuth()
        }
      }
      // 收藏和推荐相关接口不自动跳转
      const isOptionalAuthRequest = requestUrl.includes('/is_favorited') ||
                                    requestUrl.includes('/user-recommend') ||
                                    requestUrl.includes('/favorites')
      
      switch (response.status) {
        case 401:
          if (!isAuthRequest && !isOptionalAuthRequest) {
            // 非登录请求的401才跳转
            clearAuth()
            ElMessage.error('登录已过期，请重新登录')
            window.location.href = '/login'
          }
          // 登录请求和可选认证请求的401不跳转，让业务代码处理
          break
        case 403:
          if (!isAuthRequest) {
            ElMessage.error('没有权限访问')
          }
          break
        case 404:
          ElMessage.error('请求的资源不存在')
          break
        case 500:
          ElMessage.error('服务器错误，请稍后重试')
          break
        default:
          // 其他错误让业务代码处理
          break
      }
    } else {
      ElMessage.error('网络连接失败，请检查网络')
    }

    return Promise.reject(error)
  }
)

/**
 * GET请求
 */
export function get(url, params = {}) {
  return request({ url, method: 'get', params })
}

/**
 * POST请求
 */
export function post(url, data = {}) {
  return request({ url, method: 'post', data })
}

/**
 * PUT请求
 */
export function put(url, data = {}) {
  return request({ url, method: 'put', data })
}

/**
 * DELETE请求
 */
export function del(url, params = {}) {
  return request({ url, method: 'delete', params })
}

export default request