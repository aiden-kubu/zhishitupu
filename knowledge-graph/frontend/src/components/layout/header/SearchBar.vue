<script setup lang="ts">
import { ref, onMounted, onUnmounted, watch, nextTick, computed } from 'vue'
import { useRouter } from 'vue-router'
import { graphApi } from '@/services/graphApi'
import { ApiError } from '@/services/http'
import { NODE_TYPE_LABELS } from '@/services/types'
import type { SuggestionDto } from '@/services/types'
import PlusIcon from '@/icons/PlusIcon.vue'

/**
 * §4.3 顶部全局搜索（唯一搜索入口）+ §6.2 搜索联想：
 * ≥1 字符 300ms 防抖；按完全命中/前缀/别名/包含排序由后端负责。
 * 本地联想为空时展示「AI 生成并收录」操作：只有用户按 Enter 或点击才调用
 * POST /api/search/ai-expand，输入/防抖/联想过程绝不触发大模型（§12-§17 任务约定）。
 */
const router = useRouter()

const query = ref('')
const suggestions = ref<SuggestionDto[]>([])
const loading = ref(false)
const open = ref(false)
const activeIndex = ref(-1)
const errorMessage = ref<string | null>(null)
const containerRef = ref<HTMLElement | null>(null)

/** AI 生成并收录：加载/取消/错误状态（aiKeyword 固定触发时的关键词，不受继续输入影响） */
const aiLoading = ref(false)
const aiKeyword = ref('')
const aiError = ref<string | null>(null)
const aiErrorDetail = ref<string | null>(null)

const AI_ERROR_LABELS: Record<string, string> = {
  LLM_NOT_CONFIGURED: '尚未配置默认模型',
  LLM_AUTH_FAILED: '模型认证失败',
  LLM_UNREACHABLE: '模型服务暂时不可用',
  LLM_BAD_RESPONSE: '模型返回内容无法形成知识节点',
  NETWORK_ERROR: '网络错误，无法连接后端服务',
}

let debounceTimer: ReturnType<typeof setTimeout> | null = null
let abortController: AbortController | null = null
let aiAbortController: AbortController | null = null

const aiOptionVisible = computed(
  () => query.value.trim().length > 0 && suggestions.value.length === 0 && !loading.value && !errorMessage.value,
)

const focusSearchInput = () => {
  const inputElement = document.getElementById('search-input')
  inputElement?.focus()
}

const handleKeydown = (event: KeyboardEvent) => {
  if ((event.metaKey || event.ctrlKey) && event.key === 'k') {
    event.preventDefault()
    focusSearchInput()
  }
}

onMounted(() => {
  window.addEventListener('keydown', handleKeydown)
  document.addEventListener('click', handleClickOutside)
})

onUnmounted(() => {
  window.removeEventListener('keydown', handleKeydown)
  document.removeEventListener('click', handleClickOutside)
  abortController?.abort()
  aiAbortController?.abort()
})

const handleClickOutside = (event: MouseEvent) => {
  if (containerRef.value && !containerRef.value.contains(event.target as Node)) {
    open.value = false
  }
}

watch(query, (value) => {
  aiError.value = null
  aiErrorDetail.value = null
  if (debounceTimer) clearTimeout(debounceTimer)
  const keyword = value.trim()
  if (!keyword) {
    suggestions.value = []
    open.value = false
    loading.value = false
    return
  }
  debounceTimer = setTimeout(() => void fetchSuggestions(keyword), 300)
})

async function fetchSuggestions(keyword: string) {
  abortController?.abort()
  abortController = new AbortController()
  loading.value = true
  errorMessage.value = null
  try {
    suggestions.value = await graphApi.suggestions(keyword, 8, abortController.signal)
    activeIndex.value = suggestions.value.length > 0 ? 0 : -1
    open.value = true
  } catch (error) {
    if ((error as Error)?.name === 'AbortError') return
    suggestions.value = []
    open.value = true
    errorMessage.value =
      error instanceof ApiError ? error.message : '搜索联想失败，请确认后端服务已启动'
  } finally {
    loading.value = false
  }
}

function select(suggestion: SuggestionDto) {
  open.value = false
  query.value = ''
  suggestions.value = []
  // §6.2.4 更新 URL，图谱工作台监听 query 自动聚焦
  void router.push({ path: '/', query: { node: String(suggestion.id), depth: '1', mode: 'local' } })
}

