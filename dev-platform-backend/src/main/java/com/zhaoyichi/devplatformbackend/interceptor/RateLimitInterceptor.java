package com.zhaoyichi.devplatformbackend.interceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhaoyichi.devplatformbackend.common.Result;
import com.zhaoyichi.devplatformbackend.service.LoginAuditService;
import com.zhaoyichi.devplatformbackend.service.SimpleRateLimiter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.HashMap;
import java.util.Map;

/**
 * 应用层限流拦截器。
 *
 * <p>规则表 {@link #RULES}：uri -> {limit, windowSec}。
 * 登录按 IP 限流（未登录时没有 userId）；
 * 其他按 userId（若登录）或 IP（未登录时）限流。</p>
 *
 * <p>被限流时返回 HTTP 429 + 标准 {@link Result} 响应体，前端 {@code request.js} 会统一给出吐司提示。</p>
 */
@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    private static final Map<String, int[]> RULES = new HashMap<>();
    static {
        RULES.put("/api/user/login",   new int[]{5, 60});
        RULES.put("/api/post/publish", new int[]{3, 60});
        RULES.put("/api/credit/query", new int[]{10, 60});
    }

    @Autowired
    private SimpleRateLimiter limiter;

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public boolean preHandle(
            @NonNull HttpServletRequest req,
            @NonNull HttpServletResponse res,
            @NonNull Object handler
    ) throws Exception {
        int[] rule = RULES.get(req.getRequestURI());
        if (rule == null) {
            return true;
        }

        String key;
        if ("/api/user/login".equals(req.getRequestURI())) {
            key = "ip:" + LoginAuditService.extractIp(req) + ":" + req.getRequestURI();
        } else {
            Object uid = req.getAttribute("currentUserId");
            key = (uid == null
                    ? "ip:" + LoginAuditService.extractIp(req)
                    : "uid:" + uid) + ":" + req.getRequestURI();
        }

        if (!limiter.tryAcquire(key, rule[0], rule[1])) {
            res.setStatus(429);
            res.setCharacterEncoding("UTF-8");
            res.setContentType("application/json;charset=UTF-8");
            res.getWriter().write(objectMapper.writeValueAsString(
                    Result.error("操作过于频繁，请稍后再试")));
            return false;
        }
        return true;
    }
}
