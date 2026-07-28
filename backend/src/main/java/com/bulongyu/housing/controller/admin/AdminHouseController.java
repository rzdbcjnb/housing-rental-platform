package com.bulongyu.housing.controller.admin;

import com.bulongyu.housing.security.CurrentUserId;

import com.bulongyu.housing.dto.HouseAuditRequest;
import com.bulongyu.housing.vo.HouseDetailResponse;
import com.bulongyu.housing.vo.HouseDetailView;

import com.bulongyu.housing.service.HouseService;
import org.springframework.web.bind.annotation.*;

/**
 * 房源接口控制器
 */
@RestController
@RequestMapping("/api/admin/houses")
public class AdminHouseController {
    private final HouseService houseService;

    /**
     * 初始化 {@code AdminHouseController} 并注入所需依赖。
     *
     * @param houseService 房源业务服务
     */
    public AdminHouseController(HouseService houseService) {
        this.houseService = houseService;
    }

    /**
     * 查询房源详情并补充房东与户型信息。
     *
     * @param currentUserId 当前登录用户编号
     * @param id 编号
     * @return 房源详情
     */
    @GetMapping("/{id}/")
    HouseDetailView detail(@CurrentUserId Long currentUserId, @PathVariable Long id) {
        return houseService.detail(id, currentUserId);
    }

    /**
     * 审核房源并同步更新发布状态。
     *
     * @param currentUserId 当前登录用户编号
     * @param id 编号
     * @param request 请求参数
     */
    @PutMapping("/{id}/audit/")
    HouseDetailResponse audit(@CurrentUserId Long currentUserId, @PathVariable Long id,
                                     @RequestBody HouseAuditRequest request) {
        return houseService.audit(currentUserId, id, request.action());
    }
}
