<template>
  <div class="page">
    <div class="head">
      <div>
        <div class="title">个人中心</div>
        <div class="subtitle">完善资料、绑定 GitHub、维护技术标签</div>
      </div>
      <div class="actions">
        <el-button v-if="user?.username" size="small" plain @click="goPublicSelf">公开主页</el-button>
        <el-button size="small" type="primary" plain :loading="exporting" @click="exportMyData">导出我的数据</el-button>
        <el-button size="small" @click="reload">刷新</el-button>
        <el-button size="small" type="danger" plain @click="logout">退出登录</el-button>
      </div>
    </div>

    <el-card class="card">
      <template #header>
        <div class="row">
          <div style="font-weight: 800">账号概览</div>
          <el-tag size="small" type="info">{{ user?.role || 'user' }}</el-tag>
        </div>
      </template>
      <div class="profile-header">
        <div class="avatar-section">
          <div class="avatar-box">
            <img
              v-if="user?.avatar && !avatarBroken"
              :key="user.avatar"
              :src="mediaUrl(user.avatar)"
              :alt="user.nickname || user.username"
              @error="onAvatarError"
            >
            <span v-else class="avatar-placeholder">{{ getInitial(user?.nickname || user?.username || 'U') }}</span>
          </div>
          <div class="avatar-actions">
            <el-upload
              action="#"
              :show-file-list="false"
              accept="image/jpeg,image/png,image/gif,image/webp,.jpg,.jpeg,.png,.gif,.webp"
              :before-upload="beforeAvatarUpload"
              :http-request="uploadAvatarRequest"
              :disabled="avatarUploading"
            >
              <el-button size="small" type="primary" plain :loading="avatarUploading">更换头像</el-button>
            </el-upload>
            <div class="avatar-hint">支持 JPG/PNG/GIF/WebP，不超过 2MB</div>
          </div>
        </div>
        <div class="profile-info">
          <div class="kv">
            <div class="kv-item"><span class="k">用户名</span><span class="v">{{ user?.username || '—' }}</span></div>
            <div class="kv-item"><span class="k">GitHub</span><span class="v">{{ user?.githubUsername || '未绑定' }}</span></div>
            <div class="kv-item"><span class="k">手机号</span><span class="v">{{ user?.phone || '未填写' }}</span></div>
            <div class="kv-item"><span class="k">邮箱</span><span class="v">{{ user?.email || '未填写' }}</span></div>
          </div>
          <div v-if="user?.credit" class="credit">
            <div class="credit-item">
              <div class="k">综合信用</div>
              <div class="v">{{ user.credit.totalScore ?? 0 }} · {{ user.credit.level || '—' }}</div>
            </div>
          </div>
        </div>
      </div>
    </el-card>

    <div class="grid">
      <el-card class="card">
        <template #header>
          <div class="row">
            <div style="font-weight: 800">基础资料</div>
            <el-button size="small" type="primary" :loading="savingProfile" @click="saveProfile">保存</el-button>
          </div>
        </template>

        <el-form label-width="90px">
          <el-form-item label="昵称">
            <el-input v-model="profile.nickname" placeholder="用于展示（可选）" />
          </el-form-item>
          <el-form-item label="简介">
            <el-input v-model="profile.bio" type="textarea" :rows="4" placeholder="介绍你的方向、经验或擅长领域" />
          </el-form-item>
          <el-form-item label="手机号">
            <el-input v-model="profile.phone" placeholder="用于找回/安全验证" />
          </el-form-item>
          <el-form-item label="邮箱">
            <el-input v-model="profile.email" placeholder="用于通知/找回（可选）" />
          </el-form-item>
        </el-form>
      </el-card>

      <el-card class="card">
        <template #header>
          <div class="row">
            <div style="font-weight: 800">GitHub 绑定</div>
            <el-tag size="small" :type="user?.githubUsername ? 'success' : 'warning'">
              {{ user?.githubUsername ? '已绑定' : '未绑定' }}
            </el-tag>
          </div>
        </template>

        <div class="muted">
          绑定后系统才能生成更完整的信用画像，并在协作申请时执行准入风控校验。
        </div>

        <div style="margin-top: 12px; display:flex; gap: 10px; flex-wrap: wrap;">
          <el-button v-if="!user?.githubUsername" type="primary" @click="startGithubBind">立即绑定 GitHub</el-button>
          <el-button v-else type="danger" plain :loading="unbinding" @click="unbindGithub">解除绑定</el-button>
        </div>

        <el-alert
          v-if="!githubClientId"
          style="margin-top:12px;"
          title="未配置 GitHub OAuth ClientId：请在前端环境变量中设置 VITE_GITHUB_CLIENT_ID"
          type="warning"
          show-icon
          :closable="false"
        />
      </el-card>

      <el-card class="card">
        <template #header>
          <div class="row">
            <div style="font-weight: 800">技术标签</div>
            <el-button size="small" type="primary" :loading="savingTags" @click="saveTags">保存</el-button>
          </div>
        </template>

        <div class="muted">使用英文逗号分隔，例如：Java,SpringBoot,Vue,MySQL</div>
        <el-input v-model="techTags" style="margin-top:10px;" placeholder="例如: Java,SpringBoot,Vue" />
      </el-card>
    </div>

    <el-card class="card tabs-card">
      <el-tabs v-model="extraTab" @tab-change="onExtraTabChange">
        <el-tab-pane label="隐私" name="privacy">
          <div class="muted" style="margin-bottom: 12px;">
            这些设置会影响他人查看你在平台上的信用展示与私信入口。
          </div>
          <el-form label-width="140px" style="max-width: 560px;">
            <el-form-item label="公开信用信息">
              <el-switch v-model="privacy.creditPublic" />
            </el-form-item>
            <el-form-item label="公开我的动态">
              <el-switch v-model="privacy.feedPublic" />
            </el-form-item>
            <el-form-item label="允许他人私信">
              <el-switch v-model="privacy.allowMessage" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="privacySaving" @click="savePrivacy">保存隐私设置</el-button>
            </el-form-item>
          </el-form>
          <el-divider />
          <div class="muted">
            提示：关闭“公开信用信息”后，其他用户的公开主页将不再展示你的信用分与明细。
          </div>
        </el-tab-pane>

        <el-tab-pane label="安全" name="security">
          <div class="muted" style="margin-bottom: 12px;">修改登录密码后需重新登录。</div>
          <el-form label-width="100px" style="max-width: 480px;">
            <el-form-item label="当前密码">
              <el-input v-model="passwordForm.oldPassword" type="password" show-password autocomplete="current-password" />
            </el-form-item>
            <el-form-item label="新密码">
              <el-input v-model="passwordForm.newPassword" type="password" show-password autocomplete="new-password" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="passwordLoading" @click="changePassword">保存新密码</el-button>
            </el-form-item>
          </el-form>
          <el-divider />
          <div class="row" style="margin-bottom: 10px;">
            <div style="font-weight: 800">信用数据</div>
            <el-button
              size="small"
              type="primary"
              plain
              :loading="refreshCreditLoading"
              :disabled="!user?.githubUsername"
              @click="refreshCredit"
            >
              刷新信用画像
            </el-button>
          </div>
          <div class="muted">需已绑定 GitHub；刷新后可在信用页查看最新结果。</div>
        </el-tab-pane>

        <el-tab-pane label="我的发布" name="posts">
          <div v-if="!myPosts.length" class="muted">暂无动态</div>
          <div v-else class="list-block">
            <div v-for="p in myPosts" :key="p.id" class="list-row">
              <div>
                <div class="list-title">{{ p.title || '（无标题）' }}</div>
                <div class="list-sub">{{ formatTime(p.createTime) }}</div>
              </div>
              <el-button size="small" type="danger" text @click="deletePost(p.id)">删除</el-button>
            </div>
          </div>
        </el-tab-pane>

        <el-tab-pane label="我的收藏" name="collects">
          <div v-if="!myCollects.length" class="muted">暂无收藏</div>
          <div v-else class="list-block">
            <div v-for="c in myCollects" :key="c.id || c.targetId + '-' + c.type" class="list-row">
              <div>
                <div class="list-title">{{ favoriteLabel(c) }}</div>
                <div class="list-sub">{{ c.type || '—' }}</div>
              </div>
              <el-button size="small" text type="warning" @click="removeFavorite(c)">取消收藏</el-button>
            </div>
          </div>
        </el-tab-pane>

        <el-tab-pane label="关注 / 粉丝" name="follow">
          <div class="two-col-follow">
            <div>
              <div class="pane-title">关注</div>
              <div v-if="!myFollows.length" class="muted">暂无</div>
              <div v-else class="chip-list">
                <el-tag v-for="u in myFollows" :key="'f-' + (u.userId || u.id)" class="chip" @click="goPublic(u.username)">
                  {{ u.username || u.nickname || u.userId }}
                </el-tag>
              </div>
            </div>
            <div>
              <div class="pane-title">粉丝</div>
              <div v-if="!myFans.length" class="muted">暂无</div>
              <div v-else class="chip-list">
                <el-tag v-for="u in myFans" :key="'fan-' + (u.userId || u.id)" class="chip" @click="goPublic(u.username)">
                  {{ u.username || u.nickname || u.userId }}
                </el-tag>
              </div>
            </div>
          </div>
        </el-tab-pane>

        <el-tab-pane label="关注动态" name="feed">
          <div v-if="!followingFeed.length" class="muted">暂无动态</div>
          <div v-else class="list-block">
            <div v-for="(item, idx) in followingFeed" :key="idx" class="feed-line">
              {{ item.content || item.title || JSON.stringify(item) }}
            </div>
          </div>
        </el-tab-pane>

        <el-tab-pane label="成就徽章" name="badges">
          <div v-if="!badges.length" class="muted">暂无徽章数据</div>
          <div v-else class="badge-grid">
            <div
              v-for="b in badges"
              :key="b.id || b.code"
              class="badge-item"
              :class="{ obtained: b.obtained || b.status === 'obtained' }"
            >
              <div class="badge-name">{{ b.name || b.title || b.code }}</div>
              <div class="badge-desc muted">{{ b.description || b.desc || '' }}</div>
            </div>
          </div>
        </el-tab-pane>
      </el-tabs>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { api, unwrap } from '../api'
