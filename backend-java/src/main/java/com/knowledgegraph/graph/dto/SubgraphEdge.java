package com.knowledgegraph.graph.dto;

/**
 * 子图边（§12.2 示例结构）。
 */
public record SubgraphEdge(long id, long source, long target, String relation, double weight) {
}
