package com.bulongyu.housing.mapper;

import com.bulongyu.housing.entity.PaymentRecord;
import com.bulongyu.housing.entity.PointAccount;
import com.bulongyu.housing.entity.RecommendPoint;
import com.bulongyu.housing.entity.RecommendStatusRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 支付与推荐点数据访问接口
 */
@Mapper
public interface CommerceMapper {
    /**
     * 统计房东已发布的房源数量。
     *
     * @param profileId 用户资料编号
     * @return 符合条件的数据数量
     */
    long countHouses(Long profileId);
    /**
     * 查询并锁定房东未使用的发布支付记录。
     *
     * @param profileId 用户资料编号
     * @return 加锁后的未使用支付记录；不存在时为 {@code null}
     */
    PaymentRecord findUnusedPaymentForUpdate(Long profileId);
    /**
     * 新增房源发布支付记录。
     *
     * @param record 支付记录
     * @return 受影响行数
     */
    int insertPayment(PaymentRecord record);
    /**
     * 将支付记录与新发布的房源关联。
     *
     * @param id 编号
     * @param houseId 房源编号
     */
    int linkPayment(@Param("id") Long id, @Param("houseId") Long houseId);
    /**
     * 解除支付记录与房源的关联。
     *
     * @param houseId 房源编号
     */
    int deletePaymentForHouse(Long houseId);

    /**
     * 查询用户推荐点账户。
     *
     * @param profileId 用户资料编号
     * @return 用户推荐点账户；不存在时为 {@code null}
     */
    PointAccount findAccount(Long profileId);
    /**
     * 查询并锁定用户推荐点账户。
     *
     * @param profileId 用户资料编号
     * @return 加锁后的推荐点账户；不存在时为 {@code null}
     */
    PointAccount findAccountForUpdate(Long profileId);
    /**
     * 新增用户推荐点账户。
     *
     * @param account 用户推荐点账户
     * @return 受影响行数
     */
    int insertAccount(PointAccount account);
    /**
     * 更新用户推荐点账户。
     *
     * @param account 用户推荐点账户
     */
    int updateAccount(PointAccount account);

    /**
     * 查询并锁定房源推荐点记录。
     *
     * @param houseId 房源编号
     * @return 加锁后的房源推荐点记录；不存在时为 {@code null}
     */
    RecommendPoint findRecommendPointForUpdate(Long houseId);
    /**
     * 新增房源推荐点记录。
     *
     * @param point 房源推荐点记录
     * @return 受影响行数
     */
    int insertRecommendPoint(RecommendPoint point);
    /**
     * 更新房源推荐点记录。
     *
     * @param point 房源推荐点记录
     */
    int updateRecommendPoint(RecommendPoint point);
    /**
     * 新增推荐点购买流水。
     *
     * @param userId 用户编号
     * @param houseId 房源编号
     * @param points 积分
     * @param amount 金额
     * @param createdAt 创建时间
     * @return 受影响行数
     */
    int insertPurchase(@Param("userId") Long userId, @Param("houseId") Long houseId,
                       @Param("points") Integer points, @Param("amount") BigDecimal amount,
                       @Param("createdAt") LocalDateTime createdAt);
    /**
     * 查询房源当前推荐点数。
     *
     * @param profileId 用户资料编号
     * @return 房源当前推荐点数
     */
    List<RecommendStatusRow> findRecommendStatus(Long profileId);

    /**
     * 原子增加房源点击次数。
     *
     * @param houseId 房源编号
     */
    int incrementClick(Long houseId);
    /**
     * 查询房源点击次数。
     *
     * @param houseId 房源编号
     * @return 符合条件的数据数量
     */
    Integer findClickCount(Long houseId);
}
