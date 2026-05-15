package ai.bookmind.ai;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 模型路由器 — SiliconFlow 版
 *
 * Key-C: Qwen/Qwen3-8B → 日常对话、RAG、摘要、工具调用
 * Key-S: THUDM/GLM-Z1-9B-0414 → 知识图谱抽取
 */
@Component
@Slf4j
public class ModelRouter {

    public enum ModelType {
        CHAT,       // Key-C: Qwen/Qwen3-8B
        SPECIAL     // Key-S: THUDM/GLM-Z1-9B-0414
    }

    /**
     * 根据场景选择模型
     */
    public ModelType select(String scene, String message) {
        if (message == null) message = "";

        // 知识图谱抽取 → 专用模型
        if ("knowledge_graph".equals(scene)) {
            log.debug("【模型路由】知识图谱场景 → 专用模型 (GLM-Z1)");
            return ModelType.SPECIAL;
        }

        // 其余全部走主力对话模型
        log.debug("【模型路由】默认 → 对话模型 (Qwen3-8B)");
        return ModelType.CHAT;
    }

    public static String getModelDisplayName(ModelType type) {
        return switch (type) {
            case CHAT -> "CHAT";
            case SPECIAL -> "SPECIAL";
        };
    }
}
