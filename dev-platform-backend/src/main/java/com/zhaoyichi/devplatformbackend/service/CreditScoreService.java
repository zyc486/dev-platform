package com.zhaoyichi.devplatformbackend.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.zhaoyichi.devplatformbackend.entity.*;
import com.zhaoyichi.devplatformbackend.mapper.*;
import com.zhaoyichi.devplatformbackend.service.credit.CreditScoreV2Service;
import com.zhaoyichi.devplatformbackend.service.credit.CreditThresholdService;
import com.zhaoyichi.devplatformbackend.vo.CreditExplainDetailVO;
import com.zhaoyichi.devplatformbackend.vo.CreditRankItemVO;
import com.zhaoyichi.devplatformbackend.vo.CreditScoreResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class CreditScoreService {
    private static final Logger log = LoggerFactory.getLogger(CreditScoreService.class);

    @Autowired
    private CreditScoreMapper creditScoreMapper;
    @Autowired
    private QueryLogMapper queryLogMapper;
    @Autowired
    private CreditHistoryMapper creditHistoryMapper;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private CollaborationReviewMapper collaborationReviewMapper;
    @Autowired
    private CreditScoreV2Service creditScoreV2Service;
    @Autowired
    private CreditThresholdService creditThresholdService;
    @Autowired
    private com.zhaoyichi.devplatformbackend.mapper.GithubEventStatsMapper githubEventStatsMapper;

    /** 当前系统仅保留 V2。 */
    private static final String ALGO_VERSION = CreditScoreV2Service.ALGO_VERSION;

    // ================= 原有方法（保持不变） =================
    public CreditScore queryByGithub(String githubUsername) {
        if (githubUsername == null || githubUsername.trim().isEmpty()) return null;
        QueryWrapper<CreditScore> wrapper = new QueryWrapper<>();
        wrapper.eq("github_username", githubUsername);
        return creditScoreMapper.selectOne(wrapper);
    }

    public void updateCollaborationScore(String githubUsername, Integer addedScore) {
        if (githubUsername == null || addedScore == null) return;
        CreditScore existing = queryByGithub(githubUsername);
        if (existing != null) {
            Integer current = existing.getCollaboration() == null ? 0 : existing.getCollaboration();
            existing.setCollaboration(current + addedScore);
            Integer currentTotal = existing.getTotalScore() == null ? 0 : existing.getTotalScore();
            existing.setTotalScore(currentTotal + addedScore);
            creditScoreMapper.updateById(existing);
        } else {
            CreditScore newScore = new CreditScore();
            newScore.setGithubUsername(githubUsername);
            newScore.setCollaboration(addedScore);
            newScore.setTotalScore(addedScore);
            creditScoreMapper.insert(newScore);
        }
    }

    // ================= 两参数重载（兼容旧调用） =================
    public CreditScoreResult queryCredit(String githubUsername, String scene) {
        return queryCredit(githubUsername, scene, null);
    }

    // ================= 核心查询方法（带日志保存） =================
    public CreditScoreResult queryCredit(String githubUsername, String scene, Integer currentUserId) {
        long startTime = System.currentTimeMillis();
        String status = "success";
        Integer totalScoreSnapshot = null;
        CreditScoreResult result = null;
        String normalizedScene = normalizeScene(scene);
        try {
            QueryWrapper<CreditScore> wrapper = new QueryWrapper<>();
            wrapper.eq("github_username", githubUsername);
            wrapper.eq("scene", normalizedScene);
            wrapper.eq("algo_version", ALGO_VERSION);
            CreditScore creditScore = creditScoreMapper.selectOne(wrapper);
            if (creditScore == null) {
                creditScore = fetchAndCalculateFromGithub(githubUsername, normalizedScene);
                if (creditScore == null) {
                    status = "fail";
                    return null;
                }
            } else {
                // 库内已有 credit_score 时仍可能残留「算法升级前」的四维快照；用本地 stats 按当前公式重算并写回
                try {
                    if (creditScoreV2Service.recalculateFromCachedStats(githubUsername, normalizedScene)) {
                        creditScore = creditScoreMapper.selectOne(wrapper);
                    }
                } catch (Exception ex) {
                    log.warn("[credit] 基于本地 stats 重算失败 githubUsername={} scene={}", githubUsername, normalizedScene, ex);
                }
            }
            syncCreditTotalWithV2Policy(creditScore, normalizedScene);
            totalScoreSnapshot = creditScore.getTotalScore();
            result = new CreditScoreResult();
            BeanUtils.copyProperties(creditScore, result);
            result.setLevel(creditThresholdService.classify(result.getTotalScore(), normalizedScene, ALGO_VERSION));
            return result;
        } catch (Exception e) {
            status = "fail";
            throw e;
        } finally {
            long cost = System.currentTimeMillis() - startTime;
            // 仅记录「已登录用户」在显式查询路径下的行为；洞察/对比等内部读路径传 userId=null，不应污染 query_log
            if (currentUserId != null) {
                try {
                    saveQueryLog(githubUsername, normalizedScene, currentUserId, cost, status, totalScoreSnapshot);
                } catch (Exception ex) {
                    log.warn("[query_log] 写入失败 githubUsername={} userId={}", githubUsername, currentUserId, ex);
                }
            }
        }
    }

    /**
     * 按当前 V2 规则（加权基准 + 单维卓越补偿 + 判级）校正库内总分/等级，避免旧快照与线上策略长期不一致。
     */
    private void syncCreditTotalWithV2Policy(CreditScore cs, String normalizedScene) {
        if (cs == null) {
            return;
        }
        int st = safeScore(cs.getStability());
        int pr = safeScore(cs.getPrQuality());
        int col = safeScore(cs.getCollaboration());
        int comp = safeScore(cs.getCompliance());
        int base = creditScoreV2Service.weightedTotal(st, pr, col, comp, normalizedScene);
        int synced = creditScoreV2Service.applyExcellenceBoost(st, pr, col, comp, base);
        String lvl = creditThresholdService.classify(synced, normalizedScene, ALGO_VERSION);
        Integer oldTot = cs.getTotalScore();
        if (!Objects.equals(oldTot, synced) || !Objects.equals(lvl, cs.getLevel())) {
            cs.setTotalScore(synced);
            cs.setLevel(lvl);
            try {
                if (cs.getId() != null) {
                    creditScoreMapper.updateById(cs);
                }
            } catch (Exception e) {
                log.warn("[credit_score] 同步总分/等级失败 githubUsername={}", cs.getGithubUsername(), e);
            }
        }
    }

    // 保存查询日志
    private void saveQueryLog(String githubUsername, String scene, Integer userId, long responseTime, String status, Integer totalScore) {
        QueryLog log = new QueryLog();
        log.setGithubUsername(githubUsername);
        log.setScene(scene);
        log.setUserId(userId);
        log.setResponseTime((int) responseTime);
        log.setStatus(status);
        log.setTotalScore(totalScore);
        queryLogMapper.insert(log);
    }

    // ================= 信用历史趋势（支持场景） =================
    public List<CreditHistory> getCreditTrend(String githubUsername, String scene, int months) {
        String normalizedScene = normalizeScene(scene);
        QueryWrapper<User> userQuery = new QueryWrapper<>();
        userQuery.eq("github_username", githubUsername);
        User user = userMapper.selectOne(userQuery);
        if (user == null) return new ArrayList<>();
        LocalDate startDate = LocalDate.now().minusMonths(months);
        QueryWrapper<CreditHistory> wrapper = new QueryWrapper<>();
        wrapper.eq("user_id", user.getId())
                .ge("record_date", startDate)
                .orderByAsc("record_date");
        if (!"综合".equals(normalizedScene)) {
            wrapper.eq("scene", normalizedScene);
        }
        return creditHistoryMapper.selectList(wrapper);
    }

    // ================= 获取查询历史 =================
    public List<QueryLog> getQueryHistory(Integer userId) {
        QueryWrapper<QueryLog> wrapper = new QueryWrapper<>();
        wrapper.eq("user_id", userId).orderByDesc("query_time").last("LIMIT 50");
        return queryLogMapper.selectList(wrapper);
    }

    // ================= 定时保存信用快照（每月1号凌晨3点） =================
    @Scheduled(cron = "0 0 3 1 * ?")
    public void saveMonthlyCreditSnapshot() {
        List<CreditScore> allScores = creditScoreMapper.selectList(null);
        int saved = 0;
        for (CreditScore score : allScores) {
            if (score.getUserId() == null || score.getUserId() == 0) continue;
            CreditHistory history = new CreditHistory();
            history.setUserId(score.getUserId());
            history.setTotalScore(score.getTotalScore());
            history.setStability(score.getStability());
            history.setPrQuality(score.getPrQuality());
            history.setCollaboration(score.getCollaboration());
            history.setCompliance(score.getCompliance());
            history.setScene(score.getScene());
            history.setRecordDate(LocalDate.now());
            history.setAlgoVersion(score.getAlgoVersion() == null ? ALGO_VERSION : score.getAlgoVersion());
            creditHistoryMapper.insert(history);
            saved++;
        }
        log.info("信用快照保存完成 count={}", saved);
    }

    // ================= GitHub 实时爬取 + 协作互评加权 =================
    private CreditScore fetchAndCalculateFromGithub(String githubUsername, String scene) {
        try {
            return creditScoreV2Service.fetchAndCalculate(githubUsername, normalizeScene(scene));
        } catch (RuntimeException e) {
            log.warn("[v2] 评分失败 githubUsername={} scene={}", githubUsername, scene, e);
            return null;
        }
    }

    /**
     * V2 版可解释性明细：原始指标直接呈现 OI/OP/MP/IC/PRR 等文献标准字段，
     * 公式摘要标注文献来源，等级走 ThresholdService（分位数优先，样本不足回退静态）。
     */
    private CreditExplainDetailVO buildCreditExplainDetailV2(Long platformUserId, String gh, String scene) {
        CreditExplainDetailVO vo = new CreditExplainDetailVO();
        vo.setUserId(platformUserId);
        vo.setGithubUsername(gh);
        vo.setScene(scene);

        Map<String, Object> reviewSummary = new LinkedHashMap<>();
        ReviewAggregate agg = loadReviewAggregate(platformUserId);
        reviewSummary.put("receivedCount", agg.count);
        reviewSummary.put("avgRating", agg.count > 0 ? Math.round(agg.avgRating * 100.0) / 100.0 : null);
        vo.setCollaborationReviewSummary(reviewSummary);

        CreditScore cs;
        try {
            cs = creditScoreV2Service.fetchAndCalculate(gh, scene);
        } catch (RuntimeException e) {
            log.warn("[v2-explain] 实时计算失败 gh={} scene={}", gh, scene, e);
            cs = null;
        }
        if (cs == null) {
            // GitHub 不可达时回退读取最近一次 V2 快照
            cs = creditScoreMapper.selectOne(new QueryWrapper<CreditScore>()
                    .eq("github_username", gh).eq("scene", scene));
            if (cs == null) {
                vo.setGithubDataUnavailable(true);
                vo.setDegradedMessage(degradedGithubExplain(false, null));
                vo.setImprovementSuggestions(Collections.singletonList(
                        "请检查 application.yml 中 app.github.token 是否已配置、代理 app.github.proxy-enabled 是否正确，并在网络可达后重新查询或点击「刷新信用」。"));
                return vo;
            }
            vo.setGithubDataUnavailable(true);
            vo.setDegradedMessage(degradedGithubExplain(true, cs.getAlgoVersion()));
        } else {
            vo.setGithubDataUnavailable(false);
        }

        vo.setTotalScore(cs.getTotalScore());
        vo.setLevel(cs.getLevel());
        // 库内可能残留历史等级文案（如字母档），与 v2 分位数规则不一致时，降级展示统一按当前 v2 规则重算等级（仅影响 VO，不写库）
        if (Boolean.TRUE.equals(vo.getGithubDataUnavailable()) && vo.getTotalScore() != null) {
            vo.setLevel(creditThresholdService.classify(vo.getTotalScore(), scene, CreditScoreV2Service.ALGO_VERSION));
        }

        double[] w = creditScoreV2Service.loadSceneWeights(scene);
        int stability = cs.getStability() == null ? 0 : cs.getStability();
        int prQuality = cs.getPrQuality() == null ? 0 : cs.getPrQuality();
        int collaboration = cs.getCollaboration() == null ? 0 : cs.getCollaboration();
        int compliance = cs.getCompliance() == null ? 0 : cs.getCompliance();

        vo.setDimensions(buildV2DimensionLines(gh, stability, prQuality, collaboration, compliance, w));
        // 降级时库内 algo_version 可能仍为历史 v1，但界面按当前服务端 v2 公式展示各维度；避免读者误以为「仍是 v1 算法」
        if (Boolean.TRUE.equals(vo.getGithubDataUnavailable())) {
            String stored = cs.getAlgoVersion() == null ? "未标注" : cs.getAlgoVersion();
            vo.setAlgoVersion("服务端v2（加权/维度按 v2）；库内 algo_version=" + stored + "（历史字段，下次成功拉 GitHub 后会写为 v2）");
        } else {
            vo.setAlgoVersion(cs.getAlgoVersion() == null ? "v2" : cs.getAlgoVersion());
        }
        vo.setWeightedTotalExplanation(buildWeightedTotalExplanation(w[0], w[1], w[2], w[3],
                stability, prQuality, collaboration, compliance, vo.getTotalScore() == null ? 0 : vo.getTotalScore()));
        GithubEventStats eventStats = loadEventStats(gh);
        vo.setImprovementSuggestions(buildImprovementSuggestions(stability, prQuality, collaboration, compliance, agg, eventStats));
        return vo;
    }

    private List<CreditExplainDetailVO.CreditDimensionLineVO> buildV2DimensionLines(
            String gh, int stab, int pr, int coll, int comp, double[] w) {
        List<CreditExplainDetailVO.CreditDimensionLineVO> lines = new ArrayList<>();

        // 从 github_event_stats 读原始指标
        GithubEventStats stats = loadEventStats(gh);

        CreditExplainDetailVO.CreditDimensionLineVO l1 = new CreditExplainDetailVO.CreditDimensionLineVO();
        l1.setCode("stability");
        l1.setDisplayName("账号成熟度（V2）");
        l1.setDimensionScore(stab);
        l1.setWeight(w[0]);
        l1.setFormulaSummary("100·σ(k·(Σwᵢ·z_i − 0.5))；z_1=log(1+活跃天数)/log(3651), z_2=log(1+90d Push)/log(201), z_3=PR合并率。权重 0.30/0.40/0.30 源自文献 H 相关系数先验 + 文献 E 全域均值；Sigmoid k=6 源自文献 I。");
        l1.setRawIndicators(rawStability(stats));
        l1.setWeightedContribution(stab * w[0]);
        lines.add(l1);

        CreditExplainDetailVO.CreditDimensionLineVO l2 = new CreditExplainDetailVO.CreditDimensionLineVO();
        l2.setCode("prQuality");
        l2.setDisplayName("代码贡献质量（V2）");
        l2.setDimensionScore(pr);
        l2.setWeight(w[1]);
        l2.setFormulaSummary("0.50·PR合并率(MP/OP) + 0.30·log(1+PRR_90d)/log(51) + 0.20·CI存在率；权重取 T/CESA 附录 B MP/PRR/OP 归一化 (0.50, 0.30, 0.20)，合并率依据文献 E（ICSE 2014 全域均值 55%）。");
        l2.setRawIndicators(rawPrQuality(stats));
        l2.setWeightedContribution(pr * w[1]);
        lines.add(l2);

        CreditExplainDetailVO.CreditDimensionLineVO l3 = new CreditExplainDetailVO.CreditDimensionLineVO();
        l3.setCode("collaboration");
        l3.setDisplayName("协作度（V2：行为 AHP + 社区认可度）");
        l3.setDimensionScore(coll);
        l3.setWeight(w[2]);
        l3.setFormulaSummary("协作分 = 0.60×S_behavior + 0.40×S_recognition；S_behavior 为 ICSE-SEIP 2024 Table 2 六行为项对数归一化加权和（CR=0.027）；S_recognition 为 followers 与采样仓库 stargazers 合计的对数归一化混合（与文献 H「Star 与活跃度」互补）。站内互评另在融合阶段上限 50%。");
        l3.setRawIndicators(rawCollaboration(stats));
        l3.setWeightedContribution(coll * w[2]);
        lines.add(l3);

        CreditExplainDetailVO.CreditDimensionLineVO l4 = new CreditExplainDetailVO.CreditDimensionLineVO();
        l4.setCode("compliance");
        l4.setDisplayName("规范合规性（V2，多属性）");
        l4.setDimensionScore(comp);
        l4.setWeight(w[3]);
        l4.setFormulaSummary("0.30·License率 + 0.25·Security(SECURITY.md)率 + 0.25·PR评审率 + 0.20·Workflow(CI)率；四属性分解源自文献 F（孙晶等 2017 可信性 5 属性）。CVE 因 API 权限约束降级为 SECURITY.md 存在率。");
        l4.setRawIndicators(rawCompliance(stats));
        l4.setWeightedContribution(comp * w[3]);
        lines.add(l4);

        return lines;
    }

    private GithubEventStats loadEventStats(String gh) {
        try {
            return githubEventStatsMapper.selectOne(new QueryWrapper<GithubEventStats>().eq("github_username", gh));
        } catch (RuntimeException e) {
            return null;
        }
    }

    private Map<String, Object> rawStability(GithubEventStats s) {
        Map<String, Object> m = new LinkedHashMap<>();
        if (s == null) {
            m.put("note", "github_event_stats 无行或未更新：通常因本次连接 GitHub 失败，尚未执行 V2 采集落库。配置 Token 并保证网络/代理后再查。");
            return m;
        }
        m.put("activeDays", s.getActiveDays());
        m.put("push_90d", s.getPushEvent90d());
        m.put("OP", s.getOpenPrCount());
        m.put("MP", s.getMergedPrCount());
        int op = s.getOpenPrCount() == null ? 0 : s.getOpenPrCount();
        int mp = s.getMergedPrCount() == null ? 0 : s.getMergedPrCount();
        m.put("mergeRate(%)", op > 0 ? Math.round(mp * 10000.0 / op) / 100.0 : null);
        return m;
    }

    private Map<String, Object> rawPrQuality(GithubEventStats s) {
        Map<String, Object> m = new LinkedHashMap<>();
        if (s == null) {
            m.put("note", "github_event_stats 无行或未更新：通常因本次连接 GitHub 失败。见稳定性维度说明。");
            return m;
        }
        m.put("OP", s.getOpenPrCount());
        m.put("MP", s.getMergedPrCount());
        m.put("PRR_90d", s.getPrReview90d());
        m.put("workflowPresent/sampledRepo",
                (s.getWorkflowPresentCount() == null ? 0 : s.getWorkflowPresentCount())
                        + "/" + (s.getSampledRepoCount() == null ? 0 : s.getSampledRepoCount()));
        return m;
    }

    private Map<String, Object> rawCollaboration(GithubEventStats s) {
        Map<String, Object> m = new LinkedHashMap<>();
        if (s == null) {
            m.put("note", "github_event_stats 无行或未更新：通常因本次连接 GitHub 失败。见稳定性维度说明。");
            return m;
        }
        m.put("OI", s.getOpenIssueCount());
        m.put("OP", s.getOpenPrCount());
        m.put("IC_90d", s.getIssueComment90d());
        m.put("PRR_90d", s.getPrReview90d());
        m.put("CloseIssue_90d", s.getCloseIssue90d());
        m.put("ClosePR_90d", s.getClosePr90d());
        m.put("followers", s.getFollowers());
        m.put("repoStarsTotal", s.getRepoStarsTotal());
        return m;
    }

    private Map<String, Object> rawCompliance(GithubEventStats s) {
        Map<String, Object> m = new LinkedHashMap<>();
        if (s == null) {
            m.put("note", "github_event_stats 无行或未更新：通常因本次连接 GitHub 失败。见稳定性维度说明。");
            return m;
        }
        m.put("sampledRepoCount", s.getSampledRepoCount());
        m.put("licensePresent", s.getLicensePresentCount());
        m.put("workflowPresent", s.getWorkflowPresentCount());
        m.put("securityPresent", s.getSecurityPresentCount());
        return m;
    }

    /**
     * GitHub 档案经同样公式重算后写回该账号下所有场景行，并在存在「综合」场景时同步 user 表摘要分。
     *
     * @return false 表示 GitHub 不可达或未生成任何 credit_score 行
     */
    public boolean refreshGithubUserCreditScores(String githubUsername) {
        if (githubUsername == null || githubUsername.trim().isEmpty()) {
            return false;
        }
        String gh = githubUsername.trim();
        boolean anyOk = false;
        for (String scene : new String[]{"综合", "核心开发者", "辅助贡献"}) {
            try {
                CreditScore cs = creditScoreV2Service.fetchAndCalculate(gh, scene);
                if (cs != null) anyOk = true;
            } catch (RuntimeException e) {
                log.warn("[v2] 刷新失败 scene={} gh={}", scene, gh, e);
            }
        }
        return anyOk;
    }

    public enum ManualRefreshResult {
        SUCCESS,
        NO_GITHUB,
        GITHUB_UNAVAILABLE
    }

    /**
     * 手动刷新：拉 GitHub 重算各场景 credit_score，并写入当日 credit_history 快照（与定时任务结构一致）。
     */
    public ManualRefreshResult manualRefreshCreditForUser(Long userId) {
        if (userId == null) {
            return ManualRefreshResult.NO_GITHUB;
        }
        User user = userMapper.selectById(userId);
        if (user == null || user.getGithubUsername() == null || user.getGithubUsername().trim().isEmpty()) {
            return ManualRefreshResult.NO_GITHUB;
        }
        String gh = user.getGithubUsername().trim();
        boolean ok = refreshGithubUserCreditScores(gh);
        if (!ok) {
            return ManualRefreshResult.GITHUB_UNAVAILABLE;
        }
        appendHistorySnapshotsForUser(userId.intValue(), gh);
        return ManualRefreshResult.SUCCESS;
    }

    private void appendHistorySnapshotsForUser(int userId, String githubUsername) {
        QueryWrapper<CreditScore> w = new QueryWrapper<>();
        w.eq("github_username", githubUsername);
        List<CreditScore> scores = creditScoreMapper.selectList(w);
        for (CreditScore score : scores) {
            CreditHistory history = new CreditHistory();
            history.setUserId(userId);
            history.setTotalScore(score.getTotalScore());
            history.setStability(score.getStability());
            history.setPrQuality(score.getPrQuality());
            history.setCollaboration(score.getCollaboration());
            history.setCompliance(score.getCompliance());
            history.setScene(score.getScene());
            history.setRecordDate(LocalDate.now());
            history.setAlgoVersion(score.getAlgoVersion() == null ? ALGO_VERSION : score.getAlgoVersion());
            creditHistoryMapper.insert(history);
        }
    }

    /**
     * 可解释性明细：优先实时拉取 GitHub；失败则降级为库内快照并提示。
     */
    public CreditExplainDetailVO buildCreditExplainDetail(Long userId, String sceneParam) {
        User user = userMapper.selectById(userId);
        if (user == null || user.getGithubUsername() == null || user.getGithubUsername().trim().isEmpty()) {
            return null;
        }
        String gh = user.getGithubUsername().trim();
        String scene = normalizeScene(sceneParam);
        return buildCreditExplainDetailV2(user.getId(), gh, scene);
    }

    /**
     * 按 GitHub 账号生成可解释性快照（无需登录），供导出详细报告、对比页后台使用。
     * 若该账号已绑定本站用户，会合并站内互评；否则仅 GitHub 侧指标。
     */
    public CreditExplainDetailVO buildCreditExplainExportSnapshot(String githubUsername, String sceneParam) {
        if (githubUsername == null || githubUsername.trim().isEmpty()) {
            return null;
        }
        String gh = githubUsername.trim();
        String scene = normalizeScene(sceneParam);
        User owner = userMapper.selectOne(new QueryWrapper<User>().eq("github_username", gh));
        Long uid = owner == null ? null : owner.getId();
        return buildCreditExplainDetailV2(uid, gh, scene);
    }

    /**
     * 说明为何进入「降级展示」：与代码路径 {@code CreditScoreV2Service#fetchAndCalculate}
     * 在 {@code GithubMetricsCollector#collectAndSave} 首步拉取用户档案失败时返回 null 一致。
     */
    private static String degradedGithubExplain(boolean hasDbSnapshot, String storedAlgoVersion) {
        String common = "本次未能从 GitHub 获取该用户最新指标（采集入口含：GET /users/{login}，以及 search/issues、events、仓库元数据等）。"
                + "常见原因：① 运行环境访问 api.github.com 失败（网络、防火墙）；② application.yml 中 app.github.proxy-enabled / proxy-host / proxy-port 配置不当；"
                + "③ app.github.token 未配置、过期或权限不足；④ 触发 GitHub API 频率限制（HTTP 403/429）；⑤ 该 GitHub 用户名不存在。";
        if (hasDbSnapshot) {
            String av = storedAlgoVersion == null ? "未标注" : storedAlgoVersion;
            return common + " 当前四维分、总分与评级来自数据库 credit_score 的历史缓存（库内 algo_version=" + av + "）。"
                    + "github_event_stats 未能在本次会话中更新时，各维度「原始/派生指标」会显示暂无快照——并非公式缺失。";
        }
        return common + " 数据库中也无该场景下的信用记录，无法展示分数。";
    }

    private static String buildWeightedTotalExplanation(double wSt, double wPr, double wCo, double wCp,
                                                        int stability, int prQuality, int collaboration, int compliance,
                                                        int totalRounded) {
        double raw = wSt * stability + wPr * prQuality + wCo * collaboration + wCp * compliance;
        int baseRounded = (int) Math.round(raw);
        int dimSum = stability + prQuality + collaboration + compliance;
        if (totalRounded > baseRounded) {
            int boost = totalRounded - baseRounded;
            return String.format(java.util.Locale.CHINA,
                    "加权基准分 = round(稳定性×%.4f + PR质量×%.4f + 协作×%.4f + 合规×%.4f) = round(%.2f) = %d；"
                            + "单维卓越补偿 +%d → 综合总分 %d。四维裸分相加=%d，与加权/补偿口径不可直接等同。",
                    wSt, wPr, wCo, wCp, raw, baseRounded, boost, totalRounded, dimSum);
        }
        return String.format(java.util.Locale.CHINA,
                "综合总分 = round(稳定性×%.4f + PR质量×%.4f + 协作×%.4f + 合规×%.4f) = round(%.2f) = %d。"
                        + "（未触发单维卓越补偿。）四维裸分相加=%d，一般不等于加权分。",
                wSt, wPr, wCo, wCp, raw, totalRounded, dimSum);
    }

    /**
     * 根据库中 {@link CreditScore} 与当前场景权重，生成与「总分」一致的加权说明（CSV 导出等复用）。
     */
    public String formatWeightedTotalForRow(CreditScore cs, String normalizedScene) {
        if (cs == null) {
            return "";
        }
        int st = safeScore(cs.getStability());
        int pr = safeScore(cs.getPrQuality());
        int col = safeScore(cs.getCollaboration());
        int comp = safeScore(cs.getCompliance());
        int tot = safeScore(cs.getTotalScore());
        double[] w = creditScoreV2Service.loadSceneWeights(normalizedScene);
        return buildWeightedTotalExplanation(w[0], w[1], w[2], w[3], st, pr, col, comp, tot);
    }

    private List<String> buildImprovementSuggestions(int stability, int prQuality, int collaboration, int compliance,
                                                     ReviewAggregate agg, GithubEventStats githubStats) {
        List<String> list = new ArrayList<>();
        if (stability < 70) {
            list.add("稳定性偏低：保持账号长期活跃并适当增加公开贡献频率，有助于提升账号成熟度得分。");
        }
        if (prQuality < 70) {
            list.add("代码贡献质量偏低：提升 PR 合并率与评审参与度，并完善 CI 流程（如 GitHub Actions）。");
        }
        if (collaboration < 70) {
            int fo = githubStats == null || githubStats.getFollowers() == null ? 0 : githubStats.getFollowers();
            int stars = githubStats == null || githubStats.getRepoStarsTotal() == null ? 0 : githubStats.getRepoStarsTotal();
            if (fo < 5_000 && stars < 8_000) {
                list.add("协作维度偏低：参与更多 Issue/PR 协作（评论、评审、关闭），并在站内协作后获取同伴 4～5 星互评。");
            } else {
                list.add("近 90 天 Issue/PR 互动计数偏低，但粉丝或仓库星标已反映较高社区能见度；若希望本维分数更高，可适当增加公开的评审与讨论（非必须）。");
            }
        }
        if (compliance < 70) {
            list.add("合规维度偏低：为仓库补齐 LICENSE、SECURITY.md 与 CI workflows，并提高评审覆盖率。");
        }
        if (agg.count < 3) {
            list.add("互评样本较少：每完成一次协作请邀请对方评价，评价条数达到 3～5 条后互评权重会更稳定。");
        }
        if (list.isEmpty()) {
            list.add("各维度相对均衡：可继续保持公开贡献节奏，并定期在站内刷新信用快照。");
        }
        return list;
    }

    private ReviewAggregate loadReviewAggregate(Long platformUserId) {
        ReviewAggregate ra = new ReviewAggregate();
        if (platformUserId == null) {
            return ra;
        }
        Map<String, Object> m = collaborationReviewMapper.aggregateRatingForUser(platformUserId);
        if (m == null || m.isEmpty()) {
            return ra;
        }
        Object avg = null;
        Object cnt = null;
        for (Map.Entry<String, Object> e : m.entrySet()) {
            if (e.getKey() == null || e.getValue() == null) {
                continue;
            }
            if (e.getKey().equalsIgnoreCase("avgRating")) {
                avg = e.getValue();
            }
            if (e.getKey().equalsIgnoreCase("cnt")) {
                cnt = e.getValue();
            }
        }
        if (avg instanceof Number) {
            ra.avgRating = ((Number) avg).doubleValue();
        }
        if (cnt instanceof Number) {
            ra.count = ((Number) cnt).intValue();
        }
        return ra;
    }

    private static final class ReviewAggregate {
        double avgRating;
        int count;
    }

    public List<CreditScore> queryByScene(String scene, int minScore, int limit) {
        QueryWrapper<CreditScore> wrapper = new QueryWrapper<>();
        wrapper.eq("scene", normalizeScene(scene)).eq("algo_version", ALGO_VERSION).ge("total_score", minScore)
                .orderByDesc("total_score").last("LIMIT " + Math.min(limit, 100));
        return creditScoreMapper.selectList(wrapper);
    }

    // ================= 排行榜 =================
    public List<CreditScore> getRankList(String scene, int limit) {
        QueryWrapper<CreditScore> wrapper = new QueryWrapper<>();
        wrapper.eq("scene", normalizeScene(scene)).eq("algo_version", ALGO_VERSION);
        wrapper.orderByDesc("total_score").last("LIMIT " + limit);
        return creditScoreMapper.selectList(wrapper);
    }

    public List<CreditRankItemVO> getAdvancedRankList(String scene,
                                                      String tag,
                                                      String level,
                                                      Integer minScore,
                                                      Integer maxScore,
                                                      int limit,
                                                      boolean siteOnly) {
        int safeLimit = Math.max(1, Math.min(limit, 100));
        QueryWrapper<CreditScore> wrapper = new QueryWrapper<>();
        wrapper.eq("scene", normalizeScene(scene)).eq("algo_version", ALGO_VERSION);
        if (level != null && !level.trim().isEmpty()) {
            wrapper.eq("level", level.trim());
        }
        if (minScore != null) {
            wrapper.ge("total_score", minScore);
        }
        if (maxScore != null) {
            wrapper.le("total_score", maxScore);
        }
        // 本站榜只保留「有 user 行」的记录，需多扫一些行再过滤，避免结果条数过少
        int fetchCap = siteOnly ? 500 : 100;
        wrapper.orderByDesc("total_score").last("LIMIT " + fetchCap);
        List<CreditScore> scores = creditScoreMapper.selectList(wrapper);
        Map<String, User> userMap = loadUsersByGithubUsernames(
                scores.stream().map(CreditScore::getGithubUsername).collect(Collectors.toList())
        );

        List<CreditRankItemVO> result = new ArrayList<>();
        for (CreditScore score : scores) {
            User user = userMap.get(score.getGithubUsername());
            if (siteOnly && user == null) {
                continue;
            }
            if (!matchesTag(tag, user)) {
                continue;
            }
            result.add(buildRankItem(score, user));
            if (result.size() >= safeLimit) {
                break;
            }
        }
        return result;
    }

    public List<Map<String, Object>> compareUsers(List<String> usernames, String scene) {
        String normalizedScene = normalizeScene(scene);
        List<Map<String, Object>> result = new ArrayList<>();
        for (String username : usernames) {
            CreditScoreResult score = queryCredit(username, normalizedScene);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("githubUsername", username);
            if (score != null) {
                item.put("totalScore", score.getTotalScore());
                item.put("level", score.getLevel());
                item.put("stability", score.getStability());
                item.put("prQuality", score.getPrQuality());
                item.put("collaboration", score.getCollaboration());
                item.put("compliance", score.getCompliance());
                item.put("scene", score.getScene());
                item.put("trend", getCreditTrend(username, normalizedScene, 6));
            } else {
                item.put("error", "未找到信用档案");
            }
            result.add(item);
        }
        return result;
    }

    public Map<String, Object> getInsights(String githubUsername, String scene) {
        String normalizedScene = normalizeScene(scene);
        CreditScoreResult score = queryCredit(githubUsername, normalizedScene);
        if (score == null) {
            return null;
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("githubUsername", githubUsername);
        result.put("scene", normalizedScene);
        result.put("totalScore", score.getTotalScore());
        result.put("level", score.getLevel());
        result.put("algoVersion", score.getAlgoVersion() == null || score.getAlgoVersion().isEmpty() ? ALGO_VERSION : score.getAlgoVersion());

        int stability = safeScore(score.getStability());
        int prQuality = safeScore(score.getPrQuality());
        int collaboration = safeScore(score.getCollaboration());
        int compliance = safeScore(score.getCompliance());

        Map<String, Object> dimensions = new LinkedHashMap<>();
        dimensions.put("stability", stability);
        dimensions.put("prQuality", prQuality);
        dimensions.put("collaboration", collaboration);
        dimensions.put("compliance", compliance);
        result.put("dimensions", dimensions);

        // 占比：按当前场景权重下的「加权贡献」占加权总和的比例（与总分口径一致，避免四维简单加总误导）
        double[] w = creditScoreV2Service.loadSceneWeights(normalizedScene);
        double c0 = w[0] * stability;
        double c1 = w[1] * prQuality;
        double c2 = w[2] * collaboration;
        double c3 = w[3] * compliance;
        double wsum = c0 + c1 + c2 + c3;
        if (wsum <= 0) {
            wsum = 1;
        }

        Map<String, Double> ratios = new LinkedHashMap<>();
        ratios.put("stability", Math.round(c0 * 10000.0 / wsum) / 100.0);
        ratios.put("prQuality", Math.round(c1 * 10000.0 / wsum) / 100.0);
        ratios.put("collaboration", Math.round(c2 * 10000.0 / wsum) / 100.0);
        ratios.put("compliance", Math.round(c3 * 10000.0 / wsum) / 100.0);
        result.put("ratios", ratios);
        result.put("trend", getCreditTrend(githubUsername, normalizedScene, 6));
        return result;
    }

    /** 与查询/对比一致的场景名归一化，供导出等接口复用。 */
    public String normalizeScene(String scene) {
        if (scene == null || scene.trim().isEmpty() || "all".equalsIgnoreCase(scene)) {
            return "综合";
        }
        String normalized = scene.trim();
        if ("default".equalsIgnoreCase(normalized)) {
            return "综合";
        }
        if ("综合全维".equals(normalized)) {
            return "综合";
        }
        if ("core".equalsIgnoreCase(normalized)
                || "核心开发者".equals(normalized)
                || "后端".equals(normalized)
                || "前端".equals(normalized)
                || "后端工程".equals(normalized)
                || "前端表现".equals(normalized)) {
            return "核心开发者";
        }
        if ("assist".equalsIgnoreCase(normalized)
                || "辅助贡献".equals(normalized)
                || "协作".equals(normalized)
                || "合规".equals(normalized)
                || "协作活跃度".equals(normalized)
                || "开源合规".equals(normalized)) {
            return "辅助贡献";
        }
        if ("项目开发者".equals(normalized)) {
            return "核心开发者";
        }
        if ("质量开发者".equals(normalized)) {
            return "辅助贡献";
        }
        return normalized;
    }

    private CreditRankItemVO buildRankItem(CreditScore score, User user) {
        CreditRankItemVO item = new CreditRankItemVO();
        item.setUserId(user == null ? null : user.getId());
        item.setUsername(user == null ? null : user.getUsername());
        item.setGithubUsername(score.getGithubUsername());
        item.setNickname(user == null ? null : user.getNickname());
        item.setAvatar(user == null ? null : user.getAvatar());
        item.setTechTags(user == null ? null : user.getTechTags());
        item.setTotalScore(safeScore(score.getTotalScore()));
        item.setLevel(score.getLevel());
        item.setScene(score.getScene());
        item.setStability(safeScore(score.getStability()));
        item.setPrQuality(safeScore(score.getPrQuality()));
        item.setCollaboration(safeScore(score.getCollaboration()));
        item.setCompliance(safeScore(score.getCompliance()));
        return item;
    }

    private Map<String, User> loadUsersByGithubUsernames(List<String> githubUsernames) {
        Set<String> uniqueUsernames = githubUsernames == null
                ? Collections.emptySet()
                : githubUsernames.stream()
                .filter(name -> name != null && !name.trim().isEmpty())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (uniqueUsernames.isEmpty()) {
            return Collections.emptyMap();
        }
        QueryWrapper<User> wrapper = new QueryWrapper<>();
        wrapper.in("github_username", uniqueUsernames);
        return userMapper.selectList(wrapper).stream()
                .filter(user -> user.getGithubUsername() != null && !user.getGithubUsername().trim().isEmpty())
                .collect(Collectors.toMap(User::getGithubUsername, Function.identity(), (left, right) -> left));
    }

    private boolean matchesTag(String tag, User user) {
        if (tag == null || tag.trim().isEmpty()) {
            return true;
        }
        if (user == null || user.getTechTags() == null || user.getTechTags().trim().isEmpty()) {
            return false;
        }
        String normalizedTags = user.getTechTags().replace('，', ',');
        // 支持传多个关键词：tag=Vue,React,TypeScript（任意命中即可）
        String[] keywords = tag.replace('|', ',').split(",");
        String tagsLower = normalizedTags.toLowerCase(Locale.ROOT);
        for (String kw : keywords) {
            String k = kw == null ? "" : kw.trim().toLowerCase(Locale.ROOT);
            if (k.isEmpty()) continue;
            for (String item : normalizedTags.split(",")) {
                if (item != null && item.trim().toLowerCase(Locale.ROOT).contains(k)) {
                    return true;
                }
            }
            if (tagsLower.contains(k)) {
                return true;
            }
        }
        return false;
    }

    private int safeScore(Integer value) {
        return value == null ? 0 : value;
    }
}