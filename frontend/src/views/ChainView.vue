<template>
  <div class="chain-page">
    <div class="chain-glow glow-1"></div>
    <div class="chain-glow glow-2"></div>

    <!-- ==================== 顶部导航 ==================== -->
    <header class="nav">
      <div class="nav-inner">
        <div class="brand" @click="$router.push('/')">
          <span class="brand-icon">🧠</span>
          <span class="brand-name">Smart<span class="grad-text">Storm</span></span>
        </div>
        <div class="nav-actions">
          <el-button class="btn-ghost" round :loading="loading" @click="refresh">
            <el-icon><Refresh /></el-icon>&nbsp;刷新
          </el-button>
          <el-button class="btn-ghost" round @click="$router.push('/')">← 返回首页</el-button>
        </div>
      </div>
    </header>

    <!-- ==================== 主体 ==================== -->
    <main class="chain-main">
      <!-- ========== 链概览 ========== -->
      <section class="panel">
        <div class="list-title">链概览</div>
        <div class="stat-grid">
          <div class="stat-tile">
            <span class="stat-num">{{ overview?.blockNumber ?? '—' }}</span>
            <span class="stat-label">当前区块高度</span>
          </div>
          <div class="stat-tile">
            <span class="stat-num">{{ overview?.txCount ?? '—' }}</span>
            <span class="stat-label">链上交易总数</span>
          </div>
          <div class="stat-tile">
            <span class="stat-num">{{ overview?.anchorTotal ?? 0 }}</span>
            <span class="stat-label">本系统存证批次</span>
          </div>
          <div class="stat-tile">
            <span class="stat-num">{{ overview?.anchorConfirmed ?? 0 }}</span>
            <span class="stat-label">已上链确认</span>
          </div>
        </div>

        <div class="conn-line" :class="connClass">
          <span class="conn-dot"></span>{{ connText }}
          <span v-if="overview?.sealerCount" class="conn-extra">
            · 共识节点 {{ overview.sealerCount }} 个
          </span>
        </div>
      </section>

      <!-- ========== 链不可用：降级说明 ========== -->
      <section v-if="!chainReachable" class="panel">
        <div class="list-title">区块</div>
        <div class="empty-box">
          <el-icon class="empty-icon"><InfoFilled /></el-icon>
          <p class="empty-title">{{ overview?.chainEnabled ? '存证链连接异常' : '存证链未启用' }}</p>
          <p v-if="overview?.chainEnabled" class="empty-text">
            后端已开启存证链（app.fisco.enabled=true），但当前连不上节点。
            请检查虚拟机里的 FISCO BCOS 是否已启动、证书与节点地址是否正确。
          </p>
          <p v-else class="empty-text">
            当前后端以 <code>app.fisco.enabled=false</code> 运行，因此读不到区块数据。
          </p>
          <p class="empty-text">
            这<b>不影响</b>存证功能本身：操作日志的哈希链、Merkle 根与存证批次都照常工作，
            可以在房间里的「区块链存证」面板查看。搭好 FISCO BCOS 并开启开关后，这里会显示真实区块。
          </p>
        </div>
      </section>

      <!-- ========== 区块列表 ========== -->
      <section v-else class="panel" v-loading="loading" element-loading-text="加载中…">
        <div class="list-title">
          最新区块
          <span class="title-hint">点击展开详情</span>
        </div>

        <div v-if="!blocks.length" class="empty-box">
          <p class="empty-text">链上还没有区块。</p>
        </div>

        <div v-for="b in blocks" :key="b.number" class="block-wrap">
          <div
            class="room-row"
            :class="{ expanded: expandedNumber === b.number }"
            @click="toggleBlock(b.number)"
          >
            <div class="row-left">
              <span class="block-no">#{{ b.number }}</span>
              <code class="hash">{{ shortHash(b.hash) }}</code>
            </div>
            <div class="row-right">
              <span class="row-meta">{{ b.timestamp }}</span>
              <span class="row-meta">{{ b.txCount }} 笔交易</span>
              <span v-if="hasOurs(b)" class="badge-ours">含存证</span>
              <el-icon class="row-caret" :class="{ open: expandedNumber === b.number }">
                <ArrowDown />
              </el-icon>
            </div>
          </div>

          <!-- 区块详情（内联展开） -->
          <div v-if="expandedNumber === b.number" class="block-detail">
            <div v-if="!expandedDetail" class="detail-loading">加载中…</div>
            <template v-else>
              <div class="kv-list">
                <div class="kv">
                  <span class="kv-k">区块哈希</span>
                  <code class="kv-v hash">{{ expandedDetail.hash }}</code>
                </div>
                <div v-if="expandedDetail.parentHash" class="kv">
                  <span class="kv-k">父区块哈希</span>
                  <code class="kv-v hash">{{ expandedDetail.parentHash }}</code>
                </div>
                <div class="kv">
                  <span class="kv-k">出块时间</span>
                  <span class="kv-v">{{ expandedDetail.timestamp }}</span>
                </div>
                <div v-if="expandedDetail.sealerIndex !== null" class="kv">
                  <span class="kv-k">出块节点</span>
                  <span class="kv-v">
                    第 {{ expandedDetail.sealerIndex + 1 }} 个
                    <template v-if="expandedDetail.sealerCount">
                      （共 {{ expandedDetail.sealerCount }} 个共识节点）
                    </template>
                  </span>
                </div>
                <div v-if="expandedDetail.stateRoot" class="kv">
                  <span class="kv-k">状态根</span>
                  <code class="kv-v hash">{{ expandedDetail.stateRoot }}</code>
                </div>
                <div v-if="expandedDetail.transactionsRoot" class="kv">
                  <span class="kv-k">交易根</span>
                  <code class="kv-v hash">{{ expandedDetail.transactionsRoot }}</code>
                </div>
                <div v-if="expandedDetail.gasUsed" class="kv">
                  <span class="kv-k">Gas 用量</span>
                  <span class="kv-v">{{ expandedDetail.gasUsed }}</span>
                </div>
              </div>

              <div class="list-title sub">交易 · {{ expandedDetail.txCount }}</div>
              <p
                v-if="expandedDetail.txCount > expandedDetail.transactions.length"
                class="detail-note"
              >
                仅显示前 {{ expandedDetail.transactions.length }} 笔。
              </p>
              <div v-if="!expandedDetail.transactions.length" class="detail-note">
                这个区块里没有交易（空块）。
              </div>
              <div
                v-for="tx in expandedDetail.transactions"
                :key="tx.txHash"
                class="tx-row"
                :class="{ ours: tx.ours }"
              >
                <code class="hash">{{ shortHash(tx.txHash) }}</code>
                <span v-if="tx.ours" class="tx-tag">
                  本系统存证 · 房间 {{ tx.roomId }} · seq {{ tx.fromSeq }}–{{ tx.toSeq }}
                </span>
                <span v-else class="tx-tag muted">其他交易</span>
              </div>
            </template>
          </div>
        </div>
      </section>
    </main>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getBlock, getChainOverview, listBlocks } from '@/api/chain'
