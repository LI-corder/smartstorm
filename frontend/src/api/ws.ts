import { useNotesStore } from '@/stores/notes'
import type { AnalysisData, AnchorRecord, Note, OpMessage, WSInbound } from '@/types'

/**
 * 实时协同 WebSocket 客户端。
 *
 * 职责：
 * - 建立/断开连接（含自动重连）
 * - 发送各类操作消息
 * - 分发入站消息：room_state / user_list / op / cursor
 *
 * 关键逻辑（乐观更新）：
 * - 本地新增便利贴时先放入临时 id（负数），立即显示；
 * - 服务端对 add_note 的 op 回执回到本地时，用 FIFO 队列找到对应的临时 id，
 *   替换成服务端生成的真实 id；
 * - 远端 op 按服务端分配的 seq 顺序应用，保证各端一致。
 */
class WSClient {
  private ws: WebSocket | null = null
  private roomId = 0
  private myUserId = ''
  private myUserName = ''
  private myColor = ''
  private reconnectTimer: number | null = null
  private reconnectAttempts = 0
  private manuallyClosed = false
  /** 握手用的 JWT token（重连时复用） */
  private myToken = ''

  /** 我发起的 add_note 临时 id FIFO（服务端串行处理、按发送顺序回执） */
  private pendingAdds: number[] = []

  /** 连接状态回调 */
  onStatusChange?: (connected: boolean) => void
  /** 收到 user_list */
  onUserList?: (users: { userId: string; userName: string; color: string }[]) => void
  /** 收到 cursor */
  onCursor?: (data: { user: { id: string; name: string; color: string }; data: { x: number; y: number } }) => void
  /** 收到 error */
  onError?: (msg: string) => void
  /** 收到 analysis（智能整理结果广播） */
  onAnalysis?: (data: AnalysisData) => void
  /** 收到 chain_anchor（存证锚定上链完成广播） */
  onAnchor?: (data: AnchorRecord) => void
  /** 收到 room_state（通知 UI 渲染快照） */
  onRoomState?: () => void

  get connected() {
    return this.ws?.readyState === WebSocket.OPEN
  }

  connect(roomId: number, userId: string, userName: string, color = 'blue', token = '') {
    this.roomId = roomId
    this.myUserId = userId
    this.myUserName = userName
    this.myColor = color
    this.myToken = token
    this.manuallyClosed = false
    this.reconnectAttempts = 0
    this.open(token)
  }

  private open(token: string) {
    const proto = location.protocol === 'https:' ? 'wss' : 'ws'
    // 登录用户带 token 用于服务端写权限校验；游客不带
    const url = token
      ? `${proto}://${location.host}/ws?token=${encodeURIComponent(token)}`
      : `${proto}://${location.host}/ws`
    const ws = new WebSocket(url)
    this.ws = ws

    ws.onopen = () => {
      this.reconnectAttempts = 0
      this.onStatusChange?.(true)
      this.sendRaw({
        type: 'join_room',
        roomId: this.roomId,
        data: { userId: this.myUserId, userName: this.myUserName, color: this.myColor }
      })
    }

    ws.onclose = () => {
      this.onStatusChange?.(false)
      if (!this.manuallyClosed) {
        this.scheduleReconnect()
      }
    }

    ws.onmessage = (e) => this.handleMessage(e)
  }

  private scheduleReconnect() {
    if (this.reconnectTimer || this.manuallyClosed) return
    const delay = Math.min(1000 * 2 ** this.reconnectAttempts, 10000)
    this.reconnectAttempts++
    this.reconnectTimer = window.setTimeout(() => {
      this.reconnectTimer = null
      this.open(this.myToken)
    }, delay)
  }

  private handleMessage(e: MessageEvent) {
    let msg: WSInbound
    try {
      msg = JSON.parse(e.data) as WSInbound
    } catch {
      console.error('[WS] 消息解析失败', e.data)
      return
    }

    const notesStore = useNotesStore()

    switch (msg.type) {
      case 'room_state': {
        const data = msg.data as { notes: Note[] }
        notesStore.setNotes(data.notes ?? [])
        this.onRoomState?.()
        break
      }
      case 'user_list': {
        const data = msg.data as { users: { userId: string; userName: string; color: string }[] }
        this.onUserList?.(data.users ?? [])
        break
      }
      case 'op': {
        const op = msg.data as OpMessage
        const payload = op.payload ?? {}
        if (msg.seq) {
          notesStore.lastSeq = Math.max(notesStore.lastSeq, msg.seq)
        }

        if (op.type === 'add_note' && msg.user?.id === this.myUserId) {
          // 我自己的 add 回执：替换乐观占位 id
          const tempId = this.pendingAdds.shift()
          if (tempId != null) {
            notesStore.confirmAdd(tempId, payload.id as number)
          } else {
            notesStore.applyOp(op.type, payload, msg.user.name, msg.user.color)
          }
        } else {
          notesStore.applyOp(op.type, payload, msg.user?.name, msg.user?.color)
        }
        break
      }
      case 'cursor':
        this.onCursor?.(msg as never)
        break
      case 'analysis':
        this.onAnalysis?.(msg.data as AnalysisData)
        break
      case 'chain_anchor':
        this.onAnchor?.(msg.data as AnchorRecord)
        break
      case 'error':
        this.onError?.(msg.message || '未知错误')
        break
    }
  }

  // ---------- 发送 ----------

  private sendRaw(msg: Record<string, unknown>) {
    if (this.ws?.readyState === WebSocket.OPEN) {
      this.ws.send(JSON.stringify(msg))
    } else {
      console.warn('[WS] 连接未就绪，消息未发送', msg.type)
    }
  }

  addNote(data: { x: number; y: number; content: string; color: string }, tempId: number) {
    this.pendingAdds.push(tempId)
    this.sendRaw({ type: 'add_note', roomId: this.roomId, data })
  }

  editNote(id: number, content: string) {
    this.sendRaw({ type: 'edit_note', roomId: this.roomId, data: { id, content } })
  }

  moveNote(id: number, x: number, y: number) {
    this.sendRaw({ type: 'move_note', roomId: this.roomId, data: { id, x, y } })
  }

  colorNote(id: number, color: string) {
    this.sendRaw({ type: 'color_note', roomId: this.roomId, data: { id, color } })
  }

  deleteNote(id: number) {
    this.sendRaw({ type: 'delete_note', roomId: this.roomId, data: { id } })
  }

  sendCursor(x: number, y: number) {
    this.sendRaw({ type: 'cursor_move', roomId: this.roomId, data: { x, y } })
  }

  disconnect() {
    this.manuallyClosed = true
    if (this.reconnectTimer) {
      clearTimeout(this.reconnectTimer)
      this.reconnectTimer = null
    }
    this.ws?.close()
    this.ws = null
  }
}

export const wsClient = new WSClient()
