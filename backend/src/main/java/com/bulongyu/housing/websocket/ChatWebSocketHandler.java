package com.bulongyu.housing.websocket;

import com.bulongyu.housing.vo.ChatHouseShareView;
import com.bulongyu.housing.vo.ChatMessageView;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bulongyu.housing.service.ChatService;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 实时聊天 WebSocket 消息处理器
 */
@Component
public class ChatWebSocketHandler extends TextWebSocketHandler {
    private static final Logger log = LoggerFactory.getLogger(ChatWebSocketHandler.class);

    private final Map<Long, Set<WebSocketSession>> roomSessions = new ConcurrentHashMap<>();
    private final Map<Long, Set<String>> userSessions = new ConcurrentHashMap<>();
    private final ChatService chatService;
    private final ObjectMapper objectMapper;

    /**
     * 初始化 {@code ChatWebSocketHandler} 并注入所需依赖。
     *
     * @param chatService 聊天业务服务
     * @param objectMapper JSON 序列化组件
     */
    public ChatWebSocketHandler(ChatService chatService, ObjectMapper objectMapper) {
        this.chatService = chatService;
        this.objectMapper = objectMapper;
    }

    /**
     * 注册新建立的 WebSocket 会话。
     *
     * @param session WebSocket 会话
     */
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        log.info("建立聊天WebSocket连接，参数：sessionId={}", session.getId());
        // 1. 用户和房间编号只读取握手拦截器写入的受信属性，不接受客户端消息覆盖身份。
        Long userId = userId(session);
        Long roomId = roomId(session);
        // 2. 同时登记房间会话和用户会话，用于房间广播及多端在线状态判断。
        roomSessions.computeIfAbsent(roomId, ignored -> ConcurrentHashMap.newKeySet()).add(session);
        userSessions.computeIfAbsent(userId, ignored -> ConcurrentHashMap.newKeySet()).add(session.getId());
        // 3. 连接登记完成后再更新在线状态，并只向其他会话广播上线事件。
        chatService.online(userId, true);
        send(session, Map.of("type", "room_info", "room", chatService.room(userId, roomId)));
        broadcast(roomId, Map.of("type", "online_status", "user_id", userId,
                "is_online", true), session.getId());
    }

    /**
     * 解析并处理 WebSocket 文本消息。
     *
     * @param session WebSocket 会话
     * @param message 消息
     */
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        log.info("处理聊天WebSocket消息，参数：sessionId={}，payloadLength={}",
                session.getId(), message.getPayloadLength());
        // 消息动作字段仅负责路由；权限和持久化仍由聊天服务在每次操作中复核。
        JsonNode data = objectMapper.readTree(message.getPayload());
        String action = data.path("action").asText();
        switch (action) {
            case "send_message" -> sendMessage(session, data);
            case "mark_read" -> markRead(session, data);
            case "typing" -> broadcast(roomId(session), Map.of(
                    "type", "typing_status", "user_id", userId(session),
                    "is_typing", data.path("is_typing").asBoolean(false)), session.getId());
            case "get_history" -> history(session, data);
            default -> log.warn("未知聊天WebSocket动作，参数：sessionId={}，action={}",
                    session.getId(), action);
        }
    }

    /**
     * 移除已经关闭的 WebSocket 会话。
     *
     * @param session WebSocket 会话
     * @param status 状态
     */
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        log.info("关闭聊天WebSocket连接，参数：sessionId={}，status={}", session.getId(), status);
        // 断开事件继续使用握手阶段保存的身份，确保清理的是当前连接所属用户和房间。
        Long userId = userId(session);
        Long roomId = roomId(session);
        // 1. 先从房间广播集合移除连接，避免关闭中的会话继续接收消息。
        remove(roomSessions.get(roomId), session);
        Set<String> sessions = userSessions.get(userId);
        if (sessions != null) {
            sessions.remove(session.getId());
            // 2. 同一用户可能有多个终端连接，只有最后一个连接断开才标记离线。
            if (sessions.isEmpty()) {
                userSessions.remove(userId);
                chatService.online(userId, false);
                broadcast(roomId, Map.of("type", "online_status", "user_id", userId,
                        "is_online", false), session.getId());
            }
        }
    }

    /**
     * 校验聊天室权限并保存聊天消息。
     *
     * @param session WebSocket 会话
     * @param data 业务数据
     */
    private void sendMessage(WebSocketSession session, JsonNode data) {
        // 先由 Service 校验参与权限并持久化，成功后再广播，保证广播消息可被历史记录恢复。
        ChatMessageView message = chatService.sendMessage(userId(session), roomId(session),
                data.path("message_type").asText("text"), data.path("content").asText());
        broadcast(roomId(session), Map.of("type", "new_message", "message", message), null);
    }

    /**
     * 将指定消息或通知标记为已读。
     *
     * @param session WebSocket 会话
     * @param data 业务数据
     */
    private void markRead(WebSocketSession session, JsonNode data) {
        List<Long> ids = new ArrayList<>();
        data.path("message_ids").forEach(value -> ids.add(value.asLong()));
        // 已读状态落库成功后再通知其他会话，广播只用于状态同步，不作为事实来源。
        chatService.markReadByIds(userId(session), roomId(session), ids);
        broadcast(roomId(session), Map.of("type", "messages_read", "message_ids", ids),
                session.getId());
    }

    /**
     * 查询当前聊天室的历史消息并按时间正序返回。
     *
     * @param session WebSocket 会话
     * @param data 业务数据
     */
    private void history(WebSocketSession session, JsonNode data) throws IOException {
        int currentPage = Math.max(1, data.path("page").asInt(1));
        int pageSize = Math.min(100, Math.max(1, data.path("page_size").asInt(20)));
        List<ChatMessageView> messages = new ArrayList<>(
                chatService.messages(userId(session), roomId(session), currentPage, pageSize).results());
        Collections.reverse(messages);
        send(session, Map.of("type", "history_messages", "messages", messages, "page", currentPage));
    }

    /**
     * 向聊天室参与者广播房源分享消息。
     *
     * @param roomId 聊天室编号
     * @param message 消息
     */
    public void broadcastHouseShare(Long roomId, ChatHouseShareView message) {
        broadcast(roomId, Map.of("type", "new_message", "message", message), null);
    }

    /**
     * 按数据库写入顺序广播房源咨询文本和房源卡片。
     *
     * @param inquiry 已持久化的房源咨询结果
     */
    public void broadcastHouseInquiry(ChatService.HouseInquiryResult inquiry) {
        broadcast(inquiry.roomId(), Map.of(
                "type", "new_message",
                "message", inquiry.textMessage()), null);
        broadcast(inquiry.roomId(), Map.of(
                "type", "new_message",
                "message", inquiry.houseShareMessage()), null);
    }
    /**
     * 向聊天室内的在线参与者广播消息。
     *
     * @param roomId 聊天室编号
     * @param payload 消息载荷
     * @param excludedSessionId excludedSession编号
     */
    private void broadcast(Long roomId, Object payload, String excludedSessionId) {
        Set<WebSocketSession> sessions = roomSessions.get(roomId);
        if (sessions == null) {
            return;
        }
        for (WebSocketSession session : sessions) {
            if (session.isOpen() && !session.getId().equals(excludedSessionId)) {
                try {
                    send(session, payload);
                } catch (IOException exception) {
                    log.warn("广播聊天消息失败，参数：sessionId={}，roomId={}",
                            session.getId(), roomId, exception);
                }
            }
        }
    }

    /**
     * 创建站内通知并通过 WebSocket 推送给在线用户。
     *
     * @param session WebSocket 会话
     * @param payload 消息载荷
     */
    private void send(WebSocketSession session, Object payload) throws IOException {
        // 底层会话不保证并发发送安全，因此按会话串行化写操作。
        synchronized (session) {
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(payload)));
        }
    }

    /**
     * 从当前认证令牌中读取用户编号。
     *
     * @param session WebSocket 会话
     * @return 当前登录用户编号
     */
    private Long userId(WebSocketSession session) {
        return (Long) session.getAttributes().get(ChatHandshakeInterceptor.USER_ID);
    }

    /**
     * 从 WebSocket 会话属性中读取聊天室编号。
     *
     * @param session WebSocket 会话
     */
    private Long roomId(WebSocketSession session) {
        return (Long) session.getAttributes().get(ChatHandshakeInterceptor.ROOM_ID);
    }

    /**
     * 移除用户已经关闭的 WebSocket 会话。
     *
     * @param sessions WebSocket 会话集合
     * @param session WebSocket 会话
     */
    private void remove(Set<WebSocketSession> sessions, WebSocketSession session) {
        if (sessions != null) {
            sessions.remove(session);
        }
    }
}
