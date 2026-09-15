<template>
  <div class="board">
    <!-- ==================== 顶栏 ==================== -->
    <header class="board-header">
      <div class="header-left">
        <el-button class="back-btn" circle @click="goBack">
          <el-icon><ArrowLeft /></el-icon>
        </el-button>
        <div class="room-info">
          <h2 class="room-name">
            <el-icon class="room-icon"><OfficeBuilding /></el-icon>
            {{ roomStore.roomName }}
          </h2>
          <span class="room-id">房间号 {{ roomStore.roomCode }}</span>
        </div>
      </div>

      <div class="header-right">
        <!-- 在线成员 -->
        <div class="member-stack">
          <el-tooltip
            v-for="m in displayMembers"
            :key="m.userId"
            :content="m.userName + (m.userId === roomStore.currentUser.userId ? '（我）' : '')"
            placement="bottom"
          >
            <el-avatar :size="32" class="member-avatar" :style="{ background: memberColor(m.color) }">
              {{ m.userName.slice(0, 1) }}
            </el-avatar>
          </el-tooltip>
        </div>

        <el-tag :type="roomStore.connected ? 'success' : 'info'" effect="light" round>
          {{ roomStore.connected ? '实时已连接' : '连接中…' }}
        </el-tag>

        <el-tooltip content="复制房间号邀请伙伴" placement="bottom">
          <el-button round class="btn-ghost" @click="copyRoomCode">
            <el-icon><CopyDocument /></el-icon>&nbsp;邀请
          </el-button>
        </el-tooltip>
      </div>
    </header>

    <!-- ==================== 主体 ==================== -->
    <div class="board-body">
      <!-- 工具条 -->
      <aside class="toolbar">
        <!-- 只读提示：游客不可编辑 -->
        <el-tooltip :content="readonly ? '登录后即可编辑' : '添加便利贴'" placement="right">
          <button class="tool-btn" :class="{ active: !readonly }" :disabled="readonly" @click="readonly ? goLogin() : addNote()">
            <el-icon size="20"><Postcard /></el-icon>
          </button>
        </el-tooltip>

        <template v-if="!readonly">
          <div class="tool-divider"></div>

          <!-- 色板 -->
          <div class="color-palette">
            <button
              v-for="c in colorOptions"
              :key="c.key"
              class="color-dot"
              :class="{ active: selectedColor === c.key }"
              :style="{ background: c.value }"
              :title="c.label"
              @click="changeColor(c.key)"
            />
          </div>
        </template>

        <div class="tool-divider"></div>

        <el-tooltip content="智能整理（DeepSeek 分组 + 冲突）" placement="right">
          <button class="tool-btn" :disabled="analyzing" @click="runAnalysis">
            <el-icon v-if="analyzing" size="20" class="spin"><Loading /></el-icon>
            <el-icon v-else size="20"><Magnet /></el-icon>
          </button>
        </el-tooltip>

        <el-tooltip content="清除整理结果" placement="right">
          <button class="tool-btn" @click="clearAnalysis">
            <el-icon size="20"><Warning /></el-icon>
          </button>
        </el-tooltip>

        <div class="tool-divider"></div>

        <el-tooltip content="放大" placement="right">
          <button class="tool-btn" @click="zoomBy(1.15)">
            <el-icon size="20"><ZoomIn /></el-icon>
          </button>
        </el-tooltip>
        <el-tooltip content="缩小" placement="right">
          <button class="tool-btn" @click="zoomBy(0.85)">
            <el-icon size="20"><ZoomOut /></el-icon>
          </button>
        </el-tooltip>
        <el-tooltip content="重置视图" placement="right">
          <button class="tool-btn" @click="resetView">
            <el-icon size="20"><FullScreen /></el-icon>
          </button>
        </el-tooltip>
      </aside>

      <!-- 画布 -->
      <main class="canvas-area">
        <CanvasStage ref="canvasRef" :readonly="readonly" />
        <span class="zoom-indicator">{{ zoomPercent }}%</span>

        <!-- 智能整理结果面板 -->
        <transition name="ap-fade">
          <div v-if="analysisStore.active" class="analysis-panel">
            <div class="ap-head">
              <span class="ap-title">
                <el-icon><Magnet /></el-icon>&nbsp;智能整理
              </span>
              <el-button text size="small" @click="clearAnalysis">清除</el-button>
            </div>
            <p v-if="analysisStore.current?.summary" class="ap-summary">{{ analysisStore.current.summary }}</p>
            <div v-if="groupCount > 0" class="ap-block">
              <div class="ap-sub">分组 · {{ groupCount }}</div>
              <div v-for="(g, i) in analysisStore.current?.groups || []" :key="i" class="ap-row">
                <span class="ap-dot" :style="{ background: groupColor(i) }"></span>
                <span class="ap-name">{{ g.name }}</span>
                <span class="ap-count">{{ g.noteIds.length }} 张</span>
              </div>
            </div>
            <div v-if="conflictCount > 0" class="ap-block">
              <div class="ap-sub ap-warn-title">冲突 · {{ conflictCount }}</div>
              <div v-for="(c, j) in analysisStore.current?.conflicts || []" :key="j" class="ap-row ap-conflict">
                <el-icon class="ap-warn-icon"><Warning /></el-icon>
                <span class="ap-reason">{{ c.reason }}</span>
              </div>
            </div>
          </div>
        </transition>
      </main>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import CanvasStage from '@/components/canvas/CanvasStage.vue'
