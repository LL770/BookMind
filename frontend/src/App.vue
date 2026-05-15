<template>
<div class="app-root" :class="[themeClass, 'sidebar-state-' + sidebarState, { 'is-authenticated': userStore.isAuthenticated }]">
    <!-- ===== 桌面端左侧边栏 ===== -->
    <nav v-if="userStore.isAuthenticated" class="sidebar">
      <router-link to="/" class="sidebar-brand">
        <span class="brand-icon">📚</span>
        <span class="brand-text">Book<span class="brand-sub">Mind</span></span>
      </router-link>
      <div class="sidebar-links">
        <router-link to="/" class="sidebar-link" active-class="sidebar-link--active" :title="'书房'">
          <svg class="sidebar-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5"><path d="M3 9l9-7 9 7v11a2 2 0 01-2 2H5a2 2 0 01-2-2V9z"/><polyline points="9 22 9 12 15 12 15 22"/></svg>
          <span class="sidebar-label">书房</span>
        </router-link>
        <router-link to="/chat" class="sidebar-link" active-class="sidebar-link--active" :title="'对话'">
          <svg class="sidebar-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5"><path d="M21 15a2 2 0 01-2 2H7l-4 4V5a2 2 0 012-2h14a2 2 0 012 2z"/></svg>
          <span class="sidebar-label">对话</span>
        </router-link>
        <router-link to="/notes" class="sidebar-link" active-class="sidebar-link--active" :title="'笔记'">
          <svg class="sidebar-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5"><path d="M11 5H6a2 2 0 00-2 2v11a2 2 0 002 2h11a2 2 0 002-2v-5m-1.414-9.414a2 2 0 112.828 2.828L11.828 15H9v-2.828l8.586-8.586z"/></svg>
          <span class="sidebar-label">笔记</span>
        </router-link>
        <router-link to="/graph" class="sidebar-link" active-class="sidebar-link--active" :title="'图谱'">
          <svg class="sidebar-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5"><circle cx="12" cy="12" r="3"/><circle cx="19" cy="5" r="2"/><circle cx="5" cy="5" r="2"/><circle cx="19" cy="19" r="2"/><circle cx="5" cy="19" r="2"/><path d="M12 9l3-3M12 15l3 3M9 12l-3-3M15 12l3-3M9 12l-3 3"/></svg>
          <span class="sidebar-label">图谱</span>
        </router-link>
        <router-link to="/search" class="sidebar-link" active-class="sidebar-link--active" :title="'搜索'">
          <svg class="sidebar-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5"><circle cx="11" cy="11" r="8"/><path d="M21 21l-4.35-4.35"/></svg>
          <span class="sidebar-label">搜索</span>
        </router-link>
        <router-link to="/upload" class="sidebar-link" active-class="sidebar-link--active" :title="'上传'">
          <svg class="sidebar-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5"><path d="M21 15v4a2 2 0 01-2 2H5a2 2 0 01-2-2v-4M17 8l-5-5-5 5M12 3v12"/></svg>
          <span class="sidebar-label">上传</span>
        </router-link>
      </div>
      <div class="sidebar-footer">
        <router-link to="/settings" class="sidebar-link sidebar-link--slim" :title="'设置'">
          <svg class="sidebar-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5"><circle cx="12" cy="12" r="3"/><path d="M19.4 15a1.65 1.65 0 00.33 1.82l.06.06a2 2 0 01-2.83 2.83l-.06-.06a1.65 1.65 0 00-1.82-.33 1.65 1.65 0 00-1 1.51V21a2 2 0 01-4 0v-.09A1.65 1.65 0 009 19.4a1.65 1.65 0 00-1.82.33l-.06.06a2 2 0 01-2.83-2.83l.06-.06A1.65 1.65 0 004.68 15a1.65 1.65 0 00-1.51-1H3a2 2 0 010-4h.09A1.65 1.65 0 004.6 9a1.65 1.65 0 00-.33-1.82l-.06-.06a2 2 0 012.83-2.83l.06.06A1.65 1.65 0 009 4.68a1.65 1.65 0 001-1.51V3a2 2 0 014 0v.09a1.65 1.65 0 001 1.51 1.65 1.65 0 001.82-.33l.06-.06a2 2 0 012.83 2.83l-.06.06A1.65 1.65 0 0019.4 9a1.65 1.65 0 001.51 1H21a2 2 0 010 4h-.09a1.65 1.65 0 00-1.51 1z"/></svg>
          <span class="sidebar-label">设置</span>
        </router-link>
        <button @click="handleLogout" class="sidebar-link sidebar-link--slim" :title="'退出登录'">
          <svg class="sidebar-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5"><path d="M9 21H5a2 2 0 01-2-2V5a2 2 0 012-2h4M16 17l5-5-5-5M21 12H9"/></svg>
          <span class="sidebar-label">退出</span>
        </button>
        <button @click="toggleSidebar()" class="sidebar-collapse-btn" :title="sidebarState === 2 ? '展开导航栏' : '隐藏导航栏'">
          <span class="collapse-arrow">{{ sidebarState === 2 ? '>' : '<' }}</span>
          <span v-if="sidebarDefault === 0" class="sidebar-collapse-label">{{ sidebarState === 2 ? '展开' : '隐藏' }}</span>
        </button>
      </div>
    </nav>
    <!-- 侧边栏隐藏时的触发展开区域 -->
    <div v-if="userStore.isAuthenticated && sidebarState === 2" class="sidebar-reveal" @click="showSidebar" :title="'显示侧边栏'">
      <span style="font-size:14px">{{ sidebarDefault === 0 ? '❯' : '>' }}</span>
    </div>

    <!-- ===== 移动端底部导航栏 ===== -->
    <nav v-if="userStore.isAuthenticated" class="bottom-nav">
      <router-link to="/" class="bottom-link" active-class="bottom-link--active">
        <svg class="sidebar-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5"><path d="M3 9l9-7 9 7v11a2 2 0 01-2 2H5a2 2 0 01-2-2V9z"/><polyline points="9 22 9 12 15 12 15 22"/></svg>
        <span>书房</span>
      </router-link>
      <router-link to="/chat" class="bottom-link" active-class="bottom-link--active">
        <svg class="sidebar-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5"><path d="M21 15a2 2 0 01-2 2H7l-4 4V5a2 2 0 012-2h14a2 2 0 012 2z"/></svg>
        <span>对话</span>
      </router-link>
      <router-link to="/notes" class="bottom-link" active-class="bottom-link--active">
        <svg class="sidebar-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5"><path d="M11 5H6a2 2 0 00-2 2v11a2 2 0 002 2h11a2 2 0 002-2v-5m-1.414-9.414a2 2 0 112.828 2.828L11.828 15H9v-2.828l8.586-8.586z"/></svg>
        <span>笔记</span>
      </router-link>
      <router-link to="/search" class="bottom-link" active-class="bottom-link--active">
        <svg class="sidebar-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5"><circle cx="11" cy="11" r="8"/><path d="M21 21l-4.35-4.35"/></svg>
        <span>搜索</span>
      </router-link>
      <router-link to="/graph" class="bottom-link" active-class="bottom-link--active">
        <svg class="sidebar-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5"><circle cx="12" cy="12" r="3"/><circle cx="19" cy="5" r="2"/><circle cx="5" cy="5" r="2"/><circle cx="19" cy="19" r="2"/><circle cx="5" cy="19" r="2"/><path d="M12 9l3-3M12 15l3 3M9 12l-3-3M15 12l3-3M9 12l-3 3"/></svg>
        <span>图谱</span>
      </router-link>
      <router-link to="/settings" class="bottom-link" active-class="bottom-link--active">
        <svg class="sidebar-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5"><circle cx="12" cy="12" r="3"/><path d="M19.4 15a1.65 1.65 0 00.33 1.82l.06.06a2 2 0 01-2.83 2.83l-.06-.06a1.65 1.65 0 00-1.82-.33 1.65 1.65 0 00-1 1.51V21a2 2 0 01-4 0v-.09A1.65 1.65 0 009 19.4a1.65 1.65 0 00-1.82.33l-.06.06a2 2 0 01-2.83-2.83l.06-.06A1.65 1.65 0 004.68 15a1.65 1.65 0 00-1.51-1H3a2 2 0 010-4h.09A1.65 1.65 0 004.6 9a1.65 1.65 0 00-.33-1.82l-.06-.06a2 2 0 012.83-2.83l.06.06A1.65 1.65 0 009 4.68a1.65 1.65 0 001-1.51V3a2 2 0 014 0v.09a1.65 1.65 0 001 1.51 1.65 1.65 0 001.82-.33l.06-.06a2 2 0 012.83 2.83l-.06.06A1.65 1.65 0 0019.4 9a1.65 1.65 0 001.51 1H21a2 2 0 010 4h-.09a1.65 1.65 0 00-1.51 1z"/></svg>
        <span>设置</span>
      </router-link>
    </nav>

    <!-- 全局弹窗 -->
    <div v-if="modalState.show" class="modal-overlay" @click.self="handleModalBgClick">
      <div class="modal-card">
        <div class="modal-icon">{{ modalState.icon }}</div>
        <h3 class="modal-title">{{ modalState.title }}</h3>
        <p class="modal-message">{{ modalState.message }}</p>
        <div class="modal-actions">
          <button v-if="modalState.type === 'confirm'" @click="modalState.onCancel?.()" class="modal-btn modal-btn--secondary">{{ modalState.cancelText || '取消' }}</button>
          <button @click="modalState.onConfirm?.()" class="modal-btn modal-btn--primary">{{ modalState.confirmText || '确定' }}</button>
        </div>
      </div>
    </div>

    <!-- 主内容 -->
    <main class="main-content">
      <router-view v-slot="{ Component }">
        <transition name="page" mode="out-in">
          <keep-alive :include="['Chat']">
            <component :is="Component" />
          </keep-alive>
        </transition>
      </router-view>
    </main>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores'
