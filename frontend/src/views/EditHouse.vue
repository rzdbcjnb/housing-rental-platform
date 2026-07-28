<template>
  <div class="edit-house">
    <div class="page-header">
      <h2>编辑房源</h2>
    </div>

    <el-card v-loading="loading" class="form-card">
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
          <el-button type="primary" :loading="submitting" @click="handleSubmit">保存修改</el-button>
          <el-button @click="goBack">取消</el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { getHouseDetailApi, updateHouseApi, getAreasApi } from '@/api/house'
import { uploadImageApi } from '@/api/upload'

const route = useRoute()
const router = useRouter()
const formRef = ref(null)
const loading = ref(false)
const submitting = ref(false)
const uploading = ref(false)

// 户型分组
const rooms = reactive({ shi: 1, ting: 1, wei: null, chu: null })

const form = reactive({
  title: '',
  description: '',
  rooms: '',
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

// 解析户型字符串
function parseRoomsString(roomsStr) {
  if (!roomsStr) return
  const match = roomsStr.match(/(\d+)室(\d+)厅(\d+)卫(\d+)厨/)
  if (match) {
    rooms.shi = parseInt(match[1])
    rooms.ting = parseInt(match[2])
    rooms.wei = parseInt(match[3])
    rooms.chu = parseInt(match[4])
  }
}

// 生成户型字符串
function generateRoomsString() {
  return `${rooms.shi}室${rooms.ting}厅${rooms.wei}卫${rooms.chu}厨`
}

function beforeImageUpload(file) {
  const isImage = file.type.startsWith('image/')
  const isLt2M = file.size / 1024 / 1024 < 2
  if (!isImage) { ElMessage.error('只能上传图片文件!'); return false }
  if (!isLt2M) { ElMessage.error('图片大小不能超过 2MB!'); return false }
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

async function resolveRegionHierarchy(regionId) {
  if (!regionId) return
  try {
    const allAreas = await getAreasApi({})
    const areas = allAreas.results || allAreas || []
    const region = areas.find(a => a.id === regionId)
    if (!region) return

    const district = areas.find(a => a.id === region.parent)
    if (district) {
      const city = areas.find(a => a.id === district.parent)
      if (city) {
        selectedCity.value = city.id
        await handleCityChange(city.id)
        selectedDistrict.value = district.id
        await handleDistrictChange(district.id)
        form.region = regionId
      }
    }
  } catch (e) {
    console.error('解析地区层级失败:', e)
  }
}

async function loadHouseDetail() {
  const id = route.params.id
  if (!id) {
    ElMessage.error('房源ID不存在')
    return
  }
  loading.value = true
  try {
    const res = await getHouseDetailApi(id)
    const house = res.data || res
    form.title = house.title || ''
    form.description = house.description || ''
    form.rooms = house.rooms || ''
    form.house_type = house.house_type || ''
    form.price = Number(house.price) || null
    form.area = house.area || null
    form.address_detail = house.address_detail || ''
    form.image = house.image || ''
    
    // 解析户型
    parseRoomsString(house.rooms)
    rooms.shi = house.bedroom_count ?? rooms.shi
    rooms.ting = house.living_room_count ?? rooms.ting
    rooms.wei = house.bathroom_count ?? rooms.wei
    rooms.chu = house.kitchen_count ?? rooms.chu
    
    if (house.region) {
      await resolveRegionHierarchy(house.region)
    }
  } catch (e) {
    ElMessage.error('获取房源信息失败')
  } finally {
    loading.value = false
  }
}

async function handleSubmit() {
  if (!formRef.value) return
  try {
    await formRef.value.validate()
  } catch {
    return
  }
  if ([rooms.shi, rooms.ting, rooms.wei, rooms.chu].some(value => value === null)) {
    ElMessage.warning('\u8bf7\u8865\u5168\u5ba4\u3001\u5385\u3001\u536b\u3001\u53a8\u6570\u91cf')
    return
  }
  
  // 生成户型字符串
  form.rooms = generateRoomsString()
  
  submitting.value = true
  try {
    const id = route.params.id
    await updateHouseApi(id, {
      title: form.title,
      description: form.description,
      rooms: form.rooms,
      bedroom_count: rooms.shi,
      living_room_count: rooms.ting,
      bathroom_count: rooms.wei,
      kitchen_count: rooms.chu,
      house_type: form.house_type,
      price: form.price,
      area: form.area,
      region: form.region,
      address_detail: form.address_detail,
      image: form.image
    })
    ElMessage.success('修改成功')
    router.push('/houses/' + id)
  } catch (e) {
    ElMessage.error(e.response?.data?.detail || '修改失败')
  } finally {
    submitting.value = false
  }
}

function goBack() {
  router.back()
}

onMounted(async () => {
  await loadCities()
  await loadHouseDetail()
})
</script>

<style scoped>
.edit-house { max-width: 800px; margin: 0 auto; padding: 20px; }
.page-header { margin-bottom: 20px; }
.page-header h2 { font-size: 24px; color: #303133; margin: 0 0 8px; }
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
</style>