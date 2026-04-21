package com.zhaoyichi.devplatformbackend.controller;

import com.zhaoyichi.devplatformbackend.common.Result;
import com.zhaoyichi.devplatformbackend.entity.*;
import com.zhaoyichi.devplatformbackend.service.CommunityService;
import com.zhaoyichi.devplatformbackend.utils.AuthHelper;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

@RestController
@RequestMapping("/api/community")
@CrossOrigin
public class CommunityController {

    private final CommunityService communityService;

    public CommunityController(CommunityService communityService) {
        this.communityService = communityService;
    }

    @PostMapping("/create")
    public Result<Long> create(@RequestBody Community community, HttpServletRequest request) {
        Result<Long> auth = AuthHelper.requireLogin(request);
        if (auth != null) return auth;
        return communityService.create(community, AuthHelper.currentUserId(request));
    }

    @GetMapping("/list")
    public Result<List<Community>> list(
            @RequestParam(required = false) String tag,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.success(communityService.list(tag, page, size));
    }

    @GetMapping("/{id}")
    public Result<Community> detail(@PathVariable Long id) {
        Community community = communityService.getById(id);
        if (community == null) return Result.error("社群不存在");
        return Result.success(community);
    }

    @PostMapping("/apply")
    public Result<String> apply(@RequestBody ApplyRequest req, HttpServletRequest request) {
        Result<String> auth = AuthHelper.requireLogin(request);
        if (auth != null) return auth;
        return communityService.apply(req.getCommunityId(), AuthHelper.currentUserId(request), req.getReason());
    }

    @PostMapping("/apply/review")
    public Result<String> review(@RequestBody ReviewRequest req, HttpServletRequest request) {
        Result<String> auth = AuthHelper.requireLogin(request);
        if (auth != null) return auth;
        return communityService.reviewApply(req.getApplyId(), AuthHelper.currentUserId(request), req.getAction());
    }

    @GetMapping("/{id}/members")
    public Result<List<CommunityMember>> members(@PathVariable Long id) {
        return Result.success(communityService.getMembers(id));
    }

    @GetMapping("/{id}/applies")
    public Result<List<CommunityApply>> applies(@PathVariable Long id, HttpServletRequest request) {
        Result<List<CommunityApply>> auth = AuthHelper.requireLogin(request);
        if (auth != null) return auth;
        return Result.success(communityService.getPendingApplies(id, AuthHelper.currentUserId(request)));
    }

    @PostMapping("/post/publish")
    public Result<Long> publishPost(@RequestBody CommunityPost post, HttpServletRequest request) {
        Result<Long> auth = AuthHelper.requireLogin(request);
        if (auth != null) return auth;
        return communityService.publishPost(post, AuthHelper.currentUserId(request));
    }

    @GetMapping("/{id}/posts")
    public Result<List<CommunityPost>> posts(
            @PathVariable Long id,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String category) {
        return Result.success(communityService.getPosts(id, type, category));
    }

    @PostMapping("/post/{postId}/attachment")
    public Result<CommunityAttachment> uploadAttachment(@PathVariable Long postId,
                                                        @RequestParam("file") MultipartFile file,
                                                        HttpServletRequest request) {
        Result<CommunityAttachment> auth = AuthHelper.requireLogin(request);
        if (auth != null) return auth;
        Long uid = AuthHelper.currentUserId(request);
        try {
            return Result.success(communityService.uploadAttachment(postId, uid, file));
        } catch (IllegalStateException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            return Result.systemError("上传失败");
        }
    }

    @GetMapping("/post/{postId}/attachments")
    public Result<List<CommunityAttachment>> listAttachments(@PathVariable Long postId) {
        return Result.success(communityService.listAttachments(postId));
    }

    @PatchMapping("/post/{postId}/sticky")
    public Result<String> sticky(
            @PathVariable Long postId,
            @RequestParam boolean value,
            HttpServletRequest request) {
        Result<String> auth = AuthHelper.requireLogin(request);
        if (auth != null) {
            return auth;
        }
        return communityService.setPostSticky(postId, AuthHelper.currentUserId(request), value);
    }

    @PatchMapping("/post/{postId}/essence")
    public Result<String> essence(
            @PathVariable Long postId,
            @RequestParam boolean value,
            HttpServletRequest request) {
        Result<String> auth = AuthHelper.requireLogin(request);
        if (auth != null) {
            return auth;
        }
        return communityService.setPostEssence(postId, AuthHelper.currentUserId(request), value);
    }

    @GetMapping("/{id}/isMember")
    public Result<Boolean> isMember(@PathVariable Long id, HttpServletRequest request) {
        Result<Boolean> auth = AuthHelper.requireLogin(request);
        if (auth != null) return auth;
        return Result.success(communityService.isMember(id, AuthHelper.currentUserId(request)));
    }

    public static class ApplyRequest {
        private Long communityId;
        private String reason;
        public Long getCommunityId() { return communityId; }
        public void setCommunityId(Long communityId) { this.communityId = communityId; }
        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
    }

    public static class ReviewRequest {
        private Long applyId;
        private String action;
        public Long getApplyId() { return applyId; }
        public void setApplyId(Long applyId) { this.applyId = applyId; }
        public String getAction() { return action; }
        public void setAction(String action) { this.action = action; }
    }
}
