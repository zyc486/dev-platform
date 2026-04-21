package com.zhaoyichi.devplatformbackend.service.ai;

import com.zhaoyichi.devplatformbackend.config.GithubProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * GitHub 仓库采样器：用于 AI 画像输入构建。
 *
 * <p>设计目标：可复现、轻量、成本可控。默认不拉全量代码，仅采样关键文件（依赖/README/工作流等）。</p>
 */
@Component
public class GithubRepoSampler {

    private static final int DEFAULT_REPO_SAMPLE_LIMIT = 10;
    private static final int DEFAULT_MAX_FILE_BYTES = 80 * 1024; // 80KB

    @Autowired
    private GithubProperties githubProperties;

    public SampledGithubProfile sample(String githubUsername) {
        return sample(githubUsername, DEFAULT_REPO_SAMPLE_LIMIT, DEFAULT_MAX_FILE_BYTES);
    }

    public SampledGithubProfile sample(String githubUsername, int repoSampleLimit, int maxFileBytes) {
        if (githubUsername == null || githubUsername.trim().isEmpty()) {
            return null;
        }
        String gh = githubUsername.trim();

        RestTemplate rt = buildRestTemplate(githubProperties.isProxyEnabled());
        HttpEntity<String> entity = buildEntity();

        Map<String, Object> user = fetchMap(rt, entity, "https://api.github.com/users/" + gh);
        if (user == null) {
            // 代理失败时尝试直连一次
            if (githubProperties.isProxyEnabled()) {
                rt = buildRestTemplate(false);
                user = fetchMap(rt, entity, "https://api.github.com/users/" + gh);
            }
            if (user == null) {
                return null;
            }
        }

        SampledGithubProfile out = new SampledGithubProfile();
        out.githubUsername = gh;
        out.user = user;

        String reposUrl = "https://api.github.com/users/" + gh + "/repos?per_page=" + Math.max(1, repoSampleLimit) + "&sort=pushed";
        List<Map<String, Object>> repos = fetchList(rt, entity, reposUrl);
        out.repos = new ArrayList<>();

        for (Map<String, Object> repo : repos) {
            if (repo == null) continue;
            Object nameObj = repo.get("name");
            if (nameObj == null) continue;
            String repoName = String.valueOf(nameObj);

            SampledRepo r = new SampledRepo();
            r.name = repoName;
            r.repoMeta = repo;

            // releases：只取最新一条
            String relUrl = "https://api.github.com/repos/" + gh + "/" + repoName + "/releases?per_page=1";
            r.latestRelease = firstOrNull(fetchList(rt, entity, relUrl));

            // languages：语言字节分布
            String langUrl = "https://api.github.com/repos/" + gh + "/" + repoName + "/languages";
            r.languages = fetchMap(rt, entity, langUrl);

            // root tree（1 层）：用 contents API 列目录
            String rootContentsUrl = "https://api.github.com/repos/" + gh + "/" + repoName + "/contents";
            List<Map<String, Object>> root = fetchList(rt, entity, rootContentsUrl);
            r.rootEntries = limitList(root, 120);

            // 关键文件抽样
            r.files = new LinkedHashMap<>();
            for (String p : keyFileCandidates(root)) {
                String content = fetchTextFileViaContents(rt, entity, gh, repoName, p, maxFileBytes);
                if (content != null) {
                    r.files.put(p, content);
                }
            }
            out.repos.add(r);
        }

        return out;
    }

    // ============================ 输出结构 ============================

    public static class SampledGithubProfile {
        public String githubUsername;
        public Map<String, Object> user;
        public List<SampledRepo> repos;
    }

    public static class SampledRepo {
        public String name;
        public Map<String, Object> repoMeta;
        public Map<String, Object> latestRelease;
        public Map<String, Object> languages;
        public List<Map<String, Object>> rootEntries;
        /** key: path, value: truncated text */
        public Map<String, String> files;
    }

    // ============================ HTTP 基础设施 ============================

