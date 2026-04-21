package com.zhaoyichi.devplatformbackend.service.collab;

import com.zhaoyichi.devplatformbackend.entity.ProjectActivity;
import com.zhaoyichi.devplatformbackend.mapper.ProjectActivityMapper;
import org.springframework.stereotype.Service;

@Service
public class CollabActivityService {

    private final ProjectActivityMapper projectActivityMapper;

    public CollabActivityService(ProjectActivityMapper projectActivityMapper) {
        this.projectActivityMapper = projectActivityMapper;
    }

    public void add(Long projectId, Long actorUserId, String type, String refType, Long refId, String summary, String detail) {
        try {
            ProjectActivity a = new ProjectActivity();
            a.setProjectId(projectId);
            a.setActorUserId(actorUserId);
            a.setType(type);
            a.setRefType(refType);
            a.setRefId(refId);
            a.setSummary(summary == null ? "" : summary);
            a.setDetail(detail);
            projectActivityMapper.insert(a);
        } catch (Exception ignore) {
            // 动态流不应影响主流程
        }
    }
}

