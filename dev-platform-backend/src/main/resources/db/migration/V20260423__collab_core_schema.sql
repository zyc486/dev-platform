-- 协作平台核心层（一期）：项目/成员/任务/评论/附件/验收互评/动态流/审计

CREATE TABLE IF NOT EXISTS project (
    id bigint NOT NULL AUTO_INCREMENT,
    name varchar(120) NOT NULL,
    description text NULL,
    visibility varchar(20) NOT NULL DEFAULT 'private' COMMENT 'private/public',
    owner_user_id bigint NOT NULL COMMENT '创建者/Owner',
    created_at datetime DEFAULT CURRENT_TIMESTAMP,
    updated_at datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_project_owner (owner_user_id),
    KEY idx_project_visibility (visibility)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS project_member (
    id bigint NOT NULL AUTO_INCREMENT,
    project_id bigint NOT NULL,
    user_id bigint NOT NULL,
    role varchar(20) NOT NULL DEFAULT 'dev' COMMENT 'owner/maintainer/dev/viewer',
    joined_at datetime DEFAULT CURRENT_TIMESTAMP,
    created_at datetime DEFAULT CURRENT_TIMESTAMP,
    updated_at datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_project_member (project_id, user_id),
    KEY idx_pm_user (user_id),
    KEY idx_pm_project (project_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS issue (
    id bigint NOT NULL AUTO_INCREMENT,
    project_id bigint NOT NULL,
    title varchar(200) NOT NULL,
    description longtext NULL,
    status varchar(20) NOT NULL DEFAULT 'todo' COMMENT 'todo/doing/done',
    priority varchar(20) NOT NULL DEFAULT 'medium' COMMENT 'low/medium/high/urgent',
    labels_json text NULL COMMENT '标签数组 JSON',
    assignee_user_id bigint NULL COMMENT '负责人',
    due_at datetime NULL COMMENT '截止时间',
    created_by bigint NOT NULL,
    created_at datetime DEFAULT CURRENT_TIMESTAMP,
    updated_at datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_issue_project (project_id),
    KEY idx_issue_status (project_id, status),
    KEY idx_issue_assignee (assignee_user_id),
    KEY idx_issue_creator (created_by)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS issue_comment (
    id bigint NOT NULL AUTO_INCREMENT,
    issue_id bigint NOT NULL,
    project_id bigint NOT NULL,
    user_id bigint NOT NULL,
    content longtext NOT NULL,
    created_at datetime DEFAULT CURRENT_TIMESTAMP,
    updated_at datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_comment_issue (issue_id),
    KEY idx_comment_project (project_id),
    KEY idx_comment_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS issue_attachment (
    id bigint NOT NULL AUTO_INCREMENT,
    issue_id bigint NOT NULL,
    project_id bigint NOT NULL,
    user_id bigint NOT NULL,
    original_name varchar(255) NOT NULL,
    storage_path varchar(500) NOT NULL COMMENT '相对 uploads/ 路径',
    content_type varchar(120) NULL,
    size_bytes bigint NOT NULL DEFAULT 0,
    sha256 varchar(64) NULL,
    created_at datetime DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_att_issue (issue_id),
    KEY idx_att_project (project_id),
    KEY idx_att_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS issue_review (
    id bigint NOT NULL AUTO_INCREMENT,
    issue_id bigint NOT NULL,
    project_id bigint NOT NULL,
    reviewer_user_id bigint NOT NULL,
    rating int NOT NULL COMMENT '1-5',
    comment varchar(1000) NULL,
    created_at datetime DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_issue_reviewer (issue_id, reviewer_user_id),
    KEY idx_review_issue (issue_id),
    KEY idx_review_project (project_id),
    KEY idx_review_reviewer (reviewer_user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS project_activity (
    id bigint NOT NULL AUTO_INCREMENT,
    project_id bigint NOT NULL,
    actor_user_id bigint NOT NULL,
    type varchar(50) NOT NULL COMMENT 'issue_create/status_change/comment/attachment/review/...',
    ref_type varchar(30) NULL COMMENT 'issue/comment/attachment/review',
    ref_id bigint NULL,
    summary varchar(300) NOT NULL,
    detail longtext NULL,
    created_at datetime DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_pa_project (project_id, created_at),
    KEY idx_pa_actor (actor_user_id),
    KEY idx_pa_ref (ref_type, ref_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS audit_log (
    id bigint NOT NULL AUTO_INCREMENT,
    user_id bigint NULL,
    action varchar(80) NOT NULL,
    entity_type varchar(40) NULL,
    entity_id bigint NULL,
    ip varchar(64) NULL,
    user_agent varchar(255) NULL,
    detail longtext NULL,
    created_at datetime DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_audit_user (user_id, created_at),
    KEY idx_audit_entity (entity_type, entity_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

