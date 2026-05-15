<template>
  <div class="home-page">
    <div class="container-main">

      <!-- 顶部问候 + 搜索 + 批量操作 -->
      <div class="home-header">
        <div>
          <h1 class="page-title">
            {{ timeGreeting }}，<span class="text-gradient">{{ userStore.user?.username || '书友' }}</span>
          </h1>
          <p class="page-subtitle">{{ readingQuote }}</p>
        </div>
        <div class="header-actions">
          <button @click="showSidebar = !showSidebar" class="btn btn--ghost btn-sm" title="分类">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M3 6h18M3 12h18M3 18h18"/></svg>
          </button>
          <button v-if="!batchMode" @click="batchMode = true" class="btn btn--ghost btn-sm">🗑️ 批量删除</button>
          <template v-else>
            <span class="batch-info">已选 {{ selectedIds.size }} 本</span>
            <button @click="handleBatchDelete" v-if="selectedIds.size > 0" class="btn btn-sm" style="background:var(--accent-rose);color:white">✓ 确认删除</button>
            <button @click="batchMode = false; selectedIds = new Set()" class="btn btn--ghost btn-sm">✕ 取消</button>
          </template>
        </div>
      </div>

      <!-- 左侧分类边栏 + 右侧内容 -->
      <div class="home-layout">
        <aside class="cat-sidebar" :class="{ 'cat-sidebar--hidden': !showSidebar }">
          <!-- 顶部：「全部」+ 下拉箭头 + 关闭（在同一个框内） -->
          <div class="cat-sidebar-header">
            <button @click="catExpanded = !catExpanded" class="cat-current" :title="isAllCategory ? '全部' : getCatLabel(activeCategory)">
              <span class="cat-current-emoji">{{ isAllCategory ? '📂' : getCatEmoji(activeCategory) }}</span>
              <span class="cat-current-label">{{ isAllCategory ? '全部' : getCatLabel(activeCategory) }}</span>
              <svg :class="{ rotated: catExpanded }" class="cat-chevron" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M6 9l6 6 6-6"/></svg>
              <span @click.stop="showSidebar = false" class="cat-close-btn" title="关闭分类">✕</span>
            </button>
          </div>

          <!-- 展开的分类列表（含拖动 + 三点操作） -->
          <div v-if="catExpanded" class="cat-sidebar-list">
            <div
              v-for="(cat, i) in categories"
              :key="cat.id"
              class="cat-sidebar-item-wrap"
            >
              <button
                @click="activeCategory = (cat.id === 'all' || cat.label === '全部') ? 'all' : cat.label; catExpanded = false"
                class="cat-sidebar-item"
                :class="{ 'cat-sidebar-item--active': activeCategory === ((cat.id === 'all' || cat.label === '全部') ? 'all' : cat.label) }"
              >
                <span class="cat-emoji">{{ cat.emoji }}</span>
                <span class="cat-label">{{ cat.label }}</span>
              </button>
              <div v-if="cat.label !== '全部'" class="cat-item-menu">
                <button @click.stop="toggleCatMenu(cat.id)" class="cat-dots-btn" title="操作">⋮</button>
                <div v-if="catMenuId === cat.id" class="cat-drop-menu" @click.stop>
                  <button @click.stop="openRenameModal(cat)" class="cat-drop-item">✏️ 重命名</button>
                  <button @click.stop="deleteCat(cat)" class="cat-drop-item cat-drop-item--danger">🗑️ 删除</button>
                </div>
              </div>
            </div>
            <!-- 新增分类入口 -->
            <button @click="showCategoryEditor = true" class="cat-sidebar-item cat-sidebar-item--edit">
              <span style="font-size:14px">➕</span>
              <span>新增分类</span>
            </button>
          </div>
        </aside>
        <div v-if="showSidebar" class="cat-overlay" @click="showSidebar = false"></div>

        <!-- 重命名分类弹窗 -->
        <div v-if="renameModal.show" class="modal-overlay modal-overlay--cats" @click.self="renameModal.show = false">
          <div class="modal-card" style="max-width:360px;text-align:left">
            <h3 class="modal-title" style="text-align:center;font-size:16px">✏️ 重命名分类</h3>
            <div class="new-cat-body">
              <div class="new-cat-emoji-area">
                <div class="new-cat-emoji-label">图标</div>
                <div class="emoji-slot" @wheel.prevent="epNav(0, $event.deltaY > 0 ? 1 : -1)" @keydown="epKeydown" tabindex="0">
                  <button @click.stop="epNav(0, -1)" class="emoji-btn ep-up" title="上">▲</button>
                  <div class="ep-mid">
                    <button @click.stop="epNav(-1, 0)" class="emoji-btn" title="左">◀</button>
                    <div class="emoji-grid">
                      <span v-for="(row, ri) in [epRow(-1), epRow(0), epRow(1)]" :key="ri" class="emoji-slot-row" :class="ri === 1 ? 'emoji-slot-row--current' : 'emoji-slot-row--dim'">
                        <span v-for="e in row" :key="e" class="emoji-cell" :class="{ 'emoji-cell--sel': e === ep.selected }" @click="epNav(allEmojis.indexOf(e) - ep.idx, 0)">{{ e }}</span>
                      </span>
                    </div>
                    <button @click.stop="epNav(1, 0)" class="emoji-btn" title="右">▶</button>
                  </div>
                  <button @click.stop="epNav(0, 1)" class="emoji-btn ep-down" title="下">▼</button>
                </div>
                <div class="new-cat-preview">已选：{{ ep.selected }}</div>
              </div>
              <div class="new-cat-name-area">
                <label>名称</label>
                <input v-model="renameVal" @keydown.enter="confirmRename" class="new-cat-name-input" placeholder="输入分类名称" />
              </div>
            </div>
            <div class="modal-actions" style="margin-top:16px">
              <button @click="confirmRename" class="modal-btn modal-btn--primary" :disabled="!renameVal.trim()">确定</button>
              <button @click="renameModal.show = false" class="modal-btn modal-btn--secondary">取消</button>
            </div>
          </div>
        </div>

        <!-- 书籍重命名弹窗 -->
        <div v-if="bookRename.show" class="modal-overlay" @click.self="bookRename.show = false">
          <div class="modal-card" style="max-width:360px;text-align:left">
            <h3 class="modal-title" style="text-align:center;font-size:16px">✏️ 重命名书籍</h3>
            <div class="form-stack" style="margin-top:12px">
              <label class="form-label">书名</label>
              <input v-model="bookRename.title" class="input" placeholder="书名" />
              <label class="form-label">作者</label>
              <input v-model="bookRename.author" class="input" placeholder="作者" />
              <label class="form-label">分类</label>
              <select v-model="bookRename.category" class="input">
                <option v-for="c in apiCategories" :key="c.id" :value="c.label">{{ c.emoji }} {{ c.label }}</option>
              </select>
            </div>
            <div class="modal-actions" style="margin-top:16px">
              <button @click="confirmBookRename" class="modal-btn modal-btn--primary" :disabled="!bookRename.title.trim()">确定</button>
              <button @click="bookRename.show = false" class="modal-btn modal-btn--secondary">取消</button>
            </div>
          </div>
        </div>

        <!-- 同名分类提示（z-index高于分类弹窗） -->
        <div v-if="dupModal.show" class="modal-overlay dup-modal" @click.self="dupModal.show = false">
          <div class="modal-card" style="max-width:320px">
            <div class="modal-icon" style="font-size:38px">⚠️</div>
            <h3 class="modal-title">分类已存在</h3>
            <p class="modal-message">已存在同名分类「<strong>{{ dupModal.name }}</strong>」，请使用其他名称。</p>
            <div class="modal-actions">
              <button @click="dupModal.show = false" class="modal-btn modal-btn--primary">知道了</button>
            </div>
          </div>
        </div>

        <!-- 新增分类弹窗 -->
        <div v-if="showCategoryEditor" class="modal-overlay modal-overlay--cats" @click.self="showCategoryEditor = false">
          <div class="modal-card" style="max-width:360px;text-align:left">
            <h3 class="modal-title" style="text-align:center;font-size:16px">➕ 添加分类</h3>
            <div class="new-cat-body">
              <div class="new-cat-emoji-area">
                <div class="new-cat-emoji-label">图标</div>
                <div class="emoji-slot" @wheel.prevent="epNav(0, $event.deltaY > 0 ? 1 : -1)" @keydown="epKeydown" tabindex="0">
                  <button @click.stop="epNav(0, -1)" class="emoji-btn ep-up" title="上">▲</button>
                  <div class="ep-mid">
                    <button @click.stop="epNav(-1, 0)" class="emoji-btn" title="左">◀</button>
                    <div class="emoji-grid">
                      <span v-for="(row, ri) in [epRow(-1), epRow(0), epRow(1)]" :key="ri" class="emoji-slot-row" :class="ri === 1 ? 'emoji-slot-row--current' : 'emoji-slot-row--dim'">
                        <span v-for="e in row" :key="e" class="emoji-cell" :class="{ 'emoji-cell--sel': e === ep.selected }" @click="epNav(allEmojis.indexOf(e) - ep.idx, 0)">{{ e }}</span>
                      </span>
                    </div>
                    <button @click.stop="epNav(1, 0)" class="emoji-btn" title="右">▶</button>
                  </div>
                  <button @click.stop="epNav(0, 1)" class="emoji-btn ep-down" title="下">▼</button>
                </div>
                <div class="new-cat-preview">已选：{{ ep.selected }}</div>
              </div>
              <div class="new-cat-name-area">
                <label>名称</label>
                <input v-model="newCatName" @keydown.enter="confirmAddCat" class="new-cat-name-input" placeholder="输入分类名称" />
              </div>
            </div>
            <div class="modal-actions" style="margin-top:16px">
              <button @click="confirmAddCat" class="modal-btn modal-btn--primary" :disabled="!newCatName.trim()">确定</button>
              <button @click="showCategoryEditor = false" class="modal-btn modal-btn--secondary">取消</button>
            </div>
          </div>
        </div>

        <!-- 右侧书籍区域 -->
        <div class="home-content">

          <!-- 书籍网格 -->
          <div class="book-section">
        <div v-if="loading" class="book-grid">
          <SkeletonLoader v-for="i in 6" :key="i" type="card" />
        </div>

        <div v-else-if="filteredBooks.length === 0" class="empty-state">
          <div class="empty-icon">📭</div>
          <h3 class="empty-title">还没有书籍</h3>
          <p class="empty-desc">上传你的第一本书，开始阅读之旅</p>
          <router-link to="/upload" class="btn btn--primary" style="margin-top:12px">上传书籍</router-link>
        </div>

        <div v-else class="book-grid">
          <BookCard
            v-for="book in filteredBooks"
            :key="book.id"
            :book="book"
            :batchMode="batchMode"
            :selected="selectedIds.has(book.id)"
            @click="batchMode ? toggleSelect(book.id) : handleBookClick(book)"
            @delete="handleDelete(book)"
            @toggleSelect="toggleSelect"
            @coverUpload="handleCoverUpload(book, $event)"
            @rename="openRenameDialog"
          />
          <!-- 上传书籍占位 -->
          <router-link to="/upload" class="upload-placeholder">
            <div class="upload-placeholder-icon">+</div>
            <div class="upload-placeholder-text">上传书籍</div>
          </router-link>
        </div>
      </div>

          <!-- 分页 -->
          <div v-if="totalPages > 1" class="pagination">
            <button @click="changePage(currentPage - 1)" :disabled="currentPage === 1" class="btn btn--ghost btn-sm">← 上一页</button>
            <span class="page-info">{{ currentPage }} / {{ totalPages }}</span>
            <button @click="changePage(currentPage + 1)" :disabled="currentPage === totalPages" class="btn btn--ghost btn-sm">下一页 →</button>
          </div>
        </div><!-- /.home-content -->
      </div><!-- /.home-layout -->
    </div><!-- /.container-main -->

  </div>
