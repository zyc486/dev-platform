package com.zhaoyichi.devplatformbackend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhaoyichi.devplatformbackend.entity.CollaborationReview;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Map;

@Mapper
public interface CollaborationReviewMapper extends BaseMapper<CollaborationReview> {

    /**
     * 统计某用户作为被评价方收到的星级均值与条数，用于「协作信誉」维度加权。
     */
    @Select("SELECT COALESCE(AVG(rating), 0) AS avgRating, COUNT(*) AS cnt FROM collaboration_review WHERE to_user_id = #{uid}")
    Map<String, Object> aggregateRatingForUser(@Param("uid") Long uid);
}
