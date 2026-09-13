<template>
  <AdminLayout>
    <PageBreadcrumb page-title="处理中心" description="跟踪资料从识别到入库的进度，处理中的任务会自动更新。" />

    <div v-if="!loading && !error && jobs.length" class="grid grid-cols-2 gap-3 lg:grid-cols-4">
      <button v-for="item in filters" :key="item.value" type="button" :aria-pressed="filter === item.value" @click="filter = item.value"
        class="rounded-xl border p-4 text-start transition"
        :class="filter === item.value ? 'border-brand-300 bg-brand-50 dark:border-brand-500/50 dark:bg-brand-500/10' : 'border-gray-200 bg-white hover:border-brand-300 dark:border-gray-800 dark:bg-white/[0.03]'">
        <span class="text-xs text-gray-500 dark:text-gray-400">{{ item.label }}</span>
        <span class="mt-1 block text-2xl font-semibold tabular-nums text-gray-800 dark:text-white/90">{{ item.count }}</span>
      </button>
    </div>
    <div v-if="loading" class="mt-5 rounded-2xl border border-gray-200 p-10 text-center dark:border-gray-800">
      <p class="text-sm text-gray-500 dark:text-gray-400">任务加载中…</p>
    </div>

    <div v-else-if="error" class="mt-5 space-y-3">
      <Alert variant="error" title="加载失败" :message="error" />
      <Button size="sm" variant="outline" @click="start">重试</Button>
    </div>

    <template v-else-if="jobs.length > 0">
      <p v-if="!visibleJobs.length" class="mt-5 rounded-xl border border-gray-200 p-8 text-center text-sm text-gray-500 dark:border-gray-800 dark:text-gray-400">此状态下暂无任务，选择「全部任务」查看上传记录。</p>
      <ProcessingTable
        class="mt-5"
        :jobs="visibleJobs"
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
        解析、OCR、AI 抽取任务的进度会在这里展示；抽取完成后由 AI 复审并自动入库。
      </p>
    </div>
  </AdminLayout>
</template>

<script setup lang="ts">
import { computed, ref, onMounted } from 'vue'
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
import { JOB_ACTIVE_STATUSES } from '@/services/types'
import type { ProcessingJobDto } from '@/services/types'

const router = useRouter()
const { jobs, loading, error, start, refresh } = useIngestionJobs()

const filter = ref('all')
const matches = (job: ProcessingJobDto, value: string) => value === 'all' || (value === 'active' ? JOB_ACTIVE_STATUSES.includes(job.status) : job.status === value)
const visibleJobs = computed(() => jobs.value.filter(job => matches(job, filter.value)))
const filters = computed(() => [
  { value: 'all', label: '全部任务' }, { value: 'active', label: '正在处理' },
  { value: 'COMPLETED', label: '已入库' }, { value: 'FAILED', label: '需要处理' },
].map(item => ({ ...item, count: jobs.value.filter(job => matches(job, item.value)).length })))

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
