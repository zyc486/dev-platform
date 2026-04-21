# V1 vs V2 信用评分算法对比与论文写作支撑（可直接入论文）

> 面向毕业设计《基于 Spring Boot 的开源开发者协作与信用评价平台》：本文档给出 **旧算法 V1（代理指标/经验参数）** 与 **新算法 V2（文献依据/客观化重构）** 的差异对比、改进点证据链、实验设计建议与风险声明。  
> 适用于论文“算法设计与改进”“客观性证据链”“实验对比与消融分析”“威胁与局限性”章节。

---

## 1. 一句话总结（答辩口径）

- **V1**：用少量可得字段（followers、public_repos、created_at）构造评分，存在经验常数、代理指标与主观阈值。  
- **V2**：对齐 **T/CESA 团体标准** 指标（OI/IC/OP/PRR/MP）并引入 **AHP/CRITIC/分位数/Sigmoid** 等客观方法，形成可追溯的“指标—权重—阈值—函数”证据链。

---

## 2. 维度与指标体系对比（指标客观化）

### 2.1 维度定义

| 维度 | V1 定义（旧） | V2 定义（新） | 文献/标准依据 |
|---|---|---|---|
| Stability | “代码稳定性”≈账号年龄+仓库数 | “账号成熟度与持续活跃度” | 文献 H/I（活动与健康/趋势；Sigmoid） |
| PR Quality | “PR质量”由仓库数+粉丝代理 | “代码贡献质量”=合并率+评审+测试/CI | 标准 A + 文献 E |
| Collaboration | “协作”由粉丝代理+站内互评融合 | “协作行为”按 AHP 权重合成+互评融合 | 文献 B（AHP一致性） |
| Compliance | “合规”≈活跃天数线性映射 | “多属性合规”=License/Security/Review/CI | 文献 F（可信性分解） |

### 2.2 核心原始指标对比

| 指标类 | V1 | V2 |
|---|---|---|
| 贡献行为 | 未采集（用 repos/followers 代替） | **OI/IC/OP/PRR/MP**（T/CESA） |
| 活跃度 | created_at → activeDays | activeDays + 近 90 天 PushEvent（commit频率代理） |
| PR 质量 | repos/followers 代理 | MP/OP（合并率）+ PRR（评审）+ Workflow（测试/CI代理） |
| 合规 | 不涉及 License/CVE/CI | License 率 + Workflow 率 + SECURITY.md 率 + PR评审率 |

---

## 3. 数学形式对比（函数客观化）

### 3.1 Stability（稳定/成熟度）

**V1（旧）**  
- 对数归一化：
  - \(ageScore = \\min(100, \\log_{10}(days+1) / \\log_{10}(1825) \\times 100)\)  
  - \(repoScore = \\min(100, \\log_{10}(repos+1) / \\log_{10}(51) \\times 100)\)  
- 加权：\(stability = 0.4\\cdot ageScore + 0.6\\cdot repoScore\)
- **问题**：1825/51 与 0.4/0.6 缺文献支撑，属于经验拍定。

**V2（新）**  
- 先对三项做归一化/稳健处理：  
  - \(z_1 = \\log(1+activeDays)\\)（上界取 10 年覆盖）  
  - \(z_2 = \\log(1+pushEvents\\_{90d})\\)（上界取活跃区间覆盖）  
  - \(z_3 = mergeRate = MP/OP\)（OP 样本不足时用文献 E 全域均值 0.55 兜底）  
- 再做 Sigmoid 边际控制（文献 I 依据）：  
  - \(stability = 100\\cdot \\sigma(k(z-0.5))\)
- 权重采用相关性先验（文献 H/E），并预留成熟期 CRITIC 重算（文献 D）。

### 3.2 PR Quality（PR 质量/贡献质量）

**V1（旧）**  
- 经验函数：\(100\\times (1 - 1/(1+repos\\cdot0.5+followers\\cdot0.1))\)
- **问题**：repos/followers 不是 PR 质量的直接观测量。

**V2（新）**  
- \(prQuality = 0.50\\cdot mergeRate + 0.30\\cdot f(PRR) + 0.20\\cdot CI\\_rate\)  
- 权重来源：T/CESA 附录 B（MP/PRR/OP）归一化；mergeRate 依据文献 E。

### 3.3 Collaboration（协作）

**V1（旧）**  
- followers 对数作为社交影响力 + 站内互评混合（上限 50% 为经验设置）。

**V2（新）**  
- 直接采用文献 B Table 2 的 AHP 权重（CR=0.027 通过一致性检验）：  
  - IC 5.25%、PRR 7.43%、CloseIssue 9.71%、ClosePR 14.70%、OI 22.24%、OP 40.68%  
- 站内互评融合仍保留（用于平台闭环），但解释为“多源证据融合”而非替代 GitHub 真实行为。

> 注：OpenRank 属于“项目/社区网络”算法，若只做“单账号实时查询”难以构造协作图；论文可将其作为离线榜单/样例仓库实验展示，而不是强塞进单点评分。

### 3.4 Compliance（合规）

**V1（旧）**  
- \(compliance = \\min(99, 20 + activeDays/365\\cdot 10)\)  
- **问题**：与 License/安全/审查等合规要素不匹配。

**V2（新）**  
- 多属性合规（文献 F）：  
  - License 率、Security（以 SECURITY.md 代理）、PR 评审率、Workflow/CI 率  
- 权重由 AHP/共识给出，并可在成熟期用数据方法微调。