import { auth } from '../auth'
import { prepareGithubOAuthState } from '../oauthGithub'

const router = useRouter()

const githubClientId = String((import.meta as any).env?.VITE_GITHUB_CLIENT_ID || '').trim()

const user = ref<any>(null)
const avatarUploading = ref(false)
const avatarBroken = ref(false)

const profile = reactive({
  phone: '',
  email: '',
  nickname: '',
  bio: '',
})
const techTags = ref('')

const privacy = reactive({
  creditPublic: true,
  feedPublic: true,
  allowMessage: true,
})
const privacySaving = ref(false)

const savingProfile = ref(false)
const savingTags = ref(false)
const unbinding = ref(false)
const exporting = ref(false)

const extraTab = ref('security')
const passwordForm = reactive({ oldPassword: '', newPassword: '' })
const passwordLoading = ref(false)
const refreshCreditLoading = ref(false)

const myPosts = ref<any[]>([])
const myCollects = ref<any[]>([])
const myFollows = ref<any[]>([])
const myFans = ref<any[]>([])
const followingFeed = ref<any[]>([])
const badges = ref<any[]>([])

const reload = async () => {
  const info = await unwrap<any>(api.get('/api/user/info'))
  user.value = info
  // 同步到全局会话（用于 AppShell 显示用户名等）
  auth.setLogin(auth.token.value, info)

  profile.phone = info.phone || ''
  profile.email = info.email || ''
  profile.nickname = info.nickname || ''
  profile.bio = info.bio || ''
  techTags.value = info.techTags || ''

  await Promise.all([loadMyPosts(), loadMyCollects(), loadMyFollows(), loadMyFans(), loadBadges(), loadPrivacy()])
}

