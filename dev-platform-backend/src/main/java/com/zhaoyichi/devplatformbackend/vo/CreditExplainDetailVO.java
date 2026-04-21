package com.zhaoyichi.devplatformbackend.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 信用分可解释性明细：各维度原始指标、得分、权重、公式摘要与提升建议。
 */
@Data
public class CreditExplainDetailVO {
    private Long userId;
    private String githubUsername;
    private String scene;
    private Integer totalScore;
    private String level;
    /** 为 true 时表示 GitHub 实时拉取失败，维度分可能来自库内快照 */
    private Boolean githubDataUnavailable;
    private String degradedMessage;
    private List<CreditDimensionLineVO> dimensions = new ArrayList<>();
    private List<String> improvementSuggestions = new ArrayList<>();
    /** 互评：收到评价条数与平均分（1～5），无评价时为 null */
    private Map<String, Object> collaborationReviewSummary;
    /** 实际采用的算法版本：v1 / v2 */
    private String algoVersion;
    /**
     * 总分加权说明：总分由「各维度分 × 场景权重」再四舍五入得到，不等于四维简单相加。
     */
    private String weightedTotalExplanation;

    @Data
    public static class CreditDimensionLineVO {
        /** 稳定键：stability / prQuality / collaboration / compliance */
        private String code;
        private String displayName;
        /** 原始指标，如 followers、publicRepos、activeDays */
        private Map<String, Object> rawIndicators = new LinkedHashMap<>();
        private Integer dimensionScore;
        /** 0～1 之间的小数权重 */
        private Double weight;
        /** 人类可读的公式说明 */
        private String formulaSummary;
        /** 该维度对总分的贡献（约等于 dimensionScore × weight） */
        private Double weightedContribution;
    }
}
