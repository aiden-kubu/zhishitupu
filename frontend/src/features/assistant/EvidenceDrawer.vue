<template>
  <Modal full-screen-backdrop @close="emit('close')">
    <template #body>
      <div
        ref="drawer"
        class="evidence-drawer relative ms-auto h-full min-w-0 w-full max-w-2xl border-s border-gray-200 bg-white p-5 text-gray-700 dark:border-gray-800 dark:bg-gray-900 dark:text-gray-300 sm:p-7"
      >
        <div class="flex items-center justify-between gap-3">
          <h2 class="text-lg font-semibold text-gray-900 dark:text-white/90">引用与原文</h2>
          <Button size="sm" variant="outline" @click="emit('close')">关闭</Button>
        </div>
        <p class="mt-2 text-sm leading-6">
          状态记录不是正文，引用数量不等于片段数量。只有含原文指针的引用提供重读入口；指针不表示引用已核查，引用有效也不代表论断正确。内容语义未经评估。
        </p>
        <p class="mt-2 text-xs">
          共 {{ artifact.evidence.length }} 条引用，其中
          {{ artifact.evidence.filter((entry) => entry.rereadPointer).length }} 条含原文重读指针；全部继承与原始引用保留在下方。
        </p>
        <p v-if="targetMissing" class="mt-2 text-sm" role="status">所选条目未返回对应引用，当前显示全部引用。</p>
        <p v-if="!artifact.evidence.length" class="mt-6">本成果没有返回引用。</p>
        <ul class="mt-5 space-y-4">
          <li
            v-for="(entry, index) in artifact.evidence"
            :key="`${entry.sourceId}:${index}`"
            :data-source-id="entry.sourceId"
            :class="entry.sourceId === preferredSourceId ? 'ring-2 ring-brand-500' : ''"
            class="scroll-mt-3 rounded-xl border border-gray-200 p-4 dark:border-gray-800 focus-visible:outline-2 focus-visible:outline-brand-500"
            tabindex="-1"
          >
            <div class="flex flex-wrap items-start justify-between gap-2">
              <h3 class="min-w-0 break-words text-sm font-semibold">
                引用 {{ index + 1 }} ·
                {{ evidenceKindLabel(entry) }}
              </h3>
              <span class="text-xs">{{ statusLabel(entry.sourceState) }}</span>
            </div>
            <p v-if="entry.sourceId === preferredSourceId" class="mt-1 text-xs text-brand-600 dark:text-brand-400">当前定位引用</p>
            <p class="mt-2 text-sm">{{ entry.locator || '服务未提供位置描述' }}</p>
            <blockquote
              v-if="entry.excerpt"
              class="mt-3 whitespace-pre-wrap break-words border-s-2 border-brand-300 ps-3 text-sm dark:border-brand-700"
            >
              {{ entry.excerpt }}
            </blockquote>
            <p class="mt-2 text-xs text-gray-500 dark:text-gray-400">
              观察时间：{{ entry.observedAt }}
            </p>
            <Button
              v-if="entry.rereadPointer"
              class="mt-3"
              size="sm"
              variant="outline"
              :disabled="!canReread || busy[index]"
              @click="reread(entry, index)"
              >{{ busy[index] ? '正在重读…' : '精确重读原文' }}</Button
            >
            <p v-if="!entry.rereadPointer" class="mt-3 text-xs">
              {{ entry.kind === 'system_state' && entry.object.kind === 'document'
                ? '这是资料状态记录，不是正文；没有片段指针，因此不提供原文重读。'
                : '此引用未提供原文指针，可查看来源与身份详情，不提供原文重读。' }}
            </p>
            <p v-else-if="!canReread" class="mt-2 text-xs">原文重读能力未启用，当前无法读取。</p>
            <p
              v-if="errors[index]"
              class="mt-3 break-words text-sm text-error-600 dark:text-error-400"
              role="alert"
            >
              {{ errorSummary(errors[index]) }}
            </p>
            <div
              v-if="results[index]"
              class="mt-4 rounded-lg bg-gray-50 p-3 dark:bg-white/[0.03]"
              aria-live="polite"
            >
              <p class="text-sm font-medium">
                {{ statusLabel(results[index].status)
                }}{{ results[index].bodyTruncated ? ' · 正文截断' : '' }}
              </p>
              <p class="mt-2 whitespace-pre-wrap break-words text-sm leading-7">
                {{ results[index].content }}
              </p>
              <p class="mt-2 text-xs">正式来源可用；这不表示正式测评可用或语义已验证。</p>
              <ul
                v-if="results[index].limitations.length"
                class="mt-2 list-inside list-disc break-words text-xs"
              >
                <li v-for="(reason, n) in results[index].limitations" :key="n">
                  {{ reasonLabel(reason) }}
                </li>
              </ul>
            </div>
            <details class="mt-3 break-all text-xs">
              <summary
                class="cursor-pointer rounded focus-visible:outline-2 focus-visible:outline-brand-500"
              >
                来源身份与定位详情
              </summary>
              <dl class="mt-2 space-y-2">
                <div>
                  <dt>原始引用类型 / 状态 / 对象类型</dt>
                  <dd>{{ entry.kind }} / {{ entry.sourceState }} / {{ entry.object.kind }}</dd>
                </div>
                <div v-if="errors[index]">
                  <dt>重读错误原始详情</dt>
                  <dd>{{ errors[index] }}</dd>
                </div>
                <div>
                  <dt>引用标识</dt>
                  <dd>{{ entry.sourceId }}</dd>
                </div>
                <div>
                  <dt>最初任务 / 成果</dt>
                  <dd>{{ entry.originTaskId }} / {{ entry.originArtifactId }}</dd>
                </div>
                <div>
                  <dt>对象 / 版本</dt>
                  <dd>
                    {{ entry.object.kind }} · {{ entry.object.id }} /
                    {{ entry.object.version ?? '未提供' }}
                  </dd>
                </div>
                <div>
                  <dt>范围</dt>
                  <dd>
                    库：{{ entry.scope.libraryIds.join('、') || '未约束' }}；资料：{{
                      entry.scope.documentIds.join('、') || '未约束'
                    }}；节点：{{ entry.scope.nodeIds.join('、') || '未约束' }}
                  </dd>
                </div>
                <template v-if="entry.rereadPointer"
                  ><div>
                    <dt>资料 / 片段 ID</dt>
                    <dd>
                      {{ entry.rereadPointer.documentId }} / {{ entry.rereadPointer.chunkId }}
                    </dd>
                  </div>
                  <div>
                    <dt>内容 SHA-256</dt>
                    <dd>{{ entry.rereadPointer.contentHash }}</dd>
                  </div></template
                >
                <template v-if="results[index]"
                  ><div>
                    <dt>重读原始状态与限制</dt>
                    <dd>{{ results[index].status }}</dd>
                    <dd v-for="(reason, n) in results[index].limitations" :key="n">{{ reason }}</dd>
                  </div>
                  <div>
                    <dt>重读观察时间</dt>
                    <dd>{{ results[index].observedAt }}</dd>
                  </div>
                  <div>
                    <dt>单元 ID / 序号；片段序号；偏移</dt>
                    <dd>
                      {{ results[index].unitId }} / {{ results[index].unitIndex }}；{{
                        results[index].chunkIndex
                      }}；{{ results[index].startOffset }}～{{ results[index].endOffset }}
                    </dd>
                  </div>
                  <div>
                    <dt>重读定位 / 内容 SHA-256</dt>
                    <dd>
                      {{ results[index].sourceLocator ?? '未提供' }} /
                      {{ results[index].contentHash }}
                    </dd>
                  </div></template
                >
              </dl>
            </details>
          </li>
        </ul>
      </div>
    </template>
  </Modal>
