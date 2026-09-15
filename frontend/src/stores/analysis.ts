import { defineStore } from 'pinia'
import type { AnalysisData } from '@/types'

/**
 * 智能整理结果（当前房间展示的那一次）。
 *
 * 结果可来自两条路径：触发人的 REST 返回 与 房间 WebSocket "analysis" 广播。
 * 二者携带同一个 analysis id，这里按 id 幂等去重，避免重复渲染。
 */
export const useAnalysisStore = defineStore('analysis', {
  state: () => ({
    current: null as AnalysisData | null
  }),

  getters: {
    active: (state) => !!state.current
  },

  actions: {
    /** 应用一次整理结果；只接受比当前更新的（id 更大） */
    setAnalysis(data: AnalysisData) {
      if (!data || !data.id) return
      if (!this.current || data.id > this.current.id) {
        this.current = data
      }
    },

    /** 清除当前覆盖层（本地隐藏；他人不受影响） */
    clear() {
      this.current = null
    }
  }
})
