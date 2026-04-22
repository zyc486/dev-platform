<template>
  <div class="page">
    <div class="head">
      <div>
        <div class="title">开发者社区</div>
        <div class="subtitle">广场动态 + 社群讨论 · 支持评论楼中楼与 @ 提及</div>
      </div>
      <div class="actions">
        <el-button size="small" @click="refreshAll">刷新</el-button>
        <el-button v-if="hasToken" size="small" type="primary" @click="openCreate">创建社群</el-button>
        <el-button v-if="hasToken" size="small" type="success" plain @click="openSquarePublish">广场发动态</el-button>
        <el-button
          v-if="hasToken && viewMode === 'community' && selected"
          size="small"
          type="primary"
          plain
          @click="openPublish"
        >发社群帖</el-button>
      </div>
    </div>

    <el-alert
      title="广场无需登录即可浏览；社群帖子同样可浏览。仅发帖/入群申请/管理操作需要登录与权限。"
      type="info"
      show-icon
      :closable="false"
    />

    <div class="grid">
      <el-card class="left">
        <template #header>
          <div class="row">
            <div style="font-weight: 800">入口</div>
            <el-tag size="small" type="info">{{ viewMode === 'square' ? '广场' : '社群' }}</el-tag>
          </div>
        </template>

        <div class="entry" :class="{ active: viewMode === 'square' }" @click="selectSquare">
          <div class="entry-title">全站广场</div>
          <div class="entry-sub">全体可见的历史动态</div>
        </div>

        <div class="sep-title">社群列表</div>
        <div class="row" style="gap: 10px; margin-bottom: 10px">
          <el-input
            v-model="tagFilter"
            size="small"
            placeholder="按技术标签筛选"
            clearable
            @keyup.enter="loadCommunities"
          />
          <el-button size="small" type="primary" @click="loadCommunities">刷新</el-button>
        </div>

        <div v-if="communities.length === 0" class="empty">暂无社群，可点击上方「创建社群」</div>
        <div
          v-for="c in communities"
          :key="c.id"
          class="comm"
          :class="{ active: viewMode === 'community' && selected && selected.id === c.id }"
          @click="selectCommunity(c)"
        >
          <div class="comm-title">{{ c.name }}</div>
          <div class="comm-sub">成员 {{ c.memberCount || 0 }} · {{ c.techTags || '未标注' }}</div>
        </div>
      </el-card>

      <el-card class="right" v-if="viewMode === 'square'">
        <template #header>
          <div class="row">
            <div style="font-weight: 800">广场动态</div>
            <div class="row" style="gap: 8px">
              <el-segmented
                v-model="squareSort"
                :options="[
                  { label: '最新', value: 'new' },
                  { label: '热门', value: 'hot' },
                ]"
                size="small"
                @change="loadSquarePosts"
              />
            </div>
          </div>
        </template>

        <div class="tagbar">
          <div class="tagbar-label">热门标签</div>
          <el-tag
            size="small"
            :effect="activeTag === '' ? 'dark' : 'plain'"
            style="cursor: pointer"
            @click="setActiveTag('')"
          >全部</el-tag>
          <el-tag
            v-for="t in hotTagList"
            :key="t.tag"
            size="small"
            type="info"
            :effect="activeTag === t.tag ? 'dark' : 'plain'"
            style="cursor: pointer"
            @click="setActiveTag(t.tag)"
          >{{ t.tag }} · {{ t.cnt }}</el-tag>
          <span v-if="hotTagList.length === 0" class="muted">（暂无标签，发帖时可填写标签来聚合）</span>
        </div>

        <div v-if="squarePosts.length === 0" class="empty">暂无动态，登录后可点击「广场发动态」</div>
        <div v-else class="post" v-for="p in squarePosts" :key="'sq-' + p.id">
          <div class="post-title">
            <span class="post-title-text">{{ p.title || '（无标题）' }}</span>
            <el-tag
              v-for="tg in splitTags(p.tags)"
              :key="tg"
              size="small"
              type="info"
              style="cursor: pointer"
              @click.stop="setActiveTag(tg)"
            >{{ tg }}</el-tag>
          </div>
          <div class="post-meta">
            {{ p.username || '用户' }} · {{ p.time }} · 赞 {{ p.likeCount ?? 0 }} · 收藏 {{ p.collectCount ?? 0 }} · 评论
            {{ p.commentCount ?? 0 }}
            <span v-if="squareSort === 'hot'" class="hot">🔥 {{ p.hotScore ?? 0 }}</span>
          </div>
          <div class="post-body">{{ p.content }}</div>
          <div class="post-actions">
            <el-button size="small" link type="primary" @click="openComments(p)">💬 评论 / 楼中楼</el-button>
            <el-button size="small" link type="success" :disabled="!hasToken" @click="likePost(p)">👍 点赞</el-button>
            <el-button size="small" link type="warning" :disabled="!hasToken" @click="collectPost(p)">⭐ 收藏</el-button>
            <el-button size="small" link type="danger" :disabled="!hasToken" @click="openReport(p)">🚩 举报</el-button>
          </div>
        </div>
      </el-card>

      <el-card class="right" v-else-if="viewMode === 'community' && selected">
        <template #header>
          <div class="row">
            <div style="font-weight: 800">社群</div>
            <div class="row" style="gap: 8px">
              <el-tag v-if="isMember" type="success" size="small">已是成员</el-tag>
              <el-button v-if="hasToken && !isMember" size="small" type="warning" @click="openApply">申请加入</el-button>
            </div>
          </div>
        </template>

        <div class="comm-hd">
          <div class="comm-hd-title">{{ selected.name }}</div>
          <div class="comm-hd-desc">{{ selected.description || '暂无简介' }}</div>
        </div>

        <div class="summary-grid">
          <div class="summary-card">
            <div class="summary-title">成员概览</div>
            <div v-if="members.length" class="summary-list">
              <div class="summary-item" v-for="m in members.slice(0, 8)" :key="'m-' + m.id">
                <div class="summary-item-title">
                  {{ m.nickname || m.username || ('用户' + m.userId) }}
                  <span class="muted" style="margin-left: 8px;">#{{ m.userId }}</span>
                </div>
                <div class="summary-item-meta">
                  角色：{{ memberRoleLabel(m.role) }} · 加入时间：{{ formatTime(m.joinTime) || '—' }}
                </div>
                <div class="summary-item-meta">
                  信用：{{ m.totalScore ?? '—' }} · {{ m.level || '—' }}
                </div>
              </div>
            </div>
            <div v-else class="empty" style="padding: 18px 0">暂无成员信息</div>
          </div>

          <div class="summary-card" v-if="canModerate">
            <div class="summary-title">待审核入群申请</div>
            <div v-if="pendingApplies.length" class="summary-list">
              <div class="summary-item" v-for="a in pendingApplies" :key="'a-' + a.id">
                <div class="summary-item-title">申请人 ID：{{ a.userId }}</div>
                <div class="summary-item-meta">申请时间：{{ formatTime(a.createTime) || '—' }}</div>
                <div class="summary-item-meta">申请理由：{{ a.applyReason || '未填写' }}</div>
                <div class="summary-item-actions">
                  <el-button size="small" type="success" @click="reviewCommunityApply(a, 'approved')">通过</el-button>
                  <el-button size="small" type="danger" @click="reviewCommunityApply(a, 'rejected')">拒绝</el-button>
                </div>
              </div>
            </div>
            <div v-else class="empty" style="padding: 18px 0">暂无待审核申请</div>
          </div>
        </div>

        <div class="tabs">
          <div class="tab" :class="{ active: postTab === 'all' }" @click="setPostTab('all')">全部</div>
          <div class="tab" :class="{ active: postTab === 'announcement' }" @click="setPostTab('announcement')">公告</div>
          <div class="tab" :class="{ active: postTab === 'discussion' }" @click="setPostTab('discussion')">讨论</div>
          <div class="tab" :class="{ active: postTab === 'share' }" @click="setPostTab('share')">分享</div>
          <div class="tab" :class="{ active: postTab === 'question' }" @click="setPostTab('question')">问答</div>
          <div class="tab" :class="{ active: postTab === 'resource' }" @click="setPostTab('resource')">资源</div>
        </div>

        <div v-if="posts.length === 0" class="empty">该分类下暂无帖子</div>
        <div v-else class="post" v-for="p in posts" :key="'cp-' + p.id">
          <div class="post-title">
            <span v-if="p.isSticky === 1" class="badge sticky">置顶</span>
            <span v-if="p.isEssence === 1" class="badge essence">精华</span>
            <span class="post-title-text">{{ p.title || '（无标题）' }}</span>
          </div>
          <div class="post-meta">
            {{ p.type === 'announcement' ? '公告' : '帖子' }} · {{ categoryLabel(p.category) }} ·
            <span
              class="link"
              role="button"
              tabindex="0"
              @click="openUserProfile(p.authorUsername)"
              @keydown.enter.prevent="openUserProfile(p.authorUsername)"
              @keydown.space.prevent="openUserProfile(p.authorUsername)"
            >{{ p.authorUsername || ('用户' + p.userId) }}</span>
            · {{ formatTime(p.createTime) }}
          </div>
          <div class="post-body">{{ p.content }}</div>
          <div class="post-actions">
            <el-button size="small" @click="openAttachments(p)">附件</el-button>
            <el-button v-if="canModerate" size="small" @click="toggleSticky(p)">{{ p.isSticky === 1 ? '取消置顶' : '置顶' }}</el-button>
            <el-button v-if="canModerate" size="small" type="warning" plain @click="toggleEssence(p)">{{ p.isEssence === 1 ? '取消精华' : '加精' }}</el-button>
          </div>
        </div>
      </el-card>

      <el-card v-else class="right">
        <div class="empty">请从左侧选择一个社群，或点击「全站广场」查看全体动态</div>
      </el-card>
    </div>

    <!-- 楼中楼评论 -->
    <el-dialog v-model="showComments" :title="commentPost ? ('评论 · ' + (commentPost.title || '无标题')) : '评论'" width="680px">
      <div v-if="commentPost" class="muted" style="margin-bottom: 10px">
        {{ commentPost.username }} · {{ commentPost.time }}
      </div>
      <div class="comments">
        <div v-if="commentTree.length === 0" class="empty" style="padding: 18px">暂无评论，快来抢沙发</div>
        <div v-for="c in commentTree" :key="'c-' + c.id" class="c-root">
          <div class="c-row">
            <div class="c-avatar">{{ (c.user || '？').substring(0, 1) }}</div>
            <div class="c-main">
              <div class="c-meta"><b>{{ c.user || '用户' }}</b> <span class="muted" style="margin-left: 8px">{{ c.time }}</span></div>
              <div class="c-text">{{ c.text }}</div>
              <el-button size="small" link type="primary" @click="startReply(c)">回复</el-button>
            </div>
          </div>
          <div v-if="c.children && c.children.length" class="c-children">
            <div v-for="cc in c.children" :key="'cc-' + cc.id" class="c-child">
              <div class="c-meta">
                <b>{{ cc.user || '用户' }}</b>
                <span v-if="cc.replyTo" class="muted">回复 @{{ cc.replyTo }}</span>
                <span class="muted" style="margin-left: 8px">{{ cc.time }}</span>
              </div>
              <div class="c-text">{{ cc.text }}</div>
              <el-button size="small" link type="primary" @click="startReply(cc)">回复</el-button>
            </div>
          </div>
        </div>
      </div>

      <div class="comment-box">
        <div v-if="replyTarget" class="replying">
          正在回复 <b>@{{ replyTarget.user }}</b>：{{ preview(replyTarget.text, 30) }}
          <el-button size="small" link @click="cancelReply">取消</el-button>
        </div>
        <el-input
          v-model="commentInput"
          type="textarea"
          :rows="3"
          :placeholder="replyTarget ? ('回复 @' + replyTarget.user + '（支持 @用户名 提及他人）') : '发表评论（支持 @用户名 提及他人）'"
        />
        <div style="margin-top: 8px; text-align: right">
          <el-button type="primary" @click="submitComment">发送</el-button>
        </div>
      </div>
    </el-dialog>

    <!-- 举报 -->
    <el-dialog v-model="showReport" title="举报帖子" width="460px">
      <div class="muted" style="margin-bottom: 8px" v-if="reportPost">帖子：{{ reportPost.title || '（无标题）' }}</div>
      <el-input v-model="reportReason" type="textarea" :rows="4" placeholder="请输入举报理由（必填）" />
      <template #footer>
        <el-button @click="showReport = false">取消</el-button>
        <el-button type="danger" :loading="reporting" @click="submitReport">提交举报</el-button>
      </template>
    </el-dialog>

    <!-- 申请加入 -->
    <el-dialog v-model="showApply" title="申请加入社群" width="420px">
      <el-input v-model="applyReason" type="textarea" :rows="4" placeholder="简要说明申请理由" />
      <template #footer>
        <el-button @click="showApply = false">取消</el-button>
        <el-button type="primary" :loading="applying" @click="submitApply">提交</el-button>
      </template>
    </el-dialog>

    <!-- 创建社群 -->
    <el-dialog v-model="showCreate" title="创建社群" width="520px">
      <el-form label-width="88px">
        <el-form-item label="名称"><el-input v-model="createForm.name" /></el-form-item>
        <el-form-item label="简介"><el-input v-model="createForm.description" type="textarea" :rows="3" /></el-form-item>
        <el-form-item label="技术标签"><el-input v-model="createForm.techTags" placeholder="如 Java,Spring" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCreate = false">取消</el-button>
        <el-button type="primary" :loading="creating" @click="submitCreate">创建</el-button>
      </template>
    </el-dialog>

    <!-- 广场发动态 -->
    <el-dialog v-model="showSquarePublish" title="发布广场动态" width="560px">
      <el-form label-width="72px">
        <el-form-item label="标题"><el-input v-model="squarePublishForm.title" /></el-form-item>
        <el-form-item label="标签"><el-input v-model="squarePublishForm.tags" placeholder="英文逗号分隔，例如 Java,SpringBoot,AI" /></el-form-item>
        <el-form-item label="正文">
          <el-input v-model="squarePublishForm.content" type="textarea" :rows="6" placeholder="可使用 @用户名 提及他人，对方会在消息中心收到通知" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showSquarePublish = false">取消</el-button>
        <el-button type="primary" :loading="publishingSquare" @click="submitSquarePublish">发布</el-button>
      </template>
    </el-dialog>

    <!-- 社群发帖 -->
    <el-dialog v-model="showPublish" title="发布社群帖子" width="560px">
      <el-form label-width="96px">
        <el-form-item label="类型" v-if="canModerate">
          <el-radio-group v-model="publishForm.type">
            <el-radio label="post">普通帖</el-radio>
            <el-radio label="announcement">公告</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="分类" v-if="publishForm.type === 'post'">
          <el-select v-model="publishForm.category" style="width: 100%">
            <el-option label="讨论" value="discussion" />
            <el-option label="分享" value="share" />
            <el-option label="问答" value="question" />
            <el-option label="资源" value="resource" />
          </el-select>
        </el-form-item>
        <el-form-item label="标题"><el-input v-model="publishForm.title" /></el-form-item>
        <el-form-item label="正文"><el-input v-model="publishForm.content" type="textarea" :rows="6" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showPublish = false">取消</el-button>
        <el-button type="primary" :loading="publishing" @click="submitPublish">发布</el-button>
      </template>
    </el-dialog>

    <!-- 社群附件 -->
    <el-dialog v-model="showAttachments" :title="attachPost ? ('附件 · ' + (attachPost.title || '无标题')) : '附件'" width="560px">
      <div v-loading="attachmentsLoading">
        <div v-if="attachments.length === 0" class="empty" style="padding: 18px">暂无附件</div>
        <div v-else class="list-block">
          <div v-for="a in attachments" :key="'att-' + a.id" class="list-row">
            <div>
              <div class="list-title">{{ a.originalName }}</div>
              <div class="list-sub">{{ formatTime(a.createTime) }} · {{ prettySize(a.sizeBytes) }}</div>
            </div>
            <el-button size="small" type="primary" plain @click="downloadAttachment(a)">下载</el-button>
          </div>
        </div>
        <el-divider />
        <div class="muted" style="margin-bottom: 8px;">上传附件（20MB 以内）：仅帖子作者或社群管理员可上传。</div>
        <el-upload
          v-if="hasToken && attachPost"
          action="#"
          :show-file-list="false"
          :http-request="uploadAttachmentRequest"
        >
          <el-button type="primary" :loading="uploadingAttachment">上传文件</el-button>
        </el-upload>
        <el-alert
          v-else
          title="请先登录后再上传附件"
          type="warning"
          show-icon
          :closable="false"
        />
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { api, unwrap } from '../api'
import { auth } from '../auth'

