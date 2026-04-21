package com.zhaoyichi.devplatformbackend.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.zhaoyichi.devplatformbackend.config.GithubProperties;
import com.zhaoyichi.devplatformbackend.common.Result;
import com.zhaoyichi.devplatformbackend.entity.CreditScore;
import com.zhaoyichi.devplatformbackend.entity.User;
import com.zhaoyichi.devplatformbackend.mapper.CreditScoreMapper;
import com.zhaoyichi.devplatformbackend.mapper.UserMapper;
import com.zhaoyichi.devplatformbackend.service.BadgeService;
import com.zhaoyichi.devplatformbackend.service.CreditScoreService;
import com.zhaoyichi.devplatformbackend.service.LoginAuditService;
import com.zhaoyichi.devplatformbackend.service.UserService;
import com.zhaoyichi.devplatformbackend.utils.AuthHelper;
import com.zhaoyichi.devplatformbackend.utils.JwtUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/user")
@CrossOrigin(origins = "*")
public class UserController {
    private static final Logger log = LoggerFactory.getLogger(UserController.class);

    private static final long MAX_AVATAR_BYTES = 2L * 1024 * 1024;
    private static final Set<String> AVATAR_CONTENT_TYPES = new HashSet<>(Arrays.asList(
            "image/jpeg", "image/png", "image/gif", "image/webp"
    ));

    private final UserService userService;
    private final CreditScoreService creditScoreService;
    private final CreditScoreMapper creditScoreMapper;
    private final UserMapper userMapper;
    private final GithubProperties githubProperties;
    private final LoginAuditService loginAuditService;
    private final BadgeService badgeService;

    public UserController(UserService userService,
                          CreditScoreService creditScoreService,
                          CreditScoreMapper creditScoreMapper,
                          UserMapper userMapper,
                          GithubProperties githubProperties,
                          LoginAuditService loginAuditService,
                          BadgeService badgeService) {
        this.userService = userService;
        this.creditScoreService = creditScoreService;
        this.creditScoreMapper = creditScoreMapper;
        this.userMapper = userMapper;
        this.githubProperties = githubProperties;
        this.loginAuditService = loginAuditService;
        this.badgeService = badgeService;
    }

    @PostMapping("/register")
    public Result<String> register(@RequestBody User user) {
        return userService.register(user);
    }

    @PostMapping("/login")
    public Result<?> login(@RequestBody Map<String, String> loginData, HttpServletRequest request) {
        String username = loginData.get("username");
        try {
            String password = loginData.get("password");

            Result<String> result = userService.login(username, password);

            if (result.getCode() == 200) {
                User dbUser = userService.findByLoginAccount(username);

                if (dbUser != null) {
                    // 登录成功：写审计 + 异常 IP 提醒（异常不影响主流程）
                    try { loginAuditService.record(dbUser.getId(), dbUser.getUsername(), request, true, null); } catch (Exception ignore) {}
                    dbUser = userService.sanitize(dbUser);
                    Map<String, Object> returnData = new HashMap<>();
                    returnData.put("token", result.getData());
                    returnData.put("user", dbUser);
                    return Result.success(returnData);
                }
            }
            // 登录失败：仍然记一条（userId 可能未知）
            try { loginAuditService.record(null, username, request, false, result.getMessage()); } catch (Exception ignore) {}
            return result;
        } catch (Exception e) {
            log.error("登录异常 username={}", username, e);
            try { loginAuditService.record(null, username, request, false, "系统异常"); } catch (Exception ignore) {}
            return Result.systemError("登录服务异常，请稍后重试");
        }
    }

    @PostMapping("/oauth/login")
    public Result<?> oauthLogin(@RequestParam String code, HttpServletRequest request) {
        try {
            String githubUsername = getGithubUsernameByCode(code);
            if (githubUsername == null) return Result.error("GitHub 授权验证失败");

            QueryWrapper<User> wrapper = new QueryWrapper<>();
            wrapper.eq("github_username", githubUsername);
            User user = userMapper.selectOne(wrapper);

            if (user == null) {
                return Result.error("该 GitHub 账号尚未绑定平台账号，请先常规登录后绑定！");
            }

            String token = JwtUtils.generateToken(user.getId(), user.getUsername(), user.getRole());
            // 登录审计：OAuth 登录也需要记录
            try { loginAuditService.record(user.getId(), user.getUsername(), request, true, null); } catch (Exception ignore) {}
            Map<String, Object> data = new HashMap<>();
            data.put("token", token);
            data.put("user", userService.sanitize(user));
            return Result.success(data);
        } catch (Exception e) {
            log.error("GitHub OAuth 登录失败", e);
            return Result.systemError("GitHub 登录失败，请稍后重试");
        }
    }