const formatTime = (t: any) => (t ? String(t).replace('T', ' ').slice(0, 16) : '')

const favoriteLabel = (c: any) => {
  if (c.title) return c.title
  if (c.name) return c.name
  if (c.targetId) return String(c.targetId)
  return '收藏项'
}

const goPublic = (username: string) => {
  const u = String(username || '').trim()
  if (u) router.push(`/u/${encodeURIComponent(u)}`)
}

const onExtraTabChange = (name: string | number) => {
  const n = String(name)
  if (n === 'posts') void loadMyPosts()
  if (n === 'collects') void loadMyCollects()
  if (n === 'follow') void Promise.all([loadMyFollows(), loadMyFans()])
  if (n === 'feed') void loadFollowingFeed()
  if (n === 'badges') void loadBadges()
  if (n === 'privacy') void loadPrivacy()
}

const loadMyPosts = async () => {
  const id = user.value?.id
  if (!id) return
  try {
    myPosts.value = await unwrap<any[]>(api.get(`/api/post/myList`, { params: { userId: id } }))
  } catch {
    myPosts.value = []
  }
}

const loadMyCollects = async () => {
  try {
    myCollects.value = await unwrap<any[]>(api.get('/api/favorite/list'))
  } catch {
    myCollects.value = []
  }
}

