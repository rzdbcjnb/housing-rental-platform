package com.bulongyu.housing.service;

import com.bulongyu.housing.vo.ChatCountView;
import com.bulongyu.housing.vo.ChatDetailView;
import com.bulongyu.housing.vo.ChatHouseShareView;
import com.bulongyu.housing.vo.ChatHouseView;
import com.bulongyu.housing.vo.ChatLastMessageView;
import com.bulongyu.housing.vo.ChatMessagePage;
import com.bulongyu.housing.vo.ChatMessageView;
import com.bulongyu.housing.vo.ChatOtherUserView;
import com.bulongyu.housing.vo.ChatRoomPage;
import com.bulongyu.housing.vo.ChatRoomView;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bulongyu.housing.entity.ChatMessage;
import com.bulongyu.housing.entity.ChatRoom;
import com.bulongyu.housing.entity.ChatMessageRow;
import com.bulongyu.housing.entity.RoomRow;
import com.bulongyu.housing.mapper.ChatIdentityMapper;
import com.bulongyu.housing.mapper.ChatMapper;
import com.bulongyu.housing.common.BusinessException;
import com.bulongyu.housing.entity.HouseRow;
import com.bulongyu.housing.mapper.HouseMapper;
import com.bulongyu.housing.entity.AuthUser;
import com.bulongyu.housing.entity.UserProfile;
import com.bulongyu.housing.mapper.UserMapper;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 实时聊天业务服务
 */
@Service
public class ChatService {
    private static final Logger log = LoggerFactory.getLogger(ChatService.class);

    private static final Set<String> MESSAGE_TYPES =
            Set.of("text", "image", "file", "system", "house_share");
    private final ChatMapper chatMapper;
    private final ChatIdentityMapper identityMapper;
    private final UserMapper userMapper;
    private final HouseMapper houseMapper;
    private final ObjectMapper objectMapper;

    /**
     * 初始化 {@code ChatService} 并注入所需依赖。
     *
     * @param chatMapper 聊天数据访问组件
     * @param identityMapper 用户身份数据访问组件
     * @param userMapper 用户数据访问组件
     * @param houseMapper 房源数据访问组件
     * @param objectMapper JSON 序列化组件
     */
    public ChatService(ChatMapper chatMapper, ChatIdentityMapper identityMapper,
                       UserMapper userMapper, HouseMapper houseMapper, ObjectMapper objectMapper) {
        this.chatMapper = chatMapper;
        this.identityMapper = identityMapper;
        this.userMapper = userMapper;
        this.houseMapper = houseMapper;
        this.objectMapper = objectMapper;
    }

    /**
     * 查询当前用户参与的聊天室列表。
     *
     * @param userId 用户编号
     * @param requestedPage 请求页码
     * @param requestedSize 请求的每页数量
     */
    public ChatRoomPage rooms(Long userId, int requestedPage, int requestedSize) {
        UserProfile profile = profileForUser(userId);
        int currentPage = Math.max(1, requestedPage);
        int pageSize = size(requestedSize);
        long totalCount = chatMapper.countRooms(profile.id());
        int offset = (currentPage - 1) * pageSize;
        List<ChatRoomView> results = chatMapper.findRooms(profile.id(), offset, pageSize)
                .stream()
                .map(this::roomView)
                .toList();

        // totalCount 描述用户参与的全部聊天室，results 仅包含 offset 对应的当前页。
        return new ChatRoomPage(totalCount, results, currentPage, pageSize);
    }

    /**
     * 查询指定聊天室详情并校验参与权限。
     *
     * @param userId 用户编号
     * @param roomId 聊天室编号
     */
    public ChatRoomView room(Long userId, Long roomId) {
        return roomView(requireRoom(roomId, profileForUser(userId).id()));
    }

