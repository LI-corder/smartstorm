<template>
  <div class="login-page">
    <!-- 背景光晕 -->
    <div class="login-glow glow-1"></div>
    <div class="login-glow glow-2"></div>

    <div class="login-card">
      <div class="login-brand" @click="$router.push('/')">
        <span class="brand-icon">🧠</span>
        <span class="brand-name">Smart<span class="grad-text">Storm</span></span>
      </div>
      <p class="login-sub">登录 / 注册 SmartStorm 账号</p>

      <!-- 登录 / 注册切换 -->
      <el-tabs v-model="mode" class="auth-tabs" stretch>
        <!-- ============ 登录 ============ -->
        <el-tab-pane label="登录" name="login">
          <el-form :model="loginForm" :rules="loginRules" ref="loginFormRef" label-position="top" size="large">
            <el-form-item label="邮箱" prop="email">
              <el-input v-model="loginForm.email" placeholder="请输入邮箱" :prefix-icon="Message" />
            </el-form-item>
            <el-form-item label="密码" prop="password">
              <el-input
                v-model="loginForm.password"
                type="password"
                placeholder="请输入密码"
                show-password
                :prefix-icon="Lock"
                @keyup.enter="onLogin"
              />
            </el-form-item>
            <div class="forgot-row">
              <span class="forgot-link" @click="goResetPassword">忘记密码？</span>
            </div>
            <el-button class="submit-btn" type="primary" size="large" :loading="loginLoading" @click="onLogin">
              登 录
            </el-button>
          </el-form>
        </el-tab-pane>

        <!-- ============ 注册 ============ -->
        <el-tab-pane label="注册" name="register">
          <el-form :model="regForm" :rules="regRules" ref="regFormRef" label-position="top" size="large">
            <el-form-item label="邮箱" prop="email">
              <el-input v-model="regForm.email" placeholder="请输入邮箱" :prefix-icon="Message" />
            </el-form-item>

            <el-form-item label="验证码" prop="code">
              <div class="code-row">
                <el-input v-model="regForm.code" placeholder="6 位验证码" :prefix-icon="Key" maxlength="6" />
                <el-button class="code-btn" :disabled="countdown > 0" @click="onSendCode">
                  {{ countdown > 0 ? `${countdown}s 后重发` : '获取验证码' }}
                </el-button>
              </div>
            </el-form-item>

            <el-form-item label="昵称" prop="nickname">
              <el-input v-model="regForm.nickname" placeholder="给自己起个名字" :prefix-icon="User" maxlength="32" />
            </el-form-item>

            <el-form-item label="密码" prop="password">
              <el-input
                v-model="regForm.password"
                type="password"
                placeholder="6-32 位密码"
                show-password
                :prefix-icon="Lock"
                @keyup.enter="onRegister"
              />
            </el-form-item>
            <el-button class="submit-btn" type="primary" size="large" :loading="regLoading" @click="onRegister">
              注册并登录
            </el-button>
          </el-form>
        </el-tab-pane>
      </el-tabs>

      <div class="login-back" @click="$router.push('/')">← 返回首页</div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onBeforeUnmount, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Key, Lock, Message, User } from '@element-plus/icons-vue'
import { sendCode, register as registerApi, login as loginApi } from '@/api/auth'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const authStore = useAuthStore()

const mode = ref<'login' | 'register'>('login')

// ---------- 表单 ----------
const loginFormRef = ref()
const regFormRef = ref()
const loginLoading = ref(false)
const regLoading = ref(false)

const loginForm = reactive({ email: '', password: '' })
const regForm = reactive({ email: '', code: '', password: '', nickname: '' })

const emailRule = { required: true, message: '请输入邮箱', trigger: 'blur' }
const loginRules = {
  email: [
    emailRule,
    { type: 'email', message: '邮箱格式不正确', trigger: 'blur' }
  ],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}
const regRules = {
  email: [
    emailRule,
    { type: 'email', message: '邮箱格式不正确', trigger: 'blur' }
  ],
  code: [{ required: true, message: '请输入验证码', trigger: 'blur' }],
  nickname: [
    { required: true, message: '请输入昵称', trigger: 'blur' },
    { min: 1, max: 32, message: '昵称最长 32 位', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 32, message: '密码长度需在 6-32 位之间', trigger: 'blur' }
  ]
}

// ---------- 验证码倒计时 ----------
const countdown = ref(0)
let timer: number | null = null

