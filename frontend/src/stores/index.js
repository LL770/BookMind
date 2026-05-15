import { defineStore } from 'pinia'
import { ref, computed, reactive } from 'vue'
import { authAPI, bookAPI, searchAPI } from '@/api'

/**
 * 用户状态管理
 */
export const useUserStore = defineStore('user', () => {
  // 状态
  const user = ref(null)
  const token = ref(localStorage.getItem('token') || null)
  const isAuthenticated = computed(() => !!token.value)

  //  Actions
  async function login(username, password) {
    try {
      const res = await authAPI.login({ username, password })
      token.value = res.data.token
      user.value = res.data.user
      
      // 保存到 localStorage
      localStorage.setItem('token', token.value)
      localStorage.setItem('user', JSON.stringify(user.value))

      // 重置各 store 数据
      resetAllStores()
      
      return { success: true }
    } catch (error) {
      return { success: false, message: error.message }
    }
  }

  async function register(username, password, phone) {
    try {
      // 1. 先注册
      await authAPI.register({ username, password, phone })
      // 2. 注册成功后自动登录
      const loginRes = await authAPI.login({ username, password })
      token.value = loginRes.data.token
      user.value = loginRes.data.user

      localStorage.setItem('token', token.value)
      localStorage.setItem('user', JSON.stringify(user.value))

      resetAllStores()

      return { success: true }
    } catch (error) {
      return { success: false, message: error.message }
    }
  }

  async function logout() {
    try {
      await authAPI.logout()
    } catch (error) {
      console.error('退出登录失败', error)
    } finally {
      // 清除本地状态
      token.value = null
      user.value = null
      localStorage.removeItem('token')
      localStorage.removeItem('user')
      resetAllStores()
    }
  }

  async function loadUser() {
    if (!token.value) return
    
    try {
      const res = await authAPI.getCurrentUser()
      user.value = res.data
      localStorage.setItem('user', JSON.stringify(user.value))
      return true
    } catch (error) {
      // 用户信息失效，清除 token
      logout()
      return false
    }
  }

  // 初始化：从 localStorage 恢复并验证 token
  async function init() {
    const savedToken = localStorage.getItem('token')
    const savedUser = localStorage.getItem('user')
    
    if (savedToken) {
      token.value = savedToken
    }
    if (savedUser) {
      try {
        user.value = JSON.parse(savedUser)
      } catch (e) {
        localStorage.removeItem('user')
      }
    }
    // 异步验证 token 是否有效
    if (savedToken) {
      const ok = await loadUser()
      if (!ok) {
        resetAllStores()
      }
    }
  }

  return {
    user,
    token,
    isAuthenticated,
    login,
    register,
    logout,
    loadUser,
    init,
  }
})

function resetAllStores() {
  const bookStore = useBookStore()
  const categoryStore = useCategoryStore()
  const searchStore = useSearchStore()
  const chatStore = useChatStore()
  bookStore.reset()
  categoryStore.reset()
  searchStore.clearResults()
  chatStore.clear()
}

/**
 * 书籍状态管理
 */
export const useBookStore = defineStore('book', () => {
  const bookList = ref([])
  const currentBook = ref(null)
  const loading = ref(false)

  async function loadBookList(params = {}) {
    loading.value = true
    try {
      const res = await bookAPI.getList(params)
      bookList.value = res.data.records || []
      return res.data
    } catch (error) {
      console.error('加载书籍列表失败', error)
      throw error
    } finally {
      loading.value = false
    }
  }

  async function loadBook(bookId) {
    loading.value = true
    try {
      const res = await bookAPI.getById(bookId)
      currentBook.value = res.data
      return res.data
    } catch (error) {
      console.error('加载书籍详情失败', error)
      throw error
    } finally {
      loading.value = false
    }
  }

  async function uploadBook(formData) {
    try {
      const res = await bookAPI.upload(formData)
      // 添加到列表
      bookList.value.unshift(res.data)
      return res.data
    } catch (error) {
      console.error('上传书籍失败', error)
      throw error
    }
  }

  async function deleteBook(bookId) {
    try {
      await bookAPI.delete(bookId)
      // 从列表移除
      const index = bookList.value.findIndex(b => b.id === bookId)
      if (index !== -1) {
        bookList.value.splice(index, 1)
      }
      if (currentBook.value?.id === bookId) {
        currentBook.value = null
      }
    } catch (error) {
      console.error('删除书籍失败', error)
      throw error
    }
  }

  function reset() {
    bookList.value = []
    currentBook.value = null
    loading.value = false
  }

  return {
    bookList,
    currentBook,
    loading,
    loadBookList,
    loadBook,
    uploadBook,
    deleteBook,
    reset,
  }
})

/**
 * 阅读器状态管理
 */