const loadFollowingFeed = async () => {
  try {
    followingFeed.value = await unwrap<any[]>(api.get('/api/feed/following'))
  } catch {
    followingFeed.value = []
  }
}

const loadMyFollows = async () => {
  try {
    myFollows.value = await unwrap<any[]>(api.get('/follow/my'))
  } catch (e: any) {
    myFollows.value = []
    ElMessage.error(e?.message || '加载关注列表失败')
  }
}

const loadMyFans = async () => {
  try {
    myFans.value = await unwrap<any[]>(api.get('/follow/fans'))
  } catch (e: any) {
    myFans.value = []
    ElMessage.error(e?.message || '加载粉丝列表失败')
  }
}

const onFollowChanged = async () => {
  // 个人中心打开时，尽量同步关注/粉丝/关注动态
  try {
    await Promise.all([loadMyFollows(), loadMyFans()])
    if (extraTab.value === 'feed') await loadFollowingFeed()
  } catch {}
}

const loadBadges = async () => {
  const id = user.value?.id
  if (!id) return
  try {
    const data = await unwrap<any>(api.get(`/api/badge/user/${id}`))
    badges.value = Array.isArray(data) ? data : data?.list || data?.badges || []
  } catch {
    badges.value = []
  }
}

const deletePost = async (id: number) => {
  try {
    await ElMessageBox.confirm('确认删除这条动态吗？', '提示', { type: 'warning' })
  } catch {
    return
  }
  try {
    await unwrap(api.delete(`/api/post/delete/${id}`))
    ElMessage.success('已删除')
    await loadMyPosts()
  } catch (e: any) {
    ElMessage.error(e?.message || '删除失败')
  }
}

const removeFavorite = async (item: any) => {
  try {
    await unwrap(
      api.post('/api/favorite/toggle', { type: item.type, targetId: item.targetId }),
    )
    ElMessage.success('已更新收藏')
    await loadMyCollects()
  } catch (e: any) {
    ElMessage.error(e?.message || '操作失败')
  }
}

