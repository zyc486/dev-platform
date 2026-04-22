package com.zhaoyichi.devplatformbackend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhaoyichi.devplatformbackend.common.ErrorCode;
import com.zhaoyichi.devplatformbackend.common.Result;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

import javax.servlet.RequestDispatcher;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * SPA 路由回退 + 统一错误响应。
 *
 * <p>替代原先基于 {@code @RequestMapping("/**")} 的 SpaForwardController —— 其在 forward 到 {@code /index.html}
 * 后会再次命中自身，造成 {@link StackOverflowError} 死循环（任何前端路径刷新都会 500）。</p>
 *
 * <p>本控制器仅接管 Spring Boot 默认的 {@code /error} 分发路径：
 * <ul>
 *   <li>{@code 404} 且 URI 不属于 {@code /api|/uploads|/ws|/error}，且最后一段不含扩展名时，
 *       视为前端 history 路由（例如 {@code /dm}、{@code /projects/1/board}），forward 到 {@code /index.html}。</li>
 *   <li>其余情况按 HTTP 状态码直接写 JSON 错误响应，保持和 {@code GlobalExceptionHandler} 一致的结构。</li>
 * </ul>
 * </p>
 *
 * <p>实现要点：{@code @Controller} 下若返回 {@code ResponseEntity} 会被视作视图名解析并 404；这里统一使用
 * {@code HttpServletResponse} 直写 JSON，{@code return null} 告知 MVC 无需视图渲染。</p>
 */
@Controller
public class SpaErrorController implements ErrorController {

    private static final String ERROR_PATH = "/error";

    private final ObjectMapper objectMapper;

    public SpaErrorController(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @RequestMapping(ERROR_PATH)
    public String handleError(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Integer status = extractStatus(request);
        String uri = extractOriginalUri(request);

        if (status != null && status == HttpServletResponse.SC_NOT_FOUND
                && isFrontendRoute(uri)) {
            return "forward:/index.html";
        }

        int code = status == null ? 500 : status;
        response.setStatus(code);
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json;charset=UTF-8");
        ErrorCode errorCode = mapErrorCode(code);
        String message = resolveMessage(request, errorCode);
        response.getWriter().write(objectMapper.writeValueAsString(Result.failure(errorCode, message)));
        return null;
    }

    private static Integer extractStatus(HttpServletRequest request) {
        Object raw = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        if (raw instanceof Number) {
            return ((Number) raw).intValue();
        }
        return null;
    }

    private static String extractOriginalUri(HttpServletRequest request) {
        Object raw = request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI);
        if (raw instanceof String) {
            return (String) raw;
        }
        return request.getRequestURI();
    }

    private static boolean isFrontendRoute(String uri) {
        if (uri == null || uri.isEmpty()) {
            return false;
        }
        if (uri.startsWith("/api/") || uri.startsWith("/uploads/")
                || uri.startsWith("/ws/") || uri.startsWith("/error")) {
            return false;
        }
        int slash = uri.lastIndexOf('/');
        String last = slash >= 0 ? uri.substring(slash + 1) : uri;
        return !last.contains(".");
    }

    private static ErrorCode mapErrorCode(int status) {
        switch (status) {
            case 400:
                return ErrorCode.VALIDATION_ERROR;
            case 401:
                return ErrorCode.UNAUTHORIZED;
            case 403:
                return ErrorCode.FORBIDDEN;
            case 404:
                return ErrorCode.BUSINESS_ERROR;
            default:
                return ErrorCode.INTERNAL_ERROR;
        }
    }

    private static String resolveMessage(HttpServletRequest request, ErrorCode errorCode) {
        Object msg = request.getAttribute(RequestDispatcher.ERROR_MESSAGE);
        if (msg instanceof String && !((String) msg).trim().isEmpty()) {
            return (String) msg;
        }
        return errorCode.getDefaultMessage();
    }
}
