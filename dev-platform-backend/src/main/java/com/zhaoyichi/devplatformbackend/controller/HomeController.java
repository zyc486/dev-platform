package com.zhaoyichi.devplatformbackend.controller;

import com.zhaoyichi.devplatformbackend.common.Result;
import com.zhaoyichi.devplatformbackend.service.HomeQueryService;
import com.zhaoyichi.devplatformbackend.service.TechPulseService;
import com.zhaoyichi.devplatformbackend.vo.HomeRecommendCollabVO;
import com.zhaoyichi.devplatformbackend.vo.HomeRecentPostVO;
import com.zhaoyichi.devplatformbackend.vo.HomeSummaryVO;
import com.zhaoyichi.devplatformbackend.vo.TechPulseItemVO;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/home")
@CrossOrigin
public class HomeController {
    private final HomeQueryService homeQueryService;
    private final TechPulseService techPulseService;
    private final JdbcTemplate jdbcTemplate;

    public HomeController(HomeQueryService homeQueryService, TechPulseService techPulseService, JdbcTemplate jdbcTemplate) {
        this.homeQueryService = homeQueryService;
        this.techPulseService = techPulseService;
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping("/summary")
    public Result<HomeSummaryVO> summary() {
        return Result.success(homeQueryService.getSummary());
    }

    @GetMapping("/recommendCollabs")
    public Result<List<HomeRecommendCollabVO>> recommendCollabs() {
        return Result.success(homeQueryService.getRecommendCollabs());
    }

    @GetMapping("/recentPosts")
    public Result<List<HomeRecentPostVO>> recentPosts() {
        return Result.success(homeQueryService.getRecentPosts());
    }

    @GetMapping("/hotDevelopers")
    public Result<List<Map<String, Object>>> hotDevelopers(@RequestParam(defaultValue = "10") int limit) {
        String sql = "SELECT u.id, u.username, COALESCE(u.nickname, u.username) AS nickname, " +
                "u.avatar, u.github_username AS githubUsername, u.tech_tags AS techTags, " +
                "COALESCE(cs.total_score, u.total_score, 0) AS score, " +
                "COALESCE(cs.level, u.level, '未评估') AS level " +
                "FROM user u " +
                "LEFT JOIN credit_score cs ON cs.github_username = u.github_username AND cs.scene = '综合' " +
                "WHERE u.status = 'normal' OR u.status IS NULL " +
                "ORDER BY COALESCE(cs.total_score, u.total_score, 0) DESC " +
                "LIMIT ?";
        return Result.success(jdbcTemplate.queryForList(sql, Math.min(limit, 50)));
    }

    @GetMapping("/hotPosts")
    public Result<List<Map<String, Object>>> hotPosts(@RequestParam(defaultValue = "10") int limit) {
        String sql = "SELECT p.id, p.title, p.content, p.like_count AS likeCount, " +
                "p.collect_count AS collectCount, " +
                "DATE_FORMAT(p.create_time, '%Y-%m-%d %H:%i') AS time, " +
                "u.username AS authorName, COALESCE(u.nickname, u.username) AS authorNickname, u.avatar AS authorAvatar " +
                "FROM user_post p " +
                "JOIN user u ON p.user_id = u.id " +
                "WHERE COALESCE(p.status, 'approved') = 'approved' " +
                "ORDER BY (p.like_count + p.collect_count) DESC, p.create_time DESC " +
                "LIMIT ?";
        return Result.success(jdbcTemplate.queryForList(sql, Math.min(limit, 50)));
    }

    /**
     * 精选文章：RSS / Atom + Dev.to 公开 API，按 {@code category} 缓存；{@code refresh=true} 跳过缓存。
     * 分类：all, tech, opensource, law, github, blockchain, devops, security, ai
     */
    @GetMapping("/techPulse")
    public Result<List<TechPulseItemVO>> techPulse(
            @RequestParam(defaultValue = "12") int limit,
            @RequestParam(defaultValue = "all") String category,
            @RequestParam(defaultValue = "false") String refresh
    ) {
        boolean force = "true".equalsIgnoreCase(refresh) || "1".equals(refresh);
        return Result.success(techPulseService.getPulse(limit, category, force));
    }
}
