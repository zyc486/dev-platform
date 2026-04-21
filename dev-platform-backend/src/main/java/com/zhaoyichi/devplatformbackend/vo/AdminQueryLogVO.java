package com.zhaoyichi.devplatformbackend.vo;

import lombok.Data;

@Data
public class AdminQueryLogVO {
    private Integer id;
    private String githubUsername;
    private String scene;
    private Integer userId;
    private String queryTime;
    private Integer responseTime;
    private String status;
    private Integer totalScore;
}
