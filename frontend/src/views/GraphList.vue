<template>
  <div class="min-h-screen" style="background:#FDF8F0;">
    <div class="container-main pb-8">
      <div class="text-center mb-8">
        <h1 class="text-2xl font-bold text-bookmind-dark mb-2">🕸️ 知识图谱</h1>
        <p class="text-bookmind-secondary">选择一本书，查看人物关系与实体关联</p>
      </div>

      <div class="max-w-2xl mx-auto">
        <div v-if="books.length === 0" class="text-center py-16 text-slate-500">
          <p class="text-5xl mb-4">📚</p>
          <p>还没有可查看的书籍</p>
          <router-link to="/upload" class="btn btn-primary mt-4" style="background:#8B5E3C;">上传书籍</router-link>
        </div>
        <div v-else class="space-y-3">
          <div v-for="book in books" :key="book.id" @click="$router.push('/graph/' + book.id)"
               class="card p-4 flex items-center gap-4 cursor-pointer hover:shadow-md transition-shadow"
               style="border-color:#E8D5C0;">
            <span class="text-3xl">📖</span>
            <div class="flex-1">
              <h3 class="font-medium text-bookmind-dark">{{ book.title }}</h3>
              <p class="text-sm text-bookmind-secondary">{{ book.author || '未知作者' }} · {{ book.category }}</p>
            </div>
            <span class="text-bookmind-primary">→</span>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useBookStore } from '@/stores'

const bookStore = useBookStore()
const books = ref([])

onMounted(async () => {
  try {
    const data = await bookStore.loadBookList({ page: 1, size: 50 })
    books.value = (data.records || []).filter(b => b.status === 3)
  } catch (e) { console.error(e) }
})
</script>

<style scoped>
@media (max-width: 768px) {
  .container-main { padding: 16px 12px !important; }
  h1 { font-size: 18px !important; }
  .card { padding: 12px !important; }
  .card span.text-3xl { font-size: 24px !important; }
}
</style>