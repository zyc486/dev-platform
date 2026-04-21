package com.zhaoyichi.devplatformbackend.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.zhaoyichi.devplatformbackend.common.Result;
import com.zhaoyichi.devplatformbackend.entity.User;
import com.zhaoyichi.devplatformbackend.mapper.UserMapper;
import com.zhaoyichi.devplatformbackend.utils.JwtUtils;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;
import java.util.Objects;

@Service
public class UserService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserMapper userMapper, PasswordEncoder passwordEncoder) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * 注册逻辑 (终极版：接收完整的用户信息，包含手机号等)
     */
    public Result<String> register(User user) {
        if (user == null) {
            return Result.error("参数错误");
        }
        if (user.getUsername() == null || user.getUsername().trim().isEmpty()) {
            return Result.error("用户名不能为空");
        }
        if (user.getPassword() == null || user.getPassword().trim().isEmpty()) {
            return Result.error("密码不能为空");
        }
        // phone/email 二选一（可都填）
        String phone = user.getPhone() == null ? "" : user.getPhone().trim();
        String email = user.getEmail() == null ? "" : user.getEmail().trim();
        if (phone.isEmpty() && email.isEmpty()) {
            return Result.error("请至少填写手机号或邮箱");
        }
        user.setPhone(phone.isEmpty() ? null : phone);
        user.setEmail(email.isEmpty() ? null : email);
        user.setUsername(user.getUsername().trim());

        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("username", user.getUsername());
        if (userMapper.selectCount(queryWrapper) > 0) {
            return Result.error("用户名已被注册，请换一个");
        }

        if (user.getPhone() != null && !user.getPhone().isEmpty()) {
            QueryWrapper<User> phoneQuery = new QueryWrapper<>();
            phoneQuery.eq("phone", user.getPhone());
            if (userMapper.selectCount(phoneQuery) > 0) {
                return Result.error("该手机号已被绑定");
            }
        }

        if (user.getEmail() != null && !user.getEmail().isEmpty()) {
            QueryWrapper<User> emailQuery = new QueryWrapper<>();
            emailQuery.eq("email", user.getEmail());
            if (userMapper.selectCount(emailQuery) > 0) {
                return Result.error("该邮箱已被绑定");
            }
        }

        user.setPassword(passwordEncoder.encode(user.getPassword()));
        user.setCreateTime(new Date());
        user.setStatus("normal");
        user.setRole("admin".equalsIgnoreCase(user.getUsername()) ? "admin" : "user");

        userMapper.insert(user);
        return Result.successMsg("注册成功");
    }

    /**
     * 登录逻辑 (终极版：支持用户名/手机号/邮箱任意一种方式登录！)
     */
    public Result<String> login(String username, String password) {
        User user = findByLoginAccount(username);

        if (user == null) {
            return Result.error("用户不存在");
        }

        if ("frozen".equalsIgnoreCase(user.getStatus())) {
            return Result.error("账号已被冻结，请联系管理员");
        }

        if (!passwordEncoder.matches(password, user.getPassword())) {
            return Result.error("密码错误");
        }

        String token = JwtUtils.generateToken(user.getId(), user.getUsername(), user.getRole());

        return Result.success(token);
    }

    public User findByLoginAccount(String loginAccount) {
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("username", loginAccount)
                .or().eq("phone", loginAccount)
                .or().eq("email", loginAccount);
        return userMapper.selectOne(queryWrapper);
    }

    public User findById(Long userId) {
        return userId == null ? null : userMapper.selectById(userId);
    }

    public User sanitize(User user) {
        if (user == null) {
            return null;
        }
        user.setPassword(null);
        return user;
    }

    public Result<String> updateTags(Long userId, String techTags) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            return Result.error("用户不存在");
        }
        user.setTechTags(techTags == null ? null : techTags.trim());
        userMapper.updateById(user);
        return Result.successMsg("标签保存成功");
    }

    public Result<User> updateProfile(Long userId, String phone, String email) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            return Result.error("用户不存在");
        }

        if (phone != null && !phone.trim().isEmpty() && !Objects.equals(phone, user.getPhone())) {
            QueryWrapper<User> phoneQuery = new QueryWrapper<>();
            phoneQuery.eq("phone", phone).ne("id", userId);
            if (userMapper.selectCount(phoneQuery) > 0) {
                return Result.error("该手机号已被其他账号使用");
            }
            user.setPhone(phone.trim());
        }

        if (email != null && !email.trim().isEmpty() && !Objects.equals(email, user.getEmail())) {
            QueryWrapper<User> emailQuery = new QueryWrapper<>();
            emailQuery.eq("email", email).ne("id", userId);
            if (userMapper.selectCount(emailQuery) > 0) {
                return Result.error("该邮箱已被其他账号使用");
            }
            user.setEmail(email.trim());
        }

        userMapper.updateById(user);
        return Result.success(sanitize(user));
    }

    public Result<String> changePassword(Long userId, String oldPassword, String newPassword) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            return Result.error("用户不存在");
        }
        if (oldPassword == null || newPassword == null || newPassword.trim().length() < 6) {
            return Result.error("密码长度至少为6位");
        }
        if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
            return Result.error("原密码错误");
        }
        user.setPassword(passwordEncoder.encode(newPassword.trim()));
        userMapper.updateById(user);
        return Result.successMsg("密码修改成功");
    }

    public Result<String> resetPasswordByAdmin(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) return Result.error("用户不存在");
        String tempPassword = "Reset@" + (int)(Math.random() * 900000 + 100000);
        user.setPassword(passwordEncoder.encode(tempPassword));
        userMapper.updateById(user);
        return Result.success("临时密码已重置为：" + tempPassword);
    }

    public Result<User> updateFullProfile(Long userId, String phone, String email,
                                          String avatar, String nickname, String bio) {
        User user = userMapper.selectById(userId);
        if (user == null) return Result.error("用户不存在");

        if (phone != null && !phone.trim().isEmpty() && !Objects.equals(phone, user.getPhone())) {
            if (userMapper.selectCount(new QueryWrapper<User>().eq("phone", phone).ne("id", userId)) > 0) {
                return Result.error("该手机号已被其他账号使用");
            }
            user.setPhone(phone.trim());
        }
        if (email != null && !email.trim().isEmpty() && !Objects.equals(email, user.getEmail())) {
            if (userMapper.selectCount(new QueryWrapper<User>().eq("email", email).ne("id", userId)) > 0) {
                return Result.error("该邮箱已被其他账号使用");
            }
            user.setEmail(email.trim());
        }
        if (avatar != null) user.setAvatar(avatar.trim());
        if (nickname != null) user.setNickname(nickname.trim());
        if (bio != null) user.setBio(bio.trim());
        userMapper.updateById(user);
        return Result.success(sanitize(user));
    }

    /**
     * 获取信用排行榜 (新增功能)
     */
    public Result<List<User>> getCreditRank() {
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.orderByDesc("total_score");
        List<User> rankList = userMapper.selectList(queryWrapper);

        for (User user : rankList) {
            user.setPassword(null);
        }

        return Result.success(rankList);
    }
}