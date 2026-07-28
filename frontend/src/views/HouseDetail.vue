<template>
  <div class="page-house-detail">
    <div v-if="loading" class="loading-wrapper">
      <el-skeleton :rows="10" animated />
    </div>

    <template v-else-if="house">
      <div class="detail-header">
        <el-button :icon="ArrowLeft" @click="goBack">返回列表</el-button>
        <div class="header-actions">
          <el-button
            v-if="userStore.isLoggedIn"
            :type="isFavorited ? 'danger' : 'default'"
            :icon="isFavorited ? StarFilled : Star"
            @click="toggleFavorite"
          >
            {{ isFavorited ? '取消收藏' : '收藏' }}
          </el-button>
          <el-button
            v-if="userStore.isLoggedIn"
            type="success"
            :icon="Share"
            @click="openShareDialog"
          >
            分享
          </el-button>
          <el-button
            v-if="userStore.isLoggedIn"
            type="warning"
            :icon="ChatDotRound"
            @click="askAI"
          >
            问问AI
          </el-button>
          <el-button v-if="isOwner" type="primary" :icon="Edit" @click="goEdit">
            编辑房源
          </el-button>
        </div>
      </div>

      <div class="detail-section image-section">
        <el-image
          :src="house.image"
          fit="cover"
          class="house-cover"
          :preview-src-list="[house.image]"
          preview-teleported
        >
          <template #error>
            <div class="image-error">
              <el-icon><Picture /></el-icon>
              <span>暂无图片</span>
            </div>
          </template>
        </el-image>
      </div>

      <div class="detail-section">
        <h2 class="house-title">{{ house.title }}</h2>
        <div class="price-tag">
          <span class="price-amount">¥{{ house.price }}</span>
          <span class="price-unit">/月</span>
        </div>
        <div class="info-tags">
          <el-tag type="primary" size="large">{{ house.rooms }}</el-tag>
          <el-tag type="success" size="large">{{ house.area }}m²</el-tag>
          <el-tag type="warning" size="large">{{ houseTypeText }}</el-tag>
        </div>
      </div>

      <div class="detail-section">
        <h3 class="section-title">
          <el-icon><Location /></el-icon>
          位置信息
        </h3>
        <div class="location-info">
          <p class="location-region">{{ house.region_name }}</p>
          <p class="location-address">{{ house.address_detail }}</p>
        </div>
      </div>

      <div class="detail-section">
        <h3 class="section-title">
          <el-icon><Document /></el-icon>
          房源描述
        </h3>
        <p class="house-description">{{ house.description || '暂无描述' }}</p>
      </div>

      <div class="detail-section">
        <h3 class="section-title">
          <el-icon><User /></el-icon>
          房东信息
        </h3>
        <div class="landlord-info">
          <el-avatar :size="64" :src="house.landlord_info?.avatar">
            {{ house.landlord_info?.username?.charAt(0) }}
          </el-avatar>
          <div class="landlord-detail">
            <p class="landlord-name">{{ house.landlord_info?.username }}</p>
            <p class="landlord-phone">
              <el-icon><Phone /></el-icon>
              {{ house.landlord_info?.phone || '暂无联系方式' }}
            </p>
            <el-button 
              v-if="userStore.isLoggedIn && house.landlord_info?.id !== userStore.userInfo?.id"
              type="primary" 
              :icon="ChatDotRound"
              @click="startChat"
            >
              与房东沟通
            </el-button>

          </div>
        </div>
      </div>

      <div class="detail-section recommend-section">
        <h3 class="section-title">
          <el-icon><Star /></el-icon>
          相似房源推荐
        </h3>
        <div v-if="recommendLoading" class="recommend-loading">
          <el-skeleton :rows="3" animated />
        </div>
        <div v-else-if="recommendedHouses.length > 0" class="recommend-list">
          <el-row :gutter="16">
            <el-col v-for="item in recommendedHouses" :key="item.id" :span="6">
              <div class="recommend-card" @click="goToDetail(item.id)">
                <el-image :src="item.image" fit="cover" class="recommend-image">
                  <template #error>
                    <div class="image-error">
                      <el-icon><Picture /></el-icon>
                    </div>
                  </template>
                </el-image>
                <div class="recommend-info">
                  <p class="recommend-title">{{ item.title }}</p>
                  <div class="recommend-meta">
                    <span class="recommend-price">¥{{ item.price }}/月</span>
                    <span class="recommend-rooms">{{ item.rooms }}</span>
                    <span class="recommend-area">{{ item.area }}m²</span>
                  </div>
                  <p class="recommend-region">{{ item.region_name }}</p>
                </div>
              </div>
            </el-col>
          </el-row>
        </div>
        <div v-else class="recommend-empty">
          <el-empty description="暂无相似房源推荐" :image-size="80" />
        </div>
      </div>
    <!-- 分享对话框 -->
    <el-dialog v-model="shareDialogVisible" title="分享给好友" width="400px">
      <div v-if="chatRooms.length === 0" class="share-empty">
        <el-empty description="暂无聊天记录，请先与好友聊天" :image-size="60" />
      </div>
      <div v-else class="share-room-list">
        <div 
          v-for="room in chatRooms" 
          :key="room.id" 
          class="share-room-item"
          @click="shareToRoom(room)"
        >
          <el-avatar :size="40">
            {{ room.other_user?.username?.charAt(0) || '?' }}
          </el-avatar>
          <div class="share-room-info">
            <span class="share-room-name">{{ room.other_user?.username || '未知用户' }}</span>
            <span v-if="room.last_message" class="share-room-last">{{ room.last_message.content?.substring(0, 20) }}</span>
          </div>
        </div>
      </div>
    </el-dialog>
    </template>

    <div v-else class="error-wrapper">
      <el-empty description="房源信息加载失败" />
      <el-button type="primary" @click="fetchHouseDetail">重新加载</el-button>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  ArrowLeft,
  Edit,
  Picture,
  Location,
  Document,
  User,
  Phone,
  Star,
  StarFilled,
  ChatDotRound,
  Share
} from '@element-plus/icons-vue'
import { getHouseDetailApi, getHouseRecommendApi, addFavoriteApi, removeFavoriteApi, checkFavoriteApi, addBrowseHistoryApi, recordHouseClickApi } from '@/api/house'
import { createChatRoomApi, getChatRoomsApi, sendHouseShareMessageApi } from '@/api/chat'
import { useUserStore } from '@/store/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const house = ref(null)
const loading = ref(true)
const recommendedHouses = ref([])
const recommendLoading = ref(false)
const isFavorited = ref(false)
const shareDialogVisible = ref(false)
const chatRooms = ref([])
const favoriteId = ref(null)

