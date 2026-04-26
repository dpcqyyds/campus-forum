<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useAuthStore } from '../../stores/auth'
import {
  createPostApi,
  listAvailableBoardsApi,
  listPublishedPostsApi,
  uploadImagesApi
} from '../../services/modules/forumApi'
import { renderMarkdownToHtml } from '../../utils/contentFormat'

const authStore = useAuthStore()
const submitting = ref(false)
const savingDraft = ref(false)
const successMessage = ref('')
const errorMessage = ref('')
const preAuditResult = ref(null)
const boards = ref([])
const canPublishDirectly = computed(() => {
  const role = authStore.user?.role
  return role === 'super_admin' || role === 'teacher'
})

const formatOptions = [
  { value: 'rich_text', label: 'Markdown' },
  { value: 'plain_text', label: '普通文本（可上传图片）' }
]

const showMarkdownPreview = ref(false)

const form = reactive({
  title: '',
  summary: '',
  boardId: '',
  format: 'rich_text',
  content: '',
  tagsText: '',
  isTop: false,
  isFeatured: false
})

const localImages = ref([])

const markdownPreviewHtml = computed(() => {
  if (!form.content) return '<p class="empty-hint">暂无内容</p>'
  return renderMarkdownToHtml(form.content)
})

const contentPlaceholder = computed(() => {
  if (form.format === 'rich_text') return '请输入 Markdown 内容，支持图片链接语法：[![图片](图片地址)](跳转地址)'
  if (form.format === 'plain_text') return '请输入普通文本内容，可同时上传图片'
  return '请输入正文内容'
})

const canUsePublishDirectly = computed(() => canPublishDirectly.value)

watch(
  () => form.format,
  (value) => {
    if (value !== 'plain_text') {
      clearLocalImages()
    }
  }
)

function resetForm() {
  form.title = ''
  form.summary = ''
  form.boardId = boards.value[0]?.id || ''
  form.format = 'rich_text'
  form.content = ''
  form.tagsText = ''
  form.isTop = false
  form.isFeatured = false
  preAuditResult.value = null
  clearLocalImages()
}

function clearLocalImages() {
  localImages.value.forEach((item) => URL.revokeObjectURL(item.previewUrl))
  localImages.value = []
}

async function onLocalImageChange(event) {
  const files = Array.from(event.target.files || [])
  if (!files.length) return

  const records = await Promise.all(
    files.map(
      (file) =>
        new Promise((resolve) => {
          resolve({
            name: file.name,
            caption: '',
            previewUrl: URL.createObjectURL(file),
            file
          })
        })
    )
  )

  localImages.value = [...localImages.value, ...records]
  event.target.value = ''
}

function removeLocalImage(index) {
  const target = localImages.value[index]
  if (!target) return
  URL.revokeObjectURL(target.previewUrl)
  localImages.value.splice(index, 1)
}

function moveImage(index, direction) {
  const next = index + direction
  if (next < 0 || next >= localImages.value.length) return
  const temp = localImages.value[index]
  localImages.value[index] = localImages.value[next]
  localImages.value[next] = temp
}

async function loadBoards() {
  errorMessage.value = ''
  try {
    const data = await listAvailableBoardsApi()
    boards.value = data.list
    if (!form.boardId) form.boardId = boards.value[0]?.id || ''
  } catch (error) {
    try {
      const published = await listPublishedPostsApi()
      const merged = []
      for (const post of published.list || []) {
        if (!post.boardId || !post.boardName) continue
        if (merged.some((item) => item.id === post.boardId)) continue
        merged.push({ id: post.boardId, name: post.boardName })
      }
      boards.value = merged
      if (!form.boardId) form.boardId = boards.value[0]?.id || ''
      if (!boards.value.length) {
        errorMessage.value = '暂无可选板块，请联系管理员检查板块配置。'
      }
    } catch (fallbackError) {
      errorMessage.value = `板块加载失败：${fallbackError.message}`
    }
  }
}

function buildPayload(overrideStatus) {
  const resolvedStatus = overrideStatus || (canUsePublishDirectly.value ? 'published' : 'pending')
  const payload = {
    title: form.title,
    summary: form.summary,
    boardId: Number(form.boardId),
    format: form.format,
    tags: form.tagsText,
    status: resolvedStatus,
    isTop: form.isTop,
    isFeatured: form.isFeatured,
    author: authStore.user?.username || 'unknown'
  }

  if (form.format === 'rich_text') {
    payload.content = form.content
    payload.attachments = []
  }

  if (form.format === 'plain_text') {
    payload.content = form.content
    payload.attachments = []
    payload.galleryCaptions = localImages.value.map((item) => item.caption)
  }

  return payload
}

