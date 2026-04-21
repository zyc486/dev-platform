package com.zhaoyichi.devplatformbackend.vo;

import lombok.Data;

@Data
public class AdminActionLogVO {
    private Integer id;
    private Long adminUserId;
    private String adminUsername;
    private String actionType;
    private String targetType;
    private Long targetId;
    private String detail;
    private String createTime;
}
