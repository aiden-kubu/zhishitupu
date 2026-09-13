<template>
  <Modal v-if="isOpen" full-screen-backdrop @close="close">
    <template #body>
      <div
        class="relative m-4 w-full max-w-[600px] overflow-y-auto rounded-2xl bg-white p-5 lg:p-8 dark:bg-gray-900"
      >
        <!-- header -->
        <div class="mb-6 flex items-start justify-between">
          <div>
            <h3 class="text-xl font-semibold text-gray-800 dark:text-white/90">导入资料</h3>
            <p class="mt-1 text-sm text-gray-500 dark:text-gray-400">
              选择资料即可开始，AI 会识别内容、自动命名并按主题整理。
            </p>
          </div>
          <button
            class="flex h-9 w-9 items-center justify-center rounded-lg text-gray-400 hover:bg-gray-100 hover:text-gray-600 dark:hover:bg-white/[0.05] dark:hover:text-white/80"
            aria-label="关闭"
            @click="close"
          >
            <svg
              width="20"
              height="20"
              viewBox="0 0 24 24"
              fill="none"
              xmlns="http://www.w3.org/2000/svg"
            >
              <path
                fill-rule="evenodd"
                clip-rule="evenodd"
                d="M6.21967 7.28131C5.92678 6.98841 5.92678 6.51354 6.21967 6.22065C6.51256 5.92775 6.98744 5.92775 7.28033 6.22065L11.999 10.9393L16.7176 6.22078C17.0105 5.92789 17.4854 5.92788 17.7782 6.22078C18.0711 6.51367 18.0711 6.98855 17.7782 7.28144L13.0597 12L17.7782 16.7186C18.0711 17.0115 18.0711 17.4863 17.7782 17.7792C17.4854 18.0721 17.0105 18.0721 16.7176 17.7792L11.999 13.0607L7.28033 17.7794C6.98744 18.0722 6.51256 18.0722 6.21967 17.7794C5.92678 17.4865 5.92678 17.0116 6.21967 16.7187L10.9384 12L6.21967 7.28131Z"
                fill="currentColor"
              />
            </svg>
          </button>
        </div>

        <div
          class="mb-6 flex items-center justify-between rounded-lg bg-gray-50 px-4 py-3 text-xs font-medium text-gray-500 dark:bg-gray-800 dark:text-gray-400"
        >
          <span>01 上传资料</span><ChevronRightIcon class="h-3.5 w-3.5 rtl:rotate-180" /><span
            >02 AI 整理</span
          ><ChevronRightIcon class="h-3.5 w-3.5 rtl:rotate-180" /><span>03 自动入库</span>
        </div>
        <div class="space-y-5">
          <FileInput
            v-model="file"
            label="资料文件"
            accept=".pdf,.ppt,.pptx,.zip"
            hint="支持 PDF、PowerPoint 和照片 ZIP。重复资料会自动提示，无需重复上传。"
            :disabled="submitting"
            :error="fileError"
          />
          <div>
            <label class="mb-1.5 block text-sm font-medium text-gray-700 dark:text-gray-400">
              知识整理方式
            </label>
            <SelectInput
              v-model="libraryId"
              :options="libraryOptions"
              placeholder="AI 自动整理（推荐）"
              :disabled="submitting"
            />
            <p class="mt-2 text-xs text-gray-500 dark:text-gray-400">
              无需提前建库或起名；识别完成后可在「AI 复审」查看分类，在知识库中修改名称。
            </p>
          </div>
        </div>

        <details
          class="mt-5 rounded-xl border border-gray-200 px-4 py-3 text-xs leading-5 text-gray-500 dark:border-gray-800 dark:text-gray-400"
        >
          <summary class="cursor-pointer font-medium text-gray-600 dark:text-gray-300">
            文件要求与识别说明
          </summary>
          <ul class="mt-3 space-y-2">
            <li>PDF：支持文字与扫描页；PPT / PPTX：识别文字与备注。</li>
            <li>照片 ZIP：仅放入 JPG、PNG 或 WebP 图片，按文件名排序。</li>
            <li>
              单文件最大 200MB；ZIP 最多 500 张图片，解压后不超过 1GB。不支持 RAR、7z 或加密压缩包。
            </li>
            <li>扫描页与照片需要支持图片识别的模型。</li>
          </ul>
        </details>
        <Alert
          v-if="errorMessage"
          variant="error"
          title="上传失败"
          :message="errorMessage"
          class="mt-5"
        />

        <!-- footer -->
        <div
          class="mt-6 flex items-center justify-end gap-3 border-t border-gray-100 pt-5 dark:border-gray-800"
        >
          <Button variant="outline" @click="close">取消</Button>
          <Button :disabled="!canSubmit || submitting" @click="submit">
            {{ submitting ? '上传中…' : '上传并识别' }}
          </Button>
        </div>
      </div>
    </template>
  </Modal>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ChevronRightIcon } from '@/icons'
