<template>
  <div class="kg-page">
    <div class="container-main pb-8">
      <div class="max-w-4xl mx-auto">
        <!-- 标题 -->
        <div class="text-center mb-5">
          <h1 class="text-2xl font-bold text-slate-800 dark:text-slate-100">
            🧠 知识图谱
          </h1>
        </div>

        <!-- 图谱容器 -->
        <div class="card overflow-hidden graph-card" :class="{ 'card-fullscreen': isFullscreen }">
          <div v-if="!hasEdges && !loading" class="kg-empty-hint">
            <span>暂未生成关系连线</span>
          </div>
          <div v-show="showToolbar" class="graph-toolbar">
            <button @click="toggleToolbar" class="graph-btn graph-btn--hide" :title="showToolbar ? '隐藏导航栏' : '显示导航栏'" style="margin-right:2px">
              <svg class="w-3 h-3" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" viewBox="0 0 24 24"><path d="M4 5h16M4 12h16M4 19h16"/></svg>
            </button>
            <span class="text-xs text-slate-400 mr-2 whitespace-nowrap">{{ bookTitle }}</span>
            <div class="flex flex-wrap items-center gap-1">
              <label
                v-for="cat in categoryFilters" :key="cat.key"
                class="inline-flex items-center gap-1 px-2 py-0.5 rounded-full cursor-pointer select-none transition-all text-xs"
                :class="selectedCategories.has(cat.key) ? 'shadow-sm' : 'opacity-45'"
                :style="{
                  background: selectedCategories.has(cat.key) ? cat.color + '16' : 'rgba(139,94,60,0.06)',
                  border: '1px solid ' + (selectedCategories.has(cat.key) ? cat.color : '#dcd0c0'),
                }"
              >
                <input type="checkbox" :checked="selectedCategories.has(cat.key)" @change="toggleCategory(cat.key)" class="sr-only" />
                <span class="w-2 h-2 rounded-full" :style="{ background: cat.color }"></span>
                <span :style="{ color: selectedCategories.has(cat.key) ? cat.color : '#8b7d6b' }">{{ cat.label }}</span>
              </label>
            </div>
            <div class="flex gap-1 ml-auto">
              <button @click="focusNodeId = null; renderGraph()" v-if="focusNodeId" class="graph-btn" title="显示全部">🔙</button>
              <button @click="isFullPage = !isFullPage" class="graph-btn" :title="isFullPage ? '退出全页' : '全页显示'">
                <svg v-if="!isFullPage" class="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 8V4m0 0h4M4 4l5 5m11-1V4m0 0h-4m4 0l-5 5M4 16v4m0 0h4m-4 0l5-5m11 5l-5-5m5 5v-4m0 4h-4"/></svg>
                <svg v-else class="w-3.5 h-3.5" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" viewBox="0 0 24 24"><path d="M15 19l-7-7 7-7"/></svg>
              </button>
              <button @click="toggleFullscreen" class="graph-btn" :title="isFullscreen ? '退出全屏' : '全屏显示'">
                <svg v-if="!isFullscreen" class="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M8 3H5a2 2 0 00-2 2v3m18 0V5a2 2 0 00-2-2h-3m0 18h3a2 2 0 002-2v-3M3 16v3a2 2 0 002 2h3"/></svg>
                <svg v-else class="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12"/></svg>
              </button>
              <button @click="toggleLines" class="graph-btn" :title="useCurvedLines ? '切换为直线' : '切换为弧线'">
                <svg v-if="useCurvedLines" class="w-3.5 h-3.5" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" viewBox="0 0 24 24"><path d="M2 18 Q8 4 16 12 T22 6"/></svg>
                <svg v-else class="w-3.5 h-3.5" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" viewBox="0 0 24 24"><line x1="2" y1="12" x2="22" y2="12"/></svg>
              </button>
              <button @click="toggleEdgeLabels" class="graph-btn" :title="showEdgeLabels ? '隐藏概念' : '显示概念'" :style="{ opacity: showEdgeLabels ? 1 : 0.4 }">
                <svg class="w-3.5 h-3.5" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" viewBox="0 0 24 24"><path d="M4 7h16M4 12h16M4 17h10"/></svg>
              </button>
            </div>
          </div>
          <div :ref="(el) => setGraphRef(el, 'card')" class="graph-container" :style="{ height: isFullPage ? 'calc(100vh - 120px)' : '600px' }"></div>
          <button v-show="!showToolbar" @click="toggleToolbar" class="tb-show-btn" :title="'显示导航栏'">
            <svg class="w-3 h-3" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" viewBox="0 0 24 24"><path d="M6 5l12 7-12 7"/></svg>
          </button>
        </div>
        <!-- 子图 / 全图切换 -->
        <div v-if="focusNodeId" class="flex items-center gap-2 mt-2">
          <button @click="focusNodeId = null; renderGraph()" class="btn btn-sm" style="background:#8B5E3C;color:white;">
            🔙 显示全部
          </button>
          <span class="text-sm text-slate-500">当前聚焦：{{ focusNodeName }} 的关系网</span>
        </div>

        <!-- 节点详情弹窗 -->
        <div v-if="detailModalVisible" class="modal-mask" @click.self="closeDetail">
          <div class="modal-content">
            <div class="flex items-start justify-between mb-3">
              <div>
                <h3 class="text-lg font-semibold text-slate-900 dark:text-slate-100">
                  {{ selectedNode?.name }}
                </h3>
                <p class="text-sm text-slate-500">
                  {{ nodeTypeLabels[selectedNode?.category] || selectedNode?.type }}
                </p>
              </div>
              <button @click="closeDetail" class="p-1 rounded hover:bg-slate-100 dark:hover:bg-slate-700">
                <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12"/>
                </svg>
              </button>
            </div>
            <p v-if="selectedNode?.description" class="text-sm text-slate-600 dark:text-slate-300 mb-4">
              {{ selectedNode.description }}
            </p>
            <div class="flex gap-2">
              <button @click="focusNode(selectedNode.id, selectedNode.name); closeDetail()" class="btn btn-accent btn-sm">
                🔗 显示关联节点
              </button>
              <button @click="showFullGraph(selectedNode.id, selectedNode.name); closeDetail()" class="btn btn-secondary btn-sm">
                🌐 查看关系网
              </button>
              <button v-if="relatedNotesCount > 0" @click="viewRelatedNotes" class="btn btn-secondary btn-sm">
                📝 相关笔记 ({{ relatedNotesCount }})
              </button>
            </div>
          </div>
        </div>

        <!-- 笔记弹窗 -->
        <div v-if="notesModalVisible" class="modal-mask" @click.self="notesModalVisible = false">
          <div class="modal-content notes-modal">
            <div class="flex items-center justify-between mb-3">
              <h3 class="text-base font-semibold">📝 相关笔记（{{ notesModalData.length }} 条）</h3>
              <button @click="notesModalVisible = false" class="p-1 rounded hover:bg-slate-100">
                <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12"/>
                </svg>
              </button>
            </div>
            <div class="notes-list">
              <div v-for="(note, i) in notesModalData" :key="i" class="note-item">
                <div class="note-quote" v-if="note.quoteText">「{{ note.quoteText }}」</div>
                <div class="note-content">{{ note.content }}</div>
              </div>
            </div>
            <button @click="notesModalVisible = false" class="btn btn-sm mt-3 w-full" style="background:#8B5E3C;color:white;">
              关闭
            </button>
          </div>
        </div>

      </div>
    </div>
    <!-- 全页覆盖层：仅图谱 + toolbar -->
    <div v-if="isFullPage" class="kg-fullpage-overlay" @click.self="isFullPage = false">
      <div v-show="showToolbar" class="graph-toolbar">
        <button @click="toggleToolbar" class="graph-btn graph-btn--hide" :title="'隐藏导航栏'" style="margin-right:2px">
          <svg class="w-3 h-3" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" viewBox="0 0 24 24"><path d="M4 5h16M4 12h16M4 19h16"/></svg>
        </button>
        <span class="text-xs text-slate-400 mr-2 whitespace-nowrap">{{ bookTitle }}</span>
        <div class="flex flex-wrap items-center gap-1">
          <label
            v-for="cat in categoryFilters" :key="cat.key"
            class="inline-flex items-center gap-1 px-2 py-0.5 rounded-full cursor-pointer select-none transition-all text-xs"
            :class="selectedCategories.has(cat.key) ? 'shadow-sm' : 'opacity-45'"
            :style="{
              background: selectedCategories.has(cat.key) ? cat.color + '16' : 'rgba(139,94,60,0.06)',
              border: '1px solid ' + (selectedCategories.has(cat.key) ? cat.color : '#dcd0c0'),
            }"
          >
            <input type="checkbox" :checked="selectedCategories.has(cat.key)" @change="toggleCategory(cat.key)" class="sr-only" />
            <span class="w-2 h-2 rounded-full" :style="{ background: cat.color }"></span>
            <span :style="{ color: selectedCategories.has(cat.key) ? cat.color : '#8b7d6b' }">{{ cat.label }}</span>
          </label>
        </div>
        <div class="flex gap-1 ml-auto">
          <button @click="focusNodeId = null; renderGraph()" v-if="focusNodeId" class="graph-btn" title="显示全部">🔙</button>
          <button @click="toggleFullscreen" class="graph-btn" :title="isFullscreen ? '退出全屏' : '全屏显示'">
            <svg v-if="!isFullscreen" class="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M8 3H5a2 2 0 00-2 2v3m18 0V5a2 2 0 00-2-2h-3m0 18h3a2 2 0 002-2v-3M3 16v3a2 2 0 002 2h3"/></svg>
            <svg v-else class="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12"/></svg>
          </button>
          <button @click="toggleLines" class="graph-btn" :title="useCurvedLines ? '切换为直线' : '切换为弧线'">
            <svg v-if="useCurvedLines" class="w-3.5 h-3.5" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" viewBox="0 0 24 24"><path d="M2 18 Q8 4 16 12 T22 6"/></svg>
            <svg v-else class="w-3.5 h-3.5" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" viewBox="0 0 24 24"><line x1="2" y1="12" x2="22" y2="12"/></svg>
          </button>
          <button @click="toggleEdgeLabels" class="graph-btn" :title="showEdgeLabels ? '隐藏概念' : '显示概念'" :style="{ opacity: showEdgeLabels ? 1 : 0.4 }">
            <svg class="w-3.5 h-3.5" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" viewBox="0 0 24 24"><path d="M4 7h16M4 12h16M4 17h10"/></svg>
          </button>
          <button @click="isFullPage = false" class="graph-btn" title="退出全页">
            <svg class="w-3.5 h-3.5" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" viewBox="0 0 24 24"><path d="M15 19l-7-7 7-7"/></svg>
          </button>
        </div>
      </div>
      <div :ref="(el) => setGraphRef(el, 'overlay')" class="graph-container" style="flex:1;min-height:0;">
        <button v-show="!showToolbar" @click="toggleToolbar" class="tb-show-btn" :title="'显示导航栏'">
          <svg class="w-3 h-3" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" viewBox="0 0 24 24"><path d="M6 5l12 7-12 7"/></svg>
        </button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted, onUnmounted, nextTick } from 'vue'
