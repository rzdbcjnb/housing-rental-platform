<template>
  <div class="publish-house">
    <div class="page-header">
      <h2>发布房源</h2>
      <p>填写房源信息，让更多租客看到您的房源</p>
    </div>

    <!-- 发布次数提示 -->
    <el-alert
      v-if="publishLimit"
      :title="publishLimit.need_pay ? '免费发布次数已用完，需要付费发布' : `还可免费发布 ${publishLimit.free_remaining} 条房源`"
      :type="publishLimit.need_pay ? 'warning' : 'success'"
      show-icon
      :closable="false"
      style="margin-bottom: 20px"
    />

    <el-card class="form-card">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px" size="large">
        <el-form-item label="房源标题" prop="title">
          <el-input v-model="form.title" placeholder="请输入房源标题" maxlength="50" show-word-limit />
        </el-form-item>

        <el-form-item label="房源描述" prop="description">
          <el-input v-model="form.description" type="textarea" placeholder="请描述房源特点" :rows="4" maxlength="500" show-word-limit />
        </el-form-item>

        <el-form-item label="户型" required>
          <div class="rooms-input-group">
            <el-input-number v-model="rooms.shi" :min="0" :max="10" controls-position="right" placeholder="室" />
            <span class="rooms-unit">室</span>
            <el-input-number v-model="rooms.ting" :min="0" :max="10" controls-position="right" placeholder="厅" />
            <span class="rooms-unit">厅</span>
            <el-input-number v-model="rooms.wei" :min="0" :max="10" controls-position="right" placeholder="卫" />
            <span class="rooms-unit">卫</span>
            <el-input-number v-model="rooms.chu" :min="0" :max="10" controls-position="right" placeholder="厨" />
            <span class="rooms-unit">厨</span>
          </div>
        </el-form-item>

        <el-form-item label="租赁方式" prop="house_type">
          <el-select v-model="form.house_type" placeholder="选择租赁方式" style="width:100%">
            <el-option label="整租" value="whole" />
            <el-option label="合租" value="share" />
          </el-select>
        </el-form-item>

        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="月租金" prop="price">
              <el-input-number v-model="form.price" :min="1" :max="100000" :step="100" controls-position="right" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="面积(m²)" prop="area">
              <el-input-number v-model="form.area" :min="1" :max="10000" :step="10" controls-position="right" style="width:100%" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="城市" prop="city">
              <el-select v-model="selectedCity" placeholder="选择城市" style="width:100%" @change="handleCityChange">
                <el-option v-for="c in cityList" :key="c.id" :label="c.name" :value="c.id" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="区" prop="district">
              <el-select v-model="selectedDistrict" placeholder="选择区" style="width:100%" :disabled="!selectedCity" @change="handleDistrictChange">
                <el-option v-for="d in districtList" :key="d.id" :label="d.name" :value="d.id" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="街道" prop="region">
              <el-select v-model="form.region" placeholder="选择街道" style="width:100%" :disabled="!selectedDistrict">
                <el-option v-for="s in streetList" :key="s.id" :label="s.name" :value="s.id" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>

        <el-form-item label="详细地址" prop="address_detail">
          <el-input v-model="form.address_detail" placeholder="请输入详细地址" />
        </el-form-item>

        <el-form-item label="封面图片" prop="image">
          <el-upload
            class="image-uploader"
            :show-file-list="false"
            :before-upload="beforeImageUpload"
            :http-request="handleImageUpload"
            accept="image/*"
          >
            <img v-if="form.image" :src="form.image" class="preview-image" />
            <div v-else class="upload-placeholder">
              <el-icon class="upload-icon"><Plus /></el-icon>
              <span>点击上传图片</span>
            </div>
          </el-upload>
          <div class="upload-tip">支持 jpg/png/gif 格式，最大 2MB</div>
        </el-form-item>

        <el-form-item>
          <el-button type="primary" :loading="submitting" @click="handleSubmit">发布房源</el-button>
          <el-button @click="goBack">取消</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 支付弹窗 -->
    <el-dialog v-model="showPayDialog" title="发布付费" width="400px" :close-on-click-modal="false">
      <div class="pay-dialog">
        <div class="pay-info">
          <el-icon :size="48" color="#E6A23C"><Warning /></el-icon>
          <h3>免费发布次数已用完</h3>
          <p>您已发布 {{ publishLimit?.total_published }} 条房源</p>
          <p>继续发布需要付费 <strong>10元/条</strong></p>
        </div>
        <div class="pay-amount">
          <span>支付金额：</span>
          <span class="price">¥10.00</span>
        </div>
      </div>
      <template #footer>
        <el-button @click="goBack">取消</el-button>
        <el-button type="primary" :loading="paying" @click="handlePay">
          {{ paying ? '支付中...' : '确认支付' }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Plus, Warning } from '@element-plus/icons-vue'
import { createHouseApi, getAreasApi, checkPublishLimitApi, simulatePaymentApi } from '@/api/house'
import { uploadImageApi } from '@/api/upload'

const router = useRouter()
const formRef = ref(null)
const submitting = ref(false)
const uploading = ref(false)

// 支付相关
const showPayDialog = ref(false)
const publishLimit = ref(null)
const paying = ref(false)

// 户型分组
const rooms = reactive({ shi: 1, ting: 1, wei: 1, chu: 1 })

const form = reactive({
  title: '',
  description: '',
  rooms: '',
  bedroom_count: 1,
  living_room_count: 1,
  bathroom_count: 1,
  kitchen_count: 1,
  house_type: '',
  price: null,
  area: null,
  region: null,
  address_detail: '',
  image: ''
})

