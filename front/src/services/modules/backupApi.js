import http from '../http'

/**
 * 创建备份
 */
export async function createBackupApi() {
  const { data } = await http.post('/v1/backup/create')
  return data.data
}

/**
 * 获取备份列表
 */
export async function listBackupsApi() {
  const { data } = await http.get('/v1/backup/list')
  return data.data
}

/**
 * 下载备份文件
 */
export async function downloadBackupApi(fileName) {
  const response = await http.get(`/v1/backup/download/${fileName}`, {
    responseType: 'blob'
  })
  return response.data
}

/**
 * 删除备份文件
 */
export async function deleteBackupApi(fileName) {
  const { data } = await http.delete(`/v1/backup/${fileName}`)
  return data.data
}

/**
 * 上传并恢复备份
 */
export async function restoreBackupApi(file) {
  const formData = new FormData()
  formData.append('file', file)
  const { data } = await http.post('/v1/backup/restore', formData, {
    headers: {
      'Content-Type': 'multipart/form-data'
    }
  })
  return data.data
}

/**
 * 从备份列表恢复
 */
export async function restoreFromListApi(fileName) {
  const { data } = await http.post(`/v1/backup/restore/${fileName}`)
  return data.data
}
