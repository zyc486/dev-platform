package com.zhaoyichi.devplatformbackend.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * GitHub 原始指标计数沉淀表。
 *
 * <p>对应 V2 算法使用的 T/CESA 团体标准附录 B 指标（OI/IC/OP/PRR/MP）与合规多属性指标
 * （License/Workflow/Security）。受 GitHub {@code /users/{u}/events} API 硬限制（近 90 天、最多 300 条）
 * 影响，带 90d 后缀的指标是 90 天窗口内的近似计数，论文中需在"数据采集范围"一节明确声明该约束。</p>
 */
@Data
@TableName("github_event_stats")
public class GithubEventStats {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String githubUsername;

    /** OI：作为作者新开 Issue 总数，来源 /search/issues?q=author:{u}+type:issue */
    private Integer openIssueCount;
    /** OP：作为作者新开 PR 总数，来源 /search/issues?q=author:{u}+type:pr */
    private Integer openPrCount;
    /** MP：已合并 PR 数，来源 /search/issues?q=author:{u}+type:pr+is:merged */
    private Integer mergedPrCount;

    /** IC：近 90 天 IssueCommentEvent 次数（受 events API 窗口限制） */
    @TableField("issue_comment_90d")
    private Integer issueComment90d;
    /** PRR：近 90 天 PullRequestReviewEvent 次数 */
    @TableField("pr_review_90d")
    private Integer prReview90d;
    /** 近 90 天 PushEvent 次数（作为 commit 频率的代理） */
    @TableField("push_event_90d")
    private Integer pushEvent90d;
    /** 近 90 天关闭 Issue 事件数（IssuesEvent action=closed） */
    @TableField("close_issue_90d")
    private Integer closeIssue90d;
    /** 近 90 天关闭 PR 事件数（PullRequestEvent action=closed） */
    @TableField("close_pr_90d")
    private Integer closePr90d;

    /** 合规采样的仓库数（分母，取最多前 N 个公开仓库） */
    private Integer sampledRepoCount;
    /** LICENSE 存在的仓库数（/repos/{u}/{r}/license 非 404） */
    private Integer licensePresentCount;
    /** .github/workflows 非空的仓库数（CI/CD 存在率） */
    private Integer workflowPresentCount;
    /** SECURITY.md 存在的仓库数（作为 CVE 漏洞响应能力的代理，因 vulnerability-alerts 仅管理员可见） */
    private Integer securityPresentCount;

    /** 账号活跃天数（now - created_at） */
    private Long activeDays;
    /** 公开仓库数（userData.public_repos） */
    private Integer publicRepos;
    /** 粉丝数（userData.followers） */
    private Integer followers;

    /**
     * 合规采样路径上遍历的公开仓库 {@code stargazers_count} 之和（与 /users/{u}/repos 列表一致），
     * 作为「仓库社区认可度」代理，与 followers 一并融入协作维，缓解「大号低频互动」被 90d 事件低估的问题。
     */
    @TableField("repo_stars_total")
    private Integer repoStarsTotal;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime fetchTime;
}
