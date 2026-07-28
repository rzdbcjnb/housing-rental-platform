import request from './index'

/**
 * 获取房源列表（支持筛选参数）
 * @param {Object} params - 筛选参数
 * @param {string} [params.keyword] - 关键词
 * @param {string} [params.city] - 城市
 * @param {string} [params.district] - 区
 * @param {string} [params.street] - 街道
 * @param {number} [params.price_min] - 最低价格
 * @param {number} [params.price_max] - 最高价格
 * @param {number} [params.rooms] - 户型（室数）
 * @param {number} [params.area_min] - 最小面积
 * @param {number} [params.area_max] - 最大面积
 * @param {number} [params.page] - 页码
 * @param {number} [params.page_size] - 每页数量
 * @returns {Promise}
 */
export function getHouseListApi(params = {}) {
  const searchParams = new URLSearchParams()
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') {
      searchParams.append(key, value)
    }
  })
  const queryString = searchParams.toString()
  const url = queryString ? `/houses/?${queryString}` : '/houses/'
  return request({
    url,
    method: 'get'
  })
}

/**
 * 获取房源详情
 * @param {number|string} id - 房源ID
 * @returns {Promise}
 */
export function getHouseDetailApi(id) {
  return request({
    url: `/houses/${id}/`,
    method: 'get'
  })
}

/**
 * 创建房源
 * @param {Object} data - 房源数据
 * @returns {Promise}
 */
export function createHouseApi(data) {
  return request({
    url: '/houses/',
    method: 'post',
    data
  })
}

/**
 * 更新房源
 * @param {number|string} id - 房源ID
 * @param {Object} data - 房源数据
 * @returns {Promise}
 */
export function updateHouseApi(id, data) {
  return request({
    url: `/houses/${id}/`,
    method: 'put',
    data
  })
}

/**
 * 获取地区列表
 * @param {Object} [params] - 筛选参数
 * @param {number} [params.parent] - 父级地区ID
 * @returns {Promise}
 */
export function getAreasApi(params = {}) {
  const searchParams = new URLSearchParams()
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') {
      searchParams.append(key, value)
    }
  })
  const queryString = searchParams.toString()
  const url = queryString ? `/areas/?${queryString}` : '/areas/'
  return request({
    url,
    method: 'get'
  })
}

/**
 * 获取相似房源推荐
 * @param {number|string} id - 房源ID
 * @returns {Promise}
 */
export function getHouseRecommendApi(id) {
  return request({
    url: `/houses/${id}/recommend/`,
    method: 'get'
  })
}

/**
 * 获取当前用户发布的房源
 * @param {Object} params - 查询参数
 * @returns {Promise}
 */
export function getMyHousesApi(params = {}) {
  const searchParams = new URLSearchParams()
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') {
      searchParams.append(key, value)
    }
  })
  const queryString = searchParams.toString()
  const url = queryString ? `/houses/my/?${queryString}` : '/houses/my/'
  return request({
    url,
    method: 'get'
  })
}

/**
 * 添加收藏
 * @param {number} houseId - 房源ID
 * @returns {Promise}
 */
export function addFavoriteApi(houseId) {
  return request({
    url: '/houses/favorites/add/',
    method: 'post',
    data: { house: houseId }
  })
}

/**
 * 取消收藏
 * @param {number} favoriteId - 收藏ID
 * @returns {Promise}
 */
export function removeFavoriteApi(favoriteId) {
  return request({
    url: `/houses/favorites/${favoriteId}/remove/`,
    method: 'delete'
  })
}

/**
 * 获取用户收藏列表
 * @param {Object} params - 查询参数
 * @returns {Promise}
 */
export function getFavoritesApi(params = {}) {
  const searchParams = new URLSearchParams()
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') {
      searchParams.append(key, value)
    }
  })
  const queryString = searchParams.toString()
  const url = queryString ? `/houses/favorites/?${queryString}` : '/houses/favorites/'
  return request({
    url,
    method: 'get'
  })
}

/**
 * 检查房源是否已收藏
 * @param {number} houseId - 房源ID
 * @returns {Promise}
 */
export function checkFavoriteApi(houseId) {
  return request({
    url: `/houses/${houseId}/is_favorited/`,
    method: 'get'
  })
}

/**
 * 获取用户个性化推荐
 * @param {number} limit - 推荐数量
 * @returns {Promise}
 */
export function getUserRecommendApi(limit = 10) {
  return request({
    url: `/houses/user-recommend/?limit=${limit}`,
    method: 'get'
  })
}

/**
 * 添加浏览历史
 * @param {number} houseId - 房源ID
 * @returns {Promise}
 */
export function addBrowseHistoryApi(houseId) {
  return request({
    url: '/houses/browse-history/add/',
    method: 'post',
    data: { house: houseId }
  })
}

/**
 * 获取浏览历史列表
 * @param {Object} params - 查询参数
 * @returns {Promise}
 */
export function getBrowseHistoryApi(params = {}) {
  const searchParams = new URLSearchParams()
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') {
      searchParams.append(key, value)
    }
  })
  const queryString = searchParams.toString()
  const url = queryString ? `/houses/browse-history/?${queryString}` : '/houses/browse-history/'
  return request({
    url,
    method: 'get'
  })
}

/**
 * 删除单条浏览历史
 * @param {number} id - 浏览历史ID
 * @returns {Promise}
 */
export function deleteBrowseHistoryApi(id) {
  return request({
    url: `/houses/browse-history/${id}/`,
    method: 'delete'
  })
}

/**
 * 清空所有浏览历史
 * @returns {Promise}
 */
export function clearBrowseHistoryApi() {
  return request({
    url: '/houses/browse-history/clear/',
    method: 'delete'
  })
}

/**
 * 检查发布限制
 * @returns {Promise}
 */
export function checkPublishLimitApi() {
  return request({
    url: '/houses/publish-limit/',
    method: 'get'
  })
}

/**
 * 模拟支付
 * @param {number} amount - 支付金额
 * @returns {Promise}
 */
export function simulatePaymentApi(amount) {
  return request({
    url: '/houses/simulate-payment/',
    method: 'post',
    data: { amount }
  })
}

/**
 * 记录房源点击
 * @param {number} houseId - 房源ID
 * @returns {Promise}
 */
export function recordHouseClickApi(houseId) {
  return request({
    url: `/houses/${houseId}/click/`,
    method: 'post'
  })
}

/**
 * 充值推荐点
 * @param {number} points - 购买点数
 * @returns {Promise}
 */
export function rechargePointsApi(points) {
  return request({
    url: '/houses/recharge-points/',
    method: 'post',
    data: { points }
  })
}

/**
 * 投放推荐点
 * @param {number} houseId - 房源ID
 * @param {number} points - 投放点数
 * @returns {Promise}
 */
export function investPointsApi(houseId, points) {
  return request({
    url: '/houses/invest-points/',
    method: 'post',
    data: { house_id: houseId, points }
  })
}

/**
 * 获取账户余额
 * @returns {Promise}
 */
export function getAccountBalanceApi() {
  return request({
    url: '/houses/account-balance/',
    method: 'get'
  })
}
/**
 * 获取推荐状态
 * @returns {Promise}
 */
export function getRecommendStatusApi() {
  return request({
    url: '/houses/recommend-status/',
    method: 'get'
  })
}