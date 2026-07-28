package com.bulongyu.housing.websocket;


import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * 站内通知配置类
 */
@Configuration
@EnableWebSocket
public class NotificationWebSocketConfig implements WebSocketConfigurer {
    private final NotificationWebSocketHandler handler;
    private final NotificationHandshakeInterceptor interceptor;

    /**
     * 初始化 {@code NotificationWebSocketConfig} 并注入所需依赖。
     *
     * @param handler WebSocket 处理器
     * @param interceptor WebSocket 握手认证拦截器
     */
    public NotificationWebSocketConfig(NotificationWebSocketHandler handler,
                                       NotificationHandshakeInterceptor interceptor) {
        this.handler = handler;
        this.interceptor = interceptor;
    }

    /**
     * 注册 WebSocket 地址、处理器与握手拦截器。
     *
     * @param registry WebSocket 处理器注册器
     */
    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/ws/notifications/")
                .addInterceptors(interceptor)
                .setAllowedOriginPatterns("http://127.0.0.1:*", "http://localhost:*");
    }
}
