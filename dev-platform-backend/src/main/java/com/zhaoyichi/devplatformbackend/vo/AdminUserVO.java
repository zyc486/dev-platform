package com.zhaoyichi.devplatformbackend.vo;

import lombok.Data;

@Data
public class AdminUserVO {
    private Integer id;
    private String username;
    private String githubUsername;
    private String level;
    private Integer score;
    private String registerTime;
    private String status;
    private String role;
}
