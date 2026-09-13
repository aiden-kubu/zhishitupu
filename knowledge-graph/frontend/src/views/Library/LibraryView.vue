<template>
  <AdminLayout>
    <PageBreadcrumb page-title="知识库" description="让资料按主题有序归集，随时回到知识本身。">
      <template #actions>
        <Button size="sm" variant="outline" :start-icon="PlusIcon" @click="openCreate">手动建库</Button>
      </template>
    </PageBreadcrumb>

    <div class="grid grid-cols-2 overflow-hidden rounded-xl border border-gray-200 bg-white sm:grid-cols-4 dark:border-gray-800 dark:bg-gray-900">
      <div
        v-for="(stat, index) in statCards"
        :key="stat.label"
        class="flex items-center gap-3 px-4 py-4 sm:px-5"
        :class="[
          index > 1 ? 'border-t border-gray-100 sm:border-t-0 dark:border-gray-800' : '',
          index % 2 === 1 ? 'border-s border-gray-100 dark:border-gray-800' : '',
          index === 2 ? 'sm:border-s sm:border-gray-100 sm:dark:border-gray-800' : '',
        ]"
      >
        <span class="flex size-9 shrink-0 items-center justify-center rounded-lg bg-gray-50 text-gray-500 dark:bg-gray-800 dark:text-gray-400">
          <component :is="stat.icon" class="size-5" aria-hidden="true" />
        </span>
        <div>
          <p class="text-xs text-gray-500 dark:text-gray-400">{{ stat.label }}</p>
          <p class="mt-0.5 text-xl font-semibold tabular-nums text-gray-800 dark:text-white/90">{{ loading ? '—' : stat.value }}</p>
        </div>
      </div>
    </div>

    <div class="mb-4 mt-7 flex flex-wrap items-center justify-between gap-4">
      <div>
        <h2 class="flex items-center gap-2 text-base font-semibold text-gray-800 dark:text-white/90">
          我的知识库
          <span class="rounded-md bg-gray-100 px-1.5 py-0.5 text-xs font-medium tabular-nums text-gray-500 dark:bg-gray-800 dark:text-gray-400">{{ filteredLibraries.length }}</span>
        </h2>
        <p class="mt-1 text-xs leading-5 text-gray-500 dark:text-gray-400">AI 自动命名、合并同主题资料，你可以随时调整。</p>
      </div>
      <div class="flex max-w-full items-center gap-1 overflow-x-auto rounded-lg border border-gray-200 bg-white p-1 dark:border-gray-800 dark:bg-gray-900" role="group" aria-label="按知识库类型筛选">
        <button
          v-for="filter in typeFilters"
          :key="filter.value"
          type="button"
          :aria-pressed="typeFilter === filter.value"
          class="shrink-0 rounded-md px-3 py-1.5 text-xs font-medium transition focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-500"
          :class="typeFilter === filter.value ? 'bg-brand-50 text-brand-600 dark:bg-brand-500/15 dark:text-brand-400' : 'text-gray-500 hover:bg-gray-50 hover:text-gray-700 dark:text-gray-400 dark:hover:bg-gray-800 dark:hover:text-gray-200'"
          @click="typeFilter = filter.value"
        >{{ filter.label }}</button>
      </div>
    </div>

    <div v-if="loading" class="grid gap-4 lg:grid-cols-2" role="status" aria-label="知识库加载中">
      <div v-for="item in 4" :key="item" class="animate-pulse rounded-xl border border-gray-200 bg-white p-5 dark:border-gray-800 dark:bg-gray-900">
        <div class="h-9 w-2/3 rounded-lg bg-gray-100 dark:bg-gray-800"></div>
        <div class="mt-5 h-3 w-full rounded bg-gray-100 dark:bg-gray-800"></div>
        <div class="mt-2 h-3 w-4/5 rounded bg-gray-100 dark:bg-gray-800"></div>
        <div class="mt-6 h-8 w-full rounded bg-gray-50 dark:bg-gray-800"></div>
      </div>
    </div>
    <div v-else-if="error">
      <Alert variant="error" title="加载失败" :message="error" />
      <div class="mt-3">
        <Button size="sm" variant="outline" @click="loadAll">重试</Button>
      </div>
    </div>
    <div
      v-else-if="filteredLibraries.length === 0"
      class="rounded-xl border border-dashed border-gray-300 bg-white px-5 py-14 text-center dark:border-gray-700 dark:bg-gray-900"
    >
      <span class="mx-auto mb-4 flex size-12 items-center justify-center rounded-xl bg-brand-50 text-brand-500 dark:bg-brand-500/10 dark:text-brand-400"><FolderIcon class="size-6" aria-hidden="true" /></span>
      <p class="text-base font-medium text-gray-700 dark:text-gray-300">{{ typeFilter ? '没有符合此类型的知识库' : '还没有知识库' }}</p>
      <p class="mx-auto mt-2 max-w-sm text-sm leading-6 text-gray-500 dark:text-gray-400">{{ typeFilter ? '试试其他类型，或查看已整理的全部知识库。' : '从顶部「导入资料」开始，AI 会识别内容，并自动归入已有主题或创建新知识库。' }}</p>
      <Button v-if="typeFilter" class="mt-4" size="sm" variant="outline" @click="typeFilter = ''">查看全部知识库</Button>
    </div>

    <!-- 知识库列表 -->
    <div v-else class="grid items-start gap-4 lg:grid-cols-2">
      <article
        v-for="library in filteredLibraries"
        :key="library.id"
        class="min-w-0 overflow-hidden rounded-xl border bg-white transition-shadow hover:shadow-theme-sm dark:bg-gray-900"
        :class="expandedLibraryId === library.id || documentsLibraryId === library.id ? 'border-brand-300 dark:border-brand-500/40' : 'border-gray-200 dark:border-gray-800'"
      >
        <div class="p-5">
          <div class="flex items-start gap-3">
            <span class="flex size-10 shrink-0 items-center justify-center rounded-lg bg-brand-50 text-brand-500 dark:bg-brand-500/10 dark:text-brand-400"><FolderIcon class="size-5" aria-hidden="true" /></span>
            <div class="min-w-0 flex-1">
              <h3 class="break-words text-base font-semibold leading-6 text-gray-800 dark:text-white/90">
                {{ library.name }}
              </h3>
              <div class="mt-1 flex flex-wrap items-center gap-2 text-xs text-gray-500 dark:text-gray-400">
                <span>{{ libraryTypeLabel(library.type) }}知识库</span>
                <Badge v-if="library.status === 'pending'" color="warning" size="sm">等待 AI 整理</Badge>
              </div>
            </div>
            <div class="flex shrink-0 items-center gap-1">
              <button
                v-if="library.status !== 'pending'"
                type="button"
                class="flex size-8 items-center justify-center rounded-lg text-gray-400 transition hover:bg-gray-100 hover:text-gray-700 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-500 dark:text-gray-500 dark:hover:bg-gray-800 dark:hover:text-gray-200"
                :aria-label="`编辑${library.name}`"
                title="编辑知识库与别名"
                @click="editLibrary(library)"
              ><SettingsIcon class="size-[18px]" aria-hidden="true" /></button>
              <button
                type="button"
                class="flex size-8 items-center justify-center rounded-lg text-gray-400 transition hover:bg-error-50 hover:text-error-600 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-error-500 dark:text-gray-500 dark:hover:bg-error-500/10 dark:hover:text-error-400"
                :aria-label="`删除${library.name}`"
                title="删除知识库"
                @click="askDelete(library)"
              ><TrashIcon class="size-[18px]" aria-hidden="true" /></button>
            </div>
          </div>
          <p class="mt-4 line-clamp-2 min-h-10 text-sm leading-5 text-gray-500 dark:text-gray-400">
            {{ library.description || '暂未添加描述，可在编辑中补充收录范围。' }}
          </p>
          <div class="mt-5 flex flex-wrap items-center justify-between gap-3 border-t border-gray-100 pt-4 dark:border-gray-800">
            <div class="flex flex-wrap items-center gap-3 text-xs text-gray-500 dark:text-gray-400">
              <span><strong class="font-semibold tabular-nums text-gray-700 dark:text-gray-200">{{ library.documentCount ?? 0 }}</strong> 份资料</span>
              <span><strong class="font-semibold tabular-nums text-gray-700 dark:text-gray-200">{{ library.nodeCount ?? 0 }}</strong> 个知识点</span>
              <span><strong class="font-semibold tabular-nums text-gray-700 dark:text-gray-200">{{ library.edgeCount ?? 0 }}</strong> 条关系</span>
            </div>
            <div class="flex items-center gap-2">
              <Button
                size="sm"
                variant="outline"
                :aria-expanded="documentsLibraryId === library.id"
                :aria-controls="`library-docs-${library.id}`"
                @click="toggleDocuments(library)"
              >
                资料 {{ library.documentCount ?? 0 }}
                <ChevronDownIcon class="size-4 transition-transform" :class="documentsLibraryId === library.id ? 'rotate-180' : ''" aria-hidden="true" />
              </Button>
              <Button
                size="sm"
                variant="outline"
                class="!bg-brand-50 !text-brand-600 !ring-brand-100 hover:!bg-brand-100 dark:!bg-brand-500/10 dark:!text-brand-400 dark:!ring-brand-500/20"
                :aria-expanded="expandedLibraryId === library.id"
                :aria-controls="`library-nodes-${library.id}`"
                @click="toggleNodes(library)"
              >
                {{ expandedLibraryId === library.id ? '收起知识' : '查看知识' }}
                <ChevronDownIcon class="size-4 transition-transform" :class="expandedLibraryId === library.id ? 'rotate-180' : ''" aria-hidden="true" />
              </Button>
            </div>
          </div>
        </div>

        <!-- 资料列表：标题 / 生命周期 / 可信度 / 标签（属性面板） -->
        <div v-if="documentsLibraryId === library.id" :id="`library-docs-${library.id}`" class="border-t border-gray-100 bg-gray-50/70 px-5 py-4 dark:border-gray-800 dark:bg-gray-800/30">
          <p v-if="docsLoading" class="text-sm text-gray-500 dark:text-gray-400" role="status">正在加载资料…</p>
          <div v-else-if="docsError" role="alert">
            <p class="text-sm text-error-500 dark:text-error-400">{{ docsError }}</p>
            <button type="button" class="mt-2 text-sm font-medium text-brand-600 hover:underline dark:text-brand-400" @click="loadDocuments(library)">重新加载</button>
          </div>
          <div v-else-if="documents.length === 0" class="py-2">
            <p class="text-sm font-medium text-gray-700 dark:text-gray-300">本库还没有资料</p>
            <p class="mt-1 text-xs leading-5 text-gray-500 dark:text-gray-400">从顶部「导入资料」上传，AI 整理后会显示在这里。</p>
          </div>
          <div v-else class="space-y-3">
            <div
              v-for="doc in documents"
              :key="doc.id"
              class="rounded-xl border border-gray-200 bg-white p-3.5 dark:border-gray-700 dark:bg-gray-900"
            >
              <div class="flex flex-wrap items-start justify-between gap-2">
                <div class="min-w-0">
                  <p class="break-words text-sm font-medium text-gray-800 dark:text-white/90">{{ doc.title || doc.originalName }}</p>
                  <p class="mt-0.5 text-xs text-gray-400">
                    {{ doc.originalName }} · {{ doc.extension.toUpperCase() }} · {{ formatSize(doc.sizeBytes) }} ·
                    更新 {{ shortTime(doc.updatedAt || doc.createdAt) }}
                  </p>
                  <div class="mt-1.5 flex flex-wrap items-center gap-1.5">
                    <Badge :color="lifecycleColor(doc.lifecycleStatus)" size="sm">{{ lifecycleLabel(doc.lifecycleStatus) }}</Badge>
                    <Badge v-if="doc.verification?.hash" color="light" size="sm" title="上传时已做 SHA-256 完整性校验">哈希已校验</Badge>
                    <Badge
                      v-if="doc.verification?.aiReview"
                      :color="doc.verification.aiReview.rejected === 0 ? 'success' : 'warning'"
                      size="sm"
                      :title="`AI 复审模型 ${doc.verification.aiReview.model} · ${doc.verification.aiReview.reviewedAt}`"
                    >
                      AI 复审 {{ doc.verification.aiReview.approved }}/{{ doc.verification.aiReview.total }}
                    </Badge>
                    <Badge v-if="doc.verification?.human?.checked" color="info" size="sm" :title="doc.verification.human.note || '人工校对'">人工已校对</Badge>
                  </div>
                </div>
                <Button size="sm" variant="outline" class="!px-2.5 !py-1.5" @click="openDocEditor(doc)">编辑</Button>
              </div>
              <div class="mt-2.5 flex flex-wrap items-center gap-1.5">
                <span
                  v-for="tag in doc.tags"
                  :key="tag.tag"
                  class="inline-flex items-center gap-1 rounded-full border px-2 py-0.5 text-xs"
                  :class="tag.source === 'ai'
                    ? 'border-gray-200 bg-gray-50 text-gray-500 dark:border-gray-700 dark:bg-gray-800 dark:text-gray-400'
                    : 'border-brand-200 bg-brand-50 text-brand-600 dark:border-brand-500/30 dark:bg-brand-500/10 dark:text-brand-300'"
                  :title="tag.source === 'ai' ? 'AI 自动整理标签' : '人工标签'"
                >
                  {{ tag.tag }}
                  <button
                    type="button"
                    class="text-gray-400 transition hover:text-error-500 focus-visible:outline-none"
                    :aria-label="`删除标签 ${tag.tag}`"
                    @click="removeTag(doc, tag.tag)"
                  >×</button>
                </span>
                <input
                  v-model="tagInputs[doc.id]"
                  type="text"
                  maxlength="100"
                  placeholder="+ 标签，回车添加"
                  class="w-36 rounded-full border border-gray-200 bg-white px-2.5 py-0.5 text-xs text-gray-700 placeholder:text-gray-400 focus:border-brand-400 focus:outline-none focus:ring-1 focus:ring-brand-400 dark:border-gray-700 dark:bg-gray-900 dark:text-gray-300"
                  @keydown.enter.prevent="addTag(doc)"
                />
              </div>
            </div>
          </div>
        </div>

        <!-- 节点列表（点击跳转图谱工作台并聚焦，§9.1） -->
        <div v-if="expandedLibraryId === library.id" :id="`library-nodes-${library.id}`" class="border-t border-gray-100 bg-gray-50/70 px-5 py-4 dark:border-gray-800 dark:bg-gray-800/30">
          <p v-if="nodesLoading" class="text-sm text-gray-500 dark:text-gray-400" role="status">正在加载知识点…</p>
          <div v-else-if="nodesError" role="alert">
            <p class="text-sm text-error-500 dark:text-error-400">{{ nodesError }}</p>
            <button type="button" class="mt-2 text-sm font-medium text-brand-600 hover:underline dark:text-brand-400" @click="loadNodes(library)">重新加载</button>
          </div>
          <div v-else-if="libraryNodes.length === 0" class="py-2">
            <p class="text-sm font-medium text-gray-700 dark:text-gray-300">还没有已入库的知识点</p>
            <p class="mt-1 text-xs leading-5 text-gray-500 dark:text-gray-400">资料完成识别与复审、入库后，即可在这里查看。</p>
            <router-link to="/processing" class="mt-3 inline-flex text-xs font-medium text-brand-600 hover:underline dark:text-brand-400">查看处理进度 →</router-link>
          </div>
          <div v-else>
            <p class="mb-3 text-xs text-gray-500 dark:text-gray-400">选择知识点，在图谱中探索关联{{ (library.nodeCount ?? 0) > libraryNodes.length ? ` · 当前展示 ${libraryNodes.length} 个` : '' }}</p>
            <div class="flex max-h-64 flex-wrap gap-2 overflow-y-auto">
            <button
              v-for="node in libraryNodes"
              :key="node.id"
              class="rounded-lg border border-gray-200 bg-white px-2.5 py-1.5 text-start text-sm text-gray-700 transition hover:border-brand-300 hover:text-brand-600 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-500 dark:border-gray-700 dark:bg-gray-900 dark:text-gray-300 dark:hover:border-brand-500/40 dark:hover:text-brand-400"
              @click="gotoNode(node)"
            >
              {{ node.name }}
              <span class="text-xs text-gray-400">· {{ NODE_TYPE_LABELS[node.type] ?? node.type }}</span>
            </button>
            </div>
          </div>
        </div>
      </article>
    </div>

    <!-- 新建知识库 -->
    <Modal v-if="createOpen" full-screen-backdrop @close="createOpen = false">
      <template #body>
        <div class="relative mx-4 w-full max-w-[480px] rounded-3xl bg-white p-6 dark:bg-gray-900">
          <h4 class="text-xl font-semibold text-gray-800 dark:text-white/90">{{ editingId ? '编辑知识库' : '新建知识库' }}</h4>
          <p class="mt-2 text-sm leading-6 text-gray-500 dark:text-gray-400">{{ editingId ? '调整名称、描述和别名；别名可帮助 AI 按同义名称归库。' : '按需要创建一个收录主题，也可以直接上传资料让 AI 整理。' }}</p>
          <div class="mt-5 space-y-4">
            <div>
              <label for="library-name" class="mb-1.5 block text-sm font-medium text-gray-700 dark:text-gray-400">
                名称 <span class="text-error-500">*</span>
              </label>
              <TextInput id="library-name" v-model="createForm.name" placeholder="如：计算机网络" :disabled="submitting" />
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
              <label for="library-description" class="mb-1.5 block text-sm font-medium text-gray-700 dark:text-gray-400">描述 <span class="font-normal text-gray-400">（选填）</span></label>
              <TextArea id="library-description" v-model="createForm.description" :rows="3" placeholder="一句话说明该知识库收录的内容" />
            </div>
            <div v-if="editingId">
              <label for="library-aliases" class="mb-1.5 block text-sm font-medium text-gray-700 dark:text-gray-400">
                别名 <span class="font-normal text-gray-400">（选填，逗号分隔，同义名称归库依据）</span>
              </label>
              <TextInput id="library-aliases" v-model="createForm.aliases" placeholder="如：数据结构,DS" :disabled="submitting" />
            </div>
            <p v-if="formError" class="text-sm text-error-500">{{ formError }}</p>
          </div>
          <div class="mt-6 flex justify-end gap-3">
            <Button variant="outline" size="sm" :disabled="submitting" @click="createOpen = false">取消</Button>
            <Button size="sm" :disabled="submitting || !createForm.name.trim()" @click="createLibrary">
              {{ submitting ? '保存中…' : '保存' }}
            </Button>
          </div>
        </div>
      </template>
    </Modal>

    <!-- 资料编辑：标题 / 生命周期 -->
    <Modal v-if="docEditor" full-screen-backdrop @close="docEditor = null">
      <template #body>
        <div class="relative mx-4 w-full max-w-[480px] rounded-3xl bg-white p-6 dark:bg-gray-900">
          <h4 class="break-words text-xl font-semibold text-gray-800 dark:text-white/90">编辑资料</h4>
          <p class="mt-2 break-all text-xs text-gray-400">{{ docEditor.doc.originalName }}</p>
          <div class="mt-5 space-y-4">
            <div>
              <label for="doc-title" class="mb-1.5 block text-sm font-medium text-gray-700 dark:text-gray-400">标题</label>
              <TextInput id="doc-title" v-model="docEditor.form.title" :disabled="savingDoc" />
            </div>
            <div>
              <label class="mb-1.5 block text-sm font-medium text-gray-700 dark:text-gray-400">生命周期</label>
              <SelectInput
                v-model="docEditor.form.lifecycleStatus"
                :options="[
                  { value: 'active', label: '在用' },
                  { value: 'archived', label: '已归档' },
                  { value: 'outdated', label: '已过期' },
                ]"
              />
            </div>
            <label class="flex items-center gap-2 text-sm text-gray-700 dark:text-gray-300">
              <input
                v-model="docEditor.humanChecked"
                type="checkbox"
                class="size-4 rounded border-gray-300 text-brand-600 focus:ring-brand-400 dark:border-gray-700 dark:bg-gray-900"
              />
              标记为已人工校对（记录到资料可信度）
            </label>
            <p v-if="docFormError" class="text-sm text-error-500">{{ docFormError }}</p>
          </div>
          <div class="mt-6 flex justify-end gap-3">
            <Button variant="outline" size="sm" @click="docEditor = null">取消</Button>
            <Button size="sm" :disabled="savingDoc" @click="saveDocEditor">{{ savingDoc ? '保存中…' : '保存' }}</Button>
          </div>
        </div>
      </template>
    </Modal>

    <!-- 删除确认：显示影响范围（§9.1） -->
    <Modal v-if="deleteTarget" full-screen-backdrop @close="deleteTarget = null">
      <template #body>
        <div class="relative mx-4 w-full max-w-[460px] rounded-3xl bg-white p-6 dark:bg-gray-900">
          <span class="mb-4 flex size-11 items-center justify-center rounded-xl bg-error-50 text-error-500 dark:bg-error-500/10 dark:text-error-400"><TrashIcon class="size-5" aria-hidden="true" /></span>
          <h4 class="break-words text-xl font-semibold leading-7 text-gray-800 dark:text-white/90">
            确认删除「{{ deleteTarget.name }}」
          </h4>
          <p class="mt-2 text-sm text-gray-500 dark:text-gray-400">
            此操作将影响：
          </p>
          <ul class="mt-2 space-y-1 text-sm text-gray-600 dark:text-gray-300">
            <li>· 关联资料 {{ deleteTarget.documentCount ?? 0 }} 份：仅属于本库的资料会删除，共享资料保留</li>
            <li>· 关联知识点 {{ deleteTarget.nodeCount ?? 0 }} 个：仅属于本库的知识点、相关关系与对话会删除，其他库共享的知识点保留</li>
          </ul>
          <p v-if="deleteError" class="mt-4 text-sm text-error-500 dark:text-error-400" role="alert">{{ deleteError }}</p>
          <div class="mt-6 flex justify-end gap-3">
            <Button variant="outline" size="sm" :disabled="submitting" @click="deleteTarget = null">取消</Button>
            <Button size="sm" :disabled="submitting" class="!bg-error-500 hover:!bg-error-600" @click="removeLibrary">
              {{ submitting ? '删除中…' : '确认删除' }}
            </Button>
          </div>
        </div>
      </template>
    </Modal>
  </AdminLayout>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
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
import { BoxCubeIcon, ChevronDownIcon, DocsIcon, FolderIcon, GridIcon, PlusIcon, SettingsIcon, TrashIcon } from '@/icons'
import { libraryApi } from '@/services/libraryApi'
import { insightsApi } from '@/services/insightsApi'
import { graphApi } from '@/services/graphApi'
import { ingestionApi } from '@/services/ingestionApi'
import { ApiError } from '@/services/http'
import { NODE_TYPE_LABELS } from '@/services/types'
import type { DocumentDto, GraphNodeDto, InsightSummaryDto, LibraryDto } from '@/services/types'

