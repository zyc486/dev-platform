package com.zhaoyichi.devplatformbackend.controller;

import com.zhaoyichi.devplatformbackend.common.Result;
import com.zhaoyichi.devplatformbackend.entity.Project;
import com.zhaoyichi.devplatformbackend.entity.ProjectMember;
import com.zhaoyichi.devplatformbackend.entity.User;
import com.zhaoyichi.devplatformbackend.mapper.UserMapper;
import com.zhaoyichi.devplatformbackend.mapper.CreditScoreMapper;
import com.zhaoyichi.devplatformbackend.entity.CreditScore;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.zhaoyichi.devplatformbackend.service.ai.AiProfileService;
import com.zhaoyichi.devplatformbackend.service.collab.CollabProjectReportHtmlBuilder;
import com.zhaoyichi.devplatformbackend.service.collab.CollabActivityService;
import com.zhaoyichi.devplatformbackend.service.collab.CollabAuditService;
import com.zhaoyichi.devplatformbackend.service.collab.CollabProjectService;
import com.zhaoyichi.devplatformbackend.utils.AuthHelper;
import lombok.Data;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/collab/project")
@CrossOrigin(origins = "*")
public class CollabProjectController {

    private final CollabProjectService collabProjectService;
    private final CollabActivityService collabActivityService;
    private final CollabAuditService collabAuditService;
    private final UserMapper userMapper;
    private final CreditScoreMapper creditScoreMapper;
    private final AiProfileService aiProfileService;
    private final CollabProjectReportHtmlBuilder collabProjectReportHtmlBuilder;

    public CollabProjectController(CollabProjectService collabProjectService,
                                   CollabActivityService collabActivityService,
                                   CollabAuditService collabAuditService,
                                   UserMapper userMapper,
                                   CreditScoreMapper creditScoreMapper,
                                   AiProfileService aiProfileService,
                                   CollabProjectReportHtmlBuilder collabProjectReportHtmlBuilder) {
        this.collabProjectService = collabProjectService;
        this.collabActivityService = collabActivityService;
        this.collabAuditService = collabAuditService;
        this.userMapper = userMapper;
        this.creditScoreMapper = creditScoreMapper;
        this.aiProfileService = aiProfileService;
        this.collabProjectReportHtmlBuilder = collabProjectReportHtmlBuilder;
    }

    @PostMapping("/create")
    public Result<Project> create(@RequestBody CreateBody body, HttpServletRequest request) {
        Result<Project> auth = AuthHelper.requireLogin(request);
        if (auth != null) return auth;
        Long uid = AuthHelper.currentUserId(request);
        if (body == null || body.getName() == null || body.getName().trim().isEmpty()) {
            return Result.error("name 不能为空");
        }
        Project p;
        try {
            p = collabProjectService.createProject(uid, body.getName().trim(), body.getDescription(), body.getVisibility());
        } catch (Exception e) {
            return Result.error(e.getMessage() == null ? "创建失败" : e.getMessage());
        }
        collabActivityService.add(p.getId(), uid, "project_create", "project", p.getId(), "创建项目：" + p.getName(), null);
        collabAuditService.record(request, uid, "project_create", "project", p.getId(), p.getName());
        return Result.success(p);
    }

    @GetMapping("/my")
    public Result<List<Project>> my(HttpServletRequest request) {
        Result<List<Project>> auth = AuthHelper.requireLogin(request);
        if (auth != null) return auth;
        Long uid = AuthHelper.currentUserId(request);
        return Result.success(collabProjectService.listMyProjects(uid));
    }

    @GetMapping("/detail")
    public Result<Map<String, Object>> detail(@RequestParam Long projectId, HttpServletRequest request) {
        Result<Map<String, Object>> auth = AuthHelper.requireLogin(request);
        if (auth != null) return auth;
        Long uid = AuthHelper.currentUserId(request);
        try {
            collabProjectService.requireMember(projectId, uid);
        } catch (Exception e) {
            return Result.error("无权限");
        }
        Project p = collabProjectService.getProject(projectId);
        List<ProjectMember> members = collabProjectService.listMembers(projectId);
        Map<String, Object> m = new HashMap<>();
        m.put("project", p);
        m.put("members", members);
        return Result.success(m);
    }