import { modalState } from '@/utils/modal'

const router = useRouter()
const userStore = useUserStore()

const themeClass = ref('theme-warm')
const userAvatar = ref(localStorage.getItem('user_avatar') || '')

const userInitial = computed(() => (userStore.user?.username || 'U')[0].toUpperCase())

onMounted(() => {
  userStore.init()
  if (localStorage.getItem('dark_mode') === '1') document.documentElement.classList.add('dark')
  window.addEventListener('sidebar-default-changed', (e) => {
    sidebarDefault.value = e.detail
    sidebarState.value = e.detail
    localStorage.setItem('sidebar_state', String(e.detail))
  })
  window.addEventListener('sidebar-state-changed', (e) => {
    sidebarState.value = e.detail
  })
  // 移动端：触发浏览器地址栏自动隐藏
  if ('scrollRestoration' in history) history.scrollRestoration = 'manual'
  setTimeout(() => window.scrollTo(0, 1), 100)
})

const handleModalBgClick = () => {
  if (modalState.value.type === 'info') modalState.value.onConfirm?.()
}

const sidebarState = ref(parseInt(localStorage.getItem('sidebar_state') ?? '0'))
const sidebarDefault = ref(parseInt(localStorage.getItem('sidebar_default') ?? '0'))

if ((sidebarDefault.value === 0 && sidebarState.value === 1) || (sidebarDefault.value === 1 && sidebarState.value === 0)) {
  sidebarState.value = sidebarDefault.value
  localStorage.setItem('sidebar_state', String(sidebarState.value))
}

