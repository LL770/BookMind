<template>
  <div class="reader-root" :class="{ 'reader-fullscreen': isFullscreen }" :style="readerThemeStyle" @contextmenu.prevent ref="readerRootRef">
    <!-- 顶部工具栏 -->
    <header class="reader-toolbar" v-if="!isFullscreen">
      <div class="toolbar-left">
        <a href="#" @click.prevent="goHome" class="toolbar-back">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M19 12H5M12 19l-7-7 7-7"/></svg>
          书房
        </a>
        <span class="toolbar-divider"></span>
        <span class="toolbar-title">{{ chapter?.title || '加载中...' }}</span>
      </div>
      <div class="toolbar-right">
        <span class="nav-info">
          <input v-model="pageJumpInput" @keydown.enter="jumpToPage" @blur="jumpToPage"
            class="page-jump-input" :placeholder="String(currentChapterNumber)" />
          / {{ totalChapters }} 页
        </span>
        <button @click="showSettings = !showSettings" class="toolbar-btn" title="阅读设置">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="3"/><path d="M19.4 15a1.65 1.65 0 00.33 1.82l.06.06a2 2 0 010 2.83 2 2 0 01-2.83 0l-.06-.06a1.65 1.65 0 00-1.82-.33 1.65 1.65 0 00-1 1.51V21a2 2 0 01-2 2 2 2 0 01-2-2v-.09A1.65 1.65 0 009 19.4a1.65 1.65 0 00-1.82.33l-.06.06a2 2 0 01-2.83 0 2 2 0 010-2.83l.06-.06A1.65 1.65 0 004.68 15a1.65 1.65 0 00-1.51-1H3a2 2 0 01-2-2 2 2 0 012-2h.09A1.65 1.65 0 004.6 9a1.65 1.65 0 00-.33-1.82l-.06-.06a2 2 0 010-2.83 2 2 0 012.83 0l.06.06A1.65 1.65 0 009 4.68a1.65 1.65 0 001-1.51V3a2 2 0 012-2 2 2 0 012 2v.09a1.65 1.65 0 001 1.51 1.65 1.65 0 001.82-.33l.06-.06a2 2 0 012.83 0 2 2 0 010 2.83l-.06.06A1.65 1.65 0 0019.32 9a1.65 1.65 0 001.51 1H21a2 2 0 012 2 2 2 0 01-2 2h-.09a1.65 1.65 0 00-1.51 1z"/></svg>
        </button>
        <button @click="toggleFullscreen" class="toolbar-btn" title="全屏">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M8 3H5a2 2 0 00-2 2v3m18 0V5a2 2 0 00-2-2h-3M8 21H5a2 2 0 01-2-2v-3m18 0v3a2 2 0 01-2 2h-3"/></svg>
        </button>
        <button @click="showSidebar = !showSidebar" class="toolbar-btn" title="笔记">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5"><path d="M11 5H6a2 2 0 00-2 2v11a2 2 0 002 2h11a2 2 0 002-2v-5m-1.414-9.414a2 2 0 112.828 2.828L11.828 15H9v-2.828l8.586-8.586z"/></svg>
        </button>
        <!-- 阅读设置弹窗 -->
        <div v-if="showSettings" class="settings-popup" @click.stop>
          <div class="settings-popup-inner">
            <div class="settings-section">
              <div class="settings-label">字号</div>
              <div class="font-size-row">
                <button @click="adjustFontSize(-1)" class="fs-btn">A−</button>
                <span class="fs-value">{{ fontSize }}px</span>
                <button @click="adjustFontSize(1)" class="fs-btn">A+</button>
              </div>
            </div>
            <div class="settings-section">
              <div class="settings-label">行距</div>
              <div class="font-size-row">
                <button @click="adjustLineHeight(-0.2)" class="fs-btn">−</button>
                <span class="fs-value">{{ lineHeight.toFixed(1) }}</span>
                <button @click="adjustLineHeight(0.2)" class="fs-btn">+</button>
              </div>
            </div>
            <div class="settings-section">
              <div class="settings-label">页宽</div>
              <div class="font-size-row">
                <button @click="adjustPageWidth(-40)" class="fs-btn">−</button>
                <span class="fs-value">{{ pageWidth }}px</span>
                <button @click="adjustPageWidth(40)" class="fs-btn">+</button>
              </div>
            </div>
            <div class="settings-section">
              <div class="settings-label">阅读主题</div>
              <div class="theme-row">
                <button v-for="t in readerThemes" :key="t.id" @click="setReaderTheme(t.id)"
                  class="theme-swatch" :class="{ 'theme-swatch--active': readerTheme === t.id }" :title="t.label">
                  <span class="theme-dot" :style="{ background: t.bg, border: '1px solid ' + (t.id === 'dark' ? '#555' : '#ddd') }">T</span>
                  <span class="theme-label">{{ t.label }}</span>
                </button>
              </div>
            </div>
          </div>
        </div>
      </div>
    </header>

    <!-- 全屏模式下的简约工具栏 -->
    <header class="reader-toolbar reader-toolbar--float" v-if="isFullscreen && showFloatBar">
      <div class="toolbar-left">
        <button @click="exitFullscreen" class="toolbar-btn" title="退出全屏">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M8 3v3a2 2 0 01-2 2H3m18 0h-3a2 2 0 01-2-2V3m0 18v-3a2 2 0 012-2h3M3 16h3a2 2 0 012 2v3"/></svg>
        </button>
      </div>
      <div class="toolbar-center">
        <span class="toolbar-title">{{ chapter?.title }}</span>
      </div>
      <div class="toolbar-right">
        <button @click="showSidebar = !showSidebar" class="toolbar-btn" title="笔记">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5"><path d="M11 5H6a2 2 0 00-2 2v11a2 2 0 002 2h11a2 2 0 002-2v-5m-1.414-9.414a2 2 0 112.828 2.828L11.828 15H9v-2.828l8.586-8.586z"/></svg>
        </button>
      </div>
    </header>

    <!-- 阅读进度条（已移除） -->

    <!-- 主体内容区 -->
    <div class="reader-body">
      <!-- 左侧：章节内容 -->
      <article class="reader-content" ref="contentRef" @mouseup.prevent="handleTextSelection" @touchend.prevent="handleTextSelection" @contextmenu.prevent>
        <!-- 首次加载骨架屏 -->
        <div v-if="loading && !chapter" class="content-skeleton">
          <div class="skeleton" style="height:28px;width:60%;margin-bottom:24px"></div>
          <div v-for="i in 8" :key="i" class="skeleton" :style="{ height: '14px', width: (60 + Math.random() * 35) + '%', marginBottom: '16px' }"></div>
        </div>

        <template v-if="chapter?.content">
          <div v-if="loading && chapter" class="content-loading-overlay"><div class="spinner-mini"></div></div>
          <h1 class="chapter-title">{{ chapter.title }}</h1>
          <div class="chapter-body" @touchend="handleTextSelection">
            <p
              v-for="(para, idx) in paragraphs"
              :key="idx"
              class="chapter-paragraph"
              :class="{ 'para-highlight': highlightedPara === idx }"
              :data-para="idx"
              @click="handleParaClick(idx)"
            >{{ para }}</p>
          </div>
        </template>

        <div v-else-if="!loading" class="content-empty">
          <p>暂无内容</p>
        </div>

        <!-- 底部翻页 -->
        <div class="chapter-nav">
          <button @click="goToPrev" :disabled="currentChapterNumber <= 1" class="btn btn--ghost btn-nav" :class="{ 'btn--hidden': currentChapterNumber <= 1 }">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M19 12H5M12 19l-7-7 7-7"/></svg>
            上一页
          </button>
          <span class="nav-info-bottom">{{ currentChapterNumber }} / {{ totalChapters }} 页</span>
          <button @click="goToNext" :disabled="currentChapterNumber >= totalChapters" class="btn btn--ghost btn-nav" :class="{ 'btn--hidden': currentChapterNumber >= totalChapters }">
            下一页
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M5 12h14M12 5l7 7-7 7"/></svg>
          </button>
        </div>
      </article>

      <!-- 右侧：笔记面板（工具栏图标控制开关） -->
      <div class="sidebar-wrap" v-if="showSidebar">
        <aside class="reader-sidebar">
        <div class="sidebar-header">
          <div class="sidebar-tabs">
          <button v-for="tab in sidebarTabs" :key="tab.id" @click="activeTab = tab.id"
            class="sidebar-tab" :class="{ 'sidebar-tab--active': activeTab === tab.id }">
            {{ tab.label }}
          </button>
        </div>
        </div>

        <div class="sidebar-body">
          <!-- 笔记列表（全书） -->
          <div v-if="activeTab === 'notes'" class="sidebar-list">
            <div v-for="note in allBookNotes" :key="note.id" class="sidebar-item" @click="jumpToNote(note)">
              <div class="sidebar-item-header">
                <span class="note-cat">{{ note.category }}</span>
                <span class="note-time">第{{ chapterMap[note.chapterId] || '?' }}页 · {{ formatTime(note.createTime) }}</span>
              </div>
              <p class="sidebar-item-text">{{ note.content }}</p>
              <p class="sidebar-item-quote" v-if="note.quoteText">"{{ note.quoteText }}"</p>
            </div>
            <div v-if="allBookNotes.length === 0" class="sidebar-empty">选中文字即可创建笔记</div>
          </div>

        </div>
      </aside>
      </div>
    </div>

    <!-- 选词弹出菜单 -->
    <div
      v-if="selectionMenu.show"
      class="selection-menu"
      :style="{ top: selectionMenu.y + 'px', left: selectionMenu.x + 'px' }"
    >
      <button @click="createNote" class="sel-btn" title="记笔记">
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M11 5H6a2 2 0 00-2 2v11a2 2 0 002 2h11a2 2 0 002-2v-5m-1.414-9.414a2 2 0 112.828 2.828L11.828 15H9v-2.828l8.586-8.586z"/></svg>
        笔记
      </button>
      <button @click="copySelection" class="sel-btn" title="复制">
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="9" y="9" width="13" height="13" rx="2"/><path d="M5 15H4a2 2 0 01-2-2V4a2 2 0 012-2h9a2 2 0 012 2v1"/></svg>
      </button>
    </div>

    <!-- 笔记输入弹窗（选中文字后弹出） -->
    <div v-if="noteEditor.show" class="modal-overlay" @click.self="noteEditor.show = false">
      <div class="modal-card" style="max-width:400px;text-align:left">
        <h3 class="modal-title" style="text-align:center">✏️ 新建笔记</h3>
        <p class="note-quote-text">"{{ noteEditor.quoteText }}"</p>
        <select v-model="noteEditor.category" class="input" style="width:100%;margin-bottom:10px">
          <option value="review">💬 感悟</option>
          <option value="question">❓ 疑问</option>
          <option value="quote">📌 摘录</option>
          <option value="association">🔗 联想</option>
        </select>
        <textarea v-model="noteEditor.content" class="input" rows="3" placeholder="写下你的想法..." style="width:100%;resize:vertical"></textarea>
        <div class="modal-actions" style="margin-top:12px">
          <button @click="saveNote" class="modal-btn modal-btn--primary">保存</button>
          <button @click="noteEditor.show = false" class="modal-btn modal-btn--secondary">取消</button>
        </div>
      </div>
    </div>

  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import axios from 'axios'
