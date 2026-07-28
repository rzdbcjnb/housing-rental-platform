<template>
  <div class="register-page">
    <div class="register-card">
      <div class="register-header">
        <h2 class="register-title">创建账户</h2>
        <p class="register-subtitle">注册成为平台用户</p>
      </div>

      <el-form
        ref="formRef"
        :model="formData"
        :rules="rules"
        label-position="top"
        size="large"
        @keyup.enter="handleRegister"
      >
        <el-form-item label="用户名" prop="username">
          <el-input
            v-model="formData.username"
            placeholder="请输入用户名"
            prefix-icon="User"
            clearable
            @blur="checkUsername"
          />
          <div v-if="usernameStatus.checked" class="field-status">
            <span v-if="usernameStatus.valid" class="status-valid">
              <el-icon><CircleCheck /></el-icon> {{ usernameStatus.message }}
            </span>
            <span v-else class="status-invalid">
              <el-icon><CircleClose /></el-icon> {{ usernameStatus.message }}
            </span>
          </div>
        </el-form-item>

        <el-form-item label="手机号" prop="phone">
          <el-input
            v-model="formData.phone"
            placeholder="请输入手机号"
            prefix-icon="Phone"
            clearable
            @blur="checkPhone"
          />
          <div v-if="phoneStatus.checked" class="field-status">
            <span v-if="phoneStatus.valid" class="status-valid">
              <el-icon><CircleCheck /></el-icon> {{ phoneStatus.message }}
            </span>
            <span v-else class="status-invalid">
              <el-icon><CircleClose /></el-icon> {{ phoneStatus.message }}
            </span>
          </div>
        </el-form-item>

        <el-form-item label="密码" prop="password">
          <el-input
            v-model="formData.password"
            type="password"
            placeholder="请输入密码"
            prefix-icon="Lock"
            show-password
            clearable
          />
        </el-form-item>

        <el-form-item label="确认密码" prop="confirmPassword">
          <el-input
            v-model="formData.confirmPassword"
            type="password"
            placeholder="请再次输入密码"
            prefix-icon="Lock"
            show-password
            clearable
          />
        </el-form-item>

        <el-form-item label="角色" prop="role">
          <el-select v-model="formData.role" placeholder="请选择角色" style="width: 100%">
            <el-option label="租客" value="tenant" />
            <el-option label="房东" value="landlord" />
          </el-select>
        </el-form-item>

        <el-form-item>
          <el-button
            type="primary"
            :loading="loading"
            class="register-btn"
            @click="handleRegister"
          >
            {{ loading ? '注册中...' : '注 册' }}
          </el-button>
        </el-form-item>
      </el-form>

      <div class="register-footer">
        <span class="footer-text">已有账户？</span>
        <router-link to="/login" class="footer-link">立即登录</router-link>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { CircleCheck, CircleClose } from '@element-plus/icons-vue'
import { registerApi, checkUniqueApi } from '@/api/user'

const router = useRouter()
const formRef = ref(null)
const loading = ref(false)

const formData = reactive({
  username: '',
  phone: '',
  password: '',
  confirmPassword: '',
  role: 'tenant'
})

// 验证状态
const usernameStatus = reactive({
  checked: false,
  valid: false,
  message: ''
})

const phoneStatus = reactive({
  checked: false,
  valid: false,
  message: ''
})

// 检查用户名唯一性
let usernameTimer = null
async function checkUsername() {
  const username = formData.username.trim()
  if (!username || username.length < 3) {
    usernameStatus.checked = false
    return
  }

  // 防抖：清除之前的定时器
  if (usernameTimer) {
    clearTimeout(usernameTimer)
  }

  usernameTimer = setTimeout(async () => {
    try {
      const res = await checkUniqueApi('username', username)
      usernameStatus.checked = true
      usernameStatus.valid = !res.exists
      usernameStatus.message = res.message
    } catch (e) {
      usernameStatus.checked = false
    }
  }, 300)
}

