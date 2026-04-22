<template>
  <div class="page">
    <div class="head">
      <div>
        <div class="title">私信</div>
        <div class="subtitle">与其他开发者进行一对一交流（文字/链接/代码片段）</div>
      </div>
      <div class="actions">
        <el-button size="small" @click="loadConversations">刷新会话</el-button>
      </div>
    </div>

    <el-alert
      v-if="!hasToken"
      title="当前未登录，无法发送/查看私信。请先登录。"
      type="warning"
      show-icon
      :closable="false"
    />

    <div v-else class="grid">
      <el-card class="left" shadow="never" v-loading="convLoading">
        <template #header>
          <div class="row">
            <div style="font-weight: 900">会话</div>
            <el-tag size="small" type="info">{{ conversations.length }}</el-tag>
          </div>
        </template>

        <div v-if="conversations.length === 0" class="empty">暂无会话。你可以从用户主页点击“私信”开始聊天。</div>

        <div v-else class="conv-list">
          <div
            v-for="c in conversations"
            :key="c.withUserId"
            class="conv"
            :class="{ active: c.withUserId === currentWithUserId }"
            @click="openConversation(c.withUserId)"
          >
            <div class="avatar">
              <img v-if="c.withAvatar" :src="mediaUrl(c.withAvatar)" alt="" />
              <span v-else class="ph">{{ (c.withNickname || c.withUsername || '?').slice(0, 1).toUpperCase() }}</span>
            </div>
            <div class="meta">
              <div class="name">
                <span class="txt">{{ c.withNickname || c.withUsername || ('用户#' + c.withUserId) }}</span>
                <el-tag v-if="c.unread > 0" size="small" type="danger">{{ c.unread }}</el-tag>
              </div>
              <div class="last">{{ c.lastContent || '' }}</div>
            </div>
          </div>
        </div>
      </el-card>

      <el-card class="right" shadow="never" v-loading="msgLoading">
        <template #header>
          <div class="row">
            <div style="font-weight: 900">
              {{ headerName }}
            </div>
            <el-button v-if="currentWithUserId" size="small" @click="loadMessages">刷新</el-button>
          </div>
        </template>

        <div v-if="!currentWithUserId" class="empty">
          请选择一个会话，或从用户主页发起私信。
        </div>

        <template v-else>
          <div class="msg-list">
            <div v-for="m in messages" :key="m.id" class="msg" :class="{ mine: m.fromUserId === myUserId }">
              <div class="msg-row">
                <div class="msg-avatar">
                  <img v-if="msgAvatar(m)" :src="mediaUrl(msgAvatar(m))" alt="" />
                  <span v-else class="ph">{{ msgInitial(m) }}</span>
                </div>
                <div class="msg-main">
                  <div class="msg-who">{{ msgWho(m) }}</div>
                  <div class="bubble">{{ m.content }}</div>
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

const hasToken = computed(() => !!auth.token.value)
const myUserId = computed(() => auth.me.value?.id)
const myName = computed(() => auth.me.value?.nickname || auth.me.value?.username || (myUserId.value ? `用户#${myUserId.value}` : '我'))
const myAvatar = computed(() => auth.me.value?.avatar || '')

const convLoading = ref(false)
const msgLoading = ref(false)
const sending = ref(false)

const conversations = ref<any[]>([])
const messages = ref<any[]>([])
const draft = ref('')
const currentWithUserId = ref<number | null>(null)

const headerName = computed(() => {
  const id = currentWithUserId.value
  if (!id) return '对话'
  const c = conversations.value.find((x) => Number(x.withUserId) === Number(id))
  return c?.withNickname || c?.withUsername || `用户#${id}`
})

const mediaUrl = (path: string) => {
  if (!path) return ''
  if (path.startsWith('http')) return path
  return `${import.meta.env.VITE_API_BASE || ''}${path}`
}

const fmtTime = (t: any) => {
  const s = String(t || '').replace('T', ' ')
  return s ? s.slice(0, 16) : ''
}

const currentPeer = computed(() => {
  const id = currentWithUserId.value
  if (!id) return null
  return conversations.value.find((x) => Number(x.withUserId) === Number(id)) || null
})

const msgWho = (m: any) => {
  const mine = Number(m?.fromUserId) === Number(myUserId.value)
  if (mine) return String(myName.value || '我')
  const c = currentPeer.value
  return String(c?.withNickname || c?.withUsername || (c?.withUserId ? `用户#${c.withUserId}` : '对方'))
}
const msgAvatar = (m: any) => {
  const mine = Number(m?.fromUserId) === Number(myUserId.value)
  if (mine) return String(myAvatar.value || '')
  const c = currentPeer.value
  return String(c?.withAvatar || '')
}
const msgInitial = (m: any) => {
  const who = msgWho(m)
  return (who || '?').slice(0, 1).toUpperCase()
}

const loadConversations = async () => {
  if (!hasToken.value) return
  convLoading.value = true
  try {
    conversations.value = (await unwrap<any[]>(api.get('/api/dm/conversations'))) || []
  } catch (e: any) {
    ElMessage.error(e?.message || '加载会话失败')
    conversations.value = []
  } finally {
    convLoading.value = false
  }
}