import { useRoute } from 'vue-router'
import { graphAPI, bookAPI } from '@/api'

let echarts = null
async function getEcharts() {
  if (!echarts) {
    const mod = await import('echarts')
    echarts = mod
  }
  return echarts
}

const route = useRoute()
let graphRefCard = null
let graphRefOverlay = null
function setGraphRef(el, type) {
  if (type === 'card') graphRefCard = el
  else graphRefOverlay = el
}
function getGraphEl() { return isFullPage.value ? graphRefOverlay : graphRefCard }

const bookTitle = ref('')
const graphData = ref({ nodes: [], links: [] })
const selectedNode = ref(null)
const relatedNotesCount = ref(0)
const notesModalVisible = ref(false)
const notesModalData = ref([])
const loading = ref(false)
const focusNodeId = ref(null)
const focusNodeName = ref('')
const isFullPage = ref(false)
const isFullscreen = ref(false)

const hasEdges = computed(() => (graphData.value.links || []).length > 0)

const categoryFilters = [
  { key: 'person', label: '人物', color: '#22c55e' },
  { key: 'organization', label: '组织', color: '#3b82f6' },
  { key: 'location', label: '地点', color: '#eab308' },
  { key: 'concept', label: '概念', color: '#a855f7' },
  { key: 'event', label: '事件', color: '#ef4444' },
  { key: 'artifact', label: '物品', color: '#f97316' },
]
const selectedCategories = ref(new Set(categoryFilters.map(c => c.key)))
const detailModalVisible = ref(false)
const useCurvedLines = ref(true)
const showEdgeLabels = ref(true)
const showToolbar = ref(true)

