<template>
  <div class="reset-page">
    <!-- 背景光晕 -->
    <div class="reset-glow glow-1"></div>
    <div class="reset-glow glow-2"></div>

    <div class="reset-card">
      <div class="reset-brand" @click="$router.push('/')">
        <img src="@/assets/logo.png" class="brand-icon" alt="SmartStorm" />
        <span class="brand-name">Smart<span class="grad-text">Storm</span></span>
      </div>
      <h3 class="reset-title">重置密码</h3>
      <p class="reset-sub">通过邮箱验证码找回你的密码</p>

      <el-form :model="form" :rules="rules" ref="formRef" label-position="top" size="large">
        <el-form-item label="邮箱" prop="email">
          <el-input v-model="form.email" placeholder="请输入注册邮箱" :prefix-icon="Message" />
        </el-form-item>

        <el-form-item label="验证码" prop="code">
          <div class="code-row">
            <el-input v-model="form.code" placeholder="6 位验证码" :prefix-icon="Key" maxlength="6" />
            <el-button class="code-btn" :disabled="countdown > 0" @click="onSendCode">
              {{ countdown > 0 ? `${countdown}s 后重发` : '获取验证码' }}
            </el-button>
          </div>
        </el-form-item>

        <el-form-item label="新密码" prop="password">
          <el-input
            v-model="form.password"
            type="password"
            placeholder="6-32 位新密码"
            show-password
            :prefix-icon="Lock"
          />
        </el-form-item>

        <el-form-item label="确认密码" prop="confirm">
          <el-input
            v-model="form.confirm"
            type="password"
            placeholder="再次输入新密码"
            show-password
            :prefix-icon="Lock"
            @keyup.enter="onSubmit"
          />
        </el-form-item>

        <el-button class="submit-btn" type="primary" size="large" :loading="submitting" @click="onSubmit">
          重置密码
        </el-button>
      </el-form>

      <div class="reset-back" @click="$router.push('/login')">← 返回登录</div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onBeforeUnmount, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Key, Lock, Message } from '@element-plus/icons-vue'
import { sendResetCode, resetPassword } from '@/api/auth'

const router = useRouter()

const formRef = ref()
const submitting = ref(false)

const form = reactive({ email: '', code: '', password: '', confirm: '' })

const rules = {
  email: [
    { required: true, message: '请输入邮箱', trigger: 'blur' },
    { type: 'email', message: '邮箱格式不正确', trigger: 'blur' }
  ],
  code: [{ required: true, message: '请输入验证码', trigger: 'blur' }],
  password: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, max: 32, message: '密码长度需在 6-32 位之间', trigger: 'blur' }
  ],
  confirm: [
    { required: true, message: '请再次输入新密码', trigger: 'blur' },
    {
      validator: (_rule: unknown, value: string, callback: (err?: Error) => void) => {
        if (value !== form.password) callback(new Error('两次输入的密码不一致'))
        else callback()
      },
      trigger: 'blur'
    }
  ]
}

// ---------- 验证码倒计时 ----------
const countdown = ref(0)
let timer: number | null = null

async function onSendCode() {
  const email = form.email.trim()
  if (!email) {
    ElMessage.warning('请先输入邮箱')
    return
  }
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
    ElMessage.warning('邮箱格式不正确')
    return
  }
  try {
    await sendResetCode(email)
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

async function onSubmit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  submitting.value = true
  try {
    await resetPassword({
      email: form.email.trim(),
      code: form.code.trim(),
      password: form.password
    })
    ElMessage.success('密码已重置，请使用新密码登录')
    router.push('/login')
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '重置失败')
  } finally {
    submitting.value = false
  }
}

onBeforeUnmount(() => {
  if (timer) clearInterval(timer)
})
</script>

<style scoped>
.reset-page {
  position: relative;
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(160deg, #f2f5ff 0%, #fbf5ff 55%, #fdf2f8 100%);
  overflow: hidden;
  padding: 40px 20px;
}

.reset-glow {
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

.reset-card {
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

.reset-brand {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  cursor: pointer;
}

.brand-icon {
  height: 30px;
  width: auto;      /* 图标不是正方形（256x222），按高度撑开保持比例 */
  display: block;
  flex: 0 0 auto;
}

.brand-name {
  font-size: 22px;
  font-weight: 700;
  color: var(--sc-text);
}

.reset-title {
  text-align: center;
  margin: 18px 0 4px;
  font-size: 20px;
  font-weight: 700;
  color: var(--sc-text);
}

.reset-sub {
  text-align: center;
  color: var(--sc-text-muted);
  font-size: 13px;
  margin: 0 0 22px;
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

.submit-btn {
  width: 100%;
  margin-top: 6px;
  font-weight: 600;
}

.reset-back {
  margin-top: 18px;
  text-align: center;
  color: var(--sc-text-muted);
  font-size: 13px;
  cursor: pointer;
  transition: color 0.2s;
}

.reset-back:hover {
  color: var(--sc-primary);
}
</style>
