import { createRouter, createWebHistory } from 'vue-router'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      name: 'home',
      component: () => import('@/views/Home.vue'),
      meta: { requiresAuth: true, title: '首页 - BookMind' },
    },
    {
      path: '/login',
      name: 'login',
      component: () => import('@/views/Login.vue'),
      meta: { requiresAuth: false, title: '登录 - BookMind' },
    },
    {
      path: '/register',
      name: 'register',
      component: () => import('@/views/Register.vue'),
      meta: { requiresAuth: false, title: '注册 - BookMind' },
    },
    {
      path: '/upload',
      name: 'upload',
      component: () => import('@/views/Upload.vue'),
      meta: { requiresAuth: true, title: '上传书籍 - BookMind' },
    },
    {
      path: '/reader/:bookId',
      name: 'reader',
      component: () => import('@/views/Reader.vue'),
      meta: { requiresAuth: true, title: '阅读 - BookMind' },
      children: [
        {
          path: 'chapter/:chapterNumber',
          name: 'reader-chapter',
          component: () => import('@/components/ReaderLayout.vue'),
        },
      ],
    },
    {
      path: '/notes',
      name: 'notes',
      component: () => import('@/views/Notes.vue'),
      meta: { requiresAuth: true, title: '我的笔记 - BookMind' },
    },
    {
      path: '/chat',
      name: 'chat',
      component: () => import('@/views/Chat.vue'),
      meta: { requiresAuth: true, title: '对话 - BookMind' },
    },
    {
      path: '/graph',
      name: 'graph-list',
      component: () => import('@/views/GraphList.vue'),
      meta: { requiresAuth: true, title: '知识图谱 - BookMind' },
    },
    {
      path: '/graph/:bookId',
      name: 'graph',
      component: () => import('@/views/KnowledgeGraph.vue'),
      meta: { requiresAuth: true, title: '知识图谱 - BookMind' },
    },
    {
      path: '/search',
      name: 'search',
      component: () => import('@/views/SearchResults.vue'),
      meta: { requiresAuth: true, title: '搜索结果 - BookMind' },
    },
    {
      path: '/settings',
      name: 'settings',
      component: () => import('@/views/Settings.vue'),
      meta: { requiresAuth: true, title: '设置 - BookMind' },
    },
    {
      path: '/:pathMatch(.*)*',
      name: 'not-found',
      component: () => import('@/views/NotFound.vue'),
      meta: { requiresAuth: false, title: '404 - BookMind' },
    },
  ],
})

// 路由守卫
router.beforeEach((to, from, next) => {
  // 设置页面标题
  document.title = to.meta.title || 'BookMind'

  // 检查认证
  const token = localStorage.getItem('token')
  
  if (to.meta.requiresAuth && !token) {
    // 需要登录但未登录，跳转到登录页
    next({ name: 'login', query: { redirect: to.fullPath } })
  } else if ((to.name === 'login' || to.name === 'register') && token) {
    // 已登录但访问登录/注册页，跳转到首页
    next({ name: 'home' })
  } else {
    next()
  }
})

export default router
