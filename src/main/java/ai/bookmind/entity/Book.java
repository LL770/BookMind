package ai.bookmind.entity;

import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 书籍实体
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class Book {

    /** 书籍ID */
    private Long id;

    /** 用户ID（多用户隔离） */
    private Long userId;

    /** 书名 */
    private String title;

    /** 作者 */
    private String author;

    /** 分类：小说/技术/历史/科普/商业/艺术/生活/其他 */
    private String category;

    /** 封面URL（MinIO存储） */
    private String coverUrl;

    /** 原始文件URL（MinIO存储） */
    private String fileUrl;

    /** 文件大小（字节） */
    private Long fileSize;

    /** 文件格式：pdf/docx/txt/epub */
    private String format;

    /** 文件SHA256 hash，用于同书识别共享向量库和知识图谱 */
    private String fileHash;

    /** 总页数 */
    private Integer totalPages;

    /** 总字数 */
    private Integer totalWords;

    /** 处理状态：0-上传中 1-解析中 2-向量化中 3-已完成 4-失败 */
    private Integer status;

    /** 处理进度（0-100） */
    private Integer progress;

    /** 阅读进度 0-100（持久化字段，供 Redis 兜底） */
    private Integer readingProgress;

    /** 当前阅读位置：复合值 (章节号-1) + (滚动百分比/100) */
    private Double currentPage;

    /** 知识图谱是否已生成 0-未生成 1-已生成 */
    private Integer kgGenerated;

    /** 处理描述 */
    private String processMessage;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    // ==================== 以下字段非数据库字段，仅用于前端展示 ====================

    /** 笔记总数（非持久化字段） */
    private Integer totalNotes;

    /** 阅读进度百分比（非持久化字段） */
    private Integer readProgress;

    /**
     * 分类枚举
     */
    public enum Category {
        NOVEL("📖", "小说"),
        TECH("💻", "技术/编程"),
        HISTORY("📚", "历史/社科"),
        SCIENCE("🔬", "科普/科学"),
        BUSINESS("💼", "商业/经济"),
        ART("🎨", "艺术/文学"),
        LIFE("🧘", "生活/心理"),
        OTHER("📂", "其他");

        private final String emoji;
        private final String name;

        Category(String emoji, String name) {
            this.emoji = emoji;
            this.name = name;
        }

        public String getEmoji() { return emoji; }
        public String getName() { return name; }
    }

    /**
     * 处理状态枚举
     */
    public enum Status {
        UPLOADING(0, "上传中"),
        PARSING(1, "解析中"),
        EMBEDDING(2, "向量化中"),
        COMPLETED(3, "已完成"),
        FAILED(4, "失败");

        private final int code;
        private final String message;

        Status(int code, String message) {
            this.code = code;
            this.message = message;
        }

        public int getCode() { return code; }
        public String getMessage() { return message; }

        public static Status fromCode(int code) {
            for (Status s : values()) {
                if (s.code == code) return s;
            }
            return FAILED;
        }
    }

    /**
     * 是否处理完成
     */
    public boolean isReady() {
        return status == Status.COMPLETED.getCode();
    }

    /**
     * 是否正在处理
     */
    public boolean isProcessing() {
        return status < Status.COMPLETED.getCode();
    }

    /**
     * 阅读统计数据（非持久化）
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReadingStats {
        private Long totalBooks;
        private Long completedBooks;
        private Long totalNotes;
        private Long totalReadSeconds;
        private Double totalReadHours;
        private Integer consecutiveDays;
    }

    /**
     * 章节内容（非持久化）
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChapterContent {
        private Integer chapterNumber;
        private String title;
        private String content;
        private Integer totalPages;
        private Integer currentPage;
    }

    /**
     * 章节简要信息（非持久化，用于API返回）
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChapterInfo {
        private Long id;
        private Integer chapterNumber;
        private String title;
        private String vectorId;
    }
}
