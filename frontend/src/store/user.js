import { defineStore } from 'pinia'
import { ref, computed } from 'vue'

export const useUserStore = defineStore('user', () => {
  // ========== state ==========
  const token = ref(localStorage.getItem('token') || '')
  const refreshToken = ref(localStorage.getItem('refreshToken') || '')
  const userInfo = ref(JSON.parse(localStorage.getItem('userInfo') || 'null'))
  const isLoggedIn = ref(!!token.value)

  // ========== getters ==========
  const isAdmin = computed(() => userInfo.value?.role === 'admin')
  const isLandlord = computed(() => userInfo.value?.role === 'landlord')

  // ========== actions ==========
  function login(data) {
    token.value = data.token
    refreshToken.value = data.refreshToken
    userInfo.value = data.userInfo
    isLoggedIn.value = true
    localStorage.setItem('refreshToken', data.refreshToken)
    localStorage.setItem('token', data.token)
    localStorage.setItem('userInfo', JSON.stringify(data.userInfo))
  }

  function logout() {
    token.value = ''
    refreshToken.value = ''
    userInfo.value = null
    isLoggedIn.value = false
    localStorage.removeItem('refreshToken')
    localStorage.removeItem('token')
    localStorage.removeItem('userInfo')
  }

  async function getUserInfo() {
    // 调用接口获取用户信息（后续接入 API 时实现）
    // const res = await userInfoApi()
    // userInfo.value = res.data
    // localStorage.setItem('userInfo', JSON.stringify(res.data))
  }

  return {
    // state
    refreshToken,
    token,
    userInfo,
    isLoggedIn,
    // getters
    isAdmin,
    isLandlord,
    // actions
    login,
    logout,
    getUserInfo
  }
})
