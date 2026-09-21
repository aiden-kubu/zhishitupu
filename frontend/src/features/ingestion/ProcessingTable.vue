<template>
  <div class="space-y-3">
    <article v-for="job in jobs" :key="job.id" class="rounded-2xl border border-gray-200 bg-white p-5 dark:border-gray-800 dark:bg-white/[0.03]">
      <div class="flex flex-wrap items-start justify-between gap-3">
        <div class="min-w-0 flex-1">
          <h2 class="text-sm font-semibold break-all text-gray-800 dark:text-white/90">{{ job.documentTitle || '等待 AI 识别课程名称' }}</h2>
          <p class="mt-1 text-xs text-gray-500 dark:text-gray-400">来源：{{ job.documentName }} <span class="mx-1">·</span> 任务 #{{ job.id }} <span class="mx-1">·</span> {{ formatDate(job.createdAt) }} 上传</p>
        </div>
        <Badge :color="statusColor(job.status)">{{ JOB_STATUS_LABELS[job.status] ?? job.status }}</Badge>
      </div>
      <div class="mt-5 grid gap-5 md:grid-cols-[minmax(0,1fr)_auto] md:items-end">
        <div>
          <div class="mb-2 flex flex-wrap items-center justify-between gap-2 text-xs text-gray-500 dark:text-gray-400">
            <span>{{ STAGE_LABELS[job.stage] ?? job.stage }}</span>
            <span class="tabular-nums">{{ progressDetail(job) }}<span v-if="job.retryCount > 0"> · 重试 {{ job.retryCount }} 次</span></span>
          </div>
          <JobProgress :progress="job.progress" class="w-full [&>div]:!w-full" />
          <p v-if="job.status === 'AI_EXTRACTING'" class="mt-2 text-xs leading-5 text-gray-500 dark:text-gray-400">
            {{ job.stage === 'AI_IDENTIFYING' ? 'AI 正在根据资料内容识别课程名称与版本关系…' : job.stage === 'AI_ORGANIZING' ? '正在将知识归入已识别课程，分类进度会自动保存…' : '正在抽取知识，已完成的批次会自动保存。' }}
          </p>
          <p v-else-if="job.status === 'AI_REVIEWING'" class="mt-2 text-xs text-gray-500 dark:text-gray-400">正在逐项核对原文证据，通过后自动入库，无需人工确认。</p>
          <p v-else-if="job.status === 'AWAITING_REVIEW'" class="mt-2 text-xs text-gray-500 dark:text-gray-400">识别已完成，即将由 AI 复审并自动入库。</p>
          <p v-else-if="['FAILED', 'CANCELLED'].includes(job.status) && (job.stagedBatches ?? 0) > 0" class="mt-2 text-xs leading-5 text-gray-500 dark:text-gray-400">
            已保留 {{ job.stagedBatches }} 批抽取结果，点「继续处理」将从断点继续，这些批次不会再调用模型。
          </p>
        </div>
        <div class="flex flex-wrap gap-2">
          <Button v-if="job.status === 'AWAITING_REVIEW'" size="sm" @click="emit('retry', job)">开始 AI 复审</Button>
          <Button v-if="job.candidateCount" size="sm" variant="outline" @click="emit('go-review', job)">查看复审结果</Button>
          <Button v-if="['FAILED', 'CANCELLED'].includes(job.status)" size="sm" variant="outline" @click="emit('retry', job)">继续处理</Button>
          <Button v-if="job.status === 'COMPLETED' && job.extractable" size="sm" variant="outline" @click="emit('extract', job)">重新识别知识</Button>
          <Button v-if="JOB_ACTIVE_STATUSES.includes(job.status)" size="sm" variant="outline" @click="emit('cancel', job)">取消任务</Button>
        </div>
      </div>
      <div v-if="job.sourceWarning" class="mt-4 rounded-xl bg-warning-50 p-3 text-xs leading-5 text-warning-700 dark:bg-warning-500/10 dark:text-warning-400" role="status">{{ job.sourceWarning }}</div>
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

function progressDetail(job: ProcessingJobDto) {
  if (job.status === 'COMPLETED') return `已解析 ${job.documentPages ?? 0} 页${job.candidateCount ? ` · 已复审 ${job.candidateCount} 条` : ''}`
  if (['FAILED', 'CANCELLED'].includes(job.status)) return `已解析 ${job.documentPages ?? 0} 页${job.stagedBatches ? ` · 已保存 ${job.stagedBatches} 批` : ''}`
  if (job.stage === 'AI_IDENTIFYING') return '正在阅读资料内容'
  const unit = job.stage === 'AI_ORGANIZING' ? '条知识' : job.status === 'AI_EXTRACTING' ? '批' : job.status === 'AI_REVIEWING' ? '条' : '页'
  return `已处理 ${job.processedUnits} / ${job.totalUnits || '—'} ${unit}`
}

const emit = defineEmits<{
  (e: 'go-review', job: ProcessingJobDto): void
  (e: 'retry', job: ProcessingJobDto): void
  (e: 'cancel', job: ProcessingJobDto): void
  (e: 'extract', job: ProcessingJobDto): void
}>()

/** §8.4 任务阶段 → 中文（前端负责映射） */
const STAGE_LABELS: Record<string, string> = {
  UPLOADED: '已上传',
  AI_IDENTIFYING: '识别课程',
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