import { useNotesStore } from '@/stores/notes'
import { useRoomStore } from '@/stores/room'
import { useAuthStore } from '@/stores/auth'
import { useAnalysisStore } from '@/stores/analysis'
import { wsClient } from '@/api/ws'
import { getRoomById } from '@/api/room'
import { analyzeRoom, getLatestAnalysis } from '@/api/analysis'

const route = useRoute()
const router = useRouter()
const notesStore = useNotesStore()
const roomStore = useRoomStore()
const authStore = useAuthStore()
const analysisStore = useAnalysisStore()

/** 是否正在请求智能整理 */
const analyzing = ref(false)

/** 只读：游客只能看，不能写入 */
const readonly = computed(() => !authStore.isLoggedIn)

const canvasRef = ref<InstanceType<typeof CanvasStage> | null>(null)

const zoomPercent = computed(() => {
  const v = canvasRef.value?.view
  return Math.round((v?.scale ?? 1) * 100)
})

const roomId = Number(route.params.roomId)

// 智能整理分组色板（与 CanvasStage.GROUP_COLORS 同序同值，索引对应）
const GROUP_COLORS = ['#5b8cff', '#8b6cff', '#2fc98a', '#ff9f43', '#ff5d8f', '#00b8a9', '#f8b500', '#7c6ff0']
function groupColor(i: number) {
  return GROUP_COLORS[i % GROUP_COLORS.length]
}
const groupCount = computed(() => analysisStore.current?.groups.length ?? 0)
const conflictCount = computed(() => analysisStore.current?.conflicts.length ?? 0)

// 当前用户：优先用登录账号身份，否则生成匿名访客身份（同一浏览器保持同一身份）
const COLORS = ['blue', 'purple', 'green', 'orange']
function getOrCreateUser() {
  // 已登录：使用真实账号 id 与昵称
  const authStore = useAuthStore()
  if (authStore.isLoggedIn && authStore.user) {
    return {
      userId: String(authStore.user.id),
      userName: authStore.user.nickname,
      color: authStore.user.avatarColor
    }
  }
  // 未登录：匿名访客身份
  const saved = localStorage.getItem('sc_user')
  if (saved) {
    try {
      const u = JSON.parse(saved)
      if (u.userId && u.userName) return u
    } catch {
      /* ignore */
    }
  }
  const user = {
    userId: 'u' + Date.now().toString(36) + Math.random().toString(36).slice(2, 6),
    userName: '访客' + Math.floor(100 + Math.random() * 900),
    color: COLORS[Math.floor(Math.random() * COLORS.length)]
  }
  localStorage.setItem('sc_user', JSON.stringify(user))
  return user
}

const currentUser = ref(getOrCreateUser())

const displayMembers = computed(() => {
  const users = roomStore.onlineUsers
  // 确保自己一定在列表
  if (!users.some((u) => u.userId === currentUser.value.userId)) {
    return [
      { userId: currentUser.value.userId, userName: currentUser.value.userName, color: currentUser.value.color },
      ...users
    ]
  }
  return users
})

const colorOptions = [
  { key: 'yellow', value: '#ffef9f', label: '黄色' },
  { key: 'blue', value: '#cfe3ff', label: '蓝色' },
  { key: 'green', value: '#c9f0dd', label: '绿色' },
  { key: 'pink', value: '#ffd6ec', label: '粉色' },
  { key: 'purple', value: '#e3d9ff', label: '紫色' }
]

