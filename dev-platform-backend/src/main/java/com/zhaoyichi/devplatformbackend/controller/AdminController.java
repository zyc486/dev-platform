package com.zhaoyichi.devplatformbackend.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.zhaoyichi.devplatformbackend.common.Result;
import com.zhaoyichi.devplatformbackend.entity.Feedback;
import com.zhaoyichi.devplatformbackend.entity.SceneWeightConfig;
import com.zhaoyichi.devplatformbackend.entity.SystemConfig;
import com.zhaoyichi.devplatformbackend.entity.UserPost;
import com.zhaoyichi.devplatformbackend.mapper.SceneWeightConfigMapper;
import com.zhaoyichi.devplatformbackend.mapper.SystemConfigMapper;
import com.zhaoyichi.devplatformbackend.mapper.UserPostMapper;
import com.zhaoyichi.devplatformbackend.service.AdminLogService;
import com.zhaoyichi.devplatformbackend.service.AdminQueryService;
import com.zhaoyichi.devplatformbackend.service.CreditScoreService;
import com.zhaoyichi.devplatformbackend.service.FeedbackService;
import com.zhaoyichi.devplatformbackend.service.MessageNoticeService;
import com.zhaoyichi.devplatformbackend.service.UserService;
import com.zhaoyichi.devplatformbackend.utils.AuthHelper;
import com.zhaoyichi.devplatformbackend.vo.AdminActionLogVO;
import com.zhaoyichi.devplatformbackend.vo.AdminDashboardVO;
import com.zhaoyichi.devplatformbackend.vo.AdminQueryLogVO;
import com.zhaoyichi.devplatformbackend.vo.AdminReportVO;
import com.zhaoyichi.devplatformbackend.vo.AdminUserVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@CrossOrigin
public class AdminController {

    private final FeedbackService feedbackService;
    private final MessageNoticeService messageNoticeService;
    private final SceneWeightConfigMapper sceneWeightConfigMapper;
    private final SystemConfigMapper systemConfigMapper;
    private final AdminLogService adminLogService;
    private final AdminQueryService adminQueryService;
    private final UserService userService;
    private final CreditScoreService creditScoreService;
    private final UserPostMapper userPostMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    public AdminController(FeedbackService feedbackService,
                           MessageNoticeService messageNoticeService,
                           SceneWeightConfigMapper sceneWeightConfigMapper,
                           SystemConfigMapper systemConfigMapper,
                           AdminLogService adminLogService,
                           AdminQueryService adminQueryService,
                           UserService userService,
                           CreditScoreService creditScoreService,
                           UserPostMapper userPostMapper) {
        this.feedbackService = feedbackService;
        this.messageNoticeService = messageNoticeService;
        this.sceneWeightConfigMapper = sceneWeightConfigMapper;
        this.systemConfigMapper = systemConfigMapper;
        this.adminLogService = adminLogService;
        this.adminQueryService = adminQueryService;
        this.userService = userService;
        this.creditScoreService = creditScoreService;
        this.userPostMapper = userPostMapper;
    }

    @GetMapping("/dashboard")
    public Result<AdminDashboardVO> dashboard(HttpServletRequest request) {
        Result<AdminDashboardVO> auth = AuthHelper.requireAdmin(request);
        if (auth != null) {
            return auth;
        }
        return Result.success(adminQueryService.getDashboard());
    }

    @GetMapping("/users")
    public Result<List<AdminUserVO>> getUsers(HttpServletRequest request) {
        Result<List<AdminUserVO>> auth = AuthHelper.requireAdmin(request);
        if (auth != null) {
            return auth;
        }
        return Result.success(adminQueryService.getUsers());
    }

    @PostMapping("/user/toggleStatus/{id}")
    public Result<String> toggleUserStatus(@PathVariable Integer id, HttpServletRequest request) {
        Result<String> auth = AuthHelper.requireAdmin(request);
        if (auth != null) {
            return auth;
        }
        String newStatus = adminQueryService.toggleUserStatus(id);
        adminLogService.log(AuthHelper.currentUserId(request), AuthHelper.currentUsername(request), "toggle_user_status", "user", id.longValue(), "状态变更为 " + newStatus);
        return Result.successMsg("操作成功，当前状态：" + newStatus);
    }

