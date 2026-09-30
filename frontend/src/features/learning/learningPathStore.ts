import type { GraphEdgeDto, GraphNodeDto } from '@/services/types'

export type LearningDifficulty = 'beginner' | 'intermediate' | 'advanced'
export type LearningStepStatus = 'completed' | 'current' | 'locked'

export interface LearningStep {
  id: string
  nodeId: number
  title: string
  description: string
  detail: string
  minutes: number
  status: LearningStepStatus
  visitedAt?: string | null
  assessmentScore?: number | null
}

export interface LearningPathState {
  version: 2
  libraryId: number
  libraryName: string
  goal: string
  difficulty: LearningDifficulty
  updatedAt: string
  selectedStepId: string
  steps: LearningStep[]
}

export const learningPathStorageKey = 'knowledge-graph-learning-path-v2'

export function loadLearningPath(): LearningPathState | null {
  const raw = localStorage.getItem(learningPathStorageKey)
  if (!raw) return null
  try {
    const value = JSON.parse(raw) as unknown
    if (!isLearningPathState(value)) {
      localStorage.removeItem(learningPathStorageKey)
      return null
    }
    return value
  } catch {
    localStorage.removeItem(learningPathStorageKey)
    return null
  }
}

export function saveLearningPath(path: LearningPathState): void {
  localStorage.setItem(learningPathStorageKey, JSON.stringify(path))
}

function isLearningPathState(value: unknown): value is LearningPathState {
  if (!value || typeof value !== 'object') return false
  const path = value as Partial<LearningPathState>
  if (
    path.version !== 2 ||
    !Number.isInteger(path.libraryId) ||
    typeof path.libraryName !== 'string' ||
    typeof path.goal !== 'string' ||
    !['beginner', 'intermediate', 'advanced'].includes(String(path.difficulty)) ||
    typeof path.updatedAt !== 'string' ||
    typeof path.selectedStepId !== 'string' ||
    !Array.isArray(path.steps) ||
    path.steps.length < 2
  )
    return false

  const nodeIds = new Set<number>()
  let currentCount = 0
  for (const step of path.steps) {
    if (
      !step ||
      typeof step.id !== 'string' ||
      !Number.isInteger(step.nodeId) ||
      step.nodeId <= 0 ||
      typeof step.title !== 'string' ||
      typeof step.description !== 'string' ||
      typeof step.detail !== 'string' ||
      !Number.isFinite(step.minutes) ||
      !['completed', 'current', 'locked'].includes(step.status) ||
      (step.visitedAt != null && typeof step.visitedAt !== 'string') ||
      (step.assessmentScore != null && !Number.isFinite(step.assessmentScore))
    )
      return false
    if (nodeIds.has(step.nodeId)) return false
    nodeIds.add(step.nodeId)
    if (step.status === 'current') currentCount += 1
  }
  const allCompleted = path.steps.every((step) => step.status === 'completed')
  return (
    path.steps.some((step) => step.id === path.selectedStepId) &&
    (currentCount === 1 || allCompleted)
  )
}

interface GenerateOptions {
  goal: string
  difficulty: LearningDifficulty
  count: number
}

export function generateLearningSteps(
  nodes: GraphNodeDto[],
  edges: GraphEdgeDto[],
  options: GenerateOptions,
): LearningStep[] {
  const count = Math.max(2, Math.min(options.count, nodes.length))
  const terms = goalTerms(options.goal)
  const nodeById = new Map(nodes.map((node) => [node.id, node]))
  const neighbors = new Map<number, Set<number>>()
  for (const edge of edges) {
    if (!nodeById.has(edge.source) || !nodeById.has(edge.target)) continue
    addNeighbor(neighbors, edge.source, edge.target)
    addNeighbor(neighbors, edge.target, edge.source)
  }

  const score = (node: GraphNodeDto) =>
    relevanceScore(node, terms) + evidenceScore(node) + difficultyScore(node, options.difficulty)

  const ranked = [...nodes].sort((a, b) => score(b) - score(a) || a.id - b.id)
  const chosen: GraphNodeDto[] = []
  const chosenIds = new Set<number>()
  if (ranked[0]) {
    chosen.push(ranked[0])
    chosenIds.add(ranked[0].id)
  }

  while (chosen.length < count) {
    const connected = ranked
      .filter((node) => !chosenIds.has(node.id))
      .map((node) => ({
        node,
        connections: [...(neighbors.get(node.id) ?? [])].filter((id) => chosenIds.has(id)).length,
      }))
      .sort(
        (a, b) =>
          b.connections - a.connections || score(b.node) - score(a.node) || a.node.id - b.node.id,
      )
    const next = connected[0]?.node
    if (!next) break
    chosen.push(next)
    chosenIds.add(next.id)
  }

  const ordered = orderForLearning(chosen, edges, score)
  return ordered.map((node, index) => ({
    id: `node-${node.id}`,
    nodeId: node.id,
    title: node.name,
    description: summarize(node.definition) || `学习并理解“${node.name}”及其在知识图谱中的关联。`,
    detail: node.definition?.trim() || `进入图谱查看“${node.name}”的定义、来源以及相邻知识点。`,
    minutes: minutesFor(options.difficulty, node),
    status: index === 0 ? 'current' : 'locked',
  }))
}

