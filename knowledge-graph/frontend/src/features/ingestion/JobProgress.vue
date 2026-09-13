<template>
  <div class="flex items-center gap-2">
    <div class="h-1.5 w-24 overflow-hidden rounded-full bg-gray-100 dark:bg-gray-800">
      <div
        class="h-full rounded-full transition-all"
        :class="progress >= 100 ? 'bg-success-500' : 'bg-brand-500'"
        :style="{ width: `${Math.min(Math.max(progress, 0), 100)}%` }"
      ></div>
    </div>
    <span class="w-10 text-xs text-gray-500 dark:text-gray-400">{{ Math.round(progress) }}%</span>
  </div>
</template>

<script setup lang="ts">
defineProps<{ progress: number }>()
</script>
