// 08-stage1-v1：数据库 Long 始终为十进制字符串，不转成 Number。
export type LongId = string
export type Availability = 'available' | 'disabled' | 'unavailable'
export type CapabilityId =
  | 'ingestion_read'
  | 'research_graph'
  | 'citation_check'
  | 'source_reread'
  | 'learning_preview'
  | 'assessment_preview'
  | 'formal_assessment'
  | 'real_model'
export type RunStatus =
  'running' | 'cancelling' | 'completed' | 'partial' | 'needs_input' | 'failed' | 'cancelled'
export type TaskStatus = Exclude<RunStatus, 'cancelling'> | 'pending' | 'blocked'
export type Stage = 'document_metadata' | 'source_read' | 'directed_graph' | 'citation_check'
export interface Scope {
  mode: 'project' | 'selected'
  libraryIds: LongId[]
  documentIds: LongId[]
  nodeIds: LongId[]
}
export interface Usage {
  decisionCalls: number
  toolCalls: number
  modelCalls: 0
  accountingMode: 'runtime_model_counter_as_decision_calls'
}
export interface Coverage {
  completeCoverage: boolean
  truncated: boolean
  coverageKind: string
  uncoveredDocumentIds: LongId[]
  limitations: string[]
}
export interface Capability {
  id: CapabilityId
  status: Availability
  reasonCodes: string[]
  readOnly: true
  formalAvailable: boolean
}
export interface Capabilities {
  contractVersion: '08-stage1-v1'
  hostInstanceId: string
  observedAt: string
  executionMode: 'deterministic_tools'
  accessMode: 'isolated_local_single_owner'
  multiUserIsolation: false
  databaseIdentityVerified: boolean
  capabilities: Capability[]
  persistence: {
    mode: 'memory'
    recoverableAfterRestart: false
    recoverableAfterRefresh: false
    terminalRetentionSeconds: 1800
    maxRetainedRuns: 32
    maxActiveRuns: 8
  }
}
export interface Template {
  templateId: 'source_graph_citation_v1'
  label: string
  status: Availability
  reasonCodes: string[]
  minDocuments: 1
  maxDocuments: 2
  queryMinLength: 2
  queryMaxLength: 200
  goalPolicy: 'display_only_no_plan_change'
  executionMode: 'deterministic_tools'
  stages: Stage[]
}
export interface TemplateList {
  items: Template[]
  observedAt: string
}
export interface LibraryOption {
  libraryId: LongId
  name: string
  status: string
  selectable: boolean
  reasonCodes: string[]
}
export interface DocumentOption {
  documentId: LongId
  title: string | null
  originalName: string
  visibleLibraryIds: LongId[]
  processingStatus: string
  lifecycleStatus: string | null
  selectable: boolean
  reasonCodes: string[]
}
export interface ScopePage<T> {
  items: T[]
  total: string
  page: number
  pageSize: number
  hasMore: boolean
  authorizedScope: Scope
  observedAt: string
  coverage: Coverage
}
export interface CreateRun {
  templateId: 'source_graph_citation_v1'
  selection: { libraryIds: LongId[]; documentIds: LongId[] }
  query: string
  goal?: string
}
export interface Task {
  taskId: string
  role:
    'ingestion' | 'quality_governance' | 'graph_research' | 'learning_coach' | 'assessment_analysis'
  stage: Stage
  objective: string
  authorizedScope: Scope
  dependencyIds: string[]
  inputArtifactIds: string[]
  status: TaskStatus
  artifactId: string | null
  errorCode: string | null
  usage: Usage
  startedAt: string | null
  finishedAt: string | null
}
export interface Run {
  runId: string
  hostInstanceId: string
  templateId: 'source_graph_citation_v1'
  query: string
  goal: string | null
  executionMode: 'deterministic_tools'
  status: RunStatus
  authorizedScope: Scope
  tasks: Task[]
  usage: Usage
  createdAt: string
  deadline: string
  expiresAt: string | null
  observedAt: string
  cancellationRequested: boolean
  recoverableAfterRestart: false
  recoverableAfterRefresh: false
  limitations: string[]
}
export interface SourcePointer {
  documentId: LongId
  chunkId: LongId
  contentHash: string
}
export interface Evidence {
  sourceId: string
  kind: 'source' | 'system_state' | 'graph_fact'
  object: { kind: string; id: string; version: string | null }
  scope: Scope
  locator: string | null
  excerpt: string | null
  originTaskId: string
  originArtifactId: string
  observedAt: string
  rereadPointer: SourcePointer | null
  sourceState: 'not_checked' | 'current' | 'truncated' | 'stale' | 'unavailable'
}
export interface Claim {
  claimId: string
  text: string
  kind: 'source_fact' | 'graph_fact' | 'model_explanation' | 'conflict' | 'gap'
  sourceIds: string[]
  originTaskId: string
  originArtifactId: string
  semanticSupport: 'not_evaluated'
}
export interface ChunkView extends SourcePointer {
  sourceId: string
  unitId: LongId
  unitIndex: number
  unitType: string | null
  sourceLocator: string | null
  content: string
  bodyTruncated: boolean
  matchScore: number
}
export interface GraphNode {
  nodeId: LongId
  name: string | null
  nameEn: string | null
  type: string | null
  definition: string | null
  definitionTruncated: boolean
  status: string
  updatedAt: string | null
  versionKind: 'updatedAt_not_strong_version'
  libraryIds: LongId[]
  aliases: string[]
  origin: string
  reviewStatus: string
  matchedBy: string | null
  sourceEvidenceAvailable: boolean
}
export interface GraphEdge {
  edgeId: LongId
  sourceNodeId: LongId
  targetNodeId: LongId
  relationType: string
  status: string
  updatedAt: string | null
  origin: string
  reviewStatus: string
  sourceEvidenceAvailable: boolean
}
export interface Graph {
  runId: string
  taskId: string
  artifactId: string
  authorizedScope: Scope
  observedScopes: Scope[]
  observedAt: string | null
  empty: boolean
  direction: 'stored_source_to_target'
  neighborhoodTraversal: 'incident_edges'
  pathTraversal: 'outgoing_edges'
  nodes: GraphNode[]
  edges: GraphEdge[]
  paths: LongId[][]
  evidence: Evidence[]
  coverage: Coverage
  noPathWithinDepth: boolean | null
  pathTruncated: boolean | null
}
export interface SourceCheck {
  status:
    | 'CURRENT'
    | 'TRUNCATED'
    | 'STALE'
    | 'MISSING'
    | 'WRONG_DOCUMENT'
    | 'OUT_OF_SCOPE_OR_MISSING'
    | 'IDENTITY_MISMATCH'
    | 'UNIT_UNAVAILABLE'
    | 'INVALID_POINTER'
  chunkId?: LongId
  expectedHash?: string
  actualHash?: string | null
  unitIndex?: number | null
  locator?: string | null
  content?: null
  truncated?: boolean
  originalSourceId?: string | null
}
export interface ClaimCheck {
  claimId: string
  referenceStatus: 'CURRENT' | 'UNVERIFIED' | 'UNKNOWN_CLAIM' | 'INVALID_SOURCE_COUNT'
  semanticSupport: 'not_evaluated'
  sources?: SourceCheck[]
}
export type Payload =
  | {
      kind: 'document_metadata'
      documentId: LongId
      visibleLibraryIds: LongId[]
      title: string | null
      processingStatus: string
      lifecycleStatus: string | null
      sourceFilePresent: boolean
    }
  | { kind: 'source_read'; documentId: LongId; items: ChunkView[]; coverage: Coverage }
  | {
      kind: 'document_research'
      documentId: LongId
      claims: Claim[]
      graph: Graph
      coverage: Coverage
    }
  | {
      kind: 'citation_check'
      checkedArtifactId: string
      producerTaskId: string
      checks: ClaimCheck[]
      uncheckedClaims: { claimId: string; reasonCode: string }[]
      currentClaims: number
      requestedClaims: number
      completeCoverage: boolean
      coverageBasis: 'requested_claims_only'
      semanticSupport: 'not_evaluated'
      observedAt: string | null
    }
