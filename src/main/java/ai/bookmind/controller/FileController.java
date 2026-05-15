package ai.bookmind.controller;

import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.io.InputStream;

@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
@Slf4j
public class FileController {

    private final MinioClient minioClient;

    @GetMapping("/{bucket}/**")
    public void serveFile(@PathVariable String bucket, HttpServletRequest request, HttpServletResponse response) {
        String path = (String) request.getAttribute(org.springframework.web.util.UrlPathHelper.PATH_ATTRIBUTE);
        if (path == null) {
            path = request.getRequestURI();
            String prefix = "/api/files/" + bucket + "/";
            if (path.startsWith(prefix)) {
                path = path.substring(prefix.length());
            }
        }
        try (InputStream is = minioClient.getObject(
                GetObjectArgs.builder().bucket(bucket).object(path).build())) {
            response.setContentType(detectContentType(path));
            response.setHeader("Cache-Control", "public, max-age=31536000, immutable");
            is.transferTo(response.getOutputStream());
        } catch (Exception e) {
            log.warn("文件读取失败: bucket={}, path={}", bucket, path, e);
            response.setStatus(404);
        }
    }

    private String detectContentType(String name) {
        String lower = name.toLowerCase();
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".gif")) return "image/gif";
        if (lower.endsWith(".webp")) return "image/webp";
        if (lower.endsWith(".svg")) return "image/svg+xml";
        if (lower.endsWith(".pdf")) return "application/pdf";
        return "application/octet-stream";
    }
}
