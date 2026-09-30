<template>
  <AdminLayout>
    <PageBreadcrumb
      page-title="学习进度"
      description="查看当前学习路径的完成情况，及时调整接下来的学习节奏。"
    >
      <template #actions
        ><Button size="sm" :start-icon="RefreshIcon" @click="loadProgress"
          >同步进度</Button
        ></template
      >
    </PageBreadcrumb>

    <div class="space-y-6">
      <section
        class="overflow-hidden rounded-2xl border border-gray-200 bg-white dark:border-gray-800 dark:bg-white/[0.03]"
        aria-labelledby="overview-title"
      >
        <div
          class="flex flex-wrap items-center justify-between gap-4 border-b border-gray-100 px-5 py-4 dark:border-gray-800 sm:px-6"
        >
          <div>
            <h2 id="overview-title" class="text-lg font-semibold text-gray-900 dark:text-white/90">
              {{ path ? `${path.libraryName}学习路径` : '尚未生成学习路径' }}
            </h2>
            <p class="mt-1 text-sm text-gray-500 dark:text-gray-400">
              {{
                path
                  ? `当前路径共 ${steps.length} 个学习阶段。`
                  : '请先前往学习路径生成真实的学习阶段。'
              }}
            </p>
          </div>
          <span
            class="rounded-full bg-brand-50 px-2.5 py-1 text-xs font-medium text-brand-600 dark:bg-brand-500/10 dark:text-brand-400"
            >{{ path ? (remainingCount ? '进行中' : '已完成') : '未开始' }}</span
          >
        </div>
        <div class="grid gap-6 p-5 sm:p-6 lg:grid-cols-[minmax(0,1fr)_auto] lg:items-center">
          <div>
            <div class="flex items-end justify-between gap-3">
              <p class="text-sm font-medium text-gray-700 dark:text-gray-300">整体完成度</p>
              <p class="text-3xl font-semibold tabular-nums text-gray-900 dark:text-white/90">
                {{ progress }}%
              </p>
            </div>
            <div class="mt-3 h-3 overflow-hidden rounded-full bg-gray-100 dark:bg-gray-800">
              <div
                class="h-full rounded-full bg-brand-500 transition-[width] duration-300"
                :style="{ width: `${progress}%` }"
              ></div>
            </div>
            <p class="mt-3 text-sm text-gray-500 dark:text-gray-400">
              已完成 {{ completedCount }} 个阶段，还剩 {{ remainingCount }} 个阶段。
            </p>
          </div>
          <Button size="sm" @click="continueLearning">{{
            path ? '继续学习' : '生成学习路径'
          }}</Button>
        </div>
      </section>

      <div class="grid items-start gap-6 xl:grid-cols-[minmax(0,1.65fr)_minmax(280px,0.75fr)]">
        <section
          class="overflow-hidden rounded-2xl border border-gray-200 bg-white dark:border-gray-800 dark:bg-white/[0.03]"
          aria-labelledby="stage-title"
        >
          <div class="border-b border-gray-100 px-5 py-4 dark:border-gray-800 sm:px-6">
            <h2 id="stage-title" class="text-lg font-semibold text-gray-900 dark:text-white/90">
              阶段进度
            </h2>
          </div>
          <ol v-if="steps.length" class="divide-y divide-gray-100 dark:divide-gray-800">
            <li
              v-for="(step, index) in steps"
              :key="step.id"
              class="flex items-center gap-4 px-5 py-4 sm:px-6"
            >
              <span
                :class="circleClass(step.status)"
                class="flex size-10 shrink-0 items-center justify-center rounded-full text-sm font-semibold"
                ><CheckIcon
                  v-if="step.status === 'completed'"
                  class="size-5"
                  aria-label="已完成"
                /><span v-else>{{ index + 1 }}</span></span
              >
              <div class="min-w-0 flex-1">
                <p class="font-medium text-gray-900 dark:text-white/90">{{ step.title }}</p>
                <p class="mt-1 truncate text-sm text-gray-500 dark:text-gray-400">
                  {{ step.description }}
                </p>
              </div>
              <span
                :class="statusClass(step.status)"
                class="shrink-0 rounded-full px-2.5 py-1 text-xs font-medium"
                >{{ statusText(step.status) }}</span
              >
            </li>
          </ol>
          <div
            v-else
            class="px-5 py-10 text-center text-sm text-gray-500 dark:text-gray-400 sm:px-6"
          >
            生成学习路径后，阶段进度会显示在这里。
          </div>
        </section>
        <aside class="space-y-6">
          <section
            class="rounded-2xl border border-gray-200 bg-white p-5 dark:border-gray-800 dark:bg-white/[0.03] sm:p-6"
            aria-labelledby="current-title"
          >
            <h2 id="current-title" class="text-lg font-semibold text-gray-900 dark:text-white/90">
              当前学习
            </h2>
            <p class="mt-4 text-base font-semibold text-gray-900 dark:text-white/90">
              {{ currentStep?.title ?? '已完成全部学习阶段' }}
            </p>
            <p class="mt-2 text-sm leading-6 text-gray-500 dark:text-gray-400">
              {{
                currentStep
                  ? currentStep.description
                  : '你可以返回图谱，围绕感兴趣的知识点继续探索。'
              }}
            </p>
            <Button class="mt-5" size="sm" @click="continueLearning">{{
              currentStep ? '返回学习路径' : path ? '查看学习路径' : '生成学习路径'
            }}</Button>
          </section>
          <section
            class="rounded-2xl border border-gray-200 bg-white p-5 dark:border-gray-800 dark:bg-white/[0.03] sm:p-6"
            aria-labelledby="suggestion-title"
          >
            <div class="flex items-start gap-3">
              <span
                class="flex size-10 shrink-0 items-center justify-center rounded-xl bg-warning-50 text-warning-600 dark:bg-warning-500/10 dark:text-warning-400"
                ><InfoIcon class="size-5" aria-hidden="true"
              /></span>
              <div>
                <h2 id="suggestion-title" class="font-semibold text-gray-900 dark:text-white/90">
                  学习建议
                </h2>
                <p class="mt-2 text-sm leading-6 text-gray-500 dark:text-gray-400">{{ advice }}</p>
              </div>
            </div>
          </section>
        </aside>
      </div>
    </div>
  </AdminLayout>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import AdminLayout from '@/components/layout/AdminLayout.vue'
