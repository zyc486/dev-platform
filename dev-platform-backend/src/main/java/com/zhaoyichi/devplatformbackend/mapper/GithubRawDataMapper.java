package com.zhaoyichi.devplatformbackend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhaoyichi.devplatformbackend.entity.GithubRawData;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface GithubRawDataMapper extends BaseMapper<GithubRawData> {}