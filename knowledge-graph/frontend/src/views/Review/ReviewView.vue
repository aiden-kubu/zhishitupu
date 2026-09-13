<template>
  <AdminLayout>
    <PageBreadcrumb
      page-title="AI 复审"
      description="查看 AI 对照原文的复核结果，通过的知识会自动进入图谱。"
    >
      <template #actions>
        <Button
          size="sm"
          variant="outline"
          :start-icon="RefreshIcon"
          :disabled="loading"
          @click="load"
          >刷新记录</Button
        >
      </template>
    </PageBreadcrumb>
    <div
      v-if="loading"
      role="status"
      class="rounded-2xl border border-gray-200 bg-white p-12 text-center text-sm text-gray-500 dark:border-gray-800 dark:bg-gray-900 dark:text-gray-400"
    >
      正在加载复审记录…
    </div>
    <div v-else class="space-y-6">
      <Alert v-if="error" variant="error" title="读取或处理失败" :message="error" />
      <div
        v-if="!jobs.length"
        class="rounded-2xl border border-gray-200 bg-white px-6 py-16 text-center dark:border-gray-800 dark:bg-gray-900"
      >
        <div
          class="mx-auto flex h-12 w-12 items-center justify-center rounded-xl bg-brand-50 text-brand-500 dark:bg-brand-500/10 dark:text-brand-400"
        >
          <CheckIcon class="h-6 w-6" />
        </div>
        <h2 class="mt-5 text-base font-semibold text-gray-900 dark:text-white/90">
          复审结果会记录在这里
        </h2>
        <p class="mx-auto mt-2 max-w-sm text-sm leading-6 text-gray-500 dark:text-gray-400">
          资料完成抽取后，AI 会逐项核对原文并自动入库。你可以随时查看结果和判断依据。
        </p>
        <router-link
          to="/processing"
          class="mt-5 inline-flex min-h-9 items-center gap-1 rounded-lg px-3 text-sm font-medium text-brand-500 hover:bg-brand-50 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand-500 dark:text-brand-400 dark:hover:bg-brand-500/10"
          >查看处理进度 <ChevronRightIcon class="h-4 w-4 rtl:rotate-180"
        /></router-link>
      </div>

      <section
        v-if="jobs.length"
        aria-labelledby="review-overview-title"
        class="rounded-2xl border border-gray-200 bg-white shadow-theme-xs dark:border-gray-800 dark:bg-gray-900"
      >
        <div
          class="flex flex-col justify-between gap-4 border-b border-gray-100 p-5 sm:flex-row sm:items-center sm:px-6 dark:border-gray-800"
        >
          <div class="flex items-center gap-3">
            <div
              class="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-brand-50 text-brand-500 dark:bg-brand-500/10 dark:text-brand-400"
            >
              <CheckIcon class="h-5 w-5" />
            </div>
            <div>
              <h2
                id="review-overview-title"
                class="text-sm font-semibold text-gray-900 dark:text-white/90"
              >
                复审概览
              </h2>
              <p class="mt-1 text-xs text-gray-500 dark:text-gray-400">
                {{ jobs.length }} 份资料已有候选内容
              </p>
            </div>
          </div>
          <div class="w-full sm:w-80" role="group" aria-labelledby="review-job-label">
            <span id="review-job-label" class="sr-only">选择资料任务</span>
            <SelectInput v-model="selected" :options="jobOptions" placeholder="选择资料任务" />
          </div>
        </div>

        <template v-if="activeJob">
          <div class="p-5 sm:p-6">
            <div class="flex flex-col justify-between gap-4 sm:flex-row sm:items-start">
              <div class="min-w-0">
                <div
                  class="mb-2 flex flex-wrap items-center gap-2 text-xs text-gray-500 dark:text-gray-400"
                >
                  <span>资料任务 #{{ activeJob.id }}</span>
                  <Badge
                    :color="
                      activeJob.status === 'COMPLETED'
                        ? 'success'
                        : activeJob.status === 'FAILED'
                          ? 'error'
                          : 'info'
                    "
                    >{{ JOB_STATUS_LABELS[activeJob.status] }}</Badge
                  >
                </div>
                <h3
                  class="break-words text-base font-semibold leading-6 text-gray-900 dark:text-white/90"
                >
                  {{ activeJob.documentName }}
                </h3>
                <p
                  v-if="activeJob.status === 'AI_REVIEWING'"
                  role="status"
                  class="mt-2 text-sm leading-6 text-brand-500 dark:text-brand-400"
                >
                  正在复审第 {{ Math.min(activeJob.processedUnits + 1, activeJob.totalUnits) }} /
                  {{ activeJob.totalUnits }} 批，全部完成后自动入库。
                </p>
                <p
                  v-else-if="activeJob.status === 'COMPLETED'"
                  class="mt-2 text-sm leading-6 text-gray-500 dark:text-gray-400"
                >
                  通过的知识已归入对应主题库，可前往图谱探索关联。
                </p>
                <p v-else class="mt-2 text-sm leading-6 text-gray-500 dark:text-gray-400">
                  逐项对照原文，保留有依据的知识与关系。
                </p>
              </div>
              <div class="flex shrink-0 flex-wrap items-center gap-2">
                <Button
                  v-if="['AWAITING_REVIEW', 'FAILED', 'CANCELLED'].includes(activeJob.status)"
                  size="sm"
                  :disabled="busy"
                  @click="retry"
                  >{{ busy ? '正在启动…' : '继续 AI 复审' }}</Button
                >
                <router-link
                  v-if="activeJob.status === 'COMPLETED'"
                  to="/"
                  class="inline-flex min-h-9 items-center justify-center gap-1.5 rounded-lg bg-brand-500 px-3 py-2 text-xs font-medium text-white shadow-theme-xs transition-colors hover:bg-brand-600 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand-500"
                  >查看知识图谱 <ChevronRightIcon class="h-4 w-4 rtl:rotate-180"
                /></router-link>
              </div>
            </div>
            <p
              v-if="activeJob.errorMessage"
              class="mt-4 rounded-lg bg-error-50 px-3 py-2.5 text-sm leading-6 text-error-600 dark:bg-error-500/10 dark:text-error-400"
            >
              {{ activeJob.errorMessage }}
            </p>
            <div v-if="groups.length" class="mt-4 flex flex-wrap items-center gap-2">
              <span class="me-1 text-xs text-gray-500 dark:text-gray-400">归属知识库</span>
              <span
                v-for="group in groups"
                :key="group.libraryId"
                class="inline-flex items-center gap-1.5 rounded-md border border-gray-200 px-2 py-1 text-xs text-gray-600 dark:border-gray-700 dark:text-gray-300"
                ><FolderIcon class="h-3.5 w-3.5 text-gray-400 dark:text-gray-500" />{{
                  group.name
                }}</span
              >
            </div>
          </div>
          <dl
            class="grid grid-cols-2 gap-px overflow-hidden rounded-b-2xl border-t border-gray-100 bg-gray-100 sm:grid-cols-4 dark:border-gray-800 dark:bg-gray-800"
          >
            <div class="bg-white px-5 py-4 sm:px-6 dark:bg-gray-900">
              <dt class="text-xs text-gray-500 dark:text-gray-400">候选总数</dt>
              <dd
                class="mt-1.5 text-2xl font-semibold tabular-nums text-gray-900 dark:text-white/90"
              >
                {{ total
                }}<span class="ms-1.5 text-xs font-normal text-gray-400 dark:text-gray-500"
                  >项</span
                >
              </dd>
            </div>
            <div class="bg-white px-5 py-4 sm:px-6 dark:bg-gray-900">
              <dt class="flex items-center gap-1.5 text-xs text-gray-500 dark:text-gray-400">
                <span class="h-1.5 w-1.5 rounded-full bg-success-500"></span>已通过
              </dt>
              <dd
                class="mt-1.5 text-2xl font-semibold tabular-nums text-gray-900 dark:text-white/90"
              >
                {{ approved
                }}<span class="ms-1.5 text-xs font-normal text-gray-400 dark:text-gray-500"
                  >项</span
                >
              </dd>
            </div>
            <div class="bg-white px-5 py-4 sm:px-6 dark:bg-gray-900">
              <dt class="flex items-center gap-1.5 text-xs text-gray-500 dark:text-gray-400">
                <span class="h-1.5 w-1.5 rounded-full bg-warning-500"></span>已排除
              </dt>
              <dd
                class="mt-1.5 text-2xl font-semibold tabular-nums text-gray-900 dark:text-white/90"
              >
                {{ audits.length - approved
                }}<span class="ms-1.5 text-xs font-normal text-gray-400 dark:text-gray-500"
                  >项</span
                >
              </dd>
            </div>
            <div class="bg-white px-5 py-4 sm:px-6 dark:bg-gray-900">
              <dt class="flex items-center gap-1.5 text-xs text-gray-500 dark:text-gray-400">
                <span class="h-1.5 w-1.5 rounded-full bg-gray-300 dark:bg-gray-600"></span>待复审
              </dt>
              <dd
                class="mt-1.5 text-2xl font-semibold tabular-nums text-gray-900 dark:text-white/90"
              >
                {{ Math.max(total - audits.length, 0)
                }}<span class="ms-1.5 text-xs font-normal text-gray-400 dark:text-gray-500"
                  >项</span
                >
              </dd>
            </div>
          </dl>
        </template>
        <p v-else class="p-8 text-center text-sm text-gray-500 dark:text-gray-400">
          选择一份资料，查看它的复审结果。
        </p>
      </section>

      <template v-if="activeJob">
        <section
          aria-labelledby="review-details-title"
          class="rounded-2xl border border-gray-200 bg-white shadow-theme-xs dark:border-gray-800 dark:bg-gray-900"
        >
          <div class="flex flex-wrap items-center justify-between gap-2 px-5 pb-4 pt-5 sm:px-6">
            <h2
              id="review-details-title"
              class="text-sm font-semibold text-gray-900 dark:text-white/90"
            >
              复审明细
            </h2>
            <span class="text-xs text-gray-500 dark:text-gray-400"
              >AI 已复审 {{ audits.length }} / {{ total }} 项</span
            >
          </div>
          <div
            class="flex flex-col gap-3 border-b border-gray-200 px-5 pb-5 sm:px-6 xl:flex-row xl:items-center xl:justify-between dark:border-gray-800"
          >
            <div
              role="group"
              aria-label="内容类型"
              class="flex w-fit max-w-full shrink-0 gap-1 rounded-lg bg-gray-100 p-1 dark:bg-gray-800"
            >
              <button
                v-for="option in [
                  { label: '知识点', value: 'entity', count: entities.length },
                  { label: '关系', value: 'relation', count: relations.length },
                ]"
                :key="option.value"
                type="button"
                :aria-pressed="kind === option.value"
                class="inline-flex min-h-9 items-center gap-2 rounded-md px-3 text-sm font-medium transition-colors focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand-500"
                :class="
                  kind === option.value
                    ? 'bg-white text-gray-900 shadow-theme-xs dark:bg-gray-700 dark:text-white/90'
                    : 'text-gray-500 hover:text-gray-800 dark:text-gray-400 dark:hover:text-gray-200'
                "
                @click="kind = option.value"
              >
                {{ option.label
                }}<span
                  class="text-xs tabular-nums"
                  :class="
                    kind === option.value
                      ? 'text-brand-500 dark:text-brand-400'
                      : 'text-gray-400 dark:text-gray-500'
                  "
                  >{{ option.count }}</span
                >
              </button>
            </div>
            <div class="flex min-w-0 flex-1 flex-col gap-3 sm:flex-row xl:max-w-lg">
              <div class="shrink-0 sm:w-36" role="group" aria-labelledby="review-result-label">
                <span id="review-result-label" class="sr-only">复审结果</span>
                <SelectInput
                  v-model="result"
                  :options="[
                    { label: '全部结果', value: 'all' },
                    { label: '已通过', value: 'approved' },
                    { label: '已排除', value: 'rejected' },
                    { label: '待复审', value: 'pending' },
                  ]"
                />
              </div>
              <label class="relative block min-w-0 flex-1">
                <span class="sr-only">搜索候选</span>
                <svg
                  class="pointer-events-none absolute start-3.5 top-3.5 h-4 w-4 text-gray-400 dark:text-gray-500"
                  viewBox="0 0 20 20"
                  fill="none"
                  aria-hidden="true"
                >
                  <circle cx="8.75" cy="8.75" r="5.75" stroke="currentColor" stroke-width="1.5" />
                  <path
                    d="m13 13 4 4"
                    stroke="currentColor"
                    stroke-width="1.5"
                    stroke-linecap="round"
                  />
                </svg>
                <input
                  v-model="search"
                  type="search"
                  placeholder="搜索名称…"
                  class="h-11 w-full rounded-lg border border-gray-300 bg-white pe-3 ps-10 text-sm text-gray-800 shadow-theme-xs placeholder:text-gray-400 focus:border-brand-300 focus:outline-none focus:ring-3 focus:ring-brand-500/10 dark:border-gray-700 dark:bg-gray-900 dark:text-white/90 dark:placeholder:text-gray-500"
                />
              </label>
            </div>
          </div>

          <p
            v-if="detailsLoading"
            role="status"
            class="p-10 text-center text-sm text-gray-500 dark:text-gray-400"
          >
            正在读取候选…
          </p>
          <template v-else>
            <div class="divide-y divide-gray-100 dark:divide-gray-800">
              <article v-for="row in visible" :key="row.id" class="px-5 py-5 sm:px-6">
                <div class="grid gap-4 lg:grid-cols-[minmax(0,1fr)_minmax(250px,0.75fr)] lg:gap-8">
                  <div class="min-w-0">
                    <h3
                      class="break-words text-sm font-semibold leading-6 text-gray-900 dark:text-white/90"
                    >
                      {{ row.title }}
                    </h3>
                    <p
                      v-if="row.definition"
                      class="mt-1.5 break-words text-sm leading-6 text-gray-500 dark:text-gray-400"
                    >
                      {{ row.definition }}
                    </p>
                  </div>
                  <div class="min-w-0 rounded-lg bg-gray-50 px-3.5 py-3 dark:bg-gray-800/60">
                    <div class="flex items-center justify-between gap-2">
                      <span class="text-xs font-medium text-gray-500 dark:text-gray-400"
                        >AI 复审依据</span
                      >
                      <Badge
                        :color="row.audit ? (row.audit.approved ? 'success' : 'warning') : 'light'"
                        >{{
                          row.audit ? (row.audit.approved ? '已通过' : '已排除') : '待复审'
                        }}</Badge
                      >
                    </div>
                    <p class="mt-2 break-words text-sm leading-6 text-gray-600 dark:text-gray-300">
                      {{ row.audit ? row.audit.reason : 'AI 尚未完成此项复审，结果会自动更新。' }}
                    </p>
                  </div>
                </div>
                <details class="group mt-3 text-xs text-gray-500 dark:text-gray-400">
                  <summary
                    class="flex w-fit cursor-pointer list-none items-center gap-1.5 rounded-md py-1.5 text-gray-500 transition-colors hover:text-brand-500 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand-500 dark:text-gray-400 dark:hover:text-brand-400 [&::-webkit-details-marker]:hidden"
                  >
                    <DocsIcon class="h-3.5 w-3.5" />原文证据<span class="tabular-nums">{{
                      row.evidence.length
                    }}</span
                    ><ChevronDownIcon
                      class="h-3.5 w-3.5 transition-transform group-open:rotate-180"
                    />
                  </summary>
                  <div
                    class="mt-2 space-y-3 rounded-lg border border-gray-100 bg-gray-50 p-4 dark:border-gray-800 dark:bg-gray-800/40"
                  >
                    <div v-for="e in row.evidence" :key="e.chunkId">
                      <p
                        class="[overflow-wrap:anywhere] font-medium text-gray-600 dark:text-gray-300"
                      >
                        {{ e.documentName
                        }}<span class="mx-1.5 text-gray-300 dark:text-gray-600">·</span
                        >{{ e.locator }}
                      </p>
                      <blockquote
                        class="mt-2 border-s-2 border-gray-200 ps-3 text-sm leading-6 text-gray-500 [overflow-wrap:anywhere] dark:border-gray-700 dark:text-gray-400"
                      >
                        {{ e.excerpt }}
                      </blockquote>
                    </div>
                    <p v-if="!row.evidence.length">这项候选尚无可查看的原文证据。</p>
                  </div>
                </details>
              </article>
            </div>
            <div v-if="!visible.length" class="px-6 py-12 text-center">
              <p class="text-sm font-medium text-gray-700 dark:text-gray-300">没有符合条件的候选</p>
              <p class="mt-2 text-sm text-gray-500 dark:text-gray-400">
                试试其他结果分类，或缩短搜索关键词。
              </p>
              <Button
                v-if="search || result !== 'all'"
                class="mt-4"
                size="sm"
                variant="outline"
                @click="clearFilters"
                >清除筛选</Button
              >
            </div>
            <div
              v-if="filtered.length"
              class="flex flex-wrap items-center justify-between gap-3 border-t border-gray-100 px-5 py-4 sm:px-6 dark:border-gray-800"
            >
              <span class="text-xs tabular-nums text-gray-500 dark:text-gray-400"
                >显示 {{ (page - 1) * pageSize + 1 }}–{{
                  Math.min(page * pageSize, filtered.length)
                }}
                项，共 {{ filtered.length }} 项</span
              >
              <div v-if="filtered.length > pageSize" class="flex items-center gap-3">
                <Button size="sm" variant="outline" :disabled="page === 1" @click="page--"
                  >上一页</Button
                >
                <span class="text-xs tabular-nums text-gray-500 dark:text-gray-400"
                  >{{ page }} / {{ Math.ceil(filtered.length / pageSize) }}</span
                >
                <Button
                  size="sm"
                  variant="outline"
                  :disabled="page * pageSize >= filtered.length"
                  @click="page++"
                  >下一页</Button
                >
              </div>
            </div>
          </template>
        </section>
      </template>
    </div>
  </AdminLayout>
