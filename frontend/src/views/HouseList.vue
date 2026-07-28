<template>
  <div class="page-house-list">
    <!-- 猜你喜欢按钮 -->
    <section v-if="userStore.isLoggedIn" class="recommend-trigger">
      <el-button 
        type="primary" 
        :icon="MagicStick" 
        :loading="recommendLoading"
        @click="toggleRecommend"
      >
        {{ showRecommend ? '隐藏推荐' : '猜你喜欢' }}
      </el-button>
      <span v-if="!showRecommend" class="trigger-hint">点击获取个性化推荐</span>
    </section>

    <!-- 猜你喜欢区域 -->
    <section v-if="showRecommend && recommendList.length > 0" class="recommend-section">
      <div class="section-header">
        <h3 class="section-title">
          <el-icon><MagicStick /></el-icon>
          猜你喜欢
        </h3>
        <span class="section-subtitle">根据您的收藏偏好推荐</span>
      </div>
      <div v-loading="recommendLoading" class="recommend-grid">
        <div v-for="house in recommendList" :key="house.id" class="house-card" @click="goToDetail(house.id)">
          <div class="card-cover">
            <img :src="house.image || 'https://via.placeholder.com/300x200?text=暂无图片'" :alt="house.title" />
            <div class="card-price">
              <span class="price-value">{{ house.price }}</span>
              <span class="price-unit">元/月</span>
            </div>
            <!-- 收藏按钮 -->
            <el-button
              class="favorite-btn"
              :type="house.isFavorited ? 'danger' : 'default'"
              :icon="house.isFavorited ? Star : StarFilled"
              circle
              size="small"
              @click.stop="toggleFavorite(house)"
            />
          </div>
          <div class="card-body">
            <div class="card-title">{{ house.title }}</div>
            <div class="card-meta">
              <span>{{ house.rooms }}</span>
              <span class="meta-divider">·</span>
              <span>{{ house.area }}m²</span>
            </div>
            <div class="card-location">
              <el-icon><Location /></el-icon>
              <span>{{ house.region_name }}</span>
            </div>
          </div>
        </div>
      </div>
    </section>

    <section class="search-section">
      <div class="search-bar">
        <el-input v-model="searchKeyword" placeholder="搜索房源标题、地区..." size="large" clearable @keyup.enter="handleSearch">
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
        <el-button type="primary" size="large" @click="handleSearch">搜索</el-button>
      </div>
    </section>

    <section class="filter-section">
      <div class="filter-header">
        <span class="filter-title">筛选条件</span>
        <el-button text type="primary" @click="showFilters = !showFilters">
          {{ showFilters ? '收起' : '展开' }}
        </el-button>
      </div>
      <div v-show="showFilters" class="filter-body">
        <div class="filter-grid">
          <div class="filter-item">
            <label>城市</label>
            <el-select v-model="filters.city" placeholder="选择城市" clearable @change="handleCityChange">
              <el-option v-for="c in cityList" :key="c.id" :label="c.name" :value="c.name" />
            </el-select>
          </div>
          <div class="filter-item">
            <label>区</label>
            <el-select v-model="filters.district" placeholder="选择区" clearable :disabled="!filters.city" @change="handleDistrictChange">
              <el-option v-for="d in districtList" :key="d.id" :label="d.name" :value="d.name" />
            </el-select>
          </div>
          <div class="filter-item">
            <label>街道</label>
            <el-select v-model="filters.street" placeholder="选择街道" clearable :disabled="!filters.district">
              <el-option v-for="s in streetList" :key="s.id" :label="s.name" :value="s.name" />
            </el-select>
          </div>
          <div class="filter-item">
            <label>价格(元)</label>
            <div class="range-inputs">
              <el-input-number v-model="filters.price_min" :min="1" :step="500" placeholder="最低" controls-position="right" size="small" />
              <span class="range-sep">-</span>
              <el-input-number v-model="filters.price_max" :min="1" :step="500" placeholder="最高" controls-position="right" size="small" />
            </div>
          </div>
          <div class="filter-item filter-item-full">
            <label>户型</label>
            <div class="rooms-input-group">
              <div class="rooms-input-item">
                <el-input-number v-model="rooms.shi" :min="0" :max="10" controls-position="right" size="small" />
                <span class="rooms-unit">室</span>
              </div>
              <div class="rooms-input-item">
                <el-input-number v-model="rooms.ting" :min="0" :max="10" controls-position="right" size="small" />
                <span class="rooms-unit">厅</span>
              </div>
              <div class="rooms-input-item">
                <el-input-number v-model="rooms.wei" :min="0" :max="10" controls-position="right" size="small" />
                <span class="rooms-unit">卫</span>
              </div>
              <div class="rooms-input-item">
                <el-input-number v-model="rooms.chu" :min="0" :max="10" controls-position="right" size="small" />
                <span class="rooms-unit">厨</span>
              </div>
            </div>
          </div>
        </div>
        <div class="filter-actions">
          <el-button type="primary" @click="handleSearch">筛选</el-button>
          <el-button @click="resetFilters">重置</el-button>
        </div>
      </div>
    </section>

    <section class="result-section">
      <div class="result-header">
        共找到 <strong>{{ total }}</strong> 套房源
      </div>
      <div v-loading="loading" class="house-grid">
        <template v-if="houseList.length > 0">
          <div v-for="house in houseList" :key="house.id" class="house-card" @click="goToDetail(house)">
            <div class="card-cover">
              <img :src="house.image || 'https://via.placeholder.com/300x200?text=暂无图片'" :alt="house.title" />
              <div class="card-price">
                <span class="price-value">{{ house.price }}</span>
                <span class="price-unit">元/月</span>
              </div>
              <!-- 收藏按钮 -->
              <el-button
                v-if="userStore.isLoggedIn"
                class="favorite-btn"
                :type="house.isFavorited ? 'danger' : 'default'"
                :icon="house.isFavorited ? Star : StarFilled"
                circle
                size="small"
                @click.stop="toggleFavorite(house)"
              />
            </div>
            <div class="card-body">
              <div class="card-title">{{ house.title }}</div>
              <div class="card-meta">
                <span>{{ house.rooms }}</span>
                <span class="meta-divider">·</span>
                <span>{{ house.area }}m²</span>
              </div>
              <div class="card-location">
                <el-icon><Location /></el-icon>
                <span>{{ house.region_name }}</span>
              </div>
            </div>
          </div>
        </template>
        <el-empty v-else-if="!loading" description="暂无房源数据" />
      </div>
    </section>

    <section v-if="total > 0" class="pagination-section">
      <el-pagination
        v-model:current-page="currentPage"
        v-model:page-size="pageSize"
        :total="total"
        :page-sizes="[5, 10, 15, 20, 25]"
        layout="total, sizes, prev, pager, next, jumper"
        @size-change="handleSearch"
        @current-change="handleSearch"
      />
    </section>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Search, Location, Star, StarFilled, MagicStick } from '@element-plus/icons-vue'
