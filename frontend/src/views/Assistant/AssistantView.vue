<template>
  <AdminLayout>
    <PageBreadcrumb
      page-title="智能助手"
      description="选择一个知识节点，基于已入库资料进行追问和探索。"
    />

    <div
      class="grid min-h-[calc(100dvh-15rem)] gap-6 lg:grid-cols-[minmax(260px,0.65fr)_minmax(0,1.35fr)]"
    >
      <section
        class="overflow-hidden rounded-2xl border border-gray-200 bg-white dark:border-gray-800 dark:bg-white/[0.03]"
        aria-labelledby="node-list-title"
      >
        <div
          class="flex items-center justify-between gap-3 border-b border-gray-100 px-5 py-4 dark:border-gray-800"
        >
          <div>
            <h2 id="node-list-title" class="text-lg font-semibold text-gray-900 dark:text-white/90">
              选择知识点
            </h2>
            <p class="mt-1 text-xs text-gray-500 dark:text-gray-400">
              从图谱中选择一个节点开始提问
            </p>
          </div>
          <Button
            size="sm"
            variant="ghost"
            :start-icon="RefreshIcon"
            :disabled="loading"
            @click="loadNodes"
            >刷新</Button
          >
        </div>
        <div v-if="loading" class="space-y-3 p-5" aria-label="正在加载知识点">
          <div
            v-for="index in 5"
            :key="index"
            class="h-14 animate-pulse rounded-xl bg-gray-100 dark:bg-gray-800"
          ></div>
        </div>
        <div v-else-if="error" class="p-5">
          <Alert variant="error" title="知识点加载失败" :message="error" /><Button
            class="mt-4"
            size="sm"
            variant="outline"
            @click="loadNodes"
            >重试</Button
          >
        </div>
        <div
          v-else-if="!nodes.length"
          class="p-5 text-sm leading-6 text-gray-500 dark:text-gray-400"
        >
          图谱中还没有可提问的知识点。请先导入并处理资料。
        </div>
        <div v-else class="max-h-[calc(100dvh-22rem)] overflow-y-auto p-3">
          <button
            v-for="node in nodes"
            :key="node.id"
            type="button"
            :class="
              selectedNode?.id === node.id
                ? 'bg-brand-25 text-brand-700 dark:bg-brand-500/10 dark:text-brand-300'
                : 'text-gray-700 hover:bg-gray-50 dark:text-gray-300 dark:hover:bg-white/[0.03]'
            "
            class="mb-1 flex w-full items-center gap-3 rounded-xl p-3 text-start transition focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand-500"
            @click="selectNode(node)"
          >
            <span
              :class="
                selectedNode?.id === node.id
                  ? 'bg-brand-500 text-white'
                  : 'bg-gray-100 text-gray-500 dark:bg-gray-800 dark:text-gray-400'
              "
              class="flex size-9 shrink-0 items-center justify-center rounded-lg"
              ><ChatIcon class="size-4" aria-hidden="true"
            /></span>
            <span class="min-w-0"
              ><span class="block truncate text-sm font-medium">{{ node.name }}</span
              ><span class="mt-0.5 block truncate text-xs text-gray-500 dark:text-gray-400"
                >{{ node.type }} · {{ node.degree }} 个关联</span
              ></span
            >
          </button>
        </div>
      </section>

      <section
        class="min-h-[560px] overflow-hidden rounded-2xl border border-gray-200 bg-white dark:border-gray-800 dark:bg-white/[0.03]"
      >
        <AiChatPanel
          v-if="chatVisible"
          :node="selectedNode"
          @collapse="chatVisible = false"
          @open-detail="openInGraph"
        />
        <div v-else class="flex h-full flex-col items-center justify-center px-6 text-center">
          <span
            class="flex size-14 items-center justify-center rounded-2xl bg-brand-50 text-brand-500 dark:bg-brand-500/10 dark:text-brand-400"
            ><ChatIcon class="size-7" aria-hidden="true"
          /></span>
          <h2 class="mt-4 text-lg font-semibold text-gray-900 dark:text-white/90">
            选择知识点开始对话
          </h2>
          <p class="mt-2 max-w-sm text-sm leading-6 text-gray-500 dark:text-gray-400">
            智能助手只根据知识库和图谱中的资料回答，并会标注可追溯的出处。
          </p>
        </div>
      </section>
    </div>
  </AdminLayout>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import AdminLayout from '@/components/layout/AdminLayout.vue'
import PageBreadcrumb from '@/components/common/PageBreadcrumb.vue'
import Button from '@/components/ui/Button.vue'
import Alert from '@/components/ui/Alert.vue'
import AiChatPanel from '@/features/ai-chat/AiChatPanel.vue'
import { ChatIcon, RefreshIcon } from '@/icons'
import { graphApi } from '@/services/graphApi'
import { learningPathApi } from '@/services/learningPathApi'
import type { GraphNodeDto } from '@/services/types'

const router = useRouter()
const nodes = ref<GraphNodeDto[]>([])
const selectedNode = ref<GraphNodeDto | null>(null)
const loading = ref(false)
const error = ref<string | null>(null)
const chatVisible = ref(false)
async function loadNodes() {
  loading.value = true
  error.value = null
  try {
    const [overview, learningPath] = await Promise.all([
      graphApi.getOverview(null, 30),
      learningPathApi.getCurrent().catch(() => null),
    ])
    nodes.value = overview.nodes
    if (!selectedNode.value) {
      const currentStep = learningPath?.steps.find((step) => step.status === 'current')
      let preferred = currentStep
        ? nodes.value.find((node) => node.id === currentStep.nodeId)
        : null
      if (!preferred && currentStep) {
        const detail = await graphApi.getNode(currentStep.nodeId).catch(() => null)
        if (detail) {
          preferred = {
            id: detail.id,
            name: detail.name,
            type: detail.type,
            definition: detail.definition,
            degree: detail.degree,
            sourceCount: detail.sourceCount,
          }
          nodes.value = [preferred, ...nodes.value]
        }
      }
      if (preferred || nodes.value[0]) selectNode(preferred ?? nodes.value[0])
    }
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '暂时无法获取图谱知识点。'
  } finally {
    loading.value = false
  }
}
function selectNode(node: GraphNodeDto) {
  selectedNode.value = node
  chatVisible.value = true
}
function openInGraph() {
  if (selectedNode.value)
    void router.push({
      path: '/',
      query: { node: String(selectedNode.value.id), depth: '1', mode: 'focus' },
    })
}
onMounted(() => {
  void loadNodes()
})
</script>
