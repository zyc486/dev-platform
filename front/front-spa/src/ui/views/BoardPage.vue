<template>
  <div class="grid">
    <el-card>
      <template #header>
        <div class="row">
          <div style="font-weight:700;">看板</div>
          <div class="row" style="gap:8px;">
            <el-button size="small" type="primary" @click="openCreate = true">新建任务</el-button>
            <el-button size="small" @click="load">刷新</el-button>
          </div>
        </div>
      </template>
      <div class="cols" v-loading="loading">
        <div class="col" v-for="s in statuses" :key="s.value">
          <div class="col-title">{{ s.label }} <span class="muted">({{ group[s.value]?.length || 0 }})</span></div>
          <div class="card" v-for="i in group[s.value] || []" :key="i.id" @click="goIssue(i.id)">
            <div class="title">{{ i.title }}</div>
            <div class="meta">
              <el-tag size="small" type="info">{{ i.priority }}</el-tag>
              <span class="muted">#{{ i.id }}</span>
            </div>
            <div class="actions">
              <el-button size="small" text @click.stop="move(i, prevStatus(s.value))" :disabled="!prevStatus(s.value)">←</el-button>
              <el-button size="small" text @click.stop="move(i, nextStatus(s.value))" :disabled="!nextStatus(s.value)">→</el-button>
            </div>
          </div>
        </div>
      </div>
    </el-card>

    <el-dialog v-model="openCreate" title="新建任务" width="560px">
      <el-form label-width="90px">
        <el-form-item label="标题">
          <el-input v-model="form.title" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" :rows="4" />
        </el-form-item>
        <el-form-item label="优先级">
          <el-select v-model="form.priority" style="width: 180px;">
            <el-option label="low" value="low" />
            <el-option label="medium" value="medium" />
            <el-option label="high" value="high" />
            <el-option label="urgent" value="urgent" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="openCreate = false">取消</el-button>
        <el-button type="primary" :loading="creating" @click="create">创建</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { api, unwrap } from '../api'

const route = useRoute()
const router = useRouter()
const projectId = Number(route.params.projectId)

const statuses = [
  { value: 'todo', label: 'ToDo' },
  { value: 'doing', label: 'Doing' },
  { value: 'done', label: 'Done' },
]

const loading = ref(false)
const creating = ref(false)
const issues = ref<any[]>([])

const openCreate = ref(false)
const form = reactive({ title: '', description: '', priority: 'medium' })

const group = computed(() => {
  const g: Record<string, any[]> = { todo: [], doing: [], done: [] }
  for (const i of issues.value) g[i.status || 'todo']?.push(i)
  return g
})

const load = async () => {
  loading.value = true
  try {
    issues.value = await unwrap<any[]>(api.get('/api/collab/issue/list', { params: { projectId } }))
  } catch (e: any) {
    ElMessage.error(e?.message || '加载失败')
  } finally {
    loading.value = false
  }
}

const create = async () => {
  if (!form.title.trim()) return ElMessage.warning('请输入标题')
  creating.value = true
  try {
    await unwrap(api.post('/api/collab/issue/create', { projectId, ...form }))
    ElMessage.success('已创建')
    openCreate.value = false
    form.title = ''
    form.description = ''
    form.priority = 'medium'
    await load()
  } catch (e: any) {
    ElMessage.error(e?.message || '创建失败')
  } finally {
    creating.value = false
  }
}

const move = async (issue: any, to: string | null) => {
  if (!to) return
  try {
    await unwrap(api.post('/api/collab/issue/update', { issueId: issue.id, status: to }))
    await load()
  } catch (e: any) {
    ElMessage.error(e?.message || '更新失败')
  }
}

const goIssue = (id: number) => router.push(`/issues/${id}`)

const prevStatus = (s: string) => (s === 'doing' ? 'todo' : s === 'done' ? 'doing' : null)
const nextStatus = (s: string) => (s === 'todo' ? 'doing' : s === 'doing' ? 'done' : null)

onMounted(load)
</script>

<style scoped>
.grid { display: grid; gap: 14px; }
.row { display:flex; align-items:center; justify-content:space-between; }
.cols { display:grid; grid-template-columns: repeat(3, 1fr); gap: 12px; }
.col { background:#f8fafc; border: 1px solid #e5e7eb; border-radius: 12px; padding: 10px; min-height: 60vh; }
.col-title { font-weight: 700; margin-bottom: 8px; display:flex; align-items:center; justify-content:space-between; }
.card { background:#fff; border: 1px solid #e5e7eb; border-radius: 12px; padding: 10px; margin-bottom: 10px; cursor: pointer; }
.card:hover { border-color: #c7d2fe; box-shadow: 0 6px 18px rgba(15,23,42,.06); }
.title { font-weight: 700; }
.meta { display:flex; gap:8px; align-items:center; margin-top: 8px; }
.actions { display:flex; justify-content:space-between; margin-top: 6px; }
.muted { color: #6b7280; font-size: 12px; }
</style>

