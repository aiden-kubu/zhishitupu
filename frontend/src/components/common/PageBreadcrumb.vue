<template>
  <div class="mb-7 flex flex-wrap items-start justify-between gap-4">
    <div class="min-w-0 flex-1">
      <h1 class="text-xl font-semibold tracking-tight text-gray-900 dark:text-white/90">
        {{ pageTitle }}
      </h1>
      <p
        v-if="description"
        class="mt-1.5 max-w-2xl text-sm leading-6 text-gray-500 dark:text-gray-400"
      >
        {{ description }}
      </p>
    </div>
    <div class="flex flex-col items-end gap-3">
      <nav aria-label="面包屑" class="hidden sm:block">
        <ol class="flex items-center gap-2 text-xs text-gray-500 dark:text-gray-400">
          <li><router-link to="/" class="hover:text-brand-500">工作台</router-link></li>
          <li aria-hidden="true"><ChevronRightIcon class="h-3.5 w-3.5 rtl:rotate-180" /></li>
          <li aria-current="page" class="text-gray-700 dark:text-gray-300">{{ pageTitle }}</li>
        </ol>
      </nav>
      <div v-if="$slots.actions" class="flex flex-wrap items-center gap-2">
        <slot name="actions" />
      </div>
    </div>
  </div>
</template>
<script setup lang="ts">
import { ChevronRightIcon } from '@/icons'
defineProps<{ pageTitle: string; description?: string }>()
</script>
