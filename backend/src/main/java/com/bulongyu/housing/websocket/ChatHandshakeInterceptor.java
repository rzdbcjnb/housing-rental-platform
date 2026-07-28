package com.bulongyu.housing.websocket;


import com.bulongyu.housing.service.ChatService;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;

/**
 * 实时聊天握手或请求拦截器
 */
@Component
public class ChatHandshakeInterceptor implements HandshakeInterceptor {
    public static final String USER_ID = "chatUserId";
    public static final String ROOM_ID = "chatRoomId";
    private final JwtDecoder jwtDecoder;
    private final ChatService chatService;

    /**
     * 初始化 {@code ChatHandshakeInterceptor} 并注入所需依赖。
     *
     * @param jwtDecoder 认证令牌Decoder
     * @param chatService 聊天业务服务
     */
    public ChatHandshakeInterceptor(JwtDecoder jwtDecoder, ChatService chatService) {
        this.jwtDecoder = jwtDecoder;
        this.chatService = chatService;
    }

    /**
     * 在 WebSocket 握手前校验令牌并保存用户信息。
     *
     * @param request 请求参数
     * @param response 响应
     * @param handler WebSocket 处理器
     * @param attributes WebSocket 握手属性
     * @return 条件成立时返回 true，否则返回 false
     */
    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler handler, Map<String, Object> attributes) {
        try {
            // 1. 握手阶段完成令牌解码和 token_type 校验，普通刷新令牌不能建立聊天连接。
            String token = UriComponentsBuilder.fromUri(request.getURI()).build()
                    .getQueryParams().getFirst("token");
            Jwt jwt = jwtDecoder.decode(token);
            if (!"access".equals(jwt.getClaimAsString("token_type"))) {
                return false;
            }
            Long userId = Long.valueOf(jwt.getSubject());
            // 2. 房间编号来自服务端路由，并通过数据库参与关系校验，不能只信任客户端声明。
            String[] parts = request.getURI().getPath().split("/");
            Long roomId = Long.valueOf(parts[parts.length - 1].isBlank()
                    ? parts[parts.length - 2] : parts[parts.length - 1]);
            if (!chatService.isParticipant(userId, roomId)) {
                return false;
            }
            // 3. 仅把校验后的身份写入会话属性，后续消息处理不再从 payload 读取用户编号。
            attributes.put(USER_ID, userId);
            attributes.put(ROOM_ID, roomId);
            return true;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    /**
     * 处理 WebSocket 握手完成事件。
     *
     * @param request 请求参数
     * @param response 响应
     * @param handler WebSocket 处理器
     * @param exception 异常对象
     */
    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler handler, Exception exception) {
    }
}
