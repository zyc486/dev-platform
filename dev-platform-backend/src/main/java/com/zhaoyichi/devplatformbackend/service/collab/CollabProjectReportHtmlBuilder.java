package com.zhaoyichi.devplatformbackend.service.collab;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.zhaoyichi.devplatformbackend.entity.*;
import com.zhaoyichi.devplatformbackend.mapper.*;
import com.zhaoyichi.devplatformbackend.service.CreditScoreService;
import com.zhaoyichi.devplatformbackend.service.ai.AiProfileService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 项目协作报告（HTML）：用于答辩演示与打印。
 * <p>核心目标：不依赖外部服务，也能输出“协作闭环证据”。</p>
 */
@Service
public class CollabProjectReportHtmlBuilder {

    private final ProjectMapper projectMapper;
    private final ProjectMemberMapper projectMemberMapper;
    private final IssueMapper issueMapper;
    private final IssueReviewMapper issueReviewMapper;
    private final UserMapper userMapper;
    private final CreditScoreMapper creditScoreMapper;
    private final AiProfileService aiProfileService;
    private final CreditScoreService creditScoreService;

    public CollabProjectReportHtmlBuilder(ProjectMapper projectMapper,
                                         ProjectMemberMapper projectMemberMapper,
                                         IssueMapper issueMapper,
                                         IssueReviewMapper issueReviewMapper,
                                         UserMapper userMapper,
                                         CreditScoreMapper creditScoreMapper,
                                         AiProfileService aiProfileService,
                                         CreditScoreService creditScoreService) {
        this.projectMapper = projectMapper;
        this.projectMemberMapper = projectMemberMapper;
        this.issueMapper = issueMapper;
        this.issueReviewMapper = issueReviewMapper;
        this.userMapper = userMapper;
        this.creditScoreMapper = creditScoreMapper;
        this.aiProfileService = aiProfileService;
        this.creditScoreService = creditScoreService;
    }