/** 触发「AI 生成并收录」：仅由点击/Enter 显式调用；等待期间禁止重复提交，可取消前端等待。 */
async function triggerAiExpand() {
  const keyword = query.value.trim()
  if (!keyword || aiLoading.value) return
  abortController?.abort() // 停掉进行中的联想请求
  aiError.value = null
  aiErrorDetail.value = null
  aiKeyword.value = keyword
  aiLoading.value = true
  activeIndex.value = -1
  open.value = true
  aiAbortController = new AbortController()
  try {
    const result = await graphApi.aiExpand(keyword, null, aiAbortController.signal)
    // 成功：关闭下拉框，用返回的 node.id 跳转聚焦（2D/3D 图谱监听 route.query 自动重载）
    open.value = false
    query.value = ''
    suggestions.value = []
    void router.push({ path: '/', query: { node: String(result.node.id), depth: '1', mode: 'local' } })
  } catch (error) {
    if ((error as Error)?.name === 'AbortError') return // 用户取消等待：保留关键词，后端仍会完成收录
    if (query.value.trim() !== keyword) return // 等待期间用户已转向其他搜索，错误不再展示
    suggestions.value = []
    open.value = true
    if (error instanceof ApiError) {
      const label = AI_ERROR_LABELS[error.code]
      aiError.value = label ?? error.message
      aiErrorDetail.value = label && error.message && error.message !== label ? error.message : null
    } else {
      aiError.value = 'AI 生成失败，请稍后重试'
    }
  } finally {
    aiLoading.value = false
  }
}

/** 取消前端等待（后端生成不中断；下次搜索同一名称将直接命中本地库） */
function cancelAiWait() {
  aiAbortController?.abort()
}

function onInputKeydown(event: KeyboardEvent) {
  if (event.key === 'Escape') {
    if (aiLoading.value) cancelAiWait()
    open.value = false
    return
  }
  if (!open.value) return
  if (event.key === 'Enter') {
    if (suggestions.value.length > 0) {
      event.preventDefault()
      const suggestion = suggestions.value[activeIndex.value]
      if (suggestion) select(suggestion)
    } else if (aiOptionVisible.value) {
      event.preventDefault()
      void triggerAiExpand()
    }
    return
  }
  if (suggestions.value.length === 0) return
  if (event.key === 'ArrowDown') {
    event.preventDefault()
    activeIndex.value = (activeIndex.value + 1) % suggestions.value.length
    void nextTick(scrollActiveIntoView)
  } else if (event.key === 'ArrowUp') {
    event.preventDefault()
    activeIndex.value = (activeIndex.value - 1 + suggestions.value.length) % suggestions.value.length
    void nextTick(scrollActiveIntoView)
  }
}

const scrollActiveIntoView = () => {
  const listElement = document.getElementById('kg-suggestion-list')
  listElement
    ?.querySelectorAll('[data-suggestion-item]')
    [activeIndex.value]?.scrollIntoView({ block: 'nearest' })
}
</script>

