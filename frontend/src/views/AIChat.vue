<template>
  <div class="page-ai-chat">
    <!-- 头部 -->
    <div class="chat-header">
      <div class="header-left">
        <el-button :icon="ArrowLeft" circle @click="goBack" />
        <div class="user-info">
          <el-avatar :size="36" class="ai-avatar">
            <el-icon><Service /></el-icon>
          </el-avatar>
          <div class="user-detail">
            <span class="username">AI客服 - 小智</span>
            <span class="status online">在线</span>
          </div>
        </div>
      </div>
      <div class="header-right">
        <el-tooltip content="历史对话" placement="bottom">
          <el-button :icon="Clock" circle @click="historyVisible = true" />
        </el-tooltip>
        <el-tooltip content="新建对话" placement="bottom">
          <el-button :icon="Plus" circle @click="startNewChat" />
        </el-tooltip>
      </div>
    </div>

    <el-drawer v-model="historyVisible" title="历史对话" direction="ltr" size="320px">
      <el-empty v-if="conversations.length === 0" description="暂无历史对话" :image-size="72" />
      <div v-else class="conversation-list">
        <button
          v-for="conversation in conversations"
          :key="conversation.id"
          type="button"
          class="conversation-item"
          :class="{ active: conversation.id === currentConversationId }"
          @click="openConversation(conversation.id)"
        >
          <span class="conversation-title">{{ conversation.title || '新对话' }}</span>
          <span class="conversation-time">{{ formatConversationTime(conversation.updated_at) }}</span>
        </button>
      </div>
    </el-drawer>

    <!-- 消息列表 -->
    <div ref="messageListRef" class="message-list">
      <!-- 欢迎消息 -->
      <div v-if="messages.length === 0" class="welcome-section">
        <div class="welcome-icon">
          <el-icon :size="48"><Service /></el-icon>
        </div>
        <h3>你好！我是AI客服小智</h3>
        <p>我可以帮你：</p>
        <div class="feature-list">
          <div class="feature-item" @click="sendQuickMessage('推荐房源')">
            <el-icon><House /></el-icon>
            <span>推荐房源</span>
          </div>
          <div class="feature-item" @click="sendQuickMessage('对比房源')">
            <el-icon><DataAnalysis /></el-icon>
            <span>对比房源</span>
          </div>
          <div class="feature-item" @click="sendQuickMessage('解释押一付三')">
            <el-icon><Document /></el-icon>
            <span>解释租赁条款</span>
          </div>
          <div class="feature-item" @click="sendQuickMessage('如何发布房源')">
            <el-icon><QuestionFilled /></el-icon>
            <span>使用帮助</span>
          </div>
        </div>
      </div>

      <!-- 消息列表 -->
      <div 
        v-for="msg in messages" 
        :key="msg.id" 
        class="message-item"
        :class="{ 'self': msg.role === 'user', 'ai': msg.role === 'assistant' }"
      >
        <div class="message-avatar">
          <el-avatar :size="36">
            <template v-if="msg.role === 'user'">
              {{ userStore.userInfo?.username?.charAt(0) || '?' }}
            </template>
            <template v-else>
              <el-icon><Service /></el-icon>
            </template>
          </el-avatar>
        </div>
        <div class="message-content">
          <div class="message-bubble">
            <template v-if="msg.streaming && !msg.content">
              <div class="stream-status">
                <div class="typing-indicator"><span></span><span></span><span></span></div>
                <span v-if="generationStatus">{{ generationStatus }}</span>
              </div>
            </template>
            <template v-else-if="msg.role === 'user' && msg.content && msg.content.includes('[房源信息]')">
              <div class="house-info-card">
                <div class="house-info-header">📋 房源信息</div>
                <div class="house-info-content" v-html="formatHouseInfo(msg.content)"></div>
              </div>
            </template>
            <template v-else>
              <template v-for="(part, index) in parseAIResponse(msg.content, msg.houses)" :key="index">
                <div v-if="part.type === 'text'" class="message-text" v-html="formatMessage(part.content)"></div>
                <div v-else-if="part.type === 'house'" class="house-card" @click="goToHouse(part.id)">
                  <div class="house-card-body">
                    <div class="house-card-title">🏠 {{ part.title }}</div>
                    <div v-if="part.price || part.rooms" class="house-card-info">
                      <span v-if="part.price" class="house-card-price">¥{{ part.price }}/月</span>
                      <span v-if="part.rooms" class="house-card-rooms">{{ part.rooms }}</span>
                      <span v-if="part.area" class="house-card-area">{{ part.area }}m²</span>
                    </div>
                    <div class="house-card-hint">点击查看详情 →</div>
                  </div>
                </div>
              </template>
            </template>
          </div>
          <div v-if="msg.toolCalls?.length" class="tool-activity-list">
            <div v-for="(tool, toolIndex) in msg.toolCalls" :key="`${tool.tool}-${toolIndex}`" class="tool-activity">
              <span class="tool-name">{{ toolLabel(tool.tool) }}</span>
              <el-tag :type="tool.status === 'failed' ? 'danger' : tool.status === 'running' ? 'warning' : 'success'" size="small">
                {{ tool.status === 'running' ? '执行中' : tool.status === 'failed' ? '失败' : '完成' }}
              </el-tag>
              <span v-if="tool.resultCount != null" class="tool-count">{{ tool.resultCount }} 条</span>
            </div>
          </div>
          <div v-if="msg.pendingActions?.length" class="pending-action-list">
            <div v-for="action in msg.pendingActions" :key="action.token" class="pending-action">
              <div class="pending-action-title">
                {{ action.action === 'favorite' ? '确认收藏' : '确认发送咨询' }}
              </div>
              <div v-if="action.house" class="pending-house" @click="goToHouse(action.house.id)">
                <div class="pending-house-main">
                  <strong>{{ action.house.title }}</strong>
                  <span v-if="action.house.rooms">{{ action.house.rooms }}</span>
                </div>
                <span v-if="action.house.price" class="pending-price">¥{{ action.house.price }}/月</span>
              </div>
              <div v-if="action.content" class="pending-content">{{ action.content }}</div>
              <div v-if="action.state === 'pending'" class="pending-actions">
                <el-button type="primary" size="small" :icon="Check" :loading="action.confirming" @click="confirmPendingAction(action)">
                  确认
                </el-button>
                <el-button size="small" :icon="Close" @click="aiStore.dismissAction(action)">取消</el-button>
              </div>
              <el-alert v-else-if="action.state === 'completed'" title="操作已完成" type="success" :closable="false" show-icon />
              <el-alert v-else-if="action.state === 'failed'" :title="action.error || '操作失败'" type="error" :closable="false" show-icon />
              <el-alert v-else-if="action.state === 'expired'" title="确认操作已过期" type="info" :closable="false" show-icon />
              <el-button v-if="action.executionResult?.room_id" class="open-chat-button" size="small" :icon="ChatDotRound" @click="openChat(action.executionResult.room_id)">
                进入聊天
              </el-button>
            </div>
          </div>
          <div class="message-time">{{ formatTime(msg.created_at) }}</div>
          <div v-if="msg.sources && msg.sources.length" class="message-sources">
            <div class="sources-title">参考来源</div>
            <div v-for="(source, sourceIndex) in msg.sources" :key="source.id" class="source-item">
              <span class="source-index">[知识{{ sourceIndex + 1 }}]</span>
              <span class="source-title">{{ source.title }}</span>
              <span v-if="source.category" class="source-category">{{ source.category }}</span>
              <span v-if="source.score != null" class="source-score">
                {{ Math.round(source.score * 100) }}%
              </span>
            </div>
          </div>
        </div>
      </div>

    </div>

    <!-- 输入区域 -->
    <div class="chat-input">
      <div v-if="selectedHouse" class="selected-house-context">
        <el-icon><House /></el-icon>
        <div class="selected-house-main">
          <strong>{{ selectedHouse.title }}</strong>
          <span>¥{{ selectedHouse.price }}/月 · {{ selectedHouse.rooms }}</span>
        </div>
        <el-button :icon="Close" text circle title="移除房源" @click="selectedHouse = null" />
      </div>
      <div class="input-main">
        <el-input
          v-model="inputMessage"
          type="textarea"
          :rows="1"
          :autosize="{ minRows: 1, maxRows: 4 }"
          placeholder="输入您的问题..."
          :disabled="sending"
          @keydown.enter.exact.prevent="() => sendMessage()"
        />
        <el-button
          v-if="sending"
          type="danger"
          :icon="CircleCloseFilled"
          title="停止生成"
          @click="aiStore.stopGeneration()"
        />
        <el-button
          v-else
          type="primary"
          :icon="Promotion"
          :disabled="!inputMessage.trim()"
          title="发送"
          @click="() => sendMessage()"
        />
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount, nextTick, watch } from 'vue'
import DOMPurify from 'dompurify'
import { useRouter, useRoute } from 'vue-router'
import { 
  ArrowLeft, Plus, Promotion, Service, House, 
  DataAnalysis, Document, QuestionFilled, CircleCloseFilled,
  Check, Close, ChatDotRound, Clock
} from '@element-plus/icons-vue'
import { useAiStore } from '@/store/ai'
import { useUserStore } from '@/store/user'
import { getHouseDetailApi } from '@/api/house'
import { ElMessage } from 'element-plus'