import { shortHash } from '@/utils/hash'
import type { ChainBlock, ChainOverview } from '@/types'

const route = useRoute()

const loading = ref(false)
const overview = ref<ChainOverview | null>(null)
const blocks = ref<ChainBlock[]>([])
const expandedNumber = ref<number | null>(null)

/** 已拉取过的区块详情，按高度缓存，重复展开不再请求 */
const detailCache = reactive<Record<number, ChainBlock>>({})

const expandedDetail = computed(() =>
  expandedNumber.value === null ? null : (detailCache[expandedNumber.value] ?? null)
)

/**
 * 链是否可用。
 * 注意区分三种状态：未启用 / 已启用但连不上 / 正常 —— 它们的用户提示完全不同。
 */
const chainReachable = computed(
  () => !!overview.value?.chainEnabled && overview.value?.blockNumber != null
)

const connClass = computed(() => {
  if (!overview.value?.chainEnabled) return 'off'
  return overview.value.blockNumber == null ? 'warn' : 'ok'
})

const connText = computed(() => {
  if (!overview.value?.chainEnabled) return '存证链未启用（仅本地哈希链）'
  return overview.value.blockNumber == null ? '存证链已启用，但当前连接异常' : '存证链已连接'
})

/** 该区块是否含有本系统的存证交易 */
function hasOurs(b: ChainBlock) {
  return b.transactions?.some((t) => t.ours) ?? false
}

async function loadOverview() {
  try {
    const res = await getChainOverview()
    if (res.data.code === 0) overview.value = res.data.data
  } catch {
    // 概览失败不打断整页；下面区块区会走降级态
  }
}

async function loadBlocks() {
  try {
    const res = await listBlocks(20)
    if (res.data.code === 0) blocks.value = res.data.data ?? []
  } catch {
    ElMessage.error('加载区块列表失败')
  }
}

