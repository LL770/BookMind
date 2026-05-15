<template>
  <div class="notes-page">
    <div class="container-main pb-8">
      <!-- 页面标题 -->
      <div class="notes-header">
        <h1 class="notes-title">📝 我的笔记</h1>
        <span class="notes-subtitle">共 {{ stats.totalNotes || 0 }} 条笔记</span>
      </div>

        <!-- 操作栏：筛选 + 批量删除 -->
        <div class="notes-toolbar">
          <div class="filter-group">
            <!-- 分类下拉（自定义带图标） -->
            <div class="filter-dropdown">
              <button @click="catOpen = !catOpen" class="filter-btn">
                <span v-if="filters.category">{{ getCatEmoji(filters.category) }}</span>
                <span>{{ filters.category ? getCatLabel(filters.category) : '所有分类' }}</span>
                <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M6 9l6 6 6-6"/></svg>
              </button>
              <div v-if="catOpen" class="filter-dropdown-menu" @mouseleave="catOpen = false">
                <div @click="filters.category = ''; catOpen = false; loadNotes()" class="filter-dropdown-item" :class="{ 'filter-dropdown-item--sel': !filters.category }">
                  <span>📂</span> 所有分类
                </div>
                <div v-for="cat in categories" :key="cat.id"
                  @click="filters.category = cat.id; filters.bookId = ''; catOpen = false; loadNotes()"
                  class="filter-dropdown-item" :class="{ 'filter-dropdown-item--sel': filters.category === cat.id }">
                  <span>{{ cat.emoji }}</span> {{ cat.label }}
                </div>
              </div>
            </div>

            <!-- 书籍下拉（自定义带图标） -->
            <div class="filter-dropdown">
              <button @click="bookOpen = !bookOpen" class="filter-btn">
                <span>{{ filters.bookId ? getBookTitle(filters.bookId) : '所有书籍' }}</span>
                <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M6 9l6 6 6-6"/></svg>
              </button>
              <div v-if="bookOpen" class="filter-dropdown-menu" @mouseleave="bookOpen = false">
                <div @click="filters.bookId = ''; bookOpen = false; loadNotes()" class="filter-dropdown-item" :class="{ 'filter-dropdown-item--sel': !filters.bookId }">
                  📚 所有书籍
                </div>
                <div v-for="book in books" :key="book.id"
                  @click="filters.bookId = book.id; bookOpen = false; loadNotes()"
                  class="filter-dropdown-item" :class="{ 'filter-dropdown-item--sel': filters.bookId === book.id }">
                  📖 {{ book.title }}
                </div>
              </div>
            </div>

            <input
              v-model="filters.keyword"
              @input="debounceSearch"
              type="text"
              placeholder="搜索笔记..."
              class="input py-2 text-sm w-48"
            />
          </div>

          <div class="filter-actions">
            <button v-if="!batchMode" @click="enterBatchMode" class="btn btn--ghost btn-sm">🗑️ 批量删除</button>
            <template v-if="batchMode">
              <span class="batch-count">已选 {{ selectedIds.size }} 条</span>
              <button @click="confirmBatchDelete" class="btn btn--danger btn-sm" :disabled="selectedIds.size === 0">确认删除</button>
              <button @click="exitBatchMode" class="btn btn--secondary btn-sm">取消</button>
            </template>
          </div>
        </div>

      <!-- 笔记列表 -->
      <div v-if="loading" class="space-y-4">
        <SkeletonLoader v-for="i in 5" :key="i" type="card" />
      </div>

      <div v-else-if="notes.length === 0" class="text-center py-16">
        <div class="text-6xl mb-4">📭</div>
        <h2 class="text-xl font-semibold text-slate-700 dark:text-slate-300 mb-2">
          暂无笔记
        </h2>
        <p class="text-slate-500">在阅读时划选文字可快速创建笔记</p>
      </div>

      <div v-else class="space-y-4">
        <div
          v-for="note in notes"
          :key="note.id"
          class="card p-4 hover:shadow-md transition-shadow"
        >
          <!-- 笔记头部 -->
          <div class="flex items-start justify-between mb-3">
            <div class="flex items-center gap-3">
              <!-- 批量模式：勾选框 -->
              <label v-if="batchMode" class="note-checkbox" @click.stop="toggleNote(note.id)">
                <div class="note-cb" :class="{ 'note-cb--checked': selectedIds.has(note.id) }">
                  <svg v-if="selectedIds.has(note.id)" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="white" stroke-width="3"><path d="M20 6L9 17l-5-5"/></svg>
                </div>
              </label>
              <span class="px-2 py-0.5 text-xs font-medium rounded-full"
                    :class="categoryColors[note.category]">
                {{ categoryEmojis[note.category] }} {{ note.category }}
              </span>
              <span class="text-sm text-slate-500">{{ formatTime(note.createTime) }}</span>
            </div>

            <!-- 操作按钮 -->
            <div class="flex items-center gap-2">
              <button
                @click="expandNote(note)"
                class="text-sm text-slate-500 hover:text-slate-700"
              >
                {{ note.expanded ? '收起' : '展开' }}
              </button>
              <!-- 普通模式：垃圾桶；批量模式：不显示 -->
              <button v-if="!batchMode"
                @click="openDeleteModal(note)"
                class="p-1 rounded hover:bg-red-50 text-slate-400 hover:text-red-500"
              >
                <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V7a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16"/>
                </svg>
              </button>
            </div>
          </div>

          <!-- 引用原文 -->
          <div v-if="note.expanded" class="mb-3 p-3 notes-bg dark:bg-slate-800 rounded-lg">
            <p class="text-sm text-slate-600 dark:text-slate-400 italic">
              "{{ note.quoteText }}"
            </p>
          </div>

          <!-- 笔记内容 -->
          <p class="text-slate-700 dark:text-slate-300">{{ note.content }}</p>

          <!-- 跳转原文 -->
          <button 
            @click="jumpToSource(note)"
            class="mt-3 text-sm text-bookmind-primary hover:underline"
          >
            查看原文 →
          </button>
        </div>
      </div>

      <!-- 删除确认弹窗（单条） -->
      <div v-if="deleteModal.show" class="modal-overlay" @click.self="deleteModal.show = false">
        <div class="modal-card">
          <div class="modal-icon" style="font-size:42px">🗑️</div>
          <h3 class="modal-title">删除笔记</h3>
          <p class="modal-message">确定删除这条笔记吗？<br>此操作不可撤销。</p>
          <div class="modal-actions">
            <button @click="deleteModal.show = false" class="modal-btn modal-btn--secondary">取消</button>
            <button @click="doDeleteNote" class="modal-btn modal-btn--danger">确认删除</button>
          </div>
        </div>
      </div>

      <!-- 批量删除确认弹窗 -->
      <div v-if="batchModal.show" class="modal-overlay" @click.self="batchModal.show = false">
        <div class="modal-card">
          <div class="modal-icon" style="font-size:42px">🗑️</div>
          <h3 class="modal-title">确认删除</h3>
          <p class="modal-message">确定删除选中的 <strong>{{ batchModal.count }}</strong> 条笔记吗？<br>此操作不可撤销。</p>
          <div class="modal-actions">
            <button @click="batchModal.show = false" class="modal-btn modal-btn--secondary">取消</button>
            <button @click="doBatchDelete" class="modal-btn modal-btn--danger">确认删除</button>
          </div>
        </div>
      </div>

      <!-- 分页 -->
      <div v-if="pagination.totalPages > 1" class="flex items-center justify-center gap-2 mt-8">
        <button 
          @click="changePage(pagination.currentPage - 1)"
          :disabled="pagination.currentPage === 1"
          class="btn btn-secondary btn-sm"
        >
          上一页
        </button>
        <span class="text-sm text-slate-500">
          第 {{ pagination.currentPage }} / {{ pagination.totalPages }} 页
        </span>
        <button 
          @click="changePage(pagination.currentPage + 1)"
          :disabled="pagination.currentPage === pagination.totalPages"
          class="btn btn-secondary btn-sm"
        >
          下一页
        </button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { useBookStore } from '@/stores'
