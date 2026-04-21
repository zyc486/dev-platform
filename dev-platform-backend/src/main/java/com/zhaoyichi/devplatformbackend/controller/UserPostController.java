package com.zhaoyichi.devplatformbackend.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.zhaoyichi.devplatformbackend.entity.User;
import com.zhaoyichi.devplatformbackend.entity.UserPost;
import com.zhaoyichi.devplatformbackend.mapper.UserMapper;
import com.zhaoyichi.devplatformbackend.mapper.UserPostMapper;
import com.zhaoyichi.devplatformbackend.common.Result;
import com.zhaoyichi.devplatformbackend.service.BadgeService;
import com.zhaoyichi.devplatformbackend.service.MentionService;
import com.zhaoyichi.devplatformbackend.service.MessageNoticeService;
import com.zhaoyichi.devplatformbackend.utils.AuthHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@RestController
@RequestMapping("/api/post")
@CrossOrigin
public class UserPostController {

    @Autowired
    private UserPostMapper postMapper;

    @Autowired
    private UserMapper userMapper;

    // 🌟 引入 JdbcTemplate，用于极速操作 user_post_collect 表
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MessageNoticeService messageNoticeService;

    @Autowired
    private BadgeService badgeService;

    @Autowired
    private MentionService mentionService;

    private final DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    // =====================================
    // 1. 发布帖子
    // =====================================
    @PostMapping("/publish")
    public Result<String> publish(@RequestBody UserPost post, HttpServletRequest request) {
        Integer userId = resolveUserId(request, post.getUserId());
        if (userId == null) {
            return Result.error("发帖失败：未能获取到当前用户的身份信息");
        }
        post.setUserId(userId);
        post.setLikeCount(0);
        post.setCollectCount(0);
        post.setCreateTime(LocalDateTime.now());
        // 规范化标签：去空格、首尾逗号
        if (post.getTags() != null) {
            String normalized = post.getTags().trim().replaceAll("\\s*,\\s*", ",");
            while (normalized.startsWith(",")) normalized = normalized.substring(1);
            while (normalized.endsWith(","))   normalized = normalized.substring(0, normalized.length() - 1);
            post.setTags(normalized.isEmpty() ? null : normalized);
        }
        postMapper.insert(post);

        // 功能 E：@ 提及通知
        try { mentionService.parseAndNotify(post.getContent(), post.getId() == null ? null : post.getId().longValue(), userId.longValue()); } catch (Exception ignore) {}
        // 功能 D：成就徽章（首次发帖 / 活跃创作者）
        try { badgeService.afterPublishPost(userId.longValue()); } catch (Exception ignore) {}
        return Result.successMsg("发布成功！");
    }

    // =====================================
    // 2. 获取广场帖子列表（所有人） —— 支持标签筛选 + 排序方式
    //    tag=Java   只看带 Java 标签的
    //    sort=hot   按热度分排序（热度 = 赞*2 + 收藏*3 + 评论*5）；默认 sort=new
    // =====================================
    @GetMapping("/list")
    public Result<List<Map<String, Object>>> getList(@RequestParam(required = false) String tag,
                                                     @RequestParam(defaultValue = "new") String sort,
                                                     @RequestParam(defaultValue = "50") Integer limit) {
        StringBuilder sql = new StringBuilder(
                "SELECT p.id, p.title, p.content, p.tags, " +
                        "       p.like_count AS likeCount, p.collect_count AS collectCount, p.user_id AS userId, " +
                        "       u.username, u.avatar, " +
                        "       (SELECT COUNT(*) FROM post_comment c WHERE c.post_id = p.id) AS commentCount, " +
                        "       (p.like_count*2 + p.collect_count*3 " +
                        "         + (SELECT COUNT(*) FROM post_comment c WHERE c.post_id = p.id)*5) AS hotScore, " +
                        "       DATE_FORMAT(p.create_time, '%Y-%m-%d %H:%i') AS time " +
                        "FROM user_post p LEFT JOIN user u ON u.id = p.user_id " +
                        "WHERE COALESCE(p.status, 'approved') <> 'deleted' AND COALESCE(p.status, 'approved') <> 'rejected'");
        List<Object> args = new ArrayList<>();
        if (tag != null && !tag.trim().isEmpty()) {
            sql.append(" AND CONCAT(',', IFNULL(p.tags, ''), ',') LIKE ?");
            args.add("%," + tag.trim() + ",%");
        }
        sql.append("hot".equalsIgnoreCase(sort)
                ? " ORDER BY hotScore DESC, p.create_time DESC"
                : " ORDER BY p.create_time DESC");
        sql.append(" LIMIT ?");
        args.add(limit == null || limit <= 0 ? 50 : limit);
        return Result.success(jdbcTemplate.queryForList(Objects.requireNonNull(sql.toString(), "sql"), args.toArray()));
    }

