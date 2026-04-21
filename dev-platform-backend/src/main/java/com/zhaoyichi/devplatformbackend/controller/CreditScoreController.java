package com.zhaoyichi.devplatformbackend.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.zhaoyichi.devplatformbackend.entity.CreditHistory;
import com.zhaoyichi.devplatformbackend.entity.CreditScore;
import com.zhaoyichi.devplatformbackend.entity.QueryLog;
import com.zhaoyichi.devplatformbackend.common.Result;
import com.zhaoyichi.devplatformbackend.mapper.QueryLogMapper;
import com.zhaoyichi.devplatformbackend.entity.User;
import com.zhaoyichi.devplatformbackend.service.BadgeService;
import com.zhaoyichi.devplatformbackend.service.CreditRefreshAsyncService;
import com.zhaoyichi.devplatformbackend.service.CreditRefreshThrottle;
import com.zhaoyichi.devplatformbackend.service.CreditScoreService;
import com.zhaoyichi.devplatformbackend.service.UserService;
import com.zhaoyichi.devplatformbackend.service.credit.CreditReportHtmlBuilder;
import com.zhaoyichi.devplatformbackend.service.credit.CreditScoreV2Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.zhaoyichi.devplatformbackend.utils.AuthHelper;
import com.zhaoyichi.devplatformbackend.vo.CreditExplainDetailVO;
import com.zhaoyichi.devplatformbackend.vo.CreditRankItemVO;
import com.zhaoyichi.devplatformbackend.vo.CreditScoreResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URLEncoder;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/credit")
@CrossOrigin(origins = "*", exposedHeaders = {"Content-Disposition"})
public class CreditScoreController {

    private static final Logger log = LoggerFactory.getLogger(CreditScoreController.class);

    @Autowired
    private CreditScoreService creditScoreService;
    @Autowired
    private com.zhaoyichi.devplatformbackend.mapper.CreditScoreMapper creditScoreMapper;
    @Autowired
    private QueryLogMapper queryLogMapper;
    @Autowired
    private UserService userService;
    @Autowired
    private CreditRefreshThrottle creditRefreshThrottle;
    @Autowired
    private CreditRefreshAsyncService creditRefreshAsyncService;

    @Autowired
    private BadgeService badgeService;
    @Autowired
    private CreditScoreV2Service creditScoreV2Service;
    @Autowired
    private CreditReportHtmlBuilder creditReportHtmlBuilder;

    // 查询单个用户信用分（从 token 中解析 userId 用于日志记录）
    @PostMapping("/query")
    public Result<CreditScoreResult> query(@RequestBody QueryRequest req, HttpServletRequest request) {
        Long currentUserIdLong = AuthHelper.currentUserId(request);
        Integer currentUserId = currentUserIdLong == null ? null : currentUserIdLong.intValue();
        CreditScoreResult data = creditScoreService.queryCredit(req.getGithubUsername(), req.getScene(), currentUserId);
        if (data != null) {
            // 功能 D：查询到自己的信用分 >= 600 时解锁「信用新星」徽章
            if (currentUserIdLong != null) {
                try { badgeService.afterCreditQuery(currentUserIdLong, data.getTotalScore()); } catch (Exception ignore) {}
            }
            return Result.success(data);
        } else {
            return Result.error("未查询到该用户的信用档案");
        }
    }

    @GetMapping("/rank")
    public Result<List<CreditScore>> rank(@RequestParam(defaultValue = "综合") String scene,
                       @RequestParam(defaultValue = "10") int limit) {
        List<CreditScore> list = creditScoreService.getRankList(scene, limit);
        return Result.success(list);
    }

    @GetMapping("/rank2")
    public Result<List<CreditRankItemVO>> rank2(
            @RequestParam(defaultValue = "综合") String scene,
            @RequestParam(required = false) String tag,
            @RequestParam(required = false) String level,
            @RequestParam(required = false) Integer minScore,
            @RequestParam(required = false) Integer maxScore,
            @RequestParam(defaultValue = "20") int limit,
            /** 为 true 时仅返回「本站已注册用户且已绑定 GitHub」的排行，便于与全库 GitHub 账号区分展示 */
            @RequestParam(defaultValue = "false") boolean siteOnly) {
        return Result.success(creditScoreService.getAdvancedRankList(scene, tag, level, minScore, maxScore, limit, siteOnly));
    }

