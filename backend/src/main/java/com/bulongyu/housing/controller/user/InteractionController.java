package com.bulongyu.housing.controller.user;

import com.bulongyu.housing.security.CurrentUserId;

import com.bulongyu.housing.dto.HouseIdRequest;
import com.bulongyu.housing.vo.FavoriteStatus;
import com.bulongyu.housing.vo.InteractionCreatedView;
import com.bulongyu.housing.vo.InteractionDetailView;
import com.bulongyu.housing.vo.InteractionItemView;

import com.bulongyu.housing.common.PageResponse;
import com.bulongyu.housing.service.InteractionService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 收藏与浏览历史接口控制器
 */
@RestController
@RequestMapping("/api/houses")
public class InteractionController {
    private final InteractionService interactionService;

    /**
     * 初始化 {@code InteractionController} 并注入所需依赖。
     *
     * @param interactionService 收藏与浏览历史业务服务
     */
    public InteractionController(InteractionService interactionService) {
        this.interactionService = interactionService;
    }

    /**
     * 收藏指定房源并避免重复记录。
     *
     * @param currentUserId 当前登录用户编号
     * @param request 请求参数
     */
    @PostMapping("/favorites/add/")
    ResponseEntity<InteractionCreatedView> addFavorite(
            @CurrentUserId Long currentUserId, @RequestBody HouseIdRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(interactionService.addFavorite(currentUserId, request.house()));
    }

    /**
     * 分页查询当前用户收藏的房源。
     *
     * @param currentUserId 当前登录用户编号
     * @param page 页码
     * @param pageSize 页码每页数量
     */
    @GetMapping("/favorites/")
    PageResponse<InteractionItemView> favorites(
            @CurrentUserId Long currentUserId, @RequestParam(defaultValue = "1") int page,
            @RequestParam(name = "page_size", defaultValue = "10") int pageSize) {
        return interactionService.favorites(currentUserId, page, pageSize);
    }

    /**
     * 取消收藏指定房源。
     *
     * @param currentUserId 当前登录用户编号
     * @param id 编号
     */
    @DeleteMapping("/favorites/{id}/remove/")
    InteractionDetailView removeFavorite(@CurrentUserId Long currentUserId,
                                                 @PathVariable Long id) {
        return interactionService.removeFavorite(currentUserId, id);
    }

    /**
     * 查询当前用户是否已收藏指定房源。
     *
     * @param currentUserId 当前登录用户编号
     * @param houseId 房源编号
     */
    @GetMapping("/{houseId}/is_favorited/")
    FavoriteStatus favoriteStatus(@CurrentUserId Long currentUserId,
                                                     @PathVariable Long houseId) {
        return interactionService.favoriteStatus(currentUserId, houseId);
    }

    /**
     * 新增或刷新房源浏览历史。
     *
     * @param currentUserId 当前登录用户编号
     * @param request 请求参数
     */
    @PostMapping("/browse-history/add/")
    ResponseEntity<InteractionCreatedView> addHistory(
            @CurrentUserId Long currentUserId, @RequestBody HouseIdRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(interactionService.addHistory(currentUserId, request.house()));
    }

    /**
     * 分页查询当前用户的房源浏览历史。
     *
     * @param currentUserId 当前登录用户编号
     * @param page 页码
     * @param pageSize 页码每页数量
     */
    @GetMapping("/browse-history/")
    PageResponse<InteractionItemView> history(
            @CurrentUserId Long currentUserId, @RequestParam(defaultValue = "1") int page,
            @RequestParam(name = "page_size", defaultValue = "10") int pageSize) {
        return interactionService.history(currentUserId, page, pageSize);
    }

    /**
     * 删除指定房源的浏览历史。
     *
     * @param currentUserId 当前登录用户编号
     * @param id 编号
     */
    @DeleteMapping("/browse-history/{id}/")
    InteractionDetailView removeHistory(@CurrentUserId Long currentUserId,
                                                @PathVariable Long id) {
        return interactionService.removeHistory(currentUserId, id);
    }

    /**
     * 清空当前用户的全部浏览历史。
     *
     * @param currentUserId 当前登录用户编号
     */
    @DeleteMapping("/browse-history/clear/")
    InteractionDetailView clearHistory(@CurrentUserId Long currentUserId) {
        return interactionService.clearHistory(currentUserId);
    }
}
