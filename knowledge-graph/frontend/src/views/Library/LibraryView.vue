<template>
  <AdminLayout>
    <PageBreadcrumb page-title="知识库" />

    <!-- 顶部统计（§9.1） -->
    <div class="grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-4">
      <div
        v-for="stat in statCards"
        :key="stat.label"
        class="rounded-2xl border border-gray-200 bg-white px-5 py-4 dark:border-gray-800 dark:bg-white/[0.03]"
      >
        <p class="text-sm text-gray-500 dark:text-gray-400">{{ stat.label }}</p>
        <p class="mt-1 text-title-lg font-bold text-gray-800 dark:text-white/90">
          {{ stat.value }}
        </p>
      </div>
    </div>

    <!-- 工具行 -->
    <div class="mt-6 flex flex-wrap items-center justify-between gap-3">
      <div class="w-56">
        <SelectInput
          v-model="typeFilter"
          :options="[
            { value: '', label: '全部类型' },
            { value: 'course', label: '课程' },
            { value: 'book', label: '书籍' },
            { value: 'topic', label: '主题' },
          ]"
        />
      </div>
      <Button :start-icon="PlusIcon" @click="createOpen = true">新建知识库</Button>
    </div>

    <!-- 加载/错误/空态 -->
    <div v-if="loading" class="mt-6 rounded-2xl border border-gray-200 p-10 text-center dark:border-gray-800">
      <p class="text-sm text-gray-500 dark:text-gray-400">知识库加载中…</p>
    </div>
    <div v-else-if="error" class="mt-6">
      <Alert variant="error" title="加载失败" :message="error" />
      <div class="mt-3">
        <Button size="sm" variant="outline" @click="loadAll">重试</Button>
      </div>
    </div>
    <div
      v-else-if="filteredLibraries.length === 0"
      class="mt-6 rounded-2xl border border-gray-200 p-10 text-center dark:border-gray-800"
    >
      <p class="text-sm font-medium text-gray-700 dark:text-gray-300">还没有知识库</p>
      <p class="mt-1 text-xs text-gray-400">新建知识库后即可导入 PDF / PPT / 书本照片资料</p>
      <Button class="mt-4" size="sm" :start-icon="PlusIcon" @click="createOpen = true">新建知识库</Button>
    </div>

    <!-- 知识库列表 -->
    <div v-else class="mt-4 space-y-4">
      <div
        v-for="library in filteredLibraries"
        :key="library.id"
        class="rounded-2xl border border-gray-200 bg-white dark:border-gray-800 dark:bg-white/[0.03]"
      >
        <div class="flex flex-wrap items-center justify-between gap-3 px-5 py-4">
          <div class="min-w-0">
            <div class="flex items-center gap-2">
              <h3 class="truncate text-base font-semibold text-gray-800 dark:text-white/90">
                {{ library.name }}
              </h3>
              <Badge color="info" size="sm">{{ libraryTypeLabel(library.type) }}</Badge>
            </div>
            <p class="mt-1 line-clamp-1 text-sm text-gray-500 dark:text-gray-400">
              {{ library.description || '暂无描述' }}
            </p>
          </div>
          <div class="flex flex-wrap items-center gap-4 text-sm text-gray-500 dark:text-gray-400">
            <span>资料 {{ library.documentCount ?? 0 }}</span>
            <span>节点 {{ library.nodeCount ?? 0 }}</span>
            <span>关系 {{ library.edgeCount ?? 0 }}</span>
            <Button
              size="sm"
              variant="outline"
              class="!px-3 !py-1.5"
              @click="toggleNodes(library)"
            >
              {{ expandedLibraryId === library.id ? '收起节点' : '查看节点' }}
            </Button>
            <button
              class="text-sm text-error-500 hover:text-error-600"
              @click="askDelete(library)"
            >
              删除
            </button>
          </div>
        </div>

        <!-- 节点列表（点击跳转图谱工作台并聚焦，§9.1） -->
        <div v-if="expandedLibraryId === library.id" class="border-t border-gray-100 px-5 py-4 dark:border-gray-800">
          <p v-if="nodesLoading" class="text-sm text-gray-500">节点加载中…</p>
          <p v-else-if="libraryNodes.length === 0" class="text-sm text-gray-400">该知识库暂无节点</p>
          <div v-else class="flex flex-wrap gap-2">
            <button
              v-for="node in libraryNodes"
              :key="node.id"
              class="rounded-full border border-gray-200 px-3 py-1 text-sm text-gray-700 transition hover:border-brand-300 hover:text-brand-500 dark:border-gray-700 dark:text-gray-300 dark:hover:border-brand-500/40"
              @click="gotoNode(node)"
            >
              {{ node.name }}
              <span class="text-xs text-gray-400">· {{ NODE_TYPE_LABELS[node.type] ?? node.type }}</span>
            </button>
          </div>
        </div>
      </div>
    </div>

    <!-- 新建知识库 -->
    <Modal v-if="createOpen" full-screen-backdrop @close="createOpen = false">
      <template #body>
        <div class="relative mx-4 w-full max-w-[480px] rounded-3xl bg-white p-6 dark:bg-gray-900">
          <h4 class="text-title-md font-semibold text-gray-800 dark:text-white/90">新建知识库</h4>
          <div class="mt-5 space-y-4">
            <div>
              <label class="mb-1.5 block text-sm font-medium text-gray-700 dark:text-gray-400">
                名称 <span class="text-error-500">*</span>
              </label>
              <TextInput v-model="createForm.name" placeholder="如：计算机网络" />
            </div>
            <div>
              <label class="mb-1.5 block text-sm font-medium text-gray-700 dark:text-gray-400">类型</label>
              <SelectInput
                v-model="createForm.type"
                :options="[
                  { value: 'course', label: '课程' },
                  { value: 'book', label: '书籍' },
                  { value: 'topic', label: '主题' },
                ]"
              />
            </div>
            <div>
              <label class="mb-1.5 block text-sm font-medium text-gray-700 dark:text-gray-400">描述</label>
              <TextArea v-model="createForm.description" :rows="3" placeholder="一句话说明该知识库收录的内容" />
            </div>
            <p v-if="formError" class="text-sm text-error-500">{{ formError }}</p>
          </div>
          <div class="mt-6 flex justify-end gap-3">
            <Button variant="outline" size="sm" @click="createOpen = false">取消</Button>
            <Button size="sm" :disabled="submitting || !createForm.name.trim()" @click="createLibrary">
              {{ submitting ? '创建中…' : '创建' }}
            </Button>
          </div>
        </div>
      </template>
    </Modal>

    <!-- 删除确认：显示影响范围（§9.1） -->
    <Modal v-if="deleteTarget" full-screen-backdrop @close="deleteTarget = null">
      <template #body>
        <div class="relative mx-4 w-full max-w-[460px] rounded-3xl bg-white p-6 dark:bg-gray-900">
          <h4 class="text-title-md font-semibold text-gray-800 dark:text-white/90">
            确认删除「{{ deleteTarget.name }}」
          </h4>
          <p class="mt-2 text-sm text-gray-500 dark:text-gray-400">
            此操作将影响：
          </p>
          <ul class="mt-2 space-y-1 text-sm text-gray-600 dark:text-gray-300">
            <li>· 资料 {{ deleteTarget.documentCount ?? 0 }} 份（含已解析产物）</li>
            <li>· 知识库与节点的关联 {{ deleteTarget.nodeCount ?? 0 }} 条（节点本身保留）</li>
          </ul>
          <div class="mt-6 flex justify-end gap-3">
            <Button variant="outline" size="sm" @click="deleteTarget = null">取消</Button>
            <Button size="sm" class="!bg-error-500 hover:!bg-error-600" @click="removeLibrary">
              {{ submitting ? '删除中…' : '确认删除' }}
            </Button>
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
import Alert from '@/components/ui/Alert.vue'
import Badge from '@/components/ui/Badge.vue'
import Button from '@/components/ui/Button.vue'
import Modal from '@/components/ui/Modal.vue'
import TextInput from '@/components/ui/form/TextInput.vue'
import TextArea from '@/components/ui/form/TextArea.vue'
import SelectInput from '@/components/ui/form/SelectInput.vue'
import { PlusIcon } from '@/icons'
import { libraryApi } from '@/services/libraryApi'
import { insightsApi } from '@/services/insightsApi'
import { graphApi } from '@/services/graphApi'
import { ApiError } from '@/services/http'
import { NODE_TYPE_LABELS } from '@/services/types'
import type { GraphNodeDto, InsightSummaryDto, LibraryDto } from '@/services/types'

