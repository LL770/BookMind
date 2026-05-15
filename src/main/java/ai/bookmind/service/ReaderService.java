package ai.bookmind.service;

import ai.bookmind.entity.Book;
import ai.bookmind.entity.Chapter;
import ai.bookmind.entity.Note;
import ai.bookmind.mapper.BookMapper;
import ai.bookmind.mapper.ChapterMapper;
import ai.bookmind.mapper.NoteMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CompletableFuture;

/**
 * 阅读器服务
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReaderService {

    private final BookMapper bookMapper;
    private final ChapterMapper chapterMapper;
    private final NoteMapper noteMapper;
    private final RedisTemplate<String, Object> redisTemplate;

    // 章节内容缓存：key = chapter:content:{bookId}:{chapterNumber}, value = Chapter 对象
    private static final String CHAPTER_CACHE_PREFIX = "chapter:content:";
    private static final long CHAPTER_CACHE_TTL = 7200; // 2小时，正常阅读会话足够
    // 预拉取个数（当前章后连续预取）
    private static final int PREWARM_COUNT = 5;

    /** 从 Redis 取缓存章节，null 表示未命中 */
    private Chapter getCachedChapter(Long bookId, Integer chapterNumber) {
        String key = CHAPTER_CACHE_PREFIX + bookId + ":" + chapterNumber;
        Object obj = redisTemplate.opsForValue().get(key);
        if (obj instanceof Chapter) return (Chapter) obj;
        return null;
    }

    /** 写入 Redis 章节缓存 */
    private void cacheChapter(Chapter chapter) {
        if (chapter == null || chapter.getId() == null) return;
        String key = CHAPTER_CACHE_PREFIX + chapter.getBookId() + ":" + chapter.getChapterNumber();
        redisTemplate.opsForValue().set(key, chapter, CHAPTER_CACHE_TTL, TimeUnit.SECONDS);
    }

    /** 预取前后章节（异步，缓存到 Redis） */
    private void prewarmChapters(Long bookId, Integer fromChapter) {
        CompletableFuture.runAsync(() -> {
            // 向前预取 5 章
            for (int i = 1; i <= PREWARM_COUNT; i++) {
                if (!prewarmSingleChapter(bookId, fromChapter + i)) break;
            }
            // 向后预取 5 章
            for (int i = 1; i <= PREWARM_COUNT; i++) {
                if (fromChapter - i < 1) break;
                prewarmSingleChapter(bookId, fromChapter - i);
            }
        });
    }

    private boolean prewarmSingleChapter(Long bookId, int chapterNum) {
        String key = CHAPTER_CACHE_PREFIX + bookId + ":" + chapterNum;
        if (Boolean.TRUE.equals(redisTemplate.hasKey(key))) return true;
        try {
            Chapter ch = chapterMapper.selectByBookAndChapter(bookId, chapterNum);
            if (ch != null) {
                cacheChapter(ch);
                log.debug("预取章节缓存: bookId={}, ch={}", bookId, chapterNum);
                return true;
            }
        } catch (Exception e) {
            log.warn("预取章节失败 bookId={}, ch={}", bookId, chapterNum, e);
        }
        return false;
    }

    /**
     * 获取阅读器信息
     */
    public Map<String, Object> getReaderInfo(Long userId, Long bookId) {
        Book book = bookMapper.selectById(bookId);
        if (book == null || !book.getUserId().equals(userId)) {
            return Map.of("error", "书籍不存在或无权限");
        }

        Map<String, Object> info = new HashMap<>();
        info.put("bookId", book.getId());
        info.put("title", book.getTitle());
        info.put("author", book.getAuthor());
        info.put("category", book.getCategory());
        info.put("totalPages", book.getTotalPages());
        info.put("status", book.getStatus());
        info.put("isReady", book.isReady());

        // 获取章节列表
        List<Chapter> chapters = chapterMapper.selectByBookId(bookId);
        List<Map<String, Object>> chapterList = chapters.stream()
                .map(ch -> {
                    Map<String, Object> c = new HashMap<>();
                    c.put("id", ch.getId());
                    c.put("chapterNumber", ch.getChapterNumber());
                    c.put("title", ch.getTitle());
                    return c;
                })
                .toList();
        info.put("chapters", chapterList);

        // 获取阅读进度
        Map<String, Object> progress = getProgress(userId, bookId);
        info.put("progress", progress);

        // 获取笔记计数
        info.put("noteCount", noteMapper.countByBookId(bookId));

        // 预热前几章（根据阅读进度决定起始章节）
        Map<String, Object> prog = progress;
        if (prog != null && prog.containsKey("chapterNumber")) {
            int startCh = prog.get("chapterNumber") instanceof Number
                    ? ((Number) prog.get("chapterNumber")).intValue() : 1;
            prewarmChapters(bookId, startCh);
        } else {
            prewarmChapters(bookId, 1);
        }

        return info;
    }

    /**
     * 获取章节内容（Redis 缓存 + 预取后续）
     */
    public Chapter getChapter(Long userId, Long bookId, Integer chapterNumber) {
        // 验证书籍权限
        Book book = bookMapper.selectById(bookId);
        if (book == null || !book.getUserId().equals(userId)) {
            return null;
        }
        // 1. 查缓存
        Chapter cached = getCachedChapter(bookId, chapterNumber);
        if (cached != null) {
            // 触发异步预取后续章节
            prewarmChapters(bookId, chapterNumber);
            return cached;
        }
        // 2. 查 DB
        Chapter chapter = chapterMapper.selectByBookAndChapter(bookId, chapterNumber);
        if (chapter != null) {
            cacheChapter(chapter);
            // 异步预取后续章节
            prewarmChapters(bookId, chapterNumber);
        }
        return chapter;
    }

    /**
     * 更新阅读进度
     */
    public void updateProgress(Long userId, Long bookId, Integer chapterNumber,
                               Double currentPage, Integer totalPages) {
        // 详细进度（含页码），供 Reader 组件恢复阅读位置
        String detailKey = "reading:detail:" + userId + ":" + bookId;
        // 百分比进度，供首页卡片显示 "已读 X%"
        String pctKey = "user:reading:progress:" + userId + ":" + bookId;

        // 过滤 NaN / Infinity
        if (currentPage == null || !Double.isFinite(currentPage)) return;
        int progressPercent = totalPages != null && totalPages > 0
                ? (int) Math.round(currentPage / totalPages * 100) : 0;

        Map<String, Object> progress = new HashMap<>();
        progress.put("chapterNumber", chapterNumber);
        progress.put("currentPage", currentPage);
        progress.put("totalPages", totalPages);
        progress.put("progressPercent", progressPercent);
        progress.put("lastReadTime", System.currentTimeMillis());

        redisTemplate.opsForValue().set(detailKey, progress, 7, TimeUnit.DAYS);
        // 同时写入百分比键，供首页 BookService.fillBookStats 读取
        redisTemplate.opsForValue().set(pctKey, progressPercent, 7, TimeUnit.DAYS);

        // 持久化到 MySQL（reading_progress），跨设备同步
        try {
            bookMapper.updateReadingProgress(bookId, progressPercent);
        } catch (Exception e) {
            log.warn("持久化阅读进度到 MySQL 失败 bookId={}", bookId, e);
        }

        // 更新阅读时长
        String durationKey = "bookmind:reading:duration:" + userId;
        redisTemplate.opsForValue().increment(durationKey, 1);

        log.debug("更新阅读进度: userId={}, bookId={}, page={}/{}", userId, bookId, currentPage, totalPages);
    }

    /**
     * 获取阅读进度
     */
    public Map<String, Object> getProgress(Long userId, Long bookId) {
        String key = "reading:detail:" + userId + ":" + bookId;
        Object obj = redisTemplate.opsForValue().get(key);

        if (obj instanceof Map) {
            return (Map<String, Object>) obj;
        }

        // 默认返回初始进度
        Map<String, Object> defaultProgress = new HashMap<>();
        defaultProgress.put("chapterNumber", 1);
        defaultProgress.put("currentPage", 1);
        defaultProgress.put("progressPercent", 0);
        return defaultProgress;
    }

    /**
     * 获取上一章
     */
    public Map<String, Object> getPreviousChapter(Long userId, Long bookId, Integer currentChapter) {
        if (currentChapter <= 1) {
            return Map.of("hasPrev", false);
        }

        Chapter prev = chapterMapper.selectByBookAndChapter(bookId, currentChapter - 1);
        if (prev == null) {
            return Map.of("hasPrev", false);
        }

        return Map.of(
                "hasPrev", true,
                "chapterId", prev.getId(),
                "chapterNumber", prev.getChapterNumber(),
                "title", prev.getTitle()
        );
    }

    /**
     * 获取下一章
     */
    public Map<String, Object> getNextChapter(Long userId, Long bookId, Integer currentChapter) {
        Chapter current = chapterMapper.selectByBookAndChapter(bookId, currentChapter);
        if (current == null) {
            return Map.of("hasNext", false);
        }

        Chapter next = chapterMapper.selectByBookAndChapter(bookId, currentChapter + 1);
        if (next == null) {
            return Map.of("hasNext", false);
        }

        return Map.of(
                "hasNext", true,
                "chapterId", next.getId(),
                "chapterNumber", next.getChapterNumber(),
                "title", next.getTitle()
        );
    }

    /**
     * 获取章节内的笔记
     */
    public Map<String, Object> getChapterAnnotations(Long userId, Long bookId, Integer chapterNumber) {
        Chapter chapter = chapterMapper.selectByBookAndChapter(bookId, chapterNumber);
        if (chapter == null) {
            return Map.of("error", "章节不存在");
        }

        Map<String, Object> result = new HashMap<>();
        result.put("chapterId", chapter.getId());
        result.put("chapterNumber", chapter.getChapterNumber());
        result.put("title", chapter.getTitle());

        // 获取笔记
        List<Note> notes = noteMapper.selectByBookId(userId, bookId);
        List<Note> chapterNotes = notes.stream()
                .filter(n -> n.getChapterId() != null && n.getChapterId().equals(chapter.getId()))
                .toList();
        List<Map<String, Object>> noteList = chapterNotes.stream()
                .map(n -> {
                    Map<String, Object> nt = new HashMap<>();
                    nt.put("id", n.getId());
                    nt.put("quoteText", n.getQuoteText());
                    nt.put("content", n.getContent());
                    nt.put("category", n.getCategory());
                    nt.put("createTime", n.getCreateTime());
                    return nt;
                })
                .toList();
        result.put("notes", noteList);

        return result;
    }

    /**
     * 在章节内搜索
     */
    public List<Map<String, Object>> searchInChapter(Long userId, Long bookId, Integer chapterNumber, String keyword) {
        Chapter chapter = chapterMapper.selectByBookAndChapter(bookId, chapterNumber);
        if (chapter == null || chapter.getContent() == null) {
            return List.of();
        }

        String content = chapter.getContent();
        String lowerContent = content.toLowerCase();
        String lowerKeyword = keyword.toLowerCase();

        List<Map<String, Object>> results = new ArrayList<>();
        int index = lowerContent.indexOf(lowerKeyword);
        
        while (index >= 0) {
            Map<String, Object> match = new HashMap<>();
            match.put("startIndex", index);
            match.put("endIndex", index + keyword.length());
            
            // 获取上下文（前后50字符）
            int contextStart = Math.max(0, index - 50);
            int contextEnd = Math.min(content.length(), index + keyword.length() + 50);
            match.put("context", content.substring(contextStart, contextEnd));
            
            results.add(match);
            
            index = lowerContent.indexOf(lowerKeyword, index + 1);
        }

        return results;
    }
}
