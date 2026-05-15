<template>
  <div class="settings-page">
    <div class="container-main" style="max-width:600px">
      <div class="settings-header">
        <h1 class="page-title">⚙️ 设置</h1>
        <p class="page-subtitle">个性化你的阅读空间</p>
      </div>

      <!-- 头像 -->
      <div class="card settings-card">
        <h2 class="card-title">个人资料</h2>
        <div class="avatar-section">
          <div class="avatar-preview" @click="$refs.avatarInput.click()">
            <img v-if="avatarPreview" :src="avatarPreview" class="avatar-img" />
            <div v-else class="avatar-placeholder">{{ userInitial }}</div>
            <div class="avatar-overlay">更换</div>
          </div>
          <input ref="avatarInput" type="file" accept="image/*" class="hidden" @change="handleAvatar" />
          <div class="avatar-info">
            <p class="avatar-name">{{ userStore.user?.username }}</p>
            <p class="avatar-hint">点击头像更换 · 建议 200x200</p>
          </div>
        </div>
        <div v-if="avatarFile" class="avatar-actions">
          <button @click="saveAvatar" class="btn btn--primary btn-sm">保存头像</button>
          <button @click="cancelAvatar" class="btn btn--ghost btn-sm">取消</button>
        </div>
      </div>

      <!-- 显示设置 -->
      <div class="card settings-card">
        <h2 class="card-title">显示设置</h2>
        <div class="setting-row">
          <span class="setting-label">深色模式</span>
          <label class="toggle">
            <input type="checkbox" v-model="darkMode" @change="toggleDarkMode" />
            <span class="toggle-slider"></span>
          </label>
        </div>
        <div class="setting-row">
          <span class="setting-label">侧边栏</span>
          <div class="setting-options">
            <button @click="setSidebar(0)" class="option-btn" :class="{ 'option-btn--active': sidebarState === 0 }">展开</button>
            <button @click="setSidebar(1)" class="option-btn" :class="{ 'option-btn--active': sidebarState === 1 }">图标</button>
          </div>
        </div>
      </div>


      <!-- 账号信息 -->
      <div class="card settings-card">
        <h2 class="card-title">账号信息</h2>
        <div class="info-row">
          <span class="info-label">用户名</span>
          <div class="info-value">
            <input v-model="form.username" class="input" :disabled="!editField" />
            <button v-if="!editField" @click="editField = 'username'" class="btn btn--ghost btn-sm">编辑</button>
            <template v-else>
              <button @click="saveUsername" class="btn btn--primary btn-sm">保存</button>
              <button @click="editField = null" class="btn btn--ghost btn-sm">取消</button>
            </template>
          </div>
        </div>
        <div class="info-row">
          <span class="info-label">手机号</span>
          <div class="info-value">
            <input v-model="form.phone" class="input" :disabled="!editPhone" />
            <button v-if="!editPhone" @click="editPhone = true" class="btn btn--ghost btn-sm">编辑</button>
            <template v-else>
              <button @click="savePhone" class="btn btn--primary btn-sm">保存</button>
              <button @click="editPhone = null" class="btn btn--ghost btn-sm">取消</button>
            </template>
          </div>
        </div>
      </div>


      <!-- 修改密码 -->
      <div class="card settings-card">
        <h2 class="card-title">修改密码</h2>
        <div class="pw-row">
          <label class="pw-label">旧密码</label>
          <input v-model="pw.old" type="password" class="input" placeholder="输入旧密码" />
        </div>
        <div class="pw-row">
          <label class="pw-label">新密码</label>
          <input v-model="pw.newVal" type="password" class="input" placeholder="至少6位" />
        </div>
        <div class="pw-row">
          <label class="pw-label">确认密码</label>
          <input v-model="pw.confirm" type="password" class="input" placeholder="再次输入新密码" />
        </div>
        <p v-if="pwError" class="form-error">{{ pwError }}</p>
        <button @click="changePw" class="btn btn--primary" style="margin-top:12px;width:100%">修改密码</button>
      </div>

      <!-- 退出登录 (桌面端侧边栏已有, 仅移动端显示) -->
      <div class="card settings-card logout-mobile">
        <button @click="handleLogout" class="btn btn--secondary btn-sm" style="width:100%;justify-content:center;padding:12px;font-size:14px">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" style="margin-right:6px"><path d="M9 21H5a2 2 0 01-2-2V5a2 2 0 012-2h4M16 17l5-5-5-5M21 12H9"/></svg>
          退出登录
        </button>
      </div>

      <!-- 危险操作 -->
      <div class="card settings-card" style="border-color:rgba(200,80,60,0.3)">
        <h2 class="card-title" style="color:var(--accent-rose)">⚠️ 危险区域</h2>
        <p class="danger-text">注销账号后所有数据将被永久删除，无法恢复。</p>
        <button @click="confirmDelete" class="btn btn--danger btn-sm">注销账号</button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores'
