package com.zhaoyichi.devplatformbackend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("community_apply")
public class CommunityApply {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long communityId;
    private Long userId;
    private String applyReason;
    private String status;
    private LocalDateTime createTime;
    private LocalDateTime reviewTime;
}
