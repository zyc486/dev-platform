package com.zhaoyichi.devplatformbackend.utils;

import com.zhaoyichi.devplatformbackend.entity.GithubRawData;
import com.zhaoyichi.devplatformbackend.mapper.GithubRawDataMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class GithubDataCollectorService {

    private final RestTemplate restTemplate = new RestTemplate();
    private final String GITHUB_API = "https://api.github.com";

    @Autowired
    private GithubRawDataMapper githubRawDataMapper;

    // 从application.yml读取Token（推荐方式）
    @Value("${github.token:}")
    private String token;

    public String collectUserProfile(String githubUsername) {
        // 1. 查缓存（24小时有效）
        // ...（保持你之前代码逻辑，这里简化）

        HttpHeaders headers = new HttpHeaders();
        if (!token.isEmpty()) {
            headers.set("Authorization", "token " + token);
        }
        headers.set("Accept", "application/vnd.github.v3+json");
        HttpEntity<String> entity = new HttpEntity<>(headers);

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    GITHUB_API + "/users/" + githubUsername,
                    HttpMethod.GET, entity, String.class);

            // 保存缓存
            GithubRawData data = new GithubRawData();
            data.setGithubUsername(githubUsername);
            data.setDataType("user_profile");
            data.setRawJson(response.getBody());
            githubRawDataMapper.insert(data);

            return response.getBody();
        } catch (Exception e) {
            throw new RuntimeException("GitHub API调用失败: " + e.getMessage());
        }
    }
}