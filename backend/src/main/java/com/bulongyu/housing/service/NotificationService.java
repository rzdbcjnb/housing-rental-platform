package com.bulongyu.housing.service;

import com.bulongyu.housing.dto.AnnouncementRequest;
import com.bulongyu.housing.vo.AnnouncementView;
import com.bulongyu.housing.vo.NotificationCountView;
import com.bulongyu.housing.vo.NotificationDetailView;
import com.bulongyu.housing.vo.NotificationMessageView;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bulongyu.housing.common.PageResponse;
import com.bulongyu.housing.common.BusinessException;
import com.bulongyu.housing.entity.Announcement;
import com.bulongyu.housing.entity.AnnouncementRow;
import com.bulongyu.housing.entity.NotificationMessageRow;
import com.bulongyu.housing.entity.NotificationMessage;
import com.bulongyu.housing.mapper.NotificationIdentityMapper;
import com.bulongyu.housing.mapper.NotificationMapper;
import com.bulongyu.housing.websocket.NotificationGateway;
import com.bulongyu.housing.entity.UserProfile;
import com.bulongyu.housing.mapper.UserMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * 站内通知业务服务
 */
@Service
public class NotificationService {
    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private static final Set<String> TYPES =
            Set.of("audit", "status", "system", "favorite", "new_house", "chat");
    private final NotificationMapper notificationMapper;
    private final NotificationIdentityMapper identityMapper;
    private final UserMapper userMapper;
    private final NotificationGateway gateway;

    /**
     * 初始化 {@code NotificationService} 并注入所需依赖。
     *
     * @param notificationMapper 通知数据访问组件
     * @param identityMapper 用户身份数据访问组件
     * @param userMapper 用户数据访问组件
     * @param gateway 外部能力网关
     */
    public NotificationService(NotificationMapper notificationMapper,
                               NotificationIdentityMapper identityMapper,
                               UserMapper userMapper, NotificationGateway gateway) {
        this.notificationMapper = notificationMapper;
        this.identityMapper = identityMapper;
        this.userMapper = userMapper;
        this.gateway = gateway;
    }

    /**
     * 按类型和已读状态分页查询当前用户的站内通知。
     *
     * @param userId 用户编号
     * @param type 类型
     * @param read 是否已读
     * @param requestedPage 请求页码
     * @param requestedSize 请求的每页数量
     */
    public PageResponse<NotificationMessageView> messages(Long userId, String type,
                                                          Boolean read, int requestedPage,
                                                          int requestedSize) {
        UserProfile profile = profileForUser(userId);
        int currentPage = Math.max(1, requestedPage);
        int pageSize = size(requestedSize);

        // count 与列表查询使用相同过滤条件；totalCount 不等于当前页 results 的数量。
        long totalCount = notificationMapper.countMessages(profile.id(), type, read);
        int offset = (currentPage - 1) * pageSize;
        List<NotificationMessageView> results = notificationMapper
                .findMessages(profile.id(), type, read, offset, pageSize)
                .stream()
                .map(this::messageView)
                .toList();
        return page(totalCount, currentPage, pageSize,
                "/api/notifications/messages/", results);
    }

    /**
     * 统计当前用户未读消息或通知数量。
     *
     * @param userId 用户编号
     */
    public NotificationCountView unread(Long userId) {
        return new NotificationCountView(
                notificationMapper.unreadCount(profileForUser(userId).id()));
    }

    /**
     * 将指定消息或通知标记为已读。
     *
     * @param userId 用户编号
     * @param messageId 消息编号
     */
    @Transactional
    public NotificationDetailView markRead(Long userId, Long messageId) {
        UserProfile profile = profileForUser(userId);
        if (notificationMapper.markMessageRead(messageId, profile.id()) == 0) {
            throw notFound("MESSAGE_NOT_FOUND", "\u6d88\u606f\u4e0d\u5b58\u5728");
        }
        gateway.unread(userId, notificationMapper.unreadCount(profile.id()));
        return new NotificationDetailView("\u6807\u8bb0\u6210\u529f");
    }

    /**
     * 将当前用户的全部通知标记为已读。
     *
     * @param userId 用户编号
     */
    @Transactional
    public NotificationDetailView markAllRead(Long userId) {
        UserProfile profile = profileForUser(userId);
        int count = notificationMapper.markAllRead(profile.id());
        gateway.unread(userId, 0);
        return new NotificationDetailView("\u5df2\u6807\u8bb0" + count + "\u6761\u6d88\u606f\u4e3a\u5df2\u8bfb");
    }

    /**
     * 校验权限后删除指定业务数据。
     *
     * @param userId 用户编号
     * @param messageId 消息编号
     * @return 无响应正文的 HTTP 结果
     */
    @Transactional
    public NotificationDetailView delete(Long userId, Long messageId) {
        if (notificationMapper.deleteMessage(messageId, profileForUser(userId).id()) == 0) {
            throw notFound("MESSAGE_NOT_FOUND", "\u6d88\u606f\u4e0d\u5b58\u5728");
        }
        return new NotificationDetailView("\u5220\u9664\u6210\u529f");
    }

