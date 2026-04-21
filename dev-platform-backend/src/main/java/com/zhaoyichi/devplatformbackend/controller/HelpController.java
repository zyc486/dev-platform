package com.zhaoyichi.devplatformbackend.controller;

import com.zhaoyichi.devplatformbackend.common.Result;
import com.zhaoyichi.devplatformbackend.entity.SystemConfig;
import com.zhaoyichi.devplatformbackend.mapper.SystemConfigMapper;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/help")
@CrossOrigin
public class HelpController {

    private final SystemConfigMapper systemConfigMapper;

    public HelpController(SystemConfigMapper systemConfigMapper) {
        this.systemConfigMapper = systemConfigMapper;
    }

    @GetMapping
    public Result<Map<String, String>> getHelp() {
        SystemConfig config = systemConfigMapper.selectById("help_markdown");
        Map<String, String> result = new HashMap<>();
        result.put("content", config != null ? config.getConfigValue() : "暂无帮助内容");
        return Result.success(result);
    }
}
