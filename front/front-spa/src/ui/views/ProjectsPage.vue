<template>
  <div class="grid">
    <el-card class="card">
      <template #header>
        <div class="row">
          <div>
            <div class="title">协作与项目</div>
          </div>
          <div class="actions">
            <el-button size="small" @click="refreshCurrent">刷新</el-button>
            <el-button size="small" type="primary" plain @click="openPublish = true">发布协作任务</el-button>
          </div>
        </div>
      </template>

      <el-tabs v-model="tab" @tab-change="onTabChange">
        <el-tab-pane label="协作大厅" name="hall">
          <el-table :data="hallList" v-loading="loadingHall" style="width: 100%;">
            <el-table-column prop="id" label="ID" width="90" />
            <el-table-column prop="title" label="标题" min-width="180" show-overflow-tooltip />
            <el-table-column prop="creatorUsername" label="发起人" width="120" />
            <el-table-column prop="minCredit" label="最低信用" width="100" />
            <el-table-column prop="status" label="状态" width="120" />
            <el-table-column label="操作" width="320">
              <template #default="{ row }">
                <el-button size="small" type="primary" plain @click="openMatch(row)">匹配推荐</el-button>
                <el-button size="small" @click="openRatings(row)">互评记录</el-button>
                <el-button size="small" type="success" :disabled="!hasToken" @click="apply(row)">申请加入</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="我的发布" name="myPublish">
          <el-table :data="myPublishList" v-loading="loadingMyPublish" style="width: 100%;">
            <el-table-column prop="id" label="ID" width="90" />
            <el-table-column prop="title" label="标题" min-width="180" show-overflow-tooltip />
            <el-table-column prop="minCredit" label="最低信用" width="100" />
            <el-table-column prop="status" label="状态" width="120" />
            <el-table-column label="操作" width="360">
              <template #default="{ row }">
                <el-button size="small" @click="openApplyList(row)">申请审核</el-button>
                <el-button size="small" type="warning" plain @click="closeCollab(row)" :disabled="row.status !== 'pending'">关闭</el-button>
                <el-button size="small" type="success" plain @click="finishCollab(row)" :disabled="row.status !== 'in_progress'">标记完成</el-button>
                <el-button size="small" @click="openRatings(row)">互评记录</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="我的申请" name="myApply">
          <el-table :data="myApplyList" v-loading="loadingMyApply" style="width: 100%;">
            <el-table-column prop="applyId" label="申请ID" width="100" />
            <el-table-column prop="projectId" label="项目ID" width="90" />
            <el-table-column prop="title" label="标题" min-width="180" show-overflow-tooltip />
            <el-table-column prop="status" label="状态" width="110" />
            <el-table-column prop="auditReason" label="原因/备注" min-width="160" show-overflow-tooltip />
            <el-table-column label="操作" width="260">
              <template #default="{ row }">
                <el-button size="small" @click="openRatings({ id: row.projectId })">互评记录</el-button>
                <el-button size="small" type="primary" plain @click="openRate(row)" :disabled="!canRate(row)">
                  互评
                </el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="申请审核" name="review">
          <el-alert type="info" show-icon :closable="false" class="helper">
            这里展示你发布的协作任务收到的申请，可在此通过/拒绝。通过后项目会进入进行中。
          </el-alert>
          <el-table :data="applyList" v-loading="loadingApplyList" style="width: 100%;">
            <el-table-column prop="applyId" label="申请ID" width="100" />
            <el-table-column prop="projectId" label="项目ID" width="90" />
            <el-table-column prop="projectTitle" label="项目" min-width="160" show-overflow-tooltip />
            <el-table-column prop="applicantUsername" label="申请人" width="120" />
            <el-table-column prop="githubUsername" label="GitHub" width="160" />
            <el-table-column prop="status" label="状态" width="110" />
            <el-table-column prop="auditReason" label="原因" min-width="140" show-overflow-tooltip />
            <el-table-column label="操作" width="240">
              <template #default="{ row }">
                <el-button size="small" type="success" :disabled="row.status !== 'pending'" @click="reviewApply(row,'approve')">通过</el-button>
                <el-button size="small" type="danger" plain :disabled="row.status !== 'pending'" @click="reviewApply(row,'reject')">拒绝</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="项目管理" name="pm">
          <el-alert type="warning" show-icon :closable="false" class="helper">
            这里是“项目管理/看板”能力（新模型）。它不替代协作大厅，但可用于团队任务流转。
          </el-alert>
          <el-table :data="projects" v-loading="loadingPm" style="width: 100%;">
            <el-table-column prop="id" label="ID" width="90" />
            <el-table-column prop="name" label="名称" />
            <el-table-column prop="visibility" label="可见性" width="110" />
            <el-table-column label="操作" width="220">
              <template #default="{ row }">
                <el-button size="small" @click="go(row.id)">进入</el-button>
                <el-button size="small" type="primary" plain @click="goBoard(row.id)">看板</el-button>
              </template>
            </el-table-column>
          </el-table>

          <div style="margin-top: 12px; display:flex; justify-content:flex-end;">
            <el-button type="primary" size="small" @click="openCreate = true">新建项目</el-button>
          </div>
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <!-- 发布协作任务（旧模型 /api/collab/publish） -->
    <el-dialog v-model="openPublish" title="发布协作任务" width="620px">
      <el-form label-width="110px">
        <el-form-item label="标题">
          <el-input v-model="publishForm.title" placeholder="例如：Vue3 组件库共建计划" />
        </el-form-item>
        <el-form-item label="详情">
          <el-input v-model="publishForm.content" type="textarea" :rows="6" placeholder="描述需求、技术栈、期望队友画像等" />
        </el-form-item>
        <el-form-item label="最低信用分">
          <el-input-number v-model="publishForm.minCredit" :min="0" :max="200" style="width: 200px;" />
          <span class="muted" style="margin-left: 10px;">低于该分数将被风控拦截</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="openPublish = false">取消</el-button>
        <el-button type="primary" :loading="publishing" :disabled="!hasToken" @click="publishCollab">确认发布</el-button>
      </template>
    </el-dialog>

    <!-- 审核弹窗：按项目筛选 -->
    <el-dialog v-model="applyDialog.visible" title="申请审核（按项目）" width="720px">
      <div class="muted" style="margin-bottom:10px;">项目：{{ applyDialog.project?.title || applyDialog.project?.id || '—' }}</div>
      <el-table :data="applyDialog.list" v-loading="applyDialog.loading">
        <el-table-column prop="applyId" label="申请ID" width="90" />
        <el-table-column prop="applicantUsername" label="申请人" width="120" />
        <el-table-column prop="githubUsername" label="GitHub" width="160" />
        <el-table-column prop="status" label="状态" width="110" />
        <el-table-column prop="auditReason" label="原因" min-width="140" show-overflow-tooltip />
        <el-table-column label="操作" width="220">
          <template #default="{ row }">
            <el-button size="small" type="success" :disabled="row.status !== 'pending'" @click="reviewApply(row,'approve')">通过</el-button>
            <el-button size="small" type="danger" plain :disabled="row.status !== 'pending'" @click="reviewApply(row,'reject')">拒绝</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>

    <!-- 匹配推荐 -->
    <el-dialog v-model="matchDialog.visible" title="匹配推荐开发者" width="760px">
      <div class="muted" style="margin-bottom:10px;">项目：{{ matchDialog.project?.title || matchDialog.project?.id || '—' }}</div>
      <el-table :data="matchDialog.list" v-loading="matchDialog.loading">
        <el-table-column prop="username" label="用户名" width="140" />
        <el-table-column prop="githubUsername" label="GitHub" width="160" />
        <el-table-column prop="totalScore" label="信用分" width="90" />
        <el-table-column prop="level" label="等级" width="90" />
        <el-table-column prop="techTags" label="技术标签" min-width="180" show-overflow-tooltip />
        <el-table-column label="操作" width="140">
          <template #default="{ row }">
            <el-button size="small" @click="goPublic(row.username)">看主页</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>

    <!-- 互评记录 -->
    <el-dialog v-model="ratingsDialog.visible" title="互评记录" width="760px">
      <div class="muted" style="margin-bottom:10px;">协作ID：{{ ratingsDialog.projectId || '—' }}</div>
      <el-table :data="ratingsDialog.list" v-loading="ratingsDialog.loading">
        <el-table-column prop="fromUserId" label="fromUserId" width="110" />
        <el-table-column prop="toUserId" label="toUserId" width="110" />
        <el-table-column prop="rating" label="评分" width="80" />
        <el-table-column prop="comment" label="评价" min-width="220" show-overflow-tooltip />
        <el-table-column prop="createTime" label="时间" width="160" />
      </el-table>
    </el-dialog>

    <!-- 提交互评 -->
    <el-dialog v-model="rateDialog.visible" title="提交互评" width="540px">
      <div class="muted" style="margin-bottom:10px;">协作ID：{{ rateDialog.projectId }}（对方用户ID：{{ rateDialog.toUserId }}）</div>
      <el-form label-width="90px">
        <el-form-item label="评分(1-5)">
          <el-input-number v-model="rateDialog.score" :min="1" :max="5" />
        </el-form-item>
        <el-form-item label="评价">
          <el-input v-model="rateDialog.comment" type="textarea" :rows="4" placeholder="可选" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="rateDialog.visible=false">取消</el-button>
        <el-button type="primary" :loading="rateDialog.loading" @click="submitRate">提交</el-button>
      </template>
    </el-dialog>

    <!-- 新模型：新建项目 -->
    <el-dialog v-model="openCreate" title="新建项目（看板模型）" width="520px">
      <el-form label-width="90px">
        <el-form-item label="项目名">
          <el-input v-model="form.name" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" :rows="4" />
        </el-form-item>
        <el-form-item label="可见性">
          <el-select v-model="form.visibility" style="width: 180px;">
            <el-option label="private" value="private" />
            <el-option label="public" value="public" />
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
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { api, unwrap } from '../api'
import { auth } from '../auth'

