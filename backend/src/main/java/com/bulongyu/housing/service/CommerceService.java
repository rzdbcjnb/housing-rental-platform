package com.bulongyu.housing.service;

import com.bulongyu.housing.vo.AccountView;
import com.bulongyu.housing.vo.ClickView;
import com.bulongyu.housing.vo.PaymentResponse;
import com.bulongyu.housing.vo.PointResponse;
import com.bulongyu.housing.vo.PublishLimitView;
import com.bulongyu.housing.vo.RecommendStatusItem;
import com.bulongyu.housing.vo.RecommendStatusView;


import com.bulongyu.housing.entity.PaymentRecord;
import com.bulongyu.housing.entity.PointAccount;
import com.bulongyu.housing.entity.RecommendPoint;
import com.bulongyu.housing.mapper.CommerceMapper;
import com.bulongyu.housing.common.BusinessException;
import com.bulongyu.housing.entity.HouseRow;
import com.bulongyu.housing.mapper.HouseMapper;
import com.bulongyu.housing.entity.UserProfile;
import com.bulongyu.housing.mapper.UserMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

/**
 * 支付与推荐点业务服务
 */
@Service
public class CommerceService {
    private static final int MAX_HOUSE_POINTS = 100;
    private final CommerceMapper commerceMapper;
    private final HouseMapper houseMapper;
    private final UserMapper userMapper;
    private final PublishingService publishingService;

    /**
     * 初始化 {@code CommerceService} 并注入所需依赖。
     *
     * @param commerceMapper 支付数据访问组件
     * @param houseMapper 房源数据访问组件
     * @param userMapper 用户数据访问组件
     * @param publishingService 房源发布资格服务
     */
    public CommerceService(CommerceMapper commerceMapper, HouseMapper houseMapper,
                           UserMapper userMapper, PublishingService publishingService) {
        this.commerceMapper = commerceMapper;
        this.houseMapper = houseMapper;
        this.userMapper = userMapper;
        this.publishingService = publishingService;
    }

    /**
     * 查询当前用户的房源发布额度。
     *
     * @param userId 用户编号
     */
    public PublishLimitView publishLimit(Long userId) {
        return publishingService.limit(profile(userId).id());
    }

    /**
     * 模拟支付并增加房源发布额度。
     *
     * @param userId 用户编号
     * @param requestedAmount requested金额
     */
    @Transactional
    public PaymentResponse simulatePayment(Long userId, BigDecimal requestedAmount) {
        BigDecimal amount = requestedAmount == null ? BigDecimal.TEN : requestedAmount;
        if (amount.signum() <= 0) {
            throw bad("INVALID_AMOUNT", "\u652f\u4ed8\u91d1\u989d\u5fc5\u987b\u5927\u4e8e 0");
        }
        // 先完成金额校验再写入已支付记录；事务失败时不会留下可用但未完成的发布额度。
        PaymentRecord record = new PaymentRecord();
        record.setUserId(profile(userId).id());
        record.setAmount(amount.setScale(2, RoundingMode.HALF_UP));
        record.setPaid(true);
        record.setCreatedAt(LocalDateTime.now());
        commerceMapper.insertPayment(record);
        return new PaymentResponse("\u652f\u4ed8\u6210\u529f", record.getId(), record.getAmount());
    }

    /**
     * 查询当前用户的积分账户。
     *
     * @param userId 用户编号
     */
    public AccountView account(Long userId) {
        PointAccount account = commerceMapper.findAccount(profile(userId).id());
        return account == null ? new AccountView(0, 0, 0)
                : new AccountView(account.getBalance(), account.getTotalPurchased(),
                account.getTotalInvested());
    }

    /**
     * 查询指定房源的推荐点数状态。
     *
     * @param userId 用户编号
     */
    public RecommendStatusView recommendStatus(Long userId) {
        var houses = commerceMapper.findRecommendStatus(profile(userId).id())
                .stream()
                .map(row -> new RecommendStatusItem(
                        row.houseId(), row.title(), row.status(), row.points(),
                        weight(row.points()), MAX_HOUSE_POINTS, row.clickCount()))
                .toList();
        return new RecommendStatusView(houses);
    }

