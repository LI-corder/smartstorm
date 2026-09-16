<template>
  <transition name="chain-slide">
    <aside v-if="chainStore.active" class="chain-dock">
      <!-- ============ 头部 ============ -->
      <div class="cd-head">
        <span class="cd-title">
          <el-icon><Link /></el-icon>&nbsp;区块链存证
        </span>
        <el-button text size="small" @click="chainStore.close()">关闭</el-button>
      </div>

      <!-- ============ 链状态 ============ -->
      <div class="cd-body">
        <div class="cd-status" :class="{ off: !chainStore.chainEnabled }">
          <span class="cd-dot"></span>
          {{ chainStore.chainEnabled ? '存证链已连接' : '存证链未启用（仅本地哈希链）' }}
        </div>

        <div v-if="status" class="cd-stats">
          <div class="cd-stat">
            <span class="cd-num">{{ status.totalOps }}</span>
            <span class="cd-label">画布操作</span>
          </div>
          <div class="cd-stat">
            <span class="cd-num">{{ status.anchoredSeq ?? 0 }}</span>
            <span class="cd-label">已存证至 seq</span>
          </div>
          <div class="cd-stat">
            <span class="cd-num">{{ status.confirmedCount }}</span>
            <span class="cd-label">已上链批次</span>
          </div>
        </div>

        <p v-if="status && status.unanchoredCount > 0" class="cd-hint">
          还有 {{ status.unanchoredCount }} 条操作待存证，系统会自动批量上链。
        </p>

        <!-- ============ 操作 ============ -->
        <div class="cd-actions">
          <el-button size="small" :loading="verifying" @click="runVerify">验证画布完整性</el-button>
          <el-button
            size="small"
            type="primary"
            :loading="anchoring"
            :disabled="readonly"
            @click="runAnchor"
          >
            立即存证
          </el-button>
        </div>
        <p v-if="readonly" class="cd-hint">登录后可使用存证功能</p>

        <!-- ============ 校验结果 ============ -->
        <div v-if="chainStore.verifyResult" class="cd-verify" :class="chainStore.verifyResult.ok ? 'ok' : 'bad'">
          <template v-if="chainStore.verifyResult.ok">
            <el-icon><CircleCheck /></el-icon>
            <span>完整 · 共 {{ chainStore.verifyResult.totalOps }} 条操作未被篡改</span>
          </template>
          <template v-else>
            <el-icon><CircleClose /></el-icon>
            <span>
              第 <b>{{ chainStore.verifyResult.brokenSeq }}</b> 条操作校验失败：{{
                chainStore.verifyResult.reason
              }}
            </span>
          </template>
        </div>

        <!-- ============ 链头 ============ -->
        <div v-if="status?.headHash" class="cd-head-hash">
          <div class="cd-sub">当前链头哈希</div>
          <code class="cd-hash">{{ shortHash(status.headHash) }}</code>
        </div>

        <!-- ============ 锚点列表 ============ -->
        <div class="cd-sub">存证批次 · {{ chainStore.anchors.length }}</div>
        <p v-if="!chainStore.anchors.length" class="cd-empty">
          还没有存证批次。画布操作累积到一定数量后会自动上链，也可以点「立即存证」。
        </p>

        <div v-for="a in chainStore.anchors" :key="a.id" class="cd-anchor">
          <div class="cd-anchor-head">
            <span class="cd-range">seq {{ a.fromSeq }} – {{ a.toSeq }}</span>
            <span class="cd-tag" :class="'st' + a.status">{{ a.statusText }}</span>
          </div>
          <div class="cd-kv">
            <span>Merkle 根</span><code>{{ shortHash(a.merkleRoot) }}</code>
          </div>
          <div v-if="a.txHash" class="cd-kv">
            <span>交易哈希</span><code>{{ shortHash(a.txHash) }}</code>
          </div>
          <div v-if="a.blockNumber != null" class="cd-kv">
            <span>区块高度</span>
            <a class="cd-link" @click="openBlock(a.blockNumber)">#{{ a.blockNumber }} ↗</a>
          </div>
          <div v-else-if="a.status === 0" class="cd-kv muted">
            <span>等待上链…</span>
          </div>
        </div>
      </div>
    </aside>
  </transition>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useChainStore } from '@/stores/chain'
import { anchorNow, getChainStatus, listAnchors, verifyChain } from '@/api/chain'
import { shortHash } from '@/utils/hash'

const props = defineProps<{ roomId: number; readonly: boolean }>()

const chainStore = useChainStore()
const router = useRouter()

const verifying = ref(false)
const anchoring = ref(false)

const status = computed(() => chainStore.status)

/** 跳到区块信息页并自动展开该区块，形成「房间存证 → 链上区块」的贯通路径 */
function openBlock(number: number) {
  router.push(`/chain?block=${number}`)
}

async function refresh() {
  try {
    const [statusRes, anchorRes] = await Promise.all([
      getChainStatus(props.roomId),
      listAnchors(props.roomId)
    ])
    if (statusRes.data.code === 0) chainStore.setStatus(statusRes.data.data)
    if (anchorRes.data.code === 0) chainStore.setAnchors(anchorRes.data.data)
  } catch {
    // 静默失败：面板显示旧数据即可，不打断画布操作
  }
}

