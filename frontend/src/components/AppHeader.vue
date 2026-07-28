<template>
  <el-header class="app-header">
    <div class="header-left">
      <span class="logo">房屋租赁平台</span>
    </div>
    <div class="header-right">
      <!-- 已登录：显示通知铃铛和用户信息 -->
      <template v-if="userStore.isLoggedIn">
        <NotificationBell />
        <el-dropdown @command="handleCommand">
          <span class="user-info">
            <el-icon><User /></el-icon>
            {{ userStore.userInfo?.username }}
            <el-icon><ArrowDown /></el-icon>
          </span>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="user">个人中心</el-dropdown-item>
              <el-dropdown-item command="favorites">我的收藏</el-dropdown-item>
              <el-dropdown-item command="browse-history">浏览历史</el-dropdown-item>
              <el-dropdown-item command="logout" divided>退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </template>
      <!-- 未登录：显示登录/注册按钮 -->
      <div v-else class="auth-buttons">
        <el-button type="primary" plain size="small" @click="router.push('/login')">登录</el-button>
        <el-button type="primary" size="small" @click="router.push('/register')">注册</el-button>
      </div>
    </div>
  </el-header>
</template>

<script setup>
import { useRouter } from 'vue-router'
import { ElMessageBox, ElMessage } from 'element-plus'
import { User, ArrowDown } from '@element-plus/icons-vue'
import { useUserStore } from '@/store/user'
import NotificationBell from './NotificationBell.vue'

const router = useRouter()
const userStore = useUserStore()

function handleCommand(command) {
  if (command === 'user') {
    router.push('/user')
  } else if (command === 'favorites') {
    router.push('/favorites')
  } else if (command === 'browse-history') {
    router.push('/browse-history')
  } else if (command === 'logout') {
    handleLogout()
  }
}

async function handleLogout() {
  try {
    await ElMessageBox.confirm('确定要退出登录吗？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    userStore.logout()
    ElMessage.success('已退出登录')
    router.push('/login')
  } catch {
    // 取消
  }
}
</script>

<style scoped>
.app-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background-color: #409eff;
  color: #fff;
  padding: 0 20px;
  height: 60px;
}

.logo {
  font-size: 20px;
  font-weight: bold;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 16px;
}

.user-info {
  cursor: pointer;
  display: flex;
  align-items: center;
  gap: 4px;
  color: #fff;
}

.auth-buttons {
  display: flex;
  gap: 8px;
}

.auth-buttons .el-button--primary.is-plain {
  background: transparent;
  border-color: #fff;
  color: #fff;
}

.auth-buttons .el-button--primary.is-plain:hover {
  background: rgba(255, 255, 255, 0.2);
}
</style>