import http from './http'
import type { ProfileData, Result, User } from '@/types'

/** 个人中心聚合数据（用户信息 + 统计 + 我创建的 / 我参与的房间） */
export function getProfile() {
  return http.get<Result<ProfileData>>('/profile')
}

/** 编辑昵称 / 头像颜色 */
export function updateProfile(data: { nickname: string; avatarColor: string }) {
  return http.put<Result<User>>('/profile', data)
}

/** 修改密码（需原密码） */
export function changePassword(data: { oldPassword: string; newPassword: string }) {
  return http.post<Result<null>>('/profile/change-password', data)
}
