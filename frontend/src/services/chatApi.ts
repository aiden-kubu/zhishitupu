import { api } from './http'
import type { ChatMessageDto } from './types'

/** 临时历史单条（仅允许 user / assistant，后端拒绝 system） */
export interface ChatHistoryItem {
  role: 'user' | 'assistant'
  content: string
}

export interface ChatAskPayload {
  content: string
  depth: 1 | 2
  history: ChatHistoryItem[]
}

/**
 * 节点 AI 知识助手（无历史临时模式，2026-09-14 用户决策）：
 * 只有一个无状态问答接口，应用不保存任何会话；旧 chat/sessions 系列接口已移除。
 */
export const chatApi = {
  ask(nodeId: number, payload: ChatAskPayload, signal?: AbortSignal): Promise<ChatMessageDto> {
    return api(`/api/nodes/${nodeId}/chat`, { method: 'POST', body: payload, signal })
  },
}
