import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { graphApi } from '@/services/graphApi'
import { ApiError } from '@/services/http'
import type { GraphNodeDto, SubgraphDto } from '@/services/types'

export type WorkspaceMode = 'global' | 'local' | 'focus'
export type RenderMode = '2d' | '3d'

/**
 * 图谱工作台状态（§6）：搜索聚焦、全局/局部/聚焦模式、URL 同步（§6.2.4）。
 */
export function useGraphWorkspace() {
  const route = useRoute()
  const router = useRouter()

  const mode = ref<WorkspaceMode>('global')
  // 3D 依赖 3d-force-graph，需用户确认后于阶段 C 启用（§20）
  const renderMode = ref<RenderMode>('2d')
  const depth = ref<1 | 2>(1)
  const libraryId = ref<number | null>(null)

  const subgraph = ref<SubgraphDto | null>(null)
  const loading = ref(false)
  const error = ref<string | null>(null)
  const selectedNodeId = ref<number | null>(null)

  const centerNode = computed<GraphNodeDto | null>(() => {
    const id = subgraph.value?.centerNodeId
    if (!id || !subgraph.value) return null
    return subgraph.value.nodes.find((node) => node.id === id) ?? null
  })

  const selectedNode = computed<GraphNodeDto | null>(() => {
    const id = selectedNodeId.value
    if (!id || !subgraph.value) return null
    return subgraph.value.nodes.find((node) => node.id === id) ?? null
  })

  /** AI 上下文节点：优先手动选中，否则为聚焦中心（§7.2） */
  const contextNode = computed<GraphNodeDto | null>(() => selectedNode.value ?? centerNode.value)

  const directCount = computed(() => centerNode.value?.degree ?? 0)
  const extendedCount = computed(() => Math.max((subgraph.value?.totalNodes ?? 1) - 1, 0))

  async function loadOverview() {
    loading.value = true
    error.value = null
    selectedNodeId.value = null
    try {
      subgraph.value = await graphApi.getOverview(libraryId.value, 300)
    } catch (err) {
      subgraph.value = null
      error.value = err instanceof ApiError ? err.message : '图谱加载失败，请稍后重试'
    } finally {
      loading.value = false
    }
  }

  async function focusNode(nodeId: number, targetDepth: 1 | 2 = depth.value) {
    loading.value = true
    error.value = null
    try {
      subgraph.value = await graphApi.getNeighbors(nodeId, targetDepth, 500)
      depth.value = targetDepth
      mode.value = targetDepth === 2 ? 'local' : mode.value === 'focus' ? 'focus' : 'local'
    } catch (err) {
      subgraph.value = null
      selectedNodeId.value = null
      if (err instanceof ApiError && err.code === 'NOT_FOUND') {
        await router.replace({ path: '/' })
        return
      }
      error.value = err instanceof ApiError ? err.message : '子图加载失败，请稍后重试'
    } finally {
      loading.value = false
    }
  }

  /** 画布单击：只更新选中与 AI 上下文，不重载子图（§6.5 浏览模式） */
  function selectNode(nodeId: number | null) {
    selectedNodeId.value = nodeId
  }

  /** 画布双击：按需加载下一层邻居并同步 URL（§6.3） */
  function expandNode(nodeId: number) {
    void router.push({ path: '/', query: { node: String(nodeId), depth: '2', mode: 'local' } })
  }

  /** 搜索命中（含顶部搜索与同名选择） */
  function focusFromSearch(nodeId: number, targetDepth: 1 | 2 = 1) {
    void router.push({
      path: '/',
      query: { node: String(nodeId), depth: String(targetDepth), mode: 'local' },
    })
  }

  function setMode(next: WorkspaceMode) {
    mode.value = next
    const currentId = subgraph.value?.centerNodeId
    if (next === 'global') {
      void router.push({ path: '/' })
      return
    }
    if (currentId) {
      const targetDepth: 1 | 2 = next === 'local' ? depth.value : 1
      void router.push({
        path: '/',
        query: { node: String(currentId), depth: String(targetDepth), mode: next },
      })
    }
  }

  function setDepth(next: 1 | 2) {
    const currentId = subgraph.value?.centerNodeId
    depth.value = next
    if (currentId && mode.value !== 'global') {
      void router.push({
        path: '/',
        query: { node: String(currentId), depth: String(next), mode: mode.value },
      })
    }
  }

  function setRenderMode(next: RenderMode) {
    renderMode.value = next
  }

  function resetView() {
    void router.push({ path: '/' })
  }

  /** 根据 URL query 加载对应视图（首次进入与路由变化时调用） */
  async function applyRouteQuery() {
    const nodeParam = Number(route.query.node)
    if (Number.isFinite(nodeParam) && nodeParam > 0) {
      const queryMode = String(route.query.mode ?? 'local')
      mode.value = queryMode === 'focus' ? 'focus' : 'local'
      const queryDepth = Number(route.query.depth ?? 1)
      depth.value = queryDepth === 2 ? 2 : 1
      await focusNode(nodeParam, depth.value)
    } else {
      mode.value = 'global'
      await loadOverview()
    }
  }

  watch(
    () => route.query,
    () => {
      void applyRouteQuery()
    },
  )

  // 立即按当前 URL 初始化
  void applyRouteQuery()

  return {
    mode,
    renderMode,
    depth,
    libraryId,
    subgraph,
    loading,
    error,
    selectedNodeId,
    centerNode,
    selectedNode,
    contextNode,
    directCount,
    extendedCount,
    loadOverview,
    focusNode,
    selectNode,
    expandNode,
    focusFromSearch,
    setMode,
    setDepth,
    setRenderMode,
    resetView,
    applyRouteQuery,
  }
}
