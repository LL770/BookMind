package ai.bookmind.controller;

import ai.bookmind.annotation.LogOperation;
import ai.bookmind.common.PageResult;
import ai.bookmind.common.Result;
import ai.bookmind.entity.Book;
import ai.bookmind.service.BookUploadService;
import ai.bookmind.service.BookService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * 书籍控制器
 */
@RestController
@RequestMapping("/api/books")
@RequiredArgsConstructor
@Slf4j
public class BookController {

    private final BookService bookService;
    private final BookUploadService bookUploadService;

    /**
     * 上传书籍
     */
    @PostMapping("/upload")
    @LogOperation("上传书籍")
    public Result<Book> upload(
            HttpServletRequest request,
            @RequestParam MultipartFile file,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) String author,
            @RequestParam(required = false) String category) {
        
        Long userId = getUserIdFromRequest(request);
        if (userId == null) {
            return Result.unauthorized("未登录");
        }

        try {
            Book book = bookUploadService.uploadBook(userId, file, title, author, category);
            return Result.success(book);
        } catch (Exception e) {
            log.error("上传书籍失败", e);
            return Result.serverError("上传失败: " + e.getMessage());
        }
    }

    /**
     * 获取我的书房书籍列表
     */
    @GetMapping
    public Result<PageResult<Book>> list(
            HttpServletRequest request,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size,
            @RequestParam(required = false) String category) {
        
        Long userId = getUserIdFromRequest(request);
        if (userId == null) {
            return Result.unauthorized("未登录");
        }

        PageResult<Book> result = bookService.getUserBooks(userId, page, size, category);
        return Result.success(result);
    }

    /**
     * 获取书籍详情
     */
    @GetMapping("/{bookId}")
    public Result<Book> getById(HttpServletRequest request, @PathVariable Long bookId) {
        Long userId = getUserIdFromRequest(request);
        if (userId == null) {
            return Result.unauthorized("未登录");
        }

        Book book = bookService.getBookById(userId, bookId);
        if (book == null) {
            return Result.notFound("书籍不存在");
        }
        return Result.success(book);
    }

    /**
     * 获取书籍章节列表
     */
    @GetMapping("/{bookId}/chapters")
    public Result<List<ai.bookmind.entity.Chapter>> getChapters(HttpServletRequest request, @PathVariable Long bookId) {
        Long userId = getUserIdFromRequest(request);
        if (userId == null) {
            return Result.unauthorized("未登录");
        }

        List<ai.bookmind.entity.Chapter> chapters = bookService.getBookChapters(userId, bookId);
        return Result.success(chapters);
    }

    /**
     * 更新书籍封面
     */
    @PutMapping("/{bookId}/cover")
    public Result<Void> uploadCover(HttpServletRequest request, @PathVariable Long bookId, @RequestParam("file") MultipartFile file) {
        Long userId = getUserIdFromRequest(request);
        if (userId == null) {
            return Result.unauthorized("未登录");
        }
        try {
            bookUploadService.updateBookCover(userId, bookId, file);
            return Result.success();
        } catch (Exception e) {
            log.warn("封面上传失败", e);
            return Result.badRequest("封面上传失败: " + e.getMessage());
        }
    }

    /**
     * 更新书籍信息（书名/作者/分类）
     */
    @PutMapping("/{bookId}")
    public Result<Void> updateBook(HttpServletRequest request, @PathVariable Long bookId,
                                    @RequestBody Map<String, String> body) {
        Long userId = getUserIdFromRequest(request);
        if (userId == null) return Result.unauthorized("未登录");
        bookUploadService.updateBookInfo(bookId,
                body.getOrDefault("title", ""),
                body.getOrDefault("author", ""),
                body.getOrDefault("category", ""));
        return Result.success();
    }

    /**
     * 搜索书籍
     */
    @GetMapping("/search")
    public Result<PageResult<Book>> search(
            HttpServletRequest request,
            @RequestParam String keyword,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size) {
        
        Long userId = getUserIdFromRequest(request);
        if (userId == null) {
            return Result.unauthorized("未登录");
        }

        PageResult<Book> result = bookService.searchBooks(userId, keyword, page, size);
        return Result.success(result);
    }

    /**
     * 删除书籍
     */
    @DeleteMapping("/{bookId}")
    public Result<Void> delete(HttpServletRequest request, @PathVariable Long bookId) {
        Long userId = getUserIdFromRequest(request);
        if (userId == null) {
            return Result.unauthorized("未登录");
        }

        try {
            bookUploadService.deleteBook(userId, bookId);
            return Result.success();
        } catch (Exception e) {
            log.error("删除书籍失败", e);
            return Result.serverError("删除失败: " + e.getMessage());
        }
    }

    /**
     * 从请求中获取用户ID
     */
    private Long getUserIdFromRequest(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId != null) return userId;

        // 从Session获取
        Object sessionId = request.getSession(false) != null ? request.getSession(false).getAttribute("userId") : null;
        if (sessionId instanceof Long) {
            return (Long) sessionId;
        }

        return null;
    }
}
