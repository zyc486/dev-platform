## 开发计划与状态（自动同步）

> 说明：本文件用于记录“功能清单_实现对照与落地方案.md”中的新增功能落地状态。
> 约定：每完成一个模块（后端 + 前端接入），更新对应状态与变更点。

### 已完成（后端已落地、可运行）

- **信用：多作者对比/洞察/查询记录管理/筛选排行榜**  
  - **接口**：`POST /api/credit/compare`、`GET /api/credit/insights`、`DELETE /api/credit/queryHistory/{id}`、`DELETE /api/credit/queryHistory?ids=...`、`POST /api/credit/queryHistory/favorite/{id}`、`GET /api/credit/rank2`
  - **页面接入**：✅ 已完成
    - `front/credit.html`：雷达图多人对比、单人洞察维度进度条、历史记录星标/删除/批量删除无感刷新
    - `front/rank.html`：全面切换 `/api/credit/rank2`，新增高级筛选侧边栏（场景/等级/分数区间/标签/条数），骨架屏加载
  - **鉴权修复**：✅ `request.js` 新增 `openLoginGuide` 未登录弹窗引导，`/follow` 路径自动触发；统一 `Authorization: Bearer` 头通过 `Api.authHeaders()` 注入
  - **后端重构**：✅ `CreditScoreService` 抽离 `getAdvancedRankList/compareUsers/getInsights` 三个 Service 方法，`CreditScoreController.rank2` 下沉业务逻辑，新增 `CreditRankItemVO`

- **关注动态流**  
  - **接口**：`GET /api/feed/following`
  - **页面接入**：待完成（建议接入 `front/home.html` 或 `front/community.html`）

- **帮助中心（配置化接口）**  
  - **接口**：`GET /api/help`
  - **页面接入**：待完成（`front/help.html` 当前为静态内容）

- **首页增强**  
  - **接口**：`GET /api/home/hotDevelopers`、`GET /api/home/hotPosts`
  - **页面接入**：待完成（建议接入 `front/home.html`）

- **统一收藏（跨域收藏）**  
  - **接口**：`POST /api/favorite/toggle`、`GET /api/favorite/list`、`GET /api/favorite/check`
  - **页面接入**：待完成（建议接入 `front/user-collect.html` / `front/user.html`）

- **社群最小闭环（创建/申请/审核/公告/成员）**  
  - **接口**：`/api/community/*`
  - **页面接入**：待完成（当前 `front/community.html` 为“动态广场”，建议新增社群页面或加 Tab）

- **协作增强（匹配/双向评分）**  
  - **接口**：`GET /api/collab/match`、`POST /api/collab/rate`、`GET /api/collab/ratings/{projectId}`
  - **页面接入**：待完成（建议接入 `front/collab.html`）

- **管理员增强（重置密码/重算信用/帖子审核/日志导出）**  
  - **接口**：`POST /api/admin/user/resetPassword/{id}`、`POST /api/admin/credit/recalculate`、`GET /api/admin/posts/pending`、`POST /api/admin/post/review/{id}`、`GET /api/admin/logs/export`
  - **页面接入**：待完成（建议接入 `front/admin.html`）

- **开发者主页聚合**  
  - **接口**：`GET /api/profile/{username}`
  - **页面接入**：待完成（建议新增 `front/profile.html` 或改造 `front/user.html` 支持他人主页）

- **全局搜索**  
  - **接口**：`GET /api/search`
  - **页面接入**：待完成（建议接入 `front/home.html`）

- **个人资料字段扩展（avatar/nickname/bio）**  
  - **接口**：`POST /api/user/updateProfile`、`GET /api/user/info`
  - **页面接入**：待完成（建议扩展 `front/user.html` 表单字段）

### 待扩展（当前未落地/仅规划）

- **验证码注册/登录、设备管理、异常登录提醒**（verification_code / user_session / login_log 闭环）
- **私信 1v1**（dm_message / conversation，或 WebSocket）
- **动态增强**（转发、热门/推荐算法、楼中楼评论、附件）
- **信用指标增强（GraphQL/metrics 缓存）**（github_metrics/raw_data 结构化统计）
- **系统支撑**（Redis 缓存、限流、OpenAPI、Actuator 监控、异步导出任务）

