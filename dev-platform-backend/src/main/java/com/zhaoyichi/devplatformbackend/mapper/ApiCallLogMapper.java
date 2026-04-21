package com.zhaoyichi.devplatformbackend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhaoyichi.devplatformbackend.entity.ApiCallLog;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ApiCallLogMapper extends BaseMapper<ApiCallLog> {}