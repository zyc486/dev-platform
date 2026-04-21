-- AI 开发者画像快照：用于缓存 DeepSeek 生成的结构化画像（可解释证据、技术标签、代表项目等）
CREATE TABLE IF NOT EXISTS ai_profile_snapshot (
    id bigint NOT NULL AUTO_INCREMENT,
    github_username varchar(100) NOT NULL,
    scene varchar(50) NOT NULL DEFAULT '综合',
    algo_version varchar(10) NOT NULL DEFAULT 'v2',

    profile_version varchar(20) NOT NULL DEFAULT 'v1' COMMENT '画像 schema 版本',
    prompt_version varchar(20) NOT NULL DEFAULT 'v1' COMMENT '提示词版本',
    model varchar(50) NULL COMMENT 'deepseek-chat/deepseek-reasoner 等',

    data_hash varchar(64) NULL COMMENT '输入数据哈希（用于判定是否需要刷新）',
    summary varchar(500) NULL COMMENT '一句话摘要',

    profile_json longtext NULL COMMENT '画像完整 JSON（结构化）',
    tech_tags_json text NULL COMMENT '技术标签数组 JSON',
    top_repos_json text NULL COMMENT '代表项目数组 JSON',
    evidence_json longtext NULL COMMENT '证据链 JSON（repo+文件/字段/指标）',

    token_usage int NULL COMMENT '本次调用 token 使用量（若可得）',
    cost_estimate decimal(10,4) NULL COMMENT '本次调用成本估算（若可得）',

    status varchar(20) NOT NULL DEFAULT 'ready' COMMENT 'ready/refreshing/failed',
    error_message varchar(500) NULL,

    created_at datetime DEFAULT CURRENT_TIMESTAMP,
    updated_at datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    expires_at datetime NULL,

    PRIMARY KEY (id),
    UNIQUE KEY uk_ai_profile_user_scene (github_username, scene),
    KEY idx_ai_profile_user (github_username),
    KEY idx_ai_profile_expires (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

