package com.zhaoyichi.devplatformbackend.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("api_call_log")
public class ApiCallLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String githubUsername;
    private String endpoint;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime callTime;
    private Boolean success;
}