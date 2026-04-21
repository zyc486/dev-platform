package com.zhaoyichi.devplatformbackend.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("ai_profile_snapshot")
public class AiProfileSnapshot {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String githubUsername;
    private String scene;
    private String algoVersion;

    private String profileVersion;
    private String promptVersion;
    private String model;

    private String dataHash;
    private String summary;

    private String profileJson;
    private String techTagsJson;
    private String topReposJson;
    private String evidenceJson;

    private Integer tokenUsage;
    private BigDecimal costEstimate;

    private String status;
    private String errorMessage;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
    private LocalDateTime expiresAt;
}

