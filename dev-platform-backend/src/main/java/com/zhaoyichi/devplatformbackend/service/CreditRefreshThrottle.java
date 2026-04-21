package com.zhaoyichi.devplatformbackend.service;

import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 手动信用刷新频控：同一用户 10 分钟内仅允许触发一次（内存级，单机有效；集群需 Redis）。
 */
@Component
public class CreditRefreshThrottle {

    private static final long INTERVAL_MS = 10 * 60 * 1000L;

    private final ConcurrentHashMap<Long, Long> lastTriggerAt = new ConcurrentHashMap<>();

    /**
     * @return true 表示通过限流并记录本次触发时间；false 表示冷却中
     */
    public synchronized boolean tryAcquire(Long userId) {
        if (userId == null) {
            return false;
        }
        long now = System.currentTimeMillis();
        Long prev = lastTriggerAt.get(userId);
        if (prev != null && now - prev < INTERVAL_MS) {
            return false;
        }
        lastTriggerAt.put(userId, now);
        return true;
    }

    /** 下次允许触发的时间戳（毫秒），用于前端提示 */
    public synchronized long nextAllowedAtMillis(Long userId) {
        Long prev = lastTriggerAt.get(userId);
        if (prev == null) {
            return 0L;
        }
        return prev + INTERVAL_MS;
    }

    /** 任务未成功提交时回退限流，避免误锁 10 分钟 */
    public synchronized void releaseAfterFailure(Long userId) {
        if (userId != null) {
            lastTriggerAt.remove(userId);
        }
    }
}
