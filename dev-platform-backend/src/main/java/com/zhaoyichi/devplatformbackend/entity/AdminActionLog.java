package com.zhaoyichi.devplatformbackend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("admin_action_log")
public class AdminActionLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long adminUserId;
    private String adminUsername;
    private String actionType;
    private String targetType;
    private Long targetId;
    private String detail;
    private LocalDateTime createTime;
}
