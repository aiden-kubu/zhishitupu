/**
 * 3d-force-graph 的最小类型声明（该库未随包分发 .d.ts）。
 * 仅声明本项目用到的方法；链式 API 均返回实例自身。
 */
declare module '3d-force-graph' {
  export interface Graph3DLinkObject {
    id?: number | string
    source: number | string | Graph3DNodeObject
    target: number | string | Graph3DNodeObject
    [key: string]: unknown
  }

  export interface Graph3DNodeObject {
    id: number | string
    x?: number
    y?: number
    z?: number
    [key: string]: unknown
  }

  export interface ForceGraph3DInstance {
    width(px?: number): ForceGraph3DInstance
    height(px?: number): ForceGraph3DInstance
    backgroundColor(color?: string): ForceGraph3DInstance
    showNavInfo(show?: boolean): ForceGraph3DInstance
    graphData(data: { nodes: Graph3DNodeObject[]; links: Graph3DLinkObject[] }): ForceGraph3DInstance
    nodeRelSize(size?: number): ForceGraph3DInstance
    nodeColor(accessor: (node: Graph3DNodeObject) => string): ForceGraph3DInstance
    nodeVal(accessor: (node: Graph3DNodeObject) => number): ForceGraph3DInstance
    nodeLabel(accessor: (node: Graph3DNodeObject) => string): ForceGraph3DInstance
    nodeOpacity(opacity?: number): ForceGraph3DInstance
    nodeThreeObject(
      accessor: (node: Graph3DNodeObject) => object | null,
    ): ForceGraph3DInstance
    linkColor(accessor: (link: Graph3DLinkObject) => string): ForceGraph3DInstance
    linkOpacity(opacity?: number): ForceGraph3DInstance
    linkWidth(accessor: (link: Graph3DLinkObject) => number): ForceGraph3DInstance
    linkLabel(accessor: (link: Graph3DLinkObject) => string): ForceGraph3DInstance
    linkDirectionalParticles(accessor: (link: Graph3DLinkObject) => number): ForceGraph3DInstance
    linkDirectionalParticleWidth(width?: number): ForceGraph3DInstance
    linkDirectionalParticleSpeed(speed?: number): ForceGraph3DInstance
    onNodeClick(callback: (node: Graph3DNodeObject, event: MouseEvent) => void): ForceGraph3DInstance
    onBackgroundClick(callback: (event: MouseEvent) => void): ForceGraph3DInstance
    onEngineStop(callback: () => void): ForceGraph3DInstance
    cooldownTicks(ticks?: number): ForceGraph3DInstance
    d3Force(name: string): { strength(v?: number): unknown } | undefined
    cameraPosition(
      position?: { x: number; y: number; z: number },
      lookAt?: { x: number; y: number; z: number } | null,
      transitionMs?: number,
    ): ForceGraph3DInstance
    zoomToFit(ms?: number, px?: number): ForceGraph3DInstance
    _destructor(): void
  }

  export default function ForceGraph3D(config?: {
    controlType?: 'trackball' | 'orbit' | 'fly'
  }): (element: HTMLElement) => ForceGraph3DInstance
}