</template>

<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore, useBookStore } from '@/stores'
import BookCard from '@/components/BookCard.vue'
import SkeletonLoader from '@/components/SkeletonLoader.vue'
import { showAlert, showConfirm } from '@/utils/modal'
import { categoryAPI, bookAPI } from '@/api'
import { useCategoryStore } from '@/stores'

const router = useRouter()
const userStore = useUserStore()
const bookStore = useBookStore()
const catStore = useCategoryStore()

const activeCategory = ref('all')
const showSidebar = ref(false)
const catRenderKey = ref(0)
const batchMode = ref(false)
const selectedIds = ref(new Set())
function toggleSelect(id) {
  const s = new Set(selectedIds.value)
  if (s.has(id)) s.delete(id); else s.add(id)
  selectedIds.value = s
}
function selectAll() {
  selectedIds.value = new Set(filteredBooks.value.map(b => b.id))
}
async function handleBatchDelete() {
  const ok = await showConfirm('批量删除', `确定删除选中的 ${selectedIds.value.size} 本书吗？`)
  if (!ok) return
  for (const id of selectedIds.value) {
    try { await bookStore.deleteBook(id); allBooks.value = allBooks.value.filter(b => b.id !== id) } catch (e) { console.error(e) }
  }
  selectedIds.value = new Set(); batchMode.value = false
}
watch(activeCategory, () => {
  catRenderKey.value++
  currentPage.value = 1
  loadBooks()
})
const apiCategories = computed(() => { catStore.rev; return catStore.list })
const catRev = computed(() => catStore.rev)
function loadApiCategories() { catStore.load() }
const loading = ref(false)
const currentPage = ref(1)
const totalPages = ref(1)
const showCategoryEditor = ref(false)
const newCatName = ref('')
const catExpanded = ref(false)
const catEmojiOpen = ref(false)
const catMenuId = ref(null)
const renameVal = ref('')
const renameModal = ref({ show: false, cat: null })
const bookRename = ref({ show: false, title: '', author: '', category: '', bookId: null })
const dupModal = ref({ show: false, name: '' })

