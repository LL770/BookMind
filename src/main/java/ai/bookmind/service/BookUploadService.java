package ai.bookmind.service;

import ai.bookmind.ai.AiApiClient;
import ai.bookmind.entity.Book;
import ai.bookmind.entity.Chapter;
import ai.bookmind.mapper.BookMapper;
import ai.bookmind.mapper.ChapterMapper;
import ai.bookmind.mapper.NoteMapper;
import ai.bookmind.service.ReadingStatsService;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.pdf.PDFParserConfig;
import org.apache.tika.sax.BodyContentHandler;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class BookUploadService {

    private final MinioClient minioClient;
    private final BookMapper bookMapper;
    private final ChapterMapper chapterMapper;
    private final VectorizationService vectorizationService;
    private final KnowledgeGraphService knowledgeGraphService;
    private final RabbitTemplate rabbitTemplate;
    private final AiApiClient aiApiClient;
    private final RedisTemplate<String, Object> redisTemplate;
    private final NoteMapper noteMapper;

    @Value("${spring.minio.bucket-name}")
    private String bucketName;

    @Value("${bookmind.vector.chunk-size}")
    private int chunkSize;

    @Value("${bookmind.vector.chunk-overlap}")
    private int chunkOverlap;

    @Value("${bookmind.max-file-size-pdf:200}")
    private int maxFileSizePdfMb;

    @Value("${bookmind.max-file-size-txt:20}")
    private int maxFileSizeTxtMb;

    @Value("${bookmind.max-file-size-docx:50}")
    private int maxFileSizeDocxMb;

    @Value("${bookmind.max-file-size-epub:50}")
    private int maxFileSizeEpubMb;

    private static final long STREAMING_THRESHOLD = 100 * 1024 * 1024; // 100MB

    /**
     * 上传书籍 ‒ 异步版：上传到 MinIO → 创建书籍记录 → 发送 MQ → 立即返回
     */
    public Book uploadBook(Long userId, MultipartFile file, String title, String author, String category) throws Exception {
        if (file.isEmpty()) throw new IllegalArgumentException("文件不能为空");
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null) throw new IllegalArgumentException("文件名无效");

        String fileExtension = getFileExtension(originalFilename);
        long fileSize = file.getSize();
        // 按格式检查文件大小
        long maxSizeMb = switch (fileExtension.toLowerCase()) {
            case "pdf" -> maxFileSizePdfMb;
            case "txt", "md" -> maxFileSizeTxtMb;
            case "docx" -> maxFileSizeDocxMb;
            case "epub" -> maxFileSizeEpubMb;
            default -> 50;  // 其他格式 50MB
        };
        long maxSizeBytes = maxSizeMb * 1024L * 1024L;
        if (fileSize > maxSizeBytes) {
            throw new IllegalArgumentException(String.format("文件过大（%.1fMB），%s 格式上限 %dMB",
                    fileSize / (1024.0 * 1024), fileExtension.toUpperCase(), maxSizeMb));
        }

        // 计算文件 SHA256 hash
        String fileHash = computeSha256(file.getInputStream());

        String uniqueFileName = userId + "_" + UUID.randomUUID().toString().replace("-", "") + "." + fileExtension;
        String fileUrl = uploadToMinio(file, uniqueFileName);
        Book book = insertBookRecord(userId, title, originalFilename, author, category, fileUrl, file.getSize(), fileExtension, fileHash);

        log.info("书籍上传成功(异步): bookId={}, userId={}, file={}, hash={}", book.getId(), userId, originalFilename, fileHash);

        // 发送 MQ 消息，异步处理
        Map<String, Object> msg = new HashMap<>();
        msg.put("type", "parse");
        msg.put("bookId", book.getId());
        msg.put("userId", userId);
        rabbitTemplate.convertAndSend("bookmind.process.exchange", "bookmind.process.parse", msg);

        return book;
    }

    /**
     * 从已合并的 MinIO 文件创建书籍并触发异步处理（分片上传完成后调用）
     */
    @Transactional
    public Book createBookFromMergedFile(Long userId, String fileUrl, String fileName,
                                          String title, String author, String category, Long fileSize,
                                          String fileHash) {
        String fileExtension = getFileExtension(fileName);
        Book book = insertBookRecord(userId, title, fileName, author, category, fileUrl, fileSize, fileExtension, fileHash);

        log.info("分片上传完成，创建书籍: bookId={}, userId={}, file={}, hash={}", book.getId(), userId, fileName, fileHash);

        Map<String, Object> msg = new HashMap<>();
        msg.put("type", "parse");
        msg.put("bookId", book.getId());
        msg.put("userId", userId);
        rabbitTemplate.convertAndSend("bookmind.process.exchange", "bookmind.process.parse", msg);

        return book;
    }

    @Transactional
    public Book insertBookRecord(Long userId, String title, String originalFilename, String author,
                                  String category, String fileUrl, Long fileSize, String fileExtension,
                                  String fileHash) {
        Book book = new Book();
        book.setUserId(userId);
        book.setTitle(title != null ? title : removeExtension(originalFilename));
        book.setAuthor(author);
        book.setCategory(category != null ? category : "其他");
        book.setFileUrl(fileUrl);
        book.setFileSize(fileSize);
        book.setFormat(fileExtension.toLowerCase());
        book.setFileHash(fileHash);
        book.setStatus(0);
        book.setProgress(10);
        book.setProcessMessage("文件已上传，等待处理...");
        book.setCreateTime(LocalDateTime.now());
        bookMapper.insert(book);
        return book;
    }

    // ======================== MQ 消费者调用的入口 ========================

    /** 步骤1: 解析 → 入库 → 发向量化消息 */
    public void processBookFromQueue(Long bookId, Long userId) {
        try {
            log.info("MQ 开始解析书籍: bookId={}", bookId);
            bookMapper.updateStatusProgress(bookId, 1, 0, "开始解析文件...");

            doParseBookText(bookId);

            int chapterCount = chapterMapper.countByBookId(bookId);
            log.info("章节已保存: bookId={}, count={}", bookId, chapterCount);
            if (chapterCount == 0) {
                throw new RuntimeException("章节保存失败，chapter 表中无记录");
            }

            // 解析完成 → 用户可阅读
            bookMapper.updateStatusProgress(bookId, 1, 33, "解析完成，可点击阅读");

            // 异步生成封面，不阻塞向量化消息
            CompletableFuture.runAsync(() -> generateCover(bookId, userId));

            // 发送向量化消息（不阻塞）
            Map<String, Object> msg = Map.of(
                    "type", "vectorize",
                    "bookId", bookId,
                    "userId", userId
            );
            rabbitTemplate.convertAndSend("bookmind.process.exchange", "bookmind.process.parse", msg);
            log.info("已发送向量化消息: bookId={}", bookId);

        } catch (Throwable e) {
            log.error("书籍解析失败: bookId={}", bookId, e);
            bookMapper.updateStatusProgress(bookId, 4, 0, "解析失败: " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()));
        }
    }

    /** 步骤2: 向量化 — 先查同书hash，有则跳过直接复制KG */
    public void processVectorize(Long bookId, Long userId) {
        try {
            log.info("MQ 开始向量化: bookId={}", bookId);

            Book book = bookMapper.selectById(bookId);
            if (book == null) { log.warn("书籍不存在: bookId={}", bookId); return; }

            // 查同书：相同 file_hash 且已完成处理的书籍
            if (book.getFileHash() != null && !book.getFileHash().isBlank()) {
                Book existing = bookMapper.selectByFileHash(book.getFileHash(), bookId);
                if (existing != null) {
                    log.info("发现同书，跳过向量化和KG: bookId={}, sourceBookId={}", bookId, existing.getId());
                    bookMapper.updateStatusProgress(bookId, 2, 66, "检测到同书，直接复制数据...");
                    knowledgeGraphService.copyGraph(existing.getUserId(), existing.getId(), userId, bookId);
                    int nc = knowledgeGraphService.countNodes(userId, bookId);
                    bookMapper.updateStatusProgress(bookId, 3, 100, "全部完成（同书共享）");
                    bookMapper.updateKgGenerated(bookId, nc > 0 ? 1 : 0);
                    log.info("同书处理完成: bookId={}, nodes={}", bookId, nc);
                    return;
                }
            }

            // 没有同书：正常向量化
            vectorizationService.vectorizeBook(userId, bookId);

            bookMapper.updateStatusProgress(bookId, 2, 66, "向量化完成，可使用AI对话");

            Map<String, Object> msg = Map.of(
                    "type", "graph",
                    "bookId", bookId,
                    "userId", userId
            );
            rabbitTemplate.convertAndSend("bookmind.process.exchange", "bookmind.process.parse", msg);
            log.info("已发送知识图谱消息: bookId={}", bookId);

        } catch (Throwable e) {
            log.error("向量化失败: bookId={}", bookId, e);
            bookMapper.updateStatusProgress(bookId, 4, 0, "向量化失败: " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()));
        }
    }

    /** 步骤3: 知识图谱 — U1 Fast 生成信息图 + 传统 KG 实体抽取 */
    public void processGraph(Long bookId, Long userId) {
        try {
            log.info("MQ 开始生成知识图谱: bookId={}", bookId);
            bookMapper.updateStatusProgress(bookId, 2, 66, "知识图谱生成中...");

            boolean kgOk = knowledgeGraphService.generateGraph(userId, bookId);
            bookMapper.updateKgGenerated(bookId, kgOk ? 1 : 0);

            bookMapper.updateStatusProgress(bookId, 3, 100, "全部完成，可使用知识图谱");
            log.info("书籍全部处理完成: bookId={}", bookId);

        } catch (Throwable e) {
            log.warn("知识图谱生成失败(不影响阅读和对话): bookId={}", bookId, e);
            bookMapper.updateKgGenerated(bookId, 0);
            bookMapper.updateStatusProgress(bookId, 3, 100, "处理完成（知识图谱生成失败）");
        }
    }

    // ======================== 封面生成（向量化前执行） ========================

    private void generateCover(Long bookId, Long userId) {
        try {
            Book book = bookMapper.selectById(bookId);
            if (book == null || (book.getCoverUrl() != null && !book.getCoverUrl().isBlank())) return;

            String cleanTitle = book.getTitle().replaceAll("[\\p{P}\\p{S}]", "").trim();
            String prompt = String.format(
                    "书籍封面设计，高级大气质感。书名「%s」以精美书法字体或典雅宋体置于画面上方三分之一处，"
                    + "字体颜色与背景形成高级对比，书名边缘有细腻烫金/描边效果。"
                    + "下方三分之二区域为抽象意境背景，点缀简约几何线条、书卷墨迹元素或星空光影，"
                    + "疏密有致，留白考究。整体色调沉稳（深蓝/墨绿/赭石/黑金），"
                    + "光影层次丰富，纸质纹理质感。画面干净，不出现人物，不出现多余文字或符号。",
                    cleanTitle);
            String imageUrl = aiApiClient.generateInfographic(prompt);
            if (imageUrl != null && imageUrl.startsWith("http")) {
                String coverName = "covers/" + userId + "_" + bookId + "_" + System.currentTimeMillis() + ".png";
                try (InputStream in = new java.net.URL(imageUrl).openConnection().getInputStream()) {
                    minioClient.putObject(io.minio.PutObjectArgs.builder()
                            .bucket(bucketName).object(coverName).stream(in, -1, 5 * 1024 * 1024)
                            .contentType("image/png").build());
                }
                bookMapper.updateCoverUrl(bookId, "/api/files/" + bucketName + "/" + coverName);
                log.info("封面已生成: bookId={}", bookId);
            }
        } catch (Exception e) {
            log.warn("封面生成失败(向量化继续): bookId={}", bookId, e);
        }
    }

    // ======================== MinIO 操作 ========================

    public String uploadToMinio(MultipartFile file, String fileName) throws Exception {
        try (InputStream inputStream = file.getInputStream()) {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucketName)
                            .object(fileName)
                            .stream(inputStream, file.getSize(), -1)
                            .contentType(file.getContentType())
                            .build()
            );
        }
        return getMinioUrl(fileName);
    }

    public String getMinioUrl(String fileName) {
        return "http://localhost:9000/" + bucketName + "/" + fileName;
    }

    private String getFileExtension(String filename) {
        int lastDot = filename.lastIndexOf('.');
        return lastDot > 0 ? filename.substring(lastDot + 1) : "txt";
    }

    private String removeExtension(String filename) {
        int lastDot = filename.lastIndexOf('.');
        return lastDot > 0 ? filename.substring(0, lastDot) : filename;
    }

    // ======================== 删除 ========================

    @Transactional(rollbackFor = Exception.class)
    public void deleteBook(Long userId, Long bookId) throws Exception {
        Book book = bookMapper.selectById(bookId);
        if (book == null || !book.getUserId().equals(userId)) {
            throw new IllegalArgumentException("书籍不存在或无权限");
        }
        // 删除 MinIO 文件
        String fileName = extractFileName(book.getFileUrl());
        if (fileName != null) {
            try { minioClient.removeObject(RemoveObjectArgs.builder().bucket(bucketName).object(fileName).build()); }
            catch (Exception e) { log.warn("删除MinIO文件失败: {}", fileName, e); }
        }
        // 删除封面
        if (book.getCoverUrl() != null) {
            String coverName = extractFileName(book.getCoverUrl());
            if (coverName != null) {
                try { minioClient.removeObject(RemoveObjectArgs.builder().bucket(bucketName).object(coverName).build()); }
                catch (Exception e) { log.warn("删除封面失败: {}", coverName, e); }
            }
        }
        // 删除章节
        try { chapterMapper.deleteByBookId(bookId); } catch (Exception e) { log.warn("删除章节失败", e); }
        // 删除笔记
        try { noteMapper.deleteByBookId(bookId); } catch (Exception e) { log.warn("删除笔记失败", e); }
        // 删除向量
        try { vectorizationService.deleteBookVectors(userId, bookId); } catch (Exception e) { log.warn("删除向量失败", e); }
        // 删除知识图谱
        try { knowledgeGraphService.deleteGraph(userId, bookId); } catch (Exception e) { log.warn("删除KG失败", e); }
        // 删除 Redis 阅读进度
        try {
            String progressKey = "user:reading:progress:" + userId + ":" + bookId;
            redisTemplate.delete(progressKey);
        } catch (Exception e) { log.warn("删除阅读进度失败", e); }
        // 删除书籍记录
        bookMapper.deleteById(bookId);
        log.info("书籍删除成功: bookId={}", bookId);
    }

    /**
     * 更新书籍封面
     */
    public void updateBookInfo(Long bookId, String title, String author, String category) {
        bookMapper.updateBookInfo(bookId, title, author, category);
    }

    public void updateBookCover(Long userId, Long bookId, MultipartFile file) throws Exception {
        Book book = bookMapper.selectById(bookId);
        if (book == null || !book.getUserId().equals(userId)) {
            throw new IllegalArgumentException("书籍不存在或无权限");
        }
        String ext = file.getOriginalFilename();
        ext = ext != null && ext.contains(".") ? ext.substring(ext.lastIndexOf('.')) : ".jpg";
        String coverName = "covers/" + userId + "_" + bookId + "_" + System.currentTimeMillis() + ext;
        try (InputStream is = file.getInputStream()) {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucketName).object(coverName).stream(is, file.getSize(), -1)
                    .contentType(file.getContentType()).build());
        }
        bookMapper.updateCoverUrl(bookId, "/api/files/" + bucketName + "/" + coverName);
        log.info("封面上传成功: bookId={}, coverName={}", bookId, coverName);
    }

    private String extractFileName(String fileUrl) {
        if (fileUrl == null) return null;
        int lastSlash = fileUrl.lastIndexOf('/');
        return lastSlash >= 0 ? fileUrl.substring(lastSlash + 1) : fileUrl;
    }

    // ======================== 核心解析：流式 + 可恢复 ========================

    public void doParseBookText(Long bookId) {
        try {
            log.info("开始解析书籍: bookId={}", bookId);
            bookMapper.updateStatusProgress(bookId, 1, 20, "正在解析文件...");

            Book book = bookMapper.selectById(bookId);
            if (book == null) return;

            String fileName = extractFileName(book.getFileUrl());

            // 删除旧章节（避免重复）
            chapterMapper.deleteByBookId(bookId);

            long fileSize = book.getFileSize() != null ? book.getFileSize() : 0;

            if ("pdf".equalsIgnoreCase(book.getFormat())) {
                parsePdfStreaming(bookId, fileName, fileSize);
            } else if ("txt".equalsIgnoreCase(book.getFormat()) || "md".equalsIgnoreCase(book.getFormat())) {
                parseTxtStreaming(bookId, fileName, fileSize);
            } else {
                parseWithTika(bookId, fileName, fileSize);
            }

        } catch (Throwable e) {
            log.error("解析书籍失败: bookId={}", bookId, e);
            bookMapper.updateStatusProgress(bookId, 4, 0, "解析失败: " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()));
            throw new RuntimeException(e);
        }
    }

    // ------------------ PDF 流式解析（逐页提取 → 立即写入章节） ------------------

    /**
     * DeepSeek-OCR 回退：将 PDF 渲染为图片后调用 AI OCR（Key-S）
     */
    private void tryOcrFallback(Long bookId, org.apache.pdfbox.pdmodel.PDDocument document, int totalPages) {
        try {
            org.apache.pdfbox.rendering.PDFRenderer renderer = new org.apache.pdfbox.rendering.PDFRenderer(document);
            int ocrLimit = Math.min(totalPages, 30);
            StringBuilder fullText = new StringBuilder();
            int chapterNum = 0;

            for (int page = 0; page < ocrLimit; page++) {
                java.awt.image.BufferedImage img = renderer.renderImageWithDPI(page, 200);
                String encoded = encodeImageToBase64(img);
                String pageText = aiApiClient.ocrImage(encoded, page + 1);

                if (pageText != null && !pageText.trim().isEmpty()) {
                    fullText.append(pageText).append("\n\n");
                }

                if (fullText.length() > 2000 || page == ocrLimit - 1) {
                    String content = fullText.toString().trim();
                    if (!content.isEmpty()) {
                        insertChapter(bookId, ++chapterNum, "第" + chapterNum + "页", content);
                        fullText = new StringBuilder();
                    }
                }

                if (page % 5 == 0) {
                    int pct = 35 + (int) ((double) page / ocrLimit * 15);
                    bookMapper.updateStatusProgress(bookId, 1, pct,
                            "OCR 解析中 " + (page + 1) + "/" + ocrLimit + " 页");
                }
            }

            log.info("DeepSeek-OCR 完成: bookId={}, 处理页数={}, 章节数={}", bookId, ocrLimit, chapterNum);
        } catch (Exception e) {
            log.warn("DeepSeek-OCR 回退失败(不影响已有章节): bookId={}", bookId, e);
        }
    }

    private String encodeImageToBase64(java.awt.image.BufferedImage img) throws Exception {
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(img, "png", baos);
        return java.util.Base64.getEncoder().encodeToString(baos.toByteArray());
    }

    private void parsePdfStreaming(Long bookId, String fileName, long fileSize) throws Exception {
        log.info("PDF流式解析: bookId={}, fileSize={}", bookId, fileSize);
        bookMapper.updateStatusProgress(bookId, 1, 1, "PDF 流式解析中...");

        java.nio.file.Path tempFile = null;
        try (InputStream pdfStream = minioClient.getObject(
                io.minio.GetObjectArgs.builder().bucket(bucketName).object(fileName).build())) {

            // 用临时文件避免全量内存加载（PDFBox 3.0.1 不支持直接读 InputStream）
            tempFile = java.nio.file.Files.createTempFile("pdf-", ".pdf");
            java.nio.file.Files.copy(pdfStream, tempFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }

        // PDF 解析（直接读临时文件，不占 JVM 堆）
        try (org.apache.pdfbox.pdmodel.PDDocument document = org.apache.pdfbox.Loader.loadPDF(tempFile.toFile())) {
            int totalPages = document.getNumberOfPages();
            if (document.isEncrypted()) {
                document.setAllSecurityToBeRemoved(true);
            }

            org.apache.pdfbox.text.PDFTextStripper stripper = new org.apache.pdfbox.text.PDFTextStripper();
            stripper.setSortByPosition(true);
            stripper.setShouldSeparateByBeads(false);
            stripper.setSuppressDuplicateOverlappingText(true);

            // 逐页提取并流式写入章节
            StringBuilder chapterBuf = new StringBuilder();
            int chapterNum = 0;
            int charsSinceLastWrite = 0;

            for (int page = 1; page <= totalPages; page++) {
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                String pageText = stripper.getText(document);

                if (pageText != null && !pageText.trim().isEmpty()) {
                    // 后处理：还原段落层次
                    String cleaned = cleanupPdfText(pageText);
                    chapterBuf.append(cleaned).append("\n\n");
                    charsSinceLastWrite += cleaned.length();
                }

                if (charsSinceLastWrite >= 100000 || page == totalPages) {
                    String content = chapterBuf.toString().trim();
                    if (!content.isEmpty()) {
                        insertChapter(bookId, ++chapterNum, "第" + chapterNum + "页", content);
                        chapterBuf = new StringBuilder();
                        charsSinceLastWrite = 0;
                    }
                }

                if (page % 5 == 0) {
                    int pct = (int) ((double) page / totalPages * 33);
                    bookMapper.updateStatusProgress(bookId, 1, pct,
                            "PDF 解析中 " + page + "/" + totalPages + " 页");
                }
            }

            int totalChapters = chapterMapper.countByBookId(bookId);
            int totalWords = chapterMapper.selectByBookId(bookId).stream()
                    .mapToInt(c -> c.getContent().length()).sum();

            // 检测扫描版PDF
            boolean likelyScanned = totalWords < 500 && totalPages > 5;
            if (likelyScanned) {
                log.warn("PDFBox 提取文本过少({}字/{}页)，尝试 DeepSeek-OCR: bookId={}", totalWords, totalPages, bookId);
                bookMapper.updateStatusProgress(bookId, 1, 35, "检测到扫描版PDF，尝试 AI OCR...");
                chapterMapper.deleteByBookId(bookId);
                tryOcrFallback(bookId, document, totalPages);
                totalChapters = chapterMapper.countByBookId(bookId);
                totalWords = chapterMapper.selectByBookId(bookId).stream()
                        .mapToInt(c -> c.getContent().length()).sum();
            }

            bookMapper.updateMetadata(bookId, null, null, totalChapters, totalWords);
            log.info("PDF 流式解析完成: bookId={}, 章节数={}, 字数={}", bookId, totalChapters, totalWords);
            bookMapper.updateStatusProgress(bookId, 1, 33, "文本解析完成 (" + totalChapters + " 章)");
        } finally {
            if (tempFile != null) {
                try { java.nio.file.Files.deleteIfExists(tempFile); } catch (Exception ignored) {}
            }
        }
    }

    /**
     * PDF 文本后处理：还原段落层次、列表、多栏
     */
    private String cleanupPdfText(String raw) {
        if (raw == null || raw.isEmpty()) return raw;
        // 行内换行（同一段落内折行）→ 空格
        String t = raw.replaceAll("([^\\n])\\n([^\\n])", "$1 $2");
        // 多个空行 → 单段落间隔
        t = t.replaceAll("\\n{3,}", "\n\n");
        // 列表符号开头 → 前加换行
        t = t.replaceAll("(?m)^(\\s*[•·‣⁃►※]\\s)", "\n$1");
        t = t.replaceAll("(?m)^(\\s*\\d+[\\.、\\)]\\s)", "\n$1");
        return t.trim();
    }

    // ------------------ TXT 流式解析（BufferedReader 逐行 → 积攒写入） ------------------

    private void parseTxtStreaming(Long bookId, String fileName, long fileSize) throws Exception {
        log.info("TXT 流式解析: bookId={}, fileSize={}", bookId, fileSize);
        bookMapper.updateStatusProgress(bookId, 1, 1, "TXT 流式解析中...");

        try (InputStream s = minioClient.getObject(
                io.minio.GetObjectArgs.builder().bucket(bucketName).object(fileName).build())) {

            // 编码检测：读前 4KB 判断 UTF-8/GBK
            byte[] header = new byte[4096];
            int headerLen = s.read(header);
            if (headerLen <= 0) throw new RuntimeException("TXT 内容为空");

            Charset charset = StandardCharsets.UTF_8;
            String testStr = new String(header, 0, headerLen, StandardCharsets.UTF_8);
            // 统计替换字符占比，避免字节边界截断导致误判
            int replaceCount = 0;
            for (int i = 0; i < testStr.length(); i++) {
                if (testStr.charAt(i) == '�') replaceCount++;
            }
            if (replaceCount > testStr.length() * 0.05) {
                String gbkTest = new String(header, 0, headerLen, Charset.forName("GBK"));
                if (gbkTest.indexOf('�') < replaceCount) {
                    charset = Charset.forName("GBK");
                    testStr = gbkTest;
                }
            }

            // 用选定的编码流式读取
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(s, charset))) {
                int chapterNum = 0;
                int totalWords = 0;
                StringBuilder chapterBuf = new StringBuilder();
                String line;
                Pattern chapterPattern = Pattern.compile(
                        "^\\s*(第[一二三四五六七八九十百千零〇\\d]+[章节篇回部集])|" +
                        "^\\s*(?i)(chapter|section|part|lesson|module|unit|episode)\\s+[\\dIVXL]+\\s*",
                        Pattern.MULTILINE);

                // header 内容作为第一段内容的基础
                String headerStr = new String(header, 0, headerLen, charset);
                if (headerStr.trim().isEmpty()) {
                    throw new RuntimeException("TXT 内容为空");
                }
                chapterBuf.append(headerStr);
                totalWords += headerStr.length();

                while ((line = reader.readLine()) != null) {
                    // 章节边界检测
                    if (chapterPattern.matcher(line).find() && chapterBuf.length() > 200) {
                        flushTxtChapter(bookId, ++chapterNum, chapterBuf);
                        chapterBuf = new StringBuilder();
                    }
                    chapterBuf.append(line).append("\n");

                    // 长章节每 50000 字切分
                    if (chapterBuf.length() >= 50000) {
                        flushTxtChapter(bookId, ++chapterNum, chapterBuf);
                        chapterBuf = new StringBuilder();
                    }

                    totalWords += line.length();
                    if (totalWords % 20000 == 0) {
                        int pct = (int) ((double) totalWords / fileSize * 33);
                        bookMapper.updateStatusProgress(bookId, 1, Math.min(pct, 32), "TXT 解析中 " + totalWords / 1000 + "k 字");
                    }
                }

                // 剩余内容
                if (!chapterBuf.isEmpty()) {
                    flushTxtChapter(bookId, ++chapterNum, chapterBuf);
                }

                if (chapterNum == 0) {
                    // 没有章节边界 → 用已读缓冲区按段落分组
                    String fullText = chapterBuf.toString().trim();
                    if (!fullText.isEmpty()) {
                        writeAsParagraphs(bookId, fullText, 100000);
                        totalWords = fullText.length();
                        chapterNum = chapterMapper.countByBookId(bookId);
                    }
                }

                bookMapper.updateMetadata(bookId, null, null, chapterNum, totalWords);
                log.info("TXT 解析完成: bookId={}, 字数={}, 章节数={}", bookId, totalWords, chapterNum);
                bookMapper.updateStatusProgress(bookId, 1, 33, "文本解析完成 (" + chapterNum + " 章)");
            }
        }
    }

    private void flushTxtChapter(Long bookId, int num, StringBuilder buf) {
        String content = buf.toString().trim();
        if (content.isEmpty()) return;
        insertChapter(bookId, num, "第" + num + "节", content);
    }

    // ------------------ Tika 解析（非 PDF/TXT 走 Tika） ------------------

    private void parseWithTika(Long bookId, String fileName, long fileSize) throws Exception {
        log.info("Tika 解析: bookId={}", bookId);
        bookMapper.updateStatusProgress(bookId, 1, 25, "Tika 解析中...");

        try (InputStream tikastream = minioClient.getObject(
                io.minio.GetObjectArgs.builder().bucket(bucketName).object(fileName).build())) {
            String text = extractWithTika(tikastream);

            if (text == null || text.trim().isEmpty()) {
                // 兜底：直接按 UTF-8 流式读取（防 OOM）
                try (InputStream s = minioClient.getObject(
                        io.minio.GetObjectArgs.builder().bucket(bucketName).object(fileName).build())) {
                    byte[] header = new byte[8192];
                    int hLen = s.read(header);
                    String headStr = new String(header, 0, Math.max(hLen, 0), StandardCharsets.UTF_8);
                    Charset cs = StandardCharsets.UTF_8;
                    if (headStr.contains("�")) {
                        String gbkHead = new String(header, 0, Math.max(hLen, 0), Charset.forName("GBK"));
                        if (gbkHead.indexOf('�') < headStr.indexOf('�')) cs = Charset.forName("GBK");
                    }
                    // 最多读 10MB 文本
                    ByteArrayOutputStream bos = new ByteArrayOutputStream(10 * 1024 * 1024);
                    byte[] buf = new byte[8192]; int n; long total = 0;
                    if (hLen > 0) { bos.write(header, 0, hLen); total += hLen; }
                    while ((n = s.read(buf)) > 0 && total < 10 * 1024 * 1024) {
                        bos.write(buf, 0, n); total += n;
                    }
                    text = bos.toString(cs);
                }
            }

            if (text == null || text.trim().isEmpty()) {
                throw new RuntimeException("无法提取文本内容");
            }

            bookMapper.updateStatusProgress(bookId, 1, 40, "文本提取完成 (" + text.length() + " 字)，正在拆分章节...");
            chapterMapper.deleteByBookId(bookId);

            List<Chapter> chapters = splitIntoChapters(bookId, text);
            if (chapters.isEmpty()) {
                writeAsParagraphs(bookId, text, 100000);
            } else {
                for (Chapter ch : chapters) {
                    chapterMapper.insert(ch);
                }
            }

            int totalChapters = chapterMapper.countByBookId(bookId);
            int totalWords = text.length();
            bookMapper.updateMetadata(bookId, null, null, totalChapters, totalWords);
            log.info("Tika 解析完成: bookId={}, 字数={}, 章节数={}", bookId, totalWords, totalChapters);
            bookMapper.updateStatusProgress(bookId, 1, 33, "文本解析完成 (" + totalChapters + " 章)");
        }
    }

    // ------------------ 通用辅助方法 ------------------

    private void insertChapter(Long bookId, int number, String title, String content) {
        Chapter ch = new Chapter();
        ch.setBookId(bookId);
        ch.setChapterNumber(number);
        ch.setTitle(title);
        ch.setContent(content);
        ch.setCreateTime(LocalDateTime.now());
        chapterMapper.insert(ch);
    }

    /**
     * 按段落分页写入章节
     */
    private void writeAsParagraphs(Long bookId, String text, int pageSize) {
        String[] paras = text.split("\n\\s*\n");
        if (paras.length <= 3) {
            for (int p = 0, num = 1; p < text.length(); num++) {
                int end = Math.min(p + pageSize, text.length());
                insertChapter(bookId, num, "第" + num + "页", text.substring(p, end));
                p = end;
            }
        } else {
            StringBuilder buf = new StringBuilder();
            int num = 0;
            for (String para : paras) {
                para = para.trim();
                if (para.isEmpty()) continue;
                if (buf.length() + para.length() > pageSize && buf.length() > 0) {
                    insertChapter(bookId, ++num, "第" + num + "页", buf.toString().trim());
                    buf = new StringBuilder();
                }
                if (buf.length() > 0) buf.append("\n\n");
                buf.append(para);
            }
            if (buf.length() > 0) {
                insertChapter(bookId, ++num, "第" + num + "页", buf.toString().trim());
            }
        }
    }

    /**
     * 将全本文本按章节标记拆分为 Chapter 列表
     */
    private List<Chapter> splitIntoChapters(Long bookId, String text) {
        Pattern pattern = Pattern.compile(
                "(?:^|\\n)\\s*" +
                "(第[一二三四五六七八九十百千零〇\\d]+[章节篇回部集][^\\n]{0,30}|" +
                "(?:Chapter|Section|Part|Lesson|Module|Unit|Episode)\\s+[\\dIVXL]+[^\\n]{0,30}|" +
                "\\d+\\.\\s{0,2}[\\u4e00-\\u9fa5][^\\n]{1,50}|" +
                "\\d{1,2}\\.\\d{1,2}\\s+[^\\n]{1,50})",
                Pattern.CASE_INSENSITIVE | Pattern.MULTILINE
        );

        List<Chapter> chapters = new ArrayList<>();
        Matcher matcher = pattern.matcher(text);
        int lastEnd = 0;
        int chapterNumber = 0;
        String lastTitle = null;
        boolean hasMatch = false;

        while (matcher.find()) {
            hasMatch = true;
            if (lastEnd > 0 || chapterNumber > 0) {
                String content = text.substring(lastEnd, matcher.start()).trim();
                if (!content.isEmpty()) {
                    Chapter ch = new Chapter();
                    ch.setBookId(bookId);
                    ch.setChapterNumber(++chapterNumber);
                    ch.setTitle(lastTitle != null ? lastTitle.trim() : "第" + chapterNumber + "章");
                    ch.setContent(content);
                    ch.setCreateTime(LocalDateTime.now());
                    chapters.add(ch);
                }
            } else if (matcher.start() > 0) {
                String preface = text.substring(0, matcher.start()).trim();
                if (!preface.isEmpty()) {
                    Chapter ch = new Chapter();
                    ch.setBookId(bookId);
                    ch.setChapterNumber(++chapterNumber);
                    ch.setTitle("前言");
                    ch.setContent(preface);
                    ch.setCreateTime(LocalDateTime.now());
                    chapters.add(ch);
                }
            }
            lastTitle = matcher.group().replaceAll("^\\s*", "");
            lastEnd = matcher.end();
        }

        if (hasMatch && lastEnd > 0 && lastEnd < text.length()) {
            String content = text.substring(lastEnd).trim();
            if (!content.isEmpty()) {
                Chapter ch = new Chapter();
                ch.setBookId(bookId);
                ch.setChapterNumber(chapterNumber + 1);
                ch.setTitle(lastTitle != null ? lastTitle.trim() + "（续）" : "第" + (chapterNumber + 1) + "章");
                ch.setContent(content);
                ch.setCreateTime(LocalDateTime.now());
                chapters.add(ch);
            }
        }

        return chapters;
    }

    // ------------------ Tika 提取 ------------------

    private String extractWithTika(InputStream inputStream) throws Exception {
        Metadata metadata = new Metadata();
        BodyContentHandler handler = new BodyContentHandler(10 * 1024 * 1024); // 限制 10MB
        AutoDetectParser parser = new AutoDetectParser();
        ParseContext context = new ParseContext();

        PDFParserConfig pdfConfig = new PDFParserConfig();
        pdfConfig.setExtractAnnotationText(true);
        pdfConfig.setExtractUniqueInlineImagesOnly(false);
        pdfConfig.setExtractInlineImages(false);
        pdfConfig.setAverageCharTolerance(1.0f);
        pdfConfig.setSpacingTolerance(1.0f);

        try {
            pdfConfig.setOcrStrategy(PDFParserConfig.OCR_STRATEGY.OCR_ONLY);
        } catch (Exception e) {
            log.info("Tika OCR 不可用");
        }

        context.set(PDFParserConfig.class, pdfConfig);
        context.set(org.apache.tika.parser.Parser.class, parser);
        parser.parse(inputStream, handler, metadata, context);

        return handler.toString();
    }

    // ======================== SHA256 工具 ========================

    private String computeSha256(InputStream is) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] buf = new byte[8192];
            int n;
            while ((n = is.read(buf)) != -1) {
                md.update(buf, 0, n);
            }
            return bytesToHex(md.digest());
        } catch (Exception e) {
            log.warn("计算SHA256失败", e);
            return null;
        }
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    // ======================== 保留兼容 MQ 路径（外部调用）===================

    public void parseBookText(Long bookId) {
        Book book = bookMapper.selectById(bookId);
        if (book == null) return;
        processBookFromQueue(book.getId(), book.getUserId());
    }
}