import request from '@/api/request'

const props = defineProps({
  bookId: { type: Number, required: true },
  chapterNumber: { type: Number, default: 1 }
})
const emit = defineEmits(['chapter-change'])

const route = useRoute()
const router = useRouter()

// 状态
const chapter = ref(null)
const loading = ref(true)
const totalChapters = ref(0)
const chapterNotes = ref([])
const isFullscreen = ref(false)
const showFloatBar = ref(true)
const showSidebar = ref(window.innerWidth > 768)
const activeTab = ref('notes')
const contentRef = ref(null)
const readerRootRef = ref(null)
const highlightedPara = ref(null)
const pageJumpInput = ref('')
const allBookNotes = ref([])
const chapterMap = ref({})
const showSettings = ref(false)
const fontSize = ref(parseInt(localStorage.getItem('reader_font_size') || '16'))
const lineHeight = ref(parseFloat(localStorage.getItem('reader_line_height') || '1.9'))
const pageWidth = ref(parseInt(localStorage.getItem('reader_page_width') || '720'))
const readerTheme = ref(localStorage.getItem('reader_theme') || 'cream')
const readerThemeStyle = computed(() => {
  const t = readerThemes.find(x => x.id === readerTheme.value)
  return t ? {
    '--reader-font-size': fontSize.value + 'px',
    '--reader-line-height': lineHeight.value,
    '--reader-page-width': pageWidth.value + 'px',
    '--reader-bg': t.bg,
    '--reader-color': t.color
  } : {}
})
let floatBarTimer = null

