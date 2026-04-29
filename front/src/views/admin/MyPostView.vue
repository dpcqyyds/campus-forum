<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useAuthStore } from '../../stores/auth'
import { listMyPostsApi, updatePostApi, uploadImagesApi } from '../../services/modules/forumApi'
import { renderMarkdownToHtml } from '../../utils/contentFormat'

const authStore = useAuthStore()
const posts = ref([])
const total = ref(0)
const saving = ref(false)
const editingPostId = ref(null)
const editImages = ref([])
const errorMessage = ref('')
const pageSizeOptions = [10, 20, 50, 100]
const pagination = reactive({
  page: 1,
  pageSize: 20
})

const filters = reactive({
  keyword: '',
  status: '',
  format: ''
})

const editForm = reactive({
  title: '',
  summary: '',
  content: '',
  linkUrl: '',
  linkTitle: '',
  linkSummary: ''
})

const myPosts = computed(() => {
  return posts.value
})
const totalPages = computed(() => Math.max(1, Math.ceil(Number(total.value || 0) / pagination.pageSize)))
const canGoPrev = computed(() => pagination.page > 1)
const canGoNext = computed(() => pagination.page < totalPages.value)

const editingPost = computed(() => posts.value.find((item) => item.id === editingPostId.value) || null)
const editingFormat = computed(() => normalizeEditFormat(editingPost.value?.format))
const canEditMarkdownContent = computed(() => editingFormat.value === 'rich_text')
const canEditPlainTextImages = computed(() => editingFormat.value === 'plain_text')
const canEditExternalLinkFields = computed(() => {
  const post = editingPost.value
  return post?.format === 'external_link' && (post.status === 'draft' || post.status === 'rejected')
})
const markdownPreviewHtml = computed(() => {
  if (!editForm.content) return '<p class="empty-hint">暂无内容</p>'
  return renderMarkdownToHtml(editForm.content)
})

const statusMap = {
  draft: '草稿',
  pending: '待审核',
  published: '已发布',
  rejected: '已驳回',
  hidden: '已下架'
}
const formatMap = {
  rich_text: 'Markdown',
  markdown: 'Markdown',
  plain_text: '普通文本',
  image_gallery: '普通文本',
  external_link: '外链分享'
}
const editableStatuses = new Set(['draft', 'rejected'])

function statusText(status) {
  return statusMap[status] || status
}

function formatText(post) {
  return post?.formatLabel || formatMap[post?.format] || post?.format || '-'
}

function normalizeEditFormat(format) {
  if (format === 'markdown') return 'rich_text'
  if (format === 'image_gallery') return 'plain_text'
  return format || ''
}

async function loadData() {
  errorMessage.value = ''
  try {
    const data = await listMyPostsApi({
      ...filters,
      page: pagination.page,
      pageSize: pagination.pageSize
    })
    posts.value = data.list || []
    total.value = Number(data.total ?? posts.value.length)
  } catch (error) {
    posts.value = []
    total.value = 0
    errorMessage.value = `帖子加载失败：${error.message}`
  }
}

function queryPosts() {
  pagination.page = 1
  loadData()
}

function changePageSize() {
  pagination.page = 1
  loadData()
}

function goPrevPage() {
  if (!canGoPrev.value) return
  pagination.page -= 1
  loadData()
}

function goNextPage() {
  if (!canGoNext.value) return
  pagination.page += 1
  loadData()
}

function startEdit(post) {
  if (!editableStatuses.has(post.status)) return
  clearEditImages()
  editingPostId.value = post.id
  editForm.title = post.title
  editForm.summary = post.summary || ''
  editForm.content = post.content || ''
  editForm.linkUrl = post.linkUrl || ''
  editForm.linkTitle = post.linkTitle || ''
  editForm.linkSummary = post.linkSummary || ''
  editImages.value = buildEditImages(post)
}

function cancelEdit() {
  editingPostId.value = null
  clearEditImages()
}

