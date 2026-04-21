package com.zhaoyichi.devplatformbackend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhaoyichi.devplatformbackend.entity.AuditLog;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AuditLogMapper extends BaseMapper<AuditLog> {}

