<template>
  <div class="space-y-3">
    <article v-for="job in jobs" :key="job.id" class="rounded-2xl border border-gray-200 bg-white p-5 dark:border-gray-800 dark:bg-white/[0.03]">
      <div class="flex flex-wrap items-start justify-between gap-3">
        <div class="min-w-0 flex-1">
          <h2 class="text-sm font-semibold break-all text-gray-800 dark:text-white/90">{{ job.documentName || ('文档 #' + job.documentId) }}</h2>
          <p class="mt-1 text-xs text-gray-500 dark:text-gray-400">任务 #{{ job.id }} <span class="mx-1">·</span> {{ formatDate(job.createdAt) }} 上传</p>
        </div>
        <Badge :color="statusColor(job.status)">{{ JOB_STATUS_LABELS[job.status] ?? job.status }}</Badge>
      </div>
      <div class="mt-5 grid gap-5 md:grid-cols-[minmax(0,1fr)_auto] md:items-end">
        <div>
          <div class="mb-2 flex flex-wrap items-center justify-between gap-2 text-xs text-gray-500 dark:text-gray-400">
            <span>{{ STAGE_LABELS[job.stage] ?? job.stage }}</span>
            <span class="tabular-nums">已处理 {{ job.processedUnits }} / {{ job.totalUnits || '—' }} {{ unitLabel(job) }}<span v-if="job.retryCount > 0"> · 重试 {{ job.retryCount }} 次</span></span>
          </div>
          <JobProgress :progress="job.progress" class="w-full [&>div]:!w-full" />
          <p v-if="job.status === 'AI_EXTRACTING'" class="mt-2 text-xs leading-5 text-gray-500 dark:text-gray-400">
            {{ job.stage === 'AI_ORGANIZING' ? '正在归纳主题、命名与分库…' : '正在识别第 ' + Math.min(job.processedUnits + 1, job.totalUnits) + ' / ' + job.totalUnits + ' 批，模型返回后更新进度。' }}
          </p>
          <p v-else-if="job.status === 'AI_REVIEWING'" class="mt-2 text-xs text-gray-500 dark:text-gray-400">正在逐项核对原文证据，通过后自动入库，无需人工确认。</p>
          <p v-else-if="job.status === 'AWAITING_REVIEW'" class="mt-2 text-xs text-gray-500 dark:text-gray-400">识别已完成，即将由 AI 复审并自动入库。</p>
          <p v-else-if="['FAILED', 'CANCELLED'].includes(job.status) && (job.stagedBatches ?? 0) > 0" class="mt-2 text-xs leading-5 text-gray-500 dark:text-gray-400">
            已保留 {{ job.stagedBatches }} 批抽取结果，点「重试任务」将从断点继续，这些批次不会再调用模型。
          </p>
        </div>
        <div class="flex flex-wrap gap-2">
          <Button v-if="job.status === 'AWAITING_REVIEW'" size="sm" @click="emit('retry', job)">开始 AI 复审</Button>
          <Button v-if="job.candidateCount" size="sm" variant="outline" @click="emit('go-review')">查看复审结果</Button>
          <Button v-if="['FAILED', 'CANCELLED'].includes(job.status) && !job.extractable" size="sm" variant="outline" @click="emit('retry', job)">重试任务</Button>
          <Button v-if="job.extractable" size="sm" variant="outline" @click="emit('extract', job)">重新识别知识</Button>
          <Button v-if="JOB_ACTIVE_STATUSES.includes(job.status)" size="sm" variant="outline" @click="emit('cancel', job)">取消任务</Button>
        </div>
      </div>
      <div v-if="job.errorMessage" class="mt-4 rounded-xl bg-error-50 p-3 text-xs leading-5 break-words text-error-600 dark:bg-error-500/10 dark:text-error-400" role="alert">{{ job.errorMessage }}</div>
    </article>
  </div>
</template>

<script setup lang="ts">
import Button from '@/components/ui/Button.vue'
import Badge from '@/components/ui/Badge.vue'
import JobProgress from './JobProgress.vue'
import { JOB_ACTIVE_STATUSES, JOB_STATUS_LABELS } from '@/services/types'
import type { ProcessingJobDto } from '@/services/types'

defineProps<{ jobs: ProcessingJobDto[] }>()
function formatDate(value: string) {
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? value : new Intl.DateTimeFormat('zh-CN', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', hour12: false }).format(date)
}

/** 抽取按批计数、AI 复审按候选条数计数、解析按单元计数 */
function unitLabel(job: ProcessingJobDto) {
  if (job.status === 'AI_EXTRACTING') return '批'
  if (job.status === 'AI_REVIEWING') return '条'
  return '单元'
}

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
  AI_ORGANIZING: 'AI 主题分类',
  AWAITING_REVIEW: '等待 AI 复审',
  AI_REVIEWING: 'AI 对照原文复审',
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
