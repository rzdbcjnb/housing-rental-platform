<template>
  <div class="page-favorites">
    <div class="page-header">
      <h2 class="page-title">我的收藏</h2>
      <div class="page-stats">
        共收藏 <strong>{{ total }}</strong> 套房源
      </div>
    </div>

    <div v-loading="loading" class="favorites-grid">
      <template v-if="favoriteList.length > 0">
        <div v-for="favorite in favoriteList" :key="favorite.id" class="favorite-card">
          <div class="card-cover" @click="goToDetail(favorite.house.id)">
            <img :src="favorite.house.image || 'https://via.placeholder.com/300x200?text=暂无图片'" :alt="favorite.house.title" />
            <div class="card-price">
              <span class="price-value">{{ favorite.house.price }}</span>
              <span class="price-unit">元/月</span>
            </div>
          </div>
          <div class="card-body" @click="goToDetail(favorite.house.id)">
            <div class="card-title">{{ favorite.house.title }}</div>
            <div class="card-meta">
              <span>{{ favorite.house.rooms }}</span>
              <span class="meta-divider">·</span>
              <span>{{ favorite.house.area }}m²</span>
            </div>
            <div class="card-location">
              <el-icon><Location /></el-icon>
              <span>{{ favorite.house.region_name }}</span>
            </div>
            <div class="card-time">
              收藏于 {{ formatTime(favorite.create_time) }}
            </div>
          </div>
          <div class="card-actions">
            <el-button
              type="danger"
              :icon="Delete"
              circle
              size="small"
              @click="removeFavorite(favorite)"
            />
          </div>
        </div>
      </template>
      <el-empty v-else-if="!loading" description="暂无收藏房源">
        <el-button type="primary" @click="goToList">去收藏房源</el-button>
      </el-empty>
    </div>

    <section v-if="total > 0" class="pagination-section">
      <el-pagination
        v-model:current-page="currentPage"
        v-model:page-size="pageSize"
        :total="total"
        :page-sizes="[5, 10, 15, 20, 25]"
        layout="total, sizes, prev, pager, next, jumper"
        @size-change="fetchFavorites"
        @current-change="fetchFavorites"
      />
    </section>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Location, Delete } from '@element-plus/icons-vue'
import { getFavoritesApi, removeFavoriteApi } from '@/api/house'

const router = useRouter()
const loading = ref(false)
const favoriteList = ref([])
const total = ref(0)
const currentPage = ref(1)
const pageSize = ref(10)

// 格式化时间
function formatTime(timeStr) {
  if (!timeStr) return ''
  const date = new Date(timeStr)
  const now = new Date()
  const diff = now - date
  
  // 小于1分钟
  if (diff < 60000) {
    return '刚刚'
  }
  // 小于1小时
  if (diff < 3600000) {
    return `${Math.floor(diff / 60000)}分钟前`
  }
  // 小于1天
  if (diff < 86400000) {
    return `${Math.floor(diff / 3600000)}小时前`
  }
  // 小于7天
  if (diff < 604800000) {
    return `${Math.floor(diff / 86400000)}天前`
  }
  // 超过7天显示具体日期
  return date.toLocaleDateString('zh-CN')
}

// 获取收藏列表
async function fetchFavorites() {
  loading.value = true
  try {
    const res = await getFavoritesApi({
      page: currentPage.value,
      page_size: pageSize.value
    })
    favoriteList.value = res.results || []
    total.value = res.count || 0
  } catch (e) {
    ElMessage.error('获取收藏列表失败')
  } finally {
    loading.value = false
  }
}

// 取消收藏
async function removeFavorite(favorite) {
  try {
    await ElMessageBox.confirm(
      `确定要取消收藏 "${favorite.house.title}" 吗？`,
      '取消收藏确认',
      {
        confirmButtonText: '确定取消',
        cancelButtonText: '再想想',
        type: 'warning'
      }
    )
    
    await removeFavoriteApi(favorite.id)
    ElMessage.success('取消收藏成功')
    
    // 重新加载列表
    await fetchFavorites()
  } catch (e) {
    if (e !== 'cancel') {
      ElMessage.error(e.response?.data?.detail || '取消收藏失败')
    }
  }
}

// 跳转详情
function goToDetail(id) {
  router.push(`/houses/${id}`)
}

// 去房源列表
function goToList() {
  router.push('/houses')
}

onMounted(() => {
  fetchFavorites()
})
</script>

<style scoped>
.page-favorites {
  max-width: 1200px;
  margin: 0 auto;
  padding: 20px;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24px;
}

.page-title {
  font-size: 24px;
  font-weight: 600;
  color: #303133;
  margin: 0;
}

.page-stats {
  font-size: 14px;
  color: #909399;
}

.page-stats strong {
  color: #409eff;
}

.favorites-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 20px;
  min-height: 200px;
}

.favorite-card {
  background: #fff;
  border-radius: 12px;
  overflow: hidden;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.06);
  transition: transform 0.3s, box-shadow 0.3s;
  position: relative;
}

.favorite-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.12);
}

.card-cover {
  position: relative;
  height: 200px;
  overflow: hidden;
  cursor: pointer;
}

.card-cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  transition: transform 0.3s;
}

.favorite-card:hover .card-cover img {
  transform: scale(1.05);
}

.card-price {
  position: absolute;
  bottom: 12px;
  left: 12px;
  background: rgba(0, 0, 0, 0.7);
  color: #fff;
  padding: 4px 12px;
  border-radius: 6px;
}

.price-value {
  font-size: 20px;
  font-weight: 700;
  color: #f56c6c;
}

.price-unit {
  font-size: 12px;
  margin-left: 2px;
}

.card-body {
  padding: 16px;
  cursor: pointer;
}

.card-title {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 8px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.card-meta {
  font-size: 13px;
  color: #909399;
  margin-bottom: 8px;
}

.meta-divider {
  margin: 0 6px;
}

.card-location {
  font-size: 13px;
  color: #909399;
  display: flex;
  align-items: center;
  gap: 4px;
  margin-bottom: 8px;
}

.card-location .el-icon {
  color: #409eff;
}

.card-time {
  font-size: 12px;
  color: #c0c4cc;
}

.card-actions {
  position: absolute;
  top: 12px;
  right: 12px;
}

.pagination-section {
  display: flex;
  justify-content: center;
  margin-top: 24px;
}
</style>
