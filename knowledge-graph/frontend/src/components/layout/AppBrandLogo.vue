<template>
  <router-link to="/" class="flex items-center gap-2.5">
    <!-- 项目品牌标记：知识图谱节点图形 -->
    <span
      class="flex h-9 w-9 shrink-0 items-center justify-center rounded-xl bg-brand-500 shadow-theme-xs"
    >
      <svg width="20" height="20" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
        <path
          d="M12 8.6L7.2 15.4M12 8.6L16.8 14.2"
          stroke="white"
          stroke-width="1.6"
          stroke-linecap="round"
          opacity="0.85"
        />
        <circle cx="12" cy="6.4" r="2.6" fill="white" />
        <circle cx="6.4" cy="17.2" r="2.3" fill="white" fill-opacity="0.95" />
        <circle cx="17.6" cy="16" r="2.1" fill="white" fill-opacity="0.8" />
      </svg>
    </span>
    <span
      v-if="!compact"
      class="truncate text-[17px] font-bold text-gray-800 dark:text-white/90"
    >
      知识图谱系统
    </span>
  </router-link>
</template>

<script setup lang="ts">
import { RouterLink } from 'vue-router'

withDefaults(defineProps<{ compact?: boolean }>(), { compact: false })
</script>