function toggleSidebar() {
  if (sidebarState.value === 2) {
    sidebarState.value = sidebarDefault.value
  } else {
    sidebarState.value = 2
  }
  localStorage.setItem('sidebar_state', String(sidebarState.value))
}

function showSidebar() {
  sidebarState.value = sidebarDefault.value
  localStorage.setItem('sidebar_state', String(sidebarState.value))
}

const handleLogout = async () => {
  await userStore.logout()
  router.push({ name: 'login' })
}
</script>

<style>
/* ========== 设计系统 ========== */
:root {
  --bg-cream: #F5F0E8;
  --bg-paper: #FAF6F0;
  --bg-white: #FFFFFF;
  --text-primary: #2C1810;
  --text-secondary: #6B5B4E;
  --text-muted: #9C8B7A;
  --text-on-accent: #FFFFFF;
  --accent-terracotta: #C67B5C;
  --accent-gold: #D4A853;
  --accent-rose: #B8655A;
  --accent-green: #5A8F6A;
  --border-light: #E8DDD0;
  --border-medium: #D4C8B8;
  --shadow-sm: 0 1px 3px rgba(44,24,16,0.06);
  --shadow-md: 0 4px 12px rgba(44,24,16,0.08);
  --shadow-lg: 0 8px 30px rgba(44,24,16,0.12);
  --shadow-warm: 0 4px 16px rgba(198,123,92,0.15);
  --radius-sm: 8px;
  --radius-md: 12px;
  --radius-lg: 16px;
  --font-heading: 'Georgia', 'Noto Serif SC', serif;
  --font-body: -apple-system, 'PingFang SC', 'Microsoft YaHei', sans-serif;
  --header-h: 60px;
  --sidebar-w: 90px;
  --bottomnav-h: 56px;
}

