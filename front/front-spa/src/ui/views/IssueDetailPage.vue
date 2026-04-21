<template>
  <div class="grid">
    <el-card v-loading="loading">
      <template #header>
        <div class="row">
          <div style="font-weight:700;">任务 #{{ issue?.id }}：{{ issue?.title || '—' }}</div>
          <div class="row" style="gap:8px;">
            <el-select v-model="edit.status" style="width:140px" @change="saveStatus">
              <el-option label="todo" value="todo" />
              <el-option label="doing" value="doing" />
              <el-option label="done" value="done" />
            </el-select>
            <el-button size="small" @click="reload">刷新</el-button>
          </div>
        </div>
      </template>
      <div class="muted" style="white-space: pre-wrap;">{{ issue?.description || '暂无描述' }}</div>
      <div style="margin-top:10px; display:flex; gap:8px; align-items:center;">
        <el-tag type="info">{{ issue?.priority }}</el-tag>
        <el-tag type="success">{{ issue?.status }}</el-tag>
      </div>
    </el-card>

    <el-card>
      <template #header>
        <div class="row">
          <div style="font-weight:700;">评论</div>
          <el-button size="small" type="primary" plain @click="loadComments">刷新</el-button>
        </div>
      </template>
      <el-input v-model="newComment" type="textarea" :rows="3" placeholder="写下评论，支持 @xxx（一期先不解析）" />
      <div style="margin-top:8px;">
        <el-button type="primary" size="small" :loading="commenting" @click="addComment">发表评论</el-button>
      </div>
      <div class="comments">
        <div class="comment" v-for="c in comments" :key="c.id">
          <div class="muted">userId={{ c.userId }} · {{ c.createdAt || '' }}</div>
          <div style="white-space: pre-wrap;">{{ c.content }}</div>
        </div>
      </div>
    </el-card>

    <el-card>
      <template #header>
        <div class="row">
          <div style="font-weight:700;">附件</div>
          <el-button size="small" @click="loadAttachments">刷新</el-button>
        </div>
      </template>
      <el-upload
        :http-request="doUpload"
        :show-file-list="false"
        :multiple="false"
      >
        <el-button size="small" type="primary">上传附件</el-button>
      </el-upload>
      <el-table :data="attachments" style="width:100%; margin-top:10px;">
        <el-table-column prop="originalName" label="文件名" />
        <el-table-column prop="sizeBytes" label="大小" width="120" />
        <el-table-column label="访问" width="140">
          <template #default="{ row }">
            <a :href="fileUrl(row.storagePath)" target="_blank">打开</a>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-card>
      <template #header>
        <div class="row">
          <div style="font-weight:700;">验收互评</div>
          <el-button size="small" @click="loadReviewSummary">刷新摘要</el-button>
        </div>
      </template>
      <div class="row" style="justify-content:flex-start; gap:10px;">
        <el-rate v-model="review.rating" />
        <el-input v-model="review.comment" placeholder="评价（可选）" style="max-width:420px;" />
        <el-button type="primary" size="small" :loading="reviewing" @click="submitReview">提交</el-button>
      </div>
      <div class="muted" style="margin-top:8px;">
        receivedCount={{ reviewSummary?.receivedCount ?? 0 }} · avgRating={{ reviewSummary?.avgRating ?? '—' }}
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { api, unwrap } from '../api'

const route = useRoute()
const issueId = Number(route.params.issueId)

const loading = ref(false)
const issue = ref<any>(null)
const edit = reactive({ status: 'todo' })

const comments = ref<any[]>([])
const newComment = ref('')
const commenting = ref(false)

const attachments = ref<any[]>([])

const review = reactive({ rating: 0, comment: '' })
const reviewing = ref(false)
const reviewSummary = ref<any>(null)

const reload = async () => {
  loading.value = true
  try {
    issue.value = await unwrap(api.get('/api/collab/issue/detail', { params: { issueId } }))
    edit.status = issue.value?.status || 'todo'
  } catch (e: any) {
    ElMessage.error(e?.message || '加载失败')
  } finally {
    loading.value = false
  }
}

const saveStatus = async () => {
  try {
    await unwrap(api.post('/api/collab/issue/update', { issueId, status: edit.status }))
  } catch (e: any) {
    ElMessage.error(e?.message || '更新失败')
  }
}

const loadComments = async () => {
  try {
    comments.value = await unwrap(api.get('/api/collab/issue/comment/list', { params: { issueId } }))
  } catch (e: any) {
    ElMessage.error(e?.message || '加载评论失败')
  }
}

const addComment = async () => {
  if (!newComment.value.trim()) return ElMessage.warning('请输入内容')
  commenting.value = true
  try {
    await unwrap(api.post('/api/collab/issue/comment/add', { issueId, content: newComment.value }))
    newComment.value = ''
    await loadComments()
  } catch (e: any) {
    ElMessage.error(e?.message || '评论失败')
  } finally {
    commenting.value = false
  }
}

const loadAttachments = async () => {
  try {
    attachments.value = await unwrap(api.get('/api/collab/issue/attachment/list', { params: { issueId } }))
  } catch (e: any) {
    ElMessage.error(e?.message || '加载附件失败')
  }
}

const doUpload = async (opt: any) => {
  const fd = new FormData()
  fd.append('issueId', String(issueId))
  fd.append('file', opt.file)
  try {
    await unwrap(api.post('/api/collab/issue/attachment/upload', fd, { headers: { 'Content-Type': 'multipart/form-data' } }))
    ElMessage.success('上传成功')
    await loadAttachments()
    opt.onSuccess && opt.onSuccess({}, opt.file)
  } catch (e: any) {
    ElMessage.error(e?.message || '上传失败')
    opt.onError && opt.onError(e)
  }
}

const loadReviewSummary = async () => {
  try {
    reviewSummary.value = await unwrap(api.get('/api/collab/issue/review/summary', { params: { issueId } }))
  } catch (e: any) {
    ElMessage.error(e?.message || '加载摘要失败')
  }
}

const submitReview = async () => {
  if (!review.rating) return ElMessage.warning('请评分')
  reviewing.value = true
  try {
    await unwrap(api.post('/api/collab/issue/review/submit', { issueId, rating: review.rating, comment: review.comment }))
    ElMessage.success('已提交')
    await loadReviewSummary()
  } catch (e: any) {
    ElMessage.error(e?.message || '提交失败')
  } finally {
    reviewing.value = false
  }
}

const fileUrl = (storagePath: string) => {
  // 开发期通过 vite proxy 访问 /uploads/**
  const base = window.location.origin.replace(/\/$/, '')
  return `${base}/uploads/${storagePath}`
}

onMounted(async () => {
  await reload()
  await loadComments()
  await loadAttachments()
  await loadReviewSummary()
})
</script>

<style scoped>
.grid { display: grid; gap: 14px; }
.row { display:flex; align-items:center; justify-content:space-between; }
.muted { color: #6b7280; }
.comments { margin-top: 10px; display:grid; gap: 10px; }
.comment { background:#f8fafc; border:1px solid #e5e7eb; border-radius: 12px; padding: 10px; }
</style>

