<template>
  <div class="upload-page">
    <div class="container-main" style="max-width:600px">
      <div class="upload-header">
        <h1 class="page-title">📤 上传书籍</h1>
      </div>

      <!-- 拖拽上传区域 -->
      <div
        class="dropzone"
        :class="{ 'dropzone--dragover': isDragging, 'dropzone--has-file': file }"
        @dragenter.prevent="isDragging = true"
        @dragleave.prevent="handleDragLeave"
        @dragover.prevent
        @drop.prevent="handleDrop"
        @click="file ? null : $refs.fileInput.click()"
      >
        <input ref="fileInput" type="file" accept=".pdf,.docx,.txt,.epub,.md" class="hidden" @change="handleFileSelect" />

        <!-- 空状态 -->
        <div v-if="!file" class="dropzone-empty">
          <p class="dropzone-text">
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" style="display:inline-block;vertical-align:middle;margin-right:6px;margin-bottom:2px"><path d="M21 15v4a2 2 0 01-2 2H5a2 2 0 01-2-2v-4M17 8l-5-5-5 5M12 3v12"/></svg>
            拖拽文件到此处，或<span class="dropzone-link">点击选择</span>
          </p>
          <p class="dropzone-hint">✅ PDF  ✅ DOCX  ✅ TXT  ✅ EPUB  ✅ Markdown</p>
        </div>

        <!-- 已选文件 -->
        <div v-if="file" class="dropzone-file">
          <div class="file-icon">{{ fileIcon }}</div>
          <div class="file-info">
            <p class="file-name">{{ file.name }}</p>
            <p class="file-size">{{ formatSize(file.size) }}</p>
            <p v-if="file.size > 100 * 1024 * 1024" class="file-size file-size--chunk">大文件 · 分片上传</p>
          </div>
          <button @click.stop="clearFile" class="file-clear" v-if="!uploading">✕</button>
        </div>
      </div>

      <!-- 分片上传进度 -->
      <div v-if="uploading && useChunked" class="progress-section">
        <div class="progress-header">
          <span class="progress-label">上传分片 {{ doneChunks }}/{{ totalChunks }}</span>
          <span class="progress-pct">{{ chunkProgress }}%</span>
        </div>
        <div class="progress-track">
          <div class="progress-fill" :style="{ width: chunkProgress + '%' }"></div>
        </div>
      </div>

      <!-- 单行进度 -->
      <div v-if="uploading || uploadDone" class="progress-section">
        <div class="progress-header">
          <span class="progress-label">{{ uploadStatus }}</span>
          <span class="progress-pct">{{ currentPercent.toFixed(2) }}%</span>
        </div>
        <div class="progress-track">
          <div class="progress-fill" :style="{ width: currentPercent + '%' }"></div>
        </div>
        <div class="steps">
          <div class="step" :class="{ 'step--active': step === 1, 'step--done': step > 1 }">
            <div class="step-dot">📤</div>
            <span class="step-text">上传</span>
          </div>
          <div class="step-line" :class="{ 'step-line--active': step === 1, 'step-line--done': step > 1 }"></div>
          <div class="step" :class="{ 'step--active': step === 2, 'step--done': step > 2 }">
            <div class="step-dot">📖</div>
            <span class="step-text">解析</span>
          </div>
          <div class="step-line" :class="{ 'step-line--active': step === 2, 'step-line--done': step > 2 }"></div>
          <div class="step" :class="{ 'step--active': step === 3, 'step--done': step > 3 }">
            <div class="step-dot">🧠</div>
            <span class="step-text">知识图谱</span>
          </div>
        </div>
        <div class="mt-2 flex gap-2">
          <button v-if="step >= 2 && !uploadDone" @click="goRead" class="btn btn-sm flex-1" style="background:var(--accent-terracotta);color:white">📖 先去阅读</button>
          <button v-if="step === 3 && !uploadDone" @click="pauseAndFinish" class="btn btn-sm" style="background:#888;color:white">⏸ 暂停</button>
        </div>
      </div>

      <!-- 书籍信息表单 -->
      <div class="form-section">
        <div class="form-row">
          <label class="form-label">书名 *</label>
          <input v-model="form.title" placeholder="自动使用文件名" class="input" :class="{ 'input-error': errors.title }" />
          <p v-if="errors.title" class="form-error">{{ errors.title }}</p>
        </div>
        <div class="form-row">
          <label class="form-label">作者</label>
          <input v-model="form.author" placeholder="可选" class="input" />
        </div>
        <div class="form-row">
          <label class="form-label">分类 *</label>
          <div class="cat-compact">
            <div class="cat-compact-main" @click="toggleCatDropdown">
              <span class="cat-compact-emoji">{{ selectedCat ? selectedCat.emoji : '📂' }}</span>
              <span class="cat-compact-label">{{ selectedCat ? selectedCat.label : '选择分类' }}</span>
              <svg class="cat-compact-chevron" :class="{ rotated: catDropdownOpen }" width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M6 9l6 6 6-6"/></svg>
            </div>
            <button @click="showCategoryModal = true" class="cat-compact-new" title="新建分类">➕</button>
          </div>
          <div v-if="catDropdownOpen" class="cat-dropdown" @click.stop>
            <div
              v-for="c in categoryOptions" :key="c.id"
              @click="selectCat(c)"
              class="cat-dropdown-item"
              :class="{ 'cat-dropdown-item--sel': form.category === c.label }"
            >
              <span class="cat-dropdown-emoji">{{ c.emoji }}</span>
              <span class="cat-dropdown-label">{{ c.label }}</span>
              <span v-if="form.category === c.label" class="cat-dropdown-check">✓</span>
            </div>
          </div>
          <p v-if="errors.category" class="form-error">{{ errors.category }}</p>
        </div>
      </div>

      <button
        @click="handleUpload"
        :disabled="!canUpload || uploading"
        class="btn btn--primary upload-btn"
      >
        <svg v-if="uploading" class="btn-spinner" width="16" height="16" viewBox="0 0 24 24" fill="none"><circle cx="12" cy="12" r="10" stroke="currentColor" stroke-width="3" stroke-dasharray="31.4 31.4" stroke-linecap="round"/></svg>
        <svg v-else width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" style="margin-bottom:1px"><path d="M21 15v4a2 2 0 01-2 2H5a2 2 0 01-2-2v-4M17 8l-5-5-5 5M12 3v12"/></svg>
        <span>{{ uploading ? '处理中...' : uploadDone ? '已完成' : '开始上传' }}</span>
      </button>

      <!-- 完成弹窗 -->
      <div v-if="doneModal.show" class="modal-overlay" @click.self="doneModal.show = false">
        <div class="modal-card">
          <div class="modal-icon" style="font-size:48px">✅</div>
          <h3 class="modal-title">上传成功！</h3>
          <p class="modal-message">书籍已加入处理队列</p>
          <p class="modal-message" style="margin-top:4px">
            <span v-if="coverReady">封面：✅ 已生成</span>
            <span v-else>封面：⏳ 生成中...</span>
          </p>
          <div class="modal-actions">
            <button @click="goRead" class="modal-btn modal-btn--primary">前往阅读</button>
            <button
              @click="doneModal.show = false; $router.push({name:'home'})"
              class="modal-btn modal-btn--secondary"
              :disabled="!coverReady"
            >返回书房</button>
            <span v-if="!coverReady" style="font-size:11px;color:var(--text-muted);margin-left:4px">封面就绪后可返回</span>
          </div>
        </div>
      </div>

      <!-- 新建分类弹窗 -->
      <div v-if="showCategoryModal" class="modal-overlay" @click.self="showCategoryModal = false">
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
              <label class="new-cat-name-label">名称</label>
              <input v-model="uploadNewCatName" @keydown.enter="saveUploadCategory" class="new-cat-name-input" placeholder="输入分类名称" />
            </div>
          </div>
          <div class="modal-actions" style="margin-top:16px">
            <button @click="saveUploadCategory" class="modal-btn modal-btn--primary" :disabled="!uploadNewCatName.trim()">确定</button>
            <button @click="showCategoryModal = false" class="modal-btn modal-btn--secondary">取消</button>
          </div>
        </div>
      </div>

      <div v-if="toast.show" class="toast" :class="'toast--' + toast.type">{{ toast.text }}</div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, nextTick, onMounted, onActivated } from 'vue'
