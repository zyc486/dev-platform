package com.zhaoyichi.devplatformbackend.service;

import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 进程内、基于「固定时间窗口」的简单计数限流器。
 *
 * <p>为什么不选 Bucket4j / Redis？—— 毕业项目单机部署足够，避免额外依赖。</p>
 *
 * <p>用法：{@code limiter.tryAcquire("ip:1.2.3.4:/api/user/login", 5, 60)} 表示
 * 该 key 在 60 秒内最多允许 5 次请求。</p>
 */
@Component
public class SimpleRateLimiter {

    private static class Window {
        volatile long startMs;
        final AtomicInteger count = new AtomicInteger(0);
    }

    private final ConcurrentHashMap<String, Window> map = new ConcurrentHashMap<>();

    /**
     * @param key        限流维度（IP / 用户 / 接口组合）
     * @param limit      窗口内允许的最大次数
     * @param windowSec  窗口长度（秒）
     * @return true 放行；false 被限流
     */
    public boolean tryAcquire(String key, int limit, int windowSec) {
        long now = System.currentTimeMillis();
        Window w = map.computeIfAbsent(key, k -> {
            Window x = new Window();
            x.startMs = now;
            return x;
        });
        synchronized (w) {
            if (now - w.startMs >= windowSec * 1000L) {
                w.startMs = now;
                w.count.set(0);
            }
            return w.count.incrementAndGet() <= limit;
        }
    }
}
