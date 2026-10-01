// 验证页面消费的数据结构；不在前端重新判定领域业务结果。
type Check = (value: unknown) => boolean
const str: Check = (v) => typeof v === 'string'
const bool: Check = (v) => typeof v === 'boolean'
const int: Check = (v) => typeof v === 'number' && Number.isInteger(v) && v >= 0
const one =
  (...values: unknown[]): Check =>
  (v) =>
    values.includes(v)
const nullable =
  (check: Check): Check =>
  (v) =>
    v === null || check(v)
const list =
  (check: Check, max = 1000): Check =>
  (v) =>
    Array.isArray(v) && v.length <= max && v.every(check)
const obj =
  (fields: Record<string, Check>, optional: Record<string, Check> = {}): Check =>
  (v) => {
    if (!v || typeof v !== 'object' || Array.isArray(v)) return false
    const record = v as Record<string, unknown>
    return (
      Object.entries(fields).every(([key, check]) => key in record && check(record[key])) &&
      Object.entries(optional).every(([key, check]) => !(key in record) || check(record[key]))
    )
  }
const id: Check = (v) => typeof v === 'string' && /^[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}$/.test(v)
const decimal =
  (zero: boolean): Check =>
  (v) =>
    typeof v === 'string' &&
    (zero ? /^(0|[1-9][0-9]{0,18})$/ : /^[1-9][0-9]{0,18}$/).test(v) &&
    (v.length < 19 || v <= '9223372036854775807')
const long = decimal(false)
const hash: Check = (v) => typeof v === 'string' && /^[a-fA-F0-9]{64}$/.test(v)
const strings = list(str)
const scope = obj({
  mode: one('project', 'selected'),
  libraryIds: list(long),
  documentIds: list(long),
  nodeIds: list(long),
})
const coverage = obj({
  completeCoverage: bool,
  truncated: bool,
  coverageKind: str,
  uncoveredDocumentIds: list(long),
  limitations: strings,
})
const usage = obj({
  decisionCalls: int,
  toolCalls: int,
  modelCalls: one(0),
  accountingMode: one('runtime_model_counter_as_decision_calls'),
})
const available = one('available', 'disabled', 'unavailable')
const capabilityIds = [
  'ingestion_read',
  'research_graph',
  'citation_check',
  'source_reread',
  'learning_preview',
  'assessment_preview',
  'formal_assessment',
  'real_model',
]
const capability = obj({
  id: one(...capabilityIds),
  status: available,
  reasonCodes: strings,
  readOnly: one(true),
  formalAvailable: bool,
})
const capabilitiesShape = obj({
  contractVersion: one('08-stage1-v1'),
  hostInstanceId: id,
  observedAt: str,
  executionMode: one('deterministic_tools'),
  accessMode: one('isolated_local_single_owner'),
  multiUserIsolation: one(false),
  databaseIdentityVerified: bool,
  capabilities: list(capability, 8),
  persistence: obj({
    mode: one('memory'),
    recoverableAfterRestart: one(false),
    recoverableAfterRefresh: one(false),
    terminalRetentionSeconds: one(1800),
    maxRetainedRuns: one(32),
    maxActiveRuns: one(8),
  }),
})
const capabilities: Check = (v) =>
  capabilitiesShape(v) &&
  capabilityIds.every(
    (name) =>
      (v as { capabilities: { id: string }[] }).capabilities.filter((c) => c.id === name).length ===
      1,
  )
const stages = ['document_metadata', 'source_read', 'directed_graph', 'citation_check']
const template = obj({
  templateId: one('source_graph_citation_v1'),
  label: str,
  status: available,
  reasonCodes: strings,
  minDocuments: one(1),
  maxDocuments: one(2),
  queryMinLength: one(2),
  queryMaxLength: one(200),
  goalPolicy: one('display_only_no_plan_change'),
  executionMode: one('deterministic_tools'),
  stages: (v) => Array.isArray(v) && v.length === 4 && v.every((s, i) => s === stages[i]),
})
const templates = obj({
  items: (v) => Array.isArray(v) && v.length === 1 && v.every(template),
  observedAt: str,
})
const library = obj({
  libraryId: long,
  name: str,
  status: str,
  selectable: bool,
  reasonCodes: strings,
})
const document = obj({
  documentId: long,
  title: nullable(str),
  originalName: str,
  visibleLibraryIds: list(long),
  processingStatus: str,
  lifecycleStatus: nullable(str),
  selectable: bool,
  reasonCodes: strings,
})
const page = (item: Check) =>
  obj({
    items: list(item, 20),
    total: decimal(true),
    page: (v) => int(v) && (v as number) >= 1 && (v as number) <= 1000,
    pageSize: (v) => int(v) && (v as number) >= 1 && (v as number) <= 20,
    hasMore: bool,
    authorizedScope: scope,
    observedAt: str,
    coverage,
  })
