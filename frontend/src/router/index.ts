import { createRouter, createWebHistory } from 'vue-router'
import HomeView from '@/views/HomeView.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      name: 'home',
      component: HomeView
    },
    {
      // 个人中心
      path: '/profile',
      name: 'profile',
      component: () => import('@/views/ProfileView.vue')
    },
    {
      // 第二阶段：白板页（画布 + 实时协同）
      path: '/board/:roomId',
      name: 'board',
      component: () => import('@/views/BoardView.vue')
    },
    {
      // 第四阶段：区块信息（区块链浏览器，匿名可访问）
      path: '/chain',
      name: 'chain',
      component: () => import('@/views/ChainView.vue')
    },
    {
      // 登录 / 注册
      path: '/login',
      name: 'login',
      component: () => import('@/views/LoginView.vue')
    },
    {
      // 忘记密码
      path: '/reset-password',
      name: 'reset-password',
      component: () => import('@/views/ResetPasswordView.vue')
    }
  ]
})

export default router
