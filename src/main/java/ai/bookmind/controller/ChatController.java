package ai.bookmind.controller;

import ai.bookmind.ai.AiChatService;
import ai.bookmind.annotation.LogOperation;
import ai.bookmind.common.Result;
import ai.bookmind.service.KnowledgeGraphService;
import ai.bookmind.service.SummaryService;
import java.util.concurrent.CompletableFuture;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
@Slf4j
public class ChatController {

    private final AiChatService aiChatService;
    private final KnowledgeGraphService kgService;
    private final SummaryService summaryService;

    @GetMapping(value = "/book/{bookId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @LogOperation("书籍对话")
    public Flux<String> chatWithBook(
            HttpServletRequest request, @PathVariable Long bookId,
            @RequestParam String message, @RequestParam(defaultValue = "default") String sessionId) {
        Long userId = getUserId(request);
        if (userId == null) return Flux.just("未登录");
        return chatStream(userId, sessionId, "book", message,
                () -> aiChatService.chatWithRag(userId, bookId, message, sessionId));
    }

    @GetMapping(value = "/global", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @LogOperation("自由对话")
    public Flux<String> globalChat(
            HttpServletRequest request, @RequestParam String message,
            @RequestParam(defaultValue = "default") String sessionId) {
        Long userId = getUserId(request);
        if (userId == null) return Flux.just("未登录");
        return chatStream(userId, sessionId, "local", message,
                () -> aiChatService.globalChat(userId, message, sessionId));
    }

    @GetMapping(value = "/books", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chatWithAllBooks(
            HttpServletRequest request, @RequestParam String message,
            @RequestParam(defaultValue = "default") String sessionId) {
        Long userId = getUserId(request);
        if (userId == null) return Flux.just("未登录");
        return chatStream(userId, sessionId, "book", message,
                () -> aiChatService.chatWithAllBooks(userId, message, sessionId));
    }

    /**
     * 通用流式对话处理：保存用户消息 → 流式输出 → 保存 AI 回复
     */
    private Flux<String> chatStream(Long userId, String sessionId, String mode, String userMessage,
                                     java.util.function.Supplier<Flux<String>> chatFn) {
        aiChatService.saveMessage(userId, sessionId, mode, "user", userMessage);
        AtomicReference<StringBuilder> fullResponse = new AtomicReference<>(new StringBuilder());

        return chatFn.get()
                .doOnNext(chunk -> fullResponse.get().append(chunk))
                .doOnComplete(() -> {
                    String aiResponse = fullResponse.get().toString();
                    if (!aiResponse.isEmpty()) {
                        aiChatService.saveMessage(userId, sessionId, mode, "assistant", aiResponse);
                    }
                })
                .onErrorResume(e -> {
                    log.error("AI对话流错误", e);
                    String errorMsg = "⚠️ AI服务暂时不可用（" + getErrorHint(e) + "），请稍后重试";
                    return Flux.just(errorMsg);
                })
                .doFinally(signal -> log.debug("Controller chatStream 结束, signal={}, mode={}", signal, mode));
    }

    /** 根据异常类型返回友好的错误提示 */
    private String getErrorHint(Throwable e) {
        String msg = e.getMessage();
        if (msg == null) return "未知错误";
        if (msg.contains("Connection refused") || msg.contains("connect") || msg.contains("timeout"))
            return "AI模型服务未启动";
        if (msg.contains("model") || msg.contains("not found"))
            return "AI模型未找到";
        if (msg.contains("rate") || msg.contains("quota"))
            return "请求太频繁";
        if (msg.contains("404"))
            return "AI API 404错误，请检查模型名称和API Key";
        if (msg.contains("401") || msg.contains("unauthorized") || msg.contains("Unauthorized"))
            return "API Key 无效或已过期";
        if (msg.length() > 60) return msg.substring(0, 60) + "...";
        return msg;
    }

    @GetMapping("/history")
    public Result<List<Map<String, Object>>> getHistory(
            HttpServletRequest request, @RequestParam String sessionId,
            @RequestParam String mode) {
        Long userId = getUserId(request);
        if (userId == null) return Result.unauthorized("未登录");
        return Result.success(aiChatService.getHistory(userId, sessionId, mode));
    }

    @GetMapping("/sessions")
    public Result<List<Map<String, Object>>> getSessions(HttpServletRequest request,
                                                          @RequestParam(required = false) String mode) {
        Long userId = getUserId(request);
        if (userId == null) return Result.unauthorized("未登录");
        return Result.success(aiChatService.getUserSessions(userId, mode));
    }

    @GetMapping("/sessions/search")
    public Result<List<Map<String, Object>>> searchSessions(HttpServletRequest request,
                                                             @RequestParam String keyword,
                                                             @RequestParam(required = false) String mode) {
        Long userId = getUserId(request);
        if (userId == null) return Result.unauthorized("未登录");
        if (keyword == null || keyword.isBlank()) return Result.success(List.of());
        return Result.success(aiChatService.searchSessions(userId, keyword, mode));
    }

    @DeleteMapping("/session")
    public Result<Void> deleteSession(HttpServletRequest request,
                                       @RequestParam String sessionId,
                                       @RequestParam String mode) {
        Long userId = getUserId(request);
        if (userId == null) return Result.unauthorized("未登录");
        aiChatService.deleteSession(userId, sessionId, mode);
        return Result.success();
    }

    @PutMapping("/session/name")
    public Result<Void> updateChatName(HttpServletRequest request,
                                        @RequestParam String sessionId,
                                        @RequestParam String name,
                                        @RequestParam String mode) {
        Long userId = getUserId(request);
        if (userId == null) return Result.unauthorized("未登录");
        aiChatService.updateChatName(userId, sessionId, mode, name);
        return Result.success();
    }

    @PostMapping("/summary/chapter")
    public Result<String> generateChapterSummary(
            HttpServletRequest request, @RequestParam Long bookId, @RequestParam Integer chapterNumber) {
        Long userId = getUserId(request);
        if (userId == null) return Result.unauthorized("未登录");
        return Result.success(aiChatService.generateChapterSummary(userId, bookId, chapterNumber));
    }

    @PostMapping("/compare")
    public Result<String> compareNoteWithChapter(
            HttpServletRequest request, @RequestParam Long bookId,
            @RequestParam Integer chapterNumber, @RequestParam String note) {
        Long userId = getUserId(request);
        if (userId == null) return Result.unauthorized("未登录");
        return Result.success(summaryService.generateChapterComparison(userId, bookId, chapterNumber, note));
    }

    @GetMapping("/compare")
    public Flux<String> compareNoteWithChapterStream(
            HttpServletRequest request, @RequestParam Long bookId,
            @RequestParam Integer chapterNumber, @RequestParam String note) {
        Long userId = getUserId(request);
        if (userId == null) return Flux.just("未登录");
        // 直接复用 RAG 对比：用笔记内容作为查询，检索原文对比
        return aiChatService.chatWithRag(userId, bookId,
                "请对比分析这段笔记与原文的异同：\n" + note, "compare_" + bookId);
    }

    @PostMapping("/graph/generate")
    @LogOperation("生成图谱")
    public Result<Void> generateGraph(HttpServletRequest request, @RequestParam Long bookId) {
        Long userId = getUserId(request);
        if (userId == null) return Result.unauthorized("未登录");
        // 异步执行，不阻塞 HTTP 请求
        Long uid = userId;
        CompletableFuture.runAsync(() -> kgService.generateGraph(uid, bookId));
        return Result.success();
    }

    @GetMapping("/status")
    public Result<Map<String, Object>> status() {
        return Result.success(Map.of(
                "aiAvailable", aiChatService.isAiAvailable()
        ));
    }

    @GetMapping("/test")
    public SseEmitter testConnection(HttpServletRequest request) {
        Long userId = getUserId(request);
        if (userId == null) throw new RuntimeException("未登录");
        SseEmitter emitter = new SseEmitter(300000L);
        try { emitter.send(SseEmitter.event().name("connected").data(Map.of("message", "连接成功")));
        } catch (IOException e) { log.error("SSE测试失败", e); }
        return emitter;
    }

    private Long getUserId(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId != null) return userId;
        Object s = request.getSession(false) != null ? request.getSession(false).getAttribute("userId") : null;
        return s instanceof Long ? (Long) s : null;
    }
}