    /**
     * 创建站内通知并通过 WebSocket 推送给在线用户。
     *
     * @param recipientProfileId recipient用户资料编号
     * @param senderProfileId sender用户资料编号
     * @param type 类型
     * @param title 标题
     * @param content 内容
     * @param relatedHouseId 关联房源编号
     */
    @Transactional
    public NotificationMessageView send(Long recipientProfileId, Long senderProfileId,
                                                String type, String title, String content,
                                                Long relatedHouseId) {
        log.info("发送站内通知，参数：recipientProfileId={}，senderProfileId={}，type={}，relatedHouseId={}",
                recipientProfileId, senderProfileId, type, relatedHouseId);
        if (!TYPES.contains(type)) {
            throw new IllegalArgumentException("Unsupported notification type: " + type);
        }
        UserProfile recipient = identityMapper.findProfileById(recipientProfileId);
        if (recipient == null) {
            throw notFound("RECIPIENT_NOT_FOUND", "\u63a5\u6536\u7528\u6237\u4e0d\u5b58\u5728");
        }
        NotificationMessage message = new NotificationMessage();
        message.setRecipientId(recipientProfileId);
        message.setSenderId(senderProfileId);
        message.setMessageType(type);
        message.setTitle(title);
        message.setContent(content);
        message.setRead(false);
        message.setRelatedHouseId(relatedHouseId);
        message.setCreateTime(LocalDateTime.now());
        // 先写入当前事务，再尝试向在线会话推送；推送异常由网关捕获，通知最终以事务提交后的数据库记录为准。
        notificationMapper.insertMessage(message);
        NotificationMessageView view = new NotificationMessageView(message.getId(),
                type, title, content, false, message.getCreateTime(), "\u7cfb\u7edf",
                relatedHouseId, null);
        gateway.push(recipient.userId(), view);
        return view;
    }

    /**
     * 分页查询系统公告。
     *
     * @param userId 用户编号
     * @param requestedPage 请求页码
     * @param requestedSize 请求的每页数量
     */
    public PageResponse<AnnouncementView> announcements(
            Long userId, int requestedPage, int requestedSize) {
        requireAdmin(userId);
        int currentPage = Math.max(1, requestedPage);
        int pageSize = size(requestedSize);
        long totalCount = notificationMapper.countAnnouncements();
        int offset = (currentPage - 1) * pageSize;
        List<AnnouncementView> results = notificationMapper
                .findAnnouncements(offset, pageSize)
                .stream()
                .map(this::announcementView)
                .toList();
        return page(totalCount, currentPage, pageSize,
                "/api/notifications/announcements/", results);
    }

    /**
     * 查询公告详情。
     *
     * @param userId 用户编号
     * @param id 编号
     */
    public AnnouncementView announcement(Long userId, Long id) {
        requireAdmin(userId);
        AnnouncementRow row = notificationMapper.findAnnouncement(id);
        if (row == null) {
            throw notFound("ANNOUNCEMENT_NOT_FOUND", "\u516c\u544a\u4e0d\u5b58\u5728");
        }
        return announcementView(row);
    }

    /**
     * 创建系统公告并通知目标用户。
     *
     * @param userId 用户编号
     * @param request 请求参数
     * @return 创建后的公告信息
     */
    @Transactional
    public AnnouncementView createAnnouncement(
            Long userId, AnnouncementRequest request) {
        UserProfile admin = requireAdmin(userId);
        validateAnnouncement(request.title(), request.content());
        LocalDateTime now = LocalDateTime.now();
        Announcement announcement = new Announcement();
        announcement.setTitle(request.title().trim());
        announcement.setContent(request.content().trim());
        announcement.setAuthorId(admin.id());
        announcement.setActive(request.isActive() == null || request.isActive());
        announcement.setCreateTime(now);
        announcement.setUpdateTime(now);
        // 公告与面向所有用户的通知记录在同一事务中写入，数据库异常时整体回滚。
        notificationMapper.insertAnnouncement(announcement);
        for (Long recipient : notificationMapper.findAllProfileIds()) {
            send(recipient, admin.id(), "system", announcement.getTitle(),
                    announcement.getContent(), null);
        }
        return announcement(userId, announcement.getId());
    }

    /**
     * 更新系统公告内容。
     *
     * @param userId 用户编号
     * @param id 编号
     * @param request 请求参数
     */
    @Transactional
    public AnnouncementView updateAnnouncement(
            Long userId, Long id, AnnouncementRequest request) {
        requireAdmin(userId);
        AnnouncementRow current = notificationMapper.findAnnouncement(id);
        if (current == null) {
            throw notFound("ANNOUNCEMENT_NOT_FOUND", "\u516c\u544a\u4e0d\u5b58\u5728");
        }
        String title = request.title() == null ? current.title() : request.title();
        String content = request.content() == null ? current.content() : request.content();
        validateAnnouncement(title, content);
        Announcement announcement = new Announcement();
        announcement.setId(id);
        announcement.setTitle(title.trim());
        announcement.setContent(content.trim());
        announcement.setActive(request.isActive() == null ? current.active() : request.isActive());
        announcement.setUpdateTime(LocalDateTime.now());
        notificationMapper.updateAnnouncement(announcement);
        return announcement(userId, id);
    }

