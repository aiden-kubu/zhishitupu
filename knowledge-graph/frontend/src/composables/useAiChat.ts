import { reactive } from 'vue'
import { chatApi } from '@/services/chatApi'
import { ApiError } from '@/services/http'
import type { ChatMessageDto } from '@/services/types'

/**
 * 节点 AI 知识助手（无历史临时模式，2026-09-14 用户决策）：
 * 状态只属于当前 AiChatPanel 实例——打开面板即空对话，关闭/切换节点/刷新页面即丢弃；
 * 同一次打开期间可连续追问（临时历史随请求传给模型）；
 * 不使用 localStorage/sessionStorage/IndexedDB，也不写入任何会话表。
 */
const MAX_HISTORY_MESSAGES = 10
/** 本地生成的提示文案，不得作为模型上下文 */
const LOCAL_CANCEL_NOTICE = '已停止本次回答。'

interface ChatState {
  messages: ChatMessageDto[]
  loading: boolean
}

export function useAiChat() {
  const state = reactive<ChatState>({ messages: [], loading: false })
  let controller: AbortController | null = null
  let boundNodeId: number | null = null

  function getMessages(): ChatMessageDto[] {
    return state.messages
  }

  function isLoading(): boolean {
    return state.loading
  }

  /** 中断在途请求；silent=true 时其结果被丢弃（切换节点/关闭面板/新对话）。 */
  function abortInFlight(silent: boolean) {
    const current = controller
    if (silent) controller = null
    current?.abort()
  }

  /** 当前绑定节点（面板卸载/未选节点时为空）。 */
  function currentNodeId(): number | null {
    return boundNodeId
  }

  /** 切换知识节点：立即清空旧节点对话并中断旧请求。 */
  function switchNode(nodeId: number): void {
    if (boundNodeId === nodeId) return
    boundNodeId = nodeId
    abortInFlight(true)
    state.messages = []
    state.loading = false
  }

  /** 面板关闭/卸载：中止所有在途请求并清空内存。 */
  function dispose(): void {
    boundNodeId = null
    abortInFlight(true)
    state.messages = []
    state.loading = false
  }

  /** 新对话：清空当前临时对话（不删除任何服务端数据，因为本就不保存会话）。 */
  function startNew(): void {
    abortInFlight(true)
    state.messages = []
    state.loading = false
  }

  /** 取消本次等待：保留“已停止”提示，便于用户知道是自己停的。 */
  function stop(): void {
    abortInFlight(false)
  }

  /** 后端业务错误码 → 用户可读中文提示 */
  function friendlyChatError(error: ApiError): string {
    switch (error.code) {
      case 'LLM_NOT_CONFIGURED':
        return '尚未配置可用的默认模型：请在「系统设置 → 模型档案」添加模型、启用并设为默认。'
      case 'LLM_AUTH_FAILED':
        return '模型服务认证失败：请检查默认模型的 API Key 是否有效。'
      case 'LLM_UNREACHABLE':
        return '无法连接模型服务：请检查网络与 API 地址，或稍后重试。'
      case 'LLM_BAD_RESPONSE':
        return '模型返回了无法识别的结果：请重试，或在系统设置中更换默认模型。'
      default:
        return error.message || '回答失败，请稍后重试。'
    }
  }

  /** 临时历史：最近最多 10 条成功的 user/assistant，排除本地错误提示与“已停止”提示。 */
  function buildHistory(): { role: 'user' | 'assistant'; content: string }[] {
    return state.messages
      .filter(
        (message) =>
          (message.role === 'user' || message.role === 'assistant') &&
          !message.error &&
          message.content.trim() !== LOCAL_CANCEL_NOTICE,
      )
      .slice(-MAX_HISTORY_MESSAGES)
      .map((message) => ({ role: message.role as 'user' | 'assistant', content: message.content }))
  }

  async function send(content: string, depth: 1 | 2 = 1): Promise<void> {
    const trimmed = content.trim()
    if (!trimmed || state.loading || boundNodeId === null) return

    // 当前问题入列之前先取临时历史，避免把当前问题重复带入
    const history = buildHistory()
    state.messages.push({ role: 'user', content: trimmed, citations: [] })
    state.loading = true
    const current = new AbortController()
    controller = current

    try {
      const answer = await chatApi.ask(boundNodeId, { content: trimmed, depth, history }, current.signal)
      if (controller !== current) return // 期间已切换节点/关闭面板/新对话，丢弃过期回答
      state.messages.push({
        role: 'assistant',
        content: answer.content,
        citations: answer.citations ?? [],
        insufficientEvidence: answer.insufficientEvidence,
      })
    } catch (error) {
      if (controller !== current) return // 已被切换/关闭取代，不写入任何提示
      if ((error as Error)?.name === 'AbortError') {
        state.messages.push({ role: 'assistant', content: LOCAL_CANCEL_NOTICE, citations: [] })
      } else if (error instanceof ApiError) {
        state.messages.push({
          role: 'assistant',
          content: friendlyChatError(error),
          citations: [],
          error: error.code,
        })
      } else {
        state.messages.push({
          role: 'assistant',
          content: '回答失败，请稍后重试。',
          citations: [],
          error: 'UNKNOWN',
        })
      }
    } finally {
      if (controller === current) {
        controller = null
        state.loading = false
      }
    }
  }

  return { getMessages, isLoading, currentNodeId, send, stop, startNew, switchNode, dispose }
}
