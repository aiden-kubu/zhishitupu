<template>
  <Teleport to="body">
    <div v-if="visible" class="fixed inset-0 z-99999 flex">
      <div
        class="absolute inset-0 bg-gray-400/50 backdrop-blur-[2px] dark:bg-gray-900/60"
        @click="emit('close')"
      ></div>

      <aside
        class="relative ms-auto flex h-full w-[440px] max-w-[92vw] flex-col overflow-y-auto bg-white shadow-theme-xl dark:bg-gray-900"
      >
        <!-- 头部 -->
        <div class="flex items-center justify-between border-b border-gray-100 px-6 py-4 dark:border-gray-800">
          <h3 class="text-base font-semibold text-gray-800 dark:text-white/90">节点详情</h3>
          <button
            class="flex h-9 w-9 items-center justify-center rounded-lg text-gray-400 hover:bg-gray-100 hover:text-gray-600 dark:hover:bg-white/[0.05] dark:hover:text-white/80"
            aria-label="关闭"
            @click="emit('close')"
          >
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none">
              <path
                fill-rule="evenodd"
                clip-rule="evenodd"
                d="M6.21967 7.28131C5.92678 6.98841 5.92678 6.51354 6.21967 6.22065C6.51256 5.92775 6.98744 5.92775 7.28033 6.22065L11.999 10.9393L16.7176 6.22078C17.0105 5.92789 17.4854 5.92788 17.7782 6.22078C18.0711 6.51367 18.0711 6.98855 17.7782 7.28144L13.0597 12L17.7782 16.7186C18.0711 17.0115 18.0711 17.4863 17.7782 17.7792C17.4854 18.0721 17.0105 18.0721 16.7176 17.7792L11.999 13.0607L7.28033 17.7794C6.98744 18.0722 6.51256 18.0722 6.21967 17.7794C5.92678 17.4865 5.92678 17.0116 6.21967 16.7187L10.9384 12L6.21967 7.28131Z"
                fill="currentColor"
              />
            </svg>
          </button>
        </div>

        <div v-if="loading" class="flex flex-1 items-center justify-center">
          <p class="text-sm text-gray-500 dark:text-gray-400">加载中…</p>
        </div>

        <div v-else-if="error" class="flex flex-1 flex-col items-center justify-center gap-3 px-6">
          <p class="text-sm text-error-500">{{ error }}</p>
          <Button size="sm" variant="outline" @click="load">重试</Button>
        </div>

        <div v-else-if="detail" class="flex-1 space-y-6 px-6 py-5">
          <!-- 基本信息 -->
          <section>
            <div class="flex items-start justify-between gap-3">
              <div>
                <h4 class="text-lg font-semibold text-gray-800 dark:text-white/90">
                  {{ detail.name }}
                </h4>
                <p v-if="detail.nameEn" class="mt-0.5 text-sm text-gray-400">{{ detail.nameEn }}</p>
              </div>
              <div class="flex shrink-0 flex-col items-end gap-1.5">
                <Badge color="primary">{{ nodeTypeLabel }}</Badge>
                <!-- AI 来源低干扰标识（§16）：origin=ai_search_generation 且待审核 -->
                <Badge v-if="isAiGenerated" color="warning" size="sm">AI 生成 · 待审核</Badge>
              </div>
            </div>
            <p class="mt-3 text-sm leading-6 text-gray-600 dark:text-gray-300">
              {{ detail.definition || '暂无定义' }}
            </p>
            <div v-if="detail.aliases.length > 0" class="mt-3 flex flex-wrap gap-1.5">
              <Badge v-for="alias in detail.aliases" :key="alias" color="light" size="sm">
                {{ alias }}
              </Badge>
            </div>
            <p class="mt-3 text-xs text-gray-400">
              所属知识库：{{ detail.libraries.map((library) => library.name).join('、') || '—' }}
            </p>
          </section>

          <!-- 关联关系（由邻域子图推导） -->
          <section>
            <div class="mb-2 flex items-center justify-between">
              <h5 class="text-sm font-semibold text-gray-800 dark:text-white/90">
                关联关系（{{ relatedEdges.length }}）
              </h5>
              <button
                class="text-xs font-medium text-brand-500 hover:text-brand-600"
                @click="editingRelation = editingRelation === 'new' ? null : 'new'"
              >
                新增关系
              </button>
            </div>

            <ul class="space-y-2">
              <li
                v-for="edge in relatedEdges"
                :key="edge.id"
                class="flex items-center justify-between gap-2 rounded-xl border border-gray-100 px-3 py-2 dark:border-gray-800"
              >
                <template v-if="editingRelation === edge.id">
                  <div class="flex w-full flex-col gap-2">
                    <TextInput v-model="relationEdit.type" placeholder="关系类型" />
                    <div class="flex gap-2">
                      <Button size="sm" @click="saveRelation(edge.id)">保存</Button>
                      <Button size="sm" variant="outline" @click="editingRelation = null">取消</Button>
                    </div>
                  </div>
                </template>
                <template v-else>
                  <p class="min-w-0 flex-1 truncate text-sm text-gray-700 dark:text-gray-300">
                    <span class="text-gray-400">{{ edge.direction === 'in' ? '←' : '→' }}</span>
                    {{ edge.peerName }}
                    <Badge color="light" size="sm" class="ms-1">{{ edge.relation }}</Badge>
                  </p>
                  <div class="flex shrink-0 items-center gap-1">
                    <button
                      class="rounded-lg px-2 py-1 text-xs text-gray-500 hover:bg-gray-100 dark:hover:bg-white/[0.05]"
                      @click="startEditRelation(edge)"
                    >
                      编辑
                    </button>
                    <button
                      class="rounded-lg px-2 py-1 text-xs text-error-500 hover:bg-error-50 dark:hover:bg-error-500/10"
                      @click="confirmDeleteEdge = edge.id"
                    >
                      删除
                    </button>
                  </div>
                </template>
              </li>
            </ul>

            <!-- 新增关系 -->
            <div v-if="editingRelation === 'new'" class="mt-3 space-y-2 rounded-xl border border-gray-100 p-3 dark:border-gray-800">
              <TextInput v-model="newRelation.targetName" placeholder="对端节点名称（已有节点）" />
              <TextInput v-model="newRelation.type" placeholder="关系类型，如：包含 / 对比 / 前置" />
              <p v-if="newRelation.error" class="text-xs text-error-500">{{ newRelation.error }}</p>
              <div class="flex gap-2">
                <Button size="sm" @click="createRelation">创建</Button>
                <Button size="sm" variant="outline" @click="editingRelation = null">取消</Button>
              </div>
            </div>
          </section>

          <!-- 操作 -->
          <section class="flex flex-wrap gap-2 border-t border-gray-100 pt-4 dark:border-gray-800">
            <Button size="sm" variant="outline" @click="startEdit">编辑节点</Button>
            <Button size="sm" variant="outline" class="!text-error-500" @click="confirmDelete = true">
              删除节点
            </Button>
          </section>

          <!-- 编辑表单 -->
          <section v-if="editing" class="space-y-3 rounded-2xl border border-gray-100 p-4 dark:border-gray-800">
            <div>
              <label class="mb-1.5 block text-sm font-medium text-gray-700 dark:text-gray-400">名称</label>
              <TextInput v-model="editForm.name" />
            </div>
            <div>
              <label class="mb-1.5 block text-sm font-medium text-gray-700 dark:text-gray-400">英文名</label>
              <TextInput v-model="editForm.nameEn" />
            </div>
            <div>
              <label class="mb-1.5 block text-sm font-medium text-gray-700 dark:text-gray-400">定义</label>
              <TextArea v-model="editForm.definition" :rows="4" />
            </div>
            <div class="flex gap-2">
              <Button size="sm" :disabled="saving" @click="saveNode">{{ saving ? '保存中…' : '保存' }}</Button>
              <Button size="sm" variant="outline" @click="editing = false">取消</Button>
            </div>
          </section>
        </div>

        <!-- 删除确认（§6.5 二次确认） -->
        <Modal v-if="confirmDelete" full-screen-backdrop @close="confirmDelete = false">
          <template #body>
            <div class="relative mx-4 w-full max-w-[420px] rounded-3xl bg-white p-6 dark:bg-gray-900">
              <h4 class="text-title-md font-semibold text-gray-800 dark:text-white/90">确认删除节点</h4>
              <p class="mt-2 text-sm text-gray-500 dark:text-gray-400">
                将同时删除该节点的
                <span class="font-medium text-error-500">{{ relatedEdges.length }}</span>
                条关联关系与全部别名/证据引用，此操作不可恢复。
              </p>
              <div class="mt-6 flex justify-end gap-3">
                <Button variant="outline" size="sm" @click="confirmDelete = false">取消</Button>
                <Button size="sm" class="!bg-error-500 hover:!bg-error-600" @click="deleteNode">确认删除</Button>
              </div>
            </div>
          </template>
        </Modal>

        <Modal v-if="confirmDeleteEdge !== null" full-screen-backdrop @close="confirmDeleteEdge = null">
          <template #body>
            <div class="relative mx-4 w-full max-w-[420px] rounded-3xl bg-white p-6 dark:bg-gray-900">
              <h4 class="text-title-md font-semibold text-gray-800 dark:text-white/90">确认删除关系</h4>
              <p class="mt-2 text-sm text-gray-500 dark:text-gray-400">删除后图谱中两个节点将不再直接关联。</p>
              <div class="mt-6 flex justify-end gap-3">
                <Button variant="outline" size="sm" @click="confirmDeleteEdge = null">取消</Button>
                <Button size="sm" class="!bg-error-500 hover:!bg-error-600" @click="deleteRelation">确认删除</Button>
              </div>
            </div>
          </template>
        </Modal>
      </aside>
    </div>
  </Teleport>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import Badge from '@/components/ui/Badge.vue'