async function saveEdit() {
  if (!editingPostId.value) return
  if (canEditExternalLinkFields.value && editForm.linkUrl.trim() && !isHttpUrl(editForm.linkUrl)) {
    errorMessage.value = '外链地址格式不正确，请填写 http 或 https 地址。'
    return
  }
  saving.value = true
  errorMessage.value = ''
  try {
    const payload = {
      title: editForm.title,
      summary: editForm.summary,
      content: editForm.content
    }
    if (canEditExternalLinkFields.value) {
      payload.linkUrl = editForm.linkUrl
      payload.linkTitle = editForm.linkTitle
      payload.linkSummary = editForm.linkSummary
    }
    if (canEditPlainTextImages.value) {
      const uploadedUrls = await uploadNewEditImages()
      payload.attachments = buildEditImageUrls(uploadedUrls)
      payload.galleryCaptions = editImages.value.map((item) => item.caption || '')
    }
    await updatePostApi(editingPostId.value, payload)
    editingPostId.value = null
    clearEditImages()
    await loadData()
  } catch (error) {
    errorMessage.value = error.message
  } finally {
    saving.value = false
  }
}

async function softDeletePost(post) {
  const confirmed = window.confirm('确认删除该帖子？\n帖子不会被物理删除，将改为已下架状态。')
  if (!confirmed) return
  saving.value = true
  errorMessage.value = ''
  try {
    await updatePostApi(post.id, { status: 'hidden' })
    if (editingPostId.value === post.id) {
      cancelEdit()
    }
    await loadData()
  } catch (error) {
    errorMessage.value = error.message
  } finally {
    saving.value = false
  }
}

async function withdrawPost(post) {
  saving.value = true
  errorMessage.value = ''
  try {
    await updatePostApi(post.id, { status: 'draft' })
    await loadData()
  } catch (error) {
    errorMessage.value = error.message
  } finally {
    saving.value = false
  }
}

async function republishPost(post) {
  saving.value = true
  errorMessage.value = ''
  try {
    await updatePostApi(post.id, { status: 'pending' })
    await loadData()
  } catch (error) {
    errorMessage.value = error.message
  } finally {
    saving.value = false
  }
}

function canEdit(post) {
  return editableStatuses.has(post.status)
}

function canDelete(post) {
  return post.status === 'published'
}

function canWithdraw(post) {
  return post.status === 'pending'
}

function canRepublish(post) {
  return post.status === 'draft' || post.status === 'rejected'
}

function buildEditImages(post) {
  if (normalizeEditFormat(post?.format) !== 'plain_text') return []
  const attachments = Array.isArray(post.attachments) ? post.attachments : []
  const captions = Array.isArray(post.galleryCaptions) ? post.galleryCaptions : []
  return attachments.map((url, index) => ({
    id: `existing-${index}-${url}`,
    url,
    previewUrl: url,
    name: imageNameFromUrl(url),
    caption: captions[index] || '',
    file: null
  }))
}

function imageNameFromUrl(url) {
  const text = String(url || '')
  const clean = text.split('?')[0]
  const name = clean.slice(clean.lastIndexOf('/') + 1)
  return name ? decodeURIComponent(name) : '已上传图片'
}

function clearEditImages() {
  editImages.value.forEach((item) => {
    if (item.file && item.previewUrl) {
      URL.revokeObjectURL(item.previewUrl)
    }
  })
  editImages.value = []
}

function onEditImageChange(event) {
  const files = Array.from(event.target.files || [])
  if (!files.length) return
  const records = files.map((file, index) => ({
    id: `new-${Date.now()}-${index}-${file.name}`,
    url: '',
    previewUrl: URL.createObjectURL(file),
    name: file.name,
    caption: '',
    file
  }))
  editImages.value = [...editImages.value, ...records]
  event.target.value = ''
}

function removeEditImage(index) {
  const target = editImages.value[index]
  if (!target) return
  if (target.file && target.previewUrl) {
    URL.revokeObjectURL(target.previewUrl)
  }
  editImages.value.splice(index, 1)
}

function moveEditImage(index, direction) {
  const next = index + direction
  if (next < 0 || next >= editImages.value.length) return
  const current = editImages.value[index]
  editImages.value[index] = editImages.value[next]
  editImages.value[next] = current
}

async function uploadNewEditImages() {
  const newItems = editImages.value.filter((item) => item.file)
  if (!newItems.length) return []
  const uploadResult = await uploadImagesApi(newItems.map((item) => item.file))
  const urls = (uploadResult.files || []).map((item) => item.url).filter(Boolean)
  if (urls.length !== newItems.length) {
    throw new Error('图片上传失败，请重新选择图片后再保存。')
  }
  return urls
}

