<template>
  <ThemeProvider>
    <SidebarProvider>
      <RouterView />
      <!-- 全局唯一「导入资料」弹窗（§4.2），由顶栏按钮与处理中心页共用 -->
      <ImportDocumentModal />
    </SidebarProvider>
  </ThemeProvider>
</template>

<script setup lang="ts">
import ThemeProvider from './components/layout/ThemeProvider.vue'
import SidebarProvider from './components/layout/SidebarProvider.vue'
import ImportDocumentModal from './features/ingestion/ImportDocumentModal.vue'
</script>