let chart = null

// 节点类型标签（与 DB kg_node.type 一致）
const nodeTypeLabels = {
  person: '人物',
  organization: '组织',
  location: '地点',
  concept: '概念',
  event: '事件',
  artifact: '物品',
}

// 全页切换时重渲染
watch(isFullPage, () => nextTick(() => renderGraph()))

// 加载知识图谱
onMounted(() => {
  const bookId = route.params.bookId
  loadGraph(bookId)
  document.addEventListener('fullscreenchange', onFullscreenChange)
})

const loadGraph = async (bookId) => {
  try {
    try {
      const bookRes = await bookAPI.getById(bookId)
      bookTitle.value = bookRes.data?.title || '未知书籍'
    } catch (e) { /* ignore */ }

    const res = await graphAPI.getGraph(bookId)
    graphData.value = res.data
    renderGraph()
  } catch (error) {
    console.error('加载知识图谱失败', error)
  }
}


const toggleCategory = (key) => {
  const s = new Set(selectedCategories.value)
  if (s.has(key)) s.delete(key)
  else s.add(key)
  selectedCategories.value = s
  renderGraph()
}

const closeDetail = () => {
  detailModalVisible.value = false
  selectedNode.value = null
}

const toggleLines = () => {
  useCurvedLines.value = !useCurvedLines.value
  renderGraph()
}

