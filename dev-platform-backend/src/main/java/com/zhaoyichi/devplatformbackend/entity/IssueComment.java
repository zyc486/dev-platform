package com.zhaoyichi.devplatformbackend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("issue_comment")
public class IssueComment {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long issueId;
    private Long projectId;
    private Long userId;
    private String content;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

