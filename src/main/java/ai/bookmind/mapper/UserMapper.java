package ai.bookmind.mapper;

import ai.bookmind.entity.User;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * 用户Mapper接口
 */
@Mapper
public interface UserMapper {

    /**
     * 插入用户
     */
    @Insert("""
        INSERT INTO user (username, password, phone, avatar, create_time, last_login_time, status)
        VALUES (#{username}, #{password}, #{phone}, #{avatar}, #{createTime}, #{lastLoginTime}, #{status})
    """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(User user);

    /**
     * 根据ID查询用户
     */
    @Select("SELECT * FROM user WHERE id = #{id}")
    User selectById(@Param("id") Long id);

    /**
     * 根据用户名查询用户
     */
    @Select("SELECT * FROM user WHERE username = #{username}")
    User selectByUsername(@Param("username") String username);

    /**
     * 根据手机号查询用户
     */
    @Select("SELECT * FROM user WHERE phone = #{phone}")
    User selectByPhone(@Param("phone") String phone);

    /**
     * 检查用户名是否存在
     */
    @Select("SELECT COUNT(*) FROM user WHERE username = #{username}")
    int countByUsername(@Param("username") String username);

    /**
     * 检查手机号是否存在
     */
    @Select("SELECT COUNT(*) FROM user WHERE phone = #{phone}")
    int countByPhone(@Param("phone") String phone);

    /**
     * 更新用户信息
     */
    @Update("""
        UPDATE user 
        SET username = #{username}, password = #{password}, phone = #{phone}, 
            avatar = #{avatar}, last_login_time = #{lastLoginTime}, status = #{status}
        WHERE id = #{id}
    """)
    int update(User user);

    /**
     * 更新最后登录时间
     */
    @Update("UPDATE user SET last_login_time = #{lastLoginTime} WHERE id = #{id}")
    int updateLastLoginTime(@Param("id") Long id, @Param("lastLoginTime") java.time.LocalDateTime lastLoginTime);

    /**
     * 更新密码
     */
    @Update("UPDATE user SET password = #{password} WHERE id = #{id}")
    int updatePassword(@Param("id") Long id, @Param("password") String password);

    /**
     * 更新手机号
     */
    @Update("UPDATE user SET phone = #{phone} WHERE id = #{id}")
    int updatePhone(@Param("id") Long id, @Param("phone") String phone);

    /**
     * 更新用户名
     */
    @Update("UPDATE user SET username = #{username} WHERE id = #{id}")
    int updateUsername(@Param("id") Long id, @Param("username") String username);

    /**
     * 禁用/启用账号
     */
    @Update("UPDATE user SET status = #{status} WHERE id = #{id}")
    int updateStatus(@Param("id") Long id, @Param("status") Integer status);

    /**
     * 删除用户（软删除）
     */
    @Update("UPDATE user SET status = 1 WHERE id = #{id}")
    int delete(@Param("id") Long id);

    /**
     * 查询用户列表（分页）
     */
    @Select("SELECT * FROM user WHERE status = 0 ORDER BY create_time DESC LIMIT #{offset}, #{size}")
    List<User> selectList(@Param("offset") Integer offset, @Param("size") Integer size);

    /**
     * 查询用户总数
     */
    @Select("SELECT COUNT(*) FROM user WHERE status = 0")
    int countTotal();
}