const toggleEdgeLabels = () => {
  showEdgeLabels.value = !showEdgeLabels.value
  renderGraph()
}

const toggleToolbar = () => {
  showToolbar.value = !showToolbar.value
}

const focusNode = (nodeId, nodeName) => {
  focusNodeId.value = nodeId
  focusNodeName.value = nodeName
  renderGraph()
}

const showFullGraph = (nodeId, nodeName) => {
  focusNodeId.value = null
  focusNodeName.value = ''
  renderGraph()
  // 高亮该节点
  setTimeout(() => {
    if (!chart) return
    chart.dispatchAction({ type: 'downplay' })
    chart.dispatchAction({ type: 'highlight', seriesIndex: 0, dataIndex: graphData.value.nodes.findIndex(n => String(n.id) === String(nodeId)) })
  }, 300)
}

const renderGraph = async () => {
  const el = getGraphEl()
  if (!el) return
  const ec = await getEcharts()

  if (chart) chart.dispose()
  chart = ec.init(el)

  let nodes = graphData.value.nodes || []
  let links = graphData.value.links || []

  // 概念筛选
  const catKey = (n) => n.type || n.category || 'concept'
  nodes = nodes.filter(n => selectedCategories.value.has(catKey(n)))
  const visibleIds = new Set(nodes.map(n => n.id))
  links = links.filter(l => {
    const src = l.source ?? l.from
    const tgt = l.target ?? l.to
    return visibleIds.has(src) && visibleIds.has(tgt)
  })

  // 聚焦模式：只显示选中节点及其直接关联的节点+边
  if (focusNodeId.value) {
    const connectedNodeIds = new Set()
    connectedNodeIds.add(focusNodeId.value)
    const filteredLinks = links.filter(l => {
      const src = l.source || l.from
      const tgt = l.target || l.to
      if (src == focusNodeId.value || tgt == focusNodeId.value) {
        connectedNodeIds.add(src)
        connectedNodeIds.add(tgt)
        return true
      }
      return false
    })
    links = filteredLinks
    nodes = nodes.filter(n => connectedNodeIds.has(n.id))
  }

  const nodeList = nodes.map((node, idx) => ({
    id: String(node.id || idx),
    name: node.name || String(node.id || ''),
    category: node.type || node.category || 'concept',
    symbolSize: node.symbolSize || (focusNodeId.value && String(node.id) == String(focusNodeId.value) ? 60 : 40),
    description: node.description || '',
  }))

  const hasLinks = links.length > 0

  // 平行连线分散：统计每对节点间的连线数并分配不同曲率
  const pairMap = {}
  links.forEach(l => {
    const k = [String(l.source || l.from), String(l.target || l.to)].sort().join('::')
    pairMap[k] = (pairMap[k] || 0) + 1
  })
  const pairIdx = {}
  const edgeLinks = links.map(link => {
    const src = String(link.source || link.from)
    const tgt = String(link.target || link.to)
    const pairKey = [src, tgt].sort().join('::')
    if (!pairIdx[pairKey]) pairIdx[pairKey] = 0
    const idx = pairIdx[pairKey]++
    const total = pairMap[pairKey]

    let curveness
    if (total <= 1) {
      curveness = useCurvedLines.value ? 0.35 : 0
    } else {
      curveness = -0.3 + (idx / (total - 1)) * 0.6
    }

    const labelText = link.relation || link.label || ''
    const truncatedLabel = labelText.length > 8 ? labelText.slice(0, 7) + '…' : labelText

    return {
      source: src,
      target: tgt,
      value: link.value || 1,
      id: link.id,
      lineStyle: { color: '#A08970', curveness, width: 2.5, cap: 'round', opacity: 0.7 },
      label: truncatedLabel && showEdgeLabels.value ? {
        show: true,
        formatter: truncatedLabel,
        fontSize: 10,
        color: "#6B5B4E",
        backgroundColor: "rgba(245,240,232,0.92)",
        padding: [2, 6],
        borderRadius: 4,
        borderColor: "#D4C8B8",
        borderWidth: 0.5,
      } : { show: false },
    }
  })

  const option = {
    tooltip: {
      trigger: window.innerWidth <= 768 ? 'none' : 'item',
      formatter: (params) => {
        if (params.dataType === 'node') {
          return `<div class="font-semibold">${params.data.name}</div>
            <div class="text-sm text-slate-500">${nodeTypeLabels[params.data.category] || params.data.category}</div>
            ${params.data.description ? `<div class="text-sm mt-1">${params.data.description}</div>` : ''}`
        }
        const edge = params.data
        const fullLabel = edge.rawRelation || edge.id || ''
        return `<div>${edge.source} → ${edge.target}</div>${fullLabel ? `<div class="text-xs text-slate-400 mt-1">${fullLabel}</div>` : ''}`
      },
    },
    series: [{
      type: 'graph',
      layout: 'force',
      data: nodeList,
      links: edgeLinks,
      categories: [
        { name: 'person', itemStyle: { color: '#22c55e' } },
        { name: 'organization', itemStyle: { color: '#3b82f6' } },
        { name: 'location', itemStyle: { color: '#eab308' } },
        { name: 'concept', itemStyle: { color: '#a855f7' } },
        { name: 'event', itemStyle: { color: '#ef4444' } },
        { name: 'artifact', itemStyle: { color: '#f97316' } },
      ],
      roam: true,
      zoom: 0.9,
      label: {
        show: true, position: 'right', color: '#475569', fontSize: 12,
        formatter: (p) => {
          const name = p.data.name || ''
          return name.length > 12 ? name.slice(0, 11) + '…' : name
        },
      },
      force: { repulsion: 500, edgeLength: 180, layoutAnimation: false, friction: 0.08 },
      emphasis: {
        focus: 'adjacency',
        lineStyle: { width: 4 },
        label: { show: true, fontSize: 14, fontWeight: 'bold' },
      },
      edgeLabel: { fontSize: 10, color: "#6B5B4E" },
      lineStyle: { color: 'source', opacity: 0.6 },
    }],
  }

  chart.setOption(option)

  chart.off('click')
  chart.on('click', (params) => {
    if (params.dataType === 'node') {
      selectedNode.value = params.data
      detailModalVisible.value = true
      loadRelatedNotes(params.data.id)
    }
  })

  window.addEventListener('resize', handleResize)
}