import PageBreadcrumb from '@/components/common/PageBreadcrumb.vue'
import Button from '@/components/ui/Button.vue'
import { CheckIcon, InfoIcon, RefreshIcon } from '@/icons'
import {
  loadLearningPath,
  saveLearningPath,
  type LearningPathState,
  type LearningStepStatus,
} from '@/features/learning/learningPathStore'
import { learningPathApi } from '@/services/learningPathApi'

const router = useRouter()
const path = ref<LearningPathState | null>(null)
const steps = computed(() => path.value?.steps ?? [])
const completedCount = computed(
  () => steps.value.filter((step) => step.status === 'completed').length,
)
const remainingCount = computed(() => steps.value.length - completedCount.value)
const progress = computed(() =>
  steps.value.length ? Math.round((completedCount.value / steps.value.length) * 100) : 0,
)
const currentStep = computed(() => steps.value.find((step) => step.status === 'current'))
const advice = computed(() => {
  if (!currentStep.value) return '已完成全部阶段。建议回到图谱，从一个感兴趣的知识点开始继续拓展。'
  if (!currentStep.value.visitedAt) return `下一步：进入图谱学习「${currentStep.value.title}」。`
  if (currentStep.value.assessmentScore == null)
    return '下一步：完成智能测评，达到 60 分后完成当前阶段。'
  if (currentStep.value.assessmentScore < 60)
    return `测评成绩 ${currentStep.value.assessmentScore} 分，建议复习当前知识点后重新测评。`
  return '当前阶段的图谱学习与测评均已完成，可以返回学习路径完成该阶段。'
})
async function loadProgress() {
  try {
    const remote = await learningPathApi.getCurrent()
    path.value = remote
    if (remote) saveLearningPath(remote)
  } catch {
    path.value = loadLearningPath()
  }
}
function continueLearning() {
  void router.push('/learning-path')
}
function statusText(status: LearningStepStatus) {
  return status === 'completed' ? '已完成' : status === 'current' ? '学习中' : '未开始'
}
function statusClass(status: LearningStepStatus) {
  return status === 'completed'
    ? 'bg-success-50 text-success-700 dark:bg-success-500/10 dark:text-success-400'
    : status === 'current'
      ? 'bg-brand-50 text-brand-600 dark:bg-brand-500/10 dark:text-brand-400'
      : 'bg-gray-100 text-gray-600 dark:bg-gray-800 dark:text-gray-400'
}
function circleClass(status: LearningStepStatus) {
  return status === 'completed'
    ? 'bg-success-500 text-white'
    : status === 'current'
      ? 'bg-brand-500 text-white'
      : 'border border-gray-200 bg-gray-50 text-gray-500 dark:border-gray-700 dark:bg-gray-800 dark:text-gray-400'
}
const refreshProgress = () => void loadProgress()
onMounted(() => {
  refreshProgress()
  window.addEventListener('focus', refreshProgress)
  window.addEventListener('storage', refreshProgress)
})
onBeforeUnmount(() => {
  window.removeEventListener('focus', refreshProgress)
  window.removeEventListener('storage', refreshProgress)
})
</script>
