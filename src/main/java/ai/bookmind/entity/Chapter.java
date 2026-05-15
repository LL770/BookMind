package ai.bookmind.entity;

import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

/**
 * 章节实体
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class Chapter {

    /** 章节ID */
    private Long id;

    /** 书籍ID */
    private Long bookId;

    /** 章节序号 */
    private Integer chapterNumber;

    /** 章节标题 */
    private String title;

    /** 章节内容（文本） */
    private String content;

    /** 内容向量ID（Qdrant中的vectorId） */
    private String vectorId;

    /** 起始位置（字符偏移） */
    private Integer startOffset;

    /** 结束位置（字符偏移） */
    private Integer endOffset;

    /** 创建时间 */
    private java.time.LocalDateTime createTime;
}
