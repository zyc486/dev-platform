package com.zhaoyichi.devplatformbackend.controller;

import com.zhaoyichi.devplatformbackend.common.Result;
import com.zhaoyichi.devplatformbackend.entity.MessageNotice;
import com.zhaoyichi.devplatformbackend.service.MessageNoticeService;
import com.zhaoyichi.devplatformbackend.utils.AuthHelper;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

@RestController
@RequestMapping("/api/message")
public class MessageController {
    private final MessageNoticeService messageNoticeService;

    public MessageController(MessageNoticeService messageNoticeService) {
        this.messageNoticeService = messageNoticeService;
    }

    @GetMapping("/list")
    public Result<List<MessageNotice>> list(HttpServletRequest request) {
        Result<List<MessageNotice>> auth = AuthHelper.requireLogin(request);
        if (auth != null) {
            return auth;
        }
        return Result.success(messageNoticeService.listByUserId(AuthHelper.currentUserId(request)));
    }

    @PostMapping("/read/{id}")
    public Result<String> markRead(@PathVariable Long id, HttpServletRequest request) {
        Result<String> auth = AuthHelper.requireLogin(request);
        if (auth != null) {
            return auth;
        }
        messageNoticeService.markRead(AuthHelper.currentUserId(request), id);
        return Result.successMsg("已标记为已读");
    }

    @PostMapping("/readAll")
    public Result<String> markAllRead(HttpServletRequest request) {
        Result<String> auth = AuthHelper.requireLogin(request);
        if (auth != null) {
            return auth;
        }
        messageNoticeService.markAllRead(AuthHelper.currentUserId(request));
        return Result.successMsg("已全部标记为已读");
    }

    @DeleteMapping("/delete/{id}")
    public Result<String> delete(@PathVariable Long id, HttpServletRequest request) {
        Result<String> auth = AuthHelper.requireLogin(request);
        if (auth != null) {
            return auth;
        }
        messageNoticeService.deleteOne(AuthHelper.currentUserId(request), id);
        return Result.successMsg("删除成功");
    }

    @DeleteMapping("/clear")
    public Result<String> clear(HttpServletRequest request) {
        Result<String> auth = AuthHelper.requireLogin(request);
        if (auth != null) {
            return auth;
        }
        messageNoticeService.deleteAll(AuthHelper.currentUserId(request));
        return Result.successMsg("消息已清空");
    }
}
