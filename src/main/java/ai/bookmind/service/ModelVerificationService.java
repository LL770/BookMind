package ai.bookmind.service;

import ai.bookmind.ai.AiApiClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationContext;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
@Slf4j
public class ModelVerificationService {

    private final AiApiClient aiApiClient;
    private final ObjectMapper json = new ObjectMapper();

    @Autowired
    private ApplicationContext context;

    @Value("${siliconflow.api.chat-key:}") private String siliconChatKey;
    @Value("${siliconflow.api.embed-key:}") private String siliconEmbedKey;
    @Value("${siliconflow.api.chat-model:Qwen/Qwen3-8B}") private String siliconChatModel;
    @Value("${siliconflow.api.special-model:THUDM/GLM-Z1-9B-0414}") private String siliconSpecialModel;
    @Value("${siliconflow.api.embed-model:BAAI/bge-m3}") private String siliconEmbedModel;
    @Value("${siliconflow.api.rerank-model:BAAI/bge-reranker-v2-m3}") private String siliconRerankModel;
    @Value("${sensenova.api.chat-model:sensenova-6.7-flash-lite}") private String senseChatModel;
    @Value("${sensenova.api.tool-model:deepseek-v4-flash}") private String senseToolModel;
    @Value("${sensenova.api.kg-model:deepseek-v4-flash}") private String senseKgModel;
    @Value("${sensenova.api.u1-model:sensenova-u1-fast}") private String senseU1Model;

    @EventListener(ApplicationReadyEvent.class)
    public void verifyAllModels() {
        CompletableFuture.runAsync(this::doVerify);
    }

    private void doVerify() {
        List<String[]> allModels = new ArrayList<>();
        allModels.add(new String[]{"SenseNova",   "chat",     senseChatModel});
        allModels.add(new String[]{"SenseNova",   "tool",     senseToolModel});
        allModels.add(new String[]{"SenseNova",   "u1",       senseU1Model});
        allModels.add(new String[]{"SenseNova",   "kg",       senseKgModel});
        allModels.add(new String[]{"SiliconFlow", "chat",     siliconChatModel});
        allModels.add(new String[]{"SiliconFlow", "special",  siliconSpecialModel});
        allModels.add(new String[]{"SiliconFlow", "embed",    siliconEmbedModel});
        allModels.add(new String[]{"SiliconFlow", "rerank",   siliconRerankModel});

        WebClient sfClient = WebClient.builder().baseUrl("https://api.siliconflow.cn/v1")
                .defaultHeader("Content-Type", "application/json").build();

        String dateStr = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        StringBuilder report = new StringBuilder();
        report.append("=== 模型启动验证 ").append(ts).append(" ===\n");

        for (String[] m : allModels) {
            String provider = m[0], scene = m[1], modelName = m[2];
            if (modelName == null || modelName.isEmpty()) {
                report.append(String.format("[SKIP] %-12s %s  (no config)\n", scene, modelName));
                continue;
            }
            String status;
            long start = System.currentTimeMillis();
            try {
                if ("embed".equals(scene)) {
                    ObjectNode body = json.createObjectNode();
                    body.put("model", modelName); body.put("input", "test");
                    sfClient.post().uri("/embeddings").header("Authorization", "Bearer " + siliconEmbedKey)
                            .bodyValue(body.toString()).retrieve().bodyToMono(String.class).block(java.time.Duration.ofSeconds(15));
                    status = "OK";
                } else if ("rerank".equals(scene)) {
                    ObjectNode body = json.createObjectNode();
                    body.put("model", modelName); body.put("query", "test");
                    ArrayNode docs = body.putArray("documents"); docs.add("test");
                    sfClient.post().uri("/rerank").header("Authorization", "Bearer " + siliconEmbedKey)
                            .bodyValue(body.toString()).retrieve().bodyToMono(String.class).block(java.time.Duration.ofSeconds(15));
                    status = "OK";
                } else if ("SiliconFlow".equals(provider)) {
                    aiApiClient.specialChatStream(List.of(Map.of("role","user","content","hi")), modelName, 0.5, null)
                            .blockFirst(java.time.Duration.ofSeconds(20));
                    status = "OK";
                } else if ("u1".equals(scene)) {
                    String result = aiApiClient.generateInfographic("red circle");
                    status = result != null && !result.contains("失败") ? "OK" : "FAIL";
                } else {
                    aiApiClient.chatStream(List.of(Map.of("role","user","content","hi")), modelName, 0.5, null)
                            .blockFirst(java.time.Duration.ofSeconds(30));
                    status = "OK";
                }
            } catch (Exception e) {
                String msg = e.getMessage();
                if (msg != null && (msg.contains("401") || msg.contains("403") || msg.contains("Unauthorized")))
                    status = "KEY_ERR";
                else if (msg != null && (msg.contains("Timeout") || msg.contains("timed out")))
                    status = "SLOW";
                else if (msg != null && (msg.contains("model") && msg.contains("not found")))
                    status = "NO_MODEL";
                else
                    status = "FAIL";
            }
            long ms = System.currentTimeMillis() - start;
            report.append(String.format("[%s] %-12s %-6s %s\n", status, provider+":"+scene, "("+ms+"ms)", modelName));
        }
        report.append("--- 服务验证 ---\n");

        // 天气工具
        long t0 = System.currentTimeMillis();
        try {
            String r = new String(new java.net.URL("https://wttr.in/Beijing?format=%t").openStream().readAllBytes());
            report.append(String.format("[%s] %-16s %s\n", r.contains("-")||r.contains("°")?"OK":"FAIL", "weather(wttr.in)", "("+(System.currentTimeMillis()-t0)+"ms)"));
        } catch (Exception e) {
            report.append(String.format("[FAIL] %-16s %s\n", "weather(wttr.in)", "("+(System.currentTimeMillis()-t0)+"ms)"));
        }

        // MCP 服务: open-websearch
        long t2 = System.currentTimeMillis();
        try {
            var mcp = context.getBean(ai.bookmind.service.McpClientService.class);
            String r = mcp.searchWeb("test book", 1);
            report.append(String.format("[%s] %-16s %s\n", r.contains("error")||r.contains("不可用")||r.isEmpty()?"FAIL":"OK", "mcp(open-websearch)", "("+(System.currentTimeMillis()-t2)+"ms)"));
        } catch (Exception e) {
            report.append(String.format("[FAIL] %-16s %s\n", "mcp(open-websearch)", "("+(System.currentTimeMillis()-t2)+"ms)"));
        }

        // MCP 服务: open-library
        long t3 = System.currentTimeMillis();
        try {
            var mcp = context.getBean(ai.bookmind.service.McpClientService.class);
            String r = mcp.searchBookByTitle("test");
            report.append(String.format("[%s] %-16s %s\n", r.contains("error")||r.contains("不可用")||r.isEmpty()?"FAIL":"OK", "mcp(open-library)", "("+(System.currentTimeMillis()-t3)+"ms)"));
        } catch (Exception e) {
            report.append(String.format("[FAIL] %-16s %s\n", "mcp(open-library)", "("+(System.currentTimeMillis()-t3)+"ms)"));
        }

        report.append("=== 结束 ===\n\n");

        try {
            Files.createDirectories(Paths.get("DataLog/模型"));
            Files.writeString(Paths.get("DataLog/模型/" + dateStr + ".txt"),
                    report.toString(),
                    java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
        } catch (Exception e) {
            log.warn("model log write failed: {}", e.getMessage());
        }
    }
}
