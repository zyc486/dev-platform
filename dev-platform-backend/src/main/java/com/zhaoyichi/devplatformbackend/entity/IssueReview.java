package com.zhaoyichi.devplatformbackend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("issue_review")
public class IssueReview {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long issueId;
    private Long projectId;
    private Long reviewerUserId;
    private Integer rating; // 1-5
    private String comment;

    private LocalDateTime createdAt;
}

