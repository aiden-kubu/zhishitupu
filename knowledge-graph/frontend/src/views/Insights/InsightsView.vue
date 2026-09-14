<template>
  <AdminLayout>
    <PageBreadcrumb
      page-title="数据洞察"
      description="了解知识积累与关联情况，找到下一步值得探索的内容。"
    >
      <template #actions>
        <Button
          size="sm"
          variant="outline"
          :start-icon="RefreshIcon"
          :disabled="loading"
          @click="load"
        >
          {{ loading ? '正在刷新' : '刷新数据' }}
        </Button>
      </template>
    </PageBreadcrumb>

    <div
      v-if="loading && !summary"
      class="rounded-2xl border border-gray-200 bg-white p-6 dark:border-gray-800 dark:bg-white/[0.03]"
      role="status"
    >
      <p class="text-sm text-gray-500 dark:text-gray-400">正在汇总知识数据…</p>
      <div
        class="mt-5 grid animate-pulse grid-cols-2 gap-5 motion-reduce:animate-none sm:grid-cols-4"
        aria-hidden="true"
      >
        <div v-for="index in 4" :key="index" class="space-y-3">
          <div class="h-3 w-16 rounded bg-gray-100 dark:bg-gray-800"></div>
          <div class="h-7 w-20 rounded bg-gray-100 dark:bg-gray-800"></div>
        </div>
      </div>
    </div>

    <Alert v-if="error" class="mb-5" variant="error" title="统计暂时无法更新" :message="error" />

    <div v-if="summary" class="space-y-6" :aria-busy="loading">
      <section
        class="overflow-hidden rounded-2xl border border-gray-200 bg-white dark:border-gray-800 dark:bg-white/[0.03]"
        aria-labelledby="insights-overview-title"
      >
        <div
          class="flex flex-wrap items-center justify-between gap-3 border-b border-gray-100 bg-brand-25/60 px-5 py-4 dark:border-gray-800 dark:bg-brand-500/5 sm:px-6"
        >
          <div class="flex items-center gap-3">
            <span
              class="flex size-10 items-center justify-center rounded-xl bg-brand-500 text-white"
              ><PieChartIcon class="size-5" aria-hidden="true"
            /></span>
            <div>
              <h2
                id="insights-overview-title"
                class="text-sm font-semibold text-gray-900 dark:text-white/90"
              >
                知识资产总览
              </h2>
              <p class="mt-1 text-xs text-gray-500 dark:text-gray-400">
                知识与关系按已入库内容统计
              </p>
            </div>
          </div>
          <span v-if="updatedAt" class="text-xs text-gray-500 dark:text-gray-400"
            >更新于 {{ updatedAt }}</span
          >
        </div>
        <dl class="grid grid-cols-2 sm:grid-cols-4">
          <div
            v-for="(stat, index) in kpis"
            :key="stat.label"
            :class="[
              'px-5 py-5 sm:px-6',
              index % 2 === 1 ? 'border-s border-gray-100 dark:border-gray-800' : '',
              index > 1
                ? 'border-t border-gray-100 dark:border-gray-800 sm:border-t-0 sm:border-s'
                : '',
            ]"
          >
            <dt class="flex items-center gap-2 text-xs text-gray-500 dark:text-gray-400">
              <component :is="stat.icon" class="size-4" aria-hidden="true" />{{ stat.label }}
            </dt>
            <dd class="mt-2 flex items-baseline gap-1.5">
              <span
                class="text-2xl font-semibold tabular-nums tracking-tight text-gray-900 dark:text-white/90"
                >{{ formatNumber(stat.value) }}</span
              ><span class="text-xs text-gray-400 dark:text-gray-500">{{ stat.unit }}</span>
            </dd>
          </div>
        </dl>
      </section>

      <section
        v-if="summary.nodeCount === 0"
        class="rounded-2xl border border-gray-200 bg-white px-6 py-10 dark:border-gray-800 dark:bg-white/[0.03] sm:px-10"
        aria-labelledby="insights-empty-title"
      >
        <div class="mx-auto flex max-w-xl flex-col items-center text-center">
          <span
            class="mb-5 flex size-14 items-center justify-center rounded-2xl bg-gray-50 text-brand-500 ring-1 ring-gray-100 dark:bg-gray-800 dark:text-brand-400 dark:ring-gray-700"
            ><BoxCubeIcon class="size-7" aria-hidden="true"
          /></span>
          <h2
            id="insights-empty-title"
            class="text-lg font-semibold text-gray-900 dark:text-white/90"
          >
            图谱里还没有知识
          </h2>
          <p class="mt-2 text-sm leading-6 text-gray-500 dark:text-gray-400">
            {{
              summary.documentCount > 0
                ? '资料上传后，AI 会识别内容、按主题整理并复审。通过的知识入库后，这里会展示类型与关联分布。'
                : '通过顶部「导入资料」开始积累知识。AI 会识别、整理并复审内容，入库后即可查看统计。'
            }}
          </p>
          <router-link
            v-if="summary.documentCount > 0"
            to="/processing"
            class="mt-5 inline-flex h-10 items-center justify-center gap-2 rounded-lg bg-brand-500 px-4 text-sm font-medium text-white shadow-theme-xs transition hover:bg-brand-600 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand-500"
            >查看资料处理进度<ChevronRightIcon class="size-4 rtl:rotate-180" aria-hidden="true"
          /></router-link>
        </div>
      </section>

      <template v-else>
        <div class="grid grid-cols-1 items-start gap-6 xl:grid-cols-2">
          <div class="min-w-0 space-y-6">
            <section
              class="overflow-hidden rounded-2xl border border-gray-200 bg-white dark:border-gray-800 dark:bg-white/[0.03]"
              aria-labelledby="insights-types-title"
            >
              <div class="border-b border-gray-100 px-5 py-4 dark:border-gray-800 sm:px-6">
                <h2
                  id="insights-types-title"
                  class="text-sm font-semibold text-gray-900 dark:text-white/90"
                >
                  知识内容结构
                </h2>
                <p class="mt-1 text-xs text-gray-500 dark:text-gray-400">
                  按类型查看已积累的知识，各类型占比一目了然
                </p>
              </div>
              <div class="space-y-5 p-5 sm:p-6">
                <div v-for="item in typeDistribution" :key="item.type">
                  <div class="mb-2 flex items-center justify-between gap-3 text-sm">
                    <span class="font-medium text-gray-700 dark:text-gray-300">{{
                      item.label
                    }}</span>
                    <span class="flex items-baseline gap-3 tabular-nums"
                      ><span class="text-gray-800 dark:text-white/90">{{
                        formatNumber(item.count)
                      }}</span
                      ><span class="w-12 text-end text-xs text-gray-500 dark:text-gray-400"
                        >{{ item.percentage }}%</span
                      ></span
                    >
                  </div>
                  <div
                    class="h-2 overflow-hidden rounded-full bg-gray-100 dark:bg-gray-800"
                    aria-hidden="true"
                  >
                    <div
                      class="h-full rounded-full bg-brand-500 dark:bg-brand-400"
                      :style="{ width: `${item.percentage}%` }"
                    ></div>
                  </div>
                </div>
                <p v-if="!typeDistribution.length" class="text-sm text-gray-500 dark:text-gray-400">
                  暂时没有可展示的类型分布。
                </p>
              </div>
            </section>
            <section
              class="rounded-2xl border border-gray-200 bg-white dark:border-gray-800 dark:bg-white/[0.03]"
              aria-labelledby="insights-isolated-title"
            >
              <div
                class="flex flex-wrap items-start justify-between gap-3 border-b border-gray-100 px-5 py-4 dark:border-gray-800 sm:px-6"
              >
                <div>
                  <h2
                    id="insights-isolated-title"
                    class="text-sm font-semibold text-gray-900 dark:text-white/90"
                  >
                    尚未建立关联的知识
                  </h2>
                  <p class="mt-1 text-xs text-gray-500 dark:text-gray-400">
                    这些知识点暂时独立存在，可以从这里继续探索
                  </p>
                </div>
                <span class="text-xs tabular-nums text-gray-500 dark:text-gray-400"
                  >{{
                    summary.isolatedNodes.length === 100 ? '至少 100' : summary.isolatedNodes.length
                  }}
                  个</span
                >
              </div>
              <div class="p-5 sm:p-6">
                <div v-if="summary.isolatedNodes.length" class="flex flex-wrap gap-2">
                  <router-link
                    v-for="node in visibleIsolatedNodes"
                    :key="node.id"
                    :to="graphLink(node.id)"
                    class="inline-flex min-h-9 max-w-full items-center gap-2 rounded-lg border border-gray-200 bg-white px-3 py-1.5 text-sm text-gray-600 transition hover:border-brand-300 hover:bg-brand-25 hover:text-brand-600 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand-500 dark:border-gray-700 dark:bg-gray-900 dark:text-gray-300 dark:hover:border-brand-500/40 dark:hover:bg-brand-500/10 dark:hover:text-brand-400"
                    ><span class="truncate">{{ node.name }}</span
                    ><ChevronRightIcon class="size-3.5 shrink-0 rtl:rotate-180" aria-hidden="true"
                  /></router-link>
                  <button
                    v-if="summary.isolatedNodes.length > 12"
                    class="inline-flex min-h-9 items-center rounded-lg px-3 py-1.5 text-sm font-medium text-brand-600 hover:bg-brand-50 focus-visible:outline-2 focus-visible:outline-brand-500 dark:text-brand-400 dark:hover:bg-brand-500/10"
                    :aria-expanded="showAllIsolated"
                    @click="showAllIsolated = !showAllIsolated"
                  >
                    {{
                      showAllIsolated ? '收起' : `查看其余 ${summary.isolatedNodes.length - 12} 个`
                    }}
                  </button>
                </div>
                <p v-else class="text-sm text-gray-500 dark:text-gray-400">
                  当前知识点均已建立关联。
                </p>
                <p
                  v-if="summary.isolatedNodes.length === 100"
                  class="mt-3 text-xs text-gray-400 dark:text-gray-500"
                >
                  本页最多展示 100 个未关联的知识点。
                </p>
              </div>
            </section>
          </div>
          <section
            class="overflow-hidden rounded-2xl border border-gray-200 bg-white dark:border-gray-800 dark:bg-white/[0.03]"
            aria-labelledby="insights-rank-title"
          >
            <div
              class="flex items-center justify-between gap-3 border-b border-gray-100 px-5 py-4 dark:border-gray-800 sm:px-6"
            >
              <div>
                <h2
                  id="insights-rank-title"
                  class="text-sm font-semibold text-gray-900 dark:text-white/90"
                >
                  联系最丰富的知识
                </h2>
                <p class="mt-1 text-xs text-gray-500 dark:text-gray-400">
                  按直接关联数排序，点击即可在图谱中探索
                </p>
              </div>
              <span
                class="shrink-0 rounded-md bg-gray-100 px-2 py-1 text-xs font-medium text-gray-500 dark:bg-gray-800 dark:text-gray-400"
                >前 10 名</span
              >
            </div>
            <ol
              v-if="rankedNodes.length"
              class="divide-y divide-gray-100 px-5 dark:divide-gray-800 sm:px-6"
            >
              <li v-for="(node, index) in rankedNodes" :key="node.id">
                <router-link
                  :to="graphLink(node.id)"
                  class="group flex items-center gap-3 rounded-lg py-3 transition focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand-500"
                >
                  <span
                    :class="[
                      'flex size-6 shrink-0 items-center justify-center rounded-md text-xs font-medium tabular-nums',
                      index < 3
                        ? 'bg-brand-50 text-brand-600 dark:bg-brand-500/10 dark:text-brand-400'
                        : 'bg-gray-50 text-gray-500 dark:bg-gray-800 dark:text-gray-400',
                    ]"
                    >{{ index + 1 }}</span
                  >
                  <div class="min-w-0 flex-1">
                    <div class="flex items-center justify-between gap-3 text-sm">
                      <span
                        class="truncate font-medium text-gray-700 transition group-hover:text-brand-600 dark:text-gray-300 dark:group-hover:text-brand-400"
                        >{{ node.name }}</span
                      ><span class="shrink-0 text-xs tabular-nums text-gray-500 dark:text-gray-400"
                        >{{ formatNumber(node.degree) }} 个关联</span
                      >
                    </div>
                    <div
                      class="mt-2 h-1 overflow-hidden rounded-full bg-gray-100 dark:bg-gray-800"
                      aria-hidden="true"
                    >
                      <div
                        class="h-full rounded-full bg-brand-300 dark:bg-brand-500/60"
                        :style="{ width: `${node.shareOfTop}%` }"
                      ></div>
                    </div>
                  </div>
                  <ChevronRightIcon
                    class="size-4 shrink-0 text-gray-400 transition group-hover:text-brand-500 rtl:rotate-180"
                    aria-hidden="true"
                  />
                </router-link>
              </li>
            </ol>
            <div v-else class="px-6 py-8 text-center">
              <p class="text-sm font-medium text-gray-700 dark:text-gray-300">
                知识之间还没有建立关联
              </p>
              <p class="mt-2 text-xs leading-5 text-gray-500 dark:text-gray-400">
                继续积累相关主题的资料，AI 会从内容中提取知识关系。
              </p>
            </div>
          </section>
        </div>
      </template>

      <section
        class="flex flex-col justify-between gap-4 rounded-2xl border border-gray-200 bg-white p-5 dark:border-gray-800 dark:bg-white/[0.03] sm:flex-row sm:items-center sm:px-6"
        aria-labelledby="insights-review-title"
      >
        <div class="flex items-start gap-3">
          <span
            class="flex size-10 shrink-0 items-center justify-center rounded-xl bg-gray-50 text-gray-500 dark:bg-gray-800 dark:text-gray-400"
            ><CheckIcon class="size-5" aria-hidden="true"
          /></span>
          <div>
            <h2
              id="insights-review-title"
              class="text-sm font-semibold text-gray-900 dark:text-white/90"
            >
              {{
                summary.pendingReviewCount > 0
                  ? `${formatNumber(summary.pendingReviewCount)} 项候选等待 AI 复审`
                  : '当前没有待复审候选'
              }}
            </h2>
            <p class="mt-1 text-xs leading-5 text-gray-500 dark:text-gray-400">
              AI 对照原文检查知识与关系，通过后自动入库；识别中的内容尚未计入图谱。
            </p>
          </div>
        </div>
        <router-link
          to="/review"
          class="inline-flex h-9 shrink-0 items-center justify-center gap-2 rounded-lg border border-gray-200 bg-white px-3.5 text-sm font-medium text-gray-700 transition hover:bg-gray-50 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand-500 dark:border-gray-700 dark:bg-gray-900 dark:text-gray-300 dark:hover:bg-gray-800"
          >查看复审记录<ChevronRightIcon class="size-4 rtl:rotate-180" aria-hidden="true"
        /></router-link>
      </section>
    </div>
  </AdminLayout>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import AdminLayout from '@/components/layout/AdminLayout.vue'
