<template>
  <div class="profile-page">
    <!-- 背景光晕 -->
    <div class="profile-glow glow-1"></div>
    <div class="profile-glow glow-2"></div>

    <!-- ==================== 顶部导航 ==================== -->
    <header class="nav">
      <div class="nav-inner">
        <div class="brand" @click="$router.push('/')">
          <span class="brand-icon">🧠</span>
          <span class="brand-name">Smart<span class="grad-text">Storm</span></span>
        </div>
        <div class="nav-actions">
          <el-button class="btn-ghost" round @click="$router.push('/')">← 返回首页</el-button>
        </div>
      </div>
    </header>

    <!-- ==================== 主体 ==================== -->
    <main class="profile-main" v-loading="loading" element-loading-text="加载中…">
      <template v-if="profile">
        <!-- 左侧用户信息卡 -->
        <aside class="profile-side">
          <div class="user-card">
            <el-avatar :size="76" :style="{ background: avatarBgOf(profile.user.avatarColor) }" class="user-avatar">
              {{ profile.user.nickname?.slice(0, 1) }}
            </el-avatar>
            <h2 class="user-nickname">{{ profile.user.nickname }}</h2>
            <p class="user-email">{{ profile.user.email }}</p>
            <div class="user-meta">
              <el-icon><Calendar /></el-icon>
              <span>{{ joinedAtLabel }}</span>
            </div>
          </div>

          <div class="mini-stats">
            <div class="mini-stat">
              <span class="mini-num">{{ profile.stats.createdRooms }}</span>
              <span class="mini-label">我创建的</span>
            </div>
            <div class="mini-stat">
              <span class="mini-num">{{ profile.stats.participatedRooms }}</span>
              <span class="mini-label">我参与的</span>
            </div>
            <div class="mini-stat">
              <span class="mini-num">{{ profile.stats.notes }}</span>
              <span class="mini-label">便利贴</span>
            </div>
            <div class="mini-stat">
              <span class="mini-num">{{ profile.stats.analyses }}</span>
              <span class="mini-label">整理</span>
            </div>
          </div>
        </aside>

        <!-- 右侧功能面板 -->
        <section class="profile-body">
          <el-tabs v-model="activeTab" class="profile-tabs">
            <!-- ============ 资料编辑 ============ -->
            <el-tab-pane label="资料编辑" name="edit">
              <el-form label-position="top" class="panel-form">
                <el-form-item label="昵称" required>
                  <el-input v-model="editForm.nickname" placeholder="给自己起个名字" maxlength="32" show-word-limit />
                </el-form-item>

                <el-form-item label="头像颜色">
                  <div class="color-row">
                    <div
                      v-for="c in AVATAR_COLORS"
                      :key="c"
                      class="color-dot"
                      :class="{ active: editForm.avatarColor === c }"
                      :style="{ background: AVATAR_BG[c] }"
                      :title="colorLabel(c)"
                      @click="editForm.avatarColor = c"
                    />
                  </div>
                </el-form-item>

                <div class="form-actions">
                  <el-button class="btn-primary" :loading="savingProfile" round @click="onSaveProfile">
                    保存资料
                  </el-button>
                </div>
              </el-form>
            </el-tab-pane>

            <!-- ============ 修改密码 ============ -->
            <el-tab-pane label="修改密码" name="password">
              <el-form
                ref="pwdFormRef"
                :model="pwdForm"
                :rules="pwdRules"
                label-position="top"
                class="panel-form"
              >
                <el-form-item label="原密码" prop="oldPassword">
                  <el-input
                    v-model="pwdForm.oldPassword"
                    type="password"
                    placeholder="请输入原密码"
                    show-password
                    :prefix-icon="Lock"
                  />
                </el-form-item>
                <el-form-item label="新密码" prop="newPassword">
                  <el-input
                    v-model="pwdForm.newPassword"
                    type="password"
                    placeholder="6-32 位新密码"
                    show-password
                    :prefix-icon="Key"
                  />
                </el-form-item>
                <el-form-item label="确认新密码" prop="confirmPassword">
                  <el-input
                    v-model="pwdForm.confirmPassword"
                    type="password"
                    placeholder="再次输入新密码"
                    show-password
                    :prefix-icon="Key"
                    @keyup.enter="onChangePassword"
                  />
                </el-form-item>
                <div class="form-actions">
                  <el-button class="btn-primary" :loading="savingPwd" round @click="onChangePassword">
                    修改密码
                  </el-button>
                </div>
              </el-form>
            </el-tab-pane>

            <!-- ============ 我的房间 ============ -->
            <el-tab-pane label="我的房间" name="rooms">
              <div class="stat-grid">
                <div class="stat-tile">
                  <span class="stat-num">{{ profile.stats.createdRooms }}</span>
                  <span class="stat-label">我创建的</span>
                </div>
                <div class="stat-tile">
                  <span class="stat-num">{{ profile.stats.participatedRooms }}</span>
                  <span class="stat-label">我参与的</span>
                </div>
                <div class="stat-tile">
                  <span class="stat-num">{{ profile.stats.notes }}</span>
                  <span class="stat-label">便利贴</span>
                </div>
                <div class="stat-tile">
                  <span class="stat-num">{{ profile.stats.analyses }}</span>
                  <span class="stat-label">整理次数</span>
                </div>
              </div>

              <h3 class="list-title">我创建的</h3>
              <div class="room-list">
                <div
                  v-for="room in profile.ownedRooms"
                  :key="room.id"
                  class="room-row"
                  @click="openRoom(room)"
                >
                  <div class="room-row-main">
                    <span class="room-name">{{ room.name }}</span>
                    <span class="room-code">#{{ room.roomCode }}</span>
                  </div>
                  <div class="room-row-meta">
                    <span class="room-meta-item">成员 {{ room.memberCount }}</span>
                    <span class="room-meta-item">便利贴 {{ room.noteCount }}</span>
                    <span class="room-meta-item room-date">{{ room.createdAt?.slice(0, 10) }}</span>
                  </div>
                </div>
                <div v-if="profile.ownedRooms.length === 0" class="room-empty">
                  还没有创建过房间，去首页新建一个吧
                </div>
              </div>

              <h3 class="list-title">我参与的</h3>
              <div class="room-list">
                <div
                  v-for="room in profile.joinedRooms"
                  :key="room.id"
                  class="room-row"
                  @click="openRoom(room)"
                >
                  <div class="room-row-main">
                    <span class="room-name">{{ room.name }}</span>
                    <span class="room-code">#{{ room.roomCode }}</span>
                  </div>
                  <div class="room-row-meta">
                    <span class="room-meta-item">成员 {{ room.memberCount }}</span>
                    <span class="room-meta-item">便利贴 {{ room.noteCount }}</span>
                    <span class="room-meta-item room-date">{{ room.createdAt?.slice(0, 10) }}</span>
                  </div>
                </div>
                <div v-if="profile.joinedRooms.length === 0" class="room-empty">
                  还没有参与过他人的房间
                </div>
              </div>
            </el-tab-pane>
          </el-tabs>
        </section>
      </template>
    </main>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Key, Lock } from '@element-plus/icons-vue'
