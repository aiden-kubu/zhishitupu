<template>
  <input
    :type="type"
    :value="modelValue"
    :placeholder="placeholder"
    :disabled="disabled"
    :autocomplete="autocomplete"
    class="dark:bg-dark-900 h-11 w-full rounded-lg border bg-transparent px-4 py-2.5 text-sm text-gray-800 shadow-theme-xs placeholder:text-gray-400 focus:outline-hidden focus:ring-3 disabled:cursor-not-allowed disabled:border-gray-100 disabled:bg-gray-50 dark:disabled:border-gray-800 dark:disabled:bg-white/[0.03] dark:bg-gray-900 dark:text-white/90 dark:placeholder:text-white/30"
    :class="
      error
        ? 'border-error-300 focus:border-error-300 focus:ring-error-500/10 dark:border-error-700 dark:focus:border-error-800'
        : 'border-gray-300 focus:border-brand-300 focus:ring-brand-500/10 dark:border-gray-700 dark:focus:border-brand-800'
    "
    @input="onInput"
  />
</template>

<script setup lang="ts">
withDefaults(
  defineProps<{
    modelValue: string
    type?: string
    placeholder?: string
    disabled?: boolean
    error?: string | null
    autocomplete?: string
  }>(),
  { type: 'text', placeholder: '', disabled: false, error: null, autocomplete: 'off' },
)

const emit = defineEmits<{ (e: 'update:modelValue', value: string): void }>()

const onInput = (event: Event) => {
  emit('update:modelValue', (event.target as HTMLInputElement).value)
}
</script>
