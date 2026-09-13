<template>
  <AdminLayout>
    <PageBreadcrumb page-title="审核中心" />

    <div v-if="loading" class="rounded-2xl border border-gray-200 p-10 text-center dark:border-gray-800">
      <p class="text-sm text-gray-500 dark:text-gray-400">审核任务加载中…</p>
    </div>

    <div v-else-if="error" class="space-y-3">
      <Alert variant="error" title="加载失败" :message="error" />
      <Button size="sm" variant="outline" @click="load">重试</Button>
    </div>

    <!-- 空态：暂无待审核任务 -->
    <div
      v-else-if="pendingJobs.length === 0"
      class="flex flex-col items-center justify-center rounded-2xl border border-gray-200 px-6 py-14 text-center dark:border-gray-800"
    >
      <span class="flex h-12 w-12 items-center justify-center rounded-2xl bg-success-50 text-success-500 dark:bg-success-500/15 dark:text-success-400">
        <CheckIcon class="h-6 w-6" />
      </span>
      <p class="mt-4 text-sm font-medium text-gray-700 dark:text-gray-300">暂无待审核内容</p>
      <p class="mt-1 max-w-md text-xs leading-5 text-gray-400">
        当「处理中心」中的 AI 抽取任务完成后，候选节点与候选关系会出现在这里，
        由人工逐条确认（§3.1：正式入库前必须人工审核，AI 不直接写入）。
      </p>
    </div>

    <!-- 审核工作区：任务选择器 + 候选节点/候选关系（§9.3） -->
    <div v-else class="mt-1">
      <div class="mb-4 flex flex-wrap items-center justify-between gap-3">
        <div class="flex flex-wrap items-center gap-3">
          <div class="w-72">
            <SelectInput
              v-model="selectedJobId"
              :options="jobOptions"
              placeholder="选择待审核任务"
            />
          </div>
          <div class="flex items-center gap-2 text-xs text-gray-400">
            <Badge color="warning" size="sm">待审核 {{ pendingJobs.length }}</Badge>
            <span v-if="activeJob">
              任务 #{{ activeJob.id }} · {{ activeJob.documentName || ('文档 #' + activeJob.documentId) }}
              · 节点 {{ entityCandidates.length }} · 关系 {{ relationCandidates.length }}
            </span>
          </div>
        </div>

        <div class="flex items-center gap-3">
          <Button
            size="sm"
            variant="outline"
            class="!px-3 !py-1.5"
            :disabled="candidatesLoading || pendingIds('entity').length === 0"
            @click="bulk('entity', 'accept')"
          >
            接受全部节点
          </Button>
          <Button
            size="sm"
            variant="outline"
            class="!px-3 !py-1.5"
            :disabled="candidatesLoading || pendingIds('relation').length === 0"
            @click="bulk('relation', 'accept')"
          >
            接受全部关系
          </Button>
          <Button size="sm" class="!px-3 !py-1.5" :disabled="candidatesLoading" @click="confirmCommit = true">
            确认入库
          </Button>
        </div>
      </div>

      <Alert v-if="actionNotice" :variant="actionNotice.type" :title="actionNotice.title" :message="actionNotice.message" class="mb-3" />

      <div class="mb-3 inline-flex items-center rounded-lg bg-gray-100 p-1 dark:bg-gray-800">
        <button
          v-for="tab in tabs"
          :key="tab.value"
          type="button"
          class="rounded-md px-3.5 py-1.5 text-sm font-medium transition"
          :class="
            activeTab === tab.value
              ? 'bg-white text-gray-800 shadow-theme-xs dark:bg-gray-900 dark:text-white/90'
              : 'text-gray-500 hover:text-gray-700 dark:text-gray-400 dark:hover:text-gray-200'
          "
          @click="activeTab = tab.value"
        >
          {{ tab.label }}
        </button>
      </div>

      <div v-if="candidatesLoading" class="rounded-2xl border border-gray-200 p-10 text-center dark:border-gray-800">
        <p class="text-sm text-gray-500 dark:text-gray-400">候选内容加载中…</p>
      </div>
      <div
        v-else-if="activeTab === 'entities' && entityCandidates.length === 0"
        class="rounded-2xl border border-gray-200 p-10 text-center dark:border-gray-800"
      >
        <p class="text-sm text-gray-500 dark:text-gray-400">该任务没有候选节点</p>
      </div>
      <div
        v-else-if="activeTab === 'relations' && relationCandidates.length === 0"
        class="rounded-2xl border border-gray-200 p-10 text-center dark:border-gray-800"
      >
        <p class="text-sm text-gray-500 dark:text-gray-400">该任务没有候选关系</p>
      </div>
      <template v-else>
        <EntityReviewTable
          v-if="activeTab === 'entities'"
          :candidates="entityCandidates"
          @accept="setEntityStatus($event, 'ACCEPTED')"
          @reject="setEntityStatus($event, 'REJECTED')"
          @edit="openEntityEditor"
        />
        <RelationReviewTable
          v-else
          :candidates="relationCandidates"
          @accept="setRelationStatus($event, 'ACCEPTED')"
          @reject="setRelationStatus($event, 'REJECTED')"
          @edit="openRelationEditor"
        />
      </template>

      <!-- 入库前预计数量（§9.3） -->
      <Modal v-if="confirmCommit" full-screen-backdrop @close="confirmCommit = false">
        <template #body>
          <div class="relative mx-4 w-full max-w-[460px] rounded-3xl bg-white p-6 dark:bg-gray-900">
            <h4 class="text-title-md font-semibold text-gray-800 dark:text-white/90">确认入库</h4>
            <ul class="mt-3 space-y-1.5 text-sm text-gray-600 dark:text-gray-300">
              <li>· 新增节点：{{ commitPreview.createdNodes }}</li>
              <li>· 合并到已有节点：{{ commitPreview.mergedNodes }}</li>
              <li>· 新增关系：{{ commitPreview.createdEdges }}</li>
              <li>· 跳过（未接受/拒绝）：{{ commitPreview.skipped }}</li>
            </ul>
            <p class="mt-3 text-xs text-gray-400">入库为单事务操作，失败自动回滚；重复提交不会重复入库（幂等）。</p>
            <div class="mt-6 flex justify-end gap-3">
              <Button variant="outline" size="sm" @click="confirmCommit = false">取消</Button>
              <Button size="sm" :disabled="committing" @click="commit">{{ committing ? '入库中…' : '确认' }}</Button>
            </div>
          </div>
        </template>
      </Modal>

      <!-- 候选节点编辑弹窗 -->
      <Modal v-if="entityEditor" full-screen-backdrop @close="entityEditor = null">
        <template #body>
          <div class="relative mx-4 w-full max-w-[520px] rounded-3xl bg-white p-6 dark:bg-gray-900">
            <h4 class="text-title-md font-semibold text-gray-800 dark:text-white/90">编辑候选节点</h4>
            <div class="mt-4 space-y-3">
              <div>
                <label class="mb-1 block text-xs text-gray-500 dark:text-gray-400">名称</label>
                <TextInput v-model="entityEditor.form.name" />
              </div>
              <div>
                <label class="mb-1 block text-xs text-gray-500 dark:text-gray-400">节点类型</label>
                <SelectInput v-model="entityEditor.form.nodeType" :options="nodeTypeOptions" />
              </div>
              <div>
                <label class="mb-1 block text-xs text-gray-500 dark:text-gray-400">定义</label>
                <TextArea v-model="entityEditor.form.definition" :rows="4" />
              </div>
              <div>
                <label class="mb-1 block text-xs text-gray-500 dark:text-gray-400">别名（用逗号分隔）</label>
                <TextInput v-model="entityEditor.aliasesText" />
              </div>
            </div>
            <div class="mt-6 flex justify-end gap-3">
              <Button variant="outline" size="sm" @click="entityEditor = null">取消</Button>
              <Button size="sm" :disabled="savingEdit" @click="saveEntityEdit">
                {{ savingEdit ? '保存中…' : '保存并标记已编辑' }}
              </Button>
            </div>
          </div>
        </template>
      </Modal>

      <!-- 候选关系编辑弹窗 -->
      <Modal v-if="relationEditor" full-screen-backdrop @close="relationEditor = null">
        <template #body>
          <div class="relative mx-4 w-full max-w-[520px] rounded-3xl bg-white p-6 dark:bg-gray-900">
            <h4 class="text-title-md font-semibold text-gray-800 dark:text-white/90">编辑候选关系</h4>
            <div class="mt-4 space-y-3">
              <div>
                <label class="mb-1 block text-xs text-gray-500 dark:text-gray-400">起点（候选节点）</label>
                <SelectInput v-model="relationEditor.form.sourceTempKey" :options="tempKeyOptions" />
              </div>
              <div>
                <label class="mb-1 block text-xs text-gray-500 dark:text-gray-400">关系类型</label>
                <TextInput v-model="relationEditor.form.relationType" />
              </div>
              <div>
                <label class="mb-1 block text-xs text-gray-500 dark:text-gray-400">终点（候选节点）</label>
                <SelectInput v-model="relationEditor.form.targetTempKey" :options="tempKeyOptions" />
              </div>
            </div>
            <div class="mt-6 flex justify-end gap-3">
              <Button variant="outline" size="sm" @click="relationEditor = null">取消</Button>
              <Button size="sm" :disabled="savingEdit" @click="saveRelationEdit">
                {{ savingEdit ? '保存中…' : '保存并标记已编辑' }}
              </Button>
            </div>
          </div>
        </template>
      </Modal>
    </div>
  </AdminLayout>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import AdminLayout from '@/components/layout/AdminLayout.vue'
