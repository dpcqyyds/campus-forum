<script setup>
import { ref, onMounted } from 'vue'
import { useAuthStore } from '../../stores/auth'
import {
  createBackupApi,
  listBackupsApi,
  downloadBackupApi,
  deleteBackupApi,
  restoreBackupApi,
  restoreFromListApi
} from '../../services/modules/backupApi'

const authStore = useAuthStore()
const backups = ref([])
const loading = ref(false)
const creating = ref(false)
const errorMessage = ref('')
const successMessage = ref('')
const uploadFile = ref(null)
const restoring = ref(false)

onMounted(() => {
  loadBackups()
})

async function loadBackups() {
  loading.value = true
  errorMessage.value = ''
  try {
    const result = await listBackupsApi()
    backups.value = result.backups || []
  } catch (error) {
    errorMessage.value = error.message || '加载备份列表失败'
  } finally {
    loading.value = false
  }
}

async function createBackup() {
  if (!confirm('确定要创建数据库备份吗？')) {
    return
  }

  creating.value = true
  errorMessage.value = ''
  successMessage.value = ''

  try {
    const result = await createBackupApi()
    successMessage.value = '备份创建成功：' + result.fileName
    await loadBackups()
  } catch (error) {
    errorMessage.value = error.message || '创建备份失败'
  } finally {
    creating.value = false
  }
}

async function downloadBackup(fileName) {
  try {
    const blob = await downloadBackupApi(fileName)
    const url = window.URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = fileName
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    window.URL.revokeObjectURL(url)
  } catch (error) {
    errorMessage.value = error.message || '下载备份失败'
  }
}

async function deleteBackup(fileName) {
  if (!confirm(`确定要删除备份文件 ${fileName} 吗？此操作不可恢复。`)) {
    return
  }

  errorMessage.value = ''
  successMessage.value = ''

  try {
    await deleteBackupApi(fileName)
    successMessage.value = '备份文件删除成功'
    await loadBackups()
  } catch (error) {
    errorMessage.value = error.message || '删除备份失败'
  }
}

async function restoreFromList(fileName) {
  if (
    !confirm(
      `确定要从备份 ${fileName} 恢复数据吗？\n\n警告：此操作将覆盖当前所有数据，且不可恢复！\n\n请确保已经备份当前数据。`
    )
  ) {
    return
  }

  if (!confirm('再次确认：您真的要恢复数据吗？这将覆盖所有当前数据！')) {
    return
  }

  restoring.value = true
  errorMessage.value = ''
  successMessage.value = ''

  try {
    await restoreFromListApi(fileName)
    successMessage.value = '数据恢复成功，请重新登录'
    setTimeout(() => {
      authStore.logout()
      window.location.href = '/login'
    }, 2000)
  } catch (error) {
    errorMessage.value = error.message || '数据恢复失败'
  } finally {
    restoring.value = false
  }
}

function handleFileChange(event) {
  const file = event.target.files[0]
  if (file) {
    if (!file.name.endsWith('.sql')) {
      errorMessage.value = '只支持 .sql 格式的备份文件'
      uploadFile.value = null
      return
    }
    uploadFile.value = file
    errorMessage.value = ''
  }
}

async function uploadAndRestore() {
  if (!uploadFile.value) {
    errorMessage.value = '请选择要上传的备份文件'
    return
  }

  if (
    !confirm(
      `确定要从上传的文件恢复数据吗？\n\n警告：此操作将覆盖当前所有数据，且不可恢复！\n\n请确保已经备份当前数据。`
    )
  ) {
    return
  }

  if (!confirm('再次确认：您真的要恢复数据吗？这将覆盖所有当前数据！')) {
    return
  }

  restoring.value = true
  errorMessage.value = ''
  successMessage.value = ''

  try {
    await restoreBackupApi(uploadFile.value)
    successMessage.value = '数据恢复成功，请重新登录'
    uploadFile.value = null
    setTimeout(() => {
      authStore.logout()
      window.location.href = '/login'
    }, 2000)
  } catch (error) {
    errorMessage.value = error.message || '数据恢复失败'
  } finally {
    restoring.value = false
  }
}

function formatFileSize(bytes) {
  if (bytes === 0) return '0 B'
  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return Math.round(bytes / Math.pow(k, i) * 100) / 100 + ' ' + sizes[i]
}

function formatDate(timestamp) {
  const date = new Date(timestamp)
  return date.toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit'
  })
}
</script>