import Button from '@/components/ui/Button.vue'
import Modal from '@/components/ui/Modal.vue'
import TextInput from '@/components/ui/form/TextInput.vue'
import TextArea from '@/components/ui/form/TextArea.vue'
import { graphApi } from '@/services/graphApi'
import { ApiError } from '@/services/http'
import { NODE_TYPE_LABELS } from '@/services/types'
import type { NodeDetailDto } from '@/services/types'

interface RelatedEdgeView {
  id: number
  relation: string
  direction: 'in' | 'out'
  peerName: string
  peerId: number
}

const props = defineProps<{ nodeId: number | null }>()

const emit = defineEmits<{
  (e: 'close'): void
  (e: 'deleted', nodeId: number): void
  (e: 'updated', nodeId: number): void
}>()

const visible = computed(() => props.nodeId !== null)
const loading = ref(false)
const error = ref<string | null>(null)
const detail = ref<NodeDetailDto | null>(null)
const relatedEdges = ref<RelatedEdgeView[]>([])

const editing = ref(false)
const saving = ref(false)
const editForm = ref({ name: '', nameEn: '', definition: '' })
const editingRelation = ref<number | 'new' | null>(null)
const relationEdit = ref({ type: '' })
const newRelation = ref({ targetName: '', type: '', error: null as string | null })
const confirmDelete = ref(false)
const confirmDeleteEdge = ref<number | null>(null)

