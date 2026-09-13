<template>
  <AdminLayout>
    <PageBreadcrumb page-title="系统设置" />

    <div v-if="loading" class="rounded-2xl border border-gray-200 p-10 text-center dark:border-gray-800">
      <p class="text-sm text-gray-500 dark:text-gray-400">设置加载中…</p>
    </div>

    <div v-else-if="error" class="space-y-3">
      <Alert variant="error" title="加载失败" :message="error" />
      <Button size="sm" variant="outline" @click="loadAll">重试</Button>
    </div>

    <div v-else class="grid grid-cols-1 gap-5 xl:grid-cols-2">
      <!-- 模型管理：多模型档案 -->
      <ComponentCard
        title="模型管理"
        desc="可添加多个模型档案，分别承担知识抽取、节点问答、视觉解析等任务；不同模型能力不同，请按需勾选"
        class="xl:col-span-2"
      >
        <div class="mb-4 flex flex-wrap items-center justify-between gap-3">
          <p class="text-xs text-gray-400">
            第一个添加的模型自动设为默认；带
            <span class="font-medium text-blue-light-500">视觉能力</span>
            标记的模型才能识别图片与扫描页
          </p>
          <Button size="sm" :start-icon="PlusIcon" @click="openCreate">＋ 添加模型</Button>
        </div>

        <!-- 空状态 -->
        <Alert
          v-if="profiles.length === 0"
          variant="warning"
          title="尚未添加任何模型"
          message="AI 抽取与节点问答功能将显示「未配置」状态（§15），不影响图谱浏览。点击右上角「＋ 添加模型」接入你的第一个模型。"
        />

        <!-- 档案列表 -->
        <div v-else class="space-y-3">
          <div
            v-for="profile in profiles"
            :key="profile.id"
            class="rounded-2xl border p-4"
            :class="
              profile.isDefault
                ? 'border-brand-200 bg-brand-50/40 dark:border-brand-500/30 dark:bg-brand-500/[0.06]'
                : 'border-gray-200 dark:border-gray-800'
            "
          >
            <div class="flex flex-wrap items-start justify-between gap-3">
              <div class="min-w-0">
                <div class="flex flex-wrap items-center gap-2">
                  <p class="text-sm font-semibold text-gray-800 dark:text-white/90">
                    {{ profile.name }}
                  </p>
                  <Badge v-if="profile.isDefault" color="success" size="sm">默认</Badge>
                  <Badge v-if="!profile.enabled" color="light" size="sm">已停用</Badge>
                  <Badge :color="profile.vision ? 'info' : 'light'" size="sm">
                    {{ profile.vision ? '视觉能力' : '纯文本' }}
                  </Badge>
                </div>
                <p class="mt-1 truncate text-xs text-gray-500 dark:text-gray-400">
                  {{ profile.model }}
                  <span class="text-gray-300 dark:text-gray-600"> · </span>
                  {{ profile.baseUrl }}
                </p>
                <p class="mt-0.5 text-xs text-gray-400">
                  Key：{{ profile.apiKeyConfigured ? profile.apiKeyMasked : '未配置' }}
                  · 超时 {{ profile.timeoutMs }}ms · 最大输出 {{ profile.maxOutputTokens }}
                </p>
              </div>
              <div class="flex shrink-0 flex-wrap items-center gap-2">
                <button
                  v-if="!profile.isDefault"
                  class="rounded-lg px-2.5 py-1.5 text-xs font-medium text-gray-500 hover:bg-gray-100 dark:text-gray-400 dark:hover:bg-white/[0.05]"
                  @click="setDefault(profile)"
                >
                  设为默认
                </button>
                <Button
                  size="sm"
                  variant="outline"
                  class="!px-3 !py-1.5"
                  :disabled="testingId === profile.id"
                  @click="testProfile(profile)"
                >
                  {{ testingId === profile.id ? '测试中…' : '测试连接' }}
                </Button>
                <Button size="sm" variant="outline" class="!px-3 !py-1.5" @click="openEdit(profile)">
                  编辑
                </Button>
                <button
                  class="rounded-lg px-2.5 py-1.5 text-xs font-medium text-error-500 hover:bg-error-50 dark:hover:bg-error-500/10"
                  @click="deleteTarget = profile"
                >
                  删除
                </button>
              </div>
            </div>
            <p
              v-if="testResults[profile.id]"
              class="mt-2.5 border-t border-gray-100 pt-2.5 text-xs dark:border-gray-800"
              :class="testResults[profile.id].ok ? 'text-success-600 dark:text-success-400' : 'text-error-500'"
            >
              {{ testResults[profile.id].message }}
            </p>
          </div>
        </div>
      </ComponentCard>

      <!-- 存储 -->
      <ComponentCard title="存储" desc="上传目录由环境变量提供，大小限制可调">
        <div class="space-y-4">
          <div>
            <label class="mb-1.5 block text-sm font-medium text-gray-700 dark:text-gray-400">上传目录（只读）</label>
            <TextInput :model-value="settings?.storage.root ?? ''" disabled />
          </div>
          <div>
            <label class="mb-1.5 block text-sm font-medium text-gray-700 dark:text-gray-400">单文件上限（MB）</label>
            <TextInput v-model="storageForm.maxUploadSizeMb" type="number" />
          </div>
          <div class="flex items-center gap-3">
            <Button size="sm" :disabled="saving === 'storage'" @click="saveStorage">
              {{ saving === 'storage' ? '保存中…' : '保存' }}
            </Button>
            <Badge v-if="savedSection === 'storage'" color="success" size="sm">已保存</Badge>
          </div>
        </div>
      </ComponentCard>

      <!-- 图谱偏好 -->
      <ComponentCard title="图谱" desc="搜索深度与画布规模限制（§6.4）">
        <div class="space-y-4">
          <div>
            <label class="mb-1.5 block text-sm font-medium text-gray-700 dark:text-gray-400">默认搜索深度</label>
            <SelectInput
              v-model="graphForm.defaultDepth"
              :options="[
                { value: '1', label: '1 层' },
                { value: '2', label: '2 层' },
              ]"
            />
          </div>
          <div>
            <label class="mb-1.5 block text-sm font-medium text-gray-700 dark:text-gray-400">画布节点上限</label>
            <TextInput v-model="graphForm.maxNodes" type="number" />
          </div>
          <div class="flex items-center gap-3">
            <Button size="sm" :disabled="saving === 'graph'" @click="saveGraph">
              {{ saving === 'graph' ? '保存中…' : '保存' }}
            </Button>
            <Badge v-if="savedSection === 'graph'" color="success" size="sm">已保存</Badge>
          </div>
        </div>
      </ComponentCard>

      <!-- 关系字典 -->
      <ComponentCard
        title="关系类型字典"
        desc="审核与画布编辑时可选的关系类型（持久化随阶段 E 交付）"
        class="xl:col-span-2"
      >
        <div class="overflow-x-auto">
          <table class="w-full min-w-[520px] text-sm">
            <thead>
              <tr class="border-b border-gray-100 text-gray-500 dark:border-gray-800 dark:text-gray-400">
                <th class="px-3 py-2.5 text-start font-medium">关系名称</th>
                <th class="px-3 py-2.5 text-start font-medium">方向</th>
                <th class="px-3 py-2.5 text-start font-medium">启用</th>
              </tr>
            </thead>
            <tbody>
              <tr
                v-for="relation in relationTypes"
                :key="relation.name"
                class="border-b border-gray-100 last:border-0 dark:border-gray-800"
              >
                <td class="px-3 py-2.5 text-gray-700 dark:text-gray-300">{{ relation.name }}</td>
                <td class="px-3 py-2.5 text-gray-500 dark:text-gray-400">{{ relation.direction }}</td>
                <td class="px-3 py-2.5">
                  <Badge :color="relation.enabled ? 'success' : 'light'" size="sm">
                    {{ relation.enabled ? '启用' : '停用' }}
                  </Badge>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </ComponentCard>
    </div>

    <!-- 添加/编辑模型弹窗 -->
    <Modal v-if="formOpen" full-screen-backdrop @close="formOpen = false">
      <template #body>
        <div class="relative m-4 w-full max-w-[560px] overflow-y-auto rounded-3xl bg-white p-6 lg:p-8 dark:bg-gray-900">
          <h3 class="text-title-md font-semibold text-gray-800 dark:text-white/90">
            {{ editingId === null ? '添加模型' : '编辑模型' }}
          </h3>
          <p class="mt-1 text-sm text-gray-500 dark:text-gray-400">
            OpenAI 兼容协议（/chat/completions）；密钥加密存储，保存后仅显示掩码
          </p>

          <div class="mt-5 grid grid-cols-1 gap-x-5 gap-y-4 sm:grid-cols-2">
            <div class="sm:col-span-2">
              <label class="mb-1.5 block text-sm font-medium text-gray-700 dark:text-gray-400">
                模型名称 <span class="text-error-500">*</span>
              </label>
              <TextInput v-model="profileForm.name" placeholder="如：deepseek-知识抽取 / glm-视觉解析" />
            </div>
            <div class="sm:col-span-2">
              <label class="mb-1.5 block text-sm font-medium text-gray-700 dark:text-gray-400">
                API 地址 <span class="text-error-500">*</span>
              </label>
              <TextInput v-model="profileForm.baseUrl" placeholder="https://api.deepseek.com/v1" />
            </div>
            <div>
              <label class="mb-1.5 block text-sm font-medium text-gray-700 dark:text-gray-400">
                模型标识 <span class="text-error-500">*</span>
              </label>
              <TextInput v-model="profileForm.model" placeholder="deepseek-chat / glm-4v-flash" />
            </div>
            <div>
              <label class="mb-1.5 block text-sm font-medium text-gray-700 dark:text-gray-400">API Key</label>
              <TextInput
                v-model="profileForm.apiKey"
                type="password"
                :placeholder="editingApiKeyMasked || 'sk-…'"
              />
              <p v-if="editingId !== null" class="mt-1 text-xs text-gray-400">
                留空保持原 Key 不变；输入空格可清除
              </p>
            </div>
            <div>
              <label class="mb-1.5 block text-sm font-medium text-gray-700 dark:text-gray-400">超时（毫秒）</label>
              <TextInput v-model="profileForm.timeoutMs" type="number" />
            </div>
            <div>
              <label class="mb-1.5 block text-sm font-medium text-gray-700 dark:text-gray-400">最大输出长度</label>
              <TextInput v-model="profileForm.maxOutputTokens" type="number" />
            </div>
          </div>

          <div class="mt-5 space-y-4 rounded-2xl border border-gray-200 bg-gray-50 px-4 py-4 dark:border-gray-800 dark:bg-white/[0.03]">
            <ToggleSwitch
              v-model="profileForm.vision"
              label="视觉能力（图片识别 / 文档视觉解析）"
            />
            <p v-if="profileForm.vision" class="text-xs leading-5 text-gray-500 dark:text-gray-400">
              该模型将用于识别上传的书本照片、扫描页与图片型幻灯片（解析流水线随阶段 D 启用）。
            </p>
            <p v-else class="text-xs leading-5 text-gray-500 dark:text-gray-400">
              DeepSeek 等纯文本模型无法识别图片；含照片/扫描页的资料解析时将被跳过并提示。
            </p>
            <ToggleSwitch
              v-if="editingId !== null"
              v-model="profileForm.enabled"
              label="启用该模型（停用后任务将回退到其他已启用的模型）"
            />
          </div>

          <Alert v-if="formError" variant="error" title="保存失败" :message="formError" class="mt-4" />

          <div class="mt-6 flex justify-end gap-3">
            <Button variant="outline" size="sm" @click="formOpen = false">取消</Button>
            <Button size="sm" :disabled="savingProfile" @click="saveProfile">
              {{ savingProfile ? '保存中…' : editingId === null ? '添加' : '保存' }}
            </Button>
          </div>
        </div>
      </template>
    </Modal>

    <!-- 删除确认 -->
    <Modal v-if="deleteTarget" full-screen-backdrop @close="deleteTarget = null">
      <template #body>
            <div class="relative mx-4 w-full max-w-[440px] rounded-3xl bg-white p-6 dark:bg-gray-900">
              <h4 class="text-title-md font-semibold text-gray-800 dark:text-white/90">
                确认删除「{{ deleteTarget.name }}」
              </h4>
          <p class="mt-2 text-sm text-gray-500 dark:text-gray-400">
            删除后无法恢复；若它是默认模型，将自动把剩余最早的模型提升为默认。
          </p>
          <div class="mt-6 flex justify-end gap-3">
            <Button variant="outline" size="sm" @click="deleteTarget = null">取消</Button>
            <Button size="sm" class="!bg-error-500 hover:!bg-error-600" @click="doDelete">确认删除</Button>
          </div>
        </div>
      </template>
    </Modal>
  </AdminLayout>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import AdminLayout from '@/components/layout/AdminLayout.vue'
