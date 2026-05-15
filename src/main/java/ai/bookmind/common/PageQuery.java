package ai.bookmind.common;

import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * 分页请求参数
 */
@Data
@NoArgsConstructor
public class PageQuery {

    /** 当前页码，默认1 */
    @Min(1)
    private Integer page = 1;

    /** 每页大小，默认10，最大100 */
    @Min(1)
    @Max(100)
    private Integer size = 10;

    /** 排序字段 */
    private String orderBy;

    /** 排序方向：asc/desc，默认desc */
    private String orderDir = "desc";

    /**
     * 计算偏移量（用于MySQL LIMIT）
     */
    public Integer getOffset() {
        return (page - 1) * size;
    }

    /**
     * 设置默认值
     */
    public void setDefaults() {
        if (page == null || page < 1) page = 1;
        if (size == null || size < 1) size = 10;
        if (size > 100) size = 100;
        if (orderDir == null || (!"asc".equalsIgnoreCase(orderDir) && !"desc".equalsIgnoreCase(orderDir))) {
            orderDir = "desc";
        }
    }
}
