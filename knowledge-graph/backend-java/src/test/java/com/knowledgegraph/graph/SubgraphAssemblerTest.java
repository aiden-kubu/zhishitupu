package com.knowledgegraph.graph;

import com.knowledgegraph.graph.dto.SubgraphData;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.LongStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 子图截断上限逻辑单元测试（§6.4：节点 500 / 边 1200，总览 300）。
 */
class SubgraphAssemblerTest {

    private static SubgraphAssembler.RawNode node(long id, int degree) {
        return new SubgraphAssembler.RawNode(id, "节点" + id, "concept", "定义" + id, degree, 0);
    }

    @Test
    void keepsEverythingUnderLimits() {
        List<SubgraphAssembler.RawNode> nodes = List.of(node(1, 3), node(2, 2), node(3, 0));
        List<SubgraphAssembler.RawEdge> edges = List.of(
                new SubgraphAssembler.RawEdge(100, 1, 2, "相关", 1.0),
                new SubgraphAssembler.RawEdge(101, 1, 3, "包含", 1.0));

        SubgraphData data = SubgraphAssembler.assemble(1, nodes, edges, 3, 2, 500, 1200);

        assertFalse(data.truncated());
        assertEquals(3, data.totalNodes());
        assertEquals(2, data.totalEdges());
        assertEquals(3, data.nodes().size());
        assertEquals(2, data.edges().size());
        assertEquals(1, data.centerNodeId());
    }

    @Test
    void truncatesNodesAtCapAndKeepsCenterFirst() {
        // 501 个节点，中心节点 degree=0 仍然必须保留
        List<SubgraphAssembler.RawNode> nodes = new ArrayList<>();
        nodes.add(node(1, 0));
        for (long id = 2; id <= 501; id++) {
            nodes.add(node(id, (int) (id % 10)));
        }

        SubgraphData data = SubgraphAssembler.assemble(1, nodes, List.of(), 501, 0, 500, 1200);

        assertTrue(data.truncated());
        assertEquals(501, data.totalNodes());
        assertEquals(500, data.nodes().size());
        assertEquals(1, data.nodes().get(0).id()); // 中心节点排第一且被保留
    }

    @Test
    void truncatesEdgesAtCapPrioritizingCenterEdges() {
        // 1211 个节点全部保留（nodeLimit 不截断），重点验证边上限与「中心相连优先」
        List<SubgraphAssembler.RawNode> nodes = new ArrayList<>();
        nodes.add(node(1, 1210));
        for (long id = 2; id <= 1211; id++) {
            nodes.add(node(id, 1));
        }
        List<SubgraphAssembler.RawEdge> edges = new ArrayList<>();
        long edgeId = 1;
        for (long id = 2; id <= 1211; id++) {
            edges.add(new SubgraphAssembler.RawEdge(edgeId++, 1, id, "相关", 1.0)); // 1210 条与中心相连的边
        }
        edges.add(new SubgraphAssembler.RawEdge(edgeId, 2, 3, "相关", 1.0)); // 第 1211 条，不与中心相连

        SubgraphData data = SubgraphAssembler.assemble(1, nodes, edges, 1211, 1211, 1211, 1200);

        assertTrue(data.truncated());
        assertEquals(1200, data.edges().size());
        assertEquals(1211, data.totalEdges());
        assertEquals(1211, data.nodes().size());
        // 前 1200 条应全部是与中心相连的边
        assertTrue(data.edges().stream().allMatch(e -> e.source() == 1 || e.target() == 1));
    }

    @Test
    void dropsEdgesReferencingExcludedNodes() {
        List<SubgraphAssembler.RawNode> nodes = List.of(node(1, 1), node(2, 1));
        // 边 999 的端点 3 不在保留集合内，不能返回孤儿边
        List<SubgraphAssembler.RawEdge> edges = List.of(
                new SubgraphAssembler.RawEdge(10, 1, 2, "相关", 1.0),
                new SubgraphAssembler.RawEdge(11, 2, 3, "相关", 1.0));

        SubgraphData data = SubgraphAssembler.assemble(1, nodes, edges, 3, 2, 500, 1200);

        assertEquals(1, data.edges().size());
        assertEquals(10, data.edges().get(0).id());
        assertTrue(data.truncated()); // totalEdges(2) > 返回边数(1)
        assertEquals(2, data.totalEdges());
    }

    @Test
    void overviewTruncationMarkedWhenTotalsExceedReturnedNodes() {
        // 总览场景：候选节点已是「前 N」，全量总数更大
        List<SubgraphAssembler.RawNode> top50 = new ArrayList<>();
        LongStream.rangeClosed(1, 50).forEach(id -> top50.add(node(id, (int) (60 - id))));
        List<SubgraphAssembler.RawEdge> edges = List.of(
                new SubgraphAssembler.RawEdge(1, 1, 2, "相关", 1.0));

        SubgraphData data = SubgraphAssembler.assemble(1, top50, edges, 60, 76, 50, 1200);

        assertTrue(data.truncated());
        assertEquals(60, data.totalNodes());
        assertEquals(76, data.totalEdges());
        assertEquals(50, data.nodes().size());
        assertEquals(1, data.edges().size());
    }

    @Test
    void ordersNodesByDegreeDescendingAfterCenter() {
        List<SubgraphAssembler.RawNode> nodes = List.of(node(1, 0), node(2, 5), node(3, 9), node(4, 9));
        SubgraphData data = SubgraphAssembler.assemble(1, nodes, List.of(), 4, 0, 500, 1200);

        assertEquals(1, data.nodes().get(0).id());
        assertEquals(3, data.nodes().get(1).id()); // degree 9 且 id 较小
        assertEquals(4, data.nodes().get(2).id());
        assertEquals(2, data.nodes().get(3).id());
    }
}
