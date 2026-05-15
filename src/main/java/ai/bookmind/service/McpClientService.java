package ai.bookmind.service;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.spec.McpSchema;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class McpClientService {

    private final Map<String, McpSyncClient> clients = new ConcurrentHashMap<>();
    private final Map<String, List<McpSchema.Tool>> toolCache = new ConcurrentHashMap<>();

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);

    public McpClientService() {
        initOpenLibrary();
        initOpenWebSearch();
    }

    private void initOpenLibrary() {
        try {
            ServerParameters params = ServerParameters.builder("node")
                    .args(
                            "C:\\Users\\fantuan\\Desktop\\javaweb\\rent\\scripts\\mcp-open-library-launcher.js"
                    )
                    .build();
            McpSyncClient client = McpClient.sync(new StdioClientTransport(params))
                    .requestTimeout(REQUEST_TIMEOUT)
                    .clientInfo(new McpSchema.Implementation("bookmind", "1.0.0"))
                    .build();
            client.initialize();
            McpSchema.ListToolsResult tools = client.listTools();
            clients.put("open-library", client);
            toolCache.put("open-library", tools.tools());
            log.info("[MCP] open-library 启动成功, 工具: {}", tools.tools().stream()
                    .map(McpSchema.Tool::name).toList());
        } catch (Exception e) {
            log.warn("[MCP] open-library 启动失败: {}", e.getMessage());
        }
    }

    private void initOpenWebSearch() {
        try {
            String wsPath = System.getProperty("user.dir") + "/node_modules/open-websearch/build/index.js";
            ServerParameters params = ServerParameters.builder("node")
                    .args(wsPath)
                    .addEnvVar("MODE", "stdio")
                    .build();
            McpSyncClient client = McpClient.sync(new StdioClientTransport(params))
                    .requestTimeout(REQUEST_TIMEOUT)
                    .clientInfo(new McpSchema.Implementation("bookmind", "1.0.0"))
                    .build();
            client.initialize();
            McpSchema.ListToolsResult tools = client.listTools();
            clients.put("open-websearch", client);
            toolCache.put("open-websearch", tools.tools());
            log.info("[MCP] open-websearch 启动成功, 工具: {}", tools.tools().stream()
                    .map(McpSchema.Tool::name).toList());
        } catch (Exception e) {
            log.warn("[MCP] open-websearch 启动失败: {}", e.getMessage());
        }
    }

    /** 列出指定 MCP 服务器的可用工具 */
    public List<String> listTools(String serverName) {
        List<McpSchema.Tool> tools = toolCache.get(serverName);
        if (tools == null) return List.of();
        return tools.stream().map(McpSchema.Tool::name).toList();
    }

    /** 调用 MCP 工具，返回文本结果 */
    public String callTool(String serverName, String toolName, Map<String, Object> args) {
        McpSyncClient client = clients.get(serverName);
        if (client == null) return "MCP 服务器 " + serverName + " 不可用";

        try {
            McpSchema.CallToolResult result = client.callTool(
                    new McpSchema.CallToolRequest(toolName, args));
            if (result.isError() != null && result.isError()) {
                return "工具调用失败: " + extractText(result);
            }
            return extractText(result);
        } catch (Exception e) {
            log.warn("[MCP] {}/{} 调用失败: {}", serverName, toolName, e.getMessage());
            return "工具调用出错: " + e.getMessage();
        }
    }

    /** web 搜索快捷方法 */
    public String searchWeb(String query, int limit) {
        return callTool("open-websearch", "search", Map.of(
                "query", query,
                "limit", limit
        ));
    }

    /** 按书名搜索图书信息 */
    public String searchBookByTitle(String title) {
        return callTool("open-library", "get_book_by_title", Map.of("title", title));
    }

    /** 按作者名搜索 */
    public String searchAuthorByName(String name) {
        return callTool("open-library", "get_authors_by_name", Map.of("name", name));
    }

    /** 获取任意 URL 内容 */
    public String fetchWebContent(String url) {
        return callTool("open-websearch", "fetchWebContent", Map.of("url", url));
    }

    /** 健康检查 */
    public boolean isAvailable(String serverName) {
        return clients.containsKey(serverName);
    }

    @PreDestroy
    public void shutdown() {
        clients.forEach((name, client) -> {
            try {
                client.closeGracefully();
                log.info("[MCP] {} 连接已关闭", name);
            } catch (Exception e) {
                log.warn("[MCP] {} 关闭失败: {}", name, e.getMessage());
            }
        });
        clients.clear();
    }

    private String extractText(McpSchema.CallToolResult result) {
        if (result.content() == null || result.content().isEmpty()) return "(空结果)";
        StringBuilder sb = new StringBuilder();
        for (McpSchema.Content c : result.content()) {
            if (c instanceof McpSchema.TextContent tc) {
                sb.append(tc.text()).append("\n");
            } else if (c instanceof McpSchema.EmbeddedResource er) {
                sb.append("[嵌入资源] ").append(er.resource()).append("\n");
            }
        }
        return sb.toString().trim();
    }
}
