# HTML → Vue3 SPA 全量迁移对照文档（功能最全面）

适用范围：`C:\Users\28994\Desktop\front`（旧版静态 HTML）→ `C:\Users\28994\Desktop\front\front-spa`（Vue3 SPA）。

本文目标：以“**功能最全面**”为验收标准——旧版所有核心能力在 Vue3 SPA 中都能找到等价实现（允许 UI/交互升级，但**不允许能力缺失**）。

---

## 1. 现状结论（摘要）

- **Vue3 SPA 当前未实现旧版全量功能**，存在明确缺口：
  - **管理员后台**：Vue3 版 `AdminPage` 目前为空壳；旧版 `admin.html` 功能覆盖面极广（用户/举报/反馈/权重/系统配置/帖子审核/日志/登录审计/导出等）。
  - **个人中心超集能力缺失**：旧版 `user.html` 内含收藏、关注/粉丝、关注动态、徽章、改密、刷新信用、公开主页模式等；Vue3 `MePage.vue` 目前仅迁移基础资料/头像/GitHub 绑定/技术标签。
  - **登录/注册能力缩水**：旧版登录含验证码登录、管理员登录、记住密码；Vue3 登录仅账号密码。旧版注册含验证码/强度/用户名检查；Vue3 注册仅基础字段。
- **存在高优先级安全风险（旧版遗留到新版）**：
  - GitHub OAuth **缺少 `state`**：存在 OAuth CSRF/绑定劫持风险。
  - token 存 `localStorage`：一旦出现 XSS，token 可被读取（结构性风险）。

---

## 2. 迁移基线与验收标准

### 2.1 基线（旧版页面清单）

旧版入口与功能页面（位于 `Desktop/front` 根目录）：

- `index.html`：登录（密码/验证码/管理员 Tab、记住密码）
- `register.html`：注册（手机号/验证码/密码强度/用户名检查）
- `home.html`：首页（摘要、推荐、搜索等）
- `credit.html`：信用分析/对比/导出/AI画像/趋势/历史
- `rank.html`：信用排行榜
- `collab.html`：协作大厅（大厅/我的发布/我的申请/申请审核/匹配推荐/互评/互评记录/关闭/完成）
- `publish.html`：发布协作项目
- `community.html`：社区（帖子、评论、热门标签、社群、申请、置顶/精华、发布等）
- `msg.html`：消息通知中心
- `user.html`：个人中心（“超集”：公开主页模式、关注/收藏/徽章/改密/刷新信用等）
- `callback.html`：GitHub OAuth 回调绑定
- `help.html`：帮助中心
- `feedback.html`：意见反馈
- `admin.html`：管理员后台

共用脚本：

- `request.js`：统一 API 访问/错误处理/401 引导/traceId/429 提示/可配置 baseUrl
- `shell-nav.js`：统一导航与鉴权拦截（页面级）

### 2.2 验收标准（“功能最全面”）

- **页面能力覆盖**：旧版所有页面能力在 SPA 中有对应路由/页面/组件承接。
- **接口覆盖**：旧版出现过的关键接口调用，在 SPA 中要么保留等价调用，要么明确由新接口替代（需后端兼容或前端统一）。
- **关键流程可闭环**：登录/注册 → 绑定 GitHub → 生成信用画像 → 协作发布/申请/审核/完成互评 → 消息通知 → 个人中心（收藏/关注/徽章/安全）→ 管理后台。

---

## 3. 新旧路由/页面对照（第一版）

> 说明：Vue3 路由来自 `front-spa/src/ui/router.ts`。

