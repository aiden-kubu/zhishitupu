import { api } from './http'
import type { LibraryDto } from './types'

export const libraryApi = {
  list(): Promise<LibraryDto[]> {
    return api('/api/libraries')
  },

  get(id: number): Promise<LibraryDto> {
    return api(`/api/libraries/${id}`)
  },

  create(payload: { name: string; type: string; description?: string }): Promise<LibraryDto> {
    return api('/api/libraries', { method: 'POST', body: payload })
  },

  update(
    id: number,
    payload: { name?: string; type?: string; description?: string; status?: string },
  ): Promise<LibraryDto> {
    return api(`/api/libraries/${id}`, { method: 'PUT', body: payload })
  },

  remove(id: number): Promise<void> {
    return api(`/api/libraries/${id}`, { method: 'DELETE' })
  },

  /** 维护知识库别名（整体替换；AI 自动整理按别名匹配同主题） */
  updateAliases(id: number, aliases: string[]): Promise<LibraryDto> {
    return api(`/api/libraries/${id}/aliases`, { method: 'PUT', body: { aliases } })
  },
}
