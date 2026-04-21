package com.zhaoyichi.devplatformbackend.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("scene_weight_config")
public class SceneWeightConfig {

    @TableId(type = IdType.AUTO)
    private Integer id;

    private String sceneName;           // 综合 / 核心开发者 / 辅助贡献

    private BigDecimal stabilityWeight;
    private BigDecimal prQualityWeight;
    private BigDecimal collaborationWeight;
    private BigDecimal complianceWeight;

    private Integer isDefault;          // 1=默认场景
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}