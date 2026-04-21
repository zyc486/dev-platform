package com.zhaoyichi.devplatformbackend.service.credit;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.zhaoyichi.devplatformbackend.entity.CreditScore;
import com.zhaoyichi.devplatformbackend.entity.CreditThreshold;
import com.zhaoyichi.devplatformbackend.mapper.CreditScoreMapper;
import com.zhaoyichi.devplatformbackend.mapper.CreditThresholdMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 等级阈值服务：基于"20/80 法则"（文献 C 吴哲夫等 2018）的分位数阈值，
 * 并在样本量不足时回退到静态阈值 85/70/55 以避免统计不显著。
 *
 * <h3>判级规则</h3>
 * <pre>
 * 优秀   total &gt;= P80
 * 良好   P50 &lt;= total &lt; P80
 * 合格   P20 &lt;= total &lt; P50
 * 待观察 total &lt; P20（静态回退时：总分 &lt; 55 为最低档，避免 55 分与「极高风险」语义冲突）
 * </pre>
 * 当 {@code sampleSize < MIN_SAMPLE_FOR_QUANTILE} 时回退到 85/70/55。
 */
@Service
public class CreditThresholdService {

    private static final Logger log = LoggerFactory.getLogger(CreditThresholdService.class);

    /** 分位数启用的最小样本量；低于该阈值时分位数估计不可靠，回退静态阈值。
     *  参考统计学常规经验：Bootstrap 分位数估计稳定性要求 N≥30。 */
    public static final int MIN_SAMPLE_FOR_QUANTILE = 30;

    public static final int STATIC_EXCELLENT = 85;
    public static final int STATIC_GOOD = 70;
    /** 低于该静态阈值归为最低档；55 与常见加权总分同量级，避免中等分被误标为「极高风险」。 */
    public static final int STATIC_PASS = 55;

    public static final String ALGO_V2 = "v2";

    @Autowired
    private CreditScoreMapper creditScoreMapper;
    @Autowired
    private CreditThresholdMapper creditThresholdMapper;

    /**
     * 判级：优先读阈值快照，缺省/降级时用静态阈值。
     */
    public String classify(int totalScore, String scene, String algoVersion) {
        CreditThreshold t = loadLatest(scene, algoVersion);
        if (t == null || t.getFallbackUsed() == null || t.getFallbackUsed() == 1
                || t.getP20() == null || t.getP50() == null || t.getP80() == null) {
            return staticClassify(totalScore);
        }
        if (totalScore >= t.getP80()) return "优秀";
        if (totalScore >= t.getP50()) return "良好";
        if (totalScore >= t.getP20()) return "合格";
        return "待观察";
    }

    public static String staticClassify(int totalScore) {
        if (totalScore >= STATIC_EXCELLENT) return "优秀";
        if (totalScore >= STATIC_GOOD) return "良好";
        if (totalScore >= STATIC_PASS) return "合格";
        return "待观察";
    }

    private CreditThreshold loadLatest(String scene, String algoVersion) {
        QueryWrapper<CreditThreshold> w = new QueryWrapper<>();
        w.eq("scene", scene).eq("algo_version", algoVersion)
                .orderByDesc("compute_time").last("LIMIT 1");
        return creditThresholdMapper.selectOne(w);
    }

    /**
     * 每月 1 号 03:10 重算全站分位数阈值（错峰于 CreditScoreService 的 03:00 快照任务）。
     */
    @Scheduled(cron = "0 10 3 1 * ?")
    public void refreshAllScenes() {
        for (String scene : Arrays.asList("综合", "核心开发者", "辅助贡献")) {
            recomputeOne(scene, ALGO_V2);
        }
    }

    /**
     * 基于指定场景的 credit_score 数据重新计算 P20/P50/P80 并写入 credit_threshold。
     */
    public CreditThreshold recomputeOne(String scene, String algoVersion) {
        QueryWrapper<CreditScore> w = new QueryWrapper<>();
        w.eq("scene", scene).eq("algo_version", algoVersion);
        List<CreditScore> rows = creditScoreMapper.selectList(w);

        List<Integer> totals = new ArrayList<>();
        for (CreditScore r : rows) {
            if (r.getTotalScore() != null) totals.add(r.getTotalScore());
        }

        CreditThreshold snap = new CreditThreshold();
        snap.setScene(scene);
        snap.setAlgoVersion(algoVersion);
        snap.setSampleSize(totals.size());

        if (totals.size() < MIN_SAMPLE_FOR_QUANTILE) {
            snap.setFallbackUsed(1);
            snap.setP20(null);
            snap.setP50(null);
            snap.setP80(null);
            log.info("[threshold] 样本不足 scene={} N={} -> 使用静态阈值", scene, totals.size());
        } else {
            Collections.sort(totals);
            snap.setFallbackUsed(0);
            snap.setP20(percentile(totals, 20));
            snap.setP50(percentile(totals, 50));
            snap.setP80(percentile(totals, 80));
            log.info("[threshold] scene={} N={} P20={} P50={} P80={}",
                    scene, totals.size(), snap.getP20(), snap.getP50(), snap.getP80());
        }

        // upsert by (scene, algoVersion)
        QueryWrapper<CreditThreshold> existQ = new QueryWrapper<>();
        existQ.eq("scene", scene).eq("algo_version", algoVersion);
        CreditThreshold exist = creditThresholdMapper.selectOne(existQ);
        if (exist == null) {
            creditThresholdMapper.insert(snap);
        } else {
            snap.setId(exist.getId());
            creditThresholdMapper.updateById(snap);
        }
        return snap;
    }

    /**
     * 线性插值分位数（P_p 对应 R7 定义，NumPy 默认）：h = (N-1) * p，取前后两点插值。
     */
    private static int percentile(List<Integer> sortedAsc, int p) {
        int n = sortedAsc.size();
        if (n == 0) return 0;
        if (n == 1) return sortedAsc.get(0);
        double h = (n - 1) * (p / 100.0);
        int lo = (int) Math.floor(h);
        int hi = (int) Math.ceil(h);
        double frac = h - lo;
        double v = sortedAsc.get(lo) * (1 - frac) + sortedAsc.get(hi) * frac;
        return (int) Math.round(v);
    }
}
