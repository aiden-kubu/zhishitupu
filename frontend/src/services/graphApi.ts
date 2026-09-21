import { api } from './http'
import type { AiExpandResultDto, NodeDetailDto, SubgraphDto, SuggestionDto } from './types'

/** 后端 centerNodeId 为原生 long，无中心时为 0，统一归一为 null */
function normalizeSubgraph(subgraph: SubgraphDto): SubgraphDto {
  if (!subgraph.centerNodeId) {
    subgraph.centerNodeId = null
  }
  return subgraph
}

export const graphApi = {
  /** §12.2 全局总览（默认 limit 300） */
  async getOverview(libraryId?: number | null, limit = 300): Promise<SubgraphDto> {
    const params = new URLSearchParams({ limit: String(limit) })
    if (libraryId) params.set('libraryId', String(libraryId))
    return normalizeSubgraph(await api(`/api/graph/overview?${params}`))
  },

  /** §12.2 邻域子图（depth 1–2） */
  async getNeighbors(nodeId: number, depth = 1, limit = 500): Promise<SubgraphDto> {
    return normalizeSubgraph(await api(`/api/graph/nodes/${nodeId}/neighbors?depth=${depth}&limit=${limit}`))
  },

  /** §12.2 关键词搜索并返回以命中节点为中心的子图 */
  async search(q: string, depth = 1, limit = 500, libraryId?: number | null): Promise<SubgraphDto> {
    const params = new URLSearchParams({ q, depth: String(depth), limit: String(limit) })
    if (libraryId) params.set('libraryId', String(libraryId))
    return normalizeSubgraph(await api(`/api/search?${params}`))
  },

  /** §6.2 搜索联想（300ms 防抖由调用方负责） */
  suggestions(q: string, limit = 8, signal?: AbortSignal): Promise<SuggestionDto[]> {
    return api(`/api/search/suggestions?q=${encodeURIComponent(q)}&limit=${limit}`, { signal })
  },

  /**
   * 搜索未命中时由 AI 生成并收录节点（仅用户在搜索框显式触发，禁止联想过程调用）。
   * signal 仅取消前端等待，后端仍会完成生成与收录。
   */
  aiExpand(query: string, libraryId?: number | null, signal?: AbortSignal): Promise<AiExpandResultDto> {
    return api('/api/search/ai-expand', {
      method: 'POST',
      body: { query, ...(libraryId ? { libraryId } : {}) },
      signal,
    })
  },

  getNode(id: number): Promise<NodeDetailDto> {
    return api(`/api/nodes/${id}`)
  },

  createNode(payload: {
    name: string
    nameEn?: string
    type: string
    definition?: string
    aliases?: string[]
  }): Promise<NodeDetailDto> {
    return api('/api/nodes', { method: 'POST', body: payload })
  },

  updateNode(
    id: number,
    payload: Partial<{
      name: string
      nameEn: string
      type: string
      definition: string
      status: string
    }>,
  ): Promise<NodeDetailDto> {
    return api(`/api/nodes/${id}`, { method: 'PUT', body: payload })
  },

  deleteNode(id: number): Promise<void> {
    return api(`/api/nodes/${id}`, { method: 'DELETE' })
  },

  createEdge(payload: { sourceNodeId: number; targetNodeId: number; relationType: string; weight?: number }): Promise<void> {
    // 后端通过 @JsonAlias 接受 sourceNodeId/targetNodeId/relationType
    return api('/api/edges', { method: 'POST', body: payload })
  },

  updateEdge(
    id: number,
    payload: { relationType?: string; weight?: number },
  ): Promise<void> {
    return api(`/api/edges/${id}`, { method: 'PUT', body: payload })
  },

  deleteEdge(id: number): Promise<void> {
    return api(`/api/edges/${id}`, { method: 'DELETE' })
  },
}