import PageBreadcrumb from '@/components/common/PageBreadcrumb.vue'
import Alert from '@/components/ui/Alert.vue'
import Badge from '@/components/ui/Badge.vue'
import Button from '@/components/ui/Button.vue'
import Modal from '@/components/ui/Modal.vue'
import ComponentCard from '@/components/common/ComponentCard.vue'
import TextInput from '@/components/ui/form/TextInput.vue'
import SelectInput from '@/components/ui/form/SelectInput.vue'
import ToggleSwitch from '@/components/ui/form/ToggleSwitch.vue'
import { PlusIcon } from '@/icons'
import { settingsApi } from '@/services/settingsApi'
import { ApiError } from '@/services/http'
import type {
  LlmProfileDto,
  RelationTypeDto,
  SettingsSaveRequest,
  SystemSettingsDto,
} from '@/services/types'

const settings = ref<SystemSettingsDto | null>(null)
const loading = ref(true)
const error = ref<string | null>(null)
const saving = ref<string | null>(null)
const savedSection = ref<string | null>(null)

const storageForm = reactive({ maxUploadSizeMb: '200' })
const graphForm = reactive({ defaultDepth: '1', maxNodes: '300' })

/** 关系字典：V1 前端内置默认值，持久化随阶段 E 交付 */
const relationTypes = ref<RelationTypeDto[]>([
  { name: '包含', direction: '父 → 子', enabled: true },
  { name: '属于', direction: '子 → 父', enabled: true },
  { name: '相关', direction: '双向', enabled: true },
  { name: '对比', direction: '双向', enabled: true },
  { name: '前置', direction: '后 → 前', enabled: true },
  { name: '应用', direction: '概念 → 场景', enabled: true },
])

