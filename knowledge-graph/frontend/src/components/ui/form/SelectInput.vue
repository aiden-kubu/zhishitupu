<template>
  <div v-click-outside="close" class="relative z-20">
    <!-- 触发按钮：沿用模板输入框视觉 -->
    <button
      type="button"
      :disabled="disabled"
      class="dark:bg-dark-900 flex h-11 w-full items-center justify-between gap-2 rounded-lg border bg-transparent px-4 py-2.5 text-start text-sm shadow-theme-xs transition focus:outline-hidden focus:ring-3 disabled:cursor-not-allowed disabled:border-gray-100 disabled:bg-gray-50 dark:bg-gray-900 dark:disabled:border-gray-800 dark:disabled:bg-white/[0.03]"
      :class="
        error
          ? 'border-error-300 focus:border-error-300 focus:ring-error-500/10 dark:border-error-700'
          : 'border-gray-300 focus:border-brand-300 focus:ring-brand-500/10 dark:border-gray-700 dark:focus:border-brand-800'
      "
      :aria-expanded="open"
      @click="toggle"
      @keydown="onKeydown"
    >
      <span
        v-if="selectedLabel"
        class="truncate text-gray-800 dark:text-white/90"
      >
        {{ selectedLabel }}
      </span>
      <span v-else class="truncate text-gray-400 dark:text-gray-500">
        {{ placeholder || '请选择' }}
      </span>
      <svg
        class="shrink-0 stroke-gray-400 transition-transform duration-200 dark:stroke-gray-500"
        :class="open ? 'rotate-180' : ''"
        width="20"
        height="20"
        viewBox="0 0 20 20"
        fill="none"
        xmlns="http://www.w3.org/2000/svg"
      >
        <path
          d="M4.79175 7.396L10.0001 12.6043L15.2084 7.396"
          stroke=""
          stroke-width="1.5"
          stroke-linecap="round"
          stroke-linejoin="round"
        />
      </svg>
    </button>

    <!-- 自绘下拉面板（替代原生 select 弹层，反馈②） -->
    <transition
      enter-active-class="transition duration-150 ease-out"
      enter-from-class="-translate-y-1 opacity-0"
      enter-to-class="translate-y-0 opacity-100"
      leave-active-class="transition duration-100 ease-in"
      leave-from-class="translate-y-0 opacity-100"
      leave-to-class="-translate-y-1 opacity-0"
    >
      <div
        v-if="open"
        class="absolute inset-x-0 top-full z-50 mt-1.5 max-h-60 overflow-y-auto rounded-xl border border-gray-200 bg-white p-1.5 shadow-theme-lg dark:border-gray-800 dark:bg-gray-900"
        role="listbox"
      >
        <button
          v-for="(option, index) in selectableOptions"
          :key="option.value"
          type="button"
          role="option"
          :aria-selected="option.value === modelValue"
          class="flex w-full items-center justify-between gap-2 rounded-lg px-3 py-2 text-start text-sm transition"
          :class="
            option.value === modelValue
              ? 'bg-brand-50 font-medium text-brand-600 dark:bg-brand-500/15 dark:text-brand-400'
              : index === activeIndex
                ? 'bg-gray-100 text-gray-800 dark:bg-white/[0.06] dark:text-white/90'
                : 'text-gray-600 hover:bg-gray-50 dark:text-gray-300 dark:hover:bg-white/[0.03]'
          "
          @mouseenter="activeIndex = index"
          @click="select(option)"
        >
          <span class="truncate">{{ option.label }}</span>
          <svg
            v-if="option.value === modelValue"
            class="shrink-0 stroke-brand-500 dark:stroke-brand-400"
            width="16"
            height="16"
            viewBox="0 0 20 20"
            fill="none"
            xmlns="http://www.w3.org/2000/svg"
          >
            <path
              d="M4.5 10.5L8.2 14.2L15.5 6.5"
              stroke=""
              stroke-width="1.8"
              stroke-linecap="round"
              stroke-linejoin="round"
            />
          </svg>
        </button>
      </div>
    </transition>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import vClickOutside from '@/components/common/v-click-outside.vue'

export interface SelectOption {
  value: string
  label: string
}

const props = withDefaults(
  defineProps<{
    modelValue: string
    options: SelectOption[]
    placeholder?: string
    disabled?: boolean
    error?: string | null
  }>(),
  { placeholder: '', disabled: false, error: null },
)

const emit = defineEmits<{ (e: 'update:modelValue', value: string): void }>()

const open = ref(false)
const activeIndex = ref(0)

const selectableOptions = computed(() => {
  // 已有空值选项时直接使用它，避免占位符重复生成同一个选项。
  if (props.placeholder && !props.options.some((option) => option.value === '')) {
    return [{ value: '', label: props.placeholder }, ...props.options]
  }
  return props.options
})

const selectedLabel = computed(
  () => props.options.find((option) => option.value === props.modelValue)?.label ?? '',
)

function toggle() {
  if (props.disabled) return
  open.value = !open.value
  if (open.value) {
    const current = selectableOptions.value.findIndex(
      (option) => option.value === props.modelValue,
    )
    activeIndex.value = current >= 0 ? current : 0
  }
}

function close() {
  open.value = false
}

function select(option: SelectOption) {
  emit('update:modelValue', option.value)
  close()
}

function onKeydown(event: KeyboardEvent) {
  if (props.disabled) return
  if (!open.value) {
    if (['Enter', ' ', 'ArrowDown', 'ArrowUp'].includes(event.key)) {
      event.preventDefault()
      toggle()
    }
    return
  }
  if (event.key === 'ArrowDown') {
    event.preventDefault()
    activeIndex.value = (activeIndex.value + 1) % selectableOptions.value.length
  } else if (event.key === 'ArrowUp') {
    event.preventDefault()
    activeIndex.value =
      (activeIndex.value - 1 + selectableOptions.value.length) % selectableOptions.value.length
  } else if (event.key === 'Enter') {
    event.preventDefault()
    const option = selectableOptions.value[activeIndex.value]
    if (option) select(option)
  } else if (event.key === 'Escape') {
    event.stopPropagation()
    close()
  }
}
</script>