const readerThemes = [
  { id: 'cream', label: '米白',  bg: '#FAF6F0', color: '#2C1810' },
  { id: 'parchment', label: '羊皮纸', bg: '#F5E6C8', color: '#2C1810' },
  { id: 'gray', label: '浅灰',   bg: '#E8E4E0', color: '#1A1A1A' },
  { id: 'dark', label: '暗色',   bg: '#1E1E1E', color: '#EDE6DB' },
  { id: 'green', label: '护眼',   bg: '#E8F0E0', color: '#142B1E' },
]

function adjustFontSize(delta) {
  fontSize.value = Math.max(12, Math.min(50, fontSize.value + delta))
  localStorage.setItem('reader_font_size', String(fontSize.value))
  document.documentElement.style.setProperty('--reader-font-size', fontSize.value + 'px')
}

function adjustLineHeight(delta) {
  const v = Math.round((lineHeight.value + delta) * 10) / 10
  lineHeight.value = Math.max(1.0, Math.min(4.0, v))
  localStorage.setItem('reader_line_height', String(lineHeight.value))
  document.documentElement.style.setProperty('--reader-line-height', String(lineHeight.value))
}

function adjustPageWidth(delta) {
  pageWidth.value = Math.max(400, Math.min(1400, pageWidth.value + delta))
  localStorage.setItem('reader_page_width', String(pageWidth.value))
  document.documentElement.style.setProperty('--reader-page-width', pageWidth.value + 'px')
}

function setReaderTheme(id) {
  readerTheme.value = id
  localStorage.setItem('reader_theme', id)
  const t = readerThemes.find(x => x.id === id)
  if (t) {
    document.documentElement.style.setProperty('--reader-bg', t.bg)
    document.documentElement.style.setProperty('--reader-color', t.color)
  }
}

const sidebarTabs = [
  { id: 'notes', label: '📝 笔记' },
]

// ========== 选词菜单 ==========
const selectionMenu = ref({ show: false, x: 0, y: 0, text: '', paraIndex: null })

function handleTextSelection(e) {
  setTimeout(() => {
    const sel = window.getSelection()
    const text = sel.toString().trim()
    if (!text || text.length < 2) {
      selectionMenu.value.show = false
      return
    }
    const range = sel.getRangeAt(0)
    const rect = range.getBoundingClientRect()
    selectionMenu.value = {
      show: true,
      x: rect.left + rect.width / 2 - 80,
      y: rect.top - 48,
      text,
      paraIndex: findParaIndex(e.target)
    }
    // 仅高亮选中文字 — 由 CSS ::selection 处理，不需要 DOM 操作
  }, 10)
}

function findParaIndex(el) {
  while (el && !el.dataset?.para) el = el.parentElement
  return el ? parseInt(el.dataset.para) : null
}

function copySelection() {
  navigator.clipboard.writeText(selectionMenu.value.text)
  selectionMenu.value.show = false}

// ========== 笔记编辑器 ==========
const noteEditor = ref({ show: false, quoteText: '', content: '', category: 'review', paraIndex: null })

function createNote() {
  noteEditor.value = {
    show: true,
    quoteText: selectionMenu.value.text,
    content: '',
    category: 'review',
    paraIndex: selectionMenu.value.paraIndex
  }
  selectionMenu.value.show = false}

async function saveNote() {
  if (!noteEditor.value.content.trim()) return
  try {
    await request.post('/notes', null, {
      params: {
        bookId: props.bookId,
        chapterId: chapter.value?.id,
        quoteText: noteEditor.value.quoteText,
        content: noteEditor.value.content,
        category: noteEditor.value.category
      }
    })
    noteEditor.value.show = false
    await loadAnnotations()
  } catch (e) { console.error('保存笔记失败', e) }
}