/* ========== 深色模式 ========== */
html.dark {
  --bg-cream: #1A1410;
  --bg-paper: #221C16;
  --bg-white: #2C241E;
  --text-primary: #EDE6DB;
  --text-secondary: #B8A898;
  --text-muted: #8B7D6B;
  --text-on-accent: #FFFFFF;
  --border-light: #3C342E;
  --border-medium: #4C443E;
  --shadow-sm: 0 1px 3px rgba(0,0,0,0.3);
  --shadow-md: 0 4px 12px rgba(0,0,0,0.4);
  --shadow-lg: 0 8px 30px rgba(0,0,0,0.5);
  --shadow-warm: 0 4px 16px rgba(0,0,0,0.4);
}
html.dark body { background: var(--bg-cream); }
html.dark img { opacity: 0.9; }

* { margin: 0; padding: 0; box-sizing: border-box; }
html { height: -webkit-fill-available; }

body {
  font-family: var(--font-body);
  color: var(--text-primary);
  background: var(--bg-cream);
  line-height: 1.6;
  -webkit-font-smoothing: antialiased;
  overscroll-behavior: none;
}
@media (max-width: 768px) {
  body { overflow-x: hidden; -webkit-overflow-scrolling: touch; }
  html { height: -webkit-fill-available; }
  body { min-height: 100vh; min-height: -webkit-fill-available; }
}

.app-root { min-height: 100vh; min-height: 100dvh; background: var(--bg-cream); position: relative; }
html.dark .app-root { background: var(--bg-cream); }

/* ========== 两栏 Grid 布局 — 侧边栏与主内容区无缝贴合 ========== */
.app-root { display: block; }
.is-authenticated { display: grid; grid-template-columns: var(--sidebar-w) 1fr; column-gap: 0; }

/* ========== 桌面端侧边栏 ========== */
.sidebar {
  background: var(--bg-paper);
  border-right: 1px solid var(--border-light);
  display: flex; flex-direction: column;
  height: 100vh; position: sticky; top: 0;
}
.sidebar-brand {
  display: flex; align-items: center; gap: 6px; padding: 10px 16px 10px 9px;
  text-decoration: none; border-bottom: 1px solid var(--border-light);
  flex-shrink: 0;
}
.sidebar-brand .brand-icon { font-size: 16px; flex-shrink: 0; }
.sidebar-brand .brand-text {
  font-family: var(--font-heading); font-size: 13px; font-weight: 700;
  color: var(--text-primary); letter-spacing: 0.3px; line-height: 1.2;
}
.sidebar-brand .brand-sub { display: block; font-weight: 700; }
.sidebar-links { flex: 1; overflow-y: auto; padding: 6px 0; display: flex; flex-direction: column; gap: 2px; }
.sidebar-footer {
  border-top: 1px solid var(--border-light); flex-shrink: 0;
  display: flex; flex-direction: column; gap: 2px; padding: 6px 0;
  position: relative;
}
.sidebar-link--slim {
  display: flex; align-items: center; gap: 10px; padding: 7px 12px;
  border-radius: var(--radius-sm); text-decoration: none;
  font-size: 12px; font-weight: 500; color: var(--text-secondary);
  transition: all 0.2s; cursor: pointer; background: none; border: none; width: 100%; text-align: left;
}
.sidebar-link--slim:hover { background: rgba(200,180,160,0.25); color: var(--text-primary); }
.sidebar-link--slim:last-of-type:hover { color: var(--accent-rose); }
.sidebar-collapse-btn {
  background: none; border: none; cursor: pointer;
  display: flex; align-items: center; gap: 10px; padding: 7px 12px;
  border-radius: var(--radius-sm); font-size: 12px; font-weight: 500;
  color: var(--text-muted); transition: all 0.2s;
  font-family: var(--font-body); width: 100%; text-align: left;
}
.sidebar-collapse-btn:hover { color: var(--text-primary); background: rgba(200,180,160,0.25); }
.collapse-arrow { font-size: 16px; line-height: 1; width: 20px; text-align: center; flex-shrink: 0; }
.sidebar-collapse-label { white-space: nowrap; }

