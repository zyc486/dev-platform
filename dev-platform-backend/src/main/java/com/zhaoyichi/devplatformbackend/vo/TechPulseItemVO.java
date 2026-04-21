package com.zhaoyichi.devplatformbackend.vo;

import lombok.Data;

/**
 * 首页「技术脉搏」条目：来自第三方 RSS / 公开 API，仅摘要与原文链接。
 */
@Data
public class TechPulseItemVO {
    private String title;
    /** 原文链接 */
    private String url;
    /** 来源站点名称 */
    private String source;
    /** 纯文本摘要 */
    private String summary;
    /** 展示用发布时间 */
    private String publishedAt;
}
