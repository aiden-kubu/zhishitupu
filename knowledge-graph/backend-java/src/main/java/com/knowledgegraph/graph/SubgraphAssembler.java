package com.knowledgegraph.graph;

import com.knowledgegraph.graph.dto.SubgraphData;
import com.knowledgegraph.graph.dto.SubgraphEdge;
import com.knowledgegraph.graph.dto.SubgraphNode;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 子图组装与截断的纯逻辑（无数据库依赖，便于单元测试）。
 *
 * 规则（§6.4）：
 * - 节点数超过上限时按「中心节点优先、关联度降序、id 升序」保留前 nodeLimit 个；
 * - 只返回两端都在保留节点集合内的边（不返回孤儿边，§14.3）；
 * - 边数超过上限时按「与中心相连优先、id 升序」保留前 edgeLimit 条；
 * - truncated 为真表示返回的是子集；totalNodes/totalEdges 为截断前的总数。
 */
public final class SubgraphAssembler {

    /** 子图内原始节点。 */
    public record RawNode(long id, String name, String type, String definition, int degree, int sourceCount) {
    }

    /** 子图内原始边。 */
    public record RawEdge(long id, long source, long target, String relation, double weight) {
    }

    private SubgraphAssembler() {
    }

    public static SubgraphData assemble(long centerNodeId,
                                        List<RawNode> candidateNodes,
                                        List<RawEdge> candidateEdges,
                                        long totalNodes,
                                        long totalEdges,
                                        int nodeLimit,
                                        int edgeLimit) {
        List<RawNode> sortedNodes = new ArrayList<>(candidateNodes);
        sortedNodes.sort(Comparator
                .comparing((RawNode n) -> n.id() == centerNodeId ? 0 : 1)
                .thenComparing(Comparator.comparingInt(RawNode::degree).reversed())
                .thenComparingLong(RawNode::id));

        boolean truncated = false;
        int keptCount = Math.min(sortedNodes.size(), Math.max(0, nodeLimit));
        if (keptCount < sortedNodes.size()) {
            truncated = true;
        }
        List<RawNode> keptNodes = sortedNodes.subList(0, keptCount);

        Set<Long> keptIds = new HashSet<>();
        for (RawNode n : keptNodes) {
            keptIds.add(n.id());
        }

        List<RawEdge> insideEdges = new ArrayList<>();
        for (RawEdge e : candidateEdges) {
            if (keptIds.contains(e.source()) && keptIds.contains(e.target())) {
                insideEdges.add(e);
            }
        }
        insideEdges.sort(Comparator
                .comparing((RawEdge e) -> (e.source() == centerNodeId || e.target() == centerNodeId) ? 0 : 1)
                .thenComparingLong(RawEdge::id));
        int keptEdgeCount = Math.min(insideEdges.size(), Math.max(0, edgeLimit));
        if (keptEdgeCount < insideEdges.size()) {
            truncated = true;
        }

        // 全量总数本身大于保留数量，同样视为被截断（例如总览按关联度取前 N 的场景）
        if (totalNodes > keptCount || totalEdges > keptEdgeCount) {
            truncated = true;
        }

        List<SubgraphNode> nodes = new ArrayList<>(keptCount);
        for (RawNode n : keptNodes) {
            nodes.add(new SubgraphNode(n.id(), n.name(), n.type(), n.definition(), n.degree(), n.sourceCount()));
        }
        List<SubgraphEdge> edges = new ArrayList<>(keptEdgeCount);
        for (RawEdge e : insideEdges.subList(0, keptEdgeCount)) {
            edges.add(new SubgraphEdge(e.id(), e.source(), e.target(), e.relation(), e.weight()));
        }
        return new SubgraphData(centerNodeId, List.copyOf(nodes), List.copyOf(edges), truncated, totalNodes, totalEdges);
    }
}
