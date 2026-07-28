package com.bulongyu.housing.controller.admin;

import com.bulongyu.housing.security.CurrentUserId;

import com.bulongyu.housing.dto.AdminStatusRequest;
import com.bulongyu.housing.dto.AdminUserRequest;
import com.bulongyu.housing.vo.AdminDetailView;
import com.bulongyu.housing.vo.AdminHouseView;
import com.bulongyu.housing.vo.AdminStatusView;
import com.bulongyu.housing.vo.AdminUserView;


import com.bulongyu.housing.service.AdminService;
import com.bulongyu.housing.common.PageResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 后台管理接口控制器
 */
@RestController
@RequestMapping("/api/admin")
public class AdminController {
    private final AdminService service;
    /**
     * 初始化 {@code AdminController} 并注入所需依赖。
     *
     * @param service 业务服务
     */
    public AdminController(AdminService service) { this.service = service; }

    /**
     * 汇总后台首页所需的用户、房源与交易统计。
     *
     * @param currentUserId 当前登录用户编号
     */
    @GetMapping("/dashboard/")
    public Map<String,Object> dashboard(@CurrentUserId Long currentUserId) {
        return service.dashboard(currentUserId); }
    /**
     * 按角色、状态和关键字分页查询用户。
     *
     * @param currentUserId 当前登录用户编号
     * @param role 角色
     * @param active 是否启用
     * @param keyword 搜索关键字
     * @param page 页码
     * @param size 每页数量
     */
    @GetMapping("/users/")
    public PageResponse<AdminUserView> users(@CurrentUserId Long currentUserId,
            @RequestParam(required=false) String role, @RequestParam(name="is_active",required=false) Boolean active,
            @RequestParam(required=false) String keyword, @RequestParam(defaultValue="1") int page,
            @RequestParam(name="page_size",defaultValue="10") int size) {
        return service.users(currentUserId, role, active, keyword, page, size);
    }
    /**
     * 由管理员创建平台用户。
     *
     * @param currentUserId 当前登录用户编号
     * @param request 请求参数
     * @return 创建后的用户信息
     */
    @PostMapping("/users/")
    public ResponseEntity<AdminUserView> create(@CurrentUserId Long currentUserId,
            @Valid @RequestBody AdminUserRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createUser(currentUserId, request));
    }
    /**
     * 校验权限后更新指定业务数据。
     *
     * @param currentUserId 当前登录用户编号
     * @param userId 用户编号
     * @param request 请求参数
     * @return 更新后的房源详情
     */
    @PutMapping("/users/{userId}/")
    public AdminUserView update(@CurrentUserId Long currentUserId, @PathVariable Long userId,
            @Valid @RequestBody AdminUserRequest request) {
        return service.updateUser(currentUserId, userId, request); }
    /**
     * 校验权限后删除指定业务数据。
     *
     * @param currentUserId 当前登录用户编号
     * @param userId 用户编号
     * @return 无响应正文的 HTTP 结果
     */
    @DeleteMapping("/users/{userId}/")
    public AdminDetailView delete(@CurrentUserId Long currentUserId, @PathVariable Long userId) {
        return service.disableUser(currentUserId, userId);
    }
    /**
     * 更新用户或房源的业务状态。
     *
     * @param currentUserId 当前登录用户编号
     * @param userId 用户编号
     * @param request 请求参数
     */
    @PutMapping("/users/{userId}/status/")
    public AdminStatusView status(@CurrentUserId Long currentUserId, @PathVariable Long userId,
            @RequestBody AdminStatusRequest request) {
        return service.status(currentUserId, userId, request.isActive()); }
    /**
     * 按状态和关键字分页查询待管理房源。
     *
     * @param currentUserId 当前登录用户编号
     * @param status 状态
     * @param keyword 搜索关键字
     * @param page 页码
     * @param size 每页数量
     */
    @GetMapping("/houses/")
    public PageResponse<AdminHouseView> houses(@CurrentUserId Long currentUserId,
            @RequestParam(required=false) String status, @RequestParam(required=false) String keyword,
            @RequestParam(defaultValue="1") int page, @RequestParam(name="page_size",defaultValue="10") int size) {
        return service.houses(currentUserId, status, keyword, page, size);
    }}
