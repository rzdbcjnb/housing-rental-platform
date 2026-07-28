<template>
  <div class="page-recommend">
    <div class="page-header">
      <h2 class="page-title">推荐管理</h2>
      <div class="page-desc">通过推荐点提升房源曝光度</div>
    </div>

    <!-- 账户余额卡片 -->
    <el-card class="balance-card">
      <div class="balance-info">
        <div class="balance-main">
          <div class="balance-label">推荐点余额</div>
          <div class="balance-value">{{ account.balance }}</div>
        </div>
        <div class="balance-stats">
          <div class="stat-item">
            <div class="stat-label">累计购买</div>
            <div class="stat-value">{{ account.total_purchased }}</div>
          </div>
          <div class="stat-item">
            <div class="stat-label">累计投放</div>
            <div class="stat-value">{{ account.total_invested }}</div>
          </div>
        </div>
        <el-button type="primary" @click="showRechargeDialog">充值</el-button>
      </div>
    </el-card>

    <!-- 规则说明 -->
    <el-card class="rule-card">
      <template #header>
        <span>推荐规则</span>
      </template>
      <div class="rule-list">
        <div class="rule-item">1元 = 10推荐点</div>
        <div class="rule-item">单个房源最多投入100点（10%权重）</div>
        <div class="rule-item">每天衰减5%，低于1%停止推荐</div>
      </div>
    </el-card>

    <!-- 房源列表 -->
    <div v-loading="loading" class="house-list">
      <el-card v-for="item in houses" :key="item.house_id" class="house-card">
        <div class="house-info">
          <div class="house-title">{{ item.title }}</div>
          <el-tag :type="item.status === 'approved' ? 'success' : 'info'" size="small">
            {{ item.status === 'approved' ? '已上架' : '未上架' }}
          </el-tag>
        </div>
        
        <div class="house-stats">
          <div class="stat">
            <span class="stat-label">推荐指数：</span>
            <span class="stat-value">{{ item.points }}/100</span>
          </div>
          <div class="stat">
            <span class="stat-label">权重：</span>
            <span class="stat-value">{{ (item.weight * 100).toFixed(1) }}%</span>
          </div>
          <div class="stat">
            <el-icon><View /></el-icon>
            <span>{{ item.click_count || 0 }} 次点击</span>
          </div>
        </div>

        <el-button 
          type="primary" 
          size="small"
          :disabled="item.points >= 100 || account.balance <= 0"
          @click="showInvestDialog(item)"
        >
          投放推荐点
        </el-button>
      </el-card>

      <el-empty v-if="!loading && houses.length === 0" description="暂无房源" />
    </div>

    <!-- 充值弹窗 -->
    <el-dialog v-model="rechargeVisible" title="充值推荐点" width="400px">
      <el-form label-width="80px">
        <el-form-item label="充值点数">
          <el-input-number v-model="rechargePoints" :min="10" :step="10" />
        </el-form-item>
        <el-form-item label="支付金额">
          <span class="price">¥{{ (rechargePoints / 10).toFixed(1) }}</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="rechargeVisible = false">取消</el-button>
        <el-button type="primary" :loading="recharging" @click="handleRecharge">
          {{ recharging ? '支付中...' : '确认支付' }}
        </el-button>
      </template>
    </el-dialog>

    <!-- 投放弹窗 -->
    <el-dialog v-model="investVisible" title="投放推荐点" width="400px">
      <div v-if="selectedHouse" class="invest-info">
        <div>房源：{{ selectedHouse.title }}</div>
        <div>当前推荐点：{{ selectedHouse.points }}/100</div>
        <div>账户余额：{{ account.balance }}</div>
      </div>
      <el-form label-width="80px" style="margin-top: 16px">
        <el-form-item label="投放点数">
          <el-input-number 
            v-model="investPoints" 
            :min="1" 
            :max="maxInvest"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="investVisible = false">取消</el-button>
        <el-button type="primary" :loading="investing" @click="handleInvest">
          {{ investing ? '投放中...' : '确认投放' }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { View } from '@element-plus/icons-vue'
import { getAccountBalanceApi, rechargePointsApi, investPointsApi, getRecommendStatusApi } from '@/api/house'

const loading = ref(false)
const houses = ref([])
const account = ref({ balance: 0, total_purchased: 0, total_invested: 0 })

// 充值相关
const rechargeVisible = ref(false)
const rechargePoints = ref(100)
const recharging = ref(false)

// 投放相关
const investVisible = ref(false)
const selectedHouse = ref(null)
const investPoints = ref(10)
const investing = ref(false)

const maxInvest = computed(() => {
  if (!selectedHouse.value) return 0
  return Math.min(100 - selectedHouse.value.points, account.value.balance)
})

// 获取账户余额
async function fetchAccount() {
  try {
    const res = await getAccountBalanceApi()
    account.value = res
  } catch (e) {
    console.error('获取账户余额失败:', e)
  }
}

// 获取房源推荐状态
async function fetchHouses() {
  loading.value = true
  try {
    const res = await getRecommendStatusApi()
    houses.value = res.houses || []
  } catch (e) {
    console.error('获取推荐状态失败:', e)
  } finally {
    loading.value = false
  }
}

// 刷新数据
function refresh() {
  fetchAccount()
  fetchHouses()
}

// 显示充值弹窗
function showRechargeDialog() {
  rechargePoints.value = 100
  rechargeVisible.value = true
}

// 充值
async function handleRecharge() {
  recharging.value = true
  try {
    const res = await rechargePointsApi(rechargePoints.value)
    ElMessage.success(`充值成功！当前余额：${res.balance}点`)
    rechargeVisible.value = false
    refresh()
  } catch (e) {
    ElMessage.error(e.response?.data?.detail || '充值失败')
  } finally {
    recharging.value = false
  }
}

// 显示投放弹窗
function showInvestDialog(house) {
  selectedHouse.value = house
  investPoints.value = Math.min(10, maxInvest.value)
  investVisible.value = true
}

// 投放
async function handleInvest() {
  if (!selectedHouse.value) return
  if (investPoints.value > maxInvest.value) {
    ElMessage.error(`最多投放${maxInvest.value}点`)
    return
  }
  investing.value = true
  try {
    const res = await investPointsApi(selectedHouse.value.house_id, investPoints.value)
    ElMessage.success(`投放成功！当前推荐点：${res.house_points}`)
    investVisible.value = false
    refresh()
  } catch (e) {
    ElMessage.error(e.response?.data?.detail || '投放失败')
  } finally {
    investing.value = false
  }
}

onMounted(() => {
  refresh()
})
</script>

<style scoped>
.page-recommend {
  max-width: 800px;
  margin: 0 auto;
  padding: 20px;
}

.page-header {
  margin-bottom: 20px;
}

.page-title {
  margin: 0 0 8px;
  font-size: 24px;
  color: #333;
}

.page-desc {
  color: #666;
  font-size: 14px;
}

.balance-card {
  margin-bottom: 20px;
  background: linear-gradient(135deg, #67c23a, #409eff);
  color: #fff;
}

.balance-info {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.balance-main {
  text-align: center;
}

.balance-label {
  font-size: 14px;
  opacity: 0.8;
}

.balance-value {
  font-size: 36px;
  font-weight: bold;
}

.balance-stats {
  display: flex;
  gap: 24px;
}

.stat-item {
  text-align: center;
}

.stat-label {
  font-size: 12px;
  opacity: 0.8;
}

.stat-value {
  font-size: 18px;
  font-weight: 500;
}

.rule-card {
  margin-bottom: 20px;
}

.rule-list {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}

.rule-item {
  font-size: 14px;
  color: #666;
}

.house-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.house-card {
  cursor: default;
}

.house-info {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.house-title {
  font-size: 16px;
  font-weight: 500;
}

.house-stats {
  display: flex;
  gap: 16px;
  margin-bottom: 12px;
  font-size: 14px;
  color: #666;
}

.house-stats .stat {
  display: flex;
  align-items: center;
  gap: 4px;
}

.price {
  font-size: 20px;
  font-weight: bold;
  color: #f56c6c;
}

.invest-info {
  font-size: 14px;
  color: #666;
  line-height: 2;
}
</style>