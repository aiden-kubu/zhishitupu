import { reactive } from 'vue'
import { chatApi } from '@/services/chatApi'
import { ApiError } from '@/services/http'
import type { ChatMessageDto } from '@/services/types'

interface NodeChatState {
  messages: ChatMessageDto[]
  loading: boolean
  /** 当前绑定的会话 ID；null 表示尚未绑定（打开节点时加载，或首次发送时创建） */
  sessionId: number | null
  /** 「新对话」标记：下一次发送必须新建会话，而不是复用该节点的历史会话 */
  freshChat: boolean
  /** 正在加载该节点的历史会话与消息 */
  hydrating: boolean
  controller: AbortController | null
}

/**
 * AI 问答会话（§7.2、§7.4）：按节点分别保存，切换节点不丢弃旧消息；
 * 「新对话」只清空当前绑定并标记新建，不删除历史会话记录。
 * 会话持久化在后端 chat_sessions/chat_messages，刷新后仍可读取。
 */
const states = new Map<number, NodeChatState>()

function stateFor(nodeId: number): NodeChatState {
  let state = states.get(nodeId)
  if (!state) {
    state = reactive<NodeChatState>({
      messages: [],
      loading: false,
      sessionId: null,
      freshChat: false,
      hydrating: false,
      controller: null,
    })
    states.set(nodeId, state)
  }
  return state
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

export function useAiChat() {
  function getMessages(nodeId: number): ChatMessageDto[] {
    return stateFor(nodeId).messages
  }

  function isLoading(nodeId: number): boolean {
    return stateFor(nodeId).loading
  }

  function isHydrating(nodeId: number): boolean {
    return stateFor(nodeId).hydrating
  }

  /**
   * 打开节点：自动加载该节点最近一次会话与消息（§7.2）。
   * 没有历史会话时不立即创建（避免浏览节点产生空会话），首次发送时才创建。
   */
  async function openNode(nodeId: number): Promise<void> {
    const state = stateFor(nodeId)
    if (state.sessionId || state.hydrating || state.loading) return
    state.hydrating = true
    try {
      const sessions = await chatApi.listSessions(nodeId)
      if (state.sessionId || state.loading) return // 期间已绑定或已开始提问
      if (sessions.length > 0) {
        state.sessionId = sessions[0].id
        state.messages = await chatApi.listMessages(state.sessionId)
      }
    } catch {
      // 历史加载失败不打断使用：保留空对话，发送时仍可创建会话
    } finally {
      state.hydrating = false
    }
  }

  /** 发送提问（§7.4 非流式）：失败/未配置时以助手错误消息呈现 */
  async function send(nodeId: number, content: string, depth: 1 | 2 = 1): Promise<void> {
    const state = stateFor(nodeId)
    const trimmed = content.trim()
    if (!trimmed || state.loading) return

    state.messages.push({ role: 'user', content: trimmed, citations: [] })
    state.loading = true
    const controller = new AbortController()
    state.controller = controller

    try {
      // 第一次发送创建会话；「新对话」后强制新建；其余复用当前节点最近会话
      let sessionId = state.sessionId
      if (!sessionId) {
        if (state.freshChat) {
          const created = await chatApi.createSession(nodeId, controller.signal)
          state.freshChat = false
          sessionId = created.id
        } else {
          const sessions = await chatApi.listSessions(nodeId, controller.signal)
          sessionId =
            sessions.length > 0
              ? sessions[0].id
              : (await chatApi.createSession(nodeId, controller.signal)).id
        }
        state.sessionId = sessionId
      }

      const answer = await chatApi.sendMessage(
        sessionId,
        { content: trimmed, mode: 'knowledge_only', depth },
        controller.signal,
      )
      if (state.sessionId !== sessionId) return // 期间点了新对话/已切换会话，丢弃过期回答
      state.messages.push({
        role: 'assistant',
        content: answer.content,
        citations: answer.citations ?? [],
        insufficientEvidence: answer.insufficientEvidence,
      })
    } catch (error) {
      if ((error as Error)?.name === 'AbortError') {
        if (state.messages.length > 0) {
          state.messages.push({ role: 'assistant', content: '已停止本次回答。', citations: [] })
        }
      } else if (error instanceof ApiError) {
        if (state.messages.length > 0) {
          state.messages.push({
            role: 'assistant',
            content: friendlyChatError(error),
            citations: [],
            error: error.code,
          })
        }
      } else {
        if (state.messages.length > 0) {
          state.messages.push({
            role: 'assistant',
            content: '回答失败，请稍后重试。',
            citations: [],
            error: 'UNKNOWN',
          })
        }
      }
    } finally {
      if (state.controller === controller) state.controller = null
      state.loading = false
    }
  }

  /** 取消前端等待（不保证终止远端计费，§7.4） */
  function stop(nodeId: number): void {
    stateFor(nodeId).controller?.abort()
  }

  /**
   * 新对话：清空当前会话绑定与消息，不删除历史会话（§7.2）。
   * 若有在途请求则中断，其结果因会话绑定已清空而被丢弃。
   */
  function startNew(nodeId: number): void {
    const state = stateFor(nodeId)
    if (state.loading) {
      state.controller?.abort()
    }
    state.sessionId = null
    state.freshChat = true
    state.messages = []
  }

  return { getMessages, isLoading, isHydrating, openNode, send, stop, startNew }
}
