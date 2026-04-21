<template>
  <div class="page">
    <div class="brand">
      <div class="brand-logo" aria-hidden="true">D</div>
      <div class="brand-title">登录 DevCollab</div>
      <div class="brand-sub">协作与信用评估平台</div>
    </div>

    <div class="panel">
      <el-form v-if="!adminMode" label-position="top" class="form" @submit.prevent>
        <el-form-item label="账号">
          <el-input v-model="pwdForm.username" placeholder="用户名 / 手机号 / GitHub" autocomplete="username" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input v-model="pwdForm.password" type="password" show-password autocomplete="current-password" />
        </el-form-item>
        <div class="row">
          <el-checkbox v-model="rememberPwd">记住密码</el-checkbox>
          <a class="mini-link" role="button" tabindex="0" @click="adminMode = true">管理员入口</a>
        </div>
      </el-form>

      <el-form v-else label-position="top" class="form" @submit.prevent>
        <el-form-item label="管理员账号">
          <el-input v-model="adminForm.username" placeholder="admin" autocomplete="username" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input v-model="adminForm.password" type="password" show-password autocomplete="current-password" />
        </el-form-item>
        <div class="row">
          <span class="muted2">仅管理员可登录后台</span>
          <a class="mini-link" role="button" tabindex="0" @click="adminMode = false">返回普通登录</a>
        </div>
      </el-form>

      <el-button class="primary-btn" type="primary" :loading="loading" @click="doLogin">
        {{ adminMode ? '管理员登录' : '登录' }}
      </el-button>

      <div class="oauth" v-if="githubClientId">
        <div class="or">或</div>
        <el-button class="ghost-wide" @click="startGithubLogin">使用 GitHub 登录</el-button>
      </div>
    </div>

    <div class="panel panel-slim">
      <div class="muted">
        还没有账号？
        <a class="link" role="button" tabindex="0" @click="goRegister">去注册</a>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { api, unwrap } from '../api'
import { auth } from '../auth'
import { prepareGithubOAuthState, saveGithubOAuthRedirect } from '../oauthGithub'

const REMEMBER_KEY = 'rememberUser'

const router = useRouter()
const route = useRoute()

const adminMode = ref(false)
const loading = ref(false)

const pwdForm = reactive({ username: '', password: '' })
const adminForm = reactive({ username: 'admin', password: '' })
const rememberPwd = ref(false)

const githubClientId = String((import.meta as any).env?.VITE_GITHUB_CLIENT_ID || '').trim()

onMounted(() => {
  try {
    const raw = localStorage.getItem(REMEMBER_KEY)
    if (raw) {
      const o = JSON.parse(raw)
      if (o && typeof o === 'object') {
        pwdForm.username = o.username || ''
        pwdForm.password = o.password || ''
        rememberPwd.value = true
      }
    }
  } catch {
    /* ignore */
  }
})

const persistRemember = (userObj: any) => {
  if (!adminMode.value) {
    if (rememberPwd.value) {
      localStorage.setItem(REMEMBER_KEY, JSON.stringify({ username: pwdForm.username, password: pwdForm.password }))
    } else {
      localStorage.removeItem(REMEMBER_KEY)
    }
    try {
      localStorage.setItem('username', userObj?.username || pwdForm.username || '')
    } catch {
      /* ignore */
    }
  } else {
    try {
      localStorage.setItem('username', userObj?.username || adminForm.username || '')
    } catch {
      /* ignore */
    }
  }
}