import SkeletonLoader from '@/components/SkeletonLoader.vue'
import { noteAPI, bookAPI } from '@/api'

// 笔记数据
const notes = ref([])
const loading = ref(false)
const stats = ref({ totalNotes: 0 })
const books = ref([])
const selectedIds = ref(new Set())
const batchMode = ref(false)
const batchModal = ref({ show: false, count: 0 })
const deleteModal = ref({ show: false, note: null })
const router = useRouter()

// 筛选器
const filters = ref({
  category: '',
  bookId: '',
  keyword: '',
})

// 分页
const pagination = ref({
  currentPage: 1,
  pageSize: 20,
  total: 0,
  totalPages: 0,
})

// 下拉状态
const catOpen = ref(false)
const bookOpen = ref(false)

// 笔记类型标签样式
const categoryColors = {
  review: 'bg-indigo-100 text-indigo-700',
  question: 'bg-amber-100 text-amber-700',
  quote: 'bg-emerald-100 text-emerald-700',
  association: 'bg-violet-100 text-violet-700',
}

const categoryEmojis = {
  review: '💬', question: '❓', quote: '📌', association: '🔗',
}

// 分类筛选（对应笔记类型）
const categories = [
  { id: 'review', label: '书评感悟', emoji: '💬' },
  { id: 'question', label: '疑问思考', emoji: '❓' },
  { id: 'quote', label: '重点摘录', emoji: '📌' },
  { id: 'association', label: '联想关联', emoji: '🔗' },
]

