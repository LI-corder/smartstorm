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
  /** 自定义头像 URL；为空表示未上传，前端回退到颜色块 */
  avatarUrl?: string | null
}

/** 个人中心：用户信息（含注册时间） */
export interface ProfileUser {
  id: number
  email: string
  nickname: string
  avatarColor: string
  /** 自定义头像 URL；为空表示未上传，前端回退到颜色块 */
  avatarUrl?: string | null
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

/**
 * 在线用户（WebSocket user_list 里的 user）。
 * 游客没有头像，所以 avatarUrl 为可选；服务端对「无头像」统一给空串而非 null。
 */
export interface OnlineUser {
  userId: string
  userName: string
  color: string
  avatarUrl?: string | null
}

/**
 * op / cursor 广播里的操作者信息。
 *
 * 注意键名与 OnlineUser **不同**：这一路是 `id` / `name`，user_list 那一路是
 * `userId` / `userName`。两套是服务端分别构造的，加字段时别弄混。
 */
export interface BroadcastUser {
  id: string
  name: string
  color: string
  avatarUrl?: string | null
}

/** 统一响应 */
export interface Result<T = unknown> {
  code: number
  message: string
  data: T
}

/** WebSocket 入站消息 */
export interface WSInbound {
  type: 'room_state' | 'user_list' | 'op' | 'cursor' | 'analysis' | 'chain_anchor' | 'error'
  seq?: number
  /** op / cursor 广播里的操作者信息（注意：这里的键名是 id/name，与 user_list 的 userId/userName 不同） */
  user?: { id: string; name: string; color: string; avatarUrl?: string | null }
  data?: unknown
  message?: string
}

/** 存证：房间链状态摘要（与后端 ChainStatusVO 对应） */
export interface ChainStatus {
  roomId: number
  /** 存证链是否已启用。false 时面板显示"链未连接"，不是错误 */
  chainEnabled: boolean
  totalOps: number
  maxSeq: number | null
  /** 已被某个批次覆盖到的 seq（含尚未上链的批次） */
  anchoredSeq: number | null
  /** 尚未被任何批次覆盖的操作数 */
  unanchoredCount: number
  anchorCount: number
  confirmedCount: number
  pendingCount: number
  /** 当前链头哈希 */
  headHash: string
}

/** 存证：一个锚点（与后端 AnchorVO 对应） */
export interface AnchorRecord {
  id: number
  roomId: number
  fromSeq: number
  toSeq: number
  opCount: number
  merkleRoot: string
  /** 链上交易哈希；未上链时为 null */
  txHash: string | null
  blockNumber: number | null
  /** 0待上链 1已上链 2失败 */
  status: number
  statusText: string
  createdAt: string
  confirmedAt: string
}

/** 存证：哈希链完整性校验结果（与后端 ChainVerifyResult 对应） */
export interface ChainVerifyResult {
  ok: boolean
  totalOps: number
  /** 首个出问题的 seq；链完整时为 null */
  brokenSeq: number | null
  reason: string | null
}

/** 存证：单条操作的 Merkle 存在性证明（与后端 MerkleProofVO 对应） */
export interface MerkleProof {
  seq: number
  leafHash: string
  merkleRoot: string
  fromSeq: number
  toSeq: number
  txHash: string | null
  path: { sibling: string; position: 'left' | 'right' }[]
}

/** 区块信息：区块里的一笔交易（与后端 BlockTxVO 对应） */
export interface BlockTx {
  txHash: string
  /** 是否本系统提交的存证交易 */
  ours: boolean
  /** 以下四个字段仅当 ours=true 时有值 */
  roomId: number | null
  fromSeq: number | null
  toSeq: number | null
  merkleRoot: string | null
}

/** 区块信息：一个区块（与后端 ChainBlockVO 对应） */
export interface ChainBlock {
  number: number
  hash: string
  parentHash: string | null
  /** 已由后端格式化为 yyyy-MM-dd HH:mm:ss */
  timestamp: string
  /** Unix 秒 */
  timestampSeconds: number | null
  /** 真实交易总数。明细列表可能被截断，以这个为准 */
  txCount: number
  transactions: BlockTx[]
  /** 出块节点在共识列表中的下标（从 0 开始） */
  sealerIndex: number | null
  sealerCount: number | null
  stateRoot: string | null
  transactionsRoot: string | null
  version: number | null
  gasUsed: string | null
}

/** 区块信息：链概览（与后端 ChainOverviewVO 对应） */
export interface ChainOverview {
  /** 存证链是否已启用 */
  chainEnabled: boolean
  /** 以下链上字段在链不可用时为 null，前端显示"—"而非 0 */
  blockNumber: number | null
  txCount: number | null
  failedTxCount: number | null
  sealerCount: number | null
  /** 本系统统计不依赖链，始终有值 */
  anchorTotal: number
  anchorConfirmed: number
}

/** WebSocket 操作消息（op 的 data） */
export interface OpMessage {
  type: 'add_note' | 'edit_note' | 'move_note' | 'color_note' | 'delete_note'
  payload: Record<string, unknown>
}