    /**
     * 为当前用户的积分账户充值。
     *
     * @param userId 用户编号
     * @param points 积分
     */
    @Transactional
    public PointResponse recharge(Long userId, Integer points) {
        requirePositive(points);
        Long profileId = profile(userId).id();
        // 对积分账户加行锁，使同一账户的并发余额更新串行执行。
        PointAccount account = commerceMapper.findAccountForUpdate(profileId);
        LocalDateTime now = LocalDateTime.now();
        if (account == null) {
            account = new PointAccount();
            account.setUserId(profileId);
            account.setBalance(0);
            account.setTotalPurchased(0);
            account.setTotalInvested(0);
            account.setCreatedAt(now);
        }
        account.setBalance(account.getBalance() + points);
        account.setTotalPurchased(account.getTotalPurchased() + points);
        account.setUpdatedAt(now);
        // 新账户执行插入，既有账户执行更新；随后写入购买流水，三者共享同一事务回滚边界。
        if (account.getId() == null) {
            commerceMapper.insertAccount(account);
        } else {
            commerceMapper.updateAccount(account);
        }
        BigDecimal amount = amount(points);
        commerceMapper.insertPurchase(profileId, null, points, amount, now);
        return new PointResponse("\u5145\u503c\u6210\u529f", null, 0,
                amount, account.getBalance(), null);
    }

    /**
     * 购买房源推荐点数并记录购买流水。
     *
     * @param userId 用户编号
     * @param houseId 房源编号
     * @param points 积分
     */
    @Transactional
    public PointResponse buy(Long userId, Long houseId, Integer points) {
        requirePositive(points);
        Long profileId = profile(userId).id();
        requireOwnedHouse(profileId, houseId);
        // 推荐点记录加锁后检查上限并更新；事务中的后续写入失败时也会回滚点数变更。
        RecommendPoint recommendPoint = lockedPoint(houseId);
        addPoints(recommendPoint, points);
        BigDecimal amount = amount(points);
        commerceMapper.insertPurchase(profileId, houseId, points, amount, LocalDateTime.now());
        return new PointResponse("\u8d2d\u4e70\u6210\u529f", recommendPoint.getPoints(),
                weight(recommendPoint.getPoints()), amount, null, null);
    }

    /**
     * 为指定房源追加推荐点数。
     *
     * @param userId 用户编号
     * @param houseId 房源编号
     * @param points 积分
     */
    @Transactional
    public PointResponse invest(Long userId, Long houseId, Integer points) {
        requirePositive(points);
        Long profileId = profile(userId).id();
        requireOwnedHouse(profileId, houseId);
        // 对积分账户加行锁，使同一账户的并发余额更新串行执行。
        PointAccount account = commerceMapper.findAccountForUpdate(profileId);
        if (account == null) {
            throw bad("POINT_ACCOUNT_NOT_FOUND", "\u8d26\u6237\u4e0d\u5b58\u5728\uff0c\u8bf7\u5148\u5145\u503c");
        }
        if (account.getBalance() < points) {
            throw bad("INSUFFICIENT_POINTS", "\u4f59\u989d\u4e0d\u8db3\uff0c\u5f53\u524d\u4f59\u989d" + account.getBalance() + "\u70b9");
        }
        // 推荐点记录加锁后检查上限并更新；事务中的后续写入失败时也会回滚点数变更。
        RecommendPoint recommendPoint = lockedPoint(houseId);
        if (recommendPoint.getPoints() + points > MAX_HOUSE_POINTS) {
            throw maxPoints(recommendPoint.getPoints());
        }
        // 账户扣减与房源推荐点增加在同一事务内提交，避免只扣余额或只增加推荐点。
        account.setBalance(account.getBalance() - points);
        account.setTotalInvested(account.getTotalInvested() + points);
        account.setUpdatedAt(LocalDateTime.now());
        commerceMapper.updateAccount(account);
        addPoints(recommendPoint, points);
        return new PointResponse("\u6295\u653e\u6210\u529f", null,
                weight(recommendPoint.getPoints()), null, account.getBalance(),
                recommendPoint.getPoints());
    }

