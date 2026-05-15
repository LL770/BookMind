package ai.bookmind.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.time.Duration;

/**
 * AI API 客户端 — 统一调用 SenseNova + SiliconFlow
 * 根据 providerConfig.getChatProvider() 自动路由：
 * - SenseNova: 聊天对话、知识图谱抽取、信息图生成
 * - SiliconFlow: 文本嵌入(embedding)、搜索结果重排序(rerank)、OCR
 */
@Component
@Slf4j
public class AiApiClient {

    /** 工具/函数定义 */
    public record ToolProperty(String type, String description) {}
    public record ToolFunction(String name, String description, Map<String, Object> parameters) {}
    public record ToolDefinition(String type, ToolFunction function) {
        public static ToolDefinition of(String name, String description, Map<String, Object> parameters) {
            return new ToolDefinition("function", new ToolFunction(name, description, parameters));
        }
    }

    public record ToolCallRequest(String id, String type, String functionName, String functionArgs) {}
    public record ToolCallResult(String role, String toolCallId, String content) {}

    private final ObjectMapper objectMapper;

    // ——— Provider 选择 ———
    @Autowired
    private ai.bookmind.config.ProviderConfig providerConfig;

    // ——— Chat 用 SenseNova ———
    @Value("${sensenova.api.base-url:https://token.sensenova.cn/v1}")
    private String senseNovaBaseUrl;

    @Value("${sensenova.api.chat-key:}")
    private String senseNovaChatKey;

    @Value("${sensenova.api.kg-key:}")
    private String senseNovaKgKey;

    // ——— Chat 用 SiliconFlow ———
    @Value("${siliconflow.api.base-url:https://api.siliconflow.cn/v1}")
    private String siliconFlowBaseUrl;

    @Value("${siliconflow.api.chat-key:}")
    private String siliconFlowChatKey;

    // ——— Embedding / 专用（始终 SiliconFlow） ———
    @Value("${siliconflow.api.embed-key}")
    private String embedKey;

    @Value("${siliconflow.api.special-key}")
    private String specialKey;

    @Value("${sensenova.api.ocr-model:sensenova-6.7-flash-lite}")
    private String senseNovaOcrModel;

    @Value("${sensenova.api.u1-model:sensenova-u1-fast}")
    private String senseNovaU1Model;

    @Value("${siliconflow.api.rerank-model:BAAI/bge-reranker-v2-m3}")
    private String rerankModel;

    @Value("${siliconflow.api.ocr-model:deepseek-ai/DeepSeek-OCR}")
    private String ocrModel;

    public AiApiClient() {
        this.objectMapper = new ObjectMapper();
    }

    /** 当前聊天 provider 的基础 URL */
    private String activeChatBaseUrl() {
        return "sensenova".equals(providerConfig.getChatProvider()) ? senseNovaBaseUrl : siliconFlowBaseUrl;
    }

    /** 当前聊天 provider 的 API Key */
    private String activeChatKey() {
        return "sensenova".equals(providerConfig.getChatProvider()) ? senseNovaChatKey : siliconFlowChatKey;
    }

    /** 当前聊天 provider 名称（日志用） */
    private String activeProviderName() {
        return "sensenova".equals(providerConfig.getChatProvider()) ? "SenseNova" : "SiliconFlow";
    }

    /** 使用当前 provider 的 Key 创建 WebClient */
    private WebClient chatWebClient() {
        return buildWebClient(activeChatBaseUrl(), activeChatKey());
    }

    /** 嵌入/专用 API（始终用 SiliconFlow） */
    private WebClient specialWebClient() {
        return buildWebClient(siliconFlowBaseUrl, specialKey);
    }

    /** SiliconFlow 聊天 API（始终用 SiliconFlow chat key，用于降级） */
    private WebClient siliconFlowChatWebClient() {
        return buildWebClient(siliconFlowBaseUrl, siliconFlowChatKey);
    }