const { ep, allEmojis, epNav, epOpen, epRow, epKeydown } = catStore

// 侧边栏三点菜单
function toggleCatMenu(id) { catMenuId.value = catMenuId.value === id ? null : id }
function openRenameModal(cat) { renameModal.value = { show: true, cat }; renameVal.value = cat.label; epOpen(cat.emoji); catMenuId.value = null }
function confirmRename() {
  const cat = renameModal.value.cat
  if (!cat) return
  const name = renameVal.value.trim()
  if (!name) return
  if (cat.id === 'all') { renameModal.value.show = false; return }
  if (categories.value.find(c => c.label === name && c.id !== cat.id)) {
    dupModal.value = { show: true, name }; return
  }
  categoryAPI.update(cat.id, { name, emoji: ep.selected }).then(() => { catStore.load(); renameModal.value.show = false }).catch(e => console.error('rename fail', e))
}
function deleteCat(cat) {
  catMenuId.value = null
  if (cat.id === 'all') return
  categoryAPI.delete(cat.id).then(() => {
    catStore.load()
  }).catch(e => console.error('delete cat fail', e))
}

// 点击外部关闭三点菜单
function closeSideMenus() { catMenuId.value = null; catExpanded.value = false }
const dragIdx = ref(-1)
function onDragStart(i) { dragIdx.value = i }
function onDragOver(e) {
  const row = e.target.closest('.cat-edit-row')
  if (!row) return
  const idx = Array.from(row.parentNode.children).indexOf(row)
  dragIdx.value = dragIdx.value // keep for visual
}
function onDrop(e) {
  const row = e.target.closest('.cat-edit-row')
  if (!row || dragIdx.value < 0) { dragIdx.value = -1; return }
  const parent = row.parentNode
  const idx = Array.from(parent.children).indexOf(row)
  if (idx === dragIdx.value) { dragIdx.value = -1; return }
  const arr = editableCategories.value
  const [moved] = arr.splice(dragIdx.value, 1)
  arr.splice(idx, 0, moved)
  dragIdx.value = -1
}

