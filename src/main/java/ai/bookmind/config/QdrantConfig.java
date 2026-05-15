package ai.bookmind.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;

/**
 * Qdrant 集合初始化配置
 * 统一使用 bookmind_vectors 单 collection + payload 过滤
 * userId / bookId 已建 keyword 索引加速过滤
 */
@Configuration
@Slf4j
public class QdrantConfig {
    // 集合由 HybridVectorService.init() 创建 + 建索引，无需额外初始化
}