const router = useRouter()

const summary = ref<InsightSummaryDto | null>(null)
const libraries = ref<LibraryDto[]>([])
const loading = ref(true)
const error = ref<string | null>(null)
const typeFilter = ref('')
const submitting = ref(false)
const formError = ref<string | null>(null)

const createOpen = ref(false)
const createForm = ref({ name: '', type: 'course', description: '' })
const deleteTarget = ref<LibraryDto | null>(null)

const expandedLibraryId = ref<number | null>(null)
const libraryNodes = ref<GraphNodeDto[]>([])
const nodesLoading = ref(false)

const filteredLibraries = computed(() =>
  typeFilter.value
    ? libraries.value.filter((library) => library.type === typeFilter.value)
    : libraries.value,
)

const statCards = computed(() => [
  { label: '知识库', value: libraries.value.length },
  { label: '资料', value: summary.value?.documentCount ?? 0 },
  { label: '知识节点', value: summary.value?.nodeCount ?? 0 },
  { label: '知识关系', value: summary.value?.edgeCount ?? 0 },
])

function libraryTypeLabel(type: string): string {
  switch (type) {
    case 'course':
      return '课程'
    case 'book':
      return '书籍'
    case 'topic':
      return '主题'
    default:
      return type
  }
}

async function loadAll() {
  loading.value = true
  error.value = null
  try {
    const [libraryList, insightSummary] = await Promise.all([
      libraryApi.list(),
      insightsApi.getSummary().catch(() => null),
    ])
    libraries.value = libraryList
    if (insightSummary) summary.value = insightSummary
  } catch (err) {
    error.value = err instanceof ApiError ? err.message : '知识库加载失败，请确认 Java 后端已启动'
  } finally {
    loading.value = false
  }
}

