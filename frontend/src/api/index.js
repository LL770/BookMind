import request from './request'
import axios from 'axios'

const UPLOAD_BASE = 'https://upload.zreo.top/api'

function uploadRequest() {
  const inst = axios.create({ baseURL: UPLOAD_BASE, timeout: 180000 })
  const token = localStorage.getItem('token')
  if (token) inst.defaults.headers.Authorization = `Bearer ${token}`
  return inst
}

/**
 * 认证相关 API
 */
export const authAPI = {
  // 注册
  register(data) {
    return request.post('/auth/register', null, { params: data })
  },

  // 登录
  login(data) {
    return request.post('/auth/login', null, { params: data })
  },

  // 发送验证码
  sendCode(phone) {
    return request.post('/auth/send-code', null, { params: { phone } })
  },

  // 校验验证码
  verifyCode(phone, code) {
    return request.post('/auth/verify-code', null, { params: { phone, code } })
  },

  // 检查用户名可用性
  checkUsername(username) {
    return request.get('/auth/check-username', { params: { username } })
  },

  // 获取当前用户信息
  getCurrentUser() {
    return request.get('/auth/me')
  },

  // 修改用户名
  updateUsername(newUsername) {
    return request.put('/auth/username', null, { params: { newUsername } })
  },

  // 修改密码
  updatePassword(oldPassword, newPassword) {
    return request.put('/auth/password', null, { params: { oldPassword, newPassword } })
  },

  // 绑定手机号
  bindPhone(phone) {
    return request.put('/auth/phone', null, { params: { phone } })
  },

  // 退出登录
  logout() {
    return request.post('/auth/logout')
  },

  // 注销账号
  deleteAccount() {
    return request.delete('/auth/account')
  },
}

/**
 * 书籍相关 API
 */
export const bookAPI = {
  // 上传书籍（走 upload.zreo.top 隧道不限速）
  upload(formData) {
    return uploadRequest().post('/books/upload', formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    })
  },

  // 上传封面（走 upload.zreo.top 隧道不限速）
  uploadCover(bookId, file) {
    const fd = new FormData(); fd.append('file', file)
    return uploadRequest().put(`/books/${bookId}/cover`, fd, { headers: { 'Content-Type': 'multipart/form-data' } })
  },

  // 获取书籍列表
  getList(params) {
    return request.get('/books', { params })
  },

  // 获取书籍详情
  getById(bookId) {
    return request.get(`/books/${bookId}`)
  },

  // 获取章节列表
  getChapters(bookId) {
    return request.get(`/books/${bookId}/chapters`)
  },

  // 搜索书籍
  search(params) {
    return request.get('/books/search', { params })
  },

  // 更新书籍信息（书名/作者/分类）
  update(bookId, data) {
    return request.put(`/books/${bookId}`, data)
  },

  // 删除书籍
  delete(bookId) {
    return request.delete(`/books/${bookId}`)
  },

}

/**
 * 分片上传 API
 */
export const uploadAPI = {
  // 初始化上传会话（走 upload.zreo.top 隧道不限速）
  init(fileName, fileSize) {
    return uploadRequest().post('/upload/init', null, { params: { fileName, fileSize } })
  },

  // 上传分片
  uploadChunk(uploadId, chunkIndex, file) {
    const fd = new FormData()
    fd.append('file', file)
    fd.append('chunkIndex', chunkIndex)
    return uploadRequest().post(`/upload/${uploadId}/chunk`, fd, {
      headers: { 'Content-Type': 'multipart/form-data' },
      timeout: 120000,
    })
  },

  // 检查分片状态（断点续传）
  checkChunk(uploadId, chunkIndex) {
    return uploadRequest().get(`/upload/${uploadId}/chunk/${chunkIndex}/status`)
  },

  // 完成上传（合并分片并创建书籍）
  complete(uploadId, params) {
    return uploadRequest().post(`/upload/${uploadId}/complete`, null, { params })
  },
}

/**
 * 阅读器相关 API
 */
