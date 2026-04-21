package com.zhaoyichi.devplatformbackend.controller;

import com.zhaoyichi.devplatformbackend.common.Result;
import com.zhaoyichi.devplatformbackend.entity.DmMessage;
import com.zhaoyichi.devplatformbackend.service.DmService;
import com.zhaoyichi.devplatformbackend.utils.AuthHelper;
import lombok.Data;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/dm")
@CrossOrigin
public class DmController {
    private final DmService dmService;

    public DmController(DmService dmService) {
        this.dmService = dmService;
    }

    @PostMapping("/send")
    public Result<DmMessage> send(@RequestBody SendBody body, HttpServletRequest request) {
        Result<DmMessage> auth = AuthHelper.requireLogin(request);
        if (auth != null) return auth;
        Long uid = AuthHelper.currentUserId(request);
        if (body == null || body.getToUserId() == null) {
            return Result.error("toUserId 不能为空");
        }
        try {
            DmMessage msg = dmService.send(uid, body.getToUserId(), body.getContent());
            return Result.success(msg);
        } catch (IllegalArgumentException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            return Result.systemError("发送失败，请稍后重试");
        }
    }

    @GetMapping("/list")
    public Result<List<DmMessage>> list(@RequestParam Long withUserId,
                                        @RequestParam(required = false, defaultValue = "200") int limit,
                                        HttpServletRequest request) {
        Result<List<DmMessage>> auth = AuthHelper.requireLogin(request);
        if (auth != null) return auth;
        Long uid = AuthHelper.currentUserId(request);
        try {
            return Result.success(dmService.listWith(uid, withUserId, limit));
        } catch (Exception e) {
            return Result.systemError("加载失败");
        }
    }

    @GetMapping("/conversations")
    public Result<List<Map<String, Object>>> conversations(@RequestParam(required = false, defaultValue = "30") int limit,
                                                          HttpServletRequest request) {
        Result<List<Map<String, Object>>> auth = AuthHelper.requireLogin(request);
        if (auth != null) return auth;
        Long uid = AuthHelper.currentUserId(request);
        try {
            return Result.success(dmService.conversations(uid, limit));
        } catch (Exception e) {
            return Result.systemError("加载失败");
        }
    }

    @Data
    public static class SendBody {
        private Long toUserId;
        private String content;
    }
}

