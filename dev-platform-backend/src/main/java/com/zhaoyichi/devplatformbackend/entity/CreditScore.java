package com.zhaoyichi.devplatformbackend.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("credit_score")
public class CreditScore {

    @TableId(type = IdType.AUTO)
    private Integer id;

    private Integer userId;
    private String githubUsername;     // 新增
    private String scene;              // 新增：综合/核心开发者/辅助贡献
    private Integer stability;
    private Integer prQuality;
    private Integer collaboration;
    private Integer compliance;
    private Integer totalScore;        // 新增
    private String level;              // 新增：优秀/良好/合格
    /** 算法版本：v2=基于 13 篇文献的客观化公式（T/CESA 标准 + AHP + 多属性合规） */
    private String algoVersion;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}