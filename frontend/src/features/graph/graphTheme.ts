/**
 * 图谱专用颜色（§5.2）：集中管理，优先读取 main.css 的主题 CSS 变量，
 * 以保证亮色/暗色模式一致；无法读取时使用与模板令牌一致的回退色。
 */

function cssVar(name: string, fallback: string): string {
  if (typeof window === 'undefined') return fallback
  const value = getComputedStyle(document.documentElement).getPropertyValue(name).trim()
  return value || fallback
}

const NODE_TYPE_VAR: Record<string, string> = {
  course: '--color-brand-500',
  chapter: '--color-blue-light-500',
  knowledge: '--color-success-500',
  concept: '--color-brand-400',
  method: '--color-orange-500',
  application: '--color-warning-500',
  other: '--color-gray-400',
}

const NODE_TYPE_FALLBACK: Record<string, string> = {
  course: '#465fff',
  chapter: '#0ba5ec',
  knowledge: '#12b76a',
  concept: '#7a8ffb',
  method: '#fd853a',
  application: '#f79009',
  other: '#9aa4b2',
}

export function nodeColor(type: string): string {
  const variable = NODE_TYPE_VAR[type]
  const fallback = NODE_TYPE_FALLBACK[type] ?? NODE_TYPE_FALLBACK.other
  return variable ? cssVar(variable, fallback) : fallback
}

export function edgeColor(): string {
  return cssVar('--color-gray-300', '#d5d7da')
}

export function edgeActiveColor(): string {
  return cssVar('--color-brand-500', '#465fff')
}

export function labelColor(): string {
  return cssVar('--color-gray-600', '#4b5563')
}

export function canvasBackground(): string {
  return cssVar('--color-gray-25', '#fcfcfd')
}

/** 节点类型中文标签（与 services/types.ts 的 NODE_TYPE_LABELS 对应，供画布悬停用） */
export const NODE_TYPE_LABELS: Record<string, string> = {
  course: '课程',
  chapter: '章节',
  knowledge: '知识点',
  concept: '概念',
  method: '方法',
  application: '应用',
  other: '其他',
}
