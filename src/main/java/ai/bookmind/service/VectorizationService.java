package ai.bookmind.service;

import ai.bookmind.entity.Chapter;
import ai.bookmind.entity.Note;
import ai.bookmind.mapper.BookMapper;
import ai.bookmind.mapper.ChapterMapper;
import ai.bookmind.mapper.NoteMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 向量化服务
 * 每本书独立 Qdrant collection (book_{bookId})
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class VectorizationService {

    private final HybridVectorService hybridVectorService;
    private final EmbeddingModel embeddingModel;
    private final BookMapper bookMapper;
    private final ChapterMapper chapterMapper;
    private final NoteMapper noteMapper;

    @Value("${bookmind.vector.chunk-size}")
    private int chunkSize;

    @Value("${bookmind.vector.chunk-overlap}")
    private int chunkOverlap;

    /** 获取本书的 collection 名 */
    private String collName(Long bookId) {
        return HybridVectorService.bookCollection(bookId);
    }

    /**
     * 向量化书籍内容
     */
    @Transactional(rollbackFor = Exception.class)
    public void vectorizeBook(Long userId, Long bookId) {
        try {
            log.info("【向量化】开始 bookId={}", bookId);
            bookMapper.updateStatusProgress(bookId, 2, 30, "正在创建向量库...");

            // 1. 创建 per-book collection
            String collection = collName(bookId);
            hybridVectorService.createCollection(collection);

            // 2. 获取所有章节
            List<Chapter> chapters = chapterMapper.selectByBookId(bookId);
            log.info("【向量化】读取到 {} 个章节", chapters.size());

            // 3. 顺序分块
            List<Document> documents = chapters.stream()
                    .flatMap(chapter -> splitAndEmbed(userId, bookId, chapter).stream())
                    .toList();
            log.info("【向量化】分块完成，共 {} 个文档块", documents.size());

            // 4. 批量写入
            if (!documents.isEmpty()) {
                int batchSize = 64;
                for (int i = 0; i < documents.size(); i += batchSize) {
                    int end = Math.min(i + batchSize, documents.size());
                    try {
                        hybridVectorService.addDocuments(collection, documents.subList(i, end));
                        log.info("【向量化】第 {}-{} 块写入 Qdrant 成功", i, end);
                    } catch (Exception e) {
                        log.error("【向量化】Qdrant 写入失败 bookId={}, 批次 {}-{}", bookId, i, end, e);
                        if (e.getMessage() != null && e.getMessage().contains("429")) {
                            log.warn("遇限流，等待 5s 后重试...");
                            try { Thread.sleep(5000); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
                            hybridVectorService.addDocuments(collection, documents.subList(i, end));
                        } else {
                            throw e;
                        }
                    }
                    int progress = 33 + (int) ((double) end / documents.size() * 33);
                    bookMapper.updateStatusProgress(bookId, 2, progress, "向量化中 " + end + "/" + documents.size());
                    if (i + batchSize < documents.size()) {
                        try { Thread.sleep(300); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
                    }
                }
                log.info("【向量化】全部写入完成 bookId={}, 文档数={}", bookId, documents.size());
                // 重建 HNSW 索引（写入时 m=0 禁用索引，写入后恢复）
                hybridVectorService.rebuildIndex(collection);
                bookMapper.updateStatusProgress(bookId, 2, 66, "向量化完成");
            } else {
                log.warn("【向量化】无文档块可写入 bookId={}", bookId);
                throw new RuntimeException("无文档块可写入，章节内容可能为空");
            }

        } catch (Exception e) {
            log.error("【向量化】失败 bookId={}", bookId, e);
            String errMsg = e.getMessage();
            String friendlyMsg = "向量化失败";
            if (errMsg != null) {
                if (errMsg.contains("timeout") || errMsg.contains("Timeout")) {
                    friendlyMsg = "向量化超时：嵌入服务响应过慢，请检查网络";
                } else if (errMsg.contains("Connection refused")) {
                    friendlyMsg = "向量化失败：无法连接 Qdrant";
                } else {
                    friendlyMsg = "向量化失败: " + (errMsg.length() > 100 ? errMsg.substring(0, 100) : errMsg);
                }
            }
            bookMapper.updateStatusProgress(bookId, 4, 0, friendlyMsg);
        }
    }

    /**
     * 将章节分块
     */
    private List<Document> splitAndEmbed(Long userId, Long bookId, Chapter chapter) {
        List<Document> documents = new ArrayList<>();

        String content = chapter.getContent();
        if (content == null || content.trim().isEmpty()) {
            return documents;
        }

        List<String> chunks = splitText(content, chunkSize, chunkOverlap);

        for (int i = 0; i < chunks.size(); i++) {
            String chunk = chunks.get(i);

            String enhanced = "[TITLE] " + chapter.getTitle() + " [/TITLE]\n" + chunk;
            Document doc = new Document(
                    enhanced,
                    Map.of(
                            "type", "chapter",
                            "userId", userId.toString(),
                            "bookId", bookId.toString(),
                            "bookTitle", chapter.getTitle(),
                            "chapterId", chapter.getId().toString(),
                            "chapterNumber", chapter.getChapterNumber().toString(),
                            "chapterTitle", chapter.getTitle(),
                            "chunkIndex", String.valueOf(i)
                    )
            );
            documents.add(doc);
        }

        return documents;
    }

    /**
     * 智能文本分块 — 段落感知滑动窗口
     */
    private List<String> splitText(String text, int chunkSize, int overlap) {
        List<String> chunks = new ArrayList<>();
        String[] paragraphs = text.split("\n+");

        List<String> merged = new ArrayList<>();
        StringBuilder buf = new StringBuilder();
        for (String p : paragraphs) {
            p = p.trim();
            if (p.isEmpty()) continue;
            if (buf.length() + p.length() + 1 > chunkSize * 1.5) {
                if (buf.length() > 0) { merged.add(buf.toString().trim()); buf = new StringBuilder(); }
            }
            if (buf.length() > 0) buf.append("\n");
            buf.append(p);
        }
        if (buf.length() > 0) merged.add(buf.toString().trim());

        for (int i = 0; i < merged.size(); ) {
            StringBuilder chunk = new StringBuilder();
            int j = i;
            while (j < merged.size() && chunk.length() + merged.get(j).length() + (j > i ? 1 : 0) <= chunkSize) {
                if (chunk.length() > 0) chunk.append("\n");
                chunk.append(merged.get(j));
                j++;
            }
            if (chunk.length() == 0 && j < merged.size()) {
                String longPara = merged.get(j);
                for (int start = 0; start < longPara.length(); start += chunkSize - overlap) {
                    int end = Math.min(start + chunkSize, longPara.length());
                    chunks.add(longPara.substring(start, end).trim());
                }
                j++;
            } else {
                if (chunk.length() > 0) chunks.add(chunk.toString().trim());
            }
            i = Math.max(i + 1, j - (overlap > 200 ? 1 : 0));
        }

        return chunks;
    }

    /**
     * 向量化笔记 — 存入本书的 collection
     */
    @Transactional(rollbackFor = Exception.class)
    public void vectorizeNote(Note note) {
        try {
            Document doc = new Document(
                    note.getContent(),
                    Map.of(
                            "type", "note",
                            "userId", note.getUserId().toString(),
                            "bookId", note.getBookId().toString(),
                            "noteId", note.getId().toString(),
                            "category", note.getCategory(),
                            "quoteText", note.getQuoteText()
                    )
            );
            hybridVectorService.addDocuments(collName(note.getBookId()), List.of(doc));
            log.info("笔记向量化完成: noteId={}", note.getId());
        } catch (Exception e) {
            log.error("笔记向量化失败: noteId={}", note.getId(), e);
        }
    }

    /**
     * 语义搜索书籍内容
     */
    public List<Document> searchBookContent(Long userId, Long bookId, String query, int topK) {
        try {
            String filter = String.format("userId == '%s' && bookId == '%s'", userId, bookId);
            return hybridVectorService.hybridSearch(collName(bookId), query, topK, filter);
        } catch (Exception e) {
            log.error("书籍内容搜索失败", e);
            return List.of();
        }
    }

    /**
     * 语义搜索笔记
     */
    public List<Document> searchNotes(Long userId, Long bookId, String query, int topK) {
        try {
            String filter = String.format("userId == '%s' && bookId == '%s' && type == 'note'", userId, bookId);
            return hybridVectorService.hybridSearch(collName(bookId), query, topK, filter);
        } catch (Exception e) {
            log.error("笔记搜索失败", e);
            return List.of();
        }
    }

    /**
     * 全局语义搜索 — 遍历所有 book collection
     */
    public List<Document> globalSearch(Long userId, String query, int topK) {
        try {
            String filter = String.format("userId == '%s'", userId);
            return hybridVectorService.globalSearch(query, topK, filter);
        } catch (Exception e) {
            log.error("全局搜索失败", e);
            return List.of();
        }
    }

    /**
     * 删除书籍的向量数据 — 直接删除整本书的 collection
     */
    public void deleteBookVectors(Long userId, Long bookId) {
        try {
            String filter = String.format("userId == '%s' && bookId == '%s'", userId, bookId);
            hybridVectorService.deleteByFilter(collName(bookId), filter);
            log.info("删除书籍向量成功: userId={}, bookId={}", userId, bookId);
        } catch (Exception e) {
            log.error("删除书籍向量失败 bookId={}", bookId, e);
        }
    }

    /**
     * 从本书 collection 中删除单条笔记向量
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteNoteVectors(Long bookId, Long noteId) {
        try {
            String filter = String.format("noteId == '%s'", noteId);
            hybridVectorService.deleteByFilter(collName(bookId), filter);
            log.info("删除笔记向量: noteId={}", noteId);
        } catch (Exception e) {
            log.error("删除笔记向量失败 noteId={}", noteId, e);
        }
    }

    /**
     * 获取向量统计信息
     */
    public Map<String, Object> getVectorStats(Long userId) {
        Map<String, Object> stats = new HashMap<>();
        stats.put("userId", userId);
        stats.put("collections", hybridVectorService.listBookCollections());
        return stats;
    }
}