const quotePool = {
  dawn: ['夜深了，书是唯一的灯。', '万籁俱寂，正是读书时。', '夜色温柔，字句滚烫。'],
  morning: ['一日之计在于晨，一书之悦在于心。', '晨光正好，读书趁早。', '书卷多情似故人，晨昏忧乐每相亲。'],
  afternoon: ['读书破万卷，下笔如有神。', '书籍是造就灵魂的工具。', '读万卷书，行万里路。'],
  evening: ['晚风拂卷，字句生香。', '书犹药也，善读之可以医愚。', '生活里没有书籍，就好像没有阳光。'],
}
const readingQuote = ref('')
function pickQuote() {
  const h = new Date().getHours()
  let pool
  if (h < 6) pool = quotePool.dawn
  else if (h < 12) pool = quotePool.morning
  else if (h < 18) pool = quotePool.afternoon
  else pool = quotePool.evening
  readingQuote.value = pool[Math.floor(Math.random() * pool.length)]
}
pickQuote()

const timeGreeting = computed(() => {
  const h = new Date().getHours()
  if (h < 6) return '夜深了'
  if (h < 9) return '早上好'
  if (h < 12) return '上午好'
  if (h < 14) return '中午好'
  if (h < 18) return '下午好'
  return '晚上好'
})

// ========== 分类系统 ==========

const hiddenCategories = ref(JSON.parse(localStorage.getItem('bookmind_hidden_categories') || '[]'))

const categories = computed(() => { catStore.rev; return apiCategories.value })

const editableCategories = ref([])

watch(showCategoryEditor, (v) => {
  if (v && apiCategories.value.length) {
    const all = apiCategories.value.map(c => ({ ...c }))
    editableCategories.value = all.filter(c => c.label !== '全部' && !hiddenCategories.value.includes(c.id))
    const hidden = all.filter(c => hiddenCategories.value.includes(c.id) && c.label !== '全部')
    hidden.forEach(c => editableCategories.value.push({ ...c, _hidden: true }))
  }
})

function confirmAddCat() {
  const name = newCatName.value.trim()
  if (!name) return
  if (categories.value.find(c => c.label === name)) {
    dupModal.value = { show: true, name }; return
  }
  categoryAPI.create(name, ep.selected).then(() => {
    loadApiCategories()
    newCatName.value = ''
    showCategoryEditor.value = false
  }).catch(() => {})
}

function removeCategory(i) {
  const cat = editableCategories.value[i]
  if (cat.label === '全部') return // 全部分类不可删除
  if (cat.builtin) {
    hiddenCategories.value.push(cat.id)
    localStorage.setItem('bookmind_hidden_categories', JSON.stringify(hiddenCategories.value))
    editableCategories.value[i] = { ...cat, _hidden: true }
  } else {
    editableCategories.value.splice(i, 1)
  }
}

function restoreCategory(i) {
  const cat = editableCategories.value[i]
  const idx = hiddenCategories.value.indexOf(cat.id)
  if (idx >= 0) hiddenCategories.value.splice(idx, 1)
  localStorage.setItem('bookmind_hidden_categories', JSON.stringify(hiddenCategories.value))
  editableCategories.value.splice(i, 1)
}

function saveCategories() {
  // 保存用户自定义分类（排除内置、排除已隐藏）
  const userCats = editableCategories.value
    .filter(c => !c.builtin && !c._hidden && c.label.trim())
  const names = userCats.map(c => c.label.trim().toLowerCase())
  if (new Set(names).size !== names.length) {
    showAlert('命名重复', '分类名称不能重复', '⚠️')
    return
  }
  // 被删除的分类 → 书籍移到「其他」（仅用户分类，内置不涉及）
  const oldIds = userCategories.value.map(c => c.id)
  const removedIds = oldIds.filter(id => !userCats.find(c => c.id === id))
  const token = localStorage.getItem('token')
  removedIds.forEach(catId => {
    allBooks.value.filter(b => b.category === catId).forEach(b => {
      fetch('/api/books/' + b.id + '/category?category=其他', {
        method: 'PUT', headers: { 'Authorization': 'Bearer ' + token }
      }).catch(() => {})
      b.category = '其他'
    })
  })
  userCategories.value = userCats
  saveUserCategories(userCats)
  localStorage.setItem('bookmind_hidden_categories', JSON.stringify(hiddenCategories.value))
  showCategoryEditor.value = false
  if (activeCategory.value !== 'all' && !categories.value.find(c => c.id === activeCategory.value)) {
    activeCategory.value = 'all'
  }
  loadBooks()
}

// ========== 数据加载 ==========

const allBooks = ref([])

async function loadBooks() {
  loading.value = true
  try {
    const params = { page: currentPage.value, size: 50 }
    if (activeCategory.value !== 'all') params.category = activeCategory.value
    const data = await bookStore.loadBookList(params)
    allBooks.value = data.records || []
    totalPages.value = data.pages || 1
  } catch (e) { console.error(e) }
  loading.value = false
}

