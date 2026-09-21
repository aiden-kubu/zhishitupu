import type { ApiEnvelope } from './types'

/** 业务错误：code 为后端字符串错误码，status 为 HTTP 状态码 */
export class ApiError extends Error {
  code: string
  status: number
  requestId?: string

  constructor(code: string, message: string, status: number, requestId?: string) {
    super(message)
    this.name = 'ApiError'
    this.code = code
    this.status = status
    this.requestId = requestId
  }
}

interface RequestOptions {
  method?: 'GET' | 'POST' | 'PUT' | 'DELETE'
  body?: unknown
  /** multipart/form-data 上传 */
  formData?: FormData
  signal?: AbortSignal
}

/**
 * 统一请求入口：解析 { code, message, data, requestId } 包络。
 * code !== 0 时抛出 ApiError；网络失败抛出 NETWORK_ERROR。
 */
export async function api<T>(path: string, options: RequestOptions = {}): Promise<T> {
  let response: Response
  try {
    response = await fetch(path, {
      method: options.method ?? 'GET',
      headers: options.formData ? undefined : { 'Content-Type': 'application/json' },
      body: options.formData ?? (options.body !== undefined ? JSON.stringify(options.body) : undefined),
      signal: options.signal,
    })
  } catch (error) {
    if ((error as Error)?.name === 'AbortError') throw error
    throw new ApiError('NETWORK_ERROR', '无法连接到后端服务，请确认 Java 后端已启动', 0)
  }

  let envelope: ApiEnvelope<T> | null = null
  try {
    envelope = (await response.json()) as ApiEnvelope<T>
  } catch {
    // 非 JSON 响应（如网关错误页）
  }

  if (!envelope || envelope.code === undefined) {
    throw new ApiError(
      'BAD_RESPONSE',
      `服务返回异常（HTTP ${response.status}）`,
      response.status,
    )
  }

  const requestId = envelope.requestId
  if (envelope.code !== 0) {
    const code = typeof envelope.code === 'number' ? String(envelope.code) : envelope.code
    throw new ApiError(code, envelope.message || '请求失败', response.status, requestId)
  }

  return envelope.data
}
