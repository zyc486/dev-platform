<template>
  <div class="shell">
    <header class="topbar">
      <div class="topbar-left">
        <button class="icon-btn" type="button" aria-label="打开导航菜单" @click="drawer = true">☰</button>
        <div class="brand" @click="goHome" role="button" tabindex="0">
          <div class="brand-title">DevCollab</div>
          <div class="brand-sub">协作与信用</div>
        </div>
      </div>

      <div class="topbar-mid">
        <el-input
          v-model="searchKeyword"
          size="small"
          clearable
          placeholder="搜索开发者 / 项目 / 帖子 / 社群（回车）"
          @keyup.enter="openSearch"
        />
      </div>

      <div class="topbar-right">
        <el-badge :value="unreadCount" :hidden="unreadCount === 0" class="badge">
          <el-button size="small" text @click="goNotifications">通知</el-button>
        </el-badge>

        <el-dropdown trigger="click" @command="onUserMenu">
          <span class="avatar-trigger">
            <img
              v-if="avatarUrl && !avatarLoadFailed"
              class="avatar avatar-img"
              :src="avatarUrl"
              alt="avatar"
              referrerpolicy="no-referrer"
              @error="avatarLoadFailed = true"
            />
            <span v-else class="avatar">{{ initial }}</span>
            <span class="avatar-name">{{ me?.username || '未登录' }}</span>
          </span>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="home">首页</el-dropdown-item>
              <el-dropdown-item command="projects">项目</el-dropdown-item>
              <el-dropdown-item command="credit">信用/画像</el-dropdown-item>
              <el-dropdown-item command="community">社区</el-dropdown-item>
              <el-dropdown-item command="rank">排行榜</el-dropdown-item>
              <el-dropdown-item v-if="me?.username" command="chat">群聊</el-dropdown-item>
              <el-dropdown-item v-if="me?.username" command="me">个人中心</el-dropdown-item>
              <el-dropdown-item v-if="me?.username" command="publish">发布项目</el-dropdown-item>
              <el-dropdown-item divided command="notifications">
                通知 <span v-if="unreadCount" style="margin-left:6px;color:#dc2626;">({{ unreadCount }})</span>
              </el-dropdown-item>
              <el-dropdown-item v-if="me?.username" command="dm">私信</el-dropdown-item>
              <el-dropdown-item command="help">帮助中心</el-dropdown-item>
              <el-dropdown-item command="feedback">意见反馈</el-dropdown-item>
              <el-dropdown-item v-if="me?.role === 'admin'" divided command="admin">管理后台</el-dropdown-item>
              <el-dropdown-item v-if="me?.username" divided command="logout" style="color:#dc2626;">退出登录</el-dropdown-item>
              <el-dropdown-item v-else divided command="login">去登录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
    </header>

    <el-drawer v-model="drawer" title="导航" direction="ltr" size="280px">
      <div class="nav">
        <div class="nav-group">
          <div class="nav-title">主页</div>
          <div class="nav-item" :class="{ active: isActive('/home') }" @click="navTo('/home')">首页</div>
          <div class="nav-item" :class="{ active: isActive('/rank') }" @click="navTo('/rank')">排行榜</div>
        </div>
        <div class="nav-group">
          <div class="nav-title">协作</div>
          <div class="nav-item" :class="{ active: isActive('/projects') }" @click="navTo('/projects')">项目</div>
          <div class="nav-item" :class="{ active: isActive('/publish') }" @click="navTo('/publish')">发布项目</div>
          <div class="nav-item" :class="{ active: isActive('/chat') }" @click="navTo('/chat')">群聊</div>
          <div class="nav-item" :class="{ active: isActive('/dm') }" @click="navTo('/dm')">私信</div>
        </div>
        <div class="nav-group">
          <div class="nav-title">画像与社区</div>
          <div class="nav-item" :class="{ active: isActive('/credit') }" @click="navTo('/credit')">信用/画像</div>
          <div class="nav-item" :class="{ active: isActive('/community') }" @click="navTo('/community')">社区</div>
          <div class="nav-item" :class="{ active: isActive('/notifications') }" @click="navTo('/notifications')">
            通知 <span v-if="unreadCount" class="nav-badge">{{ unreadCount > 99 ? '99+' : unreadCount }}</span>
          </div>
        </div>
        <div class="nav-group">
          <div class="nav-title">支持</div>
          <div class="nav-item" :class="{ active: isActive('/help') }" @click="navTo('/help')">帮助中心</div>
          <div class="nav-item" :class="{ active: isActive('/feedback') }" @click="navTo('/feedback')">意见反馈</div>
        </div>
      </div>
    </el-drawer>

    <main class="content">
      <router-view />
    </main>

    <el-dialog v-model="searchVisible" title="搜索" width="760px">
      <div v-if="searchLoading" class="muted">搜索中...</div>
      <div v-else-if="!searchResult" class="muted">输入关键词并回车搜索。</div>
      <div v-else class="search">
        <div class="search-col">
          <div class="search-title">开发者</div>
          <div v-if="(searchResult.developers||[]).length===0" class="muted">无</div>
          <div v-else>
            <a
              class="search-item search-link"
              v-for="d in (searchResult.developers||[]).slice(0,6)"
              :key="'d-'+d.id"
              :href="userProfileHref(d.username)"
              @click.prevent="goUserProfile(d.username)"
            >
              <div class="si-title">{{ d.nickname || d.username }}</div>
              <div class="si-sub">{{ d.githubUsername || '—' }} · {{ d.score ?? 0 }} · {{ d.level || '—' }}</div>
            </a>
          </div>
        </div>
        <div class="search-col">
          <div class="search-title">帖子</div>
          <div v-if="(searchResult.posts||[]).length===0" class="muted">无</div>
          <div v-else>
            <div class="search-item" v-for="p in (searchResult.posts||[]).slice(0,6)" :key="'p-'+p.id" @click="goCommunity()">
              <div class="si-title">{{ p.title || '（无标题）' }}</div>
              <div class="si-sub">{{ p.authorName || '—' }} · 👍 {{ p.likeCount ?? 0 }}</div>
            </div>
          </div>
        </div>
        <div class="search-col">
          <div class="search-title">社群</div>
          <div v-if="(searchResult.communities||[]).length===0" class="muted">无</div>
          <div v-else>
            <div class="search-item" v-for="c in (searchResult.communities||[]).slice(0,6)" :key="'c-'+c.id" @click="goCommunity()">
              <div class="si-title">{{ c.name }}</div>
              <div class="si-sub">成员 {{ c.memberCount ?? 0 }} · {{ c.techTags || '—' }}</div>
            </div>
          </div>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { auth } from './auth'
