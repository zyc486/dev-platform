<template>
  <div class="page">
    <div class="head">
      <div>
        <div class="title">群聊</div>
        <div class="subtitle">用于协作项目的多人沟通（最小可用：拉取会话/发消息）</div>
      </div>
      <div class="actions">
        <el-button size="small" @click="loadRooms">刷新会话</el-button>
        <el-button size="small" @click="searchVisible = true">群聊号搜索</el-button>
        <el-button size="small" type="primary" plain @click="createVisible = true">新建群聊</el-button>
      </div>
    </div>

    <el-alert
      v-if="!hasToken"
      title="当前未登录，无法查看/发送群聊消息。请先登录。"
      type="warning"
      show-icon
      :closable="false"
    />

    <div v-else class="grid">
      <el-card class="left" shadow="never" v-loading="roomsLoading">
        <template #header>
          <div class="row">
            <div style="font-weight: 900">会话</div>
            <el-tag size="small" type="info">{{ rooms.length }}</el-tag>
          </div>
        </template>

        <div v-if="rooms.length === 0" class="empty">暂无群聊。你可以点击右上角“新建群聊”。</div>

        <div v-else class="conv-list">
          <div
            v-for="r in rooms"
            :key="r.roomId"
            class="conv"
            :class="{ active: Number(r.roomId) === currentRoomId }"
            @click="openRoom(Number(r.roomId))"
          >
            <div class="avatar">
              <span class="ph">{{ String(r.name || '群').slice(0, 1).toUpperCase() }}</span>
            </div>
            <div class="meta">
              <div class="name">
                <span class="txt">{{ r.name || ('群聊#' + r.roomId) }}</span>
                <el-tag v-if="r.unread > 0" size="small" type="danger">新</el-tag>
              </div>
              <div class="sub">
                <span v-if="r.chatNo">群聊号：{{ r.chatNo }}</span>
                <span v-else>roomId：{{ r.roomId }}</span>
                <span v-if="r.role" style="margin-left:10px;">角色：{{ r.role }}</span>
              </div>
              <div class="last">
                <span v-if="r.lastFromName">{{ r.lastFromName }}：</span>{{ r.lastContent || '' }}
              </div>
            </div>
          </div>
        </div>
      </el-card>

      <el-card class="right" shadow="never" v-loading="msgsLoading">
        <template #header>
          <div class="row">
            <div style="font-weight: 900">{{ headerTitle }}</div>
            <div style="display:flex;gap:8px;align-items:center;">
              <el-button v-if="currentRoomId" size="small" @click="loadMessages">刷新</el-button>
              <el-button v-if="currentRoomId" size="small" @click="openManage">成员/管理</el-button>
              <el-button v-if="currentRoomId" size="small" type="danger" plain @click="leaveRoom">退出</el-button>
            </div>
          </div>
        </template>

        <div v-if="!currentRoomId" class="empty">请选择一个群聊会话。</div>

        <template v-else>
          <div class="msg-list">
            <div v-for="m in messages" :key="m.id" class="msg" :class="{ mine: Number(m.fromUserId) === Number(myUserId) }">
              <div class="msg-row">
                <div class="msg-avatar">
                  <img v-if="msgAvatar(m)" :src="mediaUrl(msgAvatar(m))" alt="" />
                  <span v-else class="ph">{{ msgInitial(m) }}</span>
                </div>
                <div class="msg-main">
                  <div class="msg-who">{{ msgWho(m) }}</div>
                  <div class="bubble">
                    <div class="txt">{{ m.content }}</div>
                  </div>
                  <div class="time">{{ fmtTime(m.createTime) }}</div>
                </div>
              </div>
            </div>
          </div>

          <div class="send">
            <el-input
              v-model="draft"
              type="textarea"
              :autosize="{ minRows: 2, maxRows: 5 }"
              placeholder="输入消息（回车发送，Shift+回车换行）"
              @keydown.enter.exact.prevent="doSend"
              @keydown.enter.shift.stop
            />
            <div class="send-actions">
              <div class="muted">{{ draft.length }}/2000</div>
              <el-button type="primary" :loading="sending" :disabled="!draft.trim()" @click="doSend">发送</el-button>
            </div>
          </div>
        </template>
      </el-card>
    </div>

    <el-dialog v-model="createVisible" title="新建群聊" width="520px">
      <div class="muted" style="margin-bottom:10px;">先做最小可用：创建空群聊，后续可在协作项目审核通过时自动建群并拉成员。</div>
      <el-input v-model="createName" placeholder="群聊名称（可为空）" maxlength="100" show-word-limit />
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="creating" @click="doCreate">创建</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="searchVisible" title="群聊号搜索/加入" width="560px">
      <div class="muted" style="margin-bottom:10px;">输入纯数字群聊号，搜索后可申请加入（需群主/管理员审核）。</div>
      <div style="display:flex; gap: 8px; align-items: center;">
        <el-input v-model="searchChatNo" placeholder="群聊号（纯数字）" clearable @keydown.enter.exact.prevent="doSearch" />
        <el-button type="primary" :loading="searching" @click="doSearch">搜索</el-button>
      </div>

      <div v-if="searchResult" style="margin-top:12px;">
        <el-card shadow="never" style="border-radius:12px;">
          <div style="display:flex; justify-content:space-between; gap:10px; align-items:flex-start;">
            <div>
              <div style="font-weight:900;">{{ searchResult.name || ('群聊#' + searchResult.roomId) }}</div>
              <div class="muted" style="margin-top:4px;">
                群聊号：{{ searchResult.chatNo }} · 人数：{{ searchResult.memberCount || 0 }}
              </div>
            </div>
            <div style="display:flex; gap:8px; flex-wrap:wrap; justify-content:flex-end;">
              <el-button v-if="searchResult.isMember" size="small" type="primary" @click="openRoom(Number(searchResult.roomId)); searchVisible=false">进入</el-button>
              <el-button v-else size="small" type="primary" plain :loading="applying" @click="applyJoin(searchResult)">申请加入</el-button>
            </div>
          </div>
        </el-card>
      </div>

      <template #footer>
        <el-button @click="searchVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="manageVisible" title="成员与管理" width="860px">
      <div v-if="!currentRoomId" class="muted">请先选择群聊。</div>
      <template v-else>
        <el-alert type="info" show-icon :closable="false" style="margin-bottom:10px;">
          当前身份：{{ myRoomRoleText }}<span v-if="currentChatNo"> · 群聊号：{{ currentChatNo }}</span>
        </el-alert>

        <el-card shadow="never" style="border-radius:12px;margin-bottom:12px;">
          <template #header>
            <div class="row">
              <div style="font-weight:900;">成员</div>
              <el-button size="small" @click="loadMembers">刷新成员</el-button>
            </div>
          </template>
          <el-table :data="members" stripe size="small">
            <el-table-column prop="userId" label="用户ID" width="90" />
            <el-table-column prop="username" label="用户名" width="140" />
            <el-table-column prop="nickname" label="昵称" width="140" />
            <el-table-column label="角色" width="120">
              <template #default="{ row }">
                <el-tag v-if="row.role === 'owner'" type="danger" size="small">群主</el-tag>
                <el-tag v-else-if="row.role === 'admin'" type="warning" size="small">管理员</el-tag>
                <el-tag v-else type="info" size="small">成员</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="240">
              <template #default="{ row }">
                <el-button
                  v-if="isOwner"
                  size="small"
                  type="primary"
                  plain
                  :disabled="row.role === 'owner' || Number(row.userId) === Number(myUserId)"
                  @click="toggleAdmin(row)"
                >
                  {{ row.role === 'admin' ? '撤管理员' : '设管理员' }}
                </el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>

        <el-card v-if="isManager" shadow="never" style="border-radius:12px;margin-bottom:12px;">
          <template #header>
            <div class="row">
              <div style="font-weight:900;">待处理入群申请</div>
              <el-button size="small" @click="loadApplies">刷新申请</el-button>
            </div>
          </template>
          <div v-if="applies.length === 0" class="muted">暂无待处理申请。</div>
          <el-table v-else :data="applies" stripe size="small">
            <el-table-column prop="id" label="申请ID" width="90" />
            <el-table-column prop="applicantUserId" label="申请人ID" width="100" />
            <el-table-column prop="applicantUsername" label="用户名" width="140" />
            <el-table-column prop="reason" label="理由" min-width="140" show-overflow-tooltip />
            <el-table-column label="操作" width="220">
              <template #default="{ row }">
                <el-button size="small" type="primary" @click="reviewApply(row,'approved')">同意</el-button>
                <el-button size="small" type="danger" plain @click="reviewApply(row,'rejected')">拒绝</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>

        <el-card v-if="isManager" shadow="never" style="border-radius:12px;margin-bottom:12px;">
          <template #header>
            <div class="row">
              <div style="font-weight:900;">邀请用户加入</div>
            </div>
          </template>
          <div class="muted" style="margin-bottom:8px;">最小可用：输入平台用户ID 发起邀请。</div>
          <div style="display:flex; gap:8px; align-items:center; flex-wrap:wrap;">
            <el-input v-model="inviteeUserId" placeholder="inviteeUserId（用户ID）" clearable style="width:220px" />
            <el-button type="primary" :loading="inviting" @click="doInvite">发起邀请</el-button>
          </div>
        </el-card>

        <el-card shadow="never" style="border-radius:12px;">
          <template #header>
            <div class="row">
              <div style="font-weight:900;">我的待处理邀请</div>
              <el-button size="small" @click="loadMyInvites">刷新邀请</el-button>
            </div>
          </template>
          <div v-if="myInvites.length === 0" class="muted">暂无待处理邀请。</div>
          <el-table v-else :data="myInvites" stripe size="small">
            <el-table-column prop="id" label="邀请ID" width="90" />
            <el-table-column prop="roomName" label="群名" min-width="140" show-overflow-tooltip />
            <el-table-column prop="chatNo" label="群聊号" width="120" />
            <el-table-column prop="inviterNickname" label="邀请人" width="140" />
            <el-table-column label="操作" width="220">
              <template #default="{ row }">
                <el-button size="small" type="primary" @click="respondInvite(row,'accepted')">接受</el-button>
                <el-button size="small" type="danger" plain @click="respondInvite(row,'rejected')">拒绝</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </template>

      <template #footer>
        <el-button @click="manageVisible=false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { api, unwrap } from '../api'
