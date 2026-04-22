package com.zhaoyichi.devplatformbackend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("chat_invite")
public class ChatInvite {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long roomId;
    private Long inviterUserId;
    private Long inviteeUserId;
    private String status; // pending/accepted/rejected

    private LocalDateTime createTime;
    private LocalDateTime handleTime;
}

