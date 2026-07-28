package com.bulongyu.housing.websocket;


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
 * 站内通知握手或请求拦截器
 */
@Component
public class NotificationHandshakeInterceptor implements HandshakeInterceptor {
    public static final String USER_ID = "notificationUserId";
    private final JwtDecoder jwtDecoder;

    /**
     * 初始化 {@code NotificationHandshakeInterceptor} 并注入所需依赖。
     *
     * @param jwtDecoder 认证令牌Decoder
     */
    public NotificationHandshakeInterceptor(JwtDecoder jwtDecoder) {
        this.jwtDecoder = jwtDecoder;
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
            String token = UriComponentsBuilder.fromUri(request.getURI()).build()
                    .getQueryParams().getFirst("token");
            Jwt jwt = jwtDecoder.decode(token);
            if (!"access".equals(jwt.getClaimAsString("token_type"))) {
                return false;
            }
            attributes.put(USER_ID, Long.valueOf(jwt.getSubject()));
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
