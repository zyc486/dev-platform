package com.zhaoyichi.devplatformbackend.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.zhaoyichi.devplatformbackend.common.Result;
import com.zhaoyichi.devplatformbackend.entity.CollabApply;
import com.zhaoyichi.devplatformbackend.entity.Collaboration;
import com.zhaoyichi.devplatformbackend.entity.CollaborationReview;
import com.zhaoyichi.devplatformbackend.entity.User;
import com.zhaoyichi.devplatformbackend.mapper.CollabApplyMapper;
import com.zhaoyichi.devplatformbackend.mapper.CollaborationMapper;
import com.zhaoyichi.devplatformbackend.mapper.CollaborationReviewMapper;
import com.zhaoyichi.devplatformbackend.mapper.UserMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 协作完成确认与互评写入；完成后触发被评价方信用分重算。
 */
@Service
public class CollaborationReviewService {

    private final CollaborationMapper collaborationMapper;
    private final CollabApplyMapper collabApplyMapper;
    private final CollaborationReviewMapper collaborationReviewMapper;
    private final UserMapper userMapper;
    private final CreditScoreService creditScoreService;
    private final MessageNoticeService messageNoticeService;

    public CollaborationReviewService(CollaborationMapper collaborationMapper,
                                      CollabApplyMapper collabApplyMapper,
                                      CollaborationReviewMapper collaborationReviewMapper,
                                      UserMapper userMapper,
                                      CreditScoreService creditScoreService,
                                      MessageNoticeService messageNoticeService) {
        this.collaborationMapper = collaborationMapper;
        this.collabApplyMapper = collabApplyMapper;
        this.collaborationReviewMapper = collaborationReviewMapper;
        this.userMapper = userMapper;
        this.creditScoreService = creditScoreService;
        this.messageNoticeService = messageNoticeService;
    }

    /**
     * 将协作标记为已完成：发布方或任一已通过申请者可操作；仅 {@code in_progress} 可完成。
     */
    @Transactional(rollbackFor = Exception.class)
    public Result<String> completeCollaboration(Long actorUserId, Integer collaborationId) {
        if (actorUserId == null || collaborationId == null) {
            return Result.error("参数无效");
        }
        Collaboration project = collaborationMapper.selectById(collaborationId);
        if (project == null) {
            return Result.error("协作不存在");
        }
        if (!"in_progress".equals(project.getStatus())) {
            return Result.error("仅「进行中」的协作可标记完成，当前状态：" + project.getStatus());
        }
        if (!isApprovedParticipant(actorUserId, project) && !isOwner(actorUserId, project)) {
            return Result.error("仅协作发布方或已通过申请者可标记完成");
        }
        project.setStatus("completed");
        project.setFinishTime(LocalDateTime.now());
        collaborationMapper.updateById(project);

        messageNoticeService.createNotice(
                Long.valueOf(project.getUserId()),
                "collab",
                "协作已标记完成",
                "项目《" + project.getTitle() + "》已由参与者标记为已完成，可进行互评。",
                collaborationId.longValue()
        );
        return Result.successMsg("协作已标记为已完成，可邀请对方互评");
    }

    /**
     * 提交互评：协作须为 completed；评价双方须均为参与方；每位发起者对同一接收方仅一条记录。
     */
    @Transactional(rollbackFor = Exception.class)
    public Result<String> submitReview(Long fromUserId, Integer collaborationId, Long toUserId, Integer rating, String comment) {
        if (fromUserId == null || collaborationId == null || toUserId == null || rating == null) {
            return Result.error("参数不完整");
        }
        if (rating < 1 || rating > 5) {
            return Result.error("评分范围为 1～5 星");
        }
        if (fromUserId.equals(toUserId)) {
            return Result.error("不能给自己评分");
        }
        Collaboration project = collaborationMapper.selectById(collaborationId);
        if (project == null) {
            return Result.error("协作不存在");
        }
        if (!"completed".equals(project.getStatus())) {
            return Result.error("仅已完成的协作可评价，当前状态：" + project.getStatus());
        }
        if (!isParticipant(fromUserId, project) || !isParticipant(toUserId, project)) {
            return Result.error("仅协作参与方可互相评价");
        }
        QueryWrapper<CollaborationReview> exist = new QueryWrapper<>();
        exist.eq("collaboration_id", collaborationId)
                .eq("from_user_id", fromUserId)
                .eq("to_user_id", toUserId);
        if (collaborationReviewMapper.selectCount(exist) > 0) {
            return Result.error("您已对该用户提交过评价");
        }
        CollaborationReview row = new CollaborationReview();
        row.setCollaborationId(collaborationId);
        row.setFromUserId(fromUserId);
        row.setToUserId(toUserId);
        row.setRating(rating);
        row.setComment(comment == null ? null : comment.trim());
        row.setCreateTime(LocalDateTime.now());
        collaborationReviewMapper.insert(row);

        User reviewee = userMapper.selectById(toUserId);
        if (reviewee != null && reviewee.getGithubUsername() != null && !reviewee.getGithubUsername().trim().isEmpty()) {
            creditScoreService.refreshGithubUserCreditScores(reviewee.getGithubUsername().trim());
        }

        messageNoticeService.createNotice(
                toUserId,
                "collab",
                "收到协作互评",
                "你在《" + project.getTitle() + "》中收到一条 " + rating + " 星评价",
                row.getId()
        );
        return Result.successMsg("评价提交成功");
    }

    private boolean isOwner(Long userId, Collaboration project) {
        return project.getUserId() != null && project.getUserId().longValue() == userId;
    }

    private boolean isApprovedParticipant(Long userId, Collaboration project) {
        QueryWrapper<CollabApply> w = new QueryWrapper<>();
        w.eq("project_id", project.getId())
                .eq("user_id", userId.intValue())
                .eq("status", "approved");
        return collabApplyMapper.selectCount(w) > 0;
    }

    private boolean isParticipant(Long userId, Collaboration project) {
        return isOwner(userId, project) || isApprovedParticipant(userId, project);
    }
}
