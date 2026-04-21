# 旧版 HTML → 新版 `front-spa` 映射与缺口清单（core_plus）

更新时间：2026-04-20  
目标：旧 `C:\Users\28994\Desktop\front\*.html` 全部可下线；以 `C:\Users\28994\Desktop\front\front-spa` 作为唯一入口；新版体验不弱于旧版且逻辑更清晰。

## 1) 旧页面与新路由一一对应（现状）

### 账号与入口
- **旧**：`index.html`（登录页，含“后台通道”）
  - **新版**：`/login` → `src/ui/views/LoginPage.vue`
  - **现状**：已实现账号+密码登录（`/api/user/login`）；但旧版具备“管理员通道”提示与跳 `admin.html` 的流程，新版尚未覆盖 admin 登录分流与 admin UI（二期）。
  - **缺口/风险**：
    - 旧版支持“记住密码 rememberUser”；新版未实现（可选，建议不保留）。
    - 旧版含“验证码登录”标签但未开放；新版也未实现（可不做）。
    - 新版仍有 `去旧版注册页` 链接，需要替换为 SPA 注册页。

- **旧**：`register.html`（注册）
  - **新版**：缺失（一期必须补：`/register`）
  - **旧版行为**：调用 `POST /api/user/register`；用户名可用性检查是前端模拟（非后端校验接口）。
  - **一期结论**：必须迁移为 SPA 页面（不再跳 html），并用后端真实校验（如无接口则先仅提交注册，错误提示由后端返回）。

### 首页与全局壳
- **旧**：`home.html`（控制台首页 + 导航壳 + 搜索 + 首页雷达图 + 推荐协作 + 技术精选）
  - **新版**：`/home` → `HomePage.vue` + 全局壳由 `src/ui/App.vue` 提供
  - **现状差异**：
    - 旧版有较完整的“壳”（抽屉/头像/菜单/未读红点/多入口），新版壳也有，但两套信息架构尚未统一到“模块化路由+面包屑+标题区”的规范。
    - 旧版首页包含“我的信用雷达图（首页内）”；新版首页目前以信息流/卡片为主，信用雷达图在 `/credit` 页面为主。
  - **一期建议**：把“壳”统一为 SPA 的 `AppShell`，旧版 `shell-nav.js/css` 全量弃用。

### 信用评估
- **旧**：`credit.html`（多作者对比≤5、雷达图、洞察、趋势、查询历史、导出 CSV/HTML、AI画像、可解释性明细）
  - **新版**：`/credit` → `CreditPage.vue`
  - **现状**：新版功能基本覆盖旧版，且实现上更接近工程化（ECharts init/dispose、批量删除等已存在）。
  - **待统一点**：
    - 场景值映射：旧版 `综合/后端/前端/协作/合规` → 后端场景 `综合/核心开发者/辅助贡献`；新版也做了映射，但需沉淀为共享方法，避免散落。
    - 401/错误提示：旧版 `request.js` 有较完整的“登录引导/429提示/traceId”等；新版 `ui/api.ts` 目前更简洁，需要补齐统一体验。

### 协作撮合与风控
- **旧**：`collab.html`（项目大厅/我的发布/我的申请/申请审核/匹配推荐/互评/查看互评）
  - **新版**：
    - `/projects` → `ProjectsPage.vue`（当前：项目列表+新建；更像“collab/project”而不是旧版的完整协作大厅）
    - `/projects/:projectId` → `ProjectDetailPage.vue`（项目详情+团队画像+导出报告）
    - `/projects/:projectId/board` → `BoardPage.vue`（看板）
    - `/issues/:issueId` → `IssueDetailPage.vue`（任务详情）
  - **缺口（一期必须补）**：
    - “协作大厅/我的发布/我的申请/审核”这条线，旧版完整，新版目前缺少同等信息架构与入口（新版偏“项目管理”，旧版偏“协作撮合/风控”）。
    - “申请加入+风控拦截提示”：论文要求“信用分不足弹窗+终止流程”，旧版 `applyProject` 有引导，新版目前未体现完整的“min_credit→拦截提示”的前端体验（需要在项目详情或大厅中补齐）。
    - “匹配推荐/互评/互评列表”旧版在协作模块里完整，新版目前只在 `IssueDetailPage.vue` 体现了“验收互评”一部分；需要决定一期放在哪个页面（建议放项目详情页或协作大厅子页）。

### 消息通知
- **旧**：`msg.html`（消息列表+筛选+全部已读+删除未读+清空；点击协作消息跳协作、@提及跳社区；含 WebSocket 实时刷新）
  - **新版**：`/notifications` → `NotificationsPage.vue`
  - **现状**：覆盖大部分能力，但仍存在 `打开旧版 msg.html` 按钮（一期必须删）。
  - **缺口/统一点**：
    - WebSocket 推送：旧版 `msg.html/home.html` 都连了 `/ws?token=` 并订阅 `/user/queue/notifications`；新版目前是“页面加载时拉列表并统计未读”，缺少实时推送（一期可选，但建议在“通知中心”补齐，最能体现“好用”）。

