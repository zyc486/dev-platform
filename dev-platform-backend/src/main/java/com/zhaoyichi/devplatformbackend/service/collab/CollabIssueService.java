package com.zhaoyichi.devplatformbackend.service.collab;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.zhaoyichi.devplatformbackend.entity.Issue;
import com.zhaoyichi.devplatformbackend.entity.IssueComment;
import com.zhaoyichi.devplatformbackend.entity.IssueReview;
import com.zhaoyichi.devplatformbackend.mapper.IssueCommentMapper;
import com.zhaoyichi.devplatformbackend.mapper.IssueMapper;
import com.zhaoyichi.devplatformbackend.mapper.IssueReviewMapper;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class CollabIssueService {

    private final IssueMapper issueMapper;
    private final IssueCommentMapper issueCommentMapper;
    private final IssueReviewMapper issueReviewMapper;
    private final CollabProjectService collabProjectService;
    private final CollabActivityService collabActivityService;

    public CollabIssueService(IssueMapper issueMapper,
                             IssueCommentMapper issueCommentMapper,
                             IssueReviewMapper issueReviewMapper,
                             CollabProjectService collabProjectService,
                             CollabActivityService collabActivityService) {
        this.issueMapper = issueMapper;
        this.issueCommentMapper = issueCommentMapper;
        this.issueReviewMapper = issueReviewMapper;
        this.collabProjectService = collabProjectService;
        this.collabActivityService = collabActivityService;
    }

    public Issue create(Long projectId, Long userId, String title, String description, String priority, String labelsJson, Long assigneeUserId) {
        collabProjectService.requireMember(projectId, userId);
        Issue i = new Issue();
        i.setProjectId(projectId);
        i.setTitle(title);
        i.setDescription(description);
        i.setStatus("todo");
        i.setPriority(priority == null || priority.trim().isEmpty() ? "medium" : priority.trim().toLowerCase());
        i.setLabelsJson(labelsJson);
        i.setAssigneeUserId(assigneeUserId);
        i.setCreatedBy(userId);
        issueMapper.insert(i);
        collabActivityService.add(projectId, userId, "issue_create", "issue", i.getId(), "创建任务：" + title, null);
        return i;
    }

    public List<Issue> listByProject(Long projectId, Long userId) {
        collabProjectService.requireMember(projectId, userId);
        return issueMapper.selectList(new QueryWrapper<Issue>().eq("project_id", projectId).orderByDesc("updated_at"));
    }

    public Issue get(Long issueId, Long userId) {
        Issue i = issueMapper.selectById(issueId);
        if (i == null) return null;
        collabProjectService.requireMember(i.getProjectId(), userId);
        return i;
    }

    public Issue updateFields(Long issueId, Long userId, String status, Long assigneeUserId, String labelsJson, String priority) {
        Issue i = issueMapper.selectById(issueId);
        if (i == null) throw new IllegalStateException("任务不存在");
        collabProjectService.requireMember(i.getProjectId(), userId);
        boolean changed = false;
        if (status != null && !status.trim().isEmpty()) {
            String s = status.trim().toLowerCase();
            if (!s.equals(i.getStatus())) {
                i.setStatus(s);
                changed = true;
            }
        }
        if (assigneeUserId != null && (i.getAssigneeUserId() == null || !assigneeUserId.equals(i.getAssigneeUserId()))) {
            i.setAssigneeUserId(assigneeUserId);
            changed = true;
        }
        if (labelsJson != null) {
            i.setLabelsJson(labelsJson);
            changed = true;
        }
        if (priority != null && !priority.trim().isEmpty()) {
            i.setPriority(priority.trim().toLowerCase());
            changed = true;
        }
        if (changed) {
            issueMapper.updateById(i);
            collabActivityService.add(i.getProjectId(), userId, "issue_update", "issue", i.getId(), "更新任务：" + safeTitle(i.getTitle()), null);
        }
        return i;
    }

    public void delete(Long issueId, Long userId) {
        Issue i = issueMapper.selectById(issueId);
        if (i == null) return;
        String role = collabProjectService.requireMember(i.getProjectId(), userId).getRole();
        if (!collabProjectService.isManagerRole(role) && !userId.equals(i.getCreatedBy())) {
            throw new IllegalStateException("无权限删除任务");
        }
        issueMapper.deleteById(issueId);
        collabActivityService.add(i.getProjectId(), userId, "issue_delete", "issue", issueId, "删除任务：" + safeTitle(i.getTitle()), null);
    }

    public IssueComment addComment(Long issueId, Long userId, String content) {
        Issue i = issueMapper.selectById(issueId);
        if (i == null) throw new IllegalStateException("任务不存在");
        collabProjectService.requireMember(i.getProjectId(), userId);
        IssueComment c = new IssueComment();
        c.setIssueId(issueId);
        c.setProjectId(i.getProjectId());
        c.setUserId(userId);
        c.setContent(content);
        issueCommentMapper.insert(c);
        collabActivityService.add(i.getProjectId(), userId, "issue_comment", "comment", c.getId(), "评论任务：" + safeTitle(i.getTitle()), null);
        return c;
    }

    public List<IssueComment> listComments(Long issueId, Long userId) {
        Issue i = issueMapper.selectById(issueId);
        if (i == null) throw new IllegalStateException("任务不存在");
        collabProjectService.requireMember(i.getProjectId(), userId);
        return issueCommentMapper.selectList(new QueryWrapper<IssueComment>().eq("issue_id", issueId).orderByAsc("created_at"));
    }

    public IssueReview submitReview(Long issueId, Long userId, Integer rating, String comment) {
        if (rating == null || rating < 1 || rating > 5) throw new IllegalStateException("rating 必须为 1-5");
        Issue i = issueMapper.selectById(issueId);
        if (i == null) throw new IllegalStateException("任务不存在");
        collabProjectService.requireMember(i.getProjectId(), userId);
        IssueReview existing = issueReviewMapper.selectOne(new QueryWrapper<IssueReview>()
                .eq("issue_id", issueId)
                .eq("reviewer_user_id", userId)
                .last("limit 1"));
        if (existing != null) {
            existing.setRating(rating);
            existing.setComment(comment);
            issueReviewMapper.updateById(existing);
            collabActivityService.add(i.getProjectId(), userId, "issue_review_update", "review", existing.getId(), "更新验收评分：" + safeTitle(i.getTitle()), null);
            return existing;
        }
        IssueReview r = new IssueReview();
        r.setIssueId(issueId);
        r.setProjectId(i.getProjectId());
        r.setReviewerUserId(userId);
        r.setRating(rating);
        r.setComment(comment);
        issueReviewMapper.insert(r);
        collabActivityService.add(i.getProjectId(), userId, "issue_review", "review", r.getId(), "提交验收评分：" + safeTitle(i.getTitle()), null);
        return r;
    }

    public Map<String, Object> reviewSummary(Long issueId, Long userId) {
        Issue i = issueMapper.selectById(issueId);
        if (i == null) throw new IllegalStateException("任务不存在");
        collabProjectService.requireMember(i.getProjectId(), userId);
        List<IssueReview> rs = issueReviewMapper.selectList(new QueryWrapper<IssueReview>().eq("issue_id", issueId));
        int count = rs == null ? 0 : rs.size();
        double avg = 0;
        if (rs != null && !rs.isEmpty()) {
            int sum = 0;
            for (IssueReview r : rs) sum += (r.getRating() == null ? 0 : r.getRating());
            avg = sum * 1.0 / count;
        }
        Map<String, Object> m = new HashMap<>();
        m.put("issueId", issueId);
        m.put("receivedCount", count);
        m.put("avgRating", count == 0 ? null : avg);
        return m;
    }

    private static String safeTitle(String t) {
        if (t == null) return "";
        String s = t.trim();
        if (s.length() <= 30) return s;
        return s.substring(0, 29) + "…";
    }
}

