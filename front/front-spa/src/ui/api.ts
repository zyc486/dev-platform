import axios, { type AxiosError, type InternalAxiosRequestConfig } from 'axios'
import { ElMessage } from 'element-plus'
import { auth } from './auth'
import { goLogin } from './navigation'

const TRACE_ID_HEADER = 'X-Trace-Id'

export type AppRequestConfig = InternalAxiosRequestConfig & {
  /** 为 true 时 HTTP 401 不清除登录态、不跳转（用于静默探测） */
  skipAuthLogout?: boolean
}

export const api = axios.create({
  baseURL: '',
  timeout: 30000,
})

api.interceptors.request.use((config) => {
  const t = auth.token.value
  if (t) {
    config.headers = config.headers || {}
    ;(config.headers as any).Authorization = `Bearer ${t}`
  }
  return config
})

api.interceptors.response.use(
  (resp) => resp,
  async (err: AxiosError<any>) => {
    const cfg = err.config as AppRequestConfig | undefined
    const status = err.response?.status
    const traceId = err.response?.headers?.[TRACE_ID_HEADER.toLowerCase()] as string | undefined
    const traceSuffix = traceId ? `（traceId: ${traceId}）` : ''

    if (status === 401) {
      if (!cfg?.skipAuthLogout) {
        auth.logout()
        ElMessage.error(`登录已失效，请重新登录${traceSuffix}`)
        const redirect = `${window.location.pathname}${window.location.search || ''}`
        goLogin(redirect)
      }
      return Promise.reject(err)
    }

    if (status === 403) {
      ElMessage.warning(`无权限执行该操作${traceSuffix}`)
      return Promise.reject(err)
    }

    if (status === 429) {
      let tip = '操作过于频繁，请稍后再试'
      try {
        const body = err.response?.data as any
        if (body && body.message) tip = String(body.message)
      } catch {
        /* ignore */
      }
      ElMessage.warning(tip)
      return Promise.reject(err)
    }

    return Promise.reject(err)
  },
)

export async function unwrap<T>(p: Promise<any>): Promise<T> {
  const r = await p
  const data = r?.data
  const traceId = r?.headers?.[TRACE_ID_HEADER.toLowerCase()] as string | undefined
  const traceSuffix = traceId ? `（traceId: ${traceId}）` : ''
  if (!data) throw new Error('空响应')
  if (data.code !== 200) throw new Error((data.message || '请求失败') + traceSuffix)
  return data.data as T
}