const router = useRouter()

const hasToken = computed(() => !!auth.token.value)

const currentUser = computed(() => auth.me.value)
const currentUserId = computed<number | null>(() => {
  const u = currentUser.value
  return u && u.id != null ? Number(u.id) : null
})
const platformAdmin = computed(() => currentUser.value && currentUser.value.role === 'admin')

const communities = ref<any[]>([])
const viewMode = ref<'square' | 'community'>('square')
const selected = ref<any>(null)
const members = ref<any[]>([])
const pendingApplies = ref<any[]>([])
const posts = ref<any[]>([])

const tagFilter = ref('')
const postTab = ref('all')
const isMember = ref(false)

const squarePosts = ref<any[]>([])
const squareSort = ref<'new' | 'hot'>('new')
const activeTag = ref('')
const hotTagList = ref<any[]>([])

const canModerate = computed(() => {
  if (viewMode.value !== 'community' || !selected.value || !currentUserId.value) return false
  if (platformAdmin.value) return true
  const c = selected.value
  if (Number(c.creatorId) === currentUserId.value) return true
  const m = members.value.find((x) => Number(x.userId) === currentUserId.value)
  return !!(m && (m.role === 'admin' || m.role === 'creator'))
})

// -------------------- util --------------------
const preview = (s: string, n: number) => {
  const t = String(s || '')
  return t.length > n ? t.slice(0, n) + '…' : t
}
const splitTags = (tags: any) => {
  if (!tags) return []
  return String(tags)
    .split(',')
    .map((s) => s.trim())
    .filter(Boolean)
}
const formatTime = (t: any) => (t ? String(t).replace('T', ' ').slice(0, 16) : '')
const categoryLabel = (c: any) => ({ discussion: '讨论', share: '分享', question: '问答', resource: '资源' }[c] || (c || '未分类'))
const memberRoleLabel = (role: any) => ({ creator: '创建者', admin: '管理员', member: '成员' }[role] || (role || '成员'))
const prettySize = (n: any) => {
  const b = Number(n || 0)
  if (!b) return '0B'
  if (b < 1024) return `${b}B`
  if (b < 1024 * 1024) return `${Math.round((b / 1024) * 10) / 10}KB`
  if (b < 1024 * 1024 * 1024) return `${Math.round((b / 1024 / 1024) * 10) / 10}MB`
  return `${Math.round((b / 1024 / 1024 / 1024) * 10) / 10}GB`
}

