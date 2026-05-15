package ai.bookmind.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.QdrantGrpcClient;
import io.qdrant.client.grpc.JsonWithInt;
import io.qdrant.client.grpc.Points;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.client.WebClient;

import jakarta.annotation.PostConstruct;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 混合检索服务 — 直接操作 Qdrant gRPC + HTTP API
 * 每本书独立 collection，命名: book_{bookId}
 * 每个 collection 含 dense(1024) + sparse 命名向量
 */
@Service
@Slf4j
public class HybridVectorService {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private QdrantClient qdrantClient;
    private WebClient siliconFlowClient;

    private final String host;
    private final int port;
    private final int httpPort;
    private final int vectorSize;
    private final String embedKey;
    private final String embedModel;
    private static final String DENSE = "dense";
    private static final String SPARSE = "sparse";

    public HybridVectorService(@Value("${spring.ai.vectorstore.qdrant.host:127.0.0.1}") String host,
                               @Value("${spring.ai.vectorstore.qdrant.port:6334}") int port,
                               @Value("${bookmind.qdrant.http-port:6333}") int httpPort,
                               @Value("${spring.ai.vectorstore.qdrant.vector-size:1024}") int vectorSize,
                               @Value("${siliconflow.api.embed-key:}") String embedKey,
                               @Value("${siliconflow.api.embed-model:BAAI/bge-m3}") String embedModel) {
        this.host = host;
        this.port = port;
        this.httpPort = httpPort;
        this.vectorSize = vectorSize;
        this.embedKey = embedKey;
        this.embedModel = embedModel;
    }

    @PostConstruct
    public void init() {
        this.qdrantClient = new QdrantClient(QdrantGrpcClient.newBuilder(host, port, false).build());
        this.siliconFlowClient = WebClient.builder()
                .baseUrl("https://api.siliconflow.cn/v1")
                .defaultHeader("Authorization", "Bearer " + embedKey)
                .defaultHeader("Content-Type", "application/json")
                .build();
        createCollection(VECTOR_COLLECTION);
        ensurePayloadIndexes(VECTOR_COLLECTION);
        applyQuantization(VECTOR_COLLECTION);
    }

    /** 单一 collection 名（payload 过滤实现用户/书籍隔离） */
    public static final String VECTOR_COLLECTION = "bookmind_vectors";

    public static String bookCollection(Long bookId) {
        return VECTOR_COLLECTION;
    }

    /** 创建 collection — 写入优化模式：禁用索引 + on_disk 向量存储 */
    public void createCollection(String collectionName) {
        try {
            String baseUrl = "http://" + host + ":" + httpPort;
            RestTemplate rest = new RestTemplate();
            try {
                rest.getForEntity(baseUrl + "/collections/" + collectionName, String.class);
                log.info("Qdrant collection 已存在: {}", collectionName);
                return;
            } catch (Exception e) { /* 不存在，创建 */ }

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            // 写入优化：禁用 HNSW 索引，向量存磁盘
            Map<String, Object> denseConfig = new LinkedHashMap<>();
            denseConfig.put("size", vectorSize);
            denseConfig.put("distance", "Cosine");
            denseConfig.put("on_disk", true);

            Map<String, Object> hnswConfig = new LinkedHashMap<>();
            hnswConfig.put("m", 0);
            hnswConfig.put("ef_construct", 128);
            hnswConfig.put("full_scan_threshold", 10000);

            Map<String, Object> quantizationConfig = new LinkedHashMap<>();
            quantizationConfig.put("scalar", Map.of("type", "int8", "quantile", 0.99));

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("name", collectionName);
            body.put("vectors", Map.of("dense", denseConfig));
            body.put("sparse_vectors", Map.of("sparse", Map.of()));
            body.put("hnsw_config", hnswConfig);
            body.put("optimizers_config", Map.of("indexing_threshold", 0));
            body.put("quantization_config", quantizationConfig);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
            rest.put(baseUrl + "/collections/" + collectionName, request);
            log.info("Qdrant collection 创建(写入优化): {}, dim={}, on_disk=true", collectionName, vectorSize);
        } catch (Exception e) {
            log.error("Qdrant collection 创建失败: {}", collectionName, e);
            throw new RuntimeException("Qdrant collection 创建失败: " + e.getMessage());
        }
    }

