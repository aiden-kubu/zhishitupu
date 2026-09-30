<template>
  <AdminLayout>
    <PageBreadcrumb
      page-title="学习路径"
      description="根据知识图谱循序渐进地学习，并在每个阶段回到图谱中继续探索。"
    >
      <template #actions>
        <Button
          size="sm"
          :start-icon="RefreshIcon"
          :disabled="generating || loadingLibraries"
          @click="openGenerator"
        >
          {{ generating ? '正在生成…' : path ? '重新生成学习路径' : '生成学习路径' }}
        </Button>
      </template>
    </PageBreadcrumb>

    <Alert v-if="pageError" class="mb-6" variant="error" title="操作未完成" :message="pageError" />
    <Alert
      v-else-if="notice"
      class="mb-6"
      variant="success"
      title="学习路径已更新"
      :message="notice"
    />

    <div class="space-y-6">
      <section class="grid gap-4 sm:grid-cols-3" aria-label="学习路径概览">
        <div
          v-for="stat in stats"
          :key="stat.label"
          class="rounded-2xl border border-gray-200 bg-white p-5 dark:border-gray-800 dark:bg-white/[0.03]"
        >
          <div class="flex items-center gap-4">
            <span
              :class="['flex size-11 shrink-0 items-center justify-center rounded-2xl', stat.tone]"
              ><component :is="stat.icon" class="size-5" aria-hidden="true"
            /></span>
            <div>
              <p class="text-sm text-gray-500 dark:text-gray-400">{{ stat.label }}</p>
              <p class="mt-1 text-2xl font-semibold tabular-nums text-gray-900 dark:text-white/90">
                {{ stat.value }}
              </p>
            </div>
          </div>
        </div>
      </section>

      <div class="grid items-start gap-6 xl:grid-cols-[minmax(0,1.65fr)_minmax(300px,0.9fr)]">
        <section
          class="overflow-hidden rounded-2xl border border-gray-200 bg-white dark:border-gray-800 dark:bg-white/[0.03]"
          aria-labelledby="path-title"
        >
          <div class="border-b border-gray-100 px-5 py-4 dark:border-gray-800 sm:px-6">
            <h2 id="path-title" class="text-lg font-semibold text-gray-900 dark:text-white/90">
              {{ pathTitle }}
            </h2>
            <p class="mt-1 text-sm text-gray-500 dark:text-gray-400">
              {{
                path ? `目标：${path.goal}` : '选择知识库和学习目标，生成由真实图谱节点组成的路径。'
              }}
            </p>
          </div>
          <div
            v-if="!steps.length"
            class="flex min-h-80 flex-col items-center justify-center px-6 py-12 text-center"
          >
            <span
              class="flex size-14 items-center justify-center rounded-2xl bg-brand-50 text-brand-500 dark:bg-brand-500/10 dark:text-brand-400"
              ><DocsIcon class="size-7" aria-hidden="true"
            /></span>
            <p class="mt-4 font-semibold text-gray-900 dark:text-white/90">还没有学习路径</p>
            <p class="mt-2 max-w-sm text-sm leading-6 text-gray-500 dark:text-gray-400">
              生成后将从第一个真实知识点开始，初始进度为 0%。
            </p>
            <Button class="mt-5" size="sm" @click="openGenerator">生成学习路径</Button>
          </div>
          <ol v-else class="p-3 sm:p-4">
            <li v-for="(step, index) in steps" :key="step.id" class="relative">
              <div
                v-if="index < steps.length - 1"
                class="absolute start-6 top-13 h-[calc(100%-1.5rem)] border-s-2 border-dashed border-gray-200 dark:border-gray-700"
                aria-hidden="true"
              ></div>
              <button
                type="button"
                :disabled="step.status === 'locked'"
                :aria-current="selectedStep?.id === step.id ? 'step' : undefined"
                :class="[
                  'relative z-1 flex w-full items-start gap-4 rounded-xl p-3 text-start transition focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand-500 sm:p-4',
                  selectedStep?.id === step.id
                    ? 'bg-brand-25 dark:bg-brand-500/10'
                    : 'hover:bg-gray-50 dark:hover:bg-white/[0.03]',
                  step.status === 'locked' ? 'cursor-not-allowed opacity-65' : '',
                ]"
                @click="selectStep(step.id)"
              >
                <span
                  :class="stepCircleClass(step.status)"
                  class="flex size-12 shrink-0 items-center justify-center rounded-full text-sm font-semibold"
                  ><CheckIcon
                    v-if="step.status === 'completed'"
                    class="size-5"
                    aria-label="已完成"
                  /><span v-else>{{ index + 1 }}</span></span
                >
                <span class="min-w-0 flex-1 pt-0.5"
                  ><span class="flex flex-wrap items-center justify-between gap-2"
                    ><span class="font-semibold text-gray-900 dark:text-white/90">{{
                      step.title
                    }}</span
                    ><span
                      :class="statusClass(step.status)"
                      class="rounded-full px-2.5 py-1 text-xs font-medium"
                      >{{ statusText(step.status) }}</span
                    ></span
                  ><span class="mt-1 block text-sm leading-6 text-gray-500 dark:text-gray-400">{{
                    step.description
                  }}</span></span
                >
              </button>
            </li>
          </ol>
        </section>

        <aside class="space-y-6">
          <section
            class="overflow-hidden rounded-2xl border border-gray-200 bg-white dark:border-gray-800 dark:bg-white/[0.03]"
            aria-labelledby="current-title"
          >
            <div class="border-b border-gray-100 px-5 py-4 dark:border-gray-800 sm:px-6">
              <h2 id="current-title" class="text-lg font-semibold text-gray-900 dark:text-white/90">
                {{ detailHeading }}
              </h2>
            </div>
            <div v-if="selectedStep" class="p-5 sm:p-6">
              <p class="text-lg font-semibold text-gray-900 dark:text-white/90">
                {{ selectedStep.title }}
              </p>
              <div class="mt-3 flex flex-wrap gap-2">
                <span
                  :class="statusClass(selectedStep.status)"
                  class="rounded-full px-2.5 py-1 text-xs font-medium"
                  >{{ statusText(selectedStep.status) }}</span
                ><span
                  class="rounded-full bg-gray-100 px-2.5 py-1 text-xs font-medium text-gray-600 dark:bg-gray-800 dark:text-gray-300"
                  >预计 {{ selectedStep.minutes }} 分钟</span
                >
              </div>
              <p class="mt-5 text-sm leading-7 text-gray-600 dark:text-gray-300">
                {{ selectedStep.detail }}
              </p>
              <div class="mt-6 flex flex-wrap gap-3">
                <Button
                  size="sm"
                  :disabled="selectedStep.status === 'locked' || graphChecking"
                  @click="openGraph"
                  >{{ graphChecking ? '正在验证…' : '开始学习' }}</Button
                >
              </div>
              <p
                v-if="selectedStep.status === 'locked'"
                class="mt-4 text-xs leading-5 text-gray-500 dark:text-gray-400"
              >
                请先完成上一个阶段后再学习本节。
              </p>
            </div>
            <div v-else class="p-5 text-sm leading-6 text-gray-500 dark:text-gray-400 sm:p-6">
              生成路径后，这里会展示当前阶段的真实知识内容。
            </div>
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
                <p class="mt-2 text-sm leading-6 text-gray-500 dark:text-gray-400">
                  {{ suggestion }}
                </p>
              </div>
            </div>
          </section>
        </aside>
      </div>
    </div>

    <Modal v-if="generatorOpen" full-screen-backdrop @close="closeGenerator">
      <template #body>
        <div
          class="relative z-1 w-[calc(100%-2rem)] max-w-xl rounded-2xl border border-gray-200 bg-white shadow-theme-xl dark:border-gray-800 dark:bg-gray-900"
        >
          <div class="border-b border-gray-100 px-5 py-4 dark:border-gray-800 sm:px-6">
            <h2 class="text-lg font-semibold text-gray-900 dark:text-white/90">
              {{ path ? '重新生成学习路径' : '生成学习路径' }}
            </h2>
            <p class="mt-1 text-sm text-gray-500 dark:text-gray-400">
              生成结果只会使用所选知识库中已存在的图谱节点。
            </p>
          </div>
          <div class="space-y-5 p-5 sm:p-6">
            <Alert
              v-if="generatorError"
              variant="error"
              title="无法生成学习路径"
              :message="generatorError"
            />
            <Alert
              v-if="path && completedCount > 0"
              variant="warning"
              title="已有学习进度"
              message="重新生成后，当前路径的完成状态将从 0% 开始；生成失败时不会覆盖原路径。"
            />
            <div>
              <label class="mb-2 block text-sm font-medium text-gray-700 dark:text-gray-300"
                >知识库</label
              ><SelectInput
                v-model="formLibraryId"
                :options="libraryOptions"
                placeholder="请选择知识库"
                :disabled="loadingLibraries || generating"
              />
              <p
                v-if="!loadingLibraries && !libraryOptions.length"
                class="mt-2 text-xs text-error-500"
              >
                没有包含知识节点的可用知识库。
              </p>
            </div>
            <div>
              <label class="mb-2 block text-sm font-medium text-gray-700 dark:text-gray-300"
                >学习目标</label
              ><TextInput
                v-model="formGoal"
                placeholder="例如：掌握核心概念并理解它们之间的关系"
                :disabled="generating"
              />
              <p class="mt-2 text-xs text-gray-500 dark:text-gray-400">
                目标会用于优先选择名称或定义相关的知识点。
              </p>
            </div>
            <div class="grid gap-5 sm:grid-cols-2">
              <div>
                <label class="mb-2 block text-sm font-medium text-gray-700 dark:text-gray-300"
                  >学习难度</label
                ><SelectInput
                  v-model="formDifficulty"
                  :options="difficultyOptions"
                  :disabled="generating"
                />
              </div>
              <div>
                <label class="mb-2 block text-sm font-medium text-gray-700 dark:text-gray-300"
                  >阶段数量</label
                ><SelectInput v-model="formCount" :options="countOptions" :disabled="generating" />
              </div>
            </div>
          </div>
          <div
            class="flex flex-wrap justify-end gap-3 border-t border-gray-100 px-5 py-4 dark:border-gray-800 sm:px-6"
          >
            <Button size="sm" variant="outline" :disabled="generating" @click="closeGenerator"
              >取消</Button
            ><Button
              size="sm"
              :disabled="generating || !formLibraryId || !libraryOptions.length"
              @click="generatePath"
              >{{ generating ? '正在分析图谱…' : path ? '确认重新生成' : '生成路径' }}</Button
            >
          </div>
        </div>
      </template>
    </Modal>
  </AdminLayout>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import AdminLayout from '@/components/layout/AdminLayout.vue'
