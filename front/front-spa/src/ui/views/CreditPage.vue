<template>
  <div class="page">
    <div class="title-row">
      <div class="title">信用多维分析与对比</div>
      <div class="title-actions">
        <el-button plain size="small" @click="showRuleDialog = true">评分规则</el-button>
        <el-button
          type="primary"
          size="small"
          @click="exportReport"
          :loading="exportCsvLoading"
          :disabled="compareUsers.length === 0 || exportHtmlLoading"
        >{{ exportCsvLoading ? '正在导出CSV...' : '导出简表(CSV)' }}</el-button>
        <el-button
          type="success"
          size="small"
          @click="exportDetailedReport"
          :loading="exportHtmlLoading"
          :disabled="compareUsers.length === 0 || exportCsvLoading"
        >{{ exportHtmlLoading ? '正在生成HTML...' : '导出详细报告(HTML)' }}</el-button>
      </div>
    </div>

    <el-alert v-if="exportHtmlLoading || exportCsvLoading" :title="exportHintText" type="info" show-icon :closable="false" />

    <el-card class="card">
      <template #header>
        <div class="card-hd">
          <div class="card-title">多作者信用对比</div>
          <el-tag type="info" size="small" v-if="compareUsers.length">{{ compareUsers.length }} 人</el-tag>
        </div>
      </template>

      <div class="search-row">
        <el-input v-model="newUser" placeholder="GitHub ID，如 torvalds" @keyup.enter="addUser" clearable />
        <el-select v-model="selectedScene" style="width:148px">
          <el-option v-for="s in sceneOptions" :key="s.value" :label="s.label" :value="s.value" />
        </el-select>
        <el-button type="primary" :loading="addLoading" @click="addUser">加入对比</el-button>
        <el-button :disabled="compareUsers.length === 0" @click="clearCompare">清空</el-button>
      </div>

      <div class="compare-tags">
        <el-tag
          v-for="u in compareUsers"
          :key="u.githubUsername"
          closable
          :type="(insightData && insightData.githubUsername === u.githubUsername) ? 'success' : 'info'"
          @close="removeUser(u.githubUsername)"
          @click="selectInsightUser(u.githubUsername)"
          style="cursor:pointer"
        >{{ u.githubUsername }}</el-tag>
      </div>

      <div class="chart-wrap" v-if="compareUsers.filter(u=>!u.error).length">
        <div id="radarChart" style="width:100%;height:420px;" />
      </div>
      <div v-else class="chart-empty">
        <div>暂无可对比数据</div>
        <div class="muted">输入 GitHub ID 并加入对比后自动渲染雷达图</div>
      </div>
    </el-card>

    <div class="grid-2">
      <el-card class="card">
        <template #header>
          <div class="card-hd">
            <div class="card-title">洞察</div>
            <el-button v-if="canViewExplainDetails" size="small" type="primary" plain @click="openExplainDetails">可解释性明细</el-button>
          </div>
        </template>

        <div v-if="!insightData" class="muted">选择一个对比用户以查看洞察</div>
        <div v-else>
          <div class="insights-grid">
            <div class="insight-item">
              <div class="insight-label">总分</div>
              <div class="insight-value">{{ insightData.totalScore ?? '—' }}</div>
              <div class="insight-sub">评级：<span :class="'level-'+(insightData.level||'')">{{ insightData.level ?? '—' }}</span></div>
            </div>
            <div class="insight-item">
              <div class="insight-label">算法版本</div>
              <div class="insight-value" style="font-size:18px;">{{ insightData.algoVersion ?? '—' }}</div>
              <div class="insight-sub">场景：{{ formatSceneLabel(selectedScene) }}</div>
            </div>
          </div>

          <div class="dim-bars">
            <div class="dim-row" v-for="d in dimList" :key="d.key">
              <div class="dim-label">{{ d.label }}</div>
              <div class="dim-bar-track"><div class="dim-bar-fill" :style="{width: (insightData[d.key]||0)+'%', background: d.color}"></div></div>
              <div class="dim-score-val">{{ insightData[d.key] ?? 0 }}</div>
            </div>
          </div>
        </div>
      </el-card>

      <el-card class="card">
        <template #header>
          <div class="card-hd">
            <div class="card-title">AI 画像（辅助协作）</div>
            <el-button size="small" type="primary" plain :loading="aiProfileLoading" @click="refreshAiProfile">刷新画像</el-button>
          </div>
        </template>
        <div v-if="aiProfileLoading" class="muted">加载中...</div>
        <template v-else>
          <el-alert v-if="aiProfile && (aiProfile.status && aiProfile.status !== 'ready')" :title="String(aiProfile.status)" type="info" show-icon :closable="false" />
          <div v-if="aiProfile && (aiProfile.summary || (aiProfile.status==='ready'))" class="ai-summary">
            {{ aiProfile.summary || '—' }}
          </div>
          <div v-if="aiTechTags.length" style="margin-top:8px;">
            <el-tag v-for="(t,idx) in aiTechTags" :key="idx" size="small" type="info" style="margin:0 6px 6px 0;">
              {{ t.tag }} · {{ Math.round((t.confidence||0)*100) }}%
            </el-tag>
          </div>
          <div v-if="aiTopProjects.length" style="margin-top:10px;">
            <div style="font-weight:700;margin-bottom:6px;">代表项目</div>
            <el-collapse>
              <el-collapse-item v-for="(p,idx) in aiTopProjects" :key="idx" :title="p.repo">
                <div class="muted" v-if="p.techStack && p.techStack.length">techStack：{{ p.techStack.join(' / ') }}</div>
                <div class="muted" v-if="p.reasons && p.reasons.length" style="margin-top:6px;">原因：{{ p.reasons.join('；') }}</div>
                <div class="muted" v-if="p.signals && p.signals.length" style="margin-top:6px;">信号：{{ p.signals.join('；') }}</div>
              </el-collapse-item>
            </el-collapse>
          </div>
        </template>
      </el-card>
    </div>

    <el-card class="card">
      <template #header>
        <div class="card-hd">
          <div class="card-title">趋势</div>
          <el-select v-model="trendUsername" size="small" style="width:180px" @change="loadTrend">
            <el-option v-for="u in compareUsers.filter(u=>!u.error)" :key="u.githubUsername" :label="u.githubUsername" :value="u.githubUsername" />
          </el-select>
        </div>
      </template>
      <div id="trendChart" style="width:100%;height:280px;" />
    </el-card>

    <el-card class="card">
      <template #header>
        <div class="card-hd">
          <div class="card-title">查询历史</div>
          <div class="row" style="gap:8px;">
            <el-button size="small" @click="loadHistory">刷新</el-button>
            <el-button size="small" type="warning" plain @click="toggleFilterFavorite">{{ showOnlyFavorite ? '查看全部' : '只看收藏' }}</el-button>
            <el-button size="small" type="danger" plain :disabled="selectedIds.length===0" @click="batchDelete">批量删除</el-button>
          </div>
        </div>
      </template>

      <div v-if="!historyLoaded" class="muted">加载中...</div>
      <div v-else-if="visibleHistory.length===0" class="muted">暂无历史记录（登录后才会保存）</div>
      <div v-else class="history-list">
        <div
          class="hi-card"
          v-for="item in visibleHistory"
          :key="item.id"
          :class="{ removing: removingIds.includes(item.id), favorited: item.isFavorite===1 }"
        >
          <div class="hi-select">
            <el-checkbox :model-value="selectedIds.includes(item.id)" @change="() => toggleSelect(item.id)" />
          </div>
          <div class="hi-main" @click="reQuery(item.githubUsername, item.scene)">
            <div class="hi-profile">
              <div class="hi-avatar" :class="{ 'fav-avatar': item.isFavorite===1 }">{{ (item.githubUsername||'').slice(0,2).toUpperCase() }}</div>
              <div class="hi-identity">
                <div class="hi-name">{{ item.githubUsername }}</div>
                <div class="hi-meta">
                  <span class="hi-scene-tag">{{ formatSceneLabel(item.scene) }}</span>
                  <span>{{ formatTime(item.queryTime) }}</span>
                </div>
              </div>
            </div>
            <div class="hi-score-wrap">
              <div class="hi-score" :class="scoreColorClass(item.totalScore)">{{ item.totalScore ?? '—' }}</div>
              <div class="hi-score-unit">score</div>
            </div>
          </div>
          <div class="hi-actions">
            <el-button circle size="small" @click="toggleFavorite(item)">{{ item.isFavorite===1 ? '★' : '☆' }}</el-button>
            <el-button circle size="small" type="danger" plain @click="deleteOne(item.id)">删</el-button>
          </div>
        </div>
      </div>
    </el-card>

    <!-- 可解释性明细 -->
    <el-dialog v-model="explainVisible" title="可解释性明细" width="720px">
      <div v-loading="explainLoading">
        <div v-if="!explainLoading && !explainDetail" class="muted">暂无数据</div>
        <div v-else-if="explainDetail">
          <div style="font-weight:700;margin-bottom:8px;">总分：{{ explainDetail.totalScore }} · {{ explainDetail.level }}</div>
          <div class="muted" style="white-space:pre-wrap;">{{ explainDetail.weightedTotalExplanation }}</div>
        </div>
      </div>
    </el-dialog>

    <!-- 评分规则 -->
    <el-dialog v-model="showRuleDialog" title="信用评分算法说明" width="520px">
      <div style="line-height:1.9;font-size:14px;">
        <p><b style="color:#3fb950">1. 代码稳定性 (25%)</b>：基于账号活跃年限与公开仓库数量计算，反映长期持续贡献能力。</p>
        <p style="margin-top:8px"><b style="color:#58a6ff">2. PR 质量 (30%)</b>：综合仓库数量与社交影响力，近似评估代码合并质量。</p>
        <p style="margin-top:8px"><b style="color:#a371f7">3. 团队协作 (25%)</b>：融合行为与社区认可度（followers/star）。</p>
        <p style="margin-top:8px"><b style="color:#e3b341">4. 合规性 (20%)</b>：账号注册时长与社区规范遵循度。</p>
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElLoading, ElMessage, ElMessageBox } from 'element-plus'
import * as echarts from 'echarts'
import { api, unwrap } from '../api'
import { auth } from '../auth'

