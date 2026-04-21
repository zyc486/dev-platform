package com.zhaoyichi.devplatformbackend.controller;

import com.zhaoyichi.devplatformbackend.common.Result;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/search")
@CrossOrigin
public class SearchController {

    private final JdbcTemplate jdbcTemplate;

    public SearchController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 全局搜索
     * type: developer / community / collab / post （不传则全部搜索）
     */
    @GetMapping
    public Result<Map<String, Object>> search(
            @RequestParam String keyword,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String tags,
            @RequestParam(required = false) String level,
            @RequestParam(defaultValue = "10") int limit) {

        if (keyword == null || keyword.trim().isEmpty()) {
            return Result.error("请输入搜索关键词");
        }
        String kw = "%" + keyword.trim() + "%";
        int safeLimit = Math.min(limit, 50);
        Map<String, Object> result = new LinkedHashMap<>();

        if (type == null || "developer".equals(type)) {
            String sql = "SELECT u.id, u.username, COALESCE(u.nickname, u.username) AS nickname, " +
                    "u.avatar, u.github_username AS githubUsername, u.tech_tags AS techTags, " +
                    "COALESCE(cs.total_score, u.total_score, 0) AS score, " +
                    "COALESCE(cs.level, u.level, '未评估') AS level " +
                    "FROM user u " +
                    "LEFT JOIN credit_score cs ON cs.github_username = u.github_username AND cs.scene = '综合' " +
                    "WHERE (u.username LIKE ? OR u.nickname LIKE ? OR u.github_username LIKE ? OR u.tech_tags LIKE ?) ";
            List<Object> params = new ArrayList<>(Arrays.asList(kw, kw, kw, kw));
            if (tags != null && !tags.isEmpty()) {
                sql += "AND u.tech_tags LIKE ? ";
                params.add("%" + tags + "%");
            }
            if (level != null && !level.isEmpty()) {
                sql += "AND COALESCE(cs.level, u.level) = ? ";
                params.add(level);
            }
            sql += "LIMIT ?";
            params.add(safeLimit);
            result.put("developers", jdbcTemplate.queryForList(sql, params.toArray()));
        }

        if (type == null || "collab".equals(type)) {
            String sql = "SELECT c.id, c.title, c.content, c.min_credit AS minCredit, c.status, " +
                    "DATE_FORMAT(c.create_time, '%Y-%m-%d') AS createTime, u.username AS creatorName " +
                    "FROM collaboration c JOIN user u ON c.user_id = u.id " +
                    "WHERE c.status = 'pending' AND (c.title LIKE ? OR c.content LIKE ?) LIMIT ?";
            result.put("collabs", jdbcTemplate.queryForList(sql, kw, kw, safeLimit));
        }

        if (type == null || "post".equals(type)) {
            String sql = "SELECT p.id, p.title, p.content, p.like_count AS likeCount, " +
                    "DATE_FORMAT(p.create_time, '%Y-%m-%d %H:%i') AS time, u.username AS authorName " +
                    "FROM user_post p JOIN user u ON p.user_id = u.id " +
                    "WHERE COALESCE(p.status,'approved') = 'approved' AND (p.title LIKE ? OR p.content LIKE ?) " +
                    "ORDER BY p.create_time DESC LIMIT ?";
            result.put("posts", jdbcTemplate.queryForList(sql, kw, kw, safeLimit));
        }

        if (type == null || "community".equals(type)) {
            String sql = "SELECT id, name, description, avatar, tech_tags AS techTags, member_count AS memberCount, " +
                    "DATE_FORMAT(create_time, '%Y-%m-%d') AS createTime " +
                    "FROM community WHERE status = 'active' AND (name LIKE ? OR description LIKE ? OR tech_tags LIKE ?) LIMIT ?";
            result.put("communities", jdbcTemplate.queryForList(sql, kw, kw, kw, safeLimit));
        }

        return Result.success(result);
    }
}
