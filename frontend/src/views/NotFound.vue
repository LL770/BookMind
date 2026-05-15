<template>
  <div class="min-h-screen bg-slate-50 dark:bg-slate-900 flex items-center justify-center p-4">
    <div class="text-center max-w-md">
      <div class="text-8xl mb-6">😕</div>
      <h1 class="text-3xl font-bold text-slate-900 dark:text-slate-100 mb-4">
        404 - 页面未找到
      </h1>
      <p class="text-slate-500 dark:text-slate-400 mb-8">
        您访问的页面不存在，或已被移除
      </p>
      
      <div class="flex gap-4 justify-center">
        <router-link to="/" class="btn btn-primary">
          返回首页
        </router-link>
        <button 
          @click="goBack"
          class="btn btn-secondary"
        >
          返回上一页
        </button>
      </div>
    </div>
  </div>
</template>

<script setup>
const goBack = () => {
  window.history.back()
}
</script>