const showRuleDialog = ref(false)
const explainVisible = ref(false)
const explainDetail = ref<any>(null)
const explainLoading = ref(false)

const newUser = ref('')
const sceneOptions = [
  { value: '综合', label: '综合' },
  { value: '后端', label: '后端' },
  { value: '前端', label: '前端' },
  { value: '协作', label: '协作' },
  { value: '合规', label: '合规' },
]
const selectedScene = ref('综合')
const compareUsers = ref<any[]>([])
const addLoading = ref(false)

const insightData = ref<any>(null)
const aiProfile = ref<any>(null)
const aiProfileLoading = ref(false)

const trendUsername = ref('')
const queryHistory = ref<any[]>([])
const historyLoaded = ref(false)
const selectedIds = ref<number[]>([])
const showOnlyFavorite = ref(false)
const removingIds = ref<number[]>([])

const exportCsvLoading = ref(false)
const exportHtmlLoading = ref(false)
const exportHintText = computed(() => {
  if (exportHtmlLoading.value) return '正在生成详细报告（可能需要 10-60 秒），请稍候...'
  if (exportCsvLoading.value) return '正在导出 CSV，请稍候...'
  return ''
})

let radarChart: echarts.ECharts | null = null
let trendChart: echarts.ECharts | null = null
let chartsResizeBound = false

