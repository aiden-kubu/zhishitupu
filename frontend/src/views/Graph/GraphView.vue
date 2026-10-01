<template>
  <component
    :is="researchActive ? 'div' : AdminLayout"
    bare
    :class="researchActive ? 'flex h-dvh flex-col bg-white dark:bg-gray-900' : ''"
  >
    <header
      v-if="researchActive"
      class="flex shrink-0 items-center justify-between gap-4 border-b border-gray-200 p-4 text-gray-800 dark:border-gray-800 dark:text-white/90"
    >
      <h1 class="font-semibold">研究图谱 · 授权子图只读</h1>
      <RouterLink :to="returnTo" class="text-sm text-brand-600 dark:text-brand-400"
        >返回原成果</RouterLink
      >
    </header>
    <div class="flex min-h-0 flex-1 flex-col lg:flex-row">
      <!-- 中央图谱区 -->
      <section class="flex min-h-0 min-w-0 flex-1 flex-col">
        <div
          class="shrink-0 border-b border-gray-200 bg-white px-4 py-2.5 dark:border-gray-800 dark:bg-gray-900"
        >
          <GraphToolbar
            :read-only="researchActive"
            :mode="mode"
            :panel-open="panelOpen"
            :render-mode="renderMode"
            :node-count="
              researchActive ? (researchGraph?.nodes.length ?? 0) : (subgraph?.nodes.length ?? 0)
            "
            :edge-count="
              researchActive ? (researchGraph?.edges.length ?? 0) : (subgraph?.edges.length ?? 0)
            "
            :truncated="
              researchActive
                ? (researchGraph?.coverage.truncated ?? false)
                : (subgraph?.truncated ?? false)
            "
            @update:mode="workspace.setMode"
            @update:render-mode="setRenderMode"
            @reset="resetView"
            @toggle-panel="openAiPanel"
          />
        </div>

        <div class="relative min-h-0 flex-1 bg-gray-25 dark:bg-gray-900">
          <!-- 加载态 -->
          <div
            v-if="loading"
            class="absolute inset-0 z-10 flex flex-col items-center justify-center gap-2 bg-white/70 dark:bg-gray-900/70"
          >
            <span
              class="h-8 w-8 animate-spin rounded-full border-2 border-brand-500 border-t-transparent"
            ></span>
            <p class="text-sm text-gray-500 dark:text-gray-400">图谱加载中…</p>
          </div>

          <!-- 失败态 -->
          <div
            v-else-if="error"
            class="absolute inset-0 z-10 flex flex-col items-center justify-center gap-3 px-6"
          >
            <Alert variant="error" title="图谱加载失败" :message="error" />
            <Button size="sm" variant="outline" @click="applyRouteQuery">重试</Button>
          </div>

          <!-- 空态 -->
          <div
            v-else-if="researchActive && researchGraph && !research.canvas.value"
            class="absolute inset-0 z-10 flex items-center justify-center px-6"
          >
            <Alert
              variant="warning"
              title="当前渲染限制"
              message="结果含超过 JavaScript 安全整数范围的 ID，当前 2D/3D 画布无法精确表示。右侧保留完整字符串 ID、方向、路径及来源；未截断或近似转换。"
            />
          </div>
          <div
            v-else-if="!subgraph || subgraph.nodes.length === 0"
            class="absolute inset-0 z-10 flex flex-col items-center justify-center gap-2 px-6 text-center"
          >
            <template v-if="researchActive">
              <p class="text-lg font-semibold text-gray-800 dark:text-white/90">本次研究结果为空</p>
              <p class="text-sm text-gray-500 dark:text-gray-400">
                仅表示本次授权观察未命中，请查看范围与覆盖缺口。
              </p>
            </template>
            <template v-else>
              <span
                class="mb-4 flex h-16 w-16 items-center justify-center rounded-2xl bg-brand-50 text-brand-500 dark:bg-brand-500/10"
                ><LayoutDashboardIcon class="h-8 w-8"
              /></span>
              <p class="text-xl font-semibold text-gray-800 dark:text-white/90">
                从第一份资料开始，建立知识连接
              </p>
              <p class="mt-1 max-w-sm text-sm leading-6 text-gray-500 dark:text-gray-400">
                通过顶部「导入资料」上传内容；识别中或未审核入库的知识暂不显示在图谱中。
              </p>
              <router-link
                to="/processing"
                class="mt-2 text-sm font-medium text-brand-500 hover:text-brand-600"
              >
                查看处理进度 →
              </router-link>
            </template>
          </div>

          <GraphCanvas2D
            :key="canvasKey"
            :read-only="researchActive"
            :highlighted-edge-ids="highlightedEdgeIds"
            v-else-if="renderMode === '2d'"
            :nodes="subgraph.nodes"
            :edges="subgraph.edges"
            :selected-id="selectedNodeId"
            :center-id="subgraph.centerNodeId ?? null"
            @select="selectNode"
            @expand="expandNode"
          />
          <GraphCanvas3D
            :key="canvasKey"
            :read-only="researchActive"
            :highlighted-edge-ids="highlightedEdgeIds"
            v-else
            :nodes="subgraph.nodes"
            :edges="subgraph.edges"
            :selected-id="selectedNodeId"
            :center-id="subgraph.centerNodeId ?? null"
            @select="selectNode"
            @expand="expandNode"
            @webgl-failed="onWebglFailed"
          />

          <!-- 选中/中心节点统计（§6.2.4） -->
          <div
            v-if="!researchActive && centerNode"
            class="absolute bottom-4 start-4 z-10 rounded-2xl border border-gray-200 bg-white/95 px-4 py-3 shadow-theme-md backdrop-blur dark:border-gray-800 dark:bg-gray-900/95"
          >
            <p class="text-sm font-semibold text-gray-800 dark:text-white/90">
              {{ centerNode.name }}
            </p>
            <p class="mt-0.5 text-xs text-gray-500 dark:text-gray-400">
              直接关联 {{ directCount }} · 扩展关联 {{ extendedCount }}
              <template v-if="contextNode && contextNode.id !== centerNode.id">
                · 当前查看 {{ contextNode.name }}
              </template>
            </p>
          </div>
        </div>
      </section>

      <!-- 右侧 AI 面板（桌面常驻可折叠，<1024px 抽屉，§5.3） -->
      <aside
        v-if="!researchActive && panelOpen"
        class="hidden min-h-0 shrink-0 border-s border-gray-200 bg-white lg:flex lg:w-[320px] xl:w-[360px] 2xl:w-[380px] dark:border-gray-800 dark:bg-gray-900"
      >
        <AiChatPanel
          :node="contextNode"
          @collapse="panelOpen = false"
          @open-detail="openDetailForContext"
        />
      </aside>

      <!-- 移动端 AI 抽屉 -->
      <Teleport to="body">
        <div v-if="!researchActive && mobilePanelOpen" class="fixed inset-0 z-99999 flex lg:hidden">
          <div
            class="absolute inset-0 bg-gray-400/50 dark:bg-gray-900/60"
            @click="mobilePanelOpen = false"
          ></div>
          <aside
            class="relative ms-auto h-full w-[88%] max-w-[400px] bg-white shadow-theme-xl dark:bg-gray-900"
          >
            <AiChatPanel
              :node="contextNode"
              @collapse="mobilePanelOpen = false"
              @open-detail="openDetailForContext"
            />
          </aside>
        </div>
      </Teleport>
      <ResearchResultPanel
        v-if="researchActive"
        :graph="researchGraph"
        :selected-node-id="research.selectedNodeId.value"
        :selected-edge-id="research.selectedEdgeId.value"
        :selected-path="research.selectedPath.value"
        :only-path="research.onlyPath.value"
        :return-to="returnTo"
        :reread="research.reread.value"
        :reread-error="research.rereadError.value"
        :rereading="research.rereading.value"
        @select-node="research.selectNode"
        @select-edge="research.selectEdge"
        @locate-path="research.locatePath"
        @update:only-path="research.onlyPath.value = $event"
        @close-detail="research.clearSelection"
        @reread="research.readSource"
      />
    </div>

    <NodeDetailDrawer
      v-if="!researchActive"
      :node-id="detailNodeId"
      @close="detailNodeId = null"
      @deleted="onNodeDeleted"
      @updated="onGraphDataChanged"
    />

    <Alert
      v-if="notice3d"
      variant="info"
      title="3D 图谱"
      :message="notice3d"
      class="fixed bottom-4 end-4 z-99999 max-w-sm"
    />
  </component>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