import { useRouter } from 'vue-router'
import { useBookStore } from '@/stores'
import { uploadAPI, categoryAPI, graphAPI } from '@/api'
import { useCategoryStore } from '@/stores'
import request from '@/api/request'

const router = useRouter()
const bookStore = useBookStore()

const CHUNK_SIZE = 5 * 1024 * 1024 // 5MB
const LARGE_FILE_THRESHOLD = 100 * 1024 * 1024 // 100MB

const file = ref(null)
const isDragging = ref(false)
const uploading = ref(false)
const uploadDone = ref(false)
const p1 = ref(0) // 上传
const p2 = ref(0) // 解析
const p3 = ref(0) // KG
const currentPercent = computed(() => {
  if (step.value === 1) return p1.value
  if (step.value === 2) return p2.value
  return p3.value // step≥3
})
const uploadStatus = ref('')
const step = ref(0)
const anims = {}; const animTimers = {}
function animProgress(targetRef, target, duration = 800) {
  const key = targetRef === p1 ? 'p1' : targetRef === p2 ? 'p2' : 'p3'
  if (targetRef._locked) return // 已被外部设置跳满，禁止动画覆盖
  const start = targetRef.value
  const diff = target - start
  if (Math.abs(diff) < 0.5) { targetRef.value = target; return }
  const startTime = Date.now()
  if (anims[key]) cancelAnimationFrame(anims[key])
  function tick() {
    const elapsed = Date.now() - startTime
    const t = Math.min(1, elapsed / duration)
    targetRef.value = start + diff * (1 - Math.pow(1 - t, 3))
    if (t < 1) anims[key] = requestAnimationFrame(tick)
    else targetRef.value = target
  }
  tick()
}
// 手动设置进度值并锁定（防止 setTimeout 动画覆盖）
function setProgress(targetRef, val) {
  targetRef._locked = true
  const key = targetRef === p1 ? 'p1' : targetRef === p2 ? 'p2' : 'p3'
  if (animTimers[key]) { clearTimeout(animTimers[key]); delete animTimers[key] }
  if (anims[key]) { cancelAnimationFrame(anims[key]); delete anims[key] }
  targetRef.value = val
}
// 带锁的延时动画启动
function animWithTimer(targetRef, target, duration, delay) {
  const key = targetRef === p1 ? 'p1' : targetRef === p2 ? 'p2' : 'p3'
  if (targetRef._locked) return // 已锁定不覆盖
  animTimers[key] = setTimeout(() => animProgress(targetRef, target, duration), delay)
}

