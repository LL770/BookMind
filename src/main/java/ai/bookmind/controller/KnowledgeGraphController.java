package ai.bookmind.controller;

import ai.bookmind.common.Result;
import ai.bookmind.entity.Book;
import ai.bookmind.entity.KnowledgeEdge;
import ai.bookmind.entity.KnowledgeNode;
import ai.bookmind.mapper.BookMapper;
import ai.bookmind.mapper.KnowledgeNodeMapper;
import ai.bookmind.service.KnowledgeGraphService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 知识图谱控制器
 */
@RestController
@RequestMapping("/api/graph")
@RequiredArgsConstructor
@Slf4j
public class KnowledgeGraphController {

    private final KnowledgeGraphService kgService;
    private final BookMapper bookMapper;
    private final KnowledgeNodeMapper nodeMapper;

    /**
     * 获取知识图谱数据（ECharts格式）
     */
    @GetMapping("/{bookId}")
    public Result<Map<String, Object>> getGraph(
            HttpServletRequest request,
            @PathVariable Long bookId) {
        
        Long userId = getUserIdFromRequest(request);
        if (userId == null) {
            return Result.unauthorized("未登录");
        }

        Map<String, Object> graphData = kgService.getGraphData(userId, bookId);
        return Result.success(graphData);
    }

    /**
     * 获取节点详情
     */
    @GetMapping("/{bookId}/node/{nodeId}")
    public Result<KnowledgeNode> getNode(
            HttpServletRequest request,
            @PathVariable Long bookId,
            @PathVariable Long nodeId) {
        
        Long userId = getUserIdFromRequest(request);
        if (userId == null) {
            return Result.unauthorized("未登录");
        }

        KnowledgeNode node = kgService.getNodeById(userId, bookId, nodeId);
        if (node == null) {
            return Result.notFound("节点不存在");
        }
        return Result.success(node);
    }

    /**
     * 获取节点相关笔记
     */
    @GetMapping("/{bookId}/node/{nodeId}/notes")
    public Result<List<Map<String, Object>>> getNodeNotes(
            HttpServletRequest request,
            @PathVariable Long bookId,
            @PathVariable Long nodeId) {
        
        Long userId = getUserIdFromRequest(request);
        if (userId == null) {
            return Result.unauthorized("未登录");
        }

        List<Map<String, Object>> notes = kgService.getNodeRelatedNotes(userId, bookId, nodeId);
        return Result.success(notes);
    }

    /**
     * 获取节点首次出现位置
     */
    @GetMapping("/{bookId}/node/{nodeId}/location")
    public Result<Map<String, Object>> getNodeLocation(
            HttpServletRequest request,
            @PathVariable Long bookId,
            @PathVariable Long nodeId) {
        
        Long userId = getUserIdFromRequest(request);
        if (userId == null) {
            return Result.unauthorized("未登录");
        }

        Map<String, Object> location = kgService.getNodeFirstLocation(userId, bookId, nodeId);
        return Result.success(location);
    }

    /**
     * 取消知识图谱生成（暂停）
     */
    @PostMapping("/{bookId}/cancel")
    public Result<Void> cancelGraph(
            HttpServletRequest request,
            @PathVariable Long bookId) {
        Long userId = getUserIdFromRequest(request);
        if (userId == null) return Result.unauthorized("未登录");
        kgService.cancelGraph(userId, bookId);
        return Result.success();
    }

    /**
     * 手动触发知识图谱生成（异步，不阻塞请求）
     */
    @PostMapping("/{bookId}/generate")
    public Result<Void> generateGraph(
            HttpServletRequest request,
            @PathVariable Long bookId) {
        Long userId = getUserIdFromRequest(request);
        if (userId == null) return Result.unauthorized("未登录");

        CompletableFuture.runAsync(() -> {
            try {
                boolean ok = kgService.generateGraph(userId, bookId);
                bookMapper.updateKgGenerated(bookId, ok ? 1 : 0);
            } catch (Exception e) {
                log.error("手动生成知识图谱失败 bookId={}", bookId, e);
            }
        });

        return Result.success();
    }

    /**
     * 获取知识图谱状态：是否已有图谱、节点数、边数
     */
    @GetMapping("/{bookId}/status")
    public Result<Map<String, Object>> getGraphStatus(
            HttpServletRequest request,
            @PathVariable Long bookId) {
        Long userId = getUserIdFromRequest(request);
        if (userId == null) return Result.unauthorized("未登录");

        Book book = bookMapper.selectById(bookId);
        if (book == null) return Result.notFound("书籍不存在");

        int nodeCount = 0, edgeCount = 0;
        try {
            var nodes = nodeMapper.selectByBookId(userId, bookId);
            nodeCount = nodes != null ? nodes.size() : 0;
            var edges = kgService.getGraphData(userId, bookId);
            edgeCount = edges != null && edges.containsKey("links")
                    ? ((List<?>) edges.get("links")).size() : 0;
        } catch (Exception e) {
            log.warn("查询图谱状态失败 bookId={}", bookId, e);
        }

        Map<String, Object> status = new java.util.HashMap<>();
        status.put("kgGenerated", book.getKgGenerated() != null ? book.getKgGenerated() : 0);
        status.put("nodeCount", nodeCount);
        status.put("edgeCount", edgeCount);
        return Result.success(status);
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
