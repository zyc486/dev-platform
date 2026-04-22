package com.zhaoyichi.devplatformbackend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("chat_join_request")
public class ChatJoinRequest {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long roomId;
    private Long applicantUserId;
    private String status; // pending/approved/rejected

    private String reason;
    private Long handledBy;
    private String handleReason;

    private LocalDateTime createTime;
    private LocalDateTime handleTime;
}

