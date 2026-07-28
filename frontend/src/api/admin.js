import request from './index'

/**
 * 获取数据看板统计
 * @returns {Promise}
 */
export function getDashboardStatsApi() {
  return request({
    url: '/admin/dashboard/',
    method: 'get'
  })
}

/**
 * 获取用户列表
 * @param {Object} params - 查询参数
 * @returns {Promise}
 */
export function getUsersApi(params = {}) {
  const searchParams = new URLSearchParams()
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') {
      searchParams.append(key, value)
    }
  })
  const queryString = searchParams.toString()
  const url = queryString ? `/admin/users/?${queryString}` : '/admin/users/'
  return request({
    url,
    method: 'get'
  })
}

/**
 * 创建用户
 * @param {Object} data - 用户数据
 * @returns {Promise}
 */
export function createUserApi(data) {
  return request({
    url: '/admin/users/',
    method: 'post',
    data
  })
}

/**
 * 更新用户
 * @param {number} id - 用户ID
 * @param {Object} data - 用户数据
 * @returns {Promise}
 */
export function updateUserApi(id, data) {
  return request({
    url: `/admin/users/${id}/`,
    method: 'put',
    data
  })
}

/**
 * 删除用户
 * @param {number} id - 用户ID
 * @returns {Promise}
 */
export function deleteUserApi(id) {
  return request({
    url: `/admin/users/${id}/`,
    method: 'delete'
  })
}

/**
 * 更新用户状态（启用/禁用）
 * @param {number} id - 用户ID
 * @param {boolean} isActive - 是否启用
 * @returns {Promise}
 */
export function updateUserStatusApi(id, isActive) {
  return request({
    url: `/admin/users/${id}/status/`,
    method: 'put',
    data: { is_active: isActive }
  })
}

/**
 * 获取房源审核列表
 * @param {Object} params - 查询参数
 * @returns {Promise}
 */
export function getHouseAuditListApi(params = {}) {
  const searchParams = new URLSearchParams()
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') {
      searchParams.append(key, value)
    }
  })
  const queryString = searchParams.toString()
  const url = queryString ? `/admin/houses/?${queryString}` : '/admin/houses/'
  return request({
    url,
    method: 'get'
  })
}

/**
 * 审核房源
 * @param {number} id - 房源ID
 * @param {string} action - 审核操作（approve/reject/offline）
 * @returns {Promise}
 */
export function auditHouseApi(id, action) {
  return request({
    url: `/admin/houses/${id}/audit/`,
    method: 'put',
    data: { action }
  })
}

/**
 * 管理员获取房源详情（支持所有状态）
 * @param {number} id - 房源ID
 * @returns {Promise}
 */
export function getAdminHouseDetailApi(id) {
  return request({
    url: `/admin/houses/${id}/`,
    method: 'get'
  })
}
