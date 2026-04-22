import { createRouter, createWebHistory } from 'vue-router'
import { ElMessage } from 'element-plus'
import { auth } from './auth'

const LoginPage = () => import('./views/LoginPage.vue')
const RegisterPage = () => import('./views/RegisterPage.vue')
const AuthCallbackPage = () => import('./views/AuthCallbackPage.vue')
const MePage = () => import('./views/MePage.vue')
const HomePage = () => import('./views/HomePage.vue')
const ProjectsPage = () => import('./views/ProjectsPage.vue')
const ProjectDetailPage = () => import('./views/ProjectDetailPage.vue')
const BoardPage = () => import('./views/BoardPage.vue')
const IssueDetailPage = () => import('./views/IssueDetailPage.vue')
const CreditPage = () => import('./views/CreditPage.vue')
const CommunityPage = () => import('./views/CommunityPage.vue')
const NotificationsPage = () => import('./views/NotificationsPage.vue')
const PublishPage = () => import('./views/PublishPage.vue')
const RankPage = () => import('./views/RankPage.vue')
const FeedbackPage = () => import('./views/FeedbackPage.vue')
const HelpPage = () => import('./views/HelpPage.vue')
const AdminPage = () => import('./views/AdminPage.vue')
const UserProfilePage = () => import('./views/UserProfilePage.vue')
const DmPage = () => import('./views/DmPage.vue')
const ChatPage = () => import('./views/ChatPage.vue')

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', redirect: '/home' },
    { path: '/login', component: LoginPage, meta: { public: true } },
    { path: '/register', component: RegisterPage, meta: { public: true } },
    // GitHub OAuth 回调：既支持"已登录绑定"，也支持"未登录的 OAuth 登录"
    { path: '/auth/callback', component: AuthCallbackPage, meta: { public: true } },

    // 游客可浏览（页面内再针对需要 token 的操作做提示）
    { path: '/home', component: HomePage, meta: { public: true } },
    { path: '/credit', component: CreditPage, meta: { public: true } },
    { path: '/community', component: CommunityPage, meta: { public: true } },
    { path: '/rank', component: RankPage, meta: { public: true } },
    { path: '/help', component: HelpPage, meta: { public: true } },
    { path: '/feedback', component: FeedbackPage, meta: { public: true } },
    { path: '/u/:username', component: UserProfilePage, meta: { public: true } },

    // 需要登录
    { path: '/me', component: MePage },
    { path: '/projects', component: ProjectsPage },
    { path: '/projects/:projectId', component: ProjectDetailPage },
    { path: '/projects/:projectId/board', component: BoardPage },
    { path: '/issues/:issueId', component: IssueDetailPage },
    { path: '/notifications', component: NotificationsPage },
    { path: '/dm', component: DmPage },
    { path: '/chat', component: ChatPage },
    { path: '/publish', component: PublishPage },
    { path: '/admin', component: AdminPage },
  ],
})

router.beforeEach(async (to) => {
  if (to.meta.public) return true
  if (!auth.token.value) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }
  if (to.path.startsWith('/admin')) {
    const role = auth.me.value?.role
    if (role !== 'admin') {
      ElMessage.warning('需要管理员权限')
      return { path: '/home' }
    }
  }
  return true
})

export default router
