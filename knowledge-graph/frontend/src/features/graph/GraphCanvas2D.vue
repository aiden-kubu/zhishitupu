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
              :class="
                edge.active
                  ? 'stroke-brand-400 dark:stroke-brand-400'
                  : 'stroke-gray-300 dark:stroke-gray-600'
              "
              :stroke-opacity="edge.active ? 0.9 : 0.65"
              :stroke-width="edge.active ? 1.8 : 1.2"
              vector-effect="non-scaling-stroke"
              class="pointer-events-none"
            />
            <text
              v-if="edge.showLabel"
              :x="edge.labelX"
              :y="edge.labelY"
              text-anchor="middle"
              class="pointer-events-none fill-gray-500 stroke-gray-25 text-[11px] [paint-order:stroke] dark:fill-gray-300 dark:stroke-gray-950"
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
              class="stroke-brand-500 dark:stroke-brand-400"
              stroke-width="2"
            />
            <circle
              :r="node.radius"
              :fill="nodeColor(node.type)"
              :fill-opacity="node.dimmed ? 0.2 : 0.92"
              stroke-width="1.5"
              class="stroke-white dark:stroke-gray-900"
            />
            <text
              v-if="node.showLabel"
              :y="node.radius + 16"
              text-anchor="middle"
              class="fill-gray-700 stroke-gray-25 text-[12px] font-medium [paint-order:stroke] dark:fill-gray-300 dark:stroke-gray-950"
              stroke-width="3"
              :fill-opacity="node.dimmed ? 0.3 : 1"
            >
              {{ shortLabel(node.name) }}
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
        {{ NODE_TYPE_LABELS[hoveredNode.type] ?? hoveredNode.type }} ·
        {{ hoveredNode.degree }} 个直接关联
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
import { NODE_TYPE_LABELS, nodeColor } from './graphTheme'

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
  showLabel: boolean
  labelX: number
  labelY: number
}

interface LabelBox {
  x: number
  y: number
  width: number
  height: number
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
  const labelIds = new Set<number>()
  const boxes: LabelBox[] = []
  const priority = (node: SimNode) =>
    node.id === hoveredId.value
      ? 5
      : node.id === props.selectedId
        ? 4
        : node.id === props.centerId
          ? 3
          : neighborIds.value.has(node.id)
            ? 2
            : 1
  const candidates = [...positions.value]
    .sort((a, b) => priority(b) - priority(a) || b.degree - a.degree)
    .slice(0, 80)
  // 先保留当前焦点，再放入不会覆盖节点或已显示名称的标签。
  for (const node of candidates) {
    const essential = priority(node) >= 3
    if (!essential && (labelIds.size >= 40 || transform.value.k < 0.55)) continue
    const box = nodeLabelBox(node)
    if (
      !essential &&
      (boxes.some((other) => overlaps(box, other)) ||
        positions.value.some(
          (other) => other.id !== node.id && overlaps(box, nodeCircleBox(other)),
        ))
    )
      continue
    labelIds.add(node.id)
    boxes.push(box)
  }
  return positions.value.map((node) => {
    const dimmed = focusId !== null && node.id !== focusId && !neighborIds.value.has(node.id)
    return { ...node, dimmed, showLabel: labelIds.has(node.id) }
  })
})

const renderedEdges = computed<RenderedEdge[]>(() => {
  const focusId = hoveredId.value ?? props.selectedId ?? props.centerId
  const occupied = renderedNodes.value.filter((node) => node.showLabel).map(nodeLabelBox)
  let labelCount = 0
  return props.edges.flatMap((edge) => {
    const source = posById.value.get(edge.source)
    const target = posById.value.get(edge.target)
    if (!source || !target) return []
    const active = focusId !== null && (edge.source === focusId || edge.target === focusId)
    const labelX = (source.x + target.x) / 2
    const labelY = (source.y + target.y) / 2 - 7
    const labelWidth = estimateTextWidth(edge.relation, 11) + 10
    const box = { x: labelX - labelWidth / 2, y: labelY - 12, width: labelWidth, height: 17 }
    // 关系较多时留出阅读空间；悬停节点会优先展示它的关联。
    const showLabel =
      active &&
      transform.value.k >= 0.65 &&
      labelCount < (hoveredId.value === null ? 8 : 12) &&
      !occupied.some((other) => overlaps(box, other)) &&
      !positions.value.some((node) => overlaps(box, nodeCircleBox(node)))
    if (showLabel) {
      occupied.push(box)
      labelCount++
    }
    return [
      {
        id: edge.id,
        relation: edge.relation,
        x1: source.x,
        y1: source.y,
        x2: target.x,
        y2: target.y,
        active,
        showLabel,
        labelX,
        labelY,
      },
    ]
  })
})

function shortLabel(value: string) {
  const characters = Array.from(value)
  return characters.length > 16 ? `${characters.slice(0, 15).join('')}…` : value
}

function estimateTextWidth(value: string, size: number) {
  return Array.from(value).reduce(
    (width, character) => width + (character.charCodeAt(0) > 255 ? size : size * 0.6),
    0,
  )
}

function nodeLabelBox(node: SimNode): LabelBox {
  const width = estimateTextWidth(shortLabel(node.name), 12) + 12
  return { x: node.x - width / 2, y: node.y + node.radius + 5, width, height: 19 }
}

function nodeCircleBox(node: SimNode): LabelBox {
  const radius = node.radius + 5
  return { x: node.x - radius, y: node.y - radius, width: radius * 2, height: radius * 2 }
}

function overlaps(a: LabelBox, b: LabelBox) {
  return a.x < b.x + b.width && a.x + a.width > b.x && a.y < b.y + b.height && a.y + a.height > b.y
}

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
    .filter((link): link is { source: SimNode; target: SimNode } =>
      Boolean(link.source && link.target),
    )

  const repulsion = 4200
  const springLength = 165
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
        const distance = Math.sqrt(distanceSquared)
        const minimumGap = a.radius + b.radius + 38
        const force = repulsion / distanceSquared + Math.max(0, minimumGap - distance) * 0.12
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
      node.vx += (cx - node.x) * 0.005
      node.vy += (cy - node.y) * 0.005
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
  const minX = Math.min(...xs) - 80
  const maxX = Math.max(...xs) + 80
  const minY = Math.min(...ys) - 60
  const maxY = Math.max(...ys) + 60
  const contentWidth = Math.max(maxX - minX, 1)
  const contentHeight = Math.max(maxY - minY, 1)
  const k = Math.min(containerSize.width / contentWidth, containerSize.height / contentHeight, 1.15)
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
let panStart: {
  pointerX: number
  pointerY: number
  tx: number
  ty: number
  moved: boolean
} | null = null

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
  dragNode = posById.value.get(node.id) ?? null
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
