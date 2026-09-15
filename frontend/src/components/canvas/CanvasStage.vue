<template>
  <div ref="containerRef" class="canvas-stage">
    <!-- Konva 会填充此容器。编辑浮层用 Teleport 挂到 body，避免与 Konva 的 DOM 冲突 -->
  </div>

  <!-- 双击编辑浮层（Teleport 到 body，规避 Konva 容器 DOM 结构冲突） -->
  <Teleport to="body">
    <div
      v-if="editing"
      class="edit-overlay"
      :style="overlayStyle"
      @mousedown.stop
      @click.stop
    >
      <textarea
        ref="editAreaRef"
        v-model="editContent"
        class="edit-input"
        placeholder="输入内容，Ctrl+Enter 保存"
        @keydown.ctrl.enter.prevent="saveEdit"
        @keydown.esc.prevent="cancelEdit"
      />
      <div class="edit-actions">
        <el-button size="small" type="primary" @click="saveEdit">保存</el-button>
        <el-button size="small" @click="cancelEdit">取消</el-button>
      </div>
    </div>
  </Teleport>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import Konva from 'konva'
import { useNotesStore } from '@/stores/notes'
import { useAnalysisStore } from '@/stores/analysis'
import { wsClient } from '@/api/ws'
import type { Note } from '@/types'

/** 只读模式：游客只能看，不能增删改（由 BoardView 传入） */
const props = defineProps<{ readonly?: boolean }>()

/** 便利贴颜色表 */
const NOTE_COLORS: Record<string, string> = {
  yellow: '#ffef9f',
  blue: '#cfe3ff',
  green: '#c9f0dd',
  pink: '#ffd6ec',
  purple: '#e3d9ff'
}

const notesStore = useNotesStore()
const analysisStore = useAnalysisStore()

const containerRef = ref<HTMLDivElement | null>(null)
const editAreaRef = ref<HTMLTextAreaElement | null>(null)

// ---------- Konva stage 引用 ----------
let stage: Konva.Stage | null = null
let noteLayer: Konva.Layer | null = null
let cursorLayer: Konva.Layer | null = null
let gridLayer: Konva.Layer | null = null
/** 智能整理：分组区域层（垫在便利贴下） */
let groupLayer: Konva.Layer | null = null
/** 智能整理：组标签 + 冲突线层（压在便利贴上、不拦鼠标） */
let conflictLayer: Konva.Layer | null = null

// Konva 节点映射：noteId -> Group（用于增删改便利贴）
const noteGroups = new Map<number, Konva.Group>()
// 远端光标节点映射：userId -> (group, ts)
const cursorNodes = new Map<string, { group: Konva.Group; ts: number }>()

// ---------- 画布视口（缩放/平移） ----------
const view = reactive({ scale: 1, x: 0, y: 0 })
const stageSize = reactive({ width: 0, height: 0 })

// ---------- 选中与编辑 ----------
const selectedId = ref<number | null>(null)
const editing = ref(false)
const editingNoteId = ref<number | null>(null)
const editContent = ref('')

const overlayStyle = computed(() => {
  const n = editingNoteId.value != null ? notesStore.notes[editingNoteId.value] : null
  if (!n || !stage) return {}
  // 便利贴的 stage 坐标 → 屏幕坐标
  const screenX = stage.container().getBoundingClientRect().left + view.x + n.x * view.scale
  const screenY = stage.container().getBoundingClientRect().top + view.y + n.y * view.scale
  return {
    left: screenX + 'px',
    top: screenY + 'px',
    width: n.width * view.scale + 'px',
    height: n.height * view.scale + 'px'
  }
})

// ==================== 生命周期 ====================

