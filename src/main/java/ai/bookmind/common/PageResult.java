package ai.bookmind.common;

import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 分页响应封装
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class PageResult<T> {

    /** 当前页码 */
    private Integer page;

    /** 每页大小 */
    private Integer size;

    /** 总记录数 */
    private Long total;

    /** 总页数 */
    private Integer pages;

    /** 数据列表 */
    private List<T> records;

    public static <T> PageResult<T> of(Integer page, Integer size, Long total, List<T> records) {
        PageResult<T> result = new PageResult<>();
        result.setPage(page);
        result.setSize(size);
        result.setTotal(total);
        result.setPages((int) Math.ceil((double) total / size));
        result.setRecords(records);
        return result;
    }

    public static <T> PageResult<T> empty(Integer page, Integer size) {
        return of(page, size, 0L, List.of());
    }
}