import PageBreadcrumb from '@/components/common/PageBreadcrumb.vue'
import Button from '@/components/ui/Button.vue'
import Alert from '@/components/ui/Alert.vue'
import Modal from '@/components/ui/Modal.vue'
import SelectInput from '@/components/ui/form/SelectInput.vue'
import TextInput from '@/components/ui/form/TextInput.vue'
import { CheckIcon, DocsIcon, InfoIcon, PieChartIcon, RefreshIcon, TaskIcon } from '@/icons'
import { graphApi } from '@/services/graphApi'
import { libraryApi } from '@/services/libraryApi'
import { learningPathApi } from '@/services/learningPathApi'
import type { LibraryDto } from '@/services/types'
import {
  generateLearningSteps,
  loadLearningPath,
  saveLearningPath,
  type LearningDifficulty,
  type LearningPathState,
  type LearningStepStatus,
} from '@/features/learning/learningPathStore'

const router = useRouter()
const path = ref<LearningPathState | null>(loadLearningPath())
const libraries = ref<LibraryDto[]>([])
const generatorOpen = ref(false)
const loadingLibraries = ref(false)
const generating = ref(false)
const graphChecking = ref(false)
const pageError = ref<string | null>(null)
const notice = ref<string | null>(null)
const generatorError = ref<string | null>(null)
const formLibraryId = ref('')
const formGoal = ref('')
const formDifficulty = ref<LearningDifficulty>('beginner')
const formCount = ref('5')

