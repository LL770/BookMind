package ai.bookmind.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * AI 多 Provider 配置
 *
 * 聊天：按 bookmind.ai.chat-provider 选择 senseNova 或 siliconFlow
 * 嵌入/重排序/OCR/知识图谱：始终用 SiliconFlow
 */
@Configuration
public class SiliconFlowConfig {

    // ——— Provider 选择（运行时热切换） ———
    @Autowired
    private ProviderConfig providerConfig;

    // ——— SenseNova（聊天） ———
    @Value("${sensenova.api.base-url:https://token.sensenova.cn/v1}")
    private String senseNovaBaseUrl;

    @Value("${sensenova.api.chat-key:}")
    private String senseNovaChatKey;

    @Value("${sensenova.api.chat-model:deepseek-v4-flash}")
    private String senseNovaChatModel;

    // ——— SiliconFlow（聊天 + 嵌入 + 专用） ———
    @Value("${siliconflow.api.base-url:https://api.siliconflow.cn/v1}")
    private String siliconFlowBaseUrl;

    @Value("${siliconflow.api.chat-key:}")
    private String siliconFlowChatKey;

    @Value("${siliconflow.api.embed-key:}")
    private String embedKey;

    @Value("${siliconflow.api.special-key:}")
    private String specialKey;

    @Value("${siliconflow.api.chat-model:Qwen/Qwen3-8B}")
    private String siliconFlowChatModel;

    @Value("${siliconflow.api.embed-model:BAAI/bge-m3}")
    private String embedModel;

    @Value("${siliconflow.api.special-model:THUDM/GLM-Z1-9B-0414}")
    private String specialModel;

    // ——— 按 provider 选择的值 ———
    private String activeChatBaseUrl() {
        return "sensenova".equals(providerConfig.getChatProvider()) ? senseNovaBaseUrl : siliconFlowBaseUrl;
    }

    private String activeChatKey() {
        return "sensenova".equals(providerConfig.getChatProvider()) ? senseNovaChatKey : siliconFlowChatKey;
    }

    private String activeChatModel() {
        return "sensenova".equals(providerConfig.getChatProvider()) ? senseNovaChatModel : siliconFlowChatModel;
    }

    // ——— 所有 API 共用的 siliconflow base（嵌入/专用不走 provider 切换） ———
    private String sfBaseUrl() { return siliconFlowBaseUrl; }

    // ==================== OpenAiApi ====================

    @Bean
    @Primary
    public OpenAiApi chatApi() {
        return apiWithTimeout(activeChatBaseUrl(), activeChatKey());
    }

    @Bean
    public OpenAiApi embedApi() {
        // 嵌入 key 为空时依次兜底：siliconflow chat key → sensenova key
        String key = embedKey;
        if (key.isEmpty()) key = siliconFlowChatKey;
        if (key.isEmpty()) key = senseNovaChatKey;
        return apiWithTimeout(sfBaseUrl(), key);
    }

    @Bean
    public OpenAiApi specialApi() {
        return apiWithTimeout(sfBaseUrl(), specialKey);
    }

    private OpenAiApi apiWithTimeout(String baseUrl, String apiKey) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(30_000);
        factory.setReadTimeout(120_000);
        RestClient.Builder restBuilder = RestClient.builder().requestFactory(factory);
        return new OpenAiApi(baseUrl, apiKey, restBuilder, WebClient.builder());
    }

    // ==================== ChatClient（按 provider 选择模型）====================

    @Bean
    @Primary
    public ChatClient chatClient(@Qualifier("chatApi") OpenAiApi chatApi) {
        OpenAiChatModel model = new OpenAiChatModel(chatApi,
                OpenAiChatOptions.builder()
                        .withModel(activeChatModel())
                        .withTemperature(0.7)
                        .withMaxTokens(4096)
                        .build());
        return ChatClient.builder(model)
                .defaultSystem("你是 BookMind 智能阅读助手，帮助用户深度理解书籍内容。回答要亲和、有条理。")
                .build();
    }

    // ==================== EmbeddingModel（始终用 SiliconFlow）====================

    @Bean
    @Primary
    public EmbeddingModel embeddingModel(@Qualifier("embedApi") OpenAiApi embedApi) {
        return new OpenAiEmbeddingModel(embedApi, MetadataMode.EMBED,
                OpenAiEmbeddingOptions.builder()
                        .withModel(embedModel)
                        .build());
    }

    // ==================== 专用模型 ChatClient（知识图谱，始终用 SiliconFlow）====================

    @Bean("specialChatClient")
    public ChatClient specialChatClient(@Qualifier("specialApi") OpenAiApi specialApi) {
        OpenAiChatModel model = new OpenAiChatModel(specialApi,
                OpenAiChatOptions.builder()
                        .withModel(specialModel)
                        .withTemperature(0.3)
                        .withMaxTokens(8192)
                        .build());
        return ChatClient.builder(model)
                .defaultSystem("你是知识图谱抽取专家和文档分析助手。请仔细分析内容，精确提取结构化信息。")
                .build();
    }
}