    // =====================================
    // 2b. 功能 A：热门接口（等价于 /list?sort=hot，保留独立路由便于首页直连）
    // =====================================
    @GetMapping("/hot")
    public Result<List<Map<String, Object>>> getHot(@RequestParam(defaultValue = "20") Integer limit) {
        return getList(null, "hot", limit);
    }

    // =====================================
    // 2c. 功能 C：热门标签（聚合 user_post.tags，取 Top 10）
    // =====================================
    @GetMapping("/hotTags")
    public Result<List<Map<String, Object>>> hotTags() {
        String sql =
                "SELECT tag, COUNT(*) AS cnt FROM (" +
                "  SELECT TRIM(SUBSTRING_INDEX(SUBSTRING_INDEX(p.tags, ',', n.n), ',', -1)) AS tag " +
                "  FROM user_post p " +
                "  JOIN (SELECT 1 n UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5) n " +
                "    ON CHAR_LENGTH(IFNULL(p.tags, '')) - CHAR_LENGTH(REPLACE(IFNULL(p.tags, ''), ',', '')) >= n.n - 1 " +
                "  WHERE p.tags IS NOT NULL AND p.tags <> ''" +
                ") t WHERE tag <> '' GROUP BY tag ORDER BY cnt DESC LIMIT 10";
        return Result.success(jdbcTemplate.queryForList(sql));
    }

    // =====================================
    // 3. 点赞
    // =====================================
    @PostMapping("/like/{id}")
    public Result<String> like(@PathVariable Integer id, HttpServletRequest request) {
        UserPost post = postMapper.selectById(id);
        if (post != null) {
            post.setLikeCount(post.getLikeCount() + 1);
            postMapper.updateById(post);
            Long likerId = AuthHelper.currentUserId(request);
            if (likerId != null && post.getUserId() != null) {
                long authorId = post.getUserId().longValue();
                if (authorId != likerId) {
                    String title = post.getTitle() == null ? "（无标题）" : post.getTitle();
                    messageNoticeService.createNotice(authorId, "like", "帖子被点赞",
                            "有人点赞了你的帖子《" + title + "》", id.longValue());
                }
                // 功能 D：「社交达人」徽章（累计被点赞 >= 10）
                try { badgeService.afterLikeReceived(authorId); } catch (Exception ignore) {}
            }
            return Result.successMsg("点赞成功");
        }
        return Result.error("帖子不存在");
    }

    // =====================================
    // 🌟 4. 删除帖子 (新功能)
    // =====================================
    @DeleteMapping("/delete/{id}")
    public Result<String> deletePost(@PathVariable Integer id, HttpServletRequest request) {
        UserPost post = postMapper.selectById(id);
        if (post == null) {
            return Result.error("帖子不存在");
        }
        Integer currentUserId = resolveUserId(request, null);
        if (currentUserId == null || !currentUserId.equals(post.getUserId())) {
            return Result.error("无权删除他人帖子");
        }
        postMapper.deleteById(id);
        // 顺便清理掉别人收藏的这条记录
        jdbcTemplate.update("DELETE FROM user_post_collect WHERE post_id = ?", id);
        return Result.successMsg("删除成功");
    }

    // =====================================
    // 🌟 5. 收藏/取消收藏帖子 (新功能)
    // =====================================
    @PostMapping("/collect/{id}")
    public Result<String> collect(@PathVariable Integer id,
                                  @RequestParam(required = false) Integer userId,
                                  HttpServletRequest request) {
        UserPost post = postMapper.selectById(id);
        if(post == null) return Result.error("帖子不存在");
        Integer currentUserId = resolveUserId(request, userId);
        if (currentUserId == null) {
            return Result.error("未登录");
        }

        // 查询是否已经收藏
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM user_post_collect WHERE user_id = ? AND post_id = ?",
                Integer.class, currentUserId, id);