const doLogin = async () => {
  let submit = { username: '', password: '' }
  if (adminMode.value) {
    submit = { username: adminForm.username.trim(), password: adminForm.password }
  } else {
    submit = { username: pwdForm.username.trim(), password: pwdForm.password }
  }

  if (!submit.username || !submit.password) {
    ElMessage.warning('请输入账号和密码')
    return
  }

  loading.value = true
  try {
    const data = await unwrap<{ token: string; user: any }>(
      api.post('/api/user/login', submit, { skipAuthLogout: true }),
    )

    if (adminMode.value) {
      if (data.user?.role !== 'admin') {
        ElMessage.error('权限拒绝：该账号不是管理员')
        return
      }
      auth.setLogin(data.token, data.user)
      persistRemember(data.user)
      ElMessage.success('管理员登录成功')
      router.replace('/admin')
      return
    }

    if (data.user?.role === 'admin') {
      ElMessage.warning('检测到管理员账号，请从「管理员入口」进入后台')
      return
    }

    auth.setLogin(data.token, data.user)
    persistRemember(data.user)
    ElMessage.success('登录成功')
    const redirect = String(route.query.redirect || '/home')
    router.replace(redirect)
  } catch (e: any) {
    ElMessage.error(e?.message || '登录失败')
  } finally {
    loading.value = false
  }
}

const goRegister = () => router.push('/register')

const startGithubLogin = () => {
  if (!githubClientId) return
  // 记住登录成功后的回跳地址（仅站内相对路径）
  const redirect = String(route.query.redirect || '').trim()
  if (redirect && redirect.startsWith('/')) {
    saveGithubOAuthRedirect(redirect)
  }
  const redirectUri = `${window.location.origin}/auth/callback`
  const state = prepareGithubOAuthState()
  const url =
    `https://github.com/login/oauth/authorize` +
    `?client_id=${encodeURIComponent(githubClientId)}` +
    `&scope=${encodeURIComponent('user,repo')}` +
    `&redirect_uri=${encodeURIComponent(redirectUri)}` +
    `&state=${encodeURIComponent(state)}`
  window.location.href = url
}
</script>

<style scoped>
.page{
  min-height: calc(100vh - 56px);
  padding: 44px 16px;
  display:flex;
  flex-direction: column;
  align-items:center;
  gap: 18px;
  background: #f6f8fa;
}

.brand{ text-align:center; margin-top: 6px; }
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
.brand-title{ color:#111827; font-weight: 900; font-size: 22px; letter-spacing: .1px; }
.brand-sub{ margin-top: 6px; color:#6b7280; font-size: 13px; }

.panel{
  width: 340px;
  border: 1px solid #d0d7de;
  border-radius: 6px;
  background: #fff;
  padding: 16px;
  box-shadow: none;
}
.panel-slim{ padding: 14px 16px; background: #fff; }

.form{ margin-top: 12px; }
.row{ display:flex; align-items:center; justify-content: space-between; margin-top: 2px; }
.muted2{ color:#6b7280; font-size: 12px; }
.mini-link{ color:#0969da; cursor: pointer; font-size: 12px; user-select:none; }
.mini-link:hover{ text-decoration: underline; }

.primary-btn{
  width: 100%;
  margin-top: 12px;
  height: 36px;
  font-weight: 600;
  border-radius: 6px;
}
.oauth{ margin-top: 14px; }
.or{ text-align:center; color:#6b7280; font-size: 12px; margin: 10px 0; }

.ghost-wide{ width: 100%; height: 36px; border-radius: 6px; font-weight: 600; }
.ghost-btn{ border-radius: 6px; }

.muted{ color:#24292f; font-size: 14px; text-align:center; }
.link{ color:#0969da; cursor: pointer; }
.link:hover{ text-decoration: underline; }

/* Element Plus dark-ish overrides (scoped) */
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
:deep(.el-radio-button__inner){
  background: #f6f8fa;
  border-color: #d0d7de;
  color: #24292f;
}
:deep(.el-radio-button__original-radio:checked + .el-radio-button__inner){
  background: #fff;
  border-color: #d0d7de;
  color:#24292f;
}
:deep(.el-checkbox__label){ color:#24292f; }
:deep(.el-button.ghost-wide),
:deep(.el-button.ghost-btn){
  background: #f6f8fa;
  border-color: #d0d7de;
  color:#24292f;
}
:deep(.el-button.ghost-wide:hover),
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
