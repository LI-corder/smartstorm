import http from './http'
import type { AnalysisData, Result } from '@/types'

/** 触发一次智能整理（需登录）。DeepSeek 分析较慢，需单独放宽超时。 */
export function analyzeRoom(roomId: number) {
  return http.post<Result<AnalysisData>>(`/rooms/${roomId}/analysis`, null, {
    timeout: 120000
  })
}

/** 最近一次智能整理结果（无则 data 为 null） */
export function getLatestAnalysis(roomId: number) {
  return http.get<Result<AnalysisData | null>>(`/rooms/${roomId}/analysis/latest`)
}
