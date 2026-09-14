<template>
  <!-- 绝对定位：three.js 画布为固定像素尺寸，脱离布局流避免撑大 flex 容器（§5.3 无横向滚动） -->
  <div ref="containerRef" class="absolute inset-0 overflow-hidden"></div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import * as THREE from 'three'
import ForceGraph3D from '3d-force-graph'
import type { ForceGraph3DInstance, Graph3DLinkObject, Graph3DNodeObject } from '3d-force-graph'
import type { GraphEdgeDto, GraphNodeDto } from '@/services/types'
import { NODE_TYPE_LABELS, canvasBackground, edgeActiveColor, edgeColor, nodeColor } from './graphTheme'

/**
 * 3D 星链图谱（§6.3）：左键拖动旋转、滚轮缩放、节点拖动、
 * 单击选中聚焦、双击扩展邻域、悬停提示、方向粒子、WebGL 失败回退 2D。
 */
const props = defineProps<{
  nodes: GraphNodeDto[]
  edges: GraphEdgeDto[]
  selectedId: number | null
  centerId: number | null
}>()

const emit = defineEmits<{
  (e: 'select', nodeId: number | null): void
  (e: 'expand', nodeId: number): void
  (e: 'webgl-failed'): void
}>()

const containerRef = ref<HTMLElement | null>(null)

let graph: ForceGraph3DInstance | null = null
let resizeObserver: ResizeObserver | null = null
let lastClick: { id: number; time: number } | null = null
let pendingFocusId: number | null = null

// 维护稳定对象引用：图谱数据更新时保留力导布局坐标（§6.4 增量更新）
const nodeObjects = new Map<number, Graph3DNodeObject & { meta: GraphNodeDto }>()
const linkObjects = new Map<number, Graph3DLinkObject>()
type NodeObject = Graph3DNodeObject & { meta: GraphNodeDto }

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

function buildNodeObject(node: GraphNodeDto) {
  let object = nodeObjects.get(node.id)
  if (!object) {
    object = { id: node.id, meta: node }
    nodeObjects.set(node.id, object)
  } else {
    object.meta = node
  }
  return object
}

function refreshHighlight() {
  if (!graph) return
  // 重新赋值访问器触发重绘，实现选中高亮/降透明度（§6.3）
  graph.nodeThreeObject(nodeObject3d)
  graph.linkColor(linkColorFn)
  graph.linkDirectionalParticles(linkParticles)
}

function nodeObject3d(node: Graph3DNodeObject): object {
  const meta = (node as NodeObject).meta
  const focusId = props.selectedId ?? props.centerId
  const dimmed = focusId !== null && meta.id !== focusId && !neighborIds.value.has(meta.id)
  const radius = 3.2 + Math.min(meta.degree, 40) * 0.12 + (meta.id === props.centerId ? 1.6 : 0)

  const material = new THREE.MeshLambertMaterial({
    color: nodeColor(meta.type),
    transparent: true,
    opacity: dimmed ? 0.16 : meta.id === props.selectedId ? 1 : 0.92,
    emissive: meta.id === props.selectedId || meta.id === props.centerId ? nodeColor(meta.type) : 0x000000,
    emissiveIntensity: meta.id === props.selectedId ? 0.9 : meta.id === props.centerId ? 0.45 : 0,
  })
  const mesh = new THREE.Mesh(new THREE.SphereGeometry(radius, 20, 20), material)
  return mesh
}

function linkColorFn(edge: Graph3DLinkObject): string {
  const focusId = props.selectedId ?? props.centerId
  if (focusId !== null && (edge.source === focusId || edge.target === focusId)) {
    return edgeActiveColor()
  }
  return edgeColor()
}

function linkParticles(edge: Graph3DLinkObject): number {
  const focusId = props.selectedId ?? props.centerId
  // 方向粒子只出现在选中路径上（§6.3 禁止每条边高密度特效）
  return focusId !== null && (edge.source === focusId || edge.target === focusId) ? 3 : 0
}

/**
 * Tooltip 内容由底层 3D 图谱按 HTML 渲染，节点名、定义、类型标签与关系名都可能来自
 * 持久化资料或 AI 生成内容（不可信输入），插入前必须转义。
 * 调用方需先截断再转义，避免实体被截断破坏。
 */
function escapeHtml(value: unknown): string {
  return String(value ?? '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;')
}

