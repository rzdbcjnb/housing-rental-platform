<template>
  <div class="notification-bell">
    <el-popover
      placement="bottom"
      :width="380"
      trigger="click"
      @show="handleShow"
    >
      <template #reference>
        <el-badge :value="notificationStore.unreadCount" :hidden="!notificationStore.hasUnread" class="bell-badge">
          <el-button :icon="Bell" circle class="bell-button" />
        </el-badge>
      </template>
      
      <div class="notification-panel">
        <div class="panel-header">
          <span class="panel-title">消息通知</span>
          <el-button 
            v-if="notificationStore.hasUnread" 
            type="primary" 
            link 
            size="small"
            @click="handleMarkAllRead"
          >
            全部已读
          </el-button>
        </div>
        
        <div v-loading="loading" class="message-list">
          <template v-if="messages.length > 0">
            <div 
              v-for="message in messages" 
              :key="message.id" 
              class="message-item"
              :class="{ 'unread': !message.is_read }"
              @click="handleMessageClick(message)"
            >
              <div class="message-icon">
                <el-icon :size="20" :class="getMessageIconClass(message.message_type)">
                  <component :is="getMessageIcon(message.message_type)" />
                </el-icon>
              </div>
              <div class="message-content">
                <div class="message-title">{{ message.title }}</div>
                <div class="message-text">{{ message.content }}</div>
                <div class="message-time">{{ formatTime(message.create_time) }}</div>
              </div>
              <el-button
                type="danger"
                :icon="Delete"
                link
                size="small"
                class="delete-btn"
                @click.stop="handleDelete(message)"
              />
            </div>
          </template>
          <el-empty v-else description="暂无消息" :image-size="60" />
        </div>
        
        <div class="panel-footer">
          <el-button type="primary" link @click="goToMessagePage">查看全部消息</el-button>
        </div>
      </div>
    </el-popover>

    <!-- 消息详情弹窗 -->
    <el-dialog
      v-model="detailVisible"
      :title="currentMessage?.title || '消息详情'"
      width="500px"
      class="message-detail-dialog"
    >
      <div v-if="currentMessage" class="message-detail">
        <div class="detail-header">
          <div class="detail-icon">
            <el-icon :size="24" :class="getMessageIconClass(currentMessage.message_type)">
              <component :is="getMessageIcon(currentMessage.message_type)" />
            </el-icon>
          </div>
          <div class="detail-meta">
            <span class="detail-type">{{ getMessageTypeName(currentMessage.message_type) }}</span>
            <span class="detail-time">{{ formatTime(currentMessage.create_time) }}</span>
          </div>
        </div>
        <div class="detail-content">
          {{ currentMessage.content }}
        </div>
        <div v-if="currentMessage.related_house" class="detail-action">
          <el-button type="primary" @click="goToHouse(currentMessage.related_house)">
            查看关联房源
          </el-button>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { Bell, Delete, CircleCheck, Warning, ChatDotRound, Star, Document } from '@element-plus/icons-vue'
import { useNotificationStore } from '@/store/notification'
import { useUserStore } from '@/store/user'
import { ElMessageBox, ElMessage } from 'element-plus'

const router = useRouter()
const notificationStore = useNotificationStore()
const userStore = useUserStore()

const messages = ref([])
const loading = ref(false)
const detailVisible = ref(false)
const currentMessage = ref(null)

// 获取消息图标
function getMessageIcon(type) {
  const iconMap = {
    'audit': CircleCheck,
    'status': Warning,
    'system': Document,
    'favorite': Star,
    'new_house': ChatDotRound
  }
  return iconMap[type] || Bell
}

// 获取消息图标样式类
function getMessageIconClass(type) {
  const classMap = {
    'audit': 'icon-audit',
    'status': 'icon-status',
    'system': 'icon-system',
    'favorite': 'icon-favorite',
    'new_house': 'icon-new-house'
  }
  return classMap[type] || ''
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
  if (diff < 604800000) return `${Math.floor(diff / 86400000)}天前`
  return date.toLocaleDateString('zh-CN')
}

// 获取消息类型名称
function getMessageTypeName(type) {
  const nameMap = {
    'audit': '审核通知',
    'status': '状态变更',
    'system': '系统公告',
    'favorite': '收藏提醒',
    'new_house': '新房源提醒'
  }
  return nameMap[type] || '通知'
}

