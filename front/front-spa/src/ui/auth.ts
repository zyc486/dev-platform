import { ref } from 'vue'

type Me = {
  id?: number
  username?: string
  githubUsername?: string
  role?: string
  avatar?: string
  nickname?: string
}

const token = ref<string>(localStorage.getItem('token') || '')
const initialMe = (() => {
  try {
    const raw = localStorage.getItem('user')
    return raw ? (JSON.parse(raw) as Me) : null
  } catch {
    return null
  }
})()
const me = ref<Me | null>(initialMe)

export const auth = {
  token,
  me,
  setLogin(t: string, u: any) {
    token.value = t || ''
    localStorage.setItem('token', token.value)
    me.value = u || null
    try {
      localStorage.setItem('user', JSON.stringify(me.value || null))
    } catch {}
  },
  logout() {
    token.value = ''
    localStorage.removeItem('token')
    me.value = null
    localStorage.removeItem('user')
    // 兼容旧版静态页留下的字段
    localStorage.removeItem('username')
  },
}