// -------------------- square --------------------
const loadSquarePosts = async () => {
  const params: any = { sort: squareSort.value, limit: 50 }
  if (activeTag.value) params.tag = activeTag.value
  const list = await unwrap<any[]>(api.get('/api/post/list', { params }))
  squarePosts.value = list || []
}
const loadHotTags = async () => {
  const list = await unwrap<any[]>(api.get('/api/post/hotTags'))
  hotTagList.value = list || []
}
const setActiveTag = (t: string) => {
  activeTag.value = t
  loadSquarePosts()
}
const likePost = async (p: any) => {
  if (!hasToken.value) return ElMessage.warning('请先登录')
  try {
    await unwrap(api.post(`/api/post/like/${p.id}`))
    p.likeCount = Number(p.likeCount || 0) + 1
    ElMessage.success('已点赞')
  } catch (e: any) {
    ElMessage.error(e?.message || '点赞失败')
  }
}
const collectPost = async (p: any) => {
  if (!hasToken.value) return ElMessage.warning('请先登录')
  try {
    await unwrap(api.post(`/api/post/collect/${p.id}`))
    // 后端会在收藏/取消收藏之间切换，这里只做轻量刷新
    await loadSquarePosts()
    ElMessage.success('已更新收藏状态')
  } catch (e: any) {
    ElMessage.error(e?.message || '收藏失败')
  }
}

