<template>
  <Modal v-if="isOpen" full-screen-backdrop @close="close">
    <template #body>
      <div
        class="relative m-4 w-full max-w-[620px] overflow-y-auto rounded-3xl bg-white p-5 lg:p-8 dark:bg-gray-900"
      >
        <!-- header -->
        <div class="mb-6 flex items-start justify-between">
          <div>
            <h3 class="text-title-md font-semibold text-gray-800 dark:text-white/90">导入资料</h3>
            <p class="mt-1 text-sm text-gray-500 dark:text-gray-400">
              上传后自动解析、分段，AI 抽取的候选内容经人工审核后才会入库
            </p>
          </div>
          <button
            class="flex h-9 w-9 items-center justify-center rounded-lg text-gray-400 hover:bg-gray-100 hover:text-gray-600 dark:hover:bg-white/[0.05] dark:hover:text-white/80"
            aria-label="关闭"
            @click="close"
          >
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
              <path
                fill-rule="evenodd"
                clip-rule="evenodd"
                d="M6.21967 7.28131C5.92678 6.98841 5.92678 6.51354 6.21967 6.22065C6.51256 5.92775 6.98744 5.92775 7.28033 6.22065L11.999 10.9393L16.7176 6.22078C17.0105 5.92789 17.4854 5.92788 17.7782 6.22078C18.0711 6.51367 18.0711 6.98855 17.7782 7.28144L13.0597 12L17.7782 16.7186C18.0711 17.0115 18.0711 17.4863 17.7782 17.7792C17.4854 18.0721 17.0105 18.0721 16.7176 17.7792L11.999 13.0607L7.28033 17.7794C6.98744 18.0722 6.51256 18.0722 6.21967 17.7794C5.92678 17.4865 5.92678 17.0116 6.21967 16.7187L10.9384 12L6.21967 7.28131Z"
                fill="currentColor"
              />
            </svg>
          </button>
        </div>

        <!-- §8.1 支持范围 -->
        <div
          class="mb-6 rounded-xl border border-blue-light-200 bg-blue-light-50 p-4 text-sm dark:border-blue-light-500/20 dark:bg-blue-light-500/10"
        >
          <p class="font-medium text-blue-light-700 dark:text-blue-light-300">支持的资料格式</p>
          <ul class="mt-2 space-y-1 text-gray-600 dark:text-gray-300">
            <li>· PDF（.pdf）：按页提取文本，扫描页进入 OCR</li>
            <li>· PowerPoint（.ppt / .pptx）：按幻灯片提取文字、备注与图片</li>
            <li>· 书本照片压缩包（.zip）：仅含 JPG/JPEG/PNG/WebP，按文件名自然排序</li>
            <li class="text-gray-500 dark:text-gray-400">
              限制：单文件 ≤ 200MB；ZIP 内 ≤ 500 张图片，解压后 ≤ 1GB；暂不支持 RAR/7z/加密压缩包
            </li>
          </ul>
        </div>

        <div class="space-y-5">
          <div>
            <label class="mb-1.5 block text-sm font-medium text-gray-700 dark:text-gray-400">
              导入到知识库 <span class="text-error-500">*</span>
            </label>
            <SelectInput
              v-model="libraryId"
              :options="libraryOptions"
              placeholder="请选择知识库"
              :disabled="loadingLibraries"
            />
          </div>

          <FileInput
            v-model="file"
            label="资料文件"
            accept=".pdf,.ppt,.pptx,.zip"
            hint="上传前会进行安全校验与 SHA-256 去重；重复文件将提示复用已有资料"
            :error="fileError"
          />
        </div>

        <Alert
          v-if="errorMessage"
          variant="error"
          title="上传失败"
          :message="errorMessage"
          class="mt-5"
        />

        <!-- footer -->
        <div class="mt-8 flex items-center justify-end gap-3">
          <Button variant="outline" @click="close">取消</Button>
          <Button :disabled="!canSubmit || submitting" @click="submit">
            {{ submitting ? '上传中…' : '开始上传' }}
          </Button>
        </div>
      </div>
    </template>
  </Modal>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
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

const libraryOptions = computed<SelectOption[]>(() =>
  libraries.value.map((library) => ({ value: String(library.id), label: library.name })),
)

const canSubmit = computed(() => Boolean(libraryId.value && file.value))

watch(isOpen, (open) => {
  if (open) {
    errorMessage.value = null
    fileError.value = null
    void loadLibraries()
  }
})

async function loadLibraries() {
  loadingLibraries.value = true
  try {
    libraries.value = await libraryApi.list()
    if (!libraryId.value && libraries.value.length > 0) {
      libraryId.value = String(libraries.value[0].id)
    }
  } catch (error) {
    errorMessage.value =
      error instanceof ApiError ? error.message : '知识库列表加载失败，请确认后端服务已启动'
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
    const document = await ingestionApi.upload(Number(libraryId.value), file.value)
    // 上传成功后立即触发解析流水线（§12.4），进度在处理中心轮询
    await ingestionApi.startProcess(document.id)
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
