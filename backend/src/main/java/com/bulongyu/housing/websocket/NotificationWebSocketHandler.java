package com.bulongyu.housing.websocket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bulongyu.housing.service.NotificationService;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 站内通知 WebSocket 消息处理器
 */
@Component
public class NotificationWebSocketHandler extends TextWebSocketHandler {
    private static final Logger log = LoggerFactory.getLogger(NotificationWebSocketHandler.class);

    private final NotificationGateway gateway;
    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;

    /**
     * 初始化 {@code NotificationWebSocketHandler} 并注入所需依赖。
     *
     * @param gateway 外部能力网关
     * @param notificationService 通知业务服务
     * @param objectMapper JSON 序列化组件
     */
    public NotificationWebSocketHandler(NotificationGateway gateway,
                                        NotificationService notificationService,
                                        ObjectMapper objectMapper) {
        this.gateway = gateway;
        this.notificationService = notificationService;
        this.objectMapper = objectMapper;
    }

    /**
     * 注册新建立的 WebSocket 会话。
     *
     * @param session WebSocket 会话
     */
    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        log.info("建立通知WebSocket连接，参数：sessionId={}", session.getId());
        Long userId = userId(session);
        gateway.add(userId, session);
        gateway.unread(userId, notificationService.unread(userId).count());
    }

    /**
     * 解析并处理 WebSocket 文本消息。
     *
     * @param session WebSocket 会话
     * @param message 消息
     */
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        log.info("处理通知WebSocket消息，参数：sessionId={}，payloadLength={}",
                session.getId(), message.getPayloadLength());
        JsonNode data = objectMapper.readTree(message.getPayload());
        Long userId = userId(session);
        switch (data.path("action").asText()) {
            case "mark_read" -> notificationService.markRead(userId, data.path("message_id").asLong());
            case "mark_all_read" -> notificationService.markAllRead(userId);
            case "get_unread_count" -> gateway.unread(userId,
                    notificationService.unread(userId).count());
            default -> log.warn("未知通知WebSocket动作，参数：sessionId={}，action={}",
                    session.getId(), data.path("action").asText());
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
        log.info("关闭通知WebSocket连接，参数：sessionId={}，status={}", session.getId(), status);
        gateway.remove(userId(session), session);
    }

    /**
     * 从当前认证令牌中读取用户编号。
     *
     * @param session WebSocket 会话
     * @return 当前登录用户编号
     */
    private Long userId(WebSocketSession session) {
        return (Long) session.getAttributes().get(NotificationHandshakeInterceptor.USER_ID);
    }
}
