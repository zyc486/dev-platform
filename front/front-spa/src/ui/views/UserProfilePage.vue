<template>
  <div class="page">
    <div class="head">
      <el-button text @click="router.back()">‹ 返回</el-button>
      <div class="grow" />
      <el-button v-if="isSelf" type="primary" plain @click="router.push('/me')">编辑我的资料</el-button>
    </div>

    <el-card v-loading="loading" class="card">
      <div class="profile-top">
        <div class="avatar-box">
          <img
            v-if="profile?.avatar && !avatarBroken"
            :key="profile.avatar"
            :src="mediaUrl(profile.avatar)"
            alt=""
            @error="onAvatarError"
          />
          <span v-else class="ph">{{ initial }}</span>
        </div>
        <div class="info">
          <div class="title">{{ profile?.nickname || profile?.username || username }}</div>
          <div class="sub">@{{ profile?.username || username }}</div>
          <div v-if="profile?.githubUsername" class="sub">GitHub: {{ profile.githubUsername }}</div>
          <div v-if="profile?.techTags" class="sub">标签: {{ profile.techTags }}</div>
        </div>
        <div v-if="!isSelf && isLoggedIn" class="actions">
          <el-button :loading="followLoading" @click="toggleFollow">{{ isFollowing ? '取消关注' : '关注' }}</el-button>
          <el-button :loading="favLoading" @click="toggleFavorite">{{ isFavorited ? '取消收藏主页' : '收藏主页' }}</el-button>
          <el-button type="primary" plain @click="goDm">私信</el-button>
        </div>
      </div>
      <el-divider />
      <div class="bio">{{ profile?.bio || '该开发者未填写简介。' }}</div>
      <el-divider />
      <div class="credit">
        <div class="credit-title">信用展示</div>
        <div v-if="profile?.totalScore != null" class="credit-row">
          <el-tag type="success">总分 {{ profile.totalScore }}</el-tag>
          <el-tag type="info">{{ profile?.level || '—' }}</el-tag>
        </div>
        <div v-else class="muted">对方已隐藏信用信息，暂不展示。</div>
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { api, unwrap } from '../api'
import { auth } from '../auth'

const route = useRoute()
const router = useRouter()

const username = computed(() => String(route.params.username || '').trim())
const loading = ref(false)
const profile = ref<any>(null)
const avatarBroken = ref(false)
const isFollowing = ref(false)
const isFavorited = ref(false)
const followLoading = ref(false)
const favLoading = ref(false)

const isSelf = computed(() => {
  const me = auth.me.value?.username
  return !!(me && profile.value?.username && me === profile.value.username)
})

const isLoggedIn = computed(() => !!auth.token.value)

const initial = computed(() => (username.value || '?').charAt(0).toUpperCase())

const mediaUrl = (path: string) => {
  if (!path) return ''
  if (path.startsWith('http')) return path
  return `${import.meta.env.VITE_API_BASE || ''}${path}`
}

const onAvatarError = () => {
  avatarBroken.value = true
}

watch(
  () => profile.value?.avatar,
  () => {
    avatarBroken.value = false
  },
)

const loadProfile = async () => {
  const u = username.value
  if (!u) return
  loading.value = true
  try {
    profile.value = await unwrap<any>(api.get(`/api/profile/${encodeURIComponent(u)}`))
    if (auth.token.value && !isSelf.value && profile.value?.id) {
      await loadRelationship()
    }
  } catch (e: any) {
    ElMessage.error(e?.message || '加载失败')
    profile.value = null
  } finally {
    loading.value = false
  }
}

const loadRelationship = async () => {
  const uid = profile.value?.id
  const un = profile.value?.username
  if (!uid || !un) return
  try {
    const st = await unwrap<any>(
      api.get('/follow/status', { params: { followUserId: uid }, skipAuthLogout: true }),
    )
    isFollowing.value = !!st?.following
  } catch {
    isFollowing.value = false
  }
  try {
    const fav = await unwrap<boolean>(
      api.get('/api/favorite/check', {
        params: { type: 'platform_user', targetId: un },
        skipAuthLogout: true,
      }),
    )
    isFavorited.value = !!fav
  } catch {
    isFavorited.value = false
  }
}

const toggleFollow = async () => {
  const uid = profile.value?.id
  if (!uid) return
  if (!auth.token.value) {
    ElMessage.warning('请先登录')
    return router.push({ path: '/login', query: { redirect: route.fullPath } })
  }
  followLoading.value = true
  try {
    await unwrap(api.post(`/follow/operate`, null, { params: { followUserId: uid } }))
    isFollowing.value = !isFollowing.value
    ElMessage.success('已更新关注状态')
  } catch (e: any) {
    ElMessage.error(e?.message || '操作失败')
  } finally {
    followLoading.value = false
  }
}

const toggleFavorite = async () => {
  const un = profile.value?.username
  if (!un) return
  if (!auth.token.value) {
    ElMessage.warning('请先登录')
    return router.push({ path: '/login', query: { redirect: route.fullPath } })
  }
  favLoading.value = true
  try {
    await unwrap(
      api.post('/api/favorite/toggle', { type: 'platform_user', targetId: un }),
    )
    isFavorited.value = !isFavorited.value
    ElMessage.success('已更新收藏')
  } catch (e: any) {
    ElMessage.error(e?.message || '操作失败')
  } finally {
    favLoading.value = false
  }
}

const goDm = () => {
  const uid = profile.value?.id
  if (!uid) return
  if (!auth.token.value) {
    ElMessage.warning('请先登录')
    return router.push({ path: '/login', query: { redirect: route.fullPath } })
  }
  router.push({ path: '/dm', query: { withUserId: String(uid) } })
}

watch(
  () => route.params.username,
  () => {
    void loadProfile()
  },
)

onMounted(() => {
  void loadProfile()
})
</script>

<style scoped>
.page { display: flex; flex-direction: column; gap: 12px; max-width: 900px; margin: 0 auto; }
.head { display: flex; align-items: center; gap: 8px; }
.grow { flex: 1; }
.card { border-radius: 14px; }
.profile-top { display: flex; flex-wrap: wrap; gap: 16px; align-items: flex-start; }
.avatar-box {
  width: 88px; height: 88px; border-radius: 50%; overflow: hidden;
  background: #f3f4f6; display: flex; align-items: center; justify-content: center;
  border: 1px solid #e5e7eb;
}
.avatar-box img { width: 100%; height: 100%; object-fit: cover; }
.ph { font-size: 32px; font-weight: 900; color: #374151; }
.info { flex: 1; min-width: 200px; }
.title { font-weight: 900; font-size: 18px; }
.sub { margin-top: 4px; color: #6b7280; font-size: 13px; }
.actions { display: flex; gap: 8px; flex-wrap: wrap; }
.bio { color: #374151; line-height: 1.7; font-size: 14px; }
.credit-title { font-weight: 900; margin-bottom: 10px; }
.credit-row { display:flex; gap: 8px; flex-wrap: wrap; }
.muted { color:#6b7280; font-size: 13px; line-height: 1.7; }
</style>
