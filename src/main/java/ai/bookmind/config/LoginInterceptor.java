package ai.bookmind.config;

import ai.bookmind.util.JwtTokenProvider;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 登录拦截器
 */
@Component
@RequiredArgsConstructor
public class LoginInterceptor implements HandlerInterceptor {

    private final JwtTokenProvider jwtTokenProvider;

    /**
     * 需要排除的接口（无需登录）
     */
    private static final String[] WHITE_LIST = {
        "/api/auth/register",
        "/api/auth/login",
        "/api/auth/check-username",
        "/api/public/",
        "/actuator"
    };

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String uri = request.getRequestURI();

        // 1. 检查是否在白名单
        for (String pattern : WHITE_LIST) {
            if (uri.startsWith(pattern)) {
                return true;
            }
        }

        // 2. 从Session获取用户信息
        HttpSession session = request.getSession(false);
        if (session != null) {
            Long userId = (Long) session.getAttribute("userId");
            if (userId != null) {
                // 将用户ID放入request attribute，供后续使用
                request.setAttribute("userId", userId);
                request.setAttribute("username", session.getAttribute("username"));
                return true;
            }
        }

        // 3. 从JWT令牌获取用户信息（支持 Header 和 URL 查询参数两种方式，后者用于 SSE）
        String token = request.getHeader("Authorization");
        if (token == null || !token.startsWith("Bearer ")) {
            token = request.getParameter("token");
        } else {
            token = token.substring(7);
        }
        if (token != null && jwtTokenProvider.validateToken(token)) {
            Long userId = jwtTokenProvider.getUserIdFromToken(token);
            request.setAttribute("userId", userId);
            request.setAttribute("username", jwtTokenProvider.getUsernameFromToken(token));
            return true;
        }

        // 4. 未登录
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":401,\"message\":\"未登录或登录已过期\",\"data\":null}");
        return false;
    }
}
