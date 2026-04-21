package com.zhaoyichi.devplatformbackend.vo;

import lombok.Data;

@Data
public class HomeSummaryVO {
    private Integer userCount;
    private Integer postCount;
    private Integer openCollabCount;
    private Integer queryCount7d;
}