import { getHouseListApi, getAreasApi, addFavoriteApi, removeFavoriteApi, checkFavoriteApi, getUserRecommendApi } from '@/api/house'
import { useUserStore } from '@/store/user'

const router = useRouter()
const userStore = useUserStore()
const searchKeyword = ref('')
const showFilters = ref(true)
const showRecommend = ref(false)
const loading = ref(false)
const recommendLoading = ref(false)
const houseList = ref([])
const recommendList = ref([])
const total = ref(0)
const currentPage = ref(1)
const pageSize = ref(10)

const filters = reactive({ 
  city: '', 
  district: '', 
  street: '', 
  price_min: null, 
  price_max: null, 
  area_min: null, 
  area_max: null 
})

// 户型输入组
const rooms = reactive({ shi: null, ting: null, wei: null, chu: null })

// 地区数据
const cityList = ref([])
const districtList = ref([])
const streetList = ref([])

// 加载城市列表
async function loadCities() {
  try {
    const res = await getAreasApi({ level: 1 })
    cityList.value = res.results || res || []
  } catch (e) {
    console.error('加载城市失败:', e)
  }
}

// 城市变化
async function handleCityChange(cityName) {
  filters.district = ''
  filters.street = ''
  districtList.value = []
  streetList.value = []
  if (!cityName) return
  const city = cityList.value.find(c => c.name === cityName)
  if (!city) return
  try {
    const res = await getAreasApi({ level: 2, parent_id: city.id })
    districtList.value = res.results || res || []
  } catch (e) {
    console.error('加载区失败:', e)
  }
}

// 区变化
async function handleDistrictChange(districtName) {
  filters.street = ''
  streetList.value = []
  if (!districtName) return
  const district = districtList.value.find(d => d.name === districtName)
  if (!district) return
  try {
    const res = await getAreasApi({ level: 3, parent_id: district.id })
    streetList.value = res.results || res || []
  } catch (e) {
    console.error('加载街道失败:', e)
  }
}

// 重置筛选
function resetFilters() {
  searchKeyword.value = ''
  Object.assign(filters, { 
    city: '', 
    district: '', 
    street: '', 
    price_min: null, 
    price_max: null, 
    area_min: null, 
    area_max: null 
  })
  Object.assign(rooms, { shi: null, ting: null, wei: null, chu: null })
  districtList.value = []
  streetList.value = []
  currentPage.value = 1
  handleSearch()
}

