package com.zhaoyichi.devplatformbackend.service;

import com.zhaoyichi.devplatformbackend.mapper.CreditScoreMapper;
import com.zhaoyichi.devplatformbackend.vo.AdminActionLogVO;
import com.zhaoyichi.devplatformbackend.vo.AdminDashboardVO;
import com.zhaoyichi.devplatformbackend.vo.AdminQueryLogVO;
import com.zhaoyichi.devplatformbackend.vo.AdminReportVO;
import com.zhaoyichi.devplatformbackend.vo.AdminUserVO;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class AdminQueryService {
    private final JdbcTemplate jdbcTemplate;
    private final CreditScoreMapper creditScoreMapper;

    public AdminQueryService(JdbcTemplate jdbcTemplate, CreditScoreMapper creditScoreMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.creditScoreMapper = creditScoreMapper;
    }

    public CreditScoreMapper getCreditScoreMapper() {
        return creditScoreMapper;
    }

    public AdminDashboardVO getDashboard() {
        AdminDashboardVO data = new AdminDashboardVO();
        data.setUserCount(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM user", Integer.class));
        data.setPostCount(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM user_post", Integer.class));
        data.setFeedbackCount(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM feedback", Integer.class));
        data.setPendingReportCount(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM post_report WHERE status = 'pending'", Integer.class));
        data.setPendingFeedbackCount(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM feedback WHERE status = 'pending'", Integer.class));
        return data;
    }

    public List<AdminUserVO> getUsers() {
        String sql = "SELECT u.id, u.username, u.github_username AS githubUsername, " +
                "COALESCE(cs.level, u.level, '未评估') AS level, COALESCE(cs.total_score, u.total_score, 0) AS score, " +
                "DATE_FORMAT(u.create_time, '%Y-%m-%d') AS registerTime, u.status, u.role " +
                "FROM user u " +
                "LEFT JOIN credit_score cs ON cs.github_username = u.github_username AND cs.scene = '综合' " +
                "ORDER BY u.id DESC";
        return jdbcTemplate.query(sql, BeanPropertyRowMapper.newInstance(AdminUserVO.class));
    }

    public String toggleUserStatus(Integer id) {
        String currentStatus = jdbcTemplate.queryForObject("SELECT status FROM user WHERE id = ?", String.class, id);
        String newStatus = "normal".equals(currentStatus) ? "frozen" : "normal";
        jdbcTemplate.update("UPDATE user SET status = ? WHERE id = ?", newStatus, id);
        return newStatus;
    }

    public List<AdminReportVO> getReports() {
        String sql = "SELECT r.id, r.post_id AS postId, p.content AS target, p.user_id AS authorId, " +
                "r.reason AS type, u.username AS reporter, " +
                "DATE_FORMAT(r.create_time, '%Y-%m-%d %H:%i') AS reportTime, r.status " +
                "FROM post_report r " +
                "LEFT JOIN user_post p ON r.post_id = p.id " +
                "LEFT JOIN user u ON r.reporter_id = u.id " +
                "ORDER BY r.create_time DESC";
        return jdbcTemplate.query(sql, BeanPropertyRowMapper.newInstance(AdminReportVO.class));
    }

    public void handleReport(Integer id, String action, Integer postId, Integer authorId) {
        jdbcTemplate.update("UPDATE post_report SET status = 'processed' WHERE id = ?", id);
        if ("delete".equals(action) || "ban".equals(action)) {
            if (postId != null) {
                jdbcTemplate.update("DELETE FROM user_post WHERE id = ?", postId);
            }
        }
        if ("ban".equals(action) && authorId != null) {
            jdbcTemplate.update("UPDATE user SET status = 'frozen' WHERE id = ?", authorId);
        }
    }

    public List<AdminQueryLogVO> getQueryLogs() {
        String sql = "SELECT id, github_username AS githubUsername, scene, user_id AS userId, " +
                "DATE_FORMAT(query_time, '%Y-%m-%d %H:%i:%s') AS queryTime, response_time AS responseTime, status, total_score AS totalScore " +
                "FROM query_log ORDER BY id DESC LIMIT 100";
        return jdbcTemplate.query(sql, BeanPropertyRowMapper.newInstance(AdminQueryLogVO.class));
    }

    public List<AdminActionLogVO> getAdminLogs() {
        String sql = "SELECT id, admin_user_id AS adminUserId, admin_username AS adminUsername, action_type AS actionType, " +
                "target_type AS targetType, target_id AS targetId, detail, " +
                "DATE_FORMAT(create_time, '%Y-%m-%d %H:%i:%s') AS createTime FROM admin_action_log ORDER BY id DESC LIMIT 100";
        return jdbcTemplate.query(sql, BeanPropertyRowMapper.newInstance(AdminActionLogVO.class));
    }

    public List<Map<String, Object>> getPendingPosts() {
        String sql = "SELECT p.id, p.title, p.content, p.user_id AS userId, " +
                "DATE_FORMAT(p.create_time, '%Y-%m-%d %H:%i') AS createTime, " +
                "u.username AS authorName " +
                "FROM user_post p JOIN user u ON p.user_id = u.id " +
                "WHERE p.status = 'pending' ORDER BY p.create_time ASC";
        return jdbcTemplate.queryForList(sql);
    }
}
