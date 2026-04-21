package com.zhaoyichi.devplatformbackend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhaoyichi.devplatformbackend.entity.QueryLog;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface QueryLogMapper extends BaseMapper<QueryLog> {}