import { onUnmounted, ref } from 'vue'
import { ingestionApi } from '@/services/ingestionApi'
import { ApiError } from '@/services/http'
import { JOB_ACTIVE_STATUSES } from '@/services/types'
import type { ProcessingJobDto } from '@/services/types'

/**
 * 处理任务轮询（§12.4）：每 2 秒轮询活动任务；没有活动任务时停止轮询。
 */
export function useIngestionJobs() {
  const jobs = ref<ProcessingJobDto[]>([])
  const loading = ref(false)
  const error = ref<string | null>(null)
  let timer: ReturnType<typeof setTimeout> | null = null
  let stopped = true

  async function refresh(silent = false) {
    if (!silent) loading.value = true
    try {
      const page = await ingestionApi.listJobs()
      jobs.value = page.items
      error.value = null
      const hasActive = page.items.some((job) => JOB_ACTIVE_STATUSES.includes(job.status))
      if (hasActive) {
        schedule()
      }
    } catch (err) {
      error.value = err instanceof ApiError ? err.message : '任务列表加载失败'
    } finally {
      if (!silent) loading.value = false
    }
  }

  function schedule() {
    if (timer || stopped) return
    timer = setTimeout(() => {
      timer = null
      void refresh(true)
    }, 2000)
  }

  function start() {
    stopped = false
    void refresh()
  }

  function stop() {
    stopped = true
    if (timer) {
      clearTimeout(timer)
      timer = null
    }
  }

  onUnmounted(stop)

  return { jobs, loading, error, start, stop, refresh }
}
