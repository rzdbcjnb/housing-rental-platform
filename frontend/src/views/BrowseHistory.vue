<template>
  <div class="page-browse-history">
    <div class="page-header">
      <h2 class="page-title">浏览历史</h2>
      <div class="page-actions">
        <span class="page-stats">共 <strong>{{ total }}</strong> 条记录</span>
        <el-button 
          v-if="historyList.length > 0" 
          type="danger" 
          plain 
          size="small" 
          @click="handleClearAll"
        >
          清空历史
        </el-button>
      </div>
    </div>

    <div v-loading="loading" class="history-grid">
      <template v-if="historyList.length > 0">
        <div v-for="history in historyList" :key="history.id" class="history-card">
          <div class="card-cover" @click="goToDetail(history.house.id)">
            <img :src="history.house.image || 'https://via.placeholder.com/300x200?text=暂无图片'" :alt="history.house.title" />
            <div class="card-price">
              <span class="price-value">{{ history.house.price }}</span>
              <span class="price-unit">元/月</span>
            </div>
          </div>
          <div class="card-body" @click="goToDetail(history.house.id)">
            <div class="card-title">{{ history.house.title }}</div>
            <div class="card-meta">
              <span>{{ history.house.rooms }}</span>
              <span class="meta-divider">·</span>
              <span>{{ history.house.area }}m²</span>
            </div>
            <div class="card-location">
              <el-icon><Location /></el-icon>
              <span>{{ history.house.region_name }}</span>
            </div>
            <div class="card-time">
              浏览于 {{ formatTime(history.create_time) }}
            </div>
          </div>
          <div class="card-actions">
            <el-button
              type="danger"
              :icon="Delete"
              circle
              size="small"
              @click="handleDelete(history)"
            />
          </div>
        </div>
      </template>
      <el-empty v-else-if="!loading" description="暂无浏览历史">
        <el-button type="primary" @click="goToList">去浏览房源</el-button>
      </el-empty>
    </div>

    <section v-if="total > 0" class="pagination-section">
      <el-pagination
        v-model:current-page="currentPage"
        v-model:page-size="pageSize"
        :total="total"
        :page-sizes="[5, 10, 15, 20, 25]"
        layout="total, sizes, prev, pager, next, jumper"
        @size-change="fetchHistory"
        @current-change="fetchHistory"
      />
    </section>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Location, Delete } from '@element-plus/icons-vue'
import { getBrowseHistoryApi, deleteBrowseHistoryApi, clearBrowseHistoryApi } from '@/api/house'

const router = useRouter()
const loading = ref(false)
const historyList = ref([])
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

// 获取浏览历史列表
async function fetchHistory() {
  loading.value = true
  try {
    const res = await getBrowseHistoryApi({
      page: currentPage.value,
      page_size: pageSize.value
    })
    historyList.value = res.results || []
    total.value = res.count || 0
  } catch (e) {
    ElMessage.error('获取浏览历史失败')
  } finally {
    loading.value = false
  }
}

// 删除单条历史
async function handleDelete(history) {
  try {
    await ElMessageBox.confirm(
      `确定要删除 "${history.house.title}" 的浏览记录吗？`,
      '删除确认',
      {
        confirmButtonText: '确定删除',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )
    
    await deleteBrowseHistoryApi(history.id)
    ElMessage.success('删除成功')
    
    // 重新加载列表
    await fetchHistory()
  } catch (e) {
    if (e !== 'cancel') {
      ElMessage.error(e.response?.data?.detail || '删除失败')
    }
  }
}

// 清空所有历史
async function handleClearAll() {
  try {
    await ElMessageBox.confirm(
      '确定要清空所有浏览历史吗？此操作不可恢复。',
      '清空确认',
      {
        confirmButtonText: '确定清空',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )
    
    const res = await clearBrowseHistoryApi()
    ElMessage.success(res.detail || '清空成功')
    
    // 重新加载列表
    await fetchHistory()
  } catch (e) {
    if (e !== 'cancel') {
      ElMessage.error(e.response?.data?.detail || '清空失败')
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
  fetchHistory()
})
</script>

<style scoped>
.page-browse-history {
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

.page-actions {
  display: flex;
  align-items: center;
  gap: 16px;
}

.page-stats {
  font-size: 14px;
  color: #909399;
}

.page-stats strong {
  color: #409eff;
}

.history-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 20px;
  min-height: 200px;
}

.history-card {
  background: #fff;
  border-radius: 12px;
  overflow: hidden;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.06);
  transition: transform 0.3s, box-shadow 0.3s;
  position: relative;
}

.history-card:hover {
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

.history-card:hover .card-cover img {
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
