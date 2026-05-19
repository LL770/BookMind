package ai.bookmind.service;

import ai.bookmind.common.PageResult;
import ai.bookmind.entity.Note;
import ai.bookmind.mapper.NoteMapper;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 笔记服务实现
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NoteService {

    private final NoteMapper noteMapper;
    private final VectorizationService vectorizationService;

    /**
     * 创建笔记
     */
    @Transactional(rollbackFor = Exception.class)
    public Note createNote(Long userId, Long bookId, Long chapterId, 
                           String quoteText, String content, String category) {
        
        Note note = new Note();
        note.setUserId(userId);
        note.setBookId(bookId);
        note.setChapterId(chapterId);
        note.setQuoteText(quoteText);
        note.setContent(content);
        note.setCategory(category);
        note.setCreateTime(LocalDateTime.now(ZoneId.of("Asia/Shanghai")));
        note.setUpdateTime(LocalDateTime.now(ZoneId.of("Asia/Shanghai")));

        noteMapper.insert(note);

        // 异步向量化
        vectorizationService.vectorizeNote(note);

        log.info("笔记创建成功：noteId={}, userId={}, bookId={}", note.getId(), userId, bookId);

        return note;
    }

    /**
     * 获取笔记详情
     */
    public Note getNoteById(Long userId, Long noteId) {
        Note note = noteMapper.selectById(noteId);
        if (note != null && !note.getUserId().equals(userId)) {
            return null;
        }
        return note;
    }

    /**
     * 获取用户笔记列表（分页）
     */
    public PageResult<Note> getUserNotes(Long userId, Integer page, Integer size, 
                                          Long bookId, String category, String keyword) {
        PageHelper.startPage(page, size);

        List<Note> notes;
        if (bookId != null && category != null && !category.isEmpty()) {
            notes = noteMapper.selectByBookId(userId, bookId).stream()
                    .filter(n -> category.equals(n.getCategory())).toList();
        } else if (bookId != null) {
            notes = noteMapper.selectByBookId(userId, bookId);
        } else if (category != null && !category.isEmpty()) {
            notes = noteMapper.selectByCategory(userId, category);
        } else if (keyword != null && !keyword.isEmpty()) {
            notes = noteMapper.search(userId, keyword);
        } else {
            notes = noteMapper.selectAllByUserId(userId);
        }

        PageInfo<Note> pageInfo = new PageInfo<>(notes);
        return PageResult.of(page, size, pageInfo.getTotal(), notes);
    }

    /**
     * 更新笔记
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean updateNote(Long userId, Long noteId, String content, String category) {
        Note existing = noteMapper.selectById(noteId);
        if (existing == null || !existing.getUserId().equals(userId)) {
            return false;
        }

        existing.setContent(content);
        existing.setCategory(category);
        existing.setUpdateTime(LocalDateTime.now(ZoneId.of("Asia/Shanghai")));

        noteMapper.update(existing);
        
        // 重新向量化
        vectorizationService.vectorizeNote(existing);

        return true;
    }

    /**
     * 删除笔记
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteNote(Long userId, Long noteId) {
        Note existing = noteMapper.selectById(noteId);
        if (existing == null || !existing.getUserId().equals(userId)) {
            return false;
        }

        noteMapper.deleteById(noteId);

        // 删除向量数据
        vectorizationService.deleteNoteVectors(existing.getBookId(), noteId);

        log.info("笔记删除成功：noteId={}, userId={}", noteId, userId);
        return true;
    }

    /**
     * 批量删除笔记
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteBatch(Long userId, List<Long> noteIds) {
        if (noteIds == null || noteIds.isEmpty()) return;
        List<Note> notes = noteMapper.selectBatch(noteIds);
        for (Note existing : notes) {
            if (existing != null && existing.getUserId().equals(userId)) {
                noteMapper.deleteById(existing.getId());
                vectorizationService.deleteNoteVectors(existing.getBookId(), existing.getId());
            }
        }
        log.info("笔记批量删除成功：count={}, userId={}", noteIds.size(), userId);
    }

    /**
     * 获取书籍笔记统计
     */
    public Note.BookNoteStats getBookNoteStats(Long userId, Long bookId) {
        Note.BookNoteStats stats = new Note.BookNoteStats();
        stats.setBookId(bookId);

        // 总笔记数
        stats.setTotalNotes(noteMapper.countByBookId(bookId));

        // 各分类笔记数
        Map<String, Integer> categoryCounts = new HashMap<>();
        for (Note.Category cat : Note.Category.values()) {
            int count = noteMapper.countByCategory(userId, cat.name().toLowerCase());
            categoryCounts.put(cat.name(), count);
        }
        stats.setCategoryCounts(categoryCounts);

        return stats;
    }

    /**
     * 搜索笔记
     */
    public List<Note> searchNotes(Long userId, String keyword) {
        return noteMapper.search(userId, keyword);
    }

    /**
     * 获取最近笔记
     */
    public List<Note> getRecentNotes(Long userId, Integer limit) {
        PageHelper.startPage(1, limit);
        return noteMapper.selectAllByUserId(userId);
    }
}
