<template>
  <div ref="containerRef" class="relative h-full w-full overflow-hidden">
    <svg
      ref="svgRef"
      class="h-full w-full touch-none select-none"
      :class="panning ? 'cursor-grabbing' : 'cursor-grab'"
      @wheel.prevent="onWheel"
      @pointerdown="onBackgroundPointerDown"
      @pointermove="onPointerMove"
      @pointerup="onPointerUp"
      @pointerleave="onPointerUp"
    >
      <g :transform="`translate(${transform.x},${transform.y}) scale(${transform.k})`">
        <!-- 边：标签只在选中/悬停相关边上出现（§6.3） -->
        <g>
          <template v-for="edge in renderedEdges" :key="edge.id">
            <line
              :x1="edge.x1"
              :y1="edge.y1"
              :x2="edge.x2"
              :y2="edge.y2"
              :stroke="edge.active ? edgeActiveColor : edgeColor"
              :stroke-opacity="edge.active ? 0.9 : 0.5"
              :stroke-width="edge.active ? 2 : 1.2"
            />
            <text
              v-if="edge.active"
              :x="(edge.x1 + edge.x2) / 2"
              :y="(edge.y1 + edge.y2) / 2 - 6"
              text-anchor="middle"
              class="fill-gray-500 text-[11px] dark:fill-gray-400"
              style="paint-order: stroke"
              :stroke="canvasBackground"
              stroke-width="4"
            >
              {{ edge.relation }}
            </text>
          </template>
        </g>

        <!-- 节点 -->
        <g>
          <g
            v-for="node in renderedNodes"
            :key="node.id"
            :transform="`translate(${node.x},${node.y})`"
            class="cursor-pointer"
            @pointerdown.stop="onNodePointerDown(node, $event)"
            @dblclick.stop.prevent="emit('expand', node.id)"
            @pointerenter="hoveredId = node.id"
            @pointerleave="hoveredId = null"
          >
            <circle
              v-if="node.id === centerId"
              :r="node.radius + 6"
              :fill="nodeColor(node.type)"
              fill-opacity="0.15"
            />
            <circle
              v-if="node.id === selectedId"
              :r="node.radius + 4"
              fill="none"
              :stroke="edgeActiveColor"
              stroke-width="2"
            />
            <circle
              :r="node.radius"
              :fill="nodeColor(node.type)"
              :fill-opacity="node.dimmed ? 0.2 : 0.92"
              stroke="#ffffff"
              stroke-width="1.5"
              class="dark:stroke-gray-900"
            />
            <text
              v-if="node.showLabel"
              :y="node.radius + 16"
              text-anchor="middle"
              class="fill-gray-700 text-[12px] font-medium dark:fill-gray-300"
              style="paint-order: stroke"
              :stroke="canvasBackground"
              stroke-width="3"
              :fill-opacity="node.dimmed ? 0.3 : 1"
            >
              {{ node.name }}
            </text>
          </g>
        </g>
      </g>
    </svg>

    <!-- 悬停提示（§6.3：名称、类型、定义摘要、直接关联数） -->
    <div
      v-if="hoveredNode"
      class="pointer-events-none absolute z-10 max-w-[280px] rounded-xl border border-gray-200 bg-white px-3.5 py-2.5 shadow-theme-md dark:border-gray-800 dark:bg-gray-900"
      :style="{ left: `${tooltip.x}px`, top: `${tooltip.y}px` }"
    >
      <p class="text-sm font-medium text-gray-800 dark:text-white/90">
        {{ hoveredNode.name }}
      </p>
      <p class="mt-0.5 text-xs text-gray-500 dark:text-gray-400">
        {{ NODE_TYPE_LABELS[hoveredNode.type] ?? hoveredNode.type }} · {{ hoveredNode.degree }} 个直接关联
      </p>
      <p
        v-if="hoveredNode.definition"
        class="mt-1 line-clamp-2 text-xs text-gray-500 dark:text-gray-400"
      >
        {{ hoveredNode.definition }}
      </p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import type { GraphEdgeDto, GraphNodeDto } from '@/services/types'
import {
  NODE_TYPE_LABELS,
  canvasBackground,
  edgeActiveColor,
  edgeColor,
  nodeColor,
} from './graphTheme'

interface SimNode extends GraphNodeDto {
  x: number
  y: number
  vx: number
  vy: number
  radius: number
  dimmed: boolean
  showLabel: boolean
}

interface RenderedEdge {
  id: number
  relation: string
  x1: number
  y1: number
  x2: number
  y2: number
  active: boolean
}

const props = defineProps<{
  nodes: GraphNodeDto[]
  edges: GraphEdgeDto[]
  selectedId: number | null
  centerId: number | null
}>()

const emit = defineEmits<{
  (e: 'select', nodeId: number | null): void
  (e: 'expand', nodeId: number): void
}>()

const containerRef = ref<HTMLElement | null>(null)
const svgRef = ref<SVGSVGElement | null>(null)

