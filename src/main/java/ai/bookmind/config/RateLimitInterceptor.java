package ai.bookmind.config;

import ai.bookmind.common.Result;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;

/**
 * 限流拦截器
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RateLimitInterceptor implements HandlerInterceptor {

    private final RedisTemplate<String, Object> redisTemplate;

    @Value("${bookmind.ratelimit.ai-chat}")
    private int aiChatLimit;

    @Value("${bookmind.ratelimit.upload}")
    private int uploadLimit;

    @Value("${bookmind.ratelimit.search}")
    private int searchLimit;

    /**
     * 限流配置
     */
    private static final String AI_CHAT_KEY = "bookmind:ratelimit:ai-chat:";
    private static final String UPLOAD_KEY = "bookmind:ratelimit:upload:";
    private static final String SEARCH_KEY = "bookmind:ratelimit:search:";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws IOException {
        String uri = request.getRequestURI();
        String userId = getUserIdFromRequest(request);

        if (userId == null) {
            return true; // 未登录接口不限制
        }

        // 根据接口类型应用不同限流
        if (uri.contains("/chat")) {
            return checkLimit(AI_CHAT_KEY + userId, aiChatLimit, response);
        } else if (uri.contains("/upload")) {
            return checkLimit(UPLOAD_KEY + userId, uploadLimit, response);
        } else if (uri.contains("/search")) {
            return checkLimit(SEARCH_KEY + userId, searchLimit, response);
        }

        return true;
    }

    /**
     * 检查限流
     */
    private boolean checkLimit(String key, int limit, HttpServletResponse response) throws IOException {
        // 获取当前计数
        Object countObj = redisTemplate.opsForValue().get(key);
        int count = countObj instanceof Long ? ((Long) countObj).intValue() : 0;

        if (count >= limit) {
            response.setStatus(429);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write(String.format(
                    "{\"code\":429,\"message\":\"请求过于频繁，请稍后再试\",\"data\":null}", limit));
            log.warn("限流触发: key={}, count={}, limit={}", key, count, limit);
            return false;
        }

        // 递增计数
        redisTemplate.opsForValue().increment(key);

        // 设置过期时间（1分钟）
        if (count == 0) {
            redisTemplate.expire(key, 60, java.util.concurrent.TimeUnit.SECONDS);
        }

        return true;
    }

    /**
     * 从请求中获取用户ID
     */
    private String getUserIdFromRequest(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId != null) return userId.toString();

        Object sessionId = request.getSession(false) != null ? request.getSession(false).getAttribute("userId") : null;
        if (sessionId instanceof Long) {
            return sessionId.toString();
        }

        return null;
    }
}
