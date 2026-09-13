<template>
  <div>
    <label v-if="label" class="mb-1.5 block text-sm font-medium text-gray-700 dark:text-gray-400">
      {{ label }}
    </label>
    <input
      type="file"
      :accept="accept"
      :disabled="disabled"
      class="focus:border-ring-brand-300 shadow-theme-xs focus:file:ring-brand-300 h-11 w-full overflow-hidden rounded-lg border bg-transparent text-sm text-gray-500 transition-colors ltr:file:mr-5 rtl:file:ml-5 rtl:file:mr-0 file:border-collapse file:cursor-pointer ltr:file:rounded-l-lg rtl:file:rounded-r-lg rtl:file:rounded-l-none file:border-0 ltr:file:border-r rtl:file:border-l rtl:file:border-r-0 file:border-solid file:border-gray-200 file:bg-gray-50 file:py-3 ltr:file:pr-3 ltr:file:pl-3.5 rtl:file:pl-3 rtl:file:pr-3.5 file:text-sm file:text-gray-700 placeholder:text-gray-400 hover:file:bg-gray-100 focus:outline-hidden dark:bg-gray-900 dark:file:border-gray-800 dark:file:bg-white/[0.03] dark:file:text-gray-400"
      :class="
        error
          ? 'border-error-300 focus:border-error-300 dark:border-error-700'
          : 'border-gray-300 focus:border-brand-300 dark:border-gray-700 dark:focus:border-brand-800'
      "
      @change="onChange"
    />
    <p v-if="hint && !error" class="mt-1.5 text-xs text-gray-500 dark:text-gray-400">{{ hint }}</p>
    <p v-if="error" class="mt-1.5 text-xs text-error-500">{{ error }}</p>
  </div>
</template>

<script setup lang="ts">
withDefaults(
  defineProps<{
    label?: string
    accept?: string
    hint?: string
    error?: string | null
    disabled?: boolean
  }>(),
  { label: '', accept: '', hint: '', error: null, disabled: false },
)

const emit = defineEmits<{ (e: 'update:modelValue', file: File | null): void }>()

const onChange = (event: Event) => {
  const input = event.target as HTMLInputElement
  emit('update:modelValue', input.files?.[0] ?? null)
}
</script>