const nodeTypeLabel = computed(() =>
  detail.value ? (NODE_TYPE_LABELS[detail.value.type] ?? detail.value.type) : '',
)

/** AI 搜索生成并收录的节点（§16）：按 properties_json.origin 识别，审核通过前显示低干扰标识 */
const isAiGenerated = computed(() => {
  const properties = detail.value?.properties
  return (
    !!properties &&
    properties['origin'] === 'ai_search_generation' &&
    properties['reviewStatus'] === 'PENDING'
  )
})

watch(
  () => props.nodeId,
  (nodeId) => {
    editing.value = false
    editingRelation.value = null
    confirmDelete.value = false
    confirmDeleteEdge.value = null
    if (nodeId !== null) void load()
  },
)

async function load() {
  if (props.nodeId === null) return
  loading.value = true
  error.value = null
  try {
    const [data, neighborhood] = await Promise.all([
      graphApi.getNode(props.nodeId),
      graphApi.getNeighbors(props.nodeId, 1).catch(() => null),
    ])
    detail.value = data

    // 关联边从邻域子图推导（后端节点详情不含边列表）
    const edges: RelatedEdgeView[] = []
    if (neighborhood) {
      const nameById = new Map(neighborhood.nodes.map((node) => [node.id, node.name]))
      for (const edge of neighborhood.edges) {
        if (edge.source === props.nodeId) {
          edges.push({
            id: edge.id,
            relation: edge.relation,
            direction: 'out',
            peerName: nameById.get(edge.target) ?? `#${edge.target}`,
            peerId: edge.target,
          })
        } else if (edge.target === props.nodeId) {
          edges.push({
            id: edge.id,
            relation: edge.relation,
            direction: 'in',
            peerName: nameById.get(edge.source) ?? `#${edge.source}`,
            peerId: edge.source,
          })
        }
      }
    }
    relatedEdges.value = edges
  } catch (err) {
    error.value = err instanceof ApiError ? err.message : '节点详情加载失败'
  } finally {
    loading.value = false
  }
}

