# BookMind Frontend - 智能阅读伴侣前端

基于 Vue 3 + Vite + Tailwind CSS 构建的智能阅读前端应用。

## 技术栈

| 技术 | 版本 | 说明 |
|------|------|------|
| Vue 3 | ^3.4.0 | 组合式 API |
| Vite | ^5.0.0 | 构建工具 |
| Vue Router | ^4.2.0 | 路由管理 |
| Pinia | ^2.1.0 | 状态管理 |
| Tailwind CSS | ^3.4.0 | 原子化 CSS |
| Axios | ^1.6.0 | HTTP 客户端 |
| ECharts | ^5.5.0 | 知识图谱可视化 |
| SSE | - | Server-Sent Events 流式输出 |

## 项目结构

```
frontend/
├── src/
│   ├── api/                    # API 模块
│   │   ├── request.js          # axios 封装
│   │   └── index.js            # 所有 API 接口
│   ├── assets/                 # 静态资源
│   ├── components/             # 公共组件
│   │   ├── BookCard.vue        # 书籍卡片
│   │   ├── ChatPanel.vue       # AI 对话面板
│   │   ├── ReaderLayout.vue    # 阅读器布局
│   │   ├── SearchBar.vue       # 搜索框
│   │   └── SelectionMenu.vue   # 划选菜单
│   ├── router/                 # 路由配置
│   │   └── index.js
│   ├── stores/                 # Pinia 状态管理
│   │   └── index.js            # user, book, reader, search
│   ├── styles/                 # 全局样式
│   │   └── main.css            # Tailwind + 自定义样式
│   ├── utils/                  # 工具函数
│   │   └── index.js
│   ├── views/                  # 页面组件
│   │   ├── Home.vue            # 首页
│   │   ├── Login.vue           # 登录页
│   │   ├── Register.vue        # 注册页
│   │   ├── Upload.vue          # 上传页
│   │   ├── Reader.vue          # 阅读器入口
│   │   ├── Notes.vue           # 笔记管理
│   │   ├── Settings.vue        # 设置页
│   │   ├── SearchResults.vue   # 搜索结果
│   │   ├── KnowledgeGraph.vue  # 知识图谱
│   │   └── NotFound.vue        # 404 页
│   ├── App.vue                 # 根组件
│   └── main.js                 # 入口文件
├── public/                     # 公共资源
├── index.html
├── vite.config.js
├── tailwind.config.js
├── postcss.config.js
├── .env
└── package.json
```

## 快速启动

```bash
# 安装依赖
npm install

# 开发模式
npm run dev

# 生产构建
npm run build

# 预览生产构建
npm run preview
```

## 核心功能

### 1. 用户系统
- 注册/登录（JWT 认证）
- 用户名实时校验
- 密码强度检测
- 账号设置（改密码/注销）

### 2. 书籍管理
- 拖拽上传（支持 PDF/Word/TXT/EPUB）
- 上传进度显示
- 分类筛选
- 书籍搜索

### 3. 阅读器
- 章节导航
- 阅读进度自动保存
- 翻页（键盘左右键）
- 全屏阅读
- 书签标记（6 种颜色）
- 划选写笔记（5 种分类）

### 4. AI 对话
- SSE 流式输出
- 书籍内 RAG 检索
- 全局搜索（书籍 + 笔记）
- 来源标记（📖本书 / 📝笔记 / 🌐网络）
- 章节/全书摘要生成

### 5. 知识图谱
- ECharts 力引导图
- 5 种节点类型（人物/组织/地点/概念/事件）
- 节点悬停预览
- 点击高亮关联
- 跳转原文定位

### 6. 搜索系统
- 全局搜索（防抖 300ms）
- 两阶段检索（向量粗召回 + BGE 重排序）
- 结果分栏（📖书中 / 📝笔记）
- 关键词高亮

## 响应式设计

| 断点 | 宽度 | 布局 |
|------|------|------|
| 移动端 | < 640px | 单列，底部工具栏 |
| 平板 | 640-1024px | 2 列 |
| 桌面小屏 | 1024-1280px | 3 列 |
| 桌面大屏 | > 1280px | 4 列 |

## API 接口说明

所有 API 接口文档详见后端 `README.md`。

