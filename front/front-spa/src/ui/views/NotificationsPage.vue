<template>
  <div class="page">
    <div class="head">
      <div>
        <div class="title">通知中心</div>
        <div class="subtitle">点赞 / 评论 / @ 提及 / 协作 / 社群审核等通知的统一入口</div>
      </div>
      <div class="actions">
        <el-button size="small" @click="load">刷新</el-button>
        <el-button size="small" type="primary" plain :disabled="msgList.length === 0" @click="markAllRead">全部已读</el-button>
        <el-button size="small" type="warning" plain :disabled="unreadCount === 0" @click="deleteUnread">删除未读</el-button>
        <el-button size="small" type="danger" plain :disabled="msgList.length === 0" @click="clearAll">清空</el-button>
      </div>
    </div>

    <el-alert
      v-if="!hasToken"
      title="当前未登录，无法加载消息。请先登录。"
      type="warning"
      show-icon
      :closable="false"
    />

    <el-card class="card">
      <template #header>
        <div class="row">
          <div style="font-weight: 800">筛选</div>
          <el-tag v-if="unreadCount > 0" size="small" type="info">未读 {{ unreadCount }}</el-tag>
        </div>
      </template>

      <div class="tabs">
        <div class="tab" :class="{ active: filterType === 'all' }" @click="filterType = 'all'">
          全部 <span v-if="unreadCount > 0" class="badge">{{ unreadCount }}</span>
        </div>
        <div class="tab" :class="{ active: filterType === 'system' }" @click="filterType = 'system'">
          系统 <span v-if="systemUnreadCount > 0" class="badge">{{ systemUnreadCount }}</span>
        </div>
        <div class="tab" :class="{ active: filterType === 'collab' }" @click="filterType = 'collab'">
          协作 <span v-if="collabUnreadCount > 0" class="badge">{{ collabUnreadCount }}</span>
        </div>
        <div class="tab" :class="{ active: filterType === 'follow' }" @click="filterType = 'follow'">
          关注 <span v-if="followUnreadCount > 0" class="badge">{{ followUnreadCount }}</span>
        </div>
        <div class="tab" :class="{ active: filterType === 'mention' }" @click="filterType = 'mention'">
          @提及 <span v-if="mentionUnreadCount > 0" class="badge">{{ mentionUnreadCount }}</span>
        </div>
        <div class="tab" :class="{ active: filterType === 'security' }" @click="filterType = 'security'">
          安全 <span v-if="securityUnreadCount > 0" class="badge">{{ securityUnreadCount }}</span>
        </div>
        <div class="tab" :class="{ active: filterType === 'unread' }" @click="filterType = 'unread'">
          未读 <span v-if="unreadCount > 0" class="badge">{{ unreadCount }}</span>
        </div>
      </div>
    </el-card>

    <el-card class="card" v-loading="loading">
      <template #header>
        <div class="row">
          <div style="font-weight: 800">消息列表</div>
        </div>
      </template>

      <div v-if="hasToken && filteredMsgList.length === 0" class="empty">暂无消息</div>

      <div v-else class="list">
        <div
          class="item"
          v-for="m in filteredMsgList"
          :key="m.id"
          :class="{ unread: !m.read }"
          @click="markReadAndNavigate(m)"
        >
          <div class="col type">
            <span v-if="!m.read" class="dot"></span>
            <span class="pill" :class="'type-' + m.type">{{ m.typeText }}</span>
          </div>
          <div class="col content">{{ m.content }}</div>
          <div class="col time">{{ m.time }}</div>
        </div>
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { api, unwrap } from '../api'
import { auth } from '../auth'
import { notificationsStore } from '../notificationsStore'

const router = useRouter()

const hasToken = computed(() => !!auth.token.value)

const loading = ref(false)
const msgList = ref<any[]>([])
const filterType = ref<'all' | 'system' | 'collab' | 'follow' | 'mention' | 'security' | 'unread'>('all')

