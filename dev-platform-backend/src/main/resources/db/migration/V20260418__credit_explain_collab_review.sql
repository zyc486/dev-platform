-- 协作互评（1～5 星）与信用可解释性接口配套迁移（若已用 DatabaseSchemaInitializer 可重复执行，本脚本便于独立执行/论文附录）

CREATE TABLE IF NOT EXISTS collaboration_review (
    id bigint NOT NULL AUTO_INCREMENT,
    collaboration_id int NOT NULL COMMENT '协作主键 collaboration.id',
    from_user_id bigint NOT NULL,
    to_user_id bigint NOT NULL,
    rating tinyint NOT NULL COMMENT '1-5星',
    comment varchar(500) NULL,
    create_time datetime DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_collab_review_from_to (collaboration_id, from_user_id, to_user_id),
    KEY idx_collab_review_to_user (to_user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

UPDATE collaboration SET status = 'pending' WHERE status = 'open';
UPDATE collaboration SET status = 'cancelled' WHERE status = 'closed';
UPDATE collaboration SET status = 'completed' WHERE status = 'finished';
