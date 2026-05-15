package ai.bookmind.controller;

import ai.bookmind.common.Result;
import ai.bookmind.entity.Book;
import ai.bookmind.entity.Chapter;
import ai.bookmind.mapper.BookMapper;
import ai.bookmind.mapper.ChapterMapper;
import ai.bookmind.service.ReaderService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.HashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 阅读器控制器
 */
@RestController
@RequestMapping("/api/reader")
@RequiredArgsConstructor
@Slf4j
public class ReaderController {

    private final ReaderService readerService;
    private final ChapterMapper chapterMapper;
    private final BookMapper bookMapper;

    /**
     * 获取书籍阅读信息（章节列表、进度等）
     */
    @GetMapping("/{bookId}")
    public Result<Map<String, Object>> getReaderInfo(
            HttpServletRequest request,
            @PathVariable Long bookId) {
        
        Long userId = getUserIdFromRequest(request);
        if (userId == null) {
            return Result.unauthorized("未登录");
        }

        Map<String, Object> info = readerService.getReaderInfo(userId, bookId);
        return Result.success(info);
    }

    /**
     * 获取章节内容
     */
    @GetMapping("/{bookId}/chapter/{chapterNumber}")
    public Result<Map<String, Object>> getChapter(
            HttpServletRequest request,
            @PathVariable Long bookId,
            @PathVariable Integer chapterNumber) {

        Long userId = getUserIdFromRequest(request);
        if (userId == null) {
            return Result.unauthorized("未登录");
        }

        Chapter chapter = readerService.getChapter(userId, bookId, chapterNumber);
        if (chapter == null) {
            return Result.notFound("章节不存在");
        }

        Map<String, Object> data = new HashMap<>();
        data.put("chapter", chapter);
        // 总章节数：优先 Book.totalPages（一次查询），其次 count 兜底
        int totalChaptersCount = 0;
        try {
            ai.bookmind.entity.Book b = bookMapper.selectById(bookId);
            if (b != null && b.getTotalPages() != null) totalChaptersCount = b.getTotalPages();
        } catch (Exception e) { /* 忽略 */ }
        if (totalChaptersCount == 0) {
            try {
                List<Chapter> allCh = chapterMapper.selectByBookId(bookId);
                totalChaptersCount = allCh != null ? allCh.size() : 0;
            } catch (Exception e) {
                log.warn("查询总章节数失败 bookId={}", bookId, e);
            }
        }
        data.put("totalChapters", totalChaptersCount);
        return Result.success(data);
    }

    /**
     * 更新阅读进度
     */
    @PutMapping("/{bookId}/progress")
    public Result<Void> updateProgress(
            HttpServletRequest request,
            @PathVariable Long bookId,
            @RequestParam Integer chapterNumber,
            @RequestParam Double currentPage,
            @RequestParam Integer totalPages) {
        
        Long userId = getUserIdFromRequest(request);
        if (userId == null) {
            return Result.unauthorized("未登录");
        }

        readerService.updateProgress(userId, bookId, chapterNumber, currentPage, totalPages);
        return Result.success();
    }

    /**
     * 获取阅读进度
     */
    @GetMapping("/{bookId}/progress")
    public Result<Map<String, Object>> getProgress(
            HttpServletRequest request,
            @PathVariable Long bookId) {
        
        Long userId = getUserIdFromRequest(request);
        if (userId == null) {
            return Result.unauthorized("未登录");
        }

        Map<String, Object> progress = readerService.getProgress(userId, bookId);
        return Result.success(progress);
    }

    /**
     * 获取上一章
     */
    @GetMapping("/{bookId}/chapter/prev")
    public Result<Map<String, Object>> getPreviousChapter(
            HttpServletRequest request,
            @PathVariable Long bookId,
            @RequestParam Integer currentChapter) {
        
        Long userId = getUserIdFromRequest(request);
        if (userId == null) {
            return Result.unauthorized("未登录");
        }

        Map<String, Object> prev = readerService.getPreviousChapter(userId, bookId, currentChapter);
        return Result.success(prev);
    }

    /**
     * 获取下一章
     */
    @GetMapping("/{bookId}/chapter/next")
    public Result<Map<String, Object>> getNextChapter(
            HttpServletRequest request,
            @PathVariable Long bookId,
            @RequestParam Integer currentChapter) {
        
        Long userId = getUserIdFromRequest(request);
        if (userId == null) {
            return Result.unauthorized("未登录");
        }

        Map<String, Object> next = readerService.getNextChapter(userId, bookId, currentChapter);
        return Result.success(next);
    }

    /**
     * 获取章节内的书签和笔记
     */
    @GetMapping("/{bookId}/chapter/{chapterNumber}/annotations")
    public Result<Map<String, Object>> getChapterAnnotations(
            HttpServletRequest request,
            @PathVariable Long bookId,
            @PathVariable Integer chapterNumber) {
        
        Long userId = getUserIdFromRequest(request);
        if (userId == null) {
            return Result.unauthorized("未登录");
        }

        Map<String, Object> annotations = readerService.getChapterAnnotations(userId, bookId, chapterNumber);
        return Result.success(annotations);
    }

    /**
     * 从请求中获取用户ID
     */
    private Long getUserIdFromRequest(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId != null) return userId;

        Object sessionId = request.getSession(false) != null ? request.getSession(false).getAttribute("userId") : null;
        if (sessionId instanceof Long) {
            return (Long) sessionId;
        }

        return null;
    }
}
