package ai.bookmind.service;

import ai.bookmind.common.PageResult;
import ai.bookmind.entity.Book;
import ai.bookmind.entity.Chapter;
import ai.bookmind.mapper.BookMapper;
import ai.bookmind.mapper.ChapterMapper;
import ai.bookmind.mapper.NoteMapper;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 书籍服务实现
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BookService {

    private final BookMapper bookMapper;
    private final ChapterMapper chapterMapper;
    private final NoteMapper noteMapper;
    private final ReadingStatsService readingStatsService;

    /**
     * 从请求上下文中获取当前用户ID
     */
    public Long getCurrentUserId() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes != null) {
            HttpServletRequest request = attributes.getRequest();
            Object userIdObj = request.getAttribute("userId");
            if (userIdObj instanceof Long) {
                return (Long) userIdObj;
            }
        }
        return null;
    }

    /**
     * 获取用户书籍列表（分页）
     */
    public PageResult<Book> getUserBooks(Long userId, Integer page, Integer size, String category) {
        PageHelper.startPage(page, size);
        
        List<Book> books;
        if (category != null && !category.isEmpty()) {
            books = bookMapper.selectByUserIdAndCategory(userId, category);
        } else {
            books = bookMapper.selectByUserId(userId);
        }

        PageInfo<Book> pageInfo = new PageInfo<>(books);

        // 补充笔记和书签计数
        for (Book book : books) {
            fillBookStats(book, userId);
        }

        return PageResult.of(page, size, pageInfo.getTotal(), books);
    }

    /**
     * 获取书籍详情
     */
    public Book getBookById(Long userId, Long bookId) {
        Book book = bookMapper.selectById(bookId);
        if (book != null && book.getUserId().equals(userId)) {
            fillBookStats(book, userId);
        }
        return book;
    }

    /**
     * 填充书籍统计信息
     */
    private void fillBookStats(Book book, Long userId) {
        // 笔记数
        Integer noteCount = noteMapper.countByBookId(book.getId());
        book.setTotalNotes(noteCount);

        // 阅读进度：Redis → MySQL book.progress 兜底
        Integer progress = readingStatsService.getProgress(userId, book.getId());
        if (progress == null || progress == 0) {
            progress = book.getProgress() != null ? book.getProgress() : 0;
        }
        book.setReadProgress(progress);
    }

    /**
     * 获取书籍章节列表
     */
    public List<Chapter> getBookChapters(Long userId, Long bookId) {
        Book book = bookMapper.selectById(bookId);
        if (book == null || !book.getUserId().equals(userId)) {
            return List.of();
        }

        return chapterMapper.selectByBookId(bookId);
    }

    /**
     * 搜索书籍
     */
    public PageResult<Book> searchBooks(Long userId, String keyword, Integer page, Integer size) {
        PageHelper.startPage(page, size);
        List<Book> books = bookMapper.searchByUserId(userId, keyword);
        PageInfo<Book> pageInfo = new PageInfo<>(books);

        for (Book book : books) {
            fillBookStats(book, userId);
        }

        return PageResult.of(page, size, pageInfo.getTotal(), books);
    }

    /**
     * 更新书籍处理进度
     */
    public void updateBookProgress(Long bookId, Integer status, Integer progress, String message) {
        bookMapper.updateStatusProgress(bookId, status, progress, message);
    }

    /**
     * 更新阅读进度
     */
    public void updateReadingProgress(Long userId, Long bookId, Integer chapterNumber, Integer currentPage, Integer totalPages) {
        int progressPercent = totalPages > 0 ? (int) Math.round((double) currentPage / totalPages * 100) : 0;
        readingStatsService.updateProgress(userId, bookId, progressPercent);
    }

    /**
     * 获取阅读进度
     */
    public Integer getReadingProgress(Long userId, Long bookId) {
        return readingStatsService.getProgress(userId, bookId);
    }

    /**
     * 创建书籍（用于异步处理回调）
     */
    public Long createBookRecord(Long userId, String title, String author, String category, String fileUrl, Long fileSize, String format) {
        Book book = new Book();
        book.setUserId(userId);
        book.setTitle(title);
        book.setAuthor(author);
        book.setCategory(category);
        book.setFileUrl(fileUrl);
        book.setFileSize(fileSize);
        book.setFormat(format);
        book.setStatus(0);
        book.setProgress(10);
        book.setProcessMessage("文件已上传，等待处理...");
        book.setCreateTime(LocalDateTime.now());

        bookMapper.insert(book);
        
        return book.getId();
    }
}
