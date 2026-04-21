package com.zhaoyichi.devplatformbackend.websocket;

import org.springframework.http.server.ServerHttpRequest;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;

import java.security.Principal;
import java.util.Map;

public class JwtHandshakeHandler extends DefaultHandshakeHandler {

    @Override
    protected @Nullable Principal determineUser(
            @NonNull ServerHttpRequest request,
            @NonNull WebSocketHandler wsHandler,
            @NonNull Map<String, Object> attributes
    ) {
        Object p = attributes.get(JwtHandshakeInterceptor.ATTR_PRINCIPAL);
        if (p instanceof Principal) {
            return (Principal) p;
        }
        return null;
    }
}