const filteredBooks = computed(() => {
  catRenderKey.value
  let list = allBooks.value
  if (activeCategory.value !== 'all') {
    list = list.filter(b => b.category === activeCategory.value)
  }
  return list
})



const isAllCategory = computed(() => activeCategory.value === 'all')

const getCatEmoji = (id) => {
  const found = categories.value.find(c => c.label === id || String(c.id) === String(id))
  return found ? found.emoji : '📖'
}
const getCatLabel = (id) => {
  const found = categories.value.find(c => c.label === id || String(c.id) === String(id))
  return found ? found.label : id
}

const categoryEmoji = getCatEmoji

function changePage(page) {
  if (page < 1) return
  currentPage.value = page
  loadBooks()
}

function handleBookClick(book) {
  const token = localStorage.getItem('token')
  fetch('/api/reader/' + book.id + '/progress', {
    headers: { 'Authorization': 'Bearer ' + token }
  }).then(function(r) { return r.json() }).then(function(res) {
    var chapter = 1
    if (res.code === 0 && res.data && res.data.chapterNumber) {
      chapter = res.data.chapterNumber
    }
    router.push({ name: 'reader-chapter', params: { bookId: book.id, chapterNumber: chapter } })
  }).catch(function() {
    router.push({ name: 'reader-chapter', params: { bookId: book.id, chapterNumber: 1 } })
  })
}

async function openRenameDialog(book) {
  await catStore.load()  // 总是从 API 加载最新分类，确保与其他页面同步
  bookRename.value = { show: true, title: book.title || '', author: book.author || '', category: book.category || '小说', bookId: book.id }
}
async function confirmBookRename() {
  const r = bookRename.value
  if (!r.title.trim()) return
  try {
    await bookAPI.update(r.bookId, { title: r.title.trim(), author: r.author.trim(), category: r.category })
    const b = allBooks.value.find(x => x.id === r.bookId)
    if (b) { b.title = r.title.trim(); b.author = r.author.trim(); b.category = r.category }
    bookRename.value.show = false
  } catch (e) { console.error('rename fail', e) }
}

async function handleCoverUpload(book, file) {
  if (!file) return
  try {
    await bookAPI.uploadCover(book.id, file)
    const res = await bookAPI.getById(book.id)
    if (res.data?.coverUrl) book.coverUrl = res.data.coverUrl
  } catch (e) { showAlert('封面上传失败', e.message, '❌') }
}

async function handleDelete(book) {
  const ok = await showConfirm('删除确认', `确定删除《${book.title}》吗？`)
  if (!ok) return
  try {
    await bookStore.deleteBook(book.id)
    allBooks.value = allBooks.value.filter(b => b.id !== book.id)
  } catch (e) {
    showAlert('删除失败', e.message, '❌')
  }
}

onMounted(() => {
  const oldHidden = JSON.parse(localStorage.getItem('bookmind_hidden_categories') || '[]')
  if (oldHidden.length > 0 && typeof oldHidden[0] === 'string') {
    localStorage.removeItem('bookmind_hidden_categories')
    hiddenCategories.value = []
  }
  loadApiCategories()
  loadBooks()
  document.addEventListener('click', (e) => {
    if (!e.target.closest('.cat-sidebar') && !e.target.closest('.modal-overlay')) {
      closeSideMenus()
    }
  })
  let catLoadTimer
  const debouncedCatLoad = () => {
    clearTimeout(catLoadTimer)
    catLoadTimer = setTimeout(() => loadApiCategories(), 500)
  }
  document.addEventListener('visibilitychange', () => { if (document.visibilityState === 'visible') debouncedCatLoad() })
  window.addEventListener('focus', debouncedCatLoad)
})
</script>

<style scoped>
.home-page { min-height: 100vh; }

/* 侧边栏 + 内容区布局 */
.home-layout { display: flex; gap: 24px; align-items: flex-start; }
.book-section { flex: 1; min-width: 0; }

/* 分类侧边栏 */
.cat-sidebar { width: 160px; flex-shrink: 0; transition: width 0.3s, opacity 0.3s, margin 0.3s; border-right: none; }
.cat-sidebar--hidden { width: 0; opacity: 0; margin: 0; padding: 0; min-width: 0; overflow: hidden; }

@media (max-width: 768px) {
  .cat-sidebar { position: fixed; left: 0; top: 0; bottom: 0; z-index: 50; background: var(--bg-paper); width: 220px; box-shadow: var(--shadow-lg); padding: 70px 12px 12px; overflow: visible; }
  .cat-sidebar-list { overflow: visible; }
  .cat-sidebar--hidden { width: 0; padding: 0; box-shadow: none; }
  .home-layout { gap: 12px; }
  .cat-overlay { display: block; position: fixed; inset: 0; z-index: 49; background: rgba(44,24,16,0.3); }
}
.cat-overlay { display: none; }
.cat-sidebar-header { margin-bottom: 6px; }
.cat-close-btn { display: none; margin-left: auto; padding: 2px 8px; cursor: pointer; color: var(--text-muted); font-size: 18px; border-radius: 6px; line-height: 1; }
.cat-close-btn:hover { color: var(--text-primary); background: rgba(200,180,160,0.2); }
@media (max-width: 768px) { .cat-close-btn { display: inline-block; } }
.cat-current {
  display: flex; align-items: center; gap: 6px; width: 100%;
  padding: 10px 14px; border-radius: var(--radius-sm);
  border: 1px solid var(--border-light); background: var(--bg-white);
  cursor: pointer; font-size: 14px; color: var(--text-primary);
  transition: all 0.2s;
}
.cat-current:hover { border-color: var(--accent-terracotta); }
.cat-current-emoji { font-size: 18px; }
.cat-current-label { flex: 1; text-align: left; font-weight: 500; }
.cat-chevron { color: var(--text-muted); transition: transform 0.2s; flex-shrink: 0; }
.cat-chevron.rotated { transform: rotate(180deg); }