const router = useRouter()

const tab = ref<'hall' | 'myPublish' | 'myApply' | 'review' | 'pm'>('hall')

const hasToken = computed(() => !!auth.token.value)

// 旧协作模型（collab.html 语义）
const loadingHall = ref(false)
const loadingMyPublish = ref(false)
const loadingMyApply = ref(false)
const loadingApplyList = ref(false)

const hallList = ref<any[]>([])
const myPublishList = ref<any[]>([])
const myApplyList = ref<any[]>([])
const applyList = ref<any[]>([])

const openPublish = ref(false)
const publishing = ref(false)
const publishForm = reactive({ title: '', content: '', minCredit: 60 })

const applyDialog = reactive<{ visible: boolean; loading: boolean; project: any; list: any[] }>({
  visible: false,
  loading: false,
  project: null,
  list: [],
})

const matchDialog = reactive<{ visible: boolean; loading: boolean; project: any; list: any[] }>({
  visible: false,
  loading: false,
  project: null,
  list: [],
})

const ratingsDialog = reactive<{ visible: boolean; loading: boolean; projectId: number | null; list: any[] }>({
  visible: false,
  loading: false,
  projectId: null,
  list: [],
})

const rateDialog = reactive<{ visible: boolean; loading: boolean; projectId: number; toUserId: number; score: number; comment: string }>({
  visible: false,
  loading: false,
  projectId: 0,
  toUserId: 0,
  score: 5,
  comment: '',
})

