package com.zhaoyichi.devplatformbackend.vo;

import lombok.Data;

@Data
public class HomeRecommendCollabVO {
    private Integer id;
    private String title;
    private String content;
    private Integer minCredit;
    private String creatorUsername;
}
