package com.zhaoyichi.devplatformbackend.config;

import com.zhaoyichi.devplatformbackend.interceptor.JwtInterceptor;
import com.zhaoyichi.devplatformbackend.interceptor.RateLimitInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Objects;
import java.nio.file.Paths;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final JwtInterceptor jwtInterceptor;

    private final RateLimitInterceptor rateLimitInterceptor;

    public WebMvcConfig(JwtInterceptor jwtInterceptor, RateLimitInterceptor rateLimitInterceptor) {
        this.jwtInterceptor = jwtInterceptor;
        this.rateLimitInterceptor = rateLimitInterceptor;
    }

    @Override
    public void addInterceptors(@NonNull InterceptorRegistry registry) {
        // JwtInterceptor 先跑（解析 token 写 currentUserId）；RateLimitInterceptor 后跑，从而能按 userId 限流。
        registry.addInterceptor((HandlerInterceptor) Objects.requireNonNull(jwtInterceptor, "jwtInterceptor"))
                .order(0)
                .addPathPatterns("/api/**", "/follow/**")
                .excludePathPatterns(
                        "/api/user/login",
                        "/api/user/register",
                        "/api/user/oauth/login",
                        "/api/user/oauth/login/**",
                        "/ws/**",
                        "/test/**",
                        "/error",
                        "/favicon.ico"
                );

        registry.addInterceptor((HandlerInterceptor) Objects.requireNonNull(rateLimitInterceptor, "rateLimitInterceptor"))
                .order(1)
                .addPathPatterns(
                        "/api/user/login",
                        "/api/post/publish",
                        "/api/credit/query"
                );
    }

    /**
     * 本地 uploads 目录映射为 {@code /uploads/**}，供头像、反馈附件等静态访问。
     */
    @Override
    public void addResourceHandlers(@NonNull ResourceHandlerRegistry registry) {
        String location = Paths.get(System.getProperty("user.dir"), "uploads").toUri().toString();
        if (!location.endsWith("/")) {
            location = location + "/";
        }
        registry.addResourceHandler("/uploads/**").addResourceLocations(location);
    }
}