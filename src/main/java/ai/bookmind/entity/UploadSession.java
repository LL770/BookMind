package ai.bookmind.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class UploadSession {
    private Long id;
    private String uploadId;
    private Long userId;
    private String fileName;
    private Long fileSize;
    private Integer chunkSize;
    private Integer totalChunks;
    private Integer receivedChunks;
    private Integer status;
    private Long mergedBookId;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
