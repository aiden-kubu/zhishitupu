<template>
  <div class="overflow-hidden rounded-2xl border border-gray-200 bg-white dark:border-gray-800 dark:bg-white/[0.03]">
    <div class="overflow-x-auto">
      <table class="w-full min-w-[820px] text-start">
        <thead>
          <tr class="border-b border-gray-100 text-sm text-gray-500 dark:border-gray-800 dark:text-gray-400">
            <th class="px-5 py-3.5 text-start font-medium">任务</th>
            <th class="px-5 py-3.5 text-start font-medium">当前阶段</th>
            <th class="px-5 py-3.5 text-start font-medium">状态</th>
            <th class="px-5 py-3.5 text-start font-medium">进度</th>
            <th class="px-5 py-3.5 text-start font-medium">处理单元</th>
            <th class="px-5 py-3.5 text-start font-medium">错误信息</th>
            <th class="px-5 py-3.5 text-start font-medium">创建时间</th>
            <th class="px-5 py-3.5 text-start font-medium">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr
            v-for="job in jobs"
            :key="job.id"
            class="border-b border-gray-100 text-sm text-gray-700 transition last:border-0 hover:bg-gray-50 dark:border-gray-800 dark:text-gray-300 dark:hover:bg-white/[0.03]"
          >
            <td class="px-5 py-3.5">
              <span class="font-medium text-gray-800 dark:text-white/90">#{{ job.id }}</span>
              <span v-if="job.documentId" class="text-xs text-gray-400"> · 文档 #{{ job.documentId }}</span>
            </td>
            <td class="px-5 py-3.5">{{ STAGE_LABELS[job.stage] ?? job.stage }}</td>
            <td class="px-5 py-3.5">
              <Badge :color="statusColor(job.status)">
                {{ JOB_STATUS_LABELS[job.status] ?? job.status }}
              </Badge>
            </td>
            <td class="px-5 py-3.5">
              <JobProgress :progress="job.progress" />
            </td>
            <td class="px-5 py-3.5">
              {{ job.processedUnits }} / {{ job.totalUnits || '—' }}
              <span v-if="job.retryCount > 0" class="text-xs text-gray-400">（重试 {{ job.retryCount }}）</span>
            </td>
            <td class="max-w-[220px] px-5 py-3.5">
              <span v-if="job.errorMessage" class="line-clamp-2 text-xs text-error-500" :title="job.errorMessage">
                {{ job.errorMessage }}
              </span>
              <span v-else class="text-gray-400">—</span>
            </td>
            <td class="px-5 py-3.5 text-xs">{{ job.createdAt }}</td>
            <td class="px-5 py-3.5">
              <div class="flex gap-2 text-xs">
                <button
                  v-if="job.status === 'AWAITING_REVIEW'"
                  class="font-medium text-brand-500 hover:text-brand-600"
                  @click="emit('go-review')"
                >
                  去审核
                </button>
                <button
                  v-if="job.status === 'FAILED' && !job.extractable"
                  class="font-medium text-brand-500 hover:text-brand-600"
                  @click="emit('retry', job)"
                >
                  重试
                </button>
                <button
                  v-if="job.extractable"
                  class="font-medium text-brand-500 hover:text-brand-600"
                  @click="emit('extract', job)"
                >
                  提取知识
                </button>
                <button
                  v-if="JOB_ACTIVE_STATUSES.includes(job.status)"
                  class="font-medium text-gray-500 hover:text-gray-700"
                  @click="emit('cancel', job)"
                >
                  取消
                </button>
              </div>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>

<script setup lang="ts">
import Badge from '@/components/ui/Badge.vue'
import JobProgress from './JobProgress.vue'
import { JOB_ACTIVE_STATUSES, JOB_STATUS_LABELS } from '@/services/types'
import type { ProcessingJobDto } from '@/services/types'

defineProps<{ jobs: ProcessingJobDto[] }>()

const emit = defineEmits<{
  (e: 'go-review'): void
  (e: 'retry', job: ProcessingJobDto): void
  (e: 'cancel', job: ProcessingJobDto): void
  (e: 'extract', job: ProcessingJobDto): void
}>()

/** §8.4 任务阶段 → 中文（前端负责映射） */
const STAGE_LABELS: Record<string, string> = {
  UPLOADED: '已上传',
  VALIDATING: '安全校验',
  PARSING: '文档解析',
  OCR_RUNNING: 'OCR 识别',
  CHUNKING: '分段',
  AI_EXTRACTING: 'AI 抽取',
  AWAITING_REVIEW: '等待人工审核',
  IMPORTING: '入库',
  COMPLETED: '完成',
  FAILED: '失败',
  CANCELLED: '已取消',
}

function statusColor(status: string): 'success' | 'error' | 'warning' | 'info' | 'light' {
  if (status === 'COMPLETED') return 'success'
  if (status === 'FAILED') return 'error'
  if (status === 'CANCELLED') return 'light'
  if (status === 'AWAITING_REVIEW') return 'warning'
  return 'info'
}
</script>
