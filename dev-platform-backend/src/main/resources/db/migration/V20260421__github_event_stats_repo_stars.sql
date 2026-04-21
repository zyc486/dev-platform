-- 社区认可度：合规采样路径上汇总的公开仓库 stargazers 合计（与 followers 一并进入协作维融合）
ALTER TABLE github_event_stats
    ADD COLUMN repo_stars_total int DEFAULT 0 COMMENT '采样公开仓库 stargazers_count 合计';