// ---------- 模型档案 ----------

const profiles = ref<LlmProfileDto[]>([])
const formOpen = ref(false)
const editingId = ref<number | null>(null)
const editingApiKeyMasked = ref<string | null>(null)
const savingProfile = ref(false)
const formError = ref<string | null>(null)
const deleteTarget = ref<LlmProfileDto | null>(null)
const testingId = ref<number | null>(null)
const testResults = reactive<Record<number, { ok: boolean; message: string }>>({})

const profileForm = reactive({
  name: '',
  baseUrl: '',
  model: '',
  apiKey: '',
  timeoutMs: '30000',
  maxOutputTokens: '2048',
  vision: false,
  enabled: true,
})

async function loadAll() {
  loading.value = true
  error.value = null
  try {
    const [settingsData, profileList] = await Promise.all([
      settingsApi.get(),
      settingsApi.listLlmProfiles().catch(() => []),
    ])
    settings.value = settingsData
    profiles.value = profileList
    storageForm.maxUploadSizeMb = String(settingsData.storage.maxUploadSizeMb ?? 200)
    graphForm.defaultDepth = String(settingsData.graph.defaultDepth ?? 1)
    graphForm.maxNodes = String(settingsData.graph.maxNodes ?? 300)
  } catch (err) {
    error.value = err instanceof ApiError ? err.message : '设置加载失败，请确认 Java 后端已启动'
  } finally {
    loading.value = false
  }
}

