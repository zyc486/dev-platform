package com.zhaoyichi.devplatformbackend.controller;

import com.zhaoyichi.devplatformbackend.common.Result;
import com.zhaoyichi.devplatformbackend.entity.User;
import com.zhaoyichi.devplatformbackend.service.UserFollowService;
import com.zhaoyichi.devplatformbackend.utils.AuthHelper;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/follow")
public class UserFollowController {
    private final UserFollowService userFollowService;

    public UserFollowController(UserFollowService userFollowService) {
        this.userFollowService = userFollowService;
    }

    @PostMapping("/operate")
    public Result<Void> follow(@RequestParam Long followUserId, HttpServletRequest request) {
        Result<Void> auth = AuthHelper.requireLogin(request);
        if (auth != null) {
            return auth;
        }
        Long userId = AuthHelper.currentUserId(request);
        if (userId.equals(followUserId)) {
            return Result.error("不能关注自己");
        }
        return userFollowService.follow(userId, followUserId)
                ? Result.<Void>successMsg("操作成功")
                : Result.<Void>error("操作失败");
    }

    @GetMapping("/my")
    public Result<List<User>> myFollow(HttpServletRequest request) {
        Result<List<User>> auth = AuthHelper.requireLogin(request);
        if (auth != null) {
            return auth;
        }
        return Result.success(userFollowService.myFollow(AuthHelper.currentUserId(request)));
    }

    @GetMapping("/fans")
    public Result<List<User>> myFans(HttpServletRequest request) {
        Result<List<User>> auth = AuthHelper.requireLogin(request);
        if (auth != null) {
            return auth;
        }
        return Result.success(userFollowService.myFans(AuthHelper.currentUserId(request)));
    }

    @GetMapping("/status")
    public Result<Map<String, Boolean>> status(@RequestParam Long followUserId, HttpServletRequest request) {
        Result<Map<String, Boolean>> auth = AuthHelper.requireLogin(request);
        if (auth != null) {
            return auth;
        }
        return Result.success(java.util.Collections.singletonMap(
                "following",
                userFollowService.isFollowing(AuthHelper.currentUserId(request), followUserId)
        ));
    }
}