<template>
  <div class="artifact-panel min-w-0 space-y-5 text-sm leading-6 text-gray-700 dark:text-gray-300">
    <div class="flex flex-wrap items-center justify-between gap-3">
      <h3 class="text-base font-semibold text-gray-900 dark:text-white/90">
        {{ payloadLabel }} · {{ statusLabel(artifact.outcome) }}
      </h3>
      <Button size="sm" variant="outline" @click="openEvidence()"
        >查看全部引用（{{ artifact.evidence.length }}）</Button
      >
    </div>
    <p class="whitespace-pre-wrap break-words">{{ stageSummary(artifact.summary) }}</p>
    <div
      class="rounded-xl border border-warning-200 bg-warning-50 p-4 dark:border-warning-500/30 dark:bg-warning-500/10"
    >
      <p>
        成果覆盖：{{ artifact.coverage.completeCoverage ? '服务报告覆盖完整' : '覆盖尚不完整'
        }}{{ artifact.coverage.truncated ? ' · 返回截断' : '' }}
      </p>
      <p class="mt-1">内容语义未经评估。来源有效不代表论断正确。</p>
      <p v-if="artifact.coverage.uncoveredDocumentIds.length" class="mt-1 break-words">
        尚有 {{ artifact.coverage.uncoveredDocumentIds.length }} 份资料未覆盖，请查看诊断详情。
      </p>
      <ul class="mt-2 list-inside list-disc break-words">
        <li v-for="(reason, index) in limitations" :key="index">{{ reasonLabel(reason) }}</li>
      </ul>
    </div>
    <dl v-if="artifact.payload.kind === 'document_metadata'" class="grid gap-3 sm:grid-cols-2">
      <div>
        <dt class="text-gray-500 dark:text-gray-400">资料</dt>
        <dd>{{ artifact.payload.title || `资料 ${artifact.payload.documentId}` }}</dd>
      </div>
      <div>
        <dt class="text-gray-500 dark:text-gray-400">处理 / 生命周期</dt>
        <dd>
          {{ statusLabel(artifact.payload.processingStatus) }} /
          {{
            artifact.payload.lifecycleStatus
              ? statusLabel(artifact.payload.lifecycleStatus)
              : '未提供'
          }}
        </dd>
      </div>
      <div>
        <dt class="text-gray-500 dark:text-gray-400">源文件</dt>
        <dd>{{ artifact.payload.sourceFilePresent ? '原文件存在' : '未报告原文件存在' }}</dd>
      </div>
    </dl>
    <p v-if="artifact.payload.kind === 'document_metadata'" class="text-xs">
      处理完成或处于启用状态不代表语义正确、人工核验或正式掌握度。原文件存在性不能说明已保存片段是否可读。
    </p>
    <div v-else-if="artifact.payload.kind === 'source_read'" class="space-y-4">
      <p class="font-medium">
        {{ artifact.payload.items.length
          ? `已找到 ${artifact.payload.items.length} 个片段`
          : '未找到匹配片段' }}
      </p>
      <article
        v-for="item in artifact.payload.items"
        :key="item.sourceId"
        class="rounded-xl border border-gray-200 p-4 dark:border-gray-800"
      >
        <h4 class="font-medium">原文摘录 · 单元 {{ item.unitIndex }}</h4>
        <p class="mt-1 text-xs text-gray-500 dark:text-gray-400">
          {{ item.sourceLocator || '位置未提供' }}{{ item.bodyTruncated ? ' · 原文正文截断' : '' }}
        </p>
        <blockquote class="mt-3 whitespace-pre-wrap break-words">{{ item.content }}</blockquote>
        <Button class="mt-3" size="sm" variant="ghost" @click="openEvidence([item.sourceId])"
          >定位原文引用与重读入口</Button
        >
      </article>
    </div>
    <div v-else-if="artifact.payload.kind === 'document_research'" class="space-y-4">
      <p v-if="!artifact.payload.claims.length">本次未返回论断，请查看图谱观察和覆盖缺口。</p>
      <article
        v-for="claim in artifact.payload.claims"
        :key="claim.claimId"
        class="rounded-xl border border-gray-200 p-4 dark:border-gray-800"
      >
        <h4 class="font-medium">
          {{
            claim.kind === 'source_fact'
              ? '原文事实摘录'
              : claim.kind === 'graph_fact'
                ? '图谱事实'
                : claim.kind === 'gap'
                  ? '研究缺口'
                  : claim.kind === 'conflict'
                    ? '冲突记录'
                    : '模型说明'
          }}
        </h4>
        <p class="mt-2 whitespace-pre-wrap break-words">{{ claim.text }}</p>
        <p class="mt-2 text-xs">{{ claim.sourceIds.length }} 个来源引用 · 语义未经评估</p>
        <details class="mt-2 break-all text-xs">
          <summary class="cursor-pointer">引用标识</summary>
          <p v-for="sourceId in claim.sourceIds" :key="sourceId" class="mt-1">{{ sourceId }}</p>
        </details>
        <Button
          v-if="claim.sourceIds.length"
          class="mt-3"
          size="sm"
          variant="ghost"
          @click="openEvidence(claim.sourceIds)"
          >定位对应引用</Button
        >
      </article>
      <div class="rounded-xl bg-gray-50 p-4 dark:bg-white/[0.03]">
        <h4 class="font-medium">研究子图</h4>
        <p class="mt-1">
          {{
            artifact.payload.graph.empty
              ? '本次实际图谱观察为空'
              : `服务返回 ${artifact.payload.graph.nodes.length} 个节点、${artifact.payload.graph.edges.length} 条边、${artifact.payload.graph.paths.length} 条路径`
          }}
        </p>
        <p v-if="artifact.payload.graph.noPathWithinDepth === true" class="mt-1">
          本次检索深度内未找到路径，不表示全图不存在路径。
        </p>
        <p v-if="artifact.payload.graph.pathTruncated === true" class="mt-1">路径返回截断。</p>
        <p class="mt-2 text-xs">研究图谱为只读视图，仅查看当前成果的授权子图。</p>
        <RouterLink
          v-if="canOpenGraph"
          :to="graphContext(artifact.runId, artifact.artifactId)"
          class="mt-3 inline-flex min-h-9 items-center rounded-lg border border-gray-300 px-3 py-2 text-xs font-medium text-gray-700 hover:bg-gray-100 focus-visible:outline-2 focus-visible:outline-brand-500 dark:border-gray-700 dark:text-gray-300 dark:hover:bg-gray-800"
          >查看研究图谱</RouterLink
        >
        <p v-else class="mt-2 text-xs">此成果尚无身份一致的图谱观察，无法打开研究图谱。</p>
        <details class="mt-2 break-all text-xs">
          <summary class="cursor-pointer">图谱联动上下文</summary>
          <p class="mt-1">{{ graphHref }}；仅携带本成果上下文，URL 不授予访问权限。</p>
        </details>
      </div>
    </div>
    <div v-else-if="artifact.payload.kind === 'citation_check'" class="space-y-4">
      <p>
        当前有效引用论断 {{ artifact.payload.currentClaims }} / 本次请求
        {{ artifact.payload.requestedClaims }}；{{
          artifact.payload.completeCoverage ? '本次请求的引用已覆盖' : '本次请求仍有引用缺口'
        }}。
      </p>
      <p>核查范围仅限本次请求的论断，不代表整份资料或图谱事实已覆盖。</p>
      <article
        v-for="(check, checkIndex) in artifact.payload.checks"
        :key="check.claimId"
        class="rounded-xl border border-gray-200 p-4 dark:border-gray-800"
      >
        <h4 class="break-words font-medium">
          论断 {{ checkIndex + 1 }} · {{ statusLabel(check.referenceStatus) }}
        </h4>
        <details class="mt-2 text-xs">
          <summary class="cursor-pointer">论断身份与状态详情</summary>
          <p>{{ check.claimId }} · {{ check.referenceStatus }}</p>
        </details>
        <ul class="mt-2 space-y-2">
          <li v-for="(source, index) in check.sources ?? []" :key="index">
            来源 {{ index + 1 }}：{{ statusLabel(source.status)
            }}<span v-if="source.truncated"> · 核查截断</span>
            <details class="mt-1 break-all text-xs">
              <summary class="cursor-pointer">来源核查详情</summary>
              <p>原引用：{{ source.originalSourceId ?? '未提供' }}</p>
              <p>原始状态：{{ source.status }}</p>
              <p>片段：{{ source.chunkId ?? '未提供' }} · {{ source.locator ?? '位置未提供' }}</p>
              <p>预期 hash：{{ source.expectedHash ?? '未提供' }}</p>
              <p>实际 hash：{{ source.actualHash ?? '未提供' }}</p>
            </details>
          </li>
        </ul>
      </article>
      <div v-if="artifact.payload.uncheckedClaims.length">
        <h4 class="font-medium">未核查论断</h4>
        <ul class="mt-2 list-inside list-disc break-words">
          <li v-for="item in artifact.payload.uncheckedClaims" :key="item.claimId">
            {{ reasonLabel(item.reasonCode) }}
            <details class="mt-1 text-xs">
              <summary class="cursor-pointer">未核查原因详情</summary>
              <p>{{ item.claimId }}：{{ item.reasonCode }}</p>
            </details>
          </li>
        </ul>
      </div>
    </div>
    <details class="break-words rounded-xl border border-gray-200 p-4 dark:border-gray-800">
      <summary
        class="cursor-pointer font-medium focus-visible:outline-2 focus-visible:outline-brand-500"
      >
        成果与工具观察详情
      </summary>
      <p class="mt-3">成果：{{ artifact.artifactId }}；产生任务：{{ artifact.producerTaskId }}</p>
      <p>原始状态：{{ artifact.outcome }}；原始摘要：{{ artifact.summary }}</p>
      <p v-if="artifact.payload.kind === 'document_metadata'">
        处理：{{ artifact.payload.processingStatus }}；生命周期：{{
          artifact.payload.lifecycleStatus
        }}；原文件存在：{{ artifact.payload.sourceFilePresent }}
      </p>
      <p>未覆盖资料 ID：{{ artifact.coverage.uncoveredDocumentIds.join('、') || '无' }}</p>
      <p v-for="(reason, index) in limitations" :key="`raw:${index}`">原始原因：{{ reason }}</p>
      <p>直接输入成果：{{ artifact.inputArtifactIds.join('、') || '无' }}</p>
      <p>
        库：{{ artifact.authorizedScope.libraryIds.join('、') || '未约束' }}；资料：{{
          artifact.authorizedScope.documentIds.join('、') || '未约束'
        }}
      </p>
      <p>
        范围匹配：{{
          artifact.scopeMatched ? '服务报告存在匹配观察' : '未报告匹配观察'
        }}；匹配不证明全量取材。
      </p>
      <p>覆盖口径：{{ artifact.coverage.coverageKind }}；创建：{{ artifact.createdAt }}</p>
      <ul class="mt-3 space-y-3">
        <li v-for="observation in artifact.observations" :key="observation.toolCallId">
          <p>工具调用标识：{{ observation.toolCallId }}；原始状态：{{ observation.status }}</p>
          <p>
            {{ observation.operation }} · {{ statusLabel(observation.status) }} ·
            {{ observation.observedAt }}
          </p>
          <p v-if="observation.errorCode">错误：{{ observation.errorCode }}</p>
          <p v-for="(reason, index) in observation.limitations" :key="index">{{ reason }}</p>
        </li>
      </ul>
    </details>
    <EvidenceDrawer
      v-if="evidenceOpen"
      :artifact="artifact"
      :can-reread="canReread"
      :preferred-source-id="preferredSourceId"
      :target-missing="evidenceTargetMissing"
      @close="evidenceOpen = false"
    />
  </div>