const typeTextMap: Record<string, string> = {
  system: '系统通知',
  collab: '协作消息',
  follow: '关注通知',
  dm: '私信',
  notice: '平台公告',
  community: '社群消息',
  like: '点赞通知',
  comment: '评论通知',
  mention: '@ 提及',
  security: '安全提醒',
  chat_apply: '群聊申请',
  chat_apply_result: '群聊申请结果',
  chat_invite: '群聊邀请',
  chat_invite_result: '群聊邀请结果',
  chat_member_left: '群聊成员变动',
  chat_room_closed: '群聊关闭',
  chat_kicked: '群聊移出',
}

const unreadCount = computed(() => msgList.value.filter((i) => !i.read).length)
const systemUnreadCount = computed(() => msgList.value.filter((i) => i.type === 'system' && !i.read).length)
const collabUnreadCount = computed(() => msgList.value.filter((i) => i.type === 'collab' && !i.read).length)
const followUnreadCount = computed(() => msgList.value.filter((i) => i.type === 'follow' && !i.read).length)
const mentionUnreadCount = computed(() => msgList.value.filter((i) => i.type === 'mention' && !i.read).length)
const securityUnreadCount = computed(() => msgList.value.filter((i) => i.type === 'security' && !i.read).length)

// 顶部导航红色角标来自全局 notificationsStore；本页的未读数变化时需同步回去（否则“清空/已读”后角标会残留）。
watch(
  () => unreadCount.value,
  (n) => {
    notificationsStore.setUnread(n)
  },
  { immediate: true },
)

const filteredMsgList = computed(() => {
  if (filterType.value === 'all') return msgList.value
  if (filterType.value === 'unread') return msgList.value.filter((i) => !i.read)
  return msgList.value.filter((i) => i.type === filterType.value)
})

const load = async () => {
  if (!hasToken.value) return
  loading.value = true
  try {
    const list = await unwrap<any[]>(api.get('/api/message/list'))
    msgList.value = (list || []).map((item: any) => ({
      id: item.id,
      type: item.type || 'system',
      typeText: typeTextMap[item.type] || '系统通知',
      content: item.content,
      time: String(item.createTime || '').replace('T', ' ').slice(0, 16),
      read: item.isRead === 1,
      relatedId: item.relatedId,
    }))
  } catch (e: any) {
    ElMessage.error(e?.message || '加载失败')
  } finally {
    loading.value = false
  }
}

const markAllRead = async () => {
  if (!hasToken.value) return ElMessage.warning('请先登录')
  try {
    await unwrap(api.post('/api/message/readAll'))
    msgList.value.forEach((i) => (i.read = true))
    notificationsStore.setUnread(0)
    ElMessage.success('所有消息已标记为已读')
  } catch (e: any) {
    ElMessage.error(e?.message || '操作失败')
  }
}

const deleteUnread = async () => {
  if (!hasToken.value) return ElMessage.warning('请先登录')
  const unread = msgList.value.filter((i) => !i.read)
  if (unread.length === 0) return ElMessage.info('当前没有未读消息可删除')
  try {
    await ElMessageBox.confirm(`确认删除 ${unread.length} 条未读消息？`, '删除确认', { type: 'warning' })
  } catch {
    return
  }
  try {
    for (const item of unread) {
      await unwrap(api.delete(`/api/message/delete/${item.id}`))
    }
    ElMessage.success('未读消息已删除')
    await load()
  } catch (e: any) {
    ElMessage.error(e?.message || '删除失败')
  }
}

const clearAll = async () => {
  if (!hasToken.value) return ElMessage.warning('请先登录')
  try {
    await ElMessageBox.confirm('确定要清空所有消息吗？', '确认清空', { type: 'warning' })
  } catch {
    return
  }
  try {
    await unwrap(api.delete('/api/message/clear'))
    msgList.value = []
    notificationsStore.setUnread(0)
    ElMessage.success('所有消息已清空')
  } catch (e: any) {
    ElMessage.error(e?.message || '清空失败')
  }
}

