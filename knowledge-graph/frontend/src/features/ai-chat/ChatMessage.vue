<template>
  <div class="space-y-1.5">
    <!-- 用户消息 -->
    <div v-if="message.role === 'user'" class="flex justify-end">
      <p
        class="max-w-[85%] rounded-2xl rounded-br-sm bg-brand-500 px-3.5 py-2.5 text-sm leading-6 text-white shadow-theme-xs"
      >
        {{ message.content }}
      </p>
    </div>

    <!-- 助手消息 -->
    <div v-else class="flex justify-start">
      <div class="max-w-[92%]">
        <div
          class="rounded-2xl rounded-bl-sm border px-3.5 py-2.5 text-sm leading-6 shadow-theme-xs"
          :class="
            message.error
              ? 'border-error-200 bg-error-50 text-error-600 dark:border-error-500/30 dark:bg-error-500/10 dark:text-error-400'
              : 'border-gray-200 bg-gray-50 text-gray-700 dark:border-gray-800 dark:bg-white/[0.05] dark:text-gray-300'
          "
        >
          <Badge v-if="message.insufficientEvidence" color="warning" size="sm" class="mb-2">
            证据不足
          </Badge>
          <p class="whitespace-pre-wrap">{{ message.content }}</p>
        </div>

        <!-- 引用区（§7.3） -->
        <div v-if="message.citations && message.citations.length > 0" class="mt-2 flex flex-wrap gap-1.5">
          <SourceCitation
            v-for="citation in message.citations"
            :key="citation.index"
            :citation="citation"
            @locate="emit('locate-citation', citation)"
          />
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import Badge from '@/components/ui/Badge.vue'
import SourceCitation from './SourceCitation.vue'
import type { ChatCitationDto, ChatMessageDto } from '@/services/types'

defineProps<{ message: ChatMessageDto }>()

const emit = defineEmits<{
  (e: 'locate-citation', citation: ChatCitationDto): void
}>()
</script>
