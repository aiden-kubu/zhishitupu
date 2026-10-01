import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useRoute, type LocationQuery } from 'vue-router'
import { assistantApi } from '@/services/assistantApi'
import { ApiError } from '@/services/http'
import type { Evidence, Graph, Reread } from '@/services/assistantTypes'
import type { SubgraphDto } from '@/services/types'

export type ResearchContext =
  { kind: 'normal' } | { kind: 'invalid' } | { kind: 'research'; runId: string; artifactId: string }

export function parseResearchContext(query: LocationQuery): ResearchContext {
  if (!('runId' in query) && !('artifactId' in query)) return { kind: 'normal' }
  const valid = (value: unknown): value is string =>
    typeof value === 'string' && /^[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}$/.test(value)
  if (!valid(query.runId) || !valid(query.artifactId)) return { kind: 'invalid' }
  return { kind: 'research', runId: query.runId, artifactId: query.artifactId }
}

// Check exact decimal representation before the only conversion into the legacy canvas DTO.
export function canvasId(id: string): number | null {
  const max = '9007199254740991'
  if (!/^[1-9][0-9]*$/.test(id) || id.length > max.length || (id.length === max.length && id > max))
    return null
  return Number(id)
}

export function renderResearchGraph(graph: Graph): SubgraphDto | null {
  if (
    [
      ...graph.nodes.map((n) => n.nodeId),
      ...graph.edges.flatMap((e) => [e.edgeId, e.sourceNodeId, e.targetNodeId]),
    ].some((id) => canvasId(id) === null)
  )
    return null
  return {
    nodes: graph.nodes.map((n) => ({
      id: canvasId(n.nodeId)!,
      name: n.name ?? `#${n.nodeId}`,
      type: n.type ?? 'unknown',
      definition: n.definition,
      degree: graph.edges.filter((e) => e.sourceNodeId === n.nodeId || e.targetNodeId === n.nodeId)
        .length,
      sourceCount: 0, // Not a domain source count; never displayed as one in research mode.
    })),
    edges: graph.edges.map((e) => ({
      id: canvasId(e.edgeId)!,
      source: canvasId(e.sourceNodeId)!,
      target: canvasId(e.targetNodeId)!,
      relation: e.relationType,
      weight: 1,
    })),
    truncated: graph.coverage.truncated,
    totalNodes: graph.nodes.length,
    totalEdges: graph.edges.length,
  }
}

// Transport identity/topology consistency only; authorization and business filtering stay on the host.
export function assertGraphIdentity(graph: Graph, runId: string, artifactId: string) {
  const nodes = new Set(graph.nodes.map((n) => n.nodeId))
  const edges = new Map(graph.edges.map((e) => [e.edgeId, e]))
  if (
    graph.runId !== runId ||
    graph.artifactId !== artifactId ||
    nodes.size !== graph.nodes.length ||
    edges.size !== graph.edges.length ||
    graph.empty !== (graph.nodes.length === 0) ||
    graph.edges.some((e) => !nodes.has(e.sourceNodeId) || !nodes.has(e.targetNodeId)) ||
    graph.paths.some((path) =>
      path.some((id, index) => {
        const edge = edges.get(id)
        return (
          !edge || (index > 0 && edges.get(path[index - 1])?.targetNodeId !== edge.sourceNodeId)
        )
      }),
    )
  )
    throw new ApiError('BAD_RESPONSE', '成果身份或有向图数据不一致，请刷新或返回成果。', 200)
}

function message(error: unknown) {
  if (!(error instanceof ApiError)) return '研究成果加载失败，请刷新或返回成果。'
  const hints: Record<string, string> = {
    RUN_EXPIRED: '研究成果已过期，请返回助手重新发起。',
    GRAPH_NOT_AVAILABLE: '此成果没有可用图谱观察，请返回成果查看缺口。',
    NOT_FOUND: '成果不存在或宿主已重启，请返回助手。',
    NETWORK_ERROR: '宿主/API 未接入或无法连接，请刷新或返回成果。',
    BAD_RESPONSE: '宿主/API 返回数据与当前契约不一致，请刷新或返回成果。',
  }
  return `${hints[error.code] ?? error.message}（${error.code}${error.requestId ? `；请求 ${error.requestId}` : ''}）`
}

