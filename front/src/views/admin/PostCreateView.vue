<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useAuthStore } from '../../stores/auth'
import {
  createPostApi,
  listAvailableBoardsApi,
  listPublishedPostsApi,
  uploadImagesApi
} from '../../services/modules/forumApi'
import { normalizeExternalLink, renderMarkdownToHtml } from '../../utils/contentFormat'

const authStore = useAuthStore()
const submitting = ref(false)
const savingDraft = ref(false)
const successMessage = ref('')
const errorMessage = ref('')
const preAuditResult = ref(null)
const boards = ref([])
const richTextRef = ref(null)
const canPublishDirectly = computed(() => {
  const role = authStore.user?.role
  return role === 'super_admin' || role === 'teacher'
})

const formatOptions = [
  { value: 'rich_text', label: '富文本编辑器' },
  { value: 'markdown', label: 'Markdown' },
  { value: 'image_gallery', label: '图文相册' },
  { value: 'external_link', label: '外链分享' }
]

const markdownTips = [
  { label: '一级标题', syntax: '# ', example: '# 这是一级标题' },
  { label: '二级标题', syntax: '## ', example: '## 这是二级标题' },
  { label: '三级标题', syntax: '### ', example: '### 这是三级标题' },
  { label: '加粗', syntax: '**文字**', example: '**这是加粗文字**' },
  { label: '斜体', syntax: '*文字*', example: '*这是斜体文字*' },
  { label: '删除线', syntax: '~~文字~~', example: '~~这是删除线~~' },
  { label: '引用', syntax: '> ', example: '> 这是引用内容' },
  { label: '代码块', syntax: '```\n代码\n```', example: '```javascript\nconsole.log("Hello");\n```' },
  { label: '行内代码', syntax: '`代码`', example: '这是`行内代码`示例' },
  { label: '链接', syntax: '[文字](URL)', example: '[百度](https://www.baidu.com)' },
  { label: '图片', syntax: '![描述](URL)', example: '![图片描述](https://example.com/image.jpg)' },
  { label: '无序列表', syntax: '- ', example: '- 列表项1\n- 列表项2\n- 列表项3' },
  { label: '有序列表', syntax: '1. ', example: '1. 第一项\n2. 第二项\n3. 第三项' },
  { label: '分割线', syntax: '---', example: '---' }
]

const showMarkdownHelp = ref(false)
const showRichTextPreview = ref(false)

const form = reactive({
  title: '',
  summary: '',
  boardId: '',
  format: 'rich_text',
  content: '',
  markdownContent: '',
  galleryDesc: '',
  tagsText: '',
  isTop: false,
  isFeatured: false,
  linkUrl: '',
  linkTitle: '',
  linkSummary: ''
})

const localImages = ref([])

const markdownPreviewHtml = computed(() => renderMarkdownToHtml(form.markdownContent))

const richTextPreviewHtml = computed(() => {
  if (!form.content) return '<p class="empty-hint">暂无内容</p>'
  return form.content
})

const contentPlaceholder = computed(() => {
  if (form.format === 'rich_text') return '直接输入文字内容，或使用上方按钮插入格式化内容'
  if (form.format === 'markdown') return '请输入 Markdown 内容'
  if (form.format === 'external_link') return '请输入分享说明（可选）'
  return '请输入正文内容'
})

const canUsePublishDirectly = computed(() => canPublishDirectly.value)

