const TOKEN_KEY = 'token'
const REFRESH_TOKEN_KEY = 'refreshToken'
const USER_INFO_KEY = 'userInfo'

/**
 * 获取token
 * @returns {string} token值
 */
export function getToken() {
  return localStorage.getItem(TOKEN_KEY) || ''
}

/**
 * 设置token
 * @param {string} token - token值
 */
export function setToken(token) {
  localStorage.setItem(TOKEN_KEY, token)
}

/**
 * 移除token
 */
export function removeToken() {
  localStorage.removeItem(TOKEN_KEY)
}
/** 获取刷新令牌。 */
export function getRefreshToken() {
  return localStorage.getItem(REFRESH_TOKEN_KEY) || ''
}

/** 保存刷新令牌。 */
export function setRefreshToken(token) {
  localStorage.setItem(REFRESH_TOKEN_KEY, token)
}

/** 移除刷新令牌。 */
export function removeRefreshToken() {
  localStorage.removeItem(REFRESH_TOKEN_KEY)
}


/**
 * 获取用户信息
 * @returns {Object|null} 用户信息对象
 */
export function getUserInfo() {
  const info = localStorage.getItem(USER_INFO_KEY)
  try {
    return info ? JSON.parse(info) : null
  } catch {
    return null
  }
}

/**
 * 设置用户信息
 * @param {Object} info - 用户信息对象
 */
export function setUserInfo(info) {
  localStorage.setItem(USER_INFO_KEY, JSON.stringify(info))
}

/**
 * 移除用户信息
 */
export function removeUserInfo() {
  localStorage.removeItem(USER_INFO_KEY)
}

/**
 * 清除所有认证信息
 */
export function clearAuth() {
  removeToken()
  removeUserInfo()
  removeRefreshToken()
}
