import http from './http'

/** 健康检查 */
export function fetchHealth() {
  return http.get('/health')
}

/** WebSocket 状态（骨架调试用） */
export function fetchWsStatus() {
  return http.get('/ws/status')
}