    /**
     * 删除指定系统公告。
     *
     * @param userId 用户编号
     * @param id 编号
     */
    @Transactional
    public void deleteAnnouncement(Long userId, Long id) {
        requireAdmin(userId);
        if (notificationMapper.deleteAnnouncement(id) == 0) {
            throw notFound("ANNOUNCEMENT_NOT_FOUND", "\u516c\u544a\u4e0d\u5b58\u5728");
        }
    }

    /**
     * 批量删除当前用户的通知。
     *
     * @param userId 用户编号
     * @param ids 编号集合
     */
    @Transactional
    public NotificationDetailView batchDelete(Long userId, List<Long> ids) {
        requireAdmin(userId);
        if (ids == null || ids.isEmpty()) {
            throw new BusinessException("EMPTY_ANNOUNCEMENTS", "\u8bf7\u9009\u62e9\u8981\u5220\u9664\u7684\u516c\u544a", HttpStatus.BAD_REQUEST);
        }
        int count = notificationMapper.deleteAnnouncements(ids);
        return new NotificationDetailView("\u6210\u529f\u5220\u9664" + count + "\u6761\u516c\u544a");
    }

    /**
     * 根据用户编号查询用户资料。
     *
     * @param userId 用户编号
     */
    private UserProfile profileForUser(Long userId) {
        UserProfile profile = userMapper.findProfileByUserId(userId);
        if (profile == null) {
            throw new BusinessException("PROFILE_NOT_FOUND", "\u7528\u6237\u8d44\u6599\u4e0d\u5b58\u5728", HttpStatus.BAD_REQUEST);
        }
        return profile;
    }

    /**
     * 查询用户资料并校验管理员权限。
     *
     * @param userId 用户编号
     */
    private UserProfile requireAdmin(Long userId) {
        UserProfile profile = profileForUser(userId);
        if (!"admin".equals(profile.role())) {
            throw new BusinessException("ADMIN_REQUIRED", "\u9700\u8981\u7ba1\u7406\u5458\u6743\u9650", HttpStatus.FORBIDDEN);
        }
        return profile;
    }

    /**
     * 校验公告。
     *
     * @param title 标题
     * @param content 内容
     */
    private void validateAnnouncement(String title, String content) {
        if (title == null || title.isBlank() || content == null || content.isBlank()) {
            throw new BusinessException("INVALID_ANNOUNCEMENT", "\u516c\u544a\u6807\u9898\u548c\u5185\u5bb9\u4e0d\u80fd\u4e3a\u7a7a", HttpStatus.BAD_REQUEST);
        }
    }

    /**
     * 将消息实体转换为接口视图。
     *
     * @param row 数据库查询结果
     */
    private NotificationMessageView messageView(NotificationMessageRow row) {
        return new NotificationMessageView(row.id(), row.messageType(), row.title(),
                row.content(), Boolean.TRUE.equals(row.read()), row.createTime(), row.senderName(),
                row.relatedHouseId(), row.relatedHouseTitle());
    }

    /**
     * 将公告实体转换为接口视图。
     *
     * @param row 数据库查询结果
     */
    private AnnouncementView announcementView(AnnouncementRow row) {
        return new AnnouncementView(row.id(), row.title(), row.content(),
                row.authorName(), Boolean.TRUE.equals(row.active()), row.createTime(), row.updateTime());
    }

    /**
     * 返回集合或分页结果包含的数据数量。
     *
     * @param requested 请求的每页数量
     */
    private int size(int requested) {
        return Math.min(100, Math.max(1, requested));
    }

    /**
     * 将页码限制在有效范围内。
     *
     * @param count 数量
     * @param current 当前页码
     * @param size 每页数量
     * @param path 资源路径
     * @param results 处理结果集合
     */
    private <T> PageResponse<T> page(long totalCount, int currentPage, int pageSize,
                                     String path, List<T> results) {
        String next = (long) currentPage * pageSize < totalCount
                ? path + "?page=" + (currentPage + 1) + "&page_size=" + pageSize
                : null;
        String previous = currentPage > 1
                ? path + "?page=" + (currentPage - 1) + "&page_size=" + pageSize
                : null;
        return new PageResponse<>(totalCount, next, previous, results);
    }

    /**
     * 创建资源不存在类型的业务异常。
     *
     * @param code 业务错误码
     * @param detail 详情
     */
    private BusinessException notFound(String code, String detail) {
        return new BusinessException(code, detail, HttpStatus.NOT_FOUND);
    }
}
