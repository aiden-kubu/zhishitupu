<template>
  <header
    class="sticky top-0 z-40 border-b border-gray-200 bg-white xl:h-[72px] dark:border-gray-800 dark:bg-gray-900"
  >
    <div
      class="flex flex-wrap items-center gap-3 px-4 py-3 xl:h-full xl:flex-nowrap xl:px-6 xl:py-0"
    >
      <button
        aria-label="展开或收起导航"
        :aria-expanded="isMobile ? isMobileOpen : isExpanded"
        @click="handleToggle"
        class="flex h-10 w-10 shrink-0 items-center justify-center rounded-lg border border-gray-200 text-gray-500 hover:bg-gray-50 dark:border-gray-800 dark:text-gray-400 dark:hover:bg-gray-800"
      >
        <MenuIcon class="h-4 w-4" />
      </button>
      <AppBrandLogo compact class="xl:hidden" aria-label="知识图谱工作台" />
      <SearchBar class="order-last w-full xl:order-none xl:w-auto" />
      <div class="ms-auto flex shrink-0 items-center gap-2">
        <span
          class="me-3 hidden items-center gap-1.5 text-xs xl:flex"
          :class="
            status === 'up'
              ? 'text-success-600 dark:text-success-400'
              : 'text-gray-500 dark:text-gray-400'
          "
        >
          <span
            class="h-1.5 w-1.5 rounded-full"
            :class="status === 'up' ? 'bg-success-500' : 'bg-gray-400'"
          ></span
          >{{ status === 'up' ? '本地服务正常' : status === 'down' ? '本地服务离线' : '正在连接' }}
        </span>
        <ThemeToggler />
        <Button size="sm" :start-icon="PlusIcon" @click="openImport">导入资料</Button>
      </div>
    </div>
  </header>
</template>
<script setup lang="ts">
import { useBackendStatus } from '@/composables/useBackendStatus'
const { status } = useBackendStatus()
import { useSidebar } from '@/composables/useSidebar'
import ThemeToggler from '../common/ThemeToggler.vue'
import SearchBar from './header/SearchBar.vue'
import AppBrandLogo from './AppBrandLogo.vue'
import Button from '@/components/ui/Button.vue'
import { PlusIcon, MenuIcon } from '@/icons'
import { useImportModal } from '@/composables/useImportModal'
const { toggleSidebar, toggleMobileSidebar, isMobileOpen, isMobile, isExpanded } = useSidebar()
const handleToggle = () => (window.innerWidth >= 1280 ? toggleSidebar() : toggleMobileSidebar())
const { open: openImport } = useImportModal()
</script>
