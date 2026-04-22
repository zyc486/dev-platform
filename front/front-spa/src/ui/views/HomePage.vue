<template>
  <div class="home">
    <div class="col col-left">
      <el-card class="card" shadow="never">
        <template #header>
          <div class="hd">
            <div class="hd-title">我的项目</div>
            <el-button size="small" text @click="goProjects">全部</el-button>
          </div>
        </template>
        <div v-if="!hasToken" class="muted">登录后展示你的协作项目与看板。</div>
        <div v-else>
          <div v-if="loadingProjects" class="muted">加载中...</div>
          <div v-else-if="projects.length === 0" class="muted">暂无项目，去「项目」里新建一个。</div>
          <div v-else class="list">
            <div class="item" v-for="p in projects.slice(0, 8)" :key="p.id" @click="goProject(p.id)">
              <div class="item-title">{{ p.name }}</div>
              <div class="item-sub">{{ p.visibility }} · {{ preview(p.description, 40) }}</div>
            </div>
          </div>
        </div>
      </el-card>

      <el-card class="card" shadow="never" style="margin-top: 12px">
        <template #header>
          <div class="hd">
            <div class="hd-title">推荐协作</div>
            <el-tag size="small" type="info">一期核心层</el-tag>
          </div>
        </template>
        <div v-if="loadingCollabs" class="muted">加载中...</div>
        <div v-else-if="recommendCollabs.length === 0" class="muted">暂无推荐协作</div>
        <div v-else class="list">
          <div class="item" v-for="c in recommendCollabs.slice(0, 6)" :key="c.id">
            <div class="item-title">{{ c.title || '协作' }}</div>
            <div class="item-sub">{{ preview(c.content, 44) }}</div>
          </div>
        </div>
      </el-card>
    </div>

    <div class="col col-mid">
      <el-card class="card" shadow="never">
        <template #header>
          <div class="hd">
            <div class="hd-title">科技动态</div>
            <div class="hd-actions">
              <el-select v-model="pulseCategory" size="small" style="width: 160px" @change="loadPulse">
                <el-option label="全部" value="all" />
                <el-option label="AI" value="ai" />
                <el-option label="安全" value="security" />
                <el-option label="DevOps" value="devops" />
                <el-option label="开源" value="opensource" />
                <el-option label="GitHub" value="github" />
              </el-select>
              <el-button size="small" @click="refreshPulse">刷新</el-button>
            </div>
          </div>
        </template>
        <div v-if="loadingPulse" class="muted">加载中...</div>
        <div v-else-if="pulse.length === 0" class="muted">暂无动态</div>
        <div v-else class="feed">
          <div class="feed-item" v-for="(x, idx) in pulse" :key="idx">
            <a class="feed-title" :href="x.url" target="_blank" rel="noreferrer">{{ x.title }}</a>
            <div class="feed-meta">{{ x.source || 'Tech' }} · {{ x.publishedAt || '' }}</div>
            <div class="feed-desc">{{ preview(x.summary || x.description, 120) }}</div>
          </div>
        </div>
      </el-card>
    </div>

    <div class="col col-right">
      <el-card class="card" shadow="never">
        <template #header>
          <div class="hd">
            <div class="hd-title">今日排行</div>
            <el-button size="small" text @click="goCredit">查看信用</el-button>
          </div>
        </template>
        <div v-if="loadingHotDev" class="muted">加载中...</div>
        <div v-else-if="hotDevelopers.length === 0" class="muted">暂无数据</div>
        <div v-else class="rank">
          <a class="rank-item rank-link" v-for="(u, i) in hotDevelopers.slice(0, 10)" :key="u.id || i" :href="userProfileHref(u.username)" @click.prevent="goUserProfile(u.username)">
            <div class="rank-no">{{ i + 1 }}</div>
            <div class="rank-main">
              <div class="rank-name">{{ u.nickname || u.username }}</div>
              <div class="rank-sub">{{ u.githubUsername || '—' }} · {{ u.level || '—' }}</div>
            </div>
            <div class="rank-score">{{ u.score ?? 0 }}</div>
          </a>
        </div>
      </el-card>

      <el-card class="card" shadow="never" style="margin-top: 12px">
        <template #header>
          <div class="hd">
            <div class="hd-title">热门帖子</div>
            <el-button size="small" text @click="goCommunity">去社区</el-button>
          </div>
        </template>
        <div v-if="loadingHotPosts" class="muted">加载中...</div>
        <div v-else-if="hotPosts.length === 0" class="muted">暂无数据</div>
        <div v-else class="list">
          <div class="item" v-for="p in hotPosts.slice(0, 6)" :key="p.id">
            <div class="item-title">{{ p.title || '（无标题）' }}</div>
            <div class="item-sub">{{ p.authorNickname || p.authorName || '—' }} · 👍 {{ p.likeCount ?? 0 }}</div>
          </div>
        </div>
      </el-card>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { api, unwrap } from '../api'
import { auth } from '../auth'

const router = useRouter()
const hasToken = computed(() => !!auth.token.value)

const projects = ref<any[]>([])
const loadingProjects = ref(false)

const pulseCategory = ref('all')
const pulse = ref<any[]>([])
const loadingPulse = ref(false)