// 速度乘数：1MB→1x, 10MB→1.5x, 100MB→2.5x, 500MB→4x，最低 1x
function speedMul(mb) { return Math.max(1, 1 + Math.min(3, Math.log2(Math.max(1, mb / 5)))) }

// 各阶段动画（最低 1s，大文件更慢）
let fileMb = 1
function startUploadAnim() {
  const s = speedMul(fileMb)
  p1._locked = false
  animWithTimer(p1, 80, 2000 * s, 200)
  animWithTimer(p1, 97, 3000 * s, 2200 * s)
}
function startParseAnim() {
  p2.value = 0; p2._locked = false
  const s = speedMul(fileMb)
  animWithTimer(p2, 80, 2000 * s, 200)
  animWithTimer(p2, 97, 3000 * s, 2200 * s)
}
function startKgAnim() {
  p3.value = 0; p3._locked = false
  const s = speedMul(fileMb)
  animWithTimer(p3, 70, 3000 * s, 200)
  animWithTimer(p3, 95, 5000 * s, 3200 * s)
}
const bookId = ref(null)
const toast = ref({ show: false, text: '', type: 'success' })
const useChunked = ref(false)
const doneChunks = ref(0)
const totalChunks = ref(0)
const coverReady = ref(false)
const chunkProgress = computed(() => {
  if (totalChunks.value === 0) return 0
  return Math.round((doneChunks.value / totalChunks.value) * 100)
})

