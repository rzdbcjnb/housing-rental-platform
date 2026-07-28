<template>
  <el-menu
    :default-active="route.path"
    router
    class="app-sidebar"
    background-color="#304156"
    text-color="#bfcbd9"
    active-text-color="#409eff"
  >
    <el-menu-item index="/">
      <el-icon><HomeFilled /></el-icon>
      <span>首页</span>
    </el-menu-item>
    <el-menu-item index="/houses">
      <el-icon><House /></el-icon>
      <span>房源列表</span>
    </el-menu-item>
    <el-menu-item v-if="userStore.isAdmin || userStore.isLandlord" index="/house/publish">
      <el-icon><Plus /></el-icon>
      <span>发布房源</span>
    </el-menu-item>
    <el-menu-item v-if="userStore.isAdmin || userStore.isLandlord" index="/recommend">
      <el-icon><Promotion /></el-icon>
      <span>推荐管理</span>
    </el-menu-item>
    <el-menu-item v-if="userStore.isLoggedIn" index="/user">
      <el-icon><UserFilled /></el-icon>
      <span>个人中心</span>
    </el-menu-item>
    <el-menu-item v-if="userStore.isLoggedIn" index="/favorites">
      <el-icon><StarFilled /></el-icon>
      <span>我的收藏</span>
    </el-menu-item>
    <el-menu-item v-if="userStore.isLoggedIn" index="/browse-history">
      <el-icon><Clock /></el-icon>
      <span>浏览历史</span>
    </el-menu-item>
    <el-menu-item v-if="userStore.isLoggedIn" index="/chat">
      <el-icon><ChatDotRound /></el-icon>
      <span>聊天消息</span>
    </el-menu-item>
    <el-menu-item v-if="userStore.isAdmin" index="/admin">
      <el-icon><Setting /></el-icon>
      <span>后台管理</span>
    </el-menu-item>
  </el-menu>
</template>

<script setup>
import { useRoute } from 'vue-router'
import { HomeFilled, UserFilled, Plus, Setting, House, StarFilled, Clock, ChatDotRound, Promotion } from '@element-plus/icons-vue'
import { useUserStore } from '@/store/user'

const route = useRoute()
const userStore = useUserStore()
</script>

<style scoped>
.app-sidebar {
  height: 100%;
  border-right: none;
}
</style>