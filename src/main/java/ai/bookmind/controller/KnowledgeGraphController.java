package ai.bookmind.controller;

import ai.bookmind.common.Result;
import ai.bookmind.entity.KnowledgeEdge;
import ai.bookmind.entity.KnowledgeNode;
import ai.bookmind.service.KnowledgeGraphService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 知识图谱控制器
 */
@RestController
@RequestMapping("/api/graph")
@RequiredArgsConstructor
@Slf4j
public class KnowledgeGraphController {

    private final KnowledgeGraphService kgService;

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