    @GetMapping("/reports")
    public Result<List<AdminReportVO>> getReports(HttpServletRequest request) {
        Result<List<AdminReportVO>> auth = AuthHelper.requireAdmin(request);
        if (auth != null) {
            return auth;
        }
        return Result.success(adminQueryService.getReports());
    }

    @PostMapping("/report/handle/{id}")
    public Result<String> handleReport(@PathVariable Integer id, @RequestBody ReportHandleRequest params, HttpServletRequest request) {
        Result<String> auth = AuthHelper.requireAdmin(request);
        if (auth != null) {
            return auth;
        }
        adminQueryService.handleReport(id, params.getAction(), params.getPostId(), params.getAuthorId());
        adminLogService.log(AuthHelper.currentUserId(request), AuthHelper.currentUsername(request), "handle_report", "report", id.longValue(), "处理动作: " + params.getAction());
        return Result.successMsg("处理成功");
    }

    @GetMapping("/feedbacks")
    public Result<List<Feedback>> feedbacks(HttpServletRequest request) {
        Result<List<Feedback>> auth = AuthHelper.requireAdmin(request);
        if (auth != null) {
            return auth;
        }
        return Result.success(feedbackService.listAll());
    }

    @PostMapping("/feedback/reply")
    public Result<String> replyFeedback(@RequestBody ReplyFeedbackRequest replyFeedbackRequest, HttpServletRequest request) {
        Result<String> auth = AuthHelper.requireAdmin(request);
        if (auth != null) {
            return auth;
        }
        Feedback feedback = feedbackService.findById(replyFeedbackRequest.getFeedbackId());
        if (feedback == null) {
            return Result.error("反馈不存在");
        }
        feedback.setReplyContent(replyFeedbackRequest.getReplyContent());
        feedback.setStatus(replyFeedbackRequest.getStatus() == null ? "replied" : replyFeedbackRequest.getStatus());
        feedback.setReplyTime(LocalDateTime.now());
        feedbackService.update(feedback);
        messageNoticeService.createNotice(feedback.getUserId(), "system", "反馈已回复", "你的反馈《" + feedback.getTitle() + "》已收到管理员回复。", feedback.getId());
        adminLogService.log(AuthHelper.currentUserId(request), AuthHelper.currentUsername(request), "reply_feedback", "feedback", feedback.getId(), "回复反馈");
        return Result.successMsg("反馈回复成功");
    }

    @GetMapping("/sceneWeights")
    public Result<List<SceneWeightConfig>> sceneWeights(HttpServletRequest request) {
        Result<List<SceneWeightConfig>> auth = AuthHelper.requireAdmin(request);
        if (auth != null) {
            return auth;
        }
        QueryWrapper<SceneWeightConfig> wrapper = new QueryWrapper<>();
        wrapper.orderByAsc("id");
        return Result.success(sceneWeightConfigMapper.selectList(wrapper));
    }

    @PostMapping("/sceneWeight/save")
    public Result<String> saveSceneWeight(@RequestBody SceneWeightConfig config, HttpServletRequest request) {
        Result<String> auth = AuthHelper.requireAdmin(request);
        if (auth != null) {
            return auth;
        }
        sceneWeightConfigMapper.updateById(config);
        adminLogService.log(AuthHelper.currentUserId(request), AuthHelper.currentUsername(request), "save_scene_weight", "scene_weight_config", config.getId() == null ? null : config.getId().longValue(), "更新场景权重");
        return Result.successMsg("场景权重已保存");
    }

    @GetMapping("/systemConfigs")
    public Result<List<SystemConfig>> systemConfigs(HttpServletRequest request) {
        Result<List<SystemConfig>> auth = AuthHelper.requireAdmin(request);
        if (auth != null) {
            return auth;
        }
        return Result.success(systemConfigMapper.selectList(null));
    }

    @PostMapping("/systemConfig/save")
    public Result<String> saveSystemConfig(@RequestBody SystemConfig config, HttpServletRequest request) {
        Result<String> auth = AuthHelper.requireAdmin(request);
        if (auth != null) {
            return auth;
        }
        if (systemConfigMapper.selectById(config.getConfigKey()) == null) {
            systemConfigMapper.insert(config);
        } else {
            systemConfigMapper.updateById(config);
        }
        adminLogService.log(AuthHelper.currentUserId(request), AuthHelper.currentUsername(request), "save_system_config", "system_config", null, config.getConfigKey());
        return Result.successMsg("系统配置已保存");
    }

