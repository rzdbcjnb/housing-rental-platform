<template>
  <div class="page-chat-room">
    <!-- 聊天头部 - 固定在顶部 -->
    <div class="chat-header">
      <div class="header-left">
        <el-button :icon="ArrowLeft" circle @click="goBack" />
        <div class="user-info">
          <el-avatar :size="36">
            {{ otherUser?.username?.charAt(0) || '?' }}
          </el-avatar>
          <div class="user-detail">
            <span class="username">{{ otherUser?.username || '未知用户' }}</span>
            <span class="status" :class="{ online: chatStore.otherUserOnline }">
              {{ chatStore.otherUserOnline ? '在线' : '离线' }}
            </span>
          </div>
        </div>
      </div>
      <div class="header-right">
        <el-button :icon="MoreFilled" circle />
      </div>
    </div>

    <!-- 消息列表 - 可滚动区域 -->
    <div ref="messageListRef" class="message-list" @scroll="handleScroll">
      <div v-if="loading" class="loading-wrapper">
        <el-skeleton :rows="3" animated />
      </div>
      
      <template v-else>
        <div 
          v-for="msg in chatStore.messages" 
          :key="msg.id" 
          class="message-item"
          :class="{ 
            'self': msg.sender_user_id === userId,
            'system': msg.message_type === 'system'
          }"
        >
          <!-- 系统消息 -->
          <template v-if="msg.message_type === 'system'">
            <div class="system-message">
              <div class="system-content">
                <el-icon><InfoFilled /></el-icon>
                <span>{{ msg.content }}</span>
              </div>
              <div class="system-time">{{ formatTime(msg.created_at) }}</div>
            </div>
          </template>
          
          <!-- 普通消息 -->
          <template v-else>
            <div class="message-avatar">
              <el-avatar :size="36">
                {{ msg.sender_name?.charAt(0) || '?' }}
              </el-avatar>
            </div>
            <div class="message-content">
              <div class="message-bubble">
                <div v-if="msg.message_type === 'text'" class="message-text">
                  {{ msg.content }}
                </div>
                <div v-else-if="msg.message_type === 'image'" class="message-image">
                  <el-image 
                    :src="msg.content" 
                    :preview-src-list="[msg.content]"
                    fit="cover"
                  />
                </div>
                <div v-else-if="msg.message_type === 'house_share'" class="house-card" @click="goToHouse(getHouseData(msg.content).house_id)">
                  <div class="house-card-cover">
                    <img :src="getHouseData(msg.content).image || 'https://via.placeholder.com/300x200?text=暂无图片'" alt="" />
                  </div>
                  <div class="house-card-body">
                    <div class="house-card-title">{{ getHouseData(msg.content).title }}</div>
                    <div class="house-card-info">
                      <span class="house-card-price">{{ getHouseData(msg.content).price }}元/月</span>
                      <span>{{ getHouseData(msg.content).rooms }}</span>
                      <span>{{ getHouseData(msg.content).area }}m²</span>
                    </div>
                    <div class="house-card-hint">点击查看详情</div>
                  </div>
                </div>
              </div>
              <div class="message-meta">
                <span class="message-time">{{ formatTime(msg.created_at) }}</span>
                <span v-if="msg.sender_user_id === userId" class="message-status">
                  <el-icon v-if="msg.is_read" class="read-icon"><Check /></el-icon>
                  <el-icon v-else class="sent-icon"><Check /></el-icon>
                </span>
              </div>
            </div>
          </template>
        </div>
        
        <div v-if="chatStore.isOtherTyping" class="typing-indicator">
          <span>{{ otherUser?.username }} 正在输入...</span>
        </div>
      </template>
    </div>

    <!-- 输入区域 - 固定在底部 -->
    <div class="chat-input">
      <div class="input-toolbar">
        <el-upload
          class="image-upload"
          action="/api/upload/image/"
          name="image"
          :headers="uploadHeaders"
          :show-file-list="false"
          :on-success="handleImageUpload"
        >
          <el-button :icon="PictureFilled" circle />
        </el-upload>
      </div>
      <div class="input-main">
        <el-input
          v-model="inputMessage"
          type="textarea"
          :rows="1"
          :autosize="{ minRows: 1, maxRows: 4 }"
          placeholder="输入消息..."
          @keydown.enter.exact.prevent="sendMessage"
          @input="handleTyping"
        />
        <el-button 
          type="primary" 
          :disabled="!inputMessage.trim()"
          @click="sendMessage"
        >
          发送
        </el-button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, nextTick, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowLeft, MoreFilled, PictureFilled, Check, InfoFilled } from '@element-plus/icons-vue'
