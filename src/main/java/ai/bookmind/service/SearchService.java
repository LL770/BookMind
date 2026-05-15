package ai.bookmind.service;

import ai.bookmind.ai.AiApiClient;
import ai.bookmind.entity.Chapter;
import ai.bookmind.entity.Note;
import ai.bookmind.mapper.NoteMapper;
import ai.bookmind.mapper.ChapterMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 搜索服务
 * 整合向量搜索 + BGE 重排序 + 数据库搜索
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SearchService {

    private final VectorizationService vectorizationService;
    private final NoteMapper noteMapper;
    private final ChapterMapper chapterMapper;
    private final AiApiClient aiApiClient;

    /** 格式化章节结果为 Map（动态计算相关度分数） */
    private Map<String, Object> chapterToMap(Chapter ch) {
        Map<String, Object> m = new HashMap<>();
        String content = ch.getContent() != null ? ch.getContent() : "";
        m.put("type", "book");
        m.put("bookId", ch.getBookId());
        m.put("chapterNumber", ch.getChapterNumber());
        m.put("title", ch.getTitle());
        m.put("content", content);
        // 分数基于内容长度相关性：短内容匹配更精准
        double score = Math.min(1.0, 5000.0 / Math.max(content.length(), 100));
        m.put("score", Math.max(0.3, score));
        return m;
    }

    /** 格式化向量文档为 Map */
    private Map<String, Object> docToMap(Document doc) {
        return Map.of(
                "type", "book",
                "bookId", doc.getMetadata().get("bookId"),
                "chapterNumber", doc.getMetadata().get("chapterNumber"),
                "title", doc.getMetadata().get("chapterTitle"),
                "content", doc.getContent(),
                "score", doc.getMetadata().getOrDefault("score", 0.5)
        );
    }

    /**
     * 全局搜索（DB LIKE + 向量 + 重排序，合并结果）
     */
    public Map<String, Object> globalSearch(Long userId, String query, int topK) {
        Map<String, Object> results = new HashMap<>();
        Set<String> seen = new HashSet<>(); // 去重

        // ===== DB LIKE =====
        List<Map<String, Object>> bookResults = new ArrayList<>();
        for (Chapter ch : chapterMapper.searchAllContent(query, topK * 2)) {
            if (seen.add(ch.getBookId() + ":" + ch.getChapterNumber()))
                bookResults.add(chapterToMap(ch));
        }

        // ===== 向量搜索（语义匹配） =====
        try {
            List<Document> vecDocs = vectorizationService.globalSearch(userId, query, topK * 2);
            List<Document> ranked = rerankDocuments(query, vecDocs, topK);
            for (Document doc : ranked) {
                String key = doc.getMetadata().get("bookId") + ":" + doc.getMetadata().get("chapterNumber");
                if (seen.add(key)) bookResults.add(docToMap(doc));
            }
        } catch (Exception e) {
            log.warn("向量搜索不可用(仅返回DB结果): {}", e.getMessage());
        }

        // ===== 笔记搜索（严格相关才返回） =====
        List<Note> noteResults = noteMapper.search(userId, query);
        noteResults = noteResults.stream()
                .filter(n -> n.getContent() != null && (n.getContent().contains(query) || (n.getQuoteText() != null && n.getQuoteText().contains(query))))
                .limit(topK)
                .toList();
        List<Map<String, Object>> noteMaps = noteResults.stream()
                .map(this::formatNoteResult)
                .collect(Collectors.toList());

        results.put("books", bookResults);
        results.put("notes", noteMaps);
        results.put("total", bookResults.size() + noteMaps.size());
        results.put("query", query);
        results.put("twoStageSearch", true);

        return results;
    }

    /**
     * 书籍内搜索（DB LIKE + 向量 + 重排序）
     */
    public Map<String, Object> searchInBook(Long userId, Long bookId, String query, int topK) {
        Map<String, Object> results = new HashMap<>();
        Set<String> seen = new HashSet<>();

        // ===== DB LIKE =====
        List<Map<String, Object>> bookResults = new ArrayList<>();
        for (Chapter ch : chapterMapper.searchContent(bookId, query, topK * 2)) {
            if (seen.add(ch.getBookId() + ":" + ch.getChapterNumber()))
                bookResults.add(chapterToMap(ch));
        }

        // ===== 向量搜索 =====
        try {
            List<Document> vecDocs = vectorizationService.searchBookContent(userId, bookId, query, topK * 2);
            List<Document> ranked = rerankDocuments(query, vecDocs, topK);
            for (Document doc : ranked) {
                String key = doc.getMetadata().get("bookId") + ":" + doc.getMetadata().get("chapterNumber");
                if (seen.add(key)) bookResults.add(docToMap(doc));
            }
        } catch (Exception e) {
            log.warn("向量搜索不可用(仅返回DB结果): {}", e.getMessage());
        }

        // ===== 笔记搜索（严格相关） =====
        List<Note> noteResults = noteMapper.search(userId, query);
        noteResults = noteResults.stream()
                .filter(n -> n.getBookId().equals(bookId))
                .filter(n -> n.getContent() != null && n.getContent().contains(query))
                .limit(topK)
                .toList();
        List<Map<String, Object>> noteMaps = noteResults.stream()
                .map(this::formatNoteResult)
                .collect(Collectors.toList());

        results.put("bookId", bookId);
        results.put("bookContent", bookResults);
        results.put("notes", noteMaps);
        results.put("total", bookResults.size() + noteMaps.size());

        return results;
    }

    /**
     * BGE 重排序：将向量检索结果用 BGE-reranker-v2-m3 重新排序
     * 过滤 score < 0.01 的低相关结果
     */
    private List<Document> rerankDocuments(String query, List<Document> docs, int topK) {
        if (docs.isEmpty()) return docs;

        // 准备文本列表
        List<String> texts = docs.stream()
                .map(d -> d.getContent().substring(0, Math.min(500, d.getContent().length())))
                .collect(Collectors.toList());

        // 调用 SiliconFlow Rerank API（Key-E）
        List<Map<String, Object>> reranked = aiApiClient.rerank(query, texts, topK * 2);

        if (reranked.isEmpty()) {
            // Rerank 失败，退回原始排序 + 过滤低分
            return docs.stream()
                    .filter(d -> (double) d.getMetadata().getOrDefault("score", 0.0) > 0.01)
                    .limit(topK)
                    .collect(Collectors.toList());
        }

        // 按重排序结果重新排列，过滤低分
        List<Document> result = new ArrayList<>();
        Set<Integer> added = new HashSet<>();
        for (Map<String, Object> item : reranked) {
            double score = ((Number) item.get("score")).doubleValue();
            if (score < 0.01) continue; // 过滤低相关
            int idx = ((Number) item.get("index")).intValue();
            if (idx < docs.size() && added.add(idx)) {
                docs.get(idx).getMetadata().put("score", score); // 用重排序分数覆盖
                result.add(docs.get(idx));
            }
        }
        // 补全（不足 topK 时从高向量分中补，过滤低分）
        for (int i = 0; i < docs.size() && result.size() < topK; i++) {
            if (!added.contains(i)) {
                double score = (double) docs.get(i).getMetadata().getOrDefault("score", 0.0);
                if (score > 0.01) {
                    result.add(docs.get(i));
                }
            }
        }
        return result;
    }

    /**
     * 搜索笔记
     */
    public List<Note> searchNotes(Long userId, String query, int limit) {
        List<Note> notes = noteMapper.search(userId, query);
        return notes.subList(0, Math.min(limit, notes.size()));
    }

    private List<Map<String, Object>> formatBookResults(List<Document> docs) {
        List<Map<String, Object>> results = new ArrayList<>();
        for (Document doc : docs) {
            Map<String, Object> result = new HashMap<>();
            result.put("type", "book");
            result.put("bookId", doc.getMetadata().get("bookId"));
            result.put("bookTitle", doc.getMetadata().get("bookTitle"));
            result.put("chapterId", doc.getMetadata().get("chapterId"));
            result.put("chapterNumber", doc.getMetadata().get("chapterNumber"));
            result.put("chapterTitle", doc.getMetadata().get("chapterTitle"));
            result.put("content", doc.getContent().substring(0, Math.min(200, doc.getContent().length())));
            Object score = doc.getMetadata().getOrDefault("score", 0.0);
            result.put("score", score);
            result.put("rerankScore", score);
            results.add(result);
        }
        return results;
    }

    private Map<String, Object> formatNoteResult(Note note) {
        Map<String, Object> result = new HashMap<>();
        result.put("type", "note");
        result.put("noteId", note.getId());
        result.put("bookId", note.getBookId());
        result.put("category", note.getCategory());
        result.put("quoteText", note.getQuoteText().substring(0, Math.min(100, note.getQuoteText().length())));
        result.put("content", note.getContent().substring(0, Math.min(200, note.getContent().length())));
        result.put("createTime", note.getCreateTime());
        return result;
    }

    /**
     * 语义搜索 + 关键词高亮
     */
    public List<Map<String, Object>> searchWithHighlight(Long userId, Long bookId, String query, int topK) {
        Map<String, Object> searchResults = searchInBook(userId, bookId, query, topK);
        List<Map<String, Object>> highlighted = new ArrayList<>();

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> books = (List<Map<String, Object>>) searchResults.get("bookContent");
        for (Map<String, Object> book : books) {
            String content = (String) book.get("content");
            String highlightedContent = highlightKeywords(content, query);
            book.put("content", highlightedContent);
            highlighted.add(book);
        }

        return highlighted;
    }

    private String highlightKeywords(String text, String keyword) {
        if (text == null || keyword == null || keyword.isEmpty()) {
            return text;
        }
        return text.replaceAll("(?i)(" + Pattern.quote(keyword) + ")", "**$1**");
    }
}
