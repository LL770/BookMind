<template>
  <div class="chat-page">
    <div class="chat-container">
      <!-- 左侧会话侧边栏 -->
      <aside class="chat-sidebar" :class="{ 'chat-sidebar--hidden': !showSidebar }">
        <div class="sidebar-top-actions">
          <button @click="showGlobalSearch = true" class="icon-btn" title="搜索对话">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="11" cy="11" r="8"/><path d="M21 21l-4.35-4.35"/></svg>
          </button>
          <button @click="showSidebar = false" class="icon-btn" title="折叠侧边栏">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M15 19l-7-7 7-7"/></svg>
          </button>
        </div>
        <button @click="startNewChat" class="sidebar-new-chat-btn">
          <svg width="16" height="16" viewBox="0 0 16 16" fill="none"><path d="M8 0.599609C3.91309 0.599609 0.599609 3.91309 0.599609 8C0.599609 9.13376 0.855461 10.2098 1.3125 11.1719L1.5918 11.7588L2.76562 11.2012L2.48633 10.6143C2.11034 9.82278 1.90039 8.93675 1.90039 8C1.90039 4.63106 4.63106 1.90039 8 1.90039C11.3689 1.90039 14.0996 4.63106 14.0996 8C14.0996 11.3689 11.3689 14.0996 8 14.0996C7.31041 14.0996 6.80528 14.0514 6.35742 13.9277C5.91623 13.8059 5.49768 13.6021 4.99707 13.2529C4.26492 12.7422 3.21611 12.5616 2.35156 13.1074L2.33789 13.1162L2.32422 13.126L1.58789 13.6436L2.01953 14.9297L3.0459 14.207C3.36351 14.0065 3.83838 14.0294 4.25293 14.3184C4.84547 14.7317 5.39743 15.011 6.01172 15.1807C6.61947 15.3485 7.25549 15.4004 8 15.4004C12.0869 15.4004 15.4004 12.0869 15.4004 8C15.4004 3.91309 12.0869 0.599609 8 0.599609ZM7.34473 4.93945V7.34961H4.93945V8.65039H7.34473V11.0605H8.64551V8.65039H11.0605V7.34961H8.64551V4.93945H7.34473Z" fill="currentColor"/></svg>
          <span>开启新对话</span>
        </button>
        <div class="sidebar-history">
          <div class="sidebar-list">
            <div v-for="s in filteredSessions" :key="s.sessionId"
              @click="switchSession(s.sessionId)"
              class="session-item"
              :class="{ 'session-item--active': activeSessionId === s.sessionId }">
              <div class="session-item-content">
                <span class="session-icon">{{ isPinned(s) ? '📌' : '💬' }}</span>
                <span class="session-name">{{ s.chatname || '新对话' }}</span>
              </div>
              <button @click.stop="openSessionMenu($event, s)" class="session-menu-btn">⋮</button>
            </div>
            <div v-if="filteredSessions.length === 0" class="sidebar-empty">暂无历史对话</div>
          </div>
        </div>
      </aside>
      <!-- 移动端遮罩 -->
      <div v-if="showSidebar" class="sidebar-overlay" @click="showSidebar = false"></div>

      <!-- 右侧主对话区 -->
      <div class="chat-main">
        <div class="chat-header">
          <div class="chat-header-left" v-if="!showSidebar">
            <button @click="showSidebar = true" class="icon-btn" title="展开侧边栏">
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M9 5l7 7-7 7"/></svg>
            </button>
            <button @click="showGlobalSearch = true" class="icon-btn" title="搜索对话">
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="11" cy="11" r="8"/><path d="M21 21l-4.35-4.35"/></svg>
            </button>
            <button @click="startNewChat" class="icon-btn" title="新建对话">
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M12 5v14M5 12h14"/></svg>
            </button>
          </div>
          <div class="chat-header-center">
            <div class="mode-tabs">
              <button @click="setMode('global')" class="mode-tab" :class="{ 'mode-tab--active': chatMode === 'global' }">🌐 自由对话</button>
              <button @click="setMode('book')" class="mode-tab" :class="{ 'mode-tab--active': chatMode === 'book' }">📖 书籍对话</button>
            </div>
          </div>
        </div>
        <div v-if="chatMode === 'book' && books.length > 0" class="book-selector">
          <div class="book-chips">
            <button @click="selectBook(null)" class="book-chip" :class="{ 'book-chip--active': !selectedBook }">📚 所有书籍</button>
            <button v-for="book in books" :key="book.id" @click="selectBook(book)" class="book-chip" :class="{ 'book-chip--active': selectedBook?.id === book.id }">{{ book.title }}</button>
          </div>
        </div>

        <div class="chat-scroll">
          <div class="messages-area" ref="msgContainer">
            <div v-if="loadingHistory && sessions.length > 0" class="loading-indicator">
              <div class="loading-spinner"></div>
              <p class="loading-text">加载中...</p>
            </div>

            <template v-else-if="messages.length > 0">
              <div v-for="(msg, i) in messages" :key="i" class="message" :class="'message--' + msg.role">
                <div class="message-content">
                  <details v-if="msg.thinking" class="thinking-details">
                    <summary class="thinking-summary">🤔 思考过程</summary>
                    <div class="thinking-content-static">{{ msg.thinking }}</div>
                  </details>
                  <div v-if="msg.content" class="message-text markdown-body" v-html="renderMarkdown(msg.content)"></div>
                </div>
              </div>
              <div v-if="streaming" class="message message--assistant">
                <div class="message-content">
                  <details v-if="streamThinking" class="thinking-details" open>
                    <summary class="thinking-summary">🤔 思考过程</summary>
                    <div class="thinking-content-static">{{ streamThinking }}</div>
                  </details>
                  <div v-if="streamAnswer" class="message-text markdown-body" :class="{ streaming: !streamingEnded }" v-html="renderMarkdown(streamAnswer)"></div>
                  <p v-else class="stream-placeholder">思考中...</p>
                </div>
              </div>
              <div ref="bottomAnchor" style="height:1px"></div>
            </template>
            <div v-else class="chat-welcome">
              <div class="welcome-icon">
                <svg viewBox="0 0 100 60" fill="none" xmlns="http://www.w3.org/2000/svg">
                  <defs>
                    <linearGradient id="cg" x1="0" y1="0" x2="1" y2="1">
                      <stop offset="0%" stop-color="var(--accent-terracotta)" stop-opacity="0.10"/>
                      <stop offset="100%" stop-color="var(--accent-gold)" stop-opacity="0.06"/>
                    </linearGradient>
                  </defs>
                  <path d="M18 44c-3-2-5-5-5-9 0-5 4-9 9-10 1-6 6-10 12-10 6 0 11 4 13 9 2-1 5-2 8-2 6 0 11 3 13 8 3 0 6 2 7 5 1 2 0 5-2 7-1 1-2 2-4 2H20c-1 0-2-1-2-1z" stroke="var(--accent-terracotta)" stroke-width="1.3" stroke-linecap="round" stroke-linejoin="round" fill="url(#cg)"/>
                  <path d="M24 36c1-2 3-4 6-4s5 1 7 3" stroke="var(--accent-gold)" stroke-width="0.9" stroke-linecap="round" stroke-opacity="0.3" fill="none"/>
                  <path d="M46 32c2-3 5-5 9-5s7 2 9 5" stroke="var(--accent-gold)" stroke-width="0.9" stroke-linecap="round" stroke-opacity="0.25" fill="none"/>
                  <path d="M62 34c2-2 4-3 7-3s5 1 6 3" stroke="var(--accent-gold)" stroke-width="0.7" stroke-linecap="round" stroke-opacity="0.15" fill="none"/>
                </svg>
              </div>
              <p class="welcome-text">{{ chatMode === 'global' ? '有什么想聊的？' : '选择书籍开始讨论' }}</p>
            </div>
          </div>
        </div>

        <button v-if="showScrollBtn" @click="scrollToBottom" class="scroll-to-bottom-btn">↓</button>

        <!-- 会话菜单 -->
        <div v-if="sessionMenu.show" class="menu-popup" :style="{ left: sessionMenu.x + 'px', top: sessionMenu.y + 'px' }" @click.stop>
          <button @click.stop="togglePinSession(sessionMenu.session)" class="menu-popup-btn">{{ isPinned(sessionMenu.session) ? '📌 取消置顶' : '📌 置顶对话' }}</button>
          <button @click.stop="openRenameDialog(sessionMenu.session)" class="menu-popup-btn">✏️ 重命名</button>
          <button @click.stop="deleteSession(sessionMenu.session)" class="menu-popup-btn menu-popup-btn--danger">🗑️ 删除对话</button>
        </div>

        <!-- 输入区 -->
        <div class="input-area">
          <div class="input-container">
            <input v-model="inputMessage" @keydown.enter="sendMessage" placeholder="输入消息..." autocomplete="off" style="border:none;outline:none;background:transparent;box-shadow:none" />
            <button v-if="streaming" @click="stopStreaming" class="stop-btn" title="停止"><svg width="16" height="16" viewBox="0 0 24 24" fill="currentColor"><rect x="5" y="5" width="14" height="14" rx="2"/></svg></button>
            <button v-show="!streaming" @click="sendMessage" :disabled="!inputMessage.trim()" class="send-btn">➤</button>
          </div>
        </div>
      </div>
    </div>

    <!-- 搜索弹窗 -->
    <div v-if="showGlobalSearch" class="modal-overlay" @click.self="showGlobalSearch = false">
      <div class="search-dialog">
        <div class="search-dialog-header">
          <h3>🔍 搜索历史对话</h3>
          <button @click="showGlobalSearch = false" class="search-close-btn">✕</button>
        </div>
        <input v-model="searchQuery" @input="debouncedSearch" @keydown.enter="doSearch" placeholder="搜索消息内容..." class="search-input" />
        <div v-if="searchResults.length > 0" class="search-results">
          <div v-for="r in searchResults" :key="r.sessionId" @click="switchSession(r.sessionId); showGlobalSearch = false" class="search-result-item">
            <span>{{ r.matchRole === 'user' ? '👤' : '🤖' }}</span>
            <div style="min-width:0;flex:1">
              <div class="search-result-name">{{ r.chatname }}</div>
              <div class="search-result-preview">{{ r.preview }}</div>
              <div class="search-result-time">{{ r.fromNow }}</div>
            </div>
          </div>
        </div>
        <div v-else-if="searched" class="search-empty">未找到相关对话</div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onActivated, onUnmounted, nextTick, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useBookStore, useChatStore } from '@/stores'