const bindChartsResizeOnce = () => {
  if (chartsResizeBound) return
  chartsResizeBound = true
  window.addEventListener('resize', () => {
    try {
      radarChart?.resize()
      trendChart?.resize()
    } catch {}
  })
}
const disposeRadar = () => { try { radarChart?.dispose() } catch {} radarChart = null }
const disposeTrend = () => { try { trendChart?.dispose() } catch {} trendChart = null }

const visibleHistory = computed(() =>
  showOnlyFavorite.value ? queryHistory.value.filter((i) => i.isFavorite === 1) : queryHistory.value,
)

const safeJsonParse = (s: any, fallback: any) => {
  if (!s) return fallback
  try { return JSON.parse(String(s)) } catch { return fallback }
}
const aiTechTags = computed(() => {
  const arr = safeJsonParse(aiProfile.value?.techTagsJson, [])
  return Array.isArray(arr) ? arr : []
})
const aiTopProjects = computed(() => {
  const arr = safeJsonParse(aiProfile.value?.topReposJson, [])
  return Array.isArray(arr) ? arr : []
})

const resolveSceneValue = (scene: string) => {
  const map: any = { 综合: '综合', 后端: '核心开发者', 前端: '核心开发者', 协作: '辅助贡献', 合规: '辅助贡献' }
  return map[scene] || scene || '综合'
}
const normalizeSceneFilter = (scene: string) => {
  const map: any = { 综合: '综合', 核心开发者: '后端', 辅助贡献: '协作', 后端: '后端', 前端: '前端', 协作: '协作', 合规: '合规' }
  return map[scene] || '综合'
}
const formatSceneLabel = (scene: any) => normalizeSceneFilter(String(scene || '综合'))