const router = useRouter()

const summary = ref<InsightSummaryDto | null>(null)
const libraries = ref<LibraryDto[]>([])
const loading = ref(true)
const error = ref<string | null>(null)
const typeFilter = ref('')
const submitting = ref(false)
const formError = ref<string | null>(null)
const deleteError = ref<string | null>(null)

const createOpen = ref(false)
const editingId = ref<number | null>(null)
const createForm = ref({ name: '', type: 'course', description: '', aliases: '' })
const deleteTarget = ref<LibraryDto | null>(null)

const expandedLibraryId = ref<number | null>(null)
const libraryNodes = ref<GraphNodeDto[]>([])
const nodesLoading = ref(false)
const nodesError = ref<string | null>(null)

// 资料面板（文档标签 / 标题 / 生命周期 / 可信度）
const documentsLibraryId = ref<number | null>(null)
const documents = ref<DocumentDto[]>([])
const docsLoading = ref(false)
const docsError = ref<string | null>(null)
const tagInputs = reactive<Record<number, string>>({})
const docEditor = ref<{ doc: DocumentDto; form: { title: string; lifecycleStatus: string }; humanChecked: boolean } | null>(null)
const savingDoc = ref(false)
const docFormError = ref<string | null>(null)

const typeFilters = [
  { value: '', label: '全部' },
  { value: 'course', label: '课程' },
  { value: 'book', label: '书籍' },
  { value: 'topic', label: '主题' },
]

