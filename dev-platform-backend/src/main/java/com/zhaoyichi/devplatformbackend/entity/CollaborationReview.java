package com.zhaoyichi.devplatformbackend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 协作完成后的互评记录（1～5 星 + 可选文字评价）。
 * 与历史表 {@code collab_rating} 并存；新业务能力以本表为准。
 */
@Data
@TableName("collaboration_review")
public class CollaborationReview {
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 对应 {@link Collaboration#getId()} */
    private Integer collaborationId;
    private Long fromUserId;
    private Long toUserId;
    /** 1～5 星 */
    private Integer rating;
    private String comment;
    private LocalDateTime createTime;
}