    @PostMapping("/oauth/bind")
    public Result<String> oauthBindGithub(@RequestParam String code, HttpServletRequest request) {
        Long userId = AuthHelper.currentUserId(request);
        if (userId == null) {
            return Result.unauthorized("未检测到登录状态");
        }

        try {
            validateGithubOAuthConfig();
            RestTemplate restTemplate = buildGitHubRestTemplate();

            String url = String.format("https://github.com/login/oauth/access_token?client_id=%s&client_secret=%s&code=%s",
                    githubProperties.getClientId(), githubProperties.getClientSecret(), code);

            org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
            headers.set("Accept", "application/json");
            Map<String, Object> response = restTemplate.exchange(
                    Objects.requireNonNull(url, "url"),
                    org.springframework.http.HttpMethod.POST,
                    new org.springframework.http.HttpEntity<>(headers),
                    new ParameterizedTypeReference<Map<String, Object>>() {}
            ).getBody();

            String githubToken = response == null ? null : (String) response.get("access_token");
            if (githubToken == null) return Result.error("GitHub 授权失败");

            // 4. 获取 GitHub 用户详情
            org.springframework.http.HttpHeaders userHeaders = new org.springframework.http.HttpHeaders();
            userHeaders.set("Authorization", "Bearer " + githubToken);
            Map<String, Object> githubUser = restTemplate.exchange(
                    "https://api.github.com/user",
                    org.springframework.http.HttpMethod.GET,
                    new org.springframework.http.HttpEntity<>(userHeaders),
                    new ParameterizedTypeReference<Map<String, Object>>() {}
            ).getBody();
            String githubLogin = githubUser == null ? null : (String) githubUser.get("login");
            if (githubLogin == null || githubLogin.isEmpty()) return Result.error("获取 GitHub 用户信息失败");
            githubLogin = githubLogin.trim();

            User user = userMapper.selectById(userId);
            if (user == null) {
                return Result.error("当前用户不存在");
            }

            QueryWrapper<User> bindQuery = new QueryWrapper<>();
            // selectList replaces selectOne to avoid TooManyResultsException from dirty data
            bindQuery.eq("github_username", githubLogin);
            java.util.List<User> boundUsers = userMapper.selectList(bindQuery);
            boolean alreadyBound = boundUsers.stream().anyMatch(u -> !u.getId().equals(userId));
            if (alreadyBound) {
                return Result.error("\u8be5 GitHub \u8d26\u53f7\u5df2\u7ed1\u5b9a\u5176\u4ed6\u5e73\u53f0\u8d26\u53f7\uff0c\u8bf7\u5148\u89e3\u7ed1\u539f\u8d26\u53f7\u540e\u518d\u8bd5");
            }

            user.setGithubUsername(githubLogin);
            userMapper.updateById(user);

            // 功能 D：首次绑定 GitHub 徽章（幂等）
            try { badgeService.afterBindGithub(userId); } catch (Exception ignore) {}
            try {
                // 绑定后生成一份综合信用档案（V2-only），保证个人中心和首页能直接看到最新模型数据。
                creditScoreService.queryCredit(githubLogin, "综合", userId.intValue());
            } catch (Exception ignored) {
            }

            return Result.success(githubLogin);
        } catch (DataIntegrityViolationException e) {
            // 数据库唯一索引兜底：并发或大小写/空格差异导致的重复绑定
            log.warn("GitHub 绑定触发唯一约束 userId={}", userId, e);
            return Result.error("该 GitHub 账号已绑定其他平台账号，请先解绑原账号后再试");
        } catch (Exception e) {
            log.error("GitHub \u7ed1\u5b9a\u5931\u8d25 userId={} exType={}", userId, e.getClass().getSimpleName(), e);
            return Result.systemError("绑定失败，请检查 GitHub 配置或稍后重试");
        }
    }