function getCatEmoji(id) {
  if (!id) return '📂'
  const found = categories.find(c => c.id === id)
  return found ? found.emoji : '📂'
}

function getCatLabel(id) {
  if (!id) return ''
  const found = categories.find(c => c.id === id)
  return found ? found.label : id
}

function getBookTitle(id) {
  if (!id) return ''
  const found = books.value.find(b => b.id === id)
  return found ? found.title : '未知'
}

// 防抖搜索
let searchTimer = null
const debounceSearch = () => {
  if (searchTimer) clearTimeout(searchTimer)
  searchTimer = setTimeout(() => {
    pagination.value.currentPage = 1
    loadNotes()
  }, 300)
}

// 点击外部关闭下拉
function handleClickOutside(e) {
  if (catOpen.value && !e.target.closest('.filter-dropdown')) catOpen.value = false
  if (bookOpen.value && !e.target.closest('.filter-dropdown')) bookOpen.value = false
}

// 加载笔记列表
const loadNotes = async () => {
  loading.value = true
  try {
    const params = {
      page: pagination.value.currentPage,
      size: pagination.value.pageSize,
    }
    if (filters.value.category) params.category = filters.value.category
    if (filters.value.bookId) params.bookId = filters.value.bookId
    if (filters.value.keyword) params.keyword = filters.value.keyword

    const res = await noteAPI.getList(params)
    notes.value = res.data.records || []
    pagination.value.total = res.data.total
    pagination.value.totalPages = res.data.pages || Math.ceil(res.data.total / pagination.value.pageSize)

    // 更新统计
    if (filters.value.bookId) {
      try {
        const statsRes = await noteAPI.getBookStats(filters.value.bookId)
        stats.value = statsRes.data
      } catch (e) { /* ignore */ }
    }
    // 更新总笔记数
    stats.value.totalNotes = res.data.total || 0
  } catch (error) {
    console.error('加载笔记失败', error)
  } finally {
    loading.value = false
  }
}

// 加载书籍列表
const loadBooks = async () => {
  try {
    const res = await bookAPI.getList({ size: 100 })
    books.value = res.data.records || []
  } catch (error) {
    console.error('加载书籍失败', error)
  }
}

// 展开/收起笔记
const expandNote = (note) => {
  note.expanded = !note.expanded
}

// 删除笔记
const openDeleteModal = (note) => { deleteModal.value = { show: true, note } }
const doDeleteNote = async () => {
  const note = deleteModal.value.note
  if (!note) return
  try {
    await noteAPI.delete(note.id)
    notes.value = notes.value.filter(n => n.id !== note.id)
    stats.value.totalNotes--
    deleteModal.value.show = false
  } catch (error) {
    console.error('删除笔记失败', error)
  }
}

// 批量模式
function enterBatchMode() { batchMode.value = true; selectedIds.value = new Set() }
function exitBatchMode() { batchMode.value = false; selectedIds.value = new Set() }
function toggleNote(id) {
  const s = selectedIds.value
  if (s.has(id)) s.delete(id); else s.add(id)
  selectedIds.value = new Set(s)
}
function confirmBatchDelete() {
  if (selectedIds.value.size === 0) return
  batchModal.value = { show: true, count: selectedIds.value.size }
}
function doBatchDelete() {
  const ids = [...selectedIds.value]
  noteAPI.deleteBatch(ids).then(() => {
    notes.value = notes.value.filter(n => !selectedIds.value.has(n.id))
    stats.value.totalNotes -= selectedIds.value.size
    exitBatchMode()
    batchModal.value.show = false
  }).catch(e => console.error('批量删除失败', e))
}

// 跳转到原文
const jumpToSource = (note) => {
  router.push({ name: 'reader-chapter', params: { bookId: note.bookId, chapterNumber: note.chapterNumber || 1 } })
}

