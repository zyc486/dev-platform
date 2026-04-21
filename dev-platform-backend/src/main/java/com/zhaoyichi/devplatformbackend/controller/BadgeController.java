package com.zhaoyichi.devplatformbackend.controller;

import com.zhaoyichi.devplatformbackend.common.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 徽章相关只读接口：公开可见，用于个人中心「徽章墙」。
 */
@RestController
@RequestMapping("/api/badge")
@CrossOrigin(origins = "*")
public class BadgeController {

    @Autowired
    private JdbcTemplate jdbc;

    /**
     * 查询某用户的完整徽章列表：已获得的带 obtainTime，未获得的灰色展示。
     */
    @GetMapping("/user/{userId}")
    public Result<List<Map<String, Object>>> ofUser(@PathVariable Long userId) {
        List<Map<String, Object>> list = jdbc.queryForList(
                "SELECT d.code, d.name, d.description, d.icon, " +
                        "       CASE WHEN ub.user_id IS NULL THEN 0 ELSE 1 END AS obtained, " +
                        "       DATE_FORMAT(ub.obtain_time, '%Y-%m-%d') AS obtainTime " +
                        "FROM badge_def d " +
                        "LEFT JOIN user_badge ub ON ub.badge_code = d.code AND ub.user_id = ? " +
                        "ORDER BY d.sort", userId);
        return Result.success(list);
    }

    /**
     * 全部徽章定义（可用于徽章介绍页 / 引导图鉴）。
     */
    @GetMapping("/definitions")
    public Result<List<Map<String, Object>>> definitions() {
        return Result.success(jdbc.queryForList(
                "SELECT code, name, description, icon FROM badge_def ORDER BY sort"));
    }
}
