package com.zhaoyichi.devplatformbackend.utils;

import com.zhaoyichi.devplatformbackend.common.Result;

import javax.servlet.http.HttpServletRequest;

/**
 * 鉴权上下文读取与轻量权限校验工具。
 *
 * <p>当前用户信息来自 {@code JwtInterceptor} 在请求级别写入的 attributes：
 * <ul>
 *   <li>{@code currentUserId}</li>
 *   <li>{@code currentUser}</li>
 *   <li>{@code currentUserRole}</li>
 * </ul>
 * 控制器层可通过 {@link #requireLogin(HttpServletRequest)} / {@link #requireAdmin(HttpServletRequest)}
 * 在最外层快速返回统一的 {@link Result}，避免散落的重复判断。</p>
 */
public class AuthHelper {
    private AuthHelper() {
    }

    public static Long currentUserId(HttpServletRequest request) {
        Object value = request.getAttribute("currentUserId");
        if (value == null) {
            return null;
        }
        return Long.valueOf(String.valueOf(value));
    }

    public static String currentUsername(HttpServletRequest request) {
        Object value = request.getAttribute("currentUser");
        return value == null ? null : String.valueOf(value);
    }

    public static String currentRole(HttpServletRequest request) {
        Object value = request.getAttribute("currentUserRole");
        return value == null ? "user" : String.valueOf(value);
    }

    public static <T> Result<T> requireLogin(HttpServletRequest request) {
        return currentUserId(request) == null ? Result.unauthorized("未登录或登录已过期") : null;
    }

    public static boolean isAdmin(HttpServletRequest request) {
        return JwtUtils.isAdminRole(currentRole(request));
    }

    public static <T> Result<T> requireAdmin(HttpServletRequest request) {
        if (currentUserId(request) == null) {
            return Result.unauthorized("未登录或登录已过期");
        }
        return isAdmin(request) ? null : Result.forbidden("无管理员权限");
    }
}