import PageBreadcrumb from '@/components/common/PageBreadcrumb.vue'
import Alert from '@/components/ui/Alert.vue'
import Badge from '@/components/ui/Badge.vue'
import Button from '@/components/ui/Button.vue'
import Modal from '@/components/ui/Modal.vue'
import SelectInput from '@/components/ui/form/SelectInput.vue'
import TextInput from '@/components/ui/form/TextInput.vue'
import TextArea from '@/components/ui/form/TextArea.vue'
import { CheckIcon } from '@/icons'
import EntityReviewTable from '@/features/review/EntityReviewTable.vue'
import RelationReviewTable from '@/features/review/RelationReviewTable.vue'
import { ingestionApi } from '@/services/ingestionApi'
import { reviewApi } from '@/services/reviewApi'
import type { EntityCandidate, RelationCandidate } from '@/services/reviewApi'
import { NODE_TYPES, NODE_TYPE_LABELS } from '@/services/types'
import { ApiError } from '@/services/http'
import type { ProcessingJobDto } from '@/services/types'

const tabs = [
  { value: 'entities', label: '候选节点' },
  { value: 'relations', label: '候选关系' },
] as const

const loading = ref(true)
const error = ref<string | null>(null)
const pendingJobs = ref<ProcessingJobDto[]>([])
const selectedJobId = ref('')
const activeTab = ref<'entities' | 'relations'>('entities')
const entityCandidates = ref<EntityCandidate[]>([])
const relationCandidates = ref<RelationCandidate[]>([])
const candidatesLoading = ref(false)
const confirmCommit = ref(false)
const committing = ref(false)
const savingEdit = ref(false)
const actionNotice = ref<{ type: 'success' | 'error' | 'info'; title: string; message: string } | null>(null)

