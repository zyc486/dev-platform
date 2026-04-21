package com.zhaoyichi.devplatformbackend.interceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhaoyichi.devplatformbackend.common.ErrorCode;
import com.zhaoyichi.devplatformbackend.common.Result;
import com.zhaoyichi.devplatformbackend.utils.JwtUtils;
import io.jsonwebtoken.Claims;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * JWT 登录态拦截器。
 *
 * <p>约定前端统一使用 {@code Authorization: Bearer <token>} 传递 token；
 * 拦截器解析后把用户信息写入 request attributes，供 {@code AuthHelper} 与控制器读取。</p>
 *
 * <p>管理员接口统一以 {@code /api/admin/**} 为前缀做权限控制。</p>
 */
@Component
public class JwtInterceptor implements HandlerInterceptor {
    private static final String ADMIN_PATH_PREFIX = "/api/admin";
    private static final Logger log = LoggerFactory.getLogger(JwtInterceptor.class);
    private final ObjectMapper objectMapper;

    public JwtInterceptor(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean preHandle(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull Object handler
    ) throws Exception {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String token = JwtUtils.resolveToken(request.getHeader(JwtUtils.AUTHORIZATION_HEADER));
        if (token == null) {
            if (allowAnonymousGet(request)) {
                return true;
            }
            writeJsonError(response, HttpServletResponse.SC_UNAUTHORIZED, Result.unauthorized("未登录或认证头格式错误"));
            return false;
        }

        try {
            Claims claims = JwtUtils.getClaimsByToken(token);
            if (claims == null) {
                writeJsonError(response, HttpServletResponse.SC_UNAUTHORIZED, Result.unauthorized("Token 无效"));
                return false;
            }
            String role = claims.get("role", String.class);
            request.setAttribute("currentUser", claims.get("username", String.class));
            request.setAttribute("currentUserId", claims.get("userId", String.class));
            request.setAttribute("currentUserRole", role == null ? "user" : role);
            if (isAdminPath(request) && !JwtUtils.isAdminRole(role)) {
                writeJsonError(response, HttpServletResponse.SC_FORBIDDEN, Result.forbidden("无管理员权限"));
                return false;
            }
            return true;
        } catch (Exception e) {
            log.warn("JWT 解析失败 uri={}", request.getRequestURI(), e);
            writeJsonError(response, HttpServletResponse.SC_UNAUTHORIZED, Result.failure(ErrorCode.UNAUTHORIZED, "Token 已过期或无效"));
            return false;
        }
    }

    private boolean isAdminPath(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return uri != null && uri.startsWith(ADMIN_PATH_PREFIX);
    }

    /**
     * 部分 GET 接口允许匿名访问（浏览广场动态、社群列表与帖子等），与业务层「发帖需登录」区分。
     */
    private boolean allowAnonymousGet(HttpServletRequest request) {
        if (!"GET".equalsIgnoreCase(request.getMethod())) {
            return false;
        }
        String uri = request.getRequestURI();
        if (uri == null) {
            return false;
        }
        String ctx = request.getContextPath();
        if (ctx != null && !ctx.isEmpty() && uri.startsWith(ctx)) {
            uri = uri.substring(ctx.length());
        }
        if (uri.length() > 1 && uri.endsWith("/")) {
            uri = uri.substring(0, uri.length() - 1);
        }
        if ("/api/post/list".equals(uri)) {
            return true;
        }
        if ("/api/post/hot".equals(uri)) {
            return true;
        }
        if ("/api/post/hotTags".equals(uri)) {
            return true;
        }
        if (uri.startsWith("/api/post/comments/")) {
            return true;
        }
        if ("/api/community/list".equals(uri)) {
            return true;
        }
        if (uri.matches("/api/community/\\d+/posts")) {
            return true;
        }
        if (uri.matches("/api/community/\\d+/members")) {
            return true;
        }
        if (uri.matches("/api/community/\\d+")) {
            return true;
        }
        // 徽章相关：个人徽章墙 + 全徽章定义（公开只读，便于未登录浏览个人主页）
        if (uri.startsWith("/api/badge/user/")) {
            return true;
        }
        if ("/api/badge/definitions".equals(uri)) {
            return true;
        }
        if (uri.startsWith("/api/home/")) {
            return isPublicHomeUri(uri);
        }
        if ("/api/search".equals(uri)) {
            return true;
        }
        // AI 画像查询：仅基于公开 GitHub 数据生成，允许匿名只读访问（刷新接口仍要求登录）
        if ("/api/credit/aiProfile".equals(uri)) {
            return true;
        }
        return uri.matches("/api/profile/[^/]+");
    }

    /**
     * 首页公开摘要允许匿名查看，方便访客理解平台价值；个性化消息等仍要求登录。
     */
    private boolean isPublicHomeUri(String uri) {
        return "/api/home/summary".equals(uri)
                || "/api/home/recommendCollabs".equals(uri)
                || "/api/home/recentPosts".equals(uri)
                || "/api/home/hotDevelopers".equals(uri)
                || "/api/home/hotPosts".equals(uri)
                || "/api/home/techPulse".equals(uri);
    }

    private void writeJsonError(HttpServletResponse response, int status, Result<Void> result) throws Exception {
        response.setStatus(status);
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(result));
    }
}