function validateForm(mode = 'publish') {
  if (!form.title || !form.boardId) {
    return '请至少填写标题和板块。'
  }

  if (mode === 'draft') {
    return ''
  }

  if (form.format === 'rich_text' && !form.content.trim()) {
    return '请填写 Markdown 正文。'
  }

  if (form.format === 'plain_text' && !form.content.trim() && !localImages.value.length) {
    return '请填写普通文本正文，或至少上传一张图片。'
  }

  return ''
}

async function submitPost() {
  const validateMessage = validateForm()
  if (validateMessage) {
    errorMessage.value = validateMessage
    return
  }

  submitting.value = true
  errorMessage.value = ''
  successMessage.value = ''
  preAuditResult.value = null

  try {
    let uploadedUrls = []
    if (form.format === 'plain_text' && localImages.value.length) {
      const uploadResult = await uploadImagesApi(localImages.value.map((item) => item.file))
      uploadedUrls = (uploadResult.files || [])
        .map((item) => item.url)
        .filter(Boolean)
      if (!uploadedUrls.length) {
        throw new Error('图片上传失败，请检查 /api/v1/uploads/images 接口。')
      }
    }

    const payload = buildPayload()
    if (form.format === 'plain_text') {
      payload.attachments = uploadedUrls
    }

    await createPostApi(payload)
    successMessage.value = canPublishDirectly.value ? '帖子发布成功。' : '帖子已提交审核，请等待管理员审核。'
    resetForm()
  } catch (error) {
    if (error?.status === 422 || error?.code === 42201) {
      const hitWords = Array.isArray(error?.data?.hitWords) ? error.data.hitWords : []
      preAuditResult.value = {
        message: error?.data?.message || error.message || '内容命中前置审核规则，请修改后重试。',
        riskLevel: error?.data?.riskLevel || 'high',
        hitWords
      }
      errorMessage.value = '前置审核未通过，请根据提示修改后再提交。'
      return
    }
    errorMessage.value = error.message
  } finally {
    submitting.value = false
  }
}

async function saveAsDraft() {
  const validateMessage = validateForm('draft')
  if (validateMessage) {
    errorMessage.value = validateMessage
    return
  }

  savingDraft.value = true
  errorMessage.value = ''
  successMessage.value = ''
  preAuditResult.value = null

  try {
    let uploadedUrls = []
    if (form.format === 'plain_text' && localImages.value.length) {
      const uploadResult = await uploadImagesApi(localImages.value.map((item) => item.file))
      uploadedUrls = (uploadResult.files || []).map((item) => item.url).filter(Boolean)
    }

    const payload = buildPayload('draft')
    if (form.format === 'plain_text' && uploadedUrls.length) {
      payload.attachments = uploadedUrls
    }

    await createPostApi(payload)
    successMessage.value = '已存为草稿，可在”我的帖子”中继续编辑或重新发布。'
    resetForm()
  } catch (error) {
    errorMessage.value = error.message
  } finally {
    savingDraft.value = false
  }
}

function toggleMarkdownPreview() {
  showMarkdownPreview.value = !showMarkdownPreview.value
}

onMounted(loadBoards)
onBeforeUnmount(clearLocalImages)
</script>

