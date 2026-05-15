package ai.bookmind.service;

import ai.bookmind.entity.BookCategory;
import ai.bookmind.mapper.BookCategoryMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class BookCategoryService {

    private final BookCategoryMapper bookCategoryMapper;

    /**
     * 内置分类定义
     */
    private static final String[][] BUILTIN_CATEGORIES = {
        {"全部", "📂"},
        {"小说", "📖"},
        {"技术/编程", "💻"},
        {"历史/社科", "📚"},
        {"科普/科学", "🔬"},
        {"商业/经济", "💼"},
        {"艺术/文学", "🎨"},
        {"生活/心理", "🧘"},
        {"其他", "📂"},
    };

    /**
     * 初始化用户的内置分类（首次登录时调用）
     */
    @Transactional(rollbackFor = Exception.class)
    public void initBuiltinCategories(Long userId) {
        int count = bookCategoryMapper.countByUserId(userId);
        if (count > 0) return;

        for (int i = 0; i < BUILTIN_CATEGORIES.length; i++) {
            BookCategory cat = new BookCategory();
            cat.setUserId(userId);
            cat.setName(BUILTIN_CATEGORIES[i][0]);
            cat.setEmoji(BUILTIN_CATEGORIES[i][1]);
            cat.setBuiltin(1);
            cat.setSortOrder(i);
            cat.setCreateTime(LocalDateTime.now());
            bookCategoryMapper.insert(cat);
        }
        log.info("为用户 {} 初始化内置分类", userId);
    }

    /**
     * 获取用户全部分类（内置 + 自定义，合并排序）
     */
    public List<BookCategory> getAllCategories(Long userId) {
        return bookCategoryMapper.selectByUserId(userId);
    }

    /**
     * 获取用户自定义分类
     */
    public List<BookCategory> getCustomCategories(Long userId) {
        return bookCategoryMapper.selectCustomByUserId(userId);
    }

    /**
     * 创建自定义分类
     */
    @Transactional(rollbackFor = Exception.class)
    public BookCategory create(Long userId, String name, String emoji) {
        List<BookCategory> existing = bookCategoryMapper.selectByUserId(userId);
        if (existing.stream().anyMatch(c -> c.getName().equals(name))) {
            throw new IllegalArgumentException("分类名称已存在: " + name);
        }
        BookCategory cat = new BookCategory();
        cat.setUserId(userId);
        cat.setName(name);
        cat.setEmoji(emoji != null ? emoji : "📖");
        cat.setBuiltin(0);
        cat.setSortOrder((int) (bookCategoryMapper.countByUserId(userId) + 1));
        cat.setCreateTime(LocalDateTime.now());
        bookCategoryMapper.insert(cat);
        log.info("分类创建成功：id={}, userId={}, name={}", cat.getId(), userId, name);
        return cat;
    }

    /**
     * 更新分类
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean update(Long userId, Long categoryId, String name, String emoji) {
        BookCategory existing = bookCategoryMapper.selectById(categoryId);
        if (existing == null || !existing.getUserId().equals(userId)) return false;

        if (name != null) existing.setName(name);
        if (emoji != null) existing.setEmoji(emoji);
        bookCategoryMapper.update(existing);
        return true;
    }

    /**
     * 更新分类排序
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean reorder(Long userId, List<Long> categoryIds) {
        for (int i = 0; i < categoryIds.size(); i++) {
            bookCategoryMapper.updateSortOrder(categoryIds.get(i), userId, i);
        }
        return true;
    }

    /**
     * 删除分类
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean delete(Long userId, Long categoryId) {
        BookCategory existing = bookCategoryMapper.selectById(categoryId);
        if (existing == null || !existing.getUserId().equals(userId)) return false;

        bookCategoryMapper.deleteById(categoryId, userId);
        log.info("分类删除成功：id={}, userId={}", categoryId, userId);
        return true;
    }
}
