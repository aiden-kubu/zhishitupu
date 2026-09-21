<template>
  <div>
    <p
      v-if="label"
      :id="id + '-label'"
      class="mb-2 text-sm font-medium text-gray-700 dark:text-gray-300"
    >
      {{ label }}
    </p>
    <label
      :for="id"
      class="relative flex min-h-40 flex-col items-center justify-center rounded-xl border border-dashed px-5 py-6 text-center transition focus-within:ring-2 focus-within:ring-brand-500/30"
      :class="[
        disabled ? 'cursor-not-allowed opacity-60' : 'cursor-pointer',
        error
          ? 'border-error-300 bg-error-50 dark:border-error-700 dark:bg-error-500/10'
          : dragging
            ? 'border-brand-400 bg-brand-50 dark:bg-brand-500/10'
            : 'border-gray-300 bg-gray-25 hover:border-brand-300 hover:bg-brand-25 dark:border-gray-700 dark:bg-gray-900/30 dark:hover:bg-gray-800',
      ]"
      @dragover.prevent="!disabled && (dragging = true)"
      @dragleave.prevent="dragging = false"
      @drop.prevent="onDrop"
    >
      <input
        :id="id"
        ref="input"
        type="file"
        :accept="accept"
        :disabled="disabled"
        :aria-labelledby="id + '-label'"
        :aria-describedby="id + '-hint'"
        class="sr-only"
        @change="onChange"
      />
      <span
        class="mb-3 flex h-11 w-11 items-center justify-center rounded-xl border border-gray-200 bg-white text-brand-500 shadow-theme-xs dark:border-gray-700 dark:bg-gray-800"
        ><DocsIcon class="h-5 w-5"
      /></span>
      <span
        v-if="modelValue"
        class="max-w-full text-sm font-medium break-all text-gray-800 dark:text-white/90"
        >{{ modelValue.name }}</span
      >
      <span v-else class="text-sm font-medium text-gray-700 dark:text-gray-300"
        ><span class="text-brand-500">点击选择文件</span>，或将文件拖到这里</span
      >
      <span class="mt-2 text-xs text-gray-500 dark:text-gray-400">{{
        modelValue
          ? formatSize(modelValue.size) + ' · 点击可更换文件'
          : 'PDF · PPT / PPTX · 照片 ZIP'
      }}</span>
    </label>
    <div v-if="modelValue" class="mt-2 flex justify-end">
      <button
        type="button"
        :disabled="disabled"
        class="text-xs text-gray-500 hover:text-error-500 dark:text-gray-400"
        @click="clear"
      >
        移除文件
      </button>
    </div>
    <p
      :id="id + '-hint'"
      class="mt-2 text-xs leading-5"
      :class="error ? 'text-error-500' : 'text-gray-500 dark:text-gray-400'"
      :role="error ? 'alert' : undefined"
    >
      {{ error || hint }}
    </p>
  </div>
</template>
<script setup lang="ts">
import { ref, useId } from 'vue'
import { DocsIcon } from '@/icons'
const props = withDefaults(
  defineProps<{
    modelValue?: File | null
    label?: string
    accept?: string
    hint?: string
    error?: string | null
    disabled?: boolean
  }>(),
  { modelValue: null, label: '资料文件', accept: '', hint: '', error: null, disabled: false },
)
const emit = defineEmits<{ (e: 'update:modelValue', file: File | null): void }>()
const id = useId()
const input = ref<HTMLInputElement | null>(null)
const dragging = ref(false)
function selectFile(file: File | null) {
  if (props.disabled || !file) return
  emit('update:modelValue', file)
  if (input.value) input.value.value = ''
}
const onChange = (event: Event) => selectFile((event.target as HTMLInputElement).files?.[0] ?? null)
function onDrop(event: DragEvent) {
  dragging.value = false
  selectFile(event.dataTransfer?.files[0] ?? null)
}
function clear() {
  if (input.value) input.value.value = ''
  emit('update:modelValue', null)
}
function formatSize(size: number) {
  return size >= 1024 * 1024
    ? (size / 1024 / 1024).toFixed(1) + ' MB'
    : Math.max(1, Math.round(size / 1024)) + ' KB'
}
</script>