const entityEditor = ref<{ candidate: EntityCandidate; form: { name: string; nodeType: string; definition: string }; aliasesText: string } | null>(null)
const relationEditor = ref<{ candidate: RelationCandidate; form: { sourceTempKey: string; targetTempKey: string; relationType: string } } | null>(null)

const activeJob = computed(() => pendingJobs.value.find((job) => String(job.id) === selectedJobId.value) ?? null)

const jobOptions = computed(() =>
  pendingJobs.value.map((job) => ({
    value: String(job.id),
    label: `#${job.id} · ${job.documentName || ('文档 #' + job.documentId)}`,
  })),
)

const nodeTypeOptions = computed(() =>
  NODE_TYPES.map((type) => ({ value: type, label: NODE_TYPE_LABELS[type] ?? type })),
)

const tempKeyOptions = computed(() =>
  entityCandidates.value.map((candidate) => ({
    value: candidate.tempKey,
    label: `${candidate.name}（${candidate.tempKey}）`,
  })),
)

/** 入库前预计数量（§9.3）：接受/编辑/合并计入，其余跳过 */
const commitPreview = computed(() => {
  const accepted = entityCandidates.value.filter(
    (c) => c.reviewStatus === 'ACCEPTED' || c.reviewStatus === 'EDITED' || c.reviewStatus === 'MERGE',
  )
  const relationAccepted = relationCandidates.value.filter(
    (c) => c.reviewStatus === 'ACCEPTED' || c.reviewStatus === 'EDITED',
  )
  return {
    createdNodes: accepted.filter((c) => !c.matchedNodeId && c.reviewStatus !== 'MERGE').length,
    mergedNodes: accepted.filter((c) => c.matchedNodeId).length,
    createdEdges: relationAccepted.length,
    skipped:
      entityCandidates.value.length + relationCandidates.value.length - accepted.length - relationAccepted.length,
  }
})

