<template>
  <div class="page-chat-list">
    <div class="page-header">
      <h2 class="page-title">聊天消息</h2>
      <div class="page-stats">
        共 <strong>{{ total }}</strong> 条记录
      </div>
    </div>

    <!-- AI客服入口 -->
    <div class="ai-service-card" @click="goToAiChat">
      <div class="ai-card-avatar">
        <el-avatar :size="48" class="ai-avatar">
          <el-icon :size="24"><Service /></el-icon>
        </el-avatar>
      </div>
      <div class="ai-card-info">
        <div class="ai-card-name">AI客服 - 小智</div>
        <div class="ai-card-desc">智能推荐房源、解答问题</div>
      </div>
      <el-icon class="ai-card-arrow"><ArrowRight /></el-icon>
    </div>

    <div v-loading="loading" class="chat-list">
      <template v-if="rooms.length > 0">
        <div 
          v-for="room in rooms" 
          :key="room.id" 
          class="chat-item"
          @click="goToChat(room)"
        >
          <div class="chat-avatar">
            <el-avatar :size="48" :class="{ 'system-avatar': room.room_type === 'system' }">
              <template v-if="room.room_type === 'system'">
                <el-icon><Bell /></el-icon>
              </template>
              <template v-else>
                {{ room.other_user?.username?.charAt(0) || '?' }}
              </template>
            </el-avatar>
            <span 
              v-if="room.other_user?.is_online" 
              class="online-dot"
            ></span>
          </div>
          <div class="chat-info">
            <div class="chat-header">
              <span class="chat-name">{{ room.other_user?.username || '未知用户' }}</span>
              <span class="chat-time">{{ formatTime(room.last_message?.created_at || room.updated_at) }}</span>
            </div>
            <div class="chat-preview">
              <span v-if="room.last_message" class="last-message">
                <template v-if="room.last_message.message_type === 'image'">[图片]</template>
                <template v-else-if="room.last_message.message_type === 'house_share'">[房源分享]</template>
                <template v-else>{{ room.last_message.content }}</template>
              </span>
              <span v-else class="no-message">暂无消息</span>
            </div>
            <div v-if="room.house_info" class="chat-house">
              <el-icon><House /></el-icon>
              <span>{{ room.house_info.title }}</span>
            </div>
          </div>
          <div v-if="room.unread_count > 0 && room.room_type !== 'system'" class="unread-badge">
            {{ room.unread_count > 99 ? '99+' : room.unread_count }}
          </div>
        </div>
      </template>
      <el-empty v-else-if="!loading" description="暂无聊天记录">
        <el-button type="primary" @click="goToHouseList">去浏览房源</el-button>
      </el-empty>
    </div>

    <section v-if="total > 0" class="pagination-section">
      <el-pagination
        v-model:current-page="currentPage"
        v-model:page-size="pageSize"
        :total="total"
        :page-sizes="[10, 20, 30, 50]"
        layout="total, sizes, prev, pager, next, jumper"
        @size-change="fetchRooms"
        @current-change="fetchRooms"
      />
    </section>
  </div>
</template>

<script setup>
import { ref, onMounted, onActivated } from 'vue'
import { useRouter } from 'vue-router'
import { House, Bell, Service, ArrowRight } from '@element-plus/icons-vue'
import { getChatRoomsApi } from '@/api/chat'

const router = useRouter()

const rooms = ref([])
const loading = ref(false)
const total = ref(0)
const currentPage = ref(1)
const pageSize = ref(20)

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

// 获取聊天房间列表
async function fetchRooms() {
  loading.value = true
  try {
    const res = await getChatRoomsApi({
      page: currentPage.value,
      page_size: pageSize.value
    })
    rooms.value = res.results || []
    total.value = res.count || 0
  } catch (e) {
    console.error('获取聊天列表失败:', e)
  } finally {
    loading.value = false
  }
}

// 进入聊天
function goToChat(room) {
  if (room.room_type === 'system') {
    router.push('/notifications')
  } else {
    router.push(`/chat/${room.id}`)
  }
}

// 进入AI客服
function goToAiChat() {
  router.push('/ai-chat')
}

// 去房源列表
function goToHouseList() {
  router.push('/houses')
}

onMounted(() => {
  fetchRooms()
})

// 页面激活时刷新列表（从聊天页面返回时）
onActivated(() => {
  fetchRooms()
})
</script>

<style scoped>
.page-chat-list {
  max-width: 800px;
  margin: 0 auto;
  padding: 20px;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.page-title {
  margin: 0;
  font-size: 24px;
  color: #333;
}

.page-stats {
  font-size: 14px;
  color: #909399;
}

/* AI客服入口 */
.ai-service-card {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 16px;
  background: linear-gradient(135deg, #67c23a, #409eff);
  border-radius: 12px;
  margin-bottom: 16px;
  cursor: pointer;
  transition: all 0.2s;
}

.ai-service-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(103, 194, 58, 0.3);
}

.ai-card-avatar .ai-avatar {
  background-color: rgba(255, 255, 255, 0.2);
  color: #fff;
}

.ai-card-info {
  flex: 1;
}

.ai-card-name {
  font-size: 16px;
  font-weight: 600;
  color: #fff;
}

.ai-card-desc {
  font-size: 12px;
  color: rgba(255, 255, 255, 0.8);
  margin-top: 2px;
}

.ai-card-arrow {
  color: rgba(255, 255, 255, 0.6);
  font-size: 18px;
}

/* 聊天列表 */
.chat-list {
  background: #fff;
  border-radius: 12px;
  overflow: hidden;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
}

.chat-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 16px;
  cursor: pointer;
  transition: background-color 0.2s;
  border-bottom: 1px solid #f0f0f0;
}

.chat-item:last-child {
  border-bottom: none;
}

.chat-item:hover {
  background-color: #f5f7fa;
}

.chat-avatar {
  position: relative;
  flex-shrink: 0;
}

.system-avatar {
  background-color: #409eff;
  color: #fff;
}

.online-dot {
  position: absolute;
  bottom: 2px;
  right: 2px;
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background-color: #67c23a;
  border: 2px solid #fff;
}

.chat-info {
  flex: 1;
  min-width: 0;
}

.chat-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 4px;
}

.chat-name {
  font-size: 15px;
  font-weight: 500;
  color: #333;
}

.chat-time {
  font-size: 12px;
  color: #909399;
  flex-shrink: 0;
}

.chat-preview {
  font-size: 13px;
  color: #666;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.no-message {
  color: #c0c4cc;
}

.chat-house {
  display: flex;
  align-items: center;
  gap: 4px;
  margin-top: 4px;
  font-size: 12px;
  color: #909399;
}

.unread-badge {
  flex-shrink: 0;
  min-width: 20px;
  height: 20px;
  padding: 0 6px;
  border-radius: 10px;
  background-color: #f56c6c;
  color: #fff;
  font-size: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.pagination-section {
  margin-top: 20px;
  display: flex;
  justify-content: center;
}
</style>