</template>
<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import AdminLayout from '@/components/layout/AdminLayout.vue'
import PageBreadcrumb from '@/components/common/PageBreadcrumb.vue'
import SelectInput from '@/components/ui/form/SelectInput.vue'
import Alert from '@/components/ui/Alert.vue'
import Button from '@/components/ui/Button.vue'
import Badge from '@/components/ui/Badge.vue'
import {
  CheckIcon,
  ChevronDownIcon,
  ChevronRightIcon,
  DocsIcon,
  FolderIcon,
  RefreshIcon,
} from '@/icons'
import { ingestionApi } from '@/services/ingestionApi'
import {
  reviewApi,
  type AiReviewAudit,
  type EntityCandidate,
  type RelationCandidate,
} from '@/services/reviewApi'
import { JOB_STATUS_LABELS, JOB_ACTIVE_STATUSES, type ProcessingJobDto } from '@/services/types'
const jobs = ref<ProcessingJobDto[]>([]),
  selected = ref(''),
  loading = ref(true),
  error = ref(''),
  busy = ref(false),
  detailsLoading = ref(false)
const entities = ref<EntityCandidate[]>([]),
  relations = ref<RelationCandidate[]>([]),
  audits = ref<AiReviewAudit[]>([])
const groups = ref<{ libraryId: number; name: string; nodeCount: number }[]>([])
const kind = ref('entity'),
  result = ref('all'),
  search = ref(''),
  page = ref(1),
  pageSize = 30
