<template>
  <div class="login-page">
    <div class="login-card">
      <div class="login-header">
        <h2 class="login-title">欢迎回来</h2>
        <p class="login-subtitle">登录您的账户以继续</p>
      </div>

      <el-form
        ref="formRef"
        :model="formData"
        :rules="rules"
        label-position="top"
        size="large"
        @keyup.enter="handleLogin"
      >
        <el-form-item label="用户名" prop="username">
          <el-input
            v-model="formData.username"
            placeholder="请输入用户名"
            prefix-icon="User"
            clearable
          />
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

        <el-form-item>
          <el-button
            type="primary"
            :loading="loading"
            class="login-btn"
            @click="handleLogin"
          >
            {{ loading ? '登录中...' : '登 录' }}
          </el-button>
        </el-form-item>
      </el-form>

      <div class="login-footer">
        <span class="footer-text">还没有账户？</span>
        <router-link to="/register" class="footer-link">立即注册</router-link>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { loginApi } from '@/api/user'
import { useUserStore } from '@/store/user'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const formRef = ref(null)
const loading = ref(false)

const formData = reactive({
  username: '',
  password: ''
})

const rules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 3, max: 20, message: '用户名长度在 3 到 20 个字符', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 20, message: '密码长度在 6 到 20 个字符', trigger: 'blur' }
  ]
}

async function handleLogin() {
  if (!formRef.value) return
  
  try {
    await formRef.value.validate()
  } catch {
    return
  }

  loading.value = true
  try {
    const res = await loginApi({
      username: formData.username,
      password: formData.password
    })
    
    // 处理API返回的数据格式
    const token = res.tokens?.access || res.data?.tokens?.access || res.token || res.data?.token
    const refreshToken = res.tokens?.refresh || res.data?.tokens?.refresh || res.refresh || res.data?.refresh
    const userInfo = res.user || res.data?.user || { username: formData.username }
    
    // 存储 token 和用户信息
    userStore.login({
      token,
      refreshToken,
      userInfo
    })

    ElMessage.success('登录成功')

    // 根据角色跳转到不同页面
    const userRole = userInfo.role
    let redirect = route.query.redirect
    
    if (!redirect) {
      // 管理员跳转到后台管理，其他用户跳转到首页
      redirect = userRole === 'admin' ? '/admin' : '/'
    }
    
    router.push(redirect)
  } catch (error) {
    // 明确处理各种错误情况
    const errorData = error.response?.data
    const errorMsg = errorData?.detail || errorData?.message || errorData?.error
    const statusCode = error.response?.status
    
    if (statusCode === 403) {
      // 403错误通常是账号被禁用
      ElMessage.error({
        message: errorMsg || '账号已被禁用，请联系管理员',
        duration: 3000,
        showClose: true
      })
    } else if (statusCode === 401) {
      // 401错误是用户名或密码错误
      ElMessage.error({
        message: errorMsg || '用户名或密码错误',
        duration: 3000,
        showClose: true
      })
    } else if (errorMsg) {
      // 显示后端返回的具体错误信息
      ElMessage.error({
        message: errorMsg,
        duration: 3000,
        showClose: true
      })
    } else {
      ElMessage.error({
        message: '登录失败，请稍后重试',
        duration: 3000,
        showClose: true
      })
    }
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-page {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: calc(100vh - 200px);
}

.login-card {
  width: 100%;
  max-width: 420px;
  padding: 40px;
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 4px 24px rgba(0, 0, 0, 0.08);
}

.login-header {
  text-align: center;
  margin-bottom: 32px;
}

.login-title {
  font-size: 28px;
  font-weight: 600;
  color: #1a1a2e;
  margin-bottom: 8px;
}

.login-subtitle {
  font-size: 14px;
  color: #8c8c8c;
}

.login-btn {
  width: 100%;
  height: 44px;
  font-size: 16px;
  border-radius: 8px;
}

.login-footer {
  text-align: center;
  margin-top: 24px;
  padding-top: 20px;
  border-top: 1px solid #f0f0f0;
}

.footer-text {
  color: #8c8c8c;
  font-size: 14px;
}

.footer-link {
  color: #409eff;
  text-decoration: none;
  font-size: 14px;
  margin-left: 4px;
  font-weight: 500;
}

.footer-link:hover {
  color: #337ecc;
}

:deep(.el-form-item__label) {
  font-weight: 500;
  color: #1a1a2e;
}
</style>