async function onSendCode() {
  const email = regForm.email.trim()
  if (!email) {
    ElMessage.warning('请先输入邮箱')
    return
  }
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
    ElMessage.warning('邮箱格式不正确')
    return
  }
  try {
    await sendCode(email)
    ElMessage.success('验证码已发送，请查收邮箱')
    countdown.value = 60
    timer = window.setInterval(() => {
      countdown.value--
      if (countdown.value <= 0 && timer) {
        clearInterval(timer)
        timer = null
      }
    }, 1000)
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '验证码发送失败')
  }
}

function extractError(e: any, fallback: string) {
  return e?.response?.data?.message || fallback
}

function goResetPassword() {
  router.push('/reset-password')
}

async function onLogin() {
  const valid = await loginFormRef.value?.validate().catch(() => false)
  if (!valid) return
  loginLoading.value = true
  try {
    const res = await loginApi({ email: loginForm.email.trim(), password: loginForm.password })
    const data = res.data.data
    authStore.setAuth(data.token, data.user)
    ElMessage.success(`欢迎回来，${data.user.nickname}`)
    router.push('/')
  } catch (e: any) {
    ElMessage.error(extractError(e, '登录失败'))
  } finally {
    loginLoading.value = false
  }
}

async function onRegister() {
  const valid = await regFormRef.value?.validate().catch(() => false)
  if (!valid) return
  regLoading.value = true
  try {
    const res = await registerApi({
      email: regForm.email.trim(),
      code: regForm.code.trim(),
      password: regForm.password,
      nickname: regForm.nickname.trim()
    })
    const data = res.data.data
    authStore.setAuth(data.token, data.user)
    ElMessage.success(`注册成功，欢迎 ${data.user.nickname}`)
    router.push('/')
  } catch (e: any) {
    ElMessage.error(extractError(e, '注册失败'))
  } finally {
    regLoading.value = false
  }
}

onBeforeUnmount(() => {
  if (timer) clearInterval(timer)
})
</script>

<style scoped>
.login-page {
  position: relative;
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(160deg, #f2f5ff 0%, #fbf5ff 55%, #fdf2f8 100%);
  overflow: hidden;
  padding: 40px 20px;
}

.login-glow {
  position: absolute;
  border-radius: 50%;
  filter: blur(80px);
  opacity: 0.5;
  pointer-events: none;
}

.glow-1 {
  width: 480px;
  height: 480px;
  top: -120px;
  left: -120px;
  background: radial-gradient(circle, rgba(102, 126, 234, 0.55), transparent 70%);
}

.glow-2 {
  width: 420px;
  height: 420px;
  bottom: -100px;
  right: -100px;
  background: radial-gradient(circle, rgba(139, 92, 246, 0.5), transparent 70%);
}

.login-card {
  position: relative;
  z-index: 2;
  width: 400px;
  max-width: 100%;
  background: rgba(255, 255, 255, 0.94);
  backdrop-filter: blur(12px);
  border: 1px solid var(--sc-border);
  border-radius: 20px;
  box-shadow: var(--sc-shadow-lg);
  padding: 36px 40px 28px;
}

.login-brand {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  cursor: pointer;
}

.brand-icon {
  font-size: 26px;
}

.brand-name {
  font-size: 22px;
  font-weight: 700;
  color: var(--sc-text);
}

.login-sub {
  text-align: center;
  color: var(--sc-text-muted);
  font-size: 13px;
  margin: 10px 0 22px;
}

.auth-tabs :deep(.el-tabs__item) {
  font-size: 15px;
}

.submit-btn {
  width: 100%;
  margin-top: 6px;
  font-weight: 600;
}

.forgot-row {
  display: flex;
  justify-content: flex-end;
  margin: -4px 0 6px;
}

.forgot-link {
  font-size: 13px;
  color: var(--sc-text-muted);
  cursor: pointer;
  transition: color 0.2s;
}

.forgot-link:hover {
  color: var(--sc-primary);
}

.code-row {
  display: flex;
  gap: 10px;
  width: 100%;
}

.code-btn {
  flex-shrink: 0;
  width: 120px;
}

.login-back {
  margin-top: 18px;
  text-align: center;
  color: var(--sc-text-muted);
  font-size: 13px;
  cursor: pointer;
  transition: color 0.2s;
}

.login-back:hover {
  color: var(--sc-primary);
}
</style>
