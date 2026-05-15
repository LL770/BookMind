package ai.bookmind.config;

import io.minio.MinioClient;
import lombok.Data;
import okhttp3.OkHttpClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * MinIO 配置类
 * 兼容新配置：spring.minio.*
 */
@Configuration
@Data
public class MinioConfig {

    @Value("${spring.minio.endpoint}")
    private String endpoint;

    @Value("${spring.minio.access-key}")
    private String accessKey;

    @Value("${spring.minio.secret-key}")
    private String secretKey;

    @Value("${spring.minio.bucket-name}")
    private String bucketName;

    /**
     * MinIO 客户端 Bean
     */
    @Bean
    public MinioClient minioClient() {
        OkHttpClient httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(300, TimeUnit.SECONDS)
                .writeTimeout(300, TimeUnit.SECONDS)
                .build();
        return MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .httpClient(httpClient)
                .build();
    }

    /**
     * 初始化存储桶
     */
    @Bean
    public InitBucket initBucket(MinioClient minioClient) {
        return new InitBucket(minioClient, bucketName);
    }

    @Data
    @lombok.RequiredArgsConstructor
    public static class InitBucket {
        private final MinioClient minioClient;
        private final String bucketName;

        @jakarta.annotation.PostConstruct
        public void init() {
            try {
                boolean exists = minioClient.bucketExists(io.minio.BucketExistsArgs.builder().bucket(bucketName).build());
                if (!exists) {
                    minioClient.makeBucket(io.minio.MakeBucketArgs.builder().bucket(bucketName).build());
                }
                // 设置公开读策略
                String policy = """
                    {
                      "Version": "2012-10-17",
                      "Statement": [
                        {
                          "Effect": "Allow",
                          "Principal": {"AWS": ["*"]},
                          "Action": ["s3:GetObject"],
                          "Resource": ["arn:aws:s3:::%s/*"]
                        }
                      ]
                    }
                    """.formatted(bucketName);
                minioClient.setBucketPolicy(
                    io.minio.SetBucketPolicyArgs.builder().bucket(bucketName).config(policy).build()
                );
                System.out.println("MinIO 存储桶已初始化：" + bucketName);
            } catch (Exception e) {
                System.err.println("MinIO 存储桶初始化失败：" + e.getMessage());
            }
        }
    }
}
