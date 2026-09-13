/**
 * 与后端 Java API 对齐的类型定义（见 docs/第一版开发文档-Java-TailAdmin.md §11/§12）
 */

/** 统一响应包络：成功 code=0，失败 code 为字符串错误码 */
export interface ApiEnvelope<T> {
  code: number | string
  message: string
  data: T
  requestId?: string
}

/** §12.2 子图响应 */
export interface SubgraphDto {
  centerNodeId?: number | null
  nodes: GraphNodeDto[]
  edges: GraphEdgeDto[]
  truncated: boolean
  totalNodes: number
  totalEdges: number
}

export interface GraphNodeDto {
  id: number
  name: string
  type: string
  definition?: string | null
  degree: number
  sourceCount: number
}

export interface GraphEdgeDto {
  id: number
  source: number
  target: number
  relation: string
  weight: number
}

/** §6.2 搜索联想项 */
export interface SuggestionDto {
  id: number
  name: string
  nameEn?: string | null
  aliases: string[]
  type: string
  sourceCount: number
  degree?: number
  tier?: number
}

/** POST /api/search/ai-expand：搜索未命中时由 AI 生成并收录的节点 */
export interface AiExpandNodeDto {
  id: number
  name: string
  nameEn?: string | null
  type: string
  definition?: string | null
  aliases: string[]
}

export interface AiExpandResultDto {
  node: AiExpandNodeDto
  /** false 表示请求到达时节点已存在，本次未调用模型 */
  created: boolean
  relationsCreated: number
}

/** §11.1 knowledge_libraries */
export interface LibraryDto {
  id: number
  name: string
  type: 'course' | 'book' | 'topic'
  description?: string | null
  status: string
  documentCount?: number
  nodeCount?: number
  edgeCount?: number
  createdAt?: string
  updatedAt?: string
  aliases?: string[]
}

/** GET /api/nodes/{id} 节点详情（后端 NodeDetail） */
export interface NodeDetailDto {
  id: number
  name: string
  nameEn?: string | null
  type: string
  definition?: string | null
  properties?: Record<string, unknown> | null
  status: string
  createdAt?: string
  aliases: string[]
  degree: number
  sourceCount: number
  libraries: { id: number; name: string }[]
}

/** §9.4 数据洞察汇总（后端 InsightSummary） */
export interface InsightSummaryDto {
  nodeCount: number
  edgeCount: number
  documentCount: number
  pendingReviewCount: number
  nodeTypeDistribution: { type: string; count: number }[]
  topDegreeNodes: { id: number; name: string; type: string; degree: number }[]
  isolatedNodes: { id: number; name: string; type: string }[]
}

/** 模型档案（多模型支持；密钥只返回掩码） */
export interface LlmProfileDto {
  id: number
  name: string
  baseUrl: string
  model: string
  apiKeyConfigured: boolean
  apiKeyMasked?: string | null
  timeoutMs: number
  maxOutputTokens: number
  /** 该模型是否具备视觉能力（图片 OCR / 文档视觉解析路由依据） */
  vision: boolean
  enabled: boolean
  isDefault: boolean
}

export interface LlmProfileSavePayload {
  name: string
  baseUrl: string
  model: string
  /** null/缺省=保持原值；空字符串=清除已存密钥 */
  apiKey?: string
  timeoutMs?: number
  maxOutputTokens?: number
  vision?: boolean
  enabled?: boolean
}

/** §11.4 系统配置视图（secrets 只返回掩码；后端 SettingsService.view()） */
export interface SystemSettingsDto {
  llm: {
    baseUrl: string
    model: string
    apiKeyConfigured: boolean
    apiKeyMasked?: string | null
    timeoutMs: number
    maxOutputTokens: number
    /** 模型是否具备视觉能力（可做图片 OCR / 文档视觉解析），如 DeepSeek 纯文本模型为 false */
    vision: boolean
  }
  ocr: {
    mode: string
    dataPath: string
    configured: boolean
  }
  storage: {
    root: string
    maxUploadSizeMb: number
  }
  graph: {
    defaultDepth: number
    maxNodes: number
  }
}

