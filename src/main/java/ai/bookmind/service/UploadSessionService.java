package ai.bookmind.service;

import ai.bookmind.entity.UploadSession;
import ai.bookmind.mapper.UploadSessionMapper;
import io.minio.ComposeObjectArgs;
import io.minio.ComposeSource;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.StatObjectArgs;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class UploadSessionService {

    private final UploadSessionMapper uploadSessionMapper;
    private final MinioClient minioClient;

    @Value("${spring.minio.bucket-name}")
    private String bucketName;

    private static final int DEFAULT_CHUNK_SIZE = 5 * 1024 * 1024; // 5MB

    /**
     * 初始化上传会话
     */
    @Transactional
    public UploadSession initSession(Long userId, String fileName, Long fileSize) {
        UploadSession session = new UploadSession();
        session.setUploadId(UUID.randomUUID().toString().replace("-", ""));
        session.setUserId(userId);
        session.setFileName(fileName);
        session.setFileSize(fileSize);
        session.setChunkSize(DEFAULT_CHUNK_SIZE);
        session.setTotalChunks((int) Math.ceil((double) fileSize / DEFAULT_CHUNK_SIZE));
        session.setReceivedChunks(0);
        session.setStatus(0);
        uploadSessionMapper.insert(session);
        log.info("上传会话初始化: uploadId={}, fileName={}, totalChunks={}",
                session.getUploadId(), fileName, session.getTotalChunks());
        return session;
    }

    /**
     * 上传分片
     */
    public void uploadChunk(String uploadId, int chunkIndex, MultipartFile file) throws Exception {
        String chunkPath = "temp/" + uploadId + "/chunk_" + chunkIndex;
        try (InputStream in = file.getInputStream()) {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucketName)
                            .object(chunkPath)
                            .stream(in, file.getSize(), -1)
                            .build()
            );
        }
        // 更新接收计数
        UploadSession session = uploadSessionMapper.selectByUploadId(uploadId);
        if (session != null) {
            uploadSessionMapper.updateReceivedChunks(uploadId, chunkIndex + 1);
        }
        log.info("分片上传成功: uploadId={}, chunk={}", uploadId, chunkIndex);
    }

    /**
     * 检查分片是否已上传（断点续传）
     */
    public boolean isChunkUploaded(String uploadId, int chunkIndex) {
        String chunkPath = "temp/" + uploadId + "/chunk_" + chunkIndex;
        try {
            minioClient.statObject(
                    io.minio.StatObjectArgs.builder()
                            .bucket(bucketName)
                            .object(chunkPath)
                            .build()
            );
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 合并分片 → MinIO 服务端直接合并（ComposeObject），零数据传输，清理临时分片
     */
    @Transactional
    public String mergeChunks(String uploadId) throws Exception {
        UploadSession session = uploadSessionMapper.selectByUploadIdForUpdate(uploadId);
        if (session == null) {
            throw new IllegalArgumentException("上传会话不存在: " + uploadId);
        }
        session.setStatus(1);
        uploadSessionMapper.updateReceivedChunks(uploadId, session.getReceivedChunks());

        String finalPath = "uploads/" + session.getUserId() + "_" + uploadId + "_" + session.getFileName();

        // MinIO 服务端合并所有分片
        List<ComposeSource> sources = new ArrayList<>();
        for (int i = 0; i < session.getTotalChunks(); i++) {
            String chunkPath = "temp/" + uploadId + "/chunk_" + i;
            sources.add(ComposeSource.builder()
                    .bucket(bucketName)
                    .object(chunkPath)
                    .build());
        }
        minioClient.composeObject(
                ComposeObjectArgs.builder()
                        .bucket(bucketName)
                        .object(finalPath)
                        .sources(sources)
                        .build()
        );

        // 删除临时分片
        cleanupChunks(uploadId, session.getTotalChunks());

        String fileUrl = "http://localhost:9000/" + bucketName + "/" + finalPath;
        log.info("分片合并完成(ComposeObject): uploadId={}, finalPath={}", uploadId, finalPath);
        return fileUrl;
    }

    /**
     * 处理完成后删除临时分片
     */
    private void cleanupChunks(String uploadId, int totalChunks) {
        for (int i = 0; i < totalChunks; i++) {
            try {
                minioClient.removeObject(
                        RemoveObjectArgs.builder()
                                .bucket(bucketName)
                                .object("temp/" + uploadId + "/chunk_" + i)
                                .build()
                );
            } catch (Exception e) {
                log.warn("清理分片失败: uploadId={}, chunk={}", uploadId, i);
            }
        }
    }

    /**
     * 获取会话状态
     */
    public UploadSession getSession(String uploadId) {
        return uploadSessionMapper.selectByUploadId(uploadId);
    }
}