    /**
     * 团队画像：聚合项目成员的信用分与 AI 画像摘要（用于项目主页展示）。
     */
    @GetMapping("/teamProfile")
    public Result<Map<String, Object>> teamProfile(@RequestParam Long projectId,
                                                   @RequestParam(required = false, defaultValue = "综合") String scene,
                                                   HttpServletRequest request) {
        Result<Map<String, Object>> auth = AuthHelper.requireLogin(request);
        if (auth != null) return auth;
        Long uid = AuthHelper.currentUserId(request);
        try {
            collabProjectService.requireMember(projectId, uid);
        } catch (Exception e) {
            return Result.error("无权限");
        }
        Project p = collabProjectService.getProject(projectId);
        List<ProjectMember> members = collabProjectService.listMembers(projectId);
        List<Long> uids = members.stream().map(ProjectMember::getUserId).collect(Collectors.toList());
        List<User> users = uids.isEmpty() ? java.util.Collections.emptyList()
                : userMapper.selectList(new QueryWrapper<User>().in("id", uids));
        Map<Long, User> byId = users.stream().collect(Collectors.toMap(User::getId, it -> it, (a, b) -> a));

        // 读取信用分（优先使用库内快照，不触发外部请求）
        List<String> ghs = users.stream().map(User::getGithubUsername).filter(s -> s != null && !s.trim().isEmpty()).collect(Collectors.toList());
        Map<String, CreditScore> scoreByGh = new HashMap<>();
        if (!ghs.isEmpty()) {
            List<CreditScore> scores = creditScoreMapper.selectList(new QueryWrapper<CreditScore>()
                    .in("github_username", ghs)
                    .eq("scene", scene));
            for (CreditScore s : scores) {
                if (s != null && s.getGithubUsername() != null && !scoreByGh.containsKey(s.getGithubUsername())) {
                    scoreByGh.put(s.getGithubUsername(), s);
                }
            }
        }

        java.util.List<Map<String, Object>> out = new java.util.ArrayList<>();
        for (ProjectMember pm : members) {
            User u = byId.get(pm.getUserId());
            Map<String, Object> row = new HashMap<>();
            row.put("userId", pm.getUserId());
            row.put("role", pm.getRole());
            row.put("githubUsername", u == null ? null : u.getGithubUsername());
            row.put("nickname", u == null ? null : u.getNickname());
            row.put("avatar", u == null ? null : u.getAvatar());

            String gh = u == null ? null : u.getGithubUsername();
            CreditScore cs = gh == null ? null : scoreByGh.get(gh);
            if (cs != null) {
                Map<String, Object> credit = new HashMap<>();
                credit.put("totalScore", cs.getTotalScore());
                credit.put("level", cs.getLevel());
                credit.put("algoVersion", cs.getAlgoVersion());
                row.put("credit", credit);
            } else {
                row.put("credit", null);
            }

            // AI 画像：允许返回 refreshing/failed 的结构化响应
            if (gh != null && !gh.trim().isEmpty()) {
                try {
                    Map<String, Object> ai = aiProfileService.getProfile(gh, scene);
                    Map<String, Object> aiLite = new HashMap<>();
                    if (ai != null) {
                        aiLite.put("status", ai.get("status"));
                        aiLite.put("summary", ai.get("summary"));
                        aiLite.put("techTagsJson", ai.get("techTagsJson"));
                    }
                    row.put("aiProfile", aiLite);
                } catch (Exception ignore) {
                    row.put("aiProfile", null);
                }
            }
            out.add(row);
        }
        Map<String, Object> data = new HashMap<>();
        data.put("project", p);
        data.put("members", out);
        return Result.success(data);
    }

