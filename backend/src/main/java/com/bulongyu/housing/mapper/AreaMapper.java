package com.bulongyu.housing.mapper;

import com.bulongyu.housing.entity.Area;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 地区数据访问接口
 */
@Mapper
public interface AreaMapper {
    /**
     * 按层级和上级地区查询启用的地区。
     *
     * @param level 地区层级
     * @param parentId 上级地区编号
     * @return 启用的地区列表
     */
    @Select("""
            <script>
            SELECT id, name, parent_id, level, is_active AS active
            FROM area
            WHERE is_active = TRUE
            <if test="level != null">AND level = #{level}</if>
            <if test="parentId != null">AND parent_id = #{parentId}</if>
            ORDER BY name
            </script>
            """)
    List<Area> findActive(@Param("level") Integer level, @Param("parentId") Long parentId);
}
