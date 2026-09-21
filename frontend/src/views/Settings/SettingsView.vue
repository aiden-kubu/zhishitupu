<template>
  <AdminLayout>
    <PageBreadcrumb page-title="系统设置" description="查看 AI 服务能力，管理模型连接与使用偏好。">
      <template #actions
        ><Button size="sm" :start-icon="PlusIcon" @click="openCreate">添加模型</Button></template
      >
    </PageBreadcrumb>

    <div
      v-if="loading"
      class="rounded-2xl border border-gray-200 p-10 text-center dark:border-gray-800"
    >
      <p class="text-sm text-gray-500 dark:text-gray-400">设置加载中…</p>
    </div>

    <div v-else-if="error" class="space-y-3">
      <Alert variant="error" title="加载失败" :message="error" />
      <Button size="sm" variant="outline" @click="loadAll">重试</Button>
    </div>

    <div v-else class="grid grid-cols-1 gap-5 xl:grid-cols-2">
      <Alert
        v-if="actionError"
        variant="error"
        title="操作未完成"
        :message="actionError"
        class="xl:col-span-2"
      />
      <section
        class="overflow-hidden rounded-xl border border-gray-200 bg-white xl:col-span-2 dark:border-gray-800 dark:bg-white/[0.03]"
      >
        <div
          class="flex flex-wrap items-center justify-between gap-3 border-b border-gray-100 bg-brand-25/50 px-5 py-4 dark:border-gray-800 dark:bg-brand-500/[0.04]"
        >
          <div class="flex items-center gap-3">
            <span
              class="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-brand-500 text-white"
              ><PlugInIcon class="h-5 w-5"
            /></span>
            <div>
              <h2 class="text-sm font-semibold text-gray-800 dark:text-white/90">AI 服务概览</h2>
              <p class="mt-1 text-xs text-gray-500 dark:text-gray-400">
                先了解当前配置，连接是否正常可在模型卡片中测试。
              </p>
            </div>
          </div>
          <Badge
            :color="
              defaultProfile?.enabled && defaultProfile?.apiKeyConfigured ? 'success' : 'warning'
            "
            size="sm"
            >{{
              defaultProfile?.enabled && defaultProfile?.apiKeyConfigured
                ? '默认模型已配置'
                : '待配置默认模型'
            }}</Badge
          >
        </div>
        <dl class="grid grid-cols-2 divide-gray-100 lg:grid-cols-4 dark:divide-gray-800">
          <div class="border-b border-e border-gray-100 p-5 lg:border-b-0 dark:border-gray-800">
            <dt class="text-xs text-gray-500 dark:text-gray-400">已启用模型</dt>
            <dd class="mt-2 text-sm font-semibold text-gray-800 dark:text-white/90">
              {{ enabledProfiles.length }}
              <span class="font-normal text-gray-400">/ {{ profiles.length }} 个</span>
            </dd>
          </div>
          <div class="border-b border-gray-100 p-5 lg:border-b-0 lg:border-e dark:border-gray-800">
            <dt class="text-xs text-gray-500 dark:text-gray-400">默认模型</dt>
            <dd class="mt-2 truncate text-sm font-semibold text-gray-800 dark:text-white/90">
              {{ defaultProfile?.name || '尚未设置' }}
            </dd>
          </div>
          <div class="border-e border-gray-100 p-5 dark:border-gray-800">
            <dt class="text-xs text-gray-500 dark:text-gray-400">图片识别配置</dt>
            <dd class="mt-2 text-sm font-semibold text-gray-800 dark:text-white/90">
              {{ visionProfiles.length ? visionProfiles.length + ' 个视觉模型' : '未配置' }}
            </dd>
          </div>
          <div class="p-5">
            <dt class="text-xs text-gray-500 dark:text-gray-400">文件保存方式</dt>
            <dd
              class="mt-2 flex flex-wrap items-center gap-x-2 gap-y-1 text-sm font-semibold text-gray-800 dark:text-white/90"
            >
              <span class="flex items-center gap-1.5 whitespace-nowrap"
                ><FolderIcon class="h-4 w-4 shrink-0 text-success-500" />本机存储</span
              ><span class="text-xs font-normal text-gray-400">自动管理</span>
            </dd>
          </div>
        </dl>
      </section>

      <section
        class="rounded-xl border border-gray-200 bg-white p-5 xl:col-span-2 dark:border-gray-800 dark:bg-white/[0.03]"
      >
        <div class="mb-4">
          <h2 class="text-sm font-semibold text-gray-800 dark:text-white/90">
            模型如何参与知识整理
          </h2>
          <p class="mt-1 text-xs leading-5 text-gray-500 dark:text-gray-400">
            上传后按资料类型处理，你无需为每份文件重复选择模型。
          </p>
        </div>
        <div class="grid gap-4 md:grid-cols-2">
          <div
            class="rounded-lg border border-gray-100 bg-gray-25 p-4 dark:border-gray-800 dark:bg-gray-900/40"
          >
            <div
              class="flex items-center gap-2 text-sm font-medium text-gray-800 dark:text-white/90"
            >
              <DocsIcon class="h-4 w-4 text-brand-500" />知识识别与问答
            </div>
            <p class="mt-2 text-xs leading-5 text-gray-500 dark:text-gray-400">
              识别知识、归纳主题，并围绕知识点回答问题。
            </p>
            <div class="mt-3 flex flex-wrap items-center gap-2 text-xs">
              <span class="text-gray-400">默认模型</span
              ><span
                class="rounded-md border border-gray-200 bg-white px-2 py-1 text-gray-700 dark:border-gray-700 dark:bg-gray-800 dark:text-gray-300"
                >{{ defaultProfile?.name || '请先添加模型' }}</span
              >
            </div>
          </div>
          <div
            class="rounded-lg border border-gray-100 bg-gray-25 p-4 dark:border-gray-800 dark:bg-gray-900/40"
          >
            <div
              class="flex items-center gap-2 text-sm font-medium text-gray-800 dark:text-white/90"
            >
              <PageIcon class="h-4 w-4 text-success-500" />扫描页与照片识别
            </div>
            <p class="mt-2 text-xs leading-5 text-gray-500 dark:text-gray-400">
              将图片中的文字转成可整理的内容，需要模型支持视觉。
            </p>
            <div class="mt-3 flex flex-wrap items-center gap-2 text-xs">
              <span class="text-gray-400">已启用视觉配置</span
              ><span
                class="rounded-md border border-gray-200 bg-white px-2 py-1 text-gray-700 dark:border-gray-700 dark:bg-gray-800 dark:text-gray-300"
                >{{
                  visionProfiles.map((profile) => profile.name).join('、') || '暂无视觉模型'
                }}</span
              >
            </div>
          </div>
        </div>
      </section>

      <section class="xl:col-span-2">
        <div class="mb-4 flex items-center justify-between gap-3">
          <div>
            <h2 class="text-sm font-semibold text-gray-800 dark:text-white/90">模型服务</h2>
            <p class="mt-1 text-xs text-gray-500 dark:text-gray-400">
              连接信息与状态集中管理，密钥加密保存。
            </p>
          </div>
          <span class="text-xs text-gray-400">共 {{ profiles.length }} 个模型</span>
        </div>
        <Alert
          v-if="profiles.length === 0"
          variant="warning"
          title="添加第一个 AI 模型"
          message="点击上方「添加模型」，填写服务商提供的连接信息，即可开始识别资料。"
        />
        <div v-else class="grid gap-4" :class="profiles.length > 1 ? 'lg:grid-cols-2' : ''">
          <article
            v-for="profile in profiles"
            :key="profile.id"
            class="overflow-hidden rounded-xl border border-gray-200 bg-white dark:border-gray-800 dark:bg-white/[0.03]"
          >
            <div
              class="flex items-start justify-between gap-3 border-b border-gray-100 px-5 py-4 dark:border-gray-800"
            >
              <div class="min-w-0">
                <div class="flex flex-wrap items-center gap-2">
                  <h3 class="text-sm font-semibold text-gray-800 dark:text-white/90">
                    {{ profile.name }}
                  </h3>
                  <Badge v-if="profile.isDefault" color="primary" size="sm">默认</Badge
                  ><Badge :color="profile.enabled ? 'success' : 'light'" size="sm">{{
                    profile.enabled ? '已启用' : '已停用'
                  }}</Badge>
                </div>
                <p class="mt-1.5 truncate text-xs text-gray-500 dark:text-gray-400">
                  {{ profile.model }}
                </p>
              </div>
              <div class="flex shrink-0 gap-1">
                <button
                  type="button"
                  :aria-label="'编辑模型 ' + profile.name"
                  title="编辑模型"
                  :disabled="testingId === profile.id"
                  class="flex h-8 w-8 items-center justify-center rounded-lg border border-gray-200 text-gray-500 transition hover:border-brand-300 hover:text-brand-500 dark:border-gray-700 dark:text-gray-400"
                  @click="openEdit(profile)"
                >
                  <SettingsIcon class="h-4 w-4" />
                </button>
                <button
                  type="button"
                  :aria-label="'删除模型 ' + profile.name"
                  title="删除模型"
                  class="flex h-8 w-8 items-center justify-center rounded-lg border border-gray-200 text-gray-400 transition hover:border-error-200 hover:bg-error-50 hover:text-error-500 dark:border-gray-700 dark:hover:bg-error-500/10"
                  @click="deleteTarget = profile"
                >
                  <TrashIcon class="h-4 w-4" />
                </button>
              </div>
            </div>
            <dl
              class="grid gap-x-6 gap-y-4 px-5 py-4 sm:grid-cols-2"
              :class="profiles.length === 1 ? 'lg:grid-cols-4' : ''"
            >
              <div>
                <dt class="text-xs text-gray-400">连接地址</dt>
                <dd class="mt-1.5 text-xs leading-5 break-all text-gray-700 dark:text-gray-300">
                  {{ profile.baseUrl }}
                </dd>
              </div>
              <div>
                <dt class="text-xs text-gray-400">API 密钥</dt>
                <dd class="mt-1.5 text-xs text-gray-700 dark:text-gray-300">
                  {{ profile.apiKeyConfigured ? '已配置 · ' + profile.apiKeyMasked : '未配置' }}
                </dd>
              </div>
              <div>
                <dt class="text-xs text-gray-400">识别能力</dt>
                <dd class="mt-1.5 text-xs text-gray-700 dark:text-gray-300">
                  {{ profile.vision ? '文字与图片（已配置）' : '文字' }}
                </dd>
              </div>
              <div>
                <dt class="text-xs text-gray-400">输出上限</dt>
                <dd class="mt-1.5 text-xs tabular-nums text-gray-700 dark:text-gray-300">
                  {{ profile.maxOutputTokens.toLocaleString() }} Token
                </dd>
              </div>
            </dl>
            <div
              class="flex flex-wrap items-center justify-between gap-3 border-t border-gray-100 bg-gray-25 px-5 py-3 dark:border-gray-800 dark:bg-gray-900/30"
            >
              <p
                class="min-w-0 flex-1 text-xs leading-5"
                :class="
                  testResults[profile.id]
                    ? testResults[profile.id].ok
                      ? 'text-success-600 dark:text-success-400'
                      : 'text-error-500'
                    : 'text-gray-400'
                "
              >
                {{ testResults[profile.id]?.message || '尚未测试连接' }}
              </p>
              <div class="flex shrink-0 gap-2">
                <Button
                  v-if="!profile.isDefault"
                  size="sm"
                  variant="outline"
                  @click="setDefault(profile)"
                  >设为默认</Button
                ><Button
                  size="sm"
                  variant="outline"
                  :start-icon="RefreshIcon"
                  :disabled="testingId !== null"
                  @click="testProfile(profile)"
                  >{{ testingId === profile.id ? '测试中…' : '测试连接' }}</Button
                >
              </div>
            </div>
          </article>
        </div>
      </section>

      <details
        class="group rounded-xl border border-gray-200 bg-white xl:col-span-2 dark:border-gray-800 dark:bg-white/[0.03]"
      >
        <summary class="flex cursor-pointer list-none items-center justify-between gap-4 p-5">
          <div>
            <h2 class="text-sm font-semibold text-gray-800 dark:text-white/90">
              高级设置
              <span class="ms-2 text-xs font-normal text-gray-500 dark:text-gray-400">可选</span>
            </h2>
            <p class="mt-1 text-xs leading-5 text-gray-500 dark:text-gray-400">
              文件存储、图谱显示与关系说明，按需调整即可。
            </p>
          </div>
          <ChevronDownIcon
            class="h-5 w-5 shrink-0 text-gray-400 transition group-open:rotate-180"
          />
        </summary>
        <div
          class="grid grid-cols-1 gap-5 border-t border-gray-100 p-4 sm:p-6 xl:grid-cols-2 dark:border-gray-800"
        >
          <!-- 存储 -->
          <ComponentCard title="文件存储" desc="上传文件保存在本机，通常无需修改。">
            <div class="space-y-4">
              <div>
                <label class="mb-1.5 block text-sm font-medium text-gray-700 dark:text-gray-400"
                  >文件保存位置（系统管理）</label
                >
                <p
                  class="rounded-lg bg-gray-50 p-3 text-xs leading-5 break-all text-gray-500 dark:bg-gray-800 dark:text-gray-400"
                >
                  {{ settings?.storage.root }}
                </p>
              </div>
              <div>
                <label class="mb-1.5 block text-sm font-medium text-gray-700 dark:text-gray-400"
                  >单文件上限（MB）</label
                >
                <TextInput
                  v-model="storageForm.maxUploadSizeMb"
                  type="number"
                  aria-label="单文件上限（MB）"
                />
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
          <ComponentCard title="图谱显示" desc="控制一次展示的范围，不影响知识识别和入库。">
            <div class="space-y-4">
              <div>
                <label class="mb-1.5 block text-sm font-medium text-gray-700 dark:text-gray-400"
                  >默认关联范围</label
                >
                <SelectInput
                  v-model="graphForm.defaultDepth"
                  :options="[
                    { value: '1', label: '1 层 · 直接关联的知识' },
                    { value: '2', label: '2 层 · 包含间接关联' },
                  ]"
                />
              </div>
              <div>
                <label class="mb-1.5 block text-sm font-medium text-gray-700 dark:text-gray-400"
                  >画布节点上限</label
                >
                <TextInput v-model="graphForm.maxNodes" type="number" aria-label="画布节点上限" />
                <p class="mt-2 text-xs leading-5 text-gray-500 dark:text-gray-400">
                  仅限制画布一次显示的数量；数值越大，浏览器负担越高。
                </p>
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
            title="常用关系说明"
            desc="了解知识点之间的连接含义，此处仅供参考。"
            class="xl:col-span-2"
          >
            <div class="overflow-x-auto">
              <table class="w-full min-w-[260px] text-sm">
                <thead>
                  <tr
                    class="border-b border-gray-100 text-gray-500 dark:border-gray-800 dark:text-gray-400"
                  >
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
                    <td class="px-3 py-2.5 text-gray-700 dark:text-gray-300">
                      {{ relation.name }}
                    </td>
                    <td class="px-3 py-2.5 text-gray-500 dark:text-gray-400">
                      {{ relation.direction }}
                    </td>
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
      </details>
    </div>

    <!-- 添加/编辑模型弹窗 -->
    <Modal v-if="formOpen" full-screen-backdrop @close="formOpen = false">
      <template #body>
        <div
          class="relative m-4 w-full max-w-[560px] overflow-y-auto rounded-3xl bg-white p-6 lg:p-8 dark:bg-gray-900"
        >
          <h3 class="text-2xl font-semibold text-gray-800 dark:text-white/90">
            {{ editingId === null ? '添加模型' : '编辑模型' }}
          </h3>
          <p class="mt-1 text-sm text-gray-500 dark:text-gray-400">
            填写服务商提供的连接信息。密钥会加密保存，不会完整展示。
          </p>

          <div class="mt-5 grid grid-cols-1 gap-x-5 gap-y-4 sm:grid-cols-2">
            <div class="sm:col-span-2">
              <label class="mb-1.5 block text-sm font-medium text-gray-700 dark:text-gray-400">
                模型名称 <span class="text-error-500">*</span>
              </label>
              <TextInput
                v-model="profileForm.name"
                aria-label="模型名称"
                placeholder="如：deepseek-知识抽取 / glm-视觉解析"
              />
            </div>
            <div class="sm:col-span-2">
              <label class="mb-1.5 block text-sm font-medium text-gray-700 dark:text-gray-400">
                API 地址 <span class="text-error-500">*</span>
              </label>
              <TextInput
                v-model="profileForm.baseUrl"
                aria-label="API 地址"
                placeholder="https://api.deepseek.com/v1"
              />
            </div>
            <div>
              <label class="mb-1.5 block text-sm font-medium text-gray-700 dark:text-gray-400">
                模型标识 <span class="text-error-500">*</span>
              </label>
              <TextInput
                v-model="profileForm.model"
                aria-label="模型标识"
                placeholder="deepseek-chat / glm-4v-flash"
              />
            </div>
            <div>
              <label class="mb-1.5 block text-sm font-medium text-gray-700 dark:text-gray-400"
                >API Key</label
              >
              <TextInput
                v-model="profileForm.apiKey"
                aria-label="API Key"
                type="password"
                :placeholder="editingApiKeyMasked || 'sk-…'"
              />
              <p v-if="editingId !== null" class="mt-1 text-xs text-gray-400">
                留空保持原 Key 不变；输入空格可清除
              </p>
            </div>
            <details
              class="sm:col-span-2 rounded-xl border border-gray-200 p-4 dark:border-gray-800"
            >
              <summary class="cursor-pointer text-sm font-medium text-gray-700 dark:text-gray-300">
                模型高级参数（可选）
              </summary>
              <div class="mt-4 grid gap-4 sm:grid-cols-2">
                <div>
                  <label class="mb-1.5 block text-sm font-medium text-gray-700 dark:text-gray-400"
                    >超时（毫秒）</label
                  >
                  <TextInput
                    v-model="profileForm.timeoutMs"
                    aria-label="超时（毫秒）"
                    type="number"
                  />
                </div>
                <div>
                  <label class="mb-1.5 block text-sm font-medium text-gray-700 dark:text-gray-400"
                    >最大输出长度</label
                  >
                  <TextInput
                    v-model="profileForm.maxOutputTokens"
                    aria-label="最大输出长度"
                    type="number"
                  />
                </div>
              </div>
            </details>
          </div>

          <div
            class="mt-5 space-y-4 rounded-2xl border border-gray-200 bg-gray-50 px-4 py-4 dark:border-gray-800 dark:bg-white/[0.03]"
          >
            <ToggleSwitch
              v-model="profileForm.vision"
              label="视觉能力（图片识别 / 文档视觉解析）"
            />
            <p v-if="profileForm.vision" class="text-xs leading-5 text-gray-500 dark:text-gray-400">
              该模型将用于识别上传的书本照片、扫描页与图片型幻灯片。
            </p>
            <p v-else class="text-xs leading-5 text-gray-500 dark:text-gray-400">
              DeepSeek 等纯文本模型无法识别图片；识别照片或扫描页需要另行添加支持视觉的模型。
            </p>
            <ToggleSwitch
              v-if="editingId !== null"
              v-model="profileForm.enabled"
              label="启用该模型"
            />
          </div>

          <Alert
            v-if="formError"
            variant="error"
            title="保存失败"
            :message="formError"
            class="mt-4"
          />

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
            <Button size="sm" class="!bg-error-500 hover:!bg-error-600" @click="doDelete"
              >确认删除</Button
            >
          </div>
        </div>
      </template>
    </Modal>
  </AdminLayout>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
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
import {
  PlusIcon,
  PlugInIcon,
  FolderIcon,
  DocsIcon,
  PageIcon,
  SettingsIcon,
  TrashIcon,
  RefreshIcon,
  ChevronDownIcon,
} from '@/icons'
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
const actionError = ref<string | null>(null)
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
const defaultProfile = computed(() => profiles.value.find((profile) => profile.isDefault))
const enabledProfiles = computed(() => profiles.value.filter((profile) => profile.enabled))
const visionProfiles = computed(() =>
  enabledProfiles.value.filter((profile) => profile.vision && profile.apiKeyConfigured),
)
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
      settingsApi.listLlmProfiles(),
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
      delete testResults[editingId.value]
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
    actionError.value = err instanceof ApiError ? err.message : '删除失败'
  }
}

async function setDefault(profile: LlmProfileDto) {
  try {
    await settingsApi.setDefaultLlmProfile(profile.id)
    profiles.value = await settingsApi.listLlmProfiles()
  } catch (err) {
    actionError.value = err instanceof ApiError ? err.message : '设置默认失败'
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
  actionError.value = null
  try {
    settings.value = await settingsApi.update({
      storage: { maxUploadSizeMb: Number(storageForm.maxUploadSizeMb) },
    })
    markSaved('storage')
  } catch (err) {
    actionError.value = err instanceof ApiError ? err.message : '保存失败'
  } finally {
    saving.value = null
  }
}

async function saveGraph() {
  saving.value = 'graph'
  actionError.value = null
  try {
    settings.value = await settingsApi.update({
      graph: { defaultDepth: Number(graphForm.defaultDepth), maxNodes: Number(graphForm.maxNodes) },
    })
    markSaved('graph')
  } catch (err) {
    actionError.value = err instanceof ApiError ? err.message : '保存失败'
  } finally {
    saving.value = null
  }
}

onMounted(loadAll)
</script>
