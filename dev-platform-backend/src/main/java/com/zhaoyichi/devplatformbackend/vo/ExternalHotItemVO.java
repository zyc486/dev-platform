package com.zhaoyichi.devplatformbackend.vo;

import lombok.Data;

@Data
public class ExternalHotItemVO {
    private String source;
    private String itemKey;
    private String title;
    private String excerpt;
    private String linkUrl;
    private String metricLabel;
    private String metricValue;
    private Integer rankNo;
    private String fetchTime;
}