/** PUT /api/settings 请求体（后端 SaveRequest；缺省段由后端按现有值保留） */
export interface SettingsSaveRequest {
  llm?: {
    baseUrl?: string
    model?: string
    apiKey?: string
    timeoutMs?: number
    maxOutputTokens?: number
    vision?: boolean
  }
  ocr?: { mode?: string; dataPath?: string }
  storage?: { maxUploadSizeMb?: number }
  graph?: { defaultDepth?: number; maxNodes?: number }
}

export interface RelationTypeDto {
  name: string
  direction: string
  enabled: boolean
}

/** §12.4 处理任务（后端 IngestionJobRow） */
export interface ProcessingJobDto {
  id: number
  documentId: number | null
  documentName?: string
  stage: string
  status: string
  progress: number
  processedUnits: number
  totalUnits: number
  errorCode?: string | null
  errorMessage?: string | null
  retryCount: number
  createdAt: string
  startedAt?: string | null
  finishedAt?: string | null
  /** 已解析但尚无候选结果的旧任务可发起「提取知识」 */
  extractable?: boolean
  candidateCount?: number
}

export interface PageDto<T> {
  items: T[]
  page: number
  pageSize: number
  total: number
}

/** §12.6 AI 问答 */
export interface ChatMessageDto {
  id?: number
  role: 'user' | 'assistant'
  content: string
  citations: ChatCitationDto[]
  insufficientEvidence?: boolean
  error?: string | null
  createdAt?: string
}

export interface ChatCitationDto {
  index: number
  documentId: number
  documentName: string
  unitType: 'page' | 'slide' | 'image'
  unitIndex: number
  chunkId: number
  excerpt: string
}

/** 文档标签（source：human 人工 / ai 自动整理） */
export interface DocumentTagDto {
  tag: string
  source: 'human' | 'ai'
}

/** 文档可信度汇总（verification_json） */
export interface DocumentVerificationDto {
  hash?: { sha256: string; checkedAt: string; matched?: boolean }
  aiReview?: { total: number; approved: number; rejected: number; model: string; reviewedAt: string }
  human?: { checked: boolean; note?: string; checkedAt: string }
}

/** 文档（含元数据扩展） */
export interface DocumentDto {
  id: number
  libraryId: number
  libraryName: string
  originalName: string
  mimeType?: string
  extension: string
  sizeBytes: number
  sha256: string
  status: string
  createdAt: string
  title?: string | null
  lifecycleStatus?: 'active' | 'archived' | 'outdated' | null
  tags: DocumentTagDto[]
  verification?: DocumentVerificationDto | null
  updatedAt?: string
}

/** 节点类型（§11.1 首批值） */
export const NODE_TYPES = [
  'course',
  'chapter',
  'knowledge',
  'concept',
  'method',
  'application',
  'other',
] as const

export const NODE_TYPE_LABELS: Record<string, string> = {
  course: '课程',
  chapter: '章节',
  knowledge: '知识点',
  concept: '概念',
  method: '方法',
  application: '应用',
  other: '其他',
}

/** §8.4 任务状态 → 中文映射（前端负责映射） */
export const JOB_STATUS_LABELS: Record<string, string> = {
  UPLOADED: '已上传',
  VALIDATING: '校验中',
  PARSING: '解析中',
  OCR_RUNNING: 'OCR 识别中',
  CHUNKING: '分段中',
  AI_EXTRACTING: 'AI 抽取中',
  AWAITING_REVIEW: '等待 AI 复审',
  AI_REVIEWING: 'AI 复审中',
  IMPORTING: '入库中',
  COMPLETED: '已完成',
  FAILED: '失败',
  CANCELLED: '已取消',
}

export const JOB_ACTIVE_STATUSES = [
  'UPLOADED',
  'VALIDATING',
  'PARSING',
  'OCR_RUNNING',
  'CHUNKING',
  'AI_EXTRACTING',
  'AI_REVIEWING',
  'IMPORTING',
]

/** 候选审核状态（§8.8） */
export const REVIEW_STATUS_LABELS: Record<string, string> = {
  PENDING: '待审核',
  ACCEPTED: '已接受',
  REJECTED: '已拒绝',
  MERGE: '合并建议',
  EDITED: '已编辑',
}