const hotDevelopers = ref<any[]>([])
const loadingHotDev = ref(false)

const recommendCollabs = ref<any[]>([])
const loadingCollabs = ref(false)

const hotPosts = ref<any[]>([])
const loadingHotPosts = ref(false)

const preview = (s: any, n: number) => {
  const t = String(s || '')
  return t.length > n ? t.slice(0, n) + '…' : t
}

const loadProjects = async () => {
  if (!hasToken.value) return
  loadingProjects.value = true
  try {
    projects.value = await unwrap<any[]>(api.get('/api/collab/project/my'))
  } catch (e: any) {
    ElMessage.error(e?.message || '项目加载失败')
  } finally {
    loadingProjects.value = false
  }
}

const loadPulse = async (refresh = false) => {
  loadingPulse.value = true
  try {
    pulse.value = await unwrap<any[]>(
      api.get('/api/home/techPulse', { params: { limit: 12, category: pulseCategory.value, refresh: refresh ? 'true' : 'false' } }),
    )
  } catch (e: any) {
    ElMessage.error(e?.message || '动态加载失败')
    pulse.value = []
  } finally {
    loadingPulse.value = false
  }
}
const refreshPulse = () => loadPulse(true)

const loadHotDevelopers = async () => {
  loadingHotDev.value = true
  try {
    hotDevelopers.value = await unwrap<any[]>(api.get('/api/home/hotDevelopers', { params: { limit: 10 } }))
  } catch {
    hotDevelopers.value = []
  } finally {
    loadingHotDev.value = false
  }
}

const loadRecommendCollabs = async () => {
  loadingCollabs.value = true
  try {
    recommendCollabs.value = await unwrap<any[]>(api.get('/api/home/recommendCollabs'))
  } catch {
    recommendCollabs.value = []
  } finally {
    loadingCollabs.value = false
  }
}

const loadHotPosts = async () => {
  loadingHotPosts.value = true
  try {
    hotPosts.value = await unwrap<any[]>(api.get('/api/home/hotPosts', { params: { limit: 6 } }))
  } catch {
    hotPosts.value = []
  } finally {
    loadingHotPosts.value = false
  }
}

const goProjects = () => router.push('/projects')
const goProject = (id: number) => router.push(`/projects/${id}`)
const goCredit = () => router.push('/credit')
const goCreditUser = (gh: string) => router.push({ path: '/credit', query: { user: gh } })
const goUserProfile = (username: string) => {
  const u = String(username || '').trim()
  if (!u) return ElMessage.warning('未获取到该用户的站内用户名')
  router.push(`/u/${encodeURIComponent(u)}`)
}
const userProfileHref = (username: any) => {
  const u = String(username || '').trim()
  return u ? `/u/${encodeURIComponent(u)}` : '#'
}
const goCommunity = () => router.push('/community')

onMounted(async () => {
  await Promise.all([loadPulse(false), loadHotDevelopers(), loadRecommendCollabs(), loadHotPosts()])
  await loadProjects()
})
</script>

<style scoped>
.home {
  display: grid;
  grid-template-columns: 320px minmax(0, 1fr) 320px;
  gap: 14px;
  align-items: start;
}
@media (max-width: 1100px) {
  .home { grid-template-columns: 1fr; }
}
.card {
  border-radius: 12px;
  border: 1px solid #e5e7eb;
}
.hd {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}
.hd-title { font-weight: 800; }
.hd-actions { display:flex; align-items:center; gap: 10px; }
.muted { color: #6b7280; font-size: 13px; }
.list { display:flex; flex-direction: column; gap: 10px; }
.item {
  padding: 10px 12px;
  border: 1px solid #e5e7eb;
  border-radius: 12px;
  background: #fff;
}
.item:hover { border-color: #d1d5db; background: #fafafa; }
.item-title { font-weight: 700; color: #111827; }
.item-sub { margin-top: 4px; color:#6b7280; font-size: 12px; }
.item { cursor: pointer; }
.feed { display:flex; flex-direction: column; gap: 14px; }
.feed-item { padding: 10px 0; border-bottom: 1px solid #f1f5f9; }
.feed-item:last-child { border-bottom: none; }
.feed-title { font-weight: 800; color: #111827; }
.feed-title:hover { text-decoration: underline; }
.feed-meta { margin-top: 6px; font-size: 12px; color:#6b7280; }
.feed-desc { margin-top: 8px; font-size: 13px; color:#374151; line-height: 1.7; }
.rank { display:flex; flex-direction: column; }
.rank-item {
  display:flex; align-items:center; gap: 10px;
  padding: 10px 6px;
  border-bottom: 1px solid #f1f5f9;
  cursor: pointer;
}
.rank-item:hover { background: #fafafa; }
.rank-item:last-child { border-bottom: none; }
.rank-link { text-decoration: none; color: inherit; }
.rank-no { width: 22px; text-align: center; color:#6b7280; font-weight: 800; }
.rank-main { flex:1; min-width:0; }
.rank-name { font-weight: 800; color:#111827; }
.rank-sub { margin-top: 2px; color:#6b7280; font-size: 12px; }
.rank-score { font-weight: 900; color:#111827; }
</style>