function buildEditImageUrls(uploadedUrls) {
  let uploadIndex = 0
  return editImages.value
    .map((item) => {
      if (item.file) {
        return uploadedUrls[uploadIndex++]
      }
      return item.url
    })
    .filter(Boolean)
}

function isHttpUrl(value) {
  if (!value || !value.trim()) return false
  try {
    const url = new URL(value.trim())
    return url.protocol === 'http:' || url.protocol === 'https:'
  } catch {
    return false
  }
}

onMounted(loadData)
onBeforeUnmount(clearEditImages)
</script>

<template>
  <section class="panel">
    <h2>我的帖子</h2>
    <p class="hint">展示你本人创建的全部状态帖子</p>

    <div class="filter-row">
      <input v-model.trim="filters.keyword" placeholder="按标题搜索" @keyup.enter="queryPosts" />
      <select v-model="filters.status">
        <option value="">全部状态</option>
        <option value="draft">草稿</option>
        <option value="pending">待审核</option>
        <option value="published">已发布</option>
        <option value="rejected">已驳回</option>
        <option value="hidden">已下架</option>
      </select>
      <select v-model="filters.format">
        <option value="">全部格式</option>
        <option value="rich_text">Markdown</option>
        <option value="plain_text">普通文本</option>
        <option value="external_link">外链分享</option>
      </select>
      <button type="button" @click="queryPosts">查询</button>
      <label>
        每页
        <select v-model.number="pagination.pageSize" @change="changePageSize">
          <option v-for="size in pageSizeOptions" :key="size" :value="size">{{ size }}</option>
        </select>
        条
      </label>
    </div>

    <p class="hint">共 {{ total }} 条，第 {{ pagination.page }} / {{ totalPages }} 页</p>
    <p v-if="errorMessage" class="error">{{ errorMessage }}</p>

    <table>
      <thead>
        <tr>
          <th>ID</th>
          <th>标题</th>
          <th>板块</th>
          <th>格式</th>
          <th>状态</th>
          <th>更新时间</th>
          <th>操作</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="item in myPosts" :key="item.id">
          <td>{{ item.id }}</td>
          <td>{{ item.title }}</td>
          <td>{{ item.boardName }}</td>
          <td>{{ formatText(item) }}</td>
          <td>{{ statusText(item.status) }}</td>
          <td>{{ item.updatedAt?.slice(0, 16).replace('T', ' ') }}</td>
          <td>
            <div class="table-actions">
              <button v-if="canEdit(item)" type="button" @click="startEdit(item)">编辑</button>
              <button v-if="canWithdraw(item)" type="button" @click="withdrawPost(item)">撤回</button>
              <button v-if="canRepublish(item)" type="button" @click="republishPost(item)">重新发布</button>
              <button v-if="canDelete(item)" type="button" class="danger" @click="softDeletePost(item)">删除</button>
            </div>
          </td>
        </tr>
        <tr v-if="!myPosts.length">
          <td colspan="7" class="hint">暂无帖子</td>
        </tr>
      </tbody>
    </table>

    <div class="action-row" v-if="total > 0">
      <button type="button" :disabled="!canGoPrev || saving" @click="goPrevPage">上一页</button>
      <span class="page-indicator">第 {{ pagination.page }} / {{ totalPages }} 页</span>
      <button type="button" :disabled="!canGoNext || saving" @click="goNextPage">下一页</button>
    </div>

    <div v-if="editingPostId" class="edit-modal-mask" @click.self="cancelEdit">
      <div class="edit-modal-panel">
        <div class="edit-modal-header">
          <h3>编辑我的帖子 #{{ editingPostId }}</h3>
          <button type="button" class="edit-modal-close" @click="cancelEdit">关闭</button>
        </div>
        <div class="form-grid">
          <label>
            标题
            <input v-model.trim="editForm.title" />
          </label>
          <label>
            摘要
            <input v-model.trim="editForm.summary" />
          </label>
          <label class="full-width">
            正文
            <textarea v-model="editForm.content" rows="8" />
          </label>
          <div v-if="canEditMarkdownContent" class="edit-markdown-preview full-width">
            <div class="edit-section-title">Markdown 预览</div>
            <article class="post-content markdown-preview" v-html="markdownPreviewHtml" />
          </div>
          <div v-if="canEditPlainTextImages" class="edit-image-section full-width">
            <div class="edit-section-header">
              <div>
                <div class="edit-section-title">随帖图片</div>
                <p class="hint">可继续添加、删除图片、调整顺序，并修改每张图片说明。</p>
              </div>
              <label class="edit-add-image-btn">
                继续添加
                <input type="file" accept="image/*" multiple class="file-input-hidden" @change="onEditImageChange" />
              </label>
            </div>
            <div v-if="editImages.length" class="edit-image-grid">
              <div v-for="(img, index) in editImages" :key="img.id" class="edit-image-item">
                <div class="edit-image-preview">
                  <img :src="img.previewUrl" :alt="img.name" />
                  <span>{{ index + 1 }}</span>
                </div>
                <input v-model.trim="img.caption" class="caption-input" placeholder="为这张图片添加说明..." />
                <div class="item-actions">
                  <button type="button" @click="moveEditImage(index, -1)" :disabled="index === 0">上移</button>
                  <button type="button" @click="moveEditImage(index, 1)" :disabled="index === editImages.length - 1">下移</button>
                  <button type="button" class="danger" @click="removeEditImage(index)">删除</button>
                </div>
              </div>
            </div>
            <div v-else class="edit-empty-images">
              <p>当前没有随帖图片</p>
              <label class="edit-add-image-btn">
                选择图片
                <input type="file" accept="image/*" multiple class="file-input-hidden" @change="onEditImageChange" />
              </label>
            </div>
          </div>
          <template v-if="canEditExternalLinkFields">
            <label class="full-width">
              外链地址
              <input v-model.trim="editForm.linkUrl" placeholder="https://example.com/article" />
            </label>
            <label>
              外链标题
              <input v-model.trim="editForm.linkTitle" placeholder="可选，默认使用帖子标题" />
            </label>
            <label>
              外链摘要
              <input v-model.trim="editForm.linkSummary" placeholder="可选" />
            </label>
          </template>
        </div>

        <div class="action-row">
          <button class="primary-btn" type="button" :disabled="saving" @click="saveEdit">
            {{ saving ? '保存中...' : '保存修改' }}
          </button>
          <button type="button" @click="cancelEdit">取消</button>
        </div>
      </div>
    </div>
  </section>