    private String getGithubUsernameByCode(String code) {
        validateGithubOAuthConfig();
        RestTemplate restTemplate = buildGitHubRestTemplate();

        String tokenUrl = String.format("https://github.com/login/oauth/access_token?client_id=%s&client_secret=%s&code=%s",
                githubProperties.getClientId(), githubProperties.getClientSecret(), code);

        HttpHeaders headers = new HttpHeaders();
        headers.set("Accept", "application/json");

        // 🌟 修复点：利用 ParameterizedTypeReference 显式声明泛型 Map
        ResponseEntity<Map<String, Object>> tokenResp = restTemplate.exchange(
                Objects.requireNonNull(tokenUrl, "tokenUrl"), HttpMethod.POST, new HttpEntity<>(headers),
                new ParameterizedTypeReference<Map<String, Object>>() {});

        Map<String, Object> tokenBody = tokenResp.getBody();
        if (tokenBody == null || !tokenBody.containsKey("access_token")) return null;
        String accessToken = (String) tokenBody.get("access_token");

        HttpHeaders userHeaders = new HttpHeaders();
        userHeaders.set("Authorization", "Bearer " + accessToken);
        ResponseEntity<Map<String, Object>> userResp = restTemplate.exchange(
                "https://api.github.com/user", HttpMethod.GET, new HttpEntity<>(userHeaders),
                new ParameterizedTypeReference<Map<String, Object>>() {});

        Map<String, Object> userBody = userResp.getBody();
        return (userBody != null) ? (String) userBody.get("login") : null;
    }

    private void validateGithubOAuthConfig() {
        if (githubProperties.getClientId() == null || githubProperties.getClientId().trim().isEmpty()
                || githubProperties.getClientSecret() == null || githubProperties.getClientSecret().trim().isEmpty()) {
            throw new IllegalStateException("GitHub OAuth 配置缺失");
        }
    }

