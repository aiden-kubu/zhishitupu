<template>
  <div class="flex flex-wrap items-center gap-x-4 gap-y-2">
    <!-- 视图范围（§6.1：全局/局部/聚焦） -->
    <div class="inline-flex items-center rounded-lg bg-gray-100 p-1 dark:bg-gray-800">
      <button
        v-for="item in modeItems"
        :key="item.value"
        type="button"
        class="rounded-md px-3 py-1.5 text-sm font-medium transition"
        :class="
          mode === item.value
            ? 'bg-white text-gray-800 shadow-theme-xs dark:bg-gray-900 dark:text-white/90'
            : 'text-gray-500 hover:text-gray-700 dark:text-gray-400 dark:hover:text-gray-200'
        "
        :disabled="item.disabled"
        @click="emit('update:mode', item.value)"
      >
        {{ item.label }}
      </button>
    </div>

    <!-- 渲染模式（§6.3 可切换 2D/3D）；深度由模式固定：局部 2 层、聚焦 1 层（GR02） -->
    <div class="inline-flex items-center rounded-lg bg-gray-100 p-1 dark:bg-gray-800">
      <button
        type="button"
        class="rounded-md px-3 py-1.5 text-sm font-medium transition"
        :class="
          renderMode === '2d'
            ? 'bg-white text-gray-800 shadow-theme-xs dark:bg-gray-900 dark:text-white/90'
            : 'text-gray-500 hover:text-gray-700 dark:text-gray-400 dark:hover:text-gray-200'
        "
        @click="emit('update:renderMode', '2d')"
      >
        2D
      </button>
      <button
        type="button"
        class="rounded-md px-3 py-1.5 text-sm font-medium transition"
        :class="
          renderMode === '3d'
            ? 'bg-white text-gray-800 shadow-theme-xs dark:bg-gray-900 dark:text-white/90'
            : 'text-gray-500 hover:text-gray-700 dark:text-gray-400 dark:hover:text-gray-200'
        "
        @click="emit('update:renderMode', '3d')"
      >
        3D
      </button>
    </div>

    <div class="ms-auto flex items-center gap-3">
      <span v-if="truncated" class="text-xs text-warning-600 dark:text-orange-400">
        已截断：仅显示前 {{ nodeCount }} 节点 / {{ edgeCount }} 关系
      </span>
      <span v-else class="text-xs text-gray-500 dark:text-gray-400">
        {{ nodeCount }} 节点 · {{ edgeCount }} 关系
      </span>

      <Button size="sm" variant="outline" class="!px-3 !py-1.5" @click="emit('reset')">重置</Button>

      <!-- AI 面板开关：桌面折叠 / 移动端抽屉 -->
      <Button size="sm" variant="outline" class="!px-3 !py-1.5" :class="{ 'lg:hidden': panelOpen }" @click="emit('toggle-panel')">
        AI 助手
      </Button>
    </div>
  </div>
</template>

<script setup lang="ts">
import Button from '@/components/ui/Button.vue'
import type { RenderMode, WorkspaceMode } from '@/composables/useGraphWorkspace'

defineProps<{
  panelOpen?: boolean
  mode: WorkspaceMode
  renderMode: RenderMode
  nodeCount: number
  edgeCount: number
  truncated: boolean
}>()

const emit = defineEmits<{
  (e: 'update:mode', value: WorkspaceMode): void
  (e: 'update:renderMode', value: RenderMode): void
  (e: 'reset'): void
  (e: 'toggle-panel'): void
}>()

const modeItems: { value: WorkspaceMode; label: string; disabled?: boolean }[] = [
  { value: 'global', label: '全局' },
  { value: 'local', label: '局部' },
  { value: 'focus', label: '聚焦' },
]
</script>
