import { api } from './http'
import type { ChatMessageDto } from './types'

export interface ChatSessionDto {
  id: number
  nodeId: number
  title: string
  createdAt: string
  updatedAt: string
}

/** 问答载荷（§12.6 提问请求） */
export interface ChatSendMessagePayload {
  content: string
  mode: 'knowledge_only'
  depth: number
}

export const chatApi = {
  listSessions(nodeId: number, signal?: AbortSignal): Promise<ChatSessionDto[]> {
    return api(`/api/nodes/${nodeId}/chat/sessions`, { signal })
  },

  createSession(nodeId: number, signal?: AbortSignal): Promise<ChatSessionDto> {
    return api(`/api/nodes/${nodeId}/chat/sessions`, { method: 'POST', signal })
  },

  listMessages(sessionId: number, signal?: AbortSignal): Promise<ChatMessageDto[]> {
    return api(`/api/chat/sessions/${sessionId}/messages`, { signal })
  },

  /** 发送提问：非流式，返回完整回答 + 引用 + 证据不足标记（§12.6） */
  sendMessage(
    sessionId: number,
    payload: ChatSendMessagePayload,
    signal?: AbortSignal,
  ): Promise<ChatMessageDto> {
    return api(`/api/chat/sessions/${sessionId}/messages`, { method: 'POST', body: payload, signal })
  },

  deleteSession(sessionId: number, signal?: AbortSignal): Promise<void> {
    return api(`/api/chat/sessions/${sessionId}`, { method: 'DELETE', signal })
  },
}
