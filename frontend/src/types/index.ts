/**
 * 共享类型定义。
 * 与后端协议/表结构对应。
 */

/** 便利贴（与后端 NoteVO 对应） */
export interface Note {
  id: number
  roomId: number
  userId: number
  userName: string
  userColor: string
  x: number
  y: number
  width: number
  height: number
  color: string
  content: string
  zIndex: number
  createdAt: string
}

/** 智能整理：一组便利贴 */
export interface AnalysisGroup {
  name: string
  noteIds: number[]
  summary?: string
}

/** 智能整理：一对冲突便利贴 */
export interface AnalysisConflict {
  noteA: number
  noteB: number
  reason: string
}

/** 智能整理结果（后端 AnalysisVO 的 data） */
export interface AnalysisData {
  id: number
  roomId: number
  noteCount: number
  model?: string
  createdAt?: string
  summary?: string
  groups: AnalysisGroup[]
  conflicts: AnalysisConflict[]
}

/** 房间 */
export interface Room {
  id: number
  roomCode: string
  name: string
  ownerId: number | null
  createdAt: string | null
}

/** 房间成员 */
export interface Member {
  id: number
  roomId: number
  userId: number
  userName: string
  color: string
  joinedAt: string
}

/** 登录用户（与后端 UserVO 对应） */
export interface User {
  id: number
  email: string
  nickname: string
  avatarColor: string
}

/** 个人中心：用户信息（含注册时间） */
export interface ProfileUser {
  id: number
  email: string
  nickname: string
  avatarColor: string
  createdAt: string
}

/** 个人中心：一条房间记录（附成员数 / 便利贴数） */
export interface RoomItem {
  id: number
  roomCode: string
  name: string
  ownerId: number | null
  memberCount: number
  noteCount: number
  createdAt: string
}

/** 个人中心：统计数字 */
export interface ProfileStats {
  createdRooms: number
  participatedRooms: number
  notes: number
  analyses: number
}

/** 个人中心聚合数据（与后端 ProfileVO 对应） */
export interface ProfileData {
  user: ProfileUser
  stats: ProfileStats
  ownedRooms: RoomItem[]
  joinedRooms: RoomItem[]
}

/** 在线用户（WebSocket user_list / cursor 里的 user） */
export interface OnlineUser {
  userId: string
  userName: string
  color: string
}

/** 统一响应 */
export interface Result<T = unknown> {
  code: number
  message: string
  data: T
}

/** WebSocket 入站消息 */
export interface WSInbound {
  type: 'room_state' | 'user_list' | 'op' | 'cursor' | 'analysis' | 'error'
  seq?: number
  user?: { id: string; name: string; color: string }
  data?: unknown
  message?: string
}

/** WebSocket 操作消息（op 的 data） */
export interface OpMessage {
  type: 'add_note' | 'edit_note' | 'move_note' | 'color_note' | 'delete_note'
  payload: Record<string, unknown>
}
