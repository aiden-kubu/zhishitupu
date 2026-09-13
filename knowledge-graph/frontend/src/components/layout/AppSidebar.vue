<template>
  <aside
    :class="[
      'fixed flex flex-col mt-0 top-0 px-4 start-0 bg-white dark:bg-gray-900 dark:border-gray-800 text-gray-900 h-screen transition-all duration-300 ease-in-out z-99999 border-e border-gray-200',
      {
        'xl:w-[240px]': isExpanded || isMobileOpen || isHovered,
        'xl:w-[76px]': !isExpanded && !isHovered,
        'translate-x-0 w-[240px]': isMobileOpen,
        'max-xl:-translate-x-full max-xl:rtl:translate-x-full': !isMobileOpen,
        'xl:translate-x-0': true,
      },
    ]"
    @mouseenter="!isExpanded && (isHovered = true)"
    @mouseleave="isHovered = false"
  >
    <!-- 品牌区 -->
    <div
      :class="[
        'flex h-[72px] items-center',
        !isExpanded && !isHovered ? 'xl:justify-center' : 'justify-start',
      ]"
    >
      <AppBrandLogo :compact="!isExpanded && !isHovered && !isMobileOpen" />
    </div>

    <div class="mb-6 h-px bg-gray-100 dark:bg-gray-800" role="presentation"></div>

    <!-- 模块导航（§4.1 六模块三分组） -->
    <nav class="flex min-h-0 flex-1 flex-col overflow-y-auto no-scrollbar">
      <div class="flex flex-col gap-7 pb-4">
        <div v-for="(menuGroup, groupIndex) in menuGroups" :key="groupIndex">
          <h2
            :class="[
              'mb-2 flex items-center leading-4 text-theme-xs font-medium uppercase tracking-wider text-gray-400 dark:text-gray-500',
              !isExpanded && !isHovered ? 'xl:justify-center' : 'justify-start',
            ]"
          >
            <template v-if="isExpanded || isHovered || isMobileOpen">
              {{ menuGroup.title }}
            </template>
            <HorizontalDots v-else class="h-4 w-4" />
          </h2>
          <ul class="flex flex-col gap-1">
            <li v-for="item in menuGroup.items" :key="item.name">
              <router-link
                :to="item.path"
                :aria-label="item.name"
                :title="!isExpanded && !isHovered && !isMobileOpen ? item.name : undefined"
                @click="isMobileOpen && toggleMobileSidebar()"
                :aria-current="isActive(item.path) ? 'page' : undefined"
                :class="[
                  'menu-item group',
                  {
                    'menu-item-active': isActive(item.path),
                    'menu-item-inactive': !isActive(item.path),
                  },
                ]"
              >
                <!-- 统一图标盒：所有图标固定 20px 居中，保证文字左缘对齐 -->
                <span
                  class="flex h-5 w-5 shrink-0 items-center justify-center [&>svg]:h-5 [&>svg]:w-5 [&>svg]:max-w-none"
                  :class="isActive(item.path) ? 'menu-item-icon-active' : 'menu-item-icon-inactive'"
                >
                  <component :is="item.icon" />
                </span>
                <span
                  v-if="isExpanded || isHovered || isMobileOpen"
                  class="menu-item-text truncate"
                  >{{ item.name }}</span
                >
              </router-link>
            </li>
          </ul>
        </div>
      </div>
    </nav>

    <!-- 底部：本地服务状态（真实健康检查数据，30s 刷新） -->
    <div class="pb-5 pt-2">
      <div
        v-if="isExpanded || isHovered || isMobileOpen"
        class="rounded-xl border border-gray-100 bg-gray-50 px-3.5 py-3 dark:border-gray-800 dark:bg-white/[0.03]"
      >
        <div class="flex items-center gap-2">
          <span class="relative flex h-2 w-2 shrink-0">
            <span
              v-if="status === 'up'"
              class="absolute inline-flex h-full w-full animate-ping rounded-full bg-success-400 opacity-60"
            ></span>
            <span
              class="relative inline-flex h-2 w-2 rounded-full"
              :class="
                status === 'up'
                  ? 'bg-success-500'
                  : status === 'down'
                    ? 'bg-error-500'
                    : 'bg-gray-400'
              "
            ></span>
          </span>
          <p class="truncate text-xs font-medium text-gray-700 dark:text-gray-300">
            本地服务{{ statusLabel }}
          </p>
        </div>
        <p class="mt-1 truncate text-[11px] text-gray-400 dark:text-gray-500">
          {{ status === 'up' ? '资料保存在本机' : '正在检查服务连接' }}
        </p>
      </div>
      <div v-else class="flex justify-center py-1">
        <span
          class="h-2 w-2 rounded-full"
          :class="
            status === 'up' ? 'bg-success-500' : status === 'down' ? 'bg-error-500' : 'bg-gray-400'
          "
          :title="`本地服务${statusLabel}`"
        ></span>
      </div>
    </div>
  </aside>
</template>

<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'

import { useSidebar } from '@/composables/useSidebar'
import { useBackendStatus } from '@/composables/useBackendStatus'
import AppBrandLogo from './AppBrandLogo.vue'
import {
  CheckIcon,
  DocsIcon,
  HorizontalDots,
  LayoutDashboardIcon,
  PieChartIcon,
  SettingsIcon,
  TaskIcon,
} from '@/icons'

const route = useRoute()

const { isExpanded, isMobileOpen, isHovered, toggleMobileSidebar } = useSidebar()
const { status, start } = useBackendStatus()

onMounted(() => {
  start()
})

const statusLabel = computed(() =>
  status.value === 'up' ? '在线' : status.value === 'down' ? '离线' : '检测中…',
)

interface MenuItem {
  icon: object
  name: string
  path: string
}

interface MenuGroup {
  title: string
  items: MenuItem[]
}

// §4.1 左侧导航：只放模块，不放重复操作
const menuGroups: MenuGroup[] = [
  {
    title: '主工作区',
    items: [{ icon: LayoutDashboardIcon, name: '图谱工作台', path: '/' }],
  },
  {
    title: '知识管理',
    items: [
      { icon: DocsIcon, name: '知识库', path: '/library' },
      { icon: TaskIcon, name: '处理中心', path: '/processing' },
      { icon: CheckIcon, name: 'AI 复审', path: '/review' },
    ],
  },
  {
    title: '分析与配置',
    items: [
      { icon: PieChartIcon, name: '数据洞察', path: '/insights' },
      { icon: SettingsIcon, name: '系统设置', path: '/settings' },
    ],
  },
]

const isActive = (path: string) => route.path === path
</script>
