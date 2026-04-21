package com.zhaoyichi.devplatformbackend.vo;

import lombok.Data;

@Data
public class AdminReportVO {
    private Integer id;
    private Integer postId;
    private String target;
    private Integer authorId;
    private String type;
    private String reporter;
    private String reportTime;
    private String status;
}
