package ai.bookmind.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * 阅读进度服务 — 只读每本书的阅读进度百分比
 *
 * Redis Key: user:reading:progress:{userId}:{bookId} → Integer (0~100)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReadingStatsService {

    private final RedisTemplate<String, Object> redisTemplate;

    private static final String READ_PROGRESS_KEY_PREFIX = "user:reading:progress:";

    /**
     * 更新阅读进度
     */
    public void updateProgress(Long userId, Long bookId, Integer progressPercent) {
        String key = READ_PROGRESS_KEY_PREFIX + userId + ":" + bookId;
        redisTemplate.opsForValue().set(key, progressPercent, 7, TimeUnit.DAYS);
    }

    /**
     * 获取阅读进度
     */
    public Integer getProgress(Long userId, Long bookId) {
        String key = READ_PROGRESS_KEY_PREFIX + userId + ":" + bookId;
        Object obj = redisTemplate.opsForValue().get(key);
        return obj instanceof Integer ? (Integer) obj : 0;
    }

}