async function runVerify() {
  verifying.value = true
  try {
    const res = await verifyChain(props.roomId)
    if (res.data.code === 0) {
      chainStore.setVerifyResult(res.data.data)
      if (res.data.data.ok) {
        ElMessage.success(`校验通过，共 ${res.data.data.totalOps} 条操作`)
      } else {
        ElMessage.error(`第 ${res.data.data.brokenSeq} 条操作校验失败`)
      }
    }
  } catch {
    ElMessage.error('校验请求失败')
  } finally {
    verifying.value = false
  }
}

async function runAnchor() {
  anchoring.value = true
  try {
    const res = await anchorNow(props.roomId)
    if (res.data.code === 0) {
      chainStore.pushAnchor(res.data.data)
      await refresh()
      ElMessage.success(
        res.data.data.txHash ? '已上链' : '已生成存证批次（链未启用，待补发）'
      )
    } else {
      ElMessage.info(res.data.message || '没有可存证的新操作')
    }
  } catch {
    ElMessage.error('存证请求失败')
  } finally {
    anchoring.value = false
  }
}

onMounted(refresh)

defineExpose({ refresh })
</script>

<style scoped>
.chain-dock {
  position: absolute;
  top: 0;
  right: 0;
  bottom: 0;
  width: 340px;
  display: flex;
  flex-direction: column;
  background: rgba(255, 255, 255, 0.96);
  backdrop-filter: blur(10px);
  border-left: 1px solid var(--sc-border);
  box-shadow: -12px 0 32px -16px rgba(31, 45, 61, 0.18);
  z-index: 9;
  font-size: 13px;
}

.cd-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 12px;
  border-bottom: 1px solid var(--sc-border);
  flex: 0 0 auto;
}

.cd-title {
  display: inline-flex;
  align-items: center;
  font-weight: 600;
  color: var(--sc-text);
}

.cd-body {
  flex: 1 1 auto;
  overflow: auto;
  padding: 10px 12px 16px;
}

.cd-status {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: var(--sc-text-secondary);
  margin-bottom: 10px;
}

.cd-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #2fc98a;
  flex: 0 0 auto;
}

.cd-status.off .cd-dot {
  background: #c9d1e4;
}

.cd-stats {
  display: flex;
  gap: 8px;
  margin-bottom: 10px;
}

.cd-stat {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
  padding: 8px 4px;
  background: #f6f8ff;
  border-radius: 10px;
}

.cd-num {
  font-size: 17px;
  font-weight: 600;
  color: var(--sc-primary);
}

.cd-label {
  font-size: 11px;
  color: var(--sc-text-muted);
}

.cd-hint {
  font-size: 12px;
  color: var(--sc-text-muted);
  margin: 6px 0;
  line-height: 1.5;
}

.cd-actions {
  display: flex;
  gap: 8px;
  margin: 10px 0 6px;
}

.cd-verify {
  display: flex;
  align-items: flex-start;
  gap: 6px;
  padding: 8px 10px;
  border-radius: 10px;
  margin: 8px 0;
  font-size: 12px;
  line-height: 1.5;
}

.cd-verify.ok {
  background: #eafaf2;
  color: #1f8a5b;
}

.cd-verify.bad {
  background: #fdeeee;
  color: #c0392b;
}

.cd-sub {
  font-size: 12px;
  font-weight: 600;
  color: var(--sc-text-secondary);
  margin: 12px 0 6px;
}

.cd-head-hash {
  padding: 8px 10px;
  background: #f6f8ff;
  border-radius: 10px;
}

.cd-head-hash .cd-sub {
  margin: 0 0 4px;
}

.cd-hash,
.cd-kv code {
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 11px;
  color: var(--sc-text-secondary);
  word-break: break-all;
}

.cd-empty {
  font-size: 12px;
  color: var(--sc-text-muted);
  line-height: 1.6;
  margin: 4px 0;
}

/* 区块号：可点击跳到区块信息页 */
.cd-link {
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 11px;
  color: var(--sc-primary);
  cursor: pointer;
}

.cd-link:hover {
  text-decoration: underline;
}

.cd-anchor {
  border: 1px solid var(--sc-border);
  border-radius: 10px;
  padding: 8px 10px;
  margin-bottom: 8px;
}

.cd-anchor-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 6px;
}

.cd-range {
  font-weight: 600;
  color: var(--sc-text);
}

.cd-tag {
  font-size: 11px;
  padding: 1px 7px;
  border-radius: 6px;
}

.cd-tag.st1 {
  background: #eafaf2;
  color: #1f8a5b;
}

.cd-tag.st0 {
  background: #fff5e6;
  color: #b8791a;
}

.cd-tag.st2 {
  background: #fdeeee;
  color: #c0392b;
}

.cd-kv {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  gap: 8px;
  font-size: 11px;
  color: var(--sc-text-muted);
  margin-top: 3px;
}

.cd-kv.muted {
  color: var(--sc-text-muted);
}

/* 与智能整理面板的 ap-fade 淡入不同，侧栏用滑入 */
.chain-slide-enter-active,
.chain-slide-leave-active {
  transition: transform 0.2s ease, opacity 0.2s ease;
}

.chain-slide-enter-from,
.chain-slide-leave-to {
  transform: translateX(100%);
  opacity: 0;
}
</style>
