package ai.bookmind.controller;

import ai.bookmind.common.PageResult;
import ai.bookmind.common.Result;
import ai.bookmind.entity.Note;
import ai.bookmind.service.NoteService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 笔记控制器
 */
@RestController
@RequestMapping("/api/notes")
@RequiredArgsConstructor
@Slf4j
public class NoteController {

    private final NoteService noteService;

    /**
     * 创建笔记
     */
    @PostMapping
    public Result<Note> create(
            HttpServletRequest request,
            @RequestParam Long bookId,
            @RequestParam(required = false) Long chapterId,
            @RequestParam String quoteText,
            @RequestParam String content,
            @RequestParam(defaultValue = "review") String category) {
        
        Long userId = getUserIdFromRequest(request);
        if (userId == null) {
            return Result.unauthorized("未登录");
        }

        Note note = noteService.createNote(userId, bookId, chapterId, quoteText, content, category);
        return Result.success(note);
    }

    /**
     * 获取笔记列表
     */
    @GetMapping
    public Result<PageResult<Note>> list(
            HttpServletRequest request,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size,
            @RequestParam(required = false) Long bookId,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String keyword) {
        
        Long userId = getUserIdFromRequest(request);
        if (userId == null) {
            return Result.unauthorized("未登录");
        }

        PageResult<Note> result = noteService.getUserNotes(userId, page, size, bookId, category, keyword);
        return Result.success(result);
    }

    /**
     * 获取笔记详情
     */
    @GetMapping("/{noteId}")
    public Result<Note> getById(HttpServletRequest request, @PathVariable Long noteId) {
        Long userId = getUserIdFromRequest(request);
        if (userId == null) {
            return Result.unauthorized("未登录");
        }

        Note note = noteService.getNoteById(userId, noteId);
        if (note == null) {
            return Result.notFound("笔记不存在");
        }
        return Result.success(note);
    }

    /**
     * 更新笔记
     */
    @PutMapping("/{noteId}")
    public Result<Void> update(
            HttpServletRequest request,
            @PathVariable Long noteId,
            @RequestParam String content,
            @RequestParam String category) {
        
        Long userId = getUserIdFromRequest(request);
        if (userId == null) {
            return Result.unauthorized("未登录");
        }

        boolean success = noteService.updateNote(userId, noteId, content, category);
        if (!success) {
            return Result.forbidden("无权修改此笔记");
        }
        return Result.success();
    }

    /**
     * 删除笔记
     */
    @DeleteMapping("/{noteId}")
    public Result<Void> delete(HttpServletRequest request, @PathVariable Long noteId) {
        Long userId = getUserIdFromRequest(request);
        if (userId == null) {
            return Result.unauthorized("未登录");
        }

        boolean success = noteService.deleteNote(userId, noteId);
        if (!success) {
            return Result.forbidden("无权删除此笔记");
        }
        return Result.success();
    }

    /**
     * 批量删除笔记
     */
    @DeleteMapping("/batch")
    public Result<Void> deleteBatch(HttpServletRequest request, @RequestParam String ids) {
        Long userId = getUserIdFromRequest(request);
        if (userId == null) {
            return Result.unauthorized("未登录");
        }

        List<Long> noteIds = Arrays.stream(ids.split(","))
                .map(String::trim).map(Long::parseLong).collect(Collectors.toList());
        noteService.deleteBatch(userId, noteIds);
        return Result.success();
    }

    /**
     * 获取书籍笔记统计
     */
    @GetMapping("/stats/{bookId}")
    public Result<Note.BookNoteStats> getBookStats(
            HttpServletRequest request,
            @PathVariable Long bookId) {
        
        Long userId = getUserIdFromRequest(request);
        if (userId == null) {
            return Result.unauthorized("未登录");
        }

        Note.BookNoteStats stats = noteService.getBookNoteStats(userId, bookId);
        return Result.success(stats);
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
