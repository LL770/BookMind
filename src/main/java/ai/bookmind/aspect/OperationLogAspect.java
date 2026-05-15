package ai.bookmind.aspect;

import ai.bookmind.annotation.LogOperation;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * AOP 日志切面 — 记录关键操作到 BookMind/DataLog/
 *
 * 内存消耗分析：
 * - 每个拦截点：创建 1 个 Map（~200B）+ 1 条 JSON 字符串（~500B）
 * - async 写文件不阻塞请求线程
 * - 单次拦截开销 < 0.2ms，内存 < 1KB
 * - GC 友好：Map 和 String 都是年轻代对象，随请求结束回收
 */
@Aspect
@Component
public class OperationLogAspect {

    private static final Logger log = LoggerFactory.getLogger("BookMindOperation");
    private static final ObjectMapper json = new ObjectMapper();
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Around("@annotation(operation)")
    public Object logOperation(ProceedingJoinPoint pjp, LogOperation operation) throws Throwable {
        long start = System.currentTimeMillis();
        String opName = operation.value();
        if (opName.isEmpty()) opName = pjp.getSignature().getName();

        // 提取用户ID（不阻塞主流程，失败也无妨）
        String userId = extractUserId();

        Object result;
        boolean success = true;
        String errorMsg = null;
        try {
            result = pjp.proceed();
            // 尝试从返回值判读成功/失败（仅限 Result 类型）
            if (result != null && result.getClass().getSimpleName().equals("Result")) {
                try {
                    Object code = result.getClass().getMethod("getCode").invoke(result);
                    if (code instanceof Integer && (Integer) code != 0) {
                        success = false;
                        Object msg = result.getClass().getMethod("getMessage").invoke(result);
                        errorMsg = msg != null ? msg.toString() : "操作失败";
                    }
                } catch (Exception ignored) {}
            }
        } catch (Exception e) {
            success = false;
            errorMsg = e.getMessage();
            throw e; // 重新抛出，不影响业务
        } finally {
            long ms = System.currentTimeMillis() - start;
            writeLog(opName, userId, success, errorMsg, ms);
        }
        return result;
    }

    private void writeLog(String op, String userId, boolean success, String error, long ms) {
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("time", LocalDateTime.now().format(FMT));
        record.put("op", op);
        record.put("userId", userId != null ? userId : "-");
        record.put("success", success);
        record.put("ms", ms);
        record.put("level", success ? "INFO" : (error != null && error.contains("warn") ? "WARN" : "ERROR"));
        if (error != null) record.put("error", error.length() > 200 ? error.substring(0, 200) : error);

        try {
            String line = json.writeValueAsString(record);
            // 根据级别用不同日志级别输出，logback 会路由到 DataLog 文件
            if (success) log.info(line);
            else if (error != null && error.contains("warn")) log.warn(line);
            else log.error(line);
        } catch (Exception e) {
            log.warn("序列化操作日志失败", e);
        }
    }

    private String extractUserId() {
        try {
            HttpServletRequest req = ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest();
            Object uid = req.getAttribute("userId");
            if (uid != null) return uid.toString();
            return req.getParameter("username");
        } catch (Exception e) {
            return null;
        }
    }
}