import { auth } from '../auth'

const route = useRoute()
const router = useRouter()

const hasToken = computed(() => !!auth.token.value)
const myUserId = computed(() => auth.me.value?.id)
const myName = computed(() => auth.me.value?.nickname || auth.me.value?.username || (myUserId.value ? `用户#${myUserId.value}` : '我'))
const myAvatar = computed(() => auth.me.value?.avatar || '')

const roomsLoading = ref(false)
const msgsLoading = ref(false)
const sending = ref(false)
const creating = ref(false)

const rooms = ref<any[]>([])
const messages = ref<any[]>([])
const draft = ref('')
const currentRoomId = ref<number | null>(null)

const createVisible = ref(false)
const createName = ref('')

const searchVisible = ref(false)
const searchChatNo = ref('')
const searching = ref(false)
const applying = ref(false)
const searchResult = ref<any>(null)

const manageVisible = ref(false)
const members = ref<any[]>([])
const applies = ref<any[]>([])
const inviteeUserId = ref('')
const inviting = ref(false)
const myInvites = ref<any[]>([])

const headerTitle = computed(() => {
  const id = currentRoomId.value
  if (!id) return '对话'
  const r = rooms.value.find((x) => Number(x.roomId) === Number(id))
  return r?.name || `群聊#${id}`
})