// 新项目管理模型（/api/collab/project/*）
const loadingPm = ref(false)
const creating = ref(false)
const projects = ref<any[]>([])

const openCreate = ref(false)
const form = reactive({ name: '', description: '', visibility: 'private' })

const loadPm = async () => {
  loadingPm.value = true
  try {
    projects.value = await unwrap<any[]>(api.get('/api/collab/project/my'))
  } catch (e: any) {
    ElMessage.error(e?.message || '加载失败')
  } finally {
    loadingPm.value = false
  }
}

const create = async () => {
  if (!form.name.trim()) return ElMessage.warning('请输入项目名')
  creating.value = true
  try {
    await unwrap(api.post('/api/collab/project/create', { ...form }))
    ElMessage.success('已创建')
    openCreate.value = false
    form.name = ''
    form.description = ''
    form.visibility = 'private'
    await loadPm()
  } catch (e: any) {
    ElMessage.error(e?.message || '创建失败')
  } finally {
    creating.value = false
  }
}

const go = (id: number) => router.push(`/projects/${id}`)
const goBoard = (id: number) => router.push(`/projects/${id}/board`)

const goPublic = (username: string) => {
  const u = String(username || '').trim()
  if (u) router.push(`/u/${encodeURIComponent(u)}`)
}

const normalizeTime = (t: any) => (t ? String(t).replace('T', ' ').slice(0, 16) : '')