onMounted(() => {
  if (!containerRef.value) return
  initStage()
  window.addEventListener('resize', onResize)
  window.addEventListener('keydown', onKeydown)
  wireWs()

  // 监听便利贴数据变化 → 同步 Konva 节点
  notesStore.$subscribe((_mutation, state) => {
    syncNotes(state.notes)
  })
  syncNotes(notesStore.notes)

  // 监听智能整理结果 → 重建分组/冲突覆盖层
  analysisStore.$subscribe(() => {
    refreshOverlay()
  })

  // 光标清理定时器
  cursorCleanupTimer = window.setInterval(() => {
    const now = Date.now()
    for (const [id, c] of cursorNodes) {
      if (now - c.ts > CURSOR_TTL) {
        c.group.destroy()
        cursorNodes.delete(id)
      }
    }
  }, 2000)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', onResize)
  window.removeEventListener('keydown', onKeydown)
  if (cursorCleanupTimer) clearInterval(cursorCleanupTimer)
  // 移除 ws 回调
  wsClient.onCursor = undefined
  stage?.destroy()
  stage = null
})

let cursorCleanupTimer: number | null = null

// ==================== 初始化 Stage ====================

function initStage() {
  const container = containerRef.value!
  // 用容器实际尺寸（而非 window）
  stageSize.width = container.clientWidth
  stageSize.height = container.clientHeight

  stage = new Konva.Stage({
    container,
    width: stageSize.width,
    height: stageSize.height,
    scaleX: view.scale,
    scaleY: view.scale
  })

  // 背景网格层
  gridLayer = new Konva.Layer({ listening: false })
  const bg = new Konva.Rect({
    x: -3000,
    y: -3000,
    width: 9000,
    height: 9000,
    fillPatternImage: makeGridPattern(),
    fillPatternScale: { x: 1, y: 1 },
    listening: false
  })
  gridLayer.add(bg)
  stage.add(gridLayer)

  // 智能整理分组区域层（垫在便利贴下面，不拦鼠标）
  groupLayer = new Konva.Layer({ listening: false })
  stage.add(groupLayer)

  // 便利贴层
  noteLayer = new Konva.Layer()
  stage.add(noteLayer)

  // 智能整理组标签 + 冲突线层（压在便利贴上、不拦鼠标）
  conflictLayer = new Konva.Layer({ listening: false })
  stage.add(conflictLayer)

  // 光标层
  cursorLayer = new Konva.Layer({ listening: false })
  stage.add(cursorLayer)

  // 舞台事件：缩放 + 光标发送
  stage.on('wheel', onWheel)
  stage.on('mousemove', onStageMouseMove)
}

function makeGridPattern(): HTMLImageElement {
  const canvas = document.createElement('canvas')
  canvas.width = 40
  canvas.height = 40
  const ctx = canvas.getContext('2d')!
  ctx.strokeStyle = '#d7def0'
  ctx.lineWidth = 0.6
  ctx.beginPath()
  ctx.moveTo(0, 0)
  ctx.lineTo(40, 0)
  ctx.moveTo(0, 0)
  ctx.lineTo(0, 40)
  ctx.stroke()
  // Konva 的 fillPatternImage 类型要求 HTMLImageElement，
  // 但内部同样接受 canvas（CanvasImageSource），同步返回不依赖图片加载。
  return canvas as unknown as HTMLImageElement
}

function onResize() {
  if (!containerRef.value || !stage) return
  stageSize.width = containerRef.value.clientWidth
  stageSize.height = containerRef.value.clientHeight
  stage.width(stageSize.width)
  stage.height(stageSize.height)
  stage.batchDraw()
}

// ==================== 便利贴渲染 ====================