function openCreate() {
  editingId.value = null
  editingApiKeyMasked.value = null
  Object.assign(profileForm, {
    name: '',
    baseUrl: '',
    model: '',
    apiKey: '',
    timeoutMs: '30000',
    maxOutputTokens: '2048',
    vision: false,
    enabled: true,
  })
  formError.value = null
  formOpen.value = true
}

function openEdit(profile: LlmProfileDto) {
  editingId.value = profile.id
  editingApiKeyMasked.value = profile.apiKeyMasked ?? null
  Object.assign(profileForm, {
    name: profile.name,
    baseUrl: profile.baseUrl,
    model: profile.model,
    apiKey: '',
    timeoutMs: String(profile.timeoutMs),
    maxOutputTokens: String(profile.maxOutputTokens),
    vision: profile.vision,
    enabled: profile.enabled,
  })
  formError.value = null
  formOpen.value = true
}

async function saveProfile() {
  savingProfile.value = true
  formError.value = null
  const payload = {
    name: profileForm.name.trim(),
    baseUrl: profileForm.baseUrl.trim(),
    model: profileForm.model.trim(),
    apiKey: profileForm.apiKey,
    timeoutMs: Number(profileForm.timeoutMs),
    maxOutputTokens: Number(profileForm.maxOutputTokens),
    vision: profileForm.vision,
    enabled: profileForm.enabled,
  }
  try {
    if (editingId.value === null) {
      await settingsApi.createLlmProfile(payload)
    } else {
      await settingsApi.updateLlmProfile(editingId.value, payload)
    }
    formOpen.value = false
    profiles.value = await settingsApi.listLlmProfiles()
  } catch (err) {
    formError.value = err instanceof ApiError ? err.message : '保存失败，请稍后重试'
  } finally {
    savingProfile.value = false
  }
}

