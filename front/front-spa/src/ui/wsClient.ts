import { Client } from '@stomp/stompjs'
import SockJS from 'sockjs-client'
import { notificationsStore } from './notificationsStore'
import { auth } from './auth'

type WsState = 'idle' | 'connecting' | 'connected' | 'error'

let client: Client | null = null
let state: WsState = 'idle'
let lastToken = ''

function wsUrlWithToken(token: string) {
  // 后端握手拦截器从 URL query 的 token 参数解析 JWT
  const raw = token ? token.trim() : ''
  const t = raw.startsWith('Bearer ') ? raw : `Bearer ${raw}`
  const base = (import.meta as any).env?.VITE_API_BASE || ''
  const url = `${base}/ws?token=${encodeURIComponent(t)}`
  return url
}

export const wsClient = {
  getState() {
    return state
  },

  connect() {
    const token = auth.token.value || ''
    if (!token) return
    if (client && state === 'connected' && lastToken === token) return

    wsClient.disconnect()
    lastToken = token
    state = 'connecting'

    const url = wsUrlWithToken(token)
    const c = new Client({
      // 使用 SockJS（后端端点启用了 withSockJS）
      webSocketFactory: () => new SockJS(url) as any,
      reconnectDelay: 5000,
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,
      debug: () => {},
    })

    c.onConnect = () => {
      state = 'connected'
      // 用户级队列：/user/queue/notifications
      c.subscribe('/user/queue/notifications', () => {
        // 收到任何推送都认为列表发生变化：刷新角标 + 通知页可自动 reload
        notificationsStore.bump()
      })
    }

    c.onStompError = () => {
      state = 'error'
    }
    c.onWebSocketError = () => {
      state = 'error'
    }
    c.onDisconnect = () => {
      if (state !== 'idle') state = 'idle'
    }

    client = c
    c.activate()
  },

  disconnect() {
    try {
      if (client) {
        client.deactivate()
      }
    } finally {
      client = null
      state = 'idle'
      lastToken = ''
    }
  },
}