function startEdit() {
  if (!detail.value) return
  editForm.value = {
    name: detail.value.name,
    nameEn: detail.value.nameEn ?? '',
    definition: detail.value.definition ?? '',
  }
  editing.value = true
}

async function saveNode() {
  if (!detail.value) return
  saving.value = true
  try {
    await graphApi.updateNode(detail.value.id, {
      name: editForm.value.name,
      nameEn: editForm.value.nameEn,
      definition: editForm.value.definition,
    })
    editing.value = false
    emit('updated', detail.value.id)
    await load()
  } catch (err) {
    error.value = err instanceof ApiError ? err.message : '保存失败'
  } finally {
    saving.value = false
  }
}

async function deleteNode() {
  if (!detail.value) return
  try {
    await graphApi.deleteNode(detail.value.id)
    const deletedId = detail.value.id
    confirmDelete.value = false
    emit('close')
    emit('deleted', deletedId)
  } catch (err) {
    error.value = err instanceof ApiError ? err.message : '删除失败'
    confirmDelete.value = false
  }
}

function startEditRelation(edge: RelatedEdgeView) {
  editingRelation.value = edge.id
  relationEdit.value = { type: edge.relation }
}

async function saveRelation(edgeId: number) {
  try {
    await graphApi.updateEdge(edgeId, { relationType: relationEdit.value.type })
    editingRelation.value = null
    await load()
  } catch (err) {
    error.value = err instanceof ApiError ? err.message : '关系更新失败'
  }
}

async function deleteRelation() {
  if (confirmDeleteEdge.value === null) return
  try {
    await graphApi.deleteEdge(confirmDeleteEdge.value)
    confirmDeleteEdge.value = null
    await load()
  } catch (err) {
    error.value = err instanceof ApiError ? err.message : '关系删除失败'
    confirmDeleteEdge.value = null
  }
}

async function createRelation() {
  if (!detail.value) return
  newRelation.value.error = null
  const targetName = newRelation.value.targetName.trim()
  const relationType = newRelation.value.type.trim()
  if (!targetName || !relationType) {
    newRelation.value.error = '请填写对端节点名称与关系类型'
    return
  }
  const peer = relatedEdges.value.find((edge) => edge.peerName === targetName)
  let peerId = peer?.peerId
  if (!peerId) {
    // 在邻域里找不到时，按名称精确搜索
    try {
      const subgraph = await graphApi.search(targetName, 0, 1)
      const matched = subgraph.nodes.find((node) => node.name === targetName)
      peerId = matched?.id
    } catch {
      peerId = undefined
    }
  }
  if (!peerId) {
    newRelation.value.error = '未找到该节点，请先在图谱中创建或选择'
    return
  }
  try {
    await graphApi.createEdge({
      sourceNodeId: detail.value.id,
      targetNodeId: peerId,
      relationType,
    })
    editingRelation.value = null
    newRelation.value = { targetName: '', type: '', error: null }
    await load()
  } catch (err) {
    newRelation.value.error = err instanceof ApiError ? err.message : '关系创建失败'
  }
}
</script>