/** 创建一张便利贴的 Konva 节点 */
function createNoteNode(note: Note): Konva.Group {
  const group = new Konva.Group({
    x: note.x,
    y: note.y,
    draggable: !props.readonly
  })

  // 选中高亮框
  const ring = new Konva.Rect({
    x: -5,
    y: -5,
    width: note.width + 10,
    height: note.height + 10,
    stroke: '#5b6cff',
    strokeWidth: 2,
    dash: [4, 3],
    cornerRadius: 8,
    visible: false
  })

  // 便利贴主体
  const rect = new Konva.Rect({
    width: note.width,
    height: note.height,
    fill: NOTE_COLORS[note.color] || NOTE_COLORS.yellow,
    cornerRadius: 6,
    shadowColor: 'rgba(31,45,61,0.18)',
    shadowBlur: 8,
    shadowOffsetY: 3
  })

  // 正文
  const text = new Konva.Text({
    x: 8,
    y: 8,
    width: note.width - 16,
    height: note.height - 22,
    text: note.content || (props.readonly ? '' : '双击编辑'),
    fontSize: 13,
    lineHeight: 1.5,
    fontStyle: note.content ? 'normal' : 'italic',
    fill: note.content ? '#3f4a5e' : '#9aa6bf',
    wrap: 'word'
  })

  // 作者标签
  const author = new Konva.Text({
    x: 6,
    y: note.height - 14,
    text: note.userName || '',
    fontSize: 9,
    fill: '#9aa6bf'
  })

  // 删除按钮（只读模式不创建）
  const delBtn = new Konva.Group({
    x: note.width - 14,
    y: -12,
    visible: false
  })
  delBtn.add(
    new Konva.Circle({
      radius: 11,
      fill: '#f56c6c',
      stroke: '#fff',
      strokeWidth: 2,
      shadowColor: 'rgba(245,108,108,0.4)',
      shadowBlur: 6
    }),
    new Konva.Text({
      x: -5,
      y: -9,
      text: '✕',
      fontSize: 13,
      fill: '#fff',
      width: 10,
      height: 18,
      align: 'center'
    })
  )
  delBtn.on('mousedown tap', (e: Konva.KonvaEventObject<MouseEvent | TouchEvent>) => {
    e.cancelBubble = true
    handleDelete(note.id)
  })

  group.add(ring, rect, text, author, delBtn)

  // 事件（只读模式不绑定编辑/删除交互）
  if (!props.readonly) {
    group.on('mousedown', (e) => {
      e.cancelBubble = true
      handleNoteMouseDown(note.id)
    })
    group.on('mouseenter', () => {
      delBtn.visible(true)
      noteLayer?.batchDraw()
    })
    group.on('mouseleave', () => {
      if (!isSelected(note.id)) delBtn.visible(false)
      noteLayer?.batchDraw()
    })
    group.on('dragend', () => {
      handleDragEnd(note.id, group.x(), group.y())
    })
  }

  return group
}

function isSelected(id: number) {
  return selectedId.value === id
}

/** 根据 store 数据同步 Konva 节点 */
function syncNotes(notes: Record<number, Note>) {
  if (!noteLayer || !stage) return

  // 找出已删除的便利贴，移除节点
  const currentIds = new Set(Object.keys(notes).map(Number))
  for (const [id, group] of noteGroups) {
    if (!currentIds.has(id)) {
      group.destroy()
      noteGroups.delete(id)
    }
  }

  // 新增/更新便利贴
  for (const id of currentIds) {
    const note = notes[id]
    let group = noteGroups.get(id)
    if (!group) {
      group = createNoteNode(note)
      noteLayer.add(group)
      noteGroups.set(id, group)
    }
    // 更新已有节点（位置/内容/颜色）
    updateNoteNode(group, note, id)
  }

  // 有整理覆盖层时，随便利贴移动/增删刷新分组框与冲突线几何
  if (analysisStore.current) {
    refreshOverlay()
  }

  stage.batchDraw()
}

function updateNoteNode(group: Konva.Group, note: Note, id: number) {
  const [ring, rect, text, author, delBtn] = group.children as unknown as [
    Konva.Rect, Konva.Rect, Konva.Text, Konva.Text, Konva.Group
  ]

  // 选中态
  ring.visible(isSelected(id))
  delBtn.visible(isSelected(id))
  group.draggable(!isEditing())

  // 位置（仅当与当前节点位置不一致时更新，避免拖拽闪烁）
  if (group.x() !== note.x) group.x(note.x)
  if (group.y() !== note.y) group.y(note.y)

  // 内容
  rect.fill(NOTE_COLORS[note.color] || NOTE_COLORS.yellow)
  text.text(note.content || '双击编辑')
  text.fontStyle(note.content ? 'normal' : 'italic')
  text.fill(note.content ? '#3f4a5e' : '#9aa6bf')
  author.text(note.userName || '')
}