const loggedInUser = () => auth.me.value || ({} as any)
const canViewExplainDetails = computed(() => {
  const token = auth.token.value
  const gh = insightData.value?.githubUsername
  const mine = String(loggedInUser().githubUsername || '').trim()
  return !!(token && gh && mine && mine.toLowerCase() === String(gh).trim().toLowerCase())
})

const openExplainDetails = async () => {
  const uid = loggedInUser().id
  if (!uid) return ElMessage.warning('未获取到当前登录用户 ID')
  explainVisible.value = true
  explainLoading.value = true
  explainDetail.value = null
  try {
    explainDetail.value = await unwrap(api.get(`/api/credit/detail/${uid}`, { params: { scene: resolveSceneValue(selectedScene.value) } }))
  } catch (e: any) {
    ElMessage.error(e?.message || '加载失败')
  } finally {
    explainLoading.value = false
  }
}

const addUser = async () => {
  const username = newUser.value.trim()
  if (!username) return
  if (compareUsers.value.find((u) => u.githubUsername === username)) return ElMessage.warning('该用户已在对比列表中')
  if (compareUsers.value.length >= 5) return ElMessage.warning('最多支持 5 人对比')
  addLoading.value = true
  try {
    const data = await unwrap<any>(api.post('/api/credit/query', { githubUsername: username, scene: resolveSceneValue(selectedScene.value) }))
    compareUsers.value.push(data)
    if (!trendUsername.value) trendUsername.value = username
    newUser.value = ''
    await nextTick()
    renderRadar()
    await loadInsight(username)
    await loadAiProfile(username)
    await loadTrend()
    await loadHistory()
  } catch (e: any) {
    ElMessage.error(e?.message || '未找到信用档案')
  } finally {
    addLoading.value = false
  }
}

const removeUser = (name: string) => {
  compareUsers.value = compareUsers.value.filter((u) => u.githubUsername !== name)
  if (insightData.value?.githubUsername === name) insightData.value = null
  if (trendUsername.value === name) trendUsername.value = compareUsers.value[0]?.githubUsername || ''
  renderRadar()
}
const clearCompare = () => {
  compareUsers.value = []
  insightData.value = null
  trendUsername.value = ''
  disposeRadar()
  disposeTrend()
}
const selectInsightUser = async (name: string) => {
  const u = compareUsers.value.find((x) => x.githubUsername === name)
  if (!u || u.error) return
  await loadInsight(name)
  await loadAiProfile(name)
  trendUsername.value = name
  await loadTrend()
}

const refreshCompareUsers = async () => {
  const usernames = compareUsers.value.map((i) => i.githubUsername).filter(Boolean)
  if (!usernames.length) return
  try {
    const data = await unwrap<any[]>(api.post('/api/credit/compare', { usernames, scene: resolveSceneValue(selectedScene.value) }))
    compareUsers.value = data || []
    const valid = compareUsers.value.filter((i) => !i.error)
    if (!valid.some((i) => i.githubUsername === trendUsername.value)) trendUsername.value = valid[0]?.githubUsername || ''
    await nextTick()
    renderRadar()
  } catch {}
}

