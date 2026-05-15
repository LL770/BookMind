package ai.bookmind.service;

import ai.bookmind.entity.Book;
import ai.bookmind.entity.Chapter;
import ai.bookmind.entity.Note;
import ai.bookmind.mapper.BookMapper;
import ai.bookmind.mapper.ChapterMapper;
import ai.bookmind.mapper.NoteMapper;
import ai.bookmind.ai.AiChatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * AI 摘要服务
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SummaryService {

    private final AiChatService aiChatService;
    private final BookMapper bookMapper;
    private final ChapterMapper chapterMapper;
    private final NoteMapper noteMapper;
    private final ChatClient chatClient;

    /**
     * 生成章节摘要（同步）
     */
    public String generateChapterSummary(Long userId, Long bookId, Integer chapterNumber) {
        return aiChatService.generateChapterSummary(userId, bookId, chapterNumber);
    }

    /**
     * 生成全书摘要（异步）
     */
    @Transactional(rollbackFor = Exception.class)
    @Async
    public void generateBookSummaryAsync(Long userId, Long bookId) {
        try {
            log.info("开始生成全书摘要: bookId={}", bookId);

            // 1. 生成摘要
            String summary = aiChatService.generateBookSummary(userId, bookId);

            // 2. 保存为笔记
            Book book = bookMapper.selectById(bookId);
            if (book != null) {
                Note summaryNote = new Note();
                summaryNote.setUserId(userId);
                summaryNote.setBookId(bookId);
                summaryNote.setChapterId(null);
                summaryNote.setQuoteText("《" + book.getTitle() + "》全书摘要");
                summaryNote.setContent(summary);
                summaryNote.setCategory(Note.Category.QUOTE.name().toLowerCase());
                summaryNote.setCreateTime(LocalDateTime.now());
                summaryNote.setUpdateTime(LocalDateTime.now());

                noteMapper.insert(summaryNote);

                // 向量化
                // vectorizationService.vectorizeNote(summaryNote);

                log.info("全书摘要已保存为笔记: noteId={}, bookId={}", summaryNote.getId(), bookId);
            }

        } catch (Exception e) {
            log.error("生成全书摘要失败: bookId={}", bookId, e);
        }
    }

    /**
     * 生成章节对比分析
     */
    public String generateChapterComparison(Long userId, Long bookId, Integer chapterNumber, String userNote) {
        Chapter chapter = chapterMapper.selectByBookAndChapter(bookId, chapterNumber);
        if (chapter == null) {
            return "章节不存在";
        }

        String prompt = String.format("""
            请对比分析你的笔记和书中原文：

            【你的笔记】
            %s

            【书中原文】(第%d章: %s)
            %s

            请分析：
            1. 你的理解是否正确
            2. 是否有遗漏或误解
            3. 补充建议

            请开始分析：
            """, userNote, chapter.getChapterNumber(), chapter.getTitle(),
                chapter.getContent().substring(0, Math.min(3000, chapter.getContent().length())));

        // 调用AI分析对比
        try {
            return chatClient.prompt()
                    .system("你是一个阅读分析助手，帮助用户理解自己笔记与原文的差异。")
                    .user(prompt)
                    .call()
                    .chatResponse().getResult().getOutput().getContent();
        } catch (Exception e) {
            log.error("章节对比分析失败", e);
            return "对比分析生成失败，请稍后重试";
        }
    }

    /**
     * 批量生成所有章节摘要
     */
    @Transactional(rollbackFor = Exception.class)
    @Async
    public void generateAllChapterSummaries(Long userId, Long bookId) {
        List<Chapter> chapters = chapterMapper.selectByBookId(bookId);

        for (Chapter chapter : chapters) {
            try {
                String summary = generateChapterSummary(userId, bookId, chapter.getChapterNumber());

                // 保存为笔记
                Note summaryNote = new Note();
                summaryNote.setUserId(userId);
                summaryNote.setBookId(bookId);
                summaryNote.setChapterId(chapter.getId());
                summaryNote.setQuoteText("第" + chapter.getChapterNumber() + "章: " + chapter.getTitle());
                summaryNote.setContent(summary);
                summaryNote.setCategory(Note.Category.QUOTE.name().toLowerCase());
                summaryNote.setCreateTime(LocalDateTime.now());
                summaryNote.setUpdateTime(LocalDateTime.now());

                noteMapper.insert(summaryNote);

                log.info("章节摘要已保存: bookId={}, chapter={}", bookId, chapter.getChapterNumber());

                // 延时避免API限流
                Thread.sleep(1000);

            } catch (Exception e) {
                log.error("生成章节摘要失败: bookId={}, chapter={}", bookId, chapter.getChapterNumber(), e);
            }
        }

        log.info("所有章节摘要生成完成: bookId={}", bookId);
    }

    /**
     * 生成阅读建议
     */
    public String generateReadingSuggestions(Long userId, Long bookId) {
        Book book = bookMapper.selectById(bookId);
        if (book == null) {
            return "书籍不存在";
        }

        // 获取用户笔记
        List<Note> notes = noteMapper.selectByBookId(userId, bookId);

        String prompt = String.format("""
            基于用户在《%s》中的阅读笔记，生成个性化阅读建议：

            用户笔记数量: %d条

            笔记列表:
            %s

            请给出：
            1. 重点章节推荐
            2. 需要深入理解的概念
            3. 相关延伸阅读建议

            请开始建议：
            """, book.getTitle(), notes.size(),
                notes.stream()
                        .limit(5)
                        .map(n -> "- " + n.getQuoteText().substring(0, Math.min(50, n.getQuoteText().length())))
                        .toList());

        return "基于您的阅读笔记，建议您重点关注..."; // 简化实现
    }
}