async function doDelete() {
  if (!deleteTarget.value) return
  const target = deleteTarget.value
  deleteTarget.value = null
  try {
    await settingsApi.deleteLlmProfile(target.id)
    profiles.value = await settingsApi.listLlmProfiles()
  } catch (err) {
    error.value = err instanceof ApiError ? err.message : '删除失败'
  }
}

async function setDefault(profile: LlmProfileDto) {
  try {
    await settingsApi.setDefaultLlmProfile(profile.id)
    profiles.value = await settingsApi.listLlmProfiles()
  } catch (err) {
    error.value = err instanceof ApiError ? err.message : '设置默认失败'
  }
}

async function testProfile(profile: LlmProfileDto) {
  testingId.value = profile.id
  try {
    const result = await settingsApi.testLlmProfile(profile.id)
    testResults[profile.id] = {
      ok: result.ok,
      message: `✓ ${result.message}（${result.model}，${result.latencyMs ?? '-'}ms）`,
    }
  } catch (err) {
    testResults[profile.id] = {
      ok: false,
      message: err instanceof ApiError ? `✗ ${err.message}` : '✗ 测试失败',
    }
  } finally {
    testingId.value = null
  }
}

// ---------- 其他设置 ----------

function markSaved(section: string) {
  savedSection.value = section
  window.setTimeout(() => {
    savedSection.value = null
  }, 2500)
}

async function saveStorage() {
  saving.value = 'storage'
  error.value = null
  try {
    settings.value = await settingsApi.update({
      storage: { maxUploadSizeMb: Number(storageForm.maxUploadSizeMb) },
    })
    markSaved('storage')
  } catch (err) {
    error.value = err instanceof ApiError ? err.message : '保存失败'
  } finally {
    saving.value = null
  }
}

async function saveGraph() {
  saving.value = 'graph'
  error.value = null
  try {
    settings.value = await settingsApi.update({
      graph: { defaultDepth: Number(graphForm.defaultDepth), maxNodes: Number(graphForm.maxNodes) },
    })
    markSaved('graph')
  } catch (err) {
    error.value = err instanceof ApiError ? err.message : '保存失败'
  } finally {
    saving.value = null
  }
}

onMounted(loadAll)
</script>