// ==================== 智能整理覆盖层 ====================

/** 智能整理分组色板（深色调，避免与便利贴浅色底色混淆） */
const GROUP_COLORS = ['#5b8cff', '#8b6cff', '#2fc98a', '#ff9f43', '#ff5d8f', '#00b8a9', '#f8b500', '#7c6ff0']

/** 组包围盒：由组内便利贴当前坐标现算（带内边距），没有存活便利贴返回 null */
function groupBox(ids: number[]) {
  let minX = Infinity
  let minY = Infinity
  let maxX = -Infinity
  let maxY = -Infinity
  for (const id of ids) {
    const n = notesStore.notes[id]
    if (!n) continue
    minX = Math.min(minX, n.x)
    minY = Math.min(minY, n.y)
    maxX = Math.max(maxX, n.x + n.width)
    maxY = Math.max(maxY, n.y + n.height)
  }
  if (!isFinite(minX)) return null
  const pad = 14
  return { x: minX - pad, y: minY - pad, width: maxX - minX + pad * 2, height: maxY - minY + pad * 2 }
}

/**
 * 重建智能整理覆盖层：
 * 分组区域框画在便利贴下层（半透明底 + 描边），组标签与冲突连线画在便利贴上层。
 * 覆盖层由 分析结果 或 便利贴移动/增删 触发刷新。
 */
function refreshOverlay() {
  if (!stage || !groupLayer || !conflictLayer) return
  // 捕获为本地常量，让 TS 收窄非空（模块级 let 可能被 initStage 重新赋值）
  const gLayer = groupLayer
  const cLayer = conflictLayer
  gLayer.destroyChildren()
  cLayer.destroyChildren()

  const cur = analysisStore.current
  if (cur) {
    cur.groups.forEach((g, i) => {
      const box = groupBox(g.noteIds)
      if (!box) return
      const color = GROUP_COLORS[i % GROUP_COLORS.length]
      const rect = new Konva.Rect({
        x: box.x,
        y: box.y,
        width: box.width,
        height: box.height,
        cornerRadius: 14,
        fill: color + '1f',
        stroke: color,
        strokeWidth: 2,
        listening: false
      })
      gLayer.add(rect)

      // 组名标签（放冲突层，确保盖在便利贴之上可见）
      const label = new Konva.Label({ x: box.x + 10, y: box.y + 8, listening: false })
      label.add(new Konva.Tag({ fill: color, cornerRadius: 6, pointerDirection: 'none' }))
      label.add(
        new Konva.Text({
          text: g.name,
          fontSize: 12,
          fontStyle: 'bold',
          fill: '#fff',
          padding: 6,
          width: Math.max(50, g.name.length * 13)
        })
      )
      cLayer.add(label)
    })

    // 冲突连线：两便利贴中心之间的红色虚线
    cur.conflicts.forEach((c) => {
      const a = notesStore.notes[c.noteA]
      const b = notesStore.notes[c.noteB]
      if (!a || !b) return
      cLayer.add(
        new Konva.Line({
          points: [a.x + a.width / 2, a.y + a.height / 2, b.x + b.width / 2, b.y + b.height / 2],
          stroke: '#ff4d4f',
          strokeWidth: 2,
          dash: [6, 4],
          listening: false
        })
      )
    })
  }

  stage.batchDraw()
}

// ==================== 便利贴交互 ====================

let lastMouseDown = 0
const DOUBLE_CLICK_MS = 350

function handleNoteMouseDown(id: number) {
  const now = Date.now()
  const gap = now - lastMouseDown
  setSelected(id)
  // 双击检测
  if (gap < DOUBLE_CLICK_MS) {
    lastMouseDown = 0
    handleEdit(id)
  } else {
    lastMouseDown = now
  }
}

