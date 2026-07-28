<template>
  <div class="page-user-center">
    <div class="user-center-header">
      <div class="user-avatar-section">
        <el-avatar :size="72" :src="userInfo?.avatar" class="user-avatar">
          <el-icon :size="36"><User /></el-icon>
        </el-avatar>
        <div class="user-meta">
          <h1 class="user-name">{{ userInfo?.username || '未登录' }}</h1>
          <el-tag :type="roleTagType" effect="plain" size="small">{{ roleLabel }}</el-tag>
        </div>
      </div>
    </div>

    <el-card class="user-center-card" shadow="never">
      <el-tabs v-model="activeTab" class="user-tabs">
        <el-tab-pane label="个人信息" name="info">
          <div class="tab-content">
            <h3>基本信息</h3>
            <el-descriptions :column="1" border>
              <el-descriptions-item label="用户名">{{ userInfo?.username || '-' }}</el-descriptions-item>
              <el-descriptions-item label="手机号">{{ userInfo?.phone || '-' }}</el-descriptions-item>
              <el-descriptions-item label="角色">
                <el-tag :type="roleTagType" effect="plain" size="small">{{ roleLabel }}</el-tag>
              </el-descriptions-item>
            </el-descriptions>
          </div>
        </el-tab-pane>

        <el-tab-pane v-if="isLandlord || isAdmin" label="我的房源" name="houses">
          <div class="tab-content">
            <div class="section-header">
              <h3>我发布的房源</h3>
              <el-button type="primary" @click="$router.push('/house/publish')">
                <el-icon><Plus /></el-icon>
                发布新房源
              </el-button>
            </div>

            <el-table v-loading="housesLoading" :data="myHouses" stripe border>
              <el-table-column prop="id" label="ID" width="60" />
              <el-table-column prop="title" label="标题" min-width="200" show-overflow-tooltip />
              <el-table-column prop="price" label="价格" width="100">
                <template #default="{ row }">¥{{ row.price }}/月</template>
              </el-table-column>
              <el-table-column prop="rooms" label="户型" width="120" />
              <el-table-column prop="status" label="状态" width="100">
                <template #default="{ row }">
                  <el-tag :type="statusType[row.status]">{{ statusMap[row.status] }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="操作" width="200" fixed="right">
                <template #default="{ row }">
                  <el-button type="primary" text size="small" @click="$router.push('/houses/' + row.id)">查看</el-button>
                  <el-button type="warning" text size="small" @click="$router.push('/house/edit/' + row.id)">编辑</el-button>
                  <el-button type="danger" text size="small" @click="deleteHouse(row)">删除</el-button>
                </template>
              </el-table-column>
            </el-table>
          </div>
        </el-tab-pane>
      </el-tabs>
    </el-card>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { User, Plus } from '@element-plus/icons-vue'
import { useUserStore } from '@/store/user'
import { getMyHousesApi } from '@/api/house'
import request from '@/api'

const userStore = useUserStore()
const activeTab = ref('info')

const userInfo = computed(() => userStore.userInfo)
const isAdmin = computed(() => userStore.isAdmin)
const isLandlord = computed(() => userStore.isLandlord)

const roleLabel = computed(() => {
  const map = { admin: '管理员', landlord: '房东', tenant: '租客' }
  return map[userInfo.value?.role] || '普通用户'
})

const roleTagType = computed(() => {
  const map = { admin: 'danger', landlord: 'warning', tenant: '' }
  return map[userInfo.value?.role] || 'info'
})

const statusMap = { pending: '待审核', approved: '已通过', rejected: '已拒绝', offline: '已下架' }
const statusType = { pending: 'warning', approved: 'success', rejected: 'danger', offline: 'info' }

const myHouses = ref([])
const housesLoading = ref(false)

async function loadMyHouses() {
  if (!isLandlord.value && !isAdmin.value) return
  
  housesLoading.value = true
  try {
    const res = await getMyHousesApi()
    myHouses.value = res.results || res || []
  } catch (e) {
    ElMessage.error('加载房源失败')
  } finally {
    housesLoading.value = false
  }
}

async function deleteHouse(house) {
  try {
    await ElMessageBox.confirm(`确定删除房源"${house.title}"吗？`, '提示', { type: 'warning' })
    await request({ url: `/houses/${house.id}/`, method: 'delete' })
    ElMessage.success('删除成功')
    loadMyHouses()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error(e.response?.data?.detail || '删除失败')
  }
}

onMounted(() => {
  loadMyHouses()
})
</script>

<style scoped>
.page-user-center { max-width: 1000px; margin: 0 auto; padding: 20px; }
.user-center-header { background: #fff; border-radius: 12px; padding: 24px; margin-bottom: 20px; box-shadow: 0 2px 12px rgba(0,0,0,0.06); }
.user-avatar-section { display: flex; align-items: center; gap: 20px; }
.user-meta { flex: 1; }
.user-name { margin: 0 0 8px; font-size: 24px; color: #303133; }
.user-center-card { border-radius: 12px; }
.tab-content { padding: 20px 0; }
.tab-content h3 { margin: 0 0 20px; font-size: 18px; color: #303133; }
.section-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px; }
.section-header h3 { margin: 0; }
</style>