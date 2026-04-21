package com.zhaoyichi.devplatformbackend.service.credit;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.zhaoyichi.devplatformbackend.entity.CreditScore;
import com.zhaoyichi.devplatformbackend.entity.GithubEventStats;
import com.zhaoyichi.devplatformbackend.entity.SceneWeightConfig;
import com.zhaoyichi.devplatformbackend.entity.User;
import com.zhaoyichi.devplatformbackend.mapper.CollaborationReviewMapper;
import com.zhaoyichi.devplatformbackend.mapper.CreditScoreMapper;
import com.zhaoyichi.devplatformbackend.mapper.GithubEventStatsMapper;
import com.zhaoyichi.devplatformbackend.mapper.SceneWeightConfigMapper;
import com.zhaoyichi.devplatformbackend.mapper.UserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * 信用评分算法 V2 的协调服务：采集 → 四维度计算 → 场景权重合成 → 写库 → 判级。
 *
 * <p>V2 场景权重的"出厂默认值"取自文档 §3.3，其来源为 T/CESA 团体标准附录 B 的高校师生推荐权重
 * 归一化结果。若 {@code scene_weight_config} 表中有管理员已调整的值，仍以库内为准以尊重运营策略。</p>
 */
@Service
public class CreditScoreV2Service {

    /** V2 默认场景权重（文档 §3.3，源自 T/CESA 附录 B）。Key: 场景名，Value: {stab, pr, coll, comp}。 */
    private static final Map<String, double[]> V2_DEFAULT_SCENE_WEIGHTS = new HashMap<>();
    static {
        V2_DEFAULT_SCENE_WEIGHTS.put("综合", new double[]{0.20, 0.30, 0.30, 0.20});
        V2_DEFAULT_SCENE_WEIGHTS.put("核心开发者", new double[]{0.15, 0.45, 0.25, 0.15});
        V2_DEFAULT_SCENE_WEIGHTS.put("辅助贡献", new double[]{0.20, 0.25, 0.40, 0.15});
    }

    /** 站内互评融合权重上限（与 V1 保持一致的 0.5，来源：T/CESA 附录 B MP=40% 的类推上限）。 */
    private static final double REVIEW_BLEND_CAP = 0.5;

    /**
     * 单维卓越补偿：任一维度足够高且显著高于其余三维均值时，在加权基准分之上追加有界加分。
     * <p>公开 GitHub API 对维护者（尤其内核类）的协作/合规信号覆盖不全，纯线性加权易低估「单维极强」账号；
     * 以 peak 与三均值的极差为单调信号，加分封顶，避免单维刷满替代全科发展。</p>
     */
    private static final int EXCELLENCE_PEAK_MIN = 88;
    private static final int EXCELLENCE_SPREAD_MIN = 20;
    private static final int EXCELLENCE_BOOST_BASE = 8;
    private static final int EXCELLENCE_BOOST_CAP = 35;

    @Autowired
    private GithubMetricsCollector collector;
    @Autowired
    private GithubEventStatsMapper githubEventStatsMapper;
    @Autowired
    private CreditScoreAlgoV2Service algo;
    @Autowired
    private CreditThresholdService thresholdService;
    @Autowired
    private CreditScoreMapper creditScoreMapper;
    @Autowired
    private SceneWeightConfigMapper sceneWeightConfigMapper;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private CollaborationReviewMapper collaborationReviewMapper;

    public static final String ALGO_VERSION = "v2";