const markReadAndNavigate = async (m: any) => {
  if (!m.read) {
    try {
      await unwrap(api.post(`/api/message/read/${m.id}`))
      const idx = msgList.value.findIndex((x) => x.id === m.id)
      if (idx >= 0) msgList.value[idx].read = true
    } catch {}
  }
  // 跳转策略：尽量把用户带到能“闭环验证”的页面
  if (m.type === 'collab') {
    router.push('/projects')
    return
  }
  if (m.type === 'mention' || m.type === 'comment' || m.type === 'like' || m.type === 'community') {
    router.push('/community')
    return
  }
  if (m.type === 'dm') {
    const withUserId = m.relatedId
    if (withUserId) {
      router.push({ path: '/dm', query: { withUserId: String(withUserId) } })
    } else {
      router.push('/dm')
    }
    return
  }
  if (String(m.type || '').startsWith('chat_')) {
    const roomId = m.relatedId
    if (roomId) {
      router.push({ path: '/chat', query: { roomId: String(roomId), manage: '1' } })
    } else {
      router.push('/chat')
    }
    return
  }
}

onMounted(load)

watch(
  () => notificationsStore.changedTick.value,
  () => {
    if (hasToken.value) void load()
  },
)
</script>

<style scoped>
.page { display: grid; gap: 14px; }
.head { display:flex; align-items:flex-start; justify-content:space-between; gap: 10px; flex-wrap: wrap; }
.title { font-weight: 900; font-size: 18px; }
.subtitle { margin-top: 4px; color: #6b7280; font-size: 13px; }
.actions { display:flex; gap: 8px; flex-wrap: wrap; }
.card { border-radius: 12px; }
.row { display:flex; align-items:center; justify-content:space-between; gap: 10px; }
.tabs { display:flex; gap: 8px; flex-wrap: wrap; }
.tab { padding: 7px 14px; border: 1px solid #e5e7eb; border-radius: 999px; cursor:pointer; font-size: 13px; background: #f8fafc; display:flex; align-items:center; gap: 6px; }
.tab.active { border-color:#2563eb; color:#2563eb; background:#eff6ff; font-weight: 800; }
.badge { background: #ef4444; color:#fff; font-size: 12px; padding: 1px 6px; border-radius: 999px; }
.empty { text-align:center; padding: 26px; color:#6b7280; font-size: 13px; }
.list { display:flex; flex-direction: column; }
.item { display:flex; gap: 12px; padding: 14px 12px; border-bottom: 1px solid #e5e7eb; cursor: pointer; align-items: center; }
.item:hover { background: #f8fafc; }
.item.unread { background: #f8fafc; }
.col { flex: 1; min-width: 0; }
.col.content { flex: 3; color: #111827; font-size: 14px; line-height: 1.5; }
.col.time { flex: 1; text-align: right; color:#6b7280; font-size: 12px; }
.type { display:flex; align-items:center; gap: 8px; }
.dot { width: 8px; height: 8px; border-radius: 999px; background: #2563eb; flex-shrink:0; }
.pill { padding: 2px 10px; border-radius: 999px; border: 1px solid #e5e7eb; background: #f8fafc; font-size: 12px; color:#374151; }
.type-system { color:#2563eb; border-color: rgba(37,99,235,.22); background: rgba(37,99,235,.06); }
.type-collab { color:#16a34a; border-color: rgba(22,163,74,.22); background: rgba(22,163,74,.06); }
.type-follow { color:#d97706; border-color: rgba(217,119,6,.22); background: rgba(217,119,6,.06); }
.type-dm { color:#2563eb; border-color: rgba(37,99,235,.22); background: rgba(37,99,235,.06); }
.type-mention { color:#7c3aed; border-color: rgba(124,58,237,.22); background: rgba(124,58,237,.06); }
.type-security { color:#dc2626; border-color: rgba(220,38,38,.22); background: rgba(220,38,38,.06); }
.type-like { color:#0ea5e9; border-color: rgba(14,165,233,.22); background: rgba(14,165,233,.06); }
.type-comment { color:#0891b2; border-color: rgba(8,145,178,.22); background: rgba(8,145,178,.06); }
.type-community { color:#6b7280; border-color: rgba(107,114,128,.22); background: rgba(107,114,128,.06); }
</style>

