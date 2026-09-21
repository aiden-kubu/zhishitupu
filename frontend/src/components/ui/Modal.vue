<template>
  <Teleport to="body">
    <div ref="dialog" role="dialog" aria-modal="true" :aria-label="title" tabindex="-1"
      class="modal-shell fixed inset-0 z-99999 flex items-center justify-center outline-none" @keydown="onKeydown">
      <div v-if="fullScreenBackdrop" class="fixed inset-0 bg-gray-900/40 backdrop-blur-sm" aria-hidden="true" @click="emit('close')"></div>
      <slot name="body"></slot>
    </div>
  </Teleport>
</template>
<script setup lang="ts">
import { onMounted, onBeforeUnmount, ref } from 'vue'
defineProps<{ fullScreenBackdrop?: boolean }>()
const emit = defineEmits<{ (e: 'close'): void }>()
const dialog = ref<HTMLElement | null>(null)
const title = ref('对话框')
let previousFocus: HTMLElement | null = null
let previousOverflow = ''
function onKeydown(event: KeyboardEvent) {
  if (event.key === 'Escape') { event.preventDefault(); emit('close'); return }
  if (event.key !== 'Tab') return
  const elements = Array.from(dialog.value?.querySelectorAll<HTMLElement>('button:not(:disabled), a[href], input:not(:disabled), textarea:not(:disabled), [tabindex="0"], summary') ?? []).filter(el => el.getClientRects().length)
  const first = elements[0], last = elements[elements.length - 1]
  if (!first) { event.preventDefault(); return }
  if (event.shiftKey && (document.activeElement === first || document.activeElement === dialog.value)) { event.preventDefault(); last?.focus() }
  else if (!event.shiftKey && (document.activeElement === last || document.activeElement === dialog.value)) { event.preventDefault(); first.focus() }
}
onMounted(() => {
  previousFocus = document.activeElement as HTMLElement
  previousOverflow = document.body.style.overflow
  document.body.style.overflow = 'hidden'
  title.value = dialog.value?.querySelector('h2, h3, h4')?.textContent?.trim() || '对话框'
  dialog.value?.focus()
})
onBeforeUnmount(() => {
  document.body.style.overflow = previousOverflow
  previousFocus?.focus()
})
</script>
<style scoped>
.modal-shell :slotted(div) { max-height: calc(100dvh - 2rem); overflow-y: auto; overscroll-behavior: contain; }
</style>