const loadHall = async () => {
  loadingHall.value = true
  try {
    hallList.value = await unwrap<any[]>(api.get('/api/collab/list'))
  } catch (e: any) {
    hallList.value = []
    ElMessage.error(e?.message || '加载失败')
  } finally {
    loadingHall.value = false
  }
}

const loadMyPublish = async () => {
  loadingMyPublish.value = true
  try {
    myPublishList.value = await unwrap<any[]>(api.get('/api/collab/myPublish'))
  } catch (e: any) {
    myPublishList.value = []
    ElMessage.error(e?.message || '加载失败')
  } finally {
    loadingMyPublish.value = false
  }
}

const loadMyApply = async () => {
  loadingMyApply.value = true
  try {
    myApplyList.value = await unwrap<any[]>(api.get('/api/collab/myApply'))
  } catch (e: any) {
    myApplyList.value = []
    ElMessage.error(e?.message || '加载失败')
  } finally {
    loadingMyApply.value = false
  }
}

const loadApplyList = async () => {
  loadingApplyList.value = true
  try {
    applyList.value = await unwrap<any[]>(api.get('/api/collab/applyList'))
  } catch (e: any) {
    applyList.value = []
    ElMessage.error(e?.message || '加载失败')
  } finally {
    loadingApplyList.value = false
  }
}

const refreshCurrent = async () => {
  if (tab.value === 'hall') return loadHall()
  if (tab.value === 'myPublish') return loadMyPublish()
  if (tab.value === 'myApply') return loadMyApply()
  if (tab.value === 'review') return loadApplyList()
  if (tab.value === 'pm') return loadPm()
}

const onTabChange = (name: string | number) => {
  const n = String(name)
  if (n === 'hall') void loadHall()
  if (n === 'myPublish') void loadMyPublish()
  if (n === 'myApply') void loadMyApply()
  if (n === 'review') void loadApplyList()
  if (n === 'pm') void loadPm()
}

const publishCollab = async () => {
  if (!hasToken.value) return ElMessage.warning('请先登录')
  if (!publishForm.title.trim() || !publishForm.content.trim()) return ElMessage.warning('请填写标题与详情')
  publishing.value = true
  try {
    await unwrap(api.post('/api/collab/publish', { ...publishForm }))
    ElMessage.success('已发布')
    openPublish.value = false
    publishForm.title = ''
    publishForm.content = ''
    publishForm.minCredit = 60
    tab.value = 'myPublish'
    await loadMyPublish()
  } catch (e: any) {
    ElMessage.error(e?.message || '发布失败')
  } finally {
    publishing.value = false
  }
}

const apply = async (row: any) => {
  if (!hasToken.value) return ElMessage.warning('请先登录')
  try {
    await unwrap(api.post('/api/collab/apply', null, { params: { projectId: row.id } }))
    ElMessage.success('申请已提交')
  } catch (e: any) {
    const msg = e?.message || '申请失败'
    // 风控拦截体验：明确引导去信用页查看差距
    if (String(msg).includes('风控')) {
      try {
        await ElMessageBox.confirm(msg + ' 现在去信用页查看？', '风控拦截', { type: 'warning', confirmButtonText: '去信用页', cancelButtonText: '取消' })
        router.push('/credit')
        return
      } catch {
        // ignore
      }
    }
    ElMessage.error(msg)
  }
}

const openApplyList = async (project: any) => {
  applyDialog.visible = true
  applyDialog.project = project
  applyDialog.loading = true
  try {
    applyDialog.list = await unwrap<any[]>(api.get('/api/collab/applyList', { params: { projectId: project.id } }))
    applyDialog.list = (applyDialog.list || []).map((x: any) => ({ ...x, applyTime: normalizeTime(x.applyTime), auditTime: normalizeTime(x.auditTime) }))
  } catch {
    applyDialog.list = []
  } finally {
    applyDialog.loading = false
  }
}