const ensureRadarChart = () => {
  const dom = document.getElementById('radarChart')
  if (!dom) { disposeRadar(); return null }
  if (!radarChart) { bindChartsResizeOnce(); radarChart = echarts.init(dom as any) }
  return radarChart
}
const renderRadar = () => {
  const valid = compareUsers.value.filter((u) => !u.error)
  if (!valid.length) { disposeRadar(); if (!compareUsers.value.length) disposeTrend(); return }
  nextTick(() => {
    const chart = ensureRadarChart()
    if (!chart) return
    const colors = ['#58a6ff', '#3fb950', '#f5c518', '#a371f7', '#ff7b72']
    chart.setOption({
      backgroundColor: 'transparent',
      tooltip: { trigger: 'item' },
      legend: { bottom: 0, data: valid.map((u) => u.githubUsername), textStyle: { color: '#374151', fontSize: 12 } },
      radar: {
        indicator: [{ name: '稳定性', max: 100 }, { name: 'PR质量', max: 100 }, { name: '团队协作', max: 100 }, { name: '合规性', max: 100 }],
        shape: 'polygon',
        splitNumber: 4,
        axisName: { color: '#374151', fontSize: 12 },
        splitLine: { lineStyle: { color: '#e5e7eb' } },
        splitArea: { show: false },
        axisLine: { lineStyle: { color: '#e5e7eb' } },
      },
      series: [
        {
          type: 'radar',
          data: valid.map((u, i) => ({
            name: u.githubUsername,
            value: [u.stability || 0, u.prQuality || 0, u.collaboration || 0, u.compliance || 0],
            lineStyle: { color: colors[i % colors.length], width: 2 },
            itemStyle: { color: colors[i % colors.length] },
            areaStyle: { color: colors[i % colors.length], opacity: 0.08 },
          })),
        },
      ],
    } as any, true)
    try { chart.resize() } catch {}
  })
}

const loadInsight = async (username: string) => {
  try {
    insightData.value = await unwrap(api.get('/api/credit/insights', { params: { githubUsername: username, scene: resolveSceneValue(selectedScene.value) } }))
  } catch {}
}
const loadAiProfile = async (username: string) => {
  if (!username) return
  aiProfileLoading.value = true
  try {
    aiProfile.value = await unwrap(api.get('/api/credit/aiProfile', { params: { githubUsername: username, scene: resolveSceneValue(selectedScene.value) } }))
  } catch {
    aiProfile.value = null
  } finally {
    aiProfileLoading.value = false
  }
}
const refreshAiProfile = async () => {
  const name = insightData.value?.githubUsername
  if (!name) return
  aiProfileLoading.value = true
  try {
    aiProfile.value = await unwrap(api.post('/api/credit/aiProfile/refresh', { githubUsername: name, scene: resolveSceneValue(selectedScene.value) }))
  } catch {} finally { aiProfileLoading.value = false }
}

const loadTrend = async () => {
  const name = trendUsername.value
  if (!name) return
  try {
    const data = await unwrap<any[]>(api.get('/api/credit/trend', { params: { githubUsername: name, scene: resolveSceneValue(selectedScene.value), months: 6 } }))
    renderTrend(name, data)
  } catch {}
}
const renderTrend = (name: string, data: any[]) => {
  const dom = document.getElementById('trendChart')
  if (!dom) { disposeTrend(); return }
  if (!trendChart) { bindChartsResizeOnce(); trendChart = echarts.init(dom as any) }
  const isEmpty = !data || data.length === 0
  trendChart.setOption({
    backgroundColor: 'transparent',
    graphic: isEmpty ? [{ type: 'text', left: 'center', top: 'middle', style: { text: '暂无历史趋势数据', fill: '#6b7280', font: '14px sans-serif' } }] : [],
    xAxis: { type: 'category', data: isEmpty ? [] : data.map((i) => i.recordDate || i.month || ''), axisLine: { lineStyle: { color: '#e5e7eb' } }, axisLabel: { color: '#6b7280', fontSize: 11 } },
    yAxis: { type: 'value', min: 0, max: 100, splitLine: { lineStyle: { color: '#eef2f7' } }, axisLabel: { color: '#6b7280', fontSize: 11 } },
    tooltip: { trigger: 'axis', backgroundColor: '#ffffff', borderColor: '#e5e7eb', textStyle: { color: '#111827' } },
    series: [{ name, data: isEmpty ? [] : data.map((i) => i.totalScore || 0), type: 'line', smooth: true, symbol: 'circle', symbolSize: 6, lineStyle: { color: '#58a6ff', width: 2 }, itemStyle: { color: '#58a6ff' } }],
  } as any, true)
  try { trendChart.resize() } catch {}
}

