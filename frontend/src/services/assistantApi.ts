import { api, ApiError } from './http'
import type {
  Artifact,
  Capabilities,
  CreateRun,
  DocumentOption,
  Graph,
  LibraryOption,
  Reread,
  Run,
  ScopePage,
  TemplateList,
} from './assistantTypes'
import { validateResponse } from '@/features/assistant/responseValidation'

const root = '/api/assistant'
const segment = (id: string) => encodeURIComponent(id)
const runPath = (id: string) => `${root}/runs/${segment(id)}`
const artifactPath = (runId: string, artifactId: string) =>
  `${runPath(runId)}/artifacts/${segment(artifactId)}`
type ResponseKind = Parameters<typeof validateResponse>[0]
async function request<T>(
  kind: ResponseKind,
  path: string,
  signal?: AbortSignal,
  body?: unknown,
  post = false,
): Promise<T> {
  const data = await api<unknown>(path, { signal, method: post ? 'POST' : 'GET', body })
  if (!validateResponse(kind, data))
    throw new ApiError(
      'BAD_RESPONSE',
      '助手服务返回的数据不符合当前契约，请联系总控核对接线。',
      200,
    )
  return data as T
}
export const assistantApi = {
  capabilities: (signal?: AbortSignal) =>
    request<Capabilities>('capabilities', `${root}/capabilities`, signal),
  templates: (signal?: AbortSignal) =>
    request<TemplateList>('templates', `${root}/templates`, signal),
  libraries: (page: number, signal?: AbortSignal) =>
    request<ScopePage<LibraryOption>>(
      'libraries',
      `${root}/scope/libraries?page=${page}&pageSize=20`,
      signal,
    ),
  documents: (libraryIds: string[], page: number, signal?: AbortSignal) => {
    const params = new URLSearchParams({ page: String(page), pageSize: '20' })
    libraryIds.forEach((id) => params.append('libraryIds', id))
    return request<ScopePage<DocumentOption>>(
      'documents',
      `${root}/scope/documents?${params}`,
      signal,
    )
  },
  create: (body: CreateRun, signal?: AbortSignal) =>
    request<Run>('run', `${root}/runs`, signal, body, true),
  run: (id: string, signal?: AbortSignal) => request<Run>('run', runPath(id), signal),
  cancel: (id: string, signal?: AbortSignal) =>
    request<{ accepted: boolean; run: Run }>(
      'cancel',
      `${runPath(id)}/cancel`,
      signal,
      undefined,
      true,
    ),
  artifact: (runId: string, id: string, signal?: AbortSignal) =>
    request<Artifact>('artifact', artifactPath(runId, id), signal),
  graph: (runId: string, artifactId: string, signal?: AbortSignal) =>
    request<Graph>('graph', `${artifactPath(runId, artifactId)}/graph`, signal),
  reread: (
    runId: string,
    id: string,
    sourceId: string,
    expectedContentHash: string,
    signal?: AbortSignal,
  ) =>
    request<Reread>(
      'reread',
      `${artifactPath(runId, id)}/source-reread`,
      signal,
      { sourceId, expectedContentHash, maxChars: 4000 },
      true,
    ),
}