export const useReaderStore = defineStore('reader', () => {
  const chapter = ref(null)
  const currentChapterNumber = ref(1)
  const totalChapters = ref(0)
  const notes = ref([])
  const loading = ref(false)

  async function loadChapter(bookId, chapterNumber) {
    loading.value = true
    try {
      const res = await readerAPI.getChapter(bookId, chapterNumber)
      chapter.value = res.data
      currentChapterNumber.value = chapter.value.chapterNumber
      totalChapters.value = chapter.value.totalChapters || 100

      // 加载本章标注
      await loadAnnotations(bookId, chapterNumber)

      return res.data
    } catch (error) {
      console.error('加载章节失败', error)
      throw error
    } finally {
      loading.value = false
    }
  }

  async function loadAnnotations(bookId, chapterNumber) {
    try {
      const res = await readerAPI.getChapterAnnotations(bookId, chapterNumber)
      notes.value = res.data.notes || []
    } catch (error) {
      console.error('加载标注失败', error)
    }
  }

  async function updateProgress(bookId, params) {
    try {
      await readerAPI.updateProgress(bookId, params)
    } catch (error) {
      console.error('更新进度失败', error)
    }
  }

  async function addNote(params) {
    try {
      const res = await noteAPI.create(params)
      notes.value.push(res.data)
      return res.data
    } catch (error) {
      console.error('添加笔记失败', error)
      throw error
    }
  }

  return {
    chapter,
    currentChapterNumber,
    totalChapters,
    notes,
    loading,
    loadChapter,
    loadAnnotations,
    updateProgress,
    addNote,
  }
})

/**
 * 搜索状态管理
 */
export const useSearchStore = defineStore('search', () => {
  const searchResults = ref(null)
  const searching = ref(false)

  async function search(query, topK = 5) {
    searching.value = true
    try {
      const res = await searchAPI.globalSearch(query, topK)
      const data = res.data
      // 前端过滤 score <= 0.01 的低相关结果
      if (data.books) {
        data.books = data.books.filter(b => b.score > 0.01)
        data.total = (data.books?.length || 0) + (data.notes?.length || 0)
      }
      searchResults.value = data
      return data
    } catch (error) {
      console.error('搜索失败', error)
      throw error
    } finally {
      searching.value = false
    }
  }

  function clearResults() {
    searchResults.value = null
  }

  return {
    searchResults,
    searching,
    search,
    clearResults,
  }
})

// === 对话持久化 Store（跨页面保留对话记录） ===

export const useChatStore = defineStore('chat', () => {
  const savedMessages = ref({ global: [], book: [] })

  function save(mode, messages) {
    savedMessages.value[mode] = JSON.parse(JSON.stringify(messages))
    localStorage.setItem('chat_saved_' + mode, JSON.stringify(savedMessages.value[mode]))
  }

  function restore(mode) {
    return savedMessages.value[mode] || []
  }

  function restoreAll() {
    try {
      const g = JSON.parse(localStorage.getItem('chat_saved_global') || '[]')
      const b = JSON.parse(localStorage.getItem('chat_saved_book') || '[]')
      savedMessages.value = { global: g, book: b }
    } catch (e) { /* ignore */ }
  }

  function clear(mode) {
    if (mode) { savedMessages.value[mode] = []; localStorage.removeItem('chat_saved_' + mode) }
    else { savedMessages.value = { global: [], book: [] }; localStorage.removeItem('chat_saved_global'); localStorage.removeItem('chat_saved_book') }
  }

  return { savedMessages, save, restore, restoreAll, clear }
})

// === 分类共享 Store（主页 + 上传页同步） ===
import { categoryAPI } from '@/api'

export const useCategoryStore = defineStore('category', () => {
  const list = ref([])
  const rev = ref(0)
  let lastLoad = 0

  async function load() {
    const now = Date.now()
    if (now - lastLoad < 2000) return
    lastLoad = now
    try {
      const r = await categoryAPI.getAll()
      const data = r.data
      if (data && data.length > 0) {
        list.value = data.map(c => ({ id: c.id, label: c.name, emoji: c.emoji, builtin: c.builtin === 1 }))
        rev.value++
      }
    } catch (e) { /* 静默 */ }
  }

  // 共享表情选择器状态（3×3 网格，上下±3 左右±1）
  const allEmojis = ['📖','💻','📚','🔬','💼','🎨','🧘','📂','🌟','🎵','✈️','🏠','⚽','🎮','📝','💡','🎯','🎭','🎪','🚀','🛸','🌈','🍀','🌺','🦋','🐳','🦊','🌍','🎬','🎤','📡','🕹️']
  const ep = reactive({ idx: 0, selected: '📖' })
  function epNav(dx, dy) {
    const len = allEmojis.length
    ep.idx = (ep.idx + dx + dy * 3 + len) % len
    ep.selected = allEmojis[ep.idx]
  }
  function epOpen(emoji) {
    ep.idx = Math.max(0, allEmojis.indexOf(emoji))
    ep.selected = emoji
  }
  function epRow(offset) {
    const a = allEmojis, len = a.length
    const base = (ep.idx + offset * 3 + len) % len
    return [a[(base - 1 + len) % len], a[base], a[(base + 1) % len]]
  }
  function epKeydown(e) {
    switch (e.key) {
      case 'ArrowUp': e.preventDefault(); epNav(0, -1); break
      case 'ArrowDown': e.preventDefault(); epNav(0, 1); break
      case 'ArrowLeft': e.preventDefault(); epNav(-1, 0); break
      case 'ArrowRight': e.preventDefault(); epNav(1, 0); break
    }
  }

  function reset() {
    list.value = []
    rev.value = 0
    lastLoad = 0
  }

  return { list, rev, load, reset, ep, allEmojis, epNav, epOpen, epRow, epKeydown }
})