/* 侧边栏状态：0=展开 1=仅图标 2=隐藏 */
.sidebar-state-1 { grid-template-columns: 44px 1fr; }
.sidebar-state-1 .sidebar-brand .brand-text,
.sidebar-state-1 .sidebar-label { display: none; }
.sidebar-state-1 .sidebar-brand { justify-content: center; padding: 12px 0; }
.sidebar-state-1 .sidebar-link { justify-content: center; padding: 6px 0; }
.sidebar-state-1 .sidebar-footer { align-items: center; }
.sidebar-state-1 .sidebar-link--slim .sidebar-label { display: none; }
.sidebar-state-1 .sidebar-link--slim { justify-content: center; padding: 6px 0; }
.sidebar-state-1 .sidebar-collapse-btn { justify-content: center; padding: 6px 0; }
.sidebar-state-1 .sidebar-collapse-label { display: none; }
.sidebar-state-1 .sidebar-collapse-btn svg { transform: none; }
.sidebar-state-1 .collapse-arrow { font-size: 16px; }

.sidebar-state-2 { grid-template-columns: 1fr; }
.sidebar-state-2 .sidebar { display: none; }
/* 侧边栏隐藏时左边缘悬浮按钮 */
.sidebar-state-2 .sidebar-reveal {
  position: fixed; left: 0; top: 50%; z-index: 99; transform: translateY(-50%);
  width: 28px; height: 48px; border-radius: 0 8px 8px 0;
  background: var(--bg-paper); border: 1px solid var(--border-light); border-left: none;
  cursor: pointer; display: flex; align-items: center; justify-content: center;
  color: var(--text-muted); transition: all 0.2s; opacity: 0.6;
  box-shadow: var(--shadow-sm);
}
.sidebar-state-2 .sidebar-reveal:hover { opacity: 1; color: var(--accent-terracotta); }
.sidebar-link {
  display: flex; align-items: center; gap: 10px; padding: 10px 12px;
  border-radius: var(--radius-sm); text-decoration: none;
  font-size: 13px; font-weight: 500; color: var(--text-secondary);
  transition: all 0.2s; cursor: pointer;
  white-space: nowrap; overflow: hidden;
}
.sidebar-link:hover { background: rgba(200,180,160,0.25); color: var(--text-primary); }
.sidebar-link--active { background: rgba(198,123,92,0.15); color: var(--accent-terracotta); }
.sidebar-icon { width: 20px; height: 20px; flex-shrink: 0; }
.sidebar-label { opacity: 1; transition: opacity 0.2s; }

/* 阅读/全屏时隐藏侧边栏，并让内容占满宽度 */
html.reader-active .is-authenticated { grid-template-columns: 1fr; }
html.reader-active .sidebar { display: none; }
:fullscreen .sidebar { display: none; }

/* ========== 移动端底部导航栏 ========== */
.bottom-nav { display: none; } /* 桌面端隐藏 */

/* ========== 主内容 ========== */
.main-content { min-height: 100vh; min-height: 100dvh; display: flex; flex-direction: column; }

