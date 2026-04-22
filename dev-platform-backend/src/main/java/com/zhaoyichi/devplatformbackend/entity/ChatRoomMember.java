package com.zhaoyichi.devplatformbackend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("chat_room_member")
public class ChatRoomMember {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long roomId;
    private Long userId;
    private String role;
    private Long lastReadMessageId;
    private LocalDateTime joinTime;
}