    /**
     * V2 路径：拉取指标 → 计算 → 融合站内互评 → 写回 credit_score（按场景）。
     *
     * @return 成功返回已持久化的 CreditScore；GitHub 不可达返回 null。
     */
    public CreditScore fetchAndCalculate(String githubUsername, String scene) {
        GithubEventStats stats = collector.collectAndSave(githubUsername);
        if (stats == null) {
            return null;
        }
        CreditScoreAlgoV2Service.V2Scores s = algo.compute(stats);

        User owner = userMapper.selectOne(new QueryWrapper<User>().eq("github_username", githubUsername));
        int collaboration = blendCollaborationWithReview(s.collaboration, owner == null ? null : owner.getId());

        int baseWeighted = weightedTotal(s.stability, s.prQuality, collaboration, s.compliance, scene);
        int total = applyExcellenceBoost(s.stability, s.prQuality, collaboration, s.compliance, baseWeighted);
        String level = thresholdService.classify(total, scene, ALGO_VERSION);

        CreditScore row = upsertCreditScore(owner, githubUsername, scene,
                s.stability, s.prQuality, collaboration, s.compliance, total, level);
        if (owner != null && "综合".equals(scene)) {
            owner.setTotalScore(total);
            owner.setLevel(level);
            userMapper.updateById(owner);
        }
        return row;
    }

    /**
     * 使用库内 {@link GithubEventStats} 按<strong>当前</strong> V2 公式重算指定场景的「四维 + 加权 + 卓越补偿 + 等级」并写回 {@code credit_score}，
     * 不发起 GitHub 请求。算法迭代（如协作维增加粉丝/Star）后，查询接口应调用本方法，避免界面仍展示旧四维快照。
     *
     * @return true 表示存在 stats 并已写库；false 表示无 stats 行，调用方应保留原有 credit_score 读路径。
     */
    public boolean recalculateFromCachedStats(String githubUsername, String scene) {
        if (githubUsername == null || githubUsername.trim().isEmpty()) {
            return false;
        }
        String gh = githubUsername.trim();
        GithubEventStats stats = githubEventStatsMapper.selectOne(
                new QueryWrapper<GithubEventStats>().eq("github_username", gh));
        if (stats == null) {
            return false;
        }
        CreditScoreAlgoV2Service.V2Scores s = algo.compute(stats);
        User owner = userMapper.selectOne(new QueryWrapper<User>().eq("github_username", gh));
        int collaboration = blendCollaborationWithReview(s.collaboration, owner == null ? null : owner.getId());
        int baseWeighted = weightedTotal(s.stability, s.prQuality, collaboration, s.compliance, scene);
        int total = applyExcellenceBoost(s.stability, s.prQuality, collaboration, s.compliance, baseWeighted);
        String level = thresholdService.classify(total, scene, ALGO_VERSION);
        upsertCreditScore(owner, gh, scene, s.stability, s.prQuality, collaboration, s.compliance, total, level);
        if (owner != null && "综合".equals(scene)) {
            owner.setTotalScore(total);
            owner.setLevel(level);
            userMapper.updateById(owner);
        }
        return true;
    }

    /**
     * 对外暴露：直接基于已有 stats 计算分数（不重新拉 GitHub），供 explain 接口等"只读"路径使用。
     */
    public CreditScoreAlgoV2Service.V2Scores computeFromStats(GithubEventStats stats) {
        return algo.compute(stats);
    }

    public int weightedTotal(int stability, int prQuality, int collaboration, int compliance, String scene) {
        double[] w = loadSceneWeights(scene);
        return (int) Math.round(
                stability * w[0]
                        + prQuality * w[1]
                        + collaboration * w[2]
                        + compliance * w[3]);
    }

    /**
     * 在 {@link #weightedTotal} 之上应用单维卓越补偿（与 {@link #fetchAndCalculate} 写库口径一致）。
     */
    public int applyExcellenceBoost(int stability, int prQuality, int collaboration, int compliance, int baseWeighted) {
        int peak = Math.max(Math.max(stability, prQuality), Math.max(collaboration, compliance));
        if (peak < EXCELLENCE_PEAK_MIN) {
            return baseWeighted;
        }
        int sum = stability + prQuality + collaboration + compliance;
        int othersAvg = (sum - peak) / 3;
        int spread = peak - othersAvg;
        if (spread < EXCELLENCE_SPREAD_MIN) {
            return baseWeighted;
        }
        int boost = Math.min(EXCELLENCE_BOOST_CAP, EXCELLENCE_BOOST_BASE + spread / 2);
        return Math.min(100, baseWeighted + boost);
    }

