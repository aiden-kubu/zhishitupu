package com.knowledgegraph.insight.dto;

import java.util.List;

/**
 * 数据洞察汇总（§9.4）。
 */
public record InsightSummary(
        long nodeCount,
        long edgeCount,
        long documentCount,
        long pendingReviewCount,
        List<TypeCount> nodeTypeDistribution,
        List<DegreeRank> topDegreeNodes,
        List<IsolatedNode> isolatedNodes) {

    public record TypeCount(String type, long count) {
    }

    public record DegreeRank(long id, String name, String type, int degree) {
    }

    public record IsolatedNode(long id, String name, String type) {
    }
}
