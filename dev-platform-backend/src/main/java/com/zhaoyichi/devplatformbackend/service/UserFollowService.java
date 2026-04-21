package com.zhaoyichi.devplatformbackend.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.zhaoyichi.devplatformbackend.entity.User;
import com.zhaoyichi.devplatformbackend.entity.UserFollow;
import com.zhaoyichi.devplatformbackend.mapper.UserFollowMapper;
import com.zhaoyichi.devplatformbackend.mapper.UserMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class UserFollowService {
    private final UserFollowMapper followMapper;
    private final UserMapper userMapper;
    private final MessageNoticeService messageNoticeService;

    public UserFollowService(UserFollowMapper followMapper, UserMapper userMapper, MessageNoticeService messageNoticeService) {
        this.followMapper = followMapper;
        this.userMapper = userMapper;
        this.messageNoticeService = messageNoticeService;
    }

    public boolean follow(Long userId, Long followUserId) {
        UserFollow exist = followMapper.selectOne(new QueryWrapper<UserFollow>()
                .eq("user_id", userId)
                .eq("follow_user_id", followUserId));
        if (exist != null) {
            followMapper.deleteById(exist.getId());
            return true;
        } else {
            UserFollow follow = new UserFollow();
            follow.setUserId(userId);
            follow.setFollowUserId(followUserId);
            follow.setCreateTime(LocalDateTime.now());
            boolean inserted = followMapper.insert(follow) > 0;
            if (inserted) {
                User fromUser = userMapper.selectById(userId);
                messageNoticeService.createNotice(
                        followUserId,
                        "follow",
                        "新的关注",
                        (fromUser == null ? "有用户" : fromUser.getUsername()) + " 关注了你",
                        userId
                );
            }
            return inserted;
        }
    }

    public List<User> myFollow(Long userId) {
        List<UserFollow> followList = followMapper.selectList(new QueryWrapper<UserFollow>()
                .eq("user_id", userId));
        List<Long> followIds = followList.stream().map(UserFollow::getFollowUserId).collect(Collectors.toList());
        if (followIds.isEmpty()) return new ArrayList<>();
        List<User> users = userMapper.selectBatchIds(followIds);
        users.forEach(user -> user.setPassword(null));
        return users;
    }

    public List<User> myFans(Long userId) {
        List<UserFollow> fanList = followMapper.selectList(new QueryWrapper<UserFollow>()
                .eq("follow_user_id", userId));
        List<Long> fanIds = fanList.stream().map(UserFollow::getUserId).collect(Collectors.toList());
        if (fanIds.isEmpty()) return new ArrayList<>();
        List<User> users = userMapper.selectBatchIds(fanIds);
        users.forEach(user -> user.setPassword(null));
        return users;
    }

    public boolean isFollowing(Long userId, Long followUserId) {
        return followMapper.selectCount(new QueryWrapper<UserFollow>()
                .eq("user_id", userId)
                .eq("follow_user_id", followUserId)) > 0;
    }
}