function setSelected(id: number | null) {
  selectedId.value = id
  // 更新所有便利贴的选中态
  for (const [nid, group] of noteGroups) {
    const [ring, , , , delBtn] = group.children as unknown as [
      Konva.Rect, Konva.Rect, Konva.Text, Konva.Text, Konva.Group
    ]
    ring.visible(nid === id)
    delBtn.visible(nid === id)
  }
  noteLayer?.batchDraw()
}

function handleDragEnd(id: number, x: number, y: number) {
  if (props.readonly) return
  const note = notesStore.notes[id]
  if (note) {
    note.x = x
    note.y = y
  }
  wsClient.moveNote(id, x, y)
}

function handleDelete(id: number) {
  if (props.readonly) return
  notesStore.removeNote(id)
  wsClient.deleteNote(id)
  if (selectedId.value === id) setSelected(null)
}

function handleEdit(id: number) {
  if (props.readonly) return
  const note = notesStore.notes[id]
  if (!note) return
  editingNoteId.value = id
  editContent.value = note.content || ''
  editing.value = true
  nextTick(() => editAreaRef.value?.focus())
}

// ==================== 编辑浮层 ====================

function isEditing() {
  return editing.value
}

function saveEdit() {
  if (editingNoteId.value == null) return
  const id = editingNoteId.value
  const note = notesStore.notes[id]
  if (note && editContent.value !== note.content) {
    note.content = editContent.value
    wsClient.editNote(id, editContent.value)
  }
  cancelEdit()
}

function cancelEdit() {
  editing.value = false
  editingNoteId.value = null
  // 恢复便利贴可拖拽
  for (const group of noteGroups.values()) {
    group.draggable(true)
  }
  noteLayer?.batchDraw()
}

// ==================== 缩放 ====================

const MIN_SCALE = 0.2
const MAX_SCALE = 3

function onWheel(e: Konva.KonvaEventObject<WheelEvent>) {
  e.evt.preventDefault()
  if (!stage) return
  const oldScale = view.scale
  const pointer = stage.getPointerPosition()
  if (!pointer) return
  const mousePointTo = {
    x: (pointer.x - view.x) / oldScale,
    y: (pointer.y - view.y) / oldScale
  }
  let newScale = oldScale * (e.evt.deltaY > 0 ? 0.9 : 1.1)
  newScale = Math.min(MAX_SCALE, Math.max(MIN_SCALE, newScale))
  view.scale = newScale
  view.x = pointer.x - mousePointTo.x * newScale
  view.y = pointer.y - mousePointTo.y * newScale
  applyViewTransform()
}

/** 应用缩放/平移到 stage */
function applyViewTransform() {
  if (!stage) return
  stage.scale({ x: view.scale, y: view.scale })
  stage.position({ x: view.x, y: view.y })
  // 网格保持不缩放
  const bg = gridLayer?.getChildren()[0] as Konva.Rect
  if (bg) {
    bg.fillPatternScale({ x: 1 / view.scale, y: 1 / view.scale })
  }
  stage.batchDraw()
}

// ==================== 远端光标 ====================

const CURSOR_TTL = 4000
let lastCursorSend = 0

function wireWs() {
  wsClient.onCursor = ({ user, data }) => {
    showCursor(user, data.x, data.y)
  }
}