// 检查手机号唯一性
let phoneTimer = null
async function checkPhone() {
  const phone = formData.phone.trim()
  if (!phone || !/^1[3-9]\d{9}$/.test(phone)) {
    phoneStatus.checked = false
    return
  }

  // 防抖：清除之前的定时器
  if (phoneTimer) {
    clearTimeout(phoneTimer)
  }

  phoneTimer = setTimeout(async () => {
    try {
      const res = await checkUniqueApi('phone', phone)
      phoneStatus.checked = true
      phoneStatus.valid = !res.exists
      phoneStatus.message = res.message
    } catch (e) {
      phoneStatus.checked = false
    }
  }, 300)
}

const validateConfirmPassword = (rule, value, callback) => {
  if (value !== formData.password) {
    callback(new Error('两次输入的密码不一致'))
  } else {
    callback()
  }
}

const rules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 3, max: 20, message: '用户名长度在 3 到 20 个字符', trigger: 'blur' }
  ],
  phone: [
    { required: true, message: '请输入手机号', trigger: 'blur' },
    { pattern: /^1[3-9]\d{9}$/, message: '请输入正确的手机号', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 20, message: '密码长度在 6 到 20 个字符', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: '请再次输入密码', trigger: 'blur' },
    { validator: validateConfirmPassword, trigger: 'blur' }
  ],
  role: [
    { required: true, message: '请选择角色', trigger: 'change' }
  ]
}

async function handleRegister() {
  if (!formRef.value) return
  
  try {
    await formRef.value.validate()
  } catch {
    return
  }

  // 检查用户名和手机号是否已通过验证
  if (usernameStatus.checked && !usernameStatus.valid) {
    ElMessage.error('用户名已存在，请更换')
    return
  }
  if (phoneStatus.checked && !phoneStatus.valid) {
    ElMessage.error('手机号已注册，请更换')
    return
  }

  loading.value = true
  try {
    await registerApi({
      username: formData.username,
      phone: formData.phone,
      password: formData.password,
      role: formData.role
    })
    
    ElMessage.success({
      message: '注册成功！正在跳转到登录页...',
      duration: 2000,
      showClose: true
    })
    
    // 延迟跳转，让用户看到成功提示
    setTimeout(() => {
      router.push('/login')
    }, 1500)
  } catch (error) {
    // 明确处理各种错误情况
    const errorData = error.response?.data
    const errorMsg = errorData?.detail || errorData?.message || errorData?.error
    
    if (errorMsg) {
      // 显示后端返回的具体错误信息
      ElMessage.error({
        message: errorMsg,
        duration: 3000,
        showClose: true
      })
    } else if (error.response?.status === 400) {
      // 400错误通常是验证失败
      const errors = errorData
      if (errors) {
        const firstError = Object.values(errors).flat()[0]
        if (firstError) {
          ElMessage.error(firstError)
        }
      }
    } else {
      ElMessage.error('注册失败，请稍后重试')
    }
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.register-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  padding: 20px;
}

.register-card {
  width: 100%;
  max-width: 420px;
  background: #fff;
  border-radius: 16px;
  padding: 40px;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.3);
}

.register-header {
  text-align: center;
  margin-bottom: 32px;
}

.register-title {
  font-size: 28px;
  font-weight: 700;
  color: #333;
  margin: 0 0 8px;
}

.register-subtitle {
  font-size: 14px;
  color: #999;
  margin: 0;
}

.register-btn {
  width: 100%;
  height: 48px;
  font-size: 16px;
  border-radius: 8px;
}

.register-footer {
  text-align: center;
  margin-top: 24px;
}

.footer-text {
  color: #999;
  font-size: 14px;
}

.footer-link {
  color: #667eea;
  font-size: 14px;
  text-decoration: none;
  margin-left: 4px;
}

.footer-link:hover {
  text-decoration: underline;
}

.field-status {
  margin-top: 4px;
  font-size: 12px;
  line-height: 1.5;
}

.status-valid {
  color: #67c23a;
  display: flex;
  align-items: center;
  gap: 4px;
}

.status-invalid {
  color: #f56c6c;
  display: flex;
  align-items: center;
  gap: 4px;
}
</style>