    private RestTemplate buildGitHubRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        if (githubProperties.isProxyEnabled()) {
            Proxy proxy = new Proxy(
                    Proxy.Type.HTTP,
                    new InetSocketAddress(githubProperties.getProxyHost(), githubProperties.getProxyPort())
            );
            factory.setProxy(proxy);
        }
        return new RestTemplate(factory);
    }

    @GetMapping("/info")
    public Result<Map<String, Object>> getUserInfo(@RequestParam(required = false) String username, HttpServletRequest request) {
        User user;
        if (username != null && !username.trim().isEmpty()) {
            QueryWrapper<User> queryWrapper = new QueryWrapper<>();
            queryWrapper.eq("username", username);
            user = userMapper.selectOne(queryWrapper);
        } else {
            user = userService.findById(AuthHelper.currentUserId(request));
        }

        if (user == null) return Result.error("未找到用户信息");

        Map<String, Object> result = new HashMap<>();
        result.put("id", user.getId());
        result.put("username", user.getUsername());
        result.put("nickname", user.getNickname());
        result.put("avatar", user.getAvatar());
        result.put("bio", user.getBio());
        result.put("githubUsername", user.getGithubUsername());
        result.put("phone", user.getPhone());
        result.put("email", user.getEmail());
        result.put("status", user.getStatus());
        result.put("role", user.getRole());
        result.put("techTags", user.getTechTags());

        result.put("credit", loadCreditProfile(user.getGithubUsername()));
        return Result.success(result);
    }

    private Object loadCreditProfile(String githubUsername) {
        if (githubUsername == null || githubUsername.trim().isEmpty()) {
            return null;
        }

        QueryWrapper<CreditScore> scoreWrapper = new QueryWrapper<>();
        scoreWrapper.eq("github_username", githubUsername).eq("scene", "综合");
        CreditScore score = creditScoreMapper.selectOne(scoreWrapper);
        if (score != null) {
            return score;
        }
        return null;
    }

    @PostMapping("/unbind")
    public Result<String> unbindGithub(HttpServletRequest request) {
        Long userId = AuthHelper.currentUserId(request);
        if (userId == null) {
            return Result.error("未登录");
        }
        UpdateWrapper<User> updateWrapper = new UpdateWrapper<>();
        updateWrapper.eq("id", userId).set("github_username", null);
        userMapper.update(null, updateWrapper);
        return Result.successMsg("解除绑定成功！");
    }

    @PostMapping("/updateTags")
    public Result<String> updateTags(@RequestParam String techTags, HttpServletRequest request) {
        Long userId = AuthHelper.currentUserId(request);
        if (userId == null) {
            return Result.error("未登录");
        }
        return userService.updateTags(userId, techTags);
    }

    @PostMapping("/updateProfile")
    public Result<User> updateProfile(@RequestBody UpdateProfileRequest profileRequest, HttpServletRequest request) {
        Long userId = AuthHelper.currentUserId(request);
        if (userId == null) {
            return Result.error("未登录");
        }
        return userService.updateFullProfile(userId,
                profileRequest.getPhone(), profileRequest.getEmail(),
                profileRequest.getAvatar(), profileRequest.getNickname(), profileRequest.getBio());
    }

    @GetMapping("/privacy/get")
    public Result<Map<String, Object>> getPrivacy(HttpServletRequest request) {
        Result<Map<String, Object>> auth = AuthHelper.requireLogin(request);
        if (auth != null) return auth;
        Long userId = AuthHelper.currentUserId(request);
        User user = userMapper.selectById(userId);
        if (user == null) return Result.error("用户不存在");
        Map<String, Object> out = new HashMap<>();
        out.put("privacyCreditPublic", user.getPrivacyCreditPublic() == null ? 1 : user.getPrivacyCreditPublic());
        out.put("privacyFeedPublic", user.getPrivacyFeedPublic() == null ? 1 : user.getPrivacyFeedPublic());
        out.put("privacyAllowMessage", user.getPrivacyAllowMessage() == null ? 1 : user.getPrivacyAllowMessage());
        return Result.success(out);
    }

    @PostMapping("/privacy/save")
    public Result<String> savePrivacy(@RequestBody PrivacyRequest body, HttpServletRequest request) {
        Result<String> auth = AuthHelper.requireLogin(request);
        if (auth != null) return auth;
        Long userId = AuthHelper.currentUserId(request);
        if (body == null) return Result.error("参数错误");
        int credit = body.getPrivacyCreditPublic() != null && body.getPrivacyCreditPublic() == 0 ? 0 : 1;
        int feed = body.getPrivacyFeedPublic() != null && body.getPrivacyFeedPublic() == 0 ? 0 : 1;
        int msg = body.getPrivacyAllowMessage() != null && body.getPrivacyAllowMessage() == 0 ? 0 : 1;
        UpdateWrapper<User> uw = new UpdateWrapper<>();
        uw.eq("id", userId)
                .set("privacy_credit_public", credit)
                .set("privacy_feed_public", feed)
                .set("privacy_allow_message", msg);
        userMapper.update(null, uw);
        return Result.successMsg("已保存");
    }

    /**
     * 上传本地头像图片，保存到 {@code uploads/avatar/} 并写回用户 {@code avatar} 字段（相对路径 {@code /uploads/avatar/...}）。
     */
    @PostMapping("/uploadAvatar")
    public Result<Map<String, String>> uploadAvatar(@RequestParam("file") MultipartFile file, HttpServletRequest request) {
        Long userId = AuthHelper.currentUserId(request);
        if (userId == null) {
            return Result.error("未登录");
        }
        if (file == null || file.isEmpty()) {
            return Result.error("请选择图片文件");
        }
        if (file.getSize() > MAX_AVATAR_BYTES) {
            return Result.error("图片大小不能超过 2MB");
        }
        String normalizedType = normalizeAvatarContentType(file);
        if (normalizedType == null) {
            return Result.error("仅支持 JPG、PNG、GIF、WebP 图片（若浏览器未带 Content-Type，请确保扩展名为 .jpg/.png/.gif/.webp）");
        }
        String ext = extensionForImage(normalizedType);
        String filename = userId + "_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12) + ext;
        String dirPath = System.getProperty("user.dir") + File.separator + "uploads" + File.separator + "avatar";
        File dir = new File(dirPath);
        if (!dir.exists() && !dir.mkdirs()) {
            return Result.systemError("无法创建上传目录");
        }
        try {
            User existing = userMapper.selectById(userId);
            if (existing != null && existing.getAvatar() != null) {
                tryDeleteOldAvatarFile(existing.getAvatar());
            }
            File target = new File(dir, filename);
            file.transferTo(target);
            String publicPath = "/uploads/avatar/" + filename;
            Result<User> updated = userService.updateFullProfile(userId, null, null, publicPath, null, null);
            if (updated.getCode() != 200) {
                target.delete();
                return Result.error(updated.getMessage() == null ? "保存头像失败" : updated.getMessage());
            }
            Map<String, String> data = new HashMap<>();
            data.put("url", publicPath);
            log.info("avatar upload ok userId={} path={}", userId, publicPath);
            return Result.success(data);
        } catch (IOException e) {
            log.error("uploadAvatar failed userId={}", userId, e);
            return Result.systemError("头像上传失败，请稍后重试");
        }
    }

    /**
     * 部分浏览器/环境下 Multipart 的 Content-Type 为空或为 application/octet-stream，则按原始文件名后缀推断。
     */
    private static String normalizeAvatarContentType(MultipartFile file) {
        String raw = file.getContentType();
        if (raw != null) {
            String lower = raw.toLowerCase(Locale.ROOT).trim();
            int semi = lower.indexOf(';');
            if (semi > 0) {
                lower = lower.substring(0, semi).trim();
            }
            if (AVATAR_CONTENT_TYPES.contains(lower)) {
                return lower;
            }
            if ("application/octet-stream".equals(lower) || lower.isEmpty()) {
                // fall through to extension guess
            } else {
                return null;
            }
        }
        String ext = StringUtils.getFilenameExtension(file.getOriginalFilename());
        if (ext == null) {
            return null;
        }
        switch (ext.toLowerCase(Locale.ROOT)) {
            case "jpg":
            case "jpeg":
            case "jfif":
                return "image/jpeg";
            case "png":
                return "image/png";
            case "gif":
                return "image/gif";
            case "webp":
                return "image/webp";
            default:
                return null;
        }
    }

    private static void tryDeleteOldAvatarFile(String oldAvatar) {
        if (oldAvatar == null || !oldAvatar.contains("/uploads/avatar/")) {
            return;
        }
        int slash = oldAvatar.lastIndexOf('/');
        if (slash < 0 || slash >= oldAvatar.length() - 1) {
            return;
        }
        String name = oldAvatar.substring(slash + 1);
        if (name.contains("..") || name.contains("/") || name.contains("\\")) {
            return;
        }
        File f = new File(System.getProperty("user.dir") + File.separator + "uploads" + File.separator + "avatar", name);
        if (f.isFile()) {
            // noinspection ResultOfMethodCallIgnored
            f.delete();
        }
    }

    private static String extensionForImage(String contentType) {
        switch (contentType) {
            case "image/jpeg":
                return ".jpg";
            case "image/png":
                return ".png";
            case "image/gif":
                return ".gif";
            case "image/webp":
                return ".webp";
            default:
                return ".img";
        }
    }

    @PostMapping("/changePassword")
    public Result<String> changePassword(@RequestBody ChangePasswordRequest passwordRequest, HttpServletRequest request) {
        Long userId = AuthHelper.currentUserId(request);
        if (userId == null) {
            return Result.error("未登录");
        }
        return userService.changePassword(userId, passwordRequest.getOldPassword(), passwordRequest.getNewPassword());
    }

    public static class UpdateProfileRequest {
        private String phone;
        private String email;
        private String avatar;
        private String nickname;
        private String bio;

        public String getPhone() { return phone; }
        public void setPhone(String phone) { this.phone = phone; }
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public String getAvatar() { return avatar; }
        public void setAvatar(String avatar) { this.avatar = avatar; }
        public String getNickname() { return nickname; }
        public void setNickname(String nickname) { this.nickname = nickname; }
        public String getBio() { return bio; }
        public void setBio(String bio) { this.bio = bio; }
    }

    public static class ChangePasswordRequest {
        private String oldPassword;
        private String newPassword;

        public String getOldPassword() {
            return oldPassword;
        }

        public void setOldPassword(String oldPassword) {
            this.oldPassword = oldPassword;
        }

        public String getNewPassword() {
            return newPassword;
        }

        public void setNewPassword(String newPassword) {
            this.newPassword = newPassword;
        }
    }

    public static class PrivacyRequest {
        private Integer privacyCreditPublic;
        private Integer privacyFeedPublic;
        private Integer privacyAllowMessage;

        public Integer getPrivacyCreditPublic() { return privacyCreditPublic; }
        public void setPrivacyCreditPublic(Integer privacyCreditPublic) { this.privacyCreditPublic = privacyCreditPublic; }
        public Integer getPrivacyFeedPublic() { return privacyFeedPublic; }
        public void setPrivacyFeedPublic(Integer privacyFeedPublic) { this.privacyFeedPublic = privacyFeedPublic; }
        public Integer getPrivacyAllowMessage() { return privacyAllowMessage; }
        public void setPrivacyAllowMessage(Integer privacyAllowMessage) { this.privacyAllowMessage = privacyAllowMessage; }
    }
}
