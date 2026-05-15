package ai.bookmind.entity;

import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 知识图谱边实体
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class KnowledgeEdge {

    /** 边ID */
    private Long id;

    /** 用户ID */
    private Long userId;

    /** 书籍ID */
    private Long bookId;

    /** 源节点ID */
    private Long sourceNodeId;

    /** 目标节点ID */
    private Long targetNodeId;

    /** 关系类型 */
    private String relation;

    /** 关系描述 */
    private String description;

    /** 权重（用于力引导图） */
    private Double weight;

    /** 创建时间 */
    private LocalDateTime createTime;

    /**
     * 常见关系类型
     */
    public enum RelationType {
        CREATED("创建"),
        MEMBER_OF("成员"),
        LEADS("领导"),
        FIGHTS("对抗"),
        PROTECTS("保护"),
        SUCCEEDS("接替"),
        LOCATED_AT("位于"),
        OCCURS_AT("发生于"),
        RELATED_TO("关联"),
        CAUSES("导致");

        private final String label;

        RelationType(String label) {
            this.label = label;
        }

        public String getLabel() { return label; }
    }

    /**
     * 获取ECharts渲染用的边样式
     */
    public String getLineStyle() {
        // 根据关系类型返回不同样式
        return switch (relation) {
            case "created", "leads" -> "solid";
            case "member_of" -> "dashed";
            case "fights", "causes" -> "solid";
            default -> "solid";
        };
    }

    /**
     * 获取边颜色
     */
    public String getColor() {
        return switch (relation) {
            case "created", "leads", "succeeds" -> "#2196F3";
            case "fights", "causes" -> "#F44336";
            case "protects" -> "#4CAF50";
            default -> "#9E9E9E";
        };
    }
}