const steps = computed(() => path.value?.steps ?? [])
const selectedStep = computed(
  () =>
    steps.value.find((step) => step.id === path.value?.selectedStepId) ?? steps.value[0] ?? null,
)
const currentStep = computed(() => steps.value.find((step) => step.status === 'current') ?? null)
const completedCount = computed(
  () => steps.value.filter((step) => step.status === 'completed').length,
)
const progress = computed(() =>
  steps.value.length ? Math.round((completedCount.value / steps.value.length) * 100) : 0,
)
const pendingCount = computed(
  () => steps.value.filter((step) => step.status !== 'completed').length,
)
const pathTitle = computed(() => (path.value ? `${path.value.libraryName}学习路径` : '学习路径'))
const detailHeading = computed(() =>
  selectedStep.value && currentStep.value?.id !== selectedStep.value.id ? '阶段详情' : '当前学习',
)
const suggestion = computed(() => {
  if (!path.value) return '先选择一个包含知识节点的知识库，再根据学习目标生成路径。'
  if (!currentStep.value) return '你已完成本路径。可以回到图谱中继续扩展相关知识。'
  if (!currentStep.value.visitedAt)
    return `点击“开始学习”查看「${currentStep.value.title}」的定义和关联知识点，完成测评并达到 60 分后将自动完成本阶段。`
  if (currentStep.value.assessmentScore == null)
    return `请前往智能测评；达到 60 分后将自动完成「${currentStep.value.title}」并解锁下一阶段。`
  if (currentStep.value.assessmentScore < 60)
    return `本次测评为 ${currentStep.value.assessmentScore} 分，建议复习「${currentStep.value.title}」后重新测评。`
  return `「${currentStep.value.title}」测评已通过，系统正在更新学习进度。`
})
const stats = computed(() => [
  {
    label: '当前路径',
    value: path.value ? '1' : '0',
    icon: DocsIcon,
    tone: 'bg-brand-50 text-brand-500 dark:bg-brand-500/10 dark:text-brand-400',
  },
  {
    label: '学习进度',
    value: `${progress.value}%`,
    icon: PieChartIcon,
    tone: 'bg-success-50 text-success-600 dark:bg-success-500/10 dark:text-success-400',
  },
  {
    label: '待学习',
    value: String(pendingCount.value),
    icon: TaskIcon,
    tone: 'bg-warning-50 text-warning-600 dark:bg-warning-500/10 dark:text-warning-400',
  },
])
const libraryOptions = computed(() =>
  libraries.value
    .filter((library) => (library.nodeCount ?? 0) > 0 && library.status === 'active')
    .map((library) => ({
      value: String(library.id),
      label: `${library.name}（${library.nodeCount} 个知识点）`,
    })),
)
const difficultyOptions = [
  { value: 'beginner', label: '入门：优先核心概念' },
  { value: 'intermediate', label: '进阶：概念与方法并重' },
  { value: 'advanced', label: '深入：优先方法与应用' },
]
const countOptions = [
  { value: '3', label: '3 个阶段' },
  { value: '5', label: '5 个阶段' },
  { value: '8', label: '8 个阶段' },
]

