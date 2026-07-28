package com.bulongyu.housing.controller.user;

import com.bulongyu.housing.security.CurrentUserId;

import com.bulongyu.housing.dto.CommercePaymentRequest;
import com.bulongyu.housing.dto.PointsRechargeRequest;
import com.bulongyu.housing.dto.RecommendPointsRequest;
import com.bulongyu.housing.vo.AccountView;
import com.bulongyu.housing.vo.ClickView;
import com.bulongyu.housing.vo.PaymentResponse;
import com.bulongyu.housing.vo.PointResponse;
import com.bulongyu.housing.vo.PublishLimitView;
import com.bulongyu.housing.vo.RecommendStatusView;

import com.bulongyu.housing.service.CommerceService;
import org.springframework.web.bind.annotation.*;

/**
 * 支付与推荐点接口控制器
 */
@RestController
@RequestMapping("/api/houses")
public class CommerceController {
    private final CommerceService commerceService;

    /**
     * 初始化 {@code CommerceController} 并注入所需依赖。
     *
     * @param commerceService 支付业务服务
     */
    public CommerceController(CommerceService commerceService) {
        this.commerceService = commerceService;
    }

    /**
     * 查询当前用户的房源发布额度。
     *
     * @param currentUserId 当前登录用户编号
     */
    @GetMapping("/publish-limit/")
    PublishLimitView publishLimit(@CurrentUserId Long currentUserId) {
        return commerceService.publishLimit(currentUserId);
    }

    /**
     * 模拟支付并增加房源发布额度。
     *
     * @param currentUserId 当前登录用户编号
     * @param request 请求参数
     */
    @PostMapping("/simulate-payment/")
    PaymentResponse simulatePayment(@CurrentUserId Long currentUserId,
                                                     @RequestBody(required = false)
                                                     CommercePaymentRequest request) {
        return commerceService.simulatePayment(currentUserId, request == null ? null : request.amount());
    }

    /**
     * 查询指定房源的推荐点数状态。
     *
     * @param currentUserId 当前登录用户编号
     */
    @GetMapping("/recommend-status/")
    RecommendStatusView recommendStatus(@CurrentUserId Long currentUserId) {
        return commerceService.recommendStatus(currentUserId);
    }

    /**
     * 购买房源推荐点数并原子扣减账户余额。
     *
     * @param currentUserId 当前登录用户编号
     * @param request 请求参数
     */
    @PostMapping("/buy-points/")
    PointResponse buy(@CurrentUserId Long currentUserId,
                                     @RequestBody RecommendPointsRequest request) {
        return commerceService.buy(currentUserId, request.houseId(), request.points());
    }

    /**
     * 为当前用户的积分账户充值。
     *
     * @param currentUserId 当前登录用户编号
     * @param request 请求参数
     */
    @PostMapping("/recharge-points/")
    PointResponse recharge(@CurrentUserId Long currentUserId,
                                          @RequestBody PointsRechargeRequest request) {
        return commerceService.recharge(currentUserId, request.points());
    }

    /**
     * 为指定房源追加推荐点数。
     *
     * @param currentUserId 当前登录用户编号
     * @param request 请求参数
     */
    @PostMapping("/invest-points/")
    PointResponse invest(@CurrentUserId Long currentUserId,
                                        @RequestBody RecommendPointsRequest request) {
        return commerceService.invest(currentUserId, request.houseId(), request.points());
    }

    /**
     * 查询当前用户的积分账户。
     *
     * @param currentUserId 当前登录用户编号
     */
    @GetMapping("/account-balance/")
    AccountView account(@CurrentUserId Long currentUserId) {
        return commerceService.account(currentUserId);
    }

    /**
     * 记录房源点击并更新推荐统计。
     *
     * @param houseId 房源编号
     */
    @PostMapping("/{houseId}/click/")
    ClickView click(@PathVariable Long houseId) {
        return commerceService.click(houseId);
    }
}
