import { ref } from 'vue'

/**
 * 全局唯一的「导入资料」弹窗状态（§4.2：导入资料只有一个全局入口）。
 * 模块级 ref 共享，跨组件（顶栏按钮 / 处理中心页面）打开同一个弹窗，不引入 Pinia。
 */
const isOpen = ref(false)

export function useImportModal() {
  const open = () => {
    isOpen.value = true
  }
  const close = () => {
    isOpen.value = false
  }
  return { isOpen, open, close }
}