const reviewApply = async (row: any, action: 'approve' | 'reject') => {
  if (!hasToken.value) return ElMessage.warning('请先登录')
  let reason = ''
  try {
    const prompt = action === 'approve' ? '通过原因（可空）' : '拒绝原因（建议填写）'
    const r = await ElMessageBox.prompt(prompt, '审核申请', {
      inputPlaceholder: action === 'approve' ? '例如：欢迎加入' : '例如：信用分/技能不匹配',
      confirmButtonText: '提交',
      cancelButtonText: '取消',
    })
    reason = String((r as any).value || '').trim()
  } catch {
    return
  }
  try {
    await unwrap(api.post('/api/collab/apply/review', { applyId: row.applyId, action, reason }))
    ElMessage.success('已处理')
    // 刷新两个视角
    void loadApplyList()
    if (applyDialog.visible && applyDialog.project?.id) void openApplyList(applyDialog.project)
    void loadMyPublish()
  } catch (e: any) {
    ElMessage.error(e?.message || '操作失败')
  }
}

const closeCollab = async (row: any) => {
  try { await ElMessageBox.confirm('确认关闭该协作任务？', '关闭确认', { type: 'warning' }) } catch { return }
  try {
    await unwrap(api.post('/api/collab/close', null, { params: { projectId: row.id } }))
    ElMessage.success('已关闭')
    await loadMyPublish()
  } catch (e: any) {
    ElMessage.error(e?.message || '关闭失败')
  }
}

const finishCollab = async (row: any) => {
  try { await ElMessageBox.confirm('确认将该协作任务标记为已完成？完成后可进行互评。', '完成确认', { type: 'warning' }) } catch { return }
  try {
    await unwrap(api.post('/api/collab/finish', null, { params: { projectId: row.id } }))
    ElMessage.success('已标记完成')
    await loadMyPublish()
  } catch (e: any) {
    ElMessage.error(e?.message || '操作失败')
  }
}

const openMatch = async (project: any) => {
  matchDialog.visible = true
  matchDialog.project = project
  matchDialog.loading = true
  try {
    matchDialog.list = await unwrap<any[]>(api.get('/api/collab/match', { params: { projectId: project.id, limit: 8 } }))
  } catch {
    matchDialog.list = []
  } finally {
    matchDialog.loading = false
  }
}

const openRatings = async (project: any) => {
  const pid = Number(project?.id)
  if (!pid) return
  ratingsDialog.visible = true
  ratingsDialog.projectId = pid
  ratingsDialog.loading = true
  try {
    const list = await unwrap<any[]>(api.get(`/api/collab/ratings/${pid}`))
    ratingsDialog.list = (list || []).map((x: any) => ({ ...x, createTime: normalizeTime(x.createTime) }))
  } catch {
    ratingsDialog.list = []
  } finally {
    ratingsDialog.loading = false
  }
}

const canRate = (row: any) => {
  // 只要申请通过且项目已完成/进行中，就允许互评入口（后端会做更严校验）
  return hasToken.value && row && row.status === 'approved' && (row.projectStatus === 'completed' || row.projectStatus === 'in_progress')
}

const openRate = (row: any) => {
  const projectId = Number(row.projectId)
  const toUserId = Number(row.creatorId) // 简化：对发布者互评；如需对“批准成员”互评，可扩展选择器
  if (!projectId || !toUserId) return ElMessage.warning('缺少互评目标信息')
  rateDialog.visible = true
  rateDialog.projectId = projectId
  rateDialog.toUserId = toUserId
  rateDialog.score = 5
  rateDialog.comment = ''
}

const submitRate = async () => {
  rateDialog.loading = true
  try {
    await unwrap(api.post('/api/collab/rate', { projectId: rateDialog.projectId, toUserId: rateDialog.toUserId, score: rateDialog.score, comment: rateDialog.comment }))
    ElMessage.success('互评已提交')
    rateDialog.visible = false
    void openRatings({ id: rateDialog.projectId })
  } catch (e: any) {
    ElMessage.error(e?.message || '提交失败')
  } finally {
    rateDialog.loading = false
  }
}

onMounted(async () => {
  // 默认进入协作大厅（旧版入口）
  await loadHall()
})
</script>

<style scoped>
.grid { display: grid; gap: 14px; }
.card { border-radius: 12px; }
.row { display:flex; align-items:flex-start; justify-content:space-between; gap: 10px; flex-wrap: wrap; }
.title { font-weight: 900; }
.muted { color: #6b7280; font-size: 13px; }
.actions { display:flex; gap: 8px; flex-wrap: wrap; align-items: center; }
.helper { margin-bottom: 12px; }
</style>

