<template>
  <aside
    class="max-h-[45dvh] w-full shrink-0 overflow-y-auto border-t border-gray-200 bg-white p-4 text-sm text-gray-700 lg:max-h-none lg:w-[360px] lg:border-s lg:border-t-0 dark:border-gray-800 dark:bg-gray-900 dark:text-gray-300"
  >
    <h2 class="font-semibold text-gray-800 dark:text-white/90">研究成果 · 只读</h2>
    <template v-if="graph">
      <p class="mt-2 break-all">
        运行 {{ graph.runId }} / 成果 {{ graph.artifactId }} / 任务 {{ graph.taskId }}
      </p>
      <p class="mt-2">授权范围：{{ scopeText(graph.authorizedScope) }}</p>
      <p v-for="(scope, i) in graph.observedScopes" :key="i" class="mt-1">
        观察范围：{{ scopeText(scope) }}
      </p>
      <p class="mt-2">观察时间：{{ graph.observedAt ?? '未提供' }}；非事务一致快照</p>
      <p class="mt-2">
        真实结果 {{ graph.nodes.length }} 节点 / {{ graph.edges.length }} 边；仅本次子图
      </p>
      <p class="mt-2">
        覆盖：{{ graph.coverage.completeCoverage ? '本次覆盖完整' : '部分覆盖' }}；{{
          graph.coverage.coverageKind
        }}；{{ graph.coverage.truncated ? '已截断' : '未截断' }}
      </p>
      <p v-if="graph.coverage.uncoveredDocumentIds.length">
        未覆盖资料：{{ graph.coverage.uncoveredDocumentIds.join('、') }}
      </p>
      <ul class="mt-2 list-inside list-disc text-warning-600 dark:text-warning-400">
        <li v-for="(item, i) in graph.coverage.limitations" :key="i">{{ item }}</li>
      </ul>
      <p class="mt-2">
        深度内无路径：{{ nullableFlag(graph.noPathWithinDepth) }}；路径截断：{{
          nullableFlag(graph.pathTruncated)
        }}
      </p>
      <p class="mt-2 text-xs text-gray-500 dark:text-gray-400">
        节点、边和引用均为宿主授权结果。首版契约不含草稿；草稿不并入真实统计。
      </p>

      <h3 class="mt-4 font-medium">路径（按真实边顺序 source → target）</h3>
      <p v-if="!graph.paths.length" class="mt-2">本次结果无路径；不代表全图不存在路径。</p>
      <button
        v-for="(path, index) in graph.paths"
        :key="index"
        type="button"
        class="mt-2 block w-full rounded-lg border border-gray-200 p-2 text-start dark:border-gray-700"
        :class="{ 'bg-brand-50 dark:bg-brand-950': selectedPath === index }"
        @click="emit('locate-path', index)"
      >
        定位路径 {{ index + 1 }}：{{ path.map(edgeText).join('；') }}
      </button>
      <label v-if="selectedPath !== null" class="mt-2 flex items-center gap-2"
        ><input
          type="checkbox"
          :checked="onlyPath"
          @change="emit('update:onlyPath', ($event.target as HTMLInputElement).checked)"
        />仅看选中路径</label
      >

      <h3 class="mt-4 font-medium">真实节点</h3>
      <button
        v-for="node in graph.nodes"
        :key="node.nodeId"
        type="button"
        class="mt-2 block w-full rounded-lg border border-gray-200 p-2 text-start dark:border-gray-700"
        @click="emit('select-node', node.nodeId)"
      >
        {{ node.name ?? '未提供名称' }} #{{ node.nodeId }}
      </button>
      <h3 class="mt-4 font-medium">真实关系</h3>
      <button
        v-for="edge in graph.edges"
        :key="edge.edgeId"
        type="button"
        class="mt-2 block w-full rounded-lg border border-gray-200 p-2 text-start dark:border-gray-700"
        @click="emit('select-edge', edge.edgeId)"
      >
        #{{ edge.edgeId }}：{{ edgeText(edge.edgeId) }}
      </button>
      <h3 class="mt-4 font-medium">成果引用（{{ graph.evidence.length }}）</h3>
      <p class="mt-2 text-xs">引用当前或原文可用不等于语义成立、人工核验或全图来源完整。</p>
      <button
        type="button"
        class="mt-2 text-brand-600 dark:text-brand-400"
        @click="showSources = true"
      >
        查看成果引用与重读原文
      </button>
    </template>
    <p v-else class="mt-2">等待有效的授权成果；失败时请刷新或返回成果。</p>
    <RouterLink :to="returnTo" class="mt-4 inline-block text-brand-600 dark:text-brand-400"
      >返回原成果</RouterLink
    >
    <p class="mt-2 text-xs text-gray-500 dark:text-gray-400">
      运行仅短期保存在宿主内存，重启或过期后不可恢复。
    </p>
  </aside>

  <Teleport to="body">
    <div
      v-if="graph && (node || edge || showSources)"
      class="fixed inset-0 z-99999 flex"
      data-research-detail
    >
      <button
        type="button"
        aria-label="关闭详情"
        tabindex="-1"
        class="absolute inset-0 bg-gray-900/50"
        @click="close"
      />
      <aside
        ref="dialog"
        role="dialog"
        aria-modal="true"
        aria-label="研究成果详情"
        tabindex="-1"
        @keydown="onDialogKeydown"
        class="relative ms-auto h-full w-[440px] max-w-[94vw] overflow-y-auto bg-white p-5 text-sm text-gray-700 shadow-theme-xl dark:bg-gray-900 dark:text-gray-300"
      >
        <div class="flex items-center justify-between">
          <h2 class="font-semibold">
            {{ node ? '节点来源' : edge ? '关系来源' : '成果引用' }} · 只读
          </h2>
          <button type="button" class="text-brand-600 dark:text-brand-400" @click="close">
            关闭
          </button>
        </div>
        <template v-if="node">
          <h3 class="mt-4 font-medium">{{ node.name ?? '未提供名称' }} #{{ node.nodeId }}</h3>
          <p class="mt-2">
            类型 {{ node.type ?? '未提供' }}；状态 {{ node.status }}；来源 {{ node.origin }}；审核
            {{ node.reviewStatus }}
          </p>
          <p class="mt-2">
            库 ID：{{ node.libraryIds.join('、') || '未提供' }}；别名
            {{ node.aliases.join('、') || '无' }}
          </p>
          <p class="mt-2 whitespace-pre-wrap">{{ node.definition ?? '未提供定义' }}</p>
          <p v-if="node.definitionTruncated" class="mt-2">定义已截断</p>
          <p class="mt-2">更新时间 {{ node.updatedAt ?? '未提供' }}；非强版本</p>
          <p class="mt-2">
            来源可用提示：{{ node.sourceEvidenceAvailable ? '有' : '无' }}；不等于可核验原文指针
          </p>
        </template>
        <template v-if="edge">
          <h3 class="mt-4 font-medium">关系 #{{ edge.edgeId }}</h3>
          <p class="mt-2">{{ edgeText(edge.edgeId) }}</p>
          <p class="mt-2">
            状态 {{ edge.status }}；来源 {{ edge.origin }}；审核 {{ edge.reviewStatus }}
          </p>
          <p class="mt-2">
            更新时间 {{ edge.updatedAt ?? '未提供' }}；来源可用提示
            {{ edge.sourceEvidenceAvailable ? '有' : '无' }}
          </p>
        </template>
        <p
          v-if="(node || edge) && !objectSources.length"
          class="mt-4 text-warning-600 dark:text-warning-400"
        >
          该对象没有可明确关联的来源引用；无可核验原文。成果其他引用不能自动当作该对象来源。
        </p>
        <section
          v-for="(source, index) in sources"
          :key="index"
          class="mt-4 break-all rounded-lg border border-gray-200 p-3 dark:border-gray-700"
        >
          <p>{{ source.sourceId }} · {{ source.kind }} · {{ source.sourceState }}</p>
          <p class="mt-1">
            对象 {{ source.object.kind }}/{{ source.object.id }}；版本
            {{ source.object.version ?? '未提供' }}
          </p>
          <p class="mt-1">原任务 {{ source.originTaskId }}；原成果 {{ source.originArtifactId }}</p>
          <p class="mt-1">观察 {{ source.observedAt }}；{{ scopeText(source.scope) }}</p>
          <p class="mt-1">定位 {{ source.locator ?? '未提供' }}</p>
          <p v-if="source.excerpt" class="mt-1 whitespace-pre-wrap">摘录：{{ source.excerpt }}</p>
          <template v-if="source.rereadPointer">
            <p class="mt-1">
              资料 {{ source.rereadPointer.documentId }}；chunk
              {{ source.rereadPointer.chunkId }}；hash {{ source.rereadPointer.contentHash }}
            </p>
            <button
              type="button"
              :disabled="rereading"
              class="mt-2 text-brand-600 disabled:opacity-50 dark:text-brand-400"
              @click="emit('reread', source)"
            >
              重读原文
            </button>
          </template>
          <p v-else class="mt-1">无可核验原文；不从名称或摘录推造指针。</p>
        </section>
        <p v-if="rereading" class="mt-4" role="status">原文重读中…</p>
        <p v-if="rereadError" class="mt-4 text-error-600 dark:text-error-400" role="alert">
          {{ rereadError }}
        </p>
        <section v-if="reread" class="mt-4 break-all rounded-lg bg-gray-50 p-3 dark:bg-gray-800">
          <h3 class="font-medium">
            本次精确重读 {{ reread.status }} · {{ reread.originalSourceId }}
          </h3>
          <p class="mt-2">
            资料 {{ reread.documentId }} / chunk {{ reread.chunkId }} / unit
            {{ reread.unitId }}；hash {{ reread.contentHash }}
          </p>
          <p class="mt-2">
            位置 {{ reread.sourceLocator ?? '未提供' }}；偏移 {{ reread.startOffset }}～{{
              reread.endOffset
            }}；观察 {{ reread.observedAt }}
          </p>
          <p class="mt-2">
            {{ scopeText(reread.effectiveScope) }}；{{
              reread.bodyTruncated ? '正文截断' : '正文未截断'
            }}；语义未评估
          </p>
          <p class="mt-2 whitespace-pre-wrap">{{ reread.content }}</p>
          <p v-for="(item, i) in reread.limitations" :key="i" class="mt-2">{{ item }}</p>
        </section>
      </aside>
    </div>
  </Teleport>
