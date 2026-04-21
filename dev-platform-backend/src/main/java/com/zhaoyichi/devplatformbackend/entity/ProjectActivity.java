package com.zhaoyichi.devplatformbackend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("project_activity")
public class ProjectActivity {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long projectId;
    private Long actorUserId;
    private String type;

    private String refType;
    private Long refId;

    private String summary;
    private String detail;

    private LocalDateTime createdAt;
}

