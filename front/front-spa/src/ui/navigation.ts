import type { Router } from 'vue-router'

let _router: Router | null = null

export function setAppRouter(r: Router) {
  _router = r
}

/** SPA 内跳转登录；若路由尚未注入则整页跳转 */
export function goLogin(redirectFullPath?: string) {
  const q = redirectFullPath ? `?redirect=${encodeURIComponent(redirectFullPath)}` : ''
  const path = `/login${q}`
  if (_router) {
    void _router.replace(path)
  } else {
    window.location.assign(path)
  }
}
