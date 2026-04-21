<template>
  <div class="page">
    <div class="brand">
      <div class="brand-logo" aria-hidden="true">⌂</div>
      <div class="brand-title">Create your account</div>
    </div>

    <div class="panel">
      <el-form label-position="top" class="form" @submit.prevent>
        <el-form-item label="用户名">
          <el-input v-model="form.username" placeholder="唯一标识" @blur="checkUsername" autocomplete="username" />
          <div v-if="usernameStatus === 'checking'" class="hint">检测中...</div>
          <div v-else-if="usernameStatus === 'ok'" class="ok">✔ 该用户名可用</div>
          <div v-else-if="usernameStatus === 'exist'" class="err">❌ 用户名已存在（演示规则）</div>
          <div v-else class="hint">用于登录与展示</div>
        </el-form-item>

        <el-form-item label="手机号">
          <el-input v-model="form.phone" placeholder="可选：用于找回/安全验证" autocomplete="tel" />
          <div class="hint">手机号与邮箱二选一即可（可都填）</div>
        </el-form-item>

        <el-form-item label="邮箱">
          <el-input v-model="form.email" placeholder="可选：用于登录/找回" autocomplete="email" />
        </el-form-item>

        <el-form-item label="验证码">
          <div class="code-row">
            <el-input v-model="form.code" placeholder="短信验证码（演示环境可随意填）" />
            <el-button class="ghost-btn" :disabled="codeDisabled" @click="getCode">{{ codeText }}</el-button>
          </div>
        </el-form-item>

        <el-form-item label="密码">
          <el-input v-model="form.password" type="password" show-password @input="onPwdInput" autocomplete="new-password" />
          <div class="strength">
            <div class="bar" :class="{ on: pwdStrength >= 1 }" />
            <div class="bar" :class="{ on: pwdStrength >= 2 }" />
            <div class="bar" :class="{ on: pwdStrength >= 3 }" />
          </div>
          <div class="hint">强度：{{ pwdStrengthText }}（建议字母+数字，≥6 位）</div>
        </el-form-item>

        <el-form-item label="确认密码">
          <el-input v-model="form.confirmPassword" type="password" show-password autocomplete="new-password" />
        </el-form-item>

        <el-button class="primary-btn" type="primary" :loading="loading" @click="doRegister">Create account</el-button>
      </el-form>
    </div>

    <div class="panel panel-slim">
      <div class="muted">
        Already have an account？
        <a class="link" role="button" tabindex="0" @click="goLogin">Sign in</a>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { api, unwrap } from '../api'

const router = useRouter()

const loading = ref(false)
const form = reactive({
  username: '',
  email: '',
  phone: '',
  password: '',
  confirmPassword: '',
  code: '',
})

const codeDisabled = ref(false)
const codeText = ref('获取验证码')
const usernameStatus = ref<'none' | 'checking' | 'ok' | 'exist'>('none')
const pwdStrength = ref(0)
const pwdStrengthText = ref('无')

const goLogin = () => router.push('/login')

const checkUsername = () => {
  const u = form.username.trim()
  if (!u) {
    usernameStatus.value = 'none'
    return
  }
  usernameStatus.value = 'checking'
  window.setTimeout(() => {
    if (u === 'admin' || u === 'test') usernameStatus.value = 'exist'
    else usernameStatus.value = 'ok'
  }, 400)
}

const getCode = () => {
  if (!form.phone.trim()) {
    ElMessage.warning('请先输入手机号')
    return
  }
  ElMessage.success('演示环境：验证码已“发送”，可随意填写后继续')
  codeDisabled.value = true
  let n = 60
  codeText.value = `${n}s`
  const t = window.setInterval(() => {
    n--
    codeText.value = n > 0 ? `${n}s` : '获取验证码'
    if (n <= 0) {
      window.clearInterval(t)
      codeDisabled.value = false
    }
  }, 1000)
}

const onPwdInput = () => {
  const val = form.password
  if (!val) {
    pwdStrength.value = 0
    pwdStrengthText.value = '无'
    return
  }
  let s = 0
  if (val.length >= 6) s++
  if (/[a-zA-Z]/.test(val) && /[0-9]/.test(val)) s++
  if (/[^a-zA-Z0-9]/.test(val)) s++
  pwdStrength.value = s
  pwdStrengthText.value = s <= 1 ? '弱' : s === 2 ? '中' : '强'
}

