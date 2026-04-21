const STATE_KEY = 'oauth_github_state'
const REDIRECT_KEY = 'oauth_github_redirect'

function randomState() {
  try {
    const a = new Uint8Array(16)
    crypto.getRandomValues(a)
    return Array.from(a, (b) => b.toString(16).padStart(2, '0')).join('')
  } catch {
    return String(Date.now()) + Math.random().toString(16).slice(2)
  }
}

export function prepareGithubOAuthState() {
  const state = randomState()
  try {
    sessionStorage.setItem(STATE_KEY, state)
  } catch {
    /* ignore */
  }
  return state
}

export function saveGithubOAuthRedirect(redirectTo: string) {
  const v = String(redirectTo || '').trim()
  if (!v) return
  try {
    sessionStorage.setItem(REDIRECT_KEY, v)
  } catch {
    /* ignore */
  }
}

export function consumeGithubOAuthState(expectedFromQuery: string | null | undefined): boolean {
  const expected = String(expectedFromQuery || '').trim()
  if (!expected) return false
  let saved = ''
  try {
    saved = sessionStorage.getItem(STATE_KEY) || ''
  } catch {
    return false
  }
  try {
    sessionStorage.removeItem(STATE_KEY)
  } catch {
    /* ignore */
  }
  return !!saved && saved === expected
}

export function consumeGithubOAuthRedirect(defaultValue = '/home') {
  let v = ''
  try {
    v = sessionStorage.getItem(REDIRECT_KEY) || ''
  } catch {
    return defaultValue
  }
  try {
    sessionStorage.removeItem(REDIRECT_KEY)
  } catch {
    /* ignore */
  }
  v = String(v || '').trim()
  // 仅允许站内相对路径，避免被注入到外部站点
  if (!v || !v.startsWith('/')) return defaultValue
  return v
}