import PageBreadcrumb from '@/components/common/PageBreadcrumb.vue'
import Alert from '@/components/ui/Alert.vue'
import Button from '@/components/ui/Button.vue'
import {
  BoxCubeIcon,
  CheckIcon,
  ChevronRightIcon,
  DocsIcon,
  PieChartIcon,
  PlugInIcon,
  RefreshIcon,
} from '@/icons'
import { insightsApi } from '@/services/insightsApi'
import { ApiError } from '@/services/http'
import { NODE_TYPE_LABELS } from '@/services/types'
import type { InsightSummaryDto } from '@/services/types'

const summary = ref<InsightSummaryDto | null>(null)
const loading = ref(true)
const error = ref<string | null>(null)
const updatedAt = ref('')
const showAllIsolated = ref(false)

const formatNumber = (value: number) => new Intl.NumberFormat('zh-CN').format(value)
const graphLink = (id: number) => ({
  path: '/',
  // 定位具体节点固定聚焦一层（GR02）
  query: { node: String(id), depth: '1', mode: 'focus' },
})
const kpis = computed(() => [
  { label: '知识节点', value: summary.value?.nodeCount ?? 0, unit: '个', icon: BoxCubeIcon },
  { label: '知识关系', value: summary.value?.edgeCount ?? 0, unit: '条', icon: PlugInIcon },
  { label: '已上传资料', value: summary.value?.documentCount ?? 0, unit: '份', icon: DocsIcon },
  {
    label: '待 AI 复审候选',
    value: summary.value?.pendingReviewCount ?? 0,
    unit: '项',
    icon: CheckIcon,
  },
])
const typeDistribution = computed(() =>
  (summary.value?.nodeTypeDistribution ?? [])
    .filter((item) => item.count > 0)
    .map((item) => ({
      ...item,
      label: NODE_TYPE_LABELS[item.type] ?? item.type,
      percentage: Number(
        ((item.count / Math.max(1, summary.value?.nodeCount ?? 0)) * 100).toFixed(1),
      ),
    })),
)
const rankedNodes = computed(() => {
  const nodes = (summary.value?.topDegreeNodes ?? []).filter((node) => node.degree > 0)
  const maximum = Math.max(1, ...nodes.map((node) => node.degree))
  return nodes.map((node) => ({ ...node, shareOfTop: (node.degree / maximum) * 100 }))
})
const visibleIsolatedNodes = computed(() =>
  (summary.value?.isolatedNodes ?? []).slice(0, showAllIsolated.value ? 100 : 12),
)

async function load() {
  loading.value = true
  error.value = null
  try {
    summary.value = await insightsApi.getSummary()
    updatedAt.value = new Intl.DateTimeFormat('zh-CN', {
      hour: '2-digit',
      minute: '2-digit',
    }).format(new Date())
  } catch (err) {
    error.value = err instanceof ApiError ? err.message : '统计数据加载失败，请稍后刷新重试。'
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  void load()
})
</script>
