package com.zhaoyichi.devplatformbackend.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.zhaoyichi.devplatformbackend.common.Result;
import com.zhaoyichi.devplatformbackend.entity.ProjectActivity;
import com.zhaoyichi.devplatformbackend.mapper.ProjectActivityMapper;
import com.zhaoyichi.devplatformbackend.service.collab.CollabProjectService;
import com.zhaoyichi.devplatformbackend.utils.AuthHelper;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

@RestController
@RequestMapping("/api/collab/activity")
@CrossOrigin(origins = "*")
public class CollabActivityController {

    private final ProjectActivityMapper projectActivityMapper;
    private final CollabProjectService collabProjectService;

    public CollabActivityController(ProjectActivityMapper projectActivityMapper, CollabProjectService collabProjectService) {
        this.projectActivityMapper = projectActivityMapper;
        this.collabProjectService = collabProjectService;
    }

    @GetMapping("/list")
    public Result<List<ProjectActivity>> list(@RequestParam Long projectId,
                                              @RequestParam(required = false, defaultValue = "50") Integer limit,
                                              HttpServletRequest request) {
        Result<List<ProjectActivity>> auth = AuthHelper.requireLogin(request);
        if (auth != null) return auth;
        Long uid = AuthHelper.currentUserId(request);
        try {
            collabProjectService.requireMember(projectId, uid);
            int l = limit == null ? 50 : Math.max(1, Math.min(200, limit));
            List<ProjectActivity> list = projectActivityMapper.selectList(new QueryWrapper<ProjectActivity>()
                    .eq("project_id", projectId)
                    .orderByDesc("created_at")
                    .last("limit " + l));
            return Result.success(list);
        } catch (Exception e) {
            return Result.error(e.getMessage() == null ? "查询失败" : e.getMessage());
        }
    }
}

