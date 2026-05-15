<template>
  <div class="book-card" @click="$emit('click', book)">
    <div class="book-cover">
      <div v-if="book.coverUrl" class="cover-img-wrap">
        <img :src="book.coverUrl" :alt="book.title" class="cover-img" loading="lazy" />
      </div>
      <div v-else class="cover-fallback" :class="'cover-' + palettes[colorIndex]">
        <span class="cover-icon">{{ categoryIcon }}</span>
      </div>

      <div class="cover-actions">
        <button v-if="batchMode" class="batch-cb" :class="{ 'batch-cb--checked': selected }" @click.stop="$emit('toggleSelect', book.id)">
          <svg v-if="selected" width="10" height="10" viewBox="0 0 16 16" fill="white"><path d="M12.207 4.793a1 1 0 010 1.414l-5 5a1 1 0 01-1.414 0l-2-2a1 1 0 011.414-1.414L6.5 9.086l4.293-4.293a1 1 0 011.414 0z"/></svg>
        </button>
        <button v-else @click.stop="toggleMenu" class="menu-btn" title="更多">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="currentColor"><circle cx="12" cy="5" r="2"/><circle cx="12" cy="12" r="2"/><circle cx="12" cy="19" r="2"/></svg>
        </button>
      </div>

      <div v-if="book.readProgress > 0" class="read-progress">
        <div class="read-progress-fill" :style="{ width: Math.min(book.readProgress, 100) + '%' }"></div>
      </div>

      <div v-if="showMenu" class="menu-popup" @click.stop>
        <button @click.stop="doDelete" class="menu-item menu-item--danger">🗑️ 删除</button>
        <button @click.stop="doRename" class="menu-item">✏️ 重命名</button>
        <label class="menu-item" @click.stop>
          🖼️ 换封面
          <input type="file" accept="image/*" hidden @change="e => { if(e.target.files[0]) { $emit('coverUpload', e.target.files[0]); showMenu = false } }" />
        </label>
      </div>
    </div>

    <div class="book-info">
      <h3 class="book-title" :title="book.title">{{ book.title }}</h3>
      <p class="book-author" :title="book.author">{{ book.author || '未知作者' }}</p>
      <div class="book-footer">
        <span class="book-category">{{ categoryIcon }} {{ book.category || '未分类' }}</span>
        <span v-if="book.status === 3 && book.readProgress" class="book-progress">{{ book.readProgress }}%</span>
        <span v-if="book.status !== undefined && book.status < 3" class="status-badge processing">{{ book.processMessage || '处理中' }}</span>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'

const props = defineProps({ book: { type: Object, required: true }, batchMode: Boolean, selected: Boolean })
const emit = defineEmits(['click', 'delete', 'toggleSelect', 'coverUpload', 'rename'])

const showMenu = ref(false)
function toggleMenu() { showMenu.value = !showMenu.value }
function doDelete() { showMenu.value = false; emit('delete', props.book) }
function doRename() { showMenu.value = false; emit('rename', props.book) }
function closeMenu(e) { if (!e.target.closest('.menu-popup') && !e.target.closest('.menu-btn')) showMenu.value = false }
onMounted(() => document.addEventListener('click', closeMenu))
onUnmounted(() => document.removeEventListener('click', closeMenu))

const palettes = ['terracotta', 'gold', 'sage', 'slate', 'rose', 'umber']
const colorIndex = computed(() => (props.book.title || '').length % palettes.length)
const categoryIcon = computed(() => ({ '小说': '📖', '技术': '💻', '历史': '📚', '科普': '🔬', '商业': '💼', '艺术': '🎨', '生活': '🧘', '其他': '📂' }[props.book.category] || '📖'))
</script>

<style scoped>
.book-card {
  cursor: pointer; border-radius: 10px; background: var(--bg-white);
  box-shadow: 0 1px 4px rgba(44,24,16,0.06);
  border: 1px solid var(--border-light); position: relative; overflow: hidden;
  transition: box-shadow 0.2s;
}
.book-card:hover { box-shadow: 0 4px 16px rgba(44,24,16,0.1); }

