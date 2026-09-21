import { api } from './http'
import type {
  LlmProfileDto,
  LlmProfileSavePayload,
  SettingsSaveRequest,
  SystemSettingsDto,
} from './types'

export const settingsApi = {
  /** 掩码后的系统配置视图 */
  get(): Promise<SystemSettingsDto> {
    return api('/api/settings')
  },

  update(payload: SettingsSaveRequest): Promise<SystemSettingsDto> {
    return api('/api/settings', { method: 'PUT', body: payload })
  },

  /** 测试 LLM 连接；未配置时后端返回 503 LLM_NOT_CONFIGURED（旧单模型接口，保留兼容） */
  testLlm(): Promise<{ ok: boolean; message: string; model?: string; latencyMs?: number }> {
    return api('/api/settings/llm/test', { method: 'POST' })
  },

  // ---------- 模型档案（多模型） ----------

  listLlmProfiles(): Promise<LlmProfileDto[]> {
    return api('/api/settings/llm-profiles')
  },

  createLlmProfile(payload: LlmProfileSavePayload): Promise<LlmProfileDto> {
    return api('/api/settings/llm-profiles', { method: 'POST', body: payload })
  },

  updateLlmProfile(id: number, payload: LlmProfileSavePayload): Promise<LlmProfileDto> {
    return api(`/api/settings/llm-profiles/${id}`, { method: 'PUT', body: payload })
  },

  deleteLlmProfile(id: number): Promise<void> {
    return api(`/api/settings/llm-profiles/${id}`, { method: 'DELETE' })
  },

  setDefaultLlmProfile(id: number): Promise<LlmProfileDto> {
    return api(`/api/settings/llm-profiles/${id}/default`, { method: 'POST' })
  },

  /** 按档案做连通性测试；未配置 Key 返回 503 */
  testLlmProfile(
    id: number,
  ): Promise<{ ok: boolean; message: string; model?: string; latencyMs?: number }> {
    return api(`/api/settings/llm-profiles/${id}/test`, { method: 'POST' })
  },
}
