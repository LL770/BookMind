package ai.bookmind.entity;

import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 知识图谱节点实体
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class KnowledgeNode {

    /** 节点ID */
    private Long id;

    /** 用户ID */
    private Long userId;

    /** 书籍ID */
    private Long bookId;

    /** 节点名称 */
    private String name;

    /** 节点类型：person/organization/location/concept/event */
    private String type;

    /** 节点描述 */
    private String description;

    /** 首次出现章节 */
    private Integer firstChapter;

    /** 出现次数 */
    private Integer occurrenceCount;

    /** 创建时间 */
    private LocalDateTime createTime;

    /**
     * 节点类型枚举
     */
    public enum Type {
        PERSON("🟢", "人物"),
        ORGANIZATION("🔵", "组织"),
        LOCATION("🟡", "地点"),
        CONCEPT("🟣", "概念"),
        EVENT("🔴", "事件");

        private final String emoji;
        private final String name;

        Type(String emoji, String name) {
            this.emoji = emoji;
            this.name = name;
        }

        public String getEmoji() { return emoji; }
        public String getName() { return name; }
    }

    /**
     * 获取ECharts渲染用的symbol形状
     */
    public String getSymbolShape() {
        return switch (type) {
            case "person" -> "circle";
            case "organization" -> "rectangle";
            case "location" -> "diamond";
            case "concept" -> "triangle";
            case "event" -> "pin";
            default -> "circle";
        };
    }

    /**
     * 获取ECharts渲染用的颜色
     */
    public String getSymbolColor() {
        return switch (type) {
            case "person" -> "#4CAF50";
            case "organization" -> "#2196F3";
            case "location" -> "#FFC107";
            case "concept" -> "#9C27B0";
            case "event" -> "#F44336";
            default -> "#9E9E9E";
        };
    }
}
