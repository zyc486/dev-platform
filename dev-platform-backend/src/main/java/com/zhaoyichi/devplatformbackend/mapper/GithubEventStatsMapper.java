package com.zhaoyichi.devplatformbackend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhaoyichi.devplatformbackend.entity.GithubEventStats;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface GithubEventStatsMapper extends BaseMapper<GithubEventStats> {
}
