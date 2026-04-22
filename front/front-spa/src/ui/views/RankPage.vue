<template>
  <div class="page">
    <el-card class="card" shadow="never">
      <template #header>
        <div class="hd">
          <div class="hd-title">排行榜</div>
          <div class="muted">基于信用评分（支持筛选：场景/标签/等级/分数区间/仅站内用户）。</div>
        </div>
      </template>

      <div class="filters">
        <div class="preset">
          <span class="muted">预设榜单：</span>
          <el-button
            v-for="p in presets"
            :key="p.id"
            size="small"
            :type="activePreset === p.id ? 'primary' : 'default'"
            plain
            @click="applyPreset(p)"
          >
            {{ p.name }}
          </el-button>
          <el-button size="small" plain :disabled="!activePreset" @click="clearPreset">清除预设</el-button>
        </div>

        <el-select v-model="filters.scene" style="width: 140px">
          <el-option v-for="s in scenes" :key="s" :label="s" :value="s" />
        </el-select>

        <el-input v-model="filters.tag" clearable placeholder="技术标签（可选）" style="width: 180px" />

        <el-select v-model="filters.level" clearable placeholder="等级（可选）" style="width: 140px">
          <el-option v-for="lv in levels" :key="lv" :label="lv" :value="lv" />
        </el-select>

        <el-input-number v-model="filters.minScore" :min="0" :max="100" controls-position="right" />
        <span class="muted">~</span>
        <el-input-number v-model="filters.maxScore" :min="0" :max="100" controls-position="right" />

        <el-input-number v-model="filters.limit" :min="5" :max="200" controls-position="right" />

        <el-checkbox v-model="filters.siteOnly">仅站内用户</el-checkbox>

        <el-button type="primary" :loading="loading" @click="load">查询</el-button>
        <el-button :disabled="loading" @click="reset">重置</el-button>
      </div>

      <el-table :data="list" v-loading="loading" class="tbl" stripe>
        <el-table-column type="index" label="#" width="60" />
        <el-table-column label="开发者" min-width="200">
          <template #default="{ row }">
            <div class="user user-link" @click="goProfile(row)">
              <div class="avatar">
                <img v-if="row.avatar" :src="mediaUrl(row.avatar)" alt="" @error="hideBrokenImage" />
                <span v-else class="ph">{{ initialOf(row) }}</span>
              </div>
              <div class="meta">
                <div class="name">{{ row.nickname || row.githubUsername || '—' }}</div>
                <div class="sub">@{{ row.githubUsername || '—' }}</div>
                <div v-if="row.techTags" class="tags">{{ row.techTags }}</div>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="scene" label="场景" width="120" />
        <el-table-column prop="totalScore" label="总分" width="90" sortable />
        <el-table-column prop="level" label="等级" width="100" />
        <el-table-column label="四维" min-width="220">
          <template #default="{ row }">
            <div class="dims">
              <span>稳 {{ row.stability ?? 0 }}</span>
              <span>PR {{ row.prQuality ?? 0 }}</span>
              <span>协 {{ row.collaboration ?? 0 }}</span>
              <span>规 {{ row.compliance ?? 0 }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150">
          <template #default="{ row }">
            <el-button size="small" type="primary" plain @click="goCredit(row)">查看画像</el-button>
            <el-button size="small" @click="goProfile(row)">主页</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div v-if="!loading && list.length === 0" class="empty muted">暂无数据</div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { api, unwrap } from '../api'

const router = useRouter()

const scenes = ['综合', '核心开发者', '辅助贡献']
const levels = ['S', 'A', 'B', 'C', 'D', 'E', '优秀', '良好', '合格', '待提升']

const presets = [
  { id: 'frontend', name: '前端榜', tags: ['Vue', 'React', 'TypeScript', 'TS'] },
  { id: 'backend', name: '后端榜', tags: ['Java', 'Spring', 'Go', 'Python'] },
  { id: 'fullstack', name: '全栈榜', tags: ['FullStack', '全栈', 'Node', 'Vue', 'React'] },
  { id: 'data', name: '数据榜', tags: ['Data', '数据', 'Python', 'SQL', 'Spark'] },
]

const activePreset = ref<string>('')

const filters = reactive({
  scene: '综合',
  tag: '',
  level: '',
  minScore: 0 as number | null,
  maxScore: 100 as number | null,
  limit: 20,
  siteOnly: false,
})

const loading = ref(false)
const list = ref<any[]>([])

const mediaUrl = (path: string) => {
  if (!path) return ''
  if (path.startsWith('http')) return path
  const p = path.startsWith('/') ? path : `/${path}`
  return `${import.meta.env.VITE_API_BASE || ''}${p}`
}

const hideBrokenImage = (event: Event) => {
  const img = event.target as HTMLImageElement
  if (img) img.style.display = 'none'
}

const initialOf = (row: any) => String(row?.nickname || row?.githubUsername || '?').slice(0, 1).toUpperCase()

const normalizeRange = () => {
  const min = typeof filters.minScore === 'number' ? filters.minScore : null
  const max = typeof filters.maxScore === 'number' ? filters.maxScore : null
  if (min != null && max != null && min > max) {
    const t = filters.minScore
    filters.minScore = filters.maxScore
    filters.maxScore = t
  }
}

const load = async () => {
  normalizeRange()
  loading.value = true
  try {
    const params: any = {
      scene: filters.scene,
      limit: filters.limit,
      siteOnly: filters.siteOnly,
    }
    if (filters.tag.trim()) params.tag = filters.tag.trim()
    if (filters.level) params.level = filters.level
    if (typeof filters.minScore === 'number') params.minScore = filters.minScore
    if (typeof filters.maxScore === 'number') params.maxScore = filters.maxScore

    list.value = await unwrap<any[]>(
      api.get('/api/credit/rank2', { params }),
    )
  } catch (e: any) {
    list.value = []
    ElMessage.error(e?.message || '加载失败')
  } finally {
    loading.value = false
  }
}

const reset = () => {
  activePreset.value = ''
  filters.scene = '综合'
  filters.tag = ''
  filters.level = ''
  filters.minScore = 0
  filters.maxScore = 100
  filters.limit = 20
  filters.siteOnly = false
  void load()
}

const applyPreset = (p: any) => {
  activePreset.value = String(p?.id || '')
  filters.tag = Array.isArray(p?.tags) ? p.tags.join(',') : ''
  void load()
}

const clearPreset = () => {
  activePreset.value = ''
  filters.tag = ''
  void load()
}

const goCredit = (row: any) => {
  const gh = String(row?.githubUsername || '').trim()
  if (!gh) return
  router.push({ path: '/credit', query: { user: gh } })
}

const goProfile = (row: any) => {
  const u = String(row?.username || '').trim()
  if (!u) return ElMessage.warning('该条目不是站内用户，暂无主页')
  router.push(`/u/${encodeURIComponent(u)}`)
}

onMounted(load)
</script>

<style scoped>
.page { max-width: 960px; margin: 0 auto; padding: 12px; }
.card { border-radius: 12px; }
.hd-title { font-weight: 900; }
.muted { color: #6b7280; font-size: 13px; line-height: 1.7; }
.filters { display:flex; gap: 10px; align-items:center; flex-wrap: wrap; }
.preset { display:flex; align-items:center; gap: 8px; flex-wrap: wrap; width: 100%; }
.tbl { margin-top: 12px; }
.empty { padding: 14px 0; text-align: center; }
.user { display:flex; gap: 10px; align-items:center; }
.user-link { cursor: pointer; }
.user-link:hover .name { text-decoration: underline; }
.avatar { width: 36px; height: 36px; border-radius: 999px; overflow:hidden; border: 1px solid #e5e7eb; background:#fff; display:flex; align-items:center; justify-content:center; flex-shrink:0; }
.avatar img { width: 100%; height: 100%; object-fit: cover; }
.ph { font-weight: 900; color:#111827; }
.meta { min-width: 0; }
.name { font-weight: 800; color:#111827; }
.sub { color:#6b7280; font-size: 12px; margin-top: 2px; }
.tags { color:#374151; font-size: 12px; margin-top: 4px; overflow:hidden; text-overflow: ellipsis; white-space: nowrap; max-width: 360px; }
.dims { display:flex; gap: 10px; flex-wrap: wrap; color:#374151; font-size: 12px; }
</style>