| 旧版页面 | 旧版能力概述 | Vue3 当前路由/页面 | 状态 |
|---|---|---|---|
| `index.html` | 密码/验证码/管理员登录、记住密码 | `/login` `LoginPage.vue` | **缺失**：验证码登录、管理员登录、记住密码 |
| `register.html` | 手机号+验证码、密码强度、用户名检查 | `/register` `RegisterPage.vue` | **缺失**：验证码/强度/用户名检查（功能缩水） |
| `callback.html` | OAuth 绑定（需已登录） | `/auth/callback` `AuthCallbackPage.vue` | **部分实现**：新增“未登录 OAuth 登录”，但缺少 `state` |
| `home.html` | 摘要/推荐/热榜/搜索等 | `/home` `HomePage.vue` | **大体具备**（需再对照细节） |
| `credit.html` | 信用查询/对比/导出/AI画像/趋势/历史 | `/credit` `CreditPage.vue` | **大体具备**（需对照导出与权限逻辑） |
| `rank.html` | 排行榜 | `/rank` `RankPage.vue` | 待核对 |
| `collab.html` | 协作大厅+审核+匹配+互评 | Vue3 现有：`/projects` 等（协作模型疑似重构） | **高风险差异**：旧接口与新模型不一致，需专项对照 |
| `publish.html` | 发布协作项目（`/api/collab/publish`） | `/publish` `PublishPage.vue`（`/api/collab/project/create`） | **接口不一致**：需统一/兼容 |
| `community.html` | 帖子/评论/社群/审核/置顶精华 | `/community` `CommunityPage.vue` | 待核对（重点：置顶/精华/审核能力） |
| `msg.html` | 消息筛选、已读、删除、清空、跳转 | `/notifications` `NotificationsPage.vue` | **部分实现**：功能接近，但登录态判断不响应式风险 |
| `user.html` | 个人中心超集（收藏/关注/徽章/改密/公开主页等） | `/me` `MePage.vue` | **缺失大量子模块** |
| `help.html` | 帮助中心 | `/help` `HelpPage.vue` | 待核对 |
| `feedback.html` | 意见反馈（含提交） | `/feedback` `FeedbackPage.vue` | 待核对 |
| `admin.html` | 管理后台全套 | `/admin` `AdminPage.vue` | **缺失（空壳）** |

---

## 4. 缺失功能清单（必须补齐）

### 4.1 个人中心（旧 `user.html` 超集）必须补齐的模块

旧版 `user.html` 相关关键接口（用于验收与定位）：

- 徽章/成就：`GET /api/badge/user/{id}`
- 刷新信用：`POST /api/credit/refresh`
- 我的发布：`GET /api/post/myList?userId=...`
- 我的收藏：`GET /api/favorite/list`，`POST /api/favorite/toggle`，`GET /api/favorite/check?...`
- 关注动态：`GET /api/feed/following`
- 关注/粉丝/关注状态/操作：
  - `GET /follow/my`
  - `GET /follow/fans`
  - `GET /follow/status?followUserId=...`
  - `POST /follow/operate?followUserId=...`
- 安全：改密：`POST /api/user/changePassword`
- 公开主页（查看他人资料）：`GET /api/profile/{username}`

Vue3 当前 `MePage.vue` 仅覆盖：

- `GET /api/user/info`
- `POST /api/user/updateProfile`
- `POST /api/user/updateTags`
- `POST /api/user/unbind`
- `POST /api/user/uploadAvatar`
- GitHub OAuth 授权跳转（前端拼接 authorize URL）

**迁移建议（SPA 信息架构）**：

- `GET /me`：账户概览（现有 `MePage` 可作为入口）
- `GET /me/profile`：基础资料/头像（现有内容）
- `GET /me/tech-tags`：技术标签（现有内容）
- `GET /me/security`：改密、授权信息（补齐 `/api/user/changePassword`、OAuth 绑定管理）
- `GET /me/favorites`：我的收藏（补齐 favorite 全套）
- `GET /me/follow`：关注与粉丝（补齐 follow 全套）
- `GET /me/feed`：关注动态（补齐 `/api/feed/following`）
- `GET /me/badges`：成就徽章（补齐 `/api/badge/user/...`）
- `GET /u/:username`：公开主页（承接旧版 publicMode + `/api/profile/{username}`，并支持关注/收藏/状态查询）

> 说明：如果希望“最全面但不扩路由”，也可继续在 `/me` 内做 Tab；但独立路由更利于权限、分享链接与测试。

### 4.2 管理员后台（旧 `admin.html`）必须补齐

旧版 `admin.html` 关键接口（用于验收）：

- 登录审计：`GET /api/admin/loginLogs?...`
- 仪表盘：`GET /api/admin/dashboard`
- 用户：`GET /api/admin/users`，`POST /api/admin/user/toggleStatus/{id}`，`POST /api/admin/user/resetPassword/{id}`，`POST /api/admin/credit/recalculate?...`
- 举报：`GET /api/admin/reports`，`POST /api/admin/report/handle/{id}`
- 反馈：`GET /api/admin/feedbacks`，`POST /api/admin/feedback/reply`
- 权重：`GET /api/admin/sceneWeights`，`POST /api/admin/sceneWeight/save`
- 系统配置：`GET /api/admin/systemConfigs`，`POST /api/admin/systemConfig/save`
- 帖子审核：`GET /api/admin/posts/pending`，`POST /api/admin/post/review/{id}`
- 日志：`GET /api/admin/logs/admin`，`GET /api/admin/logs/query`，`GET /api/admin/logs/export?...`