.cat-sidebar-list {
  display: flex; flex-direction: column; gap: 2px;
  padding: 4px; border-radius: var(--radius-sm);
  background: var(--bg-white);
  box-shadow: var(--shadow-sm);
  margin-right: auto; width: fit-content; min-width: 100%;
}
.cat-sidebar-item {
  display: flex; align-items: center; gap: 8px;
  padding: 8px 10px; border-radius: 6px; border: none;
  background: none; cursor: pointer; font-size: 13px; color: var(--text-secondary);
  transition: all 0.15s; width: 100%; text-align: left;
}
.cat-sidebar-item:hover { background: var(--bg-cream); color: var(--text-primary); }
.cat-sidebar-item--active { background: rgba(198,123,92,0.1); color: var(--accent-terracotta); font-weight: 500; }
.cat-sidebar-item--edit { border-top: 1px solid var(--border-light); margin-top: 4px; padding-top: 10px; color: var(--text-muted); font-size: 12px; }
.cat-sidebar-item--edit:hover { color: var(--accent-terracotta); }
.cat-emoji { font-size: 16px; }
.cat-label { flex: 1; min-width: 0; overflow: hidden; text-overflow: ellipsis; }

/* 侧边栏管理：三点菜单 + 拖拽 + 重命名 */
.cat-sidebar-item-wrap { display: flex; align-items: center; gap: 2px; position: relative; padding-right: 4px; border-radius: 6px; cursor: grab; user-select: none; }
.cat-sidebar-item-wrap:hover { background: var(--bg-cream); }
.cat-sidebar-item-wrap--over { background: rgba(198,123,92,0.15) !important; border: 1px dashed var(--accent-terracotta); }
.cat-sidebar-item-wrap .cat-sidebar-item { flex: 1; min-width: 0; background: none !important; }
.cat-item-menu { position: relative; flex-shrink: 0; display: block; opacity: 0.5; }
.cat-sidebar-item-wrap:hover .cat-item-menu { opacity: 1; }
.cat-dots-btn {
  width: 22px; height: 22px; border-radius: 6px; border: none; background: none;
  cursor: pointer; font-size: 14px; line-height: 1; color: var(--text-muted);
  display: flex; align-items: center; justify-content: center; transition: all 0.1s;
}
.cat-dots-btn:hover { background: var(--border-light); color: var(--text-primary); }
.cat-item-menu { position: relative; flex-shrink: 0; display: block; opacity: 0.5; overflow: visible; }
.cat-drop-menu {
  position: absolute; left: 100%; top: 0; z-index: 999; min-width: 120px;
  padding: 4px 0; border-radius: var(--radius-sm);
  background: var(--bg-white); border: 1px solid var(--border-light); box-shadow: var(--shadow-md);
}
.cat-drop-item {
  display: block; width: 100%; padding: 5px 10px; border: none; border-radius: 6px;
  background: none; cursor: pointer; font-size: 12px; color: var(--text-secondary); text-align: left;
  transition: background 0.1s;
}
.cat-drop-item:hover { background: var(--bg-cream); }
.cat-drop-item--danger:hover { color: var(--accent-rose); background: rgba(184,101,90,0.08); }