import type { ProfileData, RoomItem } from '@/types'
import { getProfile, updateProfile, changePassword } from '@/api/profile'
import { useAuthStore } from '@/stores/auth'
import { AVATAR_COLORS, AVATAR_BG, avatarBg as avatarBgOf } from '@/utils/avatar'

const router = useRouter()
const authStore = useAuthStore()

const loading = ref(true)
const profile = ref<ProfileData | null>(null)
const activeTab = ref('edit')

// ---------- 资料编辑 ----------
const editForm = reactive({ nickname: '', avatarColor: 'blue' })
const savingProfile = ref(false)

// ---------- 修改密码 ----------
const pwdFormRef = ref()
const pwdForm = reactive({ oldPassword: '', newPassword: '', confirmPassword: '' })
const savingPwd = ref(false)

const pwdRules = {
  oldPassword: [{ required: true, message: '请输入原密码', trigger: 'blur' }],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, max: 32, message: '新密码长度需在 6-32 位之间', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: '请再次输入新密码', trigger: 'blur' },
    {
      validator: (_rule: unknown, value: string, cb: (e?: Error) => void) => {
        if (value !== pwdForm.newPassword) cb(new Error('两次输入的密码不一致'))
        else cb()
      },
      trigger: 'blur'
    }
  ]
}

