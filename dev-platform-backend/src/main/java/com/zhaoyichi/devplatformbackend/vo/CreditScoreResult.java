package com.zhaoyichi.devplatformbackend.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor  // 生成无参构造方法（完美解决 Service 里的报错）
@AllArgsConstructor // 自动生成全参构造方法（替代了你原来手写的那一大段）
public class CreditScoreResult {
    private String githubUsername;
    private int totalScore;
    private String level;
    private int stability;
    private int prQuality;
    private int collaboration;
    private int compliance;
    private String scene;
    /** 与 credit_score.algo_version 对齐，供洞察/导出等展示 */
    private String algoVersion;
}