const myRoomRole = computed(() => {
  const id = currentRoomId.value
  if (!id) return ''
  const r = rooms.value.find((x) => Number(x.roomId) === Number(id))
  return String(r?.role || '').trim()
})
const currentChatNo = computed(() => {
  const id = currentRoomId.value
  if (!id) return ''
  const r = rooms.value.find((x) => Number(x.roomId) === Number(id))
  return String(r?.chatNo || '').trim()
})
const isOwner = computed(() => myRoomRole.value === 'owner')
const isManager = computed(() => myRoomRole.value === 'owner' || myRoomRole.value === 'admin')
const myRoomRoleText = computed(() => {
  if (isOwner.value) return '群主'
  if (myRoomRole.value === 'admin') return '管理员'
  return '成员'
})

const fmtTime = (t: any) => {
  const s = String(t || '').replace('T', ' ')
  return s ? s.slice(0, 16) : ''
}

const mediaUrl = (path: string) => {
  if (!path) return ''
  if (String(path).startsWith('http')) return String(path)
  return `${import.meta.env.VITE_API_BASE || ''}${path}`
}

const msgWho = (m: any) => {
  const mine = Number(m?.fromUserId) === Number(myUserId.value)
  if (mine) return String(myName.value || '我')
  const n = String(m?.fromName || '').trim()
  return n || `用户#${m?.fromUserId}`
}
const msgAvatar = (m: any) => {
  const mine = Number(m?.fromUserId) === Number(myUserId.value)
  return mine ? String(myAvatar.value || '') : String(m?.fromAvatar || '')
}
const msgInitial = (m: any) => {
  const who = msgWho(m)
  return (who || '?').slice(0, 1).toUpperCase()
}