import request from '@/api/request'
import { marked } from 'marked'

marked.setOptions({ breaks: true, gfm: true })

function renderMarkdown(text) {
  if (!text) return ''
  const cleaned = text.replace(/\n{3,}/g, '\n\n')
  return marked.parse(cleaned)
}

const router = useRouter()
const bookStore = useBookStore()
const chatStore = useChatStore()
const chatMode = ref(localStorage.getItem('chatMode') || 'global')
const books = ref([])
const selectedBook = ref(null)
const showSidebar = ref(window.innerWidth > 768)
const showGlobalSearch = ref(false)
const searchQuery = ref('')
const searchResults = ref([])
const searched = ref(false)
let searchTimer = null
function debouncedSearch() { clearTimeout(searchTimer); searchTimer = setTimeout(() => doSearch(), 300) }
async function doSearch() {
  const q = searchQuery.value.trim()
  if (!q) { searchResults.value = []; searched.value = false; return }
  searched.value = true
  try {
    const token = localStorage.getItem('token')
    const mode = curMode()
    const res = await fetch(`/api/chat/sessions/search?keyword=${encodeURIComponent(q)}&mode=${mode}`, {
      headers: { 'Authorization': `Bearer ${token}` }
    })
    const json = await res.json()
    searchResults.value = json.data || []
  } catch (e) {
    searchResults.value = sessions.value.filter(s => (s.chatname || '').toLowerCase().includes(q.toLowerCase()))
  }
}
const inputMessage = ref('')
const msgContainer = ref(null)
const bottomAnchor = ref(null)
const messages = ref([])
const sessions = ref([])
const sessionMenu = ref({ show: false, x: 0, y: 0, session: null })
const renameDialog = ref({ show: false, name: '', session: null })
const loadingHistory = ref(false)
const showScrollBtn = ref(false)
const sessionSearch = ref('')
let currentAbort = null
let userStopped = false

