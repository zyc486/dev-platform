package com.zhaoyichi.devplatformbackend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("issue_attachment")
public class IssueAttachment {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long issueId;
    private Long projectId;
    private Long userId;

    private String originalName;
    private String storagePath;
    private String contentType;
    private Long sizeBytes;
    private String sha256;

    private LocalDateTime createdAt;
}

