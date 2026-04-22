<template>
  <div class="layout">
    <aside class="aside">
      <div class="logo">管理后台</div>
      <div class="menu">
        <div v-for="m in menus" :key="m.key" class="menu-item" :class="{ active: activeMenu === m.key }" @click="activeMenu = m.key">
          {{ m.label }}
        </div>
      </div>
    </aside>

    <main class="main">
      <header class="header">
        <h2>{{ titleMap[activeMenu] }}</h2>
        <div class="hdr-right">
          <span class="muted">{{ adminName }}</span>
          <el-button size="small" @click="logout">退出</el-button>
        </div>
      </header>

      <el-alert type="info" show-icon :closable="false" class="helper">
        管理员操作会写入审计日志。敏感操作会弹窗二次确认。
      </el-alert>

      <section v-if="activeMenu === 'dashboard'" class="cards">
        <div class="card"><div class="k">用户总数</div><div class="v">{{ dashboard.userCount || 0 }}</div></div>
        <div class="card"><div class="k">帖子总数</div><div class="v">{{ dashboard.postCount || 0 }}</div></div>
        <div class="card"><div class="k">待处理举报</div><div class="v">{{ dashboard.pendingReportCount || 0 }}</div></div>
        <div class="card"><div class="k">反馈总数</div><div class="v">{{ dashboard.feedbackCount || 0 }}</div></div>
        <div class="card"><div class="k">待回复反馈</div><div class="v">{{ dashboard.pendingFeedbackCount || 0 }}</div></div>
      </section>

      <el-card v-else class="panel" shadow="never">
        <template #header>
          <div class="panel-hd">
            <div class="panel-title">{{ titleMap[activeMenu] }}</div>
            <div class="panel-actions">
              <el-button v-if="activeMenu === 'logs'" size="small" @click="exportLogs('admin')">导出管理员日志</el-button>
              <el-button v-if="activeMenu === 'logs'" size="small" @click="exportLogs('query')">导出查询日志</el-button>
              <el-button v-if="activeMenu === 'loginLogs'" size="small" type="primary" @click="loadLoginLogs">刷新</el-button>
              <el-button v-if="activeMenu === 'chatRooms'" size="small" type="primary" @click="loadChatRooms">查询</el-button>
              <el-button v-if="activeMenu !== 'dashboard'" size="small" @click="loadAll">刷新</el-button>
            </div>
          </div>
        </template>

        <div v-if="activeMenu === 'users'">
          <el-table :data="users" stripe size="small">
            <el-table-column prop="id" label="ID" width="70" />
            <el-table-column prop="username" label="用户名" />
            <el-table-column prop="githubUsername" label="GitHub" />
            <el-table-column prop="role" label="角色" width="90" />
            <el-table-column prop="status" label="状态" width="90" />
            <el-table-column label="操作" width="260">
              <template #default="{ row }">
                <el-button size="small" @click="toggleUser(row)">{{ row.status === 'normal' ? '冻结' : '解冻' }}</el-button>
                <el-button size="small" type="warning" plain @click="resetPassword(row)">重置密码</el-button>
                <el-button size="small" type="primary" plain :disabled="!row.githubUsername" @click="recalc(row)">重算信用</el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>

        <div v-else-if="activeMenu === 'reports'">
          <el-table :data="reports" stripe size="small">
            <el-table-column prop="id" label="ID" width="70" />
            <el-table-column prop="target" label="内容" min-width="160" show-overflow-tooltip />
            <el-table-column prop="type" label="理由" width="110" />
            <el-table-column prop="status" label="状态" width="90" />
            <el-table-column label="操作" width="230">
              <template #default="{ row }">
                <el-button size="small" :disabled="row.status !== 'pending'" @click="handleReport(row,'ignore')">忽略</el-button>
                <el-button size="small" type="warning" :disabled="row.status !== 'pending'" @click="handleReport(row,'delete')">删帖</el-button>
                <el-button size="small" type="danger" :disabled="row.status !== 'pending'" @click="handleReport(row,'ban')">封号</el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>

        <div v-else-if="activeMenu === 'feedbacks'">
          <el-table :data="feedbacks" stripe size="small">
            <el-table-column prop="id" label="ID" width="70" />
            <el-table-column prop="title" label="主题" min-width="140" />
            <el-table-column prop="type" label="类型" width="100" />
            <el-table-column prop="status" label="状态" width="100" />
            <el-table-column label="操作" width="120">
              <template #default="{ row }">
                <el-button size="small" @click="openReply(row)">回复</el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>

        <div v-else-if="activeMenu === 'logs'">
          <div class="muted" style="margin-bottom:10px;">日志列表较长，建议导出查看。</div>
          <el-table :data="adminLogs" stripe size="small">
            <el-table-column prop="adminUsername" label="管理员" width="120" />
            <el-table-column prop="actionType" label="动作" width="120" />
            <el-table-column prop="detail" label="详情" min-width="200" show-overflow-tooltip />
            <el-table-column prop="createTime" label="时间" width="160" />
          </el-table>
          <div style="margin-top:12px;"></div>
          <el-table :data="queryLogs" stripe size="small">
            <el-table-column prop="githubUsername" label="GitHub" />
            <el-table-column prop="scene" label="场景" width="110" />
            <el-table-column prop="status" label="状态" width="90" />
            <el-table-column prop="totalScore" label="分数" width="80" />
            <el-table-column prop="queryTime" label="时间" width="160" />
          </el-table>
        </div>

        <div v-else-if="activeMenu === 'loginLogs'">
          <div class="filter">
            <el-input v-model="loginFilter.username" placeholder="用户名" clearable style="width:160px" />
            <el-date-picker v-model="loginFilter.from" type="date" value-format="YYYY-MM-DD" placeholder="开始" style="width:140px" />
            <el-date-picker v-model="loginFilter.to" type="date" value-format="YYYY-MM-DD" placeholder="结束" style="width:140px" />
            <el-select v-model="loginFilter.success" placeholder="成功" clearable style="width:110px">
              <el-option label="成功" :value="1" />
              <el-option label="失败" :value="0" />
            </el-select>
            <el-button type="primary" @click="loadLoginLogs">查询</el-button>
          </div>
          <el-table :data="loginLogs" stripe size="small">
            <el-table-column prop="id" label="ID" width="70" />
            <el-table-column prop="userId" label="用户ID" width="80" />
            <el-table-column prop="username" label="用户名" width="120" />
            <el-table-column prop="ip" label="IP" min-width="100" show-overflow-tooltip />
            <el-table-column prop="success" label="成功" width="70" />
            <el-table-column prop="failReason" label="失败原因" min-width="140" show-overflow-tooltip />
            <el-table-column prop="time" label="时间" width="160" />
          </el-table>
        </div>

        <div v-else-if="activeMenu === 'chatRooms'">
          <div class="filter">
            <el-input v-model="chatRoomFilter.roomId" placeholder="roomId" clearable style="width:120px" />
            <el-input v-model="chatRoomFilter.chatNo" placeholder="群聊号" clearable style="width:140px" />
            <el-input v-model="chatRoomFilter.nameLike" placeholder="群名关键词" clearable style="width:160px" />
            <el-input v-model="chatRoomFilter.userId" placeholder="成员用户ID" clearable style="width:140px" />
            <el-select v-model="chatRoomFilter.userScope" placeholder="用户范围" clearable style="width:120px">
              <el-option label="成员包含" value="member" />
              <el-option label="仅群主" value="owner" />
            </el-select>
            <el-date-picker v-model="chatRoomFilter.createdFrom" type="date" value-format="YYYY-MM-DD" placeholder="开始" style="width:140px" />
            <el-date-picker v-model="chatRoomFilter.createdTo" type="date" value-format="YYYY-MM-DD" placeholder="结束" style="width:140px" />
            <el-input v-model="chatRoomFilter.memberCountMin" placeholder="人数≥" clearable style="width:110px" />
            <el-input v-model="chatRoomFilter.memberCountMax" placeholder="人数≤" clearable style="width:110px" />
            <el-button type="primary" @click="loadChatRooms">查询</el-button>
            <el-button @click="resetChatRoomFilter">重置</el-button>
          </div>

          <el-table :data="chatRooms" stripe size="small">
            <el-table-column prop="roomId" label="roomId" width="90" />
            <el-table-column prop="chatNo" label="群聊号" width="120" />
            <el-table-column prop="name" label="群名" min-width="150" show-overflow-tooltip />
            <el-table-column prop="createdBy" label="群主ID" width="90" />
            <el-table-column prop="createTime" label="创建时间" width="160" />
            <el-table-column prop="memberCount" label="人数" width="70" />
            <el-table-column label="操作" width="220">
              <template #default="{ row }">
                <el-button size="small" @click="openChatRoomDetail(row)">详情</el-button>
                <el-button size="small" type="danger" plain @click="forceCloseChatRoom(row)">强制关闭</el-button>
              </template>
            </el-table-column>
          </el-table>
          <div class="muted" style="margin-top:10px;">共 {{ chatRoomTotal }} 条</div>
        </div>
      </el-card>
    </main>

    <el-dialog v-model="chatRoomDetail.visible" title="群聊详情" width="860px">
      <div class="muted" style="margin-bottom:10px;">
        roomId={{ chatRoomDetail.room?.roomId }} · 群聊号={{ chatRoomDetail.room?.chatNo }} · 群名={{ chatRoomDetail.room?.name }}
      </div>
      <el-table :data="chatRoomDetail.members" stripe size="small">
        <el-table-column prop="userId" label="用户ID" width="90" />
        <el-table-column prop="username" label="用户名" width="140" />
        <el-table-column prop="nickname" label="昵称" width="140" />
        <el-table-column prop="role" label="角色" width="90" />
        <el-table-column label="操作" width="240">
          <template #default="{ row }">
            <el-button size="small" type="warning" plain :disabled="row.role === 'owner'" @click="kickMember(chatRoomDetail.room?.roomId, row)">踢人</el-button>
            <el-button size="small" type="primary" plain :disabled="row.role === 'owner'" @click="toggleAdmin(chatRoomDetail.room?.roomId, row)">
              {{ row.role === 'admin' ? '撤管理员' : '设管理员' }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <template #footer>
        <el-button @click="chatRoomDetail.visible=false">关闭</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="reply.visible" title="回复反馈" width="520px">
      <div class="muted" style="margin-bottom:8px;">{{ reply.current?.title }}</div>
      <el-input v-model="reply.content" type="textarea" :rows="5" placeholder="回复内容" />
      <template #footer>
        <el-button @click="reply.visible=false">取消</el-button>
        <el-button type="primary" @click="submitReply">发送</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { api, unwrap } from '../api'
import { auth } from '../auth'

const router = useRouter()

const menus = [
  { key: 'dashboard', label: '仪表盘' },
  { key: 'users', label: '用户管理' },
  { key: 'reports', label: '举报处理' },
  { key: 'feedbacks', label: '反馈管理' },
  { key: 'chatRooms', label: '群聊管理' },
  { key: 'logs', label: '系统日志' },
  { key: 'loginLogs', label: '登录审计' },
] as const

const titleMap: Record<string, string> = {
  dashboard: '系统仪表盘',
  users: '用户管理',
  reports: '举报处理',
  feedbacks: '反馈管理',
  chatRooms: '群聊管理',
  logs: '系统日志',
  loginLogs: '登录审计',
}

type MenuKey = (typeof menus)[number]['key']
const activeMenu = ref<MenuKey>('dashboard')

const adminName = ref('admin')
const dashboard = ref<any>({})
const users = ref<any[]>([])
const reports = ref<any[]>([])
const feedbacks = ref<any[]>([])
const adminLogs = ref<any[]>([])
const queryLogs = ref<any[]>([])

const loginLogs = ref<any[]>([])
const loginFilter = reactive<{ username: string; from: string; to: string; success: number | '' | null }>({
  username: '',
  from: '',
  to: '',
  success: null,
})

const chatRooms = ref<any[]>([])
const chatRoomTotal = ref(0)
const chatRoomFilter = reactive<any>({
  userId: '',
  userScope: 'member',
  createdFrom: '',
  createdTo: '',
  memberCountMin: '',
  memberCountMax: '',
  chatNo: '',
  roomId: '',
  nameLike: '',
  page: 1,
  size: 50,
})
const chatRoomDetail = reactive<{ visible: boolean; room: any; members: any[] }>({ visible: false, room: null, members: [] })

const reply = reactive<{ visible: boolean; current: any; content: string }>({
  visible: false,
  current: null,
  content: '',
})

const loadAll = async () => {
  if (activeMenu.value === 'chatRooms') return void loadChatRooms()
  try {
    const [d, u, r, f, a, q] = await Promise.all([
      api.get('/api/admin/dashboard'),
      api.get('/api/admin/users'),
      api.get('/api/admin/reports'),
      api.get('/api/admin/feedbacks'),
      api.get('/api/admin/logs/admin'),
      api.get('/api/admin/logs/query'),
    ])
    const body = (x: any) => x?.data
    if (Number(body(d)?.code) === 200) dashboard.value = body(d).data || {}
    if (Number(body(u)?.code) === 200) users.value = body(u).data || []
    if (Number(body(r)?.code) === 200) reports.value = body(r).data || []
    if (Number(body(f)?.code) === 200) feedbacks.value = body(f).data || []
    if (Number(body(a)?.code) === 200) adminLogs.value = body(a).data || []
    if (Number(body(q)?.code) === 200) queryLogs.value = body(q).data || []
  } catch (e: any) {
    ElMessage.error(e?.message || '加载失败')
  }
}

const resetChatRoomFilter = () => {
  chatRoomFilter.userId = ''
  chatRoomFilter.userScope = 'member'
  chatRoomFilter.createdFrom = ''
  chatRoomFilter.createdTo = ''
  chatRoomFilter.memberCountMin = ''
  chatRoomFilter.memberCountMax = ''
  chatRoomFilter.chatNo = ''
  chatRoomFilter.roomId = ''
  chatRoomFilter.nameLike = ''
  chatRoomFilter.page = 1
  chatRoomFilter.size = 50
}

const loadChatRooms = async () => {
  try {
    const params: any = { page: chatRoomFilter.page, size: chatRoomFilter.size }
    if (chatRoomFilter.userId) params.userId = Number(chatRoomFilter.userId)
    if (chatRoomFilter.userScope) params.userScope = chatRoomFilter.userScope
    if (chatRoomFilter.createdFrom) params.createdFrom = chatRoomFilter.createdFrom
    if (chatRoomFilter.createdTo) params.createdTo = chatRoomFilter.createdTo
    if (chatRoomFilter.memberCountMin) params.memberCountMin = Number(chatRoomFilter.memberCountMin)
    if (chatRoomFilter.memberCountMax) params.memberCountMax = Number(chatRoomFilter.memberCountMax)
    if (chatRoomFilter.chatNo) params.chatNo = String(chatRoomFilter.chatNo).trim()
    if (chatRoomFilter.roomId) params.roomId = Number(chatRoomFilter.roomId)
    if (chatRoomFilter.nameLike) params.nameLike = String(chatRoomFilter.nameLike).trim()
    const data = await unwrap<{ list: any[]; total: number }>(api.get('/api/admin/chat/rooms', { params }))
    chatRooms.value = data?.list || []
    chatRoomTotal.value = Number(data?.total || 0)
  } catch (e: any) {
    chatRooms.value = []
    chatRoomTotal.value = 0
    ElMessage.error(e?.message || '加载失败')
  }
}

const openChatRoomDetail = async (row: any) => {
  try {
    const data = await unwrap<any>(api.get(`/api/admin/chat/room/${row.roomId}`))
    chatRoomDetail.room = data || null
    chatRoomDetail.members = data?.members || []
    chatRoomDetail.visible = true
  } catch (e: any) {
    ElMessage.error(e?.message || '加载失败')
  }
}

const forceCloseChatRoom = async (row: any) => {
  try { await ElMessageBox.confirm(`确认强制关闭群聊「${row.name}」？此操作不可恢复。`, '强制关闭', { type: 'warning' }) } catch { return }
  try {
    await unwrap(api.post(`/api/admin/chat/room/${row.roomId}/close`))
    ElMessage.success('已关闭')
    await loadChatRooms()
  } catch (e: any) {
    ElMessage.error(e?.message || '失败')
  }
}

const kickMember = async (roomId: any, row: any) => {
  if (!roomId) return
  try { await ElMessageBox.confirm(`确认将用户「${row.username || row.userId}」踢出群聊？`, '踢人', { type: 'warning' }) } catch { return }
  try {
    await unwrap(api.post(`/api/admin/chat/room/${roomId}/members/${row.userId}/kick`))
    ElMessage.success('已踢出')
    await openChatRoomDetail({ roomId })
    await loadChatRooms()
  } catch (e: any) {
    ElMessage.error(e?.message || '失败')
  }
}

const toggleAdmin = async (roomId: any, row: any) => {
  if (!roomId) return
  const next = row.role === 'admin' ? 'member' : 'admin'
  try {
    await unwrap(api.post(`/api/admin/chat/room/${roomId}/members/${row.userId}/role`, { role: next }))
    ElMessage.success('已更新')
    await openChatRoomDetail({ roomId })
  } catch (e: any) {
    ElMessage.error(e?.message || '失败')
  }
}

const loadLoginLogs = async () => {
  try {
    const params: any = {}
    if (loginFilter.username) params.username = loginFilter.username
    if (loginFilter.from) params.from = loginFilter.from
    if (loginFilter.to) params.to = loginFilter.to
    if (loginFilter.success !== null && loginFilter.success !== '') params.success = loginFilter.success
    const data = await unwrap<{ list: any[]; total: number }>(api.get('/api/admin/loginLogs', { params }))
    loginLogs.value = data?.list || []
  } catch {
    loginLogs.value = []
  }
}

const toggleUser = async (row: any) => {
  try { await ElMessageBox.confirm('确认切换该用户状态？', '用户状态', { type: 'warning' }) } catch { return }
  try {
    await unwrap(api.post(`/api/admin/user/toggleStatus/${row.id}`))
    ElMessage.success('已更新')
    await loadAll()
  } catch (e: any) {
    ElMessage.error(e?.message || '操作失败')
  }
}

const resetPassword = async (row: any) => {
  try { await ElMessageBox.confirm(`确认重置用户「${row.username}」密码？`, '重置密码', { type: 'warning' }) } catch { return }
  try {
    await unwrap(api.post(`/api/admin/user/resetPassword/${row.id}`))
    ElMessage.success('已重置')
  } catch (e: any) {
    ElMessage.error(e?.message || '失败')
  }
}

const recalc = async (row: any) => {
  if (!row.githubUsername) return
  try { await ElMessageBox.confirm(`确认重算「${row.username}」信用？`, '重算信用', { type: 'warning' }) } catch { return }
  try {
    await unwrap(api.post('/api/admin/credit/recalculate', null, { params: { githubUsername: row.githubUsername } }))
    ElMessage.success('已触发')
  } catch (e: any) {
    ElMessage.error(e?.message || '失败')
  }
}

const handleReport = async (row: any, action: string) => {
  const map: any = { ignore: '忽略', delete: '删帖', ban: '删帖并封号' }
  try { await ElMessageBox.confirm(`确认执行「${map[action] || action}」？`, '处理举报', { type: 'warning' }) } catch { return }
  try {
    await unwrap(api.post(`/api/admin/report/handle/${row.id}`, { action, postId: row.postId, authorId: row.authorId }))
    ElMessage.success('已处理')
    await loadAll()
  } catch (e: any) {
    ElMessage.error(e?.message || '失败')
  }
}

const openReply = (row: any) => {
  reply.current = row
  reply.content = row.replyContent || ''
  reply.visible = true
}

const submitReply = async () => {
  try {
    await unwrap(api.post('/api/admin/feedback/reply', { feedbackId: reply.current.id, replyContent: reply.content, status: 'replied' }))
    ElMessage.success('已回复')
    reply.visible = false
    await loadAll()
  } catch (e: any) {
    ElMessage.error(e?.message || '失败')
  }
}

const exportLogs = async (type: string) => {
  const base = import.meta.env.VITE_API_BASE || ''
  const url = `${base}/api/admin/logs/export?type=${encodeURIComponent(type)}`
  try {
    const res = await fetch(url, { headers: { Authorization: `Bearer ${auth.token.value || ''}` } })
    if (!res.ok) throw new Error('导出失败')
    const blob = await res.blob()
    const link = document.createElement('a')
    const objectUrl = URL.createObjectURL(blob)
    link.href = objectUrl
    link.download = `logs_${type}.csv`
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    URL.revokeObjectURL(objectUrl)
  } catch {
    ElMessage.error('导出失败')
  }
}

const logout = () => {
  auth.logout()
  router.replace('/login')
}

onMounted(() => {
  adminName.value = auth.me.value?.username || 'admin'
  void loadAll()
})
</script>

<style scoped>
.layout { display: flex; min-height: calc(100vh - 56px); }
.aside { width: 220px; border-right: 1px solid #e5e7eb; background: #fafafa; padding: 14px 0; }
.logo { font-weight: 900; text-align: center; padding: 8px 12px 14px; border-bottom: 1px solid #e5e7eb; }
.menu { padding-top: 10px; }
.menu-item { padding: 10px 14px; cursor: pointer; color: #374151; }
.menu-item.active { background: #f3f4f6; font-weight: 800; color: #111827; }
.menu-item:hover { background: #f9fafb; }
.main { flex: 1; padding: 18px; min-width: 0; }
.header { display: flex; justify-content: space-between; align-items: center; gap: 12px; flex-wrap: wrap; }
.hdr-right { display: flex; align-items: center; gap: 10px; }
.helper { margin: 14px 0 16px; }
.cards { display: grid; grid-template-columns: repeat(5, minmax(0, 1fr)); gap: 12px; }
@media (max-width: 1100px) { .cards { grid-template-columns: repeat(2, minmax(0, 1fr)); } }
@media (max-width: 600px) { .cards { grid-template-columns: 1fr; } .aside { display:none; } }
.card { border: 1px solid #e5e7eb; background: #fff; border-radius: 12px; padding: 14px; }
.k { font-size: 12px; color: #6b7280; }
.v { font-weight: 900; font-size: 26px; margin-top: 6px; }
.panel { border-radius: 12px; }
.panel-hd { display: flex; justify-content: space-between; align-items: center; gap: 10px; flex-wrap: wrap; }
.panel-title { font-weight: 900; }
.panel-actions { display: flex; gap: 8px; flex-wrap: wrap; }
.filter { display: flex; gap: 8px; flex-wrap: wrap; margin-bottom: 12px; align-items: center; }
.muted { color: #6b7280; font-size: 13px; }
</style>
