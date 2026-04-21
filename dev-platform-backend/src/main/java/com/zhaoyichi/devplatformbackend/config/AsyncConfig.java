package com.zhaoyichi.devplatformbackend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * 异步任务线程池（信用重算等），避免阻塞 Tomcat 工作线程。
 */
@Configuration
public class AsyncConfig {

    @Bean(name = "creditTaskExecutor")
    public Executor creditTaskExecutor() {
        ThreadPoolTaskExecutor ex = new ThreadPoolTaskExecutor();
        ex.setCorePoolSize(2);
        ex.setMaxPoolSize(6);
        ex.setQueueCapacity(200);
        ex.setThreadNamePrefix("credit-async-");
        ex.initialize();
        return ex;
    }
}
