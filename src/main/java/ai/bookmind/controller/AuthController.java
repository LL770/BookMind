package ai.bookmind.controller;

import ai.bookmind.annotation.LogOperation;
import ai.bookmind.common.Result;
import ai.bookmind.entity.User;
import ai.bookmind.service.UserService;
import ai.bookmind.util.JwtTokenProvider;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 用户控制器
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
@Validated
public class AuthController {

    private final UserService userService;
    private final JwtTokenProvider jwtTokenProvider;

    /**
     * 用户注册
     */
    @PostMapping("/register")
    @LogOperation("注册")
    public Result<User> register(
            @RequestParam @NotBlank String username,
            @RequestParam @NotBlank String password,
            @RequestParam @NotBlank String phone) {

        log.info("用户注册: username={}", username);
        Result<User> result = userService.register(username, password, phone);
        if (result.isSuccess() && result.getData() != null) {
            User user = result.getData();
            String token = jwtTokenProvider.generateToken(user.getId(), user.getUsername());
            result.setData(user);
        }
        return result;
    }

    private final org.springframework.data.redis.core.RedisTemplate<String, Object> redisTemplate;

    /**
     * 发送手机验证码（模拟，Redis 存储 + 60 秒限制）
     */
    @PostMapping("/send-code")
    public Result<String> sendCode(@RequestParam @NotBlank String phone) {
        String limitKey = "sms:limit:" + phone;
        String codeKey = "sms:code:" + phone;

        // 检查60秒限制
        if (Boolean.TRUE.equals(redisTemplate.hasKey(limitKey))) {
            return Result.badRequest("请 60 秒后再试");
        }

        String code = String.format("%06d", (int)(Math.random() * 1000000));
        redisTemplate.opsForValue().set(codeKey, code, 5, java.util.concurrent.TimeUnit.MINUTES);
        redisTemplate.opsForValue().set(limitKey, "1", 60, java.util.concurrent.TimeUnit.SECONDS);
        log.info("【模拟短信】验证码 {} → 手机 {}", code, phone);
        return Result.success(code);
    }

    /**
     * 校验验证码
     */
    @PostMapping("/verify-code")
    public Result<Void> verifyCode(@RequestParam @NotBlank String phone, @RequestParam @NotBlank String code) {
        String codeKey = "sms:code:" + phone;
        Object saved = redisTemplate.opsForValue().get(codeKey);
        if (saved == null || !saved.toString().equals(code.trim())) {
            return Result.badRequest("验证码错误或已过期");
        }
        redisTemplate.delete(codeKey);
        return Result.success();
    }

    /**
     * 用户登录
     */
    @PostMapping("/login")
    @LogOperation("登录")
    public Result<TokenResponse> login(
            @RequestParam @NotBlank String username,
            @RequestParam @NotBlank String password,
            HttpServletRequest request) {
        
        log.info("用户登录: username={}", username);
        
        Result<User> userResult = userService.login(username, password);
        if (!userResult.isSuccess()) {
            return Result.error(userResult.getCode(), userResult.getMessage());
        }

        User user = userResult.getData();
        
        // 生成JWT令牌
        String token = jwtTokenProvider.generateToken(user.getId(), user.getUsername());
        
        // 将用户信息存入Session（可选，用于会话管理）
        HttpSession session = request.getSession();
        session.setAttribute("userId", user.getId());
        session.setAttribute("username", user.getUsername());
        session.setAttribute("token", token);

        TokenResponse response = new TokenResponse();
        response.setUser(user);
        response.setToken(token);
        response.setExpiresIn(jwtTokenProvider.getTokenRemainingValidity(token));

        return Result.success(response);
    }

    /**
     * 检查用户名是否可用
     */
    @GetMapping("/check-username")
    public Result<Boolean> checkUsername(@RequestParam String username) {
        return userService.checkUsername(username);
    }

    /**
     * 获取当前用户信息
     */
    @GetMapping("/me")
    public Result<User> getCurrentUser(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return Result.unauthorized("未登录");
        }
        
        Long userId = (Long) session.getAttribute("userId");
        if (userId == null) {
            return Result.unauthorized("未登录");
        }

        return userService.getById(userId);
    }

    /**
     * 修改用户名
     */
    @PutMapping("/username")
    public Result<Void> updateUsername(
            HttpServletRequest request,
            @RequestParam String newUsername) {
        
        Long userId = getUserIdFromRequest(request);
        if (userId == null) {
            return Result.unauthorized("未登录");
        }
        
        return userService.updateUsername(userId, newUsername);
    }

    /**
     * 修改密码
     */
    @PutMapping("/password")
    public Result<Void> updatePassword(
            HttpServletRequest request,
            @RequestParam String oldPassword,
            @RequestParam String newPassword) {
        
        Long userId = getUserIdFromRequest(request);
        if (userId == null) {
            return Result.unauthorized("未登录");
        }
        
        return userService.updatePassword(userId, oldPassword, newPassword);
    }

    /**
     * 绑定手机号
     */
    @PutMapping("/phone")
    public Result<Void> bindPhone(
            HttpServletRequest request,
            @RequestParam String phone) {
        
        Long userId = getUserIdFromRequest(request);
        if (userId == null) {
            return Result.unauthorized("未登录");
        }
        
        return userService.bindPhone(userId, phone);
    }

    /**
     * 退出登录
     */
    @PostMapping("/logout")
    public Result<Void> logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        log.info("用户退出登录");
        return Result.success();
    }

    /**
     * 注销账号
     */
    @DeleteMapping("/account")
    public Result<Void> deleteAccount(HttpServletRequest request) {
        Long userId = getUserIdFromRequest(request);
        if (userId == null) {
            return Result.unauthorized("未登录");
        }
        
        return userService.deleteAccount(userId);
    }

    /**
     * 从请求中获取用户ID
     */
    private Long getUserIdFromRequest(HttpServletRequest request) {
        // 优先从Session获取
        HttpSession session = request.getSession(false);
        if (session != null) {
            Long userId = (Long) session.getAttribute("userId");
            if (userId != null) {
                return userId;
            }
        }
        
        // 从JWT令牌获取
        String token = request.getHeader("Authorization");
        if (token != null && token.startsWith("Bearer ")) {
            token = token.substring(7);
            if (jwtTokenProvider.validateToken(token)) {
                return jwtTokenProvider.getUserIdFromToken(token);
            }
        }
        
        return null;
    }

    /**
     * 登录响应
     */
    @lombok.Data
    @lombok.AllArgsConstructor
    @lombok.NoArgsConstructor
    public static class TokenResponse {
        private User user;
        private String token;
        private Long expiresIn;
    }
}