const streamState = ref({
  global: { streaming: false, streamingEnded: false, content: '', thinking: '', answer: '', showThinking: true },
  book:   { streaming: false, streamingEnded: false, content: '', thinking: '', answer: '', showThinking: true }
})
function ss(key) { return streamState.value[chatMode.value][key] }
function setSs(key, val) { streamState.value[chatMode.value][key] = val }
const streaming = computed({ get: () => ss('streaming'), set: v => setSs('streaming', v) })
const streamingEnded = computed({ get: () => ss('streamingEnded'), set: v => setSs('streamingEnded', v) })
const streamContent = computed({ get: () => ss('content'), set: v => setSs('content', v) })
const streamThinking = computed({ get: () => ss('thinking'), set: v => setSs('thinking', v) })
const streamAnswer = computed({ get: () => ss('answer'), set: v => setSs('answer', v) })
const showStreamThinking = computed({ get: () => ss('showThinking'), set: v => setSs('showThinking', v) })

const activeSid = ref(JSON.parse(localStorage.getItem('chatActiveSid') || '{"global":"default","book":"default"}'))
const activeSessionId = computed(() => activeSid.value[chatMode.value])

function curMode() { return chatMode.value === 'global' ? 'local' : 'book' }

let initialPins = []
try { initialPins = JSON.parse(localStorage.getItem('pinnedSessions') || '[]') } catch (e) {}
const pinnedSessions = ref(initialPins)
watch(pinnedSessions, (v) => { localStorage.setItem('pinnedSessions', JSON.stringify(v)) }, { deep: true })

const filteredSessions = computed(() => {
  const pins = pinnedSessions.value
  const q = sessionSearch.value.toLowerCase().trim()
  let list = sessions.value
  if (q) list = list.filter(s => (s.chatname || '').toLowerCase().includes(q))
  return [...list].sort((a, b) => {
    const ap = pins.includes(a.sessionId) ? 0 : 1
    const bp = pins.includes(b.sessionId) ? 0 : 1
    if (ap !== bp) return ap - bp
    return (b.lastTime || 0) - (a.lastTime || 0)
  })
})

const refreshSessions = async () => {
  try {
    const modeParam = curMode()
    const res = await request.get('/chat/sessions', { params: { mode: modeParam } })
    sessions.value = res.data || []
  } catch (e) {}
}

let saveMsgTimer = null
function saveMessages(immediate) {
  if (saveMsgTimer && !immediate) return
  clearTimeout(saveMsgTimer)
  saveMsgTimer = null
  if (immediate) {
    try {
      if (messages.value.length > 0)
        localStorage.setItem('chatMsgs_' + chatMode.value, JSON.stringify(messages.value))
    } catch (e) {}
    return
  }
  saveMsgTimer = setTimeout(() => {
    saveMsgTimer = null
    try {
      if (messages.value.length > 0)
        localStorage.setItem('chatMsgs_' + chatMode.value, JSON.stringify(messages.value))
    } catch (e) {}
  }, 300)
}

