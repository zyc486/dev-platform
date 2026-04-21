package com.zhaoyichi.devplatformbackend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhaoyichi.devplatformbackend.entity.User;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserMapper extends BaseMapper<User> {}