const joinedAtLabel = computed(() => {
  const s = profile.value?.user.createdAt
  return s ? `注册于 ${s.slice(0, 10)}` : ''
})

const COLOR_LABELS: Record<string, string> = { blue: '蓝色', purple: '紫色', green: '绿色', orange: '橙色' }
function colorLabel(c: string) {
  return COLOR_LABELS[c] || c
}

function openRoom(room: RoomItem) {
  router.push(`/board/${room.id}`)
}

// ---------- 数据加载 ----------
onMounted(async () => {
  // 刷新后登录态由本地 token 恢复；未登录则引导去登录（401 拦截也会兜底）
  await authStore.fetchMe()
  if (!authStore.isLoggedIn) {
    ElMessage.warning('请先登录')
    router.replace('/login')
    return
  }
  try {
    const res = await getProfile()
    if (res.data.code === 0) {
      profile.value = res.data.data
      editForm.nickname = res.data.data.user.nickname
      editForm.avatarColor = res.data.data.user.avatarColor
    }
  } finally {
    loading.value = false
  }
})

// ---------- 资料编辑 ----------
async function onSaveProfile() {
  const nickname = editForm.nickname.trim()
  if (!nickname) {
    ElMessage.warning('昵称不能为空')
    return
  }
  savingProfile.value = true
  try {
    const res = await updateProfile({ nickname, avatarColor: editForm.avatarColor })
    if (res.data.code === 0) {
      const user = res.data.data
      authStore.setUser(user) // 同步导航栏头像/昵称
      if (profile.value) {
        profile.value.user = { ...profile.value.user, nickname: user.nickname, avatarColor: user.avatarColor }
      }
      ElMessage.success('资料已更新')
    }
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '保存失败')
  } finally {
    savingProfile.value = false
  }
}

// ---------- 修改密码 ----------
async function onChangePassword() {
  const valid = await pwdFormRef.value?.validate().catch(() => false)
  if (!valid) return
  savingPwd.value = true
  try {
    const res = await changePassword({ oldPassword: pwdForm.oldPassword, newPassword: pwdForm.newPassword })
    if (res.data.code === 0) {
      ElMessage.success('密码修改成功，下次登录请使用新密码')
      pwdForm.oldPassword = ''
      pwdForm.newPassword = ''
      pwdForm.confirmPassword = ''
      pwdFormRef.value?.clearValidate()
    }
  } catch (e: any) {
    // 原密码错误后端返回 code=400（而非 401），不会触发登出
    ElMessage.error(e?.response?.data?.message || '修改失败')
  } finally {
    savingPwd.value = false
  }
}
</script>

