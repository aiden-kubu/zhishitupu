import { api } from './http'
import type { PageDto, ProcessingJobDto } from './types'

export const ingestionApi = {
  /** §12.3 上传文档（multipart/form-data），返回新建资料 */
  upload(libraryId: number, file: File): Promise<{ id: number; originalName: string; status: string }> {
    const formData = new FormData()
    formData.append('libraryId', String(libraryId))
    formData.append('file', file)
    return api('/api/documents', { method: 'POST', formData })
  },

  listDocuments(params: { libraryId?: number; status?: string; page?: number }): Promise<
    PageDto<{
      id: number
      libraryId: number
      originalName: string
      sizeBytes: number
      mimeType: string
      status: string
      createdAt: string
    }>
  > {
    const search = new URLSearchParams()
    if (params.libraryId) search.set('libraryId', String(params.libraryId))
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
}