// ========== 数据加载 ==========

/** 阻塞获取总章节数（确保进度条渲染前有值） */
async function fetchTotalChapters() {
  if (!props.bookId) return 0
  try {
    const res = await axios.get(`/api/books/${props.bookId}/chapters`, {
      headers: { 'Authorization': `Bearer ${localStorage.getItem('token')}` }
    })
    if (res.data?.code === 0) {
      const list = res.data.data || []
      if (list.length > 0) return list.length
    }
  } catch (e) { /* ignore */ }
  return 0
}

async function loadChapter() {
  if (!props.bookId || !props.chapterNumber) return

  const cacheKey = props.bookId + ':' + props.chapterNumber
  const cached = chapterCache.get(cacheKey)

  if (cached) {
    chapter.value = cached
    pageJumpInput.value = String(props.chapterNumber)
    loading.value = false
    await nextTick()
    if (route.query.para) scrollToParagraph(parseInt(route.query.para))
    else if (route.query.quote) findAndScrollToQuote(route.query.quote)
    else restoreScrollPosition()
    preloadAdjacentChapters()
    loadAnnotations()
    return
  }

  loading.value = true
  try {
    const res = await axios.get(`/api/reader/${props.bookId}/chapter/${props.chapterNumber}`, {
      headers: { 'Authorization': `Bearer ${localStorage.getItem('token')}` }
    })
    if (res.data.code !== 0 || !res.data.data) {
      loading.value = false; return
    }
    const d = res.data.data
    chapter.value = d.chapter || d

    // 优先用章节 API 返回的 totalChapters，否则阻塞请求
    let tc = d.totalChapters
    if (tc === undefined || tc === 0) tc = await fetchTotalChapters()
    if (tc > 0) totalChapters.value = tc

    pageJumpInput.value = String(props.chapterNumber)
    loading.value = false
    await nextTick()
    if (route.query.para) scrollToParagraph(parseInt(route.query.para))
    else if (route.query.quote) findAndScrollToQuote(route.query.quote)
    else restoreScrollPosition()
    preloadAdjacentChapters()
    loadAnnotations()
  } catch (e) { console.error('加载章节失败', e); loading.value = false }
}

// 监听页码变化 → 重新加载（组件不销毁时页码更新触发）
watch(() => props.chapterNumber, (newVal, oldVal) => {
  if (newVal && newVal !== oldVal) {
    saveReadProgress()
    loadChapter()
  }
})

async function loadAnnotations() {
  // 1. 加载当前页笔记
  try {
    const res = await axios.get(`/api/reader/${props.bookId}/chapter/${props.chapterNumber}/annotations`, {
      headers: { 'Authorization': `Bearer ${localStorage.getItem('token')}` }
    })
    if (res.data.code === 0 && res.data.data) {
      chapterNotes.value = res.data.data.notes || []
    }
  } catch (e) { console.error(e) }

  // 2. 加载全书章节列表（用于 chapterId → chapterNumber 映射），缓存复用
  const cachedList = chapterListCache.get(props.bookId)
  if (cachedList) {
    chapterMap.value = cachedList
  } else {
    try {
      const res = await axios.get(`/api/books/${props.bookId}/chapters`, {
        headers: { 'Authorization': `Bearer ${localStorage.getItem('token')}` }
      })
      if (res.data.code === 0) {
        const map = {}
        ;(res.data.data || []).forEach(ch => { map[ch.id] = ch.chapterNumber })
        chapterMap.value = map
        chapterListCache.set(props.bookId, map)
      }
    } catch (e) { /* ignore */ }
  }

  // 3. 加载全书所有笔记（PaginatedResult，数据在 res.data.data.records）
  try {
    const res = await axios.get(`/api/notes?bookId=${props.bookId}`, {
      headers: { 'Authorization': `Bearer ${localStorage.getItem('token')}` }
    })
    if (res.data.code === 0) {
      allBookNotes.value = res.data.data?.records || []
    }
  } catch (e) { /* ignore */ }
}

function jumpToPage() {
  let page = parseInt(pageJumpInput.value)
  if (isNaN(page) || page <= 0) { pageJumpInput.value = ''; return }
  // 边界限制：超上限→最后页，小于1→第一页
  page = Math.max(1, Math.min(page, totalChapters.value))
  if (page === currentChapterNumber.value) { pageJumpInput.value = ''; return }
  saveReadProgress()
  router.push({ name: 'reader-chapter', params: { bookId: props.bookId, chapterNumber: page } })
  pageJumpInput.value = ''
}

// ========== 计算属性 ==========

const currentChapterNumber = computed(() => chapter.value?.chapterNumber || props.chapterNumber)
const hasPrev = computed(() => currentChapterNumber.value > 1)
const hasNext = computed(() => currentChapterNumber.value < totalChapters.value)

const scrollPercent = ref(0)
let scrollSaveTimer = null
function updateScrollPercent() {
  const el = contentRef.value
  if (!el) return
  const pct = el.scrollTop / (el.scrollHeight - el.clientHeight)
  scrollPercent.value = isFinite(pct) ? Math.min(100, Math.max(0, Math.round(pct * 100))) : 0
  // 停止滚动 3 秒后自动保存进度
  if (scrollSaveTimer) clearTimeout(scrollSaveTimer)
  scrollSaveTimer = setTimeout(saveReadProgress, 3000)
}
// 持久化总章节数（跨导航保留，避免 totalChapters 尚未加载时进度条 0%）
let _persistedTotal = 0
const readProgressPercent = computed(() => {
  const total = totalChapters.value || _persistedTotal
  if (!total) return 0
  _persistedTotal = total
  // 用路由参数而非 currentChapterNumber，避免 chapter.value 延迟导致的旧值
  const chNum = props.chapterNumber
  const chProgress = ((chNum - 1) / total) * 100
  const pageProgress = (1 / total) * (scrollPercent.value / 100) * 100
  return Math.round(chProgress + pageProgress)
})