const router = useRouter()
const route = useRoute()
const aiStore = useAiStore()
const userStore = useUserStore()

const SANITIZE_CONFIG = {
  ALLOWED_TAGS: ['br', 'strong', 'em', 'ul', 'ol', 'li', 'h2', 'h3', 'h4', 'code', 'pre', 'table', 'thead', 'tbody', 'tr', 'th', 'td'],
  ALLOWED_ATTR: ['class']
}

const inputMessage = ref('')
const messageListRef = ref(null)
const historyVisible = ref(false)
const selectedHouse = ref(null)

const messages = computed(() => aiStore.messages)
const conversations = computed(() => aiStore.conversations)
const currentConversationId = computed(() => aiStore.currentConversationId)
const sending = computed(() => aiStore.sending)
const generationStatus = computed(() => aiStore.generationStatus)

const TOOL_LABELS = {
  searchHouses: '检索房源',
  getHouseDetail: '查询详情',
  compareHouses: '比较房源',
  searchKnowledge: '检索租房知识',
  prepareFavorite: '准备收藏',
  prepareSendLandlordMessage: '准备联系房东'
}

function toolLabel(name) {
  return TOOL_LABELS[name] || name
}

async function confirmPendingAction(action) {
  try {
    await aiStore.confirmAction(action)
    ElMessage.success('操作已完成')
  } catch {
    ElMessage.error(action.error || '操作失败')
  }
}