</template>
<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import type { RouteLocationRaw } from 'vue-router'
import type { Evidence, Graph, Reread, Scope } from '@/services/assistantTypes'
const props = defineProps<{
  graph: Graph | null
  selectedNodeId: string | null
  selectedEdgeId: string | null
  selectedPath: number | null
  onlyPath: boolean
  returnTo: RouteLocationRaw
  reread: Reread | null
  rereadError: string | null
  rereading: boolean
}>()
const emit = defineEmits<{
  (e: 'select-node', id: string): void
  (e: 'select-edge', id: string): void
  (e: 'locate-path', index: number): void
  (e: 'update:onlyPath', value: boolean): void
  (e: 'close-detail'): void
  (e: 'reread', source: Evidence): void
}>()
const showSources = ref(false)
const node = computed(() => props.graph?.nodes.find((n) => n.nodeId === props.selectedNodeId))
const edge = computed(() => props.graph?.edges.find((e) => e.edgeId === props.selectedEdgeId))
const dialog = ref<HTMLElement | null>(null)
const dialogOpen = computed(
  () => !!props.graph && !!(node.value || edge.value || showSources.value),
)
let previousFocus: HTMLElement | null = null
let previousOverflow: string | null = null
function restoreDialogFocus() {
  if (previousOverflow !== null) document.body.style.overflow = previousOverflow
  previousOverflow = null
  if (previousFocus?.isConnected) previousFocus.focus()
  previousFocus = null
}
watch(
  dialogOpen,
  async (open) => {
    if (!open) {
      restoreDialogFocus()
      return
    }
    previousFocus = document.activeElement instanceof HTMLElement ? document.activeElement : null
    previousOverflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    await nextTick()
    if (dialogOpen.value) dialog.value?.focus()
  },
  { flush: 'post' },
)
onBeforeUnmount(restoreDialogFocus)
// Disabling the focused reread button can move browser focus to body.
watch(
  () => props.rereading,
  () => {
    if (dialogOpen.value && dialog.value && !dialog.value.contains(document.activeElement)) {
      dialog.value.focus()
    }
  },
  { flush: 'post' },
)
function onDialogKeydown(event: KeyboardEvent) {
  if (event.key === 'Escape') {
    event.preventDefault()
    event.stopPropagation()
    close()
    return
  }
  if (event.key !== 'Tab') return
  const focusable = Array.from(
    dialog.value?.querySelectorAll<HTMLElement>(
      'button:not(:disabled), a[href], input:not(:disabled), textarea:not(:disabled), select:not(:disabled), [tabindex="0"], summary',
    ) ?? [],
  ).filter((element) => element.getClientRects().length > 0)
  const first = focusable[0],
    last = focusable[focusable.length - 1]
  if (!first) {
    event.preventDefault()
    dialog.value?.focus()
    return
  }
  if (
    event.shiftKey &&
    (document.activeElement === first || document.activeElement === dialog.value)
  ) {
    event.preventDefault()
    last?.focus()
  } else if (
    !event.shiftKey &&
    (document.activeElement === last || document.activeElement === dialog.value)
  ) {
    event.preventDefault()
    first.focus()
  }
}
// Explicit ObjectRef only; no inferred document-to-node attribution.
const objectSources = computed(
  () =>
    props.graph?.evidence.filter(
      (source) =>
        source.object.id === (node.value?.nodeId ?? edge.value?.edgeId) &&
        source.object.kind === (node.value ? 'knowledge_node' : 'knowledge_edge'),
    ) ?? [],
)
const sources = computed(() =>
  showSources.value ? (props.graph?.evidence ?? []) : objectSources.value,
)
watch(
  () => [props.graph, props.selectedNodeId, props.selectedEdgeId],
  () => {
    showSources.value = false
  },
)
function close() {
  showSources.value = false
  emit('close-detail')
}
function scopeText(scope: Scope) {
  return `${scope.mode}；库 ${scope.libraryIds.join('、') || '未约束'}；资料 ${scope.documentIds.join('、') || '未约束'}；节点 ${scope.nodeIds.join('、') || '未约束'}`
}
function nullableFlag(value: boolean | null) {
  return value === null ? '未观察/不可用' : value ? '是' : '否'
}
function edgeText(id: string) {
  const e = props.graph?.edges.find((e) => e.edgeId === id)
  return e
    ? `${e.sourceNodeId} → ${e.targetNodeId}（${e.relationType}，边 ${id}）`
    : `边 ${id} 不可用`
}
</script>
