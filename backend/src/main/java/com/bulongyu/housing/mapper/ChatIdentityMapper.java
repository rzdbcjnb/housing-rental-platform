package com.bulongyu.housing.mapper;

import com.bulongyu.housing.entity.UserProfile;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

/**
 * 实时聊天数据访问接口
 */
@Mapper
public interface ChatIdentityMapper {
    /**
     * 根据资料编号查询用户资料。
     *
     * @param id 用户资料编号
     * @return 用户资料；不存在时为 {@code null}
     */
    @Select("""
            SELECT id, user_id, phone, role, avatar, create_time, update_time
            FROM user_profile WHERE id = #{id}
            """)
    UserProfile findProfileById(Long id);
}
