import request from './index'

/**
 * 获取聊天房间列表
 * @param {Object} params - 查询参数
 * @returns {Promise}
 */
export function getChatRoomsApi(params = {}) {
  const searchParams = new URLSearchParams()
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') {
      searchParams.append(key, value)
    }
  })
  const queryString = searchParams.toString()
  const url = queryString ? `/chat/rooms/?${queryString}` : '/chat/rooms/'
  return request({
    url,
    method: 'get'
  })
}

/**
 * 创建聊天房间
 * @param {Object} data - 房间数据
 * @returns {Promise}
 */
export function createChatRoomApi(data) {
  return request({
    url: '/chat/rooms/create/',
    method: 'post',
    data
  })
}

/**
 * 获取聊天房间详情
 * @param {number} id - 房间ID
 * @returns {Promise}
 */
export function getChatRoomDetailApi(id) {
  return request({
    url: `/chat/rooms/${id}/`,
    method: 'get'
  })
}

/**
 * 获取或创建与指定用户的聊天房间
 * @param {number} userId - 用户ID
 * @returns {Promise}
 */
export function getOrCreateRoomWithUserApi(userId) {
  return request({
    url: `/chat/rooms/with-user/${userId}/`,
    method: 'get'
  })
}

/**
 * 获取聊天消息列表
 * @param {number} roomId - 房间ID
 * @param {Object} params - 查询参数
 * @returns {Promise}
 */
export function getChatMessagesApi(roomId, params = {}) {
  const searchParams = new URLSearchParams()
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') {
      searchParams.append(key, value)
    }
  })
  const queryString = searchParams.toString()
  const url = queryString ? `/chat/rooms/${roomId}/messages/?${queryString}` : `/chat/rooms/${roomId}/messages/`
  return request({
    url,
    method: 'get'
  })
}

/**
 * 标记消息为已读
 * @param {number} roomId - 房间ID
 * @returns {Promise}
 */
export function markMessagesReadApi(roomId) {
  return request({
    url: `/chat/rooms/${roomId}/read/`,
    method: 'post'
  })
}

/**
 * 获取未读聊天消息数量
 * @returns {Promise}
 */
export function getUnreadChatCountApi() {
  return request({
    url: '/chat/unread-count/',
    method: 'get'
  })
}

/**
 * 发送房源分享消息
 * @param {number} roomId - 房间ID
 * @param {number} houseId - 房源ID
 * @returns {Promise}
 */
export function sendHouseShareMessageApi(roomId, houseId) {
  return request({
    url: `/chat/rooms/${roomId}/share-house/`,
    method: 'post',
    data: { house_id: houseId }
  })
}