Vue3 现状：

- `AdminPage.vue` 为占位内容，且 SPA 源码内未检索到任何 `/api/admin/` 调用。

**迁移建议（SPA 信息架构）**：

- `/admin`：仪表盘
- `/admin/users`：用户管理
- `/admin/reports`：举报处理
- `/admin/feedbacks`：反馈管理
- `/admin/weights`：权重配置
- `/admin/system-configs`：系统配置
- `/admin/posts/pending`：帖子审核
- `/admin/logs`：系统日志
- `/admin/login-logs`：登录审计

并在路由守卫中补充“**admin 角色**”校验（当前仅校验 token，不校验角色）。

### 4.3 登录/注册能力补齐

旧版 `index.html`：

- 验证码登录（UI + 交互）
- 管理员登录 Tab（UI + 交互）
- 记住密码（`rememberUser`）

旧版 `register.html`：

- 短信验证码输入（测试阶段可简化，但能力存在）
- 密码强度提示 + 更严格规则
- 用户名可用性检测（旧版为模拟）

Vue3 版需要补齐：至少把这些能力以“可选策略”迁入 SPA（即使后端暂不支持短信，前端也应提供一致入口并给出可用提示/降级策略）。

---

## 5. 接口差异与兼容策略（必须明确）

### 5.1 协作发布接口不一致

- 旧版发布：`POST /api/collab/publish`（`publish.html`）
- Vue3 发布：`POST /api/collab/project/create`（`PublishPage.vue`）

**要求（全量迁移）**：

- 要么后端同时兼容两者；
- 要么前端统一到“最终接口”，同时确保旧版功能语义不丢失（title/content/minCredit 等字段对齐）。

### 5.2 旧版 `request.js` 的“统一体验能力”在 SPA 中缺失

旧版具备：

- 401/403 引导（支持 `logoutOn401: false` 防误伤）
- 429 限流提示
- traceId 拼接错误提示
- 可配置 baseUrl

Vue3 `api.ts` 当前只有：加 Authorization + 401 logout。

**要求（全量迁移）**：SPA 需要实现等价的“统一错误体验层”，否则同样的接口在 SPA 下会出现提示不一致、误清登录态、缺少引导等问题。

---

## 6. 安全风险与修复要求（迁移同时完成）

### 6.1 GitHub OAuth 必须加入 `state`

现状：旧版与 Vue3 版都缺少 `state`。

修复要求：

- 发起授权时生成随机 `state`（建议存 `sessionStorage`），并在 authorize URL 中带上 `&state=...`
- 回调页校验 `state` 与本地一致，否则拒绝继续并提示用户重试
- 后端也应校验 `state`（更关键）

### 6.2 token 存储策略

现状：旧版与 Vue3 版都使用 `localStorage` 存 token。

建议（按安全强度排序）：

1. 最低成本：继续 token，但增加 CSP、严禁 `v-html`、严控可控 URL/HTML 注入点。
2. 推荐：使用 `HttpOnly + Secure + SameSite` Cookie 承载会话（前端不再能 JS 读取 token），降低 XSS 造成的会话泄露风险。

---

## 7. 验收清单（建议直接用作测试用例）

### 7.1 登录与注册

- [ ] 密码登录成功后，所有依赖登录态的页面无需刷新即可正确进入（SPA 内响应式更新）
- [ ] 验证码登录入口存在且可用（后端不支持则有清晰提示与降级方案）
- [ ] 管理员登录入口存在且可用，并能进入 `/admin` 全套功能
- [ ] 记住密码行为与旧版一致（或给出明确替代方案）
- [ ] 注册具备手机号验证码/密码强度/用户名检查（至少交互与校验等价）

### 7.2 GitHub OAuth

- [ ] 绑定流程带 `state` 且校验通过
- [ ] 已登录绑定：绑定成功后 `me` 信息刷新，AppShell 立即更新
- [ ] 未登录 OAuth 登录：可用则发放 token 并进入首页
- [ ] 失败路径提示清晰（code 缺失、state 不匹配、后端拒绝等）

### 7.3 个人中心（补齐的超集模块）

- [ ] 基础资料保存后刷新成功（头像/昵称/简介/手机/邮箱）
- [ ] 技术标签保存并回显
- [ ] 改密成功后可重新登录（错误提示正确）
- [ ] 徽章页可加载并展示（与旧版一致）
- [ ] 收藏列表可加载；收藏/取消收藏即时生效
- [ ] 关注/粉丝列表可加载；关注状态查询正确；关注/取消关注生效
- [ ] 关注动态可加载并可跳转到对应内容
- [ ] 公开主页 `/u/:username` 可访问，且支持关注/收藏与状态展示
- [ ] 刷新信用能力存在且调用成功（权限/提示一致）

