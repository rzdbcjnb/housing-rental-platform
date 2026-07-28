package com.bulongyu.housing.mapper;

import com.bulongyu.housing.entity.House;
import com.bulongyu.housing.entity.HouseQuery;
import com.bulongyu.housing.entity.HouseRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 房源数据访问接口
 */
@Mapper
public interface HouseMapper {
    /**
     * 统计公开状态数量。
     *
     * @param query 房源筛选条件
     * @return 符合条件的数据数量
     */
    long countPublic(@Param("q") HouseQuery query);

    /**
     * 查询当前公开房源中最新的数据更新时间，作为向量索引同步水位。
     *
     * @return 最新更新时间；没有公开房源时为 {@code null}
     */
    LocalDateTime latestPublicUpdateTime();

    /**
     * 分页查询公开且启用的房源。
     *
     * @param query 房源筛选条件
     * @param offset 分页查询的起始偏移量
     * @param limit 返回数量上限
     * @return 当前页公开房源列表
     */
    List<HouseRow> findPublic(@Param("q") HouseQuery query, @Param("offset") int offset,
                              @Param("limit") int limit);

    /**
     * 根据编号查询房源。
     *
     * @param id 房源编号
     * @return 房源信息；不存在时为 {@code null}
     */
    HouseRow findById(Long id);

    /**
     * 统计房东名下符合条件的房源总数。
     *
     * @param landlordId 房东编号
     * @param keyword 搜索关键字
     * @return 符合条件的数据数量
     */
    long countByLandlord(@Param("landlordId") Long landlordId, @Param("keyword") String keyword);

    /**
     * 分页查询房东发布的房源。
     *
     * @param landlordId 房东编号
     * @param keyword 搜索关键字
     * @param offset 分页查询的起始偏移量
     * @param limit 返回数量上限
     * @return 当前页房源列表
     */
    List<HouseRow> findByLandlord(@Param("landlordId") Long landlordId,
                                  @Param("keyword") String keyword,
                                  @Param("offset") int offset, @Param("limit") int limit);

    /**
     * 新增房源记录并回填主键。
     *
     * @param house 待新增或更新的房源
     * @return 受影响行数
     */
    int insert(House house);

    /**
     * 校验权限后更新指定业务数据。
     *
     * @param house 待新增或更新的房源
     * @return 受影响行数
     */
    int update(House house);

    /**
     * 校验权限后删除指定业务数据。
     *
     * @param id 房源编号
     * @return 受影响行数
     */
    int delete(Long id);

    /**
     * 更新状态。
     *
     * @param id 房源编号
     * @param status 状态
     * @param active 是否启用
     * @param now 当前时间
     */
    int updateStatus(@Param("id") Long id, @Param("status") String status,
                     @Param("active") boolean active, @Param("now") LocalDateTime now);
}
