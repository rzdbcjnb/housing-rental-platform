package com.bulongyu.housing.controller.user;

import com.bulongyu.housing.security.CurrentUserId;

import com.bulongyu.housing.dto.HouseUpsertRequest;
import com.bulongyu.housing.vo.HouseDetailView;
import com.bulongyu.housing.vo.HouseListView;

import com.bulongyu.housing.common.PageResponse;
import com.bulongyu.housing.entity.HouseQuery;
import com.bulongyu.housing.service.HouseService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

/**
 * 房源接口控制器
 */
@RestController
@RequestMapping("/api/houses")
public class HouseController {
    private final HouseService houseService;

    /**
     * 初始化 {@code HouseController} 并注入房源业务服务。
     *
     * @param houseService 房源业务服务
     */
    public HouseController(HouseService houseService) {
        this.houseService = houseService;
    }

    /**
     * 根据筛选条件分页查询已发布房源。
     *
     * @param keyword 搜索关键字
     * @param city 城市
     * @param district 区县
     * @param street 街道
     * @param priceMin 最低租金
     * @param priceMax 最高租金
     * @param rooms 户型描述
     * @param areaMin 最小面积
     * @param areaMax 最大面积
     * @param houseType 房源类型
     * @param bedroomMin 最少卧室数量
     * @param livingRoomMin 最少客厅数量
     * @param bathroomMin 最少卫生间数量
     * @param kitchenMin 最少厨房数量
     * @param page 页码
     * @param pageSize 页码每页数量
     * @return 房源分页结果
     */
    @GetMapping("/")
    PageResponse<HouseListView> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String district,
            @RequestParam(required = false) String street,
            @RequestParam(name = "price_min", required = false) BigDecimal priceMin,
            @RequestParam(name = "price_max", required = false) BigDecimal priceMax,
            @RequestParam(required = false) String rooms,
            @RequestParam(name = "area_min", required = false) Integer areaMin,
            @RequestParam(name = "area_max", required = false) Integer areaMax,
            @RequestParam(name = "house_type", required = false) String houseType,
            @RequestParam(name = "bedroom_min", required = false) Integer bedroomMin,
            @RequestParam(name = "living_room_min", required = false) Integer livingRoomMin,
            @RequestParam(name = "bathroom_min", required = false) Integer bathroomMin,
            @RequestParam(name = "kitchen_min", required = false) Integer kitchenMin,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(name = "page_size", defaultValue = "10") int pageSize) {
        HouseQuery query = new HouseQuery(keyword, city, district, street, priceMin, priceMax,
                rooms, areaMin, areaMax, houseType, bedroomMin, livingRoomMin, bathroomMin, kitchenMin);
        return houseService.list(query, page, pageSize);
    }

    /**
     * 查询房源详情并补充房东与户型信息。
     *
     * @param id 编号
     * @param currentUserId 当前登录用户编号
     * @return 房源详情
     */
    @GetMapping("/{id}/")
    HouseDetailView detail(@PathVariable Long id,
                           @CurrentUserId(required = false) Long currentUserId) {
        return houseService.detail(id, currentUserId);
    }

    /**
     * 分页查询当前房东发布的房源。
     *
     * @param currentUserId 当前登录用户编号
     * @param keyword 搜索关键字
     * @param page 页码
     * @param pageSize 页码每页数量
     * @return 当前房东的房源分页结果
     */
    @GetMapping("/my/")
    PageResponse<HouseListView> myHouses(
            @CurrentUserId Long currentUserId,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(name = "page_size", defaultValue = "10") int pageSize) {
        return houseService.myHouses(currentUserId, keyword, page, pageSize);
    }

    /**
     * 发布新房源。
     *
     * @param currentUserId 当前登录用户编号
     * @param request 请求参数
     * @return 新建房源的详情
     */
    @PostMapping("/")
    ResponseEntity<HouseDetailView> create(
            @CurrentUserId Long currentUserId,
            @Valid @RequestBody HouseUpsertRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(houseService.create(currentUserId, request));
    }

    /**
     * 完整更新当前房东的房源信息。
     *
     * @param currentUserId 当前登录用户编号
     * @param id 编号
     * @param request 请求参数
     * @return 更新后的房源详情
     */
    @PutMapping("/{id}/")
    HouseDetailView update(@CurrentUserId Long currentUserId, @PathVariable Long id,
                                       @Valid @RequestBody HouseUpsertRequest request) {
        return houseService.update(currentUserId, id, request);
    }

    /**
     * 按请求中提供的字段局部更新房源。
     *
     * @param currentUserId 当前登录用户编号
     * @param id 编号
     * @param request 请求参数
     * @return 更新后的房源详情
     */
    @PatchMapping("/{id}/")
    HouseDetailView patch(@CurrentUserId Long currentUserId, @PathVariable Long id,
                                      @Valid @RequestBody HouseUpsertRequest request) {
        return houseService.update(currentUserId, id, request);
    }

    /**
     * 删除当前房东发布的房源。
     *
     * @param currentUserId 当前登录用户编号
     * @param id 编号
     * @return 无响应正文的 HTTP 结果
     */
    @DeleteMapping("/{id}/")
    ResponseEntity<Void> delete(@CurrentUserId Long currentUserId, @PathVariable Long id) {
        houseService.delete(currentUserId, id);
        return ResponseEntity.noContent().build();
    }
}