    public double[] loadSceneWeights(String scene) {
        SceneWeightConfig cfg = sceneWeightConfigMapper.selectOne(
                new QueryWrapper<SceneWeightConfig>().eq("scene_name", scene));
        if (cfg != null) {
            return new double[]{
                    bd(cfg.getStabilityWeight(), 0.20),
                    bd(cfg.getPrQualityWeight(), 0.30),
                    bd(cfg.getCollaborationWeight(), 0.30),
                    bd(cfg.getComplianceWeight(), 0.20)
            };
        }
        double[] def = V2_DEFAULT_SCENE_WEIGHTS.get(scene);
        if (def != null) return def.clone();
        return V2_DEFAULT_SCENE_WEIGHTS.get("综合").clone();
    }

    /**
     * 与 V1 保持一致的站内互评融合逻辑：互评样本达到 5 条给 0.5 权重上限。
     * 这一融合比例在文档 §3.2 维度 3 中引用 T/CESA 附录 B"MP 40%"作类推。
     */
    public int blendCollaborationWithReview(int githubCollab, Long platformUserId) {
        if (platformUserId == null) return githubCollab;
        Map<String, Object> agg = collaborationReviewMapper.aggregateRatingForUser(platformUserId);
        if (agg == null || agg.isEmpty()) return githubCollab;

        double avg = 0.0;
        int cnt = 0;
        for (Map.Entry<String, Object> e : agg.entrySet()) {
            if (e.getKey() == null || e.getValue() == null) continue;
            if (e.getKey().equalsIgnoreCase("avgRating") && e.getValue() instanceof Number) {
                avg = ((Number) e.getValue()).doubleValue();
            }
            if (e.getKey().equalsIgnoreCase("cnt") && e.getValue() instanceof Number) {
                cnt = ((Number) e.getValue()).intValue();
            }
        }
        if (cnt <= 0) return githubCollab;
        double reviewScore100 = (avg - 1.0) / 4.0 * 100.0;
        double w = Math.min(REVIEW_BLEND_CAP, cnt * 0.1);
        return (int) Math.round(githubCollab * (1 - w) + reviewScore100 * w);
    }

    private CreditScore upsertCreditScore(User owner, String githubUsername, String scene,
                                          int stability, int prQuality, int collaboration, int compliance,
                                          int totalScore, String level) {
        QueryWrapper<CreditScore> w = new QueryWrapper<>();
        w.eq("github_username", githubUsername).eq("scene", scene);
        CreditScore exist = creditScoreMapper.selectOne(w);
        if (exist == null) {
            CreditScore row = new CreditScore();
            row.setUserId(owner == null ? 0 : owner.getId().intValue());
            row.setGithubUsername(githubUsername);
            row.setScene(scene);
            row.setStability(stability);
            row.setPrQuality(prQuality);
            row.setCollaboration(collaboration);
            row.setCompliance(compliance);
            row.setTotalScore(totalScore);
            row.setLevel(level);
            row.setAlgoVersion(ALGO_VERSION);
            creditScoreMapper.insert(row);
            return row;
        } else {
            exist.setStability(stability);
            exist.setPrQuality(prQuality);
            exist.setCollaboration(collaboration);
            exist.setCompliance(compliance);
            exist.setTotalScore(totalScore);
            exist.setLevel(level);
            exist.setAlgoVersion(ALGO_VERSION);
            if (exist.getUserId() == null && owner != null) {
                exist.setUserId(owner.getId().intValue());
            }
            creditScoreMapper.updateById(exist);
            return exist;
        }
    }

    private static double bd(BigDecimal v, double d) {
        return v == null ? d : v.doubleValue();
    }
}