const loadMessages = async () => {
  const id = currentWithUserId.value
  if (!hasToken.value || !id) return
  msgLoading.value = true
  try {
    messages.value = (await unwrap<any[]>(api.get('/api/dm/list', { params: { withUserId: id, limit: 300 } }))) || []
    // 阅读会话后刷新会话列表（未读数会变化）
    void loadConversations()
  } catch (e: any) {
    ElMessage.error(e?.message || '加载消息失败')
    messages.value = []
  } finally {
    msgLoading.value = false
  }
}

const openConversation = async (withUserId: number) => {
  currentWithUserId.value = Number(withUserId)
  await loadMessages()
  router.replace({ path: '/dm', query: { withUserId: String(withUserId) } })
}

const doSend = async () => {
  const id = currentWithUserId.value
  if (!hasToken.value) return router.push({ path: '/login', query: { redirect: route.fullPath } })
  if (!id) return
  const c = draft.value.trim()
  if (!c) return
  if (c.length > 2000) return ElMessage.warning('消息过长（最多 2000 字）')

  sending.value = true
  try {
    await unwrap(api.post('/api/dm/send', { toUserId: id, content: c }))
    draft.value = ''
    await loadMessages()
  } catch (e: any) {
    ElMessage.error(e?.message || '发送失败')
  } finally {
    sending.value = false
  }
}

const tryOpenFromQuery = async () => {
  const q = String(route.query.withUserId || '').trim()
  if (!q) return
  const id = Number(q)
  if (!Number.isFinite(id) || id <= 0) return
  if (!hasToken.value) return
  // 若会话列表里没有该用户，也允许直接打开（消息列表接口可正常返回空）
  currentWithUserId.value = id
  await loadMessages()
}

onMounted(async () => {
  if (!hasToken.value) return
  await loadConversations()
  await tryOpenFromQuery()
})

watch(
  () => auth.token.value,
  async () => {
    if (!hasToken.value) return
    await loadConversations()
    await tryOpenFromQuery()
  },
)

watch(
  () => route.query.withUserId,
  async () => {
    if (!hasToken.value) return
    await tryOpenFromQuery()
  },
)
</script>

<style scoped>
.page { display: grid; gap: 12px; }
.head { display:flex; align-items:flex-start; justify-content:space-between; gap: 10px; flex-wrap: wrap; }
.title { font-weight: 900; font-size: 18px; }
.subtitle { margin-top: 4px; color: #6b7280; font-size: 13px; }
.actions { display:flex; gap: 8px; flex-wrap: wrap; }
.grid { display:grid; grid-template-columns: 320px 1fr; gap: 12px; align-items: start; }
.left, .right { border-radius: 12px; border: 1px solid #e5e7eb; }
.row { display:flex; align-items:center; justify-content:space-between; gap: 10px; }
.empty { padding: 18px; color:#6b7280; font-size: 13px; }
.conv-list { display:flex; flex-direction: column; }
.conv { display:flex; gap: 10px; padding: 10px 12px; border-bottom: 1px solid #f1f5f9; cursor: pointer; }
.conv:hover { background: #f8fafc; }
.conv.active { background: #eff6ff; }
.avatar { width: 36px; height: 36px; border-radius: 999px; overflow:hidden; background:#f3f4f6; border: 1px solid #e5e7eb; display:flex; align-items:center; justify-content:center; flex-shrink: 0; }
.avatar img { width:100%; height:100%; object-fit: cover; }
.ph { font-size: 14px; font-weight: 900; color:#374151; }
.meta { min-width: 0; flex: 1; }
.name { display:flex; align-items:center; justify-content:space-between; gap: 8px; }
.txt { font-weight: 800; font-size: 13px; color:#111827; overflow:hidden; text-overflow: ellipsis; white-space: nowrap; }
.last { margin-top: 4px; font-size: 12px; color:#6b7280; overflow:hidden; text-overflow: ellipsis; white-space: nowrap; }
.msg-list { display:flex; flex-direction: column; gap: 10px; padding: 12px; max-height: 56vh; overflow:auto; }
.msg { display:flex; flex-direction: column; }
.msg-row { display:flex; gap: 10px; align-items:flex-start; }
.msg-avatar { width: 34px; height: 34px; border-radius: 999px; overflow:hidden; background:#f3f4f6; border: 1px solid #e5e7eb; display:flex; align-items:center; justify-content:center; flex: 0 0 auto; }
.msg-avatar img { width:100%; height:100%; object-fit: cover; }
.msg-avatar .ph { font-size: 13px; font-weight: 900; color:#374151; }
.msg-main { min-width: 0; display:flex; flex-direction: column; gap: 4px; }
.msg-who { font-size: 12px; font-weight: 800; color:#111827; }
.msg.mine .msg-row { flex-direction: row-reverse; }
.msg.mine .msg-main { align-items:flex-end; }
.bubble { max-width: min(620px, 90%); padding: 10px 12px; border-radius: 12px; background: #f8fafc; border: 1px solid #e5e7eb; color:#111827; white-space: pre-wrap; line-height: 1.6; font-size: 13px; }
.msg.mine .bubble { background: #eff6ff; border-color: rgba(37,99,235,.22); }
.time { margin-top: 4px; font-size: 11px; color:#9ca3af; }
.send { border-top: 1px solid #e5e7eb; padding: 12px; display:flex; flex-direction: column; gap: 10px; }
.send-actions { display:flex; align-items:center; justify-content:space-between; gap: 10px; }
.muted { color:#6b7280; font-size: 12px; }
@media (max-width: 900px) {
  .grid { grid-template-columns: 1fr; }
}
</style>