// report
const showReport = ref(false)
const reportPost = ref<any>(null)
const reportReason = ref('')
const reporting = ref(false)
const openReport = (p: any) => {
  if (!hasToken.value) return ElMessage.warning('请先登录')
  reportPost.value = p
  reportReason.value = ''
  showReport.value = true
}
const submitReport = async () => {
  if (!reportPost.value) return
  const reason = reportReason.value.trim()
  if (!reason) return ElMessage.warning('请填写举报理由')
  reporting.value = true
  try {
    await unwrap(api.post('/api/post/report', { postId: reportPost.value.id, reason }))
    ElMessage.success('举报已提交')
    showReport.value = false
  } catch (e: any) {
    ElMessage.error(e?.message || '举报失败')
  } finally {
    reporting.value = false
  }
}

// -------------------- comments (threaded) --------------------
const showComments = ref(false)
const commentPost = ref<any>(null)
const commentFlat = ref<any[]>([])
const commentInput = ref('')
const replyTarget = ref<any>(null)
const commentTree = computed(() => {
  const roots: any[] = []
  const childrenOf = new Map<any, any[]>()
  for (const c of commentFlat.value) {
    if (c.parentId) {
      if (!childrenOf.has(c.parentId)) childrenOf.set(c.parentId, [])
      childrenOf.get(c.parentId)!.push(c)
    } else {
      roots.push(c)
    }
  }
  const collect = (id: any): any[] => {
    const out: any[] = []
    const direct = childrenOf.get(id) || []
    for (const d of direct) out.push(d, ...collect(d.id))
    return out
  }
  return roots.map((r) => Object.assign({}, r, { children: collect(r.id) }))
})
const openComments = async (p: any) => {
  commentPost.value = p
  commentInput.value = ''
  replyTarget.value = null
  showComments.value = true
  try {
    const list = await unwrap<any[]>(api.get(`/api/post/comments/${p.id}`))
    commentFlat.value = (list || []).map((c: any) => ({
      id: c.id,
      parentId: c.parentId || null,
      replyTo: c.replyTo || null,
      text: c.text,
      time: c.time,
      user: c.user,
    }))
  } catch {
    commentFlat.value = []
  }
}
const startReply = (target: any) => {
  replyTarget.value = { parentId: target.id, user: target.user, text: target.text }
}
const cancelReply = () => {
  replyTarget.value = null
}
const submitComment = async () => {
  if (!commentPost.value) return
  if (!hasToken.value) return ElMessage.warning('请先登录')
  const text = (commentInput.value || '').trim()
  if (!text) return ElMessage.warning('请输入评论内容')
  const body: any = { content: text }
  if (replyTarget.value) {
    body.parentId = replyTarget.value.parentId
    body.replyTo = replyTarget.value.user
  }
  try {
    await unwrap(api.post(`/api/post/comment/${commentPost.value.id}`, body))
    ElMessage.success('评论成功')
    commentInput.value = ''
    replyTarget.value = null
    await openComments(commentPost.value)
    await loadSquarePosts()
  } catch (e: any) {
    ElMessage.error(e?.message || '评论失败')
  }
}

