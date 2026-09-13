package com.knowledgegraph.graph.dto;

import java.util.List;

/**
 * 子图响应 data（§12.2）。
 */
public record SubgraphData(
        long centerNodeId,
        List<SubgraphNode> nodes,
        List<SubgraphEdge> edges,
        boolean truncated,
        long totalNodes,
        long totalEdges) {
}