    @PostMapping("/compare")
    public Result<List<Map<String, Object>>> compare(@RequestBody CompareRequest req) {
        if (req.getUsernames() == null || req.getUsernames().isEmpty()) {
            return Result.error("请提供要对比的用户名列表");
        }
        List<String> usernames = req.getUsernames();
        if (usernames.size() > 5) {
            return Result.error("最多支持5人对比");
        }
        String scene = req.getScene() == null ? "综合" : req.getScene();
        return Result.success(creditScoreService.compareUsers(usernames, scene));
    }

    /**
     * 导出信用对比 CSV。
     * <p>注意：禁止在同一 {@link HttpServletResponse} 上混用 {@code getOutputStream()} 与 {@code getWriter()}，
     * 否则会抛出 {@link IllegalStateException}，全局异常处理器返回 JSON，前端误存为 CSV。</p>
     */
    @GetMapping("/export")
    public void exportCsv(@RequestParam String usernames,
                          @RequestParam(required = false, defaultValue = "综合") String scene,
                          HttpServletResponse response) throws IOException {
        String normalizedScene = creditScoreService.normalizeScene(scene);
        List<String> userList = Arrays.stream(usernames.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
        if (userList.isEmpty()) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.setCharacterEncoding("UTF-8");
            response.setContentType("text/plain; charset=UTF-8");
            PrintWriter w = response.getWriter();
            w.println("usernames 不能为空");
            w.flush();
            return;
        }

        // V2-only：导出前按当前场景重算并落库，确保 CSV 为最新 V2 快照
        for (String gh : userList) {
            try {
                creditScoreV2Service.fetchAndCalculate(gh, normalizedScene);
            } catch (Exception e) {
                log.warn("[export] v2 刷新失败 gh={} scene={}", gh, normalizedScene, e);
            }
        }

        QueryWrapper<CreditScore> wrapper = new QueryWrapper<>();
        wrapper.in("github_username", userList).eq("scene", normalizedScene);
        List<CreditScore> scores = creditScoreMapper.selectList(wrapper);
        Map<String, CreditScore> byUser = scores.stream()
                .filter(s -> s.getGithubUsername() != null && !s.getGithubUsername().isEmpty())
                .collect(Collectors.toMap(CreditScore::getGithubUsername, Function.identity(), (a, b) -> a));

        response.setCharacterEncoding("UTF-8");
        response.setContentType("text/csv; charset=UTF-8");
        response.setHeader("Content-Disposition", buildDownloadHeader(buildExportFileName("信用对比报告", userList, "csv")));

        PrintWriter writer = response.getWriter();
        // UTF-8 BOM：仅用 Writer 写入，与 Excel 兼容；勿再调用 getOutputStream()
        writer.print('\uFEFF');
        writer.println(csvLine(
                "GitHub账号", "评估场景", "代码稳定性(分)", "PR质量(分)", "团队协作(分)", "规范合规性(分)", "总分", "系统评级", "算法版本", "加权总分说明"));
        for (String gh : userList) {
            CreditScore score = byUser.get(gh);
            if (score == null) {
                writer.println(csvLine(gh, normalizedScene, "-", "-", "-", "-", "-", "暂无档案", "-", "无库内快照"));
                continue;
            }
            String weightedNote = creditScoreService.formatWeightedTotalForRow(score, normalizedScene);
            writer.println(csvLine(
                    score.getGithubUsername(),
                    score.getScene() == null ? normalizedScene : score.getScene(),
                    String.valueOf(score.getStability() == null ? 0 : score.getStability()),
                    String.valueOf(score.getPrQuality() == null ? 0 : score.getPrQuality()),
                    String.valueOf(score.getCollaboration() == null ? 0 : score.getCollaboration()),
                    String.valueOf(score.getCompliance() == null ? 0 : score.getCompliance()),
                    String.valueOf(score.getTotalScore() == null ? 0 : score.getTotalScore()),
                    score.getLevel() == null ? "暂无" : score.getLevel(),
                    score.getAlgoVersion() == null ? "v1" : score.getAlgoVersion(),
                    weightedNote
            ));
        }
        writer.flush();
    }

