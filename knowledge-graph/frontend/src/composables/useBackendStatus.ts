import { ref } from 'vue'
import { api } from '@/services/http'

export type BackendStatus = 'checking' | 'up' | 'down'

/** 后端健康状态（真实 /api/health 探测，30 秒刷新；模块级单例，侧栏状态卡使用） */
const status = ref<BackendStatus>('checking')
let started = false

export function useBackendStatus() {
  async function check() {
    try {
      const data = await api<{ status?: string }>('/api/health')
      status.value = data?.status === 'UP' ? 'up' : 'down'
    } catch {
      status.value = 'down'
    }
  }

  function start() {
    if (started) return
    started = true
    void check()
    window.setInterval(() => void check(), 30_000)
  }

  return { status, start }
}