// -------------------- community --------------------
const loadCommunities = async () => {
  const params: any = { page: 1, size: 50 }
  if (tagFilter.value.trim()) params.tag = tagFilter.value.trim()
  const list = await unwrap<any[]>(api.get('/api/community/list', { params }))
  communities.value = list || []
}
const refreshMember = async () => {
  if (!selected.value) return
  try {
    const [mRes, memRes] = await Promise.all([
      unwrap(api.get(`/api/community/${selected.value.id}/isMember`)),
      unwrap(api.get(`/api/community/${selected.value.id}/members`)),
    ])
    isMember.value = !!mRes
    members.value = memRes || []
  } catch {
    isMember.value = false
    members.value = []
  }
}
const loadPendingApplies = async () => {
  if (!selected.value || !hasToken.value) {
    pendingApplies.value = []
    return
  }
  try {
    pendingApplies.value = await unwrap<any[]>(api.get(`/api/community/${selected.value.id}/applies`))
  } catch {
    pendingApplies.value = []
  }
}
const loadPosts = async () => {
  if (!selected.value) return
  const params: any = {}
  if (postTab.value === 'announcement') {
    params.type = 'announcement'
  } else if (postTab.value !== 'all') {
    params.type = 'post'
    params.category = postTab.value
  }
  const list = await unwrap<any[]>(api.get(`/api/community/${selected.value.id}/posts`, { params }))
  posts.value = list || []
}
const setPostTab = (tab: string) => {
  postTab.value = tab
  loadPosts()
}
const selectSquare = async () => {
  viewMode.value = 'square'
  selected.value = null
  await loadSquarePosts()
  await loadHotTags()
}
const selectCommunity = async (c: any) => {
  viewMode.value = 'community'
  selected.value = c
  try {
    selected.value = await unwrap(api.get(`/api/community/${c.id}`))
  } catch {}
  postTab.value = 'all'
  await refreshMember()
  await loadPendingApplies()
  await loadPosts()
}