    /**
     * 导出含源数据、各维度公式与文献依据、加权总分推导的 HTML 详细报告（浏览器打开即可阅读/打印）。
     */
    @GetMapping("/exportDetailed")
    public void exportDetailedHtml(@RequestParam String usernames,
                                   @RequestParam(required = false, defaultValue = "综合") String scene,
                                   HttpServletResponse response) throws IOException {
        List<String> userList = Arrays.stream(usernames.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
        if (userList.isEmpty()) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.setCharacterEncoding("UTF-8");
            response.setContentType("text/plain; charset=UTF-8");
            response.getWriter().println("usernames 不能为空");
            return;
        }
        String normalizedScene = creditScoreService.normalizeScene(scene);
        // V2-only：导出前刷新
        for (String gh : userList) {
            try {
                creditScoreV2Service.fetchAndCalculate(gh, normalizedScene);
            } catch (Exception e) {
                log.warn("[exportDetailed] v2 刷新失败 gh={}", gh, e);
            }
        }
        String html = creditReportHtmlBuilder.buildReportHtml(userList, normalizedScene);
        response.setCharacterEncoding("UTF-8");
        response.setContentType("text/html; charset=UTF-8");
        response.setHeader("Content-Disposition", buildDownloadHeader(buildExportFileName("信用详细分析报告", userList, "html")));
        PrintWriter w = response.getWriter();
        w.print(html);
        w.flush();
    }

    private static String buildExportFileName(String prefix, List<String> userList, String ext) {
        String who;
        if (userList == null || userList.isEmpty()) {
            who = "unknown";
        } else if (userList.size() == 1) {
            who = userList.get(0);
        } else {
            who = userList.get(0) + "_multi" + userList.size();
        }
        String ts = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmm"));
        return prefix + "_" + who + "_" + ts + "." + ext;
    }

    /**
     * 兼容中文文件名：同时设置 filename 与 RFC5987 的 filename*。
     */
    private static String buildDownloadHeader(String filename) {
        String safe = filename == null ? "download" : filename.replace("\"", "");
        String encoded;
        try {
            encoded = URLEncoder.encode(safe, StandardCharsets.UTF_8.name()).replace("+", "%20");
        } catch (UnsupportedEncodingException e) {
            // UTF-8 is required by the JVM spec; treat as impossible.
            throw new IllegalStateException("UTF-8 not supported", e);
        }
        return "attachment; filename=\"" + safe + "\"; filename*=UTF-8''" + encoded;
    }

