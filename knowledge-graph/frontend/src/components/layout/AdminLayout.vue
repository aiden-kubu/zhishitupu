<template>
  <div class="min-h-screen xl:flex">
    <app-sidebar />
    <Backdrop />
    <div
      class="min-w-0 flex-1 transition-all duration-300 ease-in-out"
      :class="[isExpanded || isHovered ? 'xl:ms-[240px]' : 'xl:ms-[76px]']"
    >
      <app-header />
      <!-- bare：图谱工作台等全高页面使用，去掉内边距与最大宽度约束（§5.3 无双滚动条） -->
      <div
        v-if="bare"
        class="flex h-[calc(100dvh-8rem)] flex-col overflow-hidden xl:h-[calc(100dvh-4.5rem)]"
      >
        <slot></slot>
      </div>
      <!-- 管理页面约束阅读宽度；图谱画布继续占满工作区。 -->
      <div v-else class="mx-auto w-full max-w-[1280px] px-4 py-6 pb-16 md:px-8 md:py-8">
        <slot></slot>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import AppSidebar from './AppSidebar.vue'
import AppHeader from './AppHeader.vue'
import { useSidebar } from '@/composables/useSidebar'
import Backdrop from './Backdrop.vue'

withDefaults(defineProps<{ bare?: boolean }>(), { bare: false })

const { isExpanded, isHovered } = useSidebar()
</script>