// 显示弹窗时加载消息
async function handleShow() {
  loading.value = true
  try {
    const res = await notificationStore.fetchMessages({ page_size: 10 })
    messages.value = res.results || []
  } catch (e) {
    console.error('加载消息失败:', e)
  } finally {
    loading.value = false
  }
}

// 点击消息 - 显示详情弹窗
function handleMessageClick(message) {
  if (!message.is_read) {
    notificationStore.markMessageRead(message.id)
  }
  // 显示详情弹窗
  currentMessage.value = message
  detailVisible.value = true
}

// 跳转到房源详情
function goToHouse(houseId) {
  detailVisible.value = false
  // 管理员跳转到审核页面
  if (userStore.isAdmin) {
    router.push('/admin')
    ElMessage.info('请在审核页面查看房源详情')
  } else {
    router.push(`/houses/${houseId}`)
  }
}

// 删除消息
async function handleDelete(message) {
  try {
    await ElMessageBox.confirm('确定要删除这条消息吗？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await notificationStore.deleteMessage(message.id)
    messages.value = messages.value.filter(m => m.id !== message.id)
    ElMessage.success('删除成功')
  } catch (e) {
    if (e !== 'cancel') {
      ElMessage.error('删除失败')
    }
  }
}

// 标记所有为已读
async function handleMarkAllRead() {
  await notificationStore.markAllRead()
  messages.value.forEach(m => {
    m.is_read = true
  })
  ElMessage.success('已全部标记为已读')
}

// 跳转到消息页面
function goToMessagePage() {
  router.push('/chat')
}

onMounted(() => {
  // 连接WebSocket
  notificationStore.connect()
  // 获取未读消息数量
  notificationStore.getUnreadCount()
})

onUnmounted(() => {
  // 断开WebSocket连接
  notificationStore.disconnect()
})
</script>

<style scoped>
.notification-bell {
  display: inline-block;
}

.bell-button {
  font-size: 18px;
  color: #fff;
  background: transparent;
  border: none;
}

.bell-button:hover {
  background: rgba(255, 255, 255, 0.2);
}

.notification-panel {
  margin: -12px;
}

.panel-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px;
  border-bottom: 1px solid #ebeef5;
}

.panel-title {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
}

.message-list {
  max-height: 400px;
  overflow-y: auto;
}

.message-item {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 12px 16px;
  border-bottom: 1px solid #f0f0f0;
  cursor: pointer;
  transition: background 0.2s;
  position: relative;
}

.message-item:hover {
  background: #f5f7fa;
}

.message-item.unread {
  background: #ecf5ff;
}

.message-item.unread:hover {
  background: #d9ecff;
}

.message-icon {
  flex-shrink: 0;
  width: 36px;
  height: 36px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f0f0f0;
}

.icon-audit {
  color: #67c23a;
}

.icon-status {
  color: #e6a23c;
}

.icon-system {
  color: #409eff;
}

.icon-favorite {
  color: #f56c6c;
}

.icon-new-house {
  color: #909399;
}

.message-content {
  flex: 1;
  min-width: 0;
}

.message-title {
  font-size: 14px;
  font-weight: 500;
  color: #303133;
  margin-bottom: 4px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.message-text {
  font-size: 13px;
  color: #606266;
  margin-bottom: 4px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.message-time {
  font-size: 12px;
  color: #909399;
}

.delete-btn {
  position: absolute;
  right: 16px;
  top: 50%;
  transform: translateY(-50%);
  opacity: 0;
  transition: opacity 0.2s;
}

.message-item:hover .delete-btn {
  opacity: 1;
}

.panel-footer {
  padding: 12px 16px;
  text-align: center;
  border-top: 1px solid #ebeef5;
}

/* 消息详情弹窗样式 */
.message-detail {
  padding: 10px 0;
}

.detail-header {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 20px;
  padding-bottom: 16px;
  border-bottom: 1px solid #ebeef5;
}

.detail-icon {
  width: 48px;
  height: 48px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f0f0f0;
}

.detail-meta {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.detail-type {
  font-size: 14px;
  color: #909399;
}

.detail-time {
  font-size: 13px;
  color: #c0c4cc;
}

.detail-content {
  font-size: 15px;
  color: #303133;
  line-height: 1.8;
  white-space: pre-wrap;
  word-break: break-word;
}

.detail-action {
  margin-top: 20px;
  padding-top: 16px;
  border-top: 1px solid #ebeef5;
  text-align: center;
}
</style>
