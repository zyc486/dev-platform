package com.zhaoyichi.devplatformbackend.utils;

import com.zhaoyichi.devplatformbackend.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * JWT 工具类（完全兼容 JJWT 0.9.1 + JDK8 + SpringBoot 2.7）
 * 补全所有拦截器/服务需要的方法，零报错
 */
public class JwtUtils {
    public static final String AUTHORIZATION_HEADER = "Authorization";
    public static final String TOKEN_PREFIX = "Bearer ";

    private static String secretKey = "change-this-jwt-secret";
    private static long expiration = 7 * 24 * 60 * 60 * 1000L;

    /**
     * 生成Token（兼容2参数/3参数调用，完全匹配UserService的调用）
     * 支持：generateToken(String userId, String username)
     * 支持：generateToken(Long userId, String username, String role) 兼容旧调用
     */
    public static String generateToken(String userId, String username) {
        return generateToken(userId, username, "user");
    }

    public static String generateToken(String userId, String username, String role) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userId);
        claims.put("username", username);
        claims.put("role", role == null ? "user" : role);

        return Jwts.builder()
                .setClaims(claims)
                .setExpiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(SignatureAlgorithm.HS256, secretKey.getBytes())
                .compact();
    }

    // 重载方法：兼容UserService传3个参数的调用（Long userId, String username, String role）
    public static String generateToken(Long userId, String username, String role) {
        return generateToken(String.valueOf(userId), username, role);
    }

    /**
     * 解析Token，获取所有Claims（拦截器需要的方法）
     */
    public static Claims getClaimsByToken(String token) {
        return Jwts.parser()
                .setSigningKey(secretKey.getBytes())
                .parseClaimsJws(token)
                .getBody();
    }

    /**
     * 从Token获取用户ID（兼容String类型）
     */
    public static String getUserIdFromToken(String token) {
        return getClaimsByToken(token).get("userId", String.class);
    }

    /**
     * 从Token获取用户名
     */
    public static String getUsernameFromToken(String token) {
        return getClaimsByToken(token).get("username", String.class);
    }

    public static String getRoleFromToken(String token) {
        String role = getClaimsByToken(token).get("role", String.class);
        return role == null ? "user" : role;
    }

    public static String resolveToken(String authorizationHeader) {
        if (authorizationHeader == null) {
            return null;
        }
        String header = authorizationHeader.trim();
        if (header.length() <= TOKEN_PREFIX.length()) {
            return null;
        }
        if (!header.regionMatches(true, 0, TOKEN_PREFIX, 0, TOKEN_PREFIX.length())) {
            return null;
        }
        String token = header.substring(TOKEN_PREFIX.length()).trim();
        return token.isEmpty() ? null : token;
    }

    public static boolean isAdminRole(String role) {
        return "admin".equalsIgnoreCase(role);
    }

    /**
     * 兼容旧代码的parseToken方法
     */
    public static Claims parseToken(String token) {
        return getClaimsByToken(token);
    }

    public static void configure(String secret, long expirationMs) {
        if (secret != null && !secret.trim().isEmpty()) {
            secretKey = secret.trim();
        }
        if (expirationMs > 0) {
            expiration = expirationMs;
        }
    }

    @Component
    public static class Initializer {
        public Initializer(JwtProperties jwtProperties) {
            JwtUtils.configure(jwtProperties.getSecret(), jwtProperties.getExpirationMs());
        }
    }
}