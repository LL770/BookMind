package ai.bookmind.mapper;

import ai.bookmind.entity.Book;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * 书籍Mapper接口
 */
@Mapper
public interface BookMapper {

    @Insert("""
        INSERT INTO book (user_id, title, author, category, cover_url, file_url,
                         file_size, format, total_pages, total_words, status, progress, process_message)
        VALUES (#{userId}, #{title}, #{author}, #{category}, #{coverUrl}, #{fileUrl},
                #{fileSize}, #{format}, #{totalPages}, #{totalWords}, #{status}, #{progress}, #{processMessage})
    """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Book book);

    @Select("SELECT * FROM book WHERE id = #{id}")
    Book selectById(@Param("id") Long id);

    @Select("SELECT * FROM book WHERE user_id = #{userId} ORDER BY create_time DESC")
    List<Book> selectByUserId(@Param("userId") Long userId);

    @Select("SELECT * FROM book WHERE user_id = #{userId} AND category = #{category} ORDER BY create_time DESC")
    List<Book> selectByUserIdAndCategory(@Param("userId") Long userId, @Param("category") String category);

    @Select("SELECT * FROM book WHERE user_id = #{userId} ORDER BY create_time DESC LIMIT #{offset}, #{size}")
    List<Book> selectByUserIdPage(@Param("userId") Long userId, @Param("offset") Integer offset, @Param("size") Integer size);

    @Select("SELECT COUNT(*) FROM book WHERE user_id = #{userId}")
    int countByUserId(@Param("userId") Long userId);

    @Select("SELECT COUNT(*) FROM book WHERE user_id = #{userId} AND status = 3")
    int countCompletedByUserId(@Param("userId") Long userId);

    @Update("UPDATE book SET status = #{status}, progress = #{progress}, process_message = #{processMessage} WHERE id = #{id}")
    int updateStatusProgress(@Param("id") Long id, @Param("status") Integer status, @Param("progress") Integer progress, @Param("processMessage") String processMessage);

    @Update("UPDATE book SET cover_url = #{coverUrl} WHERE id = #{id}")
    int updateCoverUrl(@Param("id") Long id, @Param("coverUrl") String coverUrl);

    @Update("UPDATE book SET total_pages = #{totalPages}, total_words = #{totalWords} WHERE id = #{id}")
    int updateMetadata(@Param("id") Long id, @Param("title") String title, @Param("author") String author, @Param("totalPages") Integer totalPages, @Param("totalWords") Integer totalWords);

    @Update("UPDATE book SET title = #{title}, author = #{author}, category = #{category} WHERE id = #{id}")
    int updateBookInfo(@Param("id") Long id, @Param("title") String title, @Param("author") String author, @Param("category") String category);

    @Delete("DELETE FROM book WHERE id = #{id}")
    int deleteById(@Param("id") Long id);

    @Update("UPDATE book SET progress = #{readProgress} WHERE id = #{id}")
    int updateReadProgress(@Param("id") Long id, @Param("readProgress") Integer readProgress);

    @Select("SELECT * FROM book WHERE user_id = #{userId} AND (title LIKE CONCAT('%', #{keyword}, '%') OR author LIKE CONCAT('%', #{keyword}, '%')) ORDER BY create_time DESC")
    List<Book> searchByUserId(@Param("userId") Long userId, @Param("keyword") String keyword);

    @Select("SELECT * FROM book WHERE status = #{status} ORDER BY create_time DESC")
    List<Book> selectByStatus(@Param("status") Integer status);
}
