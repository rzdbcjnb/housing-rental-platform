<template>
  <div class="admin-dashboard">
    <div class="admin-header">
      <h2>后台管理</h2>
    </div>

    <el-tabs v-model="activeTab" class="admin-tabs">
      <!-- 数据看板 -->
      <el-tab-pane label="数据看板" name="dashboard">
        <div v-loading="dashboardLoading" class="dashboard-container">
          <!-- 统计卡片 -->
          <div class="stats-cards">
            <el-card class="stat-card">
              <div class="stat-content">
                <div class="stat-value">{{ dashboardData.houses?.total || 0 }}</div>
                <div class="stat-label">房源总数</div>
              </div>
            </el-card>
            <el-card class="stat-card">
              <div class="stat-content">
                <div class="stat-value">{{ dashboardData.users?.total || 0 }}</div>
                <div class="stat-label">用户总数</div>
              </div>
            </el-card>
            <el-card class="stat-card">
              <div class="stat-content">
                <div class="stat-value">{{ dashboardData.houses?.approved || 0 }}</div>
                <div class="stat-label">已审核房源</div>
              </div>
            </el-card>
            <el-card class="stat-card">
              <div class="stat-content">
                <div class="stat-value">{{ dashboardData.houses?.pending || 0 }}</div>
                <div class="stat-label">待审核房源</div>
              </div>
            </el-card>
            <div class="stat-card income">
              <el-icon><Money /></el-icon>
              <div class="stat-info">
                <div class="stat-value">¥{{ dashboardData.income?.total || 0 }}</div>
                <div class="stat-label">总收入</div>
              </div>
            </div>
            <div class="stat-card income-today">
              <el-icon><Wallet /></el-icon>
              <div class="stat-info">
                <div class="stat-value">¥{{ dashboardData.income?.today || 0 }}</div>
                <div class="stat-label">今日收入</div>
              </div>
            </div>
            <div class="stat-card income-month">
              <el-icon><CreditCard /></el-icon>
              <div class="stat-info">
                <div class="stat-value">¥{{ dashboardData.income?.month || 0 }}</div>
                <div class="stat-label">本月收入</div>
              </div>
            </div>
          </div>
          
          <!-- 图表区域 -->
          <div class="charts-row">
            <el-card class="chart-card">
              <template #header>房源状态分布</template>
              <div class="chart-container">
                <div class="pie-chart">
                  <div v-for="(item, index) in houseStatusData" :key="index" class="pie-item">
                    <div class="pie-color" :style="{ backgroundColor: item.color }"></div>
                    <span>{{ item.label }}: {{ item.value }}</span>
                  </div>
                </div>
              </div>
            </el-card>
            <el-card class="chart-card">
              <template #header>用户角色分布</template>
              <div class="chart-container">
                <div class="pie-chart">
                  <div v-for="(item, index) in userRoleData" :key="index" class="pie-item">
                    <div class="pie-color" :style="{ backgroundColor: item.color }"></div>
                    <span>{{ item.label }}: {{ item.value }}</span>
                  </div>
                </div>
              </div>
            </el-card>
          </div>
          
          <div class="charts-row">
            <el-card class="chart-card">
              <template #header>价格区间分布</template>
              <div class="chart-container">
                <div class="bar-chart">
                  <div v-for="(item, index) in priceDistributionData" :key="index" class="bar-item">
                    <div class="bar-label">{{ item.label }}</div>
                    <div class="bar-wrapper">
                      <div class="bar-fill" :style="{ width: item.percentage + '%' }"></div>
                    </div>
                    <div class="bar-value">{{ item.count }}</div>
                  </div>
                </div>
              </div>
            </el-card>
            <el-card class="chart-card">
              <template #header>热门区域TOP10</template>
              <div class="chart-container">
                <div class="bar-chart">
                  <div v-for="(item, index) in areaDistributionData" :key="index" class="bar-item">
                    <div class="bar-label">{{ item.name }}</div>
                    <div class="bar-wrapper">
                      <div class="bar-fill" :style="{ width: item.percentage + '%' }"></div>
                    </div>
                    <div class="bar-value">{{ item.count }}</div>
                  </div>
                </div>
              </div>
            </el-card>
          </div>
          
          <div class="charts-row">
            <el-card class="chart-card full-width">
              <template #header>近7天趋势</template>
              <div class="chart-container">
                <div class="trend-chart">
                  <div class="trend-header">
                    <span>日期</span>
                    <span>新增房源</span>
                    <span>新增用户</span>
                  </div>
                  <div v-for="(item, index) in recentTrendData" :key="index" class="trend-row">
                    <span>{{ item.date }}</span>
                    <span>{{ item.houses }}</span>
                    <span>{{ item.users }}</span>
                  </div>
                </div>
              </div>
            </el-card>
          </div>
        </div>
      </el-tab-pane>

      <!-- 用户管理 -->
      <el-tab-pane label="用户管理" name="users">
        <div class="tab-toolbar">
          <el-button type="primary" @click="showUserDialog()">添加用户</el-button>
          <el-input v-model="userSearch" placeholder="搜索用户名" style="width:200px;margin-left:10px" clearable />
          <el-select v-model="userRoleFilter" placeholder="角色筛选" clearable style="width:120px;margin-left:10px">
            <el-option label="管理员" value="admin" />
            <el-option label="房东" value="landlord" />
            <el-option label="租客" value="tenant" />
          </el-select>
          <el-button type="primary" style="margin-left:10px" @click="loadUsers">查询</el-button>
          <el-button @click="resetUserFilter">重置</el-button>
        </div>

        <el-table v-loading="usersLoading" :data="users" stripe border>
          <el-table-column prop="id" label="ID" width="60" />
          <el-table-column prop="username" label="用户名" min-width="120" />
          <el-table-column prop="phone" label="手机号" min-width="120" />
          <el-table-column prop="role" label="角色" width="100">
            <template #default="{ row }">
              <el-tag :type="row.role==='admin'?'danger':row.role==='landlord'?'warning':'info'">{{ roleMap[row.role] }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="is_active" label="状态" width="80">
            <template #default="{ row }">
              <el-tag :type="row.is_active?'success':'danger'">{{ row.is_active?'启用':'禁用' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="250" fixed="right">
            <template #default="{ row }">
              <el-button type="primary" text size="small" @click="showUserDialog(row)">编辑</el-button>
              <el-button :type="row.is_active?'danger':'success'" text size="small" @click="toggleUserStatus(row)">{{ row.is_active?'禁用':'启用' }}</el-button>
              <el-button type="danger" text size="small" @click="deleteUser(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>

        <div class="pagination-wrapper">
          <el-pagination
            v-model:current-page="userPage"
            v-model:page-size="userPageSize"
            :page-sizes="[5, 10, 15, 20, 25]"
            :total="userTotal"
            layout="total, sizes, prev, pager, next, jumper"
            @current-change="loadUsers"
            @size-change="loadUsers"
          />
        </div>
      </el-tab-pane>

      <!-- 房源审核 -->
      <el-tab-pane label="房源审核" name="houses">
        <div class="tab-toolbar">
          <el-select v-model="houseStatusFilter" placeholder="状态筛选" clearable style="width:120px">
            <el-option label="待审核" value="pending" />
            <el-option label="已通过" value="approved" />
            <el-option label="已拒绝" value="rejected" />
            <el-option label="已下架" value="offline" />
          </el-select>
          <el-input v-model="houseSearch" placeholder="搜索标题" style="width:200px;margin-left:10px" clearable />
          <el-button type="primary" style="margin-left:10px" @click="loadHouses">查询</el-button>
          <el-button @click="resetHouseFilter">重置</el-button>
        </div>

        <el-table v-loading="housesLoading" :data="houses" stripe border>
          <el-table-column prop="id" label="ID" width="60" />
          <el-table-column prop="title" label="标题" min-width="200" show-overflow-tooltip />
          <el-table-column prop="price" label="价格" width="100">
            <template #default="{ row }">¥{{ row.price }}/月</template>
          </el-table-column>
          <el-table-column prop="landlord_name" label="房东" width="100" />
          <el-table-column prop="status" label="状态" width="100">
            <template #default="{ row }">
              <el-tag :type="statusType[row.status]">{{ statusMap[row.status] }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="250" fixed="right">
            <template #default="{ row }">
              <el-button type="primary" text size="small" @click="showHouseDetail(row)">详情</el-button>
              <el-button v-if="row.status==='pending'" type="success" text size="small" @click="auditHouse(row.id,'approve')">通过</el-button>
              <el-button v-if="row.status==='pending'" type="danger" text size="small" @click="auditHouse(row.id,'reject')">拒绝</el-button>
              <el-button v-if="row.status==='approved'" type="warning" text size="small" @click="auditHouse(row.id,'offline')">下架</el-button>
            </template>
          </el-table-column>
        </el-table>

        <div class="pagination-wrapper">
          <el-pagination
            v-model:current-page="housePage"
            v-model:page-size="housePageSize"
            :page-sizes="[5, 10, 15, 20, 25]"
            :total="houseTotal"
            layout="total, sizes, prev, pager, next, jumper"
            @current-change="loadHouses"
            @size-change="loadHouses"
          />
        </div>
      </el-tab-pane>

      <!-- 系统公告 -->
      <el-tab-pane label="系统公告" name="announcements">
        <div class="tab-toolbar">
          <el-button type="primary" @click="showAnnouncementDialog()">发布公告</el-button>
          <el-button 
            v-if="selectedAnnouncements.length > 0" 
            type="danger" 
            @click="batchDeleteAnnouncements"
          >
            批量删除 ({{ selectedAnnouncements.length }})
          </el-button>
        </div>

        <el-table v-loading="announcementsLoading" :data="announcements" stripe border @selection-change="handleAnnouncementSelectionChange">
          <el-table-column type="selection" width="55" />
          <el-table-column prop="id" label="ID" width="60" />
          <el-table-column prop="title" label="标题" min-width="200" show-overflow-tooltip />
          <el-table-column prop="author_name" label="发布者" width="100" />
          <el-table-column prop="is_active" label="状态" width="80">
            <template #default="{ row }">
              <el-tag :type="row.is_active?'success':'danger'">{{ row.is_active?'有效':'无效' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="create_time" label="发布时间" width="180">
            <template #default="{ row }">
              {{ formatTime(row.create_time) }}
            </template>
          </el-table-column>
          <el-table-column label="操作" width="200" fixed="right">
            <template #default="{ row }">
              <el-button type="primary" text size="small" @click="showAnnouncementDialog(row)">编辑</el-button>
              <el-button :type="row.is_active?'danger':'success'" text size="small" @click="toggleAnnouncementStatus(row)">
                {{ row.is_active?'禁用':'启用' }}
              </el-button>
              <el-button type="danger" text size="small" @click="deleteAnnouncement(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>

        <div class="pagination-wrapper">
          <el-pagination
            v-model:current-page="announcementPage"
            v-model:page-size="announcementPageSize"
            :page-sizes="[5, 10, 15, 20, 25]"
            :total="announcementTotal"
            layout="total, sizes, prev, pager, next, jumper"
            @current-change="loadAnnouncements"
            @size-change="loadAnnouncements"
          />
        </div>
      </el-tab-pane>
    </el-tabs>

    <!-- 公告编辑弹窗 -->
    <el-dialog v-model="announcementDialogVisible" :title="isEditAnnouncement?'编辑公告':'发布公告'" width="600px">
      <el-form :model="announcementForm" label-width="80px">
        <el-form-item label="标题" required>
          <el-input v-model="announcementForm.title" placeholder="请输入公告标题" />
        </el-form-item>
        <el-form-item label="内容" required>
          <el-input 
            v-model="announcementForm.content" 
            type="textarea" 
            :rows="6" 
            placeholder="请输入公告内容"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="announcementDialogVisible=false">取消</el-button>
        <el-button type="primary" :loading="announcementSaving" @click="saveAnnouncement">保存</el-button>
      </template>
    </el-dialog>

    <!-- 用户编辑弹窗 -->
    <el-dialog v-model="userDialogVisible" :title="isEditUser?'编辑用户':'添加用户'" width="500px">
      <el-form :model="userForm" label-width="80px">
        <el-form-item label="用户名" required>
          <el-input v-model="userForm.username" placeholder="请输入用户名" />
        </el-form-item>
        <el-form-item :label="isEditUser?'新密码':'密码'" :required="!isEditUser">
          <el-input v-model="userForm.password" type="password" :placeholder="isEditUser?'留空则不修改':'请输入密码'" show-password />
        </el-form-item>
        <el-form-item label="手机号">
          <el-input v-model="userForm.phone" placeholder="请输入手机号" />
        </el-form-item>
        <el-form-item label="角色" required>
          <el-select v-model="userForm.role" style="width:100%">
            <el-option label="管理员" value="admin" />
            <el-option label="房东" value="landlord" />
            <el-option label="租客" value="tenant" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="userDialogVisible=false">取消</el-button>
        <el-button type="primary" :loading="userSaving" @click="saveUser">保存</el-button>
      </template>
    </el-dialog>

    <!-- 房源详情弹窗 -->
    <el-dialog v-model="houseDetailVisible" title="房源详情" width="700px">
      <div v-if="currentHouse" class="house-detail-dialog">
        <div class="detail-image">
          <img :src="currentHouse.image || 'https://via.placeholder.com/600x400?text=暂无图片'" :alt="currentHouse.title" />
        </div>
        <div class="detail-info">
          <h3 class="detail-title">{{ currentHouse.title }}</h3>
          <div class="detail-price">
            <span class="price-value">¥{{ currentHouse.price }}</span>
            <span class="price-unit">/月</span>
          </div>
          <div class="detail-tags">
            <el-tag>{{ currentHouse.rooms }}</el-tag>
            <el-tag type="success">{{ currentHouse.area }}m²</el-tag>
            <el-tag type="warning">{{ currentHouse.house_type_display }}</el-tag>
            <el-tag :type="statusType[currentHouse.status]">{{ currentHouse.status_display }}</el-tag>
          </div>
          <div class="detail-location">
            <el-icon><Location /></el-icon>
            <span>{{ currentHouse.region_name }} {{ currentHouse.address_detail }}</span>
          </div>
          <div class="detail-description">
            <h4>房源描述</h4>
            <p>{{ currentHouse.description || '暂无描述' }}</p>
          </div>
          <div class="detail-landlord">
            <h4>房东信息</h4>
            <p>用户名：{{ currentHouse.landlord_info?.username }}</p>
            <p>手机号：{{ currentHouse.landlord_info?.phone || '暂无' }}</p>
          </div>
          <div class="detail-time">
            <p>发布时间：{{ formatTime(currentHouse.create_time) }}</p>
            <p>更新时间：{{ formatTime(currentHouse.update_time) }}</p>
          </div>
        </div>
      </div>
      <template #footer>
        <el-button @click="houseDetailVisible=false">关闭</el-button>
        <el-button v-if="currentHouse?.status==='pending'" type="success" @click="auditHouse(currentHouse.id,'approve'); houseDetailVisible=false">通过</el-button>
        <el-button v-if="currentHouse?.status==='pending'" type="danger" @click="auditHouse(currentHouse.id,'reject'); houseDetailVisible=false">拒绝</el-button>
        <el-button v-if="currentHouse?.status==='approved'" type="warning" @click="auditHouse(currentHouse.id,'offline'); houseDetailVisible=false">下架</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Location, Money, Wallet, CreditCard } from '@element-plus/icons-vue'
import request from '@/api'
import { getDashboardStatsApi, getAdminHouseDetailApi } from '@/api/admin'
import { getAnnouncementsApi, createAnnouncementApi, updateAnnouncementApi, deleteAnnouncementApi, batchDeleteAnnouncementsApi } from '@/api/notification'

const activeTab = ref('users')
const roleMap = { admin: '管理员', landlord: '房东', tenant: '租客' }
const statusMap = { pending: '待审核', approved: '已通过', rejected: '已拒绝', offline: '已下架' }
const statusType = { pending: 'warning', approved: 'success', rejected: 'danger', offline: 'info' }

// 房源详情
const houseDetailVisible = ref(false)
const currentHouse = ref(null)

// 用户管理
const users = ref([])
const usersLoading = ref(false)
const userSearch = ref('')
const userRoleFilter = ref('')
const userPage = ref(1)
const userPageSize = ref(10)
const userTotal = ref(0)
const userDialogVisible = ref(false)
const userSaving = ref(false)
const editingUserId = ref(null)
const isEditUser = ref(false)
const userForm = reactive({ username: '', password: '', phone: '', role: 'tenant' })

// 房源管理
const houses = ref([])
const housesLoading = ref(false)
const houseSearch = ref('')
const houseStatusFilter = ref('')
const housePage = ref(1)
const housePageSize = ref(10)
const houseTotal = ref(0)

// 公告管理
const announcements = ref([])
const announcementsLoading = ref(false)
const announcementPage = ref(1)
const announcementPageSize = ref(10)
const announcementTotal = ref(0)
const announcementDialogVisible = ref(false)
const announcementSaving = ref(false)
const editingAnnouncementId = ref(null)
const isEditAnnouncement = ref(false)
const announcementForm = reactive({ title: '', content: '' })
const selectedAnnouncements = ref([])

// 格式化时间
function formatTime(timeStr) {
  if (!timeStr) return ''
  const date = new Date(timeStr)
  return date.toLocaleString('zh-CN')
}

// 重置用户筛选
function resetUserFilter() {
  userSearch.value = ''
  userRoleFilter.value = ''
  userPage.value = 1
  loadUsers()
}

// 重置房源筛选
function resetHouseFilter() {
  houseSearch.value = ''
  houseStatusFilter.value = ''
  housePage.value = 1
  loadHouses()
}

// 加载用户列表
async function loadUsers() {
  usersLoading.value = true
  try {
    const params = { page: userPage.value, page_size: userPageSize.value }
    if (userSearch.value) params.keyword = userSearch.value
    if (userRoleFilter.value) params.role = userRoleFilter.value
    const res = await request({ url: '/admin/users/', method: 'get', params })
    users.value = res.results || res || []
    userTotal.value = res.count || 0
  } catch (e) {
    ElMessage.error('加载用户失败')
  } finally {
    usersLoading.value = false
  }
}

// 显示用户弹窗
function showUserDialog(user = null) {
  if (user) {
    isEditUser.value = true
    editingUserId.value = user.id
    userForm.username = user.username
    userForm.password = ''
    userForm.phone = user.phone || ''
    userForm.role = user.role || 'tenant'
  } else {
    isEditUser.value = false
    editingUserId.value = null
    userForm.username = ''
    userForm.password = ''
    userForm.phone = ''
    userForm.role = 'tenant'
  }
  userDialogVisible.value = true
}

// 保存用户
async function saveUser() {
  if (!userForm.username) return ElMessage.warning('请输入用户名')
  if (!isEditUser.value && !userForm.password) return ElMessage.warning('请输入密码')

  userSaving.value = true
  try {
    if (isEditUser.value) {
      await request({ url: `/admin/users/${editingUserId.value}/`, method: 'put', data: userForm })
      ElMessage.success('修改成功')
    } else {
      await request({ url: '/admin/users/', method: 'post', data: userForm })
      ElMessage.success('添加成功')
    }
    userDialogVisible.value = false
    loadUsers()
  } catch (e) {
    ElMessage.error(e.response?.data?.detail || '操作失败')
  } finally {
    userSaving.value = false
  }
}

// 切换用户状态
async function toggleUserStatus(user) {
  try {
    await request({ url: `/admin/users/${user.id}/status/`, method: 'put', data: { is_active: !user.is_active } })
    ElMessage.success(user.is_active ? '已禁用' : '已启用')
    loadUsers()
  } catch (e) {
    ElMessage.error('操作失败')
  }
}

// 删除用户
async function deleteUser(user) {
  try {
    await ElMessageBox.confirm(`确定删除用户 ${user.username} 吗？`, '提示', { type: 'warning' })
    await request({ url: `/admin/users/${user.id}/`, method: 'delete' })
    ElMessage.success('删除成功')
    loadUsers()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error(e.response?.data?.detail || '删除失败')
  }
}

// 加载房源列表
async function loadHouses() {
  housesLoading.value = true
  try {
    const params = { page: housePage.value, page_size: housePageSize.value }
    if (houseSearch.value) params.keyword = houseSearch.value
    if (houseStatusFilter.value) params.status = houseStatusFilter.value
    const res = await request({ url: '/admin/houses/', method: 'get', params })
    houses.value = res.results || res || []
    houseTotal.value = res.count || 0
  } catch (e) {
    ElMessage.error('加载房源失败')
  } finally {
    housesLoading.value = false
  }
}

// 审核房源
async function auditHouse(id, action) {
  try {
    await request({ url: `/admin/houses/${id}/audit/`, method: 'put', data: { action } })
    ElMessage.success('操作成功')
    loadHouses()
  } catch (e) {
    ElMessage.error('操作失败')
  }
}

// 显示房源详情
async function showHouseDetail(house) {
  try {
    const res = await getAdminHouseDetailApi(house.id)
    currentHouse.value = res
    houseDetailVisible.value = true
  } catch (e) {
    ElMessage.error('获取房源详情失败')
  }
}

// ============================================================
// 系统公告
// ============================================================

// 加载公告列表
async function loadAnnouncements() {
  announcementsLoading.value = true
  try {
    const res = await getAnnouncementsApi({
      page: announcementPage.value,
      page_size: announcementPageSize.value
    })
    announcements.value = res.results || []
    announcementTotal.value = res.count || 0
  } catch (e) {
    ElMessage.error('加载公告失败')
  } finally {
    announcementsLoading.value = false
  }
}

// 显示公告弹窗
function showAnnouncementDialog(announcement = null) {
  if (announcement) {
    isEditAnnouncement.value = true
    editingAnnouncementId.value = announcement.id
    announcementForm.title = announcement.title
    announcementForm.content = announcement.content
  } else {
    isEditAnnouncement.value = false
    editingAnnouncementId.value = null
    announcementForm.title = ''
    announcementForm.content = ''
  }
  announcementDialogVisible.value = true
}

// 保存公告
async function saveAnnouncement() {
  if (!announcementForm.title) return ElMessage.warning('请输入公告标题')
  if (!announcementForm.content) return ElMessage.warning('请输入公告内容')

  announcementSaving.value = true
  try {
    if (isEditAnnouncement.value) {
      await updateAnnouncementApi(editingAnnouncementId.value, announcementForm)
      ElMessage.success('修改成功')
    } else {
      await createAnnouncementApi(announcementForm)
      ElMessage.success('发布公告成功')
    }
    announcementDialogVisible.value = false
    loadAnnouncements()
  } catch (e) {
    ElMessage.error(e.response?.data?.detail || '操作失败')
  } finally {
    announcementSaving.value = false
  }
}

// 切换公告状态
async function toggleAnnouncementStatus(announcement) {
  try {
    await updateAnnouncementApi(announcement.id, { is_active: !announcement.is_active })
    ElMessage.success(announcement.is_active ? '已禁用' : '已启用')
    loadAnnouncements()
  } catch (e) {
    ElMessage.error('操作失败')
  }
}

// 删除公告
async function deleteAnnouncement(announcement) {
  try {
    await ElMessageBox.confirm(`确定删除公告「${announcement.title}」吗？`, '提示', { type: 'warning' })
    await deleteAnnouncementApi(announcement.id)
    ElMessage.success('删除成功')
    loadAnnouncements()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error('删除失败')
  }
}

// 公告选择变化处理
function handleAnnouncementSelectionChange(selection) {
  selectedAnnouncements.value = selection
}

// 批量删除公告
async function batchDeleteAnnouncements() {
  try {
    await ElMessageBox.confirm(
      `确定要删除选中的 ${selectedAnnouncements.value.length} 条公告吗？`,
      '批量删除确认',
      { type: 'warning' }
    )
    
    const ids = selectedAnnouncements.value.map(item => item.id)
    await batchDeleteAnnouncementsApi(ids)
    ElMessage.success('批量删除成功')
    selectedAnnouncements.value = []
    loadAnnouncements()
  } catch (e) {
    if (e !== 'cancel') {
      ElMessage.error('批量删除失败')
    }
  }
}

// ============================================================
// 数据看板
// ============================================================

const dashboardLoading = ref(false)
const dashboardData = ref({})

// 房源状态分布数据
const houseStatusData = ref([])
// 用户角色分布数据
const userRoleData = ref([])
// 价格区间分布数据
const priceDistributionData = ref([])
// 区域分布数据
const areaDistributionData = ref([])
// 近期趋势数据
const recentTrendData = ref([])

// 加载数据看板
async function loadDashboard() {
  dashboardLoading.value = true
  try {
    const res = await getDashboardStatsApi()
    dashboardData.value = res
    
    // 处理房源状态分布
    houseStatusData.value = [
      { label: '已通过', value: res.houses?.approved || 0, color: '#67c23a' },
      { label: '待审核', value: res.houses?.pending || 0, color: '#e6a23c' },
      { label: '已拒绝', value: res.houses?.rejected || 0, color: '#f56c6c' },
      { label: '已下架', value: res.houses?.offline || 0, color: '#909399' },
    ]
    
    // 处理用户角色分布
    userRoleData.value = [
      { label: '租客', value: res.users?.tenant || 0, color: '#409eff' },
      { label: '房东', value: res.users?.landlord || 0, color: '#e6a23c' },
      { label: '管理员', value: res.users?.admin || 0, color: '#f56c6c' },
    ]
    
    // 处理价格区间分布
    const maxPriceCount = Math.max(...(res.price_distribution || []).map(item => item.count), 1)
    priceDistributionData.value = (res.price_distribution || []).map(item => ({
      ...item,
      percentage: (item.count / maxPriceCount) * 100
    }))
    
    // 处理区域分布
    const maxAreaCount = Math.max(...(res.area_distribution || []).map(item => item.count), 1)
    areaDistributionData.value = (res.area_distribution || []).map(item => ({
      ...item,
      percentage: (item.count / maxAreaCount) * 100
    }))
    
    // 处理近期趋势
    recentTrendData.value = res.recent_trend || []
    
  } catch (e) {
    ElMessage.error('加载统计数据失败')
  } finally {
    dashboardLoading.value = false
  }
}

// 监听Tab切换，加载数据看板
watch(activeTab, (newTab) => {
  if (newTab === 'dashboard') {
    loadDashboard()
  } else if (newTab === 'announcements') {
    loadAnnouncements()
  }
})

onMounted(() => {
  loadUsers()
  loadHouses()
  loadDashboard()
})
</script>

<style scoped>
.admin-dashboard { padding: 20px; }
.admin-header h2 { margin: 0 0 20px; font-size: 24px; }
.admin-tabs { background: #fff; padding: 20px; border-radius: 12px; }
.tab-toolbar { margin-bottom: 16px; display: flex; align-items: center; }
.pagination-wrapper { display: flex; justify-content: center; margin-top: 20px; }

/* 数据看板样式 */
.dashboard-container { padding: 10px 0; }
.stats-cards { display: grid; grid-template-columns: repeat(4, 1fr); gap: 16px; margin-bottom: 20px; }
.stat-card { text-align: center; }
.stat-content { padding: 10px; }
.stat-value { font-size: 32px; font-weight: 700; color: #409eff; }
.stat-label { font-size: 14px; color: #909399; margin-top: 8px; }

.charts-row { display: grid; grid-template-columns: repeat(2, 1fr); gap: 16px; margin-bottom: 20px; }
.chart-card { min-height: 300px; }
.chart-card.full-width { grid-column: 1 / -1; }
.chart-container { padding: 10px; }

/* 饼图样式 */
.pie-chart { display: flex; flex-wrap: wrap; gap: 12px; justify-content: center; padding: 20px; }
.pie-item { display: flex; align-items: center; gap: 8px; }
.pie-color { width: 16px; height: 16px; border-radius: 4px; }
.pie-item span { font-size: 14px; color: #606266; }

/* 柱状图样式 */
.bar-chart { display: flex; flex-direction: column; gap: 12px; padding: 10px; }
.bar-item { display: flex; align-items: center; gap: 12px; }
.bar-label { width: 80px; font-size: 12px; color: #606266; text-align: right; flex-shrink: 0; }
.bar-wrapper { flex: 1; height: 24px; background: #f5f7fa; border-radius: 4px; overflow: hidden; }
.bar-fill { height: 100%; background: linear-gradient(90deg, #409eff, #67c23a); border-radius: 4px; transition: width 0.3s; }
.bar-value { width: 40px; font-size: 12px; color: #909399; text-align: left; }

/* 趋势图样式 */
.trend-chart { padding: 10px; }
.trend-header { display: grid; grid-template-columns: repeat(3, 1fr); padding: 12px; background: #f5f7fa; border-radius: 4px; font-weight: 600; color: #303133; }
.trend-row { display: grid; grid-template-columns: repeat(3, 1fr); padding: 12px; border-bottom: 1px solid #ebeef5; }
.trend-row:last-child { border-bottom: none; }
.trend-row span { text-align: center; color: #606266; }

/* 房源详情弹窗样式 */
.house-detail-dialog { display: flex; gap: 24px; }
.detail-image { flex: 0 0 300px; }
.detail-image img { width: 100%; height: 220px; object-fit: cover; border-radius: 8px; }
.detail-info { flex: 1; }
.detail-title { font-size: 20px; font-weight: 600; color: #303133; margin-bottom: 12px; }
.detail-price { margin-bottom: 12px; }
.price-value { font-size: 28px; font-weight: 700; color: #f56c6c; }
.price-unit { font-size: 14px; color: #909399; }
.detail-tags { display: flex; gap: 8px; margin-bottom: 16px; flex-wrap: wrap; }
.detail-location { display: flex; align-items: center; gap: 8px; color: #606266; margin-bottom: 16px; }
.detail-location .el-icon { color: #409eff; }
.detail-description { margin-bottom: 16px; }
.detail-description h4 { font-size: 14px; color: #303133; margin-bottom: 8px; }
.detail-description p { color: #606266; line-height: 1.6; }
.detail-landlord { margin-bottom: 16px; }
.detail-landlord h4 { font-size: 14px; color: #303133; margin-bottom: 8px; }
.detail-landlord p { color: #606266; margin-bottom: 4px; }
.detail-time { border-top: 1px solid #ebeef5; padding-top: 12px; }
.detail-time p { color: #909399; font-size: 13px; margin-bottom: 4px; }

/* 收入卡片样式 */
.stat-card.income,
.stat-card.income-today,
.stat-card.income-month {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  padding: 20px;
  border-radius: 8px;
}
.stat-card.income .el-icon,
.stat-card.income-today .el-icon,
.stat-card.income-month .el-icon {
  font-size: 32px;
  color: #fff;
}
.stat-card.income .stat-value,
.stat-card.income-today .stat-value,
.stat-card.income-month .stat-value {
  color: #fff;
  font-size: 28px;
}
.stat-card.income .stat-label,
.stat-card.income-today .stat-label,
.stat-card.income-month .stat-label {
  color: rgba(255,255,255,0.85);
}
.stat-card.income { background: linear-gradient(135deg, #f56c6c, #e6a23c); }
.stat-card.income-today { background: linear-gradient(135deg, #e6a23c, #f7ba2a); }
.stat-card.income-month { background: linear-gradient(135deg, #409eff, #67c23a); }
</style>