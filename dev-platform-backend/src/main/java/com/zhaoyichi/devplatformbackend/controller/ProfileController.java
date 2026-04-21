package com.zhaoyichi.devplatformbackend.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.zhaoyichi.devplatformbackend.common.Result;
import com.zhaoyichi.devplatformbackend.entity.CreditScore;
import com.zhaoyichi.devplatformbackend.entity.User;
import com.zhaoyichi.devplatformbackend.mapper.CreditScoreMapper;
import com.zhaoyichi.devplatformbackend.mapper.UserMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/profile")
@CrossOrigin
public class ProfileController {

    private final UserMapper userMapper;
    private final CreditScoreMapper creditScoreMapper;
    private final JdbcTemplate jdbcTemplate;

    public ProfileController(UserMapper userMapper,
                              CreditScoreMapper creditScoreMapper,
                              JdbcTemplate jdbcTemplate) {
        this.userMapper = userMapper;
        this.creditScoreMapper = creditScoreMapper;
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping("/{username}")
    public Result<Map<String, Object>> getProfile(@PathVariable String username) {
        QueryWrapper<User> userQuery = new QueryWrapper<>();
        userQuery.eq("username", username);
        User user = userMapper.selectOne(userQuery);
        if (user == null) return Result.error("用户不存在");

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", user.getId());
        result.put("username", user.getUsername());
        result.put("nickname", user.getNickname());
        result.put("avatar", user.getAvatar());
        result.put("bio", user.getBio());
        result.put("githubUsername", user.getGithubUsername());
        result.put("techTags", user.getTechTags());
        boolean creditPublic = user.getPrivacyCreditPublic() == null || user.getPrivacyCreditPublic() == 1;
        result.put("level", creditPublic ? user.getLevel() : null);
        result.put("totalScore", creditPublic ? user.getTotalScore() : null);
        result.put("status", user.getStatus());

        if (creditPublic && user.getGithubUsername() != null) {
            QueryWrapper<CreditScore> csQuery = new QueryWrapper<>();
            csQuery.eq("github_username", user.getGithubUsername()).eq("scene", "综合");
            CreditScore cs = creditScoreMapper.selectOne(csQuery);
            result.put("creditScore", cs);
        } else {
            result.put("creditScore", null);
        }

        long followCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM user_follow WHERE user_id = ?", Long.class, user.getId());
        long fansCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM user_follow WHERE follow_user_id = ?", Long.class, user.getId());
        result.put("followCount", followCount);
        result.put("fansCount", fansCount);

        List<Map<String, Object>> recentPosts = jdbcTemplate.queryForList(
                "SELECT id, title, content, like_count AS likeCount, collect_count AS collectCount, " +
                        "DATE_FORMAT(create_time, '%Y-%m-%d %H:%i') AS time " +
                        "FROM user_post WHERE user_id = ? AND COALESCE(status,'approved')='approved' " +
                        "ORDER BY create_time DESC LIMIT 5",
                user.getId());
        result.put("recentPosts", recentPosts);

        List<Map<String, Object>> recentCollabs = jdbcTemplate.queryForList(
                "SELECT id, title, status, DATE_FORMAT(create_time,'%Y-%m-%d') AS createTime " +
                        "FROM collaboration WHERE user_id = ? ORDER BY create_time DESC LIMIT 5",
                user.getId());
        result.put("recentCollabs", recentCollabs);

        return Result.success(result);
    }
}