    @GetMapping("/logs/query")
    public Result<List<AdminQueryLogVO>> queryLogs(HttpServletRequest request) {
        Result<List<AdminQueryLogVO>> auth = AuthHelper.requireAdmin(request);
        if (auth != null) {
            return auth;
        }
        return Result.success(adminQueryService.getQueryLogs());
    }

    @GetMapping("/logs/admin")
    public Result<List<AdminActionLogVO>> adminLogs(HttpServletRequest request) {
        Result<List<AdminActionLogVO>> auth = AuthHelper.requireAdmin(request);
        if (auth != null) {
            return auth;
        }
        return Result.success(adminQueryService.getAdminLogs());
    }

    @PostMapping("/user/resetPassword/{id}")
    public Result<String> resetPassword(@PathVariable Integer id, HttpServletRequest request) {
        Result<String> auth = AuthHelper.requireAdmin(request);
        if (auth != null) return auth;
        Result<String> result = userService.resetPasswordByAdmin(Long.valueOf(id));
        adminLogService.log(AuthHelper.currentUserId(request), AuthHelper.currentUsername(request),
                "reset_password", "user", Long.valueOf(id), "重置用户密码");
        return result;
    }

    @PostMapping("/credit/recalculate")
    public Result<String> recalculateCredit(@RequestParam String githubUsername, HttpServletRequest request) {
        Result<String> auth = AuthHelper.requireAdmin(request);
        if (auth != null) return auth;
        QueryWrapper<com.zhaoyichi.devplatformbackend.entity.CreditScore> wrapper = new QueryWrapper<>();
        wrapper.eq("github_username", githubUsername);
        adminQueryService.getCreditScoreMapper().delete(wrapper);
        creditScoreService.queryCredit(githubUsername, "综合", null);
        adminLogService.log(AuthHelper.currentUserId(request), AuthHelper.currentUsername(request),
                "recalculate_credit", "credit_score", null, "重算信用: " + githubUsername);
        return Result.successMsg("信用分已清除缓存并触发重新计算：" + githubUsername);
    }

    @GetMapping("/posts/pending")
    public Result<List<Map<String, Object>>> pendingPosts(HttpServletRequest request) {
        Result<List<Map<String, Object>>> auth = AuthHelper.requireAdmin(request);
        if (auth != null) return auth;
        return Result.success(adminQueryService.getPendingPosts());
    }

    @PostMapping("/post/review/{id}")
    public Result<String> reviewPost(@PathVariable Integer id,
                                      @RequestBody PostReviewRequest req,
                                      HttpServletRequest request) {
        Result<String> auth = AuthHelper.requireAdmin(request);
        if (auth != null) return auth;

        UserPost post = userPostMapper.selectById(id);
        if (post == null) return Result.error("帖子不存在");
        post.setStatus("approved".equals(req.getAction()) ? "approved" : "rejected");
        userPostMapper.updateById(post);

        adminLogService.log(AuthHelper.currentUserId(request), AuthHelper.currentUsername(request),
                "review_post", "user_post", Long.valueOf(id), "审核动作: " + req.getAction());
        return Result.successMsg("审核完成");
    }