<template>
  <section class="panel">
    <h2>内容发布</h2>
    <p class="hint">请先选择内容格式，系统会展示对应发布界面。</p>

    <div class="form-grid two-col">
      <label>
        帖子标题
        <input v-model.trim="form.title" placeholder="请输入帖子标题" />
      </label>

      <label>
        所属板块
        <select v-model="form.boardId">
          <option v-for="item in boards" :key="item.id" :value="item.id">{{ item.name }}</option>
        </select>
      </label>

      <label>
        内容格式
        <select v-model="form.format">
          <option v-for="item in formatOptions" :key="item.value" :value="item.value">{{ item.label }}</option>
        </select>
      </label>

      <label>
        内容摘要
        <input v-model.trim="form.summary" placeholder="可选，用于列表摘要展示" />
      </label>

      <label class="full-width">
        标签（逗号分隔）
        <input v-model="form.tagsText" placeholder="例如：考试周, 经验分享" />
      </label>
    </div>

    <div class="format-card" v-if="form.format === 'rich_text'">
      <h3>Markdown 发布</h3>
      <p class="hint">支持 Markdown 链接、图片和图片链接语法，不提供本地图片上传</p>
      <div class="editor-header">
        <button type="button" class="preview-toggle" @click="toggleMarkdownPreview">
          {{ showMarkdownPreview ? '隐藏预览' : '显示预览' }}
        </button>
      </div>
      <div class="editor-container" :class="{ 'split-view': showMarkdownPreview }">
        <textarea
          v-model="form.content"
          :placeholder="contentPlaceholder"
          rows="10"
          class="editor-textarea"
        />
        <div v-if="showMarkdownPreview" class="preview-panel markdown-preview">
          <h4>预览效果</h4>
          <article class="post-content" v-html="markdownPreviewHtml" />
        </div>
      </div>
    </div>

    <div class="format-card" v-if="form.format === 'plain_text'">
      <h3>普通文本发布</h3>
      <p class="hint">使用纯文本正文，可选择多张图片随帖发布</p>

      <div class="gallery-upload-section">
        <label class="upload-label">
          <span class="label-text">选择图片</span>
          <input type="file" accept="image/*" multiple @change="onLocalImageChange" class="file-input-hidden" />
          <span class="upload-hint">支持 JPG、PNG、GIF 格式，可多选</span>
        </label>

        <label class="gallery-desc-label">
          <span class="label-text">正文内容</span>
          <textarea v-model="form.content" rows="6" :placeholder="contentPlaceholder" class="gallery-textarea" />
        </label>
      </div>

      <div v-if="localImages.length" class="gallery-preview-section">
        <div class="section-header">
          <h4>已选图片 ({{ localImages.length }})</h4>
          <span class="hint-text">可拖动调整顺序，为每张图片添加说明</span>
        </div>
        <div class="gallery-grid">
          <div class="gallery-item" v-for="(img, index) in localImages" :key="img.previewUrl">
            <div class="image-wrapper">
              <img :src="img.previewUrl" :alt="img.name" />
              <span class="image-index">{{ index + 1 }}</span>
            </div>
            <input v-model.trim="img.caption" placeholder="为这张图片添加说明..." class="caption-input" />
            <div class="item-actions">
              <button type="button" @click="moveImage(index, -1)" :disabled="index === 0" class="btn-move">
                上移
              </button>
              <button type="button" @click="moveImage(index, 1)" :disabled="index === localImages.length - 1" class="btn-move">
                下移
              </button>
              <button type="button" class="btn-delete" @click="removeLocalImage(index)">
                删除
              </button>
            </div>
          </div>
        </div>
      </div>

      <div v-else class="empty-gallery">
        <p>还没有选择图片，点击上方"选择图片"按钮开始上传</p>
      </div>
    </div>

    <p v-if="errorMessage" class="error">{{ errorMessage }}</p>
    <div v-if="preAuditResult" class="panel sub-panel">
      <h3>前置审核提示</h3>
      <p class="error">{{ preAuditResult.message }}</p>
      <p class="hint">风险等级：{{ preAuditResult.riskLevel }}</p>
      <p class="hint" v-if="preAuditResult.hitWords.length">
        命中词：{{ preAuditResult.hitWords.join('，') }}
      </p>
    </div>
    <p v-if="successMessage" class="success">{{ successMessage }}</p>

    <div class="action-row">
      <button class="primary-btn" type="button" :disabled="submitting || savingDraft" @click="submitPost">
        {{ submitting ? '提交中...' : '发布内容' }}
      </button>
      <button type="button" :disabled="savingDraft || submitting" @click="saveAsDraft">
        {{ savingDraft ? '保存中...' : '存为草稿' }}
      </button>
      <button type="button" @click="resetForm">重置</button>
    </div>
  </section>
</template>

<style scoped>
.editor-header {
  display: flex;
  justify-content: flex-end;
  align-items: center;
  margin-bottom: 12px;
  gap: 12px;
}

.preview-toggle {
  padding: 6px 16px;
  font-size: 13px;
  background: #4a90e2;
  color: white;
  border: none;
  border-radius: 4px;
  cursor: pointer;
  white-space: nowrap;
}