    /**
     * 记录房源点击并更新推荐统计。
     *
     * @param houseId 房源编号
     */
    @Transactional
    public ClickView click(Long houseId) {
        if (commerceMapper.incrementClick(houseId) == 0) {
            throw new BusinessException("HOUSE_NOT_FOUND", "\u623f\u6e90\u4e0d\u5b58\u5728", HttpStatus.NOT_FOUND);
        }
        return new ClickView(commerceMapper.findClickCount(houseId));
    }

    /**
     * 加行锁查询积分账户，避免并发更新余额。
     *
     * @param houseId 房源编号
     */
    private RecommendPoint lockedPoint(Long houseId) {
        // 对推荐记录加行锁，确保推荐点数上限的检查与更新具备原子性。
        RecommendPoint point = commerceMapper.findRecommendPointForUpdate(houseId);
        if (point != null) {
            return point;
        }
        point = new RecommendPoint();
        point.setHouseId(houseId);
        point.setPoints(0);
        point.setCreatedAt(LocalDateTime.now());
        point.setUpdatedAt(point.getCreatedAt());
        commerceMapper.insertRecommendPoint(point);
        return point;
    }

    /**
     * 增加积分账户余额并记录累计购买量。
     *
     * @param point 房源推荐点记录
     * @param points 积分
     */
    private void addPoints(RecommendPoint point, int points) {
        if (point.getPoints() + points > MAX_HOUSE_POINTS) {
            throw maxPoints(point.getPoints());
        }
        point.setPoints(point.getPoints() + points);
        point.setUpdatedAt(LocalDateTime.now());
        commerceMapper.updateRecommendPoint(point);
    }

    /**
     * 查询房源并校验当前用户是否为房东。
     *
     * @param profileId 用户资料编号
     * @param houseId 房源编号
     */
    private HouseRow requireOwnedHouse(Long profileId, Long houseId) {
        HouseRow house = houseId == null ? null : houseMapper.findById(houseId);
        if (house == null || !profileId.equals(house.landlordId())) {
            throw new BusinessException("HOUSE_NOT_FOUND", "\u623f\u6e90\u4e0d\u5b58\u5728", HttpStatus.NOT_FOUND);
        }
        return house;
    }

    /**
     * 根据认证用户编号查询对应用户资料。
     *
     * @param userId 用户编号
     */
    private UserProfile profile(Long userId) {
        UserProfile profile = userMapper.findProfileByUserId(userId);
        if (profile == null) {
            throw bad("PROFILE_NOT_FOUND", "\u7528\u6237\u8d44\u6599\u4e0d\u5b58\u5728");
        }
        return profile;
    }

    /**
     * 校验数值参数必须大于零。
     *
     * @param points 积分
     */
    private void requirePositive(Integer points) {
        if (points == null || points <= 0) {
            throw bad("INVALID_POINTS", "\u70b9\u6570\u5fc5\u987b\u5927\u4e8e 0");
        }
    }

    /**
     * 校验并转换金额参数。
     *
     * @param points 积分
     */
    private BigDecimal amount(int points) {
        return BigDecimal.valueOf(points).divide(BigDecimal.TEN, 2, RoundingMode.HALF_UP);
    }

    /**
     * 根据用户操作类型计算推荐权重。
     *
     * @param points 积分
     */
    private double weight(int points) {
        return Math.min(points / 1000.0, 0.1);
    }

    /**
     * 计算房源允许投入的最大推荐点数。
     *
     * @param current 当前页码
     */
    private BusinessException maxPoints(int current) {
        return bad("MAX_RECOMMEND_POINTS", "\u6700\u591a\u6295\u5165 100 \u70b9\uff0c\u5f53\u524d\u5df2\u6709" + current + "\u70b9");
    }

    /**
     * 创建参数错误类型的业务异常。
     *
     * @param code 业务错误码
     * @param detail 详情
     */
    private BusinessException bad(String code, String detail) {
        return new BusinessException(code, detail, HttpStatus.BAD_REQUEST);
    }
}
