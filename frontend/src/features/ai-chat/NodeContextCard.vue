<template>
  <div
    class="rounded-2xl border border-gray-200 bg-white px-4 py-3.5 dark:border-gray-800 dark:bg-white/[0.03]"
  >
    <p v-if="!node" class="text-sm text-gray-500 dark:text-gray-400">
      在图谱中单击一个节点后，AI 将围绕该节点回答问题。
    </p>
    <template v-else>
      <div class="flex items-start justify-between gap-2">
        <div class="min-w-0">
          <p class="truncate text-sm font-semibold text-gray-800 dark:text-white/90">
            {{ node.name }}
          </p>
          <p class="mt-0.5 text-xs text-gray-400">
            {{ nodeTypeLabel }}
            <template v-if="node.degree > 0">· {{ node.degree }} 个直接关联</template>
          </p>
        </div>
        <button
          class="shrink-0 text-xs font-medium text-brand-500 hover:text-brand-600"
          @click="emit('open-detail')"
        >
          查看节点详情
        </button>
      </div>
      <p v-if="node.definition" class="mt-2 line-clamp-3 text-xs leading-5 text-gray-500 dark:text-gray-400">
        {{ node.definition }}
      </p>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { NODE_TYPE_LABELS } from '@/services/types'
import type { GraphNodeDto } from '@/services/types'

const props = defineProps<{ node: GraphNodeDto | null }>()

const emit = defineEmits<{ (e: 'open-detail'): void }>()

const nodeTypeLabel = computed(() =>
  props.node ? (NODE_TYPE_LABELS[props.node.type] ?? props.node.type) : '',
)
</script>