---

## 4. 权重与阈值对比（权重客观化 + 阈值客观化）

### 4.1 总分权重

| 场景 | V1（旧，写死或人工配置） | V2（新，冷启动推荐） | 依据 |
|---|---|---|---|
| 综合 | 0.25/0.30/0.25/0.20 | 0.20/0.30/0.30/0.20 | 标准 A / 文档 §3.3 |
| 核心开发者 | 旧配置不一致 | 0.15/0.45/0.25/0.15 | 标准 A / 文档 §3.3 |
| 辅助贡献 | 旧配置不一致 | 0.20/0.25/0.40/0.15 | 标准 A / 文档 §3.3 |

> 论文写法建议：说明“系统允许管理员在 `scene_weight_config` 进行运营配置，但论文实验采用文献推荐默认值，以保证可复现与客观性”。

### 4.2 等级阈值

- **V1**：固定阈值 85/70/60（主观）。  
- **V2**：分位数阈值 P20/P50/P80（文献 C 20/80 法则）；当样本不足（例如 N<30）时回退固定阈值，避免统计不显著导致“伪客观”。

---

## 5. 数据采集与可行性声明（论文必须写的“约束与降级”）

### 5.1 GitHub API 约束

- `/users/{u}/events`：只提供**近 90 天**且最多 300 条 → IC/PRR/Push/Close* 只能做 90 天窗口近似。  
- `/repos/{u}/{r}/vulnerability-alerts`：仅仓库管理员可见 → 第三方平台无法评估他人仓库 CVE → **用 SECURITY.md 存在率做安全响应能力代理**并在论文声明。

### 5.2 降级策略（严谨写法）

当 GitHub 不可达/限流时：
- 不“瞎算”新指标，而是读取库内最近快照（credit_score / github_event_stats）；  
- 报告中明确标注“本次未更新，原始指标缺失原因”；  
- 论文中把这一点写为“工程可用性约束下的鲁棒性设计”。

---

## 6. 实验对比建议（论文可直接用）

### 6.1 A/B 对比（V1 vs V2）

对同一批账号 \(U\)（例如 50/100 个）：
- 输出 V1 总分、V2 总分，比较：
  - Spearman 相关系数（排名一致性）
  - Kendall’s Tau（排序一致性）
  - Top20% 集合重合率（与文献 C 的 20/80 对齐）

### 6.2 消融实验（证明每个“客观化”模块的贡献）

- 去掉 Sigmoid（改线性）→ 观察头部大号是否“过饱和/虚高”  
- 去掉分位数阈值（回固定阈值）→ 观察等级分布是否失衡  
- 去掉 AHP 权重（均匀权重）→ 观察协作维度区分度是否下降  

### 6.3 案例分析（答辩最有效）

选 3 类账号：
1) 新号（活跃少）  
2) 高贡献但粉丝少（V1 易低估、V2 能体现）  
3) 粉丝多但贡献少（V1 易高估、V2 会抑制）  
输出“源数据→维度→总分→等级”的解释性报告截图。

---

## 7. 客观性证据链（可直接写进论文结论/答辩）

1) **团体标准 T/CESA**：指标体系合法（OI/IC/OP/PRR/MP）  
2) **ICSE-SEIP 2024**：AHP 权重一致性 CR=0.027（严格客观）  
3) **《软件学报》2018**：20/80 法则 → 分位数阈值依据  
4) **《计算机科学》2024**：CRITIC 赋权 → 成熟期数据驱动权重依据  
5) **ICSE 2014**：PR 合并率是核心质量指标  
6) **2025 活跃度研究**：Sigmoid 非线性控制边际效应

---

## 8. 代码/实现映射（便于论文写“系统实现”）

- 指标采集：`service/credit/GithubMetricsCollector` → `github_event_stats`  
- 维度计算：`service/credit/CreditScoreAlgoV2Service`  
- 场景加权：`service/credit/CreditScoreV2Service.loadSceneWeights + weightedTotal`  
- 阈值等级：`service/credit/CreditThresholdService` → `credit_threshold`  
- 可解释报告：`service/credit/CreditReportHtmlBuilder` / `CreditExplainDetailVO`

---

## 9. 写作模板（可复制）

### 9.1 “为什么 V1 主观”

> 旧算法以 followers、public_repos 等可得字段作为质量/协作代理，并引入对数归一化常数（1825、51）、维度内权重（0.4:0.6）和固定阈值（85/70/60）。这些参数缺乏可追溯文献依据，且未进行敏感性分析，因此存在主观性风险。

### 9.2 “为什么 V2 客观”

> 新算法在指标层面遵循 T/CESA 团体标准引入 OI/IC/OP/PRR/MP；在权重层面采用 ICSE-SEIP 2024 的 AHP 权重（CR=0.027 通过一致性检验），并预留 CRITIC 客观赋权；在阈值层面采用 20/80 法则对应的分位数阈值；在函数层面用 Sigmoid 控制边际效应，形成“指标—权重—阈值—函数”四重客观化证据链。

---

## 10. 局限性与未来工作（建议写法）

- GitHub Events API 的 90 天窗口限制导致部分指标为近似统计；  
- CVE/Vulnerability Alerts 存在权限限制，采用 SECURITY.md 作为代理指标；  
- 当系统样本量不足时，分位数阈值回退固定阈值以保证统计稳定性；  
- 未来可在离线任务中引入 OpenRank（基于项目协作图）用于仓库/社区榜单，而非单点实时查询。