export const readerAPI = {
  // 获取阅读信息
  getReaderInfo(bookId) {
    return request.get(`/reader/${bookId}`)
  },

  // 获取章节内容
  getChapter(bookId, chapterNumber) {
    return request.get(`/reader/${bookId}/chapter/${chapterNumber}`)
  },

  // 更新阅读进度
  updateProgress(bookId, params) {
    return request.put(`/reader/${bookId}/progress`, null, { params })
  },

  // 获取阅读进度
  getProgress(bookId) {
    return request.get(`/reader/${bookId}/progress`)
  },

  // 获取上一章
  getPreviousChapter(bookId, currentChapter) {
    return request.get(`/reader/${bookId}/chapter/prev`, { params: { currentChapter } })
  },

  // 获取下一章
  getNextChapter(bookId, currentChapter) {
    return request.get(`/reader/${bookId}/chapter/next`, { params: { currentChapter } })
  },

  // 获取章节标注（仅笔记）
  getChapterAnnotations(bookId, chapterNumber) {
    return request.get(`/reader/${bookId}/chapter/${chapterNumber}/annotations`)
  },
}

/**
 * 笔记相关 API
 */
export const noteAPI = {
  // 创建笔记
  create(params) {
    return request.post('/notes', null, { params })
  },

  // 获取笔记列表
  getList(params) {
    return request.get('/notes', { params })
  },

  // 获取笔记详情
  getById(noteId) {
    return request.get(`/notes/${noteId}`)
  },

  // 更新笔记
  update(noteId, params) {
    return request.put(`/notes/${noteId}`, null, { params })
  },

  // 删除笔记
  delete(noteId) {
    return request.delete(`/notes/${noteId}`)
  },

  // 批量删除笔记
  deleteBatch(ids) {
    return request.delete(`/notes/batch`, { params: { ids: ids.join(',') } })
  },

  // 获取书籍笔记统计
  getBookStats(bookId) {
    return request.get(`/notes/stats/${bookId}`)
  },
}

/**
 * AI 对话相关 API
 */
export const chatAPI = {
  // 书籍内 AI 对话（SSE 流式）
  chatWithBook(bookId, message) {
    const token = localStorage.getItem('token')
    const url = `/api/chat/book/${bookId}?message=${encodeURIComponent(message)}&token=${token}`
    return new EventSource(url)
  },

  // 全局 AI 对话（SSE 流式）
  globalChat(message) {
    const token = localStorage.getItem('token')
    const url = `/api/chat/global?message=${encodeURIComponent(message)}&token=${token}`
    return new EventSource(url)
  },

  // 生成章节摘要
  generateChapterSummary(bookId, chapterNumber) {
    return request.post('/chat/summary/chapter', null, {
      params: { bookId, chapterNumber },
    })
  },

  // 生成全书摘要
  generateBookSummary(bookId) {
    return request.post('/chat/summary/book', null, {
      params: { bookId },
    })
  },

  // 获取知识图谱数据
  getKnowledgeGraph(bookId) {
    return request.get(`/chat/graph/${bookId}`)
  },
}

/**
 * 搜索相关 API
 */
export const searchAPI = {
  // 全局搜索
  globalSearch(q, topK = 5) {
    return request.get('/search', { params: { q, topK } })
  },

  // 书籍内搜索
  searchInBook(bookId, q, topK = 10) {
    return request.get(`/search/book/${bookId}`, { params: { q, topK } })
  },

  // 笔记搜索
  searchNotes(q, limit = 20) {
    return request.get('/search/notes', { params: { q, limit } })
  },

  // 网络搜索
  webSearch(q, topK = 8) {
    return request.get('/search/web', { params: { q, topK } })
  },
}

/**
 * 知识图谱相关 API
 */
export const graphAPI = {
  // 获取知识图谱数据
  getGraph(bookId) {
    return request.get(`/graph/${bookId}`)
  },

  // 取消/暂停知识图谱生成
  cancelGraph(bookId) {
    return request.post(`/graph/${bookId}/cancel`)
  },

  // 获取节点详情
  getNode(bookId, nodeId) {
    return request.get(`/graph/${bookId}/node/${nodeId}`)
  },

  // 获取节点相关笔记
  getNodeNotes(bookId, nodeId) {
    return request.get(`/graph/${bookId}/node/${nodeId}/notes`)
  },

  // 获取节点首次出现位置
  getNodeLocation(bookId, nodeId) {
    return request.get(`/graph/${bookId}/node/${nodeId}/location`)
  },
}

/**
 * 分类管理 API
 */
export const categoryAPI = {
  getAll() { return request.get('/categories', { params: { _t: Date.now() } }) },
  create(name, emoji) { return request.post('/categories', null, { params: { name, emoji } }) },
  delete(categoryId) { return request.delete(`/categories/${categoryId}`) },
  update(categoryId, params) { return request.put(`/categories/${categoryId}`, null, { params }) },
}
