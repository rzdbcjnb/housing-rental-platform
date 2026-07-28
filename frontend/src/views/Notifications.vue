<template>
  <div class="page-notifications">
    <div class="page-header">
      <el-button :icon="ArrowLeft" @click="goBack">返回</el-button>
      <h2 class="page-title">系统消息</h2>
    </div>

    <div v-loading="loading" class="notification-list">
      <template v-if="messages.length > 0">
        <div 
          v-for="msg in messages" 
          :key="msg.id" 
          class="notification-item"
          :class="{ unread: !msg.is_read }"
        >
          <div class="notification-icon">
            <el-icon :size="24" :color="msg.is_read ? '#909399' : '#409eff'">
              <Bell />
            </el-icon>
          </div>
          <div class="notification-content">
            <div class="notification-header">
              <span class="notification-title">{{ msg.title }}</span>
              <span class="notification-time">{{ formatTime(msg.created_at) }}</span>
            </div>
            <div class="notification-body">{{ msg.content }}</div>
          </div>
        </div>
      </template>
      <el-empty v-else-if="!loading" description="暂无系统消息" />
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowLeft, Bell } from '@element-plus/icons-vue'
import { getMessagesApi, markAllReadApi } from '@/api/notification'
import { useNotificationStore } from '@/store/notification'

const router = useRouter()
const notificationStore = useNotificationStore()
const messages = ref([])
const loading = ref(false)

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

async function fetchMessages() {
  loading.value = true
  try {
    const res = await getMessagesApi()
    messages.value = res.results || []
  } catch (e) {
    console.error('获取消息失败:', e)
  } finally {
    loading.value = false
  }
}

function goBack() {
  router.push('/chat')
}

onMounted(async () => {
  await fetchMessages()
  // 标记所有系统消息为已读
  try {
    await markAllReadApi()
    // 重置通知store的未读计数
    notificationStore.unreadCount = 0
  } catch (e) {
    console.error('标记已读失败:', e)
  }
})
</script>

<style scoped>
.page-notifications {
  max-width: 800px;
  margin: 0 auto;
  padding: 20px;
}

.page-header {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 20px;
}

.page-title {
  margin: 0;
  font-size: 24px;
  color: #333;
}

.notification-list {
  background: #fff;
  border-radius: 12px;
  overflow: hidden;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
}

.notification-item {
  display: flex;
  gap: 12px;
  padding: 16px;
  transition: background-color 0.2s;
  border-bottom: 1px solid #f0f0f0;
}

.notification-item:last-child {
  border-bottom: none;
}

.notification-item:hover {
  background-color: #f5f7fa;
}

.notification-item.unread {
  background-color: #ecf5ff;
}

.notification-icon {
  flex-shrink: 0;
  width: 40px;
  height: 40px;
  border-radius: 50%;
  background: #f0f2f5;
  display: flex;
  align-items: center;
  justify-content: center;
}

.notification-content {
  flex: 1;
  min-width: 0;
}

.notification-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 4px;
}

.notification-title {
  font-size: 15px;
  font-weight: 500;
  color: #333;
}

.notification-time {
  font-size: 12px;
  color: #909399;
}

.notification-body {
  font-size: 14px;
  color: #666;
  line-height: 1.5;
}
</style>