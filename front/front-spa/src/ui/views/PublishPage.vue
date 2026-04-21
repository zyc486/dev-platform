<template>
  <div class="publish">
    <el-card class="card" shadow="never">
      <template #header>
        <div class="hd">
          <div class="hd-title">发布新项目</div>
          <el-button size="small" text @click="goProjects">返回项目列表</el-button>
        </div>
      </template>

      <el-form :model="form" :rules="rules" ref="formRef" label-width="100px" label-position="top">
        <el-form-item label="项目名称" prop="name">
          <el-input v-model="form.name" placeholder="例如：开源协作平台前端重构" maxlength="60" show-word-limit />
        </el-form-item>

        <el-form-item label="项目描述" prop="description">
          <el-input
            v-model="form.description"
            type="textarea"
            :rows="4"
            placeholder="详细描述项目目标、技术栈、协作方式等"
            maxlength="500"
            show-word-limit
          />
        </el-form-item>

        <el-form-item label="技术标签" prop="techTags">
          <el-select
            v-model="form.techTags"
            multiple
            filterable
            allow-create
            default-first-option
            placeholder="选择或输入技术标签，如 Vue3、TypeScript、Spring Boot"
            style="width: 100%"
          >
            <el-option v-for="tag in commonTags" :key="tag" :label="tag" :value="tag" />
          </el-select>
          <div class="form-tip">多个标签用逗号分隔，最多选择 8 个</div>
        </el-form-item>

        <el-form-item label="最低信用分要求" prop="minCredit">
          <el-slider
            v-model="form.minCredit"
            :min="0"
            :max="100"
            :step="5"
            show-stops
            show-input
            style="width: 100%"
          />
          <div class="form-tip">设置协作参与者的最低信用分要求，0 表示不限制</div>
        </el-form-item>

        <el-form-item label="项目可见性" prop="visibility">
          <el-radio-group v-model="form.visibility">
            <el-radio label="public">公开（所有人可见）</el-radio>
            <el-radio label="private">私有（仅受邀成员可见）</el-radio>
          </el-radio-group>
        </el-form-item>

        <el-form-item label="协作模式" prop="collabMode">
          <el-select v-model="form.collabMode" placeholder="选择协作模式" style="width: 100%">
            <el-option label="自由加入" value="free" />
            <el-option label="申请审核" value="apply" />
            <el-option label="邀请制" value="invite" />
          </el-select>
        </el-form-item>

        <el-form-item label="项目封面图" prop="coverImage">
          <el-upload
            class="uploader"
            action="/api/upload/image"
            :headers="uploadHeaders"
            :show-file-list="false"
            :on-success="handleUploadSuccess"
            :before-upload="beforeUpload"
            accept="image/*"
          >
            <div v-if="form.coverImage" class="preview">
              <img :src="mediaUrl(form.coverImage)" class="preview-img" />
              <div class="preview-actions">
                <el-button size="small" type="danger" @click.stop="removeCover">移除</el-button>
              </div>
            </div>
            <div v-else class="upload-placeholder">
              <el-icon size="40"><Plus /></el-icon>
              <div class="upload-text">点击上传封面图</div>
              <div class="upload-hint">建议尺寸 800×400，支持 JPG/PNG</div>
            </div>
          </el-upload>
        </el-form-item>

        <el-form-item>
          <el-button type="primary" :loading="submitting" @click="submitForm">发布项目</el-button>
          <el-button @click="resetForm">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card class="card tips-card" shadow="never" style="margin-top: 20px">
      <template #header>
        <div class="hd">
          <div class="hd-title">发布提示</div>
        </div>
      </template>
      <div class="tips">
        <div class="tip-item">
          <el-icon color="#3b82f6"><InfoFilled /></el-icon>
          <div>
            <div class="tip-title">信用分要求</div>
            <div class="tip-content">设置合理的信用分门槛可以有效筛选合适的协作者，提高协作质量。</div>
          </div>
        </div>
        <div class="tip-item">
          <el-icon color="#10b981"><Check /></el-icon>
          <div>
            <div class="tip-title">协作模式说明</div>
            <div class="tip-content">
              <div>• 自由加入：任何满足信用分要求的用户可直接加入</div>
              <div>• 申请审核：用户需提交申请，由项目创建者审核</div>
              <div>• 邀请制：仅通过邀请链接或邀请码加入</div>
            </div>
          </div>
        </div>
        <div class="tip-item">
          <el-icon color="#f59e0b"><Warning /></el-icon>
          <div>
            <div class="tip-title">注意事项</div>
            <div class="tip-content">
              <div>• 项目发布后不可删除，但可以归档</div>
              <div>• 请确保项目描述清晰，避免产生误解</div>
              <div>• 定期维护项目状态，及时处理协作申请</div>
            </div>
          </div>
        </div>
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, InfoFilled, Check, Warning } from '@element-plus/icons-vue'
import { api, unwrap } from '../api'
import { auth } from '../auth'
import type { FormInstance, FormRules, UploadProps } from 'element-plus'

const router = useRouter()

interface FormData {
  name: string
  description: string
  techTags: string[]
  minCredit: number
  visibility: string
  collabMode: string
  coverImage: string
}

const formRef = ref<FormInstance>()
const form = ref<FormData>({
  name: '',
  description: '',
  techTags: [],
  minCredit: 0,
  visibility: 'public',
  collabMode: 'apply',
  coverImage: ''
})

