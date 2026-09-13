<template>
  <AdminLayout bare>
    <div class="flex min-h-0 flex-1 flex-col lg:flex-row">
      <!-- 中央图谱区 -->
      <section class="flex min-h-0 min-w-0 flex-1 flex-col">
        <div
          class="shrink-0 border-b border-gray-200 bg-white px-4 py-2.5 dark:border-gray-800 dark:bg-gray-900"
        >
          <GraphToolbar
            :mode="mode"
            :render-mode="renderMode"
            :depth="depth"
            :node-count="subgraph?.nodes.length ?? 0"
            :edge-count="subgraph?.edges.length ?? 0"
            :truncated="subgraph?.truncated ?? false"
            @update:mode="setMode"
            @update:depth="setDepth"
            @update:render-mode="setRenderMode"
            @reset="resetView"
            @toggle-panel="mobilePanelOpen = true"
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
            v-else-if="!subgraph || subgraph.nodes.length === 0"
            class="absolute inset-0 z-10 flex flex-col items-center justify-center gap-2 px-6 text-center"
          >
            <p class="text-sm font-medium text-gray-700 dark:text-gray-300">图谱暂无可展示的节点</p>
            <p class="text-xs text-gray-400">请先在「知识库」导入资料，或检查数据库中是否有示例数据</p>
            <router-link
              to="/library"
              class="mt-2 text-sm font-medium text-brand-500 hover:text-brand-600"
            >
              前往知识库 →
            </router-link>
          </div>

          <GraphCanvas2D
            v-else-if="renderMode === '2d'"
            :nodes="subgraph.nodes"
            :edges="subgraph.edges"
            :selected-id="selectedNodeId"
            :center-id="subgraph.centerNodeId ?? null"
            @select="selectNode"
            @expand="expandNode"
          />
          <GraphCanvas3D
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
            v-if="centerNode"
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
        v-if="panelOpen"
        class="hidden min-h-0 shrink-0 border-s border-gray-200 bg-white lg:flex lg:w-[400px] xl:w-[430px] dark:border-gray-800 dark:bg-gray-900"
      >
        <AiChatPanel
          :node="contextNode"
          @collapse="panelOpen = false"
          @open-detail="openDetailForContext"
        />
      </aside>

      <!-- 移动端 AI 抽屉 -->
      <Teleport to="body">
        <div v-if="mobilePanelOpen" class="fixed inset-0 z-99999 flex lg:hidden">
          <div class="absolute inset-0 bg-gray-400/50 dark:bg-gray-900/60" @click="mobilePanelOpen = false"></div>
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
    </div>

    <NodeDetailDrawer
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
  </AdminLayout>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import AdminLayout from '@/components/layout/AdminLayout.vue'
import Alert from '@/components/ui/Alert.vue'
import Button from '@/components/ui/Button.vue'
import GraphToolbar from '@/features/graph/GraphToolbar.vue'
import GraphCanvas2D from '@/features/graph/GraphCanvas2D.vue'
import GraphCanvas3D from '@/features/graph/GraphCanvas3D.vue'
import NodeDetailDrawer from '@/features/graph/NodeDetailDrawer.vue'
import AiChatPanel from '@/features/ai-chat/AiChatPanel.vue'
import { useGraphWorkspace } from '@/composables/useGraphWorkspace'

const {
  mode,
  renderMode,
  depth,
  subgraph,
  loading,
  error,
  selectedNodeId,
  centerNode,
  contextNode,
  directCount,
  extendedCount,
  selectNode,
  expandNode,
  setMode,
  setDepth,
  setRenderMode,
  resetView,
  applyRouteQuery,
} = useGraphWorkspace()

const panelOpen = ref(true)
const mobilePanelOpen = ref(false)
const detailNodeId = ref<number | null>(null)
const notice3d = ref<string | null>(null)

function openDetailForContext() {
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
