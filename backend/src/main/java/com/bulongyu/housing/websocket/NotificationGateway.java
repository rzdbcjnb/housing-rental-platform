package com.bulongyu.housing.websocket;

import com.bulongyu.housing.vo.NotificationMessageView;


import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 站内通知外部能力访问网关
 */
@Component
public class NotificationGateway {
    private final Map<Long, Set<WebSocketSession>> sessions = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;

    /**
     * 初始化 {@code NotificationGateway} 并注入所需依赖。
     *
     * @param objectMapper JSON 序列化组件
     */
    public NotificationGateway(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 注册用户的 WebSocket 会话。
     *
     * @param userId 用户编号
     * @param session WebSocket 会话
     */
    public void add(Long userId, WebSocketSession session) {
        sessions.computeIfAbsent(userId, ignored -> ConcurrentHashMap.newKeySet()).add(session);
    }

    /**
     * 移除用户已经关闭的 WebSocket 会话。
     *
     * @param userId 用户编号
     * @param session WebSocket 会话
     */
    public void remove(Long userId, WebSocketSession session) {
        Set<WebSocketSession> userSessions = sessions.get(userId);
        if (userSessions != null) {
            userSessions.remove(session);
            if (userSessions.isEmpty()) {
                sessions.remove(userId);
            }
        }
    }

    /**
     * 向指定用户推送通知并返回未读数量。
     *
     * @param userId 用户编号
     * @param message 消息
     */
    public void push(Long userId, NotificationMessageView message) {
        sendToUser(userId, Map.of("type", "new_message", "message", message));
    }

    /**
     * 统计当前用户未读消息或通知数量。
     *
     * @param userId 用户编号
     * @param count 数量
     */
    public void unread(Long userId, long count) {
        sendToUser(userId, Map.of("type", "unread_count", "count", count));
    }

    /**
     * 向指定在线用户推送 WebSocket 消息。
     *
     * @param userId 用户编号
     * @param payload 消息载荷
     */
    private void sendToUser(Long userId, Object payload) {
        Set<WebSocketSession> userSessions = sessions.get(userId);
        if (userSessions == null) {
            return;
        }
        for (WebSocketSession session : userSessions) {
            if (session.isOpen()) {
                try {
                    synchronized (session) {
                        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(payload)));
                    }
                } catch (IOException ignored) {
                }
            }
        }
    }
}
