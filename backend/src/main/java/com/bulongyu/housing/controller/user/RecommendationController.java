package com.bulongyu.housing.controller.user;

import com.bulongyu.housing.security.CurrentUserId;

import com.bulongyu.housing.vo.HouseListView;


import com.bulongyu.housing.service.RecommendationService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 房源推荐接口控制器
 */
@RestController
@RequestMapping("/api/houses")
public class RecommendationController {
    private final RecommendationService service;
    /**
     * 初始化 {@code RecommendationController} 并注入所需依赖。
     *
     * @param service 业务服务
     */
    public RecommendationController(RecommendationService service) { this.service = service; }

    /**
     * 查询与指定房源相似的推荐房源。
     *
     * @param houseId 房源编号
     * @param currentUserId 当前登录用户编号
     * @param limit 返回数量上限
     */
    @GetMapping("/{houseId}/recommend/")
    public List<HouseListView> similar(@PathVariable Long houseId,
            @CurrentUserId(required = false) Long currentUserId,
            @RequestParam(defaultValue="10") int limit) {
        return service.similar(houseId, currentUserId, limit);
    }

    /**
     * 根据用户行为与偏好生成个性化房源推荐。
     *
     * @param currentUserId 当前登录用户编号
     * @param limit 返回数量上限
     */
    @GetMapping("/user-recommend/")
    public List<HouseListView> forUser(@CurrentUserId Long currentUserId,
                                                   @RequestParam(defaultValue="10") int limit) {
        return service.forUser(currentUserId, limit);
    }
}