const paragraphs = computed(() => {
  if (!chapter.value?.content) return []
  let parts = chapter.value.content.split('\n\n').filter(p => p.trim())
  if (parts.length <= 1) {
    parts = chapter.value.content.split('\n').filter(p => p.trim())
  }
  return parts.map(p => p.trim())
})

// ========== 阅读进度持久化 ==========

let saveTimer = null
function saveReadProgress() {
  if (!props.bookId || !currentChapterNumber.value) return Promise.resolve()
  if (!totalChapters.value && !_persistedTotal) return Promise.resolve()
  updateScrollPercent()
  let overallPos = (currentChapterNumber.value - 1) + (scrollPercent.value / 100)
  // 单章书：内容可见即视为已读
  if ((totalChapters.value || _persistedTotal) === 1 && chapter.value) {
    overallPos = 1.0
  }
  return axios.put(`/api/reader/${props.bookId}/progress`, {}, {
    params: {
      chapterNumber: currentChapterNumber.value,
      currentPage: Math.round(overallPos * 100) / 100,
      totalPages: totalChapters.value || _persistedTotal,
    },
    headers: { 'Authorization': `Bearer ${localStorage.getItem('token')}` }
  }).catch(() => {})
}
// 阅读中每 5 秒自动保存进度
function startAutoSave() {
  stopAutoSave()
  saveTimer = setInterval(saveReadProgress, 5000)
}
function stopAutoSave() {
  if (saveTimer) { clearInterval(saveTimer); saveTimer = null }
}

const chapterCache = new Map()
const chapterListCache = new Map()

function restoreScrollPosition() {
  const el = contentRef.value
  if (!el) return
  axios.get(`/api/reader/${props.bookId}/progress`, {
    headers: { 'Authorization': `Bearer ${localStorage.getItem('token')}` }
  }).then(res => {
    if (res.data?.code !== 0 || !res.data?.data) return
    const savedCh = res.data.data.chapterNumber || 1
    if (savedCh !== props.chapterNumber) return  // 仅当同一章才恢复
    const overallPos = res.data.data.currentPage || 0
    const scrollInChapter = Math.max(0, overallPos - (savedCh - 1))
    if (scrollInChapter <= 0) return
    // 等下一帧让浏览器完成布局
    requestAnimationFrame(() => {
      const e2 = contentRef.value
      if (!e2 || e2.scrollHeight <= e2.clientHeight) return
      e2.scrollTop = scrollInChapter * (e2.scrollHeight - e2.clientHeight)
    })
  }).catch(() => {})
}

function loadReadProgress() {
  axios.get(`/api/reader/${props.bookId}/progress`, {
    headers: { 'Authorization': `Bearer ${localStorage.getItem('token')}` }
  }).then(res => {
    if (res.data.code === 0 && res.data.data) { /* 路由 params 控制 */ }
  }).catch(() => {})
}

const MAX_CACHE = 10
function trimCache() {
  if (chapterCache.size > MAX_CACHE) {
    const keys = [...chapterCache.keys()]
    for (let i = 0; i < keys.length - MAX_CACHE; i++) chapterCache.delete(keys[i])
  }
}
async function preloadChapter(chapterNum) {
  const cacheKey = props.bookId + ':' + chapterNum
  if (chapterCache.has(cacheKey)) return
  try {
    const res = await axios.get(`/api/reader/${props.bookId}/chapter/${chapterNum}`, {
      headers: { 'Authorization': `Bearer ${localStorage.getItem('token')}` }
    })
    if (res.data?.code === 0 && res.data?.data) {
      const d = res.data.data
      chapterCache.set(cacheKey, d.chapter || d)
      trimCache()
      if (d.totalChapters !== undefined) totalChapters.value = d.totalChapters
    }
  } catch (e) { /* 静默 */ }
}
async function preloadAdjacentChapters() {
  const n = currentChapterNumber.value
  const max = totalChapters.value || 999
  for (let i = 1; i <= 5; i++) {
    if (n - i >= 1) preloadChapter(n - i)
    if (n + i <= max) preloadChapter(n + i)
  }
}

// ========== 导航 ==========

let navLock = false

async function goToPrev() {
  if (!hasPrev.value || navLock) return
  navLock = true
  try {
    const prevCh = currentChapterNumber.value - 1
    const cacheKey = props.bookId + ':' + prevCh
    if (!chapterCache.has(cacheKey)) await preloadChapter(prevCh)
    saveReadProgress()
    await router.push({ name: 'reader-chapter', params: { bookId: props.bookId, chapterNumber: prevCh } })
  } finally { navLock = false }
}
async function goToNext() {
  if (!hasNext.value || navLock) return
  navLock = true
  try {
    const nextCh = currentChapterNumber.value + 1
    const cacheKey = props.bookId + ':' + nextCh
    if (!chapterCache.has(cacheKey)) await preloadChapter(nextCh)
    saveReadProgress()
    await router.push({ name: 'reader-chapter', params: { bookId: props.bookId, chapterNumber: nextCh } })
  } finally { navLock = false }
}

