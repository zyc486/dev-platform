package com.zhaoyichi.devplatformbackend.vo;

import lombok.Data;

@Data
public class HomeRecentPostVO {
    private Integer id;
    private String title;
    private String content;
    private String createTime;
    private String username;
}