/* ========== 响应式 ========== */
@media (max-width: 768px) {
  .is-authenticated { grid-template-columns: 1fr; }
  .sidebar { display: none; }
  .bottom-nav {
    display: flex; position: fixed; bottom: 0; left: 0; right: 0;
    height: var(--bottomnav-h); z-index: 100;
    background: var(--bg-paper);
    backdrop-filter: blur(16px); -webkit-backdrop-filter: blur(16px);
    border-top: 1px solid var(--border-light);
    align-items: center; justify-content: space-around;
  }
  .bottom-link {
    display: flex; flex-direction: column; align-items: center; gap: 2px;
    text-decoration: none; color: var(--text-muted); font-size: 10px;
    padding: 4px 8px; border-radius: var(--radius-sm); transition: all 0.2s;
    min-width: 48px;
  }
  .bottom-link .sidebar-icon { width: 22px; height: 22px; }
  .bottom-link--active { color: var(--accent-terracotta); }
  .bottom-link:hover { color: var(--text-secondary); }
  .is-authenticated .main-content { margin-left: 0; }
  .main-content { padding-bottom: var(--bottomnav-h); }
  .modal-card { padding: 24px 20px; margin: 0 12px; max-width: 100%; }
  /* 移动端页面高度修正 - 避免底部导航遮挡 */
  .home-page, .notes-page, .settings-page, .upload-page, .kg-page {
    min-height: auto !important;
  }
}