const positions = ref<SimNode[]>([])
const transform = ref({ x: 0, y: 0, k: 1 })
const hoveredId = ref<number | null>(null)
const panning = ref(false)

let containerSize = { width: 800, height: 600 }
let resizeObserver: ResizeObserver | null = null

const posById = computed(() => {
  const map = new Map<number, SimNode>()
  for (const node of positions.value) map.set(node.id, node)
  return map
})

const hoveredNode = computed(() =>
  hoveredId.value === null ? null : (posById.value.get(hoveredId.value) ?? null),
)

const neighborIds = computed(() => {
  const ids = new Set<number>()
  const focusId = props.selectedId ?? props.centerId
  if (focusId === null) return ids
  for (const edge of props.edges) {
    if (edge.source === focusId) ids.add(edge.target)
    if (edge.target === focusId) ids.add(edge.source)
  }
  return ids
})

const renderedNodes = computed(() => {
  const focusId = props.selectedId ?? props.centerId
  return positions.value.map((node) => {
    const dimmed = focusId !== null && node.id !== focusId && !neighborIds.value.has(node.id)
    return { ...node, dimmed }
  })
})

const renderedEdges = computed<RenderedEdge[]>(() => {
  const focusId = props.selectedId ?? hoveredId.value ?? props.centerId
  return props.edges.flatMap((edge) => {
    const source = posById.value.get(edge.source)
    const target = posById.value.get(edge.target)
    if (!source || !target) return []
    const active = focusId !== null && (edge.source === focusId || edge.target === focusId)
    return [
      {
        id: edge.id,
        relation: edge.relation,
        x1: source.x,
        y1: source.y,
        x2: target.x,
        y2: target.y,
        active,
      },
    ]
  })
})

/** 标签策略：中心/选中/悬停/邻居 + 关联度前 30（§6.4 避免全局文字重叠） */
watch(
  [positions, () => props.selectedId, hoveredId, () => props.centerId],
  () => {
    const focusId = props.selectedId ?? props.centerId
    const labelIds = new Set<number>()
    const topByDegree = [...positions.value].sort((a, b) => b.degree - a.degree).slice(0, 30)
    for (const node of topByDegree) labelIds.add(node.id)
    if (props.centerId !== null) labelIds.add(props.centerId)
    if (props.selectedId !== null) {
      labelIds.add(props.selectedId)
      for (const id of neighborIds.value) labelIds.add(id)
    }
    if (hoveredId.value !== null) labelIds.add(hoveredId.value)
    for (const node of positions.value) node.showLabel = labelIds.has(node.id)
    void focusId
  },
  { deep: false },
)

// ---------- 布局 ----------

function runLayout() {
  const nodes = props.nodes
  const edges = props.edges
  const { width, height } = containerSize
  const cx = width / 2
  const cy = height / 2
  const count = nodes.length

  const sims: SimNode[] = nodes.map((node, index) => {
    const angle = (2 * Math.PI * index) / Math.max(count, 1)
    const radius = Math.min(width, height) * 0.32
    return {
      ...node,
      x: cx + radius * Math.cos(angle),
      y: cy + radius * Math.sin(angle),
      vx: 0,
      vy: 0,
      radius: 7 + Math.min(node.degree, 40) * 0.3 + (node.id === props.centerId ? 3 : 0),
      dimmed: false,
      showLabel: false,
    }
  })

  const byId = new Map(sims.map((node) => [node.id, node]))
  const links = edges
    .map((edge) => ({ source: byId.get(edge.source), target: byId.get(edge.target) }))
    .filter((link): link is { source: SimNode; target: SimNode } => Boolean(link.source && link.target))

  const repulsion = 2600
  const springLength = 95
  const iterations = count > 250 ? 90 : 180

  for (let step = 0; step < iterations; step++) {
    for (let i = 0; i < sims.length; i++) {
      for (let j = i + 1; j < sims.length; j++) {
        const a = sims[i]
        const b = sims[j]
        let dx = a.x - b.x
        let dy = a.y - b.y
        let distanceSquared = dx * dx + dy * dy
        if (distanceSquared < 1) {
          dx = (Math.random() - 0.5) * 2
          dy = (Math.random() - 0.5) * 2
          distanceSquared = dx * dx + dy * dy
        }
        const force = repulsion / distanceSquared
        const distance = Math.sqrt(distanceSquared)
        const fx = (dx / distance) * force
        const fy = (dy / distance) * force
        a.vx += fx
        a.vy += fy
        b.vx -= fx
        b.vy -= fy
      }
    }

    for (const link of links) {
      const dx = link.target.x - link.source.x
      const dy = link.target.y - link.source.y
      const distance = Math.sqrt(dx * dx + dy * dy) || 1
      const force = (distance - springLength) * 0.015
      const fx = (dx / distance) * force
      const fy = (dy / distance) * force
      link.source.vx += fx
      link.source.vy += fy
      link.target.vx -= fx
      link.target.vy -= fy
    }

    for (const node of sims) {
      node.vx += (cx - node.x) * 0.012
      node.vy += (cy - node.y) * 0.012
      node.vx *= 0.85
      node.vy *= 0.85
      node.x += Math.max(-12, Math.min(12, node.vx))
      node.y += Math.max(-12, Math.min(12, node.vy))
    }
  }

  positions.value = sims
  fitToContent()
}