watch(
  () => form.format,
  (value) => {
    if (value !== 'image_gallery') {
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
  form.markdownContent = ''
  form.galleryDesc = ''
  form.tagsText = ''
  form.isTop = false
  form.isFeatured = false
  form.linkUrl = ''
  form.linkTitle = ''
  form.linkSummary = ''
  preAuditResult.value = null
  clearLocalImages()
}

function clearLocalImages() {
  localImages.value.forEach((item) => URL.revokeObjectURL(item.previewUrl))
  localImages.value = []
}

function addPollOption() {
  form.pollOptions.push('')
}

function removePollOption(index) {
  if (form.pollOptions.length > 2) {
    form.pollOptions.splice(index, 1)
  }
}

function insertRichText(type) {
  const el = richTextRef.value
  if (!el) return

  const start = el.selectionStart ?? form.content.length
  const end = el.selectionEnd ?? start
  const before = form.content.slice(0, start)
  const selected = form.content.slice(start, end)
  const after = form.content.slice(end)

  let insertText = ''
  let cursorOffset = 0

  switch (type) {
    case 'bold':
      insertText = `<strong>${selected || '加粗文字'}</strong>`
      cursorOffset = selected ? insertText.length : 8
      break
    case 'h2':
      insertText = `<h2>${selected || '二级标题'}</h2>`
      cursorOffset = selected ? insertText.length : 4
      break
    case 'blockquote':
      insertText = `<blockquote>${selected || '引用内容'}</blockquote>`
      cursorOffset = selected ? insertText.length : 12
      break
    case 'list':
      insertText = `<ul><li>${selected || '列表项'}</li></ul>`
      cursorOffset = selected ? insertText.length : 8
      break
    case 'link': {
      const url = prompt('请输入链接地址：', 'https://example.com')
      if (!url) return
      const text = selected || prompt('请输入链接文字：', '点击这里') || '链接'
      insertText = `<a href='${url}' target='_blank'>${text}</a>`
      cursorOffset = insertText.length
      break
    }
    case 'image': {
      const url = prompt('请输入图片地址：', 'https://example.com/image.jpg')
      if (!url) return
      const alt = selected || prompt('请输入图片描述（可选）：', '图片') || '图片'
      insertText = `<img src='${url}' alt='${alt}' />`
      cursorOffset = insertText.length
      break
    }
    default:
      return
  }

  form.content = `${before}${insertText}${after}`

  requestAnimationFrame(() => {
    el.focus()
    const cursor = before.length + cursorOffset
    el.setSelectionRange(cursor, cursor)
  })
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

  if (form.format === 'markdown') {
    payload.content = form.markdownContent
    payload.attachments = []
  }

  if (form.format === 'image_gallery') {
    payload.content = form.galleryDesc
    payload.attachments = []
    payload.galleryCaptions = localImages.value.map((item) => item.caption)
  }

  if (form.format === 'external_link') {
    payload.content = normalizeExternalLink(form.linkUrl)
    payload.linkUrl = normalizeExternalLink(form.linkUrl)
    payload.linkTitle = form.linkTitle || form.title
    payload.linkSummary = form.linkSummary
    payload.attachments = []
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
    return '请填写富文本正文。'
  }

  if (form.format === 'markdown' && !form.markdownContent.trim()) {
    return '请填写 Markdown 正文。'
  }

  if (form.format === 'image_gallery' && !localImages.value.length) {
    return '请至少上传一张本地图片。'
  }

  if (form.format === 'external_link' && !form.linkUrl.trim()) {
    return '请输入要分享的外链地址。'
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
    if (form.format === 'image_gallery') {
      const uploadResult = await uploadImagesApi(localImages.value.map((item) => item.file))
      uploadedUrls = (uploadResult.files || [])
        .map((item) => item.url)
        .filter(Boolean)
      if (!uploadedUrls.length) {
        throw new Error('图片上传失败，请检查 /api/v1/uploads/images 接口。')
      }
    }

    const payload = buildPayload()
    if (form.format === 'image_gallery') {
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
    if (form.format === 'image_gallery' && localImages.value.length) {
      const uploadResult = await uploadImagesApi(localImages.value.map((item) => item.file))
      uploadedUrls = (uploadResult.files || []).map((item) => item.url).filter(Boolean)
    }

    const payload = buildPayload('draft')
    if (form.format === 'image_gallery' && uploadedUrls.length) {
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

function insertMarkdownSyntax(tip) {
  const textarea = document.querySelector('textarea[placeholder*="Markdown"]')
  if (!textarea) return

  const start = textarea.selectionStart
  const end = textarea.selectionEnd
  const selectedText = form.markdownContent.substring(start, end)

  let insertText = ''
  let cursorOffset = 0

  // 根据不同的语法类型处理
  if (tip.label === '加粗' || tip.label === '斜体' || tip.label === '删除线' || tip.label === '行内代码') {
    if (selectedText) {
      // 如果有选中文字，包裹选中的文字
      const wrapper = tip.syntax.split('文字')
      insertText = wrapper[0] + selectedText + wrapper[1]
      cursorOffset = insertText.length
    } else {
      // 没有选中文字，插入示例
      insertText = tip.example
      cursorOffset = tip.syntax.split('文字')[0].length
    }
  } else if (tip.label === '链接') {
    const url = prompt('请输入链接地址：', 'https://example.com')
    if (!url) return
    const text = selectedText || prompt('请输入链接文字：', '点击这里') || '链接'
    insertText = `[${text}](${url})`
    cursorOffset = insertText.length
  } else if (tip.label === '图片') {
    const url = prompt('请输入图片地址：', 'https://example.com/image.jpg')
    if (!url) return
    const alt = prompt('请输入图片描述（可选）：', '图片') || '图片'
    insertText = `![${alt}](${url})`
    cursorOffset = insertText.length
  } else {
    // 其他语法直接插入示例
    insertText = tip.example
    if (tip.label.includes('标题') || tip.label === '引用' || tip.label.includes('列表')) {
      cursorOffset = tip.syntax.length
    } else {
      cursorOffset = insertText.length
    }
  }

  // 插入文本
  const before = form.markdownContent.substring(0, start)
  const after = form.markdownContent.substring(end)

  // 确保前后有换行
  const needNewlineBefore = before && !before.endsWith('\n') && (tip.label.includes('标题') || tip.label === '引用' || tip.label.includes('列表') || tip.label === '代码块' || tip.label === '分割线')
  const needNewlineAfter = (tip.label.includes('标题') || tip.label === '引用' || tip.label.includes('列表') || tip.label === '代码块' || tip.label === '分割线')

  form.markdownContent = before + (needNewlineBefore ? '\n' : '') + insertText + (needNewlineAfter ? '\n' : '') + after

  // 设置光标位置
  setTimeout(() => {
    const newPosition = start + (needNewlineBefore ? 1 : 0) + cursorOffset
    textarea.focus()
    textarea.setSelectionRange(newPosition, newPosition)
  }, 0)
}

function toggleMarkdownHelp() {
  showMarkdownHelp.value = !showMarkdownHelp.value
}

function toggleRichTextPreview() {
  showRichTextPreview.value = !showRichTextPreview.value
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
      <h3>富文本发布</h3>
      <p class="hint">使用下方按钮快速插入格式化内容，无需了解 HTML 语法</p>
      <div class="editor-header">
        <div class="rich-toolbar">
          <button type="button" @click="insertRichText('bold')">加粗</button>
          <button type="button" @click="insertRichText('h2')">二级标题</button>
          <button type="button" @click="insertRichText('blockquote')">引用</button>
          <button type="button" @click="insertRichText('list')">列表</button>
          <button type="button" @click="insertRichText('link')">超链接</button>
          <button type="button" @click="insertRichText('image')">插图</button>
        </div>
        <button type="button" class="preview-toggle" @click="toggleRichTextPreview">
          {{ showRichTextPreview ? '隐藏预览' : '显示预览' }}
        </button>
      </div>
      <div class="editor-container" :class="{ 'split-view': showRichTextPreview }">
        <textarea
          ref="richTextRef"
          v-model="form.content"
          :placeholder="contentPlaceholder"
          rows="10"
          class="editor-textarea"
        />
        <div v-if="showRichTextPreview" class="preview-panel">
          <h4>预览效果</h4>
          <article class="post-content" v-html="richTextPreviewHtml" />
        </div>
      </div>
    </div>

    <div class="format-card" v-if="form.format === 'markdown'">
      <h3>Markdown 发布</h3>
      <p class="hint">使用下方按钮快速插入格式，链接和图片会弹出对话框引导填写</p>
      <div class="editor-header">
        <button type="button" class="help-toggle" @click="toggleMarkdownHelp">
          {{ showMarkdownHelp ? '隐藏语法帮助' : '显示语法帮助' }}
        </button>
      </div>
      <div v-if="showMarkdownHelp" class="markdown-help-panel">
        <h4>Markdown 语法快速插入</h4>
        <p class="hint">点击按钮快速插入格式，选中文字后点击可包裹选中内容。链接和图片按钮会弹出对话框引导填写，无需记忆语法</p>
        <div class="syntax-buttons">
          <button
            v-for="tip in markdownTips"
            :key="tip.label"
            type="button"
            class="syntax-btn"
            @click="insertMarkdownSyntax(tip)"
            :title="tip.example"
          >
            {{ tip.label }}
          </button>
        </div>
      </div>
      <p class="hint">左侧编辑，右侧实时预览</p>
      <div class="markdown-split">
        <textarea
          v-model="form.markdownContent"
          :placeholder="contentPlaceholder"
          rows="12"
        />
        <article class="post-content markdown-preview" v-html="markdownPreviewHtml" />
      </div>
    </div>

    <div class="format-card" v-if="form.format === 'image_gallery'">
      <h3>图文相册发布</h3>
      <p class="hint">支持本地选择多张图片，发布时将先上传图片并提交到帖子接口</p>

      <div class="gallery-upload-section">
        <label class="upload-label">
          <span class="label-text">📁 选择图片</span>
          <input type="file" accept="image/*" multiple @change="onLocalImageChange" class="file-input-hidden" />
          <span class="upload-hint">支持 JPG、PNG、GIF 格式，可多选</span>
        </label>

        <label class="gallery-desc-label">
          <span class="label-text">相册说明</span>
          <textarea v-model="form.galleryDesc" rows="4" placeholder="请输入相册的整体说明或介绍..." class="gallery-textarea" />
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
                ↑ 上移
              </button>
              <button type="button" @click="moveImage(index, 1)" :disabled="index === localImages.length - 1" class="btn-move">
                ↓ 下移
              </button>
              <button type="button" class="btn-delete" @click="removeLocalImage(index)">
                🗑️ 删除
              </button>
            </div>
          </div>
        </div>
      </div>

      <div v-else class="empty-gallery">
        <p>📷 还没有选择图片，点击上方"选择图片"按钮开始上传</p>
      </div>
    </div>

    <div class="format-card" v-if="form.format === 'external_link'">
      <h3>外链分享发布</h3>
      <p class="hint">分享外部链接，系统会自动抓取链接信息并生成预览卡片</p>

      <div class="link-form-section">
        <label class="link-label required">
          <span class="label-text">🔗 外链地址</span>
          <input
            v-model.trim="form.linkUrl"
            placeholder="请输入完整的 URL 地址，例如：https://example.com/article/123"
            class="link-input"
          />
          <span class="input-hint">必填项，请确保链接可访问</span>
        </label>

        <div class="link-optional-fields">
          <label class="link-label">
            <span class="label-text">📝 外链标题（可选）</span>
            <input
              v-model.trim="form.linkTitle"
              placeholder="不填写则使用帖子标题"
              class="link-input"
            />
          </label>

          <label class="link-label">
            <span class="label-text">📄 外链摘要（可选）</span>
            <textarea
              v-model="form.linkSummary"
              rows="3"
              placeholder="简要描述链接内容，帮助其他用户了解这个链接..."
              class="link-textarea"
            />
          </label>

          <label class="link-label">
            <span class="label-text">💬 补充说明（可选）</span>
            <textarea
              v-model="form.content"
              :placeholder="contentPlaceholder"
              rows="4"
              class="link-textarea"
            />
            <span class="input-hint">可以添加你对这个链接的评论或推荐理由</span>
          </label>
        </div>
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
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
  gap: 12px;
}

.rich-toolbar {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.rich-toolbar button {
  padding: 6px 12px;
  font-size: 13px;
  background: #f5f5f5;
  border: 1px solid #ddd;
  border-radius: 4px;
  cursor: pointer;
}

.rich-toolbar button:hover {
  background: #e8e8e8;
}

.preview-toggle,
.help-toggle {
  padding: 6px 16px;
  font-size: 13px;
  background: #4a90e2;
  color: white;
  border: none;
  border-radius: 4px;
  cursor: pointer;
  white-space: nowrap;
}

.preview-toggle:hover,
.help-toggle:hover {
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

.markdown-help-panel {
  background: #f9f9f9;
  border: 1px solid #e0e0e0;
  border-radius: 4px;
  padding: 16px;
  margin-bottom: 16px;
}

.markdown-help-panel h4 {
  margin: 0 0 8px 0;
  font-size: 15px;
  color: #333;
}

.syntax-buttons {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 12px;
}

.syntax-btn {
  padding: 6px 12px;
  font-size: 13px;
  background: white;
  border: 1px solid #ddd;
  border-radius: 4px;
  cursor: pointer;
  transition: all 0.2s;
}

.syntax-btn:hover {
  background: #4a90e2;
  color: white;
  border-color: #4a90e2;
  transform: translateY(-1px);
}

.syntax-btn:active {
  transform: translateY(0);
}

.markdown-split {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}

.markdown-split textarea {
  width: 100%;
  padding: 12px;
  border: 1px solid #ddd;
  border-radius: 4px;
  font-family: 'Consolas', 'Monaco', monospace;
  font-size: 14px;
  line-height: 1.6;
  resize: vertical;
}

.markdown-preview {
  border: 1px solid #ddd;
  border-radius: 4px;
  padding: 16px;
  background: #fafafa;
  overflow-y: auto;
  max-height: 500px;
  min-height: 300px;
}

@media (max-width: 768px) {
  .editor-container.split-view,
  .markdown-split {
    grid-template-columns: 1fr;
  }

  .syntax-buttons {
    gap: 6px;
  }

  .syntax-btn {
    padding: 5px 10px;
    font-size: 12px;
  }
}

/* 图文相册样式 */
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

/* 外链分享样式 */
.link-form-section {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.link-label {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.link-label.required .label-text::after {
  content: ' *';
  color: #e53e3e;
}

.link-input {
  width: 100%;
  padding: 12px;
  border: 1px solid #ddd;
  border-radius: 6px;
  font-size: 14px;
  transition: border-color 0.3s;
}

.link-input:focus {
  outline: none;
  border-color: #4a90e2;
  box-shadow: 0 0 0 3px rgba(74, 144, 226, 0.1);
}

.link-textarea {
  width: 100%;
  padding: 12px;
  border: 1px solid #ddd;
  border-radius: 6px;
  font-size: 14px;
  line-height: 1.6;
  resize: vertical;
  transition: border-color 0.3s;
}

.link-textarea:focus {
  outline: none;
  border-color: #4a90e2;
  box-shadow: 0 0 0 3px rgba(74, 144, 226, 0.1);
}

.input-hint {
  font-size: 12px;
  color: #999;
}

.link-optional-fields {
  display: flex;
  flex-direction: column;
  gap: 20px;
  padding: 20px;
  background: #f9f9f9;
  border-radius: 8px;
  border: 1px solid #e0e0e0;
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
