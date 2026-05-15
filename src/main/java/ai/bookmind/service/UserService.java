package ai.bookmind.service;

import ai.bookmind.common.Result;
import ai.bookmind.entity.Book;
import ai.bookmind.entity.User;
import ai.bookmind.entity.BookCategory;
import ai.bookmind.mapper.BookCategoryMapper;
import ai.bookmind.mapper.BookMapper;
import ai.bookmind.mapper.NoteMapper;
import ai.bookmind.mapper.UserMapper;
import ai.bookmind.util.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

import java.time.LocalDateTime;
import java.util.regex.Pattern;

/**
 * 用户服务实现
 * 使用 Spring Security BCrypt 密码编码器
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder; // Spring Security BCrypt
    private final BookMapper bookMapper;
    private final NoteMapper noteMapper;
    private final BookUploadService bookUploadService;
    private final RedisTemplate<String, Object> redisTemplate;
    private final BookCategoryMapper bookCategoryMapper;

    @Value("${bookmind.jwt.secret}")
    private String jwtSecret;

    /**
     * 手机号正则
     */
    private static final Pattern PHONE_PATTERN = Pattern.compile("^1[3-9]\\d{9}$");

    /**
     * 用户名正则（3-20 位字母数字下划线）
     */
    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_]{3,20}$");

    /**
     * 用户注册
     */
    @Transactional(rollbackFor = Exception.class)
    public Result<User> register(String username, String password, String phone) {
        // 1. 参数校验
        if (username == null || username.trim().isEmpty()) {
            return Result.badRequest("用户名不能为空");
        }
        if (!USERNAME_PATTERN.matcher(username).matches()) {
            return Result.badRequest("用户名应为 3-20 位字母、数字或下划线");
        }
        if (password == null || password.length() < 6) {
            return Result.badRequest("密码至少 6 位");
        }
        if (phone == null || !PHONE_PATTERN.matcher(phone).matches()) {
            return Result.badRequest("手机号格式不正确");
        }

        // 2. 检查用户名是否已存在
        if (userMapper.countByUsername(username) > 0) {
            return Result.badRequest("该用户名已被注册");
        }

        // 3. 检查手机号是否已存在
        if (userMapper.countByPhone(phone) > 0) {
            return Result.badRequest("该手机号已被注册");
        }

        // 4. 创建用户（使用 BCrypt 加密密码）
        User user = new User();
        user.setUsername(username.trim());
        user.setPassword(passwordEncoder.encode(password)); // BCrypt 加密
        user.setPhone(phone);
        user.setAvatar(null);
        user.setStatus(0);
        user.setCreateTime(LocalDateTime.now());
        user.setLastLoginTime(LocalDateTime.now());

        userMapper.insert(user);

        // 5. 将用户加入布隆过滤器（防恶意查询）
        // bloomFilterService.addUserToBloom(username);

        // 6. 返回用户信息（不含密码）
        user.setPassword(null);
        log.info("用户注册成功：userId={}, username={}", user.getId(), username);
        return Result.success(user);
    }

    /**
     * 用户登录
     */
    public Result<User> login(String username, String password) {
        if (username == null || username.trim().isEmpty()) {
            return Result.badRequest("用户名不能为空");
        }
        if (password == null || password.isEmpty()) {
            return Result.badRequest("密码不能为空");
        }

        // 1. 查询用户
        User user = userMapper.selectByUsername(username.trim());
        if (user == null) {
            // 为了安全，不提示具体哪个错误
            return Result.unauthorized("用户名或密码错误");
        }

        // 2. 检查账号状态
        if (user.getStatus() == 1) {
            return Result.forbidden("账号已被禁用，请联系管理员");
        }

        // 3. 验证密码（BCrypt 比对）
        if (!passwordEncoder.matches(password, user.getPassword())) {
            return Result.unauthorized("用户名或密码错误");
        }

        // 4. 更新最后登录时间
        userMapper.updateLastLoginTime(user.getId(), LocalDateTime.now());

        // 5. 返回用户信息（不含密码）
        user.setPassword(null);
        log.info("用户登录成功：userId={}, username={}", user.getId(), username);
        return Result.success(user);
    }

    /**
     * 根据 ID 获取用户
     */
    public Result<User> getById(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            return Result.notFound("用户不存在");
        }
        user.setPassword(null);
        return Result.success(user);
    }

    /**
     * 检查用户名是否可用
     */
    public Result<Boolean> checkUsername(String username) {
        if (username == null || username.trim().isEmpty()) {
            return Result.badRequest("用户名不能为空");
        }
        if (!USERNAME_PATTERN.matcher(username).matches()) {
            return Result.badRequest("用户名应为 3-20 位字母、数字或下划线");
        }
        boolean available = userMapper.countByUsername(username.trim()) == 0;
        return Result.success(available);
    }

    /**
     * 修改用户名
     */
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> updateUsername(Long userId, String newUsername) {
        if (newUsername == null || newUsername.trim().isEmpty()) {
            return Result.badRequest("用户名不能为空");
        }
        if (!USERNAME_PATTERN.matcher(newUsername).matches()) {
            return Result.badRequest("用户名应为 3-20 位字母、数字或下划线");
        }

        // 检查新用户名是否已被占用
        int count = userMapper.countByUsername(newUsername.trim());
        if (count > 0) {
            return Result.badRequest("该用户名已被使用");
        }

        userMapper.updateUsername(userId, newUsername.trim());
        return Result.success();
    }

    /**
     * 修改密码
     */
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> updatePassword(Long userId, String oldPassword, String newPassword) {
        if (oldPassword == null || oldPassword.isEmpty()) {
            return Result.badRequest("旧密码不能为空");
        }
        if (newPassword == null || newPassword.length() < 6) {
            return Result.badRequest("新密码至少 6 位");
        }

        // 1. 查询用户
        User user = userMapper.selectById(userId);
        if (user == null) {
            return Result.notFound("用户不存在");
        }

        // 2. 验证旧密码（BCrypt 比对）
        if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
            return Result.unauthorized("旧密码错误");
        }

        // 3. 更新密码（BCrypt 加密）
        userMapper.updatePassword(userId, passwordEncoder.encode(newPassword));
        return Result.success();
    }

    /**
     * 绑定手机号
     */
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> bindPhone(Long userId, String phone) {
        if (phone == null || !PHONE_PATTERN.matcher(phone).matches()) {
            return Result.badRequest("手机号格式不正确");
        }

        // 检查手机号是否已被绑定
        if (userMapper.countByPhone(phone) > 0) {
            return Result.badRequest("该手机号已被绑定");
        }

        userMapper.updatePhone(userId, phone);
        return Result.success();
    }

    /**
     * 注销账号（软删除）
     */
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> deleteAccount(Long userId) {
        // 删除用户所有书籍（笔记、KG、向量、MinIO一并清理）
        List<Book> books = bookMapper.selectByUserId(userId);
        for (Book book : books) {
            try { bookUploadService.deleteBook(userId, book.getId()); }
            catch (Exception e) { log.warn("删除书籍失败 bookId={}", book.getId(), e); }
        }
        // 删除用户所有笔记
        try { noteMapper.deleteByUserId(userId); } catch (Exception e) { log.warn("删除笔记失败", e); }
        // 清除对话历史
        try {
            Set<String> keys = redisTemplate.keys("chathistory:*" + userId + "*");
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
            }
        } catch (Exception e) { log.warn("清除对话历史失败", e); }
        // 删除用户分类
        try {
            List<BookCategory> cats = bookCategoryMapper.selectByUserId(userId);
            for (BookCategory c : cats) {
                bookCategoryMapper.deleteById(c.getId(), userId);
            }
        } catch (Exception e) { log.warn("删除分类失败", e); }
        // 删除用户
        userMapper.delete(userId);
        log.info("账号注销成功: userId={}", userId);
        return Result.success();
    }

    /**
     * 验证密码（外部调用）
     */
    public boolean verifyPassword(Long userId, String password) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            return false;
        }
        return passwordEncoder.matches(password, user.getPassword());
    }
}