const form = ref({ title: '', author: '', category: '' })
const errors = ref({})
const doneModal = ref({ show: false })
const showCategoryModal = ref(false)
const catDropdownOpen = ref(false)
const selectedCat = computed(() => categoryOptions.value.find(c => c.label === form.value.category))
function toggleCatDropdown() { catDropdownOpen.value = !catDropdownOpen.value }
function selectCat(c) { form.value.category = c.label; errors.value.category = ''; catDropdownOpen.value = false }

const catStore = useCategoryStore()
// 表情选择器（与主页共享 store 中的 ep）
const { ep, allEmojis, epNav, epOpen, epRow, epKeydown } = catStore
const uploadNewCatName = ref('')

const apiCategories = computed(() => catStore.list)
function loadApiCategories() { catStore.load() }
const categoryOptions = computed(() => {
  catStore.rev
  let cats = apiCategories.value.filter(c => c.label !== '全部')
  if (!cats.find(c => c.label === '其他')) cats.push({ id: 'other_fallback', label: '其他', emoji: '📂' })
  return cats
})

const canUpload = computed(() => file.value && form.value.category)

const fileIcons = { pdf: '📄', docx: '📝', doc: '📝', txt: '📃', epub: '📕', md: '📝' }
const fileIcon = computed(() => {
  if (!file.value) return '📁'
  const ext = file.value.name.split('.').pop().toLowerCase()
  return fileIcons[ext] || '📁'
})

function formatSize(bytes) {
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1048576) return (bytes / 1024).toFixed(1) + ' KB'
  if (bytes < 1073741824) return (bytes / 1048576).toFixed(1) + ' MB'
  return (bytes / 1073741824).toFixed(1) + ' GB'
}

async function saveUploadCategory() {
  const name = uploadNewCatName.value.trim()
  if (!name) return
  try {
    await categoryAPI.create(name, ep.selected)
    await loadApiCategories()
    uploadNewCatName.value = ''
    showCategoryModal.value = false
  } catch (e) { console.error('分类创建失败', e) }
}

function getExt(name) { return name.split('.').pop().toLowerCase() }

function setFile(f) {
  if (f.size > 5 * 1024 * 1024 * 1024) { showToast('文件不能超过5GB', 'error'); return }
  if (!['pdf', 'docx', 'doc', 'txt', 'epub', 'md'].includes(getExt(f.name))) { showToast('不支持的文件格式', 'error'); return }
  file.value = f
  form.value.title = f.name.replace(/\.[^.]+$/, '')
}

function handleFileSelect(e) { if (e.target.files[0]) setFile(e.target.files[0]) }
function handleDrop(e) { isDragging.value = false; if (e.dataTransfer.files[0]) setFile(e.dataTransfer.files[0]) }
function handleDragLeave(e) { if (e.currentTarget === e.target) isDragging.value = false }
function clearFile() { file.value = null; uploadDone.value = false; p1.value = 0; p2.value = 0; p3.value = 0; step.value = 1 }

function showToast(text, type = 'success') {
  toast.value = { show: true, text, type }
  setTimeout(() => toast.value.show = false, 3000)
}

