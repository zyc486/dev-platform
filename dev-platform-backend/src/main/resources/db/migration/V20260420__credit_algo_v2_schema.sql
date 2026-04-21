-- 信用评分算法 V2（基于 13 篇文献的客观化重构）相关建表脚本。
-- 该脚本内容与 DatabaseSchemaInitializer 中的 V2 兜底逻辑保持一致，便于独立执行/论文附录展示。

-- 1) credit_score / credit_history 增加 algo_version 字段（当前系统仅保留 V2；默认值为 v2）
ALTER TABLE credit_score
    ADD COLUMN algo_version varchar(10) DEFAULT 'v2'
    COMMENT '算法版本: v2(literature-based)';

ALTER TABLE credit_history
    ADD COLUMN algo_version varchar(10) DEFAULT 'v2'
    COMMENT '算法版本';

-- 2) github_event_stats：V2 算法所需的原始指标计数沉淀
--   - 字段映射 T/CESA 团体标准附录 B 的 OI/IC/OP/PRR/MP 指标
--   - 近 90 天窗口由 GitHub /users/{u}/events 硬限制所决定（API 只返回最近 90 天最多 300 条）
CREATE TABLE IF NOT EXISTS github_event_stats (
    id bigint NOT NULL AUTO_INCREMENT,
    github_username varchar(100) NOT NULL,
    open_issue_count int DEFAULT 0 COMMENT 'OI: /search/issues type=issue author',
    open_pr_count int DEFAULT 0 COMMENT 'OP: /search/issues type=pr author',
    merged_pr_count int DEFAULT 0 COMMENT 'MP: is:merged',
    issue_comment_90d int DEFAULT 0 COMMENT 'IC: 近 90 天 IssueCommentEvent',
    pr_review_90d int DEFAULT 0 COMMENT 'PRR: 近 90 天 PullRequestReviewEvent',
    push_event_90d int DEFAULT 0 COMMENT '近 90 天 PushEvent 次数（commit 频率代理）',
    close_issue_90d int DEFAULT 0,
    close_pr_90d int DEFAULT 0,
    sampled_repo_count int DEFAULT 0 COMMENT '合规采样的仓库数（分母）',
    license_present_count int DEFAULT 0 COMMENT 'LICENSE 存在的仓库数',
    workflow_present_count int DEFAULT 0 COMMENT '.github/workflows 存在的仓库数',
    security_present_count int DEFAULT 0 COMMENT 'SECURITY.md 存在的仓库数（CVE 代理）',
    active_days bigint DEFAULT 0 COMMENT '账号活跃天数',
    public_repos int DEFAULT 0,
    followers int DEFAULT 0,
    fetch_time datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_github_event_stats_user (github_username),
    KEY idx_ges_user (github_username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 3) credit_threshold：分位数阈值快照
--   - sample_size < 30 时 fallback_used=1，使用静态阈值 85/70/60（见文献 C 对样本量的要求）
CREATE TABLE IF NOT EXISTS credit_threshold (
    id bigint NOT NULL AUTO_INCREMENT,
    scene varchar(50) NOT NULL,
    algo_version varchar(10) NOT NULL DEFAULT 'v2',
    sample_size int NOT NULL DEFAULT 0 COMMENT '计算时的样本量 N',
    p20 int NULL COMMENT 'N>=30 时有效',
    p50 int NULL,
    p80 int NULL,
    fallback_used tinyint(1) DEFAULT 0 COMMENT '1=样本不足已退回静态 85/70/60',
    compute_time datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_threshold_scene_algo (scene, algo_version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