        if (count != null && count > 0) {
            // 已收藏 -> 取消收藏
            jdbcTemplate.update("DELETE FROM user_post_collect WHERE user_id = ? AND post_id = ?", currentUserId, id);
            post.setCollectCount(post.getCollectCount() - 1);
            postMapper.updateById(post);
            return Result.successMsg("已取消收藏");
        } else {
            // 未收藏 -> 添加收藏
            jdbcTemplate.update("INSERT INTO user_post_collect(user_id, post_id) VALUES(?, ?)", currentUserId, id);
            post.setCollectCount(post.getCollectCount() + 1);
            postMapper.updateById(post);
            return Result.successMsg("收藏成功！");
        }
    }

    // =====================================
    // 🌟 6. 查询“我的发布” (新功能)
    // =====================================
    @GetMapping("/myList")
    public Result<List<Map<String, Object>>> getMyList(@RequestParam(required = false) Integer userId,
                                                       HttpServletRequest request) {
        Integer currentUserId = resolveUserId(request, userId);
        if (currentUserId == null) {
            return Result.error("未登录");
        }
        QueryWrapper<UserPost> wrapper = new QueryWrapper<>();
        wrapper.eq("user_id", currentUserId).orderByDesc("create_time");
        return Result.success(formatPostList(postMapper.selectList(wrapper)));
    }

    // =====================================
    // 🌟 7. 查询“我的收藏夹” (新功能)
    // =====================================
    @GetMapping("/myCollect")
    public Result<List<Map<String, Object>>> getMyCollect(@RequestParam(required = false) Integer userId,
                                                          HttpServletRequest request) {
        Integer currentUserId = resolveUserId(request, userId);
        if (currentUserId == null) {
            return Result.error("未登录");
        }
        // 先查出这个人收藏了哪些帖子的ID
        List<Integer> postIds = jdbcTemplate.queryForList(
                "SELECT post_id FROM user_post_collect WHERE user_id = ? ORDER BY create_time DESC",
                Integer.class, currentUserId);

        if (postIds.isEmpty()) {
            return Result.success(new ArrayList<>()); // 没收藏直接返回空列表
        }

        // 用ID去查出真实的帖子详情
        QueryWrapper<UserPost> wrapper = new QueryWrapper<>();
        wrapper.in("id", postIds);
        return Result.success(formatPostList(postMapper.selectList(wrapper)));
    }

    // =====================================
    // 抽取出来的公共方法：把帖子列表转成带用户名的 Map 格式
    // =====================================
    private List<Map<String, Object>> formatPostList(List<UserPost> posts) {
        List<Map<String, Object>> resultList = new ArrayList<>();
        for (UserPost p : posts) {
            Map<String, Object> map = new HashMap<>();
            map.put("id", p.getId());
            map.put("title", p.getTitle());
            map.put("content", p.getContent());
            map.put("likeCount", p.getLikeCount());
            map.put("collectCount", p.getCollectCount());
            map.put("tags", p.getTags());
            map.put("time", p.getCreateTime() == null ? "" : p.getCreateTime().format(dtf));

            User user = userMapper.selectById(p.getUserId());
            map.put("username", user != null ? user.getUsername() : "未知用户");
            resultList.add(map);
        }
        return resultList;
    }
    // =====================================
    // 🌟 8. 发表评论 (真实后端保存 - 修复参数丢失问题)
    // =====================================
    @PostMapping("/comment/{postId}")
    public Result<String> addComment(@PathVariable Integer postId,
                                     @RequestBody Map<String, Object> params,
                                     HttpServletRequest request) {
        try {
            Object contentObj = params.get("content");

            if (contentObj == null || contentObj.toString().trim().isEmpty()) {
                return Result.error("参数不能为空");
            }

            Integer userId = resolveUserId(request, parseInteger(params.get("userId")));
            if (userId == null) {
                return Result.error("未登录");
            }
            String content = contentObj.toString();

            // 功能 B：楼中楼 —— parentId / replyTo 可选
            Long parentId = null;
            Object pidObj = params.get("parentId");
            if (pidObj != null && !String.valueOf(pidObj).isEmpty() && !"null".equalsIgnoreCase(String.valueOf(pidObj))) {
                try { parentId = Long.valueOf(String.valueOf(pidObj)); } catch (Exception ignore) {}
            }
            String replyTo = params.get("replyTo") == null ? null : String.valueOf(params.get("replyTo"));

            jdbcTemplate.update(
                    "INSERT INTO post_comment(post_id, user_id, content, parent_id, reply_to_user, create_time) VALUES(?, ?, ?, ?, ?, NOW())",
                    postId, userId, content, parentId, replyTo);

            UserPost post = postMapper.selectById(postId);
            User commenter = userMapper.selectById(userId);
            String commenterName = commenter != null && commenter.getUsername() != null ? commenter.getUsername() : "用户" + userId;

            if (post != null && post.getUserId() != null) {
                long authorId = post.getUserId().longValue();
                if (authorId != userId.longValue()) {
                    String ptitle = post.getTitle() == null ? "（无标题）" : post.getTitle();
                    messageNoticeService.createNotice(authorId, "comment", "新评论",
                            commenterName + " 评论了你的帖子《" + ptitle + "》", postId.longValue());
                }
            }

            // 回复楼中楼：单独给被回复用户一条通知（与楼主通知区分）
            if (replyTo != null && !replyTo.trim().isEmpty()
                    && (commenter == null || !replyTo.equals(commenter.getUsername()))) {
                User target = userMapper.selectOne(new QueryWrapper<User>().eq("username", replyTo));
                if (target != null && target.getId() != null && !target.getId().equals(userId.longValue())) {
                    messageNoticeService.createNotice(target.getId(), "comment", "有人回复了你的评论",
                            commenterName + " 回复你：" + (content.length() > 60 ? content.substring(0, 60) + "…" : content),
                            postId.longValue());
                }
            }

            // 功能 E：@ 提及
            try { mentionService.parseAndNotify(content, postId.longValue(), userId.longValue()); } catch (Exception ignore) {}

            return Result.successMsg("评论成功");
        } catch (Exception e) {
            e.printStackTrace();
            return Result.error("后端保存评论失败：" + e.getMessage());
        }
    }

    // =====================================
    // 🌟 9. 获取某帖子的真实评论列表（扁平返回；前端组树）
    // =====================================
    @GetMapping("/comments/{postId}")
    public Result<List<Map<String, Object>>> getComments(@PathVariable Integer postId) {
        try {
            String sql = "SELECT c.id, c.parent_id AS parentId, c.content AS text, c.reply_to_user AS replyTo, " +
                    "       DATE_FORMAT(c.create_time, '%Y-%m-%d %H:%i') AS time, " +
                    "       u.username AS user, u.avatar AS avatar " +
                    "FROM post_comment c LEFT JOIN user u ON c.user_id = u.id " +
                    "WHERE c.post_id = ? ORDER BY c.create_time ASC";

            List<Map<String, Object>> comments = jdbcTemplate.queryForList(sql, postId);
            return Result.success(comments);
        } catch (Exception e) {
            e.printStackTrace();
            return Result.error("获取评论失败");
        }
    }

    // =====================================
    // 🌟 10. 提交真实举报
    // =====================================
    @PostMapping("/report")
    public Result<String> reportPost(@RequestBody Map<String, Object> params, HttpServletRequest request) {
        try {
            // 1. 获取前端传来的举报参数
            Object postIdObj = params.get("postId");
            Object reasonObj = params.get("reason");

            // 2. 简单校验防空
            if (postIdObj == null || reasonObj == null || reasonObj.toString().trim().isEmpty()) {
                return Result.error("举报信息不完整或理由为空");
            }

            Integer postId = Integer.parseInt(postIdObj.toString());
            Integer reporterId = resolveUserId(request, parseInteger(params.get("reporterId")));
            if (reporterId == null) {
                return Result.error("未登录");
            }
            String reason = reasonObj.toString();

            // 3. 真实存入 post_report 数据库表，默认状态 'pending' (待处理)
            String sql = "INSERT INTO post_report(post_id, reporter_id, reason, status, create_time) VALUES (?, ?, ?, 'pending', NOW())";
            jdbcTemplate.update(sql, postId, reporterId, reason);

            return Result.successMsg("举报提交成功");
        } catch (Exception e) {
            e.printStackTrace();
            return Result.error("举报失败：" + e.getMessage());
        }
    }

    private Integer resolveUserId(HttpServletRequest request, Integer fallbackUserId) {
        Long currentUserId = AuthHelper.currentUserId(request);
        return currentUserId != null ? currentUserId.intValue() : fallbackUserId;
    }

    private Integer parseInteger(Object value) {
        if (value == null) {
            return null;
        }
        return Integer.parseInt(String.valueOf(value));
    }
}