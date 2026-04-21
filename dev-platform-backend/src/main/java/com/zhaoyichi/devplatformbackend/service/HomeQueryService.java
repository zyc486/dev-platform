package com.zhaoyichi.devplatformbackend.service;

import com.zhaoyichi.devplatformbackend.vo.HomeRecommendCollabVO;
import com.zhaoyichi.devplatformbackend.vo.HomeRecentPostVO;
import com.zhaoyichi.devplatformbackend.vo.HomeSummaryVO;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class HomeQueryService {
    private final JdbcTemplate jdbcTemplate;

    public HomeQueryService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public HomeSummaryVO getSummary() {
        HomeSummaryVO data = new HomeSummaryVO();
        data.setUserCount(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM user", Integer.class));
        data.setPostCount(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM user_post", Integer.class));
        data.setOpenCollabCount(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM collaboration WHERE status = 'pending'", Integer.class));
        data.setQueryCount7d(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM query_log WHERE query_time IS NOT NULL AND query_time >= DATE_SUB(NOW(), INTERVAL 7 DAY)",
                Integer.class
        ));
        return data;
    }

    public List<HomeRecommendCollabVO> getRecommendCollabs() {
        String sql = "SELECT c.id, c.title, c.content, c.min_credit AS minCredit, u.username AS creatorUsername " +
                "FROM collaboration c LEFT JOIN user u ON c.user_id = u.id " +
                "WHERE c.status = 'pending' ORDER BY c.create_time DESC LIMIT 5";
        return jdbcTemplate.query(sql, BeanPropertyRowMapper.newInstance(HomeRecommendCollabVO.class));
    }

    public List<HomeRecentPostVO> getRecentPosts() {
        String sql = "SELECT p.id, p.title, p.content, DATE_FORMAT(p.create_time, '%Y-%m-%d %H:%i') AS createTime, u.username " +
                "FROM user_post p LEFT JOIN user u ON p.user_id = u.id ORDER BY p.create_time DESC LIMIT 5";
        return jdbcTemplate.query(sql, BeanPropertyRowMapper.newInstance(HomeRecentPostVO.class));
    }
}
