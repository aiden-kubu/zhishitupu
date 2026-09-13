<template>
  <AdminLayout>
    <PageBreadcrumb page-title="数据洞察" />

    <div v-if="loading" class="rounded-2xl border border-gray-200 p-10 text-center dark:border-gray-800">
      <p class="text-sm text-gray-500 dark:text-gray-400">统计数据加载中…</p>
    </div>

    <div v-else-if="error" class="space-y-3">
      <Alert variant="error" title="加载失败" :message="error" />
      <Button size="sm" variant="outline" @click="load">重试</Button>
    </div>

    <template v-else-if="summary">
      <!-- KPI（§9.4） -->
      <div class="grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <div
          v-for="stat in kpis"
          :key="stat.label"
          class="rounded-2xl border border-gray-200 bg-white px-5 py-4 dark:border-gray-800 dark:bg-white/[0.03]"
        >
          <p class="text-sm text-gray-500 dark:text-gray-400">{{ stat.label }}</p>
          <p class="mt-1 text-title-lg font-bold text-gray-800 dark:text-white/90">{{ stat.value }}</p>
        </div>
      </div>

      <!-- 图表：类型分布 + 关联度排行（使用模板 ApexCharts，不引入新图表库） -->
      <div class="mt-5 grid grid-cols-1 gap-5 xl:grid-cols-2">
        <div class="rounded-2xl border border-gray-200 bg-white p-5 dark:border-gray-800 dark:bg-white/[0.03]">
          <h3 class="mb-3 text-base font-medium text-gray-800 dark:text-white/90">节点类型分布</h3>
          <VueApexCharts
            v-if="isMounted && typeChartSeries.length > 0"
            type="donut"
            height="300"
            :options="typeChartOptions"
            :series="typeChartSeries"
          />
          <p v-else class="py-10 text-center text-sm text-gray-400">暂无节点数据</p>
        </div>

        <div class="rounded-2xl border border-gray-200 bg-white p-5 dark:border-gray-800 dark:bg-white/[0.03]">
          <h3 class="mb-3 text-base font-medium text-gray-800 dark:text-white/90">关联度排行 Top 10</h3>
          <VueApexCharts
            v-if="isMounted && degreeChartSeries.length > 0"
            type="bar"
            height="300"
            :options="degreeChartOptions"
            :series="degreeChartSeries"
          />
          <p v-else class="py-10 text-center text-sm text-gray-400">暂无关系数据</p>
        </div>
      </div>

      <!-- 质量清单 -->
      <div class="mt-5 grid grid-cols-1 gap-5 xl:grid-cols-2">
        <div class="rounded-2xl border border-gray-200 bg-white p-5 dark:border-gray-800 dark:bg-white/[0.03]">
          <h3 class="mb-3 text-base font-medium text-gray-800 dark:text-white/90">
            孤立节点（{{ summary.isolatedNodes.length }}）
          </h3>
          <p v-if="summary.isolatedNodes.length === 0" class="py-6 text-center text-sm text-gray-400">
            没有孤立节点，图谱连通性良好
          </p>
          <div v-else class="flex flex-wrap gap-2">
            <router-link
              v-for="node in summary.isolatedNodes.slice(0, 30)"
              :key="node.id"
              :to="{ path: '/', query: { node: String(node.id), depth: '1', mode: 'local' } }"
              class="rounded-full border border-gray-200 px-3 py-1 text-sm text-gray-700 transition hover:border-brand-300 hover:text-brand-500 dark:border-gray-700 dark:text-gray-300"
            >
              {{ node.name }}
            </router-link>
          </div>
        </div>

        <div class="rounded-2xl border border-gray-200 bg-white p-5 dark:border-gray-800 dark:bg-white/[0.03]">
          <h3 class="mb-3 text-base font-medium text-gray-800 dark:text-white/90">处理任务指标</h3>
          <ul class="space-y-3 text-sm text-gray-600 dark:text-gray-300">
            <li class="flex justify-between">
              <span>待审核候选</span>
              <span class="font-medium text-gray-800 dark:text-white/90">{{ summary.pendingReviewCount }}</span>
            </li>
            <li class="flex justify-between">
              <span>任务成功率 / 平均耗时</span>
              <span class="text-gray-400">随阶段 D 处理流水线交付</span>
            </li>
            <li class="flex justify-between">
              <span>无来源节点</span>
              <span class="text-gray-400">随阶段 D 证据链交付</span>
            </li>
          </ul>
        </div>
      </div>
    </template>
  </AdminLayout>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import type { ApexOptions } from 'apexcharts'
import AdminLayout from '@/components/layout/AdminLayout.vue'
import PageBreadcrumb from '@/components/common/PageBreadcrumb.vue'
import Alert from '@/components/ui/Alert.vue'
import Button from '@/components/ui/Button.vue'
import VueApexCharts from 'vue3-apexcharts'
import { insightsApi } from '@/services/insightsApi'
import { ApiError } from '@/services/http'
import { NODE_TYPE_LABELS } from '@/services/types'
import type { InsightSummaryDto } from '@/services/types'

const summary = ref<InsightSummaryDto | null>(null)
const loading = ref(true)
const error = ref<string | null>(null)
const isMounted = ref(false)

const kpis = computed(() => [
  { label: '知识节点', value: summary.value?.nodeCount ?? 0 },
  { label: '知识关系', value: summary.value?.edgeCount ?? 0 },
  { label: '来源资料', value: summary.value?.documentCount ?? 0 },
  { label: '待审核', value: summary.value?.pendingReviewCount ?? 0 },
])

const typeChartSeries = computed(() =>
  (summary.value?.nodeTypeDistribution ?? []).map((item) => item.count),
)

const typeChartLabels = computed(() =>
  (summary.value?.nodeTypeDistribution ?? []).map(
    (item) => NODE_TYPE_LABELS[item.type] ?? item.type,
  ),
)

const typeChartOptions = computed<ApexOptions>(() => ({
  chart: { type: 'donut' },
  labels: typeChartLabels.value,
  legend: { position: 'bottom' },
  dataLabels: { enabled: false },
  stroke: { width: 0 },
}))

const degreeChartSeries = computed(() => [
  {
    name: '直接关联数',
    data: (summary.value?.topDegreeNodes ?? []).map((node) => node.degree),
  },
])

const degreeChartOptions = computed<ApexOptions>(() => ({
  chart: { type: 'bar' },
  plotOptions: { bar: { horizontal: true, barHeight: '60%' } },
  xaxis: {
    categories: (summary.value?.topDegreeNodes ?? []).map((node) => node.name),
  },
  dataLabels: { enabled: false },
  legend: { show: false },
}))

async function load() {
  loading.value = true
  error.value = null
  try {
    summary.value = await insightsApi.getSummary()
  } catch (err) {
    error.value = err instanceof ApiError ? err.message : '统计数据加载失败'
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  isMounted.value = true
  void load()
})
</script>
