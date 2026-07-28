package com.bulongyu.housing.controller.user;

import com.bulongyu.housing.security.CurrentUserId;

import com.bulongyu.housing.dto.AnnouncementRequest;
import com.bulongyu.housing.dto.NotificationBatchDeleteRequest;
import com.bulongyu.housing.vo.AnnouncementView;
import com.bulongyu.housing.vo.NotificationCountView;
import com.bulongyu.housing.vo.NotificationDetailView;
import com.bulongyu.housing.vo.NotificationMessageView;

import com.bulongyu.housing.common.PageResponse;
import com.bulongyu.housing.service.NotificationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 站内通知接口控制器
 */
@RestController
@RequestMapping("/api/notifications")
public class NotificationController {
    private final NotificationService notificationService;

    /**
     * 初始化 {@code NotificationController} 并注入所需依赖。
     *
     * @param notificationService 通知业务服务
     */
    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    /**
     * 查询指定会话或聊天室的消息记录。
     *
     * @param currentUserId 当前登录用户编号
     * @param type 类型
     * @param read 是否已读
     * @param page 页码
     * @param pageSize 页码每页数量
     */
    @GetMapping("/messages/")
    PageResponse<NotificationMessageView> messages(
            @CurrentUserId Long currentUserId,
            @RequestParam(name = "type", required = false) String type,
            @RequestParam(name = "is_read", required = false) Boolean read,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(name = "page_size", defaultValue = "10") int pageSize) {
        return notificationService.messages(currentUserId, type, read, page, pageSize);
    }

    /**
     * 统计当前用户未读消息或通知数量。
     *
     * @param currentUserId 当前登录用户编号
     */
    @GetMapping("/unread-count/")
    NotificationCountView unread(@CurrentUserId Long currentUserId) {
        return notificationService.unread(currentUserId);
    }

    /**
     * 将指定消息或通知标记为已读。
     *
     * @param currentUserId 当前登录用户编号
     * @param id 编号
     */
    @PutMapping("/messages/{id}/read/")
    NotificationDetailView markRead(@CurrentUserId Long currentUserId, @PathVariable Long id) {
        return notificationService.markRead(currentUserId, id);
    }

    /**
     * 将当前用户的全部通知标记为已读。
     *
     * @param currentUserId 当前登录用户编号
     */
    @PutMapping("/messages/read-all/")
    NotificationDetailView markAllRead(@CurrentUserId Long currentUserId) {
        return notificationService.markAllRead(currentUserId);
    }

    /**
     * 校验权限后删除指定业务数据。
     *
     * @param currentUserId 当前登录用户编号
     * @param id 编号
     * @return 无响应正文的 HTTP 结果
     */
    @DeleteMapping("/messages/{id}/")
    NotificationDetailView delete(@CurrentUserId Long currentUserId, @PathVariable Long id) {
        return notificationService.delete(currentUserId, id);
    }

    /**
     * 分页查询系统公告。
     *
     * @param currentUserId 当前登录用户编号
     * @param page 页码
     * @param pageSize 页码每页数量
     */
    @GetMapping("/announcements/")
    PageResponse<AnnouncementView> announcements(
            @CurrentUserId Long currentUserId, @RequestParam(defaultValue = "1") int page,
            @RequestParam(name = "page_size", defaultValue = "10") int pageSize) {
        return notificationService.announcements(currentUserId, page, pageSize);
    }

    /**
     * 创建系统公告并通知目标用户。
     *
     * @param currentUserId 当前登录用户编号
     * @param request 请求参数
     * @return 创建后的公告信息
     */
    @PostMapping("/announcements/")
    ResponseEntity<AnnouncementView> createAnnouncement(
            @CurrentUserId Long currentUserId,
            @RequestBody AnnouncementRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(notificationService.createAnnouncement(currentUserId, request));
    }

    /**
     * 查询公告详情。
     *
     * @param currentUserId 当前登录用户编号
     * @param id 编号
     */
    @GetMapping("/announcements/{id}/")
    AnnouncementView announcement(@CurrentUserId Long currentUserId,
                                                      @PathVariable Long id) {
        return notificationService.announcement(currentUserId, id);
    }

    /**
     * 更新系统公告内容。
     *
     * @param currentUserId 当前登录用户编号
     * @param id 编号
     * @param request 请求参数
     */
    @PutMapping("/announcements/{id}/")
    AnnouncementView updateAnnouncement(
            @CurrentUserId Long currentUserId, @PathVariable Long id,
            @RequestBody AnnouncementRequest request) {
        return notificationService.updateAnnouncement(currentUserId, id, request);
    }

    /**
     * 删除指定系统公告。
     *
     * @param currentUserId 当前登录用户编号
     * @param id 编号
     */
    @DeleteMapping("/announcements/{id}/")
    ResponseEntity<Void> deleteAnnouncement(@CurrentUserId Long currentUserId,
                                            @PathVariable Long id) {
        notificationService.deleteAnnouncement(currentUserId, id);
        return ResponseEntity.noContent().build();
    }

    /**
     * 批量删除当前用户的通知。
     *
     * @param currentUserId 当前登录用户编号
     * @param request 请求参数
     */
    @PostMapping("/announcements/batch-delete/")
    NotificationDetailView batchDelete(
            @CurrentUserId Long currentUserId,
            @RequestBody NotificationBatchDeleteRequest request) {
        return notificationService.batchDelete(currentUserId, request.ids());
    }
}
