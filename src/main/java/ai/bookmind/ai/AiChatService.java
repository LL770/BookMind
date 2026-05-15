package ai.bookmind.ai;

import ai.bookmind.entity.Book;
import ai.bookmind.entity.Chapter;
import ai.bookmind.entity.KnowledgeNode;
import ai.bookmind.entity.Note;
import ai.bookmind.mapper.BookMapper;
import ai.bookmind.mapper.NoteMapper;
import ai.bookmind.mapper.ChapterMapper;
import ai.bookmind.service.BookService;
import ai.bookmind.service.HybridVectorService;
import ai.bookmind.service.ToolApiService;
import ai.bookmind.service.McpClientService;
import ai.bookmind.service.BookUploadService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import reactor.core.publisher.Flux;

import java.time.LocalDateTime;

import org.springframework.beans.factory.annotation.Value;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.concurrent.TimeUnit;

/**
 * AI 对话服务 — 双 Provider
 *
 * 模型分工：
 * - SenseNova: 日常对话、RAG、摘要、工具调用（sensenova-6.7-flash-lite）
 * - SiliconFlow: 嵌入(embedding)、重排序(rerank)、OCR（通过 AiApiClient）
 * - KG: deepseek-v4-flash（SenseNova 或 SiliconFlow，按场景）
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AiChatService {

    // ==================== AI API 客户端 ====================
    @Autowired
    private AiApiClient aiApiClient;  // 直接HTTP调用

    // ——— Provider 感知的模型名 ———
    @Autowired
    private ai.bookmind.config.ProviderConfig providerConfig;

    @Value("${siliconflow.api.chat-model:Qwen/Qwen3-8B}")
    private String siliconFlowChatModel;

    @Value("${siliconflow.api.special-model:THUDM/GLM-Z1-9B-0414}")
    private String siliconFlowSpecialModel;

    @Value("${sensenova.api.chat-model:sensenova-6.7-flash-lite}")
    private String senseNovaChatModel;

    @Value("${sensenova.api.tool-model:deepseek-v4-flash}")
    private String senseNovaToolModel;

    @Value("${sensenova.api.kg-model:deepseek-v4-flash}")
    private String senseNovaKgModel;

    private String activeChatModel() {
        return "sensenova".equals(providerConfig.getChatProvider()) ? senseNovaChatModel : siliconFlowChatModel;
    }

    private String activeToolModel() {
        return "sensenova".equals(providerConfig.getChatProvider()) ? senseNovaToolModel : siliconFlowChatModel;
    }

    private String activeKgModel() {
        return "sensenova".equals(providerConfig.getChatProvider()) ? senseNovaKgModel : siliconFlowSpecialModel;
    }

    @Autowired
    @Qualifier("specialChatClient")
    private ChatClient specialChatClient;  // Key-S: GLM-Z1-9B

    @Autowired
    private ModelRouter modelRouter;

    @Autowired
    private ToolApiService toolApiService;

    // ==================== 构造函数注入 ====================
    private final HybridVectorService hybridVectorService;
    private final BookMapper bookMapper;
    private final NoteMapper noteMapper;
    private final ChapterMapper chapterMapper;
    private final ai.bookmind.mapper.KnowledgeNodeMapper kgNodeMapper;
    private final BookService bookService;
    private final RedisTemplate<String, Object> redisTemplate;
    @Autowired
    private McpClientService mcpClientService;
    @Autowired
    private BookUploadService bookUploadService;

    private static final DateTimeFormatter TIME_BUCKET_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm");
    private static final long SESSION_TTL_DAYS = 7;

    // ==================== AI 可用状态 ====================

    private volatile boolean aiAvailable = true;

    public boolean isAiAvailable() { return aiAvailable; }

    // ==================== Redis Key 构造 ====================

    private String messageKey(Long userId, String mode, String timeBucket, String keyname) {
        return "chathistory:user:" + userId + ":" + mode + ":" + timeBucket + ":" + keyname;
    }

    private String sessionIndexKey(Long userId, String mode) {
        return "chathistory:session:" + userId + ":" + mode;
    }

    // ==================== 多轮对话历史 ====================

    private String buildHistoryContext(Long userId, String sessionId, String mode) {
        List<Map<String, Object>> history = getHistory(userId, sessionId, mode);
        if (history.isEmpty()) return "";
        // 只取最近 2 轮对话，减少 prompt 长度
        int start = Math.max(0, history.size() - 4); // 最多 2 轮（用户+助手）× 2
        StringBuilder sb = new StringBuilder("\n历史:\n");
        for (int i = start; i < history.size(); i++) {
            Map<String, Object> msg = history.get(i);
            String role = "user".equals(msg.get("role")) ? "用户" : "助手";
            String content = ((String) msg.get("content"));
            if (content != null) {
                content = content.replaceAll("<thinking>[\\s\\S]*?</thinking>", "").trim();
                if (content.length() > 200) content = content.substring(0, 200) + "...";
            }
            sb.append(role).append("：").append(content != null ? content : "").append("\n");
        }
        return sb.toString();
    }

    // ==================== 系统提示词（精简版，降首字延迟） ====================

    private static final String SYSTEM_BASE = """
            你是 BookMind 阅读助手。回答基于书中内容，简洁精准。
            """;

    // ==================== 统一模型调用入口 ====================

    /**
     * 统一模型调用入口（Key-C 主力 + 降级容错）
     */
    private Flux<String> callModel(String scene, String message,
                                    String systemPrompt, String userPrompt) {
        if (!aiAvailable) {
            return Flux.just("⚠️ AI服务暂不可用，请稍后重试。");
        }
        ModelRouter.ModelType type = modelRouter.select(scene, message);
        if (type == ModelRouter.ModelType.SPECIAL) {
            log.info("【模型路由】特殊场景无流式支持，降级到对话模型");
        }

        List<Map<String, String>> msgs = List.of(Map.of("role", "user", "content", userPrompt));
        String primary = activeChatModel();       // sensenova-6.7-flash-lite
        String fallback = siliconFlowChatModel;    // Qwen/Qwen3-8B

        return aiApiClient.chatStream(msgs, primary, 0.5, systemPrompt)
                .onErrorResume(e -> {
                    log.warn("{} 失败，降级到 {}: {}", primary, fallback, e.getMessage());
                    return aiApiClient.chatStream(msgs, fallback, 0.5, systemPrompt)
                            .onErrorResume(e2 -> {
                                log.warn("{} 也失败: {}", fallback, e2.getMessage());
                                return Flux.just("⚠️ AI服务暂时不可用，请稍后重试。");
                            });
                });
    }

    // ==================== RAG 对话（书籍模式） ====================

    public Flux<String> chatWithRag(Long userId, Long bookId, String message, String sessionId) {
        // 前置检测：书籍管理类问题直接执行工具，不依赖模型函数调用
        String directResult = executeDirectTool(userId, message);
        if (directResult != null) {
            return Flux.just(directResult);
        }

        // 并行检索书籍 + 笔记
        CompletableFuture<List<Document>> bookFuture = CompletableFuture.supplyAsync(() ->
                searchBookContent(userId, bookId, message));
        CompletableFuture<List<Document>> noteFuture = CompletableFuture.supplyAsync(() ->
                searchUserNotes(userId, bookId, message));
        List<Document> bookDocs = bookFuture.join();
        List<Document> noteDocs = noteFuture.join();
        String context = buildContext(bookDocs, noteDocs, message);
        String bookTitle = getCachedBookTitle(bookId);
        String historyCtx = buildHistoryContext(userId, sessionId, "book");

        // MCP 增强
        String mcpContext = enrichWithMcp(message);
        if (!mcpContext.isEmpty()) context = context + "\n\n" + mcpContext;

        String systemPrompt = SYSTEM_BASE +
                "\n书籍：《" + bookTitle + "》。可检索书籍内容、笔记，也可用工具查书或搜书评。仅基于检索内容回答，找不到就如实说。简洁直接。\n\n"
                + "可用工具：查笔记(get_my_notes)、按书名查书(search_book_by_title)、按作者查书(search_author_by_name)、按ISBN查书(get_book_by_id)、获取作者信息(get_author_info)、获取封面URL(get_book_cover)、联网搜索书评(search_web)、获取网页内容(fetch_web_content)\n"
                + "书籍管理：删除书籍(delete_book)、重命名书名(rename_book_title)、修改作者(rename_book_author)、修改分类(change_book_category)、查书籍数量(get_book_count)、查全部书名(get_all_book_names)"
                + (historyCtx.isEmpty() ? "" : "\n\n" + historyCtx);

        // 书籍管理操作关键词检测 → 注入工具调用指令
        String m2 = message.toLowerCase();
        String toolInjection = "";
        if (m2.contains("删除") || m2.contains("移除") || m2.contains("删掉") || m2.contains("不要") || m2.contains("清理")) {
            toolInjection = "\n\n【指令】用户要删除书籍。调用 delete_book(bookName=书名) 工具执行，不要自己编造。";
        } else if (m2.contains("改名") || m2.contains("重命名") || m2.contains("改书名") || m2.contains("重命名书名")) {
            toolInjection = "\n\n【指令】用户要重命名书籍。如果明确说了新老书名，调用 rename_book_title(bookName=原名, newTitle=新名)；如果没说清楚，先调用 get_all_book_names 列出书籍。";
        } else if (m2.contains("改作者") || m2.contains("修改作者") || m2.contains("作者名") || (m2.contains("作者") && (m2.contains("改") || m2.contains("写错")))) {
            toolInjection = "\n\n【指令】用户要修改作者。如果明确了书和作者，调用 rename_book_author(bookName=书名, newAuthor=新作者)；如果没说清楚书名，先调用 get_all_book_names。";
        } else if (m2.contains("改分类") || m2.contains("修改分类") || m2.contains("换分类") || m2.contains("归类")) {
            toolInjection = "\n\n【指令】用户要修改分类。如果明确了书和新分类，调用 change_book_category(bookName=书名, newCategory=新分类)；如果没说清楚，先调用 get_all_book_names。";
        } else if (m2.contains("笔记") && (m2.contains("查") || m2.contains("找") || m2.contains("看") || m2.contains("有") || m2.contains("什么"))) {
            toolInjection = "\n\n【指令】用户在查笔记。调用 get_my_notes 工具查询笔记内容，不要说自己没有。";
        } else if (m2.contains("书评") || (m2.contains("评价") && m2.contains("书")) || (m2.contains("怎么样") && m2.contains("书"))
                || (m2.contains("好不好") && m2.contains("书")) || (m2.contains("推荐") && m2.contains("书"))) {
            toolInjection = "\n\n【指令】用户在查书评或推荐。调用 search_web(query=...) 搜索相关信息。";
        } else if (m2.contains("作者信息") || m2.contains("作者简介") || m2.contains("介绍作者")) {
            toolInjection = "\n\n【指令】用户要查作者详细信息。调用 search_author_by_name(name=...) 或 get_author_info(author_key=...)。";
        } else if (m2.contains("封面") || m2.contains("书籍封面") || m2.contains("书皮")) {
            toolInjection = "\n\n【指令】用户要查书籍封面。调用 get_book_cover 工具获取封面URL。";
        } else if (m2.contains("ISBN") || m2.contains("isbn") || m2.contains("书号")) {
            toolInjection = "\n\n【指令】用户要通过ISBN查书。调用 get_book_by_id(idType=isbn, idValue=...)。";
        } else if (m2.contains("网页") || m2.contains("打开") || m2.contains("提取") || m2.contains("抓取")) {
            toolInjection = "\n\n【指令】用户要获取网页内容。调用 fetch_web_content(url=...)。";
        } else if (m2.contains("搜") || m2.contains("搜索") || m2.contains("查一下") || m2.contains("查询")) {
            if (m2.contains("作者") || m2.contains("作家") || m2.contains("谁写的")) {
                toolInjection = "\n\n【指令】用户在搜索作者。调用 search_author_by_name(name=...) 查作者信息，或 search_web 搜索。";
            } else if (m2.contains("书") || m2.contains("小说") || m2.contains("作品")) {
                toolInjection = "\n\n【指令】用户在搜索书籍。调用 search_book_by_title(title=...) 查书，或 search_web 搜书。";
            } else {
                toolInjection = "\n\n【指令】用户要搜索信息。调用 search_web(query=...) 联网搜索。";
            }
        }

        String userPrompt = String.format("""
            检索到的内容：
            %s

            用户问题：%s%s
            """, context.isEmpty() ? "(暂无相关检索内容)" : context, message, toolInjection);

        // 函数调用式回答（书籍相关工具 + 笔记 + 书籍管理）
        List<AiApiClient.ToolDefinition> tools = List.of(
                notesTool(), mcpSearchBookTool(), mcpSearchAuthorTool(),
                mcpGetBookByIdTool(), mcpGetBookCoverTool(), mcpGetAuthorInfoTool(),
                mcpSearchWebTool(), mcpFetchWebContentTool(),
                deleteBookTool(), renameBookTitleTool(), renameBookAuthorTool(),
                changeBookCategoryTool(), getBookCountTool(), getAllBookNamesTool());
        Long uid = userId;
        return aiApiClient.chatWithTools(
                List.of(Map.of("role", "user", "content", userPrompt)),
                activeChatModel(), 0.5, systemPrompt, tools,
                tc -> executeToolCall(uid, tc))
                .onErrorResume(e -> {
                    log.warn("书籍对话工具失败，回退普通回答: {}", e.getMessage());
                    return callModel("rag", message, systemPrompt, userPrompt);
                });
    }

    // ==================== 跨书 RAG ====================

    public Flux<String> chatWithAllBooks(Long userId, String message, String sessionId) {
        CompletableFuture<List<Document>> bookFuture = CompletableFuture.supplyAsync(() ->
                searchAllBooksContent(userId, message));
        CompletableFuture<List<Document>> noteFuture = CompletableFuture.supplyAsync(() ->
                searchAllUserNotes(userId, message));
        List<Document> bookDocs = bookFuture.join();
        List<Document> noteDocs = noteFuture.join();
        String context = buildContext(bookDocs, noteDocs, message);

        String historyCtx = buildHistoryContext(userId, sessionId, "book");

        String systemPrompt = SYSTEM_BASE +
                "\n用户可查询多本书。标注来源书名，找不到就如实说。简洁直接。";

        // MCP 增强
        String mcpContext = enrichWithMcp(message);
        if (!mcpContext.isEmpty()) {
            context = context + "\n\n" + mcpContext;
        }

        String userPrompt = String.format("""
            检索到的内容：
            %s

            对话历史：
            %s

            用户问题：%s
            """, context.isEmpty() ? "(暂无相关检索内容)" : context,
                historyCtx.isEmpty() ? "(无)" : historyCtx, message);

        return callModel("rag", message, systemPrompt, userPrompt);
    }

    // ==================== 书籍管理前置执行（不依赖模型函数调用） ====================

    /**
     * 检测并直接执行书籍管理类查询（get_all_book_names / get_book_count），
     * 不依赖大模型函数调用能力。匹配到则返回结果字符串，否则返回 null。
     */
    private String executeDirectTool(Long userId, String message) {
        if (userId == null || message == null) return null;
        String m = message.trim().toLowerCase();

        // 查书籍数量
        if (m.contains("多少本") || m.contains("几本书") || m.contains("书籍数量") || m.contains("共上传")
                || m.contains("有几本") || m.contains("统计") || (m.contains("多少") && m.contains("书"))) {
            int cnt = bookMapper.countByUserId(userId);
            return "你共有 " + cnt + " 本书籍。";
        }

        // 列出全部书名（排除"作者写过哪些书"类查询）
        boolean hasAuthorQuery = m.contains("作者") || m.contains("作家") || m.contains("写过") || m.contains("著作");
        if ((m.contains("哪几本") || m.contains("我的书") || m.contains("有什么书") || m.contains("书库")
                || m.contains("列出") || m.contains("我的藏书") || m.contains("显示所有") || m.contains("书籍列表")
                || (m.contains("哪些书") && !hasAuthorQuery))
                && !hasAuthorQuery) {
            List<Book> all = bookMapper.selectByUserId(userId);
            if (all.isEmpty()) return "你还没有上传任何书籍。";
            StringBuilder sb = new StringBuilder("你的书籍列表：\n");
            for (int i = 0; i < all.size(); i++) {
                Book b = all.get(i);
                sb.append(i + 1).append(". 《").append(b.getTitle() != null ? b.getTitle() : "未命名").append("》");
                if (b.getAuthor() != null && !b.getAuthor().isEmpty()) sb.append(" - ").append(b.getAuthor());
                sb.append("\n");
            }
            return sb.toString().trim();
        }

        // ===== 删除书籍 =====
        String bookName = extractBookName(message, m);
        if ((m.contains("删除") || m.contains("移除") || m.contains("删掉")) && bookName != null) {
            Long bid = findBookIdByName(userId, bookName);
            if (bid == null) return "数据库没有《" + bookName + "》这本书";
            try { bookUploadService.deleteBook(userId, bid); return "删除成功"; }
            catch (Exception e) { return "删除失败，请你手动删除"; }
        }

        // ===== 重命名书名 =====
        java.util.regex.Matcher renameMat = java.util.regex.Pattern.compile("把[《」]?(.+?)[》」]?(?:的)?书名(?:改为|改成|叫)(.+)").matcher(message);
        if (renameMat.find()) {
            String oldName = renameMat.group(1).trim();
            String newName = renameMat.group(2).trim();
            Long bid = findBookIdByName(userId, oldName);
            if (bid == null) return "未找到《" + oldName + "》";
            if (newName.isEmpty()) return "新书名不能为空";
            Book eb = bookMapper.selectById(bid);
            bookMapper.updateBookInfo(bid, newName, eb.getAuthor(), eb.getCategory());
            return "已将《" + oldName + "》重命名为《" + newName + "》";
        }
        if ((m.contains("改名") || m.contains("重命名")) && bookName != null) {
            Long bid = findBookIdByName(userId, bookName);
            if (bid == null) return "未找到《" + bookName + "》";
            return "请指定新书名，例如「把《" + bookName + "》改名为xxx」";
        }

        // ===== 修改作者 =====
        java.util.regex.Matcher authorMat = java.util.regex.Pattern.compile("把[《」]?(.+?)[》」]?(?:的)?作者(?:改为|改成|叫)(.+)").matcher(message);
        if (authorMat.find()) {
            String bn = authorMat.group(1).trim();
            String na = authorMat.group(2).trim();
            Long bid = findBookIdByName(userId, bn);
            if (bid == null) return "未找到《" + bn + "》";
            if (na.isEmpty()) return "作者名不能为空";
            Book eb = bookMapper.selectById(bid);
            bookMapper.updateBookInfo(bid, eb.getTitle(), na, eb.getCategory());
            return "已将《" + bn + "》的作者修改为 " + na;
        }

        // ===== 修改分类 =====
        java.util.regex.Matcher catMat = java.util.regex.Pattern.compile("把[《」]?(.+?)[》」]?(?:的)?分类(?:改为|改成|叫)(.+)").matcher(message);
        if (catMat.find()) {
            String bn = catMat.group(1).trim();
            String nc = catMat.group(2).trim();
            Long bid = findBookIdByName(userId, bn);
            if (bid == null) return "未找到《" + bn + "》";
            if (nc.isEmpty()) return "分类不能为空";
            Book eb = bookMapper.selectById(bid);
            bookMapper.updateBookInfo(bid, eb.getTitle(), eb.getAuthor(), nc);
            return "已将《" + bn + "》的分类修改为 " + nc;
        }

        // ===== 查书籍封面 =====
        if ((m.contains("封面") || m.contains("书皮")) && bookName != null) {
            Long bid = findBookIdByName(userId, bookName);
            if (bid == null) return "未找到《" + bookName + "》";
            Book b = bookMapper.selectById(bid);
            if (b.getCoverUrl() != null && !b.getCoverUrl().isEmpty()) {
                return "《" + bookName + "》的封面URL: " + b.getCoverUrl();
            }
            return "《" + bookName + "》暂无封面";
        }

        // ===== 搜书/搜作者/搜网页 =====
        if (m.contains("搜书") || m.contains("查书") || (m.contains("搜索") && m.contains("书"))) {
            String title = extractBookTitle(message);
            if (title != null && mcpClientService.isAvailable("open-library")) {
                return mcpClientService.searchBookByTitle(title);
            }
        }
        if (m.contains("搜作者") || m.contains("查作者") || (m.contains("作者") && (m.contains("哪些书") || m.contains("作品")))) {
            String name = "";
            var am = java.util.regex.Pattern.compile("(?:查|搜|找)(?:下|一下)?(?:作者|作家)(.+?)(?:的|还|有)?").matcher(message);
            if (am.find()) name = am.group(1).trim();
            if (name.isEmpty()) name = "刘慈欣";
            if (mcpClientService.isAvailable("open-library")) {
                return mcpClientService.searchAuthorByName(name);
            }
        }
        if (m.contains("评价") || m.contains("书评") || m.contains("怎么样")) {
            String q = message;
            if (mcpClientService.isAvailable("open-websearch")) {
                return mcpClientService.searchWeb(q, 5);
            }
        }

        // 查笔记
        if (m.contains("我的笔记") || m.contains("查笔记") || m.contains("查看笔记") || m.contains("有什么笔记")
                || m.contains("笔记内容") || m.contains("写了什么笔记") || m.contains("我的批注") || m.contains("我的摘录")) {
            List<ai.bookmind.entity.Note> notes = noteMapper.selectAllByUserId(userId);
            if (notes.isEmpty()) return "你还没有写任何笔记。";
            StringBuilder sb = new StringBuilder("你的笔记列表：\n");
            for (int i = 0; i < Math.min(notes.size(), 20); i++) {
                var n = notes.get(i);
                sb.append(i + 1).append(". [").append(n.getCategory()).append("] ");
                if (n.getQuoteText() != null && !n.getQuoteText().isEmpty())
                    sb.append("引用：").append(n.getQuoteText().substring(0, Math.min(60, n.getQuoteText().length()))).append(" → ");
                sb.append(n.getContent() != null ? n.getContent().substring(0, Math.min(100, n.getContent().length())) : "");
                sb.append("\n");
            }
            if (notes.size() > 20) sb.append("... 共").append(notes.size()).append("条笔记");
            return sb.toString().trim();
        }

        return null;
    }

    // ==================== 工具定义（Function Calling） ====================

    private static final double BEIJING_LAT = 39.9042;
    private static final double BEIJING_LON = 116.4074;
    private static final com.fasterxml.jackson.databind.ObjectMapper TOOL_JSON = new com.fasterxml.jackson.databind.ObjectMapper();

    private AiApiClient.ToolDefinition weatherTool() {
        return AiApiClient.ToolDefinition.of(
                "get_weather",
                "查询指定城市的实时天气和未来3天预报。支持中国主要城市和全球任意经纬度坐标。",
                Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "city", Map.of("type", "string", "description", "城市名称，如 北京、上海、伦敦"),
                                "latitude", Map.of("type", "number", "description", "纬度（-90~90），不传城市名时使用"),
                                "longitude", Map.of("type", "number", "description", "经度（-180~180），不传城市名时使用")
                        ),
                        "required", List.of()
                )
        );
    }

    private AiApiClient.ToolDefinition newsTool() {
        return AiApiClient.ToolDefinition.of(
                "get_news",
                "获取指定国家和分类的最新新闻头条。",
                Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "country", Map.of("type", "string", "description", "国家代码：cn(中国), us(美国), gb(英国), jp(日本), kr(韩国), de(德国), fr(法国) 等，默认 us"),
                                "category", Map.of("type", "string", "description", "新闻分类：technology(科技), business(商业), sports(体育), entertainment(娱乐), health(健康), science(科学), general(综合)，默认 general")
                        ),
                        "required", List.of()
                )
        );
    }

    private AiApiClient.ToolDefinition notesTool() {
        return AiApiClient.ToolDefinition.of(
                "get_my_notes",
                "查询用户的笔记、批注、摘录、记录等，可按书籍和关键词筛选",
                Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "bookId", Map.of("type", "integer", "description", "书籍ID（可选，不传则查所有书）"),
                                "keyword", Map.of("type", "string", "description", "关键词筛选（可选，例如用户记录了什么、写了什么）")
                        ),
                        "required", List.of()
                )
        );
    }

    private AiApiClient.ToolDefinition timeTool() {
        return AiApiClient.ToolDefinition.of(
                "get_current_time",
                "获取当前日期和时间",
                Map.of("type", "object", "properties", Map.of(), "required", List.of())
        );
    }

    // ==================== MCP 工具定义 ====================

    private AiApiClient.ToolDefinition mcpSearchWebTool() {
        return AiApiClient.ToolDefinition.of(
                "search_web",
                "联网搜索互联网信息，可用于查找书评、书籍推荐、简介、新闻等任何网络信息",
                Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "query", Map.of("type", "string", "description", "搜索关键词，如 '三体 书评'")
                        ),
                        "required", List.of("query")
                )
        );
    }

    private AiApiClient.ToolDefinition mcpSearchBookTool() {
        return AiApiClient.ToolDefinition.of(
                "search_book_by_title",
                "通过 Open Library 按书名搜索图书的结构化信息（作者、出版日期、简介等）",
                Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "title", Map.of("type", "string", "description", "书名")
                        ),
                        "required", List.of("title")
                )
        );
    }

    private AiApiClient.ToolDefinition mcpSearchAuthorTool() {
        return AiApiClient.ToolDefinition.of(
                "search_author_by_name",
                "通过 Open Library 按作者名搜索图书信息和作者介绍",
                Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "name", Map.of("type", "string", "description", "作者名，如 '刘慈欣'")
                        ),
                        "required", List.of("name")
                )
        );
    }

    private AiApiClient.ToolDefinition mcpFetchWebContentTool() {
        return AiApiClient.ToolDefinition.of(
                "fetch_web_content",
                "获取网页内容，用于查看书评、文章详情等",
                Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "url", Map.of("type", "string", "description", "网页 URL")
                        ),
                        "required", List.of("url")
                )
        );
    }

    private AiApiClient.ToolDefinition mcpGetBookByIdTool() {
        return AiApiClient.ToolDefinition.of(
                "get_book_by_id",
                "通过 ISBN、OLID 等标识符搜索图书的详细信息",
                Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "idType", Map.of("type", "string", "enum", List.of("isbn", "lccn", "oclc", "olid"), "description", "标识符类型"),
                                "idValue", Map.of("type", "string", "description", "标识符值")
                        ),
                        "required", List.of("idType", "idValue")
                )
        );
    }

    private AiApiClient.ToolDefinition mcpGetBookCoverTool() {
        return AiApiClient.ToolDefinition.of(
                "get_book_cover",
                "获取图书封面图片的 URL，可用于展示书籍封面",
                Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "key", Map.of("type", "string", "enum", List.of("ISBN", "OCLC", "LCCN", "OLID", "ID"), "description", "标识符类型"),
                                "value", Map.of("type", "string", "description", "标识符值"),
                                "size", Map.of("type", "string", "enum", List.of("S", "M", "L"), "description", "封面尺寸 S/M/L")
                        ),
                        "required", List.of("key", "value")
                )
        );
    }

    private AiApiClient.ToolDefinition mcpGetAuthorInfoTool() {
        return AiApiClient.ToolDefinition.of(
                "get_author_info",
                "获取作者的详细个人信息，需要 Open Library 的 Author Key（如 OL23919A）",
                Map.of("type","object","properties",Map.of("author_key",Map.of("type","string","description","Open Library Author Key，如 OL23919A")),"required",List.of("author_key"))
        );
    }

    private AiApiClient.ToolDefinition mcpFetchGithubReadmeTool() {
        return AiApiClient.ToolDefinition.of("fetch_github_readme","获取 GitHub 仓库的 README 内容",
                Map.of("type","object","properties",Map.of("url",Map.of("type","string","description","GitHub 仓库 URL")),"required",List.of("url")));
    }

    private AiApiClient.ToolDefinition mcpFetchCsdnArticleTool() {
        return AiApiClient.ToolDefinition.of("fetch_csdn_article","获取 CSDN 文章内容",
                Map.of("type","object","properties",Map.of("url",Map.of("type","string","description","CSDN 文章 URL")),"required",List.of("url")));
    }

    private AiApiClient.ToolDefinition mcpFetchJuejinArticleTool() {
        return AiApiClient.ToolDefinition.of("fetch_juejin_article","获取掘金文章内容",
                Map.of("type","object","properties",Map.of("url",Map.of("type","string","description","掘金文章 URL")),"required",List.of("url")));
    }

    private AiApiClient.ToolDefinition mcpFetchLinuxDoArticleTool() {
        return AiApiClient.ToolDefinition.of("fetch_linuxdo_article","获取 Linux.do 论坛文章内容",
                Map.of("type","object","properties",Map.of("url",Map.of("type","string","description","Linux.do 文章 URL")),"required",List.of("url")));
    }

    // ==================== 书籍管理工具定义 ====================

    private AiApiClient.ToolDefinition deleteBookTool() {
        return AiApiClient.ToolDefinition.of("delete_book",
                "删除用户自己在BookMind中的某本书籍（同时删除笔记、知识图谱和向量数据）。" +
                "当用户说「删除xx」「移除xx」「把xx删掉」「不要xx这本书了」「清理掉xx」「xx不要了」等要求删除某本书时，调用此工具。" +
                "注意：如果用户只说「删本书」「删一本书」但没有指定书名，先调用get_all_book_names列出所有书让用户选择。必须传入bookName参数。",
                Map.of("type","object","properties",Map.of("bookName",Map.of("type","string","description","要删除的书籍名称")),"required",List.of("bookName")));
    }

    private AiApiClient.ToolDefinition renameBookTitleTool() {
        return AiApiClient.ToolDefinition.of("rename_book_title",
                "修改用户自己在BookMind中的某本书籍的名称/标题。" +
                "当用户说「把xx改名为xx」「修改xx书名为xx」「重命名xx为xx」「改书名叫xx」「xx改名成xx」等要求重命名书籍时，调用此工具。" +
                "注意：如果用户只说「改书名」「重命名」但没有指定是哪本书和/或新名字，先调用get_all_book_names列出所有书让用户选择。必须传入bookName和newTitle两个参数。",
                Map.of("type","object","properties",Map.of("bookName",Map.of("type","string","description","当前书籍名称"),"newTitle",Map.of("type","string","description","新的书籍名称")),"required",List.of("bookName","newTitle")));
    }

    private AiApiClient.ToolDefinition renameBookAuthorTool() {
        return AiApiClient.ToolDefinition.of("rename_book_author",
                "修改用户自己在BookMind中的某本书籍的作者名。" +
                "当用户说「把xx的作者改为xx」「修改xx书的作者为xx」「xx书的作者写错了改成xx」「更新xx的作者信息」「改作者名」等要求修改作者时，调用此工具。" +
                "注意：如果用户只说「改作者」但没有指定是哪本书，先调用get_all_book_names列出所有书让用户选择。必须传入bookName和newAuthor两个参数。",
                Map.of("type","object","properties",Map.of("bookName",Map.of("type","string","description","书籍名称"),"newAuthor",Map.of("type","string","description","新的作者名")),"required",List.of("bookName","newAuthor")));
    }

    private AiApiClient.ToolDefinition changeBookCategoryTool() {
        return AiApiClient.ToolDefinition.of("change_book_category",
                "修改用户自己在BookMind中的某本书籍的分类，例如小说、历史、科技、文学、哲学等。" +
                "当用户说「把xx的分类改为xx」「修改xx的类别」「xx书归类到xx」「这本书分类不对」「换个分类」等要求修改分类时，调用此工具。" +
                "注意：如果用户只说「改分类」但没有指定书或新分类，先调用get_all_book_names列出所有书。必须传入bookName和newCategory两个参数。",
                Map.of("type","object","properties",Map.of("bookName",Map.of("type","string","description","书籍名称"),"newCategory",Map.of("type","string","description","新的分类名称")),"required",List.of("bookName","newCategory")));
    }

    private AiApiClient.ToolDefinition getBookCountTool() {
        return AiApiClient.ToolDefinition.of("get_book_count",
                "查询用户自己在BookMind中共有多少本书籍（返回总数）。" +
                "当用户问「我有多少本书」「我的书库有几本书」「我上传了几本书」「查一下书籍数量」「统计一下我的书」「我一共有几本」等关于书籍总数的问题时，调用此工具。直接返回数字即可。",
                Map.of("type","object","properties",Map.of(),"required",List.of()));
    }

    private AiApiClient.ToolDefinition getAllBookNamesTool() {
        return AiApiClient.ToolDefinition.of("get_all_book_names",
                "查询用户自己在BookMind中的所有书籍的名称列表（返回书名和作者）。当用户问「我有哪几本书」「我的书是哪些」「列出我的书」「看看我的书库」「显示所有书」「查一下我有什么书」「我上传了哪些书」「我的藏书记录」等关于个人藏书的问题时，必须调用此工具获取数据。注意：这是查询用户自己数据库中的藏书，不是搜索外部书籍。",
                Map.of("type","object","properties",Map.of(),"required",List.of()));
    }

    // ==================== 工具执行 ====================

    private String executeToolCall(Long userId, AiApiClient.ToolCallRequest tc) {
        try {
            var args = TOOL_JSON.readTree(tc.functionArgs());

            return switch (tc.functionName()) {
                case "get_current_time" -> {
                    yield java.time.LocalDateTime.now().format(
                            java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss EEEE"));
                }
                case "get_my_notes" -> {
                    boolean hasBid = args.has("bookId") && !args.get("bookId").isNull();
                    String kw = args.has("keyword") ? args.get("keyword").asText() : null;
                    List<ai.bookmind.entity.Note> notes = hasBid
                            ? noteMapper.selectByBookId(userId, args.get("bookId").asLong())
                            : noteMapper.selectAllByUserId(userId);
                    if (kw != null && !kw.isEmpty()) {
                        String q = kw.toLowerCase();
                        notes = notes.stream()
                                .filter(n -> n.getContent().toLowerCase().contains(q) || (n.getQuoteText() != null && n.getQuoteText().toLowerCase().contains(q)))
                                .toList();
                    }
                    if (notes.isEmpty()) {
                        yield "没有找到匹配的笔记";
                    } else {
                        StringBuilder sb = new StringBuilder();
                        int lim = Math.min(notes.size(), 15);
                        for (int i = 0; i < lim; i++) {
                            var n = notes.get(i);
                            sb.append(String.format("[笔记] %s\n  引用: %s\n  内容: %s\n",
                                    n.getCategory(),
                                    n.getQuoteText() != null ? n.getQuoteText().substring(0, Math.min(100, n.getQuoteText().length())) : "",
                                    n.getContent().substring(0, Math.min(200, n.getContent().length()))));
                        }
                        if (notes.size() > 15) sb.append("... 共").append(notes.size()).append("条笔记");
                        yield sb.toString();
                    }
                }
                case "get_weather" -> {
                    String city = args.has("city") && !args.get("city").isNull()
                            ? args.get("city").asText() : null;
                    double lat, lon;
                    String cityName;

                    if (city != null && toolApiService.hasCity(city)) {
                        double[] coords = toolApiService.getCityCoords(city);
                        lat = coords[0];
                        lon = coords[1];
                        cityName = city;
                    } else if (args.has("latitude") && args.has("longitude")) {
                        lat = args.get("latitude").asDouble();
                        lon = args.get("longitude").asDouble();
                        cityName = args.has("city") ? args.get("city").asText() : String.format("%.2f,%.2f", lat, lon);
                    } else if (city != null) {
                        double[] gc = toolApiService.getCityCoords(city);
                        if (gc != null) { lat = gc[0]; lon = gc[1]; cityName = city; }
                        else { lat = BEIJING_LAT; lon = BEIJING_LON; cityName = city; }
                    } else {
                        lat = BEIJING_LAT; lon = BEIJING_LON; cityName = "北京";
                    }
                    yield toolApiService.queryWeather(lat, lon, cityName);
                }
                case "get_news" -> {
                    String country = args.has("country") && !args.get("country").isNull()
                            ? args.get("country").asText() : "us";
                    String category = args.has("category") && !args.get("category").isNull()
                            ? args.get("category").asText() : "general";
                    String countryName = toolApiService.getCountryName(country);
                    yield toolApiService.queryNews(category, country, countryName);
                }
                case "search_web" -> {
                    String q = args.has("query") ? args.get("query").asText() : "";
                    yield mcpClientService.searchWeb(q, 10);
                }
                case "search_book_by_title" -> {
                    String title = args.has("title") ? args.get("title").asText() : "";
                    yield mcpClientService.searchBookByTitle(title);
                }
                case "search_author_by_name" -> {
                    String name = args.has("name") ? args.get("name").asText() : "";
                    yield mcpClientService.searchAuthorByName(name);
                }
                case "fetch_web_content" -> {
                    String url = args.has("url") ? args.get("url").asText() : "";
                    yield mcpClientService.fetchWebContent(url);
                }
                case "get_book_by_id" -> {
                    String idType = args.has("idType") ? args.get("idType").asText() : "isbn";
                    String idValue = args.has("idValue") ? args.get("idValue").asText() : "";
                    yield mcpClientService.callTool("open-library", "get_book_by_id",
                            Map.of("idType", idType, "idValue", idValue));
                }
                case "get_book_cover" -> {
                    String key = args.has("key") ? args.get("key").asText() : "ISBN";
                    String value = args.has("value") ? args.get("value").asText() : "";
                    String size = args.has("size") ? args.get("size").asText() : "M";
                    yield mcpClientService.callTool("open-library", "get_book_cover",
                            Map.of("key", key, "value", value, "size", size));
                }
                case "get_author_info" -> {
                    String ak = args.has("author_key") ? args.get("author_key").asText() : "";
                    yield mcpClientService.callTool("open-library", "get_author_info", Map.of("author_key", ak));
                }
                case "fetch_github_readme" -> {
                    yield mcpClientService.callTool("open-websearch", "fetchGithubReadme",
                            Map.of("url", args.has("url") ? args.get("url").asText() : ""));
                }
                case "fetch_csdn_article" -> {
                    yield mcpClientService.callTool("open-websearch", "fetchCsdnArticle",
                            Map.of("url", args.has("url") ? args.get("url").asText() : ""));
                }
                case "fetch_juejin_article" -> {
                    yield mcpClientService.callTool("open-websearch", "fetchJuejinArticle",
                            Map.of("url", args.has("url") ? args.get("url").asText() : ""));
                }
                case "fetch_linuxdo_article" -> {
                    yield mcpClientService.callTool("open-websearch", "fetchLinuxDoArticle",
                            Map.of("url", args.has("url") ? args.get("url").asText() : ""));
                }
                // ==================== 书籍管理工具 ====================
                case "delete_book" -> {
                    String bn = args.has("bookName") ? args.get("bookName").asText() : "";
                    Long bid = findBookIdByName(userId, bn);
                    if (bid == null) yield "数据库没有《" + bn + "》这本书，请确认书名是否正确";
                    try {
                        bookUploadService.deleteBook(userId, bid);
                        yield "删除成功";
                    } catch (IllegalArgumentException e) {
                        yield "数据库没有《" + bn + "》这本书，请确认书名是否正确";
                    } catch (Exception e) {
                        yield "删除失败，请你手动删除。原因: " + e.getMessage();
                    }
                }
                case "rename_book_title" -> {
                    String bn = args.has("bookName") ? args.get("bookName").asText() : "";
                    String nt = args.has("newTitle") ? args.get("newTitle").asText() : "";
                    Long bid = findBookIdByName(userId, bn);
                    if (bid == null) yield "未找到名为《" + bn + "》的书籍";
                    if (nt.isEmpty()) yield "新书名不能为空";
                    Book eb = bookMapper.selectById(bid);
                    bookMapper.updateBookInfo(bid, nt, eb.getAuthor(), eb.getCategory());
                    yield "已将《" + bn + "》重命名为《" + nt + "》";
                }
                case "rename_book_author" -> {
                    String bn = args.has("bookName") ? args.get("bookName").asText() : "";
                    String na = args.has("newAuthor") ? args.get("newAuthor").asText() : "";
                    Long bid = findBookIdByName(userId, bn);
                    if (bid == null) yield "未找到名为《" + bn + "》的书籍";
                    if (na.isEmpty()) yield "作者名不能为空";
                    Book eb2 = bookMapper.selectById(bid);
                    bookMapper.updateBookInfo(bid, eb2.getTitle(), na, eb2.getCategory());
                    yield "已将《" + bn + "》的作者修改为 " + na;
                }
                case "change_book_category" -> {
                    String bn = args.has("bookName") ? args.get("bookName").asText() : "";
                    String nc = args.has("newCategory") ? args.get("newCategory").asText() : "";
                    Long bid = findBookIdByName(userId, bn);
                    if (bid == null) yield "未找到名为《" + bn + "》的书籍";
                    if (nc.isEmpty()) yield "分类不能为空";
                    Book eb3 = bookMapper.selectById(bid);
                    bookMapper.updateBookInfo(bid, eb3.getTitle(), eb3.getAuthor(), nc);
                    yield "已将《" + bn + "》的分类修改为 " + nc;
                }
                case "get_book_count" -> {
                    int cnt = bookMapper.countByUserId(userId);
                    yield "你共有 " + cnt + " 本书籍";
                }
                case "get_all_book_names" -> {
                    List<Book> allBooks = bookMapper.selectByUserId(userId);
                    if (allBooks.isEmpty()) yield "你还没有上传任何书籍";
                    StringBuilder sb2 = new StringBuilder("你的书籍列表：\n");
                    for (int i = 0; i < allBooks.size(); i++) {
                        Book b = allBooks.get(i);
                        sb2.append(i + 1).append(". 《").append(b.getTitle() != null ? b.getTitle() : "未命名").append("》");
                        if (b.getAuthor() != null && !b.getAuthor().isEmpty()) sb2.append(" - ").append(b.getAuthor());
                        sb2.append("\n");
                    }
                    yield sb2.toString();
                }
                default -> "未知工具: " + tc.functionName();
            };
        } catch (Exception e) {
            log.warn("执行工具调用失败: {}", tc.functionName(), e);
            return "工具调用失败: " + e.getMessage();
        }
    }

    // ==================== 自由对话（工具对话：天气/新闻/时间/MCP，无笔记/书籍检索）====================

    public Flux<String> globalChat(Long userId, String message, String sessionId) {
        String historyCtx = buildHistoryContext(userId, sessionId, "local");

        // 关键词匹配（快速响应天气/新闻）
        String toolResult = toolApiService.executeToolCall(message);
        if (toolResult != null) {
            String systemPrompt = buildGlobalSystemPrompt(historyCtx);
            String toolContext = "\n\n【工具返回的实时数据】\n" + toolResult + "\n\n请基于以上实时数据回答用户的问题。";
            String userWithHistory = "用户说：" + message + "\n" + historyCtx;
            return callModel("global", message, systemPrompt + toolContext, userWithHistory);
        }

        // Function Calling（天气/新闻/时间/MCP，无笔记）
        List<AiApiClient.ToolDefinition> tools = List.of(
                weatherTool(), newsTool(), timeTool(),
                mcpSearchWebTool(), mcpSearchBookTool(), mcpSearchAuthorTool(),
                mcpFetchWebContentTool(), mcpGetBookByIdTool(), mcpGetBookCoverTool(),
                mcpGetAuthorInfoTool(), mcpFetchGithubReadmeTool(),
                mcpFetchCsdnArticleTool(), mcpFetchJuejinArticleTool(), mcpFetchLinuxDoArticleTool());
        String systemPrompt = buildGlobalSystemPrompt(historyCtx);
        List<Map<String, String>> msgs = List.of(Map.of("role", "user", "content",
                "用户说：" + message + "\n" + historyCtx));

        Long uid = userId;
        return aiApiClient.chatWithTools(msgs, activeChatModel(), 0.5, systemPrompt, tools,
                tc -> executeToolCall(uid, tc))
                .onErrorResume(e -> {
                    log.warn("Tool 失败，降级到搜索: {}", e.getMessage());
                    return fallbackToWebSearch(message, systemPrompt, historyCtx);
                });
    }

    /**
     * MCP 增强：检测消息是否需要查书/作者/联网，返回格式化上下文（书籍对话使用）
     */
    private String enrichWithMcp(String message) {
        if (message == null || message.isBlank()) return "";
        boolean needsBook = message.contains("作者") || message.contains("写过哪些书")
                || message.contains("出版") || message.contains("简介")
                || message.contains("书评") || message.contains("推荐");
        boolean needsWeb = message.contains("搜索") || message.contains("搜一下")
                || message.contains("查一下") || message.contains("评价")
                || message.contains("怎么样") || message.contains("好看吗");

        StringBuilder sb = new StringBuilder();
        if (needsBook && mcpClientService.isAvailable("open-library")) {
            String title = extractBookTitle(message);
            if (title != null) {
                String r = mcpClientService.searchBookByTitle(title);
                if (r != null && !r.contains("不可用")) {
                    sb.append("【外部图书信息】").append(r).append("\n");
                }
            }
        }
        if (needsWeb && mcpClientService.isAvailable("open-websearch")) {
            String r = mcpClientService.searchWeb(message, 5);
            if (r != null && !r.isEmpty() && !r.contains("不可用")) {
                sb.append("【联网搜索结果】").append(r).append("\n");
            }
        }
        return sb.toString().trim();
    }

    /** 简单提取书名（双引号/书名号括起的内容） */
    private String extractBookTitle(String message) {
        var m = java.util.regex.Pattern.compile("[「《]([^」》]+)[」》]").matcher(message);
        if (m.find()) return m.group(1);
        m = java.util.regex.Pattern.compile("[\"“]([^\"”]+)[\"”]").matcher(message);
        if (m.find()) return m.group(1);
        return null;
    }

    /** 更灵活地提取书名：先试书名号，再从删除/改名等上下文中提取 */
    private String extractBookName(String message, String lower) {
        String t = extractBookTitle(message);
        if (t != null) return t;
        var m = java.util.regex.Pattern.compile("(?:删除|移除|删掉|把|改名|重命名)[《」]?(.{2,10})[》」]?").matcher(message);
        if (m.find()) return m.group(1).trim();
        m = java.util.regex.Pattern.compile("[《」](.{2,12})[》」]").matcher(message);
        if (m.find()) return m.group(1).trim();
        return null;
    }

    private String buildGlobalSystemPrompt(String historyCtx) {
        return "你是BookMind助手，专注于书籍阅读、文字与成长陪伴。你的回答围应当绕：\n"
                + "- 📚 书籍推荐、阅读感悟、文学讨论、文字传播\n"
                + "- 🌱 积极向上、人生感悟、自我成长、思想交流\n"
                + "- ☕ 日常生活、学习工作、心情分享、知识探讨\n"
                + "- ✍️ 写作、文字表达、内容创作、阅读方法\n\n"
                + "你能使用以下工具获取实时信息（需要时主动调用）：\n"
                + "🌤 天气 get_weather  | 📰 新闻 get_news  | ⏰ 时间 get_current_time\n"
                + "🔍 联网搜索 search_web  | 🌐 获取网页 fetch_web_content\n"
                + "📖 按书名查书 search_book_by_title  | ✍️ 按作者查书 search_author_by_name\n"
                + "🔢 按ISBN查书 get_book_by_id  | 🖼 获取封面 get_book_cover\n\n"
                + "注意：日常模式下没有笔记查询和书籍内容检索功能。回答简洁温暖，自然引导话题到书籍和成长方向。"
                + (historyCtx.isEmpty() ? "" : "\n\n" + historyCtx);
    }

    /** 笔记关键词查询 */
    private Flux<String> fallbackToWebSearch(String message, String systemPrompt, String historyCtx) {
        String searchResult = "";
        if (needsWebSearch(message) && mcpClientService.isAvailable("open-websearch")) {
            log.info("触发联网搜索: query={}", message);
            searchResult = mcpClientService.searchWeb(message, 5);
            if (searchResult != null && !searchResult.isEmpty() && !searchResult.contains("不可用")) {
                searchResult = "\n\n【联网搜索结果】\n" + searchResult;
            } else {
                searchResult = "";
            }
        }
        String userWithHistory = "用户说：" + message + "\n" + historyCtx;
        return callModel("global", message, systemPrompt + searchResult, userWithHistory);
    }

    private boolean needsWebSearch(String message) {
        if (message == null || message.isEmpty()) return false;
        String lower = message.toLowerCase();

        String[] triggers = {
                "新闻", "最新", "今天", "现在", "天气", "实时", "当前",
                "2025", "2026", "最近", "发生了什么", "热点",
                "news", "latest", "today", "weather", "current",
                "trending", "update", "recent", "股价", "股票",
                "价格", "汇率", "走势", "行情"
        };

        for (String trigger : triggers) {
            if (lower.contains(trigger)) return true;
        }

        if (message.length() > 15 && !message.contains("检索") && !message.contains("本书") && !message.contains("笔记")) {
            if (message.contains("什么") || message.contains("怎么") || message.contains("如何")
                    || message.contains("哪些") || message.contains("哪个") || message.contains("为什么")) {
                return true;
            }
        }

        return false;
    }

    // ==================== RAG 检索 ====================

    private List<Document> searchBookContent(Long userId, Long bookId, String query) {
        String collection = HybridVectorService.bookCollection(bookId);
        String kw = extractKeyword(query);
        List<Document> allDocs = new ArrayList<>();

        // KG 实体增强：按问题意图过滤类型，注入前 20 个实体名
        StringBuilder enhancedQuery = new StringBuilder(query);
        try {
            Set<String> targetTypes = detectEntityTypes(query);  // 意图识别
            List<ai.bookmind.entity.KnowledgeNode> nodes = kgNodeMapper.selectByBookId(userId, bookId);
            if (nodes != null && !nodes.isEmpty()) {
                Set<String> added = new HashSet<>();
                for (var n : nodes) {
                    if (n.getName() == null || n.getName().length() < 2) continue;
                    if (!targetTypes.contains(n.getType())) continue;
                    if (!added.add(n.getName())) continue;
                    enhancedQuery.append(" ").append(n.getName());
                    if (added.size() >= 20) break;
                }
            }
        } catch (Exception e) {
            log.warn("KG 实体注入失败(不影响检索): {}", e.getMessage());
        }

        // 1. 三通道检索：dense(60) + sparse(30) → hybridSearch 已内部 RRF 融合取 top 30
        try {
            List<Document> vecDocs = hybridVectorService.hybridSearch(collection, enhancedQuery.toString(), 30,
                    String.format("userId == '%s' && bookId == '%s'", userId, bookId));
            if (vecDocs != null) allDocs.addAll(vecDocs);
        } catch (Exception e) {
            log.warn("向量检索异常: {}", e.getMessage());
        }

        // 2. FULLTEXT 关键词通道（limit=10）
        if (kw != null && kw.length() >= 2) {
            try {
                List<ai.bookmind.entity.Chapter> chs = chapterMapper.searchContentFulltext(bookId, kw, 15);
                Set<String> seenIds = allDocs.stream()
                        .map(d -> d.getMetadata().getOrDefault("chapterId", "").toString())
                        .collect(Collectors.toSet());
                for (ai.bookmind.entity.Chapter ch : chs) {
                    String cid = ch.getId() != null ? ch.getId().toString() : "";
                    if (!seenIds.contains(cid)) {
                        seenIds.add(cid);
                        allDocs.add(new Document(ch.getContent() != null ? ch.getContent() : "",
                                Map.of("type", "chapter", "userId", userId.toString(), "bookId", bookId.toString(),
                                        "chapterNumber", ch.getChapterNumber().toString(),
                                        "chapterId", cid,
                                        "chapterTitle", ch.getTitle() != null ? ch.getTitle() : "",
                                        "_source", "keyword")));
                    }
                }
            } catch (Exception e) {
                log.warn("FULLTEXT 检索异常: {}", e.getMessage());
            }
        }

        if (allDocs.isEmpty()) return List.of();

        // 3. 分批 Rerank：每批 ≤25 条，短摘要（200字+标题）避免超 8K token
        int batchSize = 25;
        List<Document> rerankedAll = new ArrayList<>();
        for (int b = 0; b < allDocs.size(); b += batchSize) {
            int end = Math.min(b + batchSize, allDocs.size());
            List<Document> batch = allDocs.subList(b, end);
            // 生成短摘要：【章节标题】前 200 字
            List<String> summaries = batch.stream().map(d -> {
                String title = d.getMetadata().getOrDefault("chapterTitle", "").toString();
                String text = d.getContent();
                String shortText = text != null ? text.substring(0, Math.min(200, text.length())) : "";
                return title.isEmpty() ? shortText : "【" + title + "】" + shortText;
            }).toList();
            var reranked = aiApiClient.rerank(query, summaries, batch.size());
            if (reranked != null && !reranked.isEmpty()) {
                for (var item : reranked) {
                    int idx = (int) item.get("index");
                    if (idx >= 0 && idx < batch.size()) {
                        Document d = new Document(batch.get(idx).getContent(), new HashMap<>(batch.get(idx).getMetadata()));
                        d.getMetadata().put("rerankScore", (Double) item.get("score"));
                        d.getMetadata().put("_batch", b / batchSize);
                        rerankedAll.add(d);
                    }
                }
            } else {
                rerankedAll.addAll(batch);
            }
        }
        // 跨批次 RRF 融合 → top 10
        Map<String, Double> rrfFused = new HashMap<>();
        Map<String, Document> docMap = new HashMap<>();
        int k = 60;
        for (int i = 0; i < rerankedAll.size(); i++) {
            Document d = rerankedAll.get(i);
            String key = d.getMetadata().getOrDefault("chapterId", "ch" + i).toString();
            Integer batchIdx = (Integer) d.getMetadata().getOrDefault("_batch", 0);
            rrfFused.merge(key, 1.0 / (k + i - batchIdx * batchSize), Double::sum);
            if (!docMap.containsKey(key)) docMap.put(key, d);
        }
        List<Map.Entry<String, Double>> sorted = new ArrayList<>(rrfFused.entrySet());
        sorted.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
        allDocs = new ArrayList<>();
        for (var e : sorted) {
            Document d = docMap.get(e.getKey());
            if (d != null) { allDocs.add(d); if (allDocs.size() >= 15) break; }
        }

        // 4. 父子扩展：滑动窗口 ±2 章节
        for (int i = 0; i < allDocs.size() && i < 10; i++) {
            Document doc = allDocs.get(i);
            try {
                int chNum = Integer.parseInt(doc.getMetadata().getOrDefault("chapterNumber", "0").toString());
                Long bid = Long.parseLong(doc.getMetadata().getOrDefault("bookId", "0").toString());
                StringBuilder ctx = new StringBuilder();
                for (int offset = -2; offset <= 2; offset++) {
                    int targetCh = chNum + offset;
                    if (targetCh < 1) continue;
                    ai.bookmind.entity.Chapter adj = chapterMapper.selectByBookAndChapter(bid, targetCh);
                    if (adj != null && adj.getContent() != null) {
                        if (ctx.length() > 0) ctx.append("\n---\n");
                        ctx.append("[CHAPTER ").append(targetCh).append("]\n").append(adj.getContent());
                    }
                }
                if (ctx.length() > 0) doc = new Document(ctx.toString(), doc.getMetadata());
            } catch (Exception ignore) {}
        }

        log.info("检索完成: {} 条结果 (dense+sparse+fulltext→rerank)", allDocs.size());
        return allDocs;
    }

    /** 从提问中提取关键词 */
    private String extractKeyword(String query) {
        String kw = query.replaceAll("[的是吗么了？?！!，,。.、的着了过在就和都以及与或]|谁|什么|怎么|如何|哪里|哪些|哪个|为什么|何时|请问|怎样|是否|有没有|能不能", "").trim();
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("[\\u4e00-\\u9fff]{2}").matcher(kw);
        if (m.find()) return m.group();
        if (kw.length() >= 2) return kw.substring(0, 2);
        return null;
    }

    /** 意图识别：规则优先（0ms），未命中则走 LLM 兜底 */
    private static final java.util.LinkedHashMap<java.util.regex.Pattern, Set<String>> RULE_MAP = new java.util.LinkedHashMap<>();
    static {
        addRule(".*(哪里|在哪|位置|地点|位于|何处|什么地方|哪个地方|哪个位置|哪个区域).*", "LOCATION");
        addRule(".*(谁|人物|角色|主角|配角|反派|身世|背景|是谁|姓甚名谁|何许人).*", "PERSON");
        addRule(".*(组织|势力|宗门|门派|帮派|家族|集团|团伙|结盟|团队|公司|机构|联盟).*", "ORGANIZATION");
        addRule(".*(事件|大赛|战斗|战役|退婚|经历|发生|过程|故事|情节|经过|冲突|争斗|比武|挑战).*", "EVENT");
        addRule(".*(技能|功法|斗技|能力|招式|绝招|武器|宝物|道具|异宝|药|丹药|秘籍|心法|法术|天赋|异能|属性).*", "CONCEPT");
        addRule(".*(哪些强者|有哪些人|哪些角色|哪些势力|哪些宗门|哪些组织).*", "PERSON", "ORGANIZATION");
        addRule(".*(什么势力|哪个势力|哪个宗门|哪个家族).*", "ORGANIZATION");
        addRule(".*(是什么|是什么东西|是何物|什么法宝|什么宝物).*", "CONCEPT");
        addRule(".*(怎么了|发生了什么|出了什么事|结果如何|结局如何).*", "EVENT");
        addRule(".*(哪里人|老家|出生地|居所|住处|住在哪).*", "LOCATION", "PERSON");
        addRule(".*(关系|交情|恩怨|师徒|朋友|敌人|恋人|夫妻|兄弟|姐妹|父子|母女).*", "PERSON", "ORGANIZATION");
        addRule(".*(目的|目标|动机|原因|为何|理由).*", "EVENT", "CONCEPT");
        addRule(".*(来由|起源|来历|历史|背景).*", "EVENT", "ORGANIZATION");
        addRule(".*(哪些|什么|怎样的|如何).*", "PERSON", "ORGANIZATION", "LOCATION", "CONCEPT", "EVENT");
    }
    private static void addRule(String regex, String... types) {
        RULE_MAP.put(java.util.regex.Pattern.compile(regex), Set.of(types));
    }

    private Set<String> detectEntityTypes(String query) {
        for (var entry : RULE_MAP.entrySet()) {
            if (entry.getKey().matcher(query).matches()) return entry.getValue();
        }
        // 规则未命中 → LLM 兜底
        try {
            String prompt = String.format("你是一个命名实体识别助手。根据用户问题，输出需要利用的知识图谱实体类型。" +
                    "类型包括：PERSON, ORGANIZATION, LOCATION, CONCEPT, EVENT。" +
                    "只输出类型名，多个用逗号分隔，不要解释。\n问题：%s\n输出：", query);
            String result = aiApiClient.chatSync(
                    List.of(Map.of("role", "user", "content", prompt)),
                    activeChatModel(), 0.1, "你是一个精准的命名实体识别助手。")
                    .trim();
            if (!result.isEmpty()) {
                Set<String> types = new HashSet<>();
                for (String t : result.split("[,，]")) {
                    String clean = t.trim().toUpperCase();
                    if (clean.matches("PERSON|ORGANIZATION|LOCATION|CONCEPT|EVENT")) types.add(clean);
                }
                if (!types.isEmpty()) return types;
            }
        } catch (Exception e) {
            log.warn("LLM 意图识别失败: {}", e.getMessage());
        }
        return Set.of("PERSON", "ORGANIZATION", "LOCATION", "CONCEPT");
    }

    private List<Document> searchUserNotes(Long userId, Long bookId, String query) {
        try {
            String collection = HybridVectorService.bookCollection(bookId);
            return hybridVectorService.hybridSearch(collection, query, 2,
                    String.format("userId == '%s' && bookId == '%s' && type == 'note'", userId, bookId));
        } catch (Exception e) {
            log.error("笔记检索失败", e);
            return List.of();
        }
    }

    private List<Document> searchAllBooksContent(Long userId, String query) {
        try {
            String filter = String.format("userId == '%s' && type == 'chapter'", userId);
            return hybridVectorService.globalSearch(query, 10, filter);
        } catch (Exception e) {
            log.error("跨书检索失败", e);
            return List.of();
        }
    }

    private List<Document> searchAllUserNotes(Long userId, String query) {
        try {
            String filter = String.format("userId == '%s' && type == 'note'", userId);
            return hybridVectorService.globalSearch(query, 3, filter);
        } catch (Exception e) {
            log.error("跨书笔记检索失败", e);
            return List.of();
        }
    }

    private String buildContext(List<Document> bookDocs, List<Document> noteDocs, String query) {
        if (bookDocs.isEmpty() && noteDocs.isEmpty()) return "";
        StringBuilder context = new StringBuilder();
        if (!bookDocs.isEmpty()) {
            context.append("【书籍内容】\n");
            for (int i = 0; i < bookDocs.size(); i++) {
                Document doc = bookDocs.get(i);
                String bookTitle = doc.getMetadata().getOrDefault("bookTitle", "").toString();
                String chTitle = doc.getMetadata().getOrDefault("chapterTitle", "未知章节").toString();
                String chNum = doc.getMetadata().getOrDefault("chapterNumber", "?").toString();
                String content = doc.getContent();
                context.append(String.format("[%d] 《%s》第%s章 · %s\n    %s\n\n", i + 1,
                        bookTitle.isEmpty() ? "" : bookTitle, chNum, chTitle,
                        content.length() > 1500 ? content.substring(0, 1500) + "…" : content));
            }
        }
        if (!noteDocs.isEmpty()) {
            context.append("【用户笔记】\n");
            for (int i = 0; i < noteDocs.size(); i++) {
                Document doc = noteDocs.get(i);
                String bookTitle = doc.getMetadata().getOrDefault("bookTitle", "").toString();
                context.append(String.format("[%d] 《%s》笔记: %s\n\n", i + 1,
                        bookTitle.isEmpty() ? "" : bookTitle,
                        doc.getContent().length() > 1000 ? doc.getContent().substring(0, 1000) + "…" : doc.getContent()));
            }
        }
        return context.toString();
    }

    /** 缓存 book title 5 分钟，避免每次查库 */
    private String getCachedBookTitle(Long bookId) {
        String cacheKey = "book:title:" + bookId;
        try {
            Object cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached instanceof String s && !s.isEmpty()) return s;
        } catch (Exception e) { /* ignore cache miss */ }
        Book book = bookMapper.selectById(bookId);
        String title = book != null ? book.getTitle() : "未知书籍";
        try { redisTemplate.opsForValue().set(cacheKey, title, 5, TimeUnit.MINUTES); } catch (Exception e) { /* ignore */ }
        return title;
    }

    // ==================== 对话历史 Redis 存储 ====================

    public void saveMessage(Long userId, String sessionId, String mode, String role, String content) {
        String indexKey = sessionIndexKey(userId, mode);
        long now = System.currentTimeMillis();

        @SuppressWarnings("unchecked")
        Map<String, Object> idx = (Map<String, Object>) redisTemplate.opsForHash().get(indexKey, sessionId);

        if (idx == null) {
            String timeBucket = LocalDateTime.now().format(TIME_BUCKET_FMT);
            String keyname = makeKeyname(content);
            String msgKey = messageKey(userId, mode, timeBucket, keyname);

            Map<String, Object> indexEntry = new HashMap<>();
            indexEntry.put("mode", mode);
            indexEntry.put("timeBucket", timeBucket);
            indexEntry.put("keyname", keyname);
            indexEntry.put("chatname", keyname);
            indexEntry.put("lastTime", now);
            redisTemplate.opsForHash().put(indexKey, sessionId, indexEntry);

            List<Map<String, Object>> data = new ArrayList<>();
            Map<String, Object> meta = new HashMap<>();
            meta.put("chatname", keyname);
            meta.put("sessionId", sessionId);
            meta.put("mode", mode);
            meta.put("timeBucket", timeBucket);
            data.add(meta);

            Map<String, Object> msg = new HashMap<>();
            msg.put("role", role);
            msg.put("content", content);
            msg.put("time", now);
            data.add(msg);

            redisTemplate.opsForValue().set(msgKey, data, SESSION_TTL_DAYS, TimeUnit.DAYS);
        } else {
            String mode2 = (String) idx.get("mode");
            String timeBucket = (String) idx.get("timeBucket");
            String keyname = (String) idx.get("keyname");
            String msgKey = messageKey(userId, mode2, timeBucket, keyname);

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> data = (List<Map<String, Object>>) redisTemplate.opsForValue().get(msgKey);
            if (data == null) {
                data = new ArrayList<>();
                Map<String, Object> meta = new HashMap<>();
                meta.put("chatname", keyname);
                meta.put("sessionId", sessionId);
                meta.put("mode", mode2);
                meta.put("timeBucket", timeBucket);
                data.add(meta);
            }

            Map<String, Object> msg = new HashMap<>();
            msg.put("role", role);
            msg.put("content", content);
            msg.put("time", now);
            data.add(msg);
            // 更新索引中的最后时间
            idx.put("lastTime", now);
            redisTemplate.opsForHash().put(indexKey, sessionId, idx);
            redisTemplate.opsForValue().set(msgKey, data, SESSION_TTL_DAYS, TimeUnit.DAYS);
        }
    }

    private String makeKeyname(String content) {
        String trimmed = content.trim();
        if (trimmed.length() <= 5) return trimmed;
        return trimmed.substring(0, 5);
    }

    public List<Map<String, Object>> getHistory(Long userId, String sessionId, String mode) {
        String msgKey = resolveMessageKey(userId, sessionId, mode);
        if (msgKey == null) return List.of();

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> all = (List<Map<String, Object>>) redisTemplate.opsForValue().get(msgKey);
        if (all == null || all.size() <= 1) return List.of();
        return all.subList(1, all.size());
    }

    public List<Map<String, Object>> searchSessions(Long userId, String keyword, String mode) {
        if (keyword == null || keyword.isBlank()) return List.of();
        String q = keyword.toLowerCase();
        List<Map<String, Object>> result = new ArrayList<>();
        String[] modes = (mode != null && !mode.isEmpty()) ? new String[]{mode} : new String[]{"local", "book"};

        // 如果指定 mode 没结果，自动搜索全部 mode
        boolean autoExpand = (mode != null && !mode.isEmpty());
        for (String m : modes) {
            if (autoExpand && !result.isEmpty()) break;
            List<Map<String, Object>> sessions = getUserSessions(userId, m);
            for (Map<String, Object> s : sessions) {
                String sid = (String) s.get("sessionId");
                String chatname = (String) s.get("chatname");
                String msgKey = resolveMessageKey(userId, sid, m);
                if (msgKey == null) continue;
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> msgs = (List<Map<String, Object>>) redisTemplate.opsForValue().get(msgKey);
                if (msgs == null) continue;

                boolean matched = false;
                String firstMatch = null;
                String matchRole = null;
                for (Map<String, Object> msg : msgs) {
                    String content = (String) msg.get("content");
                    if (content != null && content.toLowerCase().contains(q)) {
                        if (firstMatch == null) {
                            firstMatch = content.length() > 120 ? content.substring(0, 120) + "..." : content;
                            matchRole = (String) msg.get("role");
                        }
                        matched = true;
                    }
                }
                if (matched || (chatname != null && chatname.toLowerCase().contains(q))) {
                    if (firstMatch == null && chatname != null) {
                        firstMatch = "对话: " + (chatname.length() > 60 ? chatname.substring(0, 60) : chatname);
                    }
                    Map<String, Object> item = new HashMap<>(s);
                    item.put("preview", firstMatch != null ? firstMatch : "");
                    item.put("matchRole", matchRole != null ? matchRole : "");
                    item.put("keyword", keyword);
                    result.add(item);
                }
            }
        }
        result.sort((a, b) -> Long.compare(
                ((Number) b.get("lastTime")).longValue(),
                ((Number) a.get("lastTime")).longValue()));
        return result;
    }

    public void updateChatName(Long userId, String sessionId, String mode, String newName) {
        String indexKey = sessionIndexKey(userId, mode);

        @SuppressWarnings("unchecked")
        Map<String, Object> idx = (Map<String, Object>) redisTemplate.opsForHash().get(indexKey, sessionId);
        if (idx == null) return;

        String timeBucket = (String) idx.get("timeBucket");
        String oldKeyname = (String) idx.get("keyname");
        if (oldKeyname == null || oldKeyname.equals(newName)) return;

        String oldKey = messageKey(userId, mode, timeBucket, oldKeyname);
        String newKey = messageKey(userId, mode, timeBucket, newName);

        Boolean renamed = redisTemplate.renameIfAbsent(oldKey, newKey);
        if (renamed == null || !renamed) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> data = (List<Map<String, Object>>) redisTemplate.opsForValue().get(oldKey);
            if (data != null && !data.isEmpty()) {
                data.get(0).put("chatname", newName);
                redisTemplate.opsForValue().set(newKey, data, SESSION_TTL_DAYS, TimeUnit.DAYS);
                redisTemplate.delete(oldKey);
            }
        } else {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> data = (List<Map<String, Object>>) redisTemplate.opsForValue().get(newKey);
            if (data != null && !data.isEmpty()) {
                data.get(0).put("chatname", newName);
                redisTemplate.opsForValue().set(newKey, data, SESSION_TTL_DAYS, TimeUnit.DAYS);
            }
        }

        idx.put("keyname", newName);
        idx.put("chatname", newName);
        redisTemplate.opsForHash().put(indexKey, sessionId, idx);
    }

    public void deleteSession(Long userId, String sessionId, String mode) {
        String indexKey = sessionIndexKey(userId, mode);

        @SuppressWarnings("unchecked")
        Map<String, Object> idx = (Map<String, Object>) redisTemplate.opsForHash().get(indexKey, sessionId);
        if (idx == null) return;

        String timeBucket = (String) idx.get("timeBucket");
        String keyname = (String) idx.get("keyname");
        String msgKey = messageKey(userId, mode, timeBucket, keyname);

        redisTemplate.delete(msgKey);
        // 清理关联的 thinking 键和消息键
        String thinkingKey = "chathistory:thinking:" + userId + ":" + sessionId;
        redisTemplate.delete(thinkingKey);
        redisTemplate.opsForHash().delete(indexKey, sessionId);
    }

    public List<Map<String, Object>> getUserSessions(Long userId, String mode) {
        if (mode == null) return List.of();
        String indexKey = sessionIndexKey(userId, mode);

        @SuppressWarnings("unchecked")
        Map<Object, Object> entries = redisTemplate.opsForHash().entries(indexKey);

        List<Map<String, Object>> sessions = new ArrayList<>();
        long now = System.currentTimeMillis();

        for (Map.Entry<Object, Object> e : entries.entrySet()) {
            String sid = (String) e.getKey();
            @SuppressWarnings("unchecked")
            Map<String, Object> idx = (Map<String, Object>) e.getValue();

            String timeBucket = (String) idx.get("timeBucket");
            String keyname = (String) idx.get("keyname");
            String chatname = idx.containsKey("chatname") ? (String) idx.get("chatname") : keyname;
            Object lastTimeObj = idx.get("lastTime");
            long lastTime = lastTimeObj instanceof Number ? ((Number) lastTimeObj).longValue() : now;

            Map<String, Object> s = new HashMap<>();
            s.put("sessionId", sid);
            s.put("chatname", chatname != null ? chatname : keyname);
            s.put("lastTime", lastTime);
            s.put("timeBucket", timeBucket);
            s.put("fromNow", formatTimeAgo(lastTime));
            sessions.add(s);
        }

        sessions.sort((a, b) -> Long.compare(
                ((Number) b.get("lastTime")).longValue(),
                ((Number) a.get("lastTime")).longValue()));

        return sessions;
    }

    private String formatTimeAgo(long timestampMs) {
        long now = System.currentTimeMillis();
        long diff = now - timestampMs;
        if (diff < 0) return "刚刚";

        long seconds = diff / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;

        if (days >= 1) {
            return new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
                    .format(new java.util.Date(timestampMs));
        } else if (hours >= 1) {
            return hours + "小时前";
        } else if (minutes >= 1) {
            return minutes + "分钟前";
        } else {
            return "刚刚";
        }
    }

    private String resolveMessageKey(Long userId, String sessionId, String mode) {
        String indexKey = sessionIndexKey(userId, mode);

        @SuppressWarnings("unchecked")
        Map<String, Object> idx = (Map<String, Object>) redisTemplate.opsForHash().get(indexKey, sessionId);
        if (idx == null) return null;

        String timeBucket = (String) idx.get("timeBucket");
        String keyname = (String) idx.get("keyname");
        return messageKey(userId, mode, timeBucket, keyname);
    }

    // ==================== 章节/书籍摘要 ====================

    public String generateChapterSummary(Long userId, Long bookId, Integer chapterNumber) {
        Chapter chapter = chapterMapper.selectByBookAndChapter(bookId, chapterNumber);
        if (chapter == null || chapter.getContent() == null) return "章节不存在";

        String systemP = "你是一个专业的书籍摘要助手。请用简洁的语言总结章节核心内容，突出重点观点和情节发展。";
        String userP = String.format("""
                请为以下章节生成简洁摘要（200-300字），突出核心观点和重要内容：

                标题：%s

                内容：
                %s
                """, chapter.getTitle(),
                chapter.getContent().substring(0, Math.min(5000, chapter.getContent().length())));

        List<Map<String, String>> msgs = List.of(Map.of("role", "user", "content", userP));
        return aiApiClient.chatSync(msgs, activeChatModel(), 0.5, systemP);
    }

    public String generateBookSummary(Long userId, Long bookId) {
        try {
            Book book = bookMapper.selectById(bookId);
            if (book == null) return "书籍不存在";

            String cacheKey = "booksummary:" + userId + ":" + bookId;
            try {
                Object cached = redisTemplate.opsForValue().get(cacheKey);
                if (cached instanceof String) {
                    log.info("命中书籍摘要缓存: bookId={}", bookId);
                    return (String) cached;
                }
            } catch (Exception e) {
                log.warn("读取摘要缓存失败，重新生成: bookId={}", bookId, e);
            }

            List<Chapter> chapters = chapterMapper.selectByBookId(bookId);
            if (chapters.isEmpty()) return "无章节内容";

            List<String> subSummaries = new ArrayList<>();
            int batchSize = Math.min(5, chapters.size());
            for (int i = 0; i < chapters.size(); i += batchSize) {
                int end = Math.min(i + batchSize, chapters.size());
                StringBuilder batchContent = new StringBuilder();
                for (Chapter ch : chapters.subList(i, end)) {
                    if (ch.getContent() != null)
                        batchContent.append(String.format("第%d章: %s\n%s\n\n", ch.getChapterNumber(), ch.getTitle(),
                                ch.getContent().substring(0, Math.min(1000, ch.getContent().length()))));
                }
                if (batchContent.isEmpty()) continue;
                String subP = "请为以下章节生成摘要（每章100-150字）：\n\n" + batchContent;
                List<Map<String, String>> msgs = List.of(Map.of("role", "user", "content", subP));
                try {
                    subSummaries.add(aiApiClient.chatSync(msgs, activeChatModel(), 0.5,
                            "你是一个专业的书籍摘要助手。"));
                } catch (Exception e) {
                    log.warn("子摘要生成失败: {}", e.getMessage());
                    subSummaries.add("（第" + (i / batchSize + 1) + "批摘要生成失败）");
                }
            }

            if (subSummaries.isEmpty()) return "摘要生成失败";

            String reduceP = String.format("整合以下子摘要生成全书摘要（800-1000字），要求结构清晰、涵盖核心内容：\n\n%s",
                    String.join("\n\n---\n\n", subSummaries));
            List<Map<String, String>> msgs = List.of(Map.of("role", "user", "content", reduceP));
            String summary;
            try {
                summary = aiApiClient.chatSync(msgs, activeChatModel(), 0.5,
                        "你是专业的书籍摘要整合专家，擅长将多个子摘要整合为连贯的全书概要。");
            } catch (Exception e) {
                log.warn("摘要整合失败，返回原始子摘要: {}", e.getMessage());
                summary = String.join("\n\n", subSummaries);
            }

            try {
                redisTemplate.opsForValue().set(cacheKey, summary, 7, TimeUnit.DAYS);
            } catch (Exception e) {
                log.warn("写入摘要缓存失败: bookId={}", bookId, e);
            }

            return summary;
        } catch (Exception e) {
            log.error("全书摘要生成失败: bookId={}", bookId, e);
            return "摘要生成失败，请稍后重试";
        }
    }

    // ==================== 知识图谱抽取（Key-S 专用模型） ====================

    /**
     * 知识图谱抽取 — 使用 Key-S (GLM-Z1-9B-0414, temperature=0.3)
     */
    public String extractKnowledgeGraphRaw(Long userId, Long bookId, String content) {
        Book book = bookMapper.selectById(bookId);
        String bookTitle = book != null ? book.getTitle() : "未知书籍";

        String truncatedContent = content;
        if (content.length() > 30000) {
            int third = content.length() / 3;
            truncatedContent = content.substring(0, 12000)
                    + "\n...(中间省略)...\n"
                    + content.substring(third, Math.min(third + 10000, content.length()))
                    + "\n...(省略)...\n"
                    + content.substring(Math.min(2 * third, content.length() - 8000));
        } else if (content.length() > 15000) {
            truncatedContent = content.substring(0, 10000)
                    + "\n...(省略)...\n"
                    + content.substring(content.length() - 8000);
        }

        String p = String.format("""
                你是一个知识图谱抽取专家。请从《%s》的文本中抽取重要实体和关系。

                输出要求（严格遵循）：
                1. 只输出纯JSON，不要包含任何其他文字、说明或markdown包裹
                2. JSON格式固定为：
                {"nodes":[{"name":"实体名称","type":"类型","description":"简要描述"}],"edges":[{"source":"源实体","target":"目标实体","relation":"关系描述（如'创建了'、'对抗'、'位于'等）"}]}
                3. 实体类型必须是以下之一：person（人物）, organization（组织）, location（地点）, concept（概念）, event（事件）
                4. 提取至少12个实体，覆盖核心人物、重要地点、关键事件、重要概念/组织
                5. 实体要有描述（description字段），说明该实体在书中的角色或意义
                6. 边的关系（relation）要具体，如"创建了ETO"比"关联"更好
                7. 重大事件只提取最重要的3-5个，地点只提取核心地点，概念/组织只概括关键的
                8. 输出内容必须能被JSON.parse直接解析

                内容：
                %s""", bookTitle, truncatedContent);

        try {
            // 使用 Key-S + GLM-Z1-9B-0414，temperature=0.3
            List<Map<String, String>> msgs = List.of(Map.of("role", "user", "content", p));
            return aiApiClient.specialChatSync(msgs, activeKgModel(), 0.3,
                    "你是一个JSON输出专家，只输出有效的JSON，不包含任何其他文字。");
        } catch (Exception e) {
            log.warn("GLM-Z1 知识图谱抽取失败，降级到 Qwen3-8B: {}", e.getMessage());
            List<Map<String, String>> msgs = List.of(Map.of("role", "user", "content", p));
            return aiApiClient.chatSync(msgs, activeKgModel(), 0.3,
                    "你是一个JSON输出专家，只输出有效的JSON，不包含任何其他文字。");
        }
    }

    /** 按书名查找用户书籍ID（精确→模糊） */
    private Long findBookIdByName(Long userId, String bookName) {
        if (bookName == null || bookName.isBlank()) return null;
        String trimmed = bookName.trim();
        List<Book> books = bookMapper.selectByUserId(userId);
        // 精确匹配
        for (Book b : books) {
            if (trimmed.equals(b.getTitle())) return b.getId();
        }
        // 模糊匹配
        for (Book b : books) {
            if (b.getTitle() != null && (b.getTitle().contains(trimmed) || trimmed.contains(b.getTitle()))) return b.getId();
        }
        return null;
    }

    @Deprecated
    public Map<String, Object> extractKnowledgeGraph(Long userId, Long bookId) {
        Book book = bookMapper.selectById(bookId);
        List<Chapter> chapters = chapterMapper.selectByBookId(bookId);
        StringBuilder content = new StringBuilder();
        for (Chapter ch : chapters) {
            if (ch.getContent() != null)
                content.append(ch.getContent().substring(0, Math.min(500, ch.getContent().length()))).append("\n");
        }
        String json = extractKnowledgeGraphRaw(userId, bookId, content.toString());
        Map<String, Object> result = new HashMap<>();
        result.put("raw", json);
        return result;
    }
}
