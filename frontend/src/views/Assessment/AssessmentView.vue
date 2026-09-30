<template>
  <AdminLayout>
    <PageBreadcrumb
      page-title="智能测评"
      description="通过阶段性测评检验学习成果，并根据结果调整下一步学习重点。"
    >
      <template #actions>
        <Button v-if="submitted" size="sm" :start-icon="RefreshIcon" @click="restart"
          >重新测评</Button
        >
      </template>
    </PageBreadcrumb>

    <div class="grid items-start gap-6 xl:grid-cols-[minmax(0,1.65fr)_minmax(280px,0.75fr)]">
      <section
        class="overflow-hidden rounded-2xl border border-gray-200 bg-white dark:border-gray-800 dark:bg-white/[0.03]"
        aria-labelledby="assessment-title"
      >
        <template v-if="!submitted">
          <div class="border-b border-gray-100 px-5 py-4 dark:border-gray-800 sm:px-6">
            <div class="flex flex-wrap items-center justify-between gap-3">
              <div>
                <h2
                  id="assessment-title"
                  class="text-lg font-semibold text-gray-900 dark:text-white/90"
                >
                  知识图谱基础测评
                </h2>
                <p class="mt-1 text-sm text-gray-500 dark:text-gray-400">
                  共 {{ questions.length }} 题，完成后可查看掌握情况。
                </p>
              </div>
              <span
                class="rounded-full bg-brand-50 px-2.5 py-1 text-xs font-medium text-brand-600 dark:bg-brand-500/10 dark:text-brand-400"
                >第 {{ currentIndex + 1 }} / {{ questions.length }} 题</span
              >
            </div>
            <div
              class="mt-4 h-2 overflow-hidden rounded-full bg-gray-100 dark:bg-gray-800"
              aria-label="答题进度"
            >
              <div
                class="h-full rounded-full bg-brand-500 transition-[width] duration-300"
                :style="{ width: `${progress}%` }"
              ></div>
            </div>
          </div>
          <div class="p-5 sm:p-6">
            <p class="text-xs font-medium text-brand-600 dark:text-brand-400">
              {{ currentQuestion.topic }}
            </p>
            <h3 class="mt-2 text-lg font-semibold leading-7 text-gray-900 dark:text-white/90">
              {{ currentQuestion.question }}
            </h3>
            <div class="mt-6 space-y-3" role="radiogroup" :aria-label="currentQuestion.question">
              <button
                v-for="(option, optionIndex) in currentQuestion.options"
                :key="option"
                type="button"
                role="radio"
                :aria-checked="answers[currentQuestion.id] === optionIndex"
                :class="[
                  'flex w-full items-center gap-3 rounded-xl border p-4 text-start transition focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand-500',
                  answers[currentQuestion.id] === optionIndex
                    ? 'border-brand-500 bg-brand-25 text-gray-900 dark:border-brand-400 dark:bg-brand-500/10 dark:text-white/90'
                    : 'border-gray-200 text-gray-700 hover:border-brand-200 hover:bg-gray-50 dark:border-gray-800 dark:text-gray-300 dark:hover:border-brand-500/40 dark:hover:bg-white/[0.03]',
                ]"
                @click="answers[currentQuestion.id] = optionIndex"
              >
                <span
                  :class="
                    answers[currentQuestion.id] === optionIndex
                      ? 'border-brand-500 bg-brand-500 text-white'
                      : 'border-gray-300 text-gray-500 dark:border-gray-700 dark:text-gray-400'
                  "
                  class="flex size-6 shrink-0 items-center justify-center rounded-full border text-xs font-medium"
                  >{{ optionLabels[optionIndex] }}</span
                >
                <span class="text-sm leading-6">{{ option }}</span>
              </button>
            </div>
            <div
              class="mt-8 flex flex-wrap justify-between gap-3 border-t border-gray-100 pt-5 dark:border-gray-800"
            >
              <Button
                size="sm"
                variant="outline"
                :disabled="currentIndex === 0"
                @click="currentIndex--"
                >上一题</Button
              >
              <Button v-if="currentIndex < questions.length - 1" size="sm" @click="currentIndex++"
                >下一题</Button
              >
              <Button v-else size="sm" :disabled="!allAnswered" @click="submit">提交测评</Button>
            </div>
            <p
              v-if="currentIndex === questions.length - 1 && !allAnswered"
              class="mt-3 text-xs text-gray-500 dark:text-gray-400"
            >
              请完成全部题目后再提交。
            </p>
          </div>
        </template>

        <div v-else class="p-5 sm:p-6">
          <div class="flex flex-col items-center text-center">
            <span
              :class="
                score >= 60
                  ? 'bg-success-50 text-success-600 dark:bg-success-500/10 dark:text-success-400'
                  : 'bg-warning-50 text-warning-600 dark:bg-warning-500/10 dark:text-warning-400'
              "
              class="flex size-14 items-center justify-center rounded-2xl"
              ><CheckIcon class="size-7" aria-hidden="true"
            /></span>
            <h2
              id="assessment-title"
              class="mt-4 text-xl font-semibold text-gray-900 dark:text-white/90"
            >
              {{ score >= 60 ? '测评通过，当前阶段已完成' : '测评完成，再巩固一下吧' }}
            </h2>
            <p class="mt-2 text-sm leading-6 text-gray-500 dark:text-gray-400">
              你答对了 {{ correctCount }} / {{ questions.length }} 题。
            </p>
            <p class="mt-4 text-4xl font-semibold tabular-nums text-gray-900 dark:text-white/90">
              {{ score }}<span class="text-xl text-gray-400 dark:text-gray-500"> 分</span>
            </p>
          </div>
          <div
            class="mt-8 divide-y divide-gray-100 rounded-xl border border-gray-100 dark:divide-gray-800 dark:border-gray-800"
          >
            <div v-for="question in questions" :key="question.id" class="p-4">
              <div class="flex gap-3">
                <span
                  :class="
                    answers[question.id] === question.answer
                      ? 'bg-success-50 text-success-600 dark:bg-success-500/10 dark:text-success-400'
                      : 'bg-error-50 text-error-600 dark:bg-error-500/10 dark:text-error-400'
                  "
                  class="mt-0.5 flex size-6 shrink-0 items-center justify-center rounded-full"
                  ><CheckIcon v-if="answers[question.id] === question.answer" class="size-4" /><span
                    v-else
                    >×</span
                  ></span
                >
                <div>
                  <p class="text-sm font-medium leading-6 text-gray-800 dark:text-white/90">
                    {{ question.question }}
                  </p>
                  <p
                    v-if="answers[question.id] !== question.answer"
                    class="mt-1 text-xs text-gray-500 dark:text-gray-400"
                  >
                    正确答案：{{ question.options[question.answer] }}
                  </p>
                  <p class="mt-1 text-xs leading-5 text-gray-500 dark:text-gray-400">
                    {{ question.explanation }}
                  </p>
                </div>
              </div>
            </div>
          </div>
          <div class="mt-6 flex flex-wrap justify-center gap-3">
            <Button size="sm" @click="restart">重新测评</Button
            ><Button size="sm" variant="outline" @click="goToLearningPath">返回学习路径</Button>
          </div>
        </div>
      </section>

      <aside class="space-y-6">
        <section
          class="rounded-2xl border border-gray-200 bg-white p-5 dark:border-gray-800 dark:bg-white/[0.03] sm:p-6"
          aria-labelledby="progress-title"
        >
          <h2 id="progress-title" class="text-lg font-semibold text-gray-900 dark:text-white/90">
            答题进度
          </h2>
          <div class="mt-5 flex items-end gap-2">
            <span class="text-3xl font-semibold tabular-nums text-gray-900 dark:text-white/90">{{
              answeredCount
            }}</span
            ><span class="mb-1 text-sm text-gray-500 dark:text-gray-400"
              >/ {{ questions.length }} 已作答</span
            >
          </div>
          <div class="mt-4 grid grid-cols-5 gap-2">
            <button
              v-for="(question, index) in questions"
              :key="question.id"
              type="button"
              :aria-label="`跳转到第 ${index + 1} 题`"
              :class="[
                currentIndex === index
                  ? 'ring-2 ring-brand-500 ring-offset-2 dark:ring-offset-gray-900'
                  : '',
                answers[question.id] !== undefined
                  ? 'bg-brand-500 text-white'
                  : 'bg-gray-100 text-gray-500 dark:bg-gray-800 dark:text-gray-400',
              ]"
              class="flex aspect-square items-center justify-center rounded-lg text-sm font-medium"
              :disabled="submitted"
              @click="currentIndex = index"
            >
              {{ index + 1 }}
            </button>
          </div>
        </section>
        <section
          class="rounded-2xl border border-gray-200 bg-white p-5 dark:border-gray-800 dark:bg-white/[0.03] sm:p-6"
          aria-labelledby="tip-title"
        >
          <div class="flex items-start gap-3">
            <span
              class="flex size-10 shrink-0 items-center justify-center rounded-xl bg-warning-50 text-warning-600 dark:bg-warning-500/10 dark:text-warning-400"
              ><InfoIcon class="size-5" aria-hidden="true"
            /></span>
            <div>
              <h2 id="tip-title" class="font-semibold text-gray-900 dark:text-white/90">
                测评建议
              </h2>
              <p class="mt-2 text-sm leading-6 text-gray-500 dark:text-gray-400">
                测评达到 60 分后会自动完成当前阶段并解锁下一阶段；未通过时可返回学习路径继续复习。
              </p>
            </div>
          </div>
        </section>
      </aside>
    </div>
  </AdminLayout>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import AdminLayout from '@/components/layout/AdminLayout.vue'
