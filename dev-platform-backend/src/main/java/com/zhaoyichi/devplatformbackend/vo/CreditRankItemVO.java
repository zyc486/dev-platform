package com.zhaoyichi.devplatformbackend.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreditRankItemVO {
    private String githubUsername;
    private String nickname;
    private String avatar;
    private String techTags;
    private Integer totalScore;
    private String level;
    private String scene;
    private Integer stability;
    private Integer prQuality;
    private Integer collaboration;
    private Integer compliance;
}
