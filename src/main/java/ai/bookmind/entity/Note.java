package ai.bookmind.entity;

import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 笔记实体
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class Note {

    /** 笔记ID */
    private Long id;

    /** 用户ID */
    private Long userId;

    /** 书籍ID */
    private Long bookId;

    /** 章节ID */
    private Long chapterId;

    /** 引用原文 */
    private String quoteText;

    /** 笔记内容 */
    private String content;

    /** 笔记分类 */
    private String category;

    /** 关联向量ID（用于RAG检索） */
    private String vectorId;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /**
     * 笔记分类枚举
     */
    public enum Category {
        REVIEW("💬", "书评感悟"),
        QUESTION("❓", "疑问思考"),
        QUOTE("📌", "重点摘录"),
        ASSOCIATION("🔗", "联想关联"),
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
     * 获取分类的完整显示名称（带emoji）
     */
    public String getDisplayName() {
        for (Category c : Category.values()) {
            if (c.name().equalsIgnoreCase(this.category)) {
                return c.getEmoji() + " " + c.getName();
            }
        }
        return "📂 其他";
    }

    /**
     * 书籍笔记统计（非持久化）
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BookNoteStats {
        private Long bookId;
        private Integer totalNotes;
        private Map<String, Integer> categoryCounts;
    }
}
