package com.zhaoyichi.devplatformbackend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.ai.api")
public class AiApiProperties {
    /** 硅基流动 API 基础地址，例如 https://api.siliconflow.cn/v1 */
    private String baseUrl = "https://api.siliconflow.cn/v1";
    /** 硅基流动 API Key（请使用环境变量注入，避免提交到仓库） */
    private String apiKey = "";
    /** 模型名称，例如 Qwen/Qwen2.5-32B-Instruct */
    private String model = "Qwen/Qwen2.5-32B-Instruct";
    /** 是否启用 AI 画像功能 */
    private boolean enabled = false;

    /** 是否启用 HTTP 代理（例如 Clash/TUN 本地端口） */
    private boolean proxyEnabled = false;
    private String proxyHost = "127.0.0.1";
    private int proxyPort = 6478;

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isProxyEnabled() {
        return proxyEnabled;
    }

    public void setProxyEnabled(boolean proxyEnabled) {
        this.proxyEnabled = proxyEnabled;
    }

    public String getProxyHost() {
        return proxyHost;
    }

    public void setProxyHost(String proxyHost) {
        this.proxyHost = proxyHost;
    }

    public int getProxyPort() {
        return proxyPort;
    }

    public void setProxyPort(int proxyPort) {
        this.proxyPort = proxyPort;
    }
}

