package com.zhaoyichi.devplatformbackend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;
import java.time.LocalDateTime;

@Data
public class UserPost {
    @TableId(type = IdType.AUTO)
    private Integer id;
    private Integer userId;
    private String title;
    private String content;
    private Integer likeCount;
    private Integer collectCount;
    private LocalDateTime createTime;
    private String status;
    /** 逗号分隔的标签（如 "Java,SpringBoot,AI"），用于广场标签筛选 */
    private String tags;
}