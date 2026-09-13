<template>
  <div class="space-y-3">
    <div
      v-for="candidate in candidates"
      :key="candidate.id"
      class="flex flex-wrap items-center justify-between gap-3 rounded-2xl border border-gray-200 bg-white dark:bg-white/[0.03] p-4 dark:border-gray-800"
    >
      <div class="min-w-0 max-w-full break-words">
        <p class="text-sm text-gray-800 dark:text-white/90">
          <span class="font-medium">{{ candidate.sourceName }}</span>
          <Badge color="light" size="sm" class="mx-1.5">{{ candidate.relationType }}</Badge>
          <span class="font-medium">{{ candidate.targetName }}</span>
        </p>
        <p class="mt-1 text-xs text-gray-400">
          置信度 {{ Math.round(candidate.confidence * 100) }}%
          <template v-if="candidate.matchedEdgeId">· 与已有关系重复</template>
        </p>
      </div>
      <div class="flex flex-wrap items-center gap-2">
        <Button size="sm" variant="outline" class="!px-3 !py-1.5" @click="emit('edit', candidate)">编辑</Button>
        <Button size="sm" variant="outline" class="!px-3 !py-1.5 !text-error-500" @click="emit('reject', candidate)">
          拒绝
        </Button>
        <Button size="sm" class="!px-3 !py-1.5" @click="emit('accept', candidate)">接受</Button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import Badge from '@/components/ui/Badge.vue'
import Button from '@/components/ui/Button.vue'
import type { RelationCandidate } from '@/services/reviewApi'

defineProps<{ candidates: RelationCandidate[] }>()

const emit = defineEmits<{
  (e: 'accept', candidate: RelationCandidate): void
  (e: 'reject', candidate: RelationCandidate): void
  (e: 'edit', candidate: RelationCandidate): void
}>()
</script>
