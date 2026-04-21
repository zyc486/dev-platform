package com.zhaoyichi.devplatformbackend.service.credit;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.zhaoyichi.devplatformbackend.config.GithubProperties;
import com.zhaoyichi.devplatformbackend.entity.GithubEventStats;
import com.zhaoyichi.devplatformbackend.mapper.GithubEventStatsMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * V2 算法所需的 GitHub 原始指标采集器。
 *
 * <h3>文献依据</h3>
 * <ul>
 *   <li>T/CESA XXXX—2022 开源贡献者评价模型附录 B：指标体系 {@code OI / IC / OP / PRR / MP}。</li>
 *   <li>Gousios et al., ICSE 2014：PR 合并率 = MP/OP 是核心质量指标。</li>
 *   <li>孙晶, 刘丽丽, 2017：合规可信性按 License / 可靠性 / 安全性等多属性分解。</li>
 * </ul>
 *
 * <h3>API 现实约束（必须在论文"数据采集范围"一节声明）</h3>
 * <ul>
 *   <li>{@code /users/{u}/events} 只返回最近 90 天最多 300 条；因此 IC / PRR / Push / CloseIssue / ClosePR
 *       是"近 90 天近似计数"，而 OI / OP / MP 使用 {@code /search/issues} 的全量计数。</li>
 *   <li>{@code /repos/{u}/{r}/vulnerability-alerts} 仅仓库管理员可见，第三方不可用；
 *       因此 CVE 维度降级为 {@code SECURITY.md} 文件存在性（作为安全响应能力代理）。</li>
 *   <li>为控制 API 预算，仓库级合规指标仅采样前 {@value #REPO_SAMPLE_LIMIT} 个公开仓库；
 *       同一路径汇总各仓 {@code stargazers_count} 为 {@code repo_stars_total}，供协作维「社区认可度」使用。</li>
 * </ul>
 */
@Component
public class GithubMetricsCollector {

    private static final Logger log = LoggerFactory.getLogger(GithubMetricsCollector.class);

    /** 采样仓库数上限：权衡 API 预算（5000/h）与合规统计显著性。 */
    private static final int REPO_SAMPLE_LIMIT = 10;

    /** events API 分页上限：1 页 100 条 × 3 页 = 300 条，已覆盖 GitHub 硬限制。 */
    private static final int EVENTS_PAGE_LIMIT = 3;
    private static final int EVENTS_PAGE_SIZE = 100;

    @Autowired
    private GithubProperties githubProperties;
    @Autowired
    private GithubEventStatsMapper githubEventStatsMapper;

    /**
     * @param useProxy 为 true 且配置启用代理时走 HTTP 代理；为 false 时强制直连（用于代理未启动时的回退）。
     */
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

    /**
     * 采集并落库用户的 V2 原始指标。
     *
     * <p>若 {@code app.github.proxy-enabled=true} 但本机代理未启动，首次请求会失败；此时会自动<strong>再试一次直连</strong>，
     * 避免因配置代理而完全无法访问 GitHub（毕设环境常见问题）。</p>
     *
     * @return 采集到的 stats；若用户档案不可达则返回 null。
     */
    public GithubEventStats collectAndSave(String githubUsername) {
        if (githubUsername == null || githubUsername.trim().isEmpty()) {
            return null;
        }
        String gh = githubUsername.trim();
        GithubEventStats stats = collectAndSaveInternal(gh, githubProperties.isProxyEnabled());
        if (stats == null && githubProperties.isProxyEnabled()) {
            log.warn("[collector] 已启用代理但采集失败，尝试直连 GitHub 重试 user={}", gh);
            stats = collectAndSaveInternal(gh, false);
        }
        return stats;
    }

    private GithubEventStats collectAndSaveInternal(String gh, boolean useProxy) {
        RestTemplate rt = buildRestTemplate(useProxy);
        HttpEntity<String> entity = buildEntity();

        Map<String, Object> userData = fetchUser(rt, entity, gh);
        if (userData == null) {
            return null;
        }

        GithubEventStats stats = new GithubEventStats();
        stats.setGithubUsername(gh);
        stats.setFollowers(intOf(userData.get("followers")));
        stats.setPublicRepos(intOf(userData.get("public_repos")));
        stats.setActiveDays(computeActiveDays(userData));

        stats.setOpenIssueCount(searchCount(rt, entity,
                "author:" + gh + "+type:issue"));
        stats.setOpenPrCount(searchCount(rt, entity,
                "author:" + gh + "+type:pr"));
        stats.setMergedPrCount(searchCount(rt, entity,
                "author:" + gh + "+type:pr+is:merged"));

        EventAggregate ea = fetchEvents(rt, entity, gh);
        stats.setIssueComment90d(ea.issueComments);
        stats.setPrReview90d(ea.prReviews);
        stats.setPushEvent90d(ea.pushEvents);
        stats.setCloseIssue90d(ea.closeIssues);
        stats.setClosePr90d(ea.closePrs);

        RepoCompliance rc = fetchRepoCompliance(rt, entity, gh);
        stats.setSampledRepoCount(rc.sampled);
        stats.setLicensePresentCount(rc.licensePresent);
        stats.setWorkflowPresentCount(rc.workflowPresent);
        stats.setSecurityPresentCount(rc.securityPresent);
        stats.setRepoStarsTotal(rc.starsTotal);

        upsert(stats);
        return stats;
    }

    // ============================ 明细 API 调用 ============================

    private Map<String, Object> fetchUser(RestTemplate rt, HttpEntity<String> entity, String gh) {
        try {
            ResponseEntity<Map<String, Object>> resp = rt.exchange(
                    "https://api.github.com/users/" + gh,
                    HttpMethod.GET, entity, new ParameterizedTypeReference<Map<String, Object>>() {
                    });
            return resp.getBody();
        } catch (RestClientException e) {
            log.warn("[collector] 用户档案不可达 user={} msg={}", gh, e.getMessage());
            return null;
        }
    }

    /**
     * 调用 {@code /search/issues}，只取 {@code total_count}，单次请求 per_page=1 以节省带宽。
     */
    private int searchCount(RestTemplate rt, HttpEntity<String> entity, String query) {
        try {
            String url = "https://api.github.com/search/issues?per_page=1&q=" + query;
            ResponseEntity<Map<String, Object>> resp = rt.exchange(url,
                    HttpMethod.GET, entity, new ParameterizedTypeReference<Map<String, Object>>() {
                    });
            Map<String, Object> body = resp.getBody();
            if (body == null || body.get("total_count") == null) {
                return 0;
            }
            return ((Number) body.get("total_count")).intValue();
        } catch (HttpClientErrorException.UnprocessableEntity e) {
            // 422 通常是 query 拼错，记录但不中断整个采集
            log.warn("[collector] search 422 query={}", query);
            return 0;
        } catch (RestClientException e) {
            log.warn("[collector] search 失败 query={} msg={}", query, e.getMessage());
            return 0;
        }
    }

    private EventAggregate fetchEvents(RestTemplate rt, HttpEntity<String> entity, String gh) {
        EventAggregate agg = new EventAggregate();
        for (int page = 1; page <= EVENTS_PAGE_LIMIT; page++) {
            String url = "https://api.github.com/users/" + gh
                    + "/events/public?per_page=" + EVENTS_PAGE_SIZE + "&page=" + page;
            List<Map<String, Object>> pageData = fetchListOrEmpty(rt, entity, url);
            if (pageData.isEmpty()) {
                break;
            }
            for (Map<String, Object> ev : pageData) {
                String type = asString(ev.get("type"));
                if (type == null) continue;
                switch (type) {
                    case "IssueCommentEvent":
                        agg.issueComments++;
                        break;
                    case "PullRequestReviewEvent":
                        agg.prReviews++;
                        break;
                    case "PushEvent":
                        agg.pushEvents++;
                        break;
                    case "IssuesEvent": {
                        Object payload = ev.get("payload");
                        if (payload instanceof Map
                                && "closed".equals(asString(((Map<?, ?>) payload).get("action")))) {
                            agg.closeIssues++;
                        }
                        break;
                    }
                    case "PullRequestEvent": {
                        Object payload = ev.get("payload");
                        if (payload instanceof Map
                                && "closed".equals(asString(((Map<?, ?>) payload).get("action")))) {
                            agg.closePrs++;
                        }
                        break;
                    }
                    default:
                        break;
                }
            }
            if (pageData.size() < EVENTS_PAGE_SIZE) {
                break;
            }
        }
        return agg;
    }

    private RepoCompliance fetchRepoCompliance(RestTemplate rt, HttpEntity<String> entity, String gh) {
        RepoCompliance rc = new RepoCompliance();
        String reposUrl = "https://api.github.com/users/" + gh
                + "/repos?per_page=" + REPO_SAMPLE_LIMIT + "&sort=pushed";
        List<Map<String, Object>> repos = fetchListOrEmpty(rt, entity, reposUrl);
        rc.sampled = repos.size();
        for (Map<String, Object> repo : repos) {
            String name = asString(repo.get("name"));
            if (name == null) continue;

            rc.starsTotal += intOf(repo.get("stargazers_count"));

            // license：用户档案里已经带 license 对象（非 null 即视为存在），无需额外请求
            Object license = repo.get("license");
            if (license != null) {
                rc.licensePresent++;
            }

            // workflows：/repos/{u}/{r}/actions/workflows.total_count > 0 即存在
            if (hasWorkflows(rt, entity, gh, name)) {
                rc.workflowPresent++;
            }

            // SECURITY.md：/repos/{u}/{r}/contents/SECURITY.md 返回 200 即存在
            if (hasFile(rt, entity, gh, name, "SECURITY.md")) {
                rc.securityPresent++;
            }
        }
        return rc;
    }

    private boolean hasWorkflows(RestTemplate rt, HttpEntity<String> entity, String owner, String repo) {
        try {
            ResponseEntity<Map<String, Object>> resp = rt.exchange(
                    "https://api.github.com/repos/" + owner + "/" + repo + "/actions/workflows?per_page=1",
                    HttpMethod.GET, entity, new ParameterizedTypeReference<Map<String, Object>>() {
                    });
            Map<String, Object> body = resp.getBody();
            if (body == null || body.get("total_count") == null) return false;
            return ((Number) body.get("total_count")).intValue() > 0;
        } catch (RestClientException e) {
            return false;
        }
    }

    private boolean hasFile(RestTemplate rt, HttpEntity<String> entity,
                            String owner, String repo, String path) {
        try {
            ResponseEntity<String> resp = rt.exchange(
                    "https://api.github.com/repos/" + owner + "/" + repo + "/contents/" + path,
                    HttpMethod.GET, entity, String.class);
            return resp.getStatusCode() == HttpStatus.OK;
        } catch (HttpClientErrorException.NotFound e) {
            return false;
        } catch (RestClientException e) {
            return false;
        }
    }

    private List<Map<String, Object>> fetchListOrEmpty(RestTemplate rt, HttpEntity<String> entity, String url) {
        try {
            ResponseEntity<List<Map<String, Object>>> resp = rt.exchange(Objects.requireNonNull(url, "url"),
                    HttpMethod.GET, entity, new ParameterizedTypeReference<List<Map<String, Object>>>() {
                    });
            List<Map<String, Object>> body = resp.getBody();
            return body == null ? Collections.emptyList() : body;
        } catch (RestClientException e) {
            log.debug("[collector] list fetch failed url={} msg={}", url, e.getMessage());
            return Collections.emptyList();
        }
    }

    // ============================ 工具 ============================

    private void upsert(GithubEventStats stats) {
        QueryWrapper<GithubEventStats> wrapper = new QueryWrapper<>();
        wrapper.eq("github_username", stats.getGithubUsername());
        GithubEventStats exist = githubEventStatsMapper.selectOne(wrapper);
        if (exist == null) {
            githubEventStatsMapper.insert(stats);
        } else {
            stats.setId(exist.getId());
            githubEventStatsMapper.updateById(stats);
        }
    }

    private static long computeActiveDays(Map<String, Object> userData) {
        String createdAt = asString(userData.get("created_at"));
        if (createdAt == null) return 1L;
        try {
            Instant c = Instant.parse(createdAt);
            long days = ChronoUnit.DAYS.between(c, Instant.now());
            return Math.max(1L, days);
        } catch (RuntimeException e) {
            return 1L;
        }
    }

    private static int intOf(Object v) {
        return v instanceof Number ? ((Number) v).intValue() : 0;
    }

    private static String asString(Object v) {
        return v == null ? null : String.valueOf(v);
    }

    private static final class EventAggregate {
        int issueComments;
        int prReviews;
        int pushEvents;
        int closeIssues;
        int closePrs;
    }

    private static final class RepoCompliance {
        int sampled;
        int licensePresent;
        int workflowPresent;
        int securityPresent;
        /** 与 {@link #REPO_SAMPLE_LIMIT} 列表一致的 stargazers_count 之和 */
        int starsTotal;
    }
}