function openAiPanel() {
  if (researchActive.value) return
  if (window.innerWidth >= 1024) panelOpen.value = true
  else mobilePanelOpen.value = true
}
import { LayoutDashboardIcon } from '@/icons'
import AdminLayout from '@/components/layout/AdminLayout.vue'
import Alert from '@/components/ui/Alert.vue'
import Button from '@/components/ui/Button.vue'
import GraphToolbar from '@/features/graph/GraphToolbar.vue'
import GraphCanvas2D from '@/features/graph/GraphCanvas2D.vue'
import GraphCanvas3D from '@/features/graph/GraphCanvas3D.vue'
import NodeDetailDrawer from '@/features/graph/NodeDetailDrawer.vue'
import AiChatPanel from '@/features/ai-chat/AiChatPanel.vue'
import { useGraphWorkspace } from '@/composables/useGraphWorkspace'
import { canvasId, useResearchGraph } from '@/composables/useResearchGraph'
import ResearchResultPanel from '@/features/graph/ResearchResultPanel.vue'
import { useImportModal } from '@/composables/useImportModal'

const research = useResearchGraph()
const { active: researchActive, graph: researchGraph, returnTo } = research
const workspace = useGraphWorkspace(computed(() => !researchActive.value))
const { mode, renderMode, centerNode, contextNode, directCount, extendedCount, setRenderMode } =
  workspace
