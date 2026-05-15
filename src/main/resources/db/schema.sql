-- ============================================
-- BookMind 数据库初始化脚本
-- MySQL 8.0+
-- ============================================

-- 创建数据库
CREATE DATABASE IF NOT EXISTS bookmind 
DEFAULT CHARACTER SET utf8mb4 
COLLATE utf8mb4_unicode_ci;

USE bookmind;

-- ============================================
-- 用户表
-- ============================================
CREATE TABLE IF NOT EXISTS `user` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
    `username` VARCHAR(50) NOT NULL UNIQUE COMMENT '用户名',
    `password` VARCHAR(255) NOT NULL COMMENT 'BCrypt加密密码',
    `phone` VARCHAR(20) DEFAULT NULL COMMENT '手机号',
    `avatar` VARCHAR(500) DEFAULT NULL COMMENT '头像URL',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `last_login_time` DATETIME DEFAULT NULL COMMENT '最后登录时间',
    `status` TINYINT NOT NULL DEFAULT 0 COMMENT '状态: 0-正常 1-禁用',
    INDEX `idx_username` (`username`),
    INDEX `idx_phone` (`phone`),
    INDEX `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';

-- ============================================
-- 书籍表
-- ============================================
CREATE TABLE IF NOT EXISTS `book` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `title` VARCHAR(200) NOT NULL COMMENT '书名',
    `author` VARCHAR(100) DEFAULT NULL COMMENT '作者',
    `category` VARCHAR(50) NOT NULL COMMENT '分类',
    `cover_url` VARCHAR(500) DEFAULT NULL COMMENT '封面URL',
    `file_url` VARCHAR(500) NOT NULL COMMENT '文件URL',
    `file_size` BIGINT DEFAULT NULL COMMENT '文件大小(字节)',
    `format` VARCHAR(20) NOT NULL COMMENT '文件格式',
    `file_hash` VARCHAR(64) DEFAULT NULL COMMENT '文件SHA256 hash，用于同书识别',
    `total_pages` INT DEFAULT NULL COMMENT '总页数',
    `total_words` INT DEFAULT NULL COMMENT '总字数',
    `status` TINYINT NOT NULL DEFAULT 0 COMMENT '状态: 0-上传中 1-解析中 2-向量化中 3-已完成 4-失败',
    `progress` INT DEFAULT 0 COMMENT '处理进度(0-100)',
    `reading_progress` INT DEFAULT 0 COMMENT '阅读进度百分比(0-100)',
    `current_chapter` INT DEFAULT 1 COMMENT '当前阅读章节号',
    `current_page` DECIMAL(10,2) DEFAULT 0.00 COMMENT '当前阅读位置: (章节号-1)+(章内滚动百分比/100)',
    `kg_generated` TINYINT NOT NULL DEFAULT 0 COMMENT '知识图谱已生成: 0-否 1-是',
    `process_message` VARCHAR(500) DEFAULT NULL COMMENT '处理描述',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX `idx_user_id` (`user_id`),
    INDEX `idx_category` (`category`),
    INDEX `idx_status` (`status`),
    INDEX `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='书籍表';

-- ============================================
-- 章节表
-- ============================================
CREATE TABLE IF NOT EXISTS `chapter` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
    `book_id` BIGINT NOT NULL COMMENT '书籍ID',
    `chapter_number` INT NOT NULL COMMENT '章节序号',
    `title` VARCHAR(200) NOT NULL COMMENT '章节标题',
    `content` MEDIUMTEXT COMMENT '章节内容（MEDIUMTEXT 支持 16MB，避免长章节截断）',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX `idx_book_id` (`book_id`),
    INDEX `idx_chapter_number` (`book_id`, `chapter_number`),
    FULLTEXT INDEX `ft_content` (`content`) WITH PARSER ngram
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='章节表';