const rules: FormRules = {
  name: [
    { required: true, message: '请输入项目名称', trigger: 'blur' },
    { min: 3, max: 60, message: '长度在 3 到 60 个字符', trigger: 'blur' }
  ],
  description: [
    { required: true, message: '请输入项目描述', trigger: 'blur' },
    { min: 10, max: 500, message: '长度在 10 到 500 个字符', trigger: 'blur' }
  ],
  techTags: [
    { type: 'array', max: 8, message: '最多选择 8 个标签', trigger: 'change' }
  ]
}

const commonTags = ref([
  'Vue3', 'TypeScript', 'React', 'Node.js', 'Spring Boot', 'Python', 'Go', 'Rust',
  'Docker', 'Kubernetes', 'MySQL', 'PostgreSQL', 'Redis', 'MongoDB', 'Elasticsearch',
  'Git', 'CI/CD', '微服务', '区块链', 'AI', '机器学习', '大数据', '前端', '后端', '全栈'
])

const submitting = ref(false)

const uploadHeaders = computed(() => ({
  Authorization: `Bearer ${auth.token.value || ''}`,
}))

const mediaUrl = (path: string) => {
  if (!path) return ''
  if (path.startsWith('http')) return path
  return `${import.meta.env.VITE_API_BASE || ''}${path}`
}

const handleUploadSuccess: UploadProps['onSuccess'] = (response) => {
  if (response && response.code === 200 && response.data) {
    form.value.coverImage = response.data
    ElMessage.success('封面图上传成功')
  } else {
    ElMessage.error(response?.message || '上传失败')
  }
}

const beforeUpload: UploadProps['beforeUpload'] = (file) => {
  const isImage = file.type.startsWith('image/')
  const isLt5M = file.size / 1024 / 1024 < 5

  if (!isImage) {
    ElMessage.error('只能上传图片文件')
    return false
  }
  if (!isLt5M) {
    ElMessage.error('图片大小不能超过 5MB')
    return false
  }
  return true
}

const removeCover = () => {
  form.value.coverImage = ''
}

const submitForm = async () => {
  if (!formRef.value) return
  
  try {
    await formRef.value.validate()
  } catch {
    ElMessage.warning('请完善表单信息')
    return
  }

  submitting.value = true
  try {
    const payload = {
      ...form.value,
      techTags: form.value.techTags.join(','),
    }

    let result: any = null
    let viaLegacy = false
    try {
      result = await unwrap<any>(api.post('/api/collab/project/create', payload))
    } catch (primaryErr: any) {
      try {
        result = await unwrap<any>(
          api.post('/api/collab/publish', {
            title: form.value.name,
            content: form.value.description,
            minCredit: form.value.minCredit,
          }),
        )
        viaLegacy = true
      } catch {
        throw primaryErr
      }
    }

    ElMessage.success(viaLegacy ? '发布成功（已使用兼容接口 /api/collab/publish）' : '项目发布成功')

    await ElMessageBox.confirm(
      '项目已成功发布！是否立即前往项目详情页？',
      '发布成功',
      {
        confirmButtonText: '前往项目',
        cancelButtonText: '留在本页',
        type: 'success',
      },
    )

    const newId = result?.id ?? result?.projectId ?? result?.data?.id
    if (newId != null) router.push(`/projects/${newId}`)
    else router.push('/projects')
  } catch (error: any) {
    ElMessage.error(error?.message || '发布失败，请稍后重试')
  } finally {
    submitting.value = false
  }
}

const resetForm = () => {
  form.value = {
    name: '',
    description: '',
    techTags: [],
    minCredit: 0,
    visibility: 'public',
    collabMode: 'apply',
    coverImage: ''
  }
}

const goProjects = () => {
  router.push('/projects')
}

onMounted(() => {
  if (!auth.token.value) {
    ElMessage.warning('请先登录后再发布项目')
    router.push('/login?redirect=/publish')
  }
})
</script>

<style scoped>
.publish {
  max-width: 800px;
  margin: 0 auto;
}

.card {
  border-radius: 12px;
  border: 1px solid var(--app-border-color);
  background: var(--app-card-bg);
}

.hd {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.hd-title {
  font-weight: 800;
  color: var(--app-text-color);
}

.form-tip {
  margin-top: 6px;
  font-size: 12px;
  color: var(--app-muted-color);
}

.uploader {
  width: 100%;
}

.preview {
  position: relative;
  width: 100%;
  height: 200px;
  border-radius: 8px;
  overflow: hidden;
  border: 2px dashed var(--app-border-color);
}

.preview-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.preview-actions {
  position: absolute;
  bottom: 10px;
  right: 10px;
}

.upload-placeholder {
  width: 100%;
  height: 200px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  border: 2px dashed var(--app-border-color);
  border-radius: 8px;
  cursor: pointer;
  transition: border-color 0.3s;
  color: var(--app-muted-color);
}

.upload-placeholder:hover {
  border-color: var(--app-primary-color);
}

.upload-text {
  margin-top: 10px;
  font-weight: 600;
}

.upload-hint {
  margin-top: 4px;
  font-size: 12px;
}

.tips-card {
  background: var(--app-card-bg);
}

.tips {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.tip-item {
  display: flex;
  gap: 12px;
  align-items: flex-start;
}

.tip-item .el-icon {
  flex-shrink: 0;
  margin-top: 2px;
}

.tip-title {
  font-weight: 700;
  color: var(--app-text-color);
  margin-bottom: 4px;
}

.tip-content {
  font-size: 13px;
  color: var(--app-muted-color);
  line-height: 1.6;
}

.tip-content div {
  margin-bottom: 2px;
}

@media (max-width: 768px) {
  .publish {
    padding: 0 12px;
  }
  
  .hd {
    flex-direction: column;
    align-items: flex-start;
    gap: 8px;
  }
}
</style>
