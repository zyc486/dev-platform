<template>
  <div class="wrap">
    <el-card class="card" shadow="never">
      <div class="title">{{ title }}</div>
      <div class="sub">{{ desc }}</div>
      <div style="margin-top: 14px;" v-if="showActions">
        <el-button type="primary" @click="goLogin">去登录</el-button>
        <el-button @click="goMe">个人中心</el-button>
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { api, unwrap } from '../api'
import { auth } from '../auth'
import { consumeGithubOAuthRedirect, consumeGithubOAuthState } from '../oauthGithub'

const route = useRoute()
const router = useRouter()

const title = ref('授权处理中…')
const desc = ref('正在与 GitHub 通信，同步您的开发者档案。')
const showActions = ref(false)

const goLogin = () => router.replace('/login')
const goMe = () => router.replace('/me')

const finishOk = async (msg: string, redirectTo: string) => {
  title.value = '绑定成功'
  desc.value = msg
  ElMessage.success('GitHub 授权处理成功')
  setTimeout(() => router.replace(redirectTo), 900)
}

const finishErr = (t: string, d: string) => {
  title.value = t
  desc.value = d
  showActions.value = true
}

onMounted(async () => {
  const code = String(route.query.code || '').trim()
  if (!code) return finishErr('授权码缺失', '未收到 GitHub 返回的有效 code，请重试。')

  const stateQ = String(route.query.state || '').trim()
  if (!consumeGithubOAuthState(stateQ)) {
    return finishErr('授权校验失败', 'state 不匹配或已过期，请返回个人中心重新发起 GitHub 绑定。')
  }

  // 已登录：走“绑定”流程
  if (auth.token.value) {
    try {
      const gh = await unwrap<string>(api.post(`/api/user/oauth/bind?code=${encodeURIComponent(code)}`))
      // 绑定后刷新一次用户信息，更新本地 me
      try {
        const info = await unwrap<any>(api.get('/api/user/info'))
        auth.setLogin(auth.token.value, info)
      } catch {}
      return finishOk(`已成功关联 GitHub 账号：${gh}。正在返回个人中心…`, '/me')
    } catch (e: any) {
      return finishErr('绑定失败', e?.message || '服务器拒绝了绑定请求，请稍后重试。')
    }
  }

  // 未登录：尝试 OAuth 登录（需要该 GitHub 已绑定平台账号）
  try {
    const data = await unwrap<{ token: string; user: any }>(api.post(`/api/user/oauth/login?code=${encodeURIComponent(code)}`))
    auth.setLogin(data.token, data.user)
    const redirectTo = consumeGithubOAuthRedirect('/home')
    return finishOk('已使用 GitHub 授权完成登录。正在进入页面…', redirectTo)
  } catch (e: any) {
    return finishErr('需要先登录平台账号', e?.message || '该 GitHub 账号尚未绑定平台账号，请先使用账号密码登录后再绑定。')
  }
})
</script>

<style scoped>
.wrap { min-height: 70vh; display:flex; align-items:center; justify-content:center; padding: 40px 12px; }
.card { width: 520px; border-radius: 14px; border: 1px solid #e5e7eb; }
.title { font-weight: 900; font-size: 18px; }
.sub { margin-top: 10px; color: #6b7280; line-height: 1.7; font-size: 13px; }
</style>

