<template>
  <div class="home">
    <!-- ==================== 导航栏 ==================== -->
    <header class="nav">
      <div class="nav-inner">
        <div class="brand" @click="$router.push('/')">
          <span class="brand-icon">🧠</span>
          <span class="brand-name">Smart<span class="grad-text">Storm</span></span>
        </div>
        <nav class="nav-links">
          <a href="#features">核心功能</a>
          <a href="#workflow">使用流程</a>
        </nav>
        <div class="nav-actions">
          <template v-if="authStore.isLoggedIn">
            <el-dropdown trigger="click" @command="onNavCommand">
              <span class="nav-user">
                <el-avatar :size="30" class="nav-avatar" :style="{ background: avatarBg }">
                  {{ authStore.user?.nickname?.slice(0, 1) }}
                </el-avatar>
                <span class="nav-nickname">{{ authStore.user?.nickname }}</span>
                <el-icon class="nav-caret"><ArrowDown /></el-icon>
              </span>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="profile">
                    <el-icon><User /></el-icon>&nbsp;个人中心
                  </el-dropdown-item>
                  <el-dropdown-item command="logout" divided>
                    <el-icon><SwitchButton /></el-icon>&nbsp;退出登录
                  </el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </template>
          <template v-else>
            <el-button class="btn-ghost" round @click="$router.push('/login')">登录 / 注册</el-button>
          </template>
          <el-button class="btn-ghost" round @click="openJoin">加入房间</el-button>
          <el-button class="btn-primary" round @click="createRoom">开始头脑风暴</el-button>
        </div>
      </div>
    </header>

    <!-- ==================== Hero 区 ==================== -->
    <section class="hero">
      <div class="hero-glow glow-1"></div>
      <div class="hero-glow glow-2"></div>

      <div class="hero-content">
        <span class="hero-badge">🚀 实时协作 · 智能整理 · 结构化导出</span>
        <h1 class="hero-title">
          让每个灵感<br />
          <span class="grad-text">都有落脚点</span>
        </h1>
        <p class="hero-sub">
          SmartStorm 是一款多人实时协作的在线白板。它不仅同步你的每一笔，
          更能理解画布上的内容 —— 自动整理、发现冲突、生成会议纪要。
        </p>

        <div class="hero-actions">
          <el-input
            v-model="joinRoomId"
            placeholder="输入房间号，直接加入"
            class="hero-input"
            @keyup.enter="joinRoom"
          >
            <template #prefix><el-icon><Connection /></el-icon></template>
          </el-input>
          <el-button class="btn-primary" size="large" @click="createRoom">
            创建新房间
          </el-button>
        </div>

        <div class="hero-meta">
          <span class="meta-item">✓ 多人实时同步</span>
          <span class="meta-item">✓ 无限画布</span>
          <span class="meta-item">✓ 智能分组</span>
          <span class="meta-item">✓ 冲突标记</span>
        </div>
      </div>

      <!-- 浮动便利贴画布预览 -->
      <div class="hero-visual">
        <div class="canvas-preview">
          <div class="mini-toolbar">
            <span class="tool-dot"></span>
            <span class="tool-dot"></span>
            <span class="tool-dot"></span>
          </div>
          <div class="mini-canvas">
            <div
              v-for="(n, i) in previewNotes"
              :key="i"
              class="preview-note"
              :class="[n.color, n.float ? 'float-' + ((i % 3) + 1) : '']"
              :style="{ left: n.left + '%', top: n.top + '%', transform: 'rotate(' + n.rot + 'deg)' }"
            >
              <span class="note-pin"></span>
              {{ n.text }}
            </div>
          </div>
        </div>
      </div>
    </section>

    <!-- ==================== 核心功能 ==================== -->
    <section id="features" class="features">
      <div class="section-head">
        <h2 class="section-title">三大核心能力</h2>
        <p class="section-desc">从灵感到结论，一站式完成头脑风暴的全流程</p>
      </div>

      <div class="feature-grid">
        <div
          v-for="(f, i) in features"
          :key="i"
          class="feature-card"
          :style="{ animationDelay: (i * 0.08) + 's' }"
        >
          <div class="feature-icon" :class="f.color">{{ f.icon }}</div>
          <h3 class="feature-title">{{ f.title }}</h3>
          <p class="feature-desc">{{ f.desc }}</p>
          <div class="feature-tags">
            <span v-for="t in f.tags" :key="t" class="tag">{{ t }}</span>
          </div>
        </div>
      </div>
    </section>

    <!-- ==================== 使用流程 ==================== -->
    <section id="workflow" class="workflow">
      <div class="section-head">
        <h2 class="section-title">三步开始</h2>
        <p class="section-desc">创建房间，邀请伙伴，剩下的交给 SmartStorm</p>
      </div>

      <div class="steps">
        <div v-for="(s, i) in steps" :key="i" class="step">
          <div class="step-num">{{ i + 1 }}</div>
          <div class="step-icon">{{ s.icon }}</div>
          <h4>{{ s.title }}</h4>
          <p>{{ s.desc }}</p>
        </div>
      </div>
    </section>

    <!-- ==================== 底部 ==================== -->
    <footer class="footer">
      <p>SmartStorm · 智能头脑风暴室 —— 基于实时协作画布的智能头脑风暴室（毕业设计）</p>
    </footer>
  </div>
