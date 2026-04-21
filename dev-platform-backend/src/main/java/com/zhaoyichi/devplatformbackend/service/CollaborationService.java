package com.zhaoyichi.devplatformbackend.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.zhaoyichi.devplatformbackend.common.Result;
import com.zhaoyichi.devplatformbackend.entity.CollabApply;
import com.zhaoyichi.devplatformbackend.entity.Collaboration;
import com.zhaoyichi.devplatformbackend.entity.User;
import com.zhaoyichi.devplatformbackend.mapper.CollabApplyMapper;
import com.zhaoyichi.devplatformbackend.mapper.CollaborationMapper;
import com.zhaoyichi.devplatformbackend.mapper.UserMapper;
import com.zhaoyichi.devplatformbackend.vo.CreditScoreResult;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class CollaborationService {
    private final CollaborationMapper collaborationMapper;
    private final CollabApplyMapper collabApplyMapper;
    private final UserMapper userMapper;
    private final CreditScoreService creditScoreService;
    private final MessageNoticeService messageNoticeService;

    public CollaborationService(CollaborationMapper collaborationMapper,
                                CollabApplyMapper collabApplyMapper,
                                UserMapper userMapper,
                                CreditScoreService creditScoreService,
                                MessageNoticeService messageNoticeService) {
        this.collaborationMapper = collaborationMapper;
        this.collabApplyMapper = collabApplyMapper;
        this.userMapper = userMapper;
        this.creditScoreService = creditScoreService;
        this.messageNoticeService = messageNoticeService;
    }

    public Result<String> publish(Collaboration collaboration, User creator) {
        if (creator == null) {
            return Result.error("发布失败：未找到当前用户信息");
        }
        collaboration.setUserId(creator.getId().intValue());
        collaboration.setStatus("pending");
        collaboration.setCreateTime(LocalDateTime.now());
        collaborationMapper.insert(collaboration);
        return Result.successMsg("开源项目发布成功！");
    }

    public List<Map<String, Object>> openProjects() {
        QueryWrapper<Collaboration> wrapper = new QueryWrapper<>();
        wrapper.eq("status", "pending").orderByDesc("create_time");
        return formatProjects(collaborationMapper.selectList(wrapper));
    }

    public Result<String> apply(Integer projectId, User applicant) {
        Collaboration project = collaborationMapper.selectById(projectId);
        if (project == null || !"pending".equals(project.getStatus())) {
            return Result.error("该项目不存在或已关闭！");
        }
        if (applicant == null) {
            return Result.error("未找到申请人信息！");
        }
        if (project.getUserId() != null && applicant.getId() != null &&
                project.getUserId().intValue() == applicant.getId().intValue()) {
            return Result.error("不能申请自己发布的项目！");
        }
        if (applicant.getGithubUsername() == null || applicant.getGithubUsername().trim().isEmpty()) {
            return Result.error("您尚未绑定 GitHub 生成信用档案，无法接单！");
        }

        CreditScoreResult credit = creditScoreService.queryCredit(applicant.getGithubUsername(), "综合", null);
        if (credit == null) {
            return Result.error("【风控警告】GitHub 上查无此人，或网络拉取失败，拒绝接单！");
        }

        int userScore = credit.getTotalScore();
        int requireScore = project.getMinCredit() == null ? 0 : project.getMinCredit();
        if (userScore < requireScore) {
            return Result.error("【风控拦截】该任务要求最低信用分为 " + requireScore + " 分！您当前仅为 " + userScore + " 分，能力暂不匹配。");
        }

        QueryWrapper<CollabApply> applyWrapper = new QueryWrapper<>();
        applyWrapper.eq("project_id", projectId).eq("user_id", applicant.getId());
        if (collabApplyMapper.selectCount(applyWrapper) > 0) {
            return Result.error("您已经申请过该项目，请勿重复提交！");
        }

        CollabApply apply = new CollabApply();
        apply.setProjectId(projectId);
        apply.setUserId(applicant.getId().intValue());
        apply.setStatus("pending");
        apply.setApplyTime(LocalDateTime.now());
        collabApplyMapper.insert(apply);

        messageNoticeService.createNotice(
                Long.valueOf(project.getUserId()),
                "collab",
                "有新的协作申请",
                applicant.getUsername() + " 申请加入项目《" + project.getTitle() + "》",
                apply.getId() == null ? null : apply.getId().longValue()
        );
        return Result.successMsg("信用风控校验通过！申请已提交，等待发起人审核。");
    }

    public List<Map<String, Object>> myPublish(Long userId) {
        QueryWrapper<Collaboration> wrapper = new QueryWrapper<>();
        wrapper.eq("user_id", userId).orderByDesc("create_time");
        return formatProjects(collaborationMapper.selectList(wrapper));
    }

    public List<Map<String, Object>> myApply(Long userId) {
        QueryWrapper<CollabApply> wrapper = new QueryWrapper<>();
        wrapper.eq("user_id", userId).orderByDesc("apply_time");
        List<CollabApply> applies = collabApplyMapper.selectList(wrapper);
        List<Map<String, Object>> result = new ArrayList<>();
        for (CollabApply apply : applies) {
            Collaboration project = collaborationMapper.selectById(apply.getProjectId());
            if (project == null) {
                continue;
            }
            Map<String, Object> item = new HashMap<>();
            item.put("applyId", apply.getId());
            item.put("projectId", project.getId());
            item.put("title", project.getTitle());
            item.put("content", project.getContent());
            item.put("status", apply.getStatus());
            item.put("auditReason", apply.getAuditReason());
            item.put("applyTime", apply.getApplyTime());
            item.put("projectStatus", project.getStatus());
            item.put("creatorId", project.getUserId());
            result.add(item);
        }
        return result;
    }

    public List<Map<String, Object>> applyList(Long userId, Integer projectId) {
        QueryWrapper<Collaboration> projectQuery = new QueryWrapper<>();
        projectQuery.eq("user_id", userId);
        if (projectId != null) {
            projectQuery.eq("id", projectId);
        }
        List<Collaboration> projects = collaborationMapper.selectList(projectQuery);
        List<Map<String, Object>> result = new ArrayList<>();
        for (Collaboration project : projects) {
            QueryWrapper<CollabApply> wrapper = new QueryWrapper<>();
            wrapper.eq("project_id", project.getId()).orderByDesc("apply_time");
            for (CollabApply apply : collabApplyMapper.selectList(wrapper)) {
                User applicant = userMapper.selectById(apply.getUserId());
                Map<String, Object> item = new HashMap<>();
                item.put("applyId", apply.getId());
                item.put("projectId", project.getId());
                item.put("projectTitle", project.getTitle());
                item.put("status", apply.getStatus());
                item.put("auditReason", apply.getAuditReason());
                item.put("applyTime", apply.getApplyTime());
                item.put("auditTime", apply.getAuditTime());
                item.put("applicantId", apply.getUserId());
                item.put("applicantUsername", applicant == null ? "未知用户" : applicant.getUsername());
                item.put("githubUsername", applicant == null ? null : applicant.getGithubUsername());
                result.add(item);
            }
        }
        return result;
    }

    public Result<String> review(Long ownerId, Integer applyId, String action, String reason) {
        CollabApply apply = collabApplyMapper.selectById(applyId);
        if (apply == null) {
            return Result.error("申请记录不存在");
        }
        Collaboration project = collaborationMapper.selectById(apply.getProjectId());
        if (project == null || !ownerId.equals(Long.valueOf(project.getUserId()))) {
            return Result.error("无权限审核该申请");
        }
        if (!"pending".equals(apply.getStatus())) {
            return Result.error("该申请已处理");
        }
        String newStatus = "approve".equalsIgnoreCase(action) ? "approved" : "rejected";
        apply.setStatus(newStatus);
        apply.setAuditReason(reason);
        apply.setAuditTime(LocalDateTime.now());
        collabApplyMapper.updateById(apply);

        if ("approved".equals(newStatus) && "pending".equals(project.getStatus())) {
            project.setStatus("in_progress");
            collaborationMapper.updateById(project);
        }

        User creator = userMapper.selectById(ownerId);
        messageNoticeService.createNotice(
                Long.valueOf(apply.getUserId()),
                "collab",
                "协作申请结果",
                "你申请的项目《" + project.getTitle() + "》已被" + ("approved".equals(newStatus) ? "通过" : "拒绝") +
                        (creator == null ? "" : "，处理人：" + creator.getUsername()),
                apply.getId().longValue()
        );
        return Result.successMsg("审核完成");
    }

    public Result<String> updateProjectStatus(Long ownerId, Integer projectId, String status) {
        Collaboration project = collaborationMapper.selectById(projectId);
        if (project == null) {
            return Result.error("项目不存在");
        }
        if (!ownerId.equals(Long.valueOf(project.getUserId()))) {
            return Result.error("无权限操作该项目");
        }
        project.setStatus(status);
        if ("completed".equals(status)) {
            project.setFinishTime(LocalDateTime.now());
        }
        collaborationMapper.updateById(project);
        return Result.successMsg("项目状态已更新");
    }

    public List<Map<String, Object>> matchDevelopers(Collaboration project, int limit) {
        int minCredit = project.getMinCredit() == null ? 0 : project.getMinCredit();
        QueryWrapper<com.zhaoyichi.devplatformbackend.entity.CreditScore> scoreWrapper = new QueryWrapper<>();
        scoreWrapper.eq("scene", "综合").ge("total_score", minCredit)
                .orderByDesc("total_score").last("LIMIT " + Math.min(limit * 3, 50));
        List<com.zhaoyichi.devplatformbackend.entity.CreditScore> scores =
                creditScoreService.queryByScene("综合", minCredit, limit * 3);

        List<Map<String, Object>> result = new ArrayList<>();
        for (com.zhaoyichi.devplatformbackend.entity.CreditScore cs : scores) {
            if (result.size() >= limit) break;
            User user = userMapper.selectOne(new QueryWrapper<User>().eq("github_username", cs.getGithubUsername()));
            if (user == null || Long.valueOf(project.getUserId()).equals(user.getId())) continue;
            Map<String, Object> item = new HashMap<>();
            item.put("userId", user.getId());
            item.put("username", user.getUsername());
            item.put("githubUsername", user.getGithubUsername());
            item.put("techTags", user.getTechTags());
            item.put("totalScore", cs.getTotalScore());
            item.put("level", cs.getLevel());
            result.add(item);
        }
        return result;
    }

    public List<Map<String, Object>> formatProjects(List<Collaboration> projects) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (Collaboration project : projects) {
            Map<String, Object> item = new HashMap<>();
            item.put("id", project.getId());
            item.put("title", project.getTitle());
            item.put("content", project.getContent());
            item.put("minCredit", project.getMinCredit());
            item.put("status", project.getStatus());
            item.put("createTime", project.getCreateTime());
            item.put("finishTime", project.getFinishTime());
            User creator = userMapper.selectById(project.getUserId());
            item.put("creatorUsername", creator == null ? "未知用户" : creator.getUsername());
            item.put("creatorId", project.getUserId());
            QueryWrapper<CollabApply> aw = new QueryWrapper<>();
            aw.eq("project_id", project.getId()).eq("status", "approved");
            List<Map<String, Object>> approvedApplicants = new ArrayList<>();
            for (CollabApply a : collabApplyMapper.selectList(aw)) {
                User u = userMapper.selectById(a.getUserId());
                Map<String, Object> am = new HashMap<>();
                am.put("userId", a.getUserId());
                am.put("username", u == null ? "用户" + a.getUserId() : u.getUsername());
                approvedApplicants.add(am);
            }
            item.put("approvedApplicants", approvedApplicants);
            list.add(item);
        }
        return list;
    }
}