async function fetchDetail(number: number): Promise<ChainBlock | null> {
  if (detailCache[number]) return detailCache[number]
  try {
    const res = await getBlock(number)
    if (res.data.code === 0) {
      detailCache[number] = res.data.data
      return res.data.data
    }
  } catch {
    ElMessage.error(`加载区块 #${number} 详情失败`)
  }
  return null
}

async function toggleBlock(number: number) {
  if (expandedNumber.value === number) {
    expandedNumber.value = null
    return
  }
  expandedNumber.value = number
  await fetchDetail(number)
}

async function refresh() {
  loading.value = true
  try {
    await Promise.all([loadOverview(), loadBlocks()])
    // 刷新后原来的展开项可能已经不在列表里了
    if (expandedNumber.value !== null) {
      await fetchDetail(expandedNumber.value)
    }
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  loading.value = true
  try {
    await Promise.all([loadOverview(), loadBlocks()])

    // 支持从存证面板深链过来：/chain?block=42
    const q = Number(route.query.block)
    if (Number.isFinite(q) && q > 0) {
      // 目标区块可能不在"最新 20 个"里（比如从存证面板跳到较早的批量），
      // 此时补进列表顶部，让展开逻辑对两种情况一致
      if (!blocks.value.some((b) => b.number === q)) {
        const extra = await fetchDetail(q)
        if (extra) blocks.value = [extra, ...blocks.value]
      }
      expandedNumber.value = q
      await fetchDetail(q)
    }
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
/* ============ 页面壳（与 ProfileView 同款渐变 + 光晕） ============ */
.chain-page {
  position: relative;
  min-height: 100vh;
  background: linear-gradient(160deg, #f2f5ff 0%, #fbf5ff 55%, #fdf2f8 100%);
  overflow: hidden;
  padding-bottom: 60px;
}

.chain-glow {
  position: absolute;
  border-radius: 50%;
  filter: blur(80px);
  opacity: 0.5;
  pointer-events: none;
}

.glow-1 {
  width: 520px;
  height: 520px;
  top: -140px;
  left: -140px;
  background: radial-gradient(circle, rgba(102, 126, 234, 0.55), transparent 70%);
}

.glow-2 {
  width: 460px;
  height: 460px;
  bottom: -120px;
  right: -120px;
  background: radial-gradient(circle, rgba(139, 92, 246, 0.5), transparent 70%);
}

/* ============ 导航 ============ */
.nav {
  position: relative;
  z-index: 20;
  background: rgba(255, 255, 255, 0.82);
  backdrop-filter: blur(14px);
  -webkit-backdrop-filter: blur(14px);
  border-bottom: 1px solid rgba(231, 236, 247, 0.8);
}

.nav-inner {
  max-width: 1080px;
  margin: 0 auto;
  padding: 14px 24px;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.brand {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
}

.brand-name {
  font-size: 20px;
  font-weight: 700;
  color: var(--sc-text);
}

.nav-actions {
  display: flex;
  gap: 10px;
  align-items: center;
}

/* ============ 主体 ============ */
.chain-main {
  position: relative;
  z-index: 2;
  max-width: 1080px;
  margin: 28px auto 0;
  padding: 0 24px;
  display: flex;
  flex-direction: column;
  gap: 20px;
  min-height: 60vh;
}

.panel {
  background: rgba(255, 255, 255, 0.94);
  backdrop-filter: blur(12px);
  border: 1px solid var(--sc-border);
  border-radius: 20px;
  box-shadow: var(--sc-shadow);
  padding: 20px 24px 24px;
}

/* ============ 区块标题（品牌色竖条，同 ProfileView） ============ */
.list-title {
  padding-left: 10px;
  border-left: 4px solid var(--sc-primary);
  font-size: 16px;
  font-weight: 700;
  color: var(--sc-text);
  margin-bottom: 14px;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.list-title.sub {
  font-size: 14px;
  margin-top: 18px;
  margin-bottom: 10px;
}

.title-hint {
  font-size: 12px;
  font-weight: 400;
  color: var(--sc-text-muted);
}

/* ============ 统计块 ============ */
.stat-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
}

.stat-tile {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  padding: 16px 8px;
  background: #fafcff;
  border: 1px solid var(--sc-border);
  border-radius: 12px;
}

.stat-num {
  font-size: 26px;
  font-weight: 800;
  line-height: 1.1;
  background: linear-gradient(120deg, var(--sc-grad-1), var(--sc-grad-2));
  -webkit-background-clip: text;
  background-clip: text;
  -webkit-text-fill-color: transparent;
}

.stat-label {
  font-size: 12px;
  color: var(--sc-text-muted);
}

/* ============ 连接状态 ============ */
.conn-line {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 14px;
  font-size: 12px;
  color: var(--sc-text-secondary);
}

.conn-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex: 0 0 auto;
  background: #c9d1e4;
}

.conn-line.ok .conn-dot {
  background: #2fc98a;
}

.conn-line.warn .conn-dot {
  background: #ff9f43;
}

.conn-extra {
  color: var(--sc-text-muted);
}

/* ============ 区块列表行（复用项目既有列表行范式） ============ */
.block-wrap {
  margin-bottom: 10px;
}

.room-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 14px 18px;
  background: #fafcff;
  border: 1px solid var(--sc-border);
  border-radius: 12px;
  cursor: pointer;
  transition: all 0.2s var(--sc-ease);
}

.room-row:hover {
  border-color: var(--sc-primary-soft);
  box-shadow: var(--sc-shadow);
  transform: translateY(-1px);
}

.room-row.expanded {
  border-color: var(--sc-primary-soft);
  border-bottom-left-radius: 0;
  border-bottom-right-radius: 0;
}

.row-left {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
}

.block-no {
  font-weight: 700;
  color: var(--sc-primary);
  flex: 0 0 auto;
}

.row-right {
  display: flex;
  align-items: center;
  gap: 10px;
  flex: 0 0 auto;
}

.row-meta {
  font-size: 12px;
  color: var(--sc-text-muted);
}

.badge-ours {
  font-size: 11px;
  padding: 1px 8px;
  border-radius: 6px;
  background: #eafaf2;
  color: #1f8a5b;
  white-space: nowrap;
}

.row-caret {
  color: var(--sc-text-muted);
  transition: transform 0.2s var(--sc-ease);
}

.row-caret.open {
  transform: rotate(180deg);
}

/* ============ 区块详情 ============ */
.block-detail {
  border: 1px solid var(--sc-primary-soft);
  border-top: none;
  border-radius: 0 0 12px 12px;
  padding: 14px 18px 16px;
  background: #ffffff;
}

.detail-loading,
.detail-note {
  font-size: 12px;
  color: var(--sc-text-muted);
  padding: 4px 0;
}

.kv-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.kv {
  display: flex;
  gap: 10px;
  font-size: 12px;
  line-height: 1.6;
}

.kv-k {
  flex: 0 0 84px;
  color: var(--sc-text-muted);
}

.kv-v {
  color: var(--sc-text-secondary);
  word-break: break-all;
}

/* ============ 交易行 ============ */
.tx-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 8px 12px;
  border-radius: 8px;
  margin-bottom: 6px;
  background: #fafcff;
  border: 1px solid var(--sc-border);
}

.tx-row.ours {
  background: #f2fbf7;
  border-color: #b9e8d2;
}

.tx-tag {
  font-size: 11px;
  color: #1f8a5b;
  white-space: nowrap;
}

.tx-tag.muted {
  color: var(--sc-text-muted);
}

/* ============ 等宽哈希 ============ */
.hash {
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 11px;
  color: var(--sc-text-secondary);
  word-break: break-all;
}

/* ============ 空态（项目用虚线框，不用 el-empty） ============ */
.empty-box {
  padding: 26px;
  text-align: center;
  color: var(--sc-text-muted);
  border: 1px dashed var(--sc-border);
  border-radius: 12px;
}

.empty-icon {
  font-size: 22px;
  color: var(--sc-text-muted);
  margin-bottom: 8px;
}

.empty-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--sc-text-secondary);
  margin-bottom: 10px;
}

.empty-text {
  font-size: 12.5px;
  line-height: 1.9;
  color: var(--sc-text-muted);
  max-width: 620px;
  margin: 0 auto 6px;
}

.empty-text code {
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 12px;
  background: #f2f5ff;
  padding: 1px 5px;
  border-radius: 4px;
}

/* ============ 响应式 ============ */
@media (max-width: 860px) {
  .stat-grid {
    grid-template-columns: repeat(2, 1fr);
  }

  .room-row {
    flex-direction: column;
    align-items: flex-start;
  }

  .row-right {
    flex-wrap: wrap;
  }
}
</style>
