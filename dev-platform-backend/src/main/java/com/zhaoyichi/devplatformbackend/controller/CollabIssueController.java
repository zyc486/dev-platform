package com.zhaoyichi.devplatformbackend.controller;

import com.zhaoyichi.devplatformbackend.common.Result;
import com.zhaoyichi.devplatformbackend.entity.Issue;
import com.zhaoyichi.devplatformbackend.entity.IssueAttachment;
import com.zhaoyichi.devplatformbackend.entity.IssueComment;
import com.zhaoyichi.devplatformbackend.entity.IssueReview;
import com.zhaoyichi.devplatformbackend.service.collab.CollabAttachmentService;
import com.zhaoyichi.devplatformbackend.service.collab.CollabAuditService;
import com.zhaoyichi.devplatformbackend.service.collab.CollabIssueService;
import com.zhaoyichi.devplatformbackend.utils.AuthHelper;
import lombok.Data;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/collab/issue")
@CrossOrigin(origins = "*")
public class CollabIssueController {

    private final CollabIssueService collabIssueService;
    private final CollabAttachmentService collabAttachmentService;
    private final CollabAuditService collabAuditService;

    public CollabIssueController(CollabIssueService collabIssueService,
                                 CollabAttachmentService collabAttachmentService,
                                 CollabAuditService collabAuditService) {
        this.collabIssueService = collabIssueService;
        this.collabAttachmentService = collabAttachmentService;
        this.collabAuditService = collabAuditService;
    }

    @PostMapping("/create")
    public Result<Issue> create(@RequestBody CreateBody body, HttpServletRequest request) {
        Result<Issue> auth = AuthHelper.requireLogin(request);
        if (auth != null) return auth;
        Long uid = AuthHelper.currentUserId(request);
        if (body == null || body.getProjectId() == null) return Result.error("projectId 不能为空");
        if (body.getTitle() == null || body.getTitle().trim().isEmpty()) return Result.error("title 不能为空");
        try {
            Issue i = collabIssueService.create(body.getProjectId(), uid, body.getTitle().trim(), body.getDescription(),
                    body.getPriority(), body.getLabelsJson(), body.getAssigneeUserId());
            collabAuditService.record(request, uid, "issue_create", "issue", i.getId(), i.getTitle());
            return Result.success(i);
        } catch (Exception e) {
            return Result.error(e.getMessage() == null ? "创建失败" : e.getMessage());
        }
    }

    @GetMapping("/list")
    public Result<List<Issue>> list(@RequestParam Long projectId, HttpServletRequest request) {
        Result<List<Issue>> auth = AuthHelper.requireLogin(request);
        if (auth != null) return auth;
        Long uid = AuthHelper.currentUserId(request);
        try {
            return Result.success(collabIssueService.listByProject(projectId, uid));
        } catch (Exception e) {
            return Result.error(e.getMessage() == null ? "查询失败" : e.getMessage());
        }
    }

    @GetMapping("/detail")
    public Result<Issue> detail(@RequestParam Long issueId, HttpServletRequest request) {
        Result<Issue> auth = AuthHelper.requireLogin(request);
        if (auth != null) return auth;
        Long uid = AuthHelper.currentUserId(request);
        try {
            Issue i = collabIssueService.get(issueId, uid);
            return Result.success(i);
        } catch (Exception e) {
            return Result.error(e.getMessage() == null ? "查询失败" : e.getMessage());
        }
    }

    @PostMapping("/update")
    public Result<Issue> update(@RequestBody UpdateBody body, HttpServletRequest request) {
        Result<Issue> auth = AuthHelper.requireLogin(request);
        if (auth != null) return auth;
        Long uid = AuthHelper.currentUserId(request);
        if (body == null || body.getIssueId() == null) return Result.error("issueId 不能为空");
        try {
            Issue i = collabIssueService.updateFields(body.getIssueId(), uid, body.getStatus(), body.getAssigneeUserId(),
                    body.getLabelsJson(), body.getPriority());
            collabAuditService.record(request, uid, "issue_update", "issue", i.getId(), "status=" + body.getStatus());
            return Result.success(i);
        } catch (Exception e) {
            return Result.error(e.getMessage() == null ? "更新失败" : e.getMessage());
        }
    }

    @PostMapping("/delete")
    public Result<String> delete(@RequestBody DeleteBody body, HttpServletRequest request) {
        Result<String> auth = AuthHelper.requireLogin(request);
        if (auth != null) return auth;
        Long uid = AuthHelper.currentUserId(request);
        if (body == null || body.getIssueId() == null) return Result.error("issueId 不能为空");
        try {
            collabIssueService.delete(body.getIssueId(), uid);
            collabAuditService.record(request, uid, "issue_delete", "issue", body.getIssueId(), null);
            return Result.success("ok");
        } catch (Exception e) {
            return Result.error(e.getMessage() == null ? "删除失败" : e.getMessage());
        }
    }

