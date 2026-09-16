/**
 * 长哈希 / 长十六进制串的截断显示。
 *
 * 约定：超过 20 字符才截断，取前 10 后 6，中间用真正的省略号 `…`（不是三个点）。
 * 原先这段逻辑散在 ChainPanel 里，区块信息页也要用，所以提出来共用。
 */
export function shortHash(v?: string | null): string {
  if (!v) return ''
  return v.length > 20 ? `${v.slice(0, 10)}…${v.slice(-6)}` : v
}