async function handleUpload() {
  errors.value = {}
  if (!form.value.title.trim()) { errors.value.title = '书名不能为空'; return }
  if (!form.value.category) { errors.value.category = '请选择分类'; return }
  // 如果选了兜底的「其他」且数据库中不存在，自动创建
  if (form.value.category === '其他' && !apiCategories.value.find(c => c.label === '其他')) {
    try { await categoryAPI.create('其他', '📂'); await loadApiCategories() } catch (e) {}
  }

  uploading.value = true
  uploadDone.value = false
  p1.value = 0; p2.value = 0; p3.value = 0
  step.value = 1
  uploadStatus.value = '正在上传...'
  fileMb = file.value.size / (1024 * 1024)
  useChunked.value = file.value.size > LARGE_FILE_THRESHOLD
  startUploadAnim()

  // 滚动到进度区域
  await nextTick()
  const el = document.querySelector('.progress-section')
  if (el) el.scrollIntoView({ behavior: 'smooth', block: 'nearest' })

  try {
    let book

    if (useChunked.value) {
      book = await uploadChunked()
    } else {
      book = await uploadDirect()
    }

    bookId.value = book.id
    // 上传完成 → 缓升到 100% 并停留 2s 让用户看清
    animProgress(p1, 100, 1500)
    await new Promise(r => setTimeout(r, 1500))
    setProgress(p1, 100)
    uploadStatus.value = '上传完成 100%'
    await new Promise(r => setTimeout(r, 800))
    step.value = 2
    uploadStatus.value = '解析文本中...'
    startParseAnim()

    // 轮询检测后端 status
    let lastStatus = 0
    const pollTimer = setInterval(async () => {
      try {
        const res = await request.get('/books/' + bookId.value)
        const b = res.data
        const bp = b.progress || 0

        if (b.status !== lastStatus) {
          if (b.status === 1 && step.value === 2) {
            setProgress(p2, 100) // 解析完成
          }
          if (b.status >= 2 && step.value < 3) {
            setProgress(p2, 100) // 确保解析已满
            step.value = 3; startKgAnim()
          }
          lastStatus = b.status
        }
        if (b.status === 2 && p3.value >= 95) animProgress(p3, 95 + (bp / 100) * 4, 3000)
        uploadStatus.value = p2.value >= 100 ? '解析完成，可点击阅读' :
          (b.processMessage || (step.value === 2 ? '解析文本中...' : '知识图谱生成中...'))

        if (b.coverUrl) { coverReady.value = true }
        if (b.status === 3) {
          clearInterval(pollTimer)
          p1.value = 100; p2.value = 100; p3.value = 100
          uploadStatus.value = '全部完成！'
          uploading.value = false; uploadDone.value = true
          doneModal.value.show = true
        } else if (b.status === 4) {
          clearInterval(pollTimer)
          uploading.value = false
          showToast(b.processMessage || '处理失败', 'error')
        }
      } catch (e) { /* poll继续 */ }
    }, 2000)

  } catch (e) {
    uploading.value = false
    showToast(e.message || '上传失败', 'error')
  }
}

// ===== 直接上传（≤100MB） =====
async function uploadDirect() {
  uploadStatus.value = '上传到 MinIO...'
  animProgress(p1, 0)

  const fd = new FormData()
  fd.append('file', file.value)
  fd.append('title', form.value.title)
  if (form.value.author) fd.append('author', form.value.author)
  fd.append('category', form.value.category)

  const book = await bookStore.uploadBook(fd)
  return book
}

// ===== 分片上传（>100MB） =====
async function uploadChunked() {
  step.value = 1
  uploadStatus.value = '初始化上传会话...'
  animProgress(p1, 0, 300)

  // 1. 初始化
  const initRes = await uploadAPI.init(file.value.name, file.value.size)
  const { uploadId, totalChunks: total } = initRes.data
  totalChunks.value = total
  doneChunks.value = 0

  // 2. 并发上传分片（4 个 worker 抢占式）
  const CONCURRENCY = 4
  let nextIdx = 0

  async function worker() {
    while (nextIdx < total) {
      const i = nextIdx++

      // 检查分片是否已上传（断点恢复）
      const statusRes = await uploadAPI.checkChunk(uploadId, i)
      if (statusRes.data && statusRes.data.uploaded) {
        doneChunks.value++
        continue
      }

      // 切割分片
      const start = i * CHUNK_SIZE
      const end = Math.min(start + CHUNK_SIZE, file.value.size)
      const blob = file.value.slice(start, end)

      // 上传
      await uploadAPI.uploadChunk(uploadId, i, blob)
      doneChunks.value++
      uploadStatus.value = `上传分片 ${doneChunks.value}/${total}`
    }
  }

  await Promise.all(Array.from({ length: CONCURRENCY }, () => worker()))

  // 3. 完成上传 → 合并分片并创建书籍
  uploadStatus.value = '合并分片中...'
  animProgress(p1, 45, 600)

  const completeRes = await uploadAPI.complete(uploadId, {
    title: form.value.title,
    author: form.value.author || '',
    category: form.value.category,
  })

  // 返回书籍对象（status=0，MQ 异步处理中）
  return completeRes.data
}

