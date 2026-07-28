<template>
  <div class="page-home">
    <!-- 轮播图区域 -->
    <section class="banner-section">
      <el-carousel height="400px" :interval="5000" arrow="hover">
        <el-carousel-item v-for="(banner, index) in banners" :key="index">
          <div class="banner-item" :style="{ background: banner.bg, backgroundImage: 'url(' + banner.image + ')' }">
            <div class="banner-content">
              <h2 class="banner-title">{{ banner.title }}</h2>
              <p class="banner-desc">{{ banner.desc }}</p>
              <el-button type="primary" size="large" @click="goToHouses">
                立即查看
              </el-button>
            </div>
          </div>
        </el-carousel-item>
      </el-carousel>
    </section>

    <!-- 功能介绍区域 -->
    <section class="features-section">
      <h3 class="section-title">平台优势</h3>
      <el-row :gutter="24">
        <el-col v-for="(feature, index) in features" :key="index" :span="8">
          <div class="feature-card">
            <el-icon :size="48" :color="feature.color">
              <component :is="feature.icon" />
            </el-icon>
            <h4>{{ feature.title }}</h4>
            <p>{{ feature.desc }}</p>
          </div>
        </el-col>
      </el-row>
    </section>

    <!-- 热门房源区域 -->
    <section class="hot-houses-section">
      <h3 class="section-title">热门房源</h3>
      <el-row :gutter="20">
        <el-col v-for="house in hotHouses" :key="house.id" :span="6">
          <div class="house-card" @click="goToDetail(house.id)">
            <div class="house-image">
              <el-image :src="house.image" fit="cover">
                <template #error>
                  <div class="image-error">
                    <el-icon><Picture /></el-icon>
                  </div>
                </template>
              </el-image>
              <div class="house-price">¥{{ house.price }}/月</div>
            </div>
            <div class="house-info">
              <h4 class="house-title">{{ house.title }}</h4>
              <div class="house-meta">
                <span>{{ house.rooms }}</span>
                <span>{{ house.area }}m²</span>
              </div>
              <div class="house-location">
                <el-icon><Location /></el-icon>
                {{ house.region_name }}
              </div>
            </div>
          </div>
        </el-col>
      </el-row>
      <div class="more-btn">
        <el-button type="primary" plain @click="goToHouses">查看更多房源</el-button>
      </div>
    </section>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { House, Location, Picture, Search, User, Star } from '@element-plus/icons-vue'
import { getHouseListApi } from '@/api/house'

const router = useRouter()

// 轮播图数据
const banners = [
  {
    title: '海量房源任你选',
    desc: '覆盖全国多个城市的优质房源，总有一套适合你',
    bg: 'linear-gradient(135deg, rgba(102, 126, 234, 0.7) 0%, rgba(118, 75, 162, 0.7) 100%)',
    image: 'https://picsum.photos/1200/400?random=1'
  },
  {
    title: '房东直租无中介',
    desc: '房东直接发布，省去中间环节，租房更省钱',
    bg: 'linear-gradient(135deg, rgba(240, 147, 251, 0.7) 0%, rgba(245, 87, 108, 0.7) 100%)',
    image: 'https://picsum.photos/1200/400?random=2'
  },
  {
    title: '安全可靠有保障',
    desc: '实名认证，房源审核，让租房更安心',
    bg: 'linear-gradient(135deg, rgba(79, 172, 254, 0.7) 0%, rgba(0, 242, 254, 0.7) 100%)',
    image: 'https://picsum.photos/1200/400?random=3'
  }
]

// 功能特点
const features = [
  {
    icon: 'Search',
    title: '智能搜索',
    desc: '支持多条件筛选，快速找到心仪房源',
    color: '#409eff'
  },
  {
    icon: 'House',
    title: '海量房源',
    desc: '覆盖全国多个城市，房源信息实时更新',
    color: '#67c23a'
  },
  {
    icon: 'User',
    title: '房东直租',
    desc: '房东直接发布，无中间商赚差价',
    color: '#e6a23c'
  }
]

// 热门房源
const hotHouses = ref([])

// 加载热门房源
async function loadHotHouses() {
  try {
    const res = await getHouseListApi({ page_size: 4 })
    hotHouses.value = res.results || []
  } catch (e) {
    console.error('加载热门房源失败:', e)
  }
}

function goToHouses() {
  router.push('/houses')
}

function goToDetail(id) {
  router.push(`/houses/${id}`)
}

onMounted(() => {
  loadHotHouses()
})
</script>

<style scoped>
.page-home {
  max-width: 1200px;
  margin: 0 auto;
}

/* 轮播图样式 */
.banner-section {
  margin-bottom: 40px;
  border-radius: 12px;
  overflow: hidden;
}

.banner-item {
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  background-size: cover;
  background-position: center;
  background-repeat: no-repeat;
}

.banner-content {
  text-align: center;
  color: #fff;
}

.banner-title {
  font-size: 36px;
  font-weight: 700;
  margin-bottom: 16px;
  text-shadow: 2px 2px 4px rgba(0, 0, 0, 0.3);
}

.banner-desc {
  font-size: 18px;
  margin-bottom: 24px;
  opacity: 0.9;
}

/* 功能介绍样式 */
.features-section {
  margin-bottom: 40px;
}

.section-title {
  font-size: 24px;
  font-weight: 600;
  color: #303133;
  text-align: center;
  margin-bottom: 32px;
}

.feature-card {
  background: #fff;
  border-radius: 12px;
  padding: 32px 24px;
  text-align: center;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.08);
  transition: transform 0.3s;
}

.feature-card:hover {
  transform: translateY(-8px);
}

.feature-card h4 {
  font-size: 18px;
  color: #303133;
  margin: 16px 0 8px;
}

.feature-card p {
  font-size: 14px;
  color: #909399;
}

/* 热门房源样式 */
.hot-houses-section {
  margin-bottom: 40px;
}

.house-card {
  background: #fff;
  border-radius: 12px;
  overflow: hidden;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.08);
  cursor: pointer;
  transition: transform 0.3s, box-shadow 0.3s;
  margin-bottom: 20px;
}

.house-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.12);
}

.house-image {
  position: relative;
  height: 200px;
  overflow: hidden;
}

.house-image .el-image {
  width: 100%;
  height: 100%;
}

.house-price {
  position: absolute;
  bottom: 12px;
  left: 12px;
  background: rgba(0, 0, 0, 0.7);
  color: #f56c6c;
  padding: 4px 12px;
  border-radius: 6px;
  font-size: 18px;
  font-weight: 700;
}

.house-info {
  padding: 16px;
}

.house-title {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 8px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.house-meta {
  font-size: 13px;
  color: #909399;
  margin-bottom: 8px;
}

.house-meta span {
  margin-right: 12px;
}

.house-location {
  font-size: 13px;
  color: #909399;
  display: flex;
  align-items: center;
  gap: 4px;
}

.house-location .el-icon {
  color: #409eff;
}

.image-error {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
  background: #f5f7fa;
  color: #909399;
}

.more-btn {
  text-align: center;
  margin-top: 20px;
}
</style>