    /** 使用指定 API Key 创建 WebClient */
    private WebClient buildWebClient(String baseUrl, String apiKey) {
        return WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    // ==================== 流式对话（Key-C 主力模型） ====================

    public Flux<String> chatStream(List<Map<String, String>> messages, String model, double temperature) {
        return chatStream(messages, model, temperature, null);
    }

    public Flux<String> chatStream(List<Map<String, String>> messages, String model,
                                    double temperature, String systemPrompt) {
        return doChatStream(chatWebClient(), messages, model, temperature, systemPrompt, true, null);
    }

    public Flux<String> chatStreamWithTools(
            List<Map<String, String>> messages, String model, double temperature,
            String systemPrompt, List<ToolDefinition> tools) {
        return doChatStream(chatWebClient(), messages, model, temperature, systemPrompt, true, tools);
    }

    /** 使用 Key-S 流式对话（专用模型，如知识图谱OCR） */
    public Flux<String> specialChatStream(List<Map<String, String>> messages, String model,
                                           double temperature, String systemPrompt) {
        return doChatStream(specialWebClient(), messages, model, temperature, systemPrompt, true, null);
    }

    /** SiliconFlow 降级流式对话（始终走 SiliconFlow chat key） */
    public Flux<String> siliconFlowChatStream(List<Map<String, String>> messages, String model,
                                               double temperature, String systemPrompt) {
        return doChatStream(siliconFlowChatWebClient(), messages, model, temperature, systemPrompt, true, null);
    }

    public Flux<String> siliconFlowChatStreamWithTools(
            List<Map<String, String>> messages, String model, double temperature,
            String systemPrompt, List<ToolDefinition> tools,
            Function<ToolCallRequest, String> toolExecutor) {
        WebClient sfClient = siliconFlowChatWebClient();
        String firstResponse;
        try {
            firstResponse = doChatSyncRaw(sfClient, messages, model, temperature, systemPrompt, tools);
        } catch (Exception e) {
            log.warn("SiliconFlow工具调用第一轮失败: {}", e.getMessage());
            return doChatStream(sfClient, messages, model, temperature, systemPrompt, true, null);
        }

        List<ToolCallRequest> toolCalls = parseToolCalls(firstResponse);
        if (toolCalls.isEmpty()) {
            try {
                var root = objectMapper.readTree(firstResponse);
                String content = root.path("choices").get(0).path("message").path("content").asText();
                if (content != null && !content.isEmpty()) {
                    return Flux.just(content);
                }
            } catch (Exception e) {
                log.warn("解析SiliconFlow首轮响应失败", e);
            }
            return doChatStream(sfClient, messages, model, temperature, systemPrompt, true, null);
        }

        List<Map<String, String>> toolMessages = new ArrayList<>(messages);
        try {
            var root = objectMapper.readTree(firstResponse);
            var choiceMsg = root.path("choices").get(0).path("message");
            Map<String, String> assistantMsg = Map.of("role", "assistant", "content",
                    choiceMsg.path("content").asText(""));
            toolMessages.add(assistantMsg);
        } catch (Exception e) {
            log.warn("解析SiliconFlow assistant消息失败", e);
        }

        StringBuilder toolResultsSummary = new StringBuilder();
        for (ToolCallRequest tc : toolCalls) {
            try {
                String result = toolExecutor.apply(tc);
                toolResultsSummary.append("工具[").append(tc.functionName).append("] 返回:\n").append(result).append("\n\n");
            } catch (Exception e) {
                log.warn("执行工具失败: {}", tc.functionName, e);
                toolResultsSummary.append("工具[").append(tc.functionName).append("] 执行失败: ").append(e.getMessage()).append("\n");
            }
        }
        toolMessages.add(Map.of("role", "user", "content", "工具调用结果如下，请基于这些数据回答用户的问题：\n" + toolResultsSummary));

        return doChatStream(sfClient, toolMessages, model, temperature, systemPrompt, true, null);
    }

    private String doChatSyncRaw(WebClient wc, List<Map<String, String>> messages, String model,
                                  double temperature, String systemPrompt, List<ToolDefinition> tools) {
        String requestJson = buildRequestJson(messages, model, temperature, systemPrompt, false, tools);
        return wc.post()
                .uri("/chat/completions")
                .bodyValue(requestJson)
                .retrieve()
                .bodyToMono(String.class)
                .block(Duration.ofSeconds(30));
    }

    private String chatSyncRaw(List<Map<String, String>> messages, String model, double temperature,
                                String systemPrompt, List<ToolDefinition> tools) {
        return doChatSyncRaw(chatWebClient(), messages, model, temperature, systemPrompt, tools);
    }

    private Flux<String> doChatStream(WebClient wc, List<Map<String, String>> messages, String model,
                                       double temperature, String systemPrompt, boolean stream,
                                       List<ToolDefinition> tools) {
        try {
            String requestJson = buildRequestJson(messages, model, temperature, systemPrompt, stream, tools);
            String provider = activeProviderName();
            log.debug("{} 请求: model={}, messages={}", provider, model, messages.size());

            return wc.post()
                    .uri("/chat/completions")
                    .bodyValue(requestJson)
                    .accept(MediaType.TEXT_EVENT_STREAM)
                    .retrieve()
                    .bodyToFlux(new ParameterizedTypeReference<ServerSentEvent<String>>() {})
                    .map(ServerSentEvent::data)
                    .filter(data -> data != null && !data.isEmpty())
                    .map(this::parseEventData)
                    .filter(content -> content != null && !content.isEmpty())
                    .doOnError(e -> log.error("{} API 调用失败: {}", provider, e.getMessage()))
                    .onErrorResume(e -> Flux.error(e));
        } catch (Exception e) {
            log.error("构建 {} 请求失败", activeProviderName(), e);
            return Flux.error(e);
        }
    }

    // ==================== 非流式同步调用 ====================

    /** 同步调用（使用 Key-C） */
    public String chatSync(List<Map<String, String>> messages, String model, double temperature) {
        return chatSync(messages, model, temperature, null);
    }

    public String chatSync(List<Map<String, String>> messages, String model,
                            double temperature, String systemPrompt) {
        return doChatSync(chatWebClient(), messages, model, temperature, systemPrompt, null);
    }

    /** 同步调用（使用 Key-S 专用模型，始终用 SiliconFlow） */
    public String specialChatSync(List<Map<String, String>> messages, String model,
                                   double temperature, String systemPrompt) {
        return doChatSync(buildWebClient(siliconFlowBaseUrl, specialKey), messages, model, temperature, systemPrompt, null);
    }

    /** 知识图谱同步调用（使用 KG 专用 Key → SenseNova，支持 sensenova-6.7-flash-lite 和 deepseek-v4-flash） */
    public String kgChatSync(List<Map<String, String>> messages, String model,
                              double temperature, String systemPrompt) {
        return doChatSync(buildWebClient(senseNovaBaseUrl, senseNovaKgKey), messages, model, temperature, systemPrompt, null);
    }

    private String doChatSync(WebClient wc, List<Map<String, String>> messages, String model,
                               double temperature, String systemPrompt, List<ToolDefinition> tools) {
        try {
            String requestJson = buildRequestJson(messages, model, temperature, systemPrompt, false, tools);
            String response = wc.post()
                    .uri("/chat/completions")
                    .bodyValue(requestJson)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
            if (response == null) return null;
            var root = objectMapper.readTree(response);
            var choice = root.path("choices").get(0);
            return choice != null ? choice.path("message").path("content").asText(null) : null;
        } catch (Exception e) {
            log.error("{} 同步调用失败: {}", activeProviderName(), e.getMessage());
            throw new RuntimeException("AI 调用失败: " + e.getMessage(), e);
        }
    }

    // ==================== 工具调用（Function Calling） ====================

    public List<ToolCallRequest> parseToolCalls(String responseJson) {
        try {
            var root = objectMapper.readTree(responseJson);
            var choice = root.path("choices").get(0);
            if (choice == null) return List.of();
            var message = choice.path("message");
            var toolCalls = message.path("tool_calls");
            if (toolCalls.isMissingNode() || !toolCalls.isArray()) return List.of();

            List<ToolCallRequest> result = new ArrayList<>();
            for (var tc : toolCalls) {
                result.add(new ToolCallRequest(
                        tc.path("id").asText(),
                        tc.path("type").asText("function"),
                        tc.path("function").path("name").asText(),
                        tc.path("function").path("arguments").asText()
                ));
            }
            return result;
        } catch (Exception e) {
            log.warn("解析 tool_calls 失败: {}", e.getMessage());
            return List.of();
        }
    }

    public Flux<String> chatWithTools(List<Map<String, String>> messages, String model, double temperature,
                                       String systemPrompt, List<ToolDefinition> tools,
                                       Function<ToolCallRequest, String> toolExecutor) {
        String firstResponse;
        try {
            firstResponse = chatSyncRaw(messages, model, temperature, systemPrompt, tools);
        } catch (Exception e) {
            log.warn("工具调用第一轮失败，回退到普通流式: {}", e.getMessage());
            return chatStream(messages, model, temperature, systemPrompt);
        }

        List<ToolCallRequest> toolCalls = parseToolCalls(firstResponse);
        if (toolCalls.isEmpty()) {
            try {
                var root = objectMapper.readTree(firstResponse);
                String content = root.path("choices").get(0).path("message").path("content").asText();
                if (content != null && !content.isEmpty()) {
                    return Flux.just(content);
                }
            } catch (Exception e) {
                log.warn("解析首轮响应失败", e);
            }
            return chatStream(messages, model, temperature, systemPrompt);
        }

        List<Map<String, String>> toolMessages = new ArrayList<>(messages);
        try {
            var root = objectMapper.readTree(firstResponse);
            var choiceMsg = root.path("choices").get(0).path("message");
            Map<String, String> assistantMsg = Map.of("role", "assistant", "content",
                    choiceMsg.path("content").asText(""));
            toolMessages.add(assistantMsg);
        } catch (Exception e) {
            log.warn("解析 assistant 消息失败", e);
        }

        StringBuilder toolResultsSummary = new StringBuilder();
        for (ToolCallRequest tc : toolCalls) {
            try {
                String result = toolExecutor.apply(tc);
                toolResultsSummary.append("工具[").append(tc.functionName).append("] 返回:\n").append(result).append("\n\n");
            } catch (Exception e) {
                log.warn("执行工具失败: {}", tc.functionName, e);
                toolResultsSummary.append("工具[").append(tc.functionName).append("] 执行失败: ").append(e.getMessage()).append("\n");
            }
        }

        toolMessages.add(Map.of("role", "user", "content", "工具调用结果如下，请基于这些数据回答用户的问题：\n" + toolResultsSummary));

        return chatStream(toolMessages, model, temperature, systemPrompt);
    }

    // ==================== 搜索结果重排序（Key-E + bge-reranker-v2-m3）====================

    /**
     * 调用 SiliconFlow Rerank API 对搜索结果重排序
     * @param query 原始查询
     * @param documents 待重排序的文档列表
     * @param topN 返回前N个结果
     * @return 重排序后的文档列表（按 score 降序）
     */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> rerank(String query, List<String> documents, int topN) {
        try {
            WebClient wc = buildWebClient(siliconFlowBaseUrl, embedKey);
            ObjectNode body = objectMapper.createObjectNode();
            body.put("model", rerankModel);
            body.put("query", query);
            ArrayNode docsArray = body.putArray("documents");
            for (String doc : documents) {
                docsArray.add(doc);
            }
            body.put("top_n", Math.min(topN, documents.size()));

            String response = wc.post()
                    .uri("/rerank")
                    .bodyValue(body.toString())
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            if (response == null) return List.of();
            var root = objectMapper.readTree(response);
            var results = root.path("results");
            List<Map<String, Object>> ranked = new ArrayList<>();
            if (results.isArray()) {
                for (var r : results) {
                    Map<String, Object> item = new java.util.LinkedHashMap<>();
                    item.put("index", r.path("index").asInt());
                    item.put("score", r.path("relevance_score").asDouble());
                    item.put("text", r.path("text").asText());
                    ranked.add(item);
                }
            }
            ranked.sort((a, b) -> Double.compare((Double) b.get("score"), (Double) a.get("score")));
            log.info("Rerank 完成: queryLen={}, docs={}, results={}", query.length(), documents.size(), ranked.size());
            return ranked;
        } catch (Exception e) {
            log.warn("Rerank 调用失败: {}", e.getMessage());
            return List.of();
        }
    }

    // ==================== SenseNova U1 Fast 信息图生成 ====================

    /**
     * 调用 SenseNova U1 Fast 生成知识图谱信息图
     * @param prompt 描述要生成的图表内容
     * @return 生成的图片 URL
     */
    /** 书籍封面尺寸：3:4 比例 1760x2368，匹配前端 aspect-ratio: 3/4 */
    private static final String COVER_SIZE = "1760x2368";

    public String generateInfographic(String prompt) {
        if (!"sensenova".equals(providerConfig.getChatProvider())) return "U1 Fast 仅 SenseNova 可用";
        try {
            ObjectNode root = objectMapper.createObjectNode();
            root.put("model", senseNovaU1Model);
            root.put("prompt", prompt);
            root.put("n", 1);
            root.put("size", COVER_SIZE);

            WebClient wc = buildWebClient(senseNovaBaseUrl, senseNovaChatKey);
            String response = wc.post()
                    .uri("/images/generations")
                    .bodyValue(root.toString())
                    .retrieve()
                    .bodyToMono(String.class)
                    .block(java.time.Duration.ofSeconds(60));
            if (response == null) return "生成失败";
            var json = objectMapper.readTree(response);
            var data = json.path("data");
            if (data.isArray() && data.size() > 0) {
                return data.get(0).path("url").asText("");
            }
            return "未获取到图片: " + json.toString();
        } catch (Exception e) {
            log.warn("U1 Fast 生成失败: {}", e.getMessage());
            return "生成失败: " + e.getMessage();
        }
    }

    // ==================== PDF OCR（Key-S + deepseek-ai/DeepSeek-OCR）====================

    /**
     * 使用 DeepSeek-OCR 从图片/PDF页面提取文字
     * @param base64Image 图片的 base64 编码（不含 data:image 前缀）
     * @param pageNumber 页码（用于日志）
     * @return 提取的文本
     */
    public String ocrImage(String base64Image, int pageNumber) {
        try {
            // 按 provider 选择：SenseNova 用 chat 模型做多模态，SiliconFlow 用专用 OCR
            String ocrBaseUrl = "sensenova".equals(providerConfig.getChatProvider()) ? senseNovaBaseUrl : siliconFlowBaseUrl;
            String ocrKey = "sensenova".equals(providerConfig.getChatProvider()) ? senseNovaChatKey : specialKey;
            WebClient wc = buildWebClient(ocrBaseUrl, ocrKey);
            ObjectNode root = objectMapper.createObjectNode();
            String ocrModelName = "sensenova".equals(providerConfig.getChatProvider()) ? senseNovaOcrModel : ocrModel;
            root.put("model", ocrModelName);

            ArrayNode msgsArray = objectMapper.createArrayNode();
            ObjectNode userMsg = objectMapper.createObjectNode();
            userMsg.put("role", "user");

            ArrayNode contentArray = objectMapper.createArrayNode();

            ObjectNode textPart = objectMapper.createObjectNode();
            textPart.put("type", "text");
            textPart.put("text", "请提取图片中的所有文字内容，保持原始格式和段落结构。只输出提取的文字，不要添加任何说明。");
            contentArray.add(textPart);

            ObjectNode imagePart = objectMapper.createObjectNode();
            imagePart.put("type", "image_url");
            ObjectNode imageUrl = objectMapper.createObjectNode();
            imageUrl.put("url", "data:image/png;base64," + base64Image);
            imagePart.set("image_url", imageUrl);
            contentArray.add(imagePart);

            userMsg.set("content", contentArray);
            msgsArray.add(userMsg);
            root.set("messages", msgsArray);
            root.put("stream", false);
            root.put("max_tokens", 4096);

            String response = wc.post()
                    .uri("/chat/completions")
                    .bodyValue(root.toString())
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            if (response == null) return "";
            var json = objectMapper.readTree(response);
            String text = json.path("choices").get(0).path("message").path("content").asText("");
            log.info("DeepSeek-OCR 第{}页: 提取{}字", pageNumber, text.length());
            return text;
        } catch (Exception e) {
            log.warn("DeepSeek-OCR 第{}页失败: {}", pageNumber, e.getMessage());
            return "";
        }
    }

    // ==================== 请求构建 ====================

    private String buildRequestJson(List<Map<String, String>> messages, String model,
                                     double temperature, String systemPrompt, boolean stream,
                                     List<ToolDefinition> tools) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("model", model);
        root.put("stream", stream);
        root.put("temperature", temperature);
        if (!stream) {
            root.put("max_tokens", 8192);
        }

        ArrayNode msgsArray = objectMapper.createArrayNode();
        if (systemPrompt != null && !systemPrompt.isEmpty()) {
            ObjectNode sysMsg = objectMapper.createObjectNode();
            sysMsg.put("role", "system");
            sysMsg.put("content", systemPrompt);
            msgsArray.add(sysMsg);
        }
        for (Map<String, String> msg : messages) {
            ObjectNode msgNode = objectMapper.createObjectNode();
            msgNode.put("role", msg.get("role"));
            msgNode.put("content", msg.get("content"));
            msgsArray.add(msgNode);
        }
        root.set("messages", msgsArray);

        if (tools != null && !tools.isEmpty()) {
            ArrayNode toolsArray = objectMapper.createArrayNode();
            for (ToolDefinition tool : tools) {
                ObjectNode toolNode = objectMapper.createObjectNode();
                toolNode.put("type", tool.type());
                ObjectNode funcNode = objectMapper.createObjectNode();
                funcNode.put("name", tool.function().name());
                funcNode.put("description", tool.function().description());
                funcNode.set("parameters", objectMapper.valueToTree(tool.function().parameters()));
                toolNode.set("function", funcNode);
                toolsArray.add(toolNode);
            }
            root.set("tools", toolsArray);
        }

        try {
            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            throw new RuntimeException("JSON序列化失败", e);
        }
    }

    /** 解析 SSE event data — 跳过 reasoning_content（前端不显示思考过程，加速首字感知） */
    private String parseEventData(String json) {
        try {
            if (json == null || json.trim().isEmpty() || "[DONE]".equals(json.trim())) return "";
            var root = objectMapper.readTree(json);
            var choices = root.path("choices");
            if (choices.isArray() && choices.size() > 0) {
                var delta = choices.get(0).path("delta");
                String content = delta.path("content").asText(null);
                if (content != null && !content.isEmpty()) return content;
            }
        } catch (Exception e) {
            log.warn("解析SSE数据失败: {}", e.getMessage());
        }
        return "";
    }
}
