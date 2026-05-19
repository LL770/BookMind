# BookMind - 智能阅读伴侣

基于 AI 的智能阅读平台，支持书籍管理、AI 对话、知识图谱、笔记标注等功能。

## 功能

- **书籍管理** — 上传（PDF/TXT/EPUB/DOCX/MD）、分类、搜索
- **AI 对话** — 基于 RAG 的书籍内容问答、自由对话
- **知识图谱** — 自动抽取书中实体关系，可视化展示
- **阅读器** — 多主题、字体调节、进度追踪
- **笔记标注** — 高亮、笔记、标注管理

## 技术栈

- **后端**: Spring Boot 3.3 + JDK 21 + MyBatis + RabbitMQ + MySQL + Redis + Qdrant
- **前端**: Vue 3 + Vite 5 + Pinia + Tailwind CSS 3
- **AI**: SiliconFlow API + SenseNova API + MCP 工具
- **存储**: MinIO（文件）+ Qdrant（向量）
- **部署**: Docker Compose + Cloudflare Tunnel

## 快速开始

### 前置条件

- JDK 21+
- Node.js 22+
- Docker + Docker Compose
- Maven

### 1. 启动基础设施

```bash
docker compose -f deploy/docker-compose.yml up -d
```

### 2. 配置环境变量

创建 `deploy/.env`：

```env
DB_PASSWORD=your_password
REDIS_PASSWORD=your_password
RABBIT_USERNAME=admin
RABBIT_PASSWORD=your_password
MINIO_ACCESS_KEY=admin
MINIO_SECRET_KEY=your_secret
SENSENOVA_API_KEY=your_key
SILICONFLOW_KEY_CHAT=your_key
SILICONFLOW_KEY_EMBED=your_key
```

### 3. 启动后端

```bash
export $(grep -v '^#' deploy/.env | xargs)
mvn spring-boot:run -Dspring-boot.run.arguments="--server.port=8080"
```

### 4. 启动前端

```bash
cd frontend
npm install
npm run dev
```

访问 `http://localhost:3000`

### 生产部署

```bash
# 构建前端
cd frontend && npm run build

# 使用静态服务器
node serve.js

# Cloudflare Tunnel
cloudflared tunnel --config config.yml run
```

## 服务端口

| 服务 | 端口 |
|------|------|
| 前端 (Vite) | 3000 |
| 后端 API | 8080 |
| MySQL | 3307 |
| Redis | 6379 |
| RabbitMQ | 5672 / 15672 |
| MinIO | 9000 / 9001 |
| Qdrant | 6333 / 6334 |