const selectedColor = ref('yellow')

const MEMBER_COLORS: Record<string, string> = {
  blue: '#5b8cff',
  purple: '#8b6cff',
  green: '#2fc98a',
  orange: '#ff9f43'
}
function memberColor(c: string) {
  return MEMBER_COLORS[c] || '#909399'
}

onMounted(async () => {
  // 0. 恢复登录态（刷新后仍用登录身份进房间）
  await authStore.fetchMe()
  currentUser.value = getOrCreateUser()

  // 1. 加载房间信息（拿 roomCode / name）
  try {
    const res = await getRoomById(roomId)
    if (res.data.code === 0) {
      const room = res.data.data
      roomStore.init(room.id, room.roomCode, room.name, currentUser.value)
    }
  } catch {
    ElMessage.error('加载房间失败')
  }

  // 2. 建立 WebSocket 连接（登录用户带 token，游客不带）
  wsClient.connect(
    roomId,
    currentUser.value.userId,
    currentUser.value.userName,
    currentUser.value.color,
    authStore.token
  )

  wsClient.onStatusChange = (connected) => {
    roomStore.setConnected(connected)
  }
  wsClient.onUserList = (users) => {
    roomStore.setOnlineUsers(users)
  }
  wsClient.onError = (msg) => {
    ElMessage.error(msg)
  }
  wsClient.onAnalysis = (d) => {
    analysisStore.setAnalysis(d)
  }
  // 晚加入 / 刷新：恢复最近一次整理覆盖层
  loadLatestAnalysis()
})

onBeforeUnmount(() => {
  wsClient.onAnalysis = undefined
  wsClient.disconnect()
})

function goBack() {
  router.push('/')
}

function goLogin() {
  router.push('/login')
}

function addNote() {
  canvasRef.value?.addNote()
}

function changeColor(color: string) {
  selectedColor.value = color
  canvasRef.value?.changeColor(color)
}

function zoomBy(factor: number) {
  canvasRef.value?.zoomBy(factor)
}

function resetView() {
  canvasRef.value?.resetView()
}

function copyRoomCode() {
  if (roomStore.roomCode) {
    navigator.clipboard.writeText(roomStore.roomCode).then(() => {
      ElMessage.success(`房间号 ${roomStore.roomCode} 已复制`)
    })
  }
}

async function runAnalysis() {
  if (readonly.value) {
    ElMessage.info('登录后即可使用智能整理')
    goLogin()
    return
  }
  if (analyzing.value) return
  analyzing.value = true
  try {
    const res = await analyzeRoom(roomId)
    if (res.data.code === 0 && res.data.data) {
      analysisStore.setAnalysis(res.data.data)
      const d = res.data.data
      ElMessage.success(`已识别 ${d.groups.length} 组 / ${d.conflicts.length} 处冲突`)
    } else {
      ElMessage.warning(res.data.message || '智能整理失败')
    }
  } catch (err) {
    const e = err as { response?: { data?: { message?: string } }; message?: string }
    const msg = e?.response?.data?.message || e?.message || '智能整理失败，请稍后重试'
    ElMessage.error(msg)
  } finally {
    analyzing.value = false
  }
}

function clearAnalysis() {
  if (!analysisStore.active) {
    ElMessage.info('当前没有整理结果')
    return
  }
  analysisStore.clear()
  ElMessage.success('已清除整理结果')
}

/** 进入房间时拉取最近一次整理结果（供覆盖层恢复） */
async function loadLatestAnalysis() {
  try {
    const res = await getLatestAnalysis(roomId)
    if (res.data.code === 0 && res.data.data) {
      analysisStore.setAnalysis(res.data.data)
    }
  } catch {
    /* 静默：latest 拉取失败不影响主流程 */
  }
}
</script>

<style scoped>
.board {
  height: 100vh;
  display: flex;
  flex-direction: column;
  background: #f2f4fa;
  overflow: hidden;
}

/* ============ 顶栏 ============ */
.board-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 20px;
  background: rgba(255, 255, 255, 0.92);
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  border-bottom: 1px solid var(--sc-border);
  z-index: 20;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 14px;
}

.back-btn {
  background: #f3f6ff;
  border: none;
  color: var(--sc-text);
}

.back-btn:hover {
  background: #e8eeff;
}

.room-info {
  display: flex;
  flex-direction: column;
}

.room-name {
  font-size: 15px;
  font-weight: 700;
  display: flex;
  align-items: center;
  gap: 6px;
}