const changePassword = async () => {
  if (!passwordForm.oldPassword || !passwordForm.newPassword) {
    return ElMessage.warning('请填写完整密码信息')
  }
  passwordLoading.value = true
  try {
    await unwrap(api.post('/api/user/changePassword', { ...passwordForm }))
    ElMessage.success('密码修改成功，请重新登录')
    auth.logout()
    router.replace('/login')
  } catch (e: any) {
    ElMessage.error(e?.message || '修改失败')
  } finally {
    passwordLoading.value = false
  }
}

const refreshCredit = async () => {
  if (!user.value?.githubUsername) return ElMessage.warning('请先绑定 GitHub')
  refreshCreditLoading.value = true
  try {
    await unwrap(api.post('/api/credit/refresh', {}))
    ElMessage.success('已触发刷新，请稍后在信用页查看')
    await reload()
  } catch (e: any) {
    ElMessage.error(e?.message || '刷新失败')
  } finally {
    refreshCreditLoading.value = false
  }
}

const getInitial = (text: string) => ((text || '?').trim().charAt(0) || '?').toUpperCase()

const onAvatarError = () => {
  avatarBroken.value = true
}

watch(
  () => user.value?.avatar,
  () => {
    avatarBroken.value = false
  },
)

const mediaUrl = (path: string) => {
  if (!path) return ''
  if (path.startsWith('http')) return path
  return `${import.meta.env.VITE_API_BASE || ''}${path}`
}

const beforeAvatarUpload = (file: File) => {
  const okTypes = ['image/jpeg', 'image/png', 'image/gif', 'image/webp']
  const nameOk = /\.(jpe?g|jfif|png|gif|webp)$/i.test(file.name || '')
  const ok = okTypes.includes(file.type) || ((file.type === '' || file.type === 'application/octet-stream') && nameOk)
  if (!ok) {
    ElMessage.error('仅支持 JPG、PNG、GIF、WebP')
    return false
  }
  if (file.size > 2 * 1024 * 1024) {
    ElMessage.error('图片大小不能超过 2MB')
    return false
  }
  return true
}

const uploadAvatarRequest = async (options: any) => {
  const token = auth.token.value
  if (!token) {
    ElMessage.warning('请先登录后再上传头像')
    if (typeof options.onError === 'function') options.onError(new Error('no token'))
    return
  }
  avatarUploading.value = true
  try {
    const formData = new FormData()
    formData.append('file', options.file)
    const base = import.meta.env.VITE_API_BASE || ''
    const res = await fetch(base + '/api/user/uploadAvatar', {
      method: 'POST',
      headers: { Authorization: 'Bearer ' + token },
      body: formData
    })
    const text = await res.text()
    let json = null
    try {
      json = text ? JSON.parse(text) : null
    } catch (parseErr) {
      json = null
    }
    if (res.status === 401) {
      ElMessage.error((json && json.message) || '登录已失效，请重新登录后再试')
      if (typeof options.onError === 'function') options.onError(new Error('401'))
      return
    }
    if (json && Number(json.code) === 200 && json.data && json.data.url) {
      user.value = { ...user.value, avatar: json.data.url }
      auth.setLogin(token, user.value)
      avatarBroken.value = false
      ElMessage.success('头像已更新')
      if (typeof options.onSuccess === 'function') options.onSuccess(json)
    } else {
      const msg = (json && json.message) || ('HTTP ' + res.status + (text && text.length < 200 ? '：' + text : ''))
      ElMessage.error(msg || '上传失败')
      if (typeof options.onError === 'function') options.onError(new Error('upload fail'))
    }
  } catch (e: any) {
    ElMessage.error('上传失败：网络异常')
    if (typeof options.onError === 'function') options.onError(e)
  } finally {
    avatarUploading.value = false
  }
}

const saveProfile = async () => {
  savingProfile.value = true
  try {
    await unwrap(api.post('/api/user/updateProfile', { ...profile }))
    ElMessage.success('资料已保存')
    await reload()
  } catch (e: any) {
    ElMessage.error(e?.message || '保存失败')
  } finally {
    savingProfile.value = false
  }
}