/* ========== 弹窗 ========== */
.modal-overlay {
  position: fixed; inset: 0; z-index: 200;
  background: rgba(44,24,16,0.4);
  display: flex; align-items: center; justify-content: center;
  padding: 20px;
}
.modal-card {
  background: var(--bg-white); border-radius: var(--radius-lg);
  padding: 32px; max-width: 360px; width: 100%;
  box-shadow: var(--shadow-lg); text-align: center;
}
.modal-icon { font-size: 40px; margin-bottom: 12px; line-height: 1; }
.modal-title { font-family: var(--font-heading); font-size: 18px; font-weight: 700; margin-bottom: 8px; color: var(--text-primary); }
.modal-message { font-size: 14px; color: var(--text-secondary); margin-bottom: 24px; line-height: 1.6; }
.modal-actions { display: flex; gap: 10px; justify-content: center; }
.modal-btn {
  padding: 8px 24px; border-radius: var(--radius-sm); font-size: 14px; font-weight: 500;
  border: none; cursor: pointer; transition: all 0.2s;
}
.modal-btn--primary { background: var(--accent-terracotta); color: white; }
.modal-btn--primary:hover { background: #B06A4E; }
.modal-btn--secondary { background: #F0E8DC; color: var(--text-secondary); }
.modal-btn--secondary:hover { background: #E8DDD0; }

/* ========== 路由过渡 ========== */
.page-enter-active, .page-leave-active { transition: opacity 0.12s ease, transform 0.12s ease; }
.page-enter-from { opacity: 0; transform: translateY(8px); }
.page-leave-to { opacity: 0; transform: translateY(-8px); }

/* ========== 通用组件 ========== */
.container-main { max-width: 1200px; margin: 0 auto; padding: 24px 20px; }
.is-authenticated .container-main { max-width: 1200px; margin: 0 auto; }
@media (max-width: 768px) { .container-main { padding: 16px 12px; } }


.card {
  background: var(--bg-white); border-radius: var(--radius-md);
  border: 1px solid var(--border-light);
  box-shadow: var(--shadow-sm);
  transition: transform 0.2s, box-shadow 0.25s;
}
.card:hover { box-shadow: var(--shadow-md); transform: translateY(-1px); }

input, select, textarea, .input {
  font-family: var(--font-body); font-size: 14px;
  padding: 12px 20px; border-radius: 24px;
  border: 1.5px solid #DCD0C0;
  background: var(--bg-cream); color: var(--text-primary);
  outline: none; transition: all 0.25s ease;
  width: 100%;
  box-shadow: inset 0 1px 3px rgba(44,24,16,0.04);
}
input:focus, select:focus, textarea:focus, .input:focus {
  border-color: var(--accent-terracotta);
  background: #FFFCF8;
  box-shadow: 0 0 0 3px rgba(198,123,92,0.12);
}
input::placeholder, select::placeholder, textarea::placeholder, .input::placeholder {
  color: #B8A898;
  font-size: 13px;
}

/* 表单输入组（带图标等） */
.input-group {
  display: flex; align-items: center;
  background: var(--bg-cream); border: 1.5px solid #DCD0C0;
  border-radius: 24px; transition: all 0.25s;
}
.input-group:focus-within {
  border-color: var(--accent-terracotta);
  background: #FFFCF8;
  box-shadow: 0 0 0 3px rgba(198,123,92,0.12);
}
.input-group input {
  border: none; background: transparent; box-shadow: none;
  padding: 11px 16px;
}
.input-group input:focus { box-shadow: none; background: transparent; }
.input-group-icon {
  padding-left: 14px; color: var(--text-muted);
  display: flex; align-items: center;
}
.input-error { border-color: var(--accent-rose) !important; background: #FFF8F6 !important; }
.input-error:focus { box-shadow: 0 0 0 3px rgba(184,101,90,0.12) !important; }

.btn {
  display: inline-flex; align-items: center; justify-content: center; gap: 6px;
  padding: 8px 20px; border-radius: var(--radius-sm); font-size: 14px; font-weight: 500;
  border: none; cursor: pointer; transition: all 0.2s; text-decoration: none; line-height: 1.4;
  user-select: none;
}
.btn:active { transform: scale(0.97); }
.btn--primary { background: var(--accent-terracotta); color: white; }
.btn--primary:hover { background: #B06A4E; transform: translateY(-1px); box-shadow: var(--shadow-sm); }
.btn--secondary { background: #F0E8DC; color: var(--text-secondary); }
.btn--secondary:hover { background: #E8DDD0; }
.btn--ghost { background: transparent; color: var(--text-secondary); }
.btn--ghost:hover { background: rgba(200,180,160,0.2); }
.btn--gold { background: var(--accent-gold); color: white; }
.btn--gold:hover { background: #C4963E; transform: translateY(-1px); }
.btn--danger { background: var(--accent-rose); color: white; }
.btn--danger:hover { background: #A8554A; }
.btn:disabled { opacity: 0.5; cursor: not-allowed; transform: none !important; }
.btn-sm { padding: 5px 12px; font-size: 12px; }
/* 兼容旧类名 */
.btn-primary { composes: btn btn--primary; background: var(--accent-terracotta); color: white; padding: 8px 20px; border-radius: var(--radius-sm); font-size: 14px; font-weight: 500; border: none; cursor: pointer; display: inline-flex; align-items: center; justify-content: center; gap: 6px; text-decoration: none; line-height: 1.4; transition: all 0.2s; }
.btn-primary:hover { background: #B06A4E; transform: translateY(-1px); box-shadow: var(--shadow-sm); }
.btn-secondary { composes: btn; background: #F0E8DC; color: var(--text-secondary); padding: 8px 20px; border-radius: var(--radius-sm); font-size: 14px; font-weight: 500; border: none; cursor: pointer; display: inline-flex; align-items: center; justify-content: center; gap: 6px; text-decoration: none; line-height: 1.4; transition: all 0.2s; }
.btn-secondary:hover { background: #E8DDD0; }
.btn-danger { composes: btn; background: var(--accent-rose); color: white; padding: 8px 20px; border-radius: var(--radius-sm); font-size: 14px; font-weight: 500; border: none; cursor: pointer; display: inline-flex; align-items: center; justify-content: center; gap: 6px; text-decoration: none; line-height: 1.4; transition: all 0.2s; }
.btn-danger:hover { background: #A8554A; }
.input-error { border-color: var(--accent-rose) !important; }

/* ========== Toast 通知 ========== */
.toast {
  position: fixed; bottom: 24px; right: 24px; z-index: 300;
  padding: 12px 20px; border-radius: var(--radius-sm);
  font-size: 14px; color: white; box-shadow: var(--shadow-lg);
  animation: toastIn 0.3s ease;
}
.toast--success { background: #5A8F6A; }
.toast--error { background: var(--accent-rose); }

@keyframes toastIn { from { opacity: 0; transform: translateY(12px); } to { opacity: 1; transform: translateY(0); } }

/* ========== 骨架屏 ========== */
.skeleton { background: linear-gradient(90deg, #F0E8DC 25%, #F8F4EC 50%, #F0E8DC 75%); background-size: 200% 100%; animation: shimmer 1.5s ease infinite; border-radius: 4px; }
@keyframes shimmer { 0% { background-position: 200% 0; } 100% { background-position: -200% 0; } }
</style>
