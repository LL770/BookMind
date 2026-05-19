<template>
  <div class="min-h-screen graphlist-page">
    <div class="container-main pb-8">
      <div class="text-center mb-8">
        <h1 class="text-2xl font-bold mb-2" style="color:var(--text-primary)">🕸️ 知识图谱</h1>
        <p style="color:var(--text-secondary)">选择一本书，查看人物关系与实体关联</p>
      </div>

      <div class="max-w-2xl mx-auto">
        <div v-if="loading" class="text-center py-16" style="color:var(--text-muted)">
          <p class="text-5xl mb-4">⏳</p>
          <p>加载中...</p>
        </div>
        <div v-else-if="books.length === 0" class="text-center py-16" style="color:var(--text-muted)">
          <p class="text-5xl mb-4">📚</p>
          <p>还没有可查看的书籍</p>
          <router-link to="/upload" class="btn btn-primary mt-4" style="background:var(--accent-terracotta);">上传书籍</router-link>
        </div>
        <div v-else class="space-y-3">
          <div v-for="book in books" :key="book.id"
               class="card p-4 flex items-center gap-4 transition-shadow"
               :class="book.kgGenerated ? 'cursor-pointer hover:shadow-md' : ''"
               :style="{borderColor:'var(--border-light)'}"
               @click="book.kgGenerated ? $router.push('/graph/' + book.id) : null">
            <span class="text-3xl">📖</span>
            <div class="flex-1 min-w-0">
              <h3 class="font-medium truncate" style="color:var(--text-primary)">{{ book.title }}</h3>
              <p class="text-sm truncate" style="color:var(--text-secondary)">{{ book.author || '未知作者' }} · {{ book.category }}</p>
            </div>
            <template v-if="book.kgGenerated">
              <span style="color:var(--accent-terracotta)" class="flex-shrink-0">→</span>
            </template>
            <template v-else>
              <button v-if="!book._generating" @click.stop="handleGenerate(book)"
                      class="btn btn-sm flex-shrink-0" style="background:var(--accent-terracotta);color:white;white-space:nowrap;">
                🔄 生成图谱
              </button>
              <span v-else class="text-sm flex-shrink-0" style="color:var(--text-secondary)">生成中...</span>
            </template>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useBookStore } from '@/stores'
import { graphAPI } from '@/api'

const bookStore = useBookStore()
const books = ref([])
const loading = ref(true)

async function loadBooks() {
  loading.value = true
  try {
    const data = await bookStore.loadBookList({ page: 1, size: 50 })
    books.value = (data.records || []).filter(b => b.status === 3)
  } catch (e) { console.error(e) }
  loading.value = false
}

async function handleGenerate(book) {
  book._generating = true
  try {
    await graphAPI.generateGraph(book.id)
    // 轮询直到 KG 生成完成
    const poll = setInterval(async () => {
      try {
        const res = await graphAPI.getGraphStatus(book.id)
        if (res.data && res.data.nodeCount > 0) {
          book.kgGenerated = 1
          book._generating = false
          clearInterval(poll)
        }
      } catch (e) { console.error(e) }
    }, 5000)
    // 5分钟后超时停止轮询
    setTimeout(() => { clearInterval(poll); if (book._generating) book._generating = false }, 300000)
  } catch (e) {
    console.error('generate KG fail', e)
    book._generating = false
  }
}

onMounted(loadBooks)
</script>

<style scoped>
.graphlist-page { background: var(--bg-cream); }
@media (max-width: 768px) {
  .container-main { padding: 16px 12px !important; }
  h1 { font-size: 18px !important; }
  .card { padding: 12px !important; }
  .card span.text-3xl { font-size: 24px !important; }
}
</style>