const handleResize = () => {
  chart?.resize()
}

const toggleFullscreen = async () => {
  const el = document.querySelector('.kg-page')
  if (!document.fullscreenElement) {
    try {
      await el?.requestFullscreen()
      isFullscreen.value = true
    } catch (e) { /* ignore */ }
  } else {
    await document.exitFullscreen()
    isFullscreen.value = false
  }
  setTimeout(() => chart?.resize(), 300)
}

const onFullscreenChange = () => {
  isFullscreen.value = !!document.fullscreenElement
  setTimeout(() => chart?.resize(), 300)
}

const loadRelatedNotes = async (nodeId) => {
  try {
    const res = await graphAPI.getNodeNotes(route.params.bookId, nodeId)
    relatedNotesCount.value = res.data?.length || 0
  } catch (error) {
    console.error('加载节点信息失败', error)
    relatedNotesCount.value = 0
  }
}

const viewRelatedNotes = async () => {
  if (!selectedNode.value) return
  try {
    const res = await graphAPI.getNodeNotes(route.params.bookId, selectedNode.value.id)
    notesModalData.value = res.data?.slice(0, 20) || []
    notesModalVisible.value = true
  } catch (error) {
    console.error('获取相关笔记失败', error)
  }
}

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  document.removeEventListener('fullscreenchange', onFullscreenChange)
  chart?.dispose()
})
</script>

