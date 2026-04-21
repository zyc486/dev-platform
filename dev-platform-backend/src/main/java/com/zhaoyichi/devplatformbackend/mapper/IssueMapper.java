package com.zhaoyichi.devplatformbackend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhaoyichi.devplatformbackend.entity.Issue;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface IssueMapper extends BaseMapper<Issue> {}

