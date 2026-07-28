package com.bulongyu.housing.mapper;

import com.bulongyu.housing.entity.AdminHouseRow;
import com.bulongyu.housing.entity.AdminUserRow;
import org.apache.ibatis.annotations.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 后台管理数据访问接口
 */
@Mapper
public interface AdminMapper {
    /**
     * 统计符合筛选条件的用户总数。
     *
     * @param role 角色
     * @param active 是否启用
     * @param keyword 搜索关键字
     * @return 符合条件的数据数量
     */
    @Select("""
            <script>SELECT COUNT(*) FROM auth_user u JOIN user_profile p ON p.user_id=u.id WHERE 1=1
            <if test='role != null and role != ""'>AND p.role=#{role}</if>
            <if test='active != null'>AND u.is_active=#{active}</if>
            <if test='keyword != null and keyword != ""'>AND LOWER(u.username) LIKE LOWER(CONCAT('%',#{keyword},'%'))</if>
            </script>
            """)
    long countUsers(@Param("role") String role, @Param("active") Boolean active, @Param("keyword") String keyword);

    /**
     * 按筛选条件查询用户列表。
     *
     * @param role 角色
     * @param active 是否启用
     * @param keyword 搜索关键字
     * @param offset 分页查询的起始偏移量
     * @param limit 返回数量上限
     * @return 当前页用户列表
     */
    @Select("""
            <script>SELECT u.id,u.username,u.email,u.is_active active,u.date_joined,
              p.phone,p.role,p.avatar,p.create_time profile_create_time
            FROM auth_user u JOIN user_profile p ON p.user_id=u.id WHERE 1=1
            <if test='role != null and role != ""'>AND p.role=#{role}</if>
            <if test='active != null'>AND u.is_active=#{active}</if>
            <if test='keyword != null and keyword != ""'>AND LOWER(u.username) LIKE LOWER(CONCAT('%',#{keyword},'%'))</if>
            ORDER BY u.date_joined DESC LIMIT #{limit} OFFSET #{offset}</script>
            """)
    List<AdminUserRow> findUsers(@Param("role") String role, @Param("active") Boolean active,
                                 @Param("keyword") String keyword, @Param("offset") int offset,
                                 @Param("limit") int limit);

    /**
     * 查询用户。
     *
     * @param id 编号
     * @return 用户信息；不存在时为 {@code null}
     */
    @Select("""
            SELECT u.id,u.username,u.email,u.is_active active,u.date_joined,
              p.phone,p.role,p.avatar,p.create_time profile_create_time
            FROM auth_user u JOIN user_profile p ON p.user_id=u.id WHERE u.id=#{id}
            """)
    AdminUserRow findUser(Long id);

    /**
     * 由管理员更新用户资料与角色。
     *
     * @param id 编号
     * @param username 用户名
     * @param password 用户密码
     * @param active 是否启用
     */
    @Update("""
            UPDATE auth_user SET username=#{username},password=#{password},is_active=#{active}
            WHERE id=#{id}
            """)
    int updateUser(@Param("id") Long id, @Param("username") String username,
                   @Param("password") String password, @Param("active") boolean active);

    /**
     * 更新用户资料。
     *
     * @param userId 用户编号
     * @param phone 手机号
     * @param role 角色
     * @param now 当前时间
     */
    @Update("UPDATE user_profile SET phone=#{phone},role=#{role},update_time=#{now} WHERE user_id=#{userId}")
    int updateProfile(@Param("userId") Long userId, @Param("phone") String phone,
                      @Param("role") String role, @Param("now") LocalDateTime now);

    /**
     * 统计符合筛选条件的房源总数。
     *
     * @param status 状态
     * @param keyword 搜索关键字
     * @return 符合条件的数据数量
     */
    @Select("""
            <script>SELECT COUNT(*) FROM house h WHERE 1=1
            <if test='status != null and status != ""'>AND h.status=#{status}</if>
            <if test='keyword != null and keyword != ""'>AND LOWER(h.title) LIKE LOWER(CONCAT('%',#{keyword},'%'))</if>
            </script>
            """)
    long countHouses(@Param("status") String status, @Param("keyword") String keyword);

    /**
     * 按筛选条件查询房源列表。
     *
     * @param status 状态
     * @param keyword 搜索关键字
     * @param offset 分页查询的起始偏移量
     * @param limit 返回数量上限
     * @return 当前页房源列表
     */
    @Select("""
            <script>SELECT h.id,h.title,h.description,h.price,h.area,h.rooms,h.house_type,
              h.region_id region,r.name region_name,h.address_detail,h.image,h.landlord_id landlord,
              u.username landlord_username,p.phone landlord_phone,h.status,h.is_active active,
              h.create_time,h.update_time
            FROM house h JOIN user_profile p ON p.id=h.landlord_id JOIN auth_user u ON u.id=p.user_id
            LEFT JOIN area r ON r.id=h.region_id WHERE 1=1
            <if test='status != null and status != ""'>AND h.status=#{status}</if>
            <if test='keyword != null and keyword != ""'>AND LOWER(h.title) LIKE LOWER(CONCAT('%',#{keyword},'%'))</if>
            ORDER BY h.create_time DESC LIMIT #{limit} OFFSET #{offset}</script>
            """)
    List<AdminHouseRow> findHouses(@Param("status") String status, @Param("keyword") String keyword,
                                   @Param("offset") int offset, @Param("limit") int limit);
}
