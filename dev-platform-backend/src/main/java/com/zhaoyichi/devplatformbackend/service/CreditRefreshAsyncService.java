package com.zhaoyichi.devplatformbackend.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * 异步执行信用重算，避免 HTTP 线程阻塞在 GitHub 网络 IO 上。
 */
@Service
public class CreditRefreshAsyncService {

    private static final Logger log = LoggerFactory.getLogger(CreditRefreshAsyncService.class);

    private final CreditScoreService creditScoreService;

    public CreditRefreshAsyncService(CreditScoreService creditScoreService) {
        this.creditScoreService = creditScoreService;
    }

    @Async("creditTaskExecutor")
    public void runRefreshAsync(Long userId) {
        try {
            CreditScoreService.ManualRefreshResult r = creditScoreService.manualRefreshCreditForUser(userId);
            log.info("手动信用异步刷新完成 userId={} result={}", userId, r);
        } catch (Exception e) {
            log.error("手动信用异步刷新异常 userId={}", userId, e);
        }
    }
}
