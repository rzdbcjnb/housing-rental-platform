package com.bulongyu.housing.websocket;


import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * 实时聊天配置类
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {
    private final ChatWebSocketHandler handler;
    private final ChatHandshakeInterceptor interceptor;

    /**
     * 初始化 {@code WebSocketConfig} 并注入所需依赖。
     *
     * @param handler WebSocket 处理器
     * @param interceptor WebSocket 握手认证拦截器
     */
    public WebSocketConfig(ChatWebSocketHandler handler, ChatHandshakeInterceptor interceptor) {
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
        registry.addHandler(handler, "/ws/chat/*/")
                .addInterceptors(interceptor)
                .setAllowedOriginPatterns("http://127.0.0.1:*", "http://localhost:*");
    }
}
