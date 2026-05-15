package ai.bookmind.mapper;

import ai.bookmind.entity.BookCategory;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface BookCategoryMapper {

    @Insert("""
        INSERT INTO book_category (user_id, name, emoji, builtin, sort_order)
        VALUES (#{userId}, #{name}, #{emoji}, #{builtin}, #{sortOrder})
    """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(BookCategory category);

    @Select("SELECT * FROM book_category WHERE id = #{id}")
    BookCategory selectById(@Param("id") Long id);

    @Select("SELECT * FROM book_category WHERE user_id = #{userId} ORDER BY builtin DESC, sort_order ASC, id ASC")
    List<BookCategory> selectByUserId(@Param("userId") Long userId);

    @Select("SELECT * FROM book_category WHERE user_id = #{userId} AND builtin = 1 ORDER BY sort_order ASC, id ASC")
    List<BookCategory> selectBuiltinByUserId(@Param("userId") Long userId);

    @Select("SELECT * FROM book_category WHERE user_id = #{userId} AND builtin = 0 ORDER BY sort_order ASC, id ASC")
    List<BookCategory> selectCustomByUserId(@Param("userId") Long userId);

    @Update("UPDATE book_category SET name = #{name}, emoji = #{emoji} WHERE id = #{id} AND user_id = #{userId}")
    int update(BookCategory category);

    @Update("UPDATE book_category SET sort_order = #{sortOrder} WHERE id = #{id} AND user_id = #{userId}")
    int updateSortOrder(@Param("id") Long id, @Param("userId") Long userId, @Param("sortOrder") Integer sortOrder);

    @Delete("DELETE FROM book_category WHERE id = #{id} AND user_id = #{userId}")
    int deleteById(@Param("id") Long id, @Param("userId") Long userId);

    @Select("SELECT COUNT(*) FROM book_category WHERE user_id = #{userId}")
    int countByUserId(@Param("userId") Long userId);
}
