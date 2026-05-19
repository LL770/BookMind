<template>
  <div class="min-h-screen reader-page" @contextmenu.prevent>
    <!-- 有子路由（正在阅读） → 渲染阅读组件 -->
    <!-- key 仅用 bookId：换页时保持组件存活，全屏状态不丢失 -->
    <router-view v-if="$route.params.chapterNumber"
      :key="$route.params.bookId"
      :bookId="Number($route.params.bookId)"
      :chapterNumber="Number($route.params.chapterNumber)" />

    <!-- 没有子路由 → 显示书籍选择列表 -->
    <div v-else class="container-main pt-24 pb-8">
      <div class="max-w-4xl mx-auto">
        <div class="text-center mb-8">
          <h1 class="text-2xl font-bold mb-2" style="color:var(--text-primary)">📖 选择书籍</h1>
          <p class="text-sm" style="color:var(--text-secondary)">点击书籍开始阅读</p>
        </div>

        <div v-if="books.length === 0" class="text-center py-16">
          <p class="text-5xl mb-4">📭</p>
          <p style="color:var(--text-muted)" class="mb-4">暂无已完成的书籍</p>
          <router-link to="/stats" class="px-5 py-2 rounded-lg text-white text-sm" style="background:var(--accent-terracotta);">阅读统计</router-link>
        </div>

        <div v-else class="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-4 gap-4">
          <div v-for="book in books" :key="book.id"
            @click="openBook(book)"
            class="card p-4 cursor-pointer hover:shadow-md transition-shadow"
            :style="{borderColor:'var(--border-light)'}">
            <div class="aspect-[2/3] rounded-lg mb-3 flex items-center justify-center text-5xl" style="background:var(--bg-cream);">
              📖
            </div>
            <h3 class="font-semibold truncate text-sm" style="color:var(--text-primary)">{{ book.title }}</h3>
            <p class="text-xs truncate" style="color:var(--text-secondary)">{{ book.author || '未知作者' }}</p>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useBookStore } from '@/stores'
import axios from 'axios'

const router = useRouter()
const route = useRoute()
const bookStore = useBookStore()

const books = ref([])

onMounted(async () => {
  try {
    const data = await bookStore.loadBookList({ size: 100 })
    books.value = (data.records || []).filter(b => b.status === 3)
  } catch (e) { console.error(e) }
})

const openBook = async (book) => {
  try {
    const token = localStorage.getItem('token')
    const res = await axios.get(`/api/reader/${book.id}/progress`, {
      headers: { 'Authorization': `Bearer ${token}` }
    })
    if (res.data.code === 0 && res.data.data) {
      const lastChapter = res.data.data.chapterNumber || 1
      router.push({ name: 'reader-chapter', params: { bookId: book.id, chapterNumber: lastChapter } })
      return
    }
  } catch (e) { /* 无进度记录，从第1页开始 */ }
  router.push({ name: 'reader-chapter', params: { bookId: book.id, chapterNumber: 1 } })
}
</script>

<style scoped>
.reader-page { background: var(--bg-cream); }
@media (max-width: 768px) {
  .container-main { padding: 12px; }
  h1 { font-size: 18px !important; }
  .grid { grid-template-columns: repeat(2, 1fr) !important; gap: 8px !important; }
  .card { padding: 10px !important; }
  .card h3 { font-size: 13px; }
}
</style>