function pendingIds(kind: 'entity' | 'relation'): number[] {
  const candidates = kind === 'entity' ? entityCandidates.value : relationCandidates.value
  return candidates.filter((c) => c.reviewStatus === 'PENDING' || c.reviewStatus === 'MERGE').map((c) => c.id)
}

async function load() {
  loading.value = true
  error.value = null
  try {
    const page = await ingestionApi.listJobs('AWAITING_REVIEW')
    pendingJobs.value = page.items
    if (page.items.length > 0) {
      // 默认选中最新任务（列表按 id 倒序）
      selectedJobId.value = String(activeJob.value?.id ?? page.items[0].id)
    } else {
      selectedJobId.value = ''
      entityCandidates.value = []
      relationCandidates.value = []
    }
  } catch (err) {
    error.value = err instanceof ApiError ? err.message : '审核任务加载失败'
  } finally {
    loading.value = false
  }
}

/** 切换任务后加载对应候选节点与候选关系 */
async function loadCandidates() {
  const jobId = selectedJobId.value
  if (!jobId) return
  candidatesLoading.value = true
  actionNotice.value = null
  try {
    const [entities, relations] = await Promise.all([
      reviewApi.listEntities(Number(jobId)),
      reviewApi.listRelations(Number(jobId)),
    ])
    entityCandidates.value = entities
    relationCandidates.value = relations
  } catch (err) {
    error.value = err instanceof ApiError ? err.message : '候选内容加载失败'
  } finally {
    candidatesLoading.value = false
  }
}

watch(selectedJobId, loadCandidates)

async function setEntityStatus(candidate: EntityCandidate, status: 'ACCEPTED' | 'REJECTED') {
  try {
    await reviewApi.updateEntity(candidate.id, { reviewStatus: status })
    candidate.reviewStatus = status
  } catch (err) {
    actionNotice.value = {
      type: 'error',
      title: '操作失败',
      message: err instanceof ApiError ? err.message : '操作失败，请重试',
    }
  }
}

async function setRelationStatus(candidate: RelationCandidate, status: 'ACCEPTED' | 'REJECTED') {
  try {
    await reviewApi.updateRelation(candidate.id, { reviewStatus: status })
    candidate.reviewStatus = status
  } catch (err) {
    actionNotice.value = {
      type: 'error',
      title: '操作失败',
      message: err instanceof ApiError ? err.message : '操作失败，请重试',
    }
  }
}

