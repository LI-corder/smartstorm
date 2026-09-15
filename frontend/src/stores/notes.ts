import { defineStore } from 'pinia'
import type { Note } from '@/types'

/**
 * 便利贴状态（单一数据源）。
 *
 * 关键设计：
 * - notes 是 Map<id, Note>，本地操作先"乐观更新"，收到 op 回执后确认；
 * - 远端 op 按服务器分配的 seq 顺序应用，保证各客户端一致。
 */
export const useNotesStore = defineStore('notes', {
  state: () => ({
    notes: {} as Record<number, Note>,
    /** 本地已应用的最大 seq（用于断线重连时增量同步） */
    lastSeq: 0
  }),

  getters: {
    noteList: (state): Note[] => Object.values(state.notes)
  },

  actions: {
    /** 全量加载（room_state 快照） */
    setNotes(list: Note[]) {
      const map: Record<number, Note> = {}
      for (const n of list) {
        map[n.id] = n
      }
      this.notes = map
    },

    /** 应用一条 op（本地乐观更新 / 远端广播都走这里） */
    applyOp(type: string, payload: Record<string, unknown>, userName?: string, userColor?: string) {
      switch (type) {
        case 'add_note':
          this.notes[payload.id as number] = {
            id: payload.id as number,
            roomId: 0,
            userId: 0,
            userName: userName || '我',
            userColor: userColor || 'blue',
            x: (payload.x as number) ?? 0,
            y: (payload.y as number) ?? 0,
            width: (payload.width as number) ?? 160,
            height: (payload.height as number) ?? 100,
            color: (payload.color as string) || 'yellow',
            content: (payload.content as string) || '',
            zIndex: 0,
            createdAt: ''
          }
          break
        case 'edit_note': {
          const n = this.notes[payload.id as number]
          if (n) n.content = (payload.content as string) ?? ''
          break
        }
        case 'move_note': {
          const n = this.notes[payload.id as number]
          if (n) {
            n.x = (payload.x as number) ?? n.x
            n.y = (payload.y as number) ?? n.y
          }
          break
        }
        case 'color_note': {
          const n = this.notes[payload.id as number]
          if (n) n.color = (payload.color as string) ?? n.color
          break
        }
        case 'delete_note':
          delete this.notes[payload.id as number]
          break
      }
    },

    /** 乐观新增（本地先生成占位 id，收到回执后替换为真实 id） */
    optimisticAdd(data: { x: number; y: number; content: string; color: string }): Note {
      const tempId = -Date.now()
      const note: Note = {
        id: tempId,
        roomId: 0,
        userId: 0,
        userName: '我',
        userColor: 'blue',
        x: data.x,
        y: data.y,
        width: 160,
        height: 100,
        color: data.color,
        content: data.content,
        zIndex: 0,
        createdAt: ''
      }
      this.notes[tempId] = note
      return note
    },

    /** 把乐观临时 id 替换成服务器真实 id */
    confirmAdd(tempId: number, realId: number) {
      const note = this.notes[tempId]
      if (note) {
        delete this.notes[tempId]
        note.id = realId
        this.notes[realId] = note
      }
    },

    removeNote(id: number) {
      delete this.notes[id]
    }
  }
})
