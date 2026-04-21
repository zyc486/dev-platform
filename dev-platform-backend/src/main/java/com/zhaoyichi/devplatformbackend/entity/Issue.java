package com.zhaoyichi.devplatformbackend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("issue")
public class Issue {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long projectId;
    private String title;
    private String description;

    private String status;   // todo/doing/done
    private String priority; // low/medium/high/urgent
    private String labelsJson;

    private Long assigneeUserId;
    private LocalDateTime dueAt;

    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

