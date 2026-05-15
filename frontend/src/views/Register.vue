<template>
  <div class="min-h-screen flex items-center justify-center p-3 overflow-y-auto" style="background: linear-gradient(135deg, #FDF8F0 0%, #F5E6D3 50%, #EDD9C4 100%);">
    <div class="w-full max-w-sm">
      <div class="card p-5" style="background:#FFFCF8;border-radius:var(--radius-lg);border-color:#E8DDD0">
        <div class="text-center mb-3">
          <span class="text-2xl mb-1 block">📝</span>
          <h1 class="text-lg font-bold" style="font-family:var(--font-heading);color:var(--text-primary)">
            注册
          </h1>
        </div>

        <form @submit.prevent="handleRegister" class="space-y-3">
          <div>
            <label class="block text-xs font-medium mb-1" style="color:var(--text-secondary)">
              手机号 <span class="text-red-500">*</span>
            </label>
            <input
              v-model="formData.phone"
              type="tel"
              placeholder="13800138000"
              class="input"
              :class="{ 'input-error': errors.phone }"
              @blur="validatePhone"
              autocomplete="tel"
            />
            <p v-if="errors.phone" class="mt-1 text-sm" style="color:var(--accent-rose)">{{ errors.phone }}</p>
            <p v-else-if="phoneValidated" class="mt-1 text-sm" style="color:#5A8F6A">手机号格式正确</p>
          </div>

          <!-- 验证码 -->
          <div>
            <label class="block text-sm font-medium mb-1.5" style="color:var(--text-secondary)">验证码 <span class="text-red-500">*</span></label>
            <div class="flex gap-2">
              <input v-model="formData.code" type="text" placeholder="6位验证码" maxlength="6" class="input flex-1" :class="{ 'input-error': errors.code }" />
              <button @click.prevent="sendCode" :disabled="codeSending || codeCountdown > 0" class="btn" style="background:var(--accent-terracotta);color:white;white-space:nowrap;padding:8px 14px;flex-shrink:0;border-radius:24px;">
                {{ codeCountdown > 0 ? codeCountdown + 's' : '获取验证码(模拟)' }}
              </button>
            </div>
            <p v-if="errors.code" class="mt-1 text-sm" style="color:var(--accent-rose)">{{ errors.code }}</p>
          </div>

          <div>
            <label class="block text-sm font-medium mb-1.5" style="color:var(--text-secondary)">
              用户名 <span class="text-red-500">*</span>
            </label>
            <div class="relative">
              <input
                v-model="formData.username"
                type="text"
                placeholder="请输入用户名（3-20位）"
                class="input pr-20"
                :class="{ 'input-error': errors.username }"
                @blur="checkUsername"
                @input="debounceCheckUsername"
                autocomplete="username"
              />
              <span v-if="usernameChecking" class="absolute right-3 top-1/2 -translate-y-1/2">
                <svg class="w-4 h-4 animate-spin text-slate-400" fill="none" viewBox="0 0 24 24">
                  <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
                  <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z"/>
                </svg>
              </span>
              <span v-else-if="usernameAvailable === true" class="absolute right-3 top-1/2 -translate-y-1/2 text-green-500">✓</span>
              <span v-else-if="usernameAvailable === false" class="absolute right-3 top-1/2 -translate-y-1/2 text-red-500">✗</span>
            </div>
            <p v-if="errors.username" class="mt-1 text-sm" style="color:var(--accent-rose)">{{ errors.username }}</p>
            <p v-else-if="usernameAvailable" class="mt-1 text-sm" style="color:#5A8F6A">用户名可用</p>
          </div>

          <div>
            <label class="block text-sm font-medium mb-1.5" style="color:var(--text-secondary)">
              密码 <span class="text-red-500">*</span>
            </label>
            <input
              v-model="formData.password"
              type="password"
              placeholder="至少 6 位"
              class="input"
              :class="{ 'input-error': errors.password }"
              autocomplete="new-password"
            />
            <p v-if="errors.password" class="mt-1 text-sm" style="color:var(--accent-rose)">{{ errors.password }}</p>
          </div>

          <div>
            <label class="block text-sm font-medium mb-1.5" style="color:var(--text-secondary)">
              确认密码 <span class="text-red-500">*</span>
            </label>
            <input
              v-model="formData.confirmPassword"
              type="password"
              placeholder="再次输入密码"
              class="input"
              :class="{ 'input-error': errors.confirmPassword }"
              autocomplete="new-password"
            />
            <p v-if="errors.confirmPassword" class="mt-1 text-sm" style="color:var(--accent-rose)">{{ errors.confirmPassword }}</p>
            <p v-else-if="formData.password && formData.confirmPassword && formData.password === formData.confirmPassword" class="mt-1 text-sm" style="color:#5A8F6A">
              两次密码一致
            </p>
          </div>

          <!-- 表单级错误提示 -->
          <div v-if="errors.form" class="p-3 rounded-lg" style="background:rgba(184,101,90,0.08);color:var(--accent-rose);font-size:13px">
            {{ errors.form }}
          </div>

          <button
            type="submit"
            :disabled="loading"
            class="w-full btn btn--primary" style="padding:12px 20px;font-size:15px;border-radius:var(--radius-sm);font-weight:600"
            :class="{ 'opacity-50 cursor-not-allowed': loading }"
          >
            <span v-if="loading" class="flex items-center justify-center gap-2">
              <svg class="w-5 h-5 animate-spin" fill="none" viewBox="0 0 24 24">
                <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
                <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
              </svg>
              注册中...
            </span>
            <span v-else>注册</span>
          </button>
        </form>

        <p class="mt-6 text-center text-sm text-slate-500">
          已有账号？
          <router-link to="/login" class="text-bookmind-primary font-medium hover:underline">
            去登录
          </router-link>
        </p>
      </div>
    </div>
    <!-- 模拟验证码 Toast -->
    <div v-if="codeToast" class="code-toast">
      <div class="code-toast-title">📱 验证码</div>
      <div class="code-toast-value">{{ smsCode }}</div>
    </div>
  </div>