import { authAPI } from '@/api'
import { showAlert, showConfirm } from '@/utils/modal'

const router = useRouter()
const userStore = useUserStore()

const avatarFile = ref(null)
const avatarPreview = ref(localStorage.getItem('user_avatar') || '')
const editField = ref(null)
const editPhone = ref(false)
const form = ref({ username: '', phone: '' })
const pw = ref({ old: '', newVal: '', confirm: '' })
const pwError = ref('')
const darkMode = ref(document.documentElement.classList.contains('dark'))
const sidebarState = ref(parseInt(localStorage.getItem('sidebar_state') ?? '0'))

function toggleDarkMode() {
  if (darkMode.value) {
    document.documentElement.classList.add('dark')
    localStorage.setItem('dark_mode', '1')
  } else {
    document.documentElement.classList.remove('dark')
    localStorage.setItem('dark_mode', '0')
  }
}

function setSidebar(state) {
  sidebarState.value = state
  localStorage.setItem('sidebar_state', String(state))
  window.dispatchEvent(new CustomEvent('sidebar-state-changed', { detail: state }))
}

const userInitial = computed(() => (userStore.user?.username || 'U')[0].toUpperCase())

onMounted(() => {
  if (userStore.user) {
    form.value.username = userStore.user.username || ''
    form.value.phone = userStore.user.phone || ''
  }
})

function handleAvatar(e) {
  const f = e.target.files[0]
  if (!f) return
  avatarFile.value = f
  avatarPreview.value = URL.createObjectURL(f)
}

function cancelAvatar() {
  avatarFile.value = null
  avatarPreview.value = localStorage.getItem('user_avatar') || ''
}

function saveAvatar() {
  if (!avatarFile.value) return
  const reader = new FileReader()
  reader.onload = (e) => {
    localStorage.setItem('user_avatar', e.target.result)
    avatarPreview.value = e.target.result
    avatarFile.value = null
    showAlert('成功', '头像已更新')
    window.dispatchEvent(new CustomEvent('avatar-updated'))
  }
  reader.readAsDataURL(avatarFile.value)
}

async function saveUsername() {
  try {
    await authAPI.updateUsername(form.value.username)
    userStore.user.username = form.value.username
    editField.value = null
  } catch (e) { showAlert('失败', e.message, '❌') }
}

async function savePhone() {
  try {
    await authAPI.bindPhone(form.value.phone)
    userStore.user.phone = form.value.phone
    editPhone.value = false
  } catch (e) { showAlert('失败', e.message, '❌') }
}

async function changePw() {
  pwError.value = ''
  if (!pw.value.old || !pw.value.newVal) { pwError.value = '请填写完整'; return }
  if (pw.value.newVal.length < 6) { pwError.value = '新密码至少6位'; return }
  if (pw.value.newVal !== pw.value.confirm) { pwError.value = '两次密码不一致'; return }
  try {
    await authAPI.updatePassword(pw.value.old, pw.value.newVal)
    showAlert('成功', '密码已更新')
    pw.value = { old: '', newVal: '', confirm: '' }
  } catch (e) { pwError.value = e.message }
}

async function confirmDelete() {
  const ok1 = await showConfirm('注销账号', '确定要注销吗？此操作不可恢复！')
  if (!ok1) return
  const ok2 = await showConfirm('再次确认', '所有数据将被永久删除！')
  if (!ok2) return
  try {
    await authAPI.deleteAccount()
    await userStore.logout()
    window.location.href = '/login'
  } catch (e) { showAlert('失败', e.message, '❌') }
}

async function handleLogout() {
  const ok = await showConfirm('退出登录', '确定要退出登录吗？')
  if (!ok) return
  await userStore.logout()
  router.push({ name: 'login' })
}
</script>

<style scoped>
.settings-page { min-height: 100vh; padding-top: 20px; }
.settings-header { text-align: center; margin-bottom: 28px; }
.page-title { font-family: var(--font-heading); font-size: 26px; font-weight: 700; color: var(--text-primary); }
.page-subtitle { font-size: 14px; color: var(--text-muted); margin-top: 4px; }