    @PostMapping("/comment/add")
    public Result<IssueComment> addComment(@RequestBody AddCommentBody body, HttpServletRequest request) {
        Result<IssueComment> auth = AuthHelper.requireLogin(request);
        if (auth != null) return auth;
        Long uid = AuthHelper.currentUserId(request);
        if (body == null || body.getIssueId() == null) return Result.error("issueId 不能为空");
        if (body.getContent() == null || body.getContent().trim().isEmpty()) return Result.error("content 不能为空");
        try {
            IssueComment c = collabIssueService.addComment(body.getIssueId(), uid, body.getContent());
            collabAuditService.record(request, uid, "issue_comment_add", "comment", c.getId(), null);
            return Result.success(c);
        } catch (Exception e) {
            return Result.error(e.getMessage() == null ? "评论失败" : e.getMessage());
        }
    }

    @GetMapping("/comment/list")
    public Result<List<IssueComment>> listComments(@RequestParam Long issueId, HttpServletRequest request) {
        Result<List<IssueComment>> auth = AuthHelper.requireLogin(request);
        if (auth != null) return auth;
        Long uid = AuthHelper.currentUserId(request);
        try {
            return Result.success(collabIssueService.listComments(issueId, uid));
        } catch (Exception e) {
            return Result.error(e.getMessage() == null ? "查询失败" : e.getMessage());
        }
    }

    @PostMapping("/attachment/upload")
    public Result<IssueAttachment> upload(@RequestParam Long issueId,
                                         @RequestParam("file") MultipartFile file,
                                         HttpServletRequest request) {
        Result<IssueAttachment> auth = AuthHelper.requireLogin(request);
        if (auth != null) return auth;
        Long uid = AuthHelper.currentUserId(request);
        try {
            IssueAttachment a = collabAttachmentService.upload(issueId, uid, file);
            collabAuditService.record(request, uid, "issue_attachment_upload", "attachment", a.getId(), a.getOriginalName());
            return Result.success(a);
        } catch (Exception e) {
            return Result.error(e.getMessage() == null ? "上传失败" : e.getMessage());
        }
    }

    @GetMapping("/attachment/list")
    public Result<List<IssueAttachment>> listAttachments(@RequestParam Long issueId, HttpServletRequest request) {
        Result<List<IssueAttachment>> auth = AuthHelper.requireLogin(request);
        if (auth != null) return auth;
        Long uid = AuthHelper.currentUserId(request);
        try {
            return Result.success(collabAttachmentService.listByIssue(issueId, uid));
        } catch (Exception e) {
            return Result.error(e.getMessage() == null ? "查询失败" : e.getMessage());
        }
    }

    @PostMapping("/review/submit")
    public Result<IssueReview> submitReview(@RequestBody ReviewBody body, HttpServletRequest request) {
        Result<IssueReview> auth = AuthHelper.requireLogin(request);
        if (auth != null) return auth;
        Long uid = AuthHelper.currentUserId(request);
        if (body == null || body.getIssueId() == null) return Result.error("issueId 不能为空");
        try {
            IssueReview r = collabIssueService.submitReview(body.getIssueId(), uid, body.getRating(), body.getComment());
            collabAuditService.record(request, uid, "issue_review_submit", "review", r.getId(), "rating=" + body.getRating());
            return Result.success(r);
        } catch (Exception e) {
            return Result.error(e.getMessage() == null ? "提交失败" : e.getMessage());
        }
    }

    @GetMapping("/review/summary")
    public Result<Map<String, Object>> reviewSummary(@RequestParam Long issueId, HttpServletRequest request) {
        Result<Map<String, Object>> auth = AuthHelper.requireLogin(request);
        if (auth != null) return auth;
        Long uid = AuthHelper.currentUserId(request);
        try {
            return Result.success(collabIssueService.reviewSummary(issueId, uid));
        } catch (Exception e) {
            return Result.error(e.getMessage() == null ? "查询失败" : e.getMessage());
        }
    }

    @Data
    public static class CreateBody {
        private Long projectId;
        private String title;
        private String description;
        private String priority;
        private String labelsJson;
        private Long assigneeUserId;
    }

    @Data
    public static class UpdateBody {
        private Long issueId;
        private String status;
        private String priority;
        private String labelsJson;
        private Long assigneeUserId;
    }

    @Data
    public static class DeleteBody {
        private Long issueId;
    }

    @Data
    public static class AddCommentBody {
        private Long issueId;
        private String content;
    }

    @Data
    public static class ReviewBody {
        private Long issueId;
        private Integer rating;
        private String comment;
    }
}