// 格式化时间
const formatTime = (time) => {
  if (!time) return ''
  let dateStr = time
  if (typeof dateStr === 'string' && !dateStr.endsWith('Z') && !dateStr.includes('+')) {
    // 后端无时区标记，视为 UTC+8
    dateStr = dateStr.replace(' ', 'T') + '+08:00'
  }
  const date = new Date(dateStr)
  if (isNaN(date.getTime())) return time
  const now = new Date()
  const diff = now - date
  if (diff < 60000) return '刚刚'
  if (diff < 3600000) return `${Math.floor(diff / 60000)}分钟前`
  if (diff < 86400000) return `${Math.floor(diff / 3600000)}小时前`
  if (diff < 604800000) return `${Math.floor(diff / 86400000)}天前`
  const y = date.getFullYear(); const m = date.getMonth() + 1; const d = date.getDate()
  return `${y}-${String(m).padStart(2,'0')}-${String(d).padStart(2,'0')}`
}

// 分页
const changePage = (page) => {
  if (page < 1 || page > pagination.value.totalPages) return
  pagination.value.currentPage = page
  loadNotes()
}

// 初始化
onMounted(() => {
  loadNotes()
  loadBooks()
  document.addEventListener('mousedown', handleClickOutside)
})

onUnmounted(() => {
  document.removeEventListener('mousedown', handleClickOutside)
})
</script>

<style scoped>
/* 笔记页面主题统一 */
.notes-page { background: var(--bg-cream); min-height: 100vh; }
.notes-title { font-family: var(--font-heading); font-size: 22px; font-weight: 700; color: var(--text-primary); white-space: nowrap; }
.notes-header { display: flex; align-items: baseline; gap: 12px; margin-bottom: 16px; flex-wrap: wrap; }
.notes-subtitle { font-size: 13px; color: var(--text-muted); }
.notes-bg { background: var(--bg-cream) !important; }
.notes-skel { background: #F0E8DC !important; }
.notes-result-count { font-size: 14px; color: var(--text-secondary); }

.filter-dropdown { position: relative; display: inline-block; }
.filter-btn {
  display: flex; align-items: center; gap: 6px;
  padding: 6px 12px; border-radius: var(--radius-sm);
  border: 1.5px solid #DCD0C0; background: var(--bg-cream);
  cursor: pointer; font-size: 13px; color: var(--text-primary);
  transition: all 0.2s; white-space: nowrap;
}
.filter-btn:hover { border-color: var(--accent-terracotta); }
.filter-dropdown-menu {
  position: absolute; top: 100%; left: 0; z-index: 20; min-width: 160px;
  margin-top: 4px; padding: 4px;
  border-radius: var(--radius-sm); border: 1px solid var(--border-light);
  background: var(--bg-white); box-shadow: var(--shadow-md);
}
.filter-dropdown-item {
  display: flex; align-items: center; gap: 8px;
  padding: 6px 10px; border-radius: 6px;
  cursor: pointer; font-size: 13px; color: var(--text-secondary);
  transition: background 0.15s;
}
.filter-dropdown-item:hover { background: var(--bg-cream); }
.filter-dropdown-item--sel { background: rgba(198,123,92,0.1); color: var(--accent-terracotta); font-weight: 500; }

/* 筛选栏布局 */
.notes-toolbar { display: flex; align-items: center; justify-content: space-between; gap: 10px; flex-wrap: wrap; margin-bottom: 16px; }
.filter-group { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; flex: 1; }
.filter-search { padding: 6px 12px; border-radius: var(--radius-sm); border: 1.5px solid #DCD0C0; background: var(--bg-cream); font-size: 13px; outline: none; width: 160px; transition: border-color 0.2s; }
.filter-search:focus { border-color: var(--accent-terracotta); }
.filter-actions { display: flex; align-items: center; gap: 8px; }
.batch-count { font-size: 13px; color: var(--accent-terracotta); font-weight: 500; }
.modal-btn--danger { background: var(--accent-rose); color: white; }
.modal-btn--danger:hover { background: #A8554A; }

/* 批量勾选框 */
.note-checkbox {
  display: inline-flex; cursor: pointer; line-height: 0; vertical-align: middle;
}
.note-cb {
  width: 18px; height: 18px; border-radius: 4px; border: 2px solid #D4C8B8;
  display: flex; align-items: center; justify-content: center;
  transition: all 0.15s; background: transparent;
}
.note-cb:hover { border-color: var(--accent-rose); }
.note-cb--checked { background: var(--accent-rose); border-color: var(--accent-rose); }

@media (max-width: 768px) {
  .notes-title { font-size: 18px; }
  .notes-header { gap: 8px; }
  .notes-toolbar { flex-direction: column; align-items: stretch; gap: 8px; }
  .filter-group { flex-wrap: wrap; gap: 6px; }
  .filter-search { width: 100%; }
  .filter-actions { justify-content: flex-end; }
  .note-item { padding: 10px; flex-direction: column; gap: 6px; }
  .note-meta { flex-wrap: wrap; gap: 4px; }
  .note-content { font-size: 13px; }
}
</style>
