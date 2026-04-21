package com.zhaoyichi.devplatformbackend.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("query_log")
public class QueryLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String githubUsername;
    private String scene;
    private Integer userId;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime queryTime;
    private Integer responseTime;
    private String status;
    private Integer totalScore;

    private Integer isFavorite;
}