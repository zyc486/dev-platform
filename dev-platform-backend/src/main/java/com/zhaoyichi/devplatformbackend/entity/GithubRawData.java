package com.zhaoyichi.devplatformbackend.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("github_raw_data")
public class GithubRawData {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String githubUsername;
    private String dataType;
    private String rawJson;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime fetchTime;
}