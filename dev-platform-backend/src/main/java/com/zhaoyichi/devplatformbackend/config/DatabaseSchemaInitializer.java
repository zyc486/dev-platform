package com.zhaoyichi.devplatformbackend.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Objects;
import java.util.List;
import java.util.Map;

@Slf4j
@Configuration
public class DatabaseSchemaInitializer {

    /**
     * 数据库“最小可运行”初始化器。
     *
     * <p>面向毕设/本地环境的目标是：即使使用旧库/缺字段/缺表，也能在启动时自动补齐；
     * 同时把高频索引（含 {@code user.github_username} 唯一约束）以可重复的方式落地。</p>
     *
     * <p>注意：当检测到唯一索引目标列存在重复值时，会直接抛出异常阻止启动，
     * 以避免在运行期产生不可预期的绑定/权限问题。</p>
     */
    @Bean
    public ApplicationRunner databaseSchemaRunner(@NonNull JdbcTemplate jdbcTemplate) {
        return args -> {
            log.info("[schema] start database schema initialization");
            JdbcTemplate jt = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate");
            ensureColumn(jt, "user", "role", "ALTER TABLE user ADD COLUMN role varchar(20) DEFAULT 'user' COMMENT '角色: user/admin'");
            // 兼容旧库：确保邮箱/手机号列存在（本项目注册允许 phone/email 二选一）
            ensureColumn(jt, "user", "phone", "ALTER TABLE user ADD COLUMN phone varchar(50) NULL COMMENT '手机号'");
            ensureColumn(jt, "user", "email", "ALTER TABLE user ADD COLUMN email varchar(120) NULL COMMENT '邮箱'");
            // 隐私设置：默认公开/允许私信
            ensureColumn(jt, "user", "privacy_credit_public", "ALTER TABLE user ADD COLUMN privacy_credit_public tinyint(1) DEFAULT 1 COMMENT '信用公开(1=公开,0=隐藏)'");
            ensureColumn(jt, "user", "privacy_feed_public", "ALTER TABLE user ADD COLUMN privacy_feed_public tinyint(1) DEFAULT 1 COMMENT '动态可见(1=公开,0=隐藏)'");
            ensureColumn(jt, "user", "privacy_allow_message", "ALTER TABLE user ADD COLUMN privacy_allow_message tinyint(1) DEFAULT 1 COMMENT '允许私信(1=允许,0=关闭)'");
            ensureColumn(jt, "collab_apply", "audit_time", "ALTER TABLE collab_apply ADD COLUMN audit_time datetime NULL COMMENT '审核时间'");
            ensureColumn(jt, "collab_apply", "audit_reason", "ALTER TABLE collab_apply ADD COLUMN audit_reason varchar(255) NULL COMMENT '审核备注'");
            ensureColumn(jt, "collaboration", "finish_time", "ALTER TABLE collaboration ADD COLUMN finish_time datetime NULL COMMENT '完成时间'");
            ensureColumn(jt, "user_follow", "follow_user_id", "ALTER TABLE user_follow ADD COLUMN follow_user_id bigint NULL COMMENT '被关注用户ID'");

            jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS feedback (" +
                    "id bigint NOT NULL AUTO_INCREMENT," +
                    "user_id bigint NOT NULL," +
                    "type varchar(30) NOT NULL," +
                    "title varchar(100) NOT NULL," +
                    "content varchar(1000) NOT NULL," +
                    "contact varchar(100) NULL," +
                    "attachment_path varchar(500) NULL," +
                    "status varchar(20) DEFAULT 'pending'," +
                    "reply_content varchar(1000) NULL," +
                    "create_time datetime DEFAULT CURRENT_TIMESTAMP," +
                    "reply_time datetime NULL," +
                    "PRIMARY KEY (id)" +
                    ")");

            jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS message_notice (" +
                    "id bigint NOT NULL AUTO_INCREMENT," +
                    "user_id bigint NOT NULL," +
                    "type varchar(30) NOT NULL," +
                    "title varchar(100) NOT NULL," +
                    "content varchar(500) NOT NULL," +
                    "related_id bigint NULL," +
                    "is_read tinyint(1) DEFAULT 0," +
                    "create_time datetime DEFAULT CURRENT_TIMESTAMP," +
                    "PRIMARY KEY (id)" +
                    ")");

            // 私信：最小可用（1v1）
            jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS dm_message (" +
                    "id bigint NOT NULL AUTO_INCREMENT," +
                    "from_user_id bigint NOT NULL," +
                    "to_user_id bigint NOT NULL," +
                    "content varchar(2000) NOT NULL," +
                    "is_read tinyint(1) DEFAULT 0," +
                    "create_time datetime DEFAULT CURRENT_TIMESTAMP," +
                    "PRIMARY KEY (id)," +
                    "KEY idx_dm_pair_time (from_user_id, to_user_id, create_time)," +
                    "KEY idx_dm_to_read (to_user_id, is_read, create_time)" +
                    ")");

            // 群聊：房间/成员/消息（最小可用）
            jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS chat_room (" +
                    "id bigint NOT NULL AUTO_INCREMENT," +
                    "chat_no varchar(20) NULL," +
                    "name varchar(100) NOT NULL," +
                    "created_by bigint NOT NULL," +
                    "collab_project_id int NULL," +
                    "create_time datetime DEFAULT CURRENT_TIMESTAMP," +
                    "PRIMARY KEY (id)," +
                    "UNIQUE KEY uk_chat_room_chat_no (chat_no)," +
                    "KEY idx_chat_room_collab (collab_project_id)," +
                    "KEY idx_chat_room_creator (created_by)" +
                    ")");
            jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS chat_room_member (" +
                    "id bigint NOT NULL AUTO_INCREMENT," +
                    "room_id bigint NOT NULL," +
                    "user_id bigint NOT NULL," +
                    "role varchar(20) DEFAULT 'member'," +
                    "last_read_message_id bigint NULL," +
                    "join_time datetime DEFAULT CURRENT_TIMESTAMP," +
                    "PRIMARY KEY (id)," +
                    "UNIQUE KEY uk_chat_room_member (room_id, user_id)," +
                    "KEY idx_chat_room_member_user (user_id, room_id)" +
                    ")");
            jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS chat_message (" +
                    "id bigint NOT NULL AUTO_INCREMENT," +
                    "room_id bigint NOT NULL," +
                    "from_user_id bigint NOT NULL," +
                    "content varchar(2000) NOT NULL," +
                    "create_time datetime DEFAULT CURRENT_TIMESTAMP," +
                    "PRIMARY KEY (id)," +
                    "KEY idx_chat_room_msg (room_id, id)" +
                    ")");

            // 群聊：入群申请/邀请
            jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS chat_join_request (" +
                    "id bigint NOT NULL AUTO_INCREMENT," +
                    "room_id bigint NOT NULL," +
                    "applicant_user_id bigint NOT NULL," +
                    "status varchar(20) DEFAULT 'pending'," +
                    "reason varchar(500) NULL," +
                    "handled_by bigint NULL," +
                    "handle_reason varchar(500) NULL," +
                    "create_time datetime DEFAULT CURRENT_TIMESTAMP," +
                    "handle_time datetime NULL," +
                    "PRIMARY KEY (id)," +
                    "KEY idx_chat_join_room_status_time (room_id, status, create_time)," +
                    "KEY idx_chat_join_applicant_status_time (applicant_user_id, status, create_time)" +
                    ")");

            jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS chat_invite (" +
                    "id bigint NOT NULL AUTO_INCREMENT," +
                    "room_id bigint NOT NULL," +
                    "inviter_user_id bigint NOT NULL," +
                    "invitee_user_id bigint NOT NULL," +
                    "status varchar(20) DEFAULT 'pending'," +
                    "create_time datetime DEFAULT CURRENT_TIMESTAMP," +
                    "handle_time datetime NULL," +
                    "PRIMARY KEY (id)," +
                    "KEY idx_chat_invite_room_status_time (room_id, status, create_time)," +
                    "KEY idx_chat_invite_invitee_status_time (invitee_user_id, status, create_time)" +
                    ")");

            jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS admin_action_log (" +
                    "id bigint NOT NULL AUTO_INCREMENT," +
                    "admin_user_id bigint NOT NULL," +
                    "admin_username varchar(100) NOT NULL," +
                    "action_type varchar(50) NOT NULL," +
                    "target_type varchar(50) NULL," +
                    "target_id bigint NULL," +
                    "detail varchar(500) NULL," +
                    "create_time datetime DEFAULT CURRENT_TIMESTAMP," +
                    "PRIMARY KEY (id)" +
                    ")");

            jdbcTemplate.execute("UPDATE user SET role = CASE WHEN username = 'admin' THEN 'admin' ELSE COALESCE(role, 'user') END");
            jdbcTemplate.execute("UPDATE collab_apply SET status = 'approved' WHERE status = 'pass'");
            jdbcTemplate.execute("UPDATE collab_apply SET status = 'rejected' WHERE status = 'reject'");

            // 确保 system_config 表存在（兼容未建表的旧库）
            jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS system_config (" +
                    "config_key varchar(100) NOT NULL," +
                    "config_value varchar(500) NULL," +
                    "description varchar(200) NULL," +
                    "update_time datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
                    "PRIMARY KEY (config_key)" +
                    ")");

            // 确保 scene_weight_config 表存在
            jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS scene_weight_config (" +
                    "id int NOT NULL AUTO_INCREMENT," +
                    "scene_name varchar(50) NOT NULL," +
                    "stability_weight decimal(5,2) DEFAULT 0.25," +
                    "pr_quality_weight decimal(5,2) DEFAULT 0.30," +
                    "collaboration_weight decimal(5,2) DEFAULT 0.25," +
                    "compliance_weight decimal(5,2) DEFAULT 0.20," +
                    "is_default tinyint(1) DEFAULT 0," +
                    "update_time datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
                    "PRIMARY KEY (id)" +
                    ")");

            // 补充 system_config 初始配置（不存在时才插入）
            initSystemConfig(jdbcTemplate, "platform_name", "开源开发者协作与信用评估平台", "平台名称");
            initSystemConfig(jdbcTemplate, "credit_refresh_days", "30", "信用数据自动刷新周期（天）");
            initSystemConfig(jdbcTemplate, "min_credit_default", "60", "协作项目默认最低信用分要求");
            initSystemConfig(jdbcTemplate, "github_token", "", "GitHub API Token（用于信用计算）");
            initSystemConfig(jdbcTemplate, "feedback_notice_enabled", "true", "反馈回复后是否发送站内通知");

            // 补充 scene_weight_config 初始数据（不存在时才插入）
            initSceneWeight(jdbcTemplate, "综合", "0.25", "0.30", "0.25", "0.20", 1);
            initSceneWeight(jdbcTemplate, "核心开发者", "0.30", "0.40", "0.20", "0.10", 0);
            initSceneWeight(jdbcTemplate, "辅助贡献", "0.20", "0.20", "0.35", "0.25", 0);

            ensureIndex(jdbcTemplate, "user", "idx_user_username", "CREATE INDEX idx_user_username ON `user`(username)");
            // 邮箱/手机号索引用于注册/登录查找加速（先不加唯一约束，避免旧库存在重复导致启动失败）
            ensureIndex(jdbcTemplate, "user", "idx_user_phone", "CREATE INDEX idx_user_phone ON `user`(phone)");
            ensureIndex(jdbcTemplate, "user", "idx_user_email", "CREATE INDEX idx_user_email ON `user`(email)");
            ensureUniqueIndex(jdbcTemplate, "user", "github_username", "idx_user_github_username", "CREATE UNIQUE INDEX idx_user_github_username ON `user`(github_username)");
            ensureIndex(jdbcTemplate, "credit_score", "idx_credit_score_github_scene", "CREATE INDEX idx_credit_score_github_scene ON credit_score(github_username, scene)");
            ensureIndex(jdbcTemplate, "collab_apply", "idx_collab_apply_project_user", "CREATE INDEX idx_collab_apply_project_user ON collab_apply(project_id, user_id)");
            ensureIndex(jdbcTemplate, "message_notice", "idx_message_notice_user_read", "CREATE INDEX idx_message_notice_user_read ON message_notice(user_id, is_read)");
            ensureIndex(jdbcTemplate, "post_report", "idx_post_report_status", "CREATE INDEX idx_post_report_status ON post_report(status)");

            // ===== 新增功能表 =====
            ensureColumn(jdbcTemplate, "user", "avatar", "ALTER TABLE user ADD COLUMN avatar varchar(500) NULL COMMENT '头像URL'");
            ensureColumn(jdbcTemplate, "user", "nickname", "ALTER TABLE user ADD COLUMN nickname varchar(100) NULL COMMENT '昵称'");
            ensureColumn(jdbcTemplate, "user", "bio", "ALTER TABLE user ADD COLUMN bio varchar(500) NULL COMMENT '个人简介'");
            ensureColumn(jdbcTemplate, "chat_room", "chat_no", "ALTER TABLE chat_room ADD COLUMN chat_no varchar(20) NULL COMMENT '群聊号（纯数字唯一）'");
            ensureUniqueIndex(jdbcTemplate, "chat_room", "chat_no", "uk_chat_room_chat_no", "CREATE UNIQUE INDEX uk_chat_room_chat_no ON chat_room(chat_no)");
            // backfill for old rooms
            jdbcTemplate.execute("UPDATE chat_room SET chat_no = CAST(100000 + id AS CHAR) WHERE chat_no IS NULL OR chat_no = ''");
            ensureColumn(jdbcTemplate, "query_log", "is_favorite", "ALTER TABLE query_log ADD COLUMN is_favorite tinyint(1) DEFAULT 0 COMMENT '是否收藏'");
            ensureColumn(jdbcTemplate, "user_post", "status", "ALTER TABLE user_post ADD COLUMN status varchar(20) DEFAULT 'approved' COMMENT '帖子状态: pending/approved/rejected'");
            ensureColumn(jdbcTemplate, "feedback", "reply_time", "ALTER TABLE feedback ADD COLUMN reply_time datetime NULL");

            jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS user_favorite (" +
                    "id bigint NOT NULL AUTO_INCREMENT," +
                    "user_id bigint NOT NULL," +
                    "type varchar(30) NOT NULL COMMENT 'github_user/platform_user/credit_report/community'," +
                    "target_id varchar(100) NOT NULL," +
                    "create_time datetime DEFAULT CURRENT_TIMESTAMP," +
                    "PRIMARY KEY (id)," +
                    "UNIQUE KEY uk_user_type_target (user_id, type, target_id)" +
                    ")");

            jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS community (" +
                    "id bigint NOT NULL AUTO_INCREMENT," +
                    "name varchar(100) NOT NULL," +
                    "description varchar(1000) NULL," +
                    "avatar varchar(255) NULL," +
                    "creator_id bigint NOT NULL," +
                    "status varchar(20) DEFAULT 'active'," +
                    "tech_tags varchar(255) NULL," +
                    "member_count int DEFAULT 1," +
                    "create_time datetime DEFAULT CURRENT_TIMESTAMP," +
                    "PRIMARY KEY (id)" +
                    ")");

            jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS community_member (" +
                    "id bigint NOT NULL AUTO_INCREMENT," +
                    "community_id bigint NOT NULL," +
                    "user_id bigint NOT NULL," +
                    "role varchar(20) DEFAULT 'member' COMMENT 'creator/admin/member'," +
                    "join_time datetime DEFAULT CURRENT_TIMESTAMP," +
                    "PRIMARY KEY (id)," +
                    "UNIQUE KEY uk_community_user (community_id, user_id)" +
                    ")");

            jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS community_apply (" +
                    "id bigint NOT NULL AUTO_INCREMENT," +
                    "community_id bigint NOT NULL," +
                    "user_id bigint NOT NULL," +
                    "apply_reason varchar(500) NULL," +
                    "status varchar(20) DEFAULT 'pending'," +
                    "create_time datetime DEFAULT CURRENT_TIMESTAMP," +
                    "review_time datetime NULL," +
                    "PRIMARY KEY (id)" +
                    ")");

            jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS community_post (" +
                    "id bigint NOT NULL AUTO_INCREMENT," +
                    "community_id bigint NOT NULL," +
                    "user_id bigint NOT NULL," +
                    "type varchar(20) DEFAULT 'post' COMMENT 'announcement/post'," +
                    "title varchar(200) NULL," +
                    "content text NULL," +
                    "is_sticky tinyint(1) DEFAULT 0," +
                    "is_essence tinyint(1) DEFAULT 0," +
                    "category varchar(40) DEFAULT 'discussion' COMMENT 'discussion/share/question/resource'," +
                    "create_time datetime DEFAULT CURRENT_TIMESTAMP," +
                    "PRIMARY KEY (id)" +
                    ")");

            jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS community_attachment (" +
                    "id bigint NOT NULL AUTO_INCREMENT," +
                    "community_id bigint NOT NULL," +
                    "post_id bigint NOT NULL," +
                    "user_id bigint NOT NULL," +
                    "original_name varchar(255) NOT NULL," +
                    "storage_path varchar(500) NOT NULL COMMENT '相对 uploads/ 路径'," +
                    "content_type varchar(120) NULL," +
                    "size_bytes bigint NOT NULL DEFAULT 0," +
                    "create_time datetime DEFAULT CURRENT_TIMESTAMP," +
                    "PRIMARY KEY (id)," +
                    "KEY idx_ca_post (post_id, create_time)," +
                    "KEY idx_ca_comm (community_id, create_time)" +
                    ")");
            ensureColumn(jdbcTemplate, "community_post", "is_sticky", "ALTER TABLE community_post ADD COLUMN is_sticky tinyint(1) DEFAULT 0 COMMENT '置顶'");
            ensureColumn(jdbcTemplate, "community_post", "is_essence", "ALTER TABLE community_post ADD COLUMN is_essence tinyint(1) DEFAULT 0 COMMENT '精华'");
            ensureColumn(jdbcTemplate, "community_post", "category", "ALTER TABLE community_post ADD COLUMN category varchar(40) DEFAULT 'discussion' COMMENT 'discussion/share/question/resource'");
            jdbcTemplate.update("UPDATE community_post SET category = 'discussion' WHERE (category IS NULL OR category = '') AND (type IS NULL OR type = '' OR type = 'post')");

            jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS collab_rating (" +
                    "id bigint NOT NULL AUTO_INCREMENT," +
                    "project_id int NOT NULL," +
                    "from_user_id bigint NOT NULL," +
                    "to_user_id bigint NOT NULL," +
                    "score int NOT NULL COMMENT '1-10分'," +
                    "comment varchar(500) NULL," +
                    "create_time datetime DEFAULT CURRENT_TIMESTAMP," +
                    "PRIMARY KEY (id)," +
                    "UNIQUE KEY uk_project_from_to (project_id, from_user_id, to_user_id)" +
                    ")");

            jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS collaboration_review (" +
                    "id bigint NOT NULL AUTO_INCREMENT," +
                    "collaboration_id int NOT NULL COMMENT '协作主键 collaboration.id'," +
                    "from_user_id bigint NOT NULL," +
                    "to_user_id bigint NOT NULL," +
                    "rating tinyint NOT NULL COMMENT '1-5星'," +
                    "comment varchar(500) NULL," +
                    "create_time datetime DEFAULT CURRENT_TIMESTAMP," +
                    "PRIMARY KEY (id)," +
                    "UNIQUE KEY uk_collab_review_from_to (collaboration_id, from_user_id, to_user_id)," +
                    "KEY idx_collab_review_to_user (to_user_id)" +
                    ")");

            // 协作状态词迁移：open/closed/finished -> pending/cancelled/completed（可重复执行）
            jdbcTemplate.update("UPDATE collaboration SET status = 'pending' WHERE status = 'open'");
            jdbcTemplate.update("UPDATE collaboration SET status = 'cancelled' WHERE status = 'closed'");
            jdbcTemplate.update("UPDATE collaboration SET status = 'completed' WHERE status = 'finished'");

            initSystemConfig(jdbcTemplate, "help_markdown", "## 帮助中心\n\n### 信用评估说明\n本平台通过 GitHub 公开数据计算开发者信用分，包含代码稳定性、PR质量、团队协作、规范合规四个维度。\n\n### 如何绑定 GitHub\n登录后进入个人中心，点击「绑定 GitHub」按钮，授权后自动计算信用分。\n\n### 协作项目说明\n发布协作需求需登录，申请者需满足最低信用分要求。", "帮助中心 Markdown 内容");

            // ===== 新增：演示/答辩向互动闭环功能所需的表与列 =====
            // 功能 B：评论楼中楼 —— 先兜底 post_comment 表本身，再加 parent_id / reply_to_user 列
            jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS post_comment (" +
                    "id bigint NOT NULL AUTO_INCREMENT," +
                    "post_id bigint NOT NULL," +
                    "user_id bigint NOT NULL," +
                    "content varchar(1000) NOT NULL," +
                    "parent_id bigint NULL," +
                    "reply_to_user varchar(50) NULL," +
                    "create_time datetime DEFAULT CURRENT_TIMESTAMP," +
                    "PRIMARY KEY (id)," +
                    "KEY idx_pc_post (post_id, create_time)" +
                    ")");
            ensureColumn(jdbcTemplate, "post_comment", "parent_id", "ALTER TABLE post_comment ADD COLUMN parent_id bigint NULL COMMENT '父评论ID，根评论为NULL'");
            ensureColumn(jdbcTemplate, "post_comment", "reply_to_user", "ALTER TABLE post_comment ADD COLUMN reply_to_user varchar(50) NULL COMMENT '被回复用户名冗余'");
            ensureIndex(jdbcTemplate, "post_comment", "idx_pc_parent", "CREATE INDEX idx_pc_parent ON post_comment(post_id, parent_id, create_time)");

            // 功能 C：动态标签筛选 —— user_post.tags
            ensureColumn(jdbcTemplate, "user_post", "tags", "ALTER TABLE user_post ADD COLUMN tags varchar(200) NULL COMMENT '逗号分隔标签'");

            // 功能 D：成就与徽章
            jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS badge_def (" +
                    "code varchar(50) NOT NULL," +
                    "name varchar(50) NOT NULL," +
                    "description varchar(200) NULL," +
                    "icon varchar(50) NULL COMMENT 'Element Plus 或自定义图标类名'," +
                    "sort int DEFAULT 0," +
                    "PRIMARY KEY (code)" +
                    ")");
            jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS user_badge (" +
                    "id bigint NOT NULL AUTO_INCREMENT," +
                    "user_id bigint NOT NULL," +
                    "badge_code varchar(50) NOT NULL," +
                    "obtain_time datetime DEFAULT CURRENT_TIMESTAMP," +
                    "PRIMARY KEY (id)," +
                    "UNIQUE KEY uk_user_badge (user_id, badge_code)" +
                    ")");
            initBadgeDef(jdbcTemplate, "first_post",    "初次发声",   "发布第一条动态",           "el-icon-edit",           1);
            initBadgeDef(jdbcTemplate, "active_poster", "活跃创作者", "发帖数达到 3 篇",          "el-icon-magic-stick",    2);
            initBadgeDef(jdbcTemplate, "credit_elite",  "信用新星",   "综合信用分达到 600",       "el-icon-star-on",        3);
            initBadgeDef(jdbcTemplate, "github_binder", "开源探索者", "首次绑定 GitHub 账号",     "el-icon-connection",     4);
            initBadgeDef(jdbcTemplate, "social_butter", "社交达人",   "累计获得 10 个点赞",       "el-icon-trophy",         5);

            // 功能 F：登录审计
            jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS login_log (" +
                    "id bigint NOT NULL AUTO_INCREMENT," +
                    "user_id bigint NULL," +
                    "username varchar(100) NULL," +
                    "ip varchar(64) NULL," +
                    "user_agent varchar(300) NULL," +
                    "success tinyint(1) DEFAULT 1," +
                    "fail_reason varchar(200) NULL," +
                    "create_time datetime DEFAULT CURRENT_TIMESTAMP," +
                    "PRIMARY KEY (id)" +
                    ")");
            ensureIndex(jdbcTemplate, "login_log", "idx_ll_user_time", "CREATE INDEX idx_ll_user_time ON login_log(user_id, create_time)");
            ensureIndex(jdbcTemplate, "login_log", "idx_ll_time",      "CREATE INDEX idx_ll_time ON login_log(create_time)");

            // ===== 信用评分算法 V2（文献客观化重构）相关建表 =====
            // 1) credit_score / credit_history 增加 algo_version 字段（当前系统仅保留 V2）
            ensureColumn(jdbcTemplate, "credit_score", "algo_version",
                    "ALTER TABLE credit_score ADD COLUMN algo_version varchar(10) DEFAULT 'v2' COMMENT '算法版本: v2(literature-based)'");
            ensureColumn(jdbcTemplate, "credit_history", "algo_version",
                    "ALTER TABLE credit_history ADD COLUMN algo_version varchar(10) DEFAULT 'v2' COMMENT '算法版本'");

            // 2) github_event_stats：V2 算法所需的原始指标计数沉淀（OI/OP/MP/PRR/IC/CommitN/…）
            jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS github_event_stats (" +
                    "id bigint NOT NULL AUTO_INCREMENT," +
                    "github_username varchar(100) NOT NULL," +
                    "open_issue_count int DEFAULT 0 COMMENT 'OI: /search/issues type=issue author'," +
                    "open_pr_count int DEFAULT 0 COMMENT 'OP: /search/issues type=pr author'," +
                    "merged_pr_count int DEFAULT 0 COMMENT 'MP: is:merged'," +
                    "issue_comment_90d int DEFAULT 0 COMMENT 'IC: 近 90 天 IssueCommentEvent'," +
                    "pr_review_90d int DEFAULT 0 COMMENT 'PRR: 近 90 天 PullRequestReviewEvent'," +
                    "push_event_90d int DEFAULT 0 COMMENT '近 90 天 PushEvent 次数（commit 频率代理）'," +
                    "close_issue_90d int DEFAULT 0," +
                    "close_pr_90d int DEFAULT 0," +
                    "sampled_repo_count int DEFAULT 0 COMMENT '合规采样的仓库数（分母）'," +
                    "license_present_count int DEFAULT 0 COMMENT 'LICENSE 存在的仓库数'," +
                    "workflow_present_count int DEFAULT 0 COMMENT '.github/workflows 存在的仓库数'," +
                    "security_present_count int DEFAULT 0 COMMENT 'SECURITY.md 存在的仓库数（CVE 代理）'," +
                    "active_days bigint DEFAULT 0 COMMENT '账号活跃天数'," +
                    "public_repos int DEFAULT 0," +
                    "followers int DEFAULT 0," +
                    "fetch_time datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
                    "PRIMARY KEY (id)," +
                    "UNIQUE KEY uk_github_event_stats_user (github_username)" +
                    ")");

            // 3) credit_threshold：分位数阈值快照，供 V2 的等级判定使用
            jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS credit_threshold (" +
                    "id bigint NOT NULL AUTO_INCREMENT," +
                    "scene varchar(50) NOT NULL," +
                    "algo_version varchar(10) NOT NULL DEFAULT 'v2'," +
                    "sample_size int NOT NULL DEFAULT 0 COMMENT '计算时的样本量 N'," +
                    "p20 int NULL COMMENT 'N>=30 时有效'," +
                    "p50 int NULL," +
                    "p80 int NULL," +
                    "fallback_used tinyint(1) DEFAULT 0 COMMENT '1=样本不足已退回静态 85/70/60'," +
                    "compute_time datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
                    "PRIMARY KEY (id)," +
                    "UNIQUE KEY uk_threshold_scene_algo (scene, algo_version)" +
                    ")");

            ensureIndex(jdbcTemplate, "github_event_stats", "idx_ges_user", "CREATE INDEX idx_ges_user ON github_event_stats(github_username)");

            ensureColumn(jdbcTemplate, "github_event_stats", "repo_stars_total",
                    "ALTER TABLE github_event_stats ADD COLUMN repo_stars_total int DEFAULT 0 COMMENT '采样公开仓库 stargazers 合计' AFTER followers");

            // 4) ai_profile_snapshot：AI 开发者画像快照（DeepSeek 生成，带 TTL 缓存）
            jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS ai_profile_snapshot (" +
                    "id bigint NOT NULL AUTO_INCREMENT," +
                    "github_username varchar(100) NOT NULL," +
                    "scene varchar(50) NOT NULL DEFAULT '综合'," +
                    "algo_version varchar(10) NOT NULL DEFAULT 'v2'," +
                    "profile_version varchar(20) NOT NULL DEFAULT 'v1'," +
                    "prompt_version varchar(20) NOT NULL DEFAULT 'v1'," +
                    "model varchar(50) NULL," +
                    "data_hash varchar(64) NULL," +
                    "summary varchar(500) NULL," +
                    "profile_json longtext NULL," +
                    "tech_tags_json text NULL," +
                    "top_repos_json text NULL," +
                    "evidence_json longtext NULL," +
                    "token_usage int NULL," +
                    "cost_estimate decimal(10,4) NULL," +
                    "status varchar(20) NOT NULL DEFAULT 'ready'," +
                    "error_message varchar(500) NULL," +
                    "created_at datetime DEFAULT CURRENT_TIMESTAMP," +
                    "updated_at datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
                    "expires_at datetime NULL," +
                    "PRIMARY KEY (id)," +
                    "UNIQUE KEY uk_ai_profile_user_scene (github_username, scene)" +
                    ")");
            ensureIndex(jdbcTemplate, "ai_profile_snapshot", "idx_ai_profile_user", "CREATE INDEX idx_ai_profile_user ON ai_profile_snapshot(github_username)");
            ensureIndex(jdbcTemplate, "ai_profile_snapshot", "idx_ai_profile_expires", "CREATE INDEX idx_ai_profile_expires ON ai_profile_snapshot(expires_at)");

            // 5) 协作平台核心层（一期）：项目/成员/任务/评论/附件/验收互评/动态流/审计
            jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS project (" +
                    "id bigint NOT NULL AUTO_INCREMENT," +
                    "name varchar(120) NOT NULL," +
                    "description text NULL," +
                    "visibility varchar(20) NOT NULL DEFAULT 'private' COMMENT 'private/public'," +
                    "owner_user_id bigint NOT NULL COMMENT '创建者/Owner'," +
                    "created_at datetime DEFAULT CURRENT_TIMESTAMP," +
                    "updated_at datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
                    "PRIMARY KEY (id)," +
                    "KEY idx_project_owner (owner_user_id)," +
                    "KEY idx_project_visibility (visibility)" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");

            jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS project_member (" +
                    "id bigint NOT NULL AUTO_INCREMENT," +
                    "project_id bigint NOT NULL," +
                    "user_id bigint NOT NULL," +
                    "role varchar(20) NOT NULL DEFAULT 'dev' COMMENT 'owner/maintainer/dev/viewer'," +
                    "joined_at datetime DEFAULT CURRENT_TIMESTAMP," +
                    "created_at datetime DEFAULT CURRENT_TIMESTAMP," +
                    "updated_at datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
                    "PRIMARY KEY (id)," +
                    "UNIQUE KEY uk_project_member (project_id, user_id)," +
                    "KEY idx_pm_user (user_id)," +
                    "KEY idx_pm_project (project_id)" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");

            jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS issue (" +
                    "id bigint NOT NULL AUTO_INCREMENT," +
                    "project_id bigint NOT NULL," +
                    "title varchar(200) NOT NULL," +
                    "description longtext NULL," +
                    "status varchar(20) NOT NULL DEFAULT 'todo' COMMENT 'todo/doing/done'," +
                    "priority varchar(20) NOT NULL DEFAULT 'medium' COMMENT 'low/medium/high/urgent'," +
                    "labels_json text NULL COMMENT '标签数组 JSON'," +
                    "assignee_user_id bigint NULL COMMENT '负责人'," +
                    "due_at datetime NULL COMMENT '截止时间'," +
                    "created_by bigint NOT NULL," +
                    "created_at datetime DEFAULT CURRENT_TIMESTAMP," +
                    "updated_at datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
                    "PRIMARY KEY (id)," +
                    "KEY idx_issue_project (project_id)," +
                    "KEY idx_issue_status (project_id, status)," +
                    "KEY idx_issue_assignee (assignee_user_id)," +
                    "KEY idx_issue_creator (created_by)" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");

            jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS issue_comment (" +
                    "id bigint NOT NULL AUTO_INCREMENT," +
                    "issue_id bigint NOT NULL," +
                    "project_id bigint NOT NULL," +
                    "user_id bigint NOT NULL," +
                    "content longtext NOT NULL," +
                    "created_at datetime DEFAULT CURRENT_TIMESTAMP," +
                    "updated_at datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
                    "PRIMARY KEY (id)," +
                    "KEY idx_comment_issue (issue_id)," +
                    "KEY idx_comment_project (project_id)," +
                    "KEY idx_comment_user (user_id)" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");

            jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS issue_attachment (" +
                    "id bigint NOT NULL AUTO_INCREMENT," +
                    "issue_id bigint NOT NULL," +
                    "project_id bigint NOT NULL," +
                    "user_id bigint NOT NULL," +
                    "original_name varchar(255) NOT NULL," +
                    "storage_path varchar(500) NOT NULL COMMENT '相对 uploads/ 路径'," +
                    "content_type varchar(120) NULL," +
                    "size_bytes bigint NOT NULL DEFAULT 0," +
                    "sha256 varchar(64) NULL," +
                    "created_at datetime DEFAULT CURRENT_TIMESTAMP," +
                    "PRIMARY KEY (id)," +
                    "KEY idx_att_issue (issue_id)," +
                    "KEY idx_att_project (project_id)," +
                    "KEY idx_att_user (user_id)" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");

            jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS issue_review (" +
                    "id bigint NOT NULL AUTO_INCREMENT," +
                    "issue_id bigint NOT NULL," +
                    "project_id bigint NOT NULL," +
                    "reviewer_user_id bigint NOT NULL," +
                    "rating int NOT NULL COMMENT '1-5'," +
                    "comment varchar(1000) NULL," +
                    "created_at datetime DEFAULT CURRENT_TIMESTAMP," +
                    "PRIMARY KEY (id)," +
                    "UNIQUE KEY uk_issue_reviewer (issue_id, reviewer_user_id)," +
                    "KEY idx_review_issue (issue_id)," +
                    "KEY idx_review_project (project_id)," +
                    "KEY idx_review_reviewer (reviewer_user_id)" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");

            jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS project_activity (" +
                    "id bigint NOT NULL AUTO_INCREMENT," +
                    "project_id bigint NOT NULL," +
                    "actor_user_id bigint NOT NULL," +
                    "type varchar(50) NOT NULL COMMENT 'issue_create/status_change/comment/attachment/review/...'," +
                    "ref_type varchar(30) NULL COMMENT 'issue/comment/attachment/review'," +
                    "ref_id bigint NULL," +
                    "summary varchar(300) NOT NULL," +
                    "detail longtext NULL," +
                    "created_at datetime DEFAULT CURRENT_TIMESTAMP," +
                    "PRIMARY KEY (id)," +
                    "KEY idx_pa_project (project_id, created_at)," +
                    "KEY idx_pa_actor (actor_user_id)," +
                    "KEY idx_pa_ref (ref_type, ref_id)" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");

            jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS audit_log (" +
                    "id bigint NOT NULL AUTO_INCREMENT," +
                    "user_id bigint NULL," +
                    "action varchar(80) NOT NULL," +
                    "entity_type varchar(40) NULL," +
                    "entity_id bigint NULL," +
                    "ip varchar(64) NULL," +
                    "user_agent varchar(255) NULL," +
                    "detail longtext NULL," +
                    "created_at datetime DEFAULT CURRENT_TIMESTAMP," +
                    "PRIMARY KEY (id)," +
                    "KEY idx_audit_user (user_id, created_at)," +
                    "KEY idx_audit_entity (entity_type, entity_id)" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");

            log.info("[schema] database schema initialization done");
        };
    }

    private void initBadgeDef(JdbcTemplate jdbcTemplate, String code, String name, String desc, String icon, int sort) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM badge_def WHERE code = ?", Integer.class, code);
        if (count != null && count == 0) {
            jdbcTemplate.update(
                    "INSERT INTO badge_def(code, name, description, icon, sort) VALUES(?,?,?,?,?)",
                    code, name, desc, icon, sort);
        }
    }

    private void ensureColumn(
            @NonNull JdbcTemplate jdbcTemplate,
            @NonNull String tableName,
            @NonNull String columnName,
            @NonNull String ddl
    ) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?",
                Integer.class,
                tableName,
                columnName
        );
        if (count != null && count == 0) {
            jdbcTemplate.execute(ddl);
        }
    }

    private void ensureIndex(
            @NonNull JdbcTemplate jdbcTemplate,
            @NonNull String tableName,
            @NonNull String indexName,
            @NonNull String ddl
    ) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND INDEX_NAME = ?",
                Integer.class,
                tableName,
                indexName
        );
        if (count != null && count == 0) {
            log.info("[schema] create index {} on {}", indexName, tableName);
            jdbcTemplate.execute(ddl);
        }
    }

    private void ensureUniqueIndex(
            @NonNull JdbcTemplate jdbcTemplate,
            @NonNull String tableName,
            @NonNull String columnName,
            @NonNull String indexName,
            @NonNull String ddl
    ) {
        log.info("[schema] ensure unique index {} on {}({})", indexName, tableName, columnName);
        String duplicateSql = "SELECT `" + columnName + "` AS duplicate_value, COUNT(*) AS duplicate_count " +
                "FROM `" + tableName + "` " +
                "WHERE `" + columnName + "` IS NOT NULL AND TRIM(`" + columnName + "`) <> '' " +
                "GROUP BY `" + columnName + "` HAVING COUNT(*) > 1";
        List<Map<String, Object>> duplicates = jdbcTemplate.queryForList(duplicateSql);
        if (!duplicates.isEmpty()) {
            Object duplicateValue = duplicates.get(0).get("duplicate_value");
            log.error("[schema] found duplicated {}.{} value example={}", tableName, columnName, duplicateValue);
            throw new IllegalStateException("表 " + tableName + " 中存在重复的 " + columnName + " 值（例如: " + duplicateValue + "），请先清理重复绑定数据后再启动系统。");
        }

        List<Map<String, Object>> indexes = jdbcTemplate.queryForList(
                "SELECT NON_UNIQUE FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND INDEX_NAME = ? LIMIT 1",
                tableName,
                indexName
        );
        if (!indexes.isEmpty()) {
            Number nonUnique = (Number) indexes.get(0).get("NON_UNIQUE");
            if (nonUnique != null && nonUnique.intValue() == 0) {
                log.info("[schema] unique index {} already exists", indexName);
                return;
            }
            log.warn("[schema] index {} exists but non-unique, dropping it first", indexName);
            jdbcTemplate.execute("DROP INDEX `" + indexName + "` ON `" + tableName + "`");
        }
        log.info("[schema] create unique index {} on {}({})", indexName, tableName, columnName);
        jdbcTemplate.execute(ddl);
    }

    private void initSystemConfig(JdbcTemplate jdbcTemplate, String key, String value, String desc) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM system_config WHERE config_key = ?",
                Integer.class, key);
        if (count != null && count == 0) {
            jdbcTemplate.update(
                    "INSERT INTO system_config(config_key, config_value, description) VALUES(?, ?, ?)",
                    key, value, desc);
        }
    }

    private void initSceneWeight(JdbcTemplate jdbcTemplate, String sceneName,
                                  String stability, String prQuality,
                                  String collaboration, String compliance, int isDefault) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM scene_weight_config WHERE scene_name = ?",
                Integer.class, sceneName);
        if (count != null && count == 0) {
            jdbcTemplate.update(
                    "INSERT INTO scene_weight_config(scene_name, stability_weight, pr_quality_weight, collaboration_weight, compliance_weight, is_default) VALUES(?,?,?,?,?,?)",
                    sceneName, stability, prQuality, collaboration, compliance, isDefault);
        }
    }
}
