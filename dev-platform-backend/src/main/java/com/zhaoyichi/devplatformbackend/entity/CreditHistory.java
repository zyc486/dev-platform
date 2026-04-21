package com.zhaoyichi.devplatformbackend.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDate;

@Data
@TableName("credit_history")
public class CreditHistory {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Integer userId;
    private Integer totalScore;
    private Integer stability;
    private Integer prQuality;
    private Integer collaboration;
    private Integer compliance;
    private String scene;
    private LocalDate recordDate;
    /** 算法版本：v2=基于 13 篇文献的客观化公式 */
    private String algoVersion;
}