function openChat(roomId) {
  router.push(`/chat/${roomId}`)
}
function formatConversationTime(timeStr) {
  if (!timeStr) return ''
  return new Date(timeStr).toLocaleString('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  })
}

// 格式化时间
function formatTime(timeStr) {
  if (!timeStr) return ''
  const date = new Date(timeStr)
  const now = new Date()
  const diff = now - date
  
  if (diff < 60000) return '刚刚'
  if (diff < 3600000) return `${Math.floor(diff / 60000)}分钟前`
  if (diff < 86400000) return `${Math.floor(diff / 3600000)}小时前`
  return date.toLocaleDateString('zh-CN')
}

// 格式化消息（支持Markdown）
function formatMessage(content) {
  if (!content) return ''
  let formatted = content
  
  // 处理表格
  const tableRegex = /\|(.+)\|\n\|[-| :]+\|\n((?:\|.+\|\n?)+)/g
  formatted = formatted.replace(tableRegex, (match, header, body) => {
    const headerCells = header.split('|').map(c => c.trim()).filter(c => c)
    const bodyRows = body.trim().split('\n').map(row => 
      row.split('|').map(c => c.trim()).filter(c => c)
    )
    
    let table = '<table class="md-table"><thead><tr>'
    headerCells.forEach(cell => { table += `<th>${cell}</th>` })
    table += '</tr></thead><tbody>'
    bodyRows.forEach(row => {
      table += '<tr>'
      row.forEach(cell => { table += `<td>${cell}</td>` })
      table += '</tr>'
    })
    table += '</tbody></table>'
    return table
  })
  
  // 处理代码块
  formatted = formatted.replace(/```([\s\S]*?)```/g, '<pre><code>$1</code></pre>')
  
  // 处理行内代码
  formatted = formatted.replace(/`(.*?)`/g, '<code>$1</code>')
  
  // 处理标题 (## 或 #)
  formatted = formatted.replace(/^### (.*?)$/gm, '<h4>$1</h4>')
  formatted = formatted.replace(/^## (.*?)$/gm, '<h3>$1</h3>')
  formatted = formatted.replace(/^# (.*?)$/gm, '<h2>$1</h2>')
  
  // 处理无序列表 (- 或 *)
  formatted = formatted.replace(/^[\-\*] (.*?)$/gm, '<li>$1</li>')
  formatted = formatted.replace(/(<li>.*?<\/li>\n?)+/g, '<ul>$&</ul>')
  
  // 处理有序列表 (1. 2. 等)
  formatted = formatted.replace(/^\d+\. (.*?)$/gm, '<li>$1</li>')
  
  // 处理加粗 **text**
  formatted = formatted.replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>')
  
  // 处理斜体 *text*
  formatted = formatted.replace(/\*(.*?)\*/g, '<em>$1</em>')
  
  // 处理换行
  formatted = formatted.replace(/\n/g, '<br>')
  
  // 清理多余的br
  formatted = formatted.replace(/<br><br><br>/g, '<br><br>')
  
  return DOMPurify.sanitize(formatted, SANITIZE_CONFIG)
}

// 格式化房源信息
function formatHouseInfo(content) {
  if (!content) return ''
  // 提取房源信息字段
  const lines = content.split('\n')
  let html = ''
  for (const line of lines) {
    if (line.trim()) {
      // 高亮字段名
      const formatted = line.replace(/^([^:：]+[：:])/, '<strong>$1</strong>')
      html += `<div>${formatted}</div>`
    }
  }
  return DOMPurify.sanitize(html, SANITIZE_CONFIG)
}

// 解析AI回复中的房源标记
function parseAIResponse(content, houses) {
  if (!content) return [{ type: 'text', content: '' }]
  
  const parts = []
  let actualContent = content
  let extractedHouses = houses || []
  
  // 从content中提取嵌入的房源数据
  const housesDataMatch = content.match(/\n\n__HOUSES_DATA__(\[.*\])$/s)
  if (housesDataMatch) {
    try {
      extractedHouses = JSON.parse(housesDataMatch[1])
      actualContent = content.substring(0, housesDataMatch.index)
    } catch {
      extractedHouses = houses || []
    }
  }
  
  // 如果后端返回了houses数据，优先使用
  if (extractedHouses && extractedHouses.length > 0) {
    // 先检查文本中是否有 [house:id] 格式
    const regex = /\[house:(\d+)\](.*?)\[\/house\]/g
    let lastIndex = 0
    let match
    const foundIds = new Set()
    
    while ((match = regex.exec(actualContent)) !== null) {
      if (match.index > lastIndex) {
        parts.push({ type: 'text', content: actualContent.slice(lastIndex, match.index) })
      }
      parts.push({ type: 'house', id: parseInt(match[1]), title: match[2].trim() })
      foundIds.add(match[1])
      lastIndex = regex.lastIndex
    }
    
    if (lastIndex < actualContent.length) {
      parts.push({ type: 'text', content: actualContent.slice(lastIndex) })
    }
    
    // 添加后端返回但文本中没有的房源
    for (const house of extractedHouses) {
      if (!foundIds.has(String(house.id))) {
        parts.push({ 
          type: 'house', 
          id: house.id, 
          title: house.title || '房源',
          price: house.price,
          rooms: house.rooms,
          area: house.area,
          region_name: house.region_name
        })
      }
    }
  } else {
    // 没有后端数据，使用前端解析
    const regex = /\[house:(\d+)\](.*?)\[\/house\]/g
    let lastIndex = 0
    let match
    
    while ((match = regex.exec(actualContent)) !== null) {
      if (match.index > lastIndex) {
        parts.push({ type: 'text', content: actualContent.slice(lastIndex, match.index) })
      }
      parts.push({ type: 'house', id: parseInt(match[1]), title: match[2].trim() })
      lastIndex = regex.lastIndex
    }
    
    if (lastIndex < actualContent.length) {
      parts.push({ type: 'text', content: actualContent.slice(lastIndex) })
    }
  }
  
  if (parts.length === 0) {
    parts.push({ type: 'text', content: actualContent })
  }
  
  return parts
}

// 跳转到房源详情
function goToHouse(houseId) {
  const normalizedHouseId = Number(houseId)
  if (!Number.isSafeInteger(normalizedHouseId) || normalizedHouseId <= 0) {
    ElMessage.warning('房源编号无效')
    return
  }
  router.push({
    name: 'HouseDetail',
    params: { id: String(normalizedHouseId) }
  })
}

// 滚动到底部
function scrollToBottom() {
  nextTick(() => {
    if (messageListRef.value) {
      messageListRef.value.scrollTop = messageListRef.value.scrollHeight
    }
  })
}

function latestPendingAction() {
  for (let messageIndex = messages.value.length - 1; messageIndex >= 0; messageIndex -= 1) {
    const actions = messages.value[messageIndex].pendingActions || []
    for (let actionIndex = actions.length - 1; actionIndex >= 0; actionIndex -= 1) {
      if (actions[actionIndex].state === 'pending') return actions[actionIndex]
    }
  }
  return null
}

function confirmsPendingAction(content, action) {
  const normalized = content.replace(/[\\s，。！？!?]/g, '')
  if (action.action === 'send_landlord_message') {
    return /^(直接发送|确认发送|发送|就这样发|按这个发送|帮我发送)$/.test(normalized)
  }
  return /^(确认收藏|直接收藏|收藏)$/.test(normalized)
}

// 发送消息；明确的文字确认会执行页面上已经存在的待确认操作。
async function sendMessage(prefilledContent) {
  const content = prefilledContent || inputMessage.value.trim()
  if (!content || sending.value) return

  const pendingAction = latestPendingAction()
  if (pendingAction && confirmsPendingAction(content, pendingAction)) {
    inputMessage.value = ''
    await confirmPendingAction(pendingAction)
    scrollToBottom()
    return
  }

  inputMessage.value = ''
  await aiStore.sendMessage(content, selectedHouse.value?.id || null)
  scrollToBottom()
}

// 发送快捷消息
function sendQuickMessage(content) {
  inputMessage.value = content
  sendMessage()
}

// 新建对话
async function startNewChat() {
  const created = await aiStore.newConversation()
  if (created) {
    selectedHouse.value = null
    historyVisible.value = false
  }
}

async function openConversation(conversationId) {
  if (sending.value || conversationId === currentConversationId.value) {
    historyVisible.value = false
    return
  }
  await aiStore.loadMessages(conversationId)
  selectedHouse.value = null
  historyVisible.value = false
  scrollToBottom()
}

// 返回
function goBack() {
  router.push('/chat')
}

// 监听消息变化，自动滚动
watch(() => messages.value.map((message) => message.content?.length || 0).join(','), () => {
  scrollToBottom()
})

onMounted(async () => {
  await aiStore.fetchConversations()
  
  // 加载完对话列表后，如果有消息则滚动到底部
  await nextTick()
  scrollToBottom()
  
  // 房源详情页只传递房源编号。这里展示为待提问上下文，不主动发送消息。
  let houseId = Number(route.query.houseId)
  if ((!Number.isInteger(houseId) || houseId <= 0) && route.query.houseInfo) {
    const legacyInfo = decodeURIComponent(String(route.query.houseInfo))
    houseId = Number(legacyInfo.match(/房源ID[:：]\s*(\d+)/)?.[1])
  }
  if (Number.isInteger(houseId) && houseId > 0) {
    try {
      const response = await getHouseDetailApi(houseId)
      const house = response.data || response
      selectedHouse.value = {
        id: house.id,
        title: house.title || `房源 ${house.id}`,
        price: house.price,
        rooms: house.rooms || '户型待确认'
      }
      const query = { ...route.query }
      delete query.houseId
      delete query.houseInfo
      await router.replace({ path: route.path, query })
    } catch {
      ElMessage.warning('房源不存在或已下架')
    }
  }
})

onBeforeUnmount(() => {
  if (sending.value) aiStore.stopGeneration()
})
</script>


<style scoped>
.page-ai-chat {
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

.header-right {
  display: flex;
  gap: 8px;
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

.ai-avatar {
  background-color: #67c23a;
  color: #fff;
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

.conversation-list {
  display: flex;
  flex-direction: column;
}

.conversation-item {
  display: flex;
  width: 100%;
  min-height: 58px;
  padding: 10px 12px;
  flex-direction: column;
  align-items: flex-start;
  justify-content: center;
  gap: 4px;
  border: 0;
  border-bottom: 1px solid #ebeef5;
  background: transparent;
  color: #303133;
  cursor: pointer;
  text-align: left;
}

.conversation-item:hover,
.conversation-item.active {
  background: #f0f9eb;
}

.conversation-title {
  width: 100%;
  overflow: hidden;
  font-size: 14px;
  font-weight: 500;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.conversation-time {
  color: #909399;
  font-size: 12px;
}

.message-list {
  flex: 1;
  overflow-y: auto;
  padding: 16px;
  min-height: 0;
}

/* 欢迎区域 */
.welcome-section {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 40px 20px;
  text-align: center;
}

.welcome-icon {
  width: 80px;
  height: 80px;
  border-radius: 50%;
  background: linear-gradient(135deg, #67c23a, #409eff);
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  margin-bottom: 16px;
}

.welcome-section h3 {
  margin: 0 0 8px;
  font-size: 20px;
  color: #333;
}

.welcome-section p {
  margin: 0 0 20px;
  color: #666;
}

.feature-list {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 12px;
  width: 100%;
  max-width: 400px;
}

.feature-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 16px;
  background-color: #fff;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.2s;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.05);
}

.feature-item:hover {
  background-color: #f0f9eb;
  transform: translateY(-2px);
  box-shadow: 0 4px 8px rgba(0, 0, 0, 0.1);
}

.feature-item .el-icon {
  color: #67c23a;
  font-size: 20px;
}

.feature-item span {
  font-size: 14px;
  color: #333;
}

/* 消息样式 */
.message-item {
  display: flex;
  margin-bottom: 16px;
  gap: 8px;
}

.message-item.self {
  flex-direction: row-reverse;
}

.message-avatar {
  flex-shrink: 0;
}

.message-item.ai .message-avatar .el-avatar {
  background-color: #67c23a;
  color: #fff;
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
  line-height: 1.6;
}

.message-text h2 {
  font-size: 16px;
  font-weight: 600;
  margin: 8px 0 4px;
  color: #303133;
}

.message-text h3 {
  font-size: 15px;
  font-weight: 600;
  margin: 6px 0 4px;
  color: #303133;
}

.message-text h4 {
  font-size: 14px;
  font-weight: 600;
  margin: 4px 0 2px;
  color: #303133;
}

.message-text ul,
.message-text ol {
  margin: 4px 0;
  padding-left: 20px;
}

.message-text li {
  margin: 2px 0;
  line-height: 1.5;
}

.message-text code {
  background: #f0f0f0;
  padding: 1px 4px;
  border-radius: 3px;
  font-size: 13px;
  font-family: Consolas, monospace;
}

.message-text pre {
  background: #f5f5f5;
  padding: 8px;
  border-radius: 4px;
  overflow-x: auto;
  margin: 4px 0;
}

.message-text pre code {
  background: none;
  padding: 0;
}

.house-info-card {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  border-radius: 12px;
  padding: 12px 16px;
  color: #fff;
}

.house-info-header {
  font-size: 14px;
  font-weight: 600;
  margin-bottom: 8px;
  padding-bottom: 8px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.3);
}

.house-info-content {
  font-size: 13px;
  line-height: 1.6;
}

.house-info-content strong {
  color: #ffd700;
}

.message-sources {
  margin-top: 6px;
  padding: 8px 10px;
  border-left: 3px solid #67c23a;
  background: #f6f8f5;
  font-size: 12px;
  color: #606266;
}

.sources-title {
  margin-bottom: 4px;
  font-weight: 600;
  color: #303133;
}

.source-item {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
  margin-top: 3px;
}

.source-index {
  flex-shrink: 0;
  color: #409eff;
}

.source-title {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.source-category,
.source-score {
  flex-shrink: 0;
  color: #909399;
}

.message-time {
  font-size: 12px;
  color: #909399;
  margin-top: 4px;
}

.message-item.self .message-time {
  text-align: right;
}

/* 房源卡片 */
.house-card {
  background: linear-gradient(135deg, #f0f9eb, #e1f3d8);
  border: 1px solid #67c23a;
  border-radius: 8px;
  padding: 12px;
  margin: 8px 0;
  cursor: pointer;
  transition: all 0.2s;
}

.house-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 8px rgba(103, 194, 58, 0.2);
}

.house-card-title {
  font-size: 14px;
  font-weight: 600;
  color: #333;
  margin-bottom: 4px;
}

.house-card-info {
  display: flex;
  gap: 8px;
  margin: 4px 0;
  font-size: 12px;
  color: #909399;
}

.house-card-price {
  color: #f56c6c;
  font-weight: 600;
}

.house-card-rooms,
.house-card-area {
  color: #606266;
}

.house-card-hint {
  font-size: 12px;
  color: #67c23a;
}

.stream-status {
  display: flex;
  align-items: center;
  gap: 10px;
  min-height: 24px;
  color: #606266;
  font-size: 13px;
}

.tool-activity-list {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 6px;
}

.tool-activity {
  display: flex;
  align-items: center;
  gap: 6px;
  min-height: 28px;
  padding: 3px 8px;
  border: 1px solid #dcdfe6;
  border-radius: 6px;
  background: #fff;
  font-size: 12px;
}

.tool-name {
  color: #303133;
}

.tool-count {
  color: #909399;
}

.pending-action-list {
  margin-top: 8px;
}

.pending-action {
  padding: 12px;
  border: 1px solid #c6e2ff;
  border-radius: 8px;
  background: #fff;
}

.pending-action-title {
  margin-bottom: 8px;
  font-size: 14px;
  font-weight: 600;
  color: #303133;
}

.pending-house {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 9px 10px;
  border: 1px solid #ebeef5;
  border-radius: 6px;
  cursor: pointer;
}

.pending-house-main {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 3px;
  color: #606266;
  font-size: 12px;
}

.pending-house-main strong {
  overflow: hidden;
  color: #303133;
  font-size: 13px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.pending-price {
  flex-shrink: 0;
  color: #f56c6c;
  font-weight: 600;
}

.pending-content {
  margin-top: 8px;
  padding: 8px 10px;
  border-left: 3px solid #409eff;
  background: #f5f7fa;
  color: #303133;
  font-size: 13px;
  line-height: 1.5;
  word-break: break-word;
}

.pending-actions,
.open-chat-button {
  margin-top: 10px;
}
/* 打字动画 */
.typing-indicator {
  display: flex;
  gap: 4px;
  padding: 4px 0;
}

.typing-indicator span {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background-color: #909399;
  animation: typing 1.4s infinite;
}

.typing-indicator span:nth-child(2) {
  animation-delay: 0.2s;
}

.typing-indicator span:nth-child(3) {
  animation-delay: 0.4s;
}

@keyframes typing {
  0%, 60%, 100% {
    transform: translateY(0);
    opacity: 0.5;
  }
  30% {
    transform: translateY(-8px);
    opacity: 1;
  }
}

/* 输入区域 */
.chat-input {
  background-color: #fff;
  border-top: 1px solid #e4e7ed;
  padding: 12px 16px;
  flex-shrink: 0;
}

.selected-house-context {
  display: flex;
  min-height: 54px;
  margin-bottom: 10px;
  padding: 8px 10px;
  align-items: center;
  gap: 10px;
  border: 1px solid #c6e2ff;
  border-radius: 6px;
  background: #ecf5ff;
  color: #409eff;
}

.selected-house-main {
  display: flex;
  min-width: 0;
  flex: 1;
  flex-direction: column;
  gap: 2px;
}

.selected-house-main strong {
  overflow: hidden;
  color: #303133;
  font-size: 13px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.selected-house-main span {
  color: #606266;
  font-size: 12px;
}

.input-main {
  display: flex;
  gap: 8px;
}

.input-main .el-input {
  flex: 1;
}

/* 表格样式 */
.message-text :deep(.md-table) {
  border-collapse: collapse;
  margin: 8px 0;
  width: 100%;
  font-size: 13px;
}

.message-text :deep(.md-table th),
.message-text :deep(.md-table td) {
  border: 1px solid #e0e0e0;
  padding: 6px 10px;
  text-align: left;
}

.message-text :deep(.md-table th) {
  background: #f5f5f5;
  font-weight: 600;
}

.message-text :deep(.md-table tr:nth-child(even)) {
  background: #fafafa;
}
</style>