import { useChatStore } from '@/store/chat'
import { useUserStore } from '@/store/user'
import { getChatRoomDetailApi } from '@/api/chat'

const route = useRoute()
const router = useRouter()
const chatStore = useChatStore()
const userStore = useUserStore()

const loading = ref(false)
const inputMessage = ref('')
const messageListRef = ref(null)
const otherUser = ref(null)
const typingTimer = ref(null)

const userId = computed(() => userStore.userInfo?.id)

const uploadHeaders = computed(() => ({
  Authorization: `Bearer ${localStorage.getItem('token')}`
}))

// 格式化时间
function formatTime(timeStr) {
  if (!timeStr) return ''
  const date = new Date(timeStr)
  const now = new Date()
  const diff = now - date
  
  if (diff < 60000) return '刚刚'
  if (diff < 3600000) return `${Math.floor(diff / 60000)}分钟前`
  if (diff < 86400000) return `${Math.floor(diff / 3600000)}小时前`
  if (diff < 604800000) return `${Math.floor(diff / 86400000)}天前`
  return date.toLocaleDateString('zh-CN')
}

// 解析房源分享数据
function getHouseData(content) {
  try {
    if (typeof content === 'string') {
      return JSON.parse(content)
    }
    return content
  } catch {
    return { house_id: 0, title: '未知房源', price: '0', image: '', rooms: '', area: 0 }
  }
}

// 跳转到房源详情
function goToHouse(houseId) {
  if (houseId) {
    router.push(`/houses/${houseId}`)
  }
}

// 发送消息
function sendMessage() {
  if (!inputMessage.value.trim()) return
  
  const success = chatStore.sendMessage(inputMessage.value, 'text')
  if (success) {
    inputMessage.value = ''
    scrollToBottom()
  }
}

// 处理图片上传
async function handleImageUpload(response) {
  if (response.url) {
    // 等待WebSocket连接
    let retryCount = 0
    const maxRetries = 10
    
    while (!chatStore.isConnected && retryCount < maxRetries) {
      await new Promise(resolve => setTimeout(resolve, 500))
      retryCount++
    }
    
    if (chatStore.isConnected) {
      chatStore.sendMessage(response.url, 'image')
      scrollToBottom()
    } else {
      ElMessage.error('WebSocket未连接，无法发送图片')
    }
  }
}

// 处理输入状态
function handleTyping() {
  chatStore.sendTypingStatus(true)
  
  // 清除之前的定时器
  if (typingTimer.value) {
    clearTimeout(typingTimer.value)
  }
  
  // 2秒后停止输入状态
  typingTimer.value = setTimeout(() => {
    chatStore.sendTypingStatus(false)
  }, 2000)
}

// 滚动到底部
function scrollToBottom() {
  nextTick(() => {
    if (messageListRef.value) {
      messageListRef.value.scrollTop = messageListRef.value.scrollHeight
    }
  })
  // 延迟再次滚动，确保图片加载后也能滚动到底部
  setTimeout(() => {
    if (messageListRef.value) {
      messageListRef.value.scrollTop = messageListRef.value.scrollHeight
    }
  }, 300)
}

// 处理滚动加载更多
function handleScroll() {
  if (messageListRef.value) {
    const { scrollTop } = messageListRef.value
    if (scrollTop === 0) {
      // 滚动到顶部，加载更多消息
      loadMoreMessages()
    }
  }
}

// 加载更多消息
async function loadMoreMessages() {
  // TODO: 实现加载更多历史消息
}

// 返回上一页
function goBack() {
  router.push('/chat')
}

