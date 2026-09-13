<template>
  <AdminLayout>
    <PageBreadcrumb page-title="处理中心" />

    <div class="flex flex-wrap items-center justify-between gap-3">
      <p class="text-sm text-gray-500 dark:text-gray-400">
        上传记录与解析 / OCR / AI 抽取任务进度（每 2 秒自动刷新活动任务）
      </p>
      <!-- §4.2：导入资料只在顶部全局按钮，页面内不再放第二个入口 -->
    </div>

    <div v-if="loading" class="mt-5 rounded-2xl border border-gray-200 p-10 text-center dark:border-gray-800">
      <p class="text-sm text-gray-500 dark:text-gray-400">任务加载中…</p>
    </div>

    <div v-else-if="error" class="mt-5 space-y-3">
      <Alert variant="error" title="加载失败" :message="error" />
      <Button size="sm" variant="outline" @click="start">重试</Button>
    </div>

    <template v-else-if="jobs.length > 0">
      <ProcessingTable
        class="mt-5"
        :jobs="jobs"
        @go-review="router.push('/review')"
        @retry="retryJob"
        @cancel="cancelJob"
        @extract="extractJob"
      />
    </template>

    <!-- 空态（§5.4）：只提示使用顶部全局「导入资料」按钮 -->
    <div
      v-else
      class="mt-5 flex flex-col items-center justify-center rounded-2xl border border-gray-200 px-6 py-14 text-center dark:border-gray-800"
    >
      <span class="flex h-12 w-12 items-center justify-center rounded-2xl bg-brand-50 text-brand-500 dark:bg-brand-500/15 dark:text-brand-400">
        <TaskIcon class="h-6 w-6" />
      </span>
      <p class="mt-4 text-sm font-medium text-gray-700 dark:text-gray-300">暂无处理任务</p>
      <p class="mt-1 max-w-md text-xs leading-5 text-gray-400">
        点击顶栏右上角的「＋ 导入资料」按钮上传 PDF / PPT / 书本照片 ZIP，
        解析、OCR、AI 抽取任务的进度会在这里展示；抽取完成后会进入审核中心人工确认。
      </p>
    </div>
  </AdminLayout>
</template>

<script setup lang="ts">
import { onMounted } from 'vue'
import { useRouter } from 'vue-router'
import AdminLayout from '@/components/layout/AdminLayout.vue'
import PageBreadcrumb from '@/components/common/PageBreadcrumb.vue'
import Alert from '@/components/ui/Alert.vue'
import Button from '@/components/ui/Button.vue'
import { TaskIcon } from '@/icons'
import ProcessingTable from '@/features/ingestion/ProcessingTable.vue'
import { useIngestionJobs } from '@/composables/useIngestionJobs'
import { ingestionApi } from '@/services/ingestionApi'
import { ApiError } from '@/services/http'
import type { ProcessingJobDto } from '@/services/types'

const router = useRouter()
const { jobs, loading, error, start, refresh } = useIngestionJobs()

async function retryJob(job: ProcessingJobDto) {
  try {
    await ingestionApi.retry(job.id)
    await refresh(true)
  } catch (err) {
    error.value = err instanceof ApiError ? err.message : '重试失败'
  }
}

async function cancelJob(job: ProcessingJobDto) {
  try {
    await ingestionApi.cancel(job.id)
    await refresh(true)
  } catch (err) {
    error.value = err instanceof ApiError ? err.message : '取消失败'
  }
}

/** 提取知识：对已解析但无候选结果的旧任务发起 AI 抽取（不重新解析文件） */
async function extractJob(job: ProcessingJobDto) {
  try {
    await ingestionApi.extract(job.id)
    await refresh(true)
  } catch (err) {
    error.value = err instanceof ApiError ? err.message : '发起抽取失败'
  }
}

onMounted(start)
</script>
