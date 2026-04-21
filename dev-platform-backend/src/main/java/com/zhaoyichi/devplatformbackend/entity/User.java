package com.zhaoyichi.devplatformbackend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.util.Date;

@Data
@TableName("user") // 对应数据库里的 user 表
public class User {

    @TableId(type = IdType.AUTO) // 主键自增
    private Long id;

    private String username;

    private String password;

    private String githubUsername; // MyBatis-Plus 会自动将驼峰命名转为数据库的 github_username

    private String phone;

    private String email;

    private Integer totalScore;

    private String level;

    private Date createTime;

    private String status;

    private String techTags;

    private String role;

    private String avatar;

    private String nickname;

    private String bio;

    private Integer privacyCreditPublic;

    private Integer privacyFeedPublic;

    private Integer privacyAllowMessage;
}