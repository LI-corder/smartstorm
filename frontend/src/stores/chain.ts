import { defineStore } from 'pinia'
import type { AnchorRecord, ChainStatus, ChainVerifyResult } from '@/types'

/**
 * 区块链存证状态。
 *
 * 数据有两条来源，与智能整理面板同构：
 * - 自己操作走 REST（手动锚定 / 刷新）
 * - 其他成员触发的锚定走 WebSocket 广播（类型 chain_anchor）
 * 按 id 去重，所以两条路径同时到达也不会重复渲染。
 */
export const useChainStore = defineStore('chain', {
  state: () => ({
    /** 房间链状态摘要 */
    status: null as ChainStatus | null,
    /** 锚点列表，最近的在前 */
    anchors: [] as AnchorRecord[],
    /** 最近一次完整性校验结果 */
    verifyResult: null as ChainVerifyResult | null,
    /** 面板是否展开 */
    panelOpen: false,
    /** 面板内是否有请求在飞 */
    loading: false
  }),

  getters: {
    /** 面板是否可见 */
    active: (state) => state.panelOpen,
    /** 最新一个锚点 */
    latest: (state): AnchorRecord | null => (state.anchors.length > 0 ? state.anchors[0] : null),
    /** 存证链是否已启用 */
    chainEnabled: (state) => state.status?.chainEnabled ?? false
  },

  actions: {
    setStatus(s: ChainStatus) {
      this.status = s
    },

    setAnchors(list: AnchorRecord[]) {
      this.anchors = list
    },

    /** 新增一个锚点。按 id 去重：手动触发与 WS 广播可能同时到达 */
    pushAnchor(a: AnchorRecord) {
      if (!a || this.anchors.some((x) => x.id === a.id)) return
      this.anchors.unshift(a)
    },

    setVerifyResult(r: ChainVerifyResult) {
      this.verifyResult = r
    },

    /** 画布上的操作变了，之前的校验结论就作废了 */
    clearVerifyResult() {
      this.verifyResult = null
    },

    open() {
      this.panelOpen = true
    },

    close() {
      this.panelOpen = false
    },

    toggle() {
      this.panelOpen = !this.panelOpen
    }
  }
})