function goRead() {
  if (bookId.value) router.push({ name: 'reader-chapter', params: { bookId: bookId.value, chapterNumber: 1 } })
}
async function pauseAndFinish() {
  // 暂停 KG：调后端取消 + 跳完成状态
  try {
    if (bookId.value) await graphAPI.cancelGraph(bookId.value)
  } catch (e) { /* ignore */ }
  p3.value = 100
  uploadStatus.value = '知识图谱已暂停'
  uploading.value = false; uploadDone.value = true
  doneModal.value.show = true
}

onMounted(() => {
  loadApiCategories()
  document.addEventListener('click', (e) => {
    if (!e.target.closest('.cat-compact') && !e.target.closest('.cat-dropdown')) catDropdownOpen.value = false
  })
  document.addEventListener('visibilitychange', () => { if (document.visibilityState === 'visible') loadApiCategories() })
  window.addEventListener('focus', loadApiCategories)
})
</script>

<style scoped>
.upload-page { min-height: 100vh; padding-top: 20px; }
.upload-header { text-align: center; margin-bottom: 28px; }
.page-title { font-family: var(--font-heading); font-size: 26px; font-weight: 700; color: var(--text-primary); }
.page-subtitle { font-size: 14px; color: var(--text-muted); margin-top: 4px; }

.dropzone {
  border: 2px dashed var(--border-medium); border-radius: var(--radius-lg);
  padding: 32px; text-align: center; cursor: pointer;
  transition: all 0.25s; background: var(--bg-white);
  margin-bottom: 20px;
}
.dropzone:hover { border-color: var(--accent-terracotta); }
.dropzone--dragover { border-color: var(--accent-terracotta); background: rgba(198,123,92,0.05); transform: scale(1.01); }
.dropzone--has-file { border-style: solid; border-color: var(--border-light); cursor: default; }

.dropzone-icon { color: var(--text-muted); margin-bottom: 12px; }
.dropzone-text { font-size: 15px; color: var(--text-primary); margin-bottom: 6px; }
.dropzone-link { color: var(--accent-terracotta); font-weight: 500; text-decoration: underline; }
.dropzone-hint { font-size: 12px; color: var(--text-muted); }
.hidden { display: none; }