const subgraph = computed(() =>
  researchActive.value ? research.canvas.value : workspace.subgraph.value,
)
const loading = computed(() =>
  researchActive.value ? research.loading.value : workspace.loading.value,
)
const error = computed(() => (researchActive.value ? research.error.value : workspace.error.value))
const selectedNodeId = computed(() =>
  researchActive.value
    ? research.selectedNodeId.value
      ? canvasId(research.selectedNodeId.value)
      : null
    : workspace.selectedNodeId.value,
)
const highlightedEdgeIds = computed(() =>
  research.pathIds.value.map(canvasId).filter((id): id is number => id !== null),
)
const viewGeneration = ref(0)
const canvasKey = computed(() => `${researchActive.value}:${viewGeneration.value}`)
function selectNode(id: number | null) {
  if (researchActive.value) research.selectNode(id === null ? null : String(id))
  else workspace.selectNode(id)
}
function expandNode(id: number) {
  if (!researchActive.value) workspace.expandNode(id)
}
function applyRouteQuery() {
  return researchActive.value ? research.refresh() : workspace.applyRouteQuery()
}
function resetView() {
  if (!researchActive.value) {
    workspace.resetView()
    return
  }
  research.clearSelection()
  research.selectedPath.value = null
  research.onlyPath.value = false
  viewGeneration.value++
}

const panelOpen = ref(false) // 默认折叠：打开即为空对话（2026-09-14 无历史决策）
const mobilePanelOpen = ref(false)
const detailNodeId = ref<number | null>(null)
const notice3d = ref<string | null>(null)
const { close: closeImport } = useImportModal()
watch(
  researchActive,
  (active) => {
    if (active) {
      panelOpen.value = false
      mobilePanelOpen.value = false
      detailNodeId.value = null
      closeImport()
    }
  },
  { immediate: true, flush: 'sync' },
)

function openDetailForContext() {
  if (researchActive.value) return
  detailNodeId.value = contextNode.value?.id ?? null
  mobilePanelOpen.value = false
}

function onNodeDeleted() {
  detailNodeId.value = null
  void applyRouteQuery()
}

function onGraphDataChanged() {
  void applyRouteQuery()
}

/** WebGL 不可用或初始化失败时自动回退 2D，并显示非阻塞提示（§6.3） */
function onWebglFailed() {
  setRenderMode('2d')
  notice3d.value = '当前环境不支持 WebGL，已自动回退到 2D 图谱。'
  window.setTimeout(() => {
    notice3d.value = null
  }, 6000)
}
</script>
