package com.zhaoyichi.devplatformbackend.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpServletRequest;

/**
 * 登录审计服务：
 *
 * <ul>
 *   <li>每次登录（成功或失败）写入 {@code login_log}</li>
 *   <li>成功登录时，若当前 IP 从未成功登录过本账号，则通过站内消息发送「新设备/新 IP 登录」提醒</li>
 * </ul>
 *
 * <p>不抛异常；即使审计失败也不影响登录主流程。</p>
 */
@Service
public class LoginAuditService {
    private static final Logger log = LoggerFactory.getLogger(LoginAuditService.class);

    private final JdbcTemplate jdbc;
    private final MessageNoticeService messageNoticeService;

    public LoginAuditService(JdbcTemplate jdbc, MessageNoticeService messageNoticeService) {
        this.jdbc = jdbc;
        this.messageNoticeService = messageNoticeService;
    }

    /**
     * 记录一次登录行为。
     *
     * @param userId     登录成功后解析出的 userId，失败时可为 null
     * @param username   客户端提交的用户名
     * @param req        HTTP 请求（用于抽取 IP / UA）
     * @param success    是否成功
     * @param failReason 失败原因（success=false 时有值）
     */
    public void record(Long userId, String username, HttpServletRequest req, boolean success, String failReason) {
        try {
            String ip = extractIp(req);
            String ua = req.getHeader("User-Agent");
            if (ua == null) ua = "";
            if (ua.length() > 280) ua = ua.substring(0, 280);

            jdbc.update(
                    "INSERT INTO login_log(user_id, username, ip, user_agent, success, fail_reason) VALUES(?,?,?,?,?,?)",
                    userId, username, ip, ua, success ? 1 : 0, failReason);

            if (success && userId != null) {
                detectAbnormal(userId, ip);
            }
        } catch (Exception e) {
            log.warn("[login-audit] record failed username={} ip={}", username, extractIp(req), e);
        }
    }

    /**
     * 规则：该用户此前 **一分钟之前** 没有任何从该 IP 的成功登录记录 => 视为新 IP。
     * （排除当前这一条刚写入的记录，避免自检命中。）
     */
    private void detectAbnormal(long userId, String ip) {
        try {
            Integer historyCnt = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM login_log WHERE user_id = ? AND ip = ? AND success = 1 " +
                            "AND create_time < DATE_SUB(NOW(), INTERVAL 1 MINUTE)",
                    Integer.class, userId, ip);
            if (historyCnt != null && historyCnt == 0) {
                messageNoticeService.createNotice(
                        userId,
                        "security",
                        "新设备/新 IP 登录提醒",
                        "检测到你的账号在新 IP (" + ip + ") 登录，如非本人操作请立即修改密码。",
                        null);
            }
        } catch (Exception e) {
            log.warn("[login-audit] abnormal detect failed userId={} ip={}", userId, ip, e);
        }
    }

    /**
     * 从请求头抽取客户端 IP，优先 X-Forwarded-For，再 X-Real-IP，最后远端地址。
     */
    public static String extractIp(HttpServletRequest req) {
        if (req == null) return "unknown";
        String xf = req.getHeader("X-Forwarded-For");
        if (xf != null && !xf.isEmpty() && !"unknown".equalsIgnoreCase(xf)) {
            int comma = xf.indexOf(',');
            return (comma > 0 ? xf.substring(0, comma) : xf).trim();
        }
        String ri = req.getHeader("X-Real-IP");
        if (ri != null && !ri.isEmpty()) return ri.trim();
        return req.getRemoteAddr() == null ? "unknown" : req.getRemoteAddr();
    }
}