function goHome() {
  saveReadProgress()
  router.push({ name: 'home' })
}
function handleParaClick(idx) {
  highlightedPara.value = idx
  setTimeout(() => { highlightedPara.value = null }, 1500)
}

// ========== 全屏 ==========

function toggleFullscreen() {
  if (!document.fullscreenElement) {
    document.documentElement.requestFullscreen()
    isFullscreen.value = true
  } else {
    document.exitFullscreen()
    isFullscreen.value = false
  }
}

function exitFullscreen() {
  document.exitFullscreen()
  isFullscreen.value = false
}

// ========== AI 对话侧边栏 ==========

function openChatPanel() {
  emit('open-chat', { bookId: props.bookId, chapter: chapter.value })
}

// ========== 格式化 ==========

function jumpToNote(note) {
  const page = chapterMap.value[note.chapterId]
  if (page) {
    saveReadProgress()
    router.push({
      name: 'reader-chapter',
      params: { bookId: props.bookId, chapterNumber: page },
      query: { quote: note.quoteText ? note.quoteText.substring(0, 100) : undefined }
    })
  }
}

// 滚动到指定段落
function scrollToParagraph(idx) {
  nextTick(() => {
    const el = document.querySelector(`[data-para="${idx}"]`)
    if (el) {
      el.scrollIntoView({ behavior: 'smooth', block: 'center' })
      el.classList.add('para-highlight')
      setTimeout(() => el.classList.remove('para-highlight'), 3000)
    }
  })
}

// 根据引文查找段落并滚动（笔记跳转使用）
function findAndScrollToQuote(quote) {
  if (!quote) return
  nextTick(() => {
    const paragraphs = document.querySelectorAll('.chapter-paragraph')
    for (const el of paragraphs) {
      if (el.textContent.includes(quote)) {
        el.scrollIntoView({ behavior: 'smooth', block: 'center' })
        el.classList.add('para-highlight')
        setTimeout(() => el.classList.remove('para-highlight'), 3000)
        break
      }
    }
  })
}

function formatTime(t) {
  if (!t) return ''
  let dateStr = t
  if (typeof dateStr === 'string' && !dateStr.endsWith('Z') && !dateStr.includes('+')) {
    dateStr = dateStr.replace(' ', 'T') + '+08:00'
  }
  const d = new Date(dateStr)
  const now = new Date()
  const diff = now - d
  if (diff < 60000) return '刚刚'
  if (diff < 3600000) return Math.floor(diff / 60000) + '分钟前'
  if (diff < 86400000) return Math.floor(diff / 3600000) + '小时前'
  return Math.floor(diff / 86400000) + '天前'
}

// ========== 键盘事件 ==========

function onKeyDown(e) {
  if (e.key === 'Escape' && document.fullscreenElement) exitFullscreen()
  if (e.key === 'ArrowLeft' && !e.target.matches('input, textarea')) goToPrev()
  if (e.key === 'ArrowRight' && !e.target.matches('input, textarea')) goToNext()
  if (e.key === 'f' && !e.target.matches('input, textarea')) toggleFullscreen()
}

// ========== 点击外部关闭选词菜单 ==========

function onClickOutside(e) {
  if (selectionMenu.value.show && !e.target.closest('.selection-menu') && !e.target.closest('.chapter-paragraph')) {
    selectionMenu.value.show = false
  }
  if (showSettings.value && !e.target.closest('.settings-popup') && !e.target.closest('.toolbar-btn')) {
    showSettings.value = false
  }
}

// ========== 生命周期 ==========

function onFullscreenChange() { isFullscreen.value = !!document.fullscreenElement }

onMounted(() => {
  loadChapter()
  startAutoSave()          // 阅读中每 5s 自动存进度
  window.addEventListener('keydown', onKeyDown)
  // 滚动监听更新进度
  const contentEl = contentRef.value
  if (contentEl) contentEl.addEventListener('scroll', updateScrollPercent, { passive: true })
  document.addEventListener('mouseup', onClickOutside)
  document.documentElement.classList.add('reader-active')
  document.addEventListener('fullscreenchange', onFullscreenChange)
  // 禁止浏览器右键菜单
  if (readerRootRef.value) {
    readerRootRef.value.oncontextmenu = (e) => { e.preventDefault(); return false }
  }
  // 恢复用户阅读设置
  const savedFontSize = localStorage.getItem('reader_font_size')
  if (savedFontSize) document.documentElement.style.setProperty('--reader-font-size', savedFontSize + 'px')
  const savedLineHeight = localStorage.getItem('reader_line_height')
  if (savedLineHeight) document.documentElement.style.setProperty('--reader-line-height', savedLineHeight)
  const savedPageWidth = localStorage.getItem('reader_page_width')
  if (savedPageWidth) document.documentElement.style.setProperty('--reader-page-width', savedPageWidth + 'px')
  const savedTheme = localStorage.getItem('reader_theme')
  if (savedTheme) {
    const t = readerThemes.find(x => x.id === savedTheme)
    if (t) {
      document.documentElement.style.setProperty('--reader-bg', t.bg)
      document.documentElement.style.setProperty('--reader-color', t.color)
    }
  }
})

onBeforeUnmount(() => {
  stopAutoSave()
  window.removeEventListener('keydown', onKeyDown)
  document.removeEventListener('mouseup', onClickOutside)
  document.removeEventListener('fullscreenchange', onFullscreenChange)
  const contentEl = contentRef.value
  if (contentEl) contentEl.removeEventListener('scroll', updateScrollPercent)
  document.documentElement.classList.remove('reader-active')
  if (document.fullscreenElement) document.exitFullscreen()
})
</script>