const sessionCache = {}
const switchSession = async (sid) => {
  saveMessages()
  if (currentAbort) { currentAbort.abort(); currentAbort = null }
  streaming.value = false; streamingEnded.value = false
  streamContent.value = ''; streamThinking.value = ''; streamAnswer.value = ''
  activeSid.value[chatMode.value] = sid
  const key = curMode() + ':' + sid
  if (sessionCache[key]) {
    messages.value = sessionCache[key]
    loadingHistory.value = false
    await nextTick(); scrollToBottom()
    return
  }
  loadingHistory.value = true; messages.value = []
  try {
    const res = await request.get('/chat/history', { params: { sessionId: sid, mode: curMode() } })
    const msgs = (res.data || []).map(m => {
      const parsed = parseThinkingFromContent(m.content)
      return { role: m.role === 'assistant' ? 'assistant' : 'user', content: parsed.content, thinking: m.role === 'assistant' ? parsed.thinking : null, showThinking: false }
    })
    const savedT = localStorage.getItem(`think:${curMode()}:${sid}`)
    if (savedT && msgs.length > 0) {
      const last = msgs[msgs.length - 1]
      if (last.role === 'assistant' && !last.thinking) last.thinking = savedT
    }
    sessionCache[key] = msgs
    messages.value = msgs
    saveMessages()
    loadingHistory.value = false
    await nextTick(); scrollToBottom()
  } catch (e) { messages.value = [] }
  loadingHistory.value = false
}

// 5s idle timeout: if no content for 5s, stop reading (SSE stays alive)
const sendMessage = async () => {
  const msg = inputMessage.value.trim()
  if (!msg) return
  if (streaming.value && currentAbort) {
    userStopped = true
    currentAbort.abort(); currentAbort = null
  }
  inputMessage.value = ''
  messages.value.push({ role: 'user', content: msg })
  saveMessages()
  nextTick(() => scrollToBottom())
  const currentMode = chatMode.value
  // 书籍模式但无书籍 → 提示上传
  if (currentMode !== 'global' && books.value.length === 0) {
    messages.value.push({ role: 'assistant', content: '还没有上传书籍，请先去📤上传页面添加书籍后再来提问。' })
    streaming.value = false; saveMessages(); scrollToBottom(); return
  }
  streaming.value = true; streamContent.value = ''; streamThinking.value = ''; streamAnswer.value = ''; showStreamThinking.value = true
  const sid = activeSid.value[currentMode].startsWith('new_') ? activeSid.value[currentMode].substring(4) : activeSid.value[currentMode]
  const token = localStorage.getItem('token')
  const url = currentMode === 'global'
    ? '/api/chat/global?message=' + encodeURIComponent(msg) + '&sessionId=' + sid + '&token=' + token
    : selectedBook.value
      ? '/api/chat/book/' + selectedBook.value.id + '?message=' + encodeURIComponent(msg) + '&sessionId=' + sid + '&token=' + token
      : '/api/chat/books?message=' + encodeURIComponent(msg) + '&sessionId=' + sid + '&token=' + token
  const controller = new AbortController()
  const connectTimeout = setTimeout(() => controller.abort(), 90000)
  currentAbort = controller
  let safetyTimeout
  safetyTimeout = setTimeout(() => {
    if (streamState.value[currentMode]?.streaming) {
      streamState.value[currentMode] = { streaming: false, streamingEnded: false, content: '', thinking: '', answer: '', showThinking: true }
      if (currentAbort === controller) currentAbort = null
      scrollToBottom()
    }
  }, 30000)
  try {
    const response = await fetch(url, { signal: controller.signal })
    clearTimeout(connectTimeout)
    if (!response.ok) throw new Error('请求失败: ' + response.status)
    if (!response.body) throw new Error('响应体为空')
    const reader = response.body.getReader()
    const decoder = new TextDecoder()
    let buffer = ''
    let idleTimeout = 0  // 0=first read(60s), then 5s
    while (true) {
      const readP = reader.read()
      const timeout = idleTimeout || 15000
      const timer = new Promise((_, reject) => setTimeout(() => reject(new Error('READ_TIMEOUT')), timeout))
      let result
      try { result = await Promise.race([readP, timer]) } catch (e) { break }
      const { done, value } = result
      if (done) break
      if (!value || value.length === 0) continue
      buffer += decoder.decode(value, { stream: true })
      const lines = buffer.split('\n')
      buffer = lines.pop() || ''
      for (const line of lines) {
        if (line.startsWith('data:')) {
          const raw = line.substring(5)
          const data = raw.startsWith(' ') ? raw.substring(1) : raw
          if (data === '[DONE]') break
          streamContent.value += data || '\n'
          if (!idleTimeout) idleTimeout = 600
          const parsed = parseStreamContent(streamContent.value)
          streamThinking.value = parsed.thinking
          streamAnswer.value = parsed.answer
          scrollToBottom()
        }
      }
    }
    streamingEnded.value = true
    if (streamAnswer.value) {
      const final = streamThinking.value ? `<thinking>${streamThinking.value}</thinking>\n${streamAnswer.value}` : streamAnswer.value
      messages.value.push({ role: 'assistant', content: final, thinking: streamThinking.value || null, showThinking: false })
      saveMessages()
    }
  } catch (err) {
    if (err.name === 'AbortError' && userStopped) {
    } else if (err.name === 'AbortError' && !userStopped) {
      if (!streamAnswer.value) messages.value.push({ role: 'assistant', content: '⏰ 回答超时了' })
    } else if (streamAnswer.value) {
      messages.value.push({ role: 'assistant', content: streamAnswer.value + '\n\n_⚠️ 回答可能不完整（连接中断）_', thinking: streamThinking.value || null, showThinking: false })
    }
  } finally {
    clearTimeout(connectTimeout); clearTimeout(safetyTimeout)
    saveMessages()
    streamState.value[currentMode] = { streaming: false, streamingEnded: false, content: '', thinking: '', answer: '', showThinking: true }
    if (currentAbort === controller) currentAbort = null
    userStopped = false; scrollToBottom()
    try { await refreshSessions() } catch (e) {}
  }
}