function showCursor(user: { id: string; name: string; color: string }, x: number, y: number) {
  if (!cursorLayer) return
  let node = cursorNodes.get(user.id)
  if (!node) {
    // 创建光标节点：箭头 + 名字
    const group = new Konva.Group({ x, y })
    group.add(
      new Konva.Shape({
        sceneFunc: (ctx, shape) => {
          ctx.beginPath()
          ctx.moveTo(0, 0)
          ctx.lineTo(0, 18)
          ctx.lineTo(5, 13)
          ctx.lineTo(9, 20)
          ctx.lineTo(12, 18)
          ctx.lineTo(8, 11)
          ctx.lineTo(14, 10)
          ctx.closePath()
          ctx.fillStrokeShape(shape)
        },
        fill: user.color,
        shadowColor: 'rgba(0,0,0,0.25)',
        shadowBlur: 4,
        shadowOffsetY: 2
      }),
      new Konva.Label({
        x: 12,
        y: 4
      }).add(
        new Konva.Tag({ cornerRadius: 4, fill: 'rgba(31,45,61,0.82)' }),
        new Konva.Text({
          text: user.name,
          fontSize: 12,
          fill: '#fff',
          padding: 3,
          width: user.name.length * 12
        })
      )
    )
    cursorLayer.add(group)
    node = { group, ts: Date.now() }
    cursorNodes.set(user.id, node)
  } else {
    node.group.position({ x, y })
    node.ts = Date.now()
  }
  cursorLayer.batchDraw()
}

// ==================== 光标发送 ====================

// 监听 stage 鼠标移动，节流发送自己光标
function onStageMouseMove(e: Konva.KonvaEventObject<MouseEvent>) {
  const now = Date.now()
  if (now - lastCursorSend > 200) {
    lastCursorSend = now
    const pos = stage?.getPointerPosition()
    if (pos) {
      const canvasX = (pos.x - view.x) / view.scale
      const canvasY = (pos.y - view.y) / view.scale
      wsClient.sendCursor(Math.round(canvasX), Math.round(canvasY))
    }
  }
}

// ---------- 键盘 ----------

function onKeydown(e: KeyboardEvent) {
  if (editing.value || props.readonly) return
  if (e.key === 'Delete' || e.key === 'Backspace') {
    if (selectedId.value != null) {
      handleDelete(selectedId.value)
    }
  }
  if (e.key === 'Escape') {
    setSelected(null)
  }
}

// ==================== 对外接口 ====================

/** 新建便利贴：画布中心生成（只读模式 no-op） */
function addNote() {
  if (props.readonly) return
  if (!stage) return
  const centerX = -view.x / view.scale + stageSize.width / 2 / view.scale
  const centerY = -view.y / view.scale + stageSize.height / 2 / view.scale
  const offset = (Math.random() - 0.5) * 120
  const x = Math.round(centerX + offset)
  const y = Math.round(centerY + offset)

  const note = notesStore.optimisticAdd({ x, y, content: '', color: 'yellow' })
  wsClient.addNote({ x, y, content: '', color: 'yellow' }, note.id)
  setSelected(note.id)
  handleEdit(note.id)
}

/** 修改选中便利贴颜色（只读模式 no-op） */
function changeColor(color: string) {
  if (props.readonly) return
  if (selectedId.value == null) return
  const id = selectedId.value
  const note = notesStore.notes[id]
  if (note) {
    note.color = color
    wsClient.colorNote(id, color)
  }
}

/** 缩放控制 */
function zoomBy(factor: number) {
  view.scale = Math.min(MAX_SCALE, Math.max(MIN_SCALE, view.scale * factor))
  applyViewTransform()
}

/** 重置视图 */
function resetView() {
  view.scale = 1
  view.x = 0
  view.y = 0
  applyViewTransform()
}

// ---------- 组件暴露 ----------

defineExpose({ view, addNote, changeColor, zoomBy, resetView })
</script>

<style scoped>
.canvas-stage {
  position: absolute;
  inset: 0;
  overflow: hidden;
}

.edit-overlay {
  position: fixed;
  z-index: 100;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.edit-input {
  width: 100%;
  height: 100%;
  border: 2px solid #5b6cff;
  border-radius: 6px;
  padding: 10px;
  font-size: 14px;
  line-height: 1.5;
  resize: none;
  outline: none;
  background: #fff;
  font-family: inherit;
  box-shadow: 0 8px 24px rgba(91, 108, 255, 0.2);
}

.edit-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}
</style>
