package com.zhaoyichi.devplatformbackend.service.collab;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.zhaoyichi.devplatformbackend.entity.Issue;
import com.zhaoyichi.devplatformbackend.entity.IssueAttachment;
import com.zhaoyichi.devplatformbackend.mapper.IssueAttachmentMapper;
import com.zhaoyichi.devplatformbackend.mapper.IssueMapper;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.Objects;

@Service
public class CollabAttachmentService {

    private static final long MAX_BYTES = 20L * 1024 * 1024; // 20MB

    private final IssueMapper issueMapper;
    private final IssueAttachmentMapper issueAttachmentMapper;
    private final CollabProjectService collabProjectService;
    private final CollabActivityService collabActivityService;

    public CollabAttachmentService(IssueMapper issueMapper,
                                  IssueAttachmentMapper issueAttachmentMapper,
                                  CollabProjectService collabProjectService,
                                  CollabActivityService collabActivityService) {
        this.issueMapper = issueMapper;
        this.issueAttachmentMapper = issueAttachmentMapper;
        this.collabProjectService = collabProjectService;
        this.collabActivityService = collabActivityService;
    }

    public IssueAttachment upload(Long issueId, Long userId, MultipartFile file) {
        if (file == null || file.isEmpty()) throw new IllegalStateException("文件为空");
        if (file.getSize() > MAX_BYTES) throw new IllegalStateException("文件过大，限制 20MB");

        Issue issue = issueMapper.selectById(issueId);
        if (issue == null) throw new IllegalStateException("任务不存在");
        collabProjectService.requireMember(issue.getProjectId(), userId);

        String originalName = sanitizeFilename(file.getOriginalFilename());
        String ext = "";
        int dot = originalName.lastIndexOf('.');
        if (dot > 0 && dot < originalName.length() - 1) ext = originalName.substring(dot);

        String basename = "f_" + System.currentTimeMillis() + "_" + Math.abs(originalName.hashCode());
        String storedName = basename + ext;
        Path dir = Paths.get(System.getProperty("user.dir"), "uploads", "projects",
                String.valueOf(issue.getProjectId()), "issues", String.valueOf(issueId));
        try {
            Files.createDirectories(dir);
        } catch (Exception e) {
            throw new IllegalStateException("创建上传目录失败");
        }
        Path target = dir.resolve(storedName);

        String sha256 = null;
        try (InputStream in = file.getInputStream()) {
            sha256 = sha256Hex(in);
        } catch (Exception ignore) {}

        try {
            file.transferTo(Objects.requireNonNull(target.toFile(), "targetFile"));
        } catch (Exception e) {
            throw new IllegalStateException("保存文件失败");
        }

        IssueAttachment a = new IssueAttachment();
        a.setIssueId(issueId);
        a.setProjectId(issue.getProjectId());
        a.setUserId(userId);
        a.setOriginalName(originalName);
        a.setStoragePath(toRelativeUploadsPath(target));
        a.setContentType(file.getContentType());
        a.setSizeBytes(file.getSize());
        a.setSha256(sha256);
        issueAttachmentMapper.insert(a);

        collabActivityService.add(issue.getProjectId(), userId, "issue_attachment", "attachment", a.getId(),
                "上传附件：" + originalName, null);
        return a;
    }

    public java.util.List<IssueAttachment> listByIssue(Long issueId, Long userId) {
        Issue issue = issueMapper.selectById(issueId);
        if (issue == null) throw new IllegalStateException("任务不存在");
        collabProjectService.requireMember(issue.getProjectId(), userId);
        return issueAttachmentMapper.selectList(new QueryWrapper<IssueAttachment>().eq("issue_id", issueId).orderByDesc("created_at"));
    }

    private static String sanitizeFilename(String name) {
        if (name == null || name.trim().isEmpty()) return "file";
        String s = name.trim().replace("\\", "_").replace("/", "_");
        // 去掉控制字符
        s = s.replaceAll("[\\p{Cntrl}]", "_");
        if (s.length() > 200) s = s.substring(s.length() - 200);
        return s;
    }

    private static String toRelativeUploadsPath(Path absolutePath) {
        Path root = Paths.get(System.getProperty("user.dir"), "uploads");
        Path rel = root.relativize(absolutePath);
        // URL/静态映射使用 '/'
        return rel.toString().replace("\\", "/");
    }

    private static String sha256Hex(InputStream in) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) {
            md.update(buf, 0, n);
        }
        byte[] d = md.digest();
        return bytesToHex(d);
    }

    private static String bytesToHex(byte[] bytes) {
        if (bytes == null || bytes.length == 0) return "";
        char[] hex = "0123456789abcdef".toCharArray();
        char[] out = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            int v = bytes[i] & 0xFF;
            out[i * 2] = hex[v >>> 4];
            out[i * 2 + 1] = hex[v & 0x0F];
        }
        return new String(out);
    }
}