<style scoped>
.reader-root {
  background: var(--bg-paper);
  min-height: calc(100vh - var(--header-h));
  display: flex; flex-direction: column;
}
.reader-root.reader-fullscreen {
  min-height: 100vh; background: var(--bg-paper);
}

/* 工具栏 */
.reader-toolbar {
  display: flex; align-items: center; justify-content: space-between;
  padding: 0 20px; height: 48px;
  border-bottom: 1px solid var(--border-light);
  background: rgba(250,246,240,0.95);
  backdrop-filter: blur(8px);
  flex-shrink: 0; position: sticky; top: 0; z-index: 50;
}
.reader-toolbar--float {
  position: fixed; top: 0; left: 0; right: 0; z-index: 50;
  background: rgba(250,246,240,0.85);
  animation: floatBarIn 0.3s ease;
}
@keyframes floatBarIn { from { opacity: 0; transform: translateY(-48px); } to { opacity: 1; transform: translateY(0); } }
.toolbar-left, .toolbar-right { display: flex; align-items: center; gap: 8px; }
.toolbar-center { display: flex; align-items: center; }
.toolbar-back {
  display: flex; align-items: center; gap: 4px; text-decoration: none;
  font-size: 13px; color: var(--text-secondary); transition: color 0.2s;
}
.toolbar-back:hover { color: var(--accent-terracotta); }
.toolbar-divider { width: 1px; height: 20px; background: var(--border-light); }
.toolbar-title { font-size: 14px; font-weight: 500; color: var(--text-primary); max-width: 300px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.toolbar-chapter { font-size: 12px; color: var(--text-muted); }
.page-jump-wrap { display: inline-flex; align-items: center; gap: 4px; }
.page-jump-input {
  width: auto; min-width: 3ch; max-width: 6ch; padding: 2px 4px; border-radius: 4px; border: 1px solid var(--border-light);
  font-size: 12px; text-align: center; background: var(--bg-cream); color: var(--text-primary);
  outline: none; transition: border-color 0.2s;
}
.page-jump-input:focus { border-color: var(--accent-terracotta); }
.page-jump-input::-webkit-inner-spin-button { display: none; }
.page-total { font-size: 12px; color: var(--text-muted); }
.nav-info {
  display: flex; align-items: center; gap: 4px;
  font-size: 12px; color: var(--text-muted); margin-right: 8px;
}
.nav-info-bottom { font-size: 12px; color: var(--text-muted); }
.toolbar-btn {
  width: 32px; height: 32px; border-radius: 8px; border: none;
  background: none; cursor: pointer; color: var(--text-secondary);
  display: flex; align-items: center; justify-content: center;
  transition: all 0.2s;
}
.toolbar-btn:hover { background: rgba(200,180,160,0.25); color: var(--text-primary); }

/* 翻页加载遮罩 */
.content-loading-overlay {
  position: absolute; inset: 0; z-index: 10;
  display: flex; align-items: center; justify-content: center;
  background: rgba(255,255,255,0.3); pointer-events: none;
}
.spinner-mini {
  width: 20px; height: 20px; border: 2px solid var(--border-light);
  border-top-color: var(--accent-terracotta); border-radius: 50%;
  animation: spin 0.6s linear infinite;
}
@keyframes spin { to { transform: rotate(360deg); } }

/* 阅读进度条 */
.reader-progress-bar { height: 3px; background: var(--border-light); flex-shrink: 0; }
.reader-progress-fill { height: 100%; background: var(--accent-terracotta); transition: width 0.2s ease; }

/* 主体 */
.reader-body {
  display: flex; flex: 1;
}

/* 章节内容 */
.reader-content {
  flex: 1; max-width: var(--reader-page-width, 720px); margin: 0 auto;
  padding: 32px 24px;
}
.reader-fullscreen .reader-content { max-width: min(1200px, calc(var(--reader-page-width, 720px) + 80px)); padding: 40px 32px; }

.chapter-title {
  font-family: var(--font-heading); font-size: 26px; font-weight: 700;
  color: var(--text-primary); margin-bottom: 28px; line-height: 1.4;
}
.chapter-body { font-size: var(--reader-font-size, 16px); line-height: var(--reader-line-height, 1.9); color: var(--reader-color, var(--text-primary)); }
.reader-root { background: var(--reader-bg, var(--bg-paper)); transition: background 0.3s; }
.chapter-paragraph {
  margin-bottom: 18px; text-indent: 2em;
  transition: background 0.3s; border-radius: 4px; padding: 4px 8px; margin-left: -8px; margin-right: -8px;
  cursor: default; position: relative;
}
.content-skeleton { padding: 20px 0; }
.content-empty { text-align: center; padding: 60px 0; color: var(--text-muted); }

/* 翻页导航 */
.chapter-nav {
  display: flex; align-items: center; justify-content: space-between;
  margin-top: 40px; padding-top: 20px;
  border-top: 1px solid var(--border-light);
}
.nav-info { font-size: 13px; color: var(--text-muted); }
.btn-nav { font-size: 13px; gap: 4px; }
.btn--hidden { visibility: hidden; pointer-events: none; }

/* 侧边栏 */
/* 折叠/展开按钮 */
.sidebar-wrap { position: relative; display: flex; }
.sidebar-header {
  padding: 6px 12px;
  border-bottom: 2px solid var(--accent-terracotta); flex-shrink: 0;
}

@media (max-width: 768px) {
  .reader-sidebar { width: 260px; position: fixed; top: 0; z-index: 999; max-height: 100vh; }
}

.reader-sidebar {
  width: 240px; border-left: 1px solid var(--border-light);
  display: flex; flex-direction: column;
  background: var(--reader-bg, var(--bg-paper));
  flex-shrink: 0; position: sticky; top: 48px; align-self: flex-start; max-height: calc(100vh - 48px); overflow-y: auto;
}
.sidebar-tabs {
  display: flex; flex: 1; flex-shrink: 0;
}
.sidebar-tab {
  flex: 1; padding: 10px; text-align: center; font-size: 12px; font-weight: 500;
  border: none; background: none; cursor: pointer; color: var(--text-muted);
  border-bottom: 2px solid transparent; transition: all 0.2s;
}
.sidebar-tab--active { color: var(--accent-terracotta); }
.sidebar-body { flex: 1; overflow-y: auto; padding: 12px; }
.sidebar-list { display: flex; flex-direction: column; gap: 10px; }
.sidebar-item { padding: 10px; border-radius: 8px; background: var(--bg-cream); }
.sidebar-item-header { display: flex; justify-content: space-between; margin-bottom: 4px; }
.note-cat { font-size: 11px; color: var(--accent-terracotta); font-weight: 500; }
.note-time { font-size: 11px; color: var(--text-muted); }
.sidebar-item-text { font-size: 13px; color: var(--text-primary); margin-bottom: 4px; }
.sidebar-item-quote { font-size: 12px; color: var(--text-muted); font-style: italic; border-left: 2px solid var(--border-light); padding-left: 8px; }
.sidebar-empty { text-align: center; padding: 24px 0; font-size: 13px; color: var(--text-muted); }

/* 选词菜单 */
.selection-menu {
  position: fixed; z-index: 100;
  display: flex; gap: 2px; padding: 4px 6px;
  background: var(--text-primary); border-radius: 10px;
  box-shadow: 0 4px 16px rgba(44,24,16,0.25);
  animation: selIn 0.15s ease;
}
@keyframes selIn { from { opacity: 0; transform: scale(0.9) translateY(4px); } to { opacity: 1; transform: scale(1) translateY(0); } }
.sel-btn {
  display: flex; align-items: center; gap: 4px;
  padding: 6px 10px; border: none; background: none;
  color: rgba(255,255,255,0.85); font-size: 12px; cursor: pointer;
  border-radius: 6px; transition: all 0.15s; white-space: nowrap;
}
.sel-btn:hover { background: rgba(255,255,255,0.15); color: white; }

/* 笔记弹窗 */
.note-quote-text {
  font-size: 13px; color: var(--text-muted); font-style: italic;
  padding: 8px 12px; background: var(--bg-cream); border-radius: 8px;
  margin-bottom: 12px; border-left: 3px solid var(--accent-gold);
}

/* 主题选择 */
.theme-row { display: flex; flex-wrap: wrap; gap: 6px; }
.theme-swatch {
  cursor: pointer; border: 2px solid var(--border-light); border-radius: var(--radius-sm);
  padding: 6px 8px; text-align: center; transition: all 0.15s;
  background: none; display: flex; align-items: center; gap: 5px; min-width: 60px;
}
.theme-swatch:hover { border-color: var(--accent-terracotta); }
.theme-swatch--active { border-color: var(--accent-terracotta); background: rgba(198,123,92,0.08); }
.theme-dot { width: 18px; height: 18px; border-radius: 50%; display: inline-flex; align-items: center; justify-content: center; font-size: 8px; font-weight: 700; flex-shrink: 0; }
.theme-label { font-size: 11px; color: var(--text-secondary); white-space: nowrap; }

/* ========== 阅读设置弹窗 ========== */
.settings-popup {
  position: absolute; top: 100%; right: 12px; z-index: 60;
  margin-top: 4px;
  background: var(--bg-white); border-radius: var(--radius-md);
  border: 1px solid var(--border-light); box-shadow: var(--shadow-lg);
  width: 220px; animation: popIn 0.15s ease;
}
@keyframes popIn { from { opacity: 0; transform: translateY(-4px) scale(0.97); } to { opacity: 1; transform: translateY(0) scale(1); } }
.settings-popup-inner { padding: 14px; }
.settings-section { margin-bottom: 12px; }
.settings-section:last-child { margin-bottom: 0; }
.settings-label { font-size: 11px; font-weight: 600; color: var(--text-muted); text-transform: uppercase; letter-spacing: 0.5px; margin-bottom: 6px; }
.settings-divider { height: 1px; background: var(--border-light); margin: 10px 0; }

.font-size-row { display: flex; align-items: center; gap: 10px; }
.fs-btn { width: 30px; height: 30px; border-radius: 50%; border: 1px solid var(--border-light); background: none; cursor: pointer; font-size: 14px; display: flex; align-items: center; justify-content: center; color: var(--text-secondary); transition: all 0.15s; }
.fs-btn:hover { border-color: var(--accent-terracotta); color: var(--accent-terracotta); }
.fs-value { font-size: 13px; font-weight: 600; color: var(--text-primary); min-width: 32px; text-align: center; }

.settings-action { display: flex; align-items: center; gap: 8px; padding: 6px 10px; border-radius: var(--radius-sm); border: none; background: none; cursor: pointer; font-size: 13px; color: var(--text-secondary); width: 100%; transition: all 0.15s; }
.settings-action:hover { background: var(--bg-cream); color: var(--text-primary); }

/* 窄屏阅读器 */
@media (max-width: 768px) {
  .reader-content { padding: 24px 16px; }
  .chapter-title { font-size: 22px; }
  .chapter-body { font-size: 15px; }
}
</style>
