package com.zhaoyichi.devplatformbackend.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.zhaoyichi.devplatformbackend.common.Result;
import com.zhaoyichi.devplatformbackend.entity.Collaboration;
import com.zhaoyichi.devplatformbackend.entity.CollaborationReview;
import com.zhaoyichi.devplatformbackend.entity.User;
import com.zhaoyichi.devplatformbackend.mapper.CollaborationMapper;
import com.zhaoyichi.devplatformbackend.mapper.CollaborationReviewMapper;
import com.zhaoyichi.devplatformbackend.service.CollaborationReviewService;
import com.zhaoyichi.devplatformbackend.service.CollaborationService;
import com.zhaoyichi.devplatformbackend.service.UserService;
import com.zhaoyichi.devplatformbackend.utils.AuthHelper;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/collab")
public class CollaborationController {

    private final CollaborationService collaborationService;
    private final UserService userService;
    private final CollaborationReviewMapper collaborationReviewMapper;
    private final CollaborationMapper collaborationMapper;
    private final CollaborationReviewService collaborationReviewService;

    public CollaborationController(CollaborationService collaborationService,
                                   UserService userService,
                                   CollaborationReviewMapper collaborationReviewMapper,
                                   CollaborationMapper collaborationMapper,
                                   CollaborationReviewService collaborationReviewService) {
        this.collaborationService = collaborationService;
        this.userService = userService;
        this.collaborationReviewMapper = collaborationReviewMapper;
        this.collaborationMapper = collaborationMapper;
        this.collaborationReviewService = collaborationReviewService;
    }

    @PostMapping("/publish")
    public Result<String> publishCollab(@RequestBody Collaboration collaboration, HttpServletRequest request) {
        User creator = userService.findById(AuthHelper.currentUserId(request));
        return collaborationService.publish(collaboration, creator);
    }

    @GetMapping("/list")
    public Result<List<Map<String, Object>>> getCollabList() {
        return Result.success(collaborationService.openProjects());
    }

    @PostMapping("/apply")
    public Result<String> applyCollab(@RequestParam Integer projectId, HttpServletRequest request) {
        User applicant = userService.findById(AuthHelper.currentUserId(request));
        return collaborationService.apply(projectId, applicant);
    }

    @GetMapping("/myPublish")
    public Result<List<Map<String, Object>>> myPublish(HttpServletRequest request) {
        return Result.success(collaborationService.myPublish(AuthHelper.currentUserId(request)));
    }

    @GetMapping("/myApply")
    public Result<List<Map<String, Object>>> myApply(HttpServletRequest request) {
        return Result.success(collaborationService.myApply(AuthHelper.currentUserId(request)));
    }

    @GetMapping("/applyList")
    public Result<List<Map<String, Object>>> applyList(@RequestParam(required = false) Integer projectId, HttpServletRequest request) {
        return Result.success(collaborationService.applyList(AuthHelper.currentUserId(request), projectId));
    }

    @PostMapping("/apply/review")
    public Result<String> review(@RequestBody ReviewRequest reviewRequest, HttpServletRequest request) {
        return collaborationService.review(
                AuthHelper.currentUserId(request),
                reviewRequest.getApplyId(),
                reviewRequest.getAction(),
                reviewRequest.getReason()
        );
    }

    @PostMapping("/close")
    public Result<String> closeProject(@RequestParam Integer projectId, HttpServletRequest request) {
        return collaborationService.updateProjectStatus(AuthHelper.currentUserId(request), projectId, "cancelled");
    }

    @PostMapping("/finish")
    public Result<String> finishProject(@RequestParam Integer projectId, HttpServletRequest request) {
        Result<String> auth = AuthHelper.requireLogin(request);
        if (auth != null) {
            return auth;
        }
        return collaborationReviewService.completeCollaboration(AuthHelper.currentUserId(request), projectId);
    }

    /**
     * 兼容旧路径：与 {@code POST /api/collaboration/review} 等价，评分 1～5 星。
     */
    @PostMapping("/rate")
    public Result<String> rate(@RequestBody RateRequest req, HttpServletRequest request) {
        Result<String> auth = AuthHelper.requireLogin(request);
        if (auth != null) {
            return auth;
        }
        if (req.getProjectId() == null || req.getToUserId() == null || req.getScore() == null) {
            return Result.error("参数不完整");
        }
        if (req.getScore() < 1 || req.getScore() > 5) {
            return Result.error("评分范围为 1～5 星");
        }
        return collaborationReviewService.submitReview(
                AuthHelper.currentUserId(request),
                req.getProjectId(),
                req.getToUserId(),
                req.getScore(),
                req.getComment()
        );
    }

    @GetMapping("/ratings/{projectId}")
    public Result<List<CollaborationReview>> getRatings(@PathVariable Integer projectId) {
        QueryWrapper<CollaborationReview> wrapper = new QueryWrapper<>();
        wrapper.eq("collaboration_id", projectId).orderByDesc("create_time");
        return Result.success(collaborationReviewMapper.selectList(wrapper));
    }

    @GetMapping("/match")
    public Result<List<Map<String, Object>>> matchDevelopers(
            @RequestParam Integer projectId,
            @RequestParam(defaultValue = "5") int limit) {
        Collaboration project = collaborationMapper.selectById(projectId);
        if (project == null) {
            return Result.error("项目不存在");
        }
        return Result.success(collaborationService.matchDevelopers(project, limit));
    }

    public static class RateRequest {
        private Integer projectId;
        private Long toUserId;
        private Integer score;
        private String comment;

        public Integer getProjectId() {
            return projectId;
        }

        public void setProjectId(Integer projectId) {
            this.projectId = projectId;
        }

        public Long getToUserId() {
            return toUserId;
        }

        public void setToUserId(Long toUserId) {
            this.toUserId = toUserId;
        }

        public Integer getScore() {
            return score;
        }

        public void setScore(Integer score) {
            this.score = score;
        }

        public String getComment() {
            return comment;
        }

        public void setComment(String comment) {
            this.comment = comment;
        }
    }

    public static class ReviewRequest {
        private Integer applyId;
        private String action;
        private String reason;

        public Integer getApplyId() {
            return applyId;
        }

        public void setApplyId(Integer applyId) {
            this.applyId = applyId;
        }

        public String getAction() {
            return action;
        }

        public void setAction(String action) {
            this.action = action;
        }

        public String getReason() {
            return reason;
        }

        public void setReason(String reason) {
            this.reason = reason;
        }
    }
}