.settings-card { padding: 24px; margin-bottom: 16px; }
.card-title { font-family: var(--font-heading); font-size: 16px; font-weight: 700; color: var(--text-primary); margin-bottom: 16px; }

/* 头像 */
.avatar-section { display: flex; align-items: center; gap: 16px; }
.avatar-preview {
  width: 72px; height: 72px; border-radius: 50%; overflow: hidden; cursor: pointer;
  position: relative; flex-shrink: 0;
  box-shadow: var(--shadow-sm);
}
.avatar-img { width: 100%; height: 100%; object-fit: cover; }
.avatar-placeholder {
  width: 100%; height: 100%; background: var(--accent-terracotta); color: white;
  display: flex; align-items: center; justify-content: center;
  font-size: 28px; font-weight: 600;
}
.avatar-overlay {
  position: absolute; inset: 0; background: rgba(44,24,16,0.5); color: white;
  display: flex; align-items: center; justify-content: center;
  font-size: 12px; opacity: 0; transition: opacity 0.2s;
}
.avatar-preview:hover .avatar-overlay { opacity: 1; }
.avatar-info { flex: 1; }
.avatar-name { font-size: 16px; font-weight: 600; color: var(--text-primary); }
.avatar-hint { font-size: 12px; color: var(--text-muted); margin-top: 2px; }
.avatar-actions { display: flex; gap: 8px; margin-top: 12px; padding-left: 88px; }
.hidden { display: none; }

/* 信息行 */
.info-row { display: flex; align-items: center; justify-content: space-between; padding: 10px 0; border-bottom: 1px solid var(--border-light); }
.info-row:last-child { border-bottom: none; }
.info-label { font-size: 14px; color: var(--text-primary); font-weight: 500; }
.info-value { display: flex; align-items: center; gap: 8px; }
.info-value input { flex:1; min-width:160px; }

/* 密码 */
.pw-row { display: flex; align-items: center; gap: 12px; padding: 6px 0; }
.pw-row + .pw-row { border-top: 1px solid var(--border-light); }
.pw-label { font-size: 14px; color: var(--text-primary); min-width: 72px; flex-shrink: 0; }
.pw-row .input { flex: 1; border-radius: var(--radius-md); }
.form-error { font-size: 12px; color: var(--accent-rose); margin-top: 4px; }
.danger-text { font-size: 13px; color: var(--text-muted); margin-bottom: 12px; }

/* 设置行（置顶等） */
.setting-row { display: flex; align-items: center; justify-content: space-between; padding: 12px 0; }
.setting-row + .setting-row { border-top: 1px solid var(--border-light); }
.setting-info { display: flex; flex-direction: column; gap: 2px; }
.setting-label { font-size: 14px; font-weight: 500; color: var(--text-primary); }
.setting-desc { font-size: 12px; color: var(--text-muted); }
.setting-options { display: flex; gap: 6px; }
.option-btn {
  padding: 4px 14px; border-radius: 16px; border: 1px solid var(--border-medium);
  font-size: 13px; background: transparent; color: var(--text-secondary); cursor: pointer; transition: all 0.2s;
}
.option-btn:hover { border-color: var(--accent-terracotta); color: var(--accent-terracotta); }
.option-btn--active { background: var(--accent-terracotta); color: #fff; border-color: var(--accent-terracotta); }
/* 切换开关 */
.toggle { position: relative; cursor: pointer; }
.toggle input { display: none; }
.toggle-slider {
  width: 44px; height: 24px; display: block;
  background: #D4C8B8; border-radius: 12px; transition: 0.2s;
  position: relative;
}
.toggle-slider::after {
  content: ''; width: 20px; height: 20px; border-radius: 50%;
  background: white; position: absolute; top: 2px; left: 2px;
  transition: 0.2s; box-shadow: 0 1px 3px rgba(0,0,0,0.15);
}
.toggle input:checked + .toggle-slider { background: var(--accent-terracotta); }
.toggle input:checked + .toggle-slider::after { left: 22px; }

.logout-mobile { display: none; }
@media (max-width: 768px) {
  .logout-mobile { display: block; }
  .settings-card { padding: 16px !important; }
  .avatar-section { flex-direction: column; text-align: center; }
  .settings-header h1 { font-size: 20px; }
}
</style>