function stopStreaming() {
  userStopped = true
  if (currentAbort) { currentAbort.abort(); currentAbort = null }
  if (streamAnswer.value && !streamingEnded.value) {
    messages.value.push({ role: 'assistant', content: streamAnswer.value + '\n\n_[对话已停止]', thinking: streamThinking.value || null, showThinking: false })
    saveMessages()
  }
  streaming.value = false; streamingEnded.value = false
  streamContent.value = ''; streamThinking.value = ''; streamAnswer.value = ''
}

const setMode = async (mode) => {
  if (chatMode.value === mode) return
  if (currentAbort) { currentAbort.abort(); currentAbort = null }
  streaming.value = false; streamingEnded.value = false; streamContent.value = ''; streamThinking.value = ''; streamAnswer.value = ''
  chatMode.value = mode; localStorage.setItem('chatMode', mode); selectedBook.value = null
  loadingHistory.value = true; messages.value = []
  await refreshSessions()
  if (sessions.value.length > 0) switchSession(sessions.value[0].sessionId)
}

const selectBook = (book) => {
  if (currentAbort) { currentAbort.abort(); currentAbort = null }
  streaming.value = false; streamingEnded.value = false; streamContent.value = ''; streamThinking.value = ''; streamAnswer.value = ''
  selectedBook.value = book; messages.value = []; activeSid.value[chatMode.value] = 'default'
}

const startNewChat = () => {
  activeSid.value[chatMode.value] = 'new_' + Date.now().toString(36)
  messages.value = []; streamContent.value = ''; streamThinking.value = ''; streamAnswer.value = ''
  streaming.value = false; showSidebar.value = true
  try { localStorage.removeItem('chatMsgs_' + curMode()) } catch (e) {}
}

function openSessionMenu(e, s) {
  e.stopPropagation(); sessionMenu.value.show = false
  const menuW = 140, menuH = 120
  let x = e.clientX + 10, y = e.clientY + 6
  if (x + menuW > window.innerWidth) x = window.innerWidth - menuW - 10
  if (y + menuH > window.innerHeight) y = window.innerHeight - menuH - 10
  sessionMenu.value = { show: true, x: Math.max(4, x), y: Math.max(4, y), session: s }
}
function closeSessionMenu() { sessionMenu.value.show = false }
function openRenameDialog(s) {
  renameDialog.value = { show: true, name: s.chatname || '', session: s }; sessionMenu.value.show = false
}
function isPinned(s) {
  return pinnedSessions.value.includes(s.sessionId)
}
function togglePinSession(s) {
  const idx = pinnedSessions.value.indexOf(s.sessionId)
  if (idx > -1) { pinnedSessions.value.splice(idx, 1) } else { pinnedSessions.value.push(s.sessionId) }
  sessionMenu.value.show = false
  refreshSessions()
}

async function deleteSession(s) {
  sessionMenu.value.show = false
  try {
    await request.delete('/chat/session', { params: { sessionId: s.sessionId, mode: curMode() } })
    if (activeSessionId.value === s.sessionId) { messages.value = []; activeSid.value[chatMode.value] = 'default' }
    try { localStorage.removeItem('chatMsgs_' + curMode()) } catch (e) {}
    await refreshSessions()
  } catch (e) { console.error('删除对话失败', e) }
}

async function confirmRename() {
  const name = renameDialog.value.name.trim()
  if (!name || !renameDialog.value.session) { renameDialog.value.show = false; return }
  try {
    await request.put('/chat/session/name', null, { params: { sessionId: renameDialog.value.session.sessionId, name, mode: curMode() } })
    await refreshSessions()
  } catch (e) {}
  renameDialog.value.show = false
}

function parseStreamContent(raw) {
  const m = raw.match(/<thinking>([\s\S]*?)<\/thinking>/)
  if (m) return { thinking: m[1].trim(), answer: raw.replace(/<thinking>[\s\S]*?<\/thinking>/g, '').trim() }
  return { thinking: '', answer: raw.trim() }
}
function parseThinkingFromContent(content) {
  if (!content) return { thinking: null, content: '' }
  const m = content.match(/<thinking>([\s\S]*?)<\/thinking>/)
  return m ? { thinking: m[1].trim(), content: content.replace(/<thinking>[\s\S]*?<\/thinking>/g, '').trim() } : { thinking: null, content: content.trim() }
}

function scrollToBottom(smooth = false) {
  if (bottomAnchor.value) {
    bottomAnchor.value.scrollIntoView({ behavior: smooth ? 'smooth' : 'auto' })
  }
}

