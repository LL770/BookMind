<template>
  <div class="relative">
    <form class="search-bar-wrap" autocomplete="off" @submit.prevent="performSearch">
      <svg class="w-5 h-5" style="color:var(--text-muted)" fill="none" stroke="currentColor" viewBox="0 0 24 24">
        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z"/>
      </svg>
      <input
        v-model="keyword"
        @input="handleInput"
        @keydown.enter="performSearch"
        type="text"
        :placeholder="placeholder"
        class="flex-1 text-sm"
        :class="size === 'lg' ? 'py-2' : 'py-1'"
        autocomplete="nope"
        style="border:none;outline:none;background:transparent;box-shadow:none"
      />
      <button
        v-if="keyword"
        @click="clearSearch"
        class="p-1 rounded-full hover:bg-slate-100 dark:hover:bg-slate-700"
      >
        <svg class="w-4 h-4" style="color:var(--text-muted)" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12"/>
        </svg>
      </button>
      <button
        @click="performSearch"
        class="p-1 rounded-full hover:bg-slate-100 dark:hover:bg-slate-700"
      >
        <svg class="w-5 h-5" style="color:var(--accent-terracotta)" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M14 5l7 7m0 0l-7 7m7-7H3"/>
        </svg>
      </button>
    </form>
  </div>
</template>

<script setup>
import { ref, watch } from 'vue'

const props = defineProps({
  placeholder: { type: String, default: '搜索书籍和笔记...' },
  size: { type: String, default: 'md', validator: (v) => ['sm', 'md', 'lg'].includes(v) },
  debounce: { type: Number, default: 300 },
})

const emit = defineEmits(['search'])

const keyword = ref('')
let searchTimer = null

// 防抖搜索
const handleInput = () => {
  if (searchTimer) {
    clearTimeout(searchTimer)
  }
  searchTimer = setTimeout(() => {
    if (keyword.value.trim()) {
      emit('search', keyword.value.trim())
    }
  }, props.debounce)
}

// 执行搜索
const performSearch = () => {
  if (keyword.value.trim()) {
    emit('search', keyword.value.trim())
  }
}

// 清空搜索
const clearSearch = () => {
  keyword.value = ''
  emit('search', '')
}

// 监听外部变化
watch(() => props.placeholder, (val) => {
  // placeholder 变化时不做处理
})
</script>

<style scoped>
.search-bar-wrap {
  display: flex; align-items: center; gap: 8px;
  padding: 10px 18px; border-radius: 24px;
  border: 1.5px solid #DCD0C0;
  background: var(--bg-white);
  transition: all 0.25s ease;
}
.search-bar-wrap:focus-within {
  border-color: var(--accent-terracotta);
  box-shadow: 0 0 0 3px rgba(198,123,92,0.12);
  background: #FFFCF8;
}
.search-bar-wrap input { caret-color: var(--text-primary); border: none !important; outline: none !important; box-shadow: none !important; }
</style>
