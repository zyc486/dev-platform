package com.zhaoyichi.devplatformbackend.config;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.DispatcherType;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * SPA history 路由回退过滤器。
 *
 * <p>对无扩展名、非 {@code /api|/uploads|/ws|/error|/assets} 的 GET 请求，直接 forward 到 {@code /index.html}，
 * 由前端 Vue Router 接管（刷新 {@code /dm}、{@code /projects/1/board} 等页面不再 404）。</p>
 *
 * <p>为什么不依赖 {@code ErrorController} + 404 回退：
 * Tomcat 的 {@code ResourceHttpRequestHandler} 对找不到的静态资源直接调用 {@code sendError(404)} 并写入默认响应体，
 * 此时 response 已 committed，错误页 forward 不会再触发，导致自定义 {@code /error} 控制器收不到该请求。
 * 前置拦截一次性解决该问题。</p>
 *
 * <p>防递归：只处理 {@link DispatcherType#REQUEST}（初始请求），FORWARD/INCLUDE 不再处理；
 * 对 {@code /index.html} 本身显式跳过，避免转发到自己。</p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class SpaHistoryFallbackFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        if (request.getDispatcherType() != DispatcherType.REQUEST
                || !"GET".equalsIgnoreCase(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        String uri = request.getRequestURI();
        String ctx = request.getContextPath();
        if (ctx != null && !ctx.isEmpty() && uri != null && uri.startsWith(ctx)) {
            uri = uri.substring(ctx.length());
        }

        if (isFrontendHistoryRoute(uri)) {
            request.getRequestDispatcher("/index.html").forward(request, response);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private static boolean isFrontendHistoryRoute(String uri) {
        if (uri == null || uri.isEmpty()) {
            return false;
        }
        if ("/".equals(uri) || "/index.html".equals(uri)) {
            return false;
        }
        if (uri.startsWith("/api/") || uri.startsWith("/uploads/")
                || uri.startsWith("/ws/") || uri.startsWith("/error")
                || uri.startsWith("/follow/")
                || uri.startsWith("/assets/") || uri.startsWith("/favicon")) {
            return false;
        }
        int slash = uri.lastIndexOf('/');
        String last = slash >= 0 ? uri.substring(slash + 1) : uri;
        // 带扩展名的静态文件（.js / .css / .ico / .map / .woff2 等）不回退，让它们真实 404。
        return !last.contains(".");
    }
}