onMounted(async () => {
  loadingHistory.value = true
  try { const data = await bookStore.loadBookList({ page: 1, size: 50 }); books.value = (data.records || []).filter(b => b.status >= 2) } catch (e) {}
  await refreshSessions()
  // 从 chatMsgs_ 恢复（占位消息在流式中已被 saveMessages 持久化）
  const raw = localStorage.getItem('chatMsgs_' + chatMode.value)
  if (raw) {
    try {
      const msgs = JSON.parse(raw)
      const savedSid = localStorage.getItem('chatSid_' + chatMode.value)
      // 缓存的会话 ID 不在当前用户的会话列表中 → 丢弃缓存
      if (savedSid && sessions.value.some(s => s.sessionId === savedSid)) {
        if (msgs.length > 0) messages.value = msgs
        activeSid.value[chatMode.value] = savedSid
      }
    } catch (e) {}
  }
  if (messages.value.length === 0) {
    try { localStorage.removeItem('chatBackup') } catch (e) {}
    if (sessions.value.length > 0) {
      await switchSession(sessions.value[0].sessionId)
      await nextTick(); scrollToBottom()
    }
  } else {
    loadingHistory.value = false
    await nextTick(); await nextTick(); scrollToBottom()
  }
  loadingHistory.value = false

  document.addEventListener('click', closeSessionMenu)
  const sbEl = document.querySelector('.sidebar-history')
  if (sbEl) sbEl.addEventListener('scroll', closeSessionMenu, { passive: true })
})

onActivated(async () => {
  await refreshSessions()
  await nextTick(); scrollToBottom()
})

onUnmounted(() => {
  if (currentAbort) { currentAbort.abort(); currentAbort = null }
  saveMessages(true)
  streaming.value = false
  document.removeEventListener('click', closeSessionMenu)
  const sbEl = document.querySelector('.sidebar-history')
  if (sbEl) sbEl.removeEventListener('scroll', closeSessionMenu)
})
</script>

