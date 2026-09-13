<template>
  <button
    :class="[
      'inline-flex shrink-0 items-center justify-center whitespace-nowrap font-medium gap-2 rounded-lg transition-colors focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand-500',
      sizeClasses[size],
      variantClasses[variant],
      className,
      { 'cursor-not-allowed opacity-50': disabled },
    ]"
    @click="onClick"
    :disabled="disabled"
  >
    <span v-if="startIcon" class="flex h-4 w-4 items-center justify-center [&>svg]:h-4 [&>svg]:w-4">
      <component :is="startIcon" />
    </span>
    <slot></slot>
    <span v-if="endIcon" class="flex h-4 w-4 items-center justify-center [&>svg]:h-4 [&>svg]:w-4">
      <component :is="endIcon" />
    </span>
  </button>
</template>

<script setup lang="ts">
interface ButtonProps {
  size?: 'sm' | 'md'
  variant?: 'primary' | 'outline' | 'ghost' | 'danger'
  startIcon?: object
  endIcon?: object
  onClick?: () => void
  className?: string
  disabled?: boolean
}

const props = withDefaults(defineProps<ButtonProps>(), {
  size: 'md',
  variant: 'primary',
  className: '',
  disabled: false,
})

const sizeClasses = {
  sm: 'min-h-9 px-3 py-2 text-xs sm:text-[13px]',
  md: 'min-h-10 px-4 py-2.5 text-sm',
}

const variantClasses = {
  primary: 'bg-brand-500 text-white shadow-theme-xs hover:bg-brand-600 disabled:bg-brand-300',
  ghost:
    'bg-transparent text-gray-500 hover:bg-gray-100 hover:text-gray-800 dark:text-gray-400 dark:hover:bg-gray-800 dark:hover:text-gray-200',
  danger: 'bg-error-500 text-white hover:bg-error-600 disabled:bg-error-300',
  outline:
    'bg-white text-gray-700 ring-1 ring-inset ring-gray-300 hover:bg-gray-50 dark:bg-gray-800 dark:text-gray-300 dark:ring-gray-700 dark:hover:bg-white/[0.03] dark:hover:text-gray-300',
}

const onClick = () => {
  if (!props.disabled && props.onClick) {
    props.onClick()
  }
}
</script>
