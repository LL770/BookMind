package ai.bookmind.mapper;

import ai.bookmind.entity.Note;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * 笔记Mapper接口
 */
@Mapper
public interface NoteMapper {

    /**
     * 插入笔记
     */
    @Insert("""
        INSERT INTO note (user_id, book_id, chapter_id, quote_text, content, category, vector_id)
        VALUES (#{userId}, #{bookId}, #{chapterId}, #{quoteText}, #{content}, #{category}, #{vectorId})
    """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Note note);

    /**
     * 根据ID查询笔记
     */
    @Select("SELECT * FROM note WHERE id = #{id}")
    Note selectById(@Param("id") Long id);

    /**
     * 根据用户ID查询笔记（分页、手动LIMIT）
     */
    @Select("SELECT * FROM note WHERE user_id = #{userId} ORDER BY create_time DESC LIMIT #{offset}, #{size}")
    List<Note> selectByUserId(@Param("userId") Long userId, @Param("offset") Integer offset, @Param("size") Integer size);

    /**
     * 根据用户ID查询所有笔记（无LIMIT，配合PageHelper使用）
     */
    @Select("SELECT * FROM note WHERE user_id = #{userId} ORDER BY create_time DESC")
    List<Note> selectAllByUserId(@Param("userId") Long userId);

    /**
     * 根据书籍ID查询笔记
     */
    @Select("SELECT * FROM note WHERE user_id = #{userId} AND book_id = #{bookId} ORDER BY create_time DESC")
    List<Note> selectByBookId(@Param("userId") Long userId, @Param("bookId") Long bookId);

    /**
     * 根据分类查询笔记
     */
    @Select("SELECT * FROM note WHERE user_id = #{userId} AND category = #{category} ORDER BY create_time DESC")
    List<Note> selectByCategory(@Param("userId") Long userId, @Param("category") String category);

    @Select({"<script>", "SELECT id, book_id, user_id, content FROM note WHERE id IN",
      "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach>", "</script>"})
    List<Note> selectBatch(@Param("ids") List<Long> ids);

    /**
     * 搜索笔记
     */
    @Select("SELECT * FROM note WHERE user_id = #{userId} AND (content LIKE CONCAT('%', #{keyword}, '%') OR quote_text LIKE CONCAT('%', #{keyword}, '%')) ORDER BY create_time DESC")
    List<Note> search(@Param("userId") Long userId, @Param("keyword") String keyword);

    /**
     * 更新笔记
     */
    @Update("UPDATE note SET content = #{content}, category = #{category} WHERE id = #{id}")
    int update(Note note);

    /**
     * 删除笔记
     */
    @Delete("DELETE FROM note WHERE id = #{id}")
    int deleteById(@Param("id") Long id);

    /**
     * 批量删除笔记
     */
    @Delete({
        "<script>",
        "DELETE FROM note WHERE id IN",
        "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach>",
        "</script>"
    })
    int deleteBatch(@Param("ids") List<Long> ids);

    /**
     * 删除书籍的所有笔记
     */
    @Delete("DELETE FROM note WHERE book_id = #{bookId}")
    int deleteByBookId(@Param("bookId") Long bookId);

    /**
     * 删除用户所有笔记
     */
    @Delete("DELETE FROM note WHERE user_id = #{userId}")
    int deleteByUserId(@Param("userId") Long userId);

    /**
     * 统计用户笔记总数
     */
    @Select("SELECT COUNT(*) FROM note WHERE user_id = #{userId}")
    int countByUserId(@Param("userId") Long userId);

    /**
     * 统计书籍笔记数
     */
    @Select("SELECT COUNT(*) FROM note WHERE book_id = #{bookId}")
    int countByBookId(@Param("bookId") Long bookId);

    @Select({"<script>",
      "SELECT book_id, COUNT(*) AS cnt FROM note WHERE book_id IN",
      "<foreach collection='bookIds' item='id' open='(' separator=',' close=')'>#{id}</foreach>",
      "GROUP BY book_id", "</script>"})
    List<java.util.Map<String, Object>> countByBookIds(@Param("bookIds") List<Long> bookIds);

    /**
     * 统计分类笔记数
     */
    @Select("SELECT COUNT(*) FROM note WHERE user_id = #{userId} AND category = #{category}")
    int countByCategory(@Param("userId") Long userId, @Param("category") String category);

    /**
     * 更新向量ID
     */
    @Update("UPDATE note SET vector_id = #{vectorId} WHERE id = #{id}")
    int updateVectorId(@Param("id") Long id, @Param("vectorId") String vectorId);
}