<style scoped>
.chat-page { display: flex; flex: 1; min-height: 0; max-height: 100vh; }
.chat-container { display: flex; flex: 1; width: 100%; position: relative; }
.chat-sidebar { width: 260px; flex-shrink: 0; display: flex; flex-direction: column; background: var(--bg-cream); border-right: 1px solid var(--border-light); transition: width 0.25s, min-width 0.25s; overflow: hidden; height: 100%; position: sticky; top: 0; align-self: flex-start; }
.chat-sidebar--hidden { width: 0; min-width: 0; border-right: none; }
.sidebar-top-actions { display: flex; gap: 2px; padding: 8px; align-items: center; border-bottom: 1px solid var(--border-light); }
.sidebar-overlay { display: none; }
@media (max-width: 768px) {
  .sidebar-overlay { display: block; position: fixed; inset: 0; z-index: 150; background: rgba(44,24,16,0.3); }
}
.sidebar-history { flex: 1; overflow-y: auto; padding: 4px 8px; }
.sidebar-list { display: flex; flex-direction: column; gap: 2px; }
.session-item { display: flex; align-items: center; gap: 4px; padding: 8px 10px; border-radius: 8px; cursor: pointer; transition: background 0.2s ease, box-shadow 0.2s ease; }
.session-item:hover { background: rgba(200,180,160,0.2); }
.session-item--active { background: rgba(198,123,92,0.12); box-shadow: inset 3px 0 0 var(--accent-terracotta); }
.session-item--active:hover { background: rgba(198,123,92,0.18); }
.session-item-content { display: flex; align-items: center; gap: 10px; flex: 1; overflow: hidden; }
.session-icon { font-size: 14px; flex-shrink: 0; }
.session-name { font-size: 13px; font-weight: 500; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.session-menu-btn { background: none; border: none; cursor: pointer; color: var(--text-muted); padding: 2px 6px; border-radius: 4px; font-size: 16px; line-height: 1; opacity: 0; transition: opacity 0.15s; }
.session-item:hover .session-menu-btn { opacity: 1; }
.session-menu-btn:hover { background: rgba(200,180,160,0.3); color: var(--text-primary); }
.sidebar-empty { padding: 20px; text-align: center; font-size: 13px; color: var(--text-muted); }
.sidebar-new-chat-btn { display: flex; align-items: center; gap: 8px; margin: 4px 8px 8px; padding: 8px 12px; background: rgba(198,123,92,0.1); border: 1px dashed rgba(198,123,92,0.3); border-radius: 10px; cursor: pointer; font-size: 13px; color: var(--accent-terracotta); transition: all 0.2s; }
.sidebar-new-chat-btn:hover { background: rgba(198,123,92,0.18); }
.sidebar-new-chat-btn svg { flex-shrink: 0; color: var(--accent-terracotta); }
.chat-main { flex: 1; display: flex; flex-direction: column; min-width: 0; min-height: 0; background: var(--bg-paper); }
.chat-header { display: flex; align-items: center; padding: 8px 16px; background: var(--bg-cream); border-bottom: 1px solid var(--border-light); flex-shrink: 0; }
.chat-header-left { display: flex; gap: 2px; margin-right: auto; }
.chat-header-center { flex: 1; display: flex; justify-content: center; }
.chat-scroll { flex: 1; overflow-y: auto; min-height: 0; }
.icon-btn { background: none; border: none; cursor: pointer; color: var(--text-primary); padding: 6px; border-radius: 8px; width: 34px; height: 34px; display: flex; align-items: center; justify-content: center; transition: all 0.15s; font-size: 18px; line-height: 1; }
.icon-btn svg { display: block; width: 20px; height: 20px; }
.icon-btn:hover { background: rgba(200,180,160,0.3); color: var(--accent-terracotta); }
.mode-tabs { display: flex; gap: 4px; }
.mode-tab { padding: 6px 16px; border-radius: 20px; border: none; cursor: pointer; font-size: 13px; font-weight: 500; background: transparent; color: var(--text-secondary); transition: all 0.2s; }
.mode-tab:hover { background: rgba(200,180,160,0.2); color: var(--text-primary); }
.mode-tab--active { background: white !important; color: var(--accent-terracotta) !important; box-shadow: 0 1px 4px rgba(44,24,16,0.08); }
.book-selector { padding: 8px 16px; border-bottom: 1px solid var(--border-light); background: var(--bg-cream); flex-shrink: 0; }
.book-selector-empty { font-size: 13px; color: var(--text-muted); padding: 4px 0; }
.book-chips { display: flex; flex-wrap: wrap; gap: 6px; }
.book-chip { padding: 4px 12px; border-radius: 14px; border: 1px solid var(--border-light); background: white; cursor: pointer; font-size: 12px; color: var(--text-secondary); transition: all 0.15s; white-space: nowrap; }
.book-chip:hover { border-color: var(--accent-terracotta); color: var(--accent-terracotta); }
.book-chip--active { background: rgba(198,123,92,0.12); border-color: var(--accent-terracotta); color: var(--accent-terracotta); font-weight: 500; }
.messages-area { padding: 24px 20px; display: flex; flex-direction: column; gap: 16px; max-width: 760px; margin: 0 auto; width: 100%; }
.chat-welcome { text-align: center; padding: 60px 20px; color: var(--text-muted); }
.welcome-icon { margin-bottom: 16px; display: flex; align-items: center; justify-content: center; }
.welcome-icon svg { width: 100px; height: 60px; }
.loading-indicator { text-align: center; padding: 40px 0; }
.loading-text { color: var(--text-muted); font-size: 13px; margin-top: 8px; }
.loading-spinner { width: 32px; height: 32px; margin: 0 auto 12px; border: 3px solid var(--border-light); border-top-color: var(--accent-terracotta); border-radius: 50%; animation: spin 0.7s linear infinite; }
@keyframes spin { to { transform: rotate(360deg); } }
.message { display: flex; gap: 12px; max-width: 85%; }
.message--user { align-self: flex-end; flex-direction: row-reverse; }
.message-content { font-size: 14px; line-height: 1.6; }
.message--user .message-content { order: -1; }
.message--user .message-text { background: var(--accent-terracotta); color: white; padding: 10px 16px; border-radius: 18px; }
.message--assistant .message-text { background: var(--bg-white); border: 1px solid var(--border-light); padding: 10px 16px; border-radius: 18px; box-shadow: 0 1px 3px rgba(44,24,16,0.04); }
html.dark .message--assistant .message-text { box-shadow: 0 1px 3px rgba(0,0,0,0.2); }
.message-text { overflow-wrap: break-word; word-break: break-word; }
.message-text.markdown-body { white-space: normal; }
.message-text.markdown-body p { margin: 0.25em 0; }
.message-text.markdown-body p:first-child { margin-top: 0; }
.message-text.markdown-body p:last-child { margin-bottom: 0; }
.thinking-content-static { font-size: 13px; color: #8B7D6B; line-height: 1.6; padding: 8px 12px; border-left: 3px solid #C9A96E; white-space: pre-wrap; margin-bottom: 8px; }
.thinking-details { margin-bottom: 8px; }
.thinking-summary { font-size: 12px; color: #8B7D6B; cursor: pointer; user-select: none; padding: 4px 0; font-weight: 500; list-style: none; display: flex; align-items: center; gap: 4px; }
.thinking-summary::-webkit-details-marker { display: none; }
.thinking-summary::before { content: '▶'; font-size: 10px; transition: transform 0.2s; display: inline-block; }
.thinking-details[open] .thinking-summary::before { content: '▼'; }
.input-area { padding: 16px 20px 20px; border-top: 1px solid var(--border-light); background: var(--bg-cream); flex-shrink: 0; }
.input-container { display: flex; align-items: center; gap: 8px; background: var(--bg-white); border-radius: 24px; padding: 4px 4px 4px 20px; border: 1.5px solid var(--border-light); transition: border-color 0.2s; max-width: 760px; margin: 0 auto; }
.input-container:focus-within { border-color: var(--accent-terracotta); box-shadow: 0 0 0 3px rgba(198,123,92,0.12); }
.input-container input { flex: 1; font-size: 14px; padding: 8px 0; color: var(--text-primary); }
.input-container input::placeholder { color: var(--text-muted); }
.send-btn, .stop-btn { width: 38px; height: 38px; border-radius: 50%; border: none; background: var(--border-light); color: var(--accent-rose); cursor: pointer; display: flex; align-items: center; justify-content: center; transition: all 0.2s; flex-shrink: 0; font-size: 16px; }
.send-btn:hover { background: var(--accent-terracotta); color: white; }
.stop-btn:hover { background: var(--accent-rose); color: white; }
.scroll-to-bottom-btn { position: fixed; bottom: 100px; right: 100px; z-index: 110; width: 40px; height: 40px; border-radius: 50%; border: 1px solid var(--border-light); background: var(--bg-white); box-shadow: var(--shadow-md); cursor: pointer; display: flex; align-items: center; justify-content: center; color: var(--accent-terracotta); font-size: 18px; transition: all 0.2s; }

.search-dialog { background: var(--bg-white); border-radius: var(--radius-lg); width: 400px; max-width: 90vw; max-height: 80vh; display: flex; flex-direction: column; box-shadow: var(--shadow-lg); overflow: hidden; }
.search-dialog-header { display: flex; align-items: center; justify-content: space-between; padding: 16px 20px; border-bottom: 1px solid var(--border-light); }
.search-dialog-header h3 { font-size: 15px; font-weight: 600; }
.search-close-btn { background: none; border: none; font-size: 18px; cursor: pointer; color: var(--text-muted); padding: 4px; }
.search-input { margin: 12px 16px; padding: 10px 14px; border-radius: var(--radius-sm); border: 1.5px solid var(--border-light); font-size: 14px; outline: none; width: calc(100% - 32px); box-sizing: border-box; }
.search-input:focus { border-color: var(--accent-terracotta); }
.search-results { flex: 1; overflow-y: auto; padding: 0 16px 12px; }
.search-result-item { display: flex; align-items: center; gap: 10px; padding: 10px; border-radius: var(--radius-sm); cursor: pointer; transition: background 0.15s; }
.search-result-item:hover { background: var(--bg-cream); }
.search-result-name { font-size: 14px; font-weight: 500; color: var(--text-primary); }
.search-result-preview { font-size: 12px; color: var(--text-muted); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; margin: 2px 0; }
.search-result-time { font-size: 11px; color: var(--text-muted); }
.search-empty { text-align: center; padding: 24px; color: var(--text-muted); font-size: 14px; }
.scroll-to-bottom-btn:hover { background: var(--bg-cream); transform: scale(1.05); }
.menu-popup { position: fixed; z-index: 100; background: var(--bg-white); border-radius: 12px; box-shadow: var(--shadow-lg); padding: 4px 0; min-width: 130px; border: 1px solid var(--border-light); }
.menu-popup-btn { display: block; width: 100%; padding: 8px 16px; text-align: left; background: none; border: none; cursor: pointer; font-size: 13px; color: var(--text-secondary); transition: background 0.1s; }
.menu-popup-btn:hover { background: var(--bg-cream); }
.menu-popup-btn--danger { color: var(--accent-rose); }
.stream-cursor::after { content: '▊'; animation: blink 0.8s step-end infinite; color: var(--accent-terracotta); }
.markdown-body.streaming::after { content: '▊'; animation: blink 0.8s step-end infinite; color: var(--accent-terracotta); margin-left: 2px; }
@keyframes blink { 50% { opacity: 0; } }
.stream-placeholder { color: var(--text-muted); font-size: 14px; }
.chat-welcome-text { margin-top: 8px; font-size: 14px; color: var(--text-muted); }

.markdown-body { line-height: 1.7; }
.markdown-body p { margin: 0.5em 0; }
.markdown-body p:first-child { margin-top: 0; }
.markdown-body p:last-child { margin-bottom: 0; }
.markdown-body ul, .markdown-body ol { padding-left: 1.5em; margin: 0.5em 0; }
.markdown-body li { margin: 0.25em 0; }
.markdown-body code { background: #f4f1ea; padding: 2px 6px; border-radius: 4px; font-size: 0.9em; }
.markdown-body pre { background: #f4f1ea; padding: 12px 16px; border-radius: 8px; overflow-x: auto; margin: 0.75em 0; }
.markdown-body pre code { background: none; padding: 0; border-radius: 0; }
.markdown-body table { border-collapse: collapse; margin: 0.75em 0; width: 100%; }
.markdown-body th, .markdown-body td { border: 1px solid #dcd0c0; padding: 6px 12px; text-align: left; }
.markdown-body th { background: #f4f1ea; font-weight: 600; }
.markdown-body blockquote { border-left: 3px solid #c9a96e; margin: 0.75em 0; padding: 4px 16px; color: #8b7d6b; }
.markdown-body h1, .markdown-body h2, .markdown-body h3, .markdown-body h4 { margin: 0.75em 0 0.5em; font-weight: 600; }
.markdown-body strong { font-weight: 600; }
.markdown-body a { color: var(--accent-terracotta); text-decoration: underline; }
.markdown-body hr { border: none; border-top: 1px solid #e8ddd0; margin: 1em 0; }

@media (max-width: 768px) {
  .chat-sidebar { position: fixed; left: 0; top: 0; bottom: 0; width: 280px; z-index: 160; box-shadow: var(--shadow-lg); padding-top: 70px; }
  .sidebar-overlay { z-index: 150; }
  .chat-sidebar--hidden { width: 0; box-shadow: none; }
  .chat-header { flex-wrap: wrap; gap: 4px; padding: 6px 8px; min-height: 44px; }
  .chat-header-left { display: flex !important; gap: 2px; align-items: center; }
  .icon-btn { width: 32px; height: 32px; font-size: 16px; color: var(--text-secondary); }
  .messages-area { padding: 8px; }
  .msg-wrap { padding: 6px 0; }
  .msg-bubble { max-width: 92%; padding: 8px 12px; font-size: 14px; }
  .input-area { padding: 6px 8px calc(8px + env(safe-area-inset-bottom)); gap: 4px; }
  .input-row { gap: 4px; }
  .input-area textarea { font-size: 14px; padding: 6px 10px; min-height: 36px; }
  .send-btn { width: 36px; height: 36px; }
  .mode-tabs { gap: 2px; }
  .mode-tab { padding: 4px 10px; font-size: 12px; }
  .sidebar-top-actions { padding: 8px; }
  .sidebar-new-chat-btn { margin: 0 8px 8px; padding: 8px; font-size: 13px; }
  .session-item { padding: 8px 10px; font-size: 13px; }
  .scroll-to-bottom-btn { right: 16px; bottom: 80px; z-index: 120; }
}

</style>