const openUserProfile = (username: any) => {
  const u = String(username || '').trim()
  if (!u) return
  router.push(`/u/${encodeURIComponent(u)}`)
}
const toggleSticky = async (p: any) => {
  const v = !(p.isSticky === 1)
  try {
    await unwrap(api.patch(`/api/community/post/${p.id}/sticky`, null, { params: { value: v } }))
    ElMessage.success('已更新')
    await loadPosts()
  } catch (e: any) {
    ElMessage.error(e?.message || '更新失败')
  }
}
const toggleEssence = async (p: any) => {
  const v = !(p.isEssence === 1)
  try {
    await unwrap(api.patch(`/api/community/post/${p.id}/essence`, null, { params: { value: v } }))
    ElMessage.success('已更新')
    await loadPosts()
  } catch (e: any) {
    ElMessage.error(e?.message || '更新失败')
  }
}

// -------------------- attachments --------------------
const showAttachments = ref(false)
const attachPost = ref<any>(null)
const attachments = ref<any[]>([])
const attachmentsLoading = ref(false)
const uploadingAttachment = ref(false)

const uploadsUrl = (storagePath: string) => {
  const p = String(storagePath || '').trim().replace(/^\/+/, '')
  return `${import.meta.env.VITE_API_BASE || ''}/uploads/${p}`
}

const openAttachments = async (p: any) => {
  attachPost.value = p
  showAttachments.value = true
  attachmentsLoading.value = true
  try {
    attachments.value = await unwrap<any[]>(api.get(`/api/community/post/${p.id}/attachments`))
  } catch {
    attachments.value = []
  } finally {
    attachmentsLoading.value = false
  }
}

const downloadAttachment = (a: any) => {
  const url = uploadsUrl(a.storagePath)
  window.open(url, '_blank')
}

const uploadAttachmentRequest = async (options: any) => {
  const token = auth.token.value
  if (!token) return ElMessage.warning('请先登录')
  if (!attachPost.value?.id) return
  uploadingAttachment.value = true
  try {
    const form = new FormData()
    form.append('file', options.file)
    const base = import.meta.env.VITE_API_BASE || ''
    const res = await fetch(`${base}/api/community/post/${attachPost.value.id}/attachment`, {
      method: 'POST',
      headers: { Authorization: 'Bearer ' + token },
      body: form,
    })
    const text = await res.text()
    let json: any = null
    try {
      json = text ? JSON.parse(text) : null
    } catch {
      json = null
    }
    if (!res.ok || !json || Number(json.code) !== 200) {
      throw new Error((json && json.message) || text || '上传失败')
    }
    ElMessage.success('附件上传成功')
    await openAttachments(attachPost.value)
  } catch (e: any) {
    ElMessage.error(e?.message || '上传失败')
  } finally {
    uploadingAttachment.value = false
  }
}
const reviewCommunityApply = async (item: any, action: 'approved' | 'rejected') => {
  const label = action === 'approved' ? '通过' : '拒绝'
  try {
    await ElMessageBox.confirm(`确认${label}该入群申请？`, '审核确认', { type: 'warning' })
  } catch {
    return
  }
  try {
    await unwrap(api.post('/api/community/apply/review', { applyId: item.id, action }))
    ElMessage.success('审核完成')
    await refreshMember()
    await loadPendingApplies()
  } catch (e: any) {
    ElMessage.error(e?.message || '审核失败')
  }
}

