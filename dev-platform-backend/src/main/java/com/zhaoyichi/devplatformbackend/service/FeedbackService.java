package com.zhaoyichi.devplatformbackend.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.zhaoyichi.devplatformbackend.entity.Feedback;
import com.zhaoyichi.devplatformbackend.mapper.FeedbackMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class FeedbackService {
    private final FeedbackMapper feedbackMapper;

    public FeedbackService(FeedbackMapper feedbackMapper) {
        this.feedbackMapper = feedbackMapper;
    }

    public Feedback create(Feedback feedback) {
        feedback.setStatus("pending");
        feedback.setCreateTime(LocalDateTime.now());
        feedbackMapper.insert(feedback);
        return feedback;
    }

    public List<Feedback> listByUser(Long userId) {
        QueryWrapper<Feedback> wrapper = new QueryWrapper<>();
        wrapper.eq("user_id", userId).orderByDesc("create_time");
        return feedbackMapper.selectList(wrapper);
    }

    public List<Feedback> listAll() {
        QueryWrapper<Feedback> wrapper = new QueryWrapper<>();
        wrapper.orderByDesc("create_time");
        return feedbackMapper.selectList(wrapper);
    }

    public Feedback findById(Long id) {
        return feedbackMapper.selectById(id);
    }

    public void update(Feedback feedback) {
        feedbackMapper.updateById(feedback);
    }
}
