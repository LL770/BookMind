package ai.bookmind.config;

import ai.bookmind.util.JwtTokenProvider;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;

/**
 * JWT 认证过滤器
 * 从请求头中解析 JWT 令牌并设置安全上下文
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {
        
        try {
            // 1. 从请求头获取 JWT 令牌
            String token = getJwtFromRequest(request);

            // 2. 验证令牌
            if (token != null && jwtTokenProvider.validateToken(token)) {
                // 3. 从令牌中提取用户信息
                Long userId = jwtTokenProvider.getUserIdFromToken(token);
                String username = jwtTokenProvider.getUsernameFromToken(token);

                // 4. 创建 Authentication 对象
                UserDetails userDetails = User.builder()
                        .username(username)
                        .password("") // JWT 中不包含密码
                        .authorities(new ArrayList<>()) // 预留角色
                        .build();

                UsernamePasswordAuthenticationToken authentication = 
                    new UsernamePasswordAuthenticationToken(
                        userDetails,
                        null,
                        userDetails.getAuthorities()
                    );

                // 5. 设置用户ID到安全上下文
                authentication.setDetails(userId);
                SecurityContext securityContext = SecurityContextHolder.createEmptyContext();
                securityContext.setAuthentication(authentication);
                SecurityContextHolder.setContext(securityContext);

                log.debug("JWT 认证成功: userId={}, username={}", userId, username);
            }

        } catch (Exception ex) {
            log.error("JWT 认证失败", ex);
            // 认证失败不中断请求，由后续控制器处理
        }

        // 6. 继续过滤链
        filterChain.doFilter(request, response);
    }

    /**
     * 从请求头中获取 JWT 令牌
     */
    private String getJwtFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        
        // 也支持从查询参数获取（用于 SSE 连接）
        return request.getParameter("token");
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        
        // 不需要 JWT 认证的路径
        return path.startsWith("/api/auth/register") ||
               path.startsWith("/api/auth/login") ||
               path.startsWith("/api/auth/check-username") ||
               path.startsWith("/api/auth/refresh") ||
               path.startsWith("/api/public/") ||
               path.startsWith("/api/files/") ||
               path.startsWith("/api/admin/") ||
               path.startsWith("/actuator/") ||
               path.startsWith("/swagger-ui/") ||
               path.startsWith("/v3/api-docs/") ||
               path.equals("/error");
    }
}