### 7.4 协作与项目

- [ ] 发布协作项目字段与旧版一致（标题/内容/最低信用门槛等）
- [ ] 大厅列表、我的发布、我的申请、申请审核、匹配推荐、关闭/完成、互评/互评记录全可用
- [ ] 协作流的消息通知能到达通知中心

### 7.5 消息通知

- [ ] 筛选/已读/全部已读/删除未读/清空能力可用
- [ ] 登录态变化无需刷新（退出后立即不可操作；登录后立即可加载）
- [ ] 点击消息可正确导航到关联业务页面（协作/社群/安全等）

### 7.6 管理员后台

- [ ] 仪表盘/用户/举报/反馈/权重/系统配置/帖子审核/日志/导出/登录审计全部可用
- [ ] 所有敏感操作有二次确认与统一反馈
- [ ] 非 admin 用户无法访问（路由守卫 + 后端鉴权双保险）

---

## 8. 备注：当前已识别到的 SPA 迁移风险点（建议优先修）

- **登录态判断不响应式**：SPA 多处使用 `localStorage.getItem('token')` 做 computed 判断，可能导致“刚登录/刚退出 UI 不更新”的迁移问题。应统一用 `auth.token`（`ref`）作为单一真相源，并 `watch` 触发重载。
- **401 统一体验缺失**：Vue3 仅 `logout()`，缺少旧版 `request.js` 那种“引导/提示/可配置 logoutOn401”的能力，容易造成体验回退。

已按迁移文档做了一轮可落地的代码补齐，摘要如下。

已实现
1. GitHub OAuth state
新增 src/ui/oauthGithub.ts：prepareGithubOAuthState / consumeGithubOAuthState
MePage.vue 发起授权时带 state
AuthCallbackPage.vue 校验 state，失败则提示并中止
LoginPage.vue 中「GitHub 登录」同样带 state
2. 登录 / 注册（对齐旧版形态）
登录：密码 / 验证码（说明为演示+需后端）/ 管理员 三通道；记住密码（rememberUser）；管理员登录成功进 /admin
注册：用户名检测（演示规则）、验证码倒计时、密码强度条、与旧版相近的密码规则
3. 路由与权限
/u/:username 公开开发者主页 → UserProfilePage.vue（/api/profile/:username，关注/收藏）
/admin：仅 role === 'admin' 可进，否则提示并回首页（router.ts）
4. 个人中心超集（在 MePage.vue 用 Tab 承载）
安全：改密 /api/user/changePassword、刷新信用 /api/credit/refresh
我的发布、收藏、关注/粉丝、关注动态、徽章（对应旧版接口）
顶部增加 「公开主页」 跳转 /u/:username
5. HTTP 层与登录态
新增 src/ui/navigation.ts + main.ts 注册路由，避免 api 循环依赖
api.ts：401 清会话并跳转登录；403 / 429 提示；支持请求配置 skipAuthLogout（静默探测）
HomePage / NotificationsPage / CommunityPage / CreditPage / PublishPage / ProjectDetailPage：用 auth.token / auth.me 替代 localStorage，避免登录后 UI 不刷新
6. 发布项目双接口
先调 /api/collab/project/create，失败再调旧版 /api/collab/publish（字段映射 name→title、description→content）
7. 管理后台
你仓库里的 AdminPage.vue 已是完整后台（仪表盘、用户、举报、反馈、权重、系统配置、帖子审核、日志导出、登录审计）；我修正了 loadDashboard 对 axios 响应体的解析（resp.data.code / resp.data.data）。
本地构建说明
在本机执行 npm run build 时，若仍报 「stream did not contain valid UTF-8」，多半是部分 .vue 文件编码不是 UTF-8（涉及 AdminPage.vue、RankPage.vue、HelpPage.vue、FeedbackPage.vue 等）。请在编辑器中将这些文件 另存为 UTF-8（无 BOM） 后再构建。
npx tsc --noEmit 已通过，类型侧无报错。

尚未在一次迭代里完全对齐的项（文档里仍属大块）
旧版 collab.html 全量：大厅四 Tab、匹配推荐、互评等，与当前 /projects 产品形态不一致，需单独产品/接口对齐后再做，避免和业务后端分叉冲突。
验证码真实登录：依赖后端短信/验证码接口，目前前端仅保留入口与说明。
Token 改 HttpOnly Cookie：属架构级改造，未在本次改动中做。