const houseTypeText = computed(() => {
  const typeMap = { whole: '整租', share: '合租' }
  return typeMap[house.value?.house_type] || house.value?.house_type || '未知'
})

const isOwner = computed(() => {
  if (!userStore.isLoggedIn || !house.value) return false
  return userStore.isAdmin || userStore.userInfo?.id === house.value.landlord_info?.id
})

async function fetchHouseDetail() {
  const id = route.params.id
  if (!id) {
    ElMessage.error('房源ID不存在')
    loading.value = false
    return
  }

  loading.value = true
  try {
    const res = await getHouseDetailApi(id)
    house.value = res.data || res
    fetchRecommendations(id)
    // 记录点击
    try {
      await recordHouseClickApi(id)
    } catch (e) {
      // 静默失败
    }
    // 只有登录用户才检查收藏状态和记录浏览历史
    if (userStore.isLoggedIn) {
      checkFavoriteStatus(id)
      recordBrowseHistory(id)
    }
  } catch (error) {
    // 静默失败
    house.value = null
  } finally {
    loading.value = false
  }
}

async function recordBrowseHistory(houseId) {
  try {
    await addBrowseHistoryApi(houseId)
  } catch (e) {
    // 静默失败，不影响用户体验
    // 静默失败
  }
}

async function checkFavoriteStatus(houseId) {
  try {
    const res = await checkFavoriteApi(houseId)
    isFavorited.value = res.is_favorited
    favoriteId.value = res.favorite_id
  } catch (e) {
    isFavorited.value = false
    favoriteId.value = null
  }
}

async function toggleFavorite() {
  if (!userStore.isLoggedIn) {
    ElMessage.warning('请先登录')
    router.push('/login')
    return
  }
  
  try {
    if (isFavorited.value) {
      await removeFavoriteApi(favoriteId.value)
      isFavorited.value = false
      favoriteId.value = null
      ElMessage.success('取消收藏成功')
    } else {
      const res = await addFavoriteApi(house.value.id)
      isFavorited.value = true
      favoriteId.value = res.id
      ElMessage.success('收藏成功')
    }
  } catch (e) {
    ElMessage.error(e.response?.data?.detail || '操作失败')
  }
}

async function startChat() {
  if (!userStore.isLoggedIn) {
    ElMessage.warning('请先登录')
    router.push('/login')
    return
  }
  
  try {
    const res = await createChatRoomApi({ user_id: house.value.landlord, house_id: house.value.id })
    router.push(`/chat/${res.id}`)
  } catch (e) {
    ElMessage.error('创建聊天失败')
  }
}

async function openShareDialog() {
  if (!userStore.isLoggedIn) {
    ElMessage.warning('请先登录')
    router.push('/login')
    return
  }
  
  try {
    const res = await getChatRoomsApi()
    chatRooms.value = res.results || []
    shareDialogVisible.value = true
  } catch (e) {
    ElMessage.error('获取聊天列表失败')
  }
}