function goalTerms(goal: string): string[] {
  const normalized = goal.toLowerCase().replace(/[，。；、,.!?！？：:（）()\-_/]/g, ' ')
  const parts = normalized.split(/\s+/).filter((term) => term.length >= 2)
  const compact = normalized.replace(/\s+/g, '')
  if (compact.length >= 2 && compact.length <= 20) parts.push(compact)
  for (let index = 0; index < compact.length - 1 && index < 24; index += 1) {
    parts.push(compact.slice(index, index + 2))
  }
  return [...new Set(parts)]
}

function relevanceScore(node: GraphNodeDto, terms: string[]): number {
  if (!terms.length) return 0
  const name = node.name.toLowerCase()
  const definition = (node.definition ?? '').toLowerCase()
  return terms.reduce((total, term) => {
    if (name === term) return total + 120
    if (name.includes(term) || term.includes(name)) return total + 45
    if (definition.includes(term)) return total + 12
    return total
  }, 0)
}

function evidenceScore(node: GraphNodeDto): number {
  return Math.min(node.sourceCount, 10) * 3 + Math.min(node.degree, 20)
}

function difficultyScore(node: GraphNodeDto, difficulty: LearningDifficulty): number {
  const type = node.type.toLowerCase()
  if (difficulty === 'beginner') {
    if (['course', 'chapter', 'concept', 'knowledge'].includes(type)) return 30
    if (['method', 'application'].includes(type)) return 5
  }
  if (difficulty === 'advanced') {
    if (['method', 'application'].includes(type)) return 30
    if (['concept', 'knowledge'].includes(type)) return 12
  }
  return ['concept', 'knowledge', 'method'].includes(type) ? 22 : 10
}

function orderForLearning(
  nodes: GraphNodeDto[],
  edges: GraphEdgeDto[],
  score: (node: GraphNodeDto) => number,
): GraphNodeDto[] {
  const nodeById = new Map(nodes.map((node) => [node.id, node]))
  const outgoing = new Map<number, Set<number>>()
  const indegree = new Map(nodes.map((node) => [node.id, 0]))
  for (const edge of edges) {
    if (!nodeById.has(edge.source) || !nodeById.has(edge.target)) continue
    const orderedPair = learningOrder(edge)
    if (!orderedPair || orderedPair[0] === orderedPair[1]) continue
    const targets = outgoing.get(orderedPair[0]) ?? new Set<number>()
    if (!targets.has(orderedPair[1])) {
      targets.add(orderedPair[1])
      outgoing.set(orderedPair[0], targets)
      indegree.set(orderedPair[1], (indegree.get(orderedPair[1]) ?? 0) + 1)
    }
  }

  const result: GraphNodeDto[] = []
  const available = nodes.filter((node) => indegree.get(node.id) === 0)
  while (available.length) {
    available.sort(
      (a, b) => basicTypeRank(a) - basicTypeRank(b) || score(b) - score(a) || a.id - b.id,
    )
    const current = available.shift()!
    result.push(current)
    for (const target of outgoing.get(current.id) ?? []) {
      indegree.set(target, (indegree.get(target) ?? 1) - 1)
      if (indegree.get(target) === 0) available.push(nodeById.get(target)!)
    }
  }
  if (result.length < nodes.length) {
    result.push(
      ...nodes
        .filter((node) => !result.some((item) => item.id === node.id))
        .sort((a, b) => basicTypeRank(a) - basicTypeRank(b) || score(b) - score(a)),
    )
  }
  return result
}

function learningOrder(edge: GraphEdgeDto): [number, number] | null {
  const relation = edge.relation.toLowerCase()
  if (/先修于|前置于|基础于|引出|包含/.test(relation)) return [edge.source, edge.target]
  if (/依赖|基于|属于|先修|前置|基础/.test(relation)) return [edge.target, edge.source]
  return null
}

function basicTypeRank(node: GraphNodeDto): number {
  const ranks: Record<string, number> = {
    course: 0,
    chapter: 1,
    concept: 2,
    knowledge: 2,
    method: 3,
    application: 4,
    other: 5,
  }
  return ranks[node.type.toLowerCase()] ?? 5
}

function addNeighbor(map: Map<number, Set<number>>, source: number, target: number): void {
  const values = map.get(source) ?? new Set<number>()
  values.add(target)
  map.set(source, values)
}

function summarize(value?: string | null): string {
  const text = value?.replace(/\s+/g, ' ').trim() ?? ''
  return text.length > 54 ? `${text.slice(0, 54)}…` : text
}

function minutesFor(difficulty: LearningDifficulty, node: GraphNodeDto): number {
  const base = difficulty === 'beginner' ? 15 : difficulty === 'advanced' ? 30 : 20
  return base + Math.min(Math.floor(node.degree / 5) * 5, 15)
}
