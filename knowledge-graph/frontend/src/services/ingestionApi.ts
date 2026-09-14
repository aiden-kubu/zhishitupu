import { api } from './http'
import type { DocumentDto, PageDto, ProcessingJobDto } from './types'

export const ingestionApi = {
  /** §12.3 上传文档（multipart/form-data），返回新建资料 */
  upload(libraryId: number | null, file: File): Promise<{ id: number; originalName: string; status: string }> {
    const formData = new FormData()
    if (libraryId !== null) formData.append('libraryId', String(libraryId))
    formData.append('file', file)
    return api('/api/documents', { method: 'POST', formData })
  },

  organization(documentId: number): Promise<{ libraryId: number; name: string; nodeCount: number }[]> {
    return api(`/api/documents/${documentId}/organization`)
  },

  listDocuments(params: { libraryId?: number; status?: string; page?: number; pageSize?: number }): Promise<
    PageDto<DocumentDto>
  > {
    const search = new URLSearchParams()
    if (params.libraryId) search.set('libraryId', String(params.libraryId))
    if (params.pageSize) search.set('pageSize', String(params.pageSize))
    if (params.status) search.set('status', params.status)
    search.set('page', String(params.page ?? 1))
    return api(`/api/documents?${search}`)
  },

  /** §12.4 处理任务列表（前端每 2 秒轮询活动任务） */
  listJobs(status?: string, page = 1): Promise<PageDto<ProcessingJobDto>> {
    const search = new URLSearchParams({ page: String(page) })
    if (status) search.set('status', status)
    return api(`/api/processing/jobs?${search}`)
  },

  getJob(id: number): Promise<ProcessingJobDto> {
    return api(`/api/processing/jobs/${id}`)
  },

  retry(id: number): Promise<void> {
    return api(`/api/processing/jobs/${id}/retry`, { method: 'POST' })
  },

  cancel(id: number): Promise<void> {
    return api(`/api/processing/jobs/${id}/cancel`, { method: 'POST' })
  },

  /** 恢复入口：对已解析但无候选结果的旧任务发起 AI 抽取（复用既有 chunks，不重新解析） */
  extract(id: number): Promise<void> {
    return api(`/api/processing/jobs/${id}/extract`, { method: 'POST' })
  },

  startProcess(documentId: number): Promise<void> {
    return api(`/api/documents/${documentId}/process`, { method: 'POST' })
  },

  /** 更新资料标题与生命周期 */
  updateDocumentMetadata(
    id: number,
    payload: { title?: string; lifecycleStatus?: 'active' | 'archived' | 'outdated' },
  ): Promise<DocumentDto> {
    return api(`/api/documents/${id}/metadata`, { method: 'PUT', body: payload })
  },

  /** 添加人工标签 */
  addDocumentTag(id: number, tag: string): Promise<DocumentDto> {
    return api(`/api/documents/${id}/tags`, { method: 'POST', body: { tag } })
  },

  /** 删除标签 */
  removeDocumentTag(id: number, tag: string): Promise<DocumentDto> {
    const search = new URLSearchParams({ tag })
    return api(`/api/documents/${id}/tags?${search}`, { method: 'DELETE' })
  },

  /** 人工校对记录 */
  setDocumentVerification(id: number, payload: { humanChecked: boolean; note?: string }): Promise<DocumentDto> {
    return api(`/api/documents/${id}/verification`, { method: 'PUT', body: payload })
  },
}