const loadRooms = async () => {
  if (!hasToken.value) return
  roomsLoading.value = true
  try {
    rooms.value = (await unwrap<any[]>(api.get('/api/chat/rooms'))) || []
  } catch (e: any) {
    rooms.value = []
    ElMessage.error(e?.message || '加载会话失败')
  } finally {
    roomsLoading.value = false
  }
}

const loadMessages = async () => {
  const rid = currentRoomId.value
  if (!hasToken.value || !rid) return
  msgsLoading.value = true
  try {
    messages.value = (await unwrap<any[]>(api.get(`/api/chat/room/${rid}/messages`, { params: { limit: 300 } }))) || []
    // 阅读后刷新会话（unread 变化）
    void loadRooms()
  } catch (e: any) {
    messages.value = []
    ElMessage.error(e?.message || '加载消息失败')
  } finally {
    msgsLoading.value = false
  }
}

const openRoom = async (rid: number) => {
  currentRoomId.value = Number(rid)
  await loadMessages()
  router.replace({ path: '/chat', query: { roomId: String(rid) } })
}

const openManage = async () => {
  if (!currentRoomId.value) return
  manageVisible.value = true
  await Promise.all([loadMembers(), loadMyInvites(), isManager.value ? loadApplies() : Promise.resolve()])
}

const loadMembers = async () => {
  const rid = currentRoomId.value
  if (!hasToken.value || !rid) return
  try {
    members.value = (await unwrap<any[]>(api.get(`/api/chat/room/${rid}/members`))) || []
  } catch (e: any) {
    members.value = []
    ElMessage.error(e?.message || '加载成员失败')
  }
}

const loadApplies = async () => {
  const rid = currentRoomId.value
  if (!hasToken.value || !rid) return
  try {
    applies.value = (await unwrap<any[]>(api.get(`/api/chat/room/${rid}/applies`, { params: { status: 'pending', limit: 200 } }))) || []
  } catch (e: any) {
    applies.value = []
  }
}

const reviewApply = async (row: any, action: 'approved' | 'rejected') => {
  try {
    await unwrap(api.post(`/api/chat/apply/${row.id}/review`, null, { params: { action, reason: '' } }))
    ElMessage.success('已处理')
    await loadApplies()
    await loadMembers()
    await loadRooms()
  } catch (e: any) {
    ElMessage.error(e?.message || '操作失败')
  }
}

const doInvite = async () => {
  const rid = currentRoomId.value
  if (!hasToken.value || !rid) return
  const uid = Number(String(inviteeUserId.value || '').trim())
  if (!Number.isFinite(uid) || uid <= 0) return ElMessage.warning('请输入正确的用户ID')
  inviting.value = true
  try {
    await unwrap(api.post(`/api/chat/room/${rid}/invite`, { inviteeUserId: uid }))
    inviteeUserId.value = ''
    ElMessage.success('邀请已发送')
  } catch (e: any) {
    ElMessage.error(e?.message || '邀请失败')
  } finally {
    inviting.value = false
  }
}

