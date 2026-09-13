<template>
  <button
    type="button"
    class="inline-flex max-w-full items-center gap-1 rounded-full border border-blue-light-200 bg-blue-light-50 px-2.5 py-1 text-xs text-blue-light-600 transition hover:bg-blue-light-100 dark:border-blue-light-500/30 dark:bg-blue-light-500/10 dark:text-blue-light-400"
    :title="`${citation.documentName} · 第 ${locatorLabel} 单元（片段 ${citation.chunkId}）`"
    @click="emit('locate')"
  >
    <span class="font-semibold">{{ citation.index }}</span>
    <span class="truncate">{{ citation.documentName }}</span>
    <span class="shrink-0 opacity-80">· {{ unitLabel }}</span>
  </button>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { ChatCitationDto } from '@/services/types'

const props = defineProps<{ citation: ChatCitationDto }>()

const emit = defineEmits<{ (e: 'locate'): void }>()

const unitLabel = computed(() => {
  switch (props.citation.unitType) {
    case 'page':
      return `第 ${props.citation.unitIndex} 页`
    case 'slide':
      return `第 ${props.citation.unitIndex} 页（幻灯片）`
    case 'image':
      return `图片 #${props.citation.unitIndex}`
    default:
      return `单元 ${props.citation.unitIndex}`
  }
})

const locatorLabel = computed(() =>
  props.citation.unitType === 'image' ? String(props.citation.unitIndex) : props.citation.unitIndex,
)
</script>