export function useResearchGraph() {
  const route = useRoute()
  const context = computed(() => parseResearchContext(route.query))
  const active = computed(() => context.value.kind !== 'normal')
  const graph = ref<Graph | null>(null)
  const loading = ref(false)
  const error = ref<string | null>(null)
  const selectedNodeId = ref<string | null>(null)
  const selectedEdgeId = ref<string | null>(null)
  const selectedPath = ref<number | null>(null)
  const onlyPath = ref(false)
  const reread = ref<Reread | null>(null)
  const rereadError = ref<string | null>(null)
  const rereading = ref(false)
  let generation = 0,
    readGeneration = 0
  let controller: AbortController | null = null,
    readController: AbortController | null = null
  let disposed = false
  function clearReread() {
    readGeneration++
    readController?.abort()
    reread.value = null
    rereadError.value = null
    rereading.value = false
  }
  function clearSelection() {
    selectedNodeId.value = null
    selectedEdgeId.value = null
    clearReread()
  }
  async function refresh() {
    const current = context.value
    const version = ++generation
    controller?.abort()
    clearSelection()
    selectedPath.value = null
    onlyPath.value = false
    graph.value = null
    loading.value = false
    error.value =
      current.kind === 'invalid'
        ? '研究定位参数错误：runId 与 artifactId 必须是单个有效标识。'
        : null
    if (current.kind !== 'research') return
    controller = new AbortController()
    loading.value = true
    try {
      const data = await assistantApi.graph(current.runId, current.artifactId, controller.signal)
      if (disposed || version !== generation) return
      assertGraphIdentity(data, current.runId, current.artifactId)
      graph.value = data
    } catch (err) {
      if (!disposed && version === generation && (err as Error)?.name !== 'AbortError')
        error.value = message(err)
    } finally {
      if (!disposed && version === generation) loading.value = false
    }
  }
  function selectNode(id: string | null) {
    clearSelection()
    if (graph.value?.nodes.some((n) => n.nodeId === id)) selectedNodeId.value = id
  }
  function selectEdge(id: string) {
    clearSelection()
    if (graph.value?.edges.some((e) => e.edgeId === id)) selectedEdgeId.value = id
  }
  function locatePath(index: number) {
    if (!graph.value?.paths[index]) return
    selectedPath.value = index
    selectNode(
      graph.value.edges.find((e) => e.edgeId === graph.value!.paths[index][0])?.sourceNodeId ??
        null,
    )
  }
  async function readSource(source: Evidence) {
    clearReread()
    const current = context.value,
      pointer = source.rereadPointer
    if (current.kind !== 'research' || !pointer || !graph.value?.evidence.includes(source)) return
    const version = readGeneration,
      graphVersion = generation
    readController = new AbortController()
    rereading.value = true
    try {
      const data = await assistantApi.reread(
        current.runId,
        current.artifactId,
        source.sourceId,
        pointer.contentHash,
        readController.signal,
      )
      if (disposed || version !== readGeneration || graphVersion !== generation) return
      if (
        data.runId !== current.runId ||
        data.artifactId !== current.artifactId ||
        data.originalSourceId !== source.sourceId ||
        data.documentId !== pointer.documentId ||
        data.chunkId !== pointer.chunkId ||
        data.contentHash !== pointer.contentHash
      )
        throw new ApiError('BAD_RESPONSE', '重读来源身份不一致。', 200)
      reread.value = data
    } catch (err) {
      if (!disposed && version === readGeneration && (err as Error)?.name !== 'AbortError')
        rereadError.value = message(err)
    } finally {
      if (!disposed && version === readGeneration) rereading.value = false
    }
  }
  const pathIds = computed(() =>
    selectedPath.value === null ? [] : (graph.value?.paths[selectedPath.value] ?? []),
  )
  const canvas = computed(() => {
    if (!graph.value) return null
    const data = renderResearchGraph(graph.value)
    if (!data || !onlyPath.value || selectedPath.value === null) return data
    const edges = data.edges.filter((e) => pathIds.value.includes(String(e.id)))
    const ids = new Set(edges.flatMap((e) => [e.source, e.target]))
    return { ...data, edges, nodes: data.nodes.filter((n) => ids.has(n.id)) }
  })
  const returnTo = computed(() =>
    context.value.kind === 'research'
      ? {
          path: '/assistant',
          query: { runId: context.value.runId, artifactId: context.value.artifactId },
        }
      : { path: '/assistant', query: {} },
  )
  watch(
    context,
    () => {
      void refresh()
    },
    { immediate: true, flush: 'sync' },
  )
  onBeforeUnmount(() => {
    disposed = true
    generation++
    controller?.abort()
    clearReread()
  })
  return {
    context,
    active,
    graph,
    loading,
    error,
    canvas,
    selectedNodeId,
    selectedEdgeId,
    selectedPath,
    onlyPath,
    pathIds,
    returnTo,
    reread,
    rereadError,
    rereading,
    refresh,
    selectNode,
    selectEdge,
    locatePath,
    clearSelection,
    readSource,
  }
}