async function ensureLibraries() {
  if (libraries.value.length || loadingLibraries.value) return
  loadingLibraries.value = true
  try {
    libraries.value = await libraryApi.list()
  } catch (cause) {
    generatorError.value = errorMessage(cause, '知识库加载失败，请确认本地服务正常。')
  } finally {
    loadingLibraries.value = false
  }
}
async function openGenerator() {
  pageError.value = null
  notice.value = null
  generatorError.value = null
  generatorOpen.value = true
  await ensureLibraries()
  const firstAvailable = libraryOptions.value[0]?.value ?? ''
  formLibraryId.value = path.value ? String(path.value.libraryId) : firstAvailable
  if (!libraryOptions.value.some((option) => option.value === formLibraryId.value))
    formLibraryId.value = firstAvailable
  formGoal.value = path.value?.goal ?? ''
  formDifficulty.value = path.value?.difficulty ?? 'beginner'
  formCount.value = String(path.value?.steps.length ?? 5)
}
function closeGenerator() {
  if (!generating.value) generatorOpen.value = false
}
async function generatePath() {
  const libraryId = Number(formLibraryId.value)
  const library = libraries.value.find((item) => item.id === libraryId)
  if (!library) {
    generatorError.value = '请选择一个有效的知识库。'
    return
  }
  generating.value = true
  generatorError.value = null
  try {
    const graph = await graphApi.getOverview(libraryId, 300)
    if (graph.nodes.length < 2)
      throw new Error('该知识库的有效知识节点不足，至少需要 2 个节点才能生成路径。')
    const generatedSteps = generateLearningSteps(graph.nodes, graph.edges, {
      goal: formGoal.value.trim(),
      difficulty: formDifficulty.value,
      count: Number(formCount.value),
    })
    const nextPath: LearningPathState = {
      version: 2,
      libraryId,
      libraryName: library.name,
      goal: formGoal.value.trim() || `系统化掌握${library.name}`,
      difficulty: formDifficulty.value,
      updatedAt: new Date().toISOString(),
      selectedStepId: generatedSteps[0].id,
      steps: generatedSteps,
    }
    const savedPath = await learningPathApi.save(nextPath)
    saveLearningPath(savedPath)
    path.value = savedPath
    generatorOpen.value = false
    pageError.value = null
    notice.value = `已根据“${library.name}”中的 ${generatedSteps.length} 个真实知识节点生成路径，进度从 0% 开始。`
  } catch (cause) {
    generatorError.value = errorMessage(cause, '生成失败，原学习路径已保留。')
  } finally {
    generating.value = false
  }
}
function persist() {
  if (path.value) {
    path.value.updatedAt = new Date().toISOString()
    saveLearningPath(path.value)
  }
}
function selectStep(id: string) {
  if (!path.value) return
  const step = steps.value.find((item) => item.id === id)
  if (!step || step.status === 'locked') return
  path.value.selectedStepId = id
  persist()
}
async function openGraph() {
  if (!selectedStep.value || selectedStep.value.status === 'locked') return
  graphChecking.value = true
  pageError.value = null
  try {
    await graphApi.getNode(selectedStep.value.nodeId)
    const selectedId = selectedStep.value.id
    const updated = await learningPathApi.markVisited(selectedId)
    updated.selectedStepId = selectedId
    path.value = updated
    saveLearningPath(updated)
    await router.push({
      path: '/',
      query: { node: String(selectedStep.value.nodeId), depth: '1', mode: 'focus' },
    })
  } catch (cause) {
    pageError.value = errorMessage(cause, '该知识点已不存在，请重新生成学习路径。')
  } finally {
    graphChecking.value = false
  }
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
function stepCircleClass(status: LearningStepStatus) {
  return status === 'completed'
    ? 'bg-success-500 text-white'
    : status === 'current'
      ? 'bg-brand-500 text-white shadow-theme-sm'
      : 'border border-gray-200 bg-gray-50 text-gray-500 dark:border-gray-700 dark:bg-gray-800 dark:text-gray-400'
}
function errorMessage(cause: unknown, fallback: string) {
  return cause instanceof Error && cause.message ? cause.message : fallback
}

async function synchronizePath() {
  try {
    const remote = await learningPathApi.getCurrent()
    if (remote) {
      path.value = remote
      saveLearningPath(remote)
      return
    }
    if (path.value) {
      const saved = await learningPathApi.save(path.value)
      path.value = saved
      saveLearningPath(saved)
      return
    }
    await openGenerator()
  } catch (cause) {
    pageError.value = errorMessage(cause, '学习路径同步失败，请确认本地服务正常。')
    if (!path.value) await openGenerator()
  }
}

onMounted(() => void synchronizePath())
</script>