.preview-toggle:hover {
  background: #357abd;
}

.editor-container {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.editor-container.split-view {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}

.editor-textarea {
  width: 100%;
  min-height: 300px;
  padding: 12px;
  border: 1px solid #ddd;
  border-radius: 4px;
  font-family: 'Consolas', 'Monaco', monospace;
  font-size: 14px;
  line-height: 1.6;
  resize: vertical;
}

.preview-panel {
  border: 1px solid #ddd;
  border-radius: 4px;
  padding: 16px;
  background: #fafafa;
  overflow-y: auto;
  max-height: 500px;
}

.preview-panel h4 {
  margin: 0 0 12px 0;
  font-size: 14px;
  color: #666;
  border-bottom: 1px solid #ddd;
  padding-bottom: 8px;
}

.preview-panel .empty-hint {
  color: #999;
  font-style: italic;
}

@media (max-width: 768px) {
  .editor-container.split-view {
    grid-template-columns: 1fr;
  }
}

/* 普通文本图片样式 */
.gallery-upload-section {
  display: flex;
  flex-direction: column;
  gap: 20px;
  margin-bottom: 24px;
}

.upload-label {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 24px;
  border: 2px dashed #4a90e2;
  border-radius: 8px;
  background: #f8fbff;
  cursor: pointer;
  transition: all 0.3s;
}

.upload-label:hover {
  border-color: #357abd;
  background: #f0f7ff;
}

.label-text {
  font-weight: 600;
  font-size: 15px;
  color: #333;
}

.file-input-hidden {
  display: none;
}

.upload-hint {
  font-size: 13px;
  color: #666;
}

.gallery-desc-label {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.gallery-textarea {
  width: 100%;
  padding: 12px;
  border: 1px solid #ddd;
  border-radius: 6px;
  font-size: 14px;
  line-height: 1.6;
  resize: vertical;
}

.gallery-preview-section {
  margin-top: 24px;
}

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
  padding-bottom: 12px;
  border-bottom: 2px solid #e0e0e0;
}

.section-header h4 {
  margin: 0;
  font-size: 16px;
  color: #333;
}

.hint-text {
  font-size: 13px;
  color: #666;
}

.gallery-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 20px;
}

.gallery-item {
  border: 1px solid #e0e0e0;
  border-radius: 8px;
  padding: 12px;
  background: white;
  transition: all 0.3s;
}

.gallery-item:hover {
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
  transform: translateY(-2px);
}

.image-wrapper {
  position: relative;
  width: 100%;
  height: 200px;
  border-radius: 6px;
  overflow: hidden;
  margin-bottom: 12px;
  background: #f5f5f5;
}

.image-wrapper img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.image-index {
  position: absolute;
  top: 8px;
  left: 8px;
  background: rgba(0, 0, 0, 0.7);
  color: white;
  padding: 4px 10px;
  border-radius: 12px;
  font-size: 12px;
  font-weight: 600;
}

.caption-input {
  width: 100%;
  padding: 10px;
  border: 1px solid #ddd;
  border-radius: 4px;
  font-size: 13px;
  margin-bottom: 12px;
}

.item-actions {
  display: flex;
  gap: 8px;
}

.btn-move {
  flex: 1;
  padding: 8px;
  font-size: 12px;
  background: #f5f5f5;
  border: 1px solid #ddd;
  border-radius: 4px;
  cursor: pointer;
}

.btn-move:hover:not(:disabled) {
  background: #e8e8e8;
}

.btn-move:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.btn-delete {
  flex: 1;
  padding: 8px;
  font-size: 12px;
  background: #fff5f5;
  color: #e53e3e;
  border: 1px solid #feb2b2;
  border-radius: 4px;
  cursor: pointer;
}

.btn-delete:hover {
  background: #fed7d7;
}

.empty-gallery {
  text-align: center;
  padding: 60px 20px;
  background: #f9f9f9;
  border: 2px dashed #ddd;
  border-radius: 8px;
  color: #666;
}

.empty-gallery p {
  margin: 0;
  font-size: 15px;
}

@media (max-width: 768px) {
  .gallery-grid {
    grid-template-columns: 1fr;
  }

  .section-header {
    flex-direction: column;
    align-items: flex-start;
    gap: 8px;
  }
}
</style>