async function createLibrary() {
  submitting.value = true
  formError.value = null
  try {
    await libraryApi.create({
      name: createForm.value.name.trim(),
      type: createForm.value.type,
      description: createForm.value.description.trim() || undefined,
    })
    createOpen.value = false
    createForm.value = { name: '', type: 'course', description: '' }
    await loadAll()
  } catch (err) {
    formError.value = err instanceof ApiError ? err.message : '创建失败，请稍后重试'
  } finally {
    submitting.value = false
  }
}

function askDelete(library: LibraryDto) {
  deleteTarget.value = library
}

async function removeLibrary() {
  if (!deleteTarget.value) return
  submitting.value = true
  try {
    await libraryApi.remove(deleteTarget.value.id)
    deleteTarget.value = null
    await loadAll()
  } catch (err) {
    formError.value = err instanceof ApiError ? err.message : '删除失败'
    deleteTarget.value = null
  } finally {
    submitting.value = false
  }
}

async function toggleNodes(library: LibraryDto) {
  if (expandedLibraryId.value === library.id) {
    expandedLibraryId.value = null
    return
  }
  expandedLibraryId.value = library.id
  nodesLoading.value = true
  try {
    const subgraph = await graphApi.getOverview(library.id, 100)
    libraryNodes.value = subgraph.nodes
  } catch {
    libraryNodes.value = []
  } finally {
    nodesLoading.value = false
  }
}

function gotoNode(node: GraphNodeDto) {
  void router.push({ path: '/', query: { node: String(node.id), depth: '1', mode: 'local' } })
}

onMounted(loadAll)
</script>