async function bulk(kind: 'entity' | 'relation', action: 'accept' | 'reject') {
  if (!activeJob.value) return
  const ids = pendingIds(kind)
  if (ids.length === 0) return
  try {
    await reviewApi.bulkAction(activeJob.value.id, { kind, action, ids })
    const candidates = kind === 'entity' ? entityCandidates.value : relationCandidates.value
    candidates.forEach((c) => {
      if (ids.includes(c.id)) c.reviewStatus = action === 'accept' ? 'ACCEPTED' : 'REJECTED'
    })
    actionNotice.value = {
      type: 'success',
      title: '批量操作完成',
      message: `已${action === 'accept' ? '接受' : '拒绝'} ${ids.length} 条候选${kind === 'entity' ? '节点' : '关系'}`,
    }
  } catch (err) {
    actionNotice.value = {
      type: 'error',
      title: '批量操作失败',
      message: err instanceof ApiError ? err.message : '批量操作失败，请重试',
    }
  }
}

function openEntityEditor(candidate: EntityCandidate) {
  entityEditor.value = {
    candidate,
    form: {
      name: candidate.name,
      nodeType: candidate.nodeType,
      definition: candidate.definition,
    },
    aliasesText: candidate.aliases.join(', '),
  }
}

function openRelationEditor(candidate: RelationCandidate) {
  relationEditor.value = {
    candidate,
    form: {
      sourceTempKey: candidate.sourceTempKey,
      targetTempKey: candidate.targetTempKey,
      relationType: candidate.relationType,
    },
  }
}

async function saveEntityEdit() {
  if (!entityEditor.value) return
  savingEdit.value = true
  try {
    const aliases = entityEditor.value.aliasesText
      .split(/[,，、;；]/)
      .map((item) => item.trim())
      .filter((item) => item.length > 0)
    const updated = await reviewApi.updateEntity(entityEditor.value.candidate.id, {
      reviewStatus: 'EDITED',
      edited: {
        name: entityEditor.value.form.name.trim(),
        nodeType: entityEditor.value.form.nodeType,
        definition: entityEditor.value.form.definition.trim(),
        aliases,
      },
    })
    const index = entityCandidates.value.findIndex((c) => c.id === updated.id)
    if (index >= 0) entityCandidates.value[index] = updated
    entityEditor.value = null
    actionNotice.value = { type: 'success', title: '已保存', message: '候选节点已更新并标记为「已编辑」' }
  } catch (err) {
    actionNotice.value = {
      type: 'error',
      title: '保存失败',
      message: err instanceof ApiError ? err.message : '保存失败，请重试',
    }
  } finally {
    savingEdit.value = false
  }
}

async function saveRelationEdit() {
  if (!relationEditor.value) return
  savingEdit.value = true
  try {
    const updated = await reviewApi.updateRelation(relationEditor.value.candidate.id, {
      reviewStatus: 'EDITED',
      edited: {
        sourceTempKey: relationEditor.value.form.sourceTempKey,
        targetTempKey: relationEditor.value.form.targetTempKey,
        relationType: relationEditor.value.form.relationType.trim(),
      },
    })
    const index = relationCandidates.value.findIndex((c) => c.id === updated.id)
    if (index >= 0) relationCandidates.value[index] = updated
    relationEditor.value = null
    actionNotice.value = { type: 'success', title: '已保存', message: '候选关系已更新并标记为「已编辑」' }
  } catch (err) {
    actionNotice.value = {
      type: 'error',
      title: '保存失败',
      message: err instanceof ApiError ? err.message : '保存失败，请重试',
    }
  } finally {
    savingEdit.value = false
  }
}

async function commit() {
  if (!activeJob.value) return
  committing.value = true
  try {
    const result = await reviewApi.commit(activeJob.value.id)
    confirmCommit.value = false
    actionNotice.value = {
      type: 'success',
      title: '入库完成',
      message: `新增节点 ${result.createdNodes}，合并节点 ${result.mergedNodes}，新增关系 ${result.createdEdges}，跳过 ${result.skipped}。图谱与搜索已可检索新内容。`,
    }
    await load()
  } catch (err) {
    confirmCommit.value = false
    actionNotice.value = {
      type: 'error',
      title: '入库失败',
      message: err instanceof ApiError ? err.message : '入库失败，任务已回滚，请重试',
    }
  } finally {
    committing.value = false
  }
}

onMounted(load)
</script>
