package com.zhaoyichi.devplatformbackend.service.collab;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.zhaoyichi.devplatformbackend.entity.Project;
import com.zhaoyichi.devplatformbackend.entity.ProjectMember;
import com.zhaoyichi.devplatformbackend.entity.User;
import com.zhaoyichi.devplatformbackend.mapper.ProjectMapper;
import com.zhaoyichi.devplatformbackend.mapper.ProjectMemberMapper;
import com.zhaoyichi.devplatformbackend.mapper.UserMapper;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class CollabProjectService {

    public static final String ROLE_OWNER = "owner";
    public static final String ROLE_MAINTAINER = "maintainer";
    public static final String ROLE_DEV = "dev";
    public static final String ROLE_VIEWER = "viewer";

    private final ProjectMapper projectMapper;
    private final ProjectMemberMapper projectMemberMapper;
    private final UserMapper userMapper;

    public CollabProjectService(ProjectMapper projectMapper, ProjectMemberMapper projectMemberMapper, UserMapper userMapper) {
        this.projectMapper = projectMapper;
        this.projectMemberMapper = projectMemberMapper;
        this.userMapper = userMapper;
    }

    public Project createProject(Long ownerUserId, String name, String description, String visibility) {
        Project p = new Project();
        p.setName(name);
        p.setDescription(description);
        p.setVisibility(normalizeVisibility(visibility));
        p.setOwnerUserId(ownerUserId);
        projectMapper.insert(p);

        ProjectMember m = new ProjectMember();
        m.setProjectId(p.getId());
        m.setUserId(ownerUserId);
        m.setRole(ROLE_OWNER);
        projectMemberMapper.insert(m);
        return p;
    }

    public List<Project> listMyProjects(Long userId) {
        List<ProjectMember> ms = projectMemberMapper.selectList(new QueryWrapper<ProjectMember>().eq("user_id", userId));
        if (ms == null || ms.isEmpty()) return Collections.emptyList();
        List<Long> pids = ms.stream().map(ProjectMember::getProjectId).filter(Objects::nonNull).distinct().collect(Collectors.toList());
        if (pids.isEmpty()) return Collections.emptyList();
        return projectMapper.selectList(new QueryWrapper<Project>().in("id", pids).orderByDesc("updated_at"));
    }

    public Project getProject(Long projectId) {
        return projectMapper.selectById(projectId);
    }

    public List<ProjectMember> listMembers(Long projectId) {
        return projectMemberMapper.selectList(new QueryWrapper<ProjectMember>().eq("project_id", projectId).orderByDesc("role"));
    }

    public ProjectMember requireMember(Long projectId, Long userId) {
        ProjectMember m = projectMemberMapper.selectOne(new QueryWrapper<ProjectMember>()
                .eq("project_id", projectId)
                .eq("user_id", userId)
                .last("limit 1"));
        if (m == null) {
            throw new IllegalStateException("无项目权限");
        }
        return m;
    }

    public boolean isManagerRole(String role) {
        return ROLE_OWNER.equals(role) || ROLE_MAINTAINER.equals(role);
    }

    public Long findUserIdByGithubUsername(String githubUsername) {
        if (githubUsername == null || githubUsername.trim().isEmpty()) return null;
        User u = userMapper.selectOne(new QueryWrapper<User>().eq("github_username", githubUsername.trim()).last("limit 1"));
        return u == null ? null : u.getId();
    }

    public void addMember(Long projectId, Long operatorUserId, Long newUserId, String role) {
        ProjectMember op = requireMember(projectId, operatorUserId);
        if (!isManagerRole(op.getRole())) {
            throw new IllegalStateException("无权限添加成员");
        }
        String r = normalizeRole(role);
        ProjectMember existing = projectMemberMapper.selectOne(new QueryWrapper<ProjectMember>()
                .eq("project_id", projectId)
                .eq("user_id", newUserId)
                .last("limit 1"));
        if (existing != null) {
            existing.setRole(r);
            projectMemberMapper.updateById(existing);
            return;
        }
        ProjectMember m = new ProjectMember();
        m.setProjectId(projectId);
        m.setUserId(newUserId);
        m.setRole(r);
        projectMemberMapper.insert(m);
    }

    public void updateMemberRole(Long projectId, Long operatorUserId, Long targetUserId, String role) {
        ProjectMember op = requireMember(projectId, operatorUserId);
        if (!isManagerRole(op.getRole())) {
            throw new IllegalStateException("无权限修改角色");
        }
        ProjectMember target = projectMemberMapper.selectOne(new QueryWrapper<ProjectMember>()
                .eq("project_id", projectId)
                .eq("user_id", targetUserId)
                .last("limit 1"));
        if (target == null) {
            throw new IllegalStateException("成员不存在");
        }
        if (ROLE_OWNER.equals(target.getRole())) {
            throw new IllegalStateException("不可修改 Owner 角色");
        }
        target.setRole(normalizeRole(role));
        projectMemberMapper.updateById(target);
    }

    private static String normalizeVisibility(String v) {
        if (v == null) return "private";
        String s = v.trim().toLowerCase();
        return ("public".equals(s) ? "public" : "private");
    }

    private static String normalizeRole(String role) {
        if (role == null) return ROLE_DEV;
        String r = role.trim().toLowerCase();
        switch (r) {
            case ROLE_OWNER:
            case ROLE_MAINTAINER:
            case ROLE_DEV:
            case ROLE_VIEWER:
                return r;
            default:
                return ROLE_DEV;
        }
    }
}