.room-icon {
  color: var(--sc-primary);
}

.room-id {
  font-size: 12px;
  color: var(--sc-text-muted);
}

.header-right {
  display: flex;
  align-items: center;
  gap: 14px;
}

.member-stack {
  display: flex;
  align-items: center;
}

.member-avatar {
  margin-left: -6px;
  border: 2px solid #fff;
  font-size: 13px;
}

/* ============ 主体 ============ */
.board-body {
  flex: 1;
  display: flex;
  min-height: 0;
}

/* ============ 工具条 ============ */
.toolbar {
  width: 56px;
  background: #fff;
  border-right: 1px solid var(--sc-border);
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 16px 0;
  gap: 10px;
  z-index: 15;
}

.tool-btn {
  width: 40px;
  height: 40px;
  border: none;
  border-radius: 10px;
  background: transparent;
  color: var(--sc-text-secondary);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.2s;
}

.tool-btn:hover {
  background: #f2f5ff;
  color: var(--sc-primary);
}

.tool-btn.active {
  background: linear-gradient(120deg, var(--sc-grad-1), var(--sc-grad-2));
  color: #fff;
  box-shadow: 0 6px 14px -4px rgba(102, 126, 234, 0.55);
}

.tool-divider {
  width: 28px;
  height: 1px;
  background: var(--sc-border);
  margin: 6px 0;
}

/* 色板 */
.color-palette {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.color-dot {
  width: 24px;
  height: 24px;
  border-radius: 50%;
  border: 2px solid transparent;
  cursor: pointer;
  transition: transform 0.2s;
}

.color-dot:hover {
  transform: scale(1.15);
}

.color-dot.active {
  border-color: var(--sc-primary);
  box-shadow: 0 0 0 3px rgba(91, 108, 255, 0.15);
}

/* ============ 画布区 ============ */
.canvas-area {
  flex: 1;
  position: relative;
  overflow: hidden;
}

.zoom-indicator {
  position: absolute;
  bottom: 16px;
  right: 18px;
  background: rgba(255, 255, 255, 0.9);
  border: 1px solid var(--sc-border);
  border-radius: 8px;
  padding: 3px 10px;
  font-size: 12px;
  color: var(--sc-text-secondary);
  box-shadow: 0 4px 12px -6px rgba(31, 45, 61, 0.15);
  z-index: 10;
}

/* ============ 智能整理结果面板 ============ */
.analysis-panel {
  position: absolute;
  top: 16px;
  right: 16px;
  width: 264px;
  max-height: calc(100% - 48px);
  overflow: auto;
  background: rgba(255, 255, 255, 0.96);
  backdrop-filter: blur(10px);
  border: 1px solid var(--sc-border);
  border-radius: 12px;
  box-shadow: 0 12px 32px -12px rgba(31, 45, 61, 0.18);
  padding: 10px 12px 12px;
  z-index: 8;
  font-size: 13px;
  color: var(--sc-text);
}

.ap-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 6px;
}

.ap-title {
  font-weight: 700;
  display: flex;
  align-items: center;
}

.ap-summary {
  margin: 2px 0 8px;
  color: var(--sc-text-secondary);
  font-size: 12px;
  line-height: 1.5;
}

.ap-block {
  border-top: 1px solid var(--sc-border);
  padding-top: 6px;
  margin-top: 4px;
}

.ap-sub {
  font-size: 12px;
  color: var(--sc-text-muted);
  margin-bottom: 4px;
  font-weight: 600;
}

.ap-warn-title {
  color: #f56c6c;
}

.ap-row {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 3px 0;
}

.ap-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  flex: 0 0 auto;
}

.ap-name {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.ap-count {
  color: var(--sc-text-muted);
  font-size: 12px;
}

.ap-conflict {
  align-items: flex-start;
}

.ap-warn-icon {
  color: #ff4d4f;
  margin-top: 2px;
  flex: 0 0 auto;
}

.ap-reason {
  flex: 1;
  line-height: 1.4;
}

.ap-fade-enter-active,
.ap-fade-leave-active {
  transition: opacity 0.2s;
}
.ap-fade-enter-from,
.ap-fade-leave-to {
  opacity: 0;
}

.tool-btn:disabled {
  opacity: 0.55;
  cursor: default;
}

.spin {
  animation: ap-spin 1s linear infinite;
}
@keyframes ap-spin {
  to {
    transform: rotate(360deg);
  }
}
</style>
