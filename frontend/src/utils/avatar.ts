/**
 * 头像颜色（与后端 user.avatar_color / member.color 取值对应）。
 * 多页面共用，避免在各 view 里重复维护色表。
 */

/** 可选颜色标识 */
export const AVATAR_COLORS = ['blue', 'purple', 'green', 'orange'] as const

/** 颜色标识 → 展示背景色 */
export const AVATAR_BG: Record<string, string> = {
  blue: '#5b8cff',
  purple: '#8b6cff',
  green: '#2fc98a',
  orange: '#ff9f43'
}

/** 取某颜色的展示背景（未知值回退蓝色） */
export function avatarBg(color?: string | null): string {
  return AVATAR_BG[color || 'blue'] || AVATAR_BG.blue
}
