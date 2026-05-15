package ai.bookmind.controller;

import ai.bookmind.common.Result;
import ai.bookmind.entity.Note;
import ai.bookmind.service.SearchService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 全局搜索控制器
 */
@RestController
@RequestMapping("/api/search")
@RequiredArgsConstructor
@Slf4j
public class SearchController {

    private final SearchService searchService;

    /**
     * 搜索（可指定 bookId 筛选到书内搜索）
     */
    @GetMapping
    public Result<Map<String, Object>> search(
            HttpServletRequest request,
            @RequestParam String q,
            @RequestParam(defaultValue = "5") Integer topK,
            @RequestParam(required = false) Long bookId) {

        Long userId = getUserIdFromRequest(request);
        if (userId == null) {
            return Result.unauthorized("未登录");
        }

        Map<String, Object> results;
        if (bookId != null) {
            results = searchService.searchInBook(userId, bookId, q, topK);
        } else {
            results = searchService.globalSearch(userId, q, topK);
        }

        // 兼容前端 records 格式
        List<Map<String, Object>> records = new java.util.ArrayList<>();
        if (results.containsKey("bookContent")) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> bc = (List<Map<String, Object>>) results.get("bookContent");
            records.addAll(bc);
        }
        if (results.containsKey("books")) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> bc = (List<Map<String, Object>>) results.get("books");
            records.addAll(bc);
        }
        results.put("records", records);

        return Result.success(results);
    }

    /**
     * 书籍内搜索
     */
    @GetMapping("/book/{bookId}")
    public Result<Map<String, Object>> searchInBook(
            HttpServletRequest request,
            @PathVariable Long bookId,
            @RequestParam String q,
            @RequestParam(defaultValue = "10") Integer topK) {
        
        Long userId = getUserIdFromRequest(request);
        if (userId == null) {
            return Result.unauthorized("未登录");
        }

        Map<String, Object> results = searchService.searchInBook(userId, bookId, q, topK);
        return Result.success(results);
    }

    /**
     * 笔记搜索
     */
    @GetMapping("/notes")
    public Result<List<Note>> searchNotes(
            HttpServletRequest request,
            @RequestParam String q,
            @RequestParam(defaultValue = "20") Integer limit) {
        
        Long userId = getUserIdFromRequest(request);
        if (userId == null) {
            return Result.unauthorized("未登录");
        }

        List<Note> notes = searchService.searchNotes(userId, q, limit);
        return Result.success(notes);
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