const doRegister = async () => {
  const username = form.username.trim()
  const email = form.email.trim()
  const phone = form.phone.trim()
  const password = form.password
  const confirmPassword = form.confirmPassword

  if (usernameStatus.value === 'exist') return ElMessage.error('用户名不可用')
  if (!username) return ElMessage.warning('请输入用户名')
  if (!phone && !email) return ElMessage.warning('请至少填写手机号或邮箱')
  if (!/^(?=.*[a-zA-Z])(?=.*[0-9])[a-zA-Z0-9!@#$%^&*()_+]{6,16}$/.test(password)) {
    return ElMessage.error('密码需 6–16 位且同时包含字母与数字')
  }
  if (password !== confirmPassword) return ElMessage.warning('两次输入的密码不一致')

  loading.value = true
  try {
    await unwrap(api.post('/api/user/register', { username, phone, email, password }))
    ElMessage.success('注册成功，请登录')
    router.replace('/login')
  } catch (e: any) {
    ElMessage.error(e?.message || '注册失败')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.page{
  min-height: calc(100vh - 56px);
  padding: 48px 16px;
  display:flex;
  flex-direction: column;
  align-items:center;
  gap: 18px;
  background: #f6f8fa;
}

.brand{ text-align:center; margin-top: 8px; }
.brand-logo{
  width: 48px; height: 48px;
  border-radius: 999px;
  background: #111827;
  color: #fff;
  display:flex; align-items:center; justify-content:center;
  font-weight: 900;
  margin: 0 auto 14px;
  box-shadow: 0 8px 24px rgba(0,0,0,0.12);
}
.brand-title{ color:#24292f; font-weight: 300; font-size: 24px; letter-spacing: .1px; }
.brand-sub{ margin-top: 6px; color:#57606a; font-size: 13px; max-width: 420px; }

.panel{
  width: 340px;
  border: 1px solid #d0d7de;
  border-radius: 6px;
  background: #fff;
  padding: 16px;
  box-shadow: none;
}
.panel-slim{ padding: 14px 16px; background: #fff; }

.form{ margin-top: 6px; }
.hint { font-size: 12px; color: #57606a; margin-top: 6px; }
.ok { font-size: 12px; color: #1f883d; margin-top: 6px; }
.err { font-size: 12px; color: #cf222e; margin-top: 6px; }
.code-row { display: flex; gap: 10px; width: 100%; }
.code-row :deep(.el-input) { flex: 1; }
.strength { display: flex; gap: 6px; margin-top: 8px; }
.bar { flex: 1; height: 6px; border-radius: 999px; background: #eaeef2; border: 1px solid #d0d7de; }
.bar.on { background: rgba(31,136,61,0.28); border-color: rgba(31,136,61,0.55); }
.primary-btn{ width: 100%; margin-top: 12px; height: 36px; font-weight: 600; border-radius: 6px; }
.ghost-btn{ border-radius: 6px; }
.muted{ color:#24292f; font-size: 14px; text-align:center; }
.link{ color:#0969da; cursor: pointer; }
.link:hover{ text-decoration: underline; }

:deep(.el-form-item__label){ color:#24292f; font-weight: 600; }
:deep(.el-input__wrapper){
  background: #fff;
  border: 1px solid #d0d7de;
  box-shadow: none;
  border-radius: 6px;
}
:deep(.el-input__inner){ color:#24292f; }
:deep(.el-input__wrapper.is-focus){
  border-color: #0969da;
  box-shadow: 0 0 0 3px rgba(9,105,218,0.15);
}
:deep(.el-button.ghost-btn){
  background: #f6f8fa;
  border-color: #d0d7de;
  color:#24292f;
}
:deep(.el-button.ghost-btn:hover){
  border-color: #0969da;
  color:#0969da;
}
:deep(.el-button.primary-btn){
  background: #1f883d;
  border-color: #1f883d;
}
:deep(.el-button.primary-btn:hover){
  background: #1a7f37;
  border-color: #1a7f37;
}
</style>