-- ============================================
-- 笔记表
-- ============================================
CREATE TABLE IF NOT EXISTS `note` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `book_id` BIGINT NOT NULL COMMENT '书籍ID',
    `chapter_id` BIGINT DEFAULT NULL COMMENT '章节ID',
    `quote_text` VARCHAR(2000) NOT NULL COMMENT '引用原文',
    `content` TEXT NOT NULL COMMENT '笔记内容',
    `category` VARCHAR(20) NOT NULL COMMENT '分类: review/question/quote/association/other',
    `vector_id` VARCHAR(100) DEFAULT NULL COMMENT 'Qdrant向量ID',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX `idx_user_id` (`user_id`),
    INDEX `idx_book_id` (`book_id`),
    INDEX `idx_category` (`category`),
    INDEX `idx_vector_id` (`vector_id`),
    INDEX `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='笔记表';

-- ============================================
-- 知识图谱节点表
-- ============================================
CREATE TABLE IF NOT EXISTS `kg_node` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
    `user_id` BIGINT NOT NULL COMMENT '用户 ID',
    `book_id` BIGINT NOT NULL COMMENT '书籍 ID',
    `name` VARCHAR(100) NOT NULL COMMENT '节点名称',
    `type` VARCHAR(20) NOT NULL COMMENT '节点类型：person/organization/location/concept/event',
    `description` VARCHAR(1000) DEFAULT NULL COMMENT '节点描述',
    `first_chapter` INT DEFAULT NULL COMMENT '首次出现章节',
    `occurrence_count` INT DEFAULT 1 COMMENT '出现次数',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX `idx_user_id` (`user_id`),
    INDEX `idx_book_id` (`book_id`),
    INDEX `idx_type` (`type`),
    INDEX `idx_name` (`name`),
    -- 唯一约束：同一本书中，同名同类型的节点只保留一个
    UNIQUE INDEX `idx_book_name_type` (`book_id`, `name`, `type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='知识图谱节点表';

-- ============================================
-- 知识图谱边表
-- ============================================
CREATE TABLE IF NOT EXISTS `kg_edge` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `book_id` BIGINT NOT NULL COMMENT '书籍ID',
    `source_node_id` BIGINT NOT NULL COMMENT '源节点ID',
    `target_node_id` BIGINT NOT NULL COMMENT '目标节点ID',
    `relation` VARCHAR(50) NOT NULL COMMENT '关系类型',
    `description` VARCHAR(500) DEFAULT NULL COMMENT '关系描述',
    `weight` DOUBLE DEFAULT 1.0 COMMENT '权重',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX `idx_user_id` (`user_id`),
    INDEX `idx_book_id` (`book_id`),
    INDEX `idx_source` (`source_node_id`),
    INDEX `idx_target` (`target_node_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='知识图谱边表';

-- ============================================
-- 阅读统计表已移除，改用 Redis 存储
-- 原因：
-- 1. 统计数据是易失性缓存，不需要持久化到 MySQL
-- 2. Redis Hash 结构更适合存储键值对统计
-- 3. 支持自动过期，减少数据库维护成本
-- 4. 面试亮点：说明为什么用 Redis 而不是 MySQL 做统计缓存
--
-- Redis Key 设计:
--   user:stats:{userId} → Hash {
--       totalBooks: 3,
--       completedBooks: 1,
--       totalNotes: 128,
--       totalBookmarks: 8,
--       totalReadSeconds: 54000,
--       consecutiveDays: 7,
--       lastReadDate: 2026-05-06
--   }
-- ============================================

-- ============================================
-- 分片上传会话表
-- ============================================
CREATE TABLE IF NOT EXISTS `upload_session` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
    `upload_id` VARCHAR(64) NOT NULL UNIQUE COMMENT '上传唯一标识',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `file_name` VARCHAR(255) NOT NULL COMMENT '文件名',
    `file_size` BIGINT NOT NULL COMMENT '文件大小(字节)',
    `chunk_size` INT NOT NULL COMMENT '分片大小(字节)',
    `total_chunks` INT NOT NULL COMMENT '总分片数',
    `received_chunks` INT DEFAULT 0 COMMENT '已接收分片数',
    `status` TINYINT DEFAULT 0 COMMENT '状态: 0-上传中 1-已完成 2-已合并 3-失败',
    `merged_book_id` BIGINT DEFAULT NULL COMMENT '合并后的书籍ID',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX `idx_upload_id` (`upload_id`),
    INDEX `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='分片上传会话表';

-- ============================================
-- 分类表（数据库存储，支持多设备同步）
-- ============================================
CREATE TABLE IF NOT EXISTS `book_category` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `name` VARCHAR(50) NOT NULL COMMENT '分类名称',
    `emoji` VARCHAR(20) DEFAULT '📖' COMMENT '分类图标',
    `builtin` TINYINT NOT NULL DEFAULT 0 COMMENT '是否内置分类: 0-用户自定义 1-系统内置',
    `sort_order` INT DEFAULT 0 COMMENT '排序序号',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX `idx_user_id` (`user_id`),
    INDEX `idx_builtin` (`builtin`),
    INDEX `idx_sort_order` (`sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户分类表';

-- ============================================
-- 初始化管理员账号（可选）
-- ============================================
-- 密码：admin123 (BCrypt 加密后)
-- INSERT INTO `user` (username, password, phone, status, create_time, last_login_time) 
-- VALUES ('admin', '$2a$10$...', '13800138000', 0, NOW(), NOW());