    /** 为 userId 和 bookId 建立 payload 索引（加速过滤） */
    public void ensurePayloadIndexes(String collectionName) {
        try {
            String baseUrl = "http://" + host + ":" + httpPort;
            RestTemplate rest = new RestTemplate();
            for (String field : new String[]{"userId", "bookId"}) {
                Map<String, Object> idxBody = new LinkedHashMap<>();
                idxBody.put("field_name", field);
                idxBody.put("field_type", "keyword");
                HttpEntity<Map<String, Object>> req = new HttpEntity<>(idxBody, new HttpHeaders());
                try {
                    rest.put(baseUrl + "/collections/" + collectionName + "/index", req);
                    log.info("Payload 索引已创建: collection={}, field={}", collectionName, field);
                } catch (Exception e) {
                    log.info("Payload 索引已存在或创建失败: collection={}, field={}", collectionName, field);
                }
            }
        } catch (Exception e) {
            log.warn("Payload 索引创建失败(不影响使用): {}", e.getMessage());
        }
    }

    /** 对标量量化（SQ int8），已有 collection 也生效 */
    public void applyQuantization(String collectionName) {
        try {
            String baseUrl = "http://" + host + ":" + httpPort;
            RestTemplate rest = new RestTemplate();
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            Map<String, Object> sq = new LinkedHashMap<>();
            sq.put("scalar", Map.of("type", "int8", "quantile", 0.99));
            Map<String, Object> body = Map.of("quantization_config", sq);
            HttpEntity<Map<String, Object>> req = new HttpEntity<>(body, headers);
            try {
                rest.exchange(baseUrl + "/collections/" + collectionName, org.springframework.http.HttpMethod.PATCH, req, String.class);
                log.info("SQ 量化已启用: {}", collectionName);
            } catch (Exception e) {
                String msg = e.getMessage() != null ? e.getMessage() : "";
                if (msg.contains("404") || msg.contains("PATCH")) {
                    // 旧版 Qdrant 不支持 PATCH，通过 recreate 在创建时启用
                    log.info("SQ 量化: Qdrant 版本不支持 PATCH，已在创建集合时启用");
                } else {
                    log.warn("SQ 量化 PATCH 失败: {}", msg);
                }
            }
        } catch (Exception e) {
            log.warn("applyQuantization 异常: {}", e.getMessage());
        }
    }

    /** 删除 collection（HTTP API） */
    public void deleteCollection(String collectionName) {
        try {
            String baseUrl = "http://" + host + ":" + httpPort;
            RestTemplate rest = new RestTemplate();
            rest.delete(baseUrl + "/collections/" + collectionName);
            log.info("Qdrant collection 删除成功: {}", collectionName);
        } catch (Exception e) {
            log.error("Qdrant collection 删除失败: {}", collectionName, e);
        }
    }

    /** 获取所有 book_* 开头的 collection 名 */
    public List<String> listBookCollections() {
        try {
            String baseUrl = "http://" + host + ":" + httpPort;
            RestTemplate rest = new RestTemplate();
            @SuppressWarnings("unchecked")
            Map<String, Object> resp = rest.getForObject(baseUrl + "/collections", Map.class);
            if (resp == null) return List.of();

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> collections = (List<Map<String, Object>>) resp.get("collections");
            if (collections == null) return List.of();

            return collections.stream()
                    .map(m -> (String) m.get("name"))
                    .filter(n -> n != null && n.startsWith("book_"))
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("列出 Qdrant collections 失败: {}", e.getMessage());
            return List.of();
        }
    }