<template>
  <div ref="containerRef" class="relative">
    <form @submit.prevent>
      <div class="relative">
        <label for="search-input" class="sr-only">搜索知识点、课程或资料</label>
        <span class="absolute -translate-y-1/2 start-4 top-1/2 pointer-events-none">
          <svg
            class="fill-gray-500 dark:fill-gray-400"
            width="20"
            height="20"
            viewBox="0 0 20 20"
            fill="none"
            xmlns="http://www.w3.org/2000/svg"
            aria-hidden="true"
          >
            <path
              fill-rule="evenodd"
              clip-rule="evenodd"
              d="M3.04175 9.37363C3.04175 5.87693 5.87711 3.04199 9.37508 3.04199C12.8731 3.04199 15.7084 5.87693 15.7084 9.37363C15.7084 12.8703 12.8731 15.7053 9.37508 15.7053C5.87711 15.7053 3.04175 12.8703 3.04175 9.37363ZM9.37508 1.54199C5.04902 1.54199 1.54175 5.04817 1.54175 9.37363C1.54175 13.6991 5.04902 17.2053 9.37508 17.2053C11.2674 17.2053 13.003 16.5344 14.357 15.4176L17.177 18.238C17.4699 18.5309 17.9448 18.5309 18.2377 18.238C18.5306 17.9451 18.5306 17.4703 18.2377 17.1774L15.418 14.3573C16.5365 13.0033 17.2084 11.2669 17.2084 9.37363C17.2084 5.04817 13.7011 1.54199 9.37508 1.54199Z"
            />
          </svg>
        </span>
        <input
          id="search-input"
          type="text"
          v-model="query"
          autocomplete="off"
          placeholder="搜索知识点、课程或资料…"
          class="dark:bg-dark-900 h-10 w-full rounded-lg border border-gray-200 bg-transparent py-2.5 ps-12 pe-14 text-sm text-gray-800 shadow-theme-xs placeholder:text-gray-400 focus:border-brand-300 focus:outline-hidden focus:ring-3 focus:ring-brand-500/10 dark:border-gray-800 dark:bg-white/3 dark:text-white/90 dark:placeholder:text-white/30 dark:focus:border-brand-800 xl:w-[360px]"
          @keydown="onInputKeydown"
          @focus="() => { if (suggestions.length > 0 || aiError) open = true }"
        />

        <button
          type="button"
          tabindex="-1"
          @click="focusSearchInput"
          class="absolute end-2.5 top-1/2 inline-flex -translate-y-1/2 items-center gap-0.5 rounded-lg border border-gray-200 bg-gray-50 px-[7px] py-[4.5px] text-xs -tracking-[0.2px] text-gray-500 dark:border-gray-800 dark:bg-white/3 dark:text-gray-400"
          aria-label="聚焦搜索框 (Ctrl+K)"
        >
          <span aria-hidden="true"> Ctrl </span>
          <span aria-hidden="true"> K </span>
        </button>
      </div>
    </form>

    <!-- 联想下拉 -->
    <div
      v-if="open"
      class="absolute inset-x-0 top-full z-99999 mt-2 overflow-hidden rounded-2xl border border-gray-200 bg-white shadow-theme-lg dark:border-gray-800 dark:bg-gray-900"
    >
      <div v-if="loading" class="px-4 py-3 text-sm text-gray-500 dark:text-gray-400">搜索中…</div>

      <!-- AI 生成中：可继续等待或取消前端等待（后端仍会完成收录） -->
      <div v-else-if="aiLoading" class="px-4 py-3">
        <p class="flex items-center gap-2 text-sm text-gray-700 dark:text-gray-200">
          <span
            class="h-3.5 w-3.5 shrink-0 animate-spin rounded-full border-2 border-brand-500 border-t-transparent"
            aria-hidden="true"
          ></span>
          正在使用 AI 构建「{{ aiKeyword }}」知识节点及关系……
        </p>
        <div class="mt-2 flex items-center justify-between gap-3">
          <p class="text-xs text-gray-400 dark:text-gray-500">生成约需十几秒</p>
          <button
            type="button"
            class="text-xs font-medium text-gray-500 hover:text-gray-700 dark:text-gray-400 dark:hover:text-gray-200"
            @click="cancelAiWait"
          >
            取消等待
          </button>
        </div>
      </div>

      <template v-else-if="errorMessage">
        <p class="px-4 py-3 text-sm text-error-500">{{ errorMessage }}</p>
      </template>

      <!-- 本地未命中：AI 创建操作（仅显式触发） -->
      <template v-else-if="suggestions.length === 0">
        <button
          v-if="!aiError"
          type="button"
          data-suggestion-item
          class="flex w-full items-start gap-3 px-4 py-3 text-start hover:bg-gray-50 dark:hover:bg-white/[0.03]"
          @click="triggerAiExpand"
        >
          <span
            class="mt-0.5 flex h-6 w-6 shrink-0 items-center justify-center rounded-full bg-brand-50 text-brand-500 dark:bg-brand-500/15 dark:text-brand-400"
            aria-hidden="true"
          >
            <PlusIcon />
          </span>
          <span class="min-w-0 flex-1">
            <span class="block truncate text-sm font-medium text-gray-800 dark:text-white/90">
              本地未找到，使用 AI 生成并收录「{{ query.trim() }}」
            </span>
            <span class="mt-0.5 block text-xs text-gray-400 dark:text-gray-500">
              点击或按 Enter 触发；仅在你确认后才调用大模型
            </span>
          </span>
        </button>

        <div v-else class="px-4 py-3">
          <p class="text-sm text-error-500">{{ aiError }}</p>
          <p v-if="aiErrorDetail" class="mt-1 text-xs text-gray-500 dark:text-gray-400">
            {{ aiErrorDetail }}
          </p>
          <p class="mt-2 text-xs text-gray-400 dark:text-gray-500">
            已保留关键词「{{ aiKeyword }}」，可修改后重试。
          </p>
          <button
            type="button"
            class="mt-2 text-xs font-medium text-brand-500 hover:text-brand-600"
            @click="triggerAiExpand"
          >
            重试
          </button>
        </div>
      </template>

      <ul v-else id="kg-suggestion-list" class="max-h-[360px] overflow-y-auto py-1.5">
        <li v-for="(suggestion, index) in suggestions" :key="suggestion.id">
          <button
            type="button"
            data-suggestion-item
            class="flex w-full items-center gap-3 px-4 py-2.5 text-start"
            :class="
              index === activeIndex
                ? 'bg-gray-100 dark:bg-white/[0.05]'
                : 'hover:bg-gray-50 dark:hover:bg-white/[0.03]'
            "
            @mouseenter="activeIndex = index"
            @click="select(suggestion)"
          >
            <span class="min-w-0 flex-1">
              <span class="block truncate text-sm font-medium text-gray-800 dark:text-white/90">
                {{ suggestion.name }}
                <span
                  v-if="suggestion.nameEn"
                  class="text-xs font-normal text-gray-400 dark:text-gray-500"
                >
                  {{ suggestion.nameEn }}
                </span>
              </span>
              <span
                v-if="suggestion.aliases && suggestion.aliases.length > 0"
                class="block truncate text-xs text-gray-500 dark:text-gray-400"
              >
                别名：{{ suggestion.aliases.join('、') }}
              </span>
            </span>
            <span
              class="shrink-0 rounded-full bg-brand-50 px-2 py-0.5 text-theme-xs text-brand-500 dark:bg-brand-500/15 dark:text-brand-400"
            >
              {{ NODE_TYPE_LABELS[suggestion.type] ?? suggestion.type }}
            </span>
            <span class="shrink-0 text-xs text-gray-400 dark:text-gray-500">
              {{ suggestion.sourceCount }} 关联
            </span>
          </button>
        </li>
      </ul>
    </div>
  </div>
</template>