// 搜索
async function handleSearch() {
  loading.value = true
  try {
    const params = { page: currentPage.value, page_size: pageSize.value }
    if (searchKeyword.value) params.keyword = searchKeyword.value
    if (filters.city) params.city = filters.city
    if (filters.district) params.district = filters.district
    if (filters.street) params.street = filters.street
    if (filters.price_min) params.price_min = filters.price_min
    if (filters.price_max) params.price_max = filters.price_max
    if (filters.area_min) params.area_min = filters.area_min
    if (filters.area_max) params.area_max = filters.area_max
    
    // 组合户型字符串（模糊搜索）
    let roomsParts = []
    if (rooms.shi !== null && rooms.shi !== undefined && rooms.shi !== '') {
      roomsParts.push(rooms.shi + '室')
    }
    if (rooms.ting !== null && rooms.ting !== undefined && rooms.ting !== '') {
      roomsParts.push(rooms.ting + '厅')
    }
    if (rooms.wei !== null && rooms.wei !== undefined && rooms.wei !== '') {
      roomsParts.push(rooms.wei + '卫')
    }
    if (rooms.chu !== null && rooms.chu !== undefined && rooms.chu !== '') {
      roomsParts.push(rooms.chu + '厨')
    }
    if (roomsParts.length > 0) {
      params.rooms = roomsParts.join('')
    }
    
    const res = await getHouseListApi(params)
    houseList.value = res.results || []
    total.value = res.count || 0
    
    // 检查每个房源的收藏状态
    if (userStore.isLoggedIn) {
      await checkFavoriteStatus()
    }
  } catch (e) {
    ElMessage.error('获取房源列表失败')
  } finally {
    loading.value = false
  }
}

// 检查收藏状态
async function checkFavoriteStatus() {
  for (const house of houseList.value) {
    try {
      const res = await checkFavoriteApi(house.id)
      house.isFavorited = res.is_favorited
      house.favoriteId = res.favorite_id
    } catch (e) {
      house.isFavorited = false
      house.favoriteId = null
    }
  }
}

// 切换收藏状态
async function toggleFavorite(house) {
  if (!userStore.isLoggedIn) {
    ElMessage.warning('请先登录')
    router.push('/login')
    return
  }
  
  try {
    if (house.isFavorited) {
      // 取消收藏
      await removeFavoriteApi(house.favoriteId)
      house.isFavorited = false
      house.favoriteId = null
      ElMessage.success('取消收藏成功')
    } else {
      // 添加收藏
      const res = await addFavoriteApi(house.id)
      house.isFavorited = true
      house.favoriteId = res.id
      ElMessage.success('收藏成功')
    }
  } catch (e) {
    ElMessage.error(e.response?.data?.detail || '操作失败')
  }
}

// 跳转详情
function goToDetail(house) {
  // 检查房源状态
  if (house.status && house.status !== 'approved') {
    ElMessage.warning('该房源暂未审核通过，无法查看详情')
    return
  }
  router.push('/houses/' + house.id)
}

// 加载推荐数据
async function loadRecommendations() {
  if (!userStore.isLoggedIn) return
  
  recommendLoading.value = true
  try {
    const res = await getUserRecommendApi(6)
    recommendList.value = res || []
    
    // 检查推荐房源的收藏状态
    if (recommendList.value.length > 0) {
      for (const house of recommendList.value) {
        try {
          const favRes = await checkFavoriteApi(house.id)
          house.isFavorited = favRes.is_favorited
          house.favoriteId = favRes.favorite_id
        } catch (e) {
          house.isFavorited = false
          house.favoriteId = null
        }
      }
    }
  } catch (e) {
    console.error('加载推荐失败:', e)
  } finally {
    recommendLoading.value = false
  }
}

// 切换推荐显示
async function toggleRecommend() {
  if (showRecommend.value) {
    showRecommend.value = false
  } else {
    showRecommend.value = true
    if (recommendList.value.length === 0) {
      await loadRecommendations()
    }
  }
}

onMounted(() => {
  loadCities()
  handleSearch()
})
</script>

<style scoped>
.page-house-list { max-width: 1200px; margin: 0 auto; padding: 20px; }