    // ---------- Embedding 批处理（限流 + 内存安全） ----------
    private static final int EMBED_BATCH_SIZE = 8;
    private static final int TOKEN_PER_CHAR = 2;
    private long lastRpmCheck = 0;
    private int rpmCount = 0;

    private List<float[]> embedBatch(List<String> texts) {
        if (texts.isEmpty()) return List.of();
        List<float[]> allResults = new ArrayList<>();
        for (int i = 0; i < texts.size(); i += EMBED_BATCH_SIZE) {
            int end = Math.min(i + EMBED_BATCH_SIZE, texts.size());
            List<String> batch = texts.subList(i, end);
            int batchTokens = batch.stream().mapToInt(t -> t.length() * TOKEN_PER_CHAR).sum();
            if (batchTokens > 8000) {
                for (String t : batch) allResults.add(embedSingle(t));
                continue;
            }
            rateLimitWait(batchTokens);
            allResults.addAll(doEmbedBatch(batch));
        }
        return allResults;
    }

    private List<float[]> doEmbedBatch(List<String> texts) {
        try {
            ObjectNode body = objectMapper.createObjectNode();
            body.put("model", embedModel);
            ArrayNode input = body.putArray("input");
            texts.forEach(input::add);
            String resp = siliconFlowClient.post().uri("/embeddings")
                    .bodyValue(body.toString()).retrieve().bodyToMono(String.class).block();
            if (resp == null) return texts.stream().map(t -> new float[vectorSize]).toList();
            var root = objectMapper.readTree(resp);
            var data = root.path("data");
            List<float[]> results = new ArrayList<>();
            for (var item : data) {
                var arr = item.path("embedding");
                float[] vec = new float[arr.size()];
                for (int i = 0; i < arr.size(); i++) vec[i] = (float) arr.get(i).asDouble();
                results.add(vec);
            }
            return results;
        } catch (Exception e) {
            log.error("Batch embedding 失败: {}", e.getMessage());
            if (e.getMessage() != null && e.getMessage().contains("429")) {
                try { Thread.sleep(2000); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
                return doEmbedBatch(texts);
            }
            return texts.stream().map(t -> new float[vectorSize]).toList();
        }
    }

    private float[] embedSingle(String text) {
        var results = doEmbedBatch(List.of(text));
        return results.isEmpty() ? new float[vectorSize] : results.get(0);
    }

    private synchronized void rateLimitWait(int tokens) {
        long now = System.currentTimeMillis();
        if (now - lastRpmCheck > 60000) { rpmCount = 0; lastRpmCheck = now; }
        rpmCount++;
        if (rpmCount > 1990) {
            long wait = 60000 - (now - lastRpmCheck);
            if (wait > 0) { try { Thread.sleep(wait); } catch (Exception e) {} }
            rpmCount = 0; lastRpmCheck = System.currentTimeMillis();
        }
    }

    private float[] embed(String text) {
        return embedBatch(List.of(text)).get(0);
    }

    // ---------- 索引控制 ----------
    public void rebuildIndex(String collectionName) {
        try {
            String baseUrl = "http://" + host + ":" + httpPort;
            RestTemplate rest = new RestTemplate();
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            Map<String, Object> hnsw = new LinkedHashMap<>();
            hnsw.put("m", 16); hnsw.put("ef_construct", 128); hnsw.put("full_scan_threshold", 10000);
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("hnsw_config", hnsw);
            body.put("optimizers_config", Map.of("indexing_threshold", 20000));
            try {
                rest.exchange(baseUrl + "/collections/" + collectionName,
                        org.springframework.http.HttpMethod.PATCH, new HttpEntity<>(body, headers), String.class);
                log.info("Qdrant 索引重建完成: {}", collectionName);
            } catch (Exception e) {
                log.warn("Qdrant 索引重建PATCH失败(可忽略): {}", e.getMessage());
            }
        } catch (Exception e) {
            log.warn("rebuildIndex 异常: {}", e.getMessage());
        }
    }

    /** 批量写入 — 无 sparse 向量，纯 dense */
    public void addDocuments(String collectionName, List<Document> documents) {
        if (documents == null || documents.isEmpty()) return;
        List<String> contents = documents.stream().map(Document::getContent).collect(Collectors.toList());
        List<float[]> embeddings = embedBatch(contents);
        List<Points.PointStruct> points = new ArrayList<>();
        for (int i = 0; i < documents.size(); i++) {
            Document doc = documents.get(i);
            float[] denseV = embeddings.get(i);
            Map<String, JsonWithInt.Value> payload = new HashMap<>();
            payload.put("content", JsonWithInt.Value.newBuilder().setStringValue(doc.getContent()).build());
            for (var e : doc.getMetadata().entrySet())
                if (e.getValue() != null)
                    payload.put(e.getKey(), JsonWithInt.Value.newBuilder().setStringValue(e.getValue().toString()).build());
            Points.Vector denseVector = Points.Vector.newBuilder().addAllData(toFloatList(denseV)).build();
            Points.Vector sparseVector = toSparseVector(extractSparse(doc.getContent()));
            Points.Vectors vectors = Points.Vectors.newBuilder()
                    .setVectors(Points.NamedVectors.newBuilder()
                            .putVectors(DENSE, denseVector)
                            .putVectors(SPARSE, sparseVector)
                            .build()).build();
            points.add(Points.PointStruct.newBuilder()
                    .setId(Points.PointId.newBuilder().setUuid(UUID.randomUUID().toString()).build())
                    .setVectors(vectors).putAllPayload(payload).build());
        }
        for (int j = 0; j < points.size(); j += 100) {
            int end = Math.min(j + 100, points.size());
            try {
                qdrantClient.upsertAsync(Points.UpsertPoints.newBuilder()
                        .setCollectionName(collectionName).addAllPoints(points.subList(j, end)).build())
                        .get(120, TimeUnit.SECONDS);
            } catch (Exception e) {
                log.error("Qdrant 写入失败 batch {}-{}: {}", j, end, e.getMessage());
                throw new RuntimeException("Qdrant 写入失败", e);
            }
        }
        log.info("写入 {} 个向量到 {}", points.size(), collectionName);
    }

    // ---------- 清理旧 book_* collection ----------
    public void cleanupOldCollections() {
        try {
            String baseUrl = "http://" + host + ":" + httpPort;
            RestTemplate rest = new RestTemplate();
            @SuppressWarnings("unchecked")
            Map<String, Object> resp = rest.getForObject(baseUrl + "/collections", Map.class);
            if (resp == null) return;
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> cols = (List<Map<String, Object>>) resp.get("collections");
            if (cols == null) return;
            for (var col : cols) {
                String name = (String) col.get("name");
                if (name != null && name.startsWith("book_") && !name.equals(VECTOR_COLLECTION)) {
                    rest.delete(baseUrl + "/collections/" + name);
                    log.info("已删除旧 collection: {}", name);
                }
            }
        } catch (Exception e) {
            log.warn("清理旧 collection 失败: {}", e.getMessage());
        }
    }

    /** 混合检索 */
    public List<Document> hybridSearch(String collectionName, String query, int topK, String filterExpression) {
        try {
            float[] queryDense = embed(query);
            Map<Integer, Float> querySparse = extractSparse(query);
            Points.Filter filter = parseFilter(filterExpression);

            // 双通道：dense + sparse
            List<Points.ScoredPoint> denseResults = search(collectionName, DENSE, toFloatList(queryDense), topK * 2, filter);
            List<Points.ScoredPoint> sparseResults = search(collectionName, SPARSE, sparseToList(querySparse), topK, filter);

            // RRF 融合 (k=60)
            Map<String, Double> fused = new LinkedHashMap<>();
            Map<String, Points.ScoredPoint> ptMap = new LinkedHashMap<>();
            int k = 60;
            for (int i = 0; i < denseResults.size(); i++) {
                String id = pointIdStr(denseResults.get(i));
                fused.merge(id, 1.0 / (k + i), Double::sum);
                ptMap.putIfAbsent(id, denseResults.get(i));
            }
            for (int i = 0; i < sparseResults.size(); i++) {
                String id = pointIdStr(sparseResults.get(i));
                fused.merge(id, 1.0 / (k + i), Double::sum);
                ptMap.putIfAbsent(id, sparseResults.get(i));
            }
            // 按融合分排序
            List<Map.Entry<String, Double>> sorted = new ArrayList<>(fused.entrySet());
            sorted.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));

            List<Document> results = new ArrayList<>();
            for (var entry : sorted) {
                Document doc = toDocument(ptMap.get(entry.getKey()));
                if (doc != null) {
                    doc.getMetadata().put("score", entry.getValue());
                    results.add(doc);
                    if (results.size() >= topK) break;
                }
            }
            return results;

        } catch (Exception e) {
            log.error("混合检索失败 collection={}", collectionName, e);
            return List.of();
        }
    }

    /** 跨所有 book collection 全局检索 */
    public List<Document> globalSearch(String query, int topK, String filterExpression) {
        List<String> collections = listBookCollections();
        if (collections.isEmpty()) return List.of();

        // 每本书取 topK，合并后重排序
        List<Document> all = new ArrayList<>();
        for (String coll : collections) {
            try {
                all.addAll(hybridSearch(coll, query, Math.max(5, topK / collections.size()), filterExpression));
            } catch (Exception e) {
                log.warn("检索 collection {} 失败: {}", coll, e.getMessage());
            }
        }
        // 按分数降序取 topK
        all.sort((a, b) -> Double.compare(
                (double) b.getMetadata().getOrDefault("score", 0.0),
                (double) a.getMetadata().getOrDefault("score", 0.0)));
        return all.subList(0, Math.min(topK, all.size()));
    }

    /** 单向量搜索 */
    private List<Points.ScoredPoint> search(String collectionName, String vectorName, List<Float> vector, int limit, Points.Filter filter) {
        try {
            Points.SearchPoints.Builder builder = Points.SearchPoints.newBuilder()
                    .setCollectionName(collectionName)
                    .setLimit(limit)
                    .setTimeout(30000)
                    .setVectorName(vectorName)
                    .setWithPayload(Points.WithPayloadSelector.newBuilder().setEnable(true).build());

            if (SPARSE.equals(vectorName)) {
                List<Integer> indices = new ArrayList<>();
                List<Float> values = new ArrayList<>();
                for (int i = 0; i < vector.size(); i++) {
                    float v = vector.get(i);
                    if (v != 0) { indices.add(i); values.add(v); }
                }
                builder.addAllVector(values);
                if (!indices.isEmpty()) {
                    builder.setSparseIndices(Points.SparseIndices.newBuilder().addAllData(indices).build());
                }
            } else {
                builder.addAllVector(vector);
            }

            if (filter != null) builder.setFilter(filter);
            return qdrantClient.searchAsync(builder.build()).get(30, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("{} 搜索失败: {}", vectorName, e.getMessage());
            return List.of();
        }
    }

    // ---------- 稀疏向量：字符双哈希（固定词表，确保 query 和 doc 索引一致） ----------
    private static final int SPARSE_DIM = 50000;
    private static final int SPARSE_TOPK = 30;

    /** 提取稀疏向量 (term_hash → weight)，用于 Qdrant sparse search */
    private Map<Integer, Float> extractSparse(String text) {
        Map<Integer, Float> sparse = new HashMap<>();
        if (text == null || text.isEmpty()) return sparse;
        // 双字词哈希
        String cleaned = text.replaceAll("[\\s\\p{P}]", "");
        Map<Integer, Integer> freq = new HashMap<>();
        for (int i = 0; i < cleaned.length() - 1; i++) {
            String bigram = cleaned.substring(i, i + 2);
            int hash = (bigram.hashCode() & Integer.MAX_VALUE) % SPARSE_DIM;
            freq.merge(hash, 1, Integer::sum);
        }
        // BM25 式 TF 加权 + 归一化
        double norm = 0;
        for (int f : freq.values()) { double w = Math.sqrt(1 + Math.log(1 + f)); norm += w * w; }
        norm = Math.sqrt(norm);
        if (norm == 0) return sparse;
        for (var e : freq.entrySet()) {
            float w = (float) (Math.sqrt(1 + Math.log(1 + e.getValue())) / norm);
            if (w > 0.01f) sparse.put(e.getKey(), w);
        }
        return sparse;
    }

    /** 稀疏向量 → Qdrant PointStruct 的 sparse vector */
    private Points.Vector toSparseVector(Map<Integer, Float> sparse) {
        List<Integer> indices = new ArrayList<>();
        List<Float> values = new ArrayList<>();
        sparse.entrySet().stream()
                .sorted((a, b) -> Float.compare(b.getValue(), a.getValue()))
                .limit(SPARSE_TOPK)
                .forEach(e -> { indices.add(e.getKey()); values.add(e.getValue()); });
        return Points.Vector.newBuilder()
                .addAllData(values)
                .setIndices(Points.SparseIndices.newBuilder().addAllData(indices).build())
                .build();
    }

    /** sparse Map → List<Float>（与 search 方法的 sparse 通道兼容） */
    private List<Float> sparseToList(Map<Integer, Float> sparse) {
        List<Float> vec = new ArrayList<>();
        for (int i = 0; i < SPARSE_DIM; i++) vec.add(sparse.getOrDefault(i, 0f));
        return vec;
    }

    private List<Float> toFloatList(float[] arr) {
        List<Float> list = new ArrayList<>(arr.length);
        for (float v : arr) list.add(v);
        return list;
    }

    private String pointIdStr(Points.ScoredPoint p) {
        Points.PointId id = p.getId();
        if (id.getPointIdOptionsCase() == Points.PointId.PointIdOptionsCase.UUID) return id.getUuid();
        return String.valueOf(id.getNum());
    }

    private Document toDocument(Points.ScoredPoint point) {
        try {
            Map<String, Object> meta = new HashMap<>();
            String content = "";
            for (var entry : point.getPayloadMap().entrySet()) {
                String val = entry.getValue().getStringValue();
                if ("content".equals(entry.getKey())) content = val;
                else if (!val.isEmpty()) meta.put(entry.getKey(), val);
            }
            if (content.isEmpty()) return null;
            Document doc = new Document(content, meta);
            doc.getMetadata().put("score", (double) point.getScore());
            return doc;
        } catch (Exception e) {
            return null;
        }
    }

    private Points.Filter parseFilter(String expr) {
        if (expr == null || expr.isBlank()) return null;
        Points.Filter.Builder fb = Points.Filter.newBuilder();
        Pattern p = Pattern.compile("(\\w+)\\s*==\\s*'([^']+)'");
        for (String part : expr.split("&&")) {
            var m = p.matcher(part.trim());
            if (m.find()) {
                fb.addMust(Points.Condition.newBuilder()
                        .setField(Points.FieldCondition.newBuilder()
                                .setKey(m.group(1))
                                .setMatch(Points.Match.newBuilder()
                                        .setKeyword(m.group(2))
                                        .build())
                                .build())
                        .build());
            }
        }
        return fb.build();
    }

    public void deleteByFilter(String collectionName, String filterExpression) {
        try {
            qdrantClient.deleteAsync(Points.DeletePoints.newBuilder()
                    .setCollectionName(collectionName)
                    .setPoints(Points.PointsSelector.newBuilder()
                            .setFilter(parseFilter(filterExpression))
                            .build())
                    .build()).get(30, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.error("Qdrant 删除失败 collection={}", collectionName, e);
        }
    }
}