    public String build(Long projectId, String scene) {
        Project p = projectMapper.selectById(projectId);
        if (p == null) {
            return simpleError("项目不存在");
        }

        List<ProjectMember> members = projectMemberMapper.selectList(new QueryWrapper<ProjectMember>()
                .eq("project_id", projectId));
        List<Long> userIds = members.stream().map(ProjectMember::getUserId).filter(Objects::nonNull).distinct().collect(Collectors.toList());
        List<User> users = userIds.isEmpty() ? Collections.emptyList()
                : userMapper.selectList(new QueryWrapper<User>().in("id", userIds));
        Map<Long, User> byUid = users.stream().collect(Collectors.toMap(User::getId, it -> it, (a, b) -> a));

        List<Issue> issues = issueMapper.selectList(new QueryWrapper<Issue>().eq("project_id", projectId).orderByDesc("updated_at"));
        int total = issues == null ? 0 : issues.size();
        int done = 0;
        if (issues != null) {
            for (Issue i : issues) if ("done".equalsIgnoreCase(i.getStatus())) done++;
        }
        int doing = Math.max(0, total - done);
        double completion = total == 0 ? 0 : (done * 100.0 / total);

        // 互评统计
        List<IssueReview> allReviews = issues == null || issues.isEmpty()
                ? Collections.emptyList()
                : issueReviewMapper.selectList(new QueryWrapper<IssueReview>().eq("project_id", projectId));
        int reviewCount = allReviews.size();
        Double avgRating = null;
        if (reviewCount > 0) {
            int sum = 0;
            for (IssueReview r : allReviews) sum += (r.getRating() == null ? 0 : r.getRating());
            avgRating = sum * 1.0 / reviewCount;
        }

        // 信用分快照（不触发外部抓取）
        String normalizedScene = creditScoreService.normalizeScene(scene == null ? "综合" : scene);
        List<String> ghs = users.stream().map(User::getGithubUsername).filter(s -> s != null && !s.trim().isEmpty()).collect(Collectors.toList());
        Map<String, CreditScore> scoreByGh = new HashMap<>();
        if (!ghs.isEmpty()) {
            List<CreditScore> scores = creditScoreMapper.selectList(new QueryWrapper<CreditScore>().in("github_username", ghs).eq("scene", normalizedScene));
            for (CreditScore s : scores) {
                if (s != null && s.getGithubUsername() != null && !scoreByGh.containsKey(s.getGithubUsername())) {
                    scoreByGh.put(s.getGithubUsername(), s);
                }
            }
        }

        StringBuilder html = new StringBuilder(40_000);
        html.append("<!DOCTYPE html><html lang=\"zh-CN\"><head><meta charset=\"UTF-8\"/>");
        html.append("<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\"/>");
        html.append("<title>项目协作报告</title>");
        html.append("<style>");
        html.append(":root{--bg:#f6f8fa;--card:#fff;--muted:#64748b;--text:#0f172a;--border:#e2e8f0;--head2:#1e40af;}");
        html.append("body{font-family:ui-sans-serif,Segoe UI,Microsoft YaHei,system-ui,-apple-system,sans-serif;margin:0;line-height:1.65;color:var(--text);background:var(--bg);}");
        html.append(".wrap{max-width:1100px;margin:20px auto;padding:0 16px;}");
        html.append(".page{background:var(--card);border:1px solid var(--border);border-radius:14px;box-shadow:0 6px 24px rgba(15,23,42,.06);padding:20px 22px;}");
        html.append("h1{font-size:22px;margin:0 0 10px 0;border-bottom:1px solid var(--border);padding-bottom:12px;}");
        html.append("h2{font-size:18px;margin:22px 0 10px 0;color:var(--head2);}");
        html.append(".meta{color:#475569;font-size:13px;margin:12px 0;}");
        html.append(".box{background:#f8fafc;border:1px solid var(--border);border-radius:12px;padding:12px 14px;margin:12px 0;font-size:13px;}");
        html.append(".kvs{display:flex;flex-wrap:wrap;gap:10px;margin-top:6px;color:#475569;font-size:12px;}");
        html.append("table{border-collapse:separate;border-spacing:0;width:100%;margin:12px 0;font-size:13px;border:1px solid var(--border);border-radius:12px;overflow:hidden;}");
        html.append("th,td{border-bottom:1px solid var(--border);padding:10px 10px;vertical-align:top;}");
        html.append("thead th{background:#eef5ff;color:#0f172a;}");
        html.append("tbody tr:nth-child(2n){background:#fafcff;}");
        html.append(".muted{color:var(--muted);} .num{white-space:nowrap;font-variant-numeric:tabular-nums;}");
        html.append(".tag{display:inline-flex;align-items:center;padding:2px 8px;border-radius:999px;border:1px solid #cbd5e1;background:#fff;font-size:12px;margin-right:6px;}");
        html.append("</style></head><body><div class=\"wrap\"><div class=\"page\">");

        html.append("<h1>项目协作报告</h1>");
        html.append("<div class=\"meta\">生成时间：").append(esc(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))));
        html.append(" &nbsp;|&nbsp; 项目：<strong>").append(esc(p.getName())).append("</strong>");
        html.append(" &nbsp;|&nbsp; 场景：<strong>").append(esc(normalizedScene)).append("</strong></div>");

        html.append("<div class=\"box\"><strong>协作概览</strong>");
        html.append("<div class=\"kvs\">");
        html.append("<div><span class=\"muted\">任务总数</span> = <span class=\"num\">").append(total).append("</span></div>");
        html.append("<div><span class=\"muted\">已完成</span> = <span class=\"num\">").append(done).append("</span></div>");
        html.append("<div><span class=\"muted\">进行中/未完成</span> = <span class=\"num\">").append(doing).append("</span></div>");
        html.append("<div><span class=\"muted\">完成率</span> = <span class=\"num\">").append(String.format("%.1f", completion)).append("%</span></div>");
        html.append("<div><span class=\"muted\">验收评分数</span> = <span class=\"num\">").append(reviewCount).append("</span></div>");
        html.append("<div><span class=\"muted\">平均评分</span> = <span class=\"num\">").append(avgRating == null ? "—" : String.format("%.2f", avgRating)).append("</span></div>");
        html.append("</div></div>");

        html.append("<h2>任务列表</h2>");
        html.append("<table><thead><tr><th>ID</th><th>标题</th><th>状态</th><th>优先级</th><th>负责人</th><th>更新时间</th></tr></thead><tbody>");
        if (issues != null) {
            for (Issue i : issues) {
                String assignee = "—";
                if (i.getAssigneeUserId() != null) {
                    User au = byUid.get(i.getAssigneeUserId());
                    assignee = au == null ? String.valueOf(i.getAssigneeUserId()) : firstNonBlank(au.getGithubUsername(), au.getUsername(), String.valueOf(au.getId()));
                }
                html.append("<tr>");
                html.append("<td class=\"num\">").append(i.getId() == null ? "—" : i.getId()).append("</td>");
                html.append("<td>").append(esc(i.getTitle())).append("</td>");
                html.append("<td><span class=\"tag\">").append(esc(i.getStatus())).append("</span></td>");
                html.append("<td>").append(esc(i.getPriority())).append("</td>");
                html.append("<td>").append(esc(assignee)).append("</td>");
                html.append("<td class=\"muted\">").append(esc(i.getUpdatedAt() == null ? "" : i.getUpdatedAt().toString())).append("</td>");
                html.append("</tr>");
            }
        }
        html.append("</tbody></table>");

        html.append("<h2>团队画像（信用分 + AI 画像摘要）</h2>");
        html.append("<table><thead><tr><th>成员</th><th>角色</th><th>信用</th><th>AI 摘要</th></tr></thead><tbody>");
        for (ProjectMember pm : members) {
            User u = byUid.get(pm.getUserId());
            String gh = u == null ? null : u.getGithubUsername();
            String who = u == null ? ("userId=" + pm.getUserId()) : firstNonBlank(gh, u.getUsername(), "userId=" + pm.getUserId());
            CreditScore cs = (gh == null) ? null : scoreByGh.get(gh);

            String creditText = cs == null ? "—" : (String.valueOf(cs.getTotalScore()) + " / " + (cs.getLevel() == null ? "—" : cs.getLevel()));
            String aiSummary = "—";
            if (gh != null && !gh.trim().isEmpty()) {
                try {
                    Map<String, Object> ai = aiProfileService.getProfile(gh, normalizedScene);
                    Object summary = ai == null ? null : ai.get("summary");
                    Object status = ai == null ? null : ai.get("status");
                    aiSummary = firstNonBlank(summary == null ? null : String.valueOf(summary),
                            status == null ? null : String.valueOf(status),
                            "—");
                } catch (Exception ignore) {}
            }

            html.append("<tr>");
            html.append("<td>").append(esc(who)).append("</td>");
            html.append("<td>").append(esc(pm.getRole())).append("</td>");
            html.append("<td>").append(esc(creditText)).append("</td>");
            html.append("<td class=\"muted\">").append(esc(aiSummary)).append("</td>");
            html.append("</tr>");
        }
        html.append("</tbody></table>");

        html.append("<p class=\"meta\" style=\"margin-top:40px;\">— 报告由系统自动生成 —</p>");
        html.append("</div></div></body></html>");
        return html.toString();
    }

    private static String simpleError(String msg) {
        return "<!DOCTYPE html><html><head><meta charset=\"UTF-8\"/></head><body><h3>" + esc(msg) + "</h3></body></html>";
    }

    private static String firstNonBlank(String... cands) {
        if (cands == null) return null;
        for (String s : cands) {
            if (s != null && !s.trim().isEmpty()) return s.trim();
        }
        return null;
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }
}

