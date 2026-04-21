package com.zhaoyichi.devplatformbackend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 首页外站热门（GitHub / CSDN）抓取配置。
 */
@Component
@ConfigurationProperties(prefix = "app.external-hot")
public class ExternalHotProperties {

    /**
     * 是否启用定时抓取与对外接口读库。
     */
    private boolean enabled = true;

    /**
     * 启动后是否在后台异步预热一轮（避免首页长时间空白）。
     */
    private boolean fetchOnStartup = true;

    /**
     * Cron：默认每天 8、14、20 点各一次。
     */
    private String fetchCron = "0 0 8,14,20 * * ?";

    private int githubLimit = 10;

    private int csdnLimit = 10;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isFetchOnStartup() {
        return fetchOnStartup;
    }

    public void setFetchOnStartup(boolean fetchOnStartup) {
        this.fetchOnStartup = fetchOnStartup;
    }

    public String getFetchCron() {
        return fetchCron;
    }

    public void setFetchCron(String fetchCron) {
        this.fetchCron = fetchCron;
    }

    public int getGithubLimit() {
        return githubLimit;
    }

    public void setGithubLimit(int githubLimit) {
        this.githubLimit = githubLimit;
    }

    public int getCsdnLimit() {
        return csdnLimit;
    }

    public void setCsdnLimit(int csdnLimit) {
        this.csdnLimit = csdnLimit;
    }
}