const loadHistory = async () => {
  historyLoaded.value = false
  if (!auth.token.value) { historyLoaded.value = true; return }
  try {
    queryHistory.value = await unwrap<any[]>(api.get('/api/credit/queryHistory'))
  } catch {}
  historyLoaded.value = true
}

const formatTime = (t: any) => (t ? String(t).replace('T', ' ').substring(0, 16) : '')
const toggleSelect = (id: number) => {
  const idx = selectedIds.value.indexOf(id)
  if (idx === -1) selectedIds.value.push(id)
  else selectedIds.value.splice(idx, 1)
}
const toggleFilterFavorite = () => { showOnlyFavorite.value = !showOnlyFavorite.value; selectedIds.value = [] }
const toggleFavorite = async (item: any) => {
  try {
    await unwrap(api.post(`/api/credit/queryHistory/favorite/${item.id}`))
    item.isFavorite = item.isFavorite === 1 ? 0 : 1
  } catch {}
}
const animateRemove = (ids: number[], delay = 240) => {
  removingIds.value = [...removingIds.value, ...ids]
  return new Promise<void>((resolve) => setTimeout(() => {
    const set = new Set(ids)
    queryHistory.value = queryHistory.value.filter((i) => !set.has(i.id))
    removingIds.value = removingIds.value.filter((i) => !set.has(i))
    selectedIds.value = selectedIds.value.filter((i) => !set.has(i))
    resolve()
  }, delay))
}
const deleteOne = async (id: number) => {
  try { await ElMessageBox.confirm('确认删除此条查询记录？', '删除确认', { type: 'warning' }) } catch { return }
  try { await unwrap(api.delete(`/api/credit/queryHistory/${id}`)); ElMessage.success('已删除'); await animateRemove([id]) } catch {}
}
const batchDelete = async () => {
  if (!selectedIds.value.length) return
  try { await ElMessageBox.confirm(`确认删除选中的 ${selectedIds.value.length} 条记录？此操作不可撤销。`, '批量删除', { type: 'warning' }) } catch { return }
  const ids = [...selectedIds.value]
  try { await unwrap(api.delete('/api/credit/queryHistory', { params: { ids: ids.join(',') } })); ElMessage.success(`已删除 ${ids.length} 条记录`); await animateRemove(ids) } catch {}
}
const reQuery = (username: string, scene: string) => {
  newUser.value = username
  selectedScene.value = normalizeSceneFilter(scene)
  addUser()
}