function fitToContent() {
  if (positions.value.length === 0) return
  const xs = positions.value.map((node) => node.x)
  const ys = positions.value.map((node) => node.y)
  const minX = Math.min(...xs) - 40
  const maxX = Math.max(...xs) + 40
  const minY = Math.min(...ys) - 40
  const maxY = Math.max(...ys) + 40
  const contentWidth = Math.max(maxX - minX, 1)
  const contentHeight = Math.max(maxY - minY, 1)
  const k = Math.min(
    containerSize.width / contentWidth,
    containerSize.height / contentHeight,
    1.4,
  )
  const scale = Math.max(k, 0.15)
  transform.value = {
    k: scale,
    x: (containerSize.width - contentWidth * scale) / 2 - minX * scale,
    y: (containerSize.height - contentHeight * scale) / 2 - minY * scale,
  }
}

watch(
  () => [props.nodes, props.edges, props.centerId],
  () => {
    runLayout()
  },
  { deep: false },
)

// ---------- 交互 ----------

let dragNode: SimNode | null = null
let dragMoved = false
let panStart: { pointerX: number; pointerY: number; tx: number; ty: number; moved: boolean } | null =
  null

function toWorld(clientX: number, clientY: number): { x: number; y: number } {
  const rect = svgRef.value?.getBoundingClientRect()
  if (!rect) return { x: 0, y: 0 }
  const localX = clientX - rect.left
  const localY = clientY - rect.top
  return {
    x: (localX - transform.value.x) / transform.value.k,
    y: (localY - transform.value.y) / transform.value.k,
  }
}

function toScreen(clientX: number, clientY: number): { x: number; y: number } {
  const rect = svgRef.value?.getBoundingClientRect()
  if (!rect) return { x: 0, y: 0 }
  return { x: clientX - rect.left, y: clientY - rect.top }
}

function onWheel(event: WheelEvent) {
  const rect = svgRef.value?.getBoundingClientRect()
  if (!rect) return
  const localX = event.clientX - rect.left
  const localY = event.clientY - rect.top
  const factor = Math.exp(-event.deltaY * 0.0012)
  const nextK = Math.min(3, Math.max(0.15, transform.value.k * factor))
  const worldX = (localX - transform.value.x) / transform.value.k
  const worldY = (localY - transform.value.y) / transform.value.k
  transform.value = {
    k: nextK,
    x: localX - worldX * nextK,
    y: localY - worldY * nextK,
  }
}

function onNodePointerDown(node: SimNode, event: PointerEvent) {
  event.preventDefault()
  dragNode = node
  dragMoved = false
  svgRef.value?.setPointerCapture?.(event.pointerId)
}

function onBackgroundPointerDown(event: PointerEvent) {
  panStart = {
    pointerX: event.clientX,
    pointerY: event.clientY,
    tx: transform.value.x,
    ty: transform.value.y,
    moved: false,
  }
  panning.value = true
  svgRef.value?.setPointerCapture?.(event.pointerId)
}

function onPointerMove(event: PointerEvent) {
  if (dragNode) {
    const world = toWorld(event.clientX, event.clientY)
    if (!dragMoved && Math.abs(world.x - dragNode.x) + Math.abs(world.y - dragNode.y) > 4) {
      dragMoved = true
    }
    dragNode.x = world.x
    dragNode.y = world.y
    return
  }
  if (panStart) {
    const dx = event.clientX - panStart.pointerX
    const dy = event.clientY - panStart.pointerY
    if (Math.abs(dx) + Math.abs(dy) > 3) panStart.moved = true
    transform.value = { ...transform.value, x: panStart.tx + dx, y: panStart.ty + dy }
  } else if (hoveredNode.value) {
    const screen = toScreen(event.clientX, event.clientY)
    tooltip.value = { x: screen.x + 14, y: screen.y + 14 }
  }
}

function onPointerUp(event: PointerEvent) {
  if (dragNode) {
    if (!dragMoved) emit('select', dragNode.id)
    dragNode = null
    dragMoved = false
    return
  }
  if (panStart) {
    // 点击空白：取消强调（AI 会话按节点保留，§6.3）
    if (!panStart.moved) emit('select', null)
    panStart = null
  }
  panning.value = false
  svgRef.value?.releasePointerCapture?.(event.pointerId)
}

const tooltip = ref({ x: 0, y: 0 })

onMounted(() => {
  const element = containerRef.value
  if (!element) return
  const updateSize = () => {
    containerSize = { width: element.clientWidth, height: element.clientHeight }
    if (positions.value.length > 0) fitToContent()
  }
  updateSize()
  resizeObserver = new ResizeObserver(updateSize)
  resizeObserver.observe(element)
  runLayout()
})

onBeforeUnmount(() => {
  resizeObserver?.disconnect()
})
</script>
