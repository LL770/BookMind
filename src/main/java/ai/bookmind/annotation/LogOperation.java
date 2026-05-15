package ai.bookmind.annotation;

import java.lang.annotation.*;

/**
 * 关键操作日志注解 — AOP 拦截记录成功/失败
 * value = 操作名称（登录/注册/上传/对话/...）
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface LogOperation {
    String value() default "";
    boolean logParams() default false;
}
