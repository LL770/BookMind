package ai.bookmind.controller;

import ai.bookmind.common.Result;
import ai.bookmind.mapper.BookMapper;
import ai.bookmind.service.VectorizationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 内部管理接口 — 用于手动触发向量化等操作
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@Slf4j
public class AdminController {

    private final VectorizationService vectorizationService;
    private final BookMapper bookMapper;

    /**
     * 手动触发书籍向量化（跳过解析，直接用已有章节）
     */
    @PostMapping("/vectorize/{bookId}")
    public Result<String> vectorizeBook(@PathVariable Long bookId) {
        try {
            var book = bookMapper.selectById(bookId);
            if (book == null) {
                return Result.error("书籍不存在");
            }
            log.info("手动触发向量化: bookId={}, userId={}", bookId, book.getUserId());
            vectorizationService.vectorizeBook(book.getUserId(), bookId);
            return Result.success("向量化完成");
        } catch (Exception e) {
            log.error("手动向量化失败: bookId={}", bookId, e);
            return Result.error("向量化失败: " + e.getMessage());
        }
    }

    /**
     * 检查向量数据统计
     */
    @GetMapping("/vectorize/stats")
    public Result<java.util.Map<String, Object>> vectorStats() {
        try {
            var rest = new org.springframework.web.client.RestTemplate();
            var resp = rest.getForEntity("http://127.0.0.1:6333/collections", String.class);
            return Result.success(java.util.Map.of("collections", resp.getBody()));
        } catch (Exception e) {
            return Result.error("Qdrant 不可用: " + e.getMessage());
        }
    }
}
