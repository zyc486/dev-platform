package com.zhaoyichi.devplatformbackend.websocket;

import com.zhaoyichi.devplatformbackend.utils.JwtUtils;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import javax.servlet.http.HttpServletRequest;
import java.util.Map;

/**
 * 从握手 URL 的 {@code token} 参数解析 JWT，并写入 STOMP 用户身份（与 {@link JwtHandshakeHandler} 配合）。
 */
public class JwtHandshakeInterceptor implements HandshakeInterceptor {

    public static final String ATTR_PRINCIPAL = "jwtPrincipal";

    @Override
    public boolean beforeHandshake(
            @NonNull ServerHttpRequest request,
            @NonNull ServerHttpResponse response,
            @NonNull WebSocketHandler wsHandler,
            @NonNull Map<String, Object> attributes
    ) {
        if (!(request instanceof ServletServerHttpRequest)) {
            return true;
        }
        HttpServletRequest servletRequest = ((ServletServerHttpRequest) request).getServletRequest();
        String token = servletRequest.getParameter("token");
        if (token == null || token.trim().isEmpty()) {
            return true;
        }
        try {
            String raw = token.startsWith(JwtUtils.TOKEN_PREFIX) ? token.substring(JwtUtils.TOKEN_PREFIX.length()).trim() : token.trim();
            String username = JwtUtils.getUsernameFromToken(raw);
            attributes.put(ATTR_PRINCIPAL, new StompPrincipal(username));
        } catch (Exception e) {
            return false;
        }
        return true;
    }

    @Override
    public void afterHandshake(
            @NonNull ServerHttpRequest request,
            @NonNull ServerHttpResponse response,
            @NonNull WebSocketHandler wsHandler,
            @Nullable Exception exception
    ) {
    }
}
