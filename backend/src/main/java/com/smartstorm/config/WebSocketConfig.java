package com.smartstorm.config;

import com.smartstorm.handler.CustomWebSocketHandler;
import com.smartstorm.util.JwtUtil;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;

import java.net.URI;
import java.util.Map;

/**
 * WebSocket 配置：注册 /ws 端点，供前端建立实时连接。
 * <p>握手时解析查询参数 {@code token}（JWT），校验通过则把 userId 与登录态
 * 写入 session attributes，供 {@link CustomWebSocketHandler} 判断写权限。</p>
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    public static final String ATTR_USER_ID = "wsUserId";
    public static final String ATTR_LOGGED_IN = "wsLoggedIn";

    private final CustomWebSocketHandler wsHandler;
    private final JwtUtil jwtUtil;

    public WebSocketConfig(CustomWebSocketHandler wsHandler, JwtUtil jwtUtil) {
        this.wsHandler = wsHandler;
        this.jwtUtil = jwtUtil;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(wsHandler, "/ws")
                .setAllowedOrigins("*")
                .addInterceptors(authHandshakeInterceptor());
    }

    /** 握手拦截器：从 ?token= 解析 JWT，写入会话登录态 */
    private HandshakeInterceptor authHandshakeInterceptor() {
        return new HandshakeInterceptor() {
            @Override
            public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                           WebSocketHandler wsHandler, Map<String, Object> attributes) {
                Long userId = null;
                URI uri = request.getURI();
                String query = uri.getQuery();
                if (query != null) {
                    for (String pair : query.split("&")) {
                        String[] kv = pair.split("=", 2);
                        if (kv.length == 2 && "token".equals(kv[0])) {
                            userId = jwtUtil.parseUserId(kv[1]);
                            break;
                        }
                    }
                }
                attributes.put(ATTR_USER_ID, userId);
                attributes.put(ATTR_LOGGED_IN, userId != null);
                return true;
            }

            @Override
            public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                       WebSocketHandler wsHandler, Exception exception) {
                // no-op
            }
        };
    }

    @Bean
    public ServletServerContainerFactoryBean createWebSocketContainer() {
        ServletServerContainerFactoryBean container = new ServletServerContainerFactoryBean();
        container.setMaxTextMessageBufferSize(64 * 1024);
        container.setMaxBinaryMessageBufferSize(64 * 1024);
        return container;
    }
}
