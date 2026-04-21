package com.zhaoyichi.devplatformbackend.controller;

import com.zhaoyichi.devplatformbackend.common.Result;
import com.zhaoyichi.devplatformbackend.utils.AuthHelper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/feed")
@CrossOrigin
public class FeedController {

    private final JdbcTemplate jdbcTemplate;

    public FeedController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping("/following")
    public Result<List<Map<String, Object>>> followingFeed(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            HttpServletRequest request) {
        Result<List<Map<String, Object>>> auth = AuthHelper.requireLogin(request);
        if (auth != null) return auth;

        Long userId = AuthHelper.currentUserId(request);
        int offset = (page - 1) * size;

        String sql = "SELECT p.id, p.title, p.content, p.like_count AS likeCount, " +
                "p.collect_count AS collectCount, " +
                "DATE_FORMAT(p.create_time, '%Y-%m-%d %H:%i') AS time, " +
                "u.id AS authorId, u.username AS authorName, " +
                "COALESCE(u.nickname, u.username) AS authorNickname, " +
                "u.avatar AS authorAvatar " +
                "FROM user_post p " +
                "JOIN user u ON p.user_id = u.id " +
                "JOIN user_follow f ON f.follow_user_id = u.id " +
                "WHERE f.user_id = ? AND COALESCE(p.status, 'approved') = 'approved' " +
                "ORDER BY p.create_time DESC " +
                "LIMIT ? OFFSET ?";

        List<Map<String, Object>> list = jdbcTemplate.queryForList(sql, userId, size, offset);
        return Result.success(list);
    }
}
