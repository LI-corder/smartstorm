import { defineStore } from 'pinia'
import type { OnlineUser } from '@/types'

/**
 * 房间状态：当前房间、当前用户、在线成员。
 */
export const useRoomStore = defineStore('room', {
  state: () => ({
    roomId: 0,
    roomCode: '',
    roomName: '',
    /** 当前用户（会话期固定）。avatarUrl 为空表示未上传头像，回退到颜色块 */
    currentUser: {
      userId: '',
      userName: '访客',
      color: 'blue',
      avatarUrl: ''
    } as OnlineUser,
    onlineUsers: [] as OnlineUser[],
    connected: false
  }),
  actions: {
    init(roomId: number, roomCode: string, roomName: string, user: OnlineUser) {
      this.roomId = roomId
      this.roomCode = roomCode
      this.roomName = roomName
      this.currentUser = user
    },
    setConnected(connected: boolean) {
      this.connected = connected
    },
    setOnlineUsers(users: OnlineUser[]) {
      this.onlineUsers = users
    }
  }
})