</template>

<script setup lang="ts">
import { onMounted, computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { fetchHealth } from '@/api/health'
import { createRoom as createRoomApi, getRoomByCode } from '@/api/room'
import { useAuthStore } from '@/stores/auth'
import { avatarBg as avatarBgOf } from '@/utils/avatar'

const router = useRouter()
const joinRoomId = ref('')
const health = ref<any>(null)
const authStore = useAuthStore()

/** 头像背景色（由共享头像色表映射） */
const avatarBg = computed(() => avatarBgOf(authStore.user?.avatarColor))

/** 导航下拉命令 */
function onNavCommand(cmd: string) {
  if (cmd === 'profile') {
    router.push('/profile')
  } else if (cmd === 'logout') {
    authStore.logout()
    ElMessage.success('已退出登录')
  }
}

/** 画布预览里浮动的便利贴 */
const previewNotes = [
  { text: '📝 头脑风暴中…', color: 'yellow', left: 18, top: 20, rot: -3, float: true },
  { text: '💡 灵感来啦！', color: 'blue', left: 55, top: 16, rot: 2, float: true },
  { text: '🤝 实时协作', color: 'green', left: 30, top: 52, rot: 1, float: true },
  { text: '🧠 智能分组', color: 'pink', left: 62, top: 48, rot: -2, float: true },
  { text: '🎯 发现冲突', color: 'purple', left: 16, top: 66, rot: 2, float: false }
]

const features = [
  {
    icon: '🖥️',
    color: 'blue',
    title: '实时协作画布',
    desc: '多人同屏协作，每一张便利贴的创建、编辑、拖拽都实时同步。多人光标让你看到伙伴的思路轨迹。',
    tags: ['多人房间', '实时同步', '多人光标', '无限画布']
  },
  {
    icon: '🧠',
    color: 'purple',
    title: '智能整理引擎',
    desc: '一键把语义相近的想法归组，自动发现观点冲突并连线标记。让散乱的脑暴碎片，变成结构清晰的地图。',
    tags: ['智能分组', '冲突标记', '手动调整']
  },
  {
    icon: '📤',
    color: 'green',
    title: '结构化导出',
    desc: '把整理好的思路一键导出为思维导图，或调用大模型生成通顺的会议纪要初稿。从想法到文档，无缝衔接。',
    tags: ['思维导图', '会议纪要', '操作回放']
  }
]

const steps = [
  {
    icon: '🏠',
    title: '创建 / 加入房间',
    desc: '创建房间获得专属房间号，把链接分享给伙伴，或输入房间号一键加入。'
  },
  {
    icon: '✍️',
    title: '自由记录想法',
    desc: '在无限画布上放置便利贴，记录每个人的灵感，实时同步每个人的操作。'
  },
  {
    icon: '🧩',
    title: '智能整理导出',
    desc: '一键智能分组、发现冲突，生成思维导图与会议纪要，结束这场高效头脑风暴。'
  }
]

onMounted(async () => {
  try {
    const res = await fetchHealth()
    health.value = res.data
  } catch {
    health.value = null
  }
})

function createRoom() {
  // 创建房间需登录（游客只读）
  if (!authStore.isLoggedIn) {
    ElMessage.warning('请先登录后再创建房间')
    router.push('/login')
    return
  }
  createRoomApi().then((res) => {
    if (res.data.code === 0) {
      const room = res.data.data
      ElMessage.success(`房间已创建，房间号 ${room.roomCode}`)
      router.push(`/board/${room.id}`)
    }
  })
}

function openJoin() {
  const el = document.querySelector('.hero-input input') as HTMLInputElement | null
  el?.focus()
}

async function joinRoom() {
  const code = joinRoomId.value.trim()
  if (!code) return
  try {
    const res = await getRoomByCode(code)
    if (res.data.code === 0) {
      router.push(`/board/${res.data.data.id}`)
    } else {
      ElMessage.warning(res.data.message || '房间不存在')
    }
  } catch {
    ElMessage.error('加入房间失败，请检查后端服务')
  }
}
</script>

<style scoped>
/* ============ 导航栏 ============ */
.nav {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  z-index: 50;
  background: rgba(255, 255, 255, 0.82);
  backdrop-filter: blur(14px);
  -webkit-backdrop-filter: blur(14px);
  border-bottom: 1px solid rgba(231, 236, 247, 0.8);
}

.nav-inner {
  max-width: 1200px;
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

.brand-icon {
  font-size: 22px;
}

.brand-name {
  font-size: 20px;
  font-weight: 700;
  color: var(--sc-text);
}

.nav-links {
  display: flex;
  gap: 28px;
  margin-left: 48px;
}

.nav-links a {
  color: var(--sc-text-secondary);
  text-decoration: none;
  font-size: 14px;
  transition: color 0.2s;
}

.nav-links a:hover {
  color: var(--sc-primary);
}

.nav-actions {
  display: flex;
  gap: 10px;
  align-items: center;
}

.nav-user {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  padding: 4px 8px;
  border-radius: 999px;
  transition: background 0.2s;
}

.nav-user:hover {
  background: #f2f5ff;
}

.nav-avatar {
  font-size: 13px;
}

.nav-nickname {
  font-size: 14px;
  font-weight: 600;
  color: var(--sc-text);
  max-width: 120px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.nav-caret {
  color: var(--sc-text-muted);
  font-size: 12px;
}

/* ============ Hero ============ */
.hero {
  position: relative;
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 64px;
  padding: 120px 40px 80px;
  overflow: hidden;
  background: linear-gradient(160deg, #f2f5ff 0%, #fbf5ff 55%, #fdf2f8 100%);
}

.hero-glow {
  position: absolute;
  border-radius: 50%;
  filter: blur(80px);
  opacity: 0.5;
  pointer-events: none;
}

.glow-1 {
  width: 520px;
  height: 520px;
  top: -120px;
  left: -120px;
  background: radial-gradient(circle, rgba(102, 126, 234, 0.55), transparent 70%);
}

.glow-2 {
  width: 460px;
  height: 460px;
  bottom: -80px;
  right: -100px;
  background: radial-gradient(circle, rgba(139, 92, 246, 0.5), transparent 70%);
}

.hero-content {
  position: relative;
  z-index: 2;
  max-width: 540px;
}

.hero-badge {
  display: inline-block;
  padding: 6px 14px;
  border-radius: 999px;
  background: #fff;
  border: 1px solid var(--sc-border);
  color: var(--sc-text-secondary);
  font-size: 13px;
  margin-bottom: 22px;
  box-shadow: 0 4px 12px -6px rgba(31, 45, 61, 0.12);
}

.hero-title {
  font-size: 48px;
  font-weight: 800;
  line-height: 1.2;
  letter-spacing: 1px;
  margin-bottom: 20px;
}

.hero-sub {
  color: var(--sc-text-secondary);
  font-size: 16px;
  line-height: 1.8;
  margin-bottom: 32px;
}

.hero-actions {
  display: flex;
  gap: 12px;
  margin-bottom: 26px;
}

.hero-input {
  max-width: 260px;
}

.hero-meta {
  display: flex;
  gap: 18px;
  flex-wrap: wrap;
}

.meta-item {
  color: var(--sc-text-muted);
  font-size: 13px;
}

/* ============ 画布预览 ============ */
.hero-visual {
  position: relative;
  z-index: 2;
  flex-shrink: 0;
}

.canvas-preview {
  width: 420px;
  height: 320px;
  border-radius: 20px;
  background: #fff;
  box-shadow: var(--sc-shadow-lg);
  border: 1px solid var(--sc-border);
  overflow: hidden;
  transform: perspective(1200px) rotateY(-8deg) rotateX(4deg);
  transition: transform 0.5s var(--sc-ease);
}

.canvas-preview:hover {
  transform: perspective(1200px) rotateY(-2deg) rotateX(1deg);
}

.mini-toolbar {
  display: flex;
  gap: 6px;
  padding: 12px 16px;
  border-bottom: 1px solid var(--sc-border);
  background: #fafcff;
}

.tool-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: #dfe6f5;
}

.tool-dot:first-child {
  background: #ff7a7a;
}

.tool-dot:nth-child(2) {
  background: #ffd06b;
}

.mini-canvas {
  position: relative;
  height: calc(100% - 43px);
  background-image: radial-gradient(#dfe6f5 1.2px, transparent 1.2px);
  background-size: 20px 20px;
}

.preview-note {
  position: absolute;
  width: 116px;
  min-height: 76px;
  padding: 12px 10px;
  border-radius: 6px;
  font-size: 12px;
  line-height: 1.5;
  box-shadow: 0 8px 18px -8px rgba(31, 45, 61, 0.35);
  word-break: break-all;
}

.note-pin {
  position: absolute;
  top: -7px;
  left: 50%;
  transform: translateX(-50%);
  width: 16px;
  height: 16px;
  border-radius: 50% 50% 50% 0;
  background: rgba(255, 255, 255, 0.55);
  transform: translateX(-50%) rotate(-45deg);
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.15);
}

/* 便利贴颜色 */
.yellow { background: #ffef9f; }
.blue { background: #cfe3ff; }
.green { background: #c9f0dd; }
.pink { background: #ffd6ec; }
.purple { background: #e3d9ff; }

/* 浮动动画 */
.float-1 { animation: floaty 5.5s ease-in-out infinite; }
.float-2 { animation: floaty 6.2s ease-in-out 0.6s infinite; }
.float-3 { animation: floaty 5s ease-in-out 1.1s infinite; }

@keyframes floaty {
  0%, 100% { transform: translateY(0) rotate(0deg); }
  50% { transform: translateY(-14px) rotate(2deg); }
}

/* ============ 区块通用 ============ */
.features,
.workflow {
  padding: 90px 24px;
  max-width: 1200px;
  margin: 0 auto;
}

.section-head {
  text-align: center;
  margin-bottom: 52px;
}

.section-title {
  font-size: 32px;
  font-weight: 800;
  margin-bottom: 12px;
}

.section-desc {
  color: var(--sc-text-secondary);
  font-size: 15px;
}

/* ============ 功能卡片 ============ */
.feature-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 24px;
}

.feature-card {
  background: var(--sc-card-bg);
  border: 1px solid var(--sc-border);
  border-radius: var(--sc-radius);
  padding: 30px 26px;
  box-shadow: var(--sc-shadow);
  transition: transform 0.35s var(--sc-ease), box-shadow 0.35s var(--sc-ease);
  animation: fadeUp 0.6s var(--sc-ease) both;
}

.feature-card:hover {
  transform: translateY(-6px);
  box-shadow: 0 24px 48px -18px rgba(91, 108, 255, 0.35);
}

.feature-icon {
  width: 52px;
  height: 52px;
  border-radius: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 26px;
  margin-bottom: 18px;
}

.feature-icon.blue { background: #e8eeff; }
.feature-icon.purple { background: #f0e9ff; }
.feature-icon.green { background: #e3f8ee; }

.feature-title {
  font-size: 19px;
  font-weight: 700;
  margin-bottom: 10px;
}

.feature-desc {
  color: var(--sc-text-secondary);
  font-size: 14px;
  line-height: 1.7;
  margin-bottom: 18px;
}

.feature-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.tag {
  padding: 4px 10px;
  border-radius: 999px;
  background: #f2f5ff;
  color: var(--sc-primary-soft);
  font-size: 12px;
  border: 1px solid #e3e9ff;
}

/* ============ 使用流程 ============ */
.workflow {
  padding-bottom: 100px;
}

.steps {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 32px;
  position: relative;
}

.step {
  text-align: center;
  padding: 34px 22px;
  position: relative;
  background: var(--sc-card-bg);
  border-radius: var(--sc-radius);
  border: 1px solid var(--sc-border);
  transition: transform 0.35s var(--sc-ease), box-shadow 0.35s var(--sc-ease);
}

.step:hover {
  transform: translateY(-6px);
  box-shadow: var(--sc-shadow-lg);
}

.step-num {
  position: absolute;
  top: -16px;
  left: 50%;
  transform: translateX(-50%);
  width: 32px;
  height: 32px;
  border-radius: 50%;
  background: linear-gradient(120deg, var(--sc-grad-1), var(--sc-grad-2));
  color: #fff;
  font-weight: 700;
  line-height: 32px;
  box-shadow: 0 6px 14px -4px rgba(102, 126, 234, 0.6);
}

.step-icon {
  font-size: 36px;
  margin-bottom: 14px;
}

.step h4 {
  font-size: 17px;
  margin-bottom: 8px;
}

.step p {
  color: var(--sc-text-secondary);
  font-size: 13px;
  line-height: 1.7;
}

/* ============ 底部 ============ */
.footer {
  text-align: center;
  padding: 30px 24px;
  color: var(--sc-text-muted);
  font-size: 13px;
  border-top: 1px solid var(--sc-border);
  background: #fff;
}

@keyframes fadeUp {
  from {
    opacity: 0;
    transform: translateY(24px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

/* ============ 响应式 ============ */
@media (max-width: 1024px) {
  .hero {
    flex-direction: column;
    padding: 110px 24px 60px;
    gap: 48px;
  }

  .hero-content {
    max-width: 100%;
    text-align: center;
  }

  .hero-actions,
  .hero-meta {
    justify-content: center;
  }

  .feature-grid,
  .steps {
    grid-template-columns: 1fr;
  }
}
</style>
