package com.zhaoyichi.devplatformbackend.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.zhaoyichi.devplatformbackend.common.Result;
import com.zhaoyichi.devplatformbackend.entity.UserFavorite;
import com.zhaoyichi.devplatformbackend.mapper.UserFavoriteMapper;
import com.zhaoyichi.devplatformbackend.utils.AuthHelper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/favorite")
@CrossOrigin
public class FavoriteController {

    private final UserFavoriteMapper favoriteMapper;
    private final JdbcTemplate jdbcTemplate;

    public FavoriteController(UserFavoriteMapper favoriteMapper, JdbcTemplate jdbcTemplate) {
        this.favoriteMapper = favoriteMapper;
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 切换收藏状态（收藏/取消收藏）
     * type: github_user / platform_user / credit_report / community
     * targetId: 对应的用户名或ID
     */
    @PostMapping("/toggle")
    public Result<String> toggle(@RequestBody ToggleRequest req, HttpServletRequest request) {
        Result<String> auth = AuthHelper.requireLogin(request);
        if (auth != null) return auth;

        Long userId = AuthHelper.currentUserId(request);
        if (req.getType() == null || req.getTargetId() == null) {
            return Result.error("参数不完整");
        }

        QueryWrapper<UserFavorite> wrapper = new QueryWrapper<>();
        wrapper.eq("user_id", userId)
                .eq("type", req.getType())
                .eq("target_id", req.getTargetId());
        UserFavorite existing = favoriteMapper.selectOne(wrapper);

        if (existing != null) {
            favoriteMapper.deleteById(existing.getId());
            return Result.successMsg("已取消收藏");
        } else {
            UserFavorite fav = new UserFavorite();
            fav.setUserId(userId);
            fav.setType(req.getType());
            fav.setTargetId(req.getTargetId());
            fav.setCreateTime(LocalDateTime.now());
            favoriteMapper.insert(fav);
            return Result.successMsg("收藏成功");
        }
    }

    @GetMapping("/list")
    public Result<List<Map<String, Object>>> list(@RequestParam(required = false) String type, HttpServletRequest request) {
        Result<List<Map<String, Object>>> auth = AuthHelper.requireLogin(request);
        if (auth != null) return auth;

        Long userId = AuthHelper.currentUserId(request);
        QueryWrapper<UserFavorite> wrapper = new QueryWrapper<>();
        wrapper.eq("user_id", userId);
        if (type != null && !type.isEmpty()) {
            wrapper.eq("type", type);
        }
        wrapper.orderByDesc("create_time");
        List<UserFavorite> favorites = favoriteMapper.selectList(wrapper);
        return Result.success(favorites.stream().map(this::toFavoriteItem).collect(Collectors.toList()));
    }

    @GetMapping("/check")
    public Result<Boolean> check(@RequestParam String type, @RequestParam String targetId, HttpServletRequest request) {
        Result<Boolean> auth = AuthHelper.requireLogin(request);
        if (auth != null) return auth;

        Long userId = AuthHelper.currentUserId(request);
        QueryWrapper<UserFavorite> wrapper = new QueryWrapper<>();
        wrapper.eq("user_id", userId).eq("type", type).eq("target_id", targetId);
        return Result.success(favoriteMapper.selectCount(wrapper) > 0);
    }

    public static class ToggleRequest {
        private String type;
        private String targetId;

        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public String getTargetId() { return targetId; }
        public void setTargetId(String targetId) { this.targetId = targetId; }
    }

    private Map<String, Object> toFavoriteItem(UserFavorite favorite) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", favorite.getId());
        item.put("type", favorite.getType());
        item.put("targetId", favorite.getTargetId());
        item.put("createTime", favorite.getCreateTime());

        String type = favorite.getType();
        if ("platform_user".equals(type) || "github_user".equals(type) || "credit_report".equals(type)) {
            fillDeveloperFavorite(item, favorite);
            return item;
        }
        if ("community".equals(type)) {
            fillCommunityFavorite(item, favorite);
            return item;
        }
        item.put("title", favorite.getTargetId());
        item.put("subtitle", "未识别的收藏类型");
        item.put("description", "当前收藏项暂不支持富展示。");
        return item;
    }

    private void fillDeveloperFavorite(Map<String, Object> item, UserFavorite favorite) {
        String sql = "SELECT u.id, u.username, COALESCE(u.nickname, u.username) AS nickname, u.avatar, " +
                "u.github_username AS githubUsername, u.bio, u.tech_tags AS techTags, " +
                "COALESCE(cs.total_score, u.total_score, 0) AS totalScore, " +
                "COALESCE(cs.level, u.level, '未评估') AS level " +
                "FROM user u " +
                "LEFT JOIN credit_score cs ON cs.github_username = u.github_username AND cs.scene = '综合' " +
                "WHERE CAST(u.id AS CHAR) = ? OR u.username = ? OR u.github_username = ? " +
                "LIMIT 1";
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, favorite.getTargetId(), favorite.getTargetId(), favorite.getTargetId());
        if (rows.isEmpty()) {
            item.put("title", favorite.getTargetId());
            item.put("subtitle", "开发者信息不存在或已被删除");
            item.put("description", "该收藏项已无法匹配到开发者详情。");
            return;
        }
        Map<String, Object> row = rows.get(0);
        item.putAll(row);
        item.put("title", row.get("nickname"));
        item.put("subtitle", "@" + value(row.get("username")) + " · " + value(row.get("level")) + " · " + value(row.get("totalScore")) + " 分");
        item.put("description", emptyToDefault((String) row.get("bio"), emptyToDefault((String) row.get("techTags"), "这个开发者还没有公开更多介绍。")));
    }

    private void fillCommunityFavorite(Map<String, Object> item, UserFavorite favorite) {
        String sql = "SELECT id, name, description, avatar, tech_tags AS techTags, member_count AS memberCount " +
                "FROM community WHERE CAST(id AS CHAR) = ? OR name = ? LIMIT 1";
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, favorite.getTargetId(), favorite.getTargetId());
        if (rows.isEmpty()) {
            item.put("title", favorite.getTargetId());
            item.put("subtitle", "社群不存在或已关闭");
            item.put("description", "该收藏项已无法匹配到社群详情。");
            return;
        }
        Map<String, Object> row = rows.get(0);
        item.putAll(row);
        item.put("title", row.get("name"));
        item.put("subtitle", value(row.get("memberCount")) + " 位成员");
        item.put("description", emptyToDefault((String) row.get("description"), emptyToDefault((String) row.get("techTags"), "该社群还没有公开简介。")));
    }

    private String value(Object obj) {
        return obj == null ? "-" : String.valueOf(obj);
    }

    private String emptyToDefault(String value, String defaultValue) {
        return value == null || value.trim().isEmpty() ? defaultValue : value.trim();
    }
}