const activeJob = computed(() => jobs.value.find((j) => String(j.id) === selected.value))
const jobOptions = computed(() =>
  jobs.value.map((j) => ({ value: String(j.id), label: `#${j.id} · ${j.documentName}` })),
)
const total = computed(() => entities.value.length + relations.value.length)
const approved = computed(() => audits.value.filter((a) => a.approved).length)
const byId = computed(() => new Map(audits.value.map((a) => [a.id, a])))
const rows = computed(() =>
  kind.value === 'entity'
    ? entities.value.map((e) => ({
        id: `entity:${e.id}`,
        title: e.name,
        definition: e.definition,
        evidence: e.evidence,
        audit: byId.value.get(`entity:${e.id}`),
      }))
    : relations.value.map((r) => ({
        id: `relation:${r.id}`,
        title: `${r.sourceName} → ${r.relationType} → ${r.targetName}`,
        definition: '',
        evidence: r.evidence,
        audit: byId.value.get(`relation:${r.id}`),
      })),
)
const filtered = computed(() =>
  rows.value.filter(
    (r) =>
      r.title.toLowerCase().includes(search.value.toLowerCase()) &&
      (result.value === 'all' ||
        (result.value === 'pending'
          ? !r.audit
          : r.audit && r.audit.approved === (result.value === 'approved'))),
  ),
)
const visible = computed(() =>
  filtered.value.slice((page.value - 1) * pageSize, page.value * pageSize),
)
watch([kind, result, search, selected], () => {
  page.value = 1
})
function clearFilters() {
  search.value = ''
  result.value = 'all'
}
let timer: ReturnType<typeof setTimeout> | undefined,
  disposed = false,
  revision = 0
