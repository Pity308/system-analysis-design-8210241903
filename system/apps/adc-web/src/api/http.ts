/**
 * 统一的 HTTP 封装。
 * <p>
 * 后端的错误结构是固定的 {code, message, timestamp, fieldErrors?}（见 GlobalExceptionHandler），
 * 这里把它转成 ApiError，页面只按 message 提示、按 code 分支，不需要解析自然语言。
 * <p>
 * 票据放在 localStorage，每个请求自动带上；401 表示“票没了或过期”，
 * 统一清票回登录页，避免每个页面各写一段跳转。403 是“角色不够”，
 * 这种不跳登录页——人是对的，是权限不对，交回页面提示。
 */
export class ApiError extends Error {
  code: string
  status: number
  fieldErrors?: Record<string, string>

  constructor(status: number, body: any) {
    super(body?.message || `请求失败（HTTP ${status}）`)
    this.status = status
    this.code = body?.code || 'UNKNOWN'
    this.fieldErrors = body?.fieldErrors
  }
}

const BASE = '/api'
const TOKEN_KEY = 'adc_staff_token'

export const auth = {
  token: () => localStorage.getItem(TOKEN_KEY) || '',
  save: (token: string) => localStorage.setItem(TOKEN_KEY, token),
  clear: () => localStorage.removeItem(TOKEN_KEY),
}

async function request<T>(method: string, path: string, body?: unknown): Promise<T> {
  const headers: Record<string, string> = {}
  if (body !== undefined) headers['Content-Type'] = 'application/json'
  const token = auth.token()
  if (token) headers['Authorization'] = `Bearer ${token}`

  const res = await fetch(BASE + path, {
    method,
    headers,
    body: body === undefined ? undefined : JSON.stringify(body),
  })
  const text = await res.text()
  const data = text ? safeJson(text) : null

  if (!res.ok) {
    if (res.status === 401 && !path.startsWith('/auth/login')) {
      auth.clear()
      if (!location.hash.startsWith('#/login')) location.hash = '#/login'
    }
    throw new ApiError(res.status, data)
  }
  return data as T
}

function safeJson(text: string) {
  try {
    return JSON.parse(text)
  } catch {
    return { message: text }
  }
}

export const http = {
  get: <T>(path: string) => request<T>('GET', path),
  post: <T>(path: string, body?: unknown) => request<T>('POST', path, body ?? {}),
  put: <T>(path: string, body?: unknown) => request<T>('PUT', path, body ?? {}),
  del: <T>(path: string) => request<T>('DELETE', path),
}

/**
 * 带票下载文件。
 * <p>
 * 不能直接用 <a href>：导出接口要 Authorization 头，浏览器不会替我们带。
 * 所以用 fetch 拿 blob，再造一个临时链接点一下——文件名也由前端定，
 * 服务端只负责给内容。
 */
export async function download(path: string, filename: string): Promise<void> {
  const token = auth.token()
  const res = await fetch(BASE + path, {
    headers: token ? { Authorization: `Bearer ${token}` } : {},
  })
  if (!res.ok) {
    const text = await res.text()
    throw new ApiError(res.status, text ? safeJson(text) : null)
  }
  const blob = await res.blob()
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  document.body.appendChild(a)
  a.click()
  document.body.removeChild(a)
  URL.revokeObjectURL(url)
}
