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

    /**
     * 搜索笔记
     */
    public Map<String, Object> globalSearch(Long userId, String query, int topK) {
        Map<String, Object> results = new HashMap<>();

        // ===== DB FULLTEXT 搜索（走全文索引，10-100x 比 LIKE 快） =====
        List<Map<String, Object>> bookResults = new ArrayList<>();
        for (Chapter ch : chapterMapper.searchAllContentFulltext(query, topK * 2)) {
            bookResults.add(chapterToMap(ch));
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

        return results;
    }

    /**
     * 书籍内搜索（DB FULLTEXT + 笔记）
     */
    public Map<String, Object> searchInBook(Long userId, Long bookId, String query, int topK) {
        Map<String, Object> results = new HashMap<>();

        // ===== DB FULLTEXT 搜索（走全文索引） =====
        List<Map<String, Object>> bookResults = new ArrayList<>();
        for (Chapter ch : chapterMapper.searchContentFulltext(bookId, query, topK * 2)) {
            bookResults.add(chapterToMap(ch));
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
     * 搜索笔记
     */
    public List<Note> searchNotes(Long userId, String query, int limit) {
        List<Note> notes = noteMapper.search(userId, query);
        return notes.subList(0, Math.min(limit, notes.size()));
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
