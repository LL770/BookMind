/**
 * 通用工具函数
 */

/**
 * 防抖函数
 */
export function debounce(func, wait) {
  let timeout = null
  return function (...args) {
    if (timeout) clearTimeout(timeout)
    timeout = setTimeout(() => {
      func.apply(this, args)
    }, wait)
  }
}

/**
 * 节流函数
 */
export function throttle(func, limit) {
  let inThrottle
  return function (...args) {
    if (!inThrottle) {
      func.apply(this, args)
      inThrottle = true
      setTimeout(() => (inThrottle = false), limit)
    }
  }
}

/**
 * 格式化文件大小
 */
export function formatFileSize(bytes) {
  if (bytes === 0) return '0 B'
  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return (bytes / Math.pow(k, i)).toFixed(2) + ' ' + sizes[i]
}

/**
 * 格式化时间
 */
export function formatTime(time) {
  if (!time) return ''
  let dateStr = time
  if (typeof dateStr === 'string' && !dateStr.endsWith('Z') && !dateStr.includes('+') && dateStr.includes('T')) {
    dateStr += '+08:00'
  }
  const date = new Date(dateStr)
  const now = new Date()
  const diff = now - date

  if (diff < 60000) return '刚刚'
  if (diff < 3600000) return `${Math.floor(diff / 60000)}分钟前`
  if (diff < 86400000) return `${Math.floor(diff / 3600000)}小时前`
  if (diff < 604800000) return `${Math.floor(diff / 86400000)}天前`
  return date.toLocaleDateString()
}

/**
 * 默认分类列表
 */
const DEFAULT_CATEGORIES = [
  { id: 'all', label: '全部', emoji: '📂', builtin: true },
  { id: '小说', label: '小说', emoji: '📖', builtin: true },
  { id: '技术', label: '技术/编程', emoji: '💻', builtin: true },
  { id: '历史', label: '历史/社科', emoji: '📚', builtin: true },
  { id: '科普', label: '科普/科学', emoji: '🔬', builtin: true },
  { id: '商业', label: '商业/经济', emoji: '💼', builtin: true },
  { id: '艺术', label: '艺术/文学', emoji: '🎨', builtin: true },
  { id: '生活', label: '生活/心理', emoji: '🧘', builtin: true },
  { id: '其他', label: '其他', emoji: '📂', builtin: true },
]

/**
 * 获取用户自定义分类（从 localStorage）
 */
export function loadUserCategories() {
  try { return JSON.parse(localStorage.getItem('bookmind_categories') || '[]') }
  catch { return [] }
}

/**
 * 保存用户分类到 localStorage
 */
export function saveUserCategories(list) {
  localStorage.setItem('bookmind_categories', JSON.stringify(list))
}

/**
 * 获取合并后的全部分类（默认 + 用户自定义）
 */
export function getAllCategories() {
  const base = DEFAULT_CATEGORIES.map(c => ({ ...c }))
  const userCats = loadUserCategories()
  userCats.forEach(c => {
    if (!base.find(b => b.id === c.id)) base.push(c)
  })
  return base
}

/**
 * 格式化日期
 */
export function formatDate(date, format = 'YYYY-MM-DD') {
  const d = new Date(date)
  const year = d.getFullYear()
  const month = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  const hours = String(d.getHours()).padStart(2, '0')
  const minutes = String(d.getMinutes()).padStart(2, '0')
  const seconds = String(d.getSeconds()).padStart(2, '0')

  return format
    .replace('YYYY', year)
    .replace('MM', month)
    .replace('DD', day)
    .replace('HH', hours)
    .replace('mm', minutes)
    .replace('ss', seconds)
}

/**
 * 深拷贝
 */
export function deepClone(obj) {
  if (obj === null || typeof obj !== 'object') return obj
  if (obj instanceof Date) return new Date(obj)
  if (obj instanceof Array) return obj.map(item => deepClone(item))
  
  const clonedObj = {}
  for (const key in obj) {
    if (obj.hasOwnProperty(key)) {
      clonedObj[key] = deepClone(obj[key])
    }
  }
  return clonedObj
}

/**
 * 生成唯一 ID
 */
export function generateId() {
  return Date.now().toString(36) + Math.random().toString(36).substr(2)
}

/**
 * 高亮文本
 */
export function highlightText(text, keyword) {
  if (!text || !keyword) return text
  const regex = new RegExp(`(${keyword})`, 'gi')
  return text.replace(regex, '<mark class="bg-yellow-200 dark:bg-yellow-700/50 px-0.5">$1</mark>')
}

/**
 * 截断文本
 */
export function truncateText(text, length = 100, suffix = '...') {
  if (!text) return ''
  if (text.length <= length) return text
  return text.substring(0, length) + suffix
}

/**
 * 获取 URL 参数
 */
export function getUrlParam(name) {
  const params = new URLSearchParams(window.location.search)
  return params.get(name)
}

/**
 * 设置 localStorage
 */
export function setStorage(key, value) {
  try {
    localStorage.setItem(key, JSON.stringify(value))
  } catch (e) {
    console.error('localStorage 设置失败', e)
  }
}

/**
 * 获取 localStorage
 */
export function getStorage(key) {
  try {
    const item = localStorage.getItem(key)
    return item ? JSON.parse(item) : null
  } catch (e) {
    console.error('localStorage 获取失败', e)
    return null
  }
}

/**
 * 移除 localStorage
 */
export function removeStorage(key) {
  localStorage.removeItem(key)
}

/**
 * 消息提示（替代 alert）
 */
export function showMessage(text, type = 'success', duration = 3000) {
  // 创建一个临时的消息元素
  const el = document.createElement('div')
  el.className = `fixed bottom-4 right-4 z-50 px-6 py-3 rounded-lg shadow-lg text-white font-medium transition-all duration-300 ${
    type === 'error' ? 'bg-red-500' : 'bg-green-500'
  }`
  el.textContent = text
  document.body.appendChild(el)

  setTimeout(() => {
    el.style.opacity = '0'
    el.style.transform = 'translateY(20px)'
    setTimeout(() => el.remove(), 300)
  }, duration)
}

/**
 * 确认对话框
 */
export function confirm(message) {
  return new Promise((resolve) => {
    // 简化实现，实际可使用 modal 组件
    resolve(window.confirm(message))
  })
}

/**
 * 检查是否移动端
 */
export function isMobile() {
  return /Android|webOS|iPhone|iPad|iPod|BlackBerry|IEMobile|Opera Mini/i.test(navigator.userAgent)
}

/**
 * 下载文件
 */
export function downloadFile(url, filename) {
  const link = document.createElement('a')
  link.href = url
  link.download = filename || 'download'
  link.target = '_blank'
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
}

/**
 * 复制到剪贴板
 */
export async function copyToClipboard(text) {
  try {
    await navigator.clipboard.writeText(text)
    return true
  } catch (error) {
    console.error('复制失败', error)
    // 降级方案
    const textarea = document.createElement('textarea')
    textarea.value = text
    document.body.appendChild(textarea)
    textarea.select()
    document.execCommand('copy')
    document.body.removeChild(textarea)
    return true
  }
}
