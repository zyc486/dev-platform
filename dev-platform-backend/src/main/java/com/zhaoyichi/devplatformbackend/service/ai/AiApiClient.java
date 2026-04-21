package com.zhaoyichi.devplatformbackend.service.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhaoyichi.devplatformbackend.config.AiApiProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 硅基流动 OpenAI-兼容调用封装：/chat/completions
 */
@Component
public class AiApiClient {

    @Autowired
    private AiApiProperties props;
    @Autowired
    private ObjectMapper objectMapper;

    public DeepSeekChatResult chat(List<Map<String, Object>> messages, boolean jsonMode) {
        if (!props.isEnabled()) {
            throw new IllegalStateException("AI 未启用：请在 application.yml 中设置 app.ai.api.enabled=true");
        }
        String apiKey = props.getApiKey();
        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new IllegalStateException("AI API Key 未配置：请设置 app.ai.api.api-key 或环境变量注入");
        }
        String base = normalizeBaseUrl(props.getBaseUrl());
        String url = base + "/chat/completions";

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", props.getModel());
        payload.put("messages", messages == null ? new ArrayList<>() : messages);
        payload.put("stream", false);
        if (jsonMode) {
            // OpenAI 兼容：要求输出为 JSON object（DeepSeek 支持兼容字段）
            Map<String, Object> respFmt = new LinkedHashMap<>();
            respFmt.put("type", "json_object");
            payload.put("response_format", respFmt);
        }

        String body;
        try {
            body = objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("DeepSeek 请求序列化失败", e);
        }

        RestTemplate rt = buildRestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + apiKey.trim());
        headers.set("Accept", "application/json");

        try {
            ResponseEntity<String> resp = rt.exchange(url, HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
            String respBody = resp.getBody() == null ? "" : resp.getBody();
            return parse(respBody);
        } catch (RestClientException e) {
            throw new RuntimeException("DeepSeek 请求失败: " + e.getMessage(), e);
        }
    }

    private DeepSeekChatResult parse(String rawJson) {
        try {
            JsonNode root = objectMapper.readTree(rawJson == null ? "" : rawJson);
            JsonNode choices = root.path("choices");
            String content = "";
            if (choices.isArray() && choices.size() > 0) {
                content = choices.get(0).path("message").path("content").asText("");
            }
            DeepSeekChatResult r = new DeepSeekChatResult();
            r.raw = rawJson;
            r.content = content;
            JsonNode usage = root.path("usage");
            if (!usage.isMissingNode()) {
                r.promptTokens = usage.path("prompt_tokens").isInt() ? usage.path("prompt_tokens").asInt() : null;
                r.completionTokens = usage.path("completion_tokens").isInt() ? usage.path("completion_tokens").asInt() : null;
                r.totalTokens = usage.path("total_tokens").isInt() ? usage.path("total_tokens").asInt() : null;
            }
            return r;
        } catch (Exception e) {
            DeepSeekChatResult r = new DeepSeekChatResult();
            r.raw = rawJson;
            r.content = rawJson == null ? "" : rawJson;
            return r;
        }
    }

    private RestTemplate buildRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        if (props.isProxyEnabled()) {
            factory.setProxy(new Proxy(Proxy.Type.HTTP, new InetSocketAddress(props.getProxyHost(), props.getProxyPort())));
        }
        factory.setConnectTimeout(8000);
        // DeepSeek 在部分网络环境下首包/长输出可能超过 30s，适当放宽，避免频繁超时
        factory.setReadTimeout(120000);
        return new RestTemplate(factory);
    }

    private static String normalizeBaseUrl(String baseUrl) {
        String b = (baseUrl == null || baseUrl.trim().isEmpty()) ? "https://api.siliconflow.cn/v1" : baseUrl.trim();
        if (b.endsWith("/")) {
            b = b.substring(0, b.length() - 1);
        }
        // 硅基流动文档：使用 https://api.siliconflow.cn/v1
        return b;
    }

    public static class DeepSeekChatResult {
        public String content;
        public String raw;
        public Integer promptTokens;
        public Integer completionTokens;
        public Integer totalTokens;
    }
}