</template>

<style scoped>
.edit-markdown-preview,
.edit-image-section {
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  padding: 14px;
  background: #f8fafc;
}

.edit-markdown-preview .post-content {
  max-height: 360px;
  overflow: auto;
  margin-top: 10px;
  padding: 14px;
  border-radius: 8px;
  background: #ffffff;
}

.edit-section-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 14px;
}

.edit-section-title {
  font-weight: 700;
  color: #0f172a;
}

.edit-section-header .hint {
  margin: 6px 0 0;
}

.edit-add-image-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-height: 34px;
  padding: 0 14px;
  border: 1px solid #2563eb;
  border-radius: 6px;
  background: #eff6ff;
  color: #1d4ed8;
  font-size: 13px;
  font-weight: 700;
  cursor: pointer;
  white-space: nowrap;
}

.edit-add-image-btn:hover {
  background: #dbeafe;
}

.file-input-hidden {
  display: none;
}

.edit-image-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: 14px;
}

.edit-image-item {
  display: grid;
  gap: 10px;
  padding: 10px;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  background: #ffffff;
}

.edit-image-preview {
  position: relative;
  height: 160px;
  overflow: hidden;
  border-radius: 6px;
  background: #e2e8f0;
}

.edit-image-preview img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.edit-image-preview span {
  position: absolute;
  top: 8px;
  left: 8px;
  min-width: 24px;
  height: 24px;
  display: grid;
  place-items: center;
  border-radius: 999px;
  background: rgba(15, 23, 42, 0.78);
  color: #ffffff;
  font-size: 12px;
  font-weight: 700;
}

.caption-input {
  width: 100%;
}

.edit-empty-images {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 18px;
  border: 1px dashed #cbd5e1;
  border-radius: 8px;
  background: #ffffff;
  color: #64748b;
}

.edit-empty-images p {
  margin: 0;
}

@media (max-width: 640px) {
  .edit-section-header,
  .edit-empty-images {
    flex-direction: column;
  }
}
</style>
