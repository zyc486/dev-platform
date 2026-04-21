package com.zhaoyichi.devplatformbackend.vo;

import lombok.Data;

@Data
public class AdminDashboardVO {
    private Integer userCount;
    private Integer postCount;
    private Integer feedbackCount;
    private Integer pendingReportCount;
    private Integer pendingFeedbackCount;
}
