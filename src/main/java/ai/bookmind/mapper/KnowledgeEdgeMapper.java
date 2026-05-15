package ai.bookmind.mapper;

import ai.bookmind.entity.KnowledgeEdge;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * 知识图谱边Mapper接口
 */
@Mapper
public interface KnowledgeEdgeMapper {

    /**
     * 插入边
     */
    @Insert("""
        INSERT INTO kg_edge (user_id, book_id, source_node_id, target_node_id, relation, description, weight)
        VALUES (#{userId}, #{bookId}, #{sourceNodeId}, #{targetNodeId}, #{relation}, #{description}, #{weight})
    """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(KnowledgeEdge edge);

    /**
     * 批量插入边
     */
    @Insert("""
        INSERT INTO kg_edge (user_id, book_id, source_node_id, target_node_id, relation, description, weight)
        VALUES
        <foreach collection="edges" item="edge" separator=",">
            (#{edge.userId}, #{edge.bookId}, #{edge.sourceNodeId}, #{edge.targetNodeId}, #{edge.relation}, #{edge.description}, #{edge.weight})
        </foreach>
    """)
    int insertBatch(@Param("edges") List<KnowledgeEdge> edges);

    /**
     * 根据ID查询边
     */
    @Select("SELECT * FROM kg_edge WHERE id = #{id}")
    KnowledgeEdge selectById(@Param("id") Long id);

    /**
     * 根据书籍ID查询所有边
     */
    @Select("SELECT * FROM kg_edge WHERE user_id = #{userId} AND book_id = #{bookId}")
    List<KnowledgeEdge> selectByBookId(@Param("userId") Long userId, @Param("bookId") Long bookId);

    /**
     * 根据源节点查询边
     */
    @Select("SELECT * FROM kg_edge WHERE source_node_id = #{sourceNodeId}")
    List<KnowledgeEdge> selectBySourceNode(@Param("sourceNodeId") Long sourceNodeId);

    /**
     * 根据目标节点查询边
     */
    @Select("SELECT * FROM kg_edge WHERE target_node_id = #{targetNodeId}")
    List<KnowledgeEdge> selectByTargetNode(@Param("targetNodeId") Long targetNodeId);

    /**
     * 更新边权重
     */
    @Update("UPDATE kg_edge SET weight = weight + 1 WHERE id = #{id}")
    int incrementWeight(@Param("id") Long id);

    /**
     * 删除边
     */
    @Delete("DELETE FROM kg_edge WHERE id = #{id}")
    int deleteById(@Param("id") Long id);

    /**
     * 删除源节点的出边
     */
    @Delete("DELETE FROM kg_edge WHERE source_node_id = #{sourceNodeId}")
    int deleteBySourceNode(@Param("sourceNodeId") Long sourceNodeId);

    /**
     * 删除目标节点的入边
     */
    @Delete("DELETE FROM kg_edge WHERE target_node_id = #{targetNodeId}")
    int deleteByTargetNode(@Param("targetNodeId") Long targetNodeId);

    /**
     * 删除书籍的所有边
     */
    @Delete("DELETE FROM kg_edge WHERE user_id = #{userId} AND book_id = #{bookId}")
    int deleteByBookId(@Param("userId") Long userId, @Param("bookId") Long bookId);

    /**
     * 统计边数
     */
    @Select("SELECT COUNT(*) FROM kg_edge WHERE user_id = #{userId} AND book_id = #{bookId}")
    int countByBookId(@Param("userId") Long userId, @Param("bookId") Long bookId);

    /**
     * 检查边是否存在
     */
    @Select("SELECT COUNT(*) FROM kg_edge WHERE source_node_id = #{sourceNodeId} AND target_node_id = #{targetNodeId}")
    int countExists(@Param("sourceNodeId") Long sourceNodeId, @Param("targetNodeId") Long targetNodeId);
}
