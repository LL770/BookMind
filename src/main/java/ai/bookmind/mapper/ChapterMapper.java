package ai.bookmind.mapper;

import ai.bookmind.entity.Chapter;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * 章节Mapper接口
 */
@Mapper
public interface ChapterMapper {

    /**
     * 插入章节
     */
    @Insert("""
        INSERT INTO chapter (book_id, chapter_number, title, content)
        VALUES (#{bookId}, #{chapterNumber}, #{title}, #{content})
    """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Chapter chapter);

    /**
     * 批量插入章节
     */
    @Insert("""
        INSERT INTO chapter (book_id, chapter_number, title, content)
        VALUES
        <foreach collection="chapters" item="chapter" separator=",">
            (#{chapter.bookId}, #{chapter.chapterNumber}, #{chapter.title}, #{chapter.content})
        </foreach>
    """)
    int insertBatch(@Param("chapters") List<Chapter> chapters);

    /**
     * 根据书籍ID查询所有章节
     */
    @Select("SELECT id, book_id, chapter_number, title, content, create_time FROM chapter WHERE book_id = #{bookId} ORDER BY chapter_number")
    List<Chapter> selectByBookId(@Param("bookId") Long bookId);

    /**
     * 根据ID查询章节
     */
    @Select("SELECT id, book_id, chapter_number, title, content, create_time FROM chapter WHERE id = #{id}")
    Chapter selectById(@Param("id") Long id);

    /**
     * 根据书籍ID和章节号查询
     */
    @Select("SELECT id, book_id, chapter_number, title, content, create_time FROM chapter WHERE book_id = #{bookId} AND chapter_number = #{chapterNumber}")
    Chapter selectByBookAndChapter(@Param("bookId") Long bookId, @Param("chapterNumber") Integer chapterNumber);

    /**
     * MySQL FULLTEXT 搜索（走全文索引，比 LIKE 快 10-100 倍）
     */
    @Select("SELECT id, book_id, chapter_number, title, content FROM chapter WHERE book_id = #{bookId} AND MATCH(content) AGAINST(CONCAT('+', REPLACE(#{keyword}, ' ', ' +'), '*') IN BOOLEAN MODE) ORDER BY chapter_number LIMIT #{limit}")
    List<Chapter> searchContentFulltext(@Param("bookId") Long bookId, @Param("keyword") String keyword, @Param("limit") int limit);

    /**
     * 全局 FULLTEXT 搜索所有书籍
     */
    @Select("SELECT id, book_id, chapter_number, title, SUBSTRING(content, 1, 500) as content FROM chapter WHERE MATCH(content) AGAINST(CONCAT('+', REPLACE(#{keyword}, ' ', ' +'), '*') IN BOOLEAN MODE) ORDER BY book_id, chapter_number LIMIT #{limit}")
    List<Chapter> searchAllContentFulltext(@Param("keyword") String keyword, @Param("limit") int limit);

    /**
     * 更新章节内容
     */
    @Update("UPDATE chapter SET content = #{content} WHERE id = #{id}")
    int updateContent(@Param("id") Long id, @Param("content") String content);

    /**
     * 删除书籍的所有章节
     */
    @Delete("DELETE FROM chapter WHERE book_id = #{bookId}")
    int deleteByBookId(@Param("bookId") Long bookId);

    /**
     * 统计章节数
     */
    @Select("SELECT COUNT(*) FROM chapter WHERE book_id = #{bookId}")
    int countByBookId(@Param("bookId") Long bookId);
}
