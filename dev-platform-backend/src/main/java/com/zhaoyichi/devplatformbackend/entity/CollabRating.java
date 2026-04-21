package com.zhaoyichi.devplatformbackend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("collab_rating")
public class CollabRating {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Integer projectId;
    private Long fromUserId;
    private Long toUserId;
    private Integer score;
    private String comment;
    private LocalDateTime createTime;
}
