package com.bulongyu.housing.mapper;

import com.bulongyu.housing.entity.AuthUser;
import com.bulongyu.housing.entity.UserProfile;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

/**
 * 用户认证数据访问接口
 */
@Mapper
public interface UserMapper {
    /**
     * 根据用户名查询认证账号。
     *
     * @param username 用户名
     * @return 认证账号；不存在时为 {@code null}
     */
    @Select("""
            SELECT id, username, password, is_active AS active, is_staff AS staff,
                   is_superuser AS superuser, last_login, date_joined
            FROM auth_user WHERE username = #{username}
            """)
    AuthUser findByUsername(String username);

    /**
     * 根据编号查询认证账号。
     *
     * @param id 用户编号
     * @return 认证账号；不存在时为 {@code null}
     */
    @Select("""
            SELECT id, username, password, is_active AS active, is_staff AS staff,
                   is_superuser AS superuser, last_login, date_joined
            FROM auth_user WHERE id = #{id}
            """)
    AuthUser findById(Long id);

    /**
     * 统计用户名数量。
     *
     * @param username 用户名
     * @return 符合条件的数据数量
     */
    @Select("SELECT COUNT(*) FROM auth_user WHERE username = #{username}")
    int countByUsername(String username);

    /**
     * 统计手机号数量。
     *
     * @param phone 手机号
     * @return 符合条件的数据数量
     */
    @Select("SELECT COUNT(*) FROM user_profile WHERE phone = #{phone}")
    int countByPhone(String phone);

    /**
     * 新增用户记录。
     *
     * @param username 用户名
     * @param password 用户密码
     * @param now 当前时间
     * @return 受影响行数
     */
    @Insert("""
            INSERT INTO auth_user
                (password, last_login, is_superuser, username, first_name, last_name,
                 email, is_staff, is_active, date_joined)
            VALUES
                (#{password}, NULL, FALSE, #{username}, '', '', '', FALSE, TRUE, #{now})
            """)
    int insertUser(@Param("username") String username, @Param("password") String password,
                   @Param("now") LocalDateTime now);

    /**
     * 新增用户资料记录。
     *
     * @param userId 用户编号
     * @param phone 手机号
     * @param role 角色
     * @param now 当前时间
     * @return 受影响行数
     */
    @Insert("""
            INSERT INTO user_profile (phone, role, avatar, create_time, update_time, user_id)
            VALUES (#{phone}, #{role}, '', #{now}, #{now}, #{userId})
            """)
    int insertProfile(@Param("userId") Long userId, @Param("phone") String phone,
                      @Param("role") String role, @Param("now") LocalDateTime now);

    /**
     * 根据用户编号查询用户资料。
     *
     * @param userId 用户编号
     * @return 用户资料；不存在时为 {@code null}
     */
    @Select("""
            SELECT id, user_id, phone, role, avatar, create_time, update_time
            FROM user_profile WHERE user_id = #{userId}
            """)
    UserProfile findProfileByUserId(Long userId);

    /**
     * 更新用户最近登录时间。
     *
     * @param userId 用户编号
     * @param now 当前时间
     */
    @Update("UPDATE auth_user SET last_login = #{now} WHERE id = #{userId}")
    int updateLastLogin(@Param("userId") Long userId, @Param("now") LocalDateTime now);

    /**
     * 更新用户名。
     *
     * @param userId 用户编号
     * @param username 用户名
     */
    @Update("UPDATE auth_user SET username = #{username} WHERE id = #{userId}")
    int updateUsername(@Param("userId") Long userId, @Param("username") String username);

    /**
     * 更新用户资料。
     *
     * @param userId 用户编号
     * @param phone 手机号
     * @param avatar 头像地址
     * @param now 当前时间
     */
    @Update("""
            UPDATE user_profile
            SET phone = #{phone}, avatar = #{avatar}, update_time = #{now}
            WHERE user_id = #{userId}
            """)
    int updateProfile(@Param("userId") Long userId, @Param("phone") String phone,
                      @Param("avatar") String avatar, @Param("now") LocalDateTime now);
}
