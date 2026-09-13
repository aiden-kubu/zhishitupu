package com.knowledgegraph.graph.dto;

/**
 * 子图节点（§12.2 示例结构）。
 */
public record SubgraphNode(long id, String name, String type, String definition, int degree, int sourceCount) {
}
