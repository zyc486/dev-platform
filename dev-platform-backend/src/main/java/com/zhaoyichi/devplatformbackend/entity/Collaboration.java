package com.zhaoyichi.devplatformbackend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("collaboration")
public class Collaboration {
    @TableId(type = IdType.AUTO)
    private Integer id;

    private Integer userId;     // 核心：发布人的 user_id
    private String title;       // 标题
    private String content;     // 详情内容
    private Integer minCredit;  // 最低信用分要求
    /** 状态：pending 招募中；in_progress 进行中；completed 已完成；cancelled 已取消 */
    private String status;
    private LocalDateTime createTime;
    private LocalDateTime finishTime;
}