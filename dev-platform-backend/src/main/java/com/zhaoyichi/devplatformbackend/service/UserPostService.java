package com.zhaoyichi.devplatformbackend.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhaoyichi.devplatformbackend.entity.UserPost;
import com.zhaoyichi.devplatformbackend.mapper.UserPostMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;

@Service
public class UserPostService {
    @Autowired
    private UserPostMapper userPostMapper;

    // 发布动态
    public boolean publish(UserPost post) {
        post.setCreateTime(LocalDateTime.now());
        return userPostMapper.insert(post) > 0;
    }

    // 动态列表（分页+标签筛选）
    public IPage<UserPost> list(int page, int size, String techTag) {
        Page<UserPost> pageParam = new Page<>(page, size);
        QueryWrapper<UserPost> wrapper = new QueryWrapper<UserPost>().orderByDesc("create_time");
        if (techTag != null && !techTag.isEmpty()) {
            wrapper.like("tech_tag", techTag);
        }
        return userPostMapper.selectPage(pageParam, wrapper);
    }
}