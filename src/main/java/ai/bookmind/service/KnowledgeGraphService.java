package ai.bookmind.service;

import ai.bookmind.ai.AiApiClient;
import ai.bookmind.entity.KnowledgeEdge;
import ai.bookmind.mapper.BookMapper;
import ai.bookmind.entity.KnowledgeNode;
import ai.bookmind.mapper.KnowledgeNodeMapper;
import ai.bookmind.mapper.KnowledgeEdgeMapper;
import ai.bookmind.mapper.NoteMapper;
import ai.bookmind.mapper.ChapterMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * 知识图谱服务
 * 分批 + 并发双模型（sensenova-6.7-flash-lite ↔ deepseek-v4-flash）
 * 6 类实体：person, organization, location, concept, event, artifact
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class KnowledgeGraphService {

    private final KnowledgeNodeMapper nodeMapper;
    private final KnowledgeEdgeMapper edgeMapper;
    private final NoteMapper noteMapper;
    private final ChapterMapper chapterMapper;
    private final AiApiClient aiApiClient;
    private final BookMapper bookMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();
    // 专用线程池：KG 批次并行调 AI，最多 16 并发
    private final Executor kgExecutor = Executors.newWorkStealingPool(16);

    private static final int BATCH_CHAPTERS = 9999;
    private static final int MAX_CHARS_PER_BATCH = 200000;

    // 轮流分配：偶数批用 flash-lite，奇数批用 deepseek-v4-flash
    private static final String MODEL_A = "sensenova-6.7-flash-lite";
    private static final String MODEL_B = "deepseek-v4-flash";

    // ==================== 公开 API ====================

    public Map<String, Object> getGraphData(Long userId, Long bookId) {
        Map<String, Object> result = new HashMap<>();
        List<KnowledgeNode> nodes = nodeMapper.selectByBookId(userId, bookId);
        List<Map<String, Object>> echartsNodes = nodes.stream()
                .map(this::convertNodeToECharts).collect(Collectors.toList());
        List<KnowledgeEdge> edges = edgeMapper.selectByBookId(userId, bookId);
        List<Map<String, Object>> echartsEdges = edges.stream()
                .map(this::convertEdgeToECharts).collect(Collectors.toList());
        result.put("nodes", echartsNodes);
        result.put("links", echartsEdges);
        result.put("bookId", bookId);
        result.put("nodeCount", nodes.size());
        result.put("edgeCount", edges.size());
        result.put("bookTitle", "");
        return result;
    }

    public KnowledgeNode getNodeById(Long userId, Long bookId, Long nodeId) {
        KnowledgeNode node = nodeMapper.selectById(nodeId);
        if (node != null && node.getUserId().equals(userId) && node.getBookId().equals(bookId)) return node;
        return null;
    }

    public List<Map<String, Object>> getNodeRelatedNotes(Long userId, Long bookId, Long nodeId) {
        KnowledgeNode node = getNodeById(userId, bookId, nodeId);
        if (node == null) return List.of();
        return noteMapper.search(userId, node.getName()).stream()
                .map(n -> { Map<String, Object> r = new HashMap<>();
                    r.put("noteId", n.getId()); r.put("content", n.getContent().substring(0, Math.min(150, n.getContent().length())));
                    r.put("category", n.getCategory()); r.put("createTime", n.getCreateTime()); return r; })
                .collect(Collectors.toList());
    }

    public Map<String, Object> getNodeFirstLocation(Long userId, Long bookId, Long nodeId) {
        KnowledgeNode node = getNodeById(userId, bookId, nodeId);
        if (node == null || node.getFirstChapter() == null) return Map.of("found", false);
        var chapter = chapterMapper.selectByBookAndChapter(bookId, node.getFirstChapter());
        if (chapter == null) return Map.of("found", false, "message", "原章节已不存在");
        Map<String, Object> loc = new HashMap<>();
        loc.put("found", true); loc.put("chapterNumber", node.getFirstChapter());
        loc.put("chapterId", chapter.getId()); loc.put("chapterTitle", chapter.getTitle());
        loc.put("content", chapter.getContent().substring(0, Math.min(300, chapter.getContent().length())));
        return loc;
    }

    // ==================== 核心图谱生成 ====================

    @Transactional(rollbackFor = Exception.class)
    public boolean generateGraph(Long userId, Long bookId) {
        try {
            List<ChapterInfo> chapters = chapterMapper.selectByBookId(bookId).stream()
                    .map(c -> new ChapterInfo(c.getChapterNumber(), c.getTitle(),
                            c.getContent() != null ? c.getContent() : ""))
                    .filter(c -> !c.content.isBlank())
                    .collect(Collectors.toList());
            if (chapters.isEmpty()) { log.warn("无章节内容，跳过: bookId={}", bookId); return false; }
            // 限制最多处理 300 章（防超大书）
            if (chapters.size() > 300) {
                log.info("章节过多({}), 截取前 300 章: bookId={}", chapters.size(), bookId);
                chapters = chapters.subList(0, 300);
            }

            // 每章截取前 CHARS_PER_CHAPTER 字，分组分批
            List<String> batches = buildBatches(chapters);
            if (batches.isEmpty()) return false;

            // 分批并行处理：偶数批→flash-lite，奇数批→deepseek-v4-flash
            Map<String, KnowledgeNode> allNodes = new ConcurrentHashMap<>();
            Map<String, RawEdge> allEdgeMap = new ConcurrentHashMap<>();

            List<CompletableFuture<Void>> batchFutures = new ArrayList<>();
            for (int i = 0; i < batches.size(); i++) {
                String model = (i % 2 == 0) ? MODEL_A : MODEL_B;
                String content = batches.get(i);
                int idx = i;
                batchFutures.add(CompletableFuture.runAsync(() -> {
                    Thread.currentThread().setName("kg-" + idx);
                    log.info("KG批次 {}/{}: 模型={}, 输入={}字", idx + 1, batches.size(), model, content.length());
                    Map<String, Object> result = callModel(model, buildPrompt(content));
                    if (result != null) mergeBatchResult(result, allNodes, allEdgeMap);
                }, kgExecutor));
            }
            CompletableFuture.allOf(batchFutures.toArray(new CompletableFuture[0])).join();

            if (allNodes.isEmpty()) { log.warn("AI未提取到实体: bookId={}", bookId); return false; }

            // 落库
            deleteGraph(userId, bookId);
            for (KnowledgeNode node : allNodes.values()) {
                node.setUserId(userId); node.setBookId(bookId);
                node.setCreateTime(LocalDateTime.now());
                nodeMapper.insert(node);
            }
            Map<String, Long> nameIdMap = new HashMap<>();
            allNodes.values().forEach(n -> nameIdMap.put(n.getName(), n.getId()));

            int edgeCount = 0;
            for (RawEdge re : allEdgeMap.values()) {
                Long sid = nameIdMap.get(re.source); Long tid = nameIdMap.get(re.target);
                if (sid == null || tid == null || sid.equals(tid)) continue;
                KnowledgeEdge e = new KnowledgeEdge();
                e.setUserId(userId); e.setBookId(bookId);
                e.setSourceNodeId(sid); e.setTargetNodeId(tid);
                e.setRelation(re.relation != null ? re.relation : "关联");
                e.setWeight(re.weight); e.setCreateTime(LocalDateTime.now());
                edgeMapper.insert(e); edgeCount++;
            }
            log.info("知识图谱生成完成: bookId={}, nodes={}, edges={}", bookId, allNodes.size(), edgeCount);
            return true;

        } catch (Exception e) {
            log.error("知识图谱生成失败: bookId={}", bookId, e);
            return false;
        }
    }

    // ==================== 分批构建 ====================

    private List<String> buildBatches(List<ChapterInfo> chapters) {
        List<String> batches = new ArrayList<>();
        StringBuilder buf = new StringBuilder();
        for (ChapterInfo ch : chapters) {
            String text = ch.content;
            if (text == null || text.isBlank()) continue;
            if (buf.length() + text.length() + 2 > MAX_CHARS_PER_BATCH) {
                if (buf.length() > 0) { batches.add(buf.toString().trim()); buf = new StringBuilder(); }
            }
            buf.append(text).append("\n\n");
        }
        if (buf.length() > 0) batches.add(buf.toString().trim());
        return batches;
    }

    // ==================== 单模型调用 ====================

    @SuppressWarnings("unchecked")
    private Map<String, Object> callModel(String model, String prompt) {
        List<Map<String, String>> msgs = List.of(Map.of("role", "user", "content", prompt));
        String sysPrompt = "你是一个JSON输出专家，只输出有效的JSON，不包含任何其他文字。";
        String rawJson = aiApiClient.kgChatSync(msgs, model, 0.3, sysPrompt);
        if (rawJson == null || rawJson.isBlank()) return null;

        String cleaned = cleanJsonString(rawJson);
        if (cleaned == null || cleaned.isEmpty()) return null;

        try {
            Map<String, Object> parsed = objectMapper.readValue(cleaned, new TypeReference<Map<String, Object>>() {});
            List<Map<String, Object>> rawNodes = (List<Map<String, Object>>) parsed.get("nodes");
            if (rawNodes == null || rawNodes.isEmpty()) return null;
            return parsed;
        } catch (Exception e) {
            log.warn("解析JSON失败: {}", e.getMessage());
            return null;
        }
    }

    // ==================== 提示词（6类实体） ====================

    private String buildPrompt(String content) {
        return String.format("""
                从以下书籍内容中抽取重要实体和关系，构建知识图谱。

                输出要求：
                1. 只输出纯JSON，不要markdown包裹或其他文字
                2. JSON格式固定为：
                {"nodes":[{"name":"实体名称","type":"类型","description":"简要描述"}],"edges":[{"source":"源实体","target":"目标实体","relation":"关系描述"}]}
                3. 实体类型（6种）：
                   - person：人物（核心角色、次要角色）
                   - organization：组织、机构、团体
                   - location：地点、地域、场景
                   - concept：概念、理论、思想、主题
                   - event：事件、冲突、转折
                   - artifact：作品、物品、技术、工具
                4. 提取至少40个实体，覆盖核心人物/组织/地点/事件/概念/物品
                5. description 字段说明该实体在书中的角色或意义（10-30字）
                6. relation 要具体，如"统治了……"比"关联"更好
                7. 提取所有重要关系，不限数量，确保每个关系中的实体都在nodes中
                8. 特别注意追踪核心人物之间的关系演变（如冲突→合作→对立→联盟），分别作为不同关系提取
                9. 每个实体必须至少参与一条关系，不允许有孤立节点
                10. 输出必须能被JSON.parse直接解析

                内容：
                %s""", content);
    }

    // ==================== 结果合并 ====================

    @SuppressWarnings("unchecked")
    private void mergeBatchResult(Map<String, Object> batch, Map<String, KnowledgeNode> nodeMap, Map<String, RawEdge> edgeMap) {
        List<Map<String, Object>> rawNodes = (List<Map<String, Object>>) batch.get("nodes");
        if (rawNodes == null) return;
        for (Map<String, Object> raw : rawNodes) {
            String name = raw.get("name") != null ? raw.get("name").toString().trim() : "";
            if (name.isEmpty()) continue;
            String type = raw.getOrDefault("type", "concept").toString();
            String desc = raw.getOrDefault("description", "").toString();

            if (nodeMap.containsKey(name)) {
                KnowledgeNode existing = nodeMap.get(name);
                existing.setOccurrenceCount(existing.getOccurrenceCount() + 1);
            } else {
                KnowledgeNode node = new KnowledgeNode();
                node.setName(name);
                node.setType(type);
                node.setDescription(desc.length() > 200 ? desc.substring(0, 200) : desc);
                node.setFirstChapter(1);
                node.setOccurrenceCount(1);
                nodeMap.put(name, node);
            }
        }

        List<Map<String, Object>> rawEdges = (List<Map<String, Object>>) batch.get("edges");
        if (rawEdges != null) {
            for (Map<String, Object> raw : rawEdges) {
                String s = raw.get("source") != null ? raw.get("source").toString().trim() : null;
                String t = raw.get("target") != null ? raw.get("target").toString().trim() : null;
                String r = raw.getOrDefault("relation", "关联").toString();
                if (s == null || t == null || s.equals(t)) continue;
                String pairSrc = s.compareTo(t) <= 0 ? s : t;
                String pairTgt = pairSrc.equals(s) ? t : s;
                String key = pairSrc + "::" + pairTgt + "::" + r;
                edgeMap.merge(key, new RawEdge(s, t, r, 1.0), (old, unused) -> new RawEdge(old.source, old.target, old.relation, old.weight + 1.0));
            }
        }
    }

    // ==================== ECharts 格式转换 ====================

    private Map<String, Object> convertNodeToECharts(KnowledgeNode node) {
        Map<String, Object> n = new HashMap<>();
        n.put("id", node.getId());
        n.put("name", node.getName());
        n.put("type", node.getType());
        n.put("category", getNodeCategoryIndex(node.getType()));
        n.put("description", node.getDescription() != null ? node.getDescription() : "");
        n.put("symbolSize", Math.max(28, node.getOccurrenceCount() * 4));
        return n;
    }

    private int getNodeCategoryIndex(String type) {
        return switch (type) {
            case "person" -> 0;
            case "organization" -> 1;
            case "location" -> 2;
            case "concept" -> 3;
            case "event" -> 4;
            case "artifact" -> 5;
            default -> 3;
        };
    }

    private Map<String, Object> convertEdgeToECharts(KnowledgeEdge edge) {
        Map<String, Object> e = new HashMap<>();
        e.put("source", edge.getSourceNodeId());
        e.put("target", edge.getTargetNodeId());
        e.put("value", edge.getWeight());
        e.put("relation", edge.getRelation() != null ? edge.getRelation() : "");
        e.put("label", Map.of("show", true, "formatter", edge.getRelation() != null ? edge.getRelation() : ""));
        e.put("lineStyle", Map.of("color", edge.getColor() != null ? edge.getColor() : "#A08970", "curveness", 0.3));
        return e;
    }

    // ==================== 清理 ====================

    @Transactional(rollbackFor = Exception.class)
    public void deleteGraph(Long userId, Long bookId) {
        List<KnowledgeNode> nodes = nodeMapper.selectByBookId(userId, bookId);
        for (KnowledgeNode node : nodes) {
            edgeMapper.deleteBySourceNode(node.getId());
            edgeMapper.deleteByTargetNode(node.getId());
            nodeMapper.deleteById(node.getId());
        }
        log.info("知识图谱删除完成: bookId={}", bookId);
    }

    /** 暂停 KG 生成：删图谱数据 + 更新书籍状态为完成 */
    @Transactional(rollbackFor = Exception.class)
    public void cancelGraph(Long userId, Long bookId) {
        deleteGraph(userId, bookId);
        bookMapper.updateStatusProgress(bookId, 3, 100, "知识图谱已暂停");
        log.info("知识图谱已暂停: bookId={}", bookId);
    }

    // ==================== JSON 清理 ====================

    private String cleanJsonString(String raw) {
        if (raw == null) return null;
        String s = raw.trim();
        if (s.startsWith("```")) s = s.replaceAll("(?s)```(?:json)?\\s*", "").trim();
        int first = s.indexOf('{'); int alt = s.indexOf('[');
        int start = -1;
        if (first >= 0 && alt >= 0) start = Math.min(first, alt);
        else if (first >= 0) start = first;
        else if (alt >= 0) start = alt;
        if (start > 0) s = s.substring(start);
        int lastBrace = s.lastIndexOf('}'); int lastBracket = s.lastIndexOf(']');
        int end = -1;
        if (lastBrace >= 0 && lastBracket >= 0) end = Math.max(lastBrace, lastBracket) + 1;
        else if (lastBrace >= 0) end = lastBrace + 1;
        else if (lastBracket >= 0) end = lastBracket + 1;
        if (end > 0) s = s.substring(0, end);
        return s.trim();
    }

    /**
     * 统计某本书的知识图谱节点数
     */
    public int countNodes(Long userId, Long bookId) {
        try {
            var nodes = nodeMapper.selectByBookId(userId, bookId);
            return nodes != null ? nodes.size() : 0;
        } catch (Exception e) {
            log.warn("统计KG节点数失败 bookId={}", bookId, e);
            return 0;
        }
    }

    /**
     * 复制知识图谱：从源书复制KG数据到目标书（同书共享）
     */
    @Transactional(rollbackFor = Exception.class)
    public void copyGraph(Long sourceUserId, Long sourceBookId, Long targetUserId, Long targetBookId) {
        try {
            var nodes = nodeMapper.selectByBookId(sourceUserId, sourceBookId);
            if (nodes == null || nodes.isEmpty()) {
                log.info("源书无KG数据可复制: sourceBookId={}", sourceBookId);
                return;
            }

            // 删除目标书旧KG数据
            deleteGraph(targetUserId, targetBookId);

            // 复制节点
            Map<Long, Long> oldIdNewIdMap = new HashMap<>();
            for (KnowledgeNode node : nodes) {
                KnowledgeNode n = new KnowledgeNode();
                n.setUserId(targetUserId);
                n.setBookId(targetBookId);
                n.setName(node.getName());
                n.setType(node.getType());
                n.setDescription(node.getDescription());
                n.setFirstChapter(node.getFirstChapter());
                n.setOccurrenceCount(node.getOccurrenceCount());
                n.setCreateTime(LocalDateTime.now());
                nodeMapper.insert(n);
                oldIdNewIdMap.put(node.getId(), n.getId());
            }

            // 复制边
            var edges = edgeMapper.selectByBookId(sourceUserId, sourceBookId);
            if (edges != null) {
                for (KnowledgeEdge edge : edges) {
                    Long newSourceId = oldIdNewIdMap.get(edge.getSourceNodeId());
                    Long newTargetId = oldIdNewIdMap.get(edge.getTargetNodeId());
                    if (newSourceId == null || newTargetId == null) continue;
                    KnowledgeEdge e = new KnowledgeEdge();
                    e.setUserId(targetUserId);
                    e.setBookId(targetBookId);
                    e.setSourceNodeId(newSourceId);
                    e.setTargetNodeId(newTargetId);
                    e.setRelation(edge.getRelation());
                    e.setDescription(edge.getDescription());
                    e.setWeight(edge.getWeight());
                    e.setCreateTime(LocalDateTime.now());
                    edgeMapper.insert(e);
                }
            }

            log.info("KG复制完成: sourceBookId={}, targetBookId={}, nodes={}", sourceBookId, targetBookId, nodes.size());
        } catch (Exception e) {
            log.error("复制知识图谱失败", e);
            throw new RuntimeException(e);
        }
    }

    // ==================== 内部数据结构 ====================

    private record ChapterInfo(int number, String title, String content) {
        String toTruncated(int maxLen) {
            String c = content.length() > maxLen ? content.substring(0, maxLen) : content;
            return "第" + number + "章: " + (title != null ? title : "") + "\n" + c;
        }
    }

    private record RawEdge(String source, String target, String relation, double weight) {}
}
