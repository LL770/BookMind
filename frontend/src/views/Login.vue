<template>
  <div class="login-bg min-h-screen flex items-center justify-center p-4" style="min-height:100dvh">
    <div class="w-full max-w-md">
      <div class="card p-8" style="background:#FFFCF8;border-radius:var(--radius-lg);border-color:#E8DDD0">
        <div class="text-center mb-8">
          <span class="text-4xl mb-4 block">📚</span>
          <h1 class="text-2xl font-bold mb-2" style="font-family:var(--font-heading);color:var(--text-primary)">
            登录 BookMind
          </h1>
          <p style="color:var(--text-muted);font-size:14px">
            欢迎回来，请输入您的账号信息
          </p>
        </div>

        <form @submit.prevent="handleLogin" class="space-y-5">
          <div>
            <label class="block text-sm font-medium text-slate-700 dark:text-slate-300 mb-1.5" style="color:var(--text-secondary)">
              用户名
            </label>
            <input
              v-model="formData.username"
              type="text"
              placeholder="请输入用户名"
              class="input"
              :class="{ 'input-error': errors.username }"
              autocomplete="username"
            />
            <p v-if="errors.username" class="mt-1 text-sm" style="color:var(--accent-rose)">{{ errors.username }}</p>
          </div>

          <div>
            <label class="block text-sm font-medium mb-1.5" style="color:var(--text-secondary)">
              密码
            </label>
            <input
              v-model="formData.password"
              type="password"
              placeholder="请输入密码"
              class="input"
              :class="{ 'input-error': errors.password }"
              autocomplete="current-password"
            />
            <p v-if="errors.password" class="mt-1 text-sm" style="color:var(--accent-rose)">{{ errors.password }}</p>
          </div>

          <!-- 表单级错误提示 -->
          <div v-if="errors.form" class="p-3 rounded-lg" style="background:rgba(184,101,90,0.1);color:var(--accent-rose);font-size:13px">
            {{ errors.form }}
          </div>

          <button
            type="submit"
            :disabled="loading"
            class="w-full btn btn--primary"
            style="padding:12px 20px;font-size:15px;border-radius:var(--radius-sm)"
            :class="{ 'opacity-50 cursor-not-allowed': loading }"
          >
            <span v-if="loading" class="flex items-center justify-center gap-2">
              <svg class="w-5 h-5 animate-spin" fill="none" viewBox="0 0 24 24">
                <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
                <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
              </svg>
              登录中...
            </span>
            <span v-else>登录</span>
          </button>
        </form>

        <p class="mt-6 text-center text-sm text-slate-500">
          没有账号？
          <router-link to="/register" class="text-bookmind-primary font-medium hover:underline">
            去注册
          </router-link>
        </p>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useUserStore } from '@/stores'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const formData = ref({
  username: '',
  password: '',
})

const errors = ref({})
const loading = ref(false)

const validateForm = () => {
  errors.value = {}

  if (!formData.value.username.trim()) {
    errors.value.username = '用户名不能为空'
  }

  if (!formData.value.password) {
    errors.value.password = '密码不能为空'
  }

  return Object.keys(errors.value).length === 0
}

const handleLogin = async () => {
  if (!validateForm()) return

  loading.value = true

  try {
    const result = await userStore.login(formData.value.username, formData.value.password)

    if (result.success) {
      const redirect = route.query.redirect || '/'
      router.push(redirect)
    } else {
      errors.value.form = result.message || '用户名或密码错误'
    }
  } catch (error) {
    console.error('登录失败', error)
    errors.value.form = '登录失败，请检查用户名和密码'
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-bg { background: linear-gradient(135deg, #FDF8F0 0%, #F5E6D3 50%, #EDD9C4 100%); }
@media (max-width: 480px) {
  .card { padding: 16px 12px !important; }
  h1 { font-size: 18px !important; }
}
</style>