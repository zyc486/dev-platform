package com.zhaoyichi.devplatformbackend.service.credit;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.zhaoyichi.devplatformbackend.entity.CreditScore;
import com.zhaoyichi.devplatformbackend.mapper.CreditScoreMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * CRITIC (Criteria Importance Through Intercriteria Correlation) 权重离线计算器。
 *
 * <h3>文献依据</h3>
 * 游兰, 田明炎, 周烨, 等. 《开源软件开发者价值评估体系及其实证研究》，计算机科学 2024.
 *
 * <h3>公式</h3>
 * <pre>
 *   C_j = σ_j · Σ_k (1 - r_{jk})     // 对比强度 × 冲突性
 *   w_j = C_j / Σ_k C_k              // 归一化
 * </pre>
 *
 * <h3>守门条件（严谨性保障）</h3>
 * 只有在 {@code sampleSize >= MIN_SAMPLE_FOR_CRITIC} 时才输出权重，否则返回 null。
 * 该阈值用于防止"样本过少 → 分布异常 → 权重剧烈摆动"导致的伪客观性。
 */
@Component
public class CriticWeightCalculator {

    private static final Logger log = LoggerFactory.getLogger(CriticWeightCalculator.class);

    /** CRITIC 法启用最小样本量：文献 D 建议 N≥50 方可获得稳定权重估计。 */
    public static final int MIN_SAMPLE_FOR_CRITIC = 50;

    @Autowired
    private CreditScoreMapper creditScoreMapper;

    /**
     * 每月 2 日 04:00 对 V2 算法的"综合"场景执行 CRITIC 计算（预留钩子；实际切换由人工运营决定）。
     *
     * <p>当前实现只打印日志，不自动覆盖 {@code scene_weight_config}，因为权重切换是重大运营决策，
     * 不宜在定时任务中自动完成。管理员可根据日志中的建议值在后台手动调整。</p>
     */
    @Scheduled(cron = "0 0 4 2 * ?")
    public void monthlyReport() {
        Map<String, Double> w = computeForScene("综合", "v2");
        if (w == null) {
            log.info("[critic] 样本不足（<{}），本月不输出 CRITIC 建议权重", MIN_SAMPLE_FOR_CRITIC);
            return;
        }
        log.info("[critic] V2 综合场景建议权重（仅供管理员参考，未自动生效）: {}", w);
    }

    /**
     * 计算指定场景 + 版本下四维度的 CRITIC 权重。
     *
     * @return {stability, prQuality, collaboration, compliance} → 0~1 权重；样本不足返回 null。
     */
    public Map<String, Double> computeForScene(String scene, String algoVersion) {
        QueryWrapper<CreditScore> q = new QueryWrapper<>();
        q.eq("scene", scene).eq("algo_version", algoVersion);
        List<CreditScore> rows = creditScoreMapper.selectList(q);
        if (rows.size() < MIN_SAMPLE_FOR_CRITIC) {
            return null;
        }

        double[] stab = toArray(rows, "stab");
        double[] prq = toArray(rows, "pr");
        double[] col = toArray(rows, "col");
        double[] com = toArray(rows, "com");

        double[][] matrix = new double[][]{stab, prq, col, com};
        int m = matrix.length;
        double[] sigma = new double[m];
        for (int i = 0; i < m; i++) sigma[i] = std(matrix[i]);

        double[] cFactor = new double[m];
        for (int j = 0; j < m; j++) {
            double conflict = 0.0;
            for (int k = 0; k < m; k++) {
                if (k == j) continue;
                conflict += 1.0 - pearson(matrix[j], matrix[k]);
            }
            cFactor[j] = sigma[j] * conflict;
        }
        double sum = 0.0;
        for (double v : cFactor) sum += v;
        if (sum <= 0) return null;

        Map<String, Double> w = new LinkedHashMap<>();
        w.put("stability", cFactor[0] / sum);
        w.put("prQuality", cFactor[1] / sum);
        w.put("collaboration", cFactor[2] / sum);
        w.put("compliance", cFactor[3] / sum);
        return w;
    }

    private static double[] toArray(List<CreditScore> rows, String which) {
        List<Double> buf = new ArrayList<>(rows.size());
        for (CreditScore r : rows) {
            Integer v = null;
            switch (which) {
                case "stab": v = r.getStability(); break;
                case "pr":   v = r.getPrQuality(); break;
                case "col":  v = r.getCollaboration(); break;
                case "com":  v = r.getCompliance(); break;
                default:
            }
            if (v != null) buf.add(v.doubleValue());
        }
        double[] arr = new double[buf.size()];
        for (int i = 0; i < buf.size(); i++) arr[i] = buf.get(i);
        return arr;
    }

    private static double std(double[] a) {
        if (a.length < 2) return 0;
        double mean = 0;
        for (double v : a) mean += v;
        mean /= a.length;
        double sq = 0;
        for (double v : a) sq += (v - mean) * (v - mean);
        return Math.sqrt(sq / (a.length - 1));
    }

    private static double pearson(double[] x, double[] y) {
        int n = Math.min(x.length, y.length);
        if (n < 2) return 0;
        double mx = 0, my = 0;
        for (int i = 0; i < n; i++) { mx += x[i]; my += y[i]; }
        mx /= n; my /= n;
        double num = 0, dx = 0, dy = 0;
        for (int i = 0; i < n; i++) {
            double a = x[i] - mx, b = y[i] - my;
            num += a * b;
            dx += a * a;
            dy += b * b;
        }
        if (dx == 0 || dy == 0) return 0;
        return num / Math.sqrt(dx * dy);
    }
}
