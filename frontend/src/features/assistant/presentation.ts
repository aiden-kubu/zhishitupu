import { ApiError } from '@/services/http'
import type { CapabilityId, Evidence, Stage } from '@/services/assistantTypes'
export const capabilityLabels: Record<CapabilityId, string> = {
  ingestion_read: '资料读取',
  research_graph: '图谱研究',
  citation_check: '引用核查',
  source_reread: '原文重读',
  learning_preview: '学习预览',
  assessment_preview: '测评预览',
  formal_assessment: '正式测评',
  real_model: '真实模型',
}
export const stageLabels: Record<Stage, string> = {
  document_metadata: '读取资料信息',
  source_read: '检索原文片段',
  directed_graph: '研究有向图谱',
  citation_check: '核查来源引用',
}
const labels: Record<string, string> = {
  available: '可用',
  active: '处于启用状态',
  inactive: '未启用',
  COMPLETED: '处理完成',
  PROCESSING: '正在处理',
  PENDING: '等待处理',
  FAILED: '处理失败',
  DELETED: '已删除',
  disabled: '已关闭',
  unavailable: '未接入',
  pending: '等待依赖',
  running: '正在运行',
  cancelling: '正在取消',
  completed: '已完成',
  partial: '部分完成',
  needs_input: '需要补充输入',
  failed: '失败',
  blocked: '依赖阻断',
  cancelled: '已取消',
  ok: '成功读取',
  error: '读取失败',
  not_checked: '未核查',
  current: '引用当前有效',
  truncated: '来源截断',
  stale: '来源已变化',
  CURRENT: '引用当前有效',
  TRUNCATED: '核查来源截断',
  STALE: '来源已变化',
  MISSING: '来源缺失',
  WRONG_DOCUMENT: '资料不匹配',
  OUT_OF_SCOPE_OR_MISSING: '范围外或来源缺失',
  IDENTITY_MISMATCH: '来源身份不匹配',
  UNIT_UNAVAILABLE: '来源单元不可用',
  INVALID_POINTER: '原文指针无效',
  UNVERIFIED: '引用未验证',
  UNKNOWN_CLAIM: '论断未知',
  INVALID_SOURCE_COUNT: '引用数量无效',
}
export function statusLabel(value: string): string {
  return labels[value] ?? `${value}（状态未识别）`
}
const reasons: Record<string, string> = {
  REQUESTED_DOCUMENTS_UNCOVERED: '所选资料仍有未覆盖部分，请查看资料范围与缺口。',
  NO_FORMAL_SOURCE_MATCH: '未找到符合正式来源条件的匹配片段，可检查关键词是否出现在原文中。',
  ASSISTANT_DISABLED: '助手服务已关闭，当前无法执行任务。',
  NOT_IN_STAGE1_TEMPLATE: '当前模板尚未接入此能力。',
  FORMAL_SCORING_UNAVAILABLE: '正式测评尚不可用，不提供正式掌握度结论。',
  REAL_MODEL_NOT_CONNECTED: '尚未连接真实模型。',
  FULL_CORPUS_COVERAGE_NOT_VERIFIED: '尚未确认覆盖全部资料。',
  UPSTREAM_OR_TOOL_RESULT_INCOMPLETE: '上游或工具结果不完整，成果仍有缺口。',
  QUOTE_EXCERPT_ONLY: '仅返回原文摘录，不代表完整正文。',
  GRAPH_SEMANTICS_NOT_EVALUATED: '图谱内容语义未经评估。',
  GRAPH_SOURCE_OR_REVIEW_UNVERIFIED: '图谱来源或审查状态尚未核实。',
  GRAPH_FACT_NOT_HUMAN_VERIFIED: '图谱事实未经人工核验。',
  PARTIAL_DEPENDENCY: '依赖阶段仅部分完成，当前结果受其限制。',
  SOURCE_STALE: '来源已变化，原文未能按原指针重读。',
  STALE_SOURCE: '来源已变化，原文未能按原指针重读。',
  NOT_FOUND: '请求的对象不存在或已不可用。',
  RUN_EXPIRED: '原运行已过期，无法读取。',
  SCOPE_NOT_ALLOWED: '请求未获范围授权，无法读取。',
  OUT_OF_SCOPE_OR_MISSING: '来源超出范围或已缺失，无法读取。',
  BAD_RESPONSE: '服务返回内容未通过校验，无法展示。',
  NETWORK_ERROR: '无法连接助手服务，请检查接入状态。',
  TRUNCATED: '返回内容被截断，覆盖存在限制。',
  STALE: '来源已变化，需要重新核对。',
  MISSING: '来源缺失，无法核对。',
  WRONG_DOCUMENT: '引用与资料不匹配。',
  IDENTITY_MISMATCH: '来源身份不匹配。',
  UNIT_UNAVAILABLE: '来源单元不可用。',
  INVALID_POINTER: '原文指针无效，无法重读。',
  UNVERIFIED: '引用尚未验证。',
  UNKNOWN_CLAIM: '论断未知，无法核查。',
  INVALID_SOURCE_COUNT: '引用数量无效，无法核查。',
}
export function reasonLabel(value: string): string {
  return (
    reasons[value] ??
    (/^[A-Za-z0-9_.:-]+$/.test(value)
      ? '服务报告了尚未解释的原因，请展开诊断详情查看原始信息。'
      : value)
  )
}
// 只映射确定性阶段键，保留真正的自然语言摘要和原文。
export function stageSummary(value: string): string {
  return Object.prototype.hasOwnProperty.call(stageLabels, value)
    ? `${stageLabels[value as Stage]}阶段结果`
    : value
}
export function evidenceKindLabel(entry: Evidence): string {
  if (entry.rereadPointer) return '原文引用（含重读指针）'
  if (entry.kind === 'system_state')
    return entry.object.kind === 'document' ? '资料状态记录' : '系统状态记录'
  return entry.kind === 'graph_fact' ? '图谱引用' : '其他来源引用'
}
export function errorSummary(value: string): string {
  if (value.startsWith('宿主/API 未接入')) return '宿主或接口尚未接入，暂时无法使用助手服务。'
  if (value.startsWith('内存运行已过期'))
    return '原运行不存在或已过期，无法恢复；可手动返回空白助手。'
  if (value.startsWith('原文重读失败')) return '原文重读失败，正文已清除；请核对来源状态。'
  if (value.startsWith('请求失败。')) return '请求失败，请展开错误详情核对原因。'
  return value
}
export function errorMessage(
  error: unknown,
  context: 'service' | 'run' | 'source' = 'service',
): string {
  if (error instanceof ApiError) {
    const code = error.code
    const prefix =
      context === 'run' &&
      (code === 'NOT_FOUND' ||
        code === 'RUN_EXPIRED' ||
        error.status === 404 ||
        error.status === 410)
        ? '内存运行已过期或在宿主重启后不可恢复，请重新发起。'
        : context === 'service' &&
            (error.status === 404 || ['NETWORK_ERROR', 'BAD_RESPONSE'].includes(code))
          ? '宿主/API 未接入，暂时无法使用助手服务。'
          : context === 'source'
            ? '原文重读失败，保留当前引用；请核对来源状态。'
            : '请求失败。'
    return `${prefix} ${error.message}（${code}${error.requestId ? `，请求编号 ${error.requestId}` : ''}）`
  }
  return error instanceof Error ? error.message : '请求失败，请稍后手动重试。'
}
export function graphContext(runId: string, artifactId: string) {
  return { path: '/', query: { runId, artifactId } }
}
