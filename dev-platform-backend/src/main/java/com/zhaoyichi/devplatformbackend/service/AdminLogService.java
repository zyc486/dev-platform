package com.zhaoyichi.devplatformbackend.service;

import com.zhaoyichi.devplatformbackend.entity.AdminActionLog;
import com.zhaoyichi.devplatformbackend.mapper.AdminActionLogMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class AdminLogService {
    private final AdminActionLogMapper adminActionLogMapper;

    public AdminLogService(AdminActionLogMapper adminActionLogMapper) {
        this.adminActionLogMapper = adminActionLogMapper;
    }

    public void log(Long adminUserId, String adminUsername, String actionType, String targetType, Long targetId, String detail) {
        AdminActionLog log = new AdminActionLog();
        log.setAdminUserId(adminUserId == null ? 0L : adminUserId);
        log.setAdminUsername(adminUsername == null ? "unknown" : adminUsername);
        log.setActionType(actionType);
        log.setTargetType(targetType);
        log.setTargetId(targetId);
        log.setDetail(detail);
        log.setCreateTime(LocalDateTime.now());
        adminActionLogMapper.insert(log);
    }
}
