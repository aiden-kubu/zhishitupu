import { api } from './http'
import { REVIEW_STATUS_LABELS } from './types'

export type EntityCandidate = {
  id: number
  jobId: number
  tempKey: string
  name: string
  aliases: string[]
  nodeType: string
  definition: string
  confidence: number
  evidenceChunkIds: number[]
  matchedNodeId: number | null
  matchedNodeName?: string | null
  reviewStatus: keyof typeof REVIEW_STATUS_LABELS
  evidence: { chunkId: number; documentName: string; locator: string; excerpt: string }[]
}

export type RelationCandidate = {
  id: number
  jobId: number
  sourceTempKey: string
  targetTempKey: string
  sourceName: string
  targetName: string
  relationType: string
  confidence: number
  evidenceChunkIds: number[]
  matchedEdgeId: number | null
  reviewStatus: keyof typeof REVIEW_STATUS_LABELS
  evidence: { chunkId: number; documentName: string; locator: string; excerpt: string }[]
}

export type AiReviewAudit = { id: string; approved: boolean; reason: string; model: string; reviewedAt: string }

export const reviewApi = {
  aiDecisions(jobId: number): Promise<AiReviewAudit[]> {
    return api(`/api/review/jobs/${jobId}/ai-decisions`)
  },
  listEntities(jobId: number): Promise<EntityCandidate[]> {
    return api(`/api/review/jobs/${jobId}/entities`)
  },

  listRelations(jobId: number): Promise<RelationCandidate[]> {
    return api(`/api/review/jobs/${jobId}/relations`)
  },

  updateEntity(
    id: number,
    payload: { reviewStatus?: string; edited?: Partial<EntityCandidate> },
  ): Promise<EntityCandidate> {
    return api(`/api/review/entities/${id}`, { method: 'PUT', body: payload })
  },

  updateRelation(
    id: number,
    payload: { reviewStatus?: string; edited?: Partial<RelationCandidate> },
  ): Promise<RelationCandidate> {
    return api(`/api/review/relations/${id}`, { method: 'PUT', body: payload })
  },

  bulkAction(
    jobId: number,
    payload: { kind: 'entity' | 'relation'; action: 'accept' | 'reject'; ids: number[] },
  ): Promise<void> {
    return api(`/api/review/jobs/${jobId}/bulk-action`, { method: 'POST', body: payload })
  },

  /** 入库（幂等：重复提交由后端幂等键保证不重复插入） */
  commit(jobId: number): Promise<{ createdNodes: number; mergedNodes: number; createdEdges: number; skipped: number }> {
    return api(`/api/review/jobs/${jobId}/commit`, { method: 'POST' })
  },
}
