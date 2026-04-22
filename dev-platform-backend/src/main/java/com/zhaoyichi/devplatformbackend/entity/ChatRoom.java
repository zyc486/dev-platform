package com.zhaoyichi.devplatformbackend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("chat_room")
public class ChatRoom {
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 对外展示的群聊号（纯数字，唯一）。
     */
    private String chatNo;

    private String name;
    private Long createdBy;
    private Integer collabProjectId;
    private LocalDateTime createTime;
}