### 个人中心/开发者主页（非常关键）
- **旧**：`user.html`（个人中心 + 公开开发者主页二合一；头像上传/资料维护/绑定GitHub/刷新信用/我的收藏/关注动态/技术标签/关注粉丝/徽章墙）
  - **新版**：缺失（一期建议至少补“我的个人中心”基础版；公开开发者主页可二期）
  - **一期必须覆盖的最小集**（与 core_plus 对齐）：
    - GitHub 绑定入口（OAuth2）与绑定状态展示
    - 资料维护（至少：nickname/bio/techTags；头像可选但建议保留）
    - “跳转到信用/协作/通知”的清晰入口
  - **风险**：你论文中的 OAuth2 绑定是核心流程；而当前 SPA 路由里没有 `callback` 回调页与个人中心入口，会导致“流程不闭环”。

### 后台管理（二期）
- **旧**：`admin.html`（用户/举报/反馈/权重配置/系统配置/帖子审核/日志/登录审计等）
  - **新版**：缺失（按你的一期 core_plus 选择，暂不做；但要保证后续可作为 `/admin/*` 接入）

## 2) 旧页面中“已存在但新版未覆盖”的功能点（按优先级）

### P0（一期必须补齐，否则无法下线旧版）
- **注册页**：`register.html` → 新增 `/register`
- **个人中心入口**：`user.html` 的“我的资料+GitHub 绑定”最小闭环 → 新增 `/me`（或 `/user`）
- **OAuth2 GitHub 绑定回调页**：论文流程要求 code 回传后端完成绑定 → 新增 `/auth/callback`（或 `/oauth/callback`）
- **删掉所有跳旧 html 的兜底**：\n  - `LoginPage.vue` 的 `register.html`\n  - `NotificationsPage.vue` 的 `msg.html`\n  - 任何 `window.location.href = '../xxx.html'`

### P1（一期强烈建议补，直接影响“好用”）
- **通知实时性（WebSocket）**：把旧版 `/ws?token=` + 订阅 `/user/queue/notifications` 迁移到 SPA（建议只在 AppShell 建一次连接，并统一派发到通知角标/列表刷新）。
- **协作大厅信息架构**：让新版 `/projects` 具备旧版 `collab.html` 的四个 tab 视角（大厅/我的发布/我的申请/审核），否则“协作撮合与风控”这个论文亮点在前端会显得弱。
- **风控拦截体验**：当申请信用分不达标时，弹窗提示与下一步指引（去信用页查看差距/建议）。

### P2（二期或一期后半段）
- 公开开发者主页（`user.html?username=...`）迁移为 `/u/:username` 或 `/profile/:username`
- 后台管理（`/admin/*`）整体迁移

## 3) 代码层面的迁移策略（避免越改越乱）
- 旧版 `front/request.js` 提供了完整的“统一请求、401引导、429提示、loading、traceId”等逻辑；新版 `src/ui/api.ts` 已有 axios + token 注入 + unwrap，但能力不完整。\n  **策略**：把旧版 `request.js` 的“体验规则”迁入 SPA 的 `shared/http`（不是复制代码，而是迁移设计点）。\n- 旧版大量页面复用 `shell-nav.js/css`；新版已经有 `App.vue` 的壳。\n  **策略**：以 SPA 的壳为唯一壳，旧 `shell-nav.*` 最终删除。\n\n---\n\n## 4) 下一步行动（按清单推进）\n- 先补 P0：`/register`、`/me`（个人中心最小闭环）、`/auth/callback`、移除所有旧 html 兜底跳转\n- 再补 P1：WebSocket 通知、协作大厅四视角、风控拦截的明确提示与引导\n+\n*** End Patch"}]}操作 failed: Unrecognized token 'json': line 1 column 1 (char 0) in ApplyPatch tool call parameters. Need freeform string not JSON. Let's call again freeform. We'll craft patch. Need one file per call. Do for first file then second. Let's retry. We'll do functions.ApplyPatch with FREEFORM. In commentary. We'll add file. Ensure plus lines no leading spaces. Let's call. We'll do two separate tool calls sequentially. We'll start first. Then second. Let's. We'll need not JSON wrapper. Let's call. Не parallel. We'll do now. We'll include full patch with correct grammar. Let's proceed. We'll create first file. Then second. We'll keep concise. Trim. We'll not use tables. It's okay. Let's apply patch. Need absolute path. Use. Great. We'll now. We'll go. In plan mode allowed. Let's call tool. }