const filteredLibraries = computed(() =>
  typeFilter.value
    ? libraries.value.filter((library) => library.type === typeFilter.value)
    : libraries.value,
)

const statCards = computed(() => [
  { label: '知识库', value: libraries.value.length, icon: FolderIcon },
  { label: '源资料', value: summary.value?.documentCount ?? '—', icon: DocsIcon },
  { label: '已入库知识点', value: summary.value?.nodeCount ?? '—', icon: BoxCubeIcon },
  { label: '知识关系', value: summary.value?.edgeCount ?? '—', icon: GridIcon },
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

function lifecycleLabel(status?: string | null): string {
  switch (status) {
    case 'active':
      return '在用'
    case 'archived':
      return '已归档'
    case 'outdated':
      return '已过期'
    default:
      return '在用'
  }
}

function lifecycleColor(status?: string | null): 'success' | 'warning' | 'light' {
  if (status === 'archived') return 'light'
  if (status === 'outdated') return 'warning'
  return 'success'
}

function formatSize(bytes: number): string {
  if (bytes >= 1024 * 1024) return (bytes / 1024 / 1024).toFixed(1) + ' MB'
  if (bytes >= 1024) return (bytes / 1024).toFixed(0) + ' KB'
  return bytes + ' B'
}

function shortTime(value?: string): string {
  if (!value) return '—'
  return value.slice(0, 10)
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
    error.value = err instanceof ApiError ? err.message : '暂时无法加载知识库，请稍后重试'
  } finally {
    loading.value = false
  }
}

function openCreate() {
  editingId.value = null
  createForm.value = { name: '', type: 'topic', description: '', aliases: '' }
  formError.value = null
  createOpen.value = true
}
function editLibrary(library: LibraryDto) {
  editingId.value = library.id
  createForm.value = {
    name: library.name,
    type: library.type,
    description: library.description ?? '',
    aliases: (library.aliases ?? []).join(', '),
  }
  formError.value = null
  createOpen.value = true
}
async function createLibrary() {
  if (submitting.value || !createForm.value.name.trim()) return
  submitting.value = true
  formError.value = null
  try {
    const payload = {
      name: createForm.value.name.trim(),
      type: createForm.value.type,
      description: createForm.value.description.trim(),
    }
    if (editingId.value) {
      const updated = await libraryApi.update(editingId.value, payload)
      // 别名整体替换（编辑态才出现该字段）
      const aliases = createForm.value.aliases
        .split(/[,，、;；]/)
        .map((item) => item.trim())
        .filter((item) => item.length > 0)
      await libraryApi.updateAliases(updated.id, aliases)
    } else {
      await libraryApi.create(payload)
    }
    createOpen.value = false
    createForm.value = { name: '', type: 'course', description: '', aliases: '' }
    await loadAll()
  } catch (err) {
    formError.value = err instanceof ApiError ? err.message : '创建失败，请稍后重试'
  } finally {
    submitting.value = false
  }
}

function askDelete(library: LibraryDto) {
  deleteError.value = null
  deleteTarget.value = library
}

async function removeLibrary() {
  if (!deleteTarget.value || submitting.value) return
  submitting.value = true
  deleteError.value = null
  try {
    await libraryApi.remove(deleteTarget.value.id)
    deleteTarget.value = null
    await loadAll()
  } catch (err) {
    deleteError.value = err instanceof ApiError ? err.message : '删除失败，请稍后重试'
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
  await loadNodes(library)
}

async function loadNodes(library: LibraryDto) {
  nodesLoading.value = true
  nodesError.value = null
  libraryNodes.value = []
  try {
    const subgraph = await graphApi.getOverview(library.id, 100)
    if (expandedLibraryId.value === library.id) libraryNodes.value = subgraph.nodes
  } catch (err) {
    if (expandedLibraryId.value === library.id) nodesError.value = err instanceof ApiError ? err.message : '知识点加载失败，请重试'
  } finally {
    if (expandedLibraryId.value === library.id) nodesLoading.value = false
  }
}

// ---------------------------------------------------------------- 资料面板

async function toggleDocuments(library: LibraryDto) {
  if (documentsLibraryId.value === library.id) {
    documentsLibraryId.value = null
    return
  }
  documentsLibraryId.value = library.id
  await loadDocuments(library)
}

async function loadDocuments(library: LibraryDto) {
  docsLoading.value = true
  docsError.value = null
  documents.value = []
  try {
    const page = await ingestionApi.listDocuments({ libraryId: library.id, pageSize: 100 })
    if (documentsLibraryId.value === library.id) documents.value = page.items
  } catch (err) {
    if (documentsLibraryId.value === library.id) docsError.value = err instanceof ApiError ? err.message : '资料加载失败，请重试'
  } finally {
    if (documentsLibraryId.value === library.id) docsLoading.value = false
  }
}

function openDocEditor(doc: DocumentDto) {
  docEditor.value = {
    doc,
    form: {
      title: doc.title || doc.originalName,
      lifecycleStatus: doc.lifecycleStatus || 'active',
    },
    humanChecked: doc.verification?.human?.checked ?? false,
  }
  docFormError.value = null
}

async function saveDocEditor() {
  if (!docEditor.value || savingDoc.value) return
  savingDoc.value = true
  docFormError.value = null
  try {
    let updated = await ingestionApi.updateDocumentMetadata(docEditor.value.doc.id, {
      title: docEditor.value.form.title.trim(),
      lifecycleStatus: (docEditor.value.form.lifecycleStatus ?? 'active') as 'active' | 'archived' | 'outdated',
    })
    if (docEditor.value.humanChecked && !docEditor.value.doc.verification?.human?.checked) {
      updated = await ingestionApi.setDocumentVerification(docEditor.value.doc.id, {
        humanChecked: true,
        note: '编辑资料时标记人工校对',
      })
    }
    const index = documents.value.findIndex((d) => d.id === updated.id)
    if (index >= 0) documents.value[index] = updated
    docEditor.value = null
  } catch (err) {
    docFormError.value = err instanceof ApiError ? err.message : '保存失败，请重试'
  } finally {
    savingDoc.value = false
  }
}

async function addTag(doc: DocumentDto) {
  const tag = (tagInputs[doc.id] || '').trim()
  if (!tag) return
  try {
    const updated = await ingestionApi.addDocumentTag(doc.id, tag)
    replaceDocument(updated)
    tagInputs[doc.id] = ''
  } catch (err) {
    docsError.value = err instanceof ApiError ? err.message : '标签添加失败'
  }
}

async function removeTag(doc: DocumentDto, tag: string) {
  try {
    const updated = await ingestionApi.removeDocumentTag(doc.id, tag)
    replaceDocument(updated)
  } catch (err) {
    docsError.value = err instanceof ApiError ? err.message : '标签删除失败'
  }
}

function replaceDocument(updated: DocumentDto) {
  const index = documents.value.findIndex((d) => d.id === updated.id)
  if (index >= 0) documents.value[index] = updated
}

function gotoNode(node: GraphNodeDto) {
  void router.push({ path: '/', query: { node: String(node.id), depth: '1', mode: 'local' } })
}

onMounted(loadAll)
</script>
