package com.zhaoyichi.devplatformbackend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("collab_apply")
public class CollabApply {
    @TableId(type = IdType.AUTO)
    private Integer id;

    private Integer projectId;  // 对应 collaboration 表的 id
    private Integer userId;     // 核心：申请人的 user_id
    private String status;      // 状态：pending/approved/rejected
    private LocalDateTime applyTime;
    private LocalDateTime auditTime;
    private String auditReason;
}