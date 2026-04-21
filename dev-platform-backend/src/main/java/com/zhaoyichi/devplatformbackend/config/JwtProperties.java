package com.zhaoyichi.devplatformbackend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * JWT 配置。
 */
@Component
@ConfigurationProperties(prefix = "app.security.jwt")
public class JwtProperties {
    private String secret = "change-this-jwt-secret";
    private long expirationMs = 604800000L;

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public long getExpirationMs() {
        return expirationMs;
    }

    public void setExpirationMs(long expirationMs) {
        this.expirationMs = expirationMs;
    }
}