const saveTags = async () => {
  savingTags.value = true
  try {
    await unwrap(api.post('/api/user/updateTags', null, { params: { techTags: techTags.value } }))
    ElMessage.success('标签已保存')
    await reload()
  } catch (e: any) {
    ElMessage.error(e?.message || '保存失败')
  } finally {
    savingTags.value = false
  }
}

const loadPrivacy = async () => {
  try {
    const data = await unwrap<any>(api.get('/api/user/privacy/get'))
    privacy.creditPublic = !(data?.privacyCreditPublic === 0)
    privacy.feedPublic = !(data?.privacyFeedPublic === 0)
    privacy.allowMessage = !(data?.privacyAllowMessage === 0)
  } catch {
    // ignore
  }
}

const savePrivacy = async () => {
  privacySaving.value = true
  try {
    await unwrap(
      api.post('/api/user/privacy/save', {
        privacyCreditPublic: privacy.creditPublic ? 1 : 0,
        privacyFeedPublic: privacy.feedPublic ? 1 : 0,
        privacyAllowMessage: privacy.allowMessage ? 1 : 0,
      }),
    )
    ElMessage.success('隐私设置已保存')
    await reload()
  } catch (e: any) {
    ElMessage.error(e?.message || '保存失败')
  } finally {
    privacySaving.value = false
  }
}