async function load() {
  try {
    const found: ProcessingJobDto[] = []
    let p = 1,
      pages = 1
    do {
      const data = await ingestionApi.listJobs(undefined, p)
      pages = Math.ceil(data.total / data.pageSize)
      found.push(...data.items.filter((j) => (j.candidateCount ?? 0) > 0))
      p++
    } while (p <= pages)
    jobs.value = found
    if (!found.some((j) => String(j.id) === selected.value))
      selected.value = found[0] ? String(found[0].id) : ''
    error.value = ''
    if (selected.value) await loadDetails()
  } catch (e) {
    error.value = e instanceof Error ? e.message : '读取失败'
  } finally {
    loading.value = false
    clearTimeout(timer)
    if (!disposed && jobs.value.some((j) => JOB_ACTIVE_STATUSES.includes(j.status)))
      timer = setTimeout(load, 4000)
  }
}
async function loadDetails() {
  const id = Number(selected.value),
    ticket = ++revision
  if (!id) return
  detailsLoading.value = !entities.value.length
  try {
    const [e, r, a, g] = await Promise.all([
      reviewApi.listEntities(id),
      reviewApi.listRelations(id),
      reviewApi.aiDecisions(id),
      ingestionApi.organization(id),
    ])
    if (ticket !== revision) return
    entities.value = e
    relations.value = r
    audits.value = a
    groups.value = g
  } catch (e) {
    if (ticket === revision) error.value = e instanceof Error ? e.message : '读取候选失败'
  } finally {
    if (ticket === revision) detailsLoading.value = false
  }
}
watch(selected, () => {
  entities.value = []
  relations.value = []
  audits.value = []
  groups.value = []
  void loadDetails()
})
async function retry() {
  if (!activeJob.value) return
  busy.value = true
  try {
    await ingestionApi.retry(activeJob.value.id)
    await load()
  } catch (e) {
    error.value = e instanceof Error ? e.message : '启动失败'
  } finally {
    busy.value = false
  }
}
onMounted(load)
onUnmounted(() => {
  disposed = true
  revision++
  clearTimeout(timer)
})
</script>