import PageBreadcrumb from '@/components/common/PageBreadcrumb.vue'
import Button from '@/components/ui/Button.vue'
import { CheckIcon, InfoIcon, RefreshIcon } from '@/icons'
import { learningPathApi } from '@/services/learningPathApi'
import { graphApi } from '@/services/graphApi'
import { saveLearningPath } from '@/features/learning/learningPathStore'

interface Question {
  id: string
  topic: string
  question: string
  options: string[]
  answer: number
  explanation: string
}
const router = useRouter()
const fallbackQuestions: Question[] = [
  {
    id: 'concept',
    topic: '知识图谱概念',
    question: '知识图谱的核心作用是什么？',
    options: ['存储大量文件', '表达知识实体及其关联', '替代所有数据库', '只用于搜索图片'],
    answer: 1,
    explanation: '知识图谱以实体、属性和关系来表达并组织知识。',
  },
  {
    id: 'entity',
    topic: '实体与关系',
    question: '下列哪项最适合作为“课程”和“教师”之间的关系？',
    options: ['课程名称', '授课', '课程编号', '教师头像'],
    answer: 1,
    explanation: '“授课”描述两个实体之间的关联，是关系而非属性。',
  },
  {
    id: 'property',
    topic: '图数据建模',
    question: '实体的“创建时间”在图数据模型中通常属于什么？',
    options: ['节点属性', '关系类型', '实体名称', '图谱主题'],
    answer: 0,
    explanation: '创建时间用于描述实体本身，因此属于节点属性。',
  },
  {
    id: 'flow',
    topic: '图谱构建流程',
    question: '知识图谱构建中，审核通常位于哪个环节？',
    options: ['资料导入前', '知识抽取并形成候选结果后', '图谱删除后', '不需要审核'],
    answer: 1,
    explanation: '候选知识经审核后才适合入库并用于图谱展示。',
  },
  {
    id: 'application',
    topic: '知识推理与应用',
    question: '观察一个知识点的关联节点，最能帮助你做什么？',
    options: ['增加文件大小', '理解知识之间的上下文联系', '修改系统主题', '删除全部关系'],
    answer: 1,
    explanation: '关联节点能够展示知识的上下文与可继续探索的方向。',
  },
]
const questions = ref<Question[]>(fallbackQuestions)
const optionLabels = ['A', 'B', 'C', 'D']
const currentIndex = ref(0)
const submitted = ref(false)
const answers = reactive<Record<string, number>>({})
const currentQuestion = computed(() => questions.value[currentIndex.value])
const answeredCount = computed(() => Object.keys(answers).length)
const allAnswered = computed(() => answeredCount.value === questions.value.length)
const progress = computed(() => Math.round((answeredCount.value / questions.value.length) * 100))
const correctCount = computed(
  () => questions.value.filter((question) => answers[question.id] === question.answer).length,
)
const score = computed(() => Math.round((correctCount.value / questions.value.length) * 100))
function submit() {
  if (!allAnswered.value) return
  submitted.value = true
  const weakTopics = questions.value
    .filter((question) => answers[question.id] !== question.answer)
    .map((question) => question.topic)
  void learningPathApi
    .recordAssessment({
      score: score.value,
      correctCount: correctCount.value,
      totalCount: questions.value.length,
      weakTopics,
    })
    .then(saveLearningPath)
    .catch(() => undefined)
}
function restart() {
  Object.keys(answers).forEach((key) => delete answers[key])
  currentIndex.value = 0
  submitted.value = false
}
function goToLearningPath() {
  void router.push('/learning-path')
}

