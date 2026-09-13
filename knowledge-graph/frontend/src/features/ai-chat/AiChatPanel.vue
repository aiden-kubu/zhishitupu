<template>
  <div class="flex h-full min-h-0 flex-col">
    <!-- 头部 -->
    <div class="flex items-center justify-between border-b border-gray-100 px-4 py-3 dark:border-gray-800">
      <div class="flex items-center gap-2">
        <span class="flex h-8 w-8 items-center justify-center rounded-lg bg-brand-50 text-brand-500 dark:bg-brand-500/15 dark:text-brand-400">
          <ChatIcon class="h-4.5 w-4.5" />
        </span>
        <div>
          <h3 class="text-sm font-semibold text-gray-800 dark:text-white/90">AI 知识助手</h3>
          <p class="text-xs text-gray-400">仅基于知识库回答</p>
        </div>
      </div>
      <div class="flex items-center gap-1">
        <button
          class="rounded-lg px-2.5 py-1.5 text-xs font-medium text-gray-500 hover:bg-gray-100 dark:text-gray-400 dark:hover:bg-white/[0.05]"
          :disabled="!node || chat.isLoading(node.id)"
          @click="node && chat.startNew(node.id)"
        >
          新对话
        </button>
        <button
          class="flex h-8 w-8 items-center justify-center rounded-lg text-gray-400 hover:bg-gray-100 dark:hover:bg-white/[0.05] max-lg:hidden"
          aria-label="折叠面板"
          @click="emit('collapse')"
        >
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none">
            <path
              d="M9.5 5L16.5 12L9.5 19"
              stroke="currentColor"
              stroke-width="2"
              stroke-linecap="round"
              stroke-linejoin="round"
              class="rtl:rotate-180"
            />
          </svg>
        </button>
      </div>
    </div>

    <div class="flex min-h-0 flex-1 flex-col gap-3 px-4 py-3">
      <!-- 当前节点卡片（§7.1） -->
      <NodeContextCard :node="node" @open-detail="emit('open-detail')" />

      <!-- 快捷提问 -->
      <div class="flex flex-wrap gap-1.5">
        <button
          v-for="question in quickQuestions"
          :key="question"
          type="button"
          class="rounded-full border border-gray-200 px-3 py-1 text-xs text-gray-600 transition hover:border-brand-300 hover:text-brand-500 disabled:cursor-not-allowed disabled:opacity-50 dark:border-gray-700 dark:text-gray-400 dark:hover:border-brand-500/40"
          :disabled="!node || chat.isLoading(node.id)"
          @click="sendQuestion(question)"
        >
          {{ question }}
        </button>
      </div>

      <!-- 对话区 -->
      <div ref="messagesRef" class="min-h-0 flex-1 space-y-3 overflow-y-auto pe-1">
        <p
          v-if="!node"
          class="mt-8 text-center text-sm text-gray-400 dark:text-gray-500"
        >
          请先选择一个知识节点（§7.2：未选择节点时输入不可用）
        </p>
        <template v-else>
          <p
            v-if="messages.length === 0 && chat.isHydrating(node.id)"
            class="mt-8 text-center text-xs text-gray-400 dark:text-gray-500"
          >
            正在加载会话记录…
          </p>
          <p
            v-else-if="messages.length === 0 && !chat.isLoading(node.id)"
            class="mt-8 text-center text-xs leading-6 text-gray-400 dark:text-gray-500"
          >
            AI 回答默认只依据知识库证据（§7.3）。<br />
            证据不足时会明确提示，不会编造答案。
          </p>
          <ChatMessage
            v-for="(message, index) in messages"
            :key="index"
            :message="message"
            @locate-citation="onLocateCitation"
          />
          <div v-if="chat.isLoading(node.id)" class="flex items-center gap-2 px-1">
            <span class="flex gap-1">
              <span class="h-1.5 w-1.5 animate-bounce rounded-full bg-brand-500 [animation-delay:0ms]"></span>
              <span class="h-1.5 w-1.5 animate-bounce rounded-full bg-brand-500 [animation-delay:150ms]"></span>
              <span class="h-1.5 w-1.5 animate-bounce rounded-full bg-brand-500 [animation-delay:300ms]"></span>
            </span>
            <button
              class="text-xs text-gray-400 underline hover:text-gray-600"
              @click="node && chat.stop(node.id)"
            >
              停止
            </button>
          </div>
        </template>
      </div>
    </div>

    <Alert
      v-if="citationNotice"
      variant="info"
      title="原文定位"
      :message="citationNotice"
      class="mx-4 mb-2"
    />

    <!-- 输入区（§7.1） -->
    <div class="border-t border-gray-100 px-4 py-3 dark:border-gray-800">
      <div class="flex items-end gap-2">
        <TextArea
          v-model="input"
          :rows="2"
          :placeholder="node ? '输入问题，例如：它和相邻概念有什么区别？' : '请先选择一个知识节点'"
          :disabled="!node || (node ? chat.isLoading(node.id) : false)"
          class="flex-1"
        />
        <Button
          size="sm"
          class="!px-3 !py-2.5"
          :disabled="!node || chat.isLoading(node.id) || !input.trim()"
          @click="sendQuestion()"
        >
          <SendIcon class="h-4 w-4" />
        </Button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import Alert from '@/components/ui/Alert.vue'
import Button from '@/components/ui/Button.vue'
import TextArea from '@/components/ui/form/TextArea.vue'
import { ChatIcon, SendIcon } from '@/icons'
import NodeContextCard from './NodeContextCard.vue'
import ChatMessage from './ChatMessage.vue'
import { useAiChat } from '@/composables/useAiChat'
import type { ChatCitationDto, GraphNodeDto } from '@/services/types'

const props = defineProps<{ node: GraphNodeDto | null }>()

const emit = defineEmits<{
  (e: 'collapse'): void
  (e: 'open-detail'): void
}>()

const chat = useAiChat()
const input = ref('')
const citationNotice = ref<string | null>(null)
const messagesRef = ref<HTMLElement | null>(null)

const quickQuestions = ['通俗解释', '与相邻节点对比', '生成练习题']

const messages = computed(() => (props.node ? chat.getMessages(props.node.id) : []))

// 切换/选中节点时自动加载该节点最近一次会话（§7.2）
watch(
  () => props.node?.id,
  (id) => {
    if (id) chat.openNode(id)
  },
  { immediate: true },
)

watch(
  () => messages.value.length,
  async () => {
    await nextTick()
    messagesRef.value?.scrollTo({ top: messagesRef.value.scrollHeight, behavior: 'smooth' })
  },
)

async function sendQuestion(preset?: string) {
  if (!props.node) return
  const content = (preset ?? input.value).trim()
  if (!content) return
  if (!preset) input.value = ''
  citationNotice.value = null
  await chat.send(props.node.id, content, 1)
}

function onLocateCitation(citation: ChatCitationDto) {
  citationNotice.value = `「${citation.documentName} · 引用 ${citation.index}」的原文预览将在阶段 D（文档解析）与阶段 F（问答引用定位）交付后可用。`
}
</script>
