import request from './index'

/**
 * 用户登录
 * @param {Object} data - 登录数据
 * @param {string} data.username - 用户名
 * @param {string} data.password - 密码
 * @returns {Promise}
 */
export function loginApi(data) {
  return request({
    url: '/users/login/',
    method: 'post',
    data
  })
}

/**
 * 用户注册
 * @param {Object} data - 注册数据
 * @param {string} data.username - 用户名
 * @param {string} data.password - 密码
 * @param {string} data.phone - 手机号
 * @param {string} data.role - 角色（tenant/landlord）
 * @returns {Promise}
 */
export function registerApi(data) {
  return request({
    url: '/users/register/',
    method: 'post',
    data
  })
}

/**
 * 获取当前用户信息
 * @returns {Promise}
 */
export function getUserInfoApi() {
  return request({
    url: '/users/info/',
    method: 'get'
  })
}

/**
 * 检查用户名或手机号是否唯一
 * @param {string} field - 字段名（username/phone）
 * @param {string} value - 字段值
 * @returns {Promise}
 */
export function checkUniqueApi(field, value) {
  return request({
    url: '/users/check-unique/',
    method: 'get',
    params: { field, value }
  })
}