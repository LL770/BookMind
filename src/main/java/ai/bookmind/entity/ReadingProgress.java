package ai.bookmind.entity;

import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

/**
 * 阅读进度（存储在Redis中）
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ReadingProgress {

    /** 用户ID */
    private Long userId;

    /** 书籍ID */
    private Long bookId;

    /** 当前章节ID */
    private Long currentChapterId;

    /** 当前章节序号 */
    private Integer currentChapterNumber;

    /** 当前页码 */
    private Integer currentPage;

    /** 总页数 */
    private Integer totalPages;

    /** 阅读进度百分比（0-100） */
    private Integer progressPercent;

    /** 累计阅读时长（秒） */
    private Long readDurationSeconds;

    /** 最后阅读时间 */
    private java.time.LocalDateTime lastReadTime;

    /**
     * Redis Key 模板
     */
    public static String getProgressKey(Long userId, Long bookId) {
        return "bookmind:reading:progress:" + userId + ":" + bookId;
    }

    /**
     * 计算进度百分比
     */
    public void calculateProgress() {
        if (totalPages > 0 && currentPage > 0) {
            this.progressPercent = (int) Math.round((double) currentPage / totalPages * 100);
        }
    }

    /**
     * 是否已完成阅读
     */
    public boolean isCompleted() {
        return currentPage >= totalPages;
    }

    /**
     * 格式化阅读时长
     */
    public String getReadDurationFormatted() {
        long hours = readDurationSeconds / 3600;
        long minutes = (readDurationSeconds % 3600) / 60;
        if (hours > 0) {
            return hours + "小时" + (minutes > 0 ? minutes + "分钟" : "");
        }
        return minutes + "分钟";
    }
}
