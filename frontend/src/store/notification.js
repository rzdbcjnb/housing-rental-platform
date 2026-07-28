import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { getMessagesApi, getUnreadCountApi, markMessageReadApi, markAllReadApi, deleteMessageApi } from '@/api/notification'
import { websocketUrl } from '@/utils/websocket'

export const useNotificationStore = defineStore('notification', () => {
  // ========== state ==========
  const messages = ref([])
  const unreadCount = ref(0)
  const loading = ref(false)
  const ws = ref(null)
  const isConnected = ref(false)

  // ========== getters ==========
  const hasUnread = computed(() => unreadCount.value > 0)

  // ========== actions ==========
  
  /**
   * 连接WebSocket
   */
  function connect() {
    const token = localStorage.getItem('token')
    if (!token) return

    const wsUrl = websocketUrl('/ws/notifications/', token)

    ws.value = new WebSocket(wsUrl)

    ws.value.onopen = () => {
      console.log('WebSocket连接成功')
      isConnected.value = true
      // 获取未读消息数量
      getUnreadCount()
    }

    ws.value.onmessage = (event) => {
      try {
        const data = JSON.parse(event.data)
        if (data.type === 'unread_count') {
          unreadCount.value = data.count
        } else if (data.type === 'new_message') {
          // 新消息到达，更新未读数量
          unreadCount.value++
          // 可以添加桌面通知或声音提醒
        }
      } catch (e) {
        console.error('解析WebSocket消息失败:', e)
      }
    }

    ws.value.onclose = () => {
      console.log('WebSocket连接关闭')
      isConnected.value = false
      // 尝试重新连接
      setTimeout(() => {
        if (localStorage.getItem('token')) {
          connect()
        }
      }, 5000)
    }

    ws.value.onerror = (error) => {
      console.error('WebSocket错误:', error)
      isConnected.value = false
    }
  }

  /**
   * 断开WebSocket连接
   */
  function disconnect() {
    if (ws.value) {
      ws.value.close()
      ws.value = null
      isConnected.value = false
    }
  }

  /**
   * 获取未读消息数量
   */
  async function getUnreadCount() {
    try {
      const res = await getUnreadCountApi()
      unreadCount.value = res.count
    } catch (e) {
      console.error('获取未读消息数量失败:', e)
    }
  }

  /**
   * 获取消息列表
   */
  async function fetchMessages(params = {}) {
    loading.value = true
    try {
      const res = await getMessagesApi(params)
      messages.value = res.results || []
      return res
    } catch (e) {
      console.error('获取消息列表失败:', e)
      return { results: [], count: 0 }
    } finally {
      loading.value = false
    }
  }

  /**
   * 标记消息为已读
   */
  async function markMessageRead(messageId) {
    try {
      await markMessageReadApi(messageId)
      // 更新本地状态
      const message = messages.value.find(m => m.id === messageId)
      if (message && !message.is_read) {
        message.is_read = true
        unreadCount.value = Math.max(0, unreadCount.value - 1)
      }
      // 通过WebSocket通知服务器
      if (ws.value && isConnected.value) {
        ws.value.send(JSON.stringify({
          action: 'mark_read',
          message_id: messageId
        }))
      }
    } catch (e) {
      console.error('标记消息已读失败:', e)
    }
  }

  /**
   * 标记所有消息为已读
   */
  async function markAllRead() {
    try {
      await markAllReadApi()
      // 更新本地状态
      messages.value.forEach(m => {
        m.is_read = true
      })
      unreadCount.value = 0
      // 通过WebSocket通知服务器
      if (ws.value && isConnected.value) {
        ws.value.send(JSON.stringify({
          action: 'mark_all_read'
        }))
      }
    } catch (e) {
      console.error('标记所有消息已读失败:', e)
    }
  }

  /**
   * 删除消息
   */
  async function deleteMessage(messageId) {
    try {
      await deleteMessageApi(messageId)
      // 更新本地状态
      const index = messages.value.findIndex(m => m.id === messageId)
      if (index !== -1) {
        const message = messages.value[index]
        if (!message.is_read) {
          unreadCount.value = Math.max(0, unreadCount.value - 1)
        }
        messages.value.splice(index, 1)
      }
    } catch (e) {
      console.error('删除消息失败:', e)
    }
  }

  return {
    // state
    messages,
    unreadCount,
    loading,
    isConnected,
    // getters
    hasUnread,
    // actions
    connect,
    disconnect,
    getUnreadCount,
    fetchMessages,
    markMessageRead,
    markAllRead,
    deleteMessage
  }
})