const loadMyInvites = async () => {
  if (!hasToken.value) return
  try {
    myInvites.value = (await unwrap<any[]>(api.get('/api/chat/invites/my', { params: { status: 'pending', limit: 200 } }))) || []
  } catch {
    myInvites.value = []
  }
}

const respondInvite = async (row: any, action: 'accepted' | 'rejected') => {
  try {
    await unwrap(api.post(`/api/chat/invite/${row.id}/respond`, null, { params: { action } }))
    ElMessage.success('已处理')
    await loadMyInvites()
    await loadRooms()
  } catch (e: any) {
    ElMessage.error(e?.message || '操作失败')
  }
}

const toggleAdmin = async (row: any) => {
  const rid = currentRoomId.value
  if (!hasToken.value || !rid) return
  const role = row.role === 'admin' ? 'member' : 'admin'
  try {
    await unwrap(api.post(`/api/chat/room/${rid}/members/${row.userId}/role`, { role }))
    ElMessage.success('已更新')
    await loadMembers()
  } catch (e: any) {
    ElMessage.error(e?.message || '操作失败')
  }
}

const doSend = async () => {
  const rid = currentRoomId.value
  if (!hasToken.value) return router.push({ path: '/login', query: { redirect: route.fullPath } })
  if (!rid) return
  const c = draft.value.trim()
  if (!c) return
  if (c.length > 2000) return ElMessage.warning('消息过长（最多 2000 字）')

  sending.value = true
  try {
    await unwrap(api.post(`/api/chat/room/${rid}/send`, { content: c }))
    draft.value = ''
    await loadMessages()
  } catch (e: any) {
    ElMessage.error(e?.message || '发送失败')
  } finally {
    sending.value = false
  }
}

const leaveRoom = async () => {
  const rid = currentRoomId.value
  if (!rid) return
  try {
    await ElMessageBox.confirm('确认退出该群聊？', '提示', { type: 'warning' })
  } catch {
    return
  }
  try {
    await unwrap(api.post(`/api/chat/room/${rid}/leave`))
    currentRoomId.value = null
    messages.value = []
    await loadRooms()
    router.replace({ path: '/chat' })
    ElMessage.success('已退出')
  } catch (e: any) {
    ElMessage.error(e?.message || '操作失败')
  }
}

const doCreate = async () => {
  if (!hasToken.value) return
  creating.value = true
  try {
    const r = await unwrap<any>(api.post('/api/chat/room/create', { name: createName.value.trim() }))
    createVisible.value = false
    createName.value = ''
    await loadRooms()
    if (r?.roomId) await openRoom(Number(r.roomId))
    ElMessage.success('已创建')
  } catch (e: any) {
    ElMessage.error(e?.message || '创建失败')
  } finally {
    creating.value = false
  }
}

const doSearch = async () => {
  if (!hasToken.value) return
  const no = String(searchChatNo.value || '').trim()
  if (!no) return ElMessage.warning('请输入群聊号')
  searching.value = true
  try {
    searchResult.value = await unwrap<any>(api.get('/api/chat/search', { params: { chatNo: no } }))
  } catch (e: any) {
    searchResult.value = null
    ElMessage.error(e?.message || '查询失败')
  } finally {
    searching.value = false
  }
}

const applyJoin = async (r: any) => {
  const rid = Number(r?.roomId)
  if (!rid) return
  applying.value = true
  try {
    await unwrap(api.post(`/api/chat/room/${rid}/apply`, { reason: '' }))
    ElMessage.success('已提交申请，等待群主/管理员审核')
  } catch (e: any) {
    ElMessage.error(e?.message || '申请失败')
  } finally {
    applying.value = false
  }
}

