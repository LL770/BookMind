<template>
  <div class="skeleton-loader" :class="[type, shape]" :style="customStyle">
    <div v-if="type === 'card'" class="skeleton-card">
      <div class="skeleton-img"></div>
      <div class="skeleton-line w-75"></div>
      <div class="skeleton-line w-50"></div>
    </div>
    <div v-else-if="type === 'text-block'" class="skeleton-text-block">
      <div v-for="i in lines" :key="i" class="skeleton-line" :class="'w-' + widths[(i - 1) % widths.length]"></div>
    </div>
    <div v-else-if="type === 'circle'" class="skeleton-circle"></div>
    <div v-else-if="type === 'grid'" class="skeleton-grid">
      <div v-for="i in count" :key="i" class="skeleton-card">
        <div class="skeleton-img"></div>
        <div class="skeleton-line w-75"></div>
        <div class="skeleton-line w-50"></div>
      </div>
    </div>
    <div v-else-if="type === 'chat-message'" class="skeleton-chat">
      <div v-for="i in count" :key="i" class="skeleton-msg" :class="{ 'skeleton-msg--right': i % 2 === 0 }">
        <div class="skeleton-avatar"></div>
        <div class="skeleton-bubble">
          <div class="skeleton-line w-80"></div>
          <div class="skeleton-line w-60"></div>
        </div>
      </div>
    </div>
    <div v-else class="skeleton-line w-100"></div>
  </div>
</template>

<script setup>
const props = defineProps({
  type: { type: String, default: 'text-block' },
  lines: { type: Number, default: 3 },
  count: { type: Number, default: 4 },
  shape: { type: String, default: '' },
  customStyle: { type: Object, default: () => ({}) },
})

const widths = ['100', '80', '60', '90', '70', '50']
</script>

<style scoped>
.skeleton-loader { width: 100%; }
.skeleton-line {
  height: 14px; border-radius: 6px; margin-bottom: 12px;
  background: linear-gradient(90deg, var(--border-light) 25%, #f0e8dc 50%, var(--border-light) 75%);
  background-size: 200% 100%; animation: shimmer 1.5s ease infinite;
}
.skeleton-line:last-child { margin-bottom: 0; }
.w-100 { width: 100%; }
.w-90 { width: 90%; }
.w-80 { width: 80%; }
.w-75 { width: 75%; }
.w-70 { width: 70%; }
.w-60 { width: 60%; }
.w-50 { width: 50%; }

.skeleton-card {
  background: var(--bg-white); border-radius: var(--radius-md);
  border: 1px solid var(--border-light); padding: 16px;
}
.skeleton-img {
  width: 100%; aspect-ratio: 3/4; border-radius: var(--radius-sm); margin-bottom: 12px;
  background: linear-gradient(90deg, var(--border-light) 25%, #f0e8dc 50%, var(--border-light) 75%);
  background-size: 200% 100%; animation: shimmer 1.5s ease infinite;
}
.skeleton-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(160px, 1fr)); gap: 16px; }

.skeleton-circle {
  width: 48px; height: 48px; border-radius: 50%;
  background: linear-gradient(90deg, var(--border-light) 25%, #f0e8dc 50%, var(--border-light) 75%);
  background-size: 200% 100%; animation: shimmer 1.5s ease infinite;
}

.skeleton-chat { display: flex; flex-direction: column; gap: 16px; padding: 16px; }
.skeleton-msg { display: flex; gap: 10px; align-items: flex-start; }
.skeleton-msg--right { flex-direction: row-reverse; }
.skeleton-avatar {
  width: 36px; height: 36px; border-radius: 50%; flex-shrink: 0;
  background: var(--border-light); animation: shimmer 1.5s ease infinite;
}
.skeleton-bubble {
  flex: 1; max-width: 70%; padding: 12px 16px;
  background: var(--bg-white); border-radius: 16px; border: 1px solid var(--border-light);
}
.skeleton-bubble .skeleton-line { background: var(--border-light); animation: shimmer 1.5s ease infinite; }

@keyframes shimmer { 0% { background-position: 200% 0; } 100% { background-position: -200% 0; } }

@media (max-width: 768px) {
  .skeleton-grid { grid-template-columns: repeat(2, 1fr); gap: 10px; }
}
</style>