    /** 将若干单元格拼成一行 CSV（RFC 4180 风格转义）。 */
    private static String csvLine(String... cells) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < cells.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(csvEscape(cells[i]));
        }
        return sb.toString();
    }

    private static String csvEscape(String raw) {
        if (raw == null) {
            return "";
        }
        boolean needQuote = raw.indexOf(',') >= 0 || raw.indexOf('"') >= 0
                || raw.indexOf('\r') >= 0 || raw.indexOf('\n') >= 0;
        if (!needQuote) {
            return raw;
        }
        return "\"" + raw.replace("\"", "\"\"") + "\"";
    }

    @GetMapping("/trend")
    public Result<List<CreditHistory>> trend(@RequestParam String githubUsername,
                        @RequestParam(defaultValue = "6") int months,
                        @RequestParam(defaultValue = "综合") String scene) {
        List<CreditHistory> list = creditScoreService.getCreditTrend(githubUsername, scene, months);
        return Result.success(list);
    }

    @GetMapping("/queryHistory")
    public Result<List<QueryLog>> queryHistory(HttpServletRequest request) {
        Result<List<QueryLog>> auth = AuthHelper.requireLogin(request);
        if (auth != null) {
            return auth;
        }
        Long userId = AuthHelper.currentUserId(request);
        List<QueryLog> list = creditScoreService.getQueryHistory(userId.intValue());
        return Result.success(list);
    }

    @DeleteMapping("/queryHistory/{id}")
    public Result<String> deleteQueryHistory(@PathVariable Long id, HttpServletRequest request) {
        Result<String> auth = AuthHelper.requireLogin(request);
        if (auth != null) return auth;
        Long userId = AuthHelper.currentUserId(request);
        QueryLog log = queryLogMapper.selectById(id);
        if (log == null) return Result.error("记录不存在");
        if (log.getUserId() == null || !log.getUserId().equals(userId.intValue())) {
            return Result.error("无权操作他人记录");
        }
        queryLogMapper.deleteById(id);
        return Result.successMsg("删除成功");
    }

    @DeleteMapping("/queryHistory")
    public Result<String> batchDeleteQueryHistory(@RequestParam String ids, HttpServletRequest request) {
        Result<String> auth = AuthHelper.requireLogin(request);
        if (auth != null) return auth;
        Long userId = AuthHelper.currentUserId(request);
        List<Long> idList = Arrays.stream(ids.split(","))
                .map(String::trim).filter(s -> !s.isEmpty()).map(Long::parseLong)
                .collect(Collectors.toList());
        if (idList.isEmpty()) return Result.error("请提供要删除的ID列表");
        QueryWrapper<QueryLog> wrapper = new QueryWrapper<>();
        wrapper.in("id", idList).eq("user_id", userId.intValue());
        queryLogMapper.delete(wrapper);
        return Result.successMsg("批量删除成功");
    }

    @PostMapping("/queryHistory/favorite/{id}")
    public Result<String> toggleFavoriteHistory(@PathVariable Long id, HttpServletRequest request) {
        Result<String> auth = AuthHelper.requireLogin(request);
        if (auth != null) return auth;
        Long userId = AuthHelper.currentUserId(request);
        QueryLog log = queryLogMapper.selectById(id);
        if (log == null) return Result.error("记录不存在");
        if (log.getUserId() == null || !log.getUserId().equals(userId.intValue())) {
            return Result.error("无权操作他人记录");
        }
        int newVal = (log.getIsFavorite() != null && log.getIsFavorite() == 1) ? 0 : 1;
        log.setIsFavorite(newVal);
        queryLogMapper.updateById(log);
        return Result.successMsg(newVal == 1 ? "已收藏" : "已取消收藏");
    }

    @GetMapping("/insights")
    public Result<Map<String, Object>> insights(@RequestParam String githubUsername,
                                                 @RequestParam(defaultValue = "综合") String scene) {
        Map<String, Object> result = creditScoreService.getInsights(githubUsername, scene);
        return result == null ? Result.error("未找到信用档案") : Result.success(result);
    }

    /**
     * 信用分可解释性明细：需登录；仅本人或管理员可查看（防越权）。
     */
    @GetMapping("/detail/{userId}")
    public Result<CreditExplainDetailVO> creditExplainDetail(@PathVariable Long userId,
                                                             @RequestParam(defaultValue = "default") String scene,
                                                             HttpServletRequest request) {
        Result<CreditExplainDetailVO> auth = AuthHelper.requireLogin(request);
        if (auth != null) {
            return auth;
        }
        Long cur = AuthHelper.currentUserId(request);
        if (!userId.equals(cur) && !AuthHelper.isAdmin(request)) {
            return Result.forbidden("仅可查看本人信用明细，或需管理员权限");
        }
        CreditExplainDetailVO vo = creditScoreService.buildCreditExplainDetail(userId, scene);
        if (vo == null) {
            return Result.error("用户不存在或未绑定 GitHub");
        }
        return Result.success(vo);
    }

    /**
     * 当前登录用户手动触发信用异步重算；10 分钟内限一次（内存限流，单机有效）。
     */
    @PostMapping("/refresh")
    public Result<Map<String, Object>> refreshCredit(HttpServletRequest request) {
        if (AuthHelper.currentUserId(request) == null) {
            return Result.unauthorized("未登录或登录已过期");
        }
        Long userId = AuthHelper.currentUserId(request);
        User user = userService.findById(userId);
        if (user == null) {
            return Result.error("用户不存在");
        }
        if (user.getGithubUsername() == null || user.getGithubUsername().trim().isEmpty()) {
            return Result.error("请先绑定 GitHub 后再刷新信用分");
        }
        if (!creditRefreshThrottle.tryAcquire(userId)) {
            long nextAt = creditRefreshThrottle.nextAllowedAtMillis(userId);
            long remainMs = Math.max(0L, nextAt - System.currentTimeMillis());
            long mins = (remainMs + 59_999L) / 60_000L;
            return Result.error("10 分钟内仅可手动刷新一次，约 " + mins + " 分钟后再试");
        }
        try {
            creditRefreshAsyncService.runRefreshAsync(userId);
        } catch (Exception e) {
            log.error("提交信用异步刷新失败 userId={}", userId, e);
            creditRefreshThrottle.releaseAfterFailure(userId);
            return Result.systemError("信用刷新任务提交失败，请检查异步线程池或稍后重试");
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("accepted", true);
        data.put("message", "已提交后台重算，数秒后刷新本页即可看到最新综合分（若 GitHub 不可达则分数不变，请查看日志）");
        return Result.success(data);
    }

    public static class QueryRequest {
        private String githubUsername;
        private String scene;
        public String getGithubUsername() { return githubUsername; }
        public String getScene() { return scene; }
        public void setGithubUsername(String githubUsername) { this.githubUsername = githubUsername; }
        public void setScene(String scene) { this.scene = scene; }
    }

    public static class CompareRequest {
        private List<String> usernames;
        private String scene;

        public List<String> getUsernames() { return usernames; }
        public void setUsernames(List<String> usernames) { this.usernames = usernames; }
        public String getScene() { return scene; }
        public void setScene(String scene) { this.scene = scene; }
    }
}