async function loadCurrentStepQuestions() {
  try {
    const path = await learningPathApi.getCurrent()
    const step = path?.steps.find((item) => item.status === 'current')
    if (!path || !step) return
    const [neighborhood, overview] = await Promise.all([
      graphApi.getNeighbors(step.nodeId, 1, 80),
      graphApi.getOverview(path.libraryId, 120),
    ])
    const center = neighborhood.nodes.find((node) => node.id === step.nodeId)
    const related = neighborhood.nodes.filter((node) => node.id !== step.nodeId)
    const relatedIds = new Set(neighborhood.nodes.map((node) => node.id))
    const unrelated = overview.nodes.filter((node) => !relatedIds.has(node.id))
    const firstEdge = neighborhood.edges.find(
      (edge) => edge.source === step.nodeId || edge.target === step.nodeId,
    )
    const edgeNodeId = firstEdge
      ? firstEdge.source === step.nodeId
        ? firstEdge.target
        : firstEdge.source
      : null
    const edgeNode = neighborhood.nodes.find((node) => node.id === edgeNodeId)
    if (!center || !related[0] || !unrelated[0] || !firstEdge || !edgeNode) return

    const definitionChoice = choices(
      center.definition || step.detail,
      [...related, ...unrelated].map((node) => node.definition || `${node.name}的相关知识说明`),
      center.id,
    )
    const relatedChoice = choices(
      related[0].name,
      unrelated.map((node) => node.name),
      center.id + 1,
    )
    const relationChoice = choices(
      firstEdge.relation,
      neighborhood.edges
        .map((edge) => edge.relation)
        .filter((value) => value !== firstEdge.relation),
      center.id + 2,
      ['属于', '包含', '关联', '对比'],
    )
    const typeChoice = choices(
      center.type,
      [...related, ...unrelated].map((node) => node.type),
      center.id + 3,
      ['concept', 'method', 'application', 'other'],
    )
    const neighborCountChoice = choices(
      String(related.length),
      [related.length - 1, related.length + 1, related.length + 2, related.length + 3]
        .filter((value) => value >= 0)
        .map(String),
      center.id + 4,
    )
    const generated = [
      definitionChoice,
      relatedChoice,
      relationChoice,
      typeChoice,
      neighborCountChoice,
    ]
    if (generated.some((item) => item.options.length < 4)) return
    questions.value = [
      {
        id: 'definition',
        topic: step.title,
        question: `以下哪项最符合“${center.name}”的定义？`,
        ...definitionChoice,
        explanation: center.definition || step.detail,
      },
      {
        id: 'related',
        topic: step.title,
        question: `根据当前知识图谱，哪个知识点与“${center.name}”直接关联？`,
        ...relatedChoice,
        explanation: `“${related[0].name}”位于“${center.name}”的一阶关联图中。`,
      },
      {
        id: 'relation',
        topic: step.title,
        question: `图谱中“${center.name}”与“${edgeNode.name}”之间的关系是什么？`,
        ...relationChoice,
        explanation: `两者在当前图谱中的关系为“${firstEdge.relation}”。`,
      },
      {
        id: 'type',
        topic: step.title,
        question: `“${center.name}”在当前知识图谱中的节点类型是什么？`,
        ...typeChoice,
        explanation: `该节点在知识库中的类型为“${center.type}”。`,
      },
      {
        id: 'neighbor-count',
        topic: step.title,
        question: `“${center.name}”当前的一阶邻域包含多少个关联知识点？`,
        ...neighborCountChoice,
        explanation: `当前图谱返回了 ${related.length} 个与“${center.name}”直接关联的知识点。`,
      },
    ]
  } catch {
    questions.value = fallbackQuestions
  }
}

function choices(
  correct: string,
  distractors: string[],
  seed: number,
  fallbacks: string[] = [],
): { options: string[]; answer: number } {
  const values = [correct, ...distractors, ...fallbacks]
    .map((value) => value?.trim())
    .filter((value, index, all): value is string => Boolean(value) && all.indexOf(value) === index)
    .slice(0, 4)
  if (values.length < 4) return { options: values, answer: 0 }
  const shift = Math.abs(seed) % values.length
  const options = [...values.slice(shift), ...values.slice(0, shift)]
  return { options, answer: options.indexOf(correct) }
}

onMounted(() => void loadCurrentStepQuestions())
</script>