/* 内联重命名（替换标签） */
.cat-label-input-inline {
  flex: 1; min-width: 0; border: none; outline: none;
  padding: 1px 4px; border-radius: 4px; font-size: 13px;
  color: var(--text-primary); background: transparent;
  border-bottom: 1px solid var(--border-medium); font-family: inherit;
}
.rename-inline-wrap { display: inline-flex; align-items: center; gap: 4px; width: 100%; }
.rename-confirm-btn {
  flex-shrink: 0; width: 20px; height: 20px; border-radius: 50%;
  border: none; background: var(--accent-terracotta); color: white;
  font-size: 12px; line-height: 1; cursor: pointer; display: flex;
  align-items: center; justify-content: center; padding: 0;
  transition: background 0.2s;
}
.rename-confirm-btn:hover { background: #B06A4E; }


/* 拖拽排序 */
.cat-edit-row { cursor: grab; transition: transform 0.15s; gap: 6px; }
.cat-edit-row:active { cursor: grabbing; opacity: 0.5; }
.cat-edit-row--over { border-color: var(--accent-terracotta) !important; background: rgba(198,123,92,0.06) !important; }
.drag-handle { color: var(--text-muted); font-size: 14px; cursor: grab; user-select: none; line-height: 1; padding: 0 2px; }
.drag-handle:active { cursor: grabbing; }

/* 新建分类子弹窗 */
.new-cat-body { display: flex; flex-direction: column; gap: 16px; margin-top: 12px; }
.new-cat-emoji-area { display: flex; flex-direction: column; align-items: center; gap: 6px; }
.new-cat-emoji-label { font-size: 12px; color: var(--text-muted); font-weight: 500; align-self: flex-start; }
.new-cat-preview { font-size: 14px; color: var(--text-secondary); }
.new-cat-name-area { display: flex; flex-direction: column; gap: 6px; }
.new-cat-name-area label { font-size: 12px; color: var(--text-muted); font-weight: 500; }
.new-cat-name-input { width: 100%; padding: 10px 14px; border-radius: 24px; border: 1.5px solid #DCD0C0; background: var(--bg-cream); outline: none; font-size: 14px; }
.new-cat-name-input:focus { border-color: var(--accent-terracotta); background: #FFFCF8; }

/* 右侧内容区 */
.home-content { flex: 1; min-width: 0; }

/* 分类编辑器 emoji 提示 */
.cat-emoji-input::placeholder { color: var(--text-muted); font-size: 11px; }

/* 管理分类弹窗遮罩 - 在侧边栏同级 */
.modal-overlay--cats { z-index: 9999; }
.dup-modal { z-index: 99999; }

.home-header {
  display: flex; align-items: flex-start; justify-content: space-between;
  margin-bottom: 16px;
}
.page-title { font-family: var(--font-heading); font-size: 28px; font-weight: 700; color: var(--text-primary); }
.text-gradient { background: linear-gradient(135deg, var(--accent-terracotta), var(--accent-gold)); -webkit-background-clip: text; -webkit-text-fill-color: transparent; background-clip: text; }
.page-subtitle { font-size: 14px; color: var(--text-muted); margin-top: 4px; }
.header-actions { display: flex; align-items: center; gap: 8px; flex-shrink: 0; }
.batch-info { font-size: 12px; color: var(--accent-rose); font-weight: 600; }

/* 分类栏 */
.categories-bar {
  display: flex; gap: 8px; margin-bottom: 24px; flex-wrap: wrap;
  padding-bottom: 8px;
}
.cat-chip {
  display: flex; align-items: center; gap: 5px;
  padding: 6px 14px; border-radius: 20px; font-size: 13px; font-weight: 500;
  border: 1px solid var(--border-light); background: var(--bg-white);
  cursor: pointer; transition: all 0.2s; color: var(--text-secondary);
}
.cat-chip:hover { border-color: var(--accent-terracotta); color: var(--text-primary); }
.cat-chip--active { background: var(--accent-terracotta); border-color: var(--accent-terracotta); color: white; }
.cat-chip--edit { border-style: dashed; color: var(--text-muted); font-size: 12px; }
.cat-chip--edit:hover { border-color: var(--accent-gold); color: var(--accent-gold); }
.cat-emoji { font-size: 14px; }
.cat-count {
  font-size: 11px; opacity: 0.7; background: rgba(0,0,0,0.08);
  padding: 0 6px; border-radius: 8px; min-width: 18px; text-align: center;
}
.cat-chip--active .cat-count { background: rgba(255,255,255,0.2); }

/* 分类编辑器 */
.category-editor { display: flex; flex-direction: column; gap: 8px; margin-top: 12px; }
.cat-edit-row { display: flex; align-items: center; gap: 6px; }
.cat-emoji-wrap { display: flex; flex-direction: column; align-items: center; gap: 3px; }
.cat-emoji-input { width: 44px; text-align: center; font-size: 20px; padding: 6px 4px; border-radius: 24px; background: var(--bg-cream); border: 1.5px solid #DCD0C0; outline: none; cursor: pointer; }
.cat-emoji-input:focus { border-color: var(--accent-terracotta); }
/* 老虎机 emoji 选择器 */
.emoji-slot { display: flex; flex-direction: column; align-items: center; gap: 8px; padding: 16px; background: var(--bg-cream); border-radius: 16px; border: 1px solid var(--border-light); outline: none; }
.emoji-slot:focus { border-color: var(--accent-terracotta); box-shadow: 0 0 0 2px rgba(198,123,92,0.12); }
.emoji-btn {
  background: rgba(200,180,160,0.08); border: none; cursor: pointer; font-size: 13px;
  width: 28px; height: 28px; border-radius: 6px;
  display: flex; align-items: center; justify-content: center;
  transition: all 0.15s; line-height: 1; color: #8B7D6B;
}
.emoji-btn:hover { color: var(--accent-terracotta); background: rgba(198,123,92,0.12); }
.ep-mid .emoji-btn { width: 25px; height: 25px; font-size: 11px; }
.ep-mid { display: flex; align-items: center; gap: 8px; }
.ep-up { margin-bottom: -4px; }
.ep-down { margin-top: -4px; }
.emoji-grid {
  display: flex; flex-direction: column; align-items: center; gap: 2px;
  padding: 12px; background: radial-gradient(circle, rgba(198,123,92,0.12) 0%, rgba(198,123,92,0.04) 60%, transparent 80%);
  border-radius: 50%; position: relative;
}
.emoji-slot-row { display: flex; gap: 6px; line-height: 1; user-select: none; justify-content: center; }
.emoji-slot-row--dim .emoji-cell { font-size: 34px; opacity: 0.2; cursor: default; }
.emoji-slot-row--current .emoji-cell { font-size: 34px; cursor: pointer; }
.emoji-cell { padding: 2px 6px; border-radius: 10px; transition: all 0.1s; }
.emoji-slot-row--current .emoji-cell:hover { background: rgba(198,123,92,0.1); transform: scale(1.2); }
.emoji-cell--sel { background: rgba(198,123,92,0.18) !important; transform: scale(1.3); box-shadow: 0 2px 12px rgba(198,123,92,0.25); }
.cat-label-input:focus { border-color: var(--accent-terracotta); background: #FFFCF8; }
.cat-edit-row--hidden { opacity: 0.4; }
.cat-edit-row--hidden:hover { opacity: 0.7; }
.cat-remove-btn, .cat-restore-btn {
  width: 28px; height: 28px; border-radius: 50%; border: none;
  cursor: pointer; display: flex; align-items: center; justify-content: center;
  font-size: 11px; transition: all 0.2s; flex-shrink: 0;
}
.cat-remove-btn { background: rgba(200,80,60,0.1); color: var(--accent-rose); }
.cat-remove-btn:hover { background: rgba(200,80,60,0.2); }
.cat-restore-btn { background: rgba(90,143,106,0.1); color: #5A8F6A; }
.cat-restore-btn:hover { background: rgba(90,143,106,0.2); }

/* 书籍网格 */
.home-content { flex: 1; min-width: 0; }
.book-section { min-height: 300px; }
.book-grid {
  display: flex; flex-wrap: wrap; gap: 16px;
}
.book-grid > * { flex-shrink: 0; }

@media (min-width: 1200px) { .book-grid > * { width: calc((100% - 80px) / 6); } }
@media (min-width: 1024px) and (max-width: 1199px) { .book-grid > * { width: calc((100% - 48px) / 4); } }
@media (min-width: 768px) and (max-width: 1023px) { .book-grid > * { width: calc((100% - 42px) / 3); } }
@media (max-width: 767px) { .book-grid > * { width: calc((100% - 10px) / 2); } }

@media (max-width: 480px) {
  .home-header { flex-direction: column; align-items: flex-start; gap: 8px; }
  .page-title { font-size: 18px; }
  .page-subtitle { font-size: 12px; }
  .book-grid { gap: 8px; }
  .book-grid > * { width: calc((100% - 8px) / 2); }
}

.upload-placeholder {
  display: flex; flex-direction: column; align-items: center; justify-content: center;
  aspect-ratio: 3/4; border-radius: 10px;
  border: 2px dashed var(--accent-terracotta);
  background: linear-gradient(135deg, #FFF8F2, #FFF0E4); cursor: pointer;
  transition: all 0.2s; text-decoration: none; gap: 8px; color: var(--accent-terracotta);
}
.upload-placeholder:hover { background: linear-gradient(135deg, #FFF0E4, #FFE8D8); }
.upload-placeholder-icon { font-size: 36px; font-weight: 300; line-height: 1; }
.upload-placeholder-text { font-size: 14px; font-weight: 600; }
@media (max-width: 640px) {
  .upload-placeholder { border-radius: 8px; gap: 4px; }
  .upload-placeholder-icon { font-size: 24px; }
  .upload-placeholder-text { font-size: 11px; }
}

.book-skeleton { border-radius: var(--radius-md); padding: 12px; background: var(--bg-white); }

.empty-state { text-align: center; padding: 60px 20px; }
.empty-icon { font-size: 56px; margin-bottom: 16px; }
.empty-title { font-family: var(--font-heading); font-size: 20px; color: var(--text-primary); margin-bottom: 6px; }
.empty-desc { font-size: 14px; color: var(--text-muted); }

/* 分页 */
.pagination { display: flex; align-items: center; justify-content: center; gap: 12px; margin-top: 32px; }
.page-info { font-size: 13px; color: var(--text-muted); }

/* AI 总结预览 */
.preview-summary-box { margin: 12px 0; padding: 14px; background: var(--bg-cream); border-radius: var(--radius-sm); border-left: 3px solid var(--accent-gold); min-height: 60px; }
.summary-text { font-size: 13px; line-height: 1.8; color: var(--text-primary); white-space: pre-wrap; }
.summary-placeholder { font-size: 13px; color: var(--text-muted); text-align: center; padding: 12px 0; }
.summary-loading { padding: 4px 0; }

/* 预览详情 */
.preview-details { margin: 12px 0; padding: 12px; background: var(--bg-cream); border-radius: var(--radius-sm); }
.preview-row { display: flex; justify-content: space-between; padding: 6px 0; font-size: 13px; border-bottom: 1px solid var(--border-light); }
.preview-row:last-child { border-bottom: none; }
.preview-label { color: var(--text-muted); }
.preview-val { color: var(--text-primary); font-weight: 600; }
.preview-msg { text-align: center; font-size: 13px; color: var(--text-muted); margin-top: 8px; }

/* 处理书籍弹窗进度条 */
.progress-bar { height: 6px; border-radius: 3px; background: #F0E8DC; overflow: hidden; margin-top: 8px; }
.progress-fill { height: 100%; background: var(--accent-terracotta); transition: width 0.5s ease; }

</style>
