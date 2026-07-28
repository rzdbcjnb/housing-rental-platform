package com.bulongyu.housing.service;

import com.bulongyu.housing.vo.PublishLimitView;


import com.bulongyu.housing.entity.PaymentRecord;
import com.bulongyu.housing.mapper.CommerceMapper;
import com.bulongyu.housing.common.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * 支付与推荐点业务服务
 */
@Service
public class PublishingService {
    private static final int FREE_LIMIT = 3;
    private final CommerceMapper commerceMapper;

    /**
     * 初始化 {@code PublishingService} 并注入所需依赖。
     *
     * @param commerceMapper 支付数据访问组件
     */
    public PublishingService(CommerceMapper commerceMapper) {
        this.commerceMapper = commerceMapper;
    }

    /**
     * 将每页数量限制在允许范围内。
     *
     * @param profileId 用户资料编号
     */
    public PublishLimitView limit(Long profileId) {
        long count = commerceMapper.countHouses(profileId);
        return new PublishLimitView(count >= FREE_LIMIT,
                Math.max(0, FREE_LIMIT - count), count);
    }

    /**
     * 预占一次房源发布资格。
     *
     * @param profileId 用户资料编号
     */
    public Long reserveForCreate(Long profileId) {
        if (commerceMapper.countHouses(profileId) < FREE_LIMIT) {
            return null;
        }
        PaymentRecord record = commerceMapper.findUnusedPaymentForUpdate(profileId);
        if (record == null) {
            throw new BusinessException("PUBLISH_PAYMENT_REQUIRED",
                    "\u514d\u8d39\u53d1\u5e03\u6b21\u6570\u5df2\u7528\u5b8c\uff0c\u8bf7\u5148\u4ed8\u8d39",
                    HttpStatus.PAYMENT_REQUIRED);
        }
        return record.getId();
    }

    /**
     * 核销已预占的房源发布资格。
     *
     * @param paymentId payment编号
     * @param houseId 房源编号
     */
    public void consume(Long paymentId, Long houseId) {
        if (paymentId != null && commerceMapper.linkPayment(paymentId, houseId) != 1) {
            throw new BusinessException("PUBLISH_PAYMENT_CONFLICT",
                    "\u53d1\u5e03\u652f\u4ed8\u8bb0\u5f55\u5df2\u88ab\u4f7f\u7528", HttpStatus.CONFLICT);
        }
    }

    /**
     * 房源审核拒绝后退还发布资格。
     *
     * @param houseId 房源编号
     */
    public void refundForRejectedHouse(Long houseId) {
        commerceMapper.deletePaymentForHouse(houseId);
    }
}
