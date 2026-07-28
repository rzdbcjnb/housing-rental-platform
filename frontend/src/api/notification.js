import request from './index'

/**
 * 获取消息列表
 * @param {Object} params - 查询参数
 * @returns {Promise}
 */
export function getMessagesApi(params = {}) {
  const searchParams = new URLSearchParams()
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') {
      searchParams.append(key, value)
    }
  })
  const queryString = searchParams.toString()
  const url = queryString ? `/notifications/messages/?${queryString}` : '/notifications/messages/'
  return request({
    url,
    method: 'get'
  })
}

/**
 * 获取未读消息数量
 * @returns {Promise}
 */
export function getUnreadCountApi() {
  return request({
    url: '/notifications/unread-count/',
    method: 'get'
  })
}

/**
 * 标记消息为已读
 * @param {number} messageId - 消息ID
 * @returns {Promise}
 */
export function markMessageReadApi(messageId) {
  return request({
    url: `/notifications/messages/${messageId}/read/`,
    method: 'put'
  })
}

/**
 * 标记所有消息为已读
 * @returns {Promise}
 */
export function markAllReadApi() {
  return request({
    url: '/notifications/messages/read-all/',
    method: 'put'
  })
}

/**
 * 删除消息
 * @param {number} messageId - 消息ID
 * @returns {Promise}
 */
export function deleteMessageApi(messageId) {
  return request({
    url: `/notifications/messages/${messageId}/`,
    method: 'delete'
  })
}

/**
 * 获取系统公告列表
 * @param {Object} params - 查询参数
 * @returns {Promise}
 */
export function getAnnouncementsApi(params = {}) {
  const searchParams = new URLSearchParams()
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') {
      searchParams.append(key, value)
    }
  })
  const queryString = searchParams.toString()
  const url = queryString ? `/notifications/announcements/?${queryString}` : '/notifications/announcements/'
  return request({
    url,
    method: 'get'
  })
}

/**
 * 创建系统公告
 * @param {Object} data - 公告数据
 * @returns {Promise}
 */
export function createAnnouncementApi(data) {
  return request({
    url: '/notifications/announcements/',
    method: 'post',
    data
  })
}

/**
 * 更新系统公告
 * @param {number} id - 公告ID
 * @param {Object} data - 公告数据
 * @returns {Promise}
 */
export function updateAnnouncementApi(id, data) {
  return request({
    url: `/notifications/announcements/${id}/`,
    method: 'put',
    data
  })
}

/**
 * 删除系统公告
 * @param {number} id - 公告ID
 * @returns {Promise}
 */
export function deleteAnnouncementApi(id) {
  return request({
    url: `/notifications/announcements/${id}/`,
    method: 'delete'
  })
}

/**
 * 批量删除公告
 * @param {Array} ids - 公告ID数组
 * @returns {Promise}
 */
export function batchDeleteAnnouncementsApi(ids) {
  return request({
    url: '/notifications/announcements/batch-delete/',
    method: 'post',
    data: { ids }
  })
}