// dialogs: apply / create / publish
const showApply = ref(false)
const applyReason = ref('')
const applying = ref(false)
const openApply = () => {
  if (!hasToken.value) return ElMessage.warning('请先登录')
  applyReason.value = ''
  showApply.value = true
}
const submitApply = async () => {
  if (!selected.value) return
  applying.value = true
  try {
    await unwrap(api.post('/api/community/apply', { communityId: selected.value.id, reason: applyReason.value || '申请加入' }))
    ElMessage.success('申请已提交')
    showApply.value = false
  } catch (e: any) {
    ElMessage.error(e?.message || '提交失败')
  } finally {
    applying.value = false
  }
}

const showCreate = ref(false)
const createForm = ref({ name: '', description: '', techTags: '' })
const creating = ref(false)
const openCreate = () => {
  if (!hasToken.value) return ElMessage.warning('请先登录')
  createForm.value = { name: '', description: '', techTags: '' }
  showCreate.value = true
}
const submitCreate = async () => {
  if (!createForm.value.name.trim()) return ElMessage.warning('请输入社群名称')
  creating.value = true
  try {
    await unwrap(api.post('/api/community/create', createForm.value))
    ElMessage.success('创建成功')
    showCreate.value = false
    await loadCommunities()
  } catch (e: any) {
    ElMessage.error(e?.message || '创建失败')
  } finally {
    creating.value = false
  }
}

const showSquarePublish = ref(false)
const squarePublishForm = ref({ title: '', content: '', tags: '' })
const publishingSquare = ref(false)
const openSquarePublish = () => {
  if (!hasToken.value) return ElMessage.warning('请先登录')
  squarePublishForm.value = { title: '', content: '', tags: '' }
  showSquarePublish.value = true
}
const submitSquarePublish = async () => {
  if (!squarePublishForm.value.content.trim()) return ElMessage.warning('正文不能为空')
  publishingSquare.value = true
  try {
    await unwrap(api.post('/api/post/publish', {
      title: squarePublishForm.value.title,
      content: squarePublishForm.value.content,
      tags: (squarePublishForm.value.tags || '').trim() || null,
      userId: currentUserId.value,
    }))
    ElMessage.success('发布成功')
    showSquarePublish.value = false
    await loadSquarePosts()
    await loadHotTags()
  } catch (e: any) {
    ElMessage.error(e?.message || '发布失败')
  } finally {
    publishingSquare.value = false
  }
}

const showPublish = ref(false)
const publishForm = ref({ type: 'post', category: 'discussion', title: '', content: '' })
const publishing = ref(false)
const openPublish = () => {
  if (!hasToken.value) return ElMessage.warning('请先登录')
  if (!selected.value) return
  if (!isMember.value && !platformAdmin.value) return ElMessage.warning('请先加入社群后再发帖（公告仅管理员可发）')
  publishForm.value = { type: 'post', category: 'discussion', title: '', content: '' }
  showPublish.value = true
}
const submitPublish = async () => {
  if (!selected.value) return
  if (!publishForm.value.content.trim()) return ElMessage.warning('正文不能为空')
  publishing.value = true
  try {
    await unwrap(api.post('/api/community/post/publish', {
      communityId: selected.value.id,
      type: canModerate.value ? publishForm.value.type : 'post',
      title: publishForm.value.title,
      content: publishForm.value.content,
      category: publishForm.value.type === 'post' ? publishForm.value.category : 'discussion',
    }))
    ElMessage.success('发布成功')
    showPublish.value = false
    await loadPosts()
  } catch (e: any) {
    ElMessage.error(e?.message || '发布失败')
  } finally {
    publishing.value = false
  }
}

const refreshAll = async () => {
  await loadCommunities()
  if (viewMode.value === 'square') {
    await loadSquarePosts()
    await loadHotTags()
  } else if (selected.value) {
    await refreshMember()
    await loadPendingApplies()
    await loadPosts()
  }
}

onMounted(async () => {
  await loadSquarePosts()
  await loadHotTags()
  await loadCommunities()
})
</script>

