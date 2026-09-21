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
          :disabled="!node || chat.isLoading()"
          @click="startNewChat"
        >
          新对话
        </button>
        <button
          class="flex h-8 w-8 items-center justify-center rounded-lg text-gray-400 hover:bg-gray-100 dark:hover:bg-white/[0.05]"
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
      <div v-if="node" class="flex flex-wrap gap-1.5">
        <button
          v-for="question in quickQuestions"
          :key="question"
          type="button"
          class="rounded-full border border-gray-200 px-3 py-1 text-xs text-gray-600 transition hover:border-brand-300 hover:text-brand-500 disabled:cursor-not-allowed disabled:opacity-50 dark:border-gray-700 dark:text-gray-400 dark:hover:border-brand-500/40"
          :disabled="!node || chat.isLoading()"
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
          请先选择一个知识节点
        </p>
        <template v-else>
          <p
            v-if="messages.length === 0 && !chat.isLoading()"
            class="mt-8 text-center text-xs leading-6 text-gray-400 dark:text-gray-500"
          >
            AI 回答以知识库资料为事实来源，资料内容会标注出处编号。<br />
            资料没覆盖到的部分由 AI 补充讲解，与当前知识点无关时会提示资料不足。
          </p>
          <ChatMessage
            v-for="(message, index) in messages"
            :key="index"
            :message="message"
            @locate-citation="onLocateCitation"
          />
          <div v-if="chat.isLoading()" class="flex items-center gap-2 px-1">
            <span class="flex gap-1">
              <span class="h-1.5 w-1.5 animate-bounce rounded-full bg-brand-500 [animation-delay:0ms]"></span>
              <span class="h-1.5 w-1.5 animate-bounce rounded-full bg-brand-500 [animation-delay:150ms]"></span>
              <span class="h-1.5 w-1.5 animate-bounce rounded-full bg-brand-500 [animation-delay:300ms]"></span>
            </span>
            <button
              class="text-xs text-gray-400 underline hover:text-gray-600"
              @click="node && chat.stop()"
            >
              停止
            </button>
          </div>
        </template>
      </div>
    </div>

    <section
      v-if="citationNotice"
      class="mx-4 mb-2 rounded-xl border border-blue-light-500 bg-blue-light-50 p-3 dark:border-blue-light-500/30 dark:bg-blue-light-500/15"
      aria-label="原文定位"
    >
      <div class="mb-1 flex items-center justify-between gap-2">
        <h4 class="text-sm font-semibold text-gray-800 dark:text-white/90">原文定位</h4>
        <button
          type="button"
          class="rounded p-1 text-gray-400 hover:bg-white/70 hover:text-gray-600 dark:hover:bg-white/10 dark:hover:text-gray-200"
          aria-label="关闭原文定位"
          @click="citationNotice = null"
        >
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" aria-hidden="true">
            <path d="M6 6L18 18M18 6L6 18" stroke="currentColor" stroke-width="2" stroke-linecap="round" />
          </svg>
        </button>
      </div>
      <p class="whitespace-pre-line text-sm text-gray-500 dark:text-gray-400">{{ citationNotice }}</p>
    </section>

    <!-- 输入区（§7.1） -->
    <div class="border-t border-gray-100 px-4 py-3 dark:border-gray-800">
      <div class="flex items-end gap-2">
        <TextArea
          v-model="input"
          :rows="2"
          :placeholder="node ? '输入问题，例如：它和相邻概念有什么区别？' : '请先选择一个知识节点'"
          :disabled="!node || (node ? chat.isLoading() : false)"
          class="flex-1"
        />
        <Button
          size="sm"
          class="!px-3 !py-2.5"
          :disabled="!node || chat.isLoading() || !input.trim()"
          @click="sendQuestion()"
        >
          <SendIcon class="h-4 w-4" />
        </Button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onUnmounted, ref, watch } from 'vue'
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

const messages = computed(() => (props.node ? chat.getMessages() : []))

// 切换/选中节点：立即清空旧节点对话并中断旧请求（无历史临时模式）
watch(
  () => props.node?.id,
  (id) => {
    citationNotice.value = null
    if (id) chat.switchNode(id)
    else chat.dispose()
  },
  { immediate: true },
)

// 面板关闭即卸载：中止在途请求并清空内存，保证下次打开是空对话
onUnmounted(() => chat.dispose())

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
  await chat.send(content, 1)
}

function startNewChat() {
  citationNotice.value = null
  chat.startNew()
}

function onLocateCitation(citation: ChatCitationDto) {
  if (citationNotice.value?.startsWith(`「${citation.documentName} ·`) && citationNotice.value.includes(`片段 #${citation.chunkId}」`)) {
    citationNotice.value = null
    return
  }
  const unit = citation.unitType === 'page' ? `第 ${citation.unitIndex} 页` : citation.unitType === 'slide' ? `第 ${citation.unitIndex} 页（幻灯片）` : `图片 #${citation.unitIndex}`
  citationNotice.value = `「${citation.documentName} · ${unit} · 片段 #${citation.chunkId}」\n${citation.excerpt}`
}
</script>
