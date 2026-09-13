<template>
  <div class="min-h-screen xl:flex">
    <app-sidebar />
    <Backdrop />
    <div
      class="flex-1 transition-all duration-300 ease-in-out"
      :class="[isExpanded || isHovered ? 'xl:ms-[290px]' : 'xl:ms-[90px]']"
    >
      <app-header />
      <!-- bare：图谱工作台等全高页面使用，去掉内边距与最大宽度约束（§5.3 无双滚动条） -->
      <div
        v-if="bare"
        class="flex h-[calc(100vh-4.1rem)] flex-col overflow-hidden lg:h-[calc(100vh-4.8rem)]"
      >
        <slot></slot>
      </div>
      <!-- 内容区自适应宽度：不设最大宽度上限，随视口流式伸展（反馈①） -->
      <div v-else class="w-full p-4 pb-20 md:p-6 md:pb-6">
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