</template>
<script setup lang="ts">
import { nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import Modal from '@/components/ui/Modal.vue'
import Button from '@/components/ui/Button.vue'
import { assistantApi } from '@/services/assistantApi'
import { ApiError } from '@/services/http'
import type { Artifact, Evidence, Reread } from '@/services/assistantTypes'
import { errorMessage, errorSummary, statusLabel, reasonLabel, evidenceKindLabel } from './presentation'
const props = defineProps<{
  artifact: Artifact
  canReread: boolean
  preferredSourceId?: string
  targetMissing?: boolean
}>()
const emit = defineEmits<{ (event: 'close'): void }>()
const drawer = ref<HTMLElement | null>(null)
const results = ref<Record<number, Reread>>({}),
  errors = ref<Record<number, string>>({}),
  busy = ref<Record<number, boolean>>({})
const controllers = new Set<AbortController>()
let disposed = false
onMounted(async () => {
  await nextTick()
  if (disposed || !props.preferredSourceId) return
  const target = Array.from(drawer.value?.querySelectorAll<HTMLElement>('[data-source-id]') ?? [])
    .find((entry) => entry.dataset.sourceId === props.preferredSourceId)
  target?.focus({ preventScroll: true })
  target?.scrollIntoView({ block: 'start', behavior: 'auto' })
})
async function reread(entry: Evidence, index: number) {
  if (!entry.rereadPointer || !props.canReread || busy.value[index]) return
  const controller = new AbortController()
  controllers.add(controller)
  busy.value[index] = true
  delete errors.value[index]
  delete results.value[index]
  try {
    const data = await assistantApi.reread(
      props.artifact.runId,
      props.artifact.artifactId,
      entry.sourceId,
      entry.rereadPointer.contentHash,
      controller.signal,
    )
    if (disposed) return
    if (
      data.runId !== props.artifact.runId ||
      data.artifactId !== props.artifact.artifactId ||
      data.originalSourceId !== entry.sourceId ||
      data.documentId !== entry.rereadPointer.documentId ||
      data.chunkId !== entry.rereadPointer.chunkId ||
      data.contentHash.toLowerCase() !== entry.rereadPointer.contentHash.toLowerCase()
    )
      throw new ApiError('BAD_RESPONSE', '原文身份与所选引用不一致，正文不予展示。', 200)
    results.value[index] = data
  } catch (error) {
    if (!disposed) errors.value[index] = errorMessage(error, 'source')
  } finally {
    controllers.delete(controller)
    if (!disposed) busy.value[index] = false
  }
}
onBeforeUnmount(() => {
  disposed = true
  controllers.forEach((c) => c.abort())
})
</script>
<style scoped>
.evidence-drawer {
  overflow-wrap: anywhere;
}
.evidence-drawer :deep(p),
.evidence-drawer :deep(blockquote),
.evidence-drawer :deep(dd),
.evidence-drawer :deep(li),
.evidence-drawer :deep(summary) {
  min-width: 0;
  overflow-wrap: anywhere;
}
</style>
