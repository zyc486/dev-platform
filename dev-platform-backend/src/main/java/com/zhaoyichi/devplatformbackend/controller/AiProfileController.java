package com.zhaoyichi.devplatformbackend.controller;

import com.zhaoyichi.devplatformbackend.common.Result;
import com.zhaoyichi.devplatformbackend.service.CreditRefreshThrottle;
import com.zhaoyichi.devplatformbackend.service.ai.AiProfileService;
import com.zhaoyichi.devplatformbackend.utils.AuthHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.Map;

@RestController
@RequestMapping("/api/credit")
@CrossOrigin(origins = "*")
public class AiProfileController {

    @Autowired
    private AiProfileService aiProfileService;
    @Autowired
    private CreditRefreshThrottle creditRefreshThrottle;

    @GetMapping("/aiProfile")
    public Result<Map<String, Object>> aiProfile(@RequestParam String githubUsername,
                                                 @RequestParam(defaultValue = "综合") String scene) {
        Map<String, Object> data = aiProfileService.getProfile(githubUsername, scene);
        return data == null ? Result.error("AI 画像不可用：参数不合法") : Result.success(data);
    }

    /**
     * 手动刷新 AI 画像：需要登录，且与信用刷新同一频控（10 分钟一次）。\n
     */
    @PostMapping("/aiProfile/refresh")
    public Result<Map<String, Object>> refresh(@RequestBody Map<String, String> req, HttpServletRequest request) {
        Result<Map<String, Object>> auth = AuthHelper.requireLogin(request);
        if (auth != null) return auth;
        Long userId = AuthHelper.currentUserId(request);
        if (!creditRefreshThrottle.tryAcquire(userId)) {
            long nextAt = creditRefreshThrottle.nextAllowedAtMillis(userId);
            return Result.error("刷新过于频繁，请稍后再试（下次可刷新时间戳：" + nextAt + "）");
        }
        String gh = req == null ? null : req.get("githubUsername");
        String scene = req == null ? "综合" : req.getOrDefault("scene", "综合");
        try {
            Map<String, Object> data = aiProfileService.refreshNow(gh, scene);
            return data == null ? Result.error("刷新失败：AI 未启用或 GitHub 数据不可达") : Result.success(data);
        } catch (Exception e) {
            creditRefreshThrottle.releaseAfterFailure(userId);
            return Result.error("刷新失败：" + e.getMessage());
        }
    }
}