    @GetMapping("/logs/export")
    public void exportLogs(@RequestParam(defaultValue = "query") String type,
                            HttpServletRequest request, HttpServletResponse response) throws IOException {
        Result<String> auth = AuthHelper.requireAdmin(request);
        if (auth != null) {
            response.setStatus(401);
            return;
        }
        response.setContentType("text/csv; charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=\"logs_" + type + ".csv\"");
        response.getOutputStream().write(new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF});
        PrintWriter writer = response.getWriter();
        if ("query".equals(type)) {
            List<AdminQueryLogVO> logs = adminQueryService.getQueryLogs();
            writer.println("查询ID,用户ID,GitHub账号,场景,响应时间(ms),状态,总分,查询时间");
            for (AdminQueryLogVO log : logs) {
                writer.println(String.format("%s,%s,%s,%s,%s,%s,%s,%s",
                        log.getId(), log.getUserId(), log.getGithubUsername(),
                        log.getScene(), log.getResponseTime(), log.getStatus(),
                        log.getTotalScore(), log.getQueryTime()));
            }
        } else {
            List<AdminActionLogVO> logs = adminQueryService.getAdminLogs();
            writer.println("操作ID,管理员ID,管理员,操作类型,目标类型,目标ID,详情,时间");
            for (AdminActionLogVO log : logs) {
                writer.println(String.format("%s,%s,%s,%s,%s,%s,%s,%s",
                        log.getId(), log.getAdminUserId(), log.getAdminUsername(),
                        log.getActionType(), log.getTargetType(), log.getTargetId(),
                        log.getDetail(), log.getCreateTime()));
            }
        }
        writer.flush();
    }

    /**
     * 功能 F：登录审计 —— 管理员侧分页查询 login_log，支持按用户名 / 时间范围过滤。
     */
    @GetMapping("/loginLogs")
    public Result<Map<String, Object>> loginLogs(@RequestParam(required = false) String username,
                                                  @RequestParam(required = false) String from,
                                                  @RequestParam(required = false) String to,
                                                  @RequestParam(required = false) Integer success,
                                                  @RequestParam(defaultValue = "1") Integer page,
                                                  @RequestParam(defaultValue = "20") Integer size,
                                                  HttpServletRequest request) {
        Result<Map<String, Object>> auth = AuthHelper.requireAdmin(request);
        if (auth != null) return auth;

        StringBuilder where = new StringBuilder(" WHERE 1=1");
        List<Object> args = new ArrayList<>();
        if (username != null && !username.trim().isEmpty()) {
            where.append(" AND username LIKE ?");
            args.add("%" + username.trim() + "%");
        }
        if (from != null && !from.trim().isEmpty()) {
            where.append(" AND create_time >= ?");
            args.add(from.trim() + " 00:00:00");
        }
        if (to != null && !to.trim().isEmpty()) {
            where.append(" AND create_time <= ?");
            args.add(to.trim() + " 23:59:59");
        }
        if (success != null) {
            where.append(" AND success = ?");
            args.add(success);
        }

        Integer total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM login_log" + where, Integer.class, args.toArray());
        int safePage = page == null || page < 1 ? 1 : page;
        int safeSize = size == null || size < 1 ? 20 : Math.min(size, 200);

        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add((safePage - 1) * safeSize);
        pageArgs.add(safeSize);

        List<Map<String, Object>> list = jdbcTemplate.queryForList(
                "SELECT id, user_id AS userId, username, ip, user_agent AS userAgent, success, " +
                        "       fail_reason AS failReason, " +
                        "       DATE_FORMAT(create_time, '%Y-%m-%d %H:%i:%s') AS time " +
                        "FROM login_log" + where + " ORDER BY create_time DESC LIMIT ?, ?",
                pageArgs.toArray());

        Map<String, Object> data = new HashMap<>();
        data.put("list", list);
        data.put("total", total == null ? 0 : total);
        data.put("page", safePage);
        data.put("size", safeSize);
        return Result.success(data);
    }

    public static class PostReviewRequest {
        private String action;
        public String getAction() { return action; }
        public void setAction(String action) { this.action = action; }
    }

    public static class ReportHandleRequest {
        private String action;
        private Integer postId;
        private Integer authorId;

        public String getAction() {
            return action;
        }

        public void setAction(String action) {
            this.action = action;
        }

        public Integer getPostId() {
            return postId;
        }

        public void setPostId(Integer postId) {
            this.postId = postId;
        }

        public Integer getAuthorId() {
            return authorId;
        }

        public void setAuthorId(Integer authorId) {
            this.authorId = authorId;
        }
    }

    public static class ReplyFeedbackRequest {
        private Long feedbackId;
        private String replyContent;
        private String status;

        public Long getFeedbackId() {
            return feedbackId;
        }

        public void setFeedbackId(Long feedbackId) {
            this.feedbackId = feedbackId;
        }

        public String getReplyContent() {
            return replyContent;
        }

        public void setReplyContent(String replyContent) {
            this.replyContent = replyContent;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }
    }
}