    @GetMapping("/exportReport")
    public void exportReport(@RequestParam Long projectId,
                             @RequestParam(required = false, defaultValue = "综合") String scene,
                             HttpServletRequest request,
                             javax.servlet.http.HttpServletResponse response) throws java.io.IOException {
        Result<?> auth = AuthHelper.requireLogin(request);
        if (auth != null) {
            response.setStatus(javax.servlet.http.HttpServletResponse.SC_UNAUTHORIZED);
            response.setCharacterEncoding("UTF-8");
            response.setContentType("text/plain; charset=UTF-8");
            response.getWriter().println("未登录");
            return;
        }
        Long uid = AuthHelper.currentUserId(request);
        try {
            collabProjectService.requireMember(projectId, uid);
        } catch (Exception e) {
            response.setStatus(javax.servlet.http.HttpServletResponse.SC_FORBIDDEN);
            response.setCharacterEncoding("UTF-8");
            response.setContentType("text/plain; charset=UTF-8");
            response.getWriter().println("无权限");
            return;
        }

        String html = collabProjectReportHtmlBuilder.build(projectId, scene);
        String ts = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmm"));
        String filename = "项目协作报告_" + projectId + "_" + ts + ".html";

        response.setCharacterEncoding("UTF-8");
        response.setContentType("text/html; charset=UTF-8");
        response.setHeader("Content-Disposition", buildDownloadHeader(filename));
        java.io.PrintWriter w = response.getWriter();
        w.print(html);
        w.flush();
    }

    private static String buildDownloadHeader(String filename) {
        String safe = filename == null ? "download" : filename.replace("\"", "");
        String encoded;
        try {
            encoded = java.net.URLEncoder.encode(safe, java.nio.charset.StandardCharsets.UTF_8.name()).replace("+", "%20");
        } catch (java.io.UnsupportedEncodingException e) {
            throw new IllegalStateException("UTF-8 not supported", e);
        }
        return "attachment; filename=\"" + safe + "\"; filename*=UTF-8''" + encoded;
    }

    @PostMapping("/member/add")
    public Result<String> addMember(@RequestBody AddMemberBody body, HttpServletRequest request) {
        Result<String> auth = AuthHelper.requireLogin(request);
        if (auth != null) return auth;
        Long uid = AuthHelper.currentUserId(request);
        if (body == null || body.getProjectId() == null) return Result.error("projectId 不能为空");
        Long targetUserId = body.getUserId();
        if (targetUserId == null && body.getGithubUsername() != null) {
            targetUserId = collabProjectService.findUserIdByGithubUsername(body.getGithubUsername());
        }
        if (targetUserId == null) return Result.error("成员不存在（userId/githubUsername 无效）");
        try {
            collabProjectService.addMember(body.getProjectId(), uid, targetUserId, body.getRole());
        } catch (Exception e) {
            return Result.error(e.getMessage() == null ? "添加失败" : e.getMessage());
        }
        collabActivityService.add(body.getProjectId(), uid, "member_add", "user", targetUserId, "添加成员", null);
        collabAuditService.record(request, uid, "project_member_add", "project", body.getProjectId(), "targetUserId=" + targetUserId);
        return Result.success("ok");
    }

    @PostMapping("/member/role")
    public Result<String> updateRole(@RequestBody UpdateRoleBody body, HttpServletRequest request) {
        Result<String> auth = AuthHelper.requireLogin(request);
        if (auth != null) return auth;
        Long uid = AuthHelper.currentUserId(request);
        if (body == null || body.getProjectId() == null || body.getUserId() == null) return Result.error("缺少参数");
        try {
            collabProjectService.updateMemberRole(body.getProjectId(), uid, body.getUserId(), body.getRole());
        } catch (Exception e) {
            return Result.error(e.getMessage() == null ? "修改失败" : e.getMessage());
        }
        collabActivityService.add(body.getProjectId(), uid, "member_role_update", "user", body.getUserId(), "修改成员角色", null);
        collabAuditService.record(request, uid, "project_member_role_update", "project", body.getProjectId(),
                "targetUserId=" + body.getUserId() + ",role=" + body.getRole());
        return Result.success("ok");
    }

    @Data
    public static class CreateBody {
        private String name;
        private String description;
        private String visibility;
    }

    @Data
    public static class AddMemberBody {
        private Long projectId;
        private Long userId;
        private String githubUsername;
        private String role;
    }

    @Data
    public static class UpdateRoleBody {
        private Long projectId;
        private Long userId;
        private String role;
    }
}