</template>
<script setup lang="ts">
import { computed, ref } from 'vue'
import Button from '@/components/ui/Button.vue'
import type { Artifact } from '@/services/assistantTypes'
import { graphContext, statusLabel, stageSummary, reasonLabel } from './presentation'
import EvidenceDrawer from './EvidenceDrawer.vue'
const props = defineProps<{ artifact: Artifact; canReread: boolean }>()
const evidenceOpen = ref(false)
const preferredSourceId = ref<string | undefined>()
const evidenceTargetMissing = ref(false)
function openEvidence(sourceIds: readonly string[] = []) {
  preferredSourceId.value = sourceIds.find((sourceId) =>
    props.artifact.evidence.some((entry) => entry.sourceId === sourceId),
  )
  evidenceTargetMissing.value = sourceIds.length > 0 && preferredSourceId.value === undefined
  evidenceOpen.value = true
}
const canOpenGraph = computed(() => {
  const payload = props.artifact.payload
  return (
    payload.kind === 'document_research' &&
    payload.graph.observedAt !== null &&
    payload.graph.runId === props.artifact.runId &&
    payload.graph.artifactId === props.artifact.artifactId &&
    payload.graph.taskId === props.artifact.producerTaskId
  )
})
const payloadLabel = computed(
  () =>
    ({
      document_metadata: '资料信息',
      source_read: '原文片段',
      document_research: '研究成果',
      citation_check: '引用核查',
    })[props.artifact.payload.kind],
)
const limitations = computed(() => [
  ...new Set([
    ...props.artifact.limitations,
    ...props.artifact.coverage.limitations,
    ...('coverage' in props.artifact.payload ? props.artifact.payload.coverage.limitations : []),
    ...(props.artifact.payload.kind === 'document_research'
      ? props.artifact.payload.graph.coverage.limitations
      : []),
  ]),
])
const graphHref = computed(() => {
  const context = graphContext(props.artifact.runId, props.artifact.artifactId)
  return `${context.path}?${new URLSearchParams(context.query)}`
})
</script>
<style scoped>
.artifact-panel {
  overflow-wrap: anywhere;
}
.artifact-panel :deep(details > summary) {
  border-radius: 0.25rem;
}
.artifact-panel :deep(details > summary:focus-visible) {
  outline: 2px solid var(--color-brand-500);
  outline-offset: 2px;
}
</style>
