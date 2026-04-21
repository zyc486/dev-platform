<template>
  <div class="page">
    <el-card class="card" shadow="never">
      <template #header>
        <div class="hd">
          <div class="hd-title">意见反馈</div>
          <div class="muted">提交问题/建议/投诉，并在此跟进处理状态与管理员回复。</div>
        </div>
      </template>

      <el-alert
        v-if="!hasToken"
        type="warning"
        show-icon
        :closable="false"
        title="当前未登录，无法提交/查看反馈。请先登录。"
        style="margin-bottom: 12px;"
      />

      <div class="grid">
        <el-card class="subcard" shadow="never">
          <template #header>
            <div class="row">
              <div style="font-weight: 900;">提交反馈</div>
              <el-button size="small" type="primary" :loading="submitting" :disabled="!hasToken" @click="submit">提交</el-button>
            </div>
          </template>

          <el-form label-width="90px">
            <el-form-item label="类型">
              <el-select v-model="form.type" style="width: 180px;">
                <el-option label="bug" value="bug" />
                <el-option label="feature" value="feature" />
                <el-option label="complaint" value="complaint" />
                <el-option label="other" value="other" />
              </el-select>
            </el-form-item>
            <el-form-item label="标题">
              <el-input v-model="form.title" placeholder="一句话描述问题或建议" />
            </el-form-item>
            <el-form-item label="内容">
              <el-input v-model="form.content" type="textarea" :rows="6" placeholder="请尽量提供复现步骤/期望结果/截图说明等" />
            </el-form-item>
            <el-form-item label="联系方式">
              <el-input v-model="form.contact" placeholder="可选：手机号/邮箱/微信等" />
            </el-form-item>
            <el-form-item label="附件">
              <el-upload
                action="#"
                :auto-upload="false"
                :limit="1"
                :on-change="onFileChange"
                :on-remove="onFileRemove"
              >
                <el-button size="small">选择文件</el-button>
                <template #tip>
                  <div class="muted">可选：截图/日志（大小受后端限制）。</div>
                </template>
              </el-upload>
            </el-form-item>
          </el-form>
        </el-card>

        <el-card class="subcard" shadow="never">
          <template #header>
            <div class="row">
              <div style="font-weight: 900;">我的反馈</div>
              <el-button size="small" :disabled="!hasToken" @click="loadMyList">刷新</el-button>
            </div>
          </template>

          <el-table :data="myList" v-loading="loading" stripe>
            <el-table-column prop="id" label="ID" width="80" />
            <el-table-column prop="type" label="类型" width="110" />
            <el-table-column prop="title" label="标题" min-width="160" show-overflow-tooltip />
            <el-table-column prop="status" label="状态" width="110" />
            <el-table-column label="附件" width="90">
              <template #default="{ row }">
                <a v-if="row.attachmentPath" class="link" :href="attachmentUrl(row.attachmentPath)" target="_blank" rel="noreferrer">查看</a>
                <span v-else class="muted">—</span>
              </template>
            </el-table-column>
            <el-table-column prop="createTime" label="提交时间" width="160" />
            <el-table-column label="回复" min-width="180" show-overflow-tooltip>
              <template #default="{ row }">
                <span v-if="row.replyContent">{{ row.replyContent }}</span>
                <span v-else class="muted">暂无</span>
              </template>
            </el-table-column>
            <el-table-column prop="replyTime" label="回复时间" width="160" />
          </el-table>

          <div v-if="!loading && myList.length === 0" class="empty muted">暂无反馈记录</div>
        </el-card>
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { api, unwrap } from '../api'
import { auth } from '../auth'

const hasToken = computed(() => !!auth.token.value)

const form = reactive({
  type: 'bug',
  title: '',
  content: '',
  contact: '',
})

const fileRef = ref<File | null>(null)
const submitting = ref(false)

const loading = ref(false)
const myList = ref<any[]>([])

const fmtTime = (t: any) => (t ? String(t).replace('T', ' ').slice(0, 16) : '')

const attachmentUrl = (path: string) => {
  if (!path) return ''
  const p = path.startsWith('/') ? path : `/${path}`
  return `${import.meta.env.VITE_API_BASE || ''}${p}`
}

const onFileChange = (file: any) => {
  fileRef.value = file?.raw || null
}
const onFileRemove = () => {
  fileRef.value = null
}

const loadMyList = async () => {
  if (!hasToken.value) {
    myList.value = []
    return
  }
  loading.value = true
  try {
    const list = await unwrap<any[]>(api.get('/api/feedback/myList'))
    myList.value = (list || []).map((x: any) => ({
      ...x,
      createTime: fmtTime(x.createTime),
      replyTime: fmtTime(x.replyTime),
    }))
  } catch (e: any) {
    myList.value = []
    ElMessage.error(e?.message || '加载失败')
  } finally {
    loading.value = false
  }
}

const submit = async () => {
  if (!hasToken.value) return ElMessage.warning('请先登录')
  if (!form.title.trim()) return ElMessage.warning('请填写标题')
  if (!form.content.trim()) return ElMessage.warning('请填写内容')

  submitting.value = true
  try {
    const fd = new FormData()
    fd.append('type', form.type)
    fd.append('title', form.title.trim())
    fd.append('content', form.content.trim())
    if (form.contact.trim()) fd.append('contact', form.contact.trim())
    if (fileRef.value) fd.append('file', fileRef.value)

    const base = import.meta.env.VITE_API_BASE || ''
    const res = await fetch(base + '/api/feedback/submit', {
      method: 'POST',
      headers: {
        Authorization: `Bearer ${auth.token.value}`,
      },
      body: fd,
    })
    const json = await res.json().catch(() => null)
    if (!res.ok) throw new Error((json && json.message) || `提交失败（HTTP ${res.status}）`)
    if (!json || Number(json.code) !== 200) throw new Error((json && json.message) || '提交失败')

    ElMessage.success('已提交，感谢反馈')
    form.type = 'bug'
    form.title = ''
    form.content = ''
    form.contact = ''
    fileRef.value = null
    await loadMyList()
  } catch (e: any) {
    ElMessage.error(e?.message || '提交失败')
  } finally {
    submitting.value = false
  }
}

onMounted(loadMyList)
</script>

<style scoped>
.page { max-width: 960px; margin: 0 auto; padding: 12px; }
.card { border-radius: 12px; }
.hd-title { font-weight: 900; }
.muted { color: #6b7280; font-size: 13px; line-height: 1.7; }
.grid { display: grid; grid-template-columns: 1fr 1fr; gap: 14px; align-items: start; }
@media (max-width: 980px) { .grid { grid-template-columns: 1fr; } }
.subcard { border-radius: 12px; }
.row { display:flex; align-items:center; justify-content:space-between; gap: 10px; }
.empty { padding: 14px 0; text-align: center; }
.link { color: #0969da; text-decoration: none; }
.link:hover { text-decoration: underline; }
</style>