/* 推荐触发按钮样式 */
.recommend-trigger { 
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 20px;
  padding: 16px 20px;
  background: linear-gradient(135deg, #f5f7fa 0%, #c3cfe2 100%);
  border-radius: 12px;
  box-shadow: 0 2px 8px rgba(0,0,0,0.06);
}
.trigger-hint {
  font-size: 14px;
  color: #909399;
}

/* 推荐区域样式 */
.recommend-section { 
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  border-radius: 12px; 
  padding: 24px; 
  margin-bottom: 20px; 
  box-shadow: 0 4px 16px rgba(102, 126, 234, 0.3);
  animation: fadeIn 0.3s ease;
}
@keyframes fadeIn {
  from { opacity: 0; transform: translateY(-10px); }
  to { opacity: 1; transform: translateY(0); }
}
.section-header { 
  display: flex; 
  align-items: center; 
  gap: 12px; 
  margin-bottom: 20px; 
}
.section-title { 
  font-size: 20px; 
  font-weight: 600; 
  color: #fff; 
  display: flex; 
  align-items: center; 
  gap: 8px; 
  margin: 0;
}
.section-subtitle { 
  font-size: 14px; 
  color: rgba(255,255,255,0.8); 
}
.recommend-grid { 
  display: grid; 
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr)); 
  gap: 16px; 
  min-height: 200px; 
}
.recommend-grid .house-card {
  background: rgba(255,255,255,0.95);
}
.recommend-grid .house-card:hover {
  background: #fff;
}

.search-section { background: #fff; border-radius: 12px; padding: 24px; margin-bottom: 20px; box-shadow: 0 2px 12px rgba(0,0,0,0.06); }
.search-bar { display: flex; gap: 12px; }
.filter-section { background: #fff; border-radius: 12px; padding: 20px 24px; margin-bottom: 20px; box-shadow: 0 2px 12px rgba(0,0,0,0.06); }
.filter-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.filter-title { font-size: 16px; font-weight: 600; color: #303133; }
.filter-grid { 
  display: grid; 
  grid-template-columns: repeat(3, 1fr); 
  gap: 16px; 
  margin-bottom: 16px; 
}
.filter-item { 
  min-width: 0; 
}
.filter-item-full {
  grid-column: 1 / -1;
}
.filter-item label { 
  display: block; 
  font-size: 13px; 
  color: #606266; 
  margin-bottom: 6px; 
  font-weight: 500; 
}
.filter-item .el-select,
.filter-item .el-input-number {
  width: 100%;
}
.range-inputs { 
  display: flex; 
  align-items: center; 
  gap: 8px; 
}
.range-inputs .el-input-number {
  flex: 1;
}
.range-sep { 
  color: #c0c4cc; 
  flex-shrink: 0;
}
.rooms-input-group { 
  display: flex; 
  align-items: center; 
  gap: 12px; 
  flex-wrap: wrap;
}
.rooms-input-item {
  display: flex;
  align-items: center;
  gap: 4px;
}
.rooms-input-item .el-input-number {
  width: 80px;
}
.rooms-unit { 
  color: #606266; 
  font-size: 14px; 
}
.filter-actions { 
  display: flex; 
  gap: 12px; 
}
.result-header { margin-bottom: 16px; font-size: 14px; color: #909399; }
.result-header strong { color: #409eff; }
.house-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(280px, 1fr)); gap: 20px; min-height: 200px; }
.house-card { background: #fff; border-radius: 12px; overflow: hidden; box-shadow: 0 2px 12px rgba(0,0,0,0.06); cursor: pointer; transition: transform 0.3s, box-shadow 0.3s; animation: fadeInUp 0.5s ease both; }
.house-card:hover { transform: translateY(-4px); box-shadow: 0 8px 24px rgba(0,0,0,0.12); }
@keyframes fadeInUp { from { opacity: 0; transform: translateY(20px); } to { opacity: 1; transform: translateY(0); } }
.card-cover { position: relative; height: 200px; overflow: hidden; }
.card-cover img { width: 100%; height: 100%; object-fit: cover; transition: transform 0.3s; }
.house-card:hover .card-cover img { transform: scale(1.05); }
.card-price { position: absolute; bottom: 12px; left: 12px; background: rgba(0,0,0,0.7); color: #fff; padding: 4px 12px; border-radius: 6px; }
.price-value { font-size: 20px; font-weight: 700; color: #f56c6c; }
.price-unit { font-size: 12px; margin-left: 2px; }
.favorite-btn { position: absolute; top: 12px; right: 12px; background: rgba(255,255,255,0.9); border: none; }
.favorite-btn:hover { background: rgba(255,255,255,1); }
.card-body { padding: 16px; }
.card-title { font-size: 16px; font-weight: 600; color: #303133; margin-bottom: 8px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.card-meta { font-size: 13px; color: #909399; margin-bottom: 8px; }
.meta-divider { margin: 0 6px; }
.card-location { font-size: 13px; color: #909399; display: flex; align-items: center; gap: 4px; }
.card-location .el-icon { color: #409eff; }
.pagination-section { display: flex; justify-content: center; margin-top: 24px; }
</style>