// 加载房间信息
async function loadRoomInfo() {
  const roomId = route.params.id
  if (!roomId) {
    ElMessage.error('房间ID不存在')
    router.push('/chat')
    return
  }

  loading.value = true
  try {
    // 获取房间详情
    const room = await getChatRoomDetailApi(roomId)
    otherUser.value = room.other_user
    
    // 获取历史消息
    await chatStore.fetchMessages(roomId)
    
    // 连接WebSocket
    chatStore.setCurrentRoom(roomId)
    chatStore.connectToRoom(roomId)
    
    // 标记消息为已读
    await chatStore.markMessagesRead(roomId)
    
    // 滚动到底部
    scrollToBottom()
  } catch (e) {
    ElMessage.error('获取聊天信息失败')
    console.error(e)
  } finally {
    loading.value = false
  }
}

// 监听新消息
watch(() => chatStore.messages.length, () => {
  scrollToBottom()
})

onMounted(() => {
  loadRoomInfo()
})

onUnmounted(() => {
  chatStore.disconnect()
  if (typingTimer.value) {
    clearTimeout(typingTimer.value)
  }
})
</script>

<style scoped>
.page-chat-room {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  display: flex;
  flex-direction: column;
  background-color: #f5f5f5;
  z-index: 1000;
}

.chat-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  background-color: #fff;
  border-bottom: 1px solid #e4e7ed;
  flex-shrink: 0;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.user-info {
  display: flex;
  align-items: center;
  gap: 8px;
}

.user-detail {
  display: flex;
  flex-direction: column;
}

.username {
  font-size: 16px;
  font-weight: 500;
}

.status {
  font-size: 12px;
  color: #909399;
}

.status.online {
  color: #67c23a;
}

.message-list {
  flex: 1;
  overflow-y: auto;
  padding: 16px;
  min-height: 0;
}

.loading-wrapper {
  padding: 20px;
}

.message-item {
  display: flex;
  margin-bottom: 16px;
  gap: 8px;
}

.message-item.self {
  flex-direction: row-reverse;
}

.message-item.system {
  justify-content: center;
}

/* 系统消息样式 */
.system-message {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  width: 100%;
}

.system-content {
  display: flex;
  align-items: center;
  gap: 6px;
  background-color: #f0f0f0;
  color: #909399;
  font-size: 12px;
  padding: 6px 16px;
  border-radius: 12px;
  max-width: 80%;
}

.system-content .el-icon {
  font-size: 14px;
}

.system-time {
  font-size: 11px;
  color: #c0c4cc;
}

.message-avatar {
  flex-shrink: 0;
}

.message-content {
  max-width: 70%;
}

.message-bubble {
  padding: 10px 14px;
  border-radius: 12px;
  background-color: #fff;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.1);
}

.message-item.self .message-bubble {
  background-color: #ecf5ff;
}

.message-text {
  word-break: break-word;
  line-height: 1.5;
}

.message-image {
  max-width: 200px;
}

.message-meta {
  display: flex;
  align-items: center;
  gap: 4px;
  margin-top: 4px;
  font-size: 12px;
  color: #909399;
}

.message-item.self .message-meta {
  justify-content: flex-end;
}

.message-status {
  display: flex;
  align-items: center;
}

.read-icon {
  color: #409eff;
}

.typing-indicator {
  padding: 8px 16px;
  font-size: 12px;
  color: #909399;
}

.chat-input {
  background-color: #fff;
  border-top: 1px solid #e4e7ed;
  padding: 12px 16px;
  flex-shrink: 0;
}

.input-toolbar {
  margin-bottom: 8px;
}

.input-main {
  display: flex;
  gap: 8px;
}

.input-main .el-input {
  flex: 1;
}

.house-card {
  cursor: pointer;
  border-radius: 12px;
  overflow: hidden;
  background-color: #fff;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.1);
  max-width: 260px;
  transition: box-shadow 0.2s;
}

.house-card:hover {
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
}

.house-card-cover {
  height: 120px;
  overflow: hidden;
}

.house-card-cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.house-card-body {
  padding: 10px;
}

.house-card-title {
  font-size: 14px;
  font-weight: 500;
  color: #333;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  margin-bottom: 6px;
}

.house-card-info {
  display: flex;
  gap: 8px;
  font-size: 12px;
  color: #909399;
  margin-bottom: 4px;
}

.house-card-price {
  color: #f56c6c;
  font-weight: 600;
}

.house-card-hint {
  font-size: 11px;
  color: #409eff;
}
</style>