export interface Observation {
  toolCallId: string
  operation:
    | 'get_document'
    | 'retrieve_chunks'
    | 'search_nodes'
    | 'get_node'
    | 'get_neighbors'
    | 'find_paths'
    | 'verify_artifact_claims'
  status: 'ok' | 'partial' | 'error'
  effectiveScope: Scope
  observedAt: string
  errorCode: string | null
  limitations: string[]
}
export interface Artifact {
  artifactId: string
  runId: string
  producerTaskId: string
  inputArtifactIds: string[]
  authorizedScope: Scope
  observedScopes: Scope[]
  outcome: 'completed' | 'partial' | 'needs_input' | 'failed'
  summary: string
  createdAt: string
  scopeMatched: boolean
  executionReceipt: false
  actionProposals: never[]
  evidence: Evidence[]
  observations: Observation[]
  coverage: Coverage
  payload: Payload
  limitations: string[]
}
export interface Reread {
  runId: string
  artifactId: string
  originalSourceId: string
  status: 'ok' | 'partial'
  effectiveScope: Scope
  observedAt: string
  documentId: LongId
  chunkId: LongId
  unitId: LongId
  unitIndex: number
  unitType: string | null
  sourceLocator: string | null
  chunkIndex: number
  startOffset: number
  endOffset: number
  contentHash: string
  content: string
  bodyTruncated: boolean
  formalAvailable: true
  semanticSupport: 'not_evaluated'
  evidence: Evidence
  limitations: string[]
}