### 认证头
```
Authorization: Bearer <token>
```

### 错误处理
统一返回格式：
```json
{
  "code": 0,
  "message": "success",
  "data": {}
}
```

- `code === 0`: 成功
- `code === 401`: 未授权（自动跳转登录）
- `code === 403`: 无权限
- `code === 404`: 资源不存在
- `code === 429`: 请求过于频繁
- `code === 500`: 服务器错误

## SSE 流式输出

AI 对话使用 SSE（Server-Sent Events）实现流式输出：

```javascript
const eventSource = new EventSource(
  `/api/chat/book/${bookId}?message=${encodeURIComponent(message)}`
)

eventSource.onmessage = (event) => {
  fullContent += event.data
  // 更新 UI
}

eventSource.onerror = () => {
  eventSource.close()
  // 处理错误
}
```

## 状态管理（Pinia）

### user store
- `user`: 当前用户信息
- `token`: JWT 令牌
- `isAuthenticated`: 是否已登录
- `login()`, `logout()`, `loadUser()`

### book store
- `bookList`: 书籍列表
- `currentBook`: 当前书籍
- `loading`: 加载状态
- `loadBookList()`, `uploadBook()`, `deleteBook()`

### reader store
- `chapter`: 当前章节
- `bookmarks`: 书签列表
- `notes`: 笔记列表
- `loadChapter()`, `addBookmark()`, `addNote()`

### search store
- `searchResults`: 搜索结果
- `searching`: 搜索中状态
- `search()`, `clearResults()`

## 样式约定

### 颜色变量（Tailwind）
```css
--color-bookmind-primary: #6366f1;
--color-bookmind-secondary: #8b5cf6;
--color-bookmind-accent: #06b6d4;
--color-bookmind-success: #10b981;
--color-bookmind-warning: #f59e0b;
--color-bookmind-danger: #ef4444;
```

### 组件类名约定
```css
.btn              /* 基础按钮 */
.btn-primary      /* 主按钮 */
.btn-secondary    /* 次按钮 */
.btn-outline      /* 边框按钮 */
.btn-danger       /* 危险按钮 */
.btn-sm           /* 小按钮 */
.btn-lg           /* 大按钮 */

.input            /* 输入框 */
.input-error      /* 错误状态 */

.card             /* 卡片 */
.card-hover       /* 悬停效果 */

.skeleton         /* 骨架屏 */
.skeleton-text    /* 文本骨架 */
```

## 环境变量

| 变量 | 说明 | 默认值 |
|------|------|--------|
| `VITE_API_BASE_URL` | API 基础 URL | `http://localhost:8080/api` |
| `VITE_APP_NAME` | 应用名称 | `BookMind` |
| `VITE_DEBUG` | 调试模式 | `false` |
| `VITE_SSE_TIMEOUT` | SSE 超时 | `300000` |
| `VITE_MAX_FILE_SIZE` | 最大文件大小 | `100` |

## 开发规范

### 组件命名
- 文件名：`PascalCase.vue`（如 `BookCard.vue`）
- 组件名：与文件名一致

### 事件命名
- 自定义事件：`kebab-case`（如 `@create-note`）
- 方法名：`camelCase`（如 `handleCreateNote`）

### Props 定义
```javascript
const props = defineProps({
  bookId: { type: Number, required: true },
  title: { type: String, default: '' },
  size: { type: String, default: 'md', validator: (v) => ['sm', 'md', 'lg'].includes(v) }
})
```

### Emits 定义
```javascript
const emit = defineEmits(['click', 'preview', 'delete'])
```

## 性能优化

1. **路由懒加载**
```javascript
{
  path: '/reader/:bookId',
  component: () => import('@/views/Reader.vue')
}
```

2. **组件懒加载**
```javascript
const ChatPanel = defineAsyncComponent(() => import('@/components/ChatPanel.vue'))
```

3. **图片懒加载**
```html
<img loading="lazy" src="..." />
```

4. **防抖/节流**
```javascript
import { debounce } from '@/utils'
const handleSearch = debounce((q) => { ... }, 300)
```

## 浏览器支持

- Chrome ≥ 87
- Firefox ≥ 78
- Safari ≥ 14
- Edge ≥ 88

## License

MIT License