</template>

<style scoped>
.code-toast { position: fixed; bottom: 24px; right: 24px; z-index: 999; background: var(--bg-white); color: var(--text-primary); padding: 16px 24px; border-radius: var(--radius-md); box-shadow: var(--shadow-lg); text-align: center; animation: fadeIn 0.3s ease; border: 1px solid var(--border-light); }
.code-toast-title { font-size: 13px; color: var(--text-muted); margin-bottom: 4px; }
.code-toast-value { font-size: 28px; font-weight: 700; letter-spacing: 6px; font-family: monospace; color: var(--accent-terracotta); }
@keyframes fadeIn { from { opacity: 0; transform: translateY(10px); } to { opacity: 1; transform: translateY(0); } }
</style>
<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores'
import { authAPI } from '@/api'

const router = useRouter()
const userStore = useUserStore()

const formData = ref({
  phone: '',
  username: '',
  password: '',
  confirmPassword: '',
  code: '',
})

const errors = ref({})
const codeSending = ref(false)
const codeCountdown = ref(0)
const smsCode = ref('')
const codeToast = ref(false)
let codeTimer = null

function sendCode() {
  if (codeCountdown.value > 0) return
  if (!formData.value.phone || formData.value.phone.length !== 11) {
    errors.value.phone = '请输入正确的手机号'
    return
  }
  smsCode.value = String(Math.floor(100000 + Math.random() * 900000))
  codeToast.value = true
  setTimeout(() => { codeToast.value = false }, 10000)
  codeCountdown.value = 60
  codeTimer = setInterval(() => {
    codeCountdown.value--
    if (codeCountdown.value <= 0) { clearInterval(codeTimer); codeTimer = null }
  }, 1000)
}
const phoneValidated = ref(false)
const usernameChecking = ref(false)
const usernameAvailable = ref(null)
const loading = ref(false)

let checkUsernameTimer = null
const debounceCheckUsername = () => {
  if (checkUsernameTimer) clearTimeout(checkUsernameTimer)
  checkUsernameTimer = setTimeout(checkUsername, 500)
}

const validatePhone = () => {
  const phoneRegex = /^1[3-9]\d{9}$/
  phoneValidated.value = phoneRegex.test(formData.value.phone)
  if (!phoneValidated.value && formData.value.phone) {
    errors.value.phone = '手机号格式不正确'
  } else {
    delete errors.value.phone
  }
}

const checkUsername = async () => {
  const username = formData.value.username.trim()
  if (username.length < 3) {
    usernameAvailable.value = false
    return
  }
  usernameChecking.value = true
  usernameAvailable.value = null
  try {
    const res = await authAPI.checkUsername(username)
    usernameAvailable.value = res.data
    if (!res.data) {
      errors.value.username = '该用户名已被注册'
    } else {
      delete errors.value.username
    }
  } catch (error) {
    console.error('检查用户名失败', error)
  } finally {
    usernameChecking.value = false
  }
}

const validateForm = () => {
  errors.value = {}

  const phoneRegex = /^1[3-9]\d{9}$/
  if (!formData.value.phone) {
    errors.value.phone = '手机号不能为空'
  } else if (!phoneRegex.test(formData.value.phone)) {
    errors.value.phone = '手机号格式不正确'
  }

  if (!formData.value.username.trim()) {
    errors.value.username = '用户名不能为空'
  } else if (formData.value.username.length < 3 || formData.value.username.length > 20) {
    errors.value.username = '用户名应为 3-20 位'
  }

  if (!formData.value.password) {
    errors.value.password = '密码不能为空'
  } else if (formData.value.password.length < 6) {
    errors.value.password = '密码至少 6 位'
  }

  if (!formData.value.confirmPassword) {
    errors.value.confirmPassword = '请确认密码'
  } else if (formData.value.password !== formData.value.confirmPassword) {
    errors.value.confirmPassword = '两次输入的密码不一致'
  }

  if (!formData.value.code || formData.value.code.length < 6) {
    errors.value.code = '请获取并输入验证码'
  }

  return Object.keys(errors.value).length === 0
}

const handleRegister = async () => {
  if (!validateForm()) return

  // 校验验证码
  if (formData.value.code !== smsCode.value) {
    errors.value.code = '验证码错误'
    return
  }

  loading.value = true

  try {
    const result = await userStore.register(
      formData.value.username,
      formData.value.password,
      formData.value.phone
    )

    if (result.success) {
      router.push({ name: 'home' })
    } else {
      errors.value.form = result.message || '注册失败，请重试'
    }
  } catch (error) {
    console.error('注册失败', error)
    errors.value.form = '注册失败，请重试'
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
@media (max-width: 480px) {
  .card { padding: 16px 12px !important; }
  h1 { font-size: 18px !important; }
}
</style>