<template>
  <div class="grid">
    <el-card>
      <template #header>
        <div class="row">
          <div style="font-weight:700;">项目：{{ project?.name || '—' }}</div>
          <div class="row" style="gap:8px;">
            <el-button
              size="small"
              type="success"
              plain
              :loading="exporting"
              :disabled="!projectId || exporting"
              @click="exportProjectReport"
            >{{ exporting ? '正在生成报告...' : '导出协作报告(HTML)' }}</el-button>
            <el-button size="small" type="primary" plain @click="goBoard">看板</el-button>
            <el-button size="small" @click="reload">刷新</el-button>
          </div>
        </div>
      </template>
      <div class="muted">{{ project?.description || '暂无描述' }}</div>
      <div style="margin-top:10px;">
        <el-tag type="info">visibility: {{ project?.visibility }}</el-tag>
      </div>
    </el-card>

    <el-card>
      <template #header>
        <div class="row">
          <div style="font-weight:700;">团队画像</div>
          <el-tag type="success" v-if="team?.members?.length">{{ team.members.length }} 人</el-tag>
        </div>
      </template>
      <el-table :data="team?.members || []" v-loading="loadingTeam">
        <el-table-column prop="userId" label="用户ID" width="100" />
        <el-table-column prop="githubUsername" label="GitHub" width="160" />
        <el-table-column prop="role" label="角色" width="120" />
        <el-table-column label="信用">
          <template #default="{ row }">
            <span v-if="row.credit">
              {{ row.credit.totalScore ?? '—' }} / {{ row.credit.level ?? '—' }}
            </span>
            <span v-else class="muted">暂无</span>
          </template>
        </el-table-column>
        <el-table-column label="AI摘要">
          <template #default="{ row }">
            <span class="muted">{{ row.aiProfile?.summary || row.aiProfile?.status || '—' }}</span>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { api, unwrap } from '../api'
import { auth } from '../auth'

const route = useRoute()
const router = useRouter()
const projectId = Number(route.params.projectId)

const loading = ref(false)
const loadingTeam = ref(false)
const project = ref<any>(null)
const team = ref<any>(null)
const exporting = ref(false)

const reload = async () => {
  loading.value = true
  try {
    const data = await unwrap<any>(api.get('/api/collab/project/detail', { params: { projectId } }))
    project.value = data.project
  } catch (e: any) {
    ElMessage.error(e?.message || '加载失败')
  } finally {
    loading.value = false
  }
}

const loadTeam = async () => {
  loadingTeam.value = true
  try {
    team.value = await unwrap<any>(api.get('/api/collab/project/teamProfile', { params: { projectId, scene: '综合' } }))
  } catch (e: any) {
    ElMessage.error(e?.message || '团队画像加载失败')
  } finally {
    loadingTeam.value = false
  }
}

const goBoard = () => router.push(`/projects/${projectId}/board`)

const parseFilenameFromDisposition = (disposition: string) => {
  if (!disposition) return ''
  const star = disposition.match(/filename\\*\\s*=\\s*UTF-8''([^;]+)/i)
  if (star && star[1]) {
    try {
      return decodeURIComponent(star[1].trim().replace(/(^\"|\"$)/g, ''))
    } catch {}
  }
  const normal = disposition.match(/filename\\s*=\\s*(\"?)([^\";]+)\\1/i)
  return normal && normal[2] ? normal[2].trim() : ''
}

const exportProjectReport = async () => {
  if (!projectId) return
  if (exporting.value) return
  exporting.value = true
  try {
    const scene = '综合'
    const t = auth.token.value || ''
    const res = await fetch(
      `/api/collab/project/exportReport?projectId=${encodeURIComponent(String(projectId))}&scene=${encodeURIComponent(scene)}`,
      { headers: { Authorization: t ? `Bearer ${t}` : '' } },
    )
    if (!res.ok) throw new Error(`导出失败（HTTP ${res.status}）`)
    const blob = await res.blob()
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    const cd = res.headers.get('content-disposition') || ''
    a.download = parseFilenameFromDisposition(cd) || `项目协作报告_${projectId}.html`
    document.body.appendChild(a)
    a.click()
    a.remove()
    URL.revokeObjectURL(url)
    ElMessage.success('已下载协作报告')
  } catch (e: any) {
    ElMessage.error(e?.message || '导出失败')
  } finally {
    exporting.value = false
  }
}

onMounted(async () => {
  await reload()
  await loadTeam()
})
</script>

<style scoped>
.grid { display: grid; gap: 14px; }
.row { display:flex; align-items:center; justify-content:space-between; }
.muted { color: #6b7280; }
</style>

