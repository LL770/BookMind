package ai.bookmind.mapper;

import ai.bookmind.entity.KnowledgeNode;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * 知识图谱节点Mapper接口
 */
@Mapper
public interface KnowledgeNodeMapper {

    /**
     * 插入节点
     */
    @Insert("""
        INSERT INTO kg_node (user_id, book_id, name, type, description, first_chapter, occurrence_count)
        VALUES (#{userId}, #{bookId}, #{name}, #{type}, #{description}, #{firstChapter}, #{occurrenceCount})
    """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(KnowledgeNode node);

    /**
     * 批量插入节点
     */
    @Insert("""
        INSERT INTO kg_node (user_id, book_id, name, type, description, first_chapter, occurrence_count)
        VALUES
        <foreach collection="nodes" item="node" separator=",">
            (#{node.userId}, #{node.bookId}, #{node.name}, #{node.type}, #{node.description}, #{node.firstChapter}, #{node.occurrenceCount})
        </foreach>
    """)
    int insertBatch(@Param("nodes") List<KnowledgeNode> nodes);

    /**
     * 根据ID查询节点
     */
    @Select("SELECT * FROM kg_node WHERE id = #{id}")
    KnowledgeNode selectById(@Param("id") Long id);

    /**
     * 根据书籍ID查询所有节点
     */
    @Select("SELECT * FROM kg_node WHERE user_id = #{userId} AND book_id = #{bookId}")
    List<KnowledgeNode> selectByBookId(@Param("userId") Long userId, @Param("bookId") Long bookId);

    /**
     * 根据名称查询节点
     */
    @Select("SELECT * FROM kg_node WHERE user_id = #{userId} AND book_id = #{bookId} AND name = #{name}")
    KnowledgeNode selectByName(@Param("userId") Long userId, @Param("bookId") Long bookId, @Param("name") String name);

    /**
     * 更新节点出现次数
     */
    @Update("UPDATE kg_node SET occurrence_count = occurrence_count + 1 WHERE id = #{id}")
    int incrementOccurrence(@Param("id") Long id);

    /**
     * 更新节点信息
     */
    @Update("UPDATE kg_node SET description = #{description}, first_chapter = #{firstChapter} WHERE id = #{id}")
    int update(KnowledgeNode node);

    /**
     * 删除节点
     */
    @Delete("DELETE FROM kg_node WHERE id = #{id}")
    int deleteById(@Param("id") Long id);

    /**
     * 删除书籍的所有节点
     */
    @Delete("DELETE FROM kg_node WHERE user_id = #{userId} AND book_id = #{bookId}")
    int deleteByBookId(@Param("userId") Long userId, @Param("bookId") Long bookId);

    /**
     * 统计节点数
     */
    @Select("SELECT COUNT(*) FROM kg_node WHERE user_id = #{userId} AND book_id = #{bookId}")
    int countByBookId(@Param("userId") Long userId, @Param("bookId") Long bookId);
}
