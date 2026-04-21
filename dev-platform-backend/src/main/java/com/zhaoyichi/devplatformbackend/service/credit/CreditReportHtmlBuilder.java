package com.zhaoyichi.devplatformbackend.service.credit;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhaoyichi.devplatformbackend.service.CreditScoreService;
import com.zhaoyichi.devplatformbackend.service.ai.AiProfileService;
import com.zhaoyichi.devplatformbackend.vo.CreditExplainDetailVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * 生成「信用详细分析报告」HTML（含源数据、维度公式、加权总分说明、文献依据摘要）。
 */
@Service
public class CreditReportHtmlBuilder {
    private static final String SERVER_ALGO_VERSION = "v2";
    private static final String REPORT_STYLE_VERSION = "2026-04-20.1";

    @Autowired
    private CreditScoreService creditScoreService;
    @Autowired
    private AiProfileService aiProfileService;
    @Autowired
    private ObjectMapper objectMapper;

    public String buildReportHtml(List<String> githubUsernames, String sceneParam) {
        String scene = creditScoreService.normalizeScene(sceneParam);
        StringBuilder html = new StringBuilder(32_768);
        html.append("<!DOCTYPE html><html lang=\"zh-CN\"><head><meta charset=\"UTF-8\"/>");
        html.append("<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\"/>");
        html.append("<title>信用详细分析报告</title>");
        html.append("<style>");
        html.append(":root{--bg:#f6f8fa;--card:#fff;--muted:#64748b;--text:#0f172a;--border:#e2e8f0;--head:#0b5fff;--head2:#1e40af;}");
        html.append("body{font-family:ui-sans-serif,Segoe UI,Microsoft YaHei,system-ui,-apple-system,sans-serif;margin:0;line-height:1.6;color:var(--text);background:var(--bg);}");
        html.append(".wrap{max-width:1100px;margin:20px auto;padding:0 16px;}");
        html.append(".page{background:var(--card);border:1px solid var(--border);border-radius:14px;box-shadow:0 6px 24px rgba(15,23,42,.06);padding:20px 22px;}");
        html.append("h1{font-size:22px;margin:0 0 10px 0;border-bottom:1px solid var(--border);padding-bottom:12px;}");
        html.append("h2{font-size:18px;margin:22px 0 10px 0;color:var(--head2);}");
        html.append("h3{font-size:15px;margin:18px 0 10px 0;color:#0f172a;}");
        html.append(".meta{color:#475569;font-size:13px;margin:12px 0;}");
        html.append(".meta code{background:#f1f5f9;border:1px solid var(--border);padding:1px 6px;border-radius:6px;}");
        html.append(".box{background:#f8fafc;border:1px solid var(--border);border-radius:12px;padding:12px 14px;margin:12px 0;font-size:13px;}");
        html.append(".box strong{color:#0f172a;}");
        html.append(".refs{color:#475569;font-size:12px;margin-top:10px;}");
        html.append(".mono{font-family:ui-monospace,SFMono-Regular,Menlo,Consolas,monospace;}");
        html.append(".pill{display:inline-flex;align-items:center;gap:6px;padding:2px 8px;border-radius:999px;border:1px solid #cbd5e1;background:#fff;font-size:12px;margin:2px 6px 2px 0;color:#0f172a;}");
        html.append(".muted{color:var(--muted);}");
        html.append(".repo-card{background:#fff;border:1px solid var(--border);border-radius:12px;padding:10px 12px;margin:8px 0;}");
        html.append(".repo-title{font-weight:700;color:var(--head2);text-decoration:none;}");
        html.append(".kvs{display:flex;flex-wrap:wrap;gap:10px;margin-top:6px;color:#475569;font-size:12px;}");

        // 表格（更紧凑、更易读）
        html.append(".dim-table{border-collapse:separate;border-spacing:0;width:100%;margin:12px 0;font-size:13px;border:1px solid var(--border);border-radius:12px;overflow:hidden;}");
        html.append(".dim-table th,.dim-table td{border-bottom:1px solid var(--border);padding:10px 10px;vertical-align:top;}");
        html.append(".dim-table thead th{position:sticky;top:0;background:#eef5ff;color:#0f172a;z-index:1;}");
        html.append(".dim-table tbody tr:nth-child(2n){background:#fafcff;}");
        html.append(".dim-table tbody tr:hover{background:#f3f7ff;}");
        html.append(".dim-table .num{white-space:nowrap;font-variant-numeric:tabular-nums;}");
        html.append(".dim-table .col-dim{min-width:150px;font-weight:600;}");
        html.append(".dim-table .col-formula{max-width:360px;}");
        html.append(".dim-table .col-ind{max-width:280px;}");
        html.append(".truncate{display:block;max-width:100%;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;color:#334155;}");
        html.append("details{border:1px solid var(--border);background:#fff;border-radius:10px;padding:8px 10px;}");
        html.append("details>summary{cursor:pointer;color:#334155;font-weight:600;}");
        html.append(".detail-body{margin-top:8px;color:#334155;font-size:12px;line-height:1.65;white-space:pre-wrap;word-break:break-word;}");
        html.append(".pill{display:inline-flex;align-items:center;gap:6px;padding:2px 8px;border-radius:999px;border:1px solid #cbd5e1;background:#fff;font-size:12px;margin:2px 6px 2px 0;color:#0f172a;}");
        html.append("</style></head><body>");
        html.append("<div class=\"wrap\"><div class=\"page\">");
        html.append("<h1>开源开发者信用 — 详细分析报告</h1>");
        html.append("<div class=\"meta\">生成时间：").append(esc(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))));
        html.append(" &nbsp;|&nbsp; 评估场景：<strong>").append(esc(scene)).append("</strong>");
        html.append(" &nbsp;|&nbsp; 服务端算法版本：<strong>").append(esc(SERVER_ALGO_VERSION)).append("</strong>");
        html.append(" &nbsp;|&nbsp; 样式版本：<strong>").append(esc(REPORT_STYLE_VERSION)).append("</strong>");
        html.append("<div style=\"margin-top:8px;color:#475569;\">");
        html.append("总分由各维度分 × 场景权重后<strong>四舍五入</strong>得到，<strong>不等于</strong>四维分数简单相加。");
        html.append(" 权重来源：若数据库表 <code>scene_weight_config</code> 存在该场景配置，则优先使用库内权重；否则使用文档 §3.3 的推荐默认权重。");
        html.append("</div></div>");

        for (String rawGh : githubUsernames) {
            if (rawGh == null || rawGh.trim().isEmpty()) {
                continue;
            }
            String gh = rawGh.trim();
            html.append("<h2>").append(esc(gh)).append("</h2>");
            CreditExplainDetailVO vo;
            try {
                vo = creditScoreService.buildCreditExplainExportSnapshot(gh, scene);
            } catch (Exception e) {
                html.append("<div class=\"box\" style=\"color:#b91c1c;\">生成该用户报告时出错，请稍后重试或检查 GitHub 网络。</div>");
                continue;
            }
            if (vo == null) {
                html.append("<div class=\"box\">无法获取该账号数据。</div>");
                continue;
            }

            html.append("<div class=\"meta\">算法版本：<strong>").append(esc(vo.getAlgoVersion() == null ? "—" : vo.getAlgoVersion())).append("</strong>");
            if (Boolean.TRUE.equals(vo.getGithubDataUnavailable())) {
                html.append(" &nbsp;|&nbsp; <span style=\"color:#b45309;\">").append(esc(vo.getDegradedMessage())).append("</span>");
            }
            html.append("</div>");

            html.append("<div class=\"box\"><strong>总分与评级</strong><br/>");
            html.append("总分：").append(vo.getTotalScore() == null ? "—" : String.valueOf(vo.getTotalScore()));
            html.append(" &nbsp;|&nbsp; 评级：").append(esc(vo.getLevel() == null ? "—" : vo.getLevel()));
            html.append("</div>");

            if (vo.getWeightedTotalExplanation() != null) {
                html.append("<div class=\"box\"><strong>总分计算依据（加权）</strong><br/>");
                html.append(esc(vo.getWeightedTotalExplanation())).append("</div>");
            }

            if (vo.getCollaborationReviewSummary() != null && !vo.getCollaborationReviewSummary().isEmpty()) {
                html.append("<div class=\"box\"><strong>站内互评摘要</strong><br/>");
                Object cnt = vo.getCollaborationReviewSummary().get("receivedCount");
                Object avg = vo.getCollaborationReviewSummary().get("avgRating");
                html.append("<div class=\"kvs\">");
                html.append("<div><span class=\"muted\">receivedCount</span> = ").append(esc(cnt == null ? "0" : String.valueOf(cnt))).append("</div>");
                html.append("<div><span class=\"muted\">avgRating</span> = ").append(esc(formatNullable(avg))).append("</div>");
                html.append("</div></div>");
            }

            // AI 画像（复用缓存快照；若 AI 未启用或无快照则跳过）
            try {
                Map<String, Object> ai = aiProfileService.getProfile(gh, scene);
                if (ai != null && ai.get("profileJson") != null && String.valueOf(ai.get("profileJson")).trim().length() > 2) {
                    html.append("<div class=\"box\"><strong>AI 画像（辅助协作）</strong><br/>");
                    String profileJson = String.valueOf(ai.get("profileJson"));
                    String summary = firstNonBlank(
                            ai.get("summary") == null ? null : String.valueOf(ai.get("summary")),
                            extractSummaryFromProfile(profileJson)
                    );
                    if (summary != null) {
                        html.append("<div style=\"font-weight:600;margin-bottom:6px;line-height:1.6;\">").append(esc(summary)).append("</div>");
                    }

                    // 技术标签：pill 展示
                    List<Map<String, Object>> tags = parseListOfMap(ai.get("techTagsJson"));
                    if (tags != null && !tags.isEmpty()) {
                        html.append("<div><strong>技术标签</strong>：");
                        for (Map<String, Object> t : tags) {
                            String tag = t == null ? null : asString(t.get("tag"));
                            Double conf = t == null ? null : asDouble(t.get("confidence"));
                            if (tag == null || tag.trim().isEmpty()) continue;
                            String confText = conf == null ? "" : ("<span class=\"muted\">" + esc(String.valueOf((int) Math.round(conf * 100))) + "%</span>");
                            html.append("<span class=\"pill\">").append(esc(tag)).append(confText.isEmpty() ? "" : (" · " + confText)).append("</span>");
                        }
                        html.append("</div>");
                    }

                    // 代表项目：卡片展示
                    List<Map<String, Object>> repos = parseListOfMap(ai.get("topReposJson"));
                    if (repos != null && !repos.isEmpty()) {
                        html.append("<div style=\"margin-top:10px;\"><strong>代表项目</strong></div>");
                        for (Map<String, Object> r : repos) {
                            if (r == null) continue;
                            String repo = asString(r.get("repo"));
                            if (repo == null || repo.trim().isEmpty()) continue;
                            html.append("<div class=\"repo-card\">");
                            html.append("<a class=\"repo-title\" href=\"https://github.com/").append(esc(gh)).append("/").append(esc(repo)).append("\" target=\"_blank\">")
                                    .append(esc(repo)).append("</a>");

                            List<String> techStack = asStringList(r.get("techStack"));
                            if (techStack != null && !techStack.isEmpty()) {
                                html.append("<div class=\"kvs\"><div><span class=\"muted\">techStack</span>：")
                                        .append(esc(String.join(" / ", techStack))).append("</div></div>");
                            }
                            List<String> reasons = asStringList(r.get("reasons"));
                            if (reasons != null && !reasons.isEmpty()) {
                                html.append("<div style=\"margin-top:6px;\" class=\"muted\">原因：").append(esc(String.join("；", reasons))).append("</div>");
                            }
                            List<String> signals = asStringList(r.get("signals"));
                            if (signals != null && !signals.isEmpty()) {
                                html.append("<div style=\"margin-top:6px;\" class=\"muted\">信号：").append(esc(String.join("；", signals))).append("</div>");
                            }
                            html.append("</div>");
                        }
                    }

                    // 证据来源：列表（可折叠）
                    List<Map<String, Object>> evs = parseListOfMap(ai.get("evidenceJson"));
                    if (evs != null && !evs.isEmpty()) {
                        html.append("<details style=\"margin-top:10px;\"><summary class=\"muted\">证据来源（可解释）</summary>");
                        html.append("<div style=\"margin-top:8px;\">");
                        for (Map<String, Object> e : evs) {
                            if (e == null) continue;
                            String key = asString(e.get("key"));
                            String detail = asString(e.get("detail"));
                            if ((key == null || key.isEmpty()) && (detail == null || detail.isEmpty())) continue;
                            html.append("<div style=\"font-size:12px;line-height:1.6;\"><code>")
                                    .append(esc(key == null ? "" : key))
                                    .append("</code> ")
                                    .append(esc(detail == null ? "" : detail))
                                    .append("</div>");
                        }
                        html.append("</div></details>");
                    }
                    html.append("</div>");
                }
            } catch (Exception ignore) {}

            html.append("<h3>各维度：源数据、权重、贡献分与计算依据</h3>");
            html.append("<table class=\"dim-table\"><thead><tr>");
            html.append("<th class=\"col-dim\">维度</th>");
            html.append("<th class=\"num\">得分</th>");
            html.append("<th class=\"num\">权重</th>");
            html.append("<th class=\"num\">贡献分</th>");
            html.append("<th class=\"col-formula\">公式与依据</th>");
            html.append("<th class=\"col-ind\">指标</th>");
            html.append("</tr></thead><tbody>");
            if (vo.getDimensions() != null) {
                for (CreditExplainDetailVO.CreditDimensionLineVO line : vo.getDimensions()) {
                    String formula = line.getFormulaSummary();
                    String formulaPreview = preview(formula, 96);
                    String indicatorsHtml = formatMap(line.getRawIndicators());

                    html.append("<tr>");
                    html.append("<td class=\"col-dim\">").append(esc(line.getDisplayName())).append("</td>");
                    html.append("<td class=\"num\">").append(line.getDimensionScore() == null ? "—" : String.valueOf(line.getDimensionScore())).append("</td>");
                    html.append("<td class=\"num\">").append(line.getWeight() == null ? "—" : String.format("%.4f", line.getWeight())).append("</td>");
                    html.append("<td class=\"num\">").append(line.getWeightedContribution() == null ? "—" : String.format("%.2f", line.getWeightedContribution())).append("</td>");

                    // 公式：默认短摘要，点开看完整
                    html.append("<td class=\"col-formula\">");
                    if (formula == null || formula.trim().isEmpty()) {
                        html.append("<span class=\"muted\">—</span>");
                    } else {
                        html.append("<details>");
                        html.append("<summary><span class=\"truncate\">").append(esc(formulaPreview)).append("</span></summary>");
                        html.append("<div class=\"detail-body mono\">").append(esc(formula)).append("</div>");
                        html.append("</details>");
                    }
                    html.append("</td>");

                    // 指标：默认折叠，避免表格过高
                    html.append("<td class=\"col-ind\">");
                    if (indicatorsHtml == null || "—".equals(indicatorsHtml)) {
                        html.append("<span class=\"muted\">—</span>");
                    } else {
                        html.append("<details>");
                        html.append("<summary>查看指标</summary>");
                        html.append("<div class=\"detail-body\">").append(indicatorsHtml).append("</div>");
                        html.append("</details>");
                    }
                    html.append("</td>");
                    html.append("</tr>");
                }
            }
            html.append("</tbody></table>");

            if (vo.getImprovementSuggestions() != null && !vo.getImprovementSuggestions().isEmpty()) {
                html.append("<div class=\"box\"><strong>改进建议</strong><ul>");
                for (String s : vo.getImprovementSuggestions()) {
                    html.append("<li>").append(esc(s)).append("</li>");
                }
                html.append("</ul></div>");
            }

            html.append("<div class=\"refs\"><strong>文献与标准依据（摘要）</strong>：");
            html.append("T/CESA 开源贡献者评价模型（OI/IC/OP/PRR/MP）；");
            html.append("ICSE-SEIP 2024 OpenRank（AHP 行为权重）+ GitHub followers/仓库 Star（社区认可度）；");
            html.append("ICSE 2014 Gousios 等（PR 合并率）；");
            html.append("《软件学报》2018（分位数/多指标）；");
            html.append("《计算机工程与设计》2017 可信性多属性；");
            html.append("具体公式见上表「公式与计算依据」列。");
            html.append("</div>");
        }

        html.append("<p class=\"meta\" style=\"margin-top:40px;\">— 报告由系统自动生成 —</p>");
        html.append("</div></div></body></html>");
        return html.toString();
    }

    private static String preview(String s, int maxLen) {
        if (s == null) return "—";
        String t = s.trim().replaceAll("\\s+", " ");
        if (t.isEmpty()) return "—";
        if (t.length() <= maxLen) return t;
        return t.substring(0, Math.max(0, maxLen - 1)) + "…";
    }

    private static String formatMap(Map<String, Object> map) {
        if (map == null || map.isEmpty()) {
            return "—";
        }
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Object> e : map.entrySet()) {
            if (e.getKey() == null) {
                continue;
            }
            sb.append("<div><code>").append(esc(e.getKey())).append("</code> = ");
            sb.append(esc(formatNullable(e.getValue()))).append("</div>");
        }
        return sb.toString();
    }

    private List<Map<String, Object>> parseListOfMap(Object jsonText) {
        if (jsonText == null) return null;
        String s = String.valueOf(jsonText).trim();
        if (s.isEmpty() || "null".equalsIgnoreCase(s) || "[]".equals(s)) return null;
        try {
            return objectMapper.readValue(s, new TypeReference<List<Map<String, Object>>>() {});
        } catch (Exception e) {
            return null;
        }
    }

    private String extractSummaryFromProfile(String profileJson) {
        if (profileJson == null) return null;
        String s = profileJson.trim();
        if (s.isEmpty()) return null;
        try {
            JsonNode root = objectMapper.readTree(s);
            String summary = root.path("summary").asText(null);
            if (summary != null && !summary.trim().isEmpty()) return summary.trim();
            // 某些模型输出可能把总结写在 notes/overview
            String notes = root.path("notes").asText(null);
            if (notes != null && !notes.trim().isEmpty()) return notes.trim();
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    private static String firstNonBlank(String... arr) {
        if (arr == null) return null;
        for (String s : arr) {
            if (s != null && !s.trim().isEmpty()) return s.trim();
        }
        return null;
    }

    private static String formatNullable(Object v) {
        if (v == null) return "—";
        String s = String.valueOf(v).trim();
        return s.isEmpty() ? "—" : s;
    }

    private static String asString(Object v) {
        if (v == null) return null;
        String s = String.valueOf(v);
        return s == null ? null : s.trim();
    }

    private static Double asDouble(Object v) {
        if (v instanceof Number) return ((Number) v).doubleValue();
        if (v == null) return null;
        try { return Double.parseDouble(String.valueOf(v)); } catch (Exception e) { return null; }
    }

    private static List<String> asStringList(Object v) {
        if (!(v instanceof List)) return null;
        List<?> list = (List<?>) v;
        java.util.ArrayList<String> out = new java.util.ArrayList<>();
        for (Object o : list) {
            if (o == null) continue;
            String s = String.valueOf(o).trim();
            if (!s.isEmpty()) out.add(s);
        }
        return out;
    }

    private static String esc(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
