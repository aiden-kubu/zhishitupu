<template>
  <div class="space-y-3">
    <div
      v-for="candidate in candidates"
      :key="candidate.id"
      class="rounded-2xl border border-gray-200 bg-white dark:bg-white/[0.03] p-4 dark:border-gray-800"
    >
      <div class="flex flex-wrap items-start justify-between gap-3">
        <div class="min-w-0 max-w-full break-words">
          <div class="flex flex-wrap items-center gap-2">
            <p class="text-sm font-semibold text-gray-800 dark:text-white/90">{{ candidate.name }}</p>
            <Badge color="light" size="sm">{{ NODE_TYPE_LABELS[candidate.nodeType] ?? candidate.nodeType }}</Badge>
            <Badge v-if="candidate.matchedNodeId" color="warning" size="sm">
              可能与已有节点「{{ candidate.matchedNodeName ?? candidate.matchedNodeId }}」重复
            </Badge>
          </div>
          <p class="mt-1.5 line-clamp-2 text-sm text-gray-500 dark:text-gray-400">
            {{ candidate.definition }}
          </p>
          <p v-if="candidate.aliases.length > 0" class="mt-1 text-xs text-gray-400">
            别名：{{ candidate.aliases.join('、') }}
          </p>
        </div>
        <div class="flex flex-wrap items-center gap-2">
          <span class="text-xs text-gray-400">置信度 {{ Math.round(candidate.confidence * 100) }}%</span>
          <Button size="sm" variant="outline" class="!px-3 !py-1.5" @click="emit('edit', candidate)">编辑</Button>
          <Button size="sm" variant="outline" class="!px-3 !py-1.5 !text-error-500" @click="emit('reject', candidate)">
            拒绝
          </Button>
          <Button size="sm" class="!px-3 !py-1.5" @click="emit('accept', candidate)">接受</Button>
        </div>
      </div>

      <!-- 证据（§9.3 显示原文证据、来源位置） -->
      <div v-if="candidate.evidence.length > 0" class="mt-3 [overflow-wrap:anywhere] space-y-1.5 border-t border-gray-100 pt-3 dark:border-gray-800">
        <p v-for="evidence in candidate.evidence" :key="evidence.chunkId" class="text-xs text-gray-500 dark:text-gray-400">
          <span class="font-medium text-gray-600 dark:text-gray-300">
            {{ evidence.documentName }} · {{ evidence.locator }}
          </span>
          ：{{ evidence.excerpt }}
        </p>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import Badge from '@/components/ui/Badge.vue'
import Button from '@/components/ui/Button.vue'
import { NODE_TYPE_LABELS } from '@/services/types'
import type { EntityCandidate } from '@/services/reviewApi'

defineProps<{ candidates: EntityCandidate[] }>()

const emit = defineEmits<{
  (e: 'accept', candidate: EntityCandidate): void
  (e: 'reject', candidate: EntityCandidate): void
  (e: 'edit', candidate: EntityCandidate): void
}>()
</script>
