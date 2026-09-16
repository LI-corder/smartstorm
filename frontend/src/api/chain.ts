import http from './http'
import type {
  AnchorRecord,
  ChainBlock,
  ChainOverview,
  ChainStatus,
  ChainVerifyResult,
  MerkleProof,
  Result
} from '@/types'

/** 房间存证状态摘要 */
export function getChainStatus(roomId: number) {
  return http.get<Result<ChainStatus>>(`/rooms/${roomId}/chain/status`)
}

/**
 * 校验哈希链完整性。
 * 这个接口不依赖区块链 —— 链没配好也照样能验本地哈希链。
 */
export function verifyChain(roomId: number) {
  return http.get<Result<ChainVerifyResult>>(`/rooms/${roomId}/chain/verify`)
}

/** 锚点列表（最近的在前） */
export function listAnchors(roomId: number) {
  return http.get<Result<AnchorRecord[]>>(`/rooms/${roomId}/chain/anchors`)
}

/** 某条操作的 Merkle 存在性证明 */
export function getMerkleProof(roomId: number, seq: number) {
  return http.get<Result<MerkleProof>>(`/rooms/${roomId}/chain/proof`, { params: { seq } })
}

/** 手动触发一次锚定（需登录）。上链可能较慢，单独放宽超时。 */
export function anchorNow(roomId: number) {
  return http.post<Result<AnchorRecord>>(`/rooms/${roomId}/chain/anchor`, null, {
    timeout: 60000
  })
}

// ---------------- 区块信息（链级，匿名可访问） ----------------

/** 链概览：区块高度、链上交易数、本系统存证批次统计 */
export function getChainOverview() {
  return http.get<Result<ChainOverview>>('/chain/overview')
}

/** 最新区块列表（高度从高到低）。上限由后端卡在 50 */
export function listBlocks(limit = 20) {
  return http.get<Result<ChainBlock[]>>('/chain/blocks', { params: { limit } })
}

/** 指定高度的区块详情 */
export function getBlock(number: number) {
  return http.get<Result<ChainBlock>>(`/chain/blocks/${number}`)
}
