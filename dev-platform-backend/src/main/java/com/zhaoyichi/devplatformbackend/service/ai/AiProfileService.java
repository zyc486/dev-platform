package com.zhaoyichi.devplatformbackend.service.ai;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhaoyichi.devplatformbackend.config.AiApiProperties;
import com.zhaoyichi.devplatformbackend.entity.AiProfileSnapshot;
import com.zhaoyichi.devplatformbackend.mapper.AiProfileSnapshotMapper;
import com.zhaoyichi.devplatformbackend.service.CreditScoreService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AiProfileService {

    private static final Logger log = LoggerFactory.getLogger(AiProfileService.class);

    @Autowired
    private GithubRepoSampler repoSampler;
    @Autowired
    private AiApiClient aiApiClient;
    @Autowired
    private AiProfilePromptBuilder promptBuilder;
    @Autowired
    private AiProfileSnapshotMapper snapshotMapper;
    @Autowired
    private AiApiProperties aiApiProperties;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private CreditScoreService creditScoreService;

    @Value("${app.ai.profile.ttl-days:7}")
    private int ttlDays;
    @Value("${app.ai.profile.repo-sample-limit:10}")
    private int repoSampleLimit;
    @Value("${app.ai.profile.max-file-bytes:81920}")
    private int maxFileBytes;

    public Map<String, Object> getProfile(String githubUsername, String scene) {
        String gh = githubUsername == null ? null : githubUsername.trim();
        if (gh == null || gh.isEmpty()) {
            return null;
        }
        String normalizedScene = creditScoreService.normalizeScene(scene);

        AiProfileSnapshot snap;
        try {
            snap = snapshotMapper.selectOne(new QueryWrapper<AiProfileSnapshot>()
                    .eq("github_username", gh)
                    .eq("scene", normalizedScene)
                    .last("LIMIT 1"));
        } catch (Exception e) {
            return unavailable(gh, normalizedScene, "读取画像缓存失败", "请检查数据库连接是否正常。");
        }

        // AI 未启用：仍允许返回已有快照（便于展示/导出），无快照则返回结构化错误信息
        if (!aiApiProperties.isEnabled()) {
            if (snap != null) {
                Map<String, Object> m = toResponse(snap, false);
                m.put("warning", "AI 当前未启用（AI_ENABLED=false），已返回历史快照；如需刷新请启用后重启后端。");
                return m;
            }
            return unavailable(gh, normalizedScene,
                    "AI 未启用（AI_ENABLED=false 或 app.ai.api.enabled=false）",
                    "请确认环境变量已生效（新开终端后启动后端），并设置 AI_ENABLED=true、AI_API_KEY=...。");
        }

        boolean promptStale = snap != null
                && !AiProfilePromptBuilder.PROMPT_VERSION.equals(snap.getPromptVersion());
        boolean expired = snap == null || snap.getExpiresAt() == null || snap.getExpiresAt().isBefore(LocalDateTime.now())
                || promptStale;
        if (!expired) {
            return toResponse(snap, false);
        }

        // 有旧缓存则先返回旧缓存，同时触发后台刷新（避免用户等待）
        if (snap != null) {
            triggerRefreshJob(gh, normalizedScene);
            return toResponse(snap, true);
        }

        // 无缓存：改为异步生成，避免首次访问阻塞在外部 AI 网络 IO 上
        try {
            triggerRefreshJob(gh, normalizedScene);
        } catch (Exception ignore) {}
        Map<String, Object> m = unavailable(gh, normalizedScene,
                "画像正在生成中（首次生成需要 20~120 秒）",
                "请稍后刷新本页面或在前端点击“刷新画像”。建议配置 GITHUB_TOKEN 以提高 GitHub 采样稳定性。");
        m.put("status", "refreshing");
        return m;
    }

    public Map<String, Object> refreshNow(String githubUsername, String scene) {
        String gh = githubUsername == null ? null : githubUsername.trim();
        if (gh == null || gh.isEmpty()) {
            return null;
        }
        String normalizedScene = creditScoreService.normalizeScene(scene);
        AiProfileSnapshot created = generateAndUpsert(gh, normalizedScene);
        return created == null
                ? unavailable(gh, normalizedScene, "刷新失败：AI 未启用或 GitHub 数据不可达",
                "请确认 AI_ENABLED=true 且已重启后端；GitHub 侧建议配置 GITHUB_TOKEN。")
                : toResponse(created, false);
    }

    @Async("creditTaskExecutor")
    public void triggerRefreshJob(String githubUsername, String scene) {
        try {
            generateAndUpsert(githubUsername, scene);
        } catch (Exception e) {
            log.warn("[ai-profile] async refresh failed gh={} scene={}", githubUsername, scene, e);
        }
    }

    private AiProfileSnapshot generateAndUpsert(String gh, String scene) {
        if (!aiApiProperties.isEnabled()) {
            return null;
        }

        GithubRepoSampler.SampledGithubProfile sampled = repoSampler.sample(gh, repoSampleLimit, maxFileBytes);
        if (sampled == null) {
            return upsertFailed(gh, scene, "GitHub 数据不可达或采样为空");
        }

        String dataHash = sha256Quiet(sampled);

        List<Map<String, Object>> messages = promptBuilder.buildMessages(sampled);
        AiApiClient.DeepSeekChatResult r;
        try {
            r = aiApiClient.chat(messages, true);
        } catch (RuntimeException e) {
            return upsertFailed(gh, scene, "AI API 请求失败（网络/超时/Key）： " + e.getMessage());
        }
        String content = r == null ? "" : r.content;

        JsonNode json = parseStrictJson(content);
        if (json == null) {
            // 修 JSON 再试一次
            AiApiClient.DeepSeekChatResult fixed;
            try {
                fixed = aiApiClient.chat(promptBuilder.buildFixJsonMessages(content), true);
            } catch (RuntimeException e) {
                return upsertFailed(gh, scene, "AI API JSON 修复请求失败： " + e.getMessage());
            }
            json = parseStrictJson(fixed == null ? "" : fixed.content);
        }
        if (json == null) {
            return upsertFailed(gh, scene, "AI 输出非合法 JSON");
        }

        String summary = json.path("summary").asText(null);
        String techTagsJson = safeStringify(json.path("techTags"));
        String topReposJson = safeStringify(json.path("topProjects"));
        String evidenceJson = safeStringify(json.path("evidence"));

        AiProfileSnapshot snap = snapshotMapper.selectOne(new QueryWrapper<AiProfileSnapshot>()
                .eq("github_username", gh)
                .eq("scene", scene)
                .last("LIMIT 1"));
        if (snap == null) {
            snap = new AiProfileSnapshot();
            snap.setGithubUsername(gh);
            snap.setScene(scene);
            snap.setAlgoVersion("v2");
        }
        snap.setProfileVersion(AiProfilePromptBuilder.PROFILE_VERSION);
        snap.setPromptVersion(AiProfilePromptBuilder.PROMPT_VERSION);
        snap.setModel(aiApiProperties.getModel());
        snap.setDataHash(dataHash);
        snap.setSummary(summary);
        snap.setProfileJson(safeStringify(json));
        snap.setTechTagsJson(techTagsJson);
        snap.setTopReposJson(topReposJson);
        snap.setEvidenceJson(evidenceJson);
        snap.setTokenUsage(r == null ? null : r.totalTokens);
        snap.setStatus("ready");
        snap.setErrorMessage(null);
        snap.setExpiresAt(LocalDateTime.now().plusDays(Math.max(1, ttlDays)));

        if (snap.getId() == null) {
            snapshotMapper.insert(snap);
        } else {
            snapshotMapper.updateById(snap);
        }
        return snap;
    }

    private Map<String, Object> unavailable(String gh, String scene, String msg, String hint) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("githubUsername", gh);
        m.put("scene", scene);
        m.put("status", "unavailable");
        m.put("errorMessage", msg);
        m.put("hint", hint);
        m.put("model", aiApiProperties.getModel());
        m.put("updatedAt", LocalDateTime.now());
        return m;
    }

    private AiProfileSnapshot upsertFailed(String gh, String scene, String msg) {
        AiProfileSnapshot snap = snapshotMapper.selectOne(new QueryWrapper<AiProfileSnapshot>()
                .eq("github_username", gh)
                .eq("scene", scene)
                .last("LIMIT 1"));
        if (snap == null) {
            snap = new AiProfileSnapshot();
            snap.setGithubUsername(gh);
            snap.setScene(scene);
            snap.setAlgoVersion("v2");
        }
        snap.setProfileVersion(AiProfilePromptBuilder.PROFILE_VERSION);
        snap.setPromptVersion(AiProfilePromptBuilder.PROMPT_VERSION);
        snap.setModel(aiApiProperties.getModel());
        snap.setStatus("failed");
        snap.setErrorMessage(msg);
        snap.setExpiresAt(LocalDateTime.now().plusHours(2)); // 失败也给个短 TTL，避免刷爆
        if (snap.getId() == null) snapshotMapper.insert(snap);
        else snapshotMapper.updateById(snap);
        return snap;
    }

    private Map<String, Object> toResponse(AiProfileSnapshot snap, boolean refreshTriggered) {
        if (snap == null) return null;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("githubUsername", snap.getGithubUsername());
        m.put("scene", snap.getScene());
        m.put("algoVersion", snap.getAlgoVersion());
        m.put("profileVersion", snap.getProfileVersion());
        m.put("promptVersion", snap.getPromptVersion());
        m.put("model", snap.getModel());
        m.put("summary", snap.getSummary());
        m.put("profileJson", snap.getProfileJson());
        m.put("techTagsJson", snap.getTechTagsJson());
        m.put("topReposJson", snap.getTopReposJson());
        m.put("evidenceJson", snap.getEvidenceJson());
        m.put("tokenUsage", snap.getTokenUsage());
        m.put("status", snap.getStatus());
        m.put("errorMessage", snap.getErrorMessage());
        m.put("updatedAt", snap.getUpdatedAt());
        m.put("expiresAt", snap.getExpiresAt());
        m.put("refreshTriggered", refreshTriggered);
        return m;
    }

    private JsonNode parseStrictJson(String s) {
        if (s == null) return null;
        String t = s.trim();
        if (t.isEmpty()) return null;
        try {
            return objectMapper.readTree(t);
        } catch (Exception e) {
            return null;
        }
    }

    private String safeStringify(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) return null;
        try {
            return objectMapper.writeValueAsString(node);
        } catch (Exception e) {
            return null;
        }
    }

    private String sha256Quiet(Object obj) {
        try {
            String json = objectMapper.writeValueAsString(obj);
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] d = md.digest(json.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : d) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }
}