.book-cover {
  position: relative; aspect-ratio: 3/4; overflow: hidden; background: #F0E8DC;
}
.cover-img-wrap { width: 100%; height: 100%; }
.cover-img { width: 100%; height: 100%; object-fit: cover; }
.cover-fallback {
  width: 100%; height: 100%; display: flex; align-items: center; justify-content: center;
}
.cover-icon { font-size: 44px; line-height: 1; }

.cover-terracotta { background: linear-gradient(145deg, #E8D5C8, #D4B8A8); }
.cover-gold { background: linear-gradient(145deg, #E8DCC8, #D4C8A0); }
.cover-sage { background: linear-gradient(145deg, #D0DCC8, #B8C8A8); }
.cover-slate { background: linear-gradient(145deg, #D0D0D8, #B8B8C8); }
.cover-rose { background: linear-gradient(145deg, #E8C8D0, #D8B0B8); }
.cover-umber { background: linear-gradient(145deg, #D4C8B8, #C0B0A0); }

.cover-actions {
  position: absolute; top: 5px; right: 5px; z-index: 10;
  display: flex; flex-direction: column; gap: 3px; align-items: flex-end;
}
.batch-cb {
  width: 22px; height: 22px; border-radius: 50%;
  border: 1.5px solid rgba(255,255,255,0.9); background: rgba(0,0,0,0.15);
  cursor: pointer; display: flex; align-items: center; justify-content: center;
}
.batch-cb--checked { background: var(--accent-terracotta); }
.menu-btn {
  width: 22px; height: 22px; border-radius: 50%; border: none;
  background: rgba(255,255,255,0.85); cursor: pointer; color: var(--text-muted);
  display: flex; align-items: center; justify-content: center;
  box-shadow: 0 1px 3px rgba(0,0,0,0.08); opacity: 0; transition: opacity 0.15s;
}
.menu-btn:hover { background: white; color: var(--text-primary); }
.book-card:hover .menu-btn { opacity: 1; }

.menu-popup {
  position: absolute; top: 30px; right: 4px; z-index: 20;
  background: white; border-radius: 8px; box-shadow: 0 4px 16px rgba(44,24,16,0.15);
  padding: 4px 0; min-width: 120px; border: 1px solid var(--border-light);
}
.menu-item {
  display: block; width: 100%; padding: 7px 14px; text-align: left;
  background: none; border: none; cursor: pointer; font-size: 12px;
  color: var(--text-secondary); transition: background 0.1s; white-space: nowrap;
}
.menu-item:hover { background: var(--bg-cream); }
.menu-item--danger { color: var(--accent-rose); }

.read-progress {
  position: absolute; bottom: 0; left: 0; right: 0; height: 3px;
  background: rgba(0,0,0,0.05);
}
.read-progress-fill { height: 100%; background: var(--accent-terracotta); transition: width 0.4s; }

.book-info { padding: 8px 10px 10px; }
.book-title {
  font-family: var(--font-heading); font-size: 13px; font-weight: 700;
  color: var(--text-primary); margin-bottom: 1px;
  white-space: nowrap; overflow: hidden; text-overflow: ellipsis;
}
.book-author {
  font-size: 11px; color: var(--text-muted); margin-bottom: 4px;
  white-space: nowrap; overflow: hidden; text-overflow: ellipsis;
}
.book-footer {
  display: flex; align-items: center; justify-content: space-between; gap: 4px;
  font-size: 10px;
}
.book-category { color: var(--text-muted); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.book-progress { color: var(--accent-terracotta); font-weight: 600; flex-shrink: 0; }
.status-badge { font-size: 9px; font-weight: 500; flex-shrink: 0; }
.status-badge.processing { color: var(--accent-terracotta); }

@media (max-width: 640px) {
  .book-cover { aspect-ratio: 3/4; }
  .cover-icon { font-size: 32px; }
  .menu-btn { opacity: 0.7; }
  .book-info { padding: 6px 8px 8px; }
  .book-title { font-size: 12px; }
  .book-author { font-size: 10px; margin-bottom: 3px; }
}
@media (max-width: 480px) {
  .cover-icon { font-size: 26px; }
  .book-title { font-size: 11px; }
  .book-author { font-size: 9px; }
  .book-footer { font-size: 9px; }
  .book-info { padding: 4px 6px 6px; }
}
</style>
