package ai.bookmind.entity;

import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 用户实体
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class User {

    /** 用户ID */
    private Long id;

    /** 用户名（唯一） */
    private String username;

    /** 密码（BCrypt加密） */
    private String password;

    /** 手机号（用于找回密码） */
    private String phone;

    /** 头像URL */
    private String avatar;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 最后登录时间 */
    private LocalDateTime lastLoginTime;

    /** 账号状态：0-正常 1-禁用 */
    private Integer status;

    /**
     * 检查用户名是否可用
     */
    public static boolean isUsernameAvailable(String username) {
        return username != null && username.length() >= 3 && username.length() <= 20;
    }

    /**
     * 检查密码强度
     */
    public static boolean isPasswordValid(String password) {
        return password != null && password.length() >= 6;
    }

    /**
     * 检查手机号格式
     */
    public static boolean isPhoneValid(String phone) {
        return phone != null && phone.matches("^1[3-9]\\d{9}$");
    }
}