import { api, unwrap } from './api'
import { notificationsStore } from './notificationsStore'
import { wsClient } from './wsClient'

const route = useRoute()
const router = useRouter()

const me = computed(() => auth.me.value)
const drawer = ref(false)

const searchKeyword = ref('')
const searchVisible = ref(false)
const searchLoading = ref(false)
const searchResult = ref<any>(null)

const unreadCount = computed(() => notificationsStore.unreadCount.value)

const initial = computed(() => String(me.value?.username || 'U').slice(0, 1).toUpperCase())

const avatarLoadFailed = ref(false)
const avatarUrl = computed(() => {
  const raw = String(me.value?.avatar || '').trim()
  if (!raw) return ''
  if (/^https?:\/\//i.test(raw)) return raw
  // 约定后端返回相对路径：/uploads/avatar/...
  return raw.startsWith('/') ? raw : `/${raw}`
})

watch(
  () => me.value?.avatar,
  () => {
    avatarLoadFailed.value = false
  },
)

const isActive = (prefix: string) => String(route.path || '').startsWith(prefix)
const navTo = (path: string) => {
  drawer.value = false
  router.push(path)
}

const goHome = () => router.push('/home')
const goNotifications = () => router.push('/notifications')
const goCommunity = () => router.push('/community')
const goCreditUser = (gh: string) => {
  searchVisible.value = false
  router.push({ path: '/credit', query: { user: gh } })
}
const goUserProfile = (username: string) => {
  const u = String(username || '').trim()
  if (!u) return
  searchVisible.value = false
  router.push(`/u/${encodeURIComponent(u)}`)
}

const userProfileHref = (username: any) => {
  const u = String(username || '').trim()
  return u ? `/u/${encodeURIComponent(u)}` : '#'
}

const openSearch = async () => {
  const kw = searchKeyword.value.trim()
  if (!kw) return
  searchVisible.value = true
  searchLoading.value = true
  searchResult.value = null
  try {
    searchResult.value = await unwrap<any>(api.get('/api/search', { params: { keyword: kw, limit: 10 } }))
  } catch {
    searchResult.value = { developers: [], posts: [], communities: [], collabs: [] }
  } finally {
    searchLoading.value = false
  }
}

const loadUnreadCount = async () => {
  if (!auth.token.value) {
    notificationsStore.setUnread(0)
    return
  }
  try {
    const list = await unwrap<any[]>(api.get('/api/message/list'))
    const n = (list || []).filter((x: any) => x && x.isRead !== 1).length
    notificationsStore.setUnread(n)
  } catch {
    notificationsStore.setUnread(0)
  }
}

const logout = () => {
  auth.logout()
  router.push('/login')
}

const onUserMenu = (cmd: string) => {
  if (cmd === 'logout') return logout()
  if (cmd === 'login') return router.push('/login')
  if (cmd === 'home') return router.push('/home')
  if (cmd === 'projects') return router.push('/projects')
  if (cmd === 'credit') return router.push('/credit')
  if (cmd === 'community') return router.push('/community')
  if (cmd === 'chat') return router.push('/chat')
  if (cmd === 'dm') return router.push('/dm')
  if (cmd === 'me') return router.push('/me')
  if (cmd === 'notifications') return router.push('/notifications')
  if (cmd === 'rank') return router.push('/rank')
  if (cmd === 'publish') return router.push('/publish')
  if (cmd === 'help') return router.push('/help')
  if (cmd === 'feedback') return router.push('/feedback')
  if (cmd === 'admin') return router.push('/admin')
}

onMounted(() => {
  loadUnreadCount()
  if (auth.token.value) wsClient.connect()
})

watch(
  () => auth.token.value,
  (t) => {
    if (t) {
      wsClient.connect()
      void loadUnreadCount()
    } else {
      wsClient.disconnect()
      notificationsStore.setUnread(0)
    }
  },
)

watch(
  () => notificationsStore.changedTick.value,
  () => {
    // 有推送后：刷新角标即可，通知页会自行监听并 reload
    void loadUnreadCount()
  },
)
</script>

<style scoped>
.shell { min-height: 100vh; background: #f6f8fa; }
.topbar {
  height: 56px;
  display:flex;
  align-items:center;
  justify-content:space-between;
  gap: 12px;
  padding: 0 16px;
  background: #ffffff;
  border-bottom: 1px solid #e5e7eb;
  transform: none;
  position: sticky;
  top: 0;
  z-index: 20;
}
.topbar-left { display:flex; align-items:center; gap: 10px; min-width: 220px; }
.icon-btn {
  width: 34px; height: 34px;
  border-radius: 10px;
  border: 1px solid #e5e7eb;
  background: #fff;
  cursor: pointer;
  font-size: 16px;
}
.icon-btn:hover { background: #fafafa; }
.brand { display:flex; flex-direction: column; line-height: 1.05; cursor: pointer; user-select:none; }
.brand-title { font-weight: 900; letter-spacing: .2px; }
.brand-sub { font-size: 12px; color:#6b7280; margin-top: 3px; }
.topbar-mid { flex: 1; max-width: 720px; }
.topbar-right { display:flex; align-items:center; gap: 10px; min-width: 240px; justify-content: flex-end; }
.avatar-trigger { display:flex; align-items:center; gap: 8px; cursor: pointer; }
.avatar { width: 28px; height: 28px; border-radius: 999px; background: #111827; color:#fff; display:flex; align-items:center; justify-content:center; font-weight: 900; font-size: 13px; }
.avatar-img { object-fit: cover; display: block; border: 1px solid #e5e7eb; background: #fff; }
.avatar-name { color:#111827; font-weight: 700; font-size: 13px; }
.content { padding: 16px; max-width: 1280px; margin: 0 auto; }
.nav { display:flex; flex-direction: column; gap: 14px; }
.nav-title { font-size: 12px; color:#6b7280; font-weight: 800; margin-bottom: 6px; }
.nav-item { padding: 10px 12px; border-radius: 12px; border: 1px solid #e5e7eb; background:#fff; cursor:pointer; font-weight: 700; color:#111827; display:flex; justify-content:space-between; align-items:center; }
.nav-item:hover { background:#fafafa; }
.nav-item.active { border-color: #111827; }
.nav-badge { font-size: 12px; color:#dc2626; font-weight: 900; }
.muted { color:#6b7280; font-size: 13px; }
.search { display:grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 12px; }
@media (max-width: 860px) { .search { grid-template-columns: 1fr; } }
.search-title { font-weight: 900; margin-bottom: 8px; }
.search-item { padding: 10px 12px; border:1px solid #e5e7eb; border-radius: 12px; cursor:pointer; background:#fff; }
.search-item:hover { background:#fafafa; }
.search-link { display:block; text-decoration: none; color: inherit; }
.si-title { font-weight: 800; color:#111827; }
.si-sub { margin-top: 4px; color:#6b7280; font-size: 12px; }
</style>
