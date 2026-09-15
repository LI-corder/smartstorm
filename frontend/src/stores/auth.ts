import { defineStore } from 'pinia'
import type { User } from '@/types'
import { getMe } from '@/api/auth'

/**
 * 登录状态（持久化到 localStorage）。
 * 可选登录：未登录时 isLoggedIn=false，不影响匿名使用白板。
 */
export const useAuthStore = defineStore('auth', {
  state: () => ({
    token: localStorage.getItem('sc_token') || '',
    user: null as User | null
  }),

  getters: {
    isLoggedIn: (state) => !!state.token && !!state.user,
    /** 供白板使用的登录身份（未登录返回 null） */
    loggedInUser: (state): { userId: string; userName: string; color: string } | null => {
      if (!state.token || !state.user) return null
      return {
        userId: String(state.user.id),
        userName: state.user.nickname,
        color: state.user.avatarColor
      }
    }
  },

  actions: {
    /** 登录/注册成功后写入凭证 */
    setAuth(token: string, user: User) {
      this.token = token
      this.user = user
      localStorage.setItem('sc_token', token)
    },

    /** 用本地 token 拉取当前用户（页面刷新后恢复登录态） */
    async fetchMe() {
      if (!this.token) return
      try {
        const res = await getMe()
        if (res.data.code === 0) {
          this.user = res.data.data
        }
      } catch {
        this.logout()
      }
    },

    /** 资料编辑后同步本地用户（导航栏头像 / 昵称即时刷新） */
    setUser(user: User) {
      this.user = user
    },

    logout() {
      this.token = ''
      this.user = null
      localStorage.removeItem('sc_token')
    }
  }
})