<style scoped>
/* ============ 背景 & 布局 ============ */
.profile-page {
  position: relative;
  min-height: 100vh;
  background: linear-gradient(160deg, #f2f5ff 0%, #fbf5ff 55%, #fdf2f8 100%);
  overflow: hidden;
  padding-bottom: 60px;
}

.profile-glow {
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

.brand-icon {
  font-size: 22px;
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

/* ============ 主体两栏 ============ */
.profile-main {
  position: relative;
  z-index: 2;
  max-width: 1080px;
  margin: 36px auto 0;
  padding: 0 24px;
  display: grid;
  grid-template-columns: 320px 1fr;
  gap: 24px;
  align-items: start;
  min-height: 60vh;
}

/* ============ 用户信息卡 ============ */
.profile-side {
  position: sticky;
  top: 84px;
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.user-card {
  background: rgba(255, 255, 255, 0.94);
  backdrop-filter: blur(12px);
  border: 1px solid var(--sc-border);
  border-radius: 20px;
  box-shadow: var(--sc-shadow);
  padding: 34px 24px 26px;
  text-align: center;
}

.user-avatar {
  font-size: 30px;
  box-shadow: 0 10px 24px -8px rgba(91, 108, 255, 0.45);
}

.user-nickname {
  font-size: 21px;
  font-weight: 700;
  margin: 16px 0 4px;
  word-break: break-all;
}

.user-email {
  color: var(--sc-text-secondary);
  font-size: 13px;
  word-break: break-all;
}

.user-meta {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  margin-top: 14px;
  color: var(--sc-text-muted);
  font-size: 12px;
}

.mini-stats {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 12px;
}

.mini-stat {
  background: rgba(255, 255, 255, 0.9);
  border: 1px solid var(--sc-border);
  border-radius: 14px;
  padding: 16px 12px;
  text-align: center;
  box-shadow: var(--sc-shadow);
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.mini-num {
  font-size: 24px;
  font-weight: 800;
  background: linear-gradient(120deg, var(--sc-grad-1), var(--sc-grad-2));
  -webkit-background-clip: text;
  background-clip: text;
  -webkit-text-fill-color: transparent;
}

.mini-label {
  color: var(--sc-text-muted);
  font-size: 12px;
}

/* ============ 右侧面板 ============ */
.profile-body {
  background: rgba(255, 255, 255, 0.94);
  backdrop-filter: blur(12px);
  border: 1px solid var(--sc-border);
  border-radius: 20px;
  box-shadow: var(--sc-shadow);
  padding: 20px 30px 34px;
}

.profile-tabs :deep(.el-tabs__item) {
  font-size: 15px;
}

.panel-form {
  max-width: 420px;
  margin-top: 8px;
}

.form-actions {
  margin-top: 6px;
}

/* 头像颜色选择 */
.color-row {
  display: flex;
  gap: 14px;
  padding-top: 4px;
}

.color-dot {
  width: 34px;
  height: 34px;
  border-radius: 50%;
  cursor: pointer;
  border: 3px solid transparent;
  transition: transform 0.2s var(--sc-ease), box-shadow 0.2s var(--sc-ease);
}

.color-dot:hover {
  transform: translateY(-2px);
}

.color-dot.active {
  border-color: #fff;
  box-shadow: 0 0 0 3px var(--sc-primary);
}

/* ============ 我的房间 ============ */
.stat-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 14px;
  margin: 6px 0 26px;
}

.stat-tile {
  background: linear-gradient(160deg, #f2f5ff, #fbf5ff);
  border: 1px solid var(--sc-border);
  border-radius: 14px;
  padding: 18px 10px;
  text-align: center;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.stat-num {
  font-size: 28px;
  font-weight: 800;
  background: linear-gradient(120deg, var(--sc-grad-1), var(--sc-grad-2));
  -webkit-background-clip: text;
  background-clip: text;
  -webkit-text-fill-color: transparent;
}

.stat-label {
  color: var(--sc-text-secondary);
  font-size: 13px;
}

.list-title {
  font-size: 16px;
  font-weight: 700;
  margin: 4px 0 12px;
  padding-left: 10px;
  border-left: 4px solid var(--sc-primary);
  line-height: 1.2;
}

.list-title:not(:first-child) {
  margin-top: 28px;
}

/* ============ 房间行 ============ */
.room-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.room-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 14px 18px;
  background: #fafcff;
  border: 1px solid var(--sc-border);
  border-radius: 12px;
  cursor: pointer;
  transition: border-color 0.2s, box-shadow 0.2s, transform 0.2s var(--sc-ease);
}

.room-row:hover {
  border-color: var(--sc-primary-soft);
  box-shadow: var(--sc-shadow);
  transform: translateY(-1px);
}

.room-row-main {
  display: flex;
  flex-direction: column;
  gap: 3px;
  min-width: 0;
}

.room-name {
  font-weight: 600;
  font-size: 14px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.room-code {
  color: var(--sc-primary);
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 0.5px;
}

.room-row-meta {
  display: flex;
  gap: 14px;
  color: var(--sc-text-muted);
  font-size: 12px;
  flex-shrink: 0;
}

.room-empty {
  padding: 26px;
  text-align: center;
  color: var(--sc-text-muted);
  font-size: 13px;
  background: #fafcff;
  border: 1px dashed var(--sc-border);
  border-radius: 12px;
}

/* ============ 响应式 ============ */
@media (max-width: 860px) {
  .profile-main {
    grid-template-columns: 1fr;
  }

  .profile-side {
    position: static;
  }

  .stat-grid {
    grid-template-columns: repeat(2, 1fr);
  }

  .room-row {
    flex-direction: column;
    align-items: flex-start;
    gap: 8px;
  }
}
</style>
