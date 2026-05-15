package ai.bookmind.controller;

import ai.bookmind.common.Result;
import ai.bookmind.entity.BookCategory;
import ai.bookmind.service.BookCategoryService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/categories")
@RequiredArgsConstructor
@Slf4j
public class BookCategoryController {

    private final BookCategoryService bookCategoryService;

    /**
     * 获取用户全部分类
     */
    @GetMapping
    public Result<List<BookCategory>> getAll(HttpServletRequest request) {
        Long userId = getUserId(request);
        if (userId == null) return Result.unauthorized("未登录");
        bookCategoryService.initBuiltinCategories(userId);
        List<BookCategory> list = bookCategoryService.getAllCategories(userId);
        return Result.success(list);
    }

    /**
     * 创建自定义分类
     */
    @PostMapping
    public Result<BookCategory> create(
            HttpServletRequest request,
            @RequestParam String name,
            @RequestParam(defaultValue = "📖") String emoji) {
        Long userId = getUserId(request);
        if (userId == null) return Result.unauthorized("未登录");
        BookCategory cat = bookCategoryService.create(userId, name, emoji);
        return Result.success(cat);
    }

    /**
     * 更新分类
     */
    @PutMapping("/{categoryId}")
    public Result<Void> update(
            HttpServletRequest request,
            @PathVariable Long categoryId,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String emoji) {
        Long userId = getUserId(request);
        if (userId == null) return Result.unauthorized("未登录");
        boolean ok = bookCategoryService.update(userId, categoryId, name, emoji);
        if (!ok) return Result.forbidden("无权修改");
        return Result.success();
    }

    /**
     * 删除自定义分类
     */
    @DeleteMapping("/{categoryId}")
    public Result<Void> delete(HttpServletRequest request, @PathVariable Long categoryId) {
        Long userId = getUserId(request);
        if (userId == null) return Result.unauthorized("未登录");
        boolean ok = bookCategoryService.delete(userId, categoryId);
        if (!ok) return Result.forbidden("无权删除");
        return Result.success();
    }

    /**
     * 重新排序
     */
    @PutMapping("/reorder")
    public Result<Void> reorder(HttpServletRequest request, @RequestBody List<Long> categoryIds) {
        Long userId = getUserId(request);
        if (userId == null) return Result.unauthorized("未登录");
        bookCategoryService.reorder(userId, categoryIds);
        return Result.success();
    }

    private Long getUserId(HttpServletRequest request) {
        return (Long) request.getAttribute("userId");
    }
}