    /**
     * 获取双方已有聊天室，不存在时创建聊天室。
     *
     * @param userId 用户编号
     * @param otherProfileId other用户资料编号
     * @param houseId 房源编号
     * @return 已存在或新创建的聊天室
     */
    @Transactional
    public ChatRoomView getOrCreate(Long userId, Long otherProfileId, Long houseId) {
        UserProfile current = profileForUser(userId);
        UserProfile other = identityMapper.findProfileById(otherProfileId);
        if (other == null) {
            throw notFound("USER_NOT_FOUND", "\u7528\u6237\u4e0d\u5b58\u5728");
        }
        if (current.id().equals(other.id())) {
            throw bad("SELF_CHAT", "\u4e0d\u80fd\u4e0e\u81ea\u5df1\u521b\u5efa\u804a\u5929");
        }
        // 先复用既有私聊房间，避免常规重复请求创建多余房间；仅在不存在时写入房间和参与者。
        Long existing = chatMapper.findExistingPrivateRoom(current.id(), other.id());
        if (existing != null) {
            return roomView(requireRoom(existing, current.id()));
        }
        if (houseId != null && houseMapper.findById(houseId) == null) {
            throw notFound("HOUSE_NOT_FOUND", "\u623f\u6e90\u4e0d\u5b58\u5728");
        }
        LocalDateTime now = LocalDateTime.now();
        ChatRoom room = new ChatRoom();
        room.setRoomType("private");
        room.setName("");
        room.setHouseId(houseId);
        room.setCreatedAt(now);
        room.setUpdatedAt(now);
        // 聊天室与双方参与者记录在同一事务中原子提交。
        chatMapper.insertRoom(room);
        chatMapper.insertParticipant(room.getId(), current.id());
        chatMapper.insertParticipant(room.getId(), other.id());
        return roomView(requireRoom(room.getId(), current.id()));
    }

    /**
     * 查询指定会话或聊天室的消息记录。
     *
     * @param userId 用户编号
     * @param roomId 聊天室编号
     * @param requestedPage 请求页码
     * @param requestedSize 请求的每页数量
     */
    public ChatMessagePage messages(Long userId, Long roomId,
                                    int requestedPage, int requestedSize) {
        UserProfile profile = profileForUser(userId);
        // 查询消息前再次校验参与关系，不能只依赖 WebSocket 握手时的权限结果。
        requireRoom(roomId, profile.id());
        int currentPage = Math.max(1, requestedPage);
        int pageSize = size(requestedSize);
        long totalCount = chatMapper.countMessages(roomId);
        int offset = (currentPage - 1) * pageSize;
        List<ChatMessageView> results = chatMapper.findMessages(roomId, offset, pageSize)
                .stream()
                .map(this::messageView)
                .toList();
        return new ChatMessagePage(totalCount, results, currentPage, pageSize);
    }

    /**
     * 将指定消息或通知标记为已读。
     *
     * @param userId 用户编号
     * @param roomId 聊天室编号
     */
    @Transactional
    public ChatDetailView markRead(Long userId, Long roomId) {
        UserProfile profile = profileForUser(userId);
        requireRoom(roomId, profile.id());
        chatMapper.markRead(roomId, profile.id());
        return new ChatDetailView("\u6807\u8bb0\u6210\u529f");
    }

    /**
     * 统计当前用户未读消息或通知数量。
     *
     * @param userId 用户编号
     */
    public ChatCountView unread(Long userId) {
        return new ChatCountView(chatMapper.unreadCount(profileForUser(userId).id()));
    }

    /**
     * 向聊天室发送房源分享消息。
     *
     * @param userId 用户编号
     * @param roomId 聊天室编号
     * @param houseId 房源编号
     */
    @Transactional
    public ChatHouseShareView shareHouse(Long userId, Long roomId, Long houseId) {
        UserProfile profile = profileForUser(userId);
        requireRoom(roomId, profile.id());
        HouseRow house = houseId == null ? null : houseMapper.findById(houseId);
        if (house == null) {
            throw notFound("HOUSE_NOT_FOUND", "\u623f\u6e90\u4e0d\u5b58\u5728");
        }
        Map<String, Object> data = Map.of(
                "id", house.id(), "title", house.title(), "price", house.price().toString(),
                "image", house.image() == null ? "" : house.image(),
                "rooms", house.rooms() == null ? "" : house.rooms(), "area", house.area());
        ChatMessage message = saveMessage(profile.id(), roomId, "house_share", json(data));
        return new ChatHouseShareView(message.getId(), "house_share", data);
    }