import { useRouter } from 'vue-router'
import Modal from '@/components/ui/Modal.vue'
import Button from '@/components/ui/Button.vue'
import Alert from '@/components/ui/Alert.vue'
import SelectInput from '@/components/ui/form/SelectInput.vue'
import FileInput from '@/components/ui/form/FileInput.vue'
import { useImportModal } from '@/composables/useImportModal'
import { libraryApi } from '@/services/libraryApi'
import { ingestionApi } from '@/services/ingestionApi'
import { ApiError } from '@/services/http'
import type { LibraryDto } from '@/services/types'
import type { SelectOption } from '@/components/ui/form/SelectInput.vue'

const { isOpen, close } = useImportModal()
const router = useRouter()

const libraries = ref<LibraryDto[]>([])
const loadingLibraries = ref(false)
const libraryId = ref('')
const file = ref<File | null>(null)
const fileError = ref<string | null>(null)
const errorMessage = ref<string | null>(null)
const submitting = ref(false)

const libraryOptions = computed<SelectOption[]>(() => [
  { value: '', label: 'AI 自动整理（推荐）' },
  ...libraries.value
    .filter((library) => library.status === 'active')
    .map((library) => ({ value: String(library.id), label: `指定知识库：${library.name}` })),
])

const canSubmit = computed(() => Boolean(file.value) && !fileError.value)

watch(file, (selected) => {
  errorMessage.value = null
  if (selected) validateFile()
  else fileError.value = null
})

watch(isOpen, (open) => {
  if (open) {
    errorMessage.value = null
    fileError.value = null
    if (file.value) validateFile()
    void loadLibraries()
  }
})

async function loadLibraries() {
  loadingLibraries.value = true
  try {
    libraries.value = await libraryApi.list()
  } catch (error) {
    libraries.value = [] // 自动识别不依赖已有知识库列表
  } finally {
    loadingLibraries.value = false
  }
}

function validateFile(): boolean {
  fileError.value = null
  if (!file.value) {
    fileError.value = '请选择要上传的文件'
    return false
  }
  const allowed = ['.pdf', '.ppt', '.pptx', '.zip']
  const lower = file.value.name.toLowerCase()
  if (!allowed.some((extension) => lower.endsWith(extension))) {
    fileError.value = '仅支持 .pdf / .ppt / .pptx / .zip（图片）文件'
    return false
  }
  if (file.value.size > 200 * 1024 * 1024) {
    fileError.value = '文件超过 200MB 上限'
    return false
  }
  return true
}

async function submit() {
  if (!validateFile() || !file.value) return
  submitting.value = true
  errorMessage.value = null
  try {
    const document = await ingestionApi.upload(
      libraryId.value ? Number(libraryId.value) : null,
      file.value,
    )
    // 上传成功后立即触发解析流水线（§12.4），进度在处理中心轮询
    await ingestionApi.startProcess(document.id)
    file.value = null
    close()
    void router.push('/processing')
  } catch (error) {
    if (error instanceof ApiError) {
      errorMessage.value =
        error.code === 'CONFLICT'
          ? error.message
          : error.code === 'NOT_IMPLEMENTED'
            ? `解析流水线尚未启用（阶段 D 交付）：${error.message}`
            : error.message
    } else {
      errorMessage.value = '上传失败，请稍后重试'
    }
  } finally {
    submitting.value = false
  }
}
</script>