const startGithubBind = async () => {
  if (!githubClientId) return ElMessage.warning('未配置 VITE_GITHUB_CLIENT_ID')
  try {
    await ElMessageBox.confirm(
      '即将跳转到 GitHub 授权页。若需切换 GitHub 账号，请先退出 GitHub 或使用无痕窗口后再继续。',
      'GitHub 绑定说明',
      { type: 'warning', confirmButtonText: '继续绑定', cancelButtonText: '取消' },
    )
  } catch {
    return
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

const unbindGithub = async () => {
  try {
    await ElMessageBox.confirm('确定解绑 GitHub 吗？解绑后会影响信用画像与协作风控。', '解绑确认', { type: 'warning' })
  } catch {
    return
  }
  unbinding.value = true
  try {
    await unwrap(api.post('/api/user/unbind'))
    ElMessage.success('解绑成功')
    await reload()
  } catch (e: any) {
    ElMessage.error(e?.message || '解绑失败')
  } finally {
    unbinding.value = false
  }
}

const logout = () => {
  auth.logout()
  router.replace('/login')
}

const exportMyData = async () => {
  const token = auth.token.value
  if (!token) {
    ElMessage.warning('请先登录')
    return router.push({ path: '/login', query: { redirect: '/me' } })
  }
  exporting.value = true
  try {
    const base = import.meta.env.VITE_API_BASE || ''
    const res = await fetch(base + '/api/me/export', {
      method: 'GET',
      headers: { Authorization: 'Bearer ' + token },
    })
    if (!res.ok) {
      const text = await res.text()
      throw new Error(text || ('HTTP ' + res.status))
    }
    const blob = await res.blob()
    const cd = res.headers.get('Content-Disposition') || ''
    let filename = 'my_data.zip'
    const m = /filename\*?=(?:UTF-8''|\"?)([^\";]+)/i.exec(cd)
    if (m && m[1]) {
      try {
        filename = decodeURIComponent(m[1].replace(/\"/g, '').trim())
      } catch {
        filename = m[1].replace(/\"/g, '').trim()
      }
    }
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = filename
    document.body.appendChild(a)
    a.click()
    a.remove()
    URL.revokeObjectURL(url)
    ElMessage.success('已开始下载')
  } catch (e: any) {
    ElMessage.error(e?.message || '导出失败')
  } finally {
    exporting.value = false
  }
}

const goPublicSelf = () => {
  const u = String(user.value?.username || '').trim()
  if (u) router.push(`/u/${encodeURIComponent(u)}`)
}

onMounted(async () => {
  window.addEventListener('follow-changed', onFollowChanged as any)
  await reload()
})

onUnmounted(() => {
  window.removeEventListener('follow-changed', onFollowChanged as any)
})
</script>

<style scoped>
.page { display: grid; gap: 14px; }
.head { display:flex; align-items:flex-start; justify-content:space-between; gap: 10px; flex-wrap: wrap; }
.title { font-weight: 900; font-size: 18px; }
.subtitle { margin-top: 4px; color: #6b7280; font-size: 13px; }
.actions { display:flex; gap: 8px; flex-wrap: wrap; }
.card { border-radius: 12px; }
.row { display:flex; align-items:center; justify-content:space-between; gap: 10px; }
.grid { display:grid; grid-template-columns: 1fr 1fr; gap: 14px; align-items: start; }
@media (max-width: 980px) { .grid { grid-template-columns: 1fr; } }
.muted { color:#6b7280; font-size: 13px; line-height: 1.7; }
.profile-header {
  display: flex;
  gap: 24px;
  align-items: flex-start;
}
@media (max-width: 768px) {
  .profile-header {
    flex-direction: column;
    gap: 16px;
  }
}
.avatar-section {
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
}
.avatar-box {
  width: 110px;
  height: 110px;
  border-radius: 50%;
  background: var(--app-card-bg);
  border: 2px solid var(--app-border-color);
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
}
.avatar-box img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.avatar-placeholder {
  font-size: 42px;
  font-weight: 700;
  color: var(--app-primary-color);
}
.avatar-actions {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
}
.avatar-hint {
  font-size: 12px;
  color: var(--app-muted-color);
  text-align: center;
}
.profile-info {
  flex: 1;
  min-width: 0;
}
.kv { display:grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 10px; }
@media (max-width: 860px) { .kv { grid-template-columns: 1fr; } }
.kv-item { display:flex; justify-content:space-between; gap: 10px; padding: 10px 12px; border: 1px solid var(--app-border-color); border-radius: 12px; background: var(--app-card-bg); }
.k { color:var(--app-muted-color); font-size: 12px; font-weight: 800; }
.v { color:var(--app-text-color); font-weight: 800; }
.credit { margin-top: 10px; display:flex; gap: 10px; flex-wrap: wrap; }
.credit-item { padding: 10px 12px; border: 1px solid var(--app-border-color); border-radius: 12px; background: var(--app-card-bg); }
.tabs-card { grid-column: 1 / -1; }
.list-block { display: flex; flex-direction: column; gap: 10px; }
.list-row {
  display: flex; align-items: center; justify-content: space-between; gap: 12px;
  padding: 10px 12px; border: 1px solid var(--app-border-color); border-radius: 12px; background: var(--app-card-bg);
}
.list-title { font-weight: 800; color: var(--app-text-color); }
.list-sub { font-size: 12px; color: var(--app-muted-color); margin-top: 4px; }
.two-col-follow { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; }
@media (max-width: 768px) { .two-col-follow { grid-template-columns: 1fr; } }
.pane-title { font-weight: 800; margin-bottom: 8px; }
.chip-list { display: flex; flex-wrap: wrap; gap: 8px; }
.chip { cursor: pointer; }
.feed-line { padding: 10px 12px; border: 1px solid var(--app-border-color); border-radius: 12px; font-size: 13px; line-height: 1.6; }
.badge-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(200px, 1fr)); gap: 12px; }
.badge-item {
  padding: 14px; border: 1px solid var(--app-border-color); border-radius: 12px; opacity: 0.65;
  background: var(--app-card-bg);
}
.badge-item.obtained { opacity: 1; border-color: var(--app-primary-color); }
.badge-name { font-weight: 800; }
.badge-desc { margin-top: 6px; font-size: 12px; }
</style>