const parseFilenameFromDisposition = (disposition: string) => {
  if (!disposition) return ''
  const star = disposition.match(/filename\\*\\s*=\\s*UTF-8''([^;]+)/i)
  if (star && star[1]) { try { return decodeURIComponent(star[1].trim().replace(/(^\"|\"$)/g, '')) } catch {} }
  const normal = disposition.match(/filename\\s*=\\s*(\"?)([^\";]+)\\1/i)
  return normal && normal[2] ? normal[2].trim() : ''
}
const buildLocalExportFileName = (prefix: string, userStr: string, ext: string) => {
  const users = String(userStr || '').split(',').map((s) => s.trim()).filter(Boolean)
  let who = 'unknown'
  if (users.length === 1) who = users[0]
  else if (users.length > 1) who = `${users[0]}_multi${users.length}`
  const d = new Date()
  const pad = (n: number) => String(n).padStart(2, '0')
  const ts = `${d.getFullYear()}${pad(d.getMonth() + 1)}${pad(d.getDate())}_${pad(d.getHours())}${pad(d.getMinutes())}`
  return `${prefix}_${who}_${ts}.${ext}`
}

const exportReport = async () => {
  const userStr = compareUsers.value.map((u) => u.githubUsername).filter(Boolean).join(',')
  if (!userStr) return ElMessage.warning('没有可导出的账号')
  if (exportCsvLoading.value || exportHtmlLoading.value) return
  exportCsvLoading.value = true
  const loading = ElLoading.service({ lock: true, text: '正在导出 CSV...', background: 'rgba(0,0,0,0.45)' })
  try {
    const t = auth.token.value || ''
    const res = await fetch(
      `/api/credit/export?usernames=${encodeURIComponent(userStr)}&scene=${encodeURIComponent(resolveSceneValue(selectedScene.value))}`,
      { headers: { Authorization: t ? `Bearer ${t}` : '' } },
    )
    if (!res.ok) throw new Error(`导出失败（HTTP ${res.status}）`)
    const blob = await res.blob()
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    const cd = res.headers.get('content-disposition') || ''
    link.download = parseFilenameFromDisposition(cd) || buildLocalExportFileName('信用对比报告', userStr, 'csv')
    document.body.appendChild(link)
    link.click()
    link.remove()
    URL.revokeObjectURL(url)
    ElMessage.success('已导出 CSV')
  } catch (e: any) {
    ElMessage.error(e?.message || '导出失败')
  } finally {
    exportCsvLoading.value = false
    loading.close()
  }
}
const exportDetailedReport = async () => {
  const userStr = compareUsers.value.map((u) => u.githubUsername).filter(Boolean).join(',')
  if (!userStr) return ElMessage.warning('没有可导出的账号')
  if (exportCsvLoading.value || exportHtmlLoading.value) return
  exportHtmlLoading.value = true
  const loading = ElLoading.service({ lock: true, text: '正在生成 HTML 详细报告...', background: 'rgba(0,0,0,0.45)' })
  try {
    const t = auth.token.value || ''
    const res = await fetch(
      `/api/credit/exportDetailed?usernames=${encodeURIComponent(userStr)}&scene=${encodeURIComponent(resolveSceneValue(selectedScene.value))}`,
      { headers: { Authorization: t ? `Bearer ${t}` : '' } },
    )
    if (!res.ok) throw new Error(`导出失败（HTTP ${res.status}）`)
    const blob = await res.blob()
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    const cd = res.headers.get('content-disposition') || ''
    link.download = parseFilenameFromDisposition(cd) || buildLocalExportFileName('信用详细分析报告', userStr, 'html')
    document.body.appendChild(link)
    link.click()
    link.remove()
    URL.revokeObjectURL(url)
    ElMessage.success('已下载 HTML 报告')
  } catch (e: any) {
    ElMessage.error(e?.message || '导出失败')
  } finally {
    exportHtmlLoading.value = false
    loading.close()
  }
}

const dimList = [
  { key: 'stability', label: '稳定性', color: '#3fb950' },
  { key: 'prQuality', label: 'PR质量', color: '#58a6ff' },
  { key: 'collaboration', label: '协作', color: '#a371f7' },
  { key: 'compliance', label: '合规', color: '#e3b341' },
]

const scoreColorClass = (score: any) => {
  const s = Number(score || 0)
  if (s >= 85) return 'score-color-high'
  if (s >= 70) return 'score-color-mid'
  if (s >= 55) return 'score-color-low'
  return 'score-color-risk'
}

const route = useRoute()

onMounted(async () => {
  await loadHistory()
  const u = String(route.query.user || '').trim()
  if (u) {
    newUser.value = u
    addUser()
  }
})

watch(compareUsers, (nv) => {
  if (nv.filter((u: any) => !u.error).length > 0) nextTick(() => renderRadar())
}, { deep: true })

watch(selectedScene, async () => {
  if (!compareUsers.value.length) return
  const focusedUser = insightData.value?.githubUsername || trendUsername.value || compareUsers.value[0]?.githubUsername || ''
  await refreshCompareUsers()
  if (focusedUser && compareUsers.value.some((item) => item.githubUsername === focusedUser && !item.error)) {
    trendUsername.value = focusedUser
    await loadInsight(focusedUser)
    await loadAiProfile(focusedUser)
  } else if (trendUsername.value) {
    await loadInsight(trendUsername.value)
    await loadAiProfile(trendUsername.value)
  }
  await loadTrend()
})
</script>

<style scoped>
.page { display: grid; gap: 14px; }
.title-row { display:flex; align-items:center; justify-content:space-between; gap: 10px; flex-wrap: wrap; }
.title { font-weight: 800; font-size: 18px; }
.title-actions { display:flex; gap: 8px; flex-wrap: wrap; }
.card { border-radius: 12px; }
.card-hd { display:flex; align-items:center; justify-content:space-between; gap: 10px; }
.card-title { font-weight: 800; }
.row { display:flex; align-items:center; justify-content:space-between; }
.muted { color:#6b7280; font-size: 13px; }
.search-row { display:flex; gap: 10px; flex-wrap: wrap; }
.search-row :deep(.el-input) { flex: 1; min-width: 200px; }
.compare-tags { display:flex; gap: 8px; flex-wrap: wrap; margin-top: 10px; }
.chart-wrap { width: 100%; }
.chart-empty { padding: 20px; text-align:center; }
.grid-2 { display:grid; grid-template-columns: 1fr 1fr; gap: 14px; }
@media (max-width: 980px) { .grid-2 { grid-template-columns: 1fr; } }
.insights-grid { display:grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 12px; margin-top: 10px; }
.insight-item { background: #f8fafc; border: 1px solid #e5e7eb; border-radius: 12px; padding: 12px; }
.insight-label { font-size: 12px; color: #6b7280; }
.insight-value { font-size: 22px; font-weight: 900; color: #2563eb; margin-top: 4px; }
.insight-sub { font-size: 12px; color: #6b7280; margin-top: 6px; }
.dim-bars { margin-top: 12px; display:flex; flex-direction: column; gap: 10px; }
.dim-row { display:flex; align-items:center; gap: 10px; }
.dim-label { width: 62px; color:#6b7280; font-size: 13px; }
.dim-bar-track { flex: 1; height: 8px; background: #eef2ff; border-radius: 999px; overflow:hidden; }
.dim-bar-fill { height: 100%; border-radius: 999px; }
.dim-score-val { width: 36px; text-align:right; font-weight: 800; color:#111827; }
.ai-summary { font-weight: 700; line-height: 1.6; }
.history-list { display:flex; flex-direction: column; gap: 10px; }
.hi-card { display:flex; gap: 12px; padding: 12px; border:1px solid #e5e7eb; border-radius: 14px; background: #ffffff; color:#111827; }
.hi-card.removing { opacity: 0; transform: translateX(16px); pointer-events: none; transition: .24s; }
.hi-main { display:flex; gap: 14px; flex: 1; min-width: 0; cursor:pointer; }
.hi-profile { display:flex; gap: 10px; flex: 1; min-width: 0; }
.hi-avatar { width: 42px; height: 42px; border-radius: 999px; background: #111827; display:flex; align-items:center; justify-content:center; font-weight: 900; color:#fff; }
.hi-name { color:#58a6ff; font-weight: 800; }
.hi-meta { color:#9ca3af; font-size: 12px; margin-top: 2px; display:flex; gap: 8px; flex-wrap: wrap; }
.hi-scene-tag { padding: 2px 8px; border-radius: 999px; border:1px solid rgba(88,166,255,.22); background: rgba(88,166,255,.12); color:#58a6ff; font-size: 11px; }
.hi-score-wrap { min-width: 82px; border:1px solid #e5e7eb; background: #fafafa; border-radius: 12px; padding: 10px 12px; }
.hi-score { font-size: 22px; font-weight: 900; }
.score-color-high { color:#3fb950; }
.score-color-mid { color:#58a6ff; }
.score-color-low { color:#e3b341; }
.score-color-risk { color:#f85149; }
.level-优秀 { color:#3fb950; } .level-良好{color:#58a6ff;} .level-合格{color:#e3b341;} .level-待观察{color:#f85149;}
</style>

