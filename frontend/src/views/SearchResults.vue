<template>
  <div class="min-h-screen" style="background:var(--bg-cream)">
    <div class="container-main pb-8">
      <!-- 搜索框 -->
      <div class="max-w-2xl mx-auto mb-8">
        <SearchBar @search="handleSearch" size="lg" />
      </div>

      <!-- 加载状态 -->
      <div v-if="searching" class="text-center py-16">
        <svg class="w-12 h-12 animate-spin mx-auto text-bookmind-primary" fill="none" viewBox="0 0 24 24">
          <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
          <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
        </svg>
        <p class="mt-4 text-slate-500">搜索中...</p>
      </div>

      <!-- 无结果 -->
      <div v-else-if="searchResults === null" class="text-center py-16">
        <div class="text-6xl mb-4">🔍</div>
        <h2 class="text-xl font-semibold text-slate-700 dark:text-slate-300 mb-2">
          输入关键词开始搜索
        </h2>
        <p class="text-slate-500">支持搜索书籍内容和笔记</p>
      </div>

      <!-- 搜索结果 -->
      <div v-else-if="searchResults.total === 0" class="text-center py-16">
        <div class="text-6xl mb-4">😕</div>
        <h2 class="text-xl font-semibold text-slate-700 dark:text-slate-300 mb-2">
          未找到相关结果
        </h2>
        <p class="text-slate-500">尝试更换关键词或检查拼写</p>
      </div>

      <div v-else>
        <!-- 结果统计 -->
        <div class="mb-6">
          <p class="text-slate-500">
            搜索 "<span class="font-medium text-slate-900 dark:text-slate-100">{{ query }}</span>" 
            共找到 {{ searchResults.total }} 条结果
          </p>
        </div>

        <div class="grid grid-cols-1 lg:grid-cols-2 gap-6">
          <!-- 书籍内容 -->
          <div>
            <h3 class="text-lg font-semibold text-slate-900 dark:text-slate-100 mb-4 flex items-center gap-2">
              <span class="text-xl">📖</span> 书中内容 ({{ searchResults.books?.length || 0 }})
            </h3>

            <div v-if="searchResults.books?.length === 0" class="text-sm text-slate-500 py-4">
              暂无匹配内容
            </div>

            <div v-else class="space-y-4">
              <div 
                v-for="(item, index) in searchResults.books"
                :key="index"
                class="card p-4 hover:shadow-md transition-shadow cursor-pointer"
                @click="jumpToBook(item)"
              >
                <div class="flex items-start gap-3">
                  <span class="text-sm px-2 py-0.5 bg-bookmind-primary/10 text-bookmind-primary rounded">
                    📖 本书
                  </span>
                  <span class="text-sm text-slate-500">
                    《{{ item.bookTitle }}》第{{ item.chapterNumber }}章
                  </span>
                </div>
                <p class="mt-2 text-slate-600 dark:text-slate-300 text-sm">
                  {{ highlightText(item.content, query) }}
                </p>
                <p class="mt-2 text-xs text-bookmind-primary">
                  相关度：{{ (item.score * 100).toFixed(1) }}%
                </p>
              </div>
            </div>
          </div>

          <!-- 笔记 -->
          <div>
            <h3 class="text-lg font-semibold text-slate-900 dark:text-slate-100 mb-4 flex items-center gap-2">
              <span class="text-xl">📝</span> 我的笔记 ({{ searchResults.notes?.length || 0 }})
            </h3>

            <div v-if="searchResults.notes?.length === 0" class="text-sm text-slate-500 py-4">
              暂无匹配笔记
            </div>

            <div v-else class="space-y-4">
              <div 
                v-for="(item, index) in searchResults.notes"
                :key="index"
                class="card p-4 hover:shadow-md transition-shadow cursor-pointer"
                @click="jumpToNote(item)"
              >
                <div class="flex items-start justify-between mb-2">
                  <div class="flex items-center gap-2">
                    <span class="text-sm px-2 py-0.5 bg-bookmind-secondary/10 text-bookmind-secondary rounded">
                      📝 笔记
                    </span>
                    <span class="text-sm px-2 py-0.5 bg-slate-100 dark:bg-slate-700 text-slate-600 dark:text-slate-300 rounded">
                      {{ item.category }}
                    </span>
                  </div>
                  <span class="text-xs text-slate-400">{{ formatTime(item.createTime) }}</span>
                </div>
                <p class="text-sm text-slate-500 mb-1">
                  引用：{{ highlightText(item.quoteText, query) }}
                </p>
                <p class="text-slate-600 dark:text-slate-300 text-sm">
                  {{ highlightText(item.content, query) }}
                </p>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useSearchStore } from '@/stores'
import SearchBar from '@/components/SearchBar.vue'

const route = useRoute()
const router = useRouter()
const searchStore = useSearchStore()

const query = ref('')
const searchResults = ref(null)
const searching = ref(false)

// 从 URL 参数获取搜索词
onMounted(() => {
  const q = route.query.q
  if (q) {
    query.value = q
    performSearch(q)
  }
})

// 执行搜索
const performSearch = async (q) => {
  if (!q.trim()) return

  searching.value = true
  try {
    const data = await searchStore.search(q)
    searchResults.value = data
  } catch (error) {
    console.error('搜索失败', error)
  } finally {
    searching.value = false
  }
}

// 处理搜索
const handleSearch = (q) => {
  query.value = q
  router.push({ name: 'search', query: { q } })
  performSearch(q)
}

// 高亮文本
const highlightText = (text, keyword) => {
  if (!text || !keyword) return text
  const regex = new RegExp(`(${keyword})`, 'gi')
  return text.replace(regex, '<mark class="bg-yellow-200 dark:bg-yellow-700/50 px-0.5">$1</mark>')
}

// 跳转到书籍
const jumpToBook = (item) => {
  router.push({
    name: 'reader-chapter',
    params: { bookId: item.bookId, chapterNumber: item.chapterNumber },
  })
}

// 跳转到笔记
const jumpToNote = (item) => {
  // 跳转到笔记详情页或阅读器
  router.push({ name: 'notes' })
}

// 格式化时间
const formatTime = (time) => {
  if (!time) return ''
  let dateStr = time
  if (typeof dateStr === 'string' && !dateStr.endsWith('Z') && !dateStr.includes('+')) dateStr = dateStr.replace(' ', 'T') + '+08:00'
  const date = new Date(dateStr)
  const now = new Date()
  const diff = now - date

  if (diff < 60000) return '刚刚'
  if (diff < 3600000) return `${Math.floor(diff / 60000)}分钟前`
  if (diff < 86400000) return `${Math.floor(diff / 3600000)}小时前`
  return date.toLocaleDateString()
}
</script>

<style scoped>
mark { border-radius: 2px; }

@media (max-width: 768px) {
  .result-item { flex-direction: column; gap: 4px; padding: 10px; }
  .result-title { font-size: 14px; }
  .result-desc { font-size: 12px; }
}
</style>