<style scoped>
.page { display: grid; gap: 14px; }
.head { display:flex; align-items:flex-start; justify-content:space-between; gap: 10px; flex-wrap: wrap; }
.title { font-weight: 900; font-size: 18px; }
.subtitle { margin-top: 4px; color: #6b7280; font-size: 13px; }
.actions { display:flex; gap: 8px; flex-wrap: wrap; }
.grid { display:grid; grid-template-columns: 320px 1fr; gap: 14px; align-items: start; }
@media (max-width: 980px) { .grid { grid-template-columns: 1fr; } }
.row { display:flex; align-items:center; justify-content:space-between; }
.left { border-radius: 12px; }
.right { border-radius: 12px; }
.sep-title { margin: 14px 0 8px; font-weight: 800; font-size: 13px; color:#111827; }
.entry, .comm { padding: 10px 10px; border-radius: 10px; border: 1px solid transparent; cursor: pointer; }
.entry:hover, .comm:hover { background: #f8fafc; }
.entry.active, .comm.active { border-color: #2563eb; background: #eff6ff; }
.entry-title, .comm-title { font-weight: 800; }
.entry-sub, .comm-sub { font-size: 12px; color: #6b7280; margin-top: 4px; }
.empty { text-align:center; padding: 26px; color:#6b7280; font-size: 13px; }
.muted { color:#6b7280; font-size: 13px; }
.tagbar { display:flex; align-items:center; gap: 8px; flex-wrap: wrap; padding: 8px 0 12px; }
.tagbar-label { color:#6b7280; font-size: 13px; margin-right: 4px; }
.post { padding: 14px 0; border-bottom: 1px solid #e5e7eb; }
.post:last-child { border-bottom: none; }
.post-title { display:flex; align-items:center; gap: 8px; flex-wrap: wrap; }
.post-title-text { font-weight: 800; color: #2563eb; }
.post-meta { margin-top: 6px; font-size: 12px; color:#6b7280; }
.post-body { margin-top: 8px; white-space: pre-wrap; line-height: 1.7; font-size: 14px; color:#111827; }
.post-actions { margin-top: 10px; display:flex; gap: 10px; flex-wrap: wrap; }
.hot { margin-left: 8px; color:#f97316; font-weight: 700; }
.comm-hd { margin-bottom: 10px; }
.comm-hd-title { font-weight: 900; font-size: 18px; }
.comm-hd-desc { margin-top: 6px; color:#6b7280; font-size: 13px; }
.summary-grid { display:grid; grid-template-columns: 1fr 1fr; gap: 12px; margin: 12px 0; }
@media (max-width: 980px) { .summary-grid { grid-template-columns: 1fr; } }
.summary-card { background: #f8fafc; border: 1px solid #e5e7eb; border-radius: 12px; padding: 12px; }
.summary-title { font-size: 13px; color:#6b7280; font-weight: 800; margin-bottom: 10px; }
.summary-list { display:flex; flex-direction: column; gap: 10px; }
.summary-item { background: #fff; border: 1px solid #e5e7eb; border-radius: 12px; padding: 10px 12px; }
.summary-item-title { font-weight: 800; }
.summary-item-meta { margin-top: 4px; font-size: 12px; color:#6b7280; line-height: 1.6; }
.summary-item-actions { margin-top: 10px; display:flex; gap: 8px; flex-wrap: wrap; }
.tabs { display:flex; gap: 8px; flex-wrap: wrap; margin-bottom: 10px; }
.tab { padding: 7px 14px; border: 1px solid #e5e7eb; border-radius: 999px; cursor:pointer; font-size: 13px; background: #f8fafc; }
.tab.active { border-color:#2563eb; color:#2563eb; background:#eff6ff; font-weight: 800; }
.badge { font-size: 11px; padding: 2px 8px; border-radius: 999px; border: 1px solid #e5e7eb; color:#6b7280; }
.badge.sticky { border-color:#f97316; color:#f97316; }
.badge.essence { border-color:#eab308; color:#a16207; }
.comments { max-height: 420px; overflow-y: auto; padding-right: 6px; }
.c-root { padding: 10px 0; border-bottom: 1px solid #e5e7eb; }
.c-row { display:flex; gap: 10px; align-items:flex-start; }
.c-avatar { width: 32px; height: 32px; border-radius: 999px; background: #eff6ff; color:#2563eb; display:flex; align-items:center; justify-content:center; font-weight: 900; flex-shrink:0; }
.c-main { flex: 1; min-width: 0; }
.c-meta { font-size: 13px; }
.c-text { margin-top: 4px; font-size: 14px; line-height: 1.7; white-space: pre-wrap; word-break: break-word; }
.c-children { margin: 8px 0 0 42px; padding: 8px 10px; background: #f8fafc; border: 1px dashed #e5e7eb; border-radius: 12px; }
.c-child { padding: 8px 0; border-bottom: 1px dashed #e5e7eb; }
.c-child:last-child { border-bottom: none; }
.comment-box { margin-top: 12px; border-top: 1px solid #e5e7eb; padding-top: 12px; }
.replying { font-size: 13px; color:#6b7280; margin-bottom: 6px; display:flex; align-items:center; gap: 8px; flex-wrap: wrap; }
.list-block { display: flex; flex-direction: column; gap: 10px; }
.list-row {
  display: flex; align-items: center; justify-content: space-between; gap: 12px;
  padding: 10px 12px; border: 1px solid #e5e7eb; border-radius: 12px; background: #fff;
}
.list-title { font-weight: 800; color: #111827; }
.list-sub { font-size: 12px; color: #6b7280; margin-top: 4px; }
.link { color:#2563eb; font-weight: 700; cursor: pointer; }
.link:hover { text-decoration: underline; }
</style>