    /**
     * 校验聊天室权限并保存聊天消息。
     *
     * @param userId 用户编号
     * @param roomId 聊天室编号
     * @param messageType 消息类型
     * @param content 内容
     */
    @Transactional
    public ChatMessageView sendMessage(Long userId, Long roomId,
                                               String messageType, String content) {
        UserProfile profile = profileForUser(userId);
        requireRoom(roomId, profile.id());
        String type = messageType == null ? "text" : messageType;
        if (!MESSAGE_TYPES.contains(type) || content == null || content.isBlank()) {
            throw bad("INVALID_MESSAGE", "\u6d88\u606f\u5185\u5bb9\u6216\u7c7b\u578b\u65e0\u6548");
        }
        ChatMessage message = saveMessage(profile.id(), roomId, type, content);
        AuthUser user = userMapper.findById(userId);
        return new ChatMessageView(message.getId(), roomId, profile.id(), userId,
                user.username(), profile.avatar(), type, content, false, message.getCreatedAt());
    }

    /**
     * 在同一事务内向房东保存咨询文本和对应房源卡片。
     *
     * @param userId 当前认证用户编号
     * @param houseId 被咨询房源编号
     * @param content 用户已经确认的咨询文本
     * @return 已持久化的聊天室和两条消息
     */
    @Transactional
    public HouseInquiryResult sendHouseInquiry(Long userId, Long houseId, String content) {
        UserProfile currentProfile = profileForUser(userId);
        HouseRow house = houseId == null ? null : houseMapper.findById(houseId);
        if (house == null || !"approved".equals(house.status()) || !Boolean.TRUE.equals(house.active())) {
            throw notFound("HOUSE_NOT_FOUND", "房源不存在");
        }
        if (currentProfile.id().equals(house.landlordId())) {
            throw bad("SELF_HOUSE_INQUIRY", "不能联系自己发布的房源");
        }
        String normalizedContent = normalizeInquiryContent(content);
        ChatRoomView room = getOrCreate(userId, house.landlordId(), houseId);

        ChatMessage textMessage = saveMessage(
                currentProfile.id(), room.id(), "text", normalizedContent);
        AuthUser user = userMapper.findById(userId);
        ChatMessageView textView = new ChatMessageView(
                textMessage.getId(),
                room.id(),
                currentProfile.id(),
                userId,
                user.username(),
                currentProfile.avatar(),
                "text",
                normalizedContent,
                false,
                textMessage.getCreatedAt());

        Map<String, Object> houseData = Map.of(
                "id", house.id(),
                "title", house.title(),
                "price", house.price().toString(),
                "image", house.image() == null ? "" : house.image(),
                "rooms", house.rooms() == null ? "" : house.rooms(),
                "area", house.area());
        ChatMessage houseMessage = saveMessage(
                currentProfile.id(), room.id(), "house_share", json(houseData));
        ChatHouseShareView houseView = new ChatHouseShareView(
                houseMessage.getId(), "house_share", houseData);
        log.info("完成房源咨询消息持久化，参数：userId={}，houseId={}，roomId={}，textMessageId={}，houseShareMessageId={}",
                userId, houseId, room.id(), textMessage.getId(), houseMessage.getId());
        return new HouseInquiryResult(room.id(), textView, houseView);
    }

    /**
     * 规范化已经由用户确认的咨询文本。
     */
    private String normalizeInquiryContent(String content) {
        if (content == null || content.isBlank()) {
            throw bad("INVALID_HOUSE_INQUIRY", "咨询内容不能为空");
        }
        String normalized = content.trim();
        if (normalized.length() > 300) {
            throw bad("INVALID_HOUSE_INQUIRY", "咨询内容不能超过 300 个字符");
        }
        return normalized;
    }
    /**
     * 批量将指定聊天消息标记为已读。
     *
     * @param userId 用户编号
     * @param roomId 聊天室编号
     * @param ids 编号集合
     */
    @Transactional
    public void markReadByIds(Long userId, Long roomId, List<Long> ids) {
        UserProfile profile = profileForUser(userId);
        requireRoom(roomId, profile.id());
        if (ids != null && !ids.isEmpty()) {
            chatMapper.markReadByIds(roomId, profile.id(), ids);
        }
    }

    /**
     * 处理用户上线后的聊天状态同步。
     *
     * @param userId 用户编号
     * @param online 是否在线
     */
    @Transactional
    public void online(Long userId, boolean online) {
        UserProfile profile = profileForUser(userId);
        Long id = chatMapper.findOnlineStatusId(profile.id());
        if (id == null) {
            chatMapper.insertOnlineStatus(profile.id(), online, LocalDateTime.now());
        } else {
            chatMapper.updateOnlineStatus(id, online, LocalDateTime.now());
        }
    }

