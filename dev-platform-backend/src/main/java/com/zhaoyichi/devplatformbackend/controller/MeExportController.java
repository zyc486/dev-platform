package com.zhaoyichi.devplatformbackend.controller;

import com.zhaoyichi.devplatformbackend.common.Result;
import com.zhaoyichi.devplatformbackend.utils.AuthHelper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@RestController
@RequestMapping("/api/me")
@CrossOrigin(origins = "*", exposedHeaders = {"Content-Disposition"})
public class MeExportController {
    private final JdbcTemplate jdbcTemplate;

    public MeExportController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping("/export")
    public void exportMyData(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Result<?> auth = AuthHelper.requireLogin(request);
        if (auth != null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setCharacterEncoding("UTF-8");
            response.setContentType("text/plain; charset=UTF-8");
            response.getWriter().println("未登录");
            return;
        }
        Long uid = AuthHelper.currentUserId(request);

        String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmm"));
        String filename = "my_data_" + uid + "_" + ts + ".zip";

        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/zip");
        response.setHeader("Content-Disposition", buildDownloadHeader(filename));

        try (OutputStream os = response.getOutputStream();
             ZipOutputStream zip = new ZipOutputStream(os)) {
            writeCsv(zip, "profile.csv", new String[]{"field", "value"}, jdbcTemplate.queryForList(
                    "SELECT 'userId' AS field, CAST(id AS CHAR) AS value FROM user WHERE id = ? " +
                            "UNION ALL SELECT 'username', COALESCE(username,'') FROM user WHERE id = ? " +
                            "UNION ALL SELECT 'nickname', COALESCE(nickname,'') FROM user WHERE id = ? " +
                            "UNION ALL SELECT 'githubUsername', COALESCE(github_username,'') FROM user WHERE id = ? " +
                            "UNION ALL SELECT 'phone', COALESCE(phone,'') FROM user WHERE id = ? " +
                            "UNION ALL SELECT 'email', COALESCE(email,'') FROM user WHERE id = ?",
                    uid, uid, uid, uid, uid, uid));

            writeCsv(zip, "credit_query_log.csv",
                    new String[]{"id", "githubUsername", "scene", "status", "totalScore", "queryTime"},
                    jdbcTemplate.queryForList(
                            "SELECT id, github_username AS githubUsername, scene, status, total_score AS totalScore, " +
                                    "DATE_FORMAT(query_time, '%Y-%m-%d %H:%i:%s') AS queryTime " +
                                    "FROM query_log WHERE user_id = ? ORDER BY query_time DESC LIMIT 2000",
                            uid));

            writeCsv(zip, "collaboration_publish.csv",
                    new String[]{"id", "title", "status", "createTime"},
                    jdbcTemplate.queryForList(
                            "SELECT id, title, status, DATE_FORMAT(create_time, '%Y-%m-%d %H:%i:%s') AS createTime " +
                                    "FROM collaboration WHERE user_id = ? ORDER BY create_time DESC LIMIT 2000",
                            uid));

            writeCsv(zip, "followings.csv",
                    new String[]{"followUserId", "time"},
                    jdbcTemplate.queryForList(
                            "SELECT follow_user_id AS followUserId, DATE_FORMAT(create_time, '%Y-%m-%d %H:%i:%s') AS time " +
                                    "FROM user_follow WHERE user_id = ? ORDER BY create_time DESC LIMIT 5000",
                            uid));

            writeCsv(zip, "followers.csv",
                    new String[]{"userId", "time"},
                    jdbcTemplate.queryForList(
                            "SELECT user_id AS userId, DATE_FORMAT(create_time, '%Y-%m-%d %H:%i:%s') AS time " +
                                    "FROM user_follow WHERE follow_user_id = ? ORDER BY create_time DESC LIMIT 5000",
                            uid));

            writeCsv(zip, "feedback.csv",
                    new String[]{"id", "type", "title", "status", "createTime", "replyTime"},
                    jdbcTemplate.queryForList(
                            "SELECT id, type, title, status, " +
                                    "DATE_FORMAT(create_time, '%Y-%m-%d %H:%i:%s') AS createTime, " +
                                    "DATE_FORMAT(reply_time, '%Y-%m-%d %H:%i:%s') AS replyTime " +
                                    "FROM feedback WHERE user_id = ? ORDER BY create_time DESC LIMIT 2000",
                            uid));

            writeCsv(zip, "dm_messages.csv",
                    new String[]{"id", "fromUserId", "toUserId", "content", "isRead", "createTime"},
                    jdbcTemplate.queryForList(
                            "SELECT id, from_user_id AS fromUserId, to_user_id AS toUserId, content, is_read AS isRead, " +
                                    "DATE_FORMAT(create_time, '%Y-%m-%d %H:%i:%s') AS createTime " +
                                    "FROM dm_message WHERE from_user_id = ? OR to_user_id = ? ORDER BY create_time DESC LIMIT 5000",
                            uid, uid));

            zip.finish();
        }
    }

    private static void writeCsv(ZipOutputStream zip,
                                 String entryName,
                                 String[] headers,
                                 List<Map<String, Object>> rows) throws IOException {
        zip.putNextEntry(new ZipEntry(entryName));
        // UTF-8 BOM for Excel
        zip.write(new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF});
        zip.write((csvLine(headers) + "\n").getBytes(StandardCharsets.UTF_8));
        if (rows != null) {
            for (Map<String, Object> r : rows) {
                String[] cells = new String[headers.length];
                for (int i = 0; i < headers.length; i++) {
                    Object v = r.get(headers[i]);
                    cells[i] = v == null ? "" : String.valueOf(v);
                }
                zip.write((csvLine(cells) + "\n").getBytes(StandardCharsets.UTF_8));
            }
        }
        zip.closeEntry();
    }

    private static String csvLine(String[] cells) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < cells.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(csvEscape(cells[i]));
        }
        return sb.toString();
    }

    private static String csvEscape(String raw) {
        if (raw == null) return "";
        boolean needQuote = raw.indexOf(',') >= 0 || raw.indexOf('"') >= 0 || raw.indexOf('\n') >= 0 || raw.indexOf('\r') >= 0;
        if (!needQuote) return raw;
        return "\"" + raw.replace("\"", "\"\"") + "\"";
    }

    private static String buildDownloadHeader(String filename) {
        String safe = filename == null ? "download.zip" : filename.replace("\"", "");
        String encoded;
        try {
            encoded = URLEncoder.encode(safe, StandardCharsets.UTF_8.name()).replace("+", "%20");
        } catch (java.io.UnsupportedEncodingException e) {
            throw new IllegalStateException("UTF-8 not supported", e);
        }
        return "attachment; filename=\"" + safe + "\"; filename*=UTF-8''" + encoded;
    }
}

