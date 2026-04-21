package com.zhaoyichi.devplatformbackend.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 等级阈值快照表。
 *
 * <p>文献 C（《软件学报》2018，吴哲夫等）的 20/80 法则在样本充足时提供了"Top 20% 为核心、Bottom 20% 为风险"
 * 的客观依据；但分位数估计需足够样本量才稳定，本系统设置 N≥30 时才启用 P20/P50/P80，
 * 否则回退到固定阈值 85/70/60 以避免"动态化反而主观"的悖论。</p>
 */
@Data
@TableName("credit_threshold")
public class CreditThreshold {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String scene;
    private String algoVersion;
    private Integer sampleSize;
    private Integer p20;
    private Integer p50;
    private Integer p80;
    /** 1=样本不足（N<30），已回退到静态 85/70/60 */
    private Integer fallbackUsed;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime computeTime;
}