.dropzone-file { display: flex; align-items: center; gap: 14px; }
.file-icon { font-size: 32px; }
.file-info { flex: 1; text-align: left; }
.file-name { font-size: 14px; font-weight: 500; color: var(--text-primary); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.file-size { font-size: 12px; color: var(--text-muted); }
.file-size--chunk { color: var(--accent-terracotta); font-weight: 500; }
.file-clear {
  width: 28px; height: 28px; border-radius: 50%; border: none;
  background: rgba(200,180,160,0.2); cursor: pointer; color: var(--text-muted);
  display: flex; align-items: center; justify-content: center;
  transition: background 0.2s; font-size: 12px;
}
.file-clear:hover { background: rgba(200,80,60,0.15); color: var(--accent-rose); }

.progress-section { margin-bottom: 20px; animation: fadeIn 0.3s ease; }
@keyframes fadeIn { from { opacity: 0; transform: translateY(8px); } to { opacity: 1; transform: translateY(0); } }
.progress-header { display: flex; justify-content: space-between; margin-bottom: 8px; }
.progress-label { font-size: 13px; color: var(--text-secondary); }
.progress-pct { font-size: 13px; font-weight: 600; color: var(--accent-terracotta); }
.progress-track { height: 6px; border-radius: 3px; background: #F0E8DC; overflow: hidden; margin-bottom: 16px; }
.progress-fill { height: 100%; background: linear-gradient(90deg, var(--accent-terracotta), var(--accent-gold)); border-radius: 3px; }

.steps { display: flex; align-items: center; justify-content: center; gap: 0; margin-top: 12px; }
.step { display: flex; flex-direction: column; align-items: center; gap: 2px; }
.step-dot { width: 28px; height: 28px; border-radius: 50%; display: flex; align-items: center; justify-content: center; font-size: 13px; background: #F0E8DC; transition: all 0.3s; }
.step--done .step-dot { background: var(--accent-terracotta); }
.step--active .step-dot { background: var(--accent-terracotta); animation: stepPulse 1.5s ease-in-out infinite; }
.step-text { font-size: 10px; color: var(--text-muted); }
.step--active .step-text { color: var(--accent-terracotta); font-weight: 600; }
.step--done .step-text { color: var(--accent-terracotta); }
.step-line { width: 40px; height: 2px; background: #F0E8DC; transition: background 0.3s; margin-bottom: 20px; }
.step-line--done { background: var(--accent-terracotta); }
.step-line--active { background: linear-gradient(90deg, var(--accent-terracotta), #F0E8DC); background-size: 200% 100%; animation: stepActive 1.5s ease infinite; }
@keyframes stepActive { 0% { background-position: 100% 0; } 100% { background-position: -100% 0; } }
@keyframes stepPulse { 0%, 100% { box-shadow: 0 0 0 0 rgba(192,57,43,0.4); } 50% { box-shadow: 0 0 0 8px rgba(192,57,43,0); } }
.progress-fill { height: 100%; background: linear-gradient(90deg, var(--accent-terracotta), #D4A853); border-radius: 4px; }

.form-section { background: var(--bg-white); border-radius: var(--radius-md); padding: 24px; border: 1px solid var(--border-light); margin-bottom: 20px; }
.form-row { margin-bottom: 16px; }
.form-row:last-child { margin-bottom: 0; }
.form-label { display: block; font-size: 13px; font-weight: 500; color: var(--text-primary); margin-bottom: 6px; }
.form-error { font-size: 12px; color: var(--accent-rose); margin-top: 4px; }

/* 紧凑分类选择器 */
.cat-compact { display: flex; gap: 8px; }
.cat-compact-main {
  flex: 1; display: flex; align-items: center; gap: 8px;
  padding: 10px 14px; border-radius: 24px;
  border: 1.5px solid #DCD0C0; background: var(--bg-cream);
  cursor: pointer; transition: all 0.2s; user-select: none;
}
.cat-compact-main:hover { border-color: var(--accent-terracotta); background: #FFFCF8; }
.cat-compact-emoji { font-size: 18px; }
.cat-compact-label { flex: 1; font-size: 14px; color: var(--text-primary); }
.cat-compact-chevron { color: var(--text-muted); transition: transform 0.2s; }
.cat-compact-chevron.rotated { transform: rotate(180deg); }
.cat-compact-new {
  width: 42px; height: 42px; border-radius: 24px;
  border: 1.5px dashed #DCD0C0; background: var(--bg-cream);
  cursor: pointer; font-size: 18px; display: flex; align-items: center; justify-content: center;
  transition: all 0.2s; flex-shrink: 0;
}
.cat-compact-new:hover { border-color: var(--accent-terracotta); background: #FFFCF8; }

/* 分类下拉菜单 */
.cat-dropdown {
  margin-top: 6px; padding: 4px;
  border-radius: var(--radius-sm); border: 1px solid var(--border-light);
  background: var(--bg-white); box-shadow: var(--shadow-md);
  max-height: 240px; overflow-y: auto;
}
.cat-dropdown-item {
  display: flex; align-items: center; gap: 8px;
  padding: 8px 10px; border-radius: 6px; cursor: pointer;
  font-size: 13px; color: var(--text-secondary); transition: background 0.1s;
}
.cat-dropdown-item:hover { background: var(--bg-cream); }
.cat-dropdown-item--sel { background: rgba(198,123,92,0.1); color: var(--accent-terracotta); }
.cat-dropdown-emoji { font-size: 16px; }
.cat-dropdown-label { flex: 1; }
.cat-dropdown-check { color: var(--accent-terracotta); font-weight: 600; font-size: 12px; }
.cat-dropdown-item { border-radius: 6px; }

/* 新建分类弹窗 */
.new-cat-body { display: flex; flex-direction: column; gap: 16px; margin-top: 12px; }
.new-cat-emoji-area { display: flex; flex-direction: column; align-items: center; gap: 6px; }
.new-cat-emoji-label { font-size: 12px; color: var(--text-muted); font-weight: 500; align-self: flex-start; }
.new-cat-preview { font-size: 13px; color: var(--text-secondary); }
.new-cat-name-area { display: flex; flex-direction: column; gap: 6px; }
.new-cat-name-label { font-size: 12px; color: var(--text-muted); font-weight: 500; }
.new-cat-name-input { width: 100%; padding: 10px 14px; border-radius: 24px; border: 1.5px solid #DCD0C0; background: var(--bg-cream); outline: none; font-size: 14px; }
.new-cat-name-input:focus { border-color: var(--accent-terracotta); background: #FFFCF8; }

/* 老虎机 emoji 样式（复用 Home 的风格） */
.emoji-slot { display: flex; flex-direction: column; align-items: center; gap: 8px; padding: 16px; background: var(--bg-cream); border-radius: 16px; border: 1px solid var(--border-light); outline: none; }
.emoji-slot:focus { border-color: var(--accent-terracotta); box-shadow: 0 0 0 2px rgba(198,123,92,0.12); }
.emoji-btn { background: rgba(200,180,160,0.08); border: none; cursor: pointer; font-size: 13px; width: 28px; height: 28px; border-radius: 6px; display: flex; align-items: center; justify-content: center; transition: all 0.15s; line-height: 1; color: #8B7D6B; }
.emoji-btn:hover { color: var(--accent-terracotta); background: rgba(198,123,92,0.12); }
.ep-mid .emoji-btn { width: 25px; height: 25px; font-size: 11px; }
.ep-mid { display: flex; align-items: center; gap: 8px; }
.ep-up { margin-bottom: -4px; }
.ep-down { margin-top: -4px; }
.emoji-grid { display: flex; flex-direction: column; align-items: center; gap: 2px; padding: 12px; background: radial-gradient(circle, rgba(198,123,92,0.12) 0%, rgba(198,123,92,0.04) 60%, transparent 80%); border-radius: 50%; }
.emoji-slot-row { display: flex; gap: 6px; line-height: 1; user-select: none; justify-content: center; }
.emoji-slot-row--dim .emoji-cell { font-size: 34px; opacity: 0.2; cursor: default; }
.emoji-slot-row--current .emoji-cell { font-size: 34px; cursor: pointer; }
.emoji-cell { padding: 2px 6px; border-radius: 10px; transition: all 0.1s; }
.emoji-slot-row--current .emoji-cell:hover { background: rgba(198,123,92,0.1); transform: scale(1.2); }
.emoji-cell--sel { background: rgba(198,123,92,0.18) !important; transform: scale(1.3); box-shadow: 0 2px 12px rgba(198,123,92,0.25); }
.category-card-emoji { font-size: 22px; line-height: 1; }
.category-card-label { font-size: 11px; color: var(--text-secondary); font-weight: 500; }

.upload-btn { width: 100%; padding: 12px; font-size: 16px; }

@media (max-width: 768px) {
  .upload-header { margin-bottom: 16px; }
  .upload-header h1 { font-size: 22px; }
  .upload-form { padding: 16px; }
  .category-grid { grid-template-columns: repeat(3, 1fr); gap: 6px; }
  .drop-zone { padding: 24px 16px; min-height: 120px; }
  .drop-zone svg { width: 36px; height: 36px; }
}
.btn-spinner { animation: spin 0.8s linear infinite; }
@keyframes spin { to { transform: rotate(360deg); } }
</style>
