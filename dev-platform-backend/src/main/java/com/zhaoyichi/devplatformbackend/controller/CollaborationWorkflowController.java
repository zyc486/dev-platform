package com.zhaoyichi.devplatformbackend.controller;

import com.zhaoyichi.devplatformbackend.common.Result;
import com.zhaoyichi.devplatformbackend.service.CollaborationReviewService;
import com.zhaoyichi.devplatformbackend.utils.AuthHelper;
import lombok.Data;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;

/**
 * 协作生命周期 REST（与既有 {@code /api/collab} 并存，路径符合论文/接口文档约定）。
 */
@RestController
@RequestMapping("/api/collaboration")
@CrossOrigin(origins = "*")
public class CollaborationWorkflowController {

    private final CollaborationReviewService collaborationReviewService;

    public CollaborationWorkflowController(CollaborationReviewService collaborationReviewService) {
        this.collaborationReviewService = collaborationReviewService;
    }

    @PostMapping("/{collaborationId}/complete")
    public Result<String> complete(@PathVariable Integer collaborationId, HttpServletRequest request) {
        Result<String> auth = AuthHelper.requireLogin(request);
        if (auth != null) {
            return auth;
        }
        return collaborationReviewService.completeCollaboration(AuthHelper.currentUserId(request), collaborationId);
    }

    @PostMapping("/review")
    public Result<String> review(@RequestBody CollaborationReviewBody body, HttpServletRequest request) {
        Result<String> auth = AuthHelper.requireLogin(request);
        if (auth != null) {
            return auth;
        }
        if (body == null || body.getCollaborationId() == null) {
            return Result.error("缺少 collaborationId");
        }
        return collaborationReviewService.submitReview(
                AuthHelper.currentUserId(request),
                body.getCollaborationId(),
                body.getToUserId(),
                body.getRating(),
                body.getComment()
        );
    }

    @Data
    public static class CollaborationReviewBody {
        private Integer collaborationId;
        private Long toUserId;
        private Integer rating;
        private String comment;
    }
}
