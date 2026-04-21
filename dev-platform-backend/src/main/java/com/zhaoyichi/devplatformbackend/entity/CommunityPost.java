package com.zhaoyichi.devplatformbackend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("community_post")
public class CommunityPost {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long communityId;
    private Long userId;
    private String type;
    private String title;
    private String content;
    /** 置顶：0/1 */
    private Integer isSticky;
    /** 精华：0/1 */
    private Integer isEssence;
    /** 分类：discussion/share/question/resource */
    private String category;
    private LocalDateTime createTime;

    @TableField(exist = false)
    private String authorUsername;
}