function nodeLabelHtml(node: Graph3DNodeObject): string {
  const meta = (node as NodeObject).meta
  const typeLabel = NODE_TYPE_LABELS[meta.type] ?? meta.type
  const name = escapeHtml(meta.name)
  const safeTypeLabel = escapeHtml(typeLabel)
  const definition = meta.definition ? escapeHtml(meta.definition.slice(0, 60)) : ''
  return `
    <div style="max-width:260px;padding:8px 12px;border-radius:10px;background:rgba(255,255,255,.96);
      border:1px solid #e5e7eb;color:#1f2937;font-size:12px;line-height:1.6;box-shadow:0 4px 12px rgba(0,0,0,.08)">
      <div style="font-weight:600">${name}</div>
      <div style="color:#6b7280">${safeTypeLabel} · ${escapeHtml(meta.degree)} 个直接关联</div>
      ${definition ? `<div style="color:#9ca3af;margin-top:2px">${definition}…</div>` : ''}
    </div>`
}

function linkLabelHtml(edge: Graph3DLinkObject): string {
  const relation = typeof edge.relation === 'string' ? edge.relation : ''
  return `<div style="padding:4px 10px;border-radius:8px;background:rgba(255,255,255,.96);border:1px solid #e5e7eb;color:#374151;font-size:12px">${escapeHtml(relation)}</div>`
}

function updateGraphData() {
  if (!graph) return
  graph.graphData({
    nodes: props.nodes.map(buildNodeObject),
    links: props.edges.map((edge) => {
      let link = linkObjects.get(edge.id)
      if (!link) {
        link = { id: edge.id, source: edge.source, target: edge.target }
        linkObjects.set(edge.id, link)
      }
      link.relation = edge.relation
      link.weight = edge.weight
      return link
    }),
  })
  refreshHighlight()
}

function focusCamera(node: Graph3DNodeObject, transitionMs = 450) {
  if (!graph) return
  const distance = 170
  const x = node.x ?? 0
  const y = node.y ?? 0
  const z = node.z ?? 0
  const axisLength = Math.sqrt(x * x + y * y + z * z) || 1
  graph.cameraPosition(
    { x: x + (x / axisLength) * distance, y: y + (y / axisLength) * distance, z: z + (z / axisLength) * distance },
    { x, y, z },
    transitionMs,
  )
}

onMounted(() => {
  const container = containerRef.value
  if (!container) return
  if (typeof window.WebGLRenderingContext === 'undefined') {
    emit('webgl-failed')
    return
  }
  try {
    graph = ForceGraph3D({ controlType: 'trackball' })(container)
      .width(container.clientWidth)
      .height(container.clientHeight)
      .backgroundColor(canvasBackground())
      .showNavInfo(false)
      .nodeLabel(nodeLabelHtml)
      .linkLabel(linkLabelHtml)
      .linkOpacity(0.35)
      .linkDirectionalParticleWidth(1.8)
      .linkDirectionalParticleSpeed(0.006)
      .cooldownTicks(180)
      .onNodeClick((node) => {
        const meta = (node as NodeObject).meta
        const now = Date.now()
        // 双击检测：两次快速点击同一节点 → 扩展下一层（§6.3）
        if (lastClick && lastClick.id === meta.id && now - lastClick.time < 320) {
          lastClick = null
          emit('expand', meta.id)
          return
        }
        lastClick = { id: meta.id, time: now }
        emit('select', meta.id)
        focusCamera(node)
      })
      .onBackgroundClick(() => emit('select', null))
      .onEngineStop(() => {
        if (pendingFocusId !== null && graph) {
          const target = nodeObjects.get(pendingFocusId)
          pendingFocusId = null
          if (target) focusCamera(target)
        }
      })

    const charge = graph.d3Force('charge')
    if (charge) charge.strength(-90)

    updateGraphData()
    graph.zoomToFit(400, 60)

    resizeObserver = new ResizeObserver(() => {
      if (graph && containerRef.value) {
        graph.width(containerRef.value.clientWidth).height(containerRef.value.clientHeight)
      }
    })
    resizeObserver.observe(container)
  } catch (error) {
    console.error('3D 图谱初始化失败', error)
    emit('webgl-failed')
  }
})

watch([() => props.nodes, () => props.edges], () => {
  updateGraphData()
})

watch([() => props.selectedId, () => props.centerId], () => {
  refreshHighlight()
  // 切换 2D/3D 或外部聚焦（如顶部搜索）时把摄像机对准当前中心节点；
  // 新节点可能尚未完成力布局，先挂起待引擎稳定后执行（§6.3 聚焦动画 300–600ms）
  if (props.centerId !== null && graph) {
    const centerObject = nodeObjects.get(props.centerId)
    if (centerObject && centerObject.x !== undefined) {
      focusCamera(centerObject)
    } else {
      pendingFocusId = props.centerId
    }
  }
})

onBeforeUnmount(() => {
  resizeObserver?.disconnect()
  graph?._destructor?.()
  graph = null
  nodeObjects.clear()
  linkObjects.clear()
})
</script>
