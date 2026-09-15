import http from './http'
import type { Member, Note, Result, Room } from '@/types'

/** 创建房间 */
export function createRoom(name?: string) {
  return http.post<Result<Room>>('/rooms', { name })
}

/** 按房间号查询 */
export function getRoomByCode(code: string) {
  return http.get<Result<Room>>(`/rooms/code/${code}`)
}

/** 按 id 查询房间 */
export function getRoomById(id: number) {
  return http.get<Result<Room>>(`/rooms/${id}`)
}

/** 加入房间（幂等），返回成员信息 */
export function joinRoom(roomId: number, userId: number, userName: string) {
  return http.post<Result<Member>>(`/rooms/${roomId}/join`, { userId, userName })
}

/** 房间成员列表 */
export function listMembers(roomId: number) {
  return http.get<Result<Member[]>>(`/rooms/${roomId}/members`)
}

/** 房间便利贴快照 */
export function listNotes(roomId: number) {
  return http.get<Result<Note[]>>(`/rooms/${roomId}/notes`)
}