const rules = {
  title: [{ required: true, message: '请输入标题', trigger: 'blur' }],
  house_type: [{ required: true, message: '请选择租赁方式', trigger: 'change' }],
  price: [{ required: true, message: '请输入价格', trigger: 'blur' }],
  area: [{ required: true, message: '请输入面积', trigger: 'blur' }],
  region: [{ required: true, message: '请选择地区', trigger: 'change' }]
}

const cityList = ref([])
const districtList = ref([])
const streetList = ref([])
const selectedCity = ref(null)
const selectedDistrict = ref(null)

// 生成户型字符串
function generateRoomsString() {
  return `${rooms.shi}室${rooms.ting}厅${rooms.wei}卫${rooms.chu}厨`
}

function beforeImageUpload(file) {
  const isImage = file.type.startsWith('image/')
  const isLt2M = file.size / 1024 / 1024 < 2
  if (!isImage) { ElMessage.error('仅支持jpg、png、gif格式'); return false }
  if (!isLt2M) { ElMessage.error('文件大小不能超过2MB'); return false }
  return true
}

async function handleImageUpload(options) {
  uploading.value = true
  try {
    const res = await uploadImageApi(options.file)
    form.image = res.url || res.data?.url || ''
    ElMessage.success('图片上传成功')
  } catch (e) {
    ElMessage.error('图片上传失败')
  } finally {
    uploading.value = false
  }
}

async function loadCities() {
  try {
    const res = await getAreasApi({ level: 1 })
    cityList.value = res.results || res || []
  } catch (e) {
    console.error('加载城市失败:', e)
  }
}

async function handleCityChange(cityId) {
  selectedDistrict.value = null
  form.region = null
  districtList.value = []
  streetList.value = []
  if (!cityId) return
  try {
    const res = await getAreasApi({ level: 2, parent_id: cityId })
    districtList.value = res.results || res || []
  } catch (e) {
    console.error('加载区失败:', e)
  }
}

async function handleDistrictChange(districtId) {
  form.region = null
  streetList.value = []
  if (!districtId) return
  try {
    const res = await getAreasApi({ level: 3, parent_id: districtId })
    streetList.value = res.results || res || []
  } catch (e) {
    console.error('加载街道失败:', e)
  }
}

// 检查发布限制
async function checkLimit() {
  try {
    const res = await checkPublishLimitApi()
    publishLimit.value = res
    // 不再自动弹出弹窗，改为点击发布按钮时检查
  } catch (e) {
    console.error('检查发布限制失败:', e)
  }
}

// 模拟支付
async function handlePay() {
  paying.value = true
  try {
    await simulatePaymentApi(10)
    ElMessage.success('支付成功！')
    showPayDialog.value = false
    // 支付成功后自动发布
    submitHouse()
  } catch (e) {
    ElMessage.error('支付失败')
  } finally {
    paying.value = false
  }
}

// 处理发布按钮点击（先验证，再检查付费，最后提交）
async function handleSubmit() {
  if (!formRef.value) return
  try {
    await formRef.value.validate()
  } catch {
    return
  }

  // 检查发布限制
  if (publishLimit.value && publishLimit.value.need_pay) {
    showPayDialog.value = true
    return
  }

  submitHouse()
}

// 实际提交房源（不含验证和付费检查）
async function submitHouse() {
  // 生成户型字符串
  form.rooms = generateRoomsString()

  Object.assign(form, {
    bedroom_count: rooms.shi,
    living_room_count: rooms.ting,
    bathroom_count: rooms.wei,
    kitchen_count: rooms.chu
  })

  submitting.value = true
  try {
    await createHouseApi(form)
    ElMessage.success('发布成功，等待审核')
    router.push('/user')
  } catch (e) {
    ElMessage.error(e.response?.data?.detail || '发布失败')
  } finally {
    submitting.value = false
  }
}

function goBack() {
  router.back()
}

onMounted(() => {
  loadCities()
  checkLimit()
})
</script>

<style scoped>
.publish-house { max-width: 800px; margin: 0 auto; padding: 20px; }
.page-header { margin-bottom: 20px; }
.page-header h2 { font-size: 24px; color: #303133; margin: 0 0 8px; }
.page-header p { color: #909399; margin: 0; }
.form-card { border-radius: 12px; }

.rooms-input-group {
  display: flex;
  align-items: center;
  gap: 8px;
}

.rooms-input-group .el-input-number {
  width: 80px;
}

.rooms-unit {
  color: #606266;
  font-size: 14px;
}

.image-uploader {
  width: 200px;
  height: 150px;
  border: 2px dashed #d9d9d9;
  border-radius: 8px;
  cursor: pointer;
  overflow: hidden;
  transition: border-color 0.3s;
}

.image-uploader:hover {
  border-color: #409eff;
}

.preview-image {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.upload-placeholder {
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  color: #8c8c8c;
}

.upload-icon {
  font-size: 28px;
  margin-bottom: 8px;
}

.upload-tip {
  font-size: 12px;
  color: #909399;
  margin-top: 8px;
}

.pay-dialog {
  text-align: center;
  padding: 20px 0;
}

.pay-info h3 {
  margin: 16px 0 8px;
  color: #333;
}

.pay-info p {
  color: #666;
  margin: 4px 0;
}

.pay-amount {
  margin-top: 20px;
  font-size: 18px;
}

.pay-amount .price {
  color: #F56C6C;
  font-size: 24px;
  font-weight: bold;
}
</style>