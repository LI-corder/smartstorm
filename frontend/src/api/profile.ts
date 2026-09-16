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

/**
 * 上传自定义头像（multipart，字段名 file）。
 *
 * <p>不手动设置 Content-Type —— 浏览器/axios 会自动带上 multipart 的 boundary，
 * 手动写死反而会导致后端解析失败。</p>
 */
export function uploadAvatar(file: File) {
  const form = new FormData()
  form.append('file', file)
  return http.post<Result<User>>('/profile/avatar', form, { timeout: 30000 })
}

/** 移除自定义头像，回退到颜色块 */
export function removeAvatar() {
  return http.delete<Result<User>>('/profile/avatar')
}

/** 修改密码（需原密码） */
export function changePassword(data: { oldPassword: string; newPassword: string }) {
  return http.post<Result<null>>('/profile/change-password', data)
}
