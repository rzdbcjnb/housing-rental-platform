package com.bulongyu.housing.mapper;

import com.bulongyu.housing.entity.InteractionEntity;
import com.bulongyu.housing.entity.InteractionRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 收藏与浏览历史数据访问接口
 */
@Mapper
public interface InteractionMapper {
    /**
     * 查询收藏编号。
     *
     * @param userId 用户编号
     * @param houseId 房源编号
     * @return 收藏记录编号；不存在时为 {@code null}
     */
    Long findFavoriteId(@Param("userId") Long userId, @Param("houseId") Long houseId);
    /**
     * 新增收藏记录。
     *
     * @param entity 持久化实体
     * @return 受影响行数
     */
    int insertFavorite(InteractionEntity entity);
    /**
     * 删除收藏。
     *
     * @param id 编号
     * @param userId 用户编号
     */
    int deleteFavorite(@Param("id") Long id, @Param("userId") Long userId);
    /**
     * 统计用户收藏总数。
     *
     * @param userId 用户编号
     * @return 符合条件的数据数量
     */
    long countFavorites(Long userId);
    /**
     * 分页查询用户收藏记录。
     *
     * @param userId 用户编号
     * @param offset 分页查询的起始偏移量
     * @param limit 返回数量上限
     * @return 当前页收藏记录
     */
    List<InteractionRow> findFavorites(@Param("userId") Long userId,
                                       @Param("offset") int offset, @Param("limit") int limit);

    /**
     * 查询房源浏览历史记录编号。
     *
     * @param userId 用户编号
     * @param houseId 房源编号
     * @return 浏览历史记录编号；不存在时为 {@code null}
     */
    Long findHistoryId(@Param("userId") Long userId, @Param("houseId") Long houseId);
    /**
     * 新增房源浏览历史记录。
     *
     * @param entity 持久化实体
     * @return 受影响行数
     */
    int insertHistory(InteractionEntity entity);
    /**
     * 刷新房源浏览历史的访问时间。
     *
     * @param id 编号
     * @param createTime 创建时间
     */
    int touchHistory(@Param("id") Long id, @Param("createTime") LocalDateTime createTime);
    /**
     * 删除房源浏览历史记录。
     *
     * @param id 编号
     * @param userId 用户编号
     */
    int deleteHistory(@Param("id") Long id, @Param("userId") Long userId);
    /**
     * 清空当前用户的全部浏览历史。
     *
     * @param userId 用户编号
     */
    int clearHistory(Long userId);
    /**
     * 统计用户浏览历史总数。
     *
     * @param userId 用户编号
     * @return 符合条件的数据数量
     */
    long countHistory(Long userId);
    /**
     * 分页查询用户浏览历史。
     *
     * @param userId 用户编号
     * @param offset 分页查询的起始偏移量
     * @param limit 返回数量上限
     * @return 当前页浏览历史记录
     */
    List<InteractionRow> findHistory(@Param("userId") Long userId,
                                     @Param("offset") int offset, @Param("limit") int limit);
}