<style scoped>
.kg-page { min-height: calc(100vh - 60px); background: var(--bg-cream); transition: all 0.2s; }
.kg-page.card-fullscreen { background: #1a1a1a; padding: 0; }
.graph-container {
  width: 100%; min-height: 600px; position: relative;
}
.kg-empty-hint {
  position: absolute; top: 50%; left: 50%; transform: translate(-50%,-50%);
  display: flex; flex-direction: column; align-items: center; gap: 4px;
  color: var(--text-muted); font-size: 14px; z-index: 5; pointer-events: none;
}
.graph-toolbar {
  display: flex; align-items: center;
  padding: 6px 12px; border-bottom: 1px solid var(--border-light, #e5ddd0);
  background: var(--bg-white, #faf6f0); gap: 8px;
}
.graph-btn {
  display: flex; align-items: center; justify-content: center;
  width: 28px; height: 28px; border: 1px solid var(--border-light, #e5ddd0);
  border-radius: 4px; background: transparent; cursor: pointer;
  color: var(--text-muted, #8b7d6b); transition: all 0.15s;
}
.graph-btn:hover { background: #f0ebe4; color: #5c4e3e; }
.kg-fullpage-overlay {
  position: fixed; inset: 0; z-index: 1000;
  display: flex; flex-direction: column;
  background: var(--bg-cream, #f5f0e8);
}
.kg-fullpage-overlay .graph-container { flex: 1; min-height: 0; }
.modal-mask {
  position: fixed; inset: 0; z-index: 1001;
  background: rgba(0,0,0,0.4); display: flex;
  align-items: center; justify-content: center;
}
.modal-content {
  background: var(--bg-white, #faf6f0); border-radius: 12px;
  padding: 20px; max-width: 500px; width: 90%; max-height: 70vh;
  box-shadow: 0 8px 32px rgba(0,0,0,0.15);
}
.notes-modal .notes-list {
  max-height: 45vh; overflow-y: auto;
  display: flex; flex-direction: column; gap: 12px;
}
.notes-modal .note-item {
  background: var(--bg-cream, #f5f0e8); border-radius: 8px;
  padding: 10px 12px; border-left: 3px solid var(--accent-terracotta, #c0392b);
}
.notes-modal .note-quote {
  font-size: 13px; color: var(--text-muted, #8b7d6b);
  font-style: italic; margin-bottom: 4px;
}
.notes-modal .note-content {
  font-size: 14px; color: var(--text-main, #3d3229); line-height: 1.5;
}

/* 工具栏显示按钮 - 工具栏隐藏时出现在卡片左上角 */
.graph-card { position: relative; }
.tb-show-btn {
  position: absolute; top: 8px; left: 8px; z-index: 100;
  width: 28px; height: 28px; border-radius: 6px;
  border: 1px solid var(--border-light, #e5ddd0);
  background: var(--bg-white, #faf6f0);
  cursor: pointer; display: flex; align-items: center; justify-content: center;
  color: var(--text-muted, #8b7d6b);
  opacity: 0.6; transition: opacity 0.15s; box-shadow: var(--shadow-sm);
}
.tb-show-btn:hover { opacity: 1; color: var(--accent-terracotta); }
.tb-show-btn svg { width: 14px; height: 14px; }

.clip-path-pin {
  clip-path: polygon(50% 0%, 100% 100%, 0% 100%);
}

@media (max-width: 768px) {
  .graph-toolbar { flex-wrap: wrap; gap: 2px; padding: 6px 8px; justify-content: space-between; }
  .graph-toolbar > span { order: 1; font-size: 11px; align-self: center; }
  .graph-toolbar > div:last-child { order: 1; margin-left: auto; }
  .graph-toolbar .flex-wrap { order: 2; display: grid; grid-template-columns: repeat(3, 1fr); gap: 3px; width: 100%; margin-top: 4px; }
  .graph-toolbar .flex-wrap label { justify-content: center; padding: 2px 4px; font-size: 11px; }
  .graph-container { min-height: 350px; }
}
</style>