    /**
     * 判断用户是否为指定聊天室参与者。
     *
     * @param userId 用户编号
     * @param roomId 聊天室编号
     * @return 条件成立时返回 true，否则返回 false
     */
    public boolean isParticipant(Long userId, Long roomId) {
        UserProfile profile = userMapper.findProfileByUserId(userId);
        return profile != null && chatMapper.countParticipant(roomId, profile.id()) > 0;
    }

    /**
     * 创建聊天消息并更新聊天室最后活跃时间。
     *
     * @param profileId 用户资料编号
     * @param roomId 聊天室编号
     * @param type 类型
     * @param content 内容
     */
    private ChatMessage saveMessage(Long profileId, Long roomId, String type, String content) {
        ChatMessage message = new ChatMessage();
        message.setRoomId(roomId);
        message.setSenderId(profileId);
        message.setMessageType(type);
        message.setContent(content);
        message.setRead(false);
        message.setDeleted(false);
        message.setRecalled(false);
        message.setRecallReason("");
        message.setCreatedAt(LocalDateTime.now());
        // 消息写入与聊天室活跃时间更新由外层事务共同提交，避免列表时间早于消息持久化。
        chatMapper.insertMessage(message);
        chatMapper.touchRoom(roomId, message.getCreatedAt());
        log.info("完成聊天消息持久化，参数：messageId={}，roomId={}，senderProfileId={}，type={}",
                message.getId(), roomId, profileId, type);
        return message;
    }

    /**
     * 查询聊天室并校验当前用户是否为参与者。
     *
     * @param roomId 聊天室编号
     * @param profileId 用户资料编号
     */
    private RoomRow requireRoom(Long roomId, Long profileId) {
        RoomRow room = chatMapper.findRoom(roomId, profileId);
        if (room == null) {
            throw notFound("CHAT_ROOM_NOT_FOUND", "\u804a\u5929\u623f\u95f4\u4e0d\u5b58\u5728");
        }
        return room;
    }

    /**
     * 根据用户编号查询用户资料。
     *
     * @param userId 用户编号
     */
    private UserProfile profileForUser(Long userId) {
        UserProfile profile = userMapper.findProfileByUserId(userId);
        if (profile == null) {
            throw bad("PROFILE_NOT_FOUND", "\u7528\u6237\u8d44\u6599\u4e0d\u5b58\u5728");
        }
        return profile;
    }

    /**
     * 将聊天室查询结果转换为接口视图。
     *
     * @param row 数据库查询结果
     */
    private ChatRoomView roomView(RoomRow row) {
        ChatHouseView house = row.houseId() == null ? null
                : new ChatHouseView(row.houseId(), row.houseTitle(), row.houseImage());
        ChatOtherUserView other = row.otherProfileId() == null ? null
                : new ChatOtherUserView(row.otherProfileId(), row.otherUsername(),
                row.otherAvatar(), Boolean.TRUE.equals(row.otherOnline()));
        ChatLastMessageView last = row.lastMessageId() == null ? null
                : new ChatLastMessageView(row.lastMessageId(), row.lastContent(),
                row.lastMessageType(), row.lastSenderName(), row.lastMessageAt());
        return new ChatRoomView(row.id(), row.roomType(), row.name(), row.houseId(),
                house, other, last, row.unreadCount(), row.createdAt(), row.updatedAt());
    }

    /**
     * 将消息实体转换为接口视图。
     *
     * @param row 数据库查询结果
     */
    private ChatMessageView messageView(ChatMessageRow row) {
        return new ChatMessageView(row.id(), row.roomId(), row.senderId(), row.senderUserId(),
                row.senderName(), row.senderAvatar(), row.messageType(), row.content(),
                Boolean.TRUE.equals(row.read()), row.createdAt());
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
     * 将对象序列化为 JSON 文本。
     *
     * @param value 字段值
     */
    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new IllegalStateException(exception);
        }
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

    /**
     * 房源咨询事务返回的聊天室和两条已持久化消息。
     *
     * @param roomId 聊天室编号
     * @param textMessage 文本消息
     * @param houseShareMessage 房源卡片消息
     */
    public record HouseInquiryResult(
            Long roomId,
            ChatMessageView textMessage,
            ChatHouseShareView houseShareMessage) {
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