    private RestTemplate buildRestTemplate(boolean useProxy) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        if (useProxy && githubProperties.isProxyEnabled()) {
            factory.setProxy(new Proxy(Proxy.Type.HTTP,
                    new InetSocketAddress(githubProperties.getProxyHost(), githubProperties.getProxyPort())));
        }
        factory.setConnectTimeout(8000);
        factory.setReadTimeout(20000);
        return new RestTemplate(factory);
    }

    private HttpEntity<String> buildEntity() {
        HttpHeaders headers = new HttpHeaders();
        String token = githubProperties.getToken();
        if (token != null && !token.trim().isEmpty()) {
            headers.set("Authorization", "Bearer " + token.trim());
        }
        headers.set("Accept", "application/vnd.github.v3+json");
        headers.set("User-Agent", "dev-platform-backend");
        return new HttpEntity<>(headers);
    }

    private Map<String, Object> fetchMap(RestTemplate rt, HttpEntity<String> entity, @NonNull String url) {
        try {
            ResponseEntity<Map<String, Object>> resp = rt.exchange(Objects.requireNonNull(url, "url"), HttpMethod.GET, entity,
                    new ParameterizedTypeReference<Map<String, Object>>() {});
            return resp.getBody();
        } catch (RestClientException e) {
            return null;
        }
    }

    private List<Map<String, Object>> fetchList(RestTemplate rt, HttpEntity<String> entity, @NonNull String url) {
        try {
            ResponseEntity<List<Map<String, Object>>> resp = rt.exchange(Objects.requireNonNull(url, "url"), HttpMethod.GET, entity,
                    new ParameterizedTypeReference<List<Map<String, Object>>>() {});
            List<Map<String, Object>> body = resp.getBody();
            return body == null ? Collections.emptyList() : body;
        } catch (RestClientException e) {
            return Collections.emptyList();
        }
    }

    /**
     * contents API 返回 JSON（含 base64 content）；这里只取文本并截断。
     */
    private String fetchTextFileViaContents(RestTemplate rt, HttpEntity<String> entity,
                                           String owner, String repo, String path,
                                           int maxBytes) {
        String url = "https://api.github.com/repos/" + owner + "/" + repo + "/contents/" + path;
        Map<String, Object> body = fetchMap(rt, entity, url);
        if (body == null || body.get("content") == null) {
            return null;
        }
        Object enc = body.get("encoding");
        if (enc != null && !"base64".equalsIgnoreCase(String.valueOf(enc))) {
            return null;
        }
        String base64 = String.valueOf(body.get("content")).replace("\n", "").replace("\r", "");
        byte[] decoded;
        try {
            decoded = java.util.Base64.getDecoder().decode(base64);
        } catch (IllegalArgumentException e) {
            return null;
        }
        if (decoded.length == 0) {
            return "";
        }
        int len = Math.min(decoded.length, Math.max(1, maxBytes));
        String text;
        try {
            text = new String(decoded, 0, len, java.nio.charset.StandardCharsets.UTF_8);
        } catch (RuntimeException e) {
            return null;
        }
        if (decoded.length > len) {
            text = text + "\n\n...（已截断，文件过大）";
        }
        return text;
    }

    // ============================ 采样策略 ============================

    private static List<String> keyFileCandidates(List<Map<String, Object>> rootEntries) {
        // 先基于目录项判断存在性，减少 404 请求
        Map<String, Boolean> present = new LinkedHashMap<>();
        if (rootEntries != null) {
            for (Map<String, Object> e : rootEntries) {
                if (e == null) continue;
                Object name = e.get("name");
                Object type = e.get("type");
                if (name == null || type == null) continue;
                present.put(String.valueOf(name), true);
            }
        }

        List<String> candidates = new ArrayList<>();
        // README 优先
        for (String n : new String[]{"README.md", "README.MD", "README"}) {
            if (present.containsKey(n)) candidates.add(n);
        }
        // 依赖/构建
        for (String n : new String[]{"package.json", "pnpm-lock.yaml", "yarn.lock", "pom.xml", "build.gradle", "build.gradle.kts",
                "go.mod", "Cargo.toml", "requirements.txt", "pyproject.toml", "Dockerfile"}) {
            if (present.containsKey(n)) candidates.add(n);
        }
        // 治理
        for (String n : new String[]{"CONTRIBUTING.md", "SECURITY.md", "LICENSE"}) {
            if (present.containsKey(n)) candidates.add(n);
        }
        // workflows：目录存在时再取一两个
        if (present.containsKey(".github")) {
            // 这里不递归列目录，避免请求爆炸；后续可在 sampler 内加一次 list contents(.github/workflows)
            candidates.add(".github/workflows");
        }
        // 去重 + 上限
        List<String> dedup = new ArrayList<>();
        for (String c : candidates) {
            if (c == null) continue;
            if (dedup.contains(c)) continue;
            dedup.add(c);
            if (dedup.size() >= 8) break;
        }
        // contents API 不支持直接读目录；把目录占位去掉
        dedup.remove(".github/workflows");
        return dedup;
    }

    private static Map<String, Object> firstOrNull(List<Map<String, Object>> list) {
        if (list == null || list.isEmpty()) return null;
        return list.get(0);
    }

    private static List<Map<String, Object>> limitList(List<Map<String, Object>> list, int cap) {
        if (list == null || list.isEmpty()) return Collections.emptyList();
        if (list.size() <= cap) return list;
        return list.subList(0, cap);
    }
}

