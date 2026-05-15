package ai.bookmind.controller;

import ai.bookmind.common.Result;
import ai.bookmind.entity.Book;
import ai.bookmind.entity.UploadSession;
import ai.bookmind.service.BookUploadService;
import ai.bookmind.service.UploadSessionService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/upload")
@RequiredArgsConstructor
@Slf4j
public class UploadSessionController {

    private final UploadSessionService uploadSessionService;
    private final BookUploadService bookUploadService;

    /**
     * 初始化上传会话
     */
    @PostMapping("/init")
    public Result<UploadSession> initSession(
            HttpServletRequest request,
            @RequestParam String fileName,
            @RequestParam Long fileSize) {
        Long userId = getUserId(request);
        if (userId == null) return Result.unauthorized("未登录");
        UploadSession session = uploadSessionService.initSession(userId, fileName, fileSize);
        return Result.success(session);
    }

    /**
     * 上传分片
     */
    @PostMapping("/{uploadId}/chunk")
    public Result<Void> uploadChunk(
            HttpServletRequest request,
            @PathVariable String uploadId,
            @RequestParam int chunkIndex,
            @RequestParam MultipartFile file) {
        Long userId = getUserId(request);
        if (userId == null) return Result.unauthorized("未登录");
        try {
            uploadSessionService.uploadChunk(uploadId, chunkIndex, file);
            return Result.success();
        } catch (Exception e) {
            log.error("分片上传失败: uploadId={}, chunk={}", uploadId, chunkIndex, e);
            return Result.serverError("分片上传失败: " + e.getMessage());
        }
    }

    /**
     * 检查分片状态（断点续传用）
     */
    @GetMapping("/{uploadId}/chunk/{chunkIndex}/status")
    public Result<Map<String, Object>> checkChunk(
            HttpServletRequest request,
            @PathVariable String uploadId,
            @PathVariable int chunkIndex) {
        Long userId = getUserId(request);
        if (userId == null) return Result.unauthorized("未登录");
        boolean uploaded = uploadSessionService.isChunkUploaded(uploadId, chunkIndex);
        return Result.success(Map.of("uploaded", uploaded));
    }

    /**
     * 合并分片 → 创建书籍 → 触发 MQ 异步处理
     */
    @PostMapping("/{uploadId}/complete")
    public Result<Book> completeUpload(
            HttpServletRequest request,
            @PathVariable String uploadId,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) String author,
            @RequestParam(required = false) String category) {
        Long userId = getUserId(request);
        if (userId == null) return Result.unauthorized("未登录");
        try {
            String fileUrl = uploadSessionService.mergeChunks(uploadId);
            UploadSession session = uploadSessionService.getSession(uploadId);

            // 创建书籍并触发 MQ 异步处理
            Book book = bookUploadService.createBookFromMergedFile(
                    userId, fileUrl, session.getFileName(),
                    title, author, category, session.getFileSize());

            // 更新 upload_session 关联
            uploadSessionService.getSession(uploadId); // refresh

            return Result.success(book);
        } catch (Exception e) {
            log.error("合并分片失败: uploadId={}", uploadId, e);
            return Result.serverError("合并失败: " + e.getMessage());
        }
    }

    /**
     * 获取上传会话状态
     */
    @GetMapping("/{uploadId}")
    public Result<UploadSession> getSession(
            HttpServletRequest request,
            @PathVariable String uploadId) {
        Long userId = getUserId(request);
        if (userId == null) return Result.unauthorized("未登录");
        UploadSession session = uploadSessionService.getSession(uploadId);
        if (session == null) return Result.notFound("会话不存在");
        return Result.success(session);
    }

    private Long getUserId(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId != null) return userId;
        Object sessionId = request.getSession(false) != null
                ? request.getSession(false).getAttribute("userId") : null;
        return sessionId instanceof Long ? (Long) sessionId : null;
    }
}
