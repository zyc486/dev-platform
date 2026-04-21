package com.zhaoyichi.devplatformbackend.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.zhaoyichi.devplatformbackend.common.Result;
import com.zhaoyichi.devplatformbackend.entity.User;
import com.zhaoyichi.devplatformbackend.mapper.UserMapper;
import com.zhaoyichi.devplatformbackend.service.UserService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/test")
public class TestController {

    private final UserMapper userMapper;
    private final UserService userService;

    public TestController(UserMapper userMapper, UserService userService) {
        this.userMapper = userMapper;
        this.userService = userService;
    }

    @GetMapping("/hello")
    public Result<String> hello() {
        return Result.success("欢迎使用开发者协作平台后端API！");
    }

    @GetMapping("/simple-test")
    public Result<String> simpleTest() {
        System.out.println("收到简单测试请求");
        return Result.success("测试成功！");
    }

    @PostMapping("/test-post")
    public Result<String> testPost(@RequestParam String username, @RequestParam String password) {
        System.out.println("收到 POST 请求：username=" + username + ", password=" + password);
        return Result.success("POST 测试成功，收到数据：" + username);
    }

    @PostMapping("/test-request-body")
    public Result<String> testRequestBody(@RequestBody Map<String, String> data) {
        System.out.println("收到 RequestBody 请求：" + data);
        return Result.success("RequestBody 测试成功，收到数据：" + data.get("username"));
    }

    @PostMapping("/test-user-login")
    public Result<?> testUserLogin(@RequestBody Map<String, String> data) {
        System.out.println("收到测试登录请求：" + data);
        try {
            String username = data.get("username");
            String password = data.get("password");
            System.out.println("测试登录：username=" + username + ", password=" + password);
            
            // 测试数据库连接
            System.out.println("测试数据库连接...");
            List<User> users = userMapper.selectList(null);
            System.out.println("数据库连接成功，用户数量：" + users.size());
            
            // 测试登录
            System.out.println("调用 userService.login...");
            Result<String> result = userService.login(username, password);
            System.out.println("登录结果：" + result);
            return result;
        } catch (Exception e) {
            System.out.println("测试登录异常：" + e.getMessage());
            e.printStackTrace();
            return Result.error("测试登录失败：" + e.getMessage());
        }
    }

    @GetMapping("/info")
    public Result<Map<String, Object>> info() {
        Map<String, Object> info = new HashMap<>();
        info.put("projectName", "开发者协作平台");
        info.put("version", "1.0.0");
        info.put("status", "运行中");
        info.put("apiBaseUrl", "/api");
        info.put("availableEndpoints", new String[]{
            "/api/user/login - 用户登录",
            "/api/user/register - 用户注册",
            "/api/user/rank - 信用排名",
            "/api/collaboration/list - 协作项目列表"
        });
        return Result.success(info);
    }

    @GetMapping("/users")
    public Result<List<User>> getUsers() {
        List<User> users = userMapper.selectList(null);
        // 脱敏处理
        for (User user : users) {
            user.setPassword(null);
        }
        return Result.success(users);
    }

    @PostMapping("/test-login")
    public Result<?> testLogin(@RequestParam String username, @RequestParam String password) {
        // 直接调用登录逻辑
        Result<String> result = userService.login(username, password);
        
        if (String.valueOf(result.getCode()).equals("200")) {
            // 增强查询：支持用户名/手机号查询
            QueryWrapper<User> wrapper = new QueryWrapper<>();
            wrapper.eq("username", username)
                    .or()
                    .eq("phone", username);

            User dbUser = userMapper.selectOne(wrapper);

            if (dbUser != null) {
                dbUser.setPassword(null); // 脱敏：绝对不能把密码传给前端

                Map<String, Object> returnData = new HashMap<>();
                returnData.put("token", result.getData()); // 放入原始 token
                returnData.put("user", dbUser);            // 放入带有 id 的完整用户对象

                return Result.success(returnData);
            }
        }

        // 如果登录失败，原封不动地返回 userService 原本的真实错误信息！
        return result;
    }
}
