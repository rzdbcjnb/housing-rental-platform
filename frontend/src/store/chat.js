import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { getChatRoomsApi, getChatMessagesApi, markMessagesReadApi, getUnreadChatCountApi } from '@/api/chat'
import { useUserStore } from '@/store/user'
import { websocketUrl } from '@/utils/websocket'
import { ElMessage } from 'element-plus'

export const useChatStore = defineStore('chat', () => {
  const userStore = useUserStore()
  const rooms = ref([])
  const currentRoom = ref(null)
  const messages = ref([])
  const unreadCount = ref(0)
  const loading = ref(false)
  const ws = ref(null)
  const isConnected = ref(false)
  const isOtherTyping = ref(false)
  const otherUserOnline = ref(false)
  const hasUnread = computed(() => unreadCount.value > 0)
  let reconnectTimer = null
  let isConnecting = false

  async function fetchRooms(params = {}) {
    loading.value = true
    try {
      const res = await getChatRoomsApi(params)
      rooms.value = res.results || []
      return res
    } catch (e) {
      console.error('获取聊天房间列表失败:', e)
      return { results: [], count: 0 }
    } finally {
      loading.value = false
    }
  }

  async function fetchMessages(roomId, params = {}) {
    try {
      const res = await getChatMessagesApi(roomId, params)
      messages.value = (res.results || []).reverse()
      return res
    } catch (e) {
      console.error('获取消息列表失败:', e)
      return { results: [], count: 0 }
    }
  }

  async function markMessagesRead(roomId) {
    try {
      const unreadIds = messages.value
        .filter(m => !m.is_read && String(m.sender_user_id) !== String(userStore.userInfo?.id))
        .map(m => m.id)
      if (unreadIds.length > 0) {
        await markMessagesReadApi(roomId, unreadIds)
        unreadIds.forEach(id => {
          const msg = messages.value.find(m => String(m.id) === String(id))
          if (msg) msg.is_read = true
        })
        if (ws.value && ws.value.readyState === WebSocket.OPEN) {
          ws.value.send(JSON.stringify({ action: 'mark_read', message_ids: unreadIds }))
        }
      }
    } catch (e) {
      console.error('标记消息已读失败:', e)
    }
  }

  function connectToRoom(roomId) {
    if (isConnecting) return
    const token = localStorage.getItem('token')
    if (!token) return

    if (reconnectTimer) { clearTimeout(reconnectTimer); reconnectTimer = null }

    if (ws.value) {
      ws.value.onclose = null
      ws.value.onerror = null
      try { ws.value.close() } catch (e) {}
      ws.value = null
    }
    isConnected.value = false
    isConnecting = true

    const wsUrl = websocketUrl(`/ws/chat/${roomId}/`, token)
    console.log('连接WebSocket:', wsUrl)

    try { ws.value = new WebSocket(wsUrl) } catch (e) { console.error('创建WebSocket失败:', e); isConnecting = false; return }

    ws.value.onopen = () => {
      console.log('聊天WebSocket连接成功')
      isConnected.value = true
      isConnecting = false
    }

    ws.value.onmessage = (event) => {
      try {
        handleWebSocketMessage(JSON.parse(event.data))
      } catch (e) {
        console.error('解析消息失败:', e)
      }
    }

    ws.value.onclose = (event) => {
      console.log('聊天WebSocket关闭, code:', event.code)
      isConnected.value = false
      isConnecting = false
      if (currentRoom.value && event.code !== 1000 && event.code !== 1005) {
        reconnectTimer = setTimeout(() => {
          if (currentRoom.value) {
            console.log('尝试重连...')
            connectToRoom(currentRoom.value)
          }
        }, 5000)
      }
    }

    ws.value.onerror = (error) => {
      console.error('聊天WebSocket错误:', error)
      isConnected.value = false
    }
  }

  function handleWebSocketMessage(data) {
    switch (data.type) {
      case 'room_info': break
      case 'new_message': {
        const msg = data.message
        if (msg.message_type === 'system') {
          if (!messages.value.find(m => String(m.id) === String(msg.id))) messages.value.push(msg)
          break
        }
        const idx = messages.value.findIndex(m =>
          String(m.id).startsWith('temp_') &&
          m.content === msg.content &&
          String(m.sender_user_id) === String(msg.sender_user_id)
        )
        if (idx !== -1) messages.value.splice(idx, 1, msg)
        else if (!messages.value.find(m => String(m.id) === String(msg.id))) messages.value.push(msg)
        break
      }
      case 'messages_read':
        (data.message_ids || []).forEach(id => {
          const m = messages.value.find(x => String(x.id) === String(id))
          if (m) m.is_read = true
        })
        break
      case 'typing_status': isOtherTyping.value = data.is_typing; break
      case 'online_status': otherUserOnline.value = data.is_online; break
    }
  }

  function sendMessage(content, messageType = 'text') {
    if (!ws.value || ws.value.readyState !== WebSocket.OPEN) {
      ElMessage.error('连接已断开，请刷新页面重试')
      return false
    }
    const tempId = 'temp_' + Date.now()
    messages.value.push({
      id: tempId, sender_user_id: userStore.userInfo?.id, sender_name: userStore.userInfo?.username,
      message_type: messageType, content, is_read: false, created_at: new Date().toISOString(), isSending: true
    })
    try {
      ws.value.send(JSON.stringify({ action: 'send_message', message_type: messageType, content }))
    } catch (e) {
      ElMessage.error('发送失败')
      const i = messages.value.findIndex(m => m.id === tempId)
      if (i !== -1) messages.value.splice(i, 1)
      return false
    }
    return true
  }

  function sendTypingStatus(isTyping) {
    if (!ws.value || ws.value.readyState !== WebSocket.OPEN) return
    ws.value.send(JSON.stringify({ action: 'typing', is_typing: isTyping }))
  }

  function disconnect() {
    if (reconnectTimer) { clearTimeout(reconnectTimer); reconnectTimer = null }
    isConnecting = false
    currentRoom.value = null
    if (ws.value) {
      ws.value.onclose = null
      ws.value.onerror = null
      try { ws.value.close() } catch (e) {}
      ws.value = null
      isConnected.value = false
      messages.value = []
      isOtherTyping.value = false
      otherUserOnline.value = false
    }
  }

  function setCurrentRoom(roomId) { currentRoom.value = roomId }

  return {
    rooms, currentRoom, messages, unreadCount, loading,
    isConnected, isOtherTyping, otherUserOnline, hasUnread,
    fetchRooms, fetchMessages, markMessagesRead,
    connectToRoom, sendMessage, sendTypingStatus, disconnect, setCurrentRoom
  }
})