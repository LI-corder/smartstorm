import http from './http'
import type { Result, User } from '@/types'

export interface LoginResponse {
  token: string
  user: User
}

/** 发送注册验证码 */
export function sendCode(email: string) {
  return http.post<Result<null>>('/auth/send-code', { email })
}

/** 发送重置密码验证码 */
export function sendResetCode(email: string) {
  return http.post<Result<null>>('/auth/send-reset-code', { email })
}

/** 重置密码 */
export function resetPassword(data: { email: string; code: string; password: string }) {
  return http.post<Result<null>>('/auth/reset-password', data)
}

/** 注册（注册即登录，返回 token） */
export function register(data: { email: string; code: string; password: string; nickname: string }) {
  return http.post<Result<LoginResponse>>('/auth/register', data)
}

/** 登录 */
export function login(data: { email: string; password: string }) {
  return http.post<Result<LoginResponse>>('/auth/login', data)
}

/** 当前登录用户 */
export function getMe() {
  return http.get<Result<User>>('/auth/me')
}
