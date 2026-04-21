package com.zhaoyichi.devplatformbackend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("community_attachment")
public class CommunityAttachment {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long communityId;
    private Long postId;
    private Long userId;
    private String originalName;
    private String storagePath;
    private String contentType;
    private Long sizeBytes;
    private LocalDateTime createTime;
}

