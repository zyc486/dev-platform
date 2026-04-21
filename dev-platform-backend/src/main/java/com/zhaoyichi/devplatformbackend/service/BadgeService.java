package com.zhaoyichi.devplatformbackend.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 成就徽章服务：统一管理徽章解锁与通知推送。
 *
 * <p>解锁规则（见 {@code badge_def} 预置数据）：
 * <ul>
 *   <li>first_post：发布第一条动态</li>
 *   <li>active_poster：发帖数 >= 3</li>
 *   <li>credit_elite：综合信用分 >= 600</li>
 *   <li>github_binder：首次绑定 GitHub 账号</li>
 *   <li>social_butter：累计被点赞 >= 10</li>
 * </ul>
 *
 * <p>所有 {@code afterXxx} 方法均幂等，任何失败都不影响主业务：
 * 使用 {@code INSERT IGNORE} 及 try/catch 兜底，绝不把异常抛给调用方。</p>
 */
@Service
public class BadgeService {
    private static final Logger log = LoggerFactory.getLogger(BadgeService.class);

    private final JdbcTemplate jdbc;
    private final MessageNoticeService messageNoticeService;

    public BadgeService(JdbcTemplate jdbc, MessageNoticeService messageNoticeService) {
        this.jdbc = jdbc;
        this.messageNoticeService = messageNoticeService;
    }

    /**
     * 尝试为用户解锁某徽章；若之前未解锁，写站内消息通知。
     */
    public void tryUnlock(long userId, String code) {
        if (code == null || code.isEmpty()) {
            return;
        }
        try {
            int rows = jdbc.update(
                    "INSERT IGNORE INTO user_badge(user_id, badge_code) VALUES(?, ?)",
                    userId, code);
            if (rows > 0) {
                Map<String, Object> def = jdbc.queryForMap(
                        "SELECT name, description FROM badge_def WHERE code = ?", code);
                messageNoticeService.createNotice(
                        userId,
                        "system",
                        "获得新徽章：" + def.get("name"),
                        String.valueOf(def.get("description")),
                        null);
            }
        } catch (DataAccessException e) {
            log.warn("[badge] unlock failed userId={} code={}", userId, code, e);
        }
    }

    public void afterPublishPost(long userId) {
        Integer n0 = jdbc.queryForObject(
                "SELECT COUNT(*) FROM user_post WHERE user_id = ?", Integer.class, userId);
        int n = n0 == null ? 0 : n0;
        if (n >= 1) tryUnlock(userId, "first_post");
        if (n >= 3) tryUnlock(userId, "active_poster");
    }

    public void afterCreditQuery(long userId, Integer totalScore) {
        if (totalScore != null && totalScore >= 600) {
            tryUnlock(userId, "credit_elite");
        }
    }

    public void afterBindGithub(long userId) {
        tryUnlock(userId, "github_binder");
    }

    public void afterLikeReceived(long authorUserId) {
        Integer likes = jdbc.queryForObject(
                "SELECT COALESCE(SUM(like_count), 0) FROM user_post WHERE user_id = ?",
                Integer.class, authorUserId);
        if (likes != null && likes >= 10) {
            tryUnlock(authorUserId, "social_butter");
        }
    }
}
