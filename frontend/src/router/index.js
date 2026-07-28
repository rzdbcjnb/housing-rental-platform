import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '@/store/user'

// 公开页面（无需登录）
const publicRouteNames = ['Home', 'HouseList', 'HouseDetail', 'Login', 'Register']

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/Login.vue'),
    meta: { title: '登录' }
  },
  {
    path: '/register',
    name: 'Register',
    component: () => import('@/views/Register.vue'),
    meta: { title: '注册' }
  },
  {
    path: '/',
    name: 'Home',
    component: () => import('@/views/Home.vue'),
    meta: { title: '首页' }
  },
  {
    path: '/houses',
    name: 'HouseList',
    component: () => import('@/views/HouseList.vue'),
    meta: { title: '房源列表' }
  },
  {
    path: '/recommend',
    name: 'RecommendManage',
    component: () => import('@/views/RecommendManage.vue'),
    meta: { title: '推荐管理', requiresAuth: true }
  },
  {
    path: '/houses/:id',
    name: 'HouseDetail',
    component: () => import('@/views/HouseDetail.vue'),
    meta: { title: '房源详情' }
  },
  {
    path: '/house/publish',
    name: 'PublishHouse',
    component: () => import('@/views/PublishHouse.vue'),
    meta: { title: '发布房源', requiresAuth: true }
  },
  {
    path: '/house/edit/:id',
    name: 'EditHouse',
    component: () => import('@/views/EditHouse.vue'),
    meta: { title: '编辑房源', requiresAuth: true }
  },
  {
    path: '/user',
    name: 'UserCenter',
    component: () => import('@/views/UserCenter.vue'),
    meta: { title: '个人中心', requiresAuth: true }
  },
  {
    path: '/favorites',
    name: 'Favorites',
    component: () => import('@/views/Favorites.vue'),
    meta: { title: '我的收藏', requiresAuth: true }
  },
  {
    path: '/browse-history',
    name: 'BrowseHistory',
    component: () => import('@/views/BrowseHistory.vue'),
    meta: { title: '浏览历史', requiresAuth: true }
  },
  {
    path: '/chat',
    name: 'ChatList',
    component: () => import('@/views/ChatList.vue'),
    meta: { title: '聊天消息', requiresAuth: true }
  },
  {
    path: '/chat/:id',
    name: 'ChatRoom',
    component: () => import('@/views/ChatRoom.vue'),
    meta: { title: '聊天', requiresAuth: true }
  },
  {
    path: '/ai-chat',
    name: 'AIChat',
    component: () => import('@/views/AIChat.vue'),
    meta: { requiresAuth: true, title: 'AI客服' }
  },
  {
    path: '/notifications',
    name: 'Notifications',
    component: () => import('@/views/Notifications.vue'),
    meta: { title: '系统消息', requiresAuth: true }
  },
  {
    path: '/admin',
    name: 'AdminDashboard',
    component: () => import('@/views/AdminDashboard.vue'),
    meta: { title: '后台管理', requiresAuth: true, requiredRole: 'admin' }
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

// 路由守卫
router.beforeEach((to, from, next) => {
  document.title = to.meta.title ? to.meta.title + ' - 房屋租赁平台' : '房屋租赁平台'

  const userStore = useUserStore()

  // 公开页面不需要认证
  if (publicRouteNames.includes(to.name)) {
    // 已登录用户访问登录/注册页时跳转首页
    if ((to.name === 'Login' || to.name === 'Register') && userStore.isLoggedIn) {
      next({ name: 'Home' })
      return
    }
    next()
    return
  }

  // 需要认证的页面
  if (!userStore.isLoggedIn) {
    next({ name: 'Login', query: { redirect: to.fullPath } })
    return
  }

  // 需要管理员权限但用户不是 admin → 跳转首页
  if (to.meta.requiredRole === 'admin' && !userStore.isAdmin) {
    next({ name: 'Home' })
    return
  }

  next()
})

export default router