const tryOpenFromQuery = async () => {
  const q = String(route.query.roomId || '').trim()
  if (!q) return
  const rid = Number(q)
  if (!Number.isFinite(rid) || rid <= 0) return
  if (!hasToken.value) return
  currentRoomId.value = rid
  await loadMessages()
  if (String(route.query.manage || '') === '1') {
    // 让用户从通知中心跳转后能直接处理申请/邀请
    await openManage()
  }
}

onMounted(async () => {
  if (!hasToken.value) return
  await loadRooms()
  await tryOpenFromQuery()
})

watch(
  () => auth.token.value,
  async () => {
    if (!hasToken.value) return
    await loadRooms()
    await tryOpenFromQuery()
  },
)

watch(
  () => route.query.roomId,
  async () => {
    if (!hasToken.value) return
    await tryOpenFromQuery()
  },
)
</script>

<style scoped>
.page { display: grid; gap: 14px; }
.head { display:flex; align-items:flex-end; justify-content:space-between; gap: 12px; flex-wrap: wrap; }
.title { font-weight: 900; font-size: 18px; }
.subtitle { color:#6b7280; font-size: 13px; margin-top: 4px; }
.actions { display:flex; gap: 8px; flex-wrap: wrap; }
.grid { display:grid; grid-template-columns: 360px 1fr; gap: 14px; }
@media (max-width: 980px) { .grid { grid-template-columns: 1fr; } }
.row { display:flex; align-items:center; justify-content:space-between; gap: 10px; }
.left, .right { border-radius: 12px; }
.empty { padding: 18px; color:#6b7280; font-size: 13px; }
.conv-list { display:flex; flex-direction: column; gap: 8px; }
.conv { display:flex; gap: 10px; padding: 10px; border:1px solid #e5e7eb; border-radius: 12px; cursor:pointer; }
.conv.active { border-color: rgba(37,99,235,.45); background: rgba(37,99,235,.06); }
.avatar { width: 34px; height: 34px; border-radius: 999px; background:#111827; display:flex; align-items:center; justify-content:center; color:#fff; font-weight:900; overflow:hidden; flex: 0 0 auto; }
.ph { font-size: 13px; }
.meta { min-width: 0; flex: 1; }
.name { display:flex; align-items:center; justify-content:space-between; gap: 8px; }
.txt { font-weight: 800; color:#111827; overflow:hidden; text-overflow: ellipsis; white-space: nowrap; }
.sub { color:#6b7280; font-size: 12px; margin-top: 2px; }
.last { color:#6b7280; font-size: 12px; overflow:hidden; text-overflow: ellipsis; white-space: nowrap; margin-top: 4px; }
.msg-list { height: 520px; overflow:auto; padding: 8px; display:flex; flex-direction: column; gap: 10px; }
.msg { display:flex; flex-direction: column; }
.msg-row { display:flex; gap: 10px; align-items:flex-start; }
.msg-avatar { width: 34px; height: 34px; border-radius: 999px; background:#f3f4f6; border: 1px solid #e5e7eb; overflow:hidden; flex: 0 0 auto; display:flex; align-items:center; justify-content:center; }
.msg-avatar img { width:100%; height:100%; object-fit: cover; }
.msg-avatar .ph { font-size: 13px; font-weight: 900; color:#374151; }
.msg-main { min-width: 0; display:flex; flex-direction: column; gap: 4px; }
.msg-who { font-size: 12px; font-weight: 800; color:#111827; }
.bubble { max-width: min(720px, 92%); border:1px solid #e5e7eb; background:#fff; border-radius: 12px; padding: 10px 12px; }
.msg.mine .bubble { background: rgba(37,99,235,.08); border-color: rgba(37,99,235,.22); }
.msg.mine .msg-row { flex-direction: row-reverse; }
.msg.mine .msg-main { align-items:flex-end; }
.msg.mine .msg-who { text-align: right; color:#111827; }
.time { font-size: 11px; color:#9ca3af; }
.send { border-top: 1px solid #e5e7eb; padding-top: 10px; display:flex; flex-direction: column; gap: 8px; }
.send-actions { display:flex; align-items:center; justify-content:space-between; }
.muted { color:#6b7280; font-size: 12px; }
</style>