<template>
  <div class="backup-container">
    <div class="page-header">
      <h1>数据备份与恢复</h1>
      <p class="page-description">管理数据库备份，支持手动备份、下载和恢复功能</p>
    </div>

    <div v-if="errorMessage" class="error">{{ errorMessage }}</div>
    <div v-if="successMessage" class="success">{{ successMessage }}</div>

    <!-- 创建备份区域 -->
    <div class="backup-section">
      <h2>创建备份</h2>
      <p class="section-description">
        点击下方按钮创建当前数据库的完整备份。系统会自动保留最近30个备份文件。
      </p>
      <button @click="createBackup" :disabled="creating" class="btn btn-primary">
        {{ creating ? '创建中...' : '立即备份' }}
      </button>
      <p class="hint">提示：系统每天凌晨2点会自动执行备份</p>
    </div>

    <!-- 上传恢复区域 -->
    <div class="backup-section">
      <h2>上传备份文件恢复</h2>
      <p class="section-description warning-text">
        ⚠️ 警告：恢复操作将覆盖当前所有数据，请谨慎操作！
      </p>
      <div class="upload-area">
        <input
          type="file"
          accept=".sql"
          @change="handleFileChange"
          :disabled="restoring"
          class="file-input"
        />
        <button
          @click="uploadAndRestore"
          :disabled="!uploadFile || restoring"
          class="btn btn-danger"
        >
          {{ restoring ? '恢复中...' : '上传并恢复' }}
        </button>
      </div>
    </div>

    <!-- 备份列表 -->
    <div class="backup-section">
      <div class="section-header">
        <h2>备份文件列表</h2>
        <button @click="loadBackups" :disabled="loading" class="btn btn-secondary">
          {{ loading ? '加载中...' : '刷新列表' }}
        </button>
      </div>

      <div v-if="loading" class="loading">加载中...</div>

      <div v-else-if="backups.length === 0" class="empty-state">
        <p>暂无备份文件</p>
      </div>

      <div v-else class="backup-list">
        <table class="backup-table">
          <thead>
            <tr>
              <th>文件名</th>
              <th>文件大小</th>
              <th>创建时间</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="backup in backups" :key="backup.fileName">
              <td class="filename">{{ backup.fileName }}</td>
              <td>{{ formatFileSize(backup.fileSize) }}</td>
              <td>{{ formatDate(backup.createdAt) }}</td>
              <td class="actions">
                <button @click="downloadBackup(backup.fileName)" class="btn-action btn-download">
                  下载
                </button>
                <button
                  @click="restoreFromList(backup.fileName)"
                  :disabled="restoring"
                  class="btn-action btn-restore"
                >
                  恢复
                </button>
                <button @click="deleteBackup(backup.fileName)" class="btn-action btn-delete">
                  删除
                </button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  </div>
</template>

<style scoped>
.backup-container {
  max-width: 1200px;
  margin: 0 auto;
  padding: 24px;
}

.page-header {
  margin-bottom: 32px;
}

.page-header h1 {
  font-size: 28px;
  font-weight: 600;
  color: #1e293b;
  margin-bottom: 8px;
}

.page-description {
  color: #64748b;
  font-size: 14px;
}

.backup-section {
  background: white;
  border-radius: 12px;
  padding: 24px;
  margin-bottom: 24px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1);
}

.backup-section h2 {
  font-size: 18px;
  font-weight: 600;
  color: #1e293b;
  margin-bottom: 12px;
}

.section-description {
  color: #64748b;
  font-size: 14px;
  margin-bottom: 16px;
}

.warning-text {
  color: #dc2626;
  font-weight: 500;
}

.hint {
  margin-top: 12px;
  color: #64748b;
  font-size: 13px;
}

.btn {
  padding: 10px 20px;
  border: none;
  border-radius: 8px;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s ease;
}

.btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.btn-primary {
  background: #0ea5e9;
  color: white;
}

.btn-primary:hover:not(:disabled) {
  background: #0284c7;
}

.btn-secondary {
  background: #64748b;
  color: white;
}

.btn-secondary:hover:not(:disabled) {
  background: #475569;
}

.btn-danger {
  background: #dc2626;
  color: white;
}

.btn-danger:hover:not(:disabled) {
  background: #b91c1c;
}

.upload-area {
  display: flex;
  gap: 12px;
  align-items: center;
}

.file-input {
  flex: 1;
  padding: 8px;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  font-size: 14px;
}

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.loading,
.empty-state {
  text-align: center;
  padding: 40px;
  color: #64748b;
}

.backup-table {
  width: 100%;
  border-collapse: collapse;
}

.backup-table thead {
  background: #f8fafc;
}

.backup-table th {
  padding: 12px;
  text-align: left;
  font-weight: 600;
  color: #475569;
  font-size: 13px;
  border-bottom: 2px solid #e2e8f0;
}

.backup-table td {
  padding: 12px;
  border-bottom: 1px solid #e2e8f0;
  font-size: 14px;
  color: #1e293b;
}

.filename {
  font-family: 'Courier New', monospace;
  color: #0ea5e9;
}

.actions {
  display: flex;
  gap: 8px;
}

.btn-action {
  padding: 6px 12px;
  border: none;
  border-radius: 6px;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s ease;
}

.btn-download {
  background: #0ea5e9;
  color: white;
}

.btn-download:hover {
  background: #0284c7;
}

.btn-restore {
  background: #10b981;
  color: white;
}

.btn-restore:hover:not(:disabled) {
  background: #059669;
}

.btn-restore:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.btn-delete {
  background: #ef4444;
  color: white;
}

.btn-delete:hover {
  background: #dc2626;
}

.error {
  color: #ef4444;
  font-size: 14px;
  margin-bottom: 16px;
  padding: 12px;
  background: #fee2e2;
  border-radius: 8px;
  border-left: 3px solid #ef4444;
}

.success {
  color: #10b981;
  font-size: 14px;
  margin-bottom: 16px;
  padding: 12px;
  background: #d1fae5;
  border-radius: 8px;
  border-left: 3px solid #10b981;
}
</style>
