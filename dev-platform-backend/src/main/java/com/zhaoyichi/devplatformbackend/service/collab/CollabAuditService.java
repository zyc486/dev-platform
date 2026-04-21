package com.zhaoyichi.devplatformbackend.service.collab;

import com.zhaoyichi.devplatformbackend.entity.AuditLog;
import com.zhaoyichi.devplatformbackend.mapper.AuditLogMapper;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpServletRequest;

@Service
public class CollabAuditService {

    private final AuditLogMapper auditLogMapper;

    public CollabAuditService(AuditLogMapper auditLogMapper) {
        this.auditLogMapper = auditLogMapper;
    }

    public void record(HttpServletRequest request, Long userId, String action, String entityType, Long entityId, String detail) {
        try {
            AuditLog log = new AuditLog();
            log.setUserId(userId);
            log.setAction(action);
            log.setEntityType(entityType);
            log.setEntityId(entityId);
            if (request != null) {
                log.setIp(request.getRemoteAddr());
                log.setUserAgent(safeUa(request.getHeader("User-Agent")));
            }
            log.setDetail(detail);
            auditLogMapper.insert(log);
        } catch (Exception ignore) {
            // 审计不应影响主流程
        }
    }

    private static String safeUa(String ua) {
        if (ua == null) return null;
        String s = ua.trim();
        if (s.length() <= 250) return s;
        return s.substring(0, 250);
    }
}