const statuses = ['running', 'completed', 'partial', 'needs_input', 'failed', 'cancelled']
const task = obj({
  taskId: id,
  role: one(
    'ingestion',
    'quality_governance',
    'graph_research',
    'learning_coach',
    'assessment_analysis',
  ),
  stage: one(...stages),
  objective: str,
  authorizedScope: scope,
  dependencyIds: list(id, 32),
  inputArtifactIds: list(id, 32),
  status: one(...statuses, 'pending', 'blocked'),
  artifactId: nullable(id),
  errorCode: nullable(str),
  usage,
  startedAt: nullable(str),
  finishedAt: nullable(str),
})
const run = obj({
  runId: id,
  hostInstanceId: id,
  templateId: one('source_graph_citation_v1'),
  query: str,
  goal: nullable(str),
  executionMode: one('deterministic_tools'),
  status: one(...statuses, 'cancelling'),
  authorizedScope: scope,
  tasks: (v) => list(task, 8)(v) && (v as unknown[]).length >= 4,
  usage,
  createdAt: str,
  deadline: str,
  expiresAt: nullable(str),
  observedAt: str,
  cancellationRequested: bool,
  recoverableAfterRestart: one(false),
  recoverableAfterRefresh: one(false),
  limitations: strings,
})
const evidence = obj({
  sourceId: str,
  kind: one('source', 'system_state', 'graph_fact'),
  object: obj({ kind: str, id: str, version: nullable(str) }),
  scope,
  locator: nullable(str),
  excerpt: nullable(str),
  originTaskId: id,
  originArtifactId: id,
  observedAt: str,
  rereadPointer: nullable(obj({ documentId: long, chunkId: long, contentHash: hash })),
  sourceState: one('not_checked', 'current', 'truncated', 'stale', 'unavailable'),
})
const node = obj({
  nodeId: long,
  name: nullable(str),
  nameEn: nullable(str),
  type: nullable(str),
  definition: nullable(str),
  definitionTruncated: bool,
  status: str,
  updatedAt: nullable(str),
  versionKind: one('updatedAt_not_strong_version'),
  libraryIds: list(long),
  aliases: strings,
  origin: str,
  reviewStatus: str,
  matchedBy: nullable(str),
  sourceEvidenceAvailable: bool,
})
const edge = obj({
  edgeId: long,
  sourceNodeId: long,
  targetNodeId: long,
  relationType: str,
  status: str,
  updatedAt: nullable(str),
  origin: str,
  reviewStatus: str,
  sourceEvidenceAvailable: bool,
})
const graph = obj({
  runId: id,
  taskId: id,
  artifactId: id,
  authorizedScope: scope,
  observedScopes: list(scope, 256),
  observedAt: nullable(str),
  empty: bool,
  direction: one('stored_source_to_target'),
  neighborhoodTraversal: one('incident_edges'),
  pathTraversal: one('outgoing_edges'),
  nodes: list(node, 10),
  edges: list(edge, 10),
  paths: list((v) => list(long, 2)(v) && (v as unknown[]).length >= 1, 3),
  evidence: list(evidence, 256),
  coverage,
  noPathWithinDepth: nullable(bool),
  pathTruncated: nullable(bool),
})
const claim = obj({
  claimId: id,
  text: str,
  kind: one('source_fact', 'graph_fact', 'model_explanation', 'conflict', 'gap'),
  sourceIds: list(str, 20),
  originTaskId: id,
  originArtifactId: id,
  semanticSupport: one('not_evaluated'),
})
const chunk = obj({
  documentId: long,
  chunkId: long,
  contentHash: hash,
  sourceId: str,
  unitId: long,
  unitIndex: int,
  unitType: nullable(str),
  sourceLocator: nullable(str),
  content: str,
  bodyTruncated: bool,
  matchScore: int,
})
const sourceCheck = obj(
  {
    status: one(
      'CURRENT',
      'TRUNCATED',
      'STALE',
      'MISSING',
      'WRONG_DOCUMENT',
      'OUT_OF_SCOPE_OR_MISSING',
      'IDENTITY_MISMATCH',
      'UNIT_UNAVAILABLE',
      'INVALID_POINTER',
    ),
  },
  {
    chunkId: long,
    expectedHash: hash,
    actualHash: nullable(hash),
    unitIndex: nullable(int),
    locator: nullable(str),
    content: one(null),
    truncated: bool,
    originalSourceId: nullable(str),
  },
)
const claimCheck = obj(
  {
    claimId: id,
    referenceStatus: one('CURRENT', 'UNVERIFIED', 'UNKNOWN_CLAIM', 'INVALID_SOURCE_COUNT'),
    semanticSupport: one('not_evaluated'),
  },
  { sources: list(sourceCheck, 2) },
)
const payloads = [
  obj({
    kind: one('document_metadata'),
    documentId: long,
    visibleLibraryIds: list(long),
    title: nullable(str),
    processingStatus: str,
    lifecycleStatus: nullable(str),
    sourceFilePresent: bool,
  }),
  obj({ kind: one('source_read'), documentId: long, items: list(chunk, 2), coverage }),
  obj({
    kind: one('document_research'),
    documentId: long,
    claims: list(claim, 10),
    graph,
    coverage,
  }),
  obj({
    kind: one('citation_check'),
    checkedArtifactId: id,
    producerTaskId: id,
    checks: list(claimCheck, 10),
    uncheckedClaims: list(obj({ claimId: id, reasonCode: str }), 10),
    currentClaims: int,
    requestedClaims: int,
    completeCoverage: bool,
    coverageBasis: one('requested_claims_only'),
    semanticSupport: one('not_evaluated'),
    observedAt: nullable(str),
  }),
]
const observation = obj({
  toolCallId: id,
  operation: one(
    'get_document',
    'retrieve_chunks',
    'search_nodes',
    'get_node',
    'get_neighbors',
    'find_paths',
    'verify_artifact_claims',
  ),
  status: one('ok', 'partial', 'error'),
  effectiveScope: scope,
  observedAt: str,
  errorCode: nullable(str),
  limitations: strings,
})
const artifact = obj({
  artifactId: id,
  runId: id,
  producerTaskId: id,
  inputArtifactIds: list(id, 32),
  authorizedScope: scope,
  observedScopes: list(scope, 256),
  outcome: one('completed', 'partial', 'needs_input', 'failed'),
  summary: str,
  createdAt: str,
  scopeMatched: bool,
  executionReceipt: one(false),
  actionProposals: list(() => false, 0),
  evidence: list(evidence, 256),
  observations: list(observation, 32),
  coverage,
  payload: (v) => payloads.some((check) => check(v)),
  limitations: strings,
})
const reread = obj({
  runId: id,
  artifactId: id,
  originalSourceId: str,
  status: one('ok', 'partial'),
  effectiveScope: scope,
  observedAt: str,
  documentId: long,
  chunkId: long,
  unitId: long,
  unitIndex: int,
  unitType: nullable(str),
  sourceLocator: nullable(str),
  chunkIndex: int,
  startOffset: int,
  endOffset: int,
  contentHash: hash,
  content: str,
  bodyTruncated: bool,
  formalAvailable: one(true),
  semanticSupport: one('not_evaluated'),
  evidence,
  limitations: strings,
})
const checks = {
  capabilities,
  templates,
  libraries: page(library),
  documents: page(document),
  run,
  cancel: obj({ accepted: bool, run }),
  artifact,
  graph,
  reread,
}
export function validateResponse(kind: keyof typeof checks, data: unknown): boolean {
  return checks[kind](data)
}