async function shareToRoom(room) {
  try {
    await sendHouseShareMessageApi(room.id, house.value.id)
    ElMessage.success('分享成功')
    shareDialogVisible.value = false
    router.push('/chat')
  } catch (e) {
    ElMessage.error('分享失败')
  }
}

async function fetchRecommendations(id) {
  recommendLoading.value = true
  try {
    const res = await getHouseRecommendApi(id)
    recommendedHouses.value = res || []
  } catch (error) {
    // 静默失败
    recommendedHouses.value = []
  } finally {
    recommendLoading.value = false
  }
}

function goBack() {
  router.push('/houses')
}

function goEdit() {
  router.push(`/house/edit/${route.params.id}`)
}

function askAI() {
  if (!userStore.isLoggedIn) {
    ElMessage.warning('请先登录')
    router.push('/login')
    return
  }
  
  // 只传递房源编号，由 AI 客服按权限读取可信详情；进入页面后等待用户提出问题。
  router.push({
    path: '/ai-chat',
    query: { houseId: String(house.value.id) }
  })
}

function goToDetail(id) {
  router.push(`/houses/${id}`)
}

watch(() => route.params.id, (newId) => {
  if (newId) fetchHouseDetail()
})

onMounted(() => {
  fetchHouseDetail()
})
</script>

<style scoped>
.page-house-detail {
  max-width: 960px;
  margin: 0 auto;
  padding: 20px;
}

.loading-wrapper {
  padding: 40px;
  background: #fff;
  border-radius: 8px;
}

.detail-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.header-actions {
  display: flex;
  gap: 12px;
}

.detail-section {
  background: #fff;
  border-radius: 8px;
  padding: 24px;
  margin-bottom: 20px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
}

.image-section {
  padding: 0;
  overflow: hidden;
}

.house-cover {
  width: 100%;
  height: 400px;
  display: block;
}

.image-error {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: #909399;
  font-size: 14px;
  background: #f5f7fa;
}

.image-error .el-icon {
  font-size: 48px;
  margin-bottom: 8px;
}

.house-title {
  font-size: 24px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 16px;
}

.price-tag {
  margin-bottom: 16px;
}

.price-amount {
  font-size: 32px;
  font-weight: 700;
  color: #f56c6c;
}

.price-unit {
  font-size: 14px;
  color: #909399;
  margin-left: 4px;
}

.info-tags {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}

.section-title {
  font-size: 18px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 16px;
  display: flex;
  align-items: center;
  gap: 8px;
}

.section-title .el-icon {
  color: #409eff;
}

.location-region {
  font-size: 16px;
  color: #606266;
  margin-bottom: 8px;
}

.location-address {
  font-size: 14px;
  color: #909399;
}

.house-description {
  font-size: 15px;
  color: #606266;
  line-height: 1.6;
}

.landlord-info {
  display: flex;
  align-items: center;
  gap: 16px;
}

.landlord-detail {
  flex: 1;
}

.landlord-name {
  font-size: 18px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 8px;
}

.landlord-phone {
  font-size: 14px;
  color: #606266;
  display: flex;
  align-items: center;
  gap: 4px;
}

.landlord-phone .el-icon {
  color: #409eff;
}

.recommend-section {
  background: #f5f7fa;
}

.recommend-list {
  margin-top: 16px;
}

.recommend-card {
  background: #fff;
  border-radius: 8px;
  overflow: hidden;
  cursor: pointer;
  transition: transform 0.3s, box-shadow 0.3s;
  margin-bottom: 16px;
}

.recommend-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
}

.recommend-image {
  width: 100%;
  height: 150px;
  display: block;
}

.recommend-info {
  padding: 12px;
}

.recommend-title {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 8px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.recommend-meta {
  font-size: 12px;
  color: #909399;
  margin-bottom: 4px;
}

.recommend-price {
  color: #f56c6c;
  font-weight: 600;
  margin-right: 8px;
}

.recommend-rooms,
.recommend-area {
  margin-right: 8px;
}

.recommend-region {
  font-size: 12px;
  color: #909399;
}

.recommend-empty {
  padding: 40px 0;
}

.error-wrapper {
  text-align: center;
  padding: 40px;
  background: #fff;
  border-radius: 8px;
}

.error-wrapper .el-button {
  margin-top: 16px;
}
.share-empty {
  padding: 20px 0;
}

.share-room-list {
  max-height: 400px;
  overflow-y: auto;
}

.share-room-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px;
  cursor: pointer;
  border-radius: 8px;
  transition: background-color 0.2s;
}

.share-room-item:hover {
  background-color: #f5f7fa;
}

.share-room-info {
  display: flex;
  flex-direction: column;
}

.share-room-name {
  font-size: 14px;
  font-weight: 500;
  color: #333;
}

.share-room-last {
  font-size: 12px;
  color: #909399;
  margin-top: 2px;
}
</style>

