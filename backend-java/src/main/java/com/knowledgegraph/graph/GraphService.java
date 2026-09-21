package com.knowledgegraph.graph;

import com.knowledgegraph.common.ApiException;
import com.knowledgegraph.graph.dto.SubgraphData;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 图谱查询服务：全局总览、邻域子图（§13 GraphService 的只读部分）。
 */
@Service
public class GraphService {

    /** 画布限制（§6.4）：单次邻域最多 500 节点 / 1200 边；总览最多返回 300 个代表性节点。 */
    public static final int MAX_NEIGHBOR_NODES = 500;
    public static final int MAX_NEIGHBOR_EDGES = 1200;
    public static final int MAX_OVERVIEW_NODES = 300;

    /** 带关联度与来源数的节点投影。 */
    static final String NODE_PROJECTION = """
            SELECT kn.id,
                   kn.canonical_name AS name,
                   kn.node_type      AS type,
                   kn.definition,
                   (SELECT COUNT(*) FROM knowledge_edges e
                     WHERE e.status = 'active'
                       AND (e.source_node_id = kn.id OR e.target_node_id = kn.id)) AS degree,
                   (SELECT COUNT(*) FROM node_evidence ne WHERE ne.node_id = kn.id) AS source_count
            FROM knowledge_nodes kn
            """;

    private final JdbcClient jdbc;

    public GraphService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** 全局总览：按关联度取前 N 个代表性节点及其内部边。 */
    public SubgraphData overview(Long libraryId, Integer limit) {
        int nodeLimit = clamp(limit == null ? MAX_OVERVIEW_NODES : limit, 1, MAX_OVERVIEW_NODES);
        boolean scoped = libraryId != null;

        String scopeJoin = scoped ? "JOIN library_nodes ln ON ln.node_id = kn.id AND ln.library_id = :libraryId" : "";
        long totalNodes = jdbc.sql("SELECT COUNT(*) FROM knowledge_nodes kn " + scopeJoin)
                .param("libraryId", libraryId)
                .query((rs, i) -> rs.getLong(1))
                .single();
        String edgeScopeSql = scoped
                ? """
                SELECT COUNT(*) FROM knowledge_edges e
                JOIN library_nodes la ON la.node_id = e.source_node_id AND la.library_id = :libraryId
                JOIN library_nodes lb ON lb.node_id = e.target_node_id AND lb.library_id = :libraryId
                WHERE e.status = 'active'
                """
                : "SELECT COUNT(*) FROM knowledge_edges e WHERE e.status = 'active'";
        long totalEdges = jdbc.sql(edgeScopeSql)
                .param("libraryId", libraryId)
                .query((rs, i) -> rs.getLong(1))
                .single();

        List<SubgraphAssembler.RawNode> selected = jdbc
                .sql(NODE_PROJECTION + scopeJoin + " ORDER BY degree DESC, kn.id ASC LIMIT :nodeLimit")
                .param("libraryId", libraryId)
                .param("nodeLimit", nodeLimit)
                .query((rs, i) -> new SubgraphAssembler.RawNode(
                        rs.getLong("id"), rs.getString("name"), rs.getString("type"),
                        rs.getString("definition"), rs.getInt("degree"), rs.getInt("source_count")))
                .list();

        List<SubgraphAssembler.RawEdge> candidateEdges = List.of();
        if (!selected.isEmpty()) {
            candidateEdges = jdbc.sql("""
                            SELECT e.id, e.source_node_id, e.target_node_id, e.relation_type, e.weight
                            FROM knowledge_edges e
                            WHERE e.status = 'active'
                              AND e.source_node_id IN (:ids) AND e.target_node_id IN (:ids)
                            ORDER BY e.id
                            LIMIT :edgeLimit
                            """)
                    .param("ids", selected.stream().map(SubgraphAssembler.RawNode::id).toList())
                    .param("edgeLimit", MAX_NEIGHBOR_EDGES)
                    .query((rs, i) -> new SubgraphAssembler.RawEdge(
                            rs.getLong(1), rs.getLong(2), rs.getLong(3), rs.getString(4), rs.getDouble(5)))
                    .list();
        }

        long centerNodeId = selected.isEmpty() ? 0L : selected.get(0).id();
        return SubgraphAssembler.assemble(centerNodeId, selected, candidateEdges,
                totalNodes, totalEdges, nodeLimit, MAX_NEIGHBOR_EDGES);
    }

    /** 邻域子图：depth 仅支持 1 或 2，节点/边上限 500/1200。 */
    public SubgraphData neighbors(long nodeId, Integer depth, Integer limit) {
        int d = depth == null ? 1 : depth;
        if (d != 1 && d != 2) {
            throw ApiException.badRequest("depth 仅支持 1 或 2");
        }
        int nodeLimit = clamp(limit == null ? MAX_NEIGHBOR_NODES : limit, 1, MAX_NEIGHBOR_NODES);

        existsOrThrow(nodeId);

        String reachSql = d == 1 ? reachDepth1() : reachDepth2();
        List<SubgraphAssembler.RawNode> nodes = jdbc
                .sql(reachSql + NODE_PROJECTION + " WHERE kn.id IN (SELECT id FROM reach)")
                .param("center", nodeId)
                .query((rs, i) -> new SubgraphAssembler.RawNode(
                        rs.getLong("id"), rs.getString("name"), rs.getString("type"),
                        rs.getString("definition"), rs.getInt("degree"), rs.getInt("source_count")))
                .list();

        List<Long> ids = nodes.stream().map(SubgraphAssembler.RawNode::id).toList();
        List<SubgraphAssembler.RawEdge> edges = ids.isEmpty() ? List.of() : jdbc.sql("""
                        SELECT e.id, e.source_node_id, e.target_node_id, e.relation_type, e.weight
                        FROM knowledge_edges e
                        WHERE e.status = 'active'
                          AND e.source_node_id IN (:ids) AND e.target_node_id IN (:ids)
                        ORDER BY e.id
                        """)
                .param("ids", ids)
                .query((rs, i) -> new SubgraphAssembler.RawEdge(
                        rs.getLong(1), rs.getLong(2), rs.getLong(3), rs.getString(4), rs.getDouble(5)))
                .list();

        long totalNodes = nodes.size();
        long totalEdges = edges.size();
        return SubgraphAssembler.assemble(nodeId, nodes, edges, totalNodes, totalEdges, nodeLimit, MAX_NEIGHBOR_EDGES);
    }

    private static String reachDepth1() {
        return """
                WITH reach AS (
                  SELECT :center AS id
                  UNION
                  SELECT e.source_node_id FROM knowledge_edges e WHERE e.status = 'active' AND e.target_node_id = :center
                  UNION
                  SELECT e.target_node_id FROM knowledge_edges e WHERE e.status = 'active' AND e.source_node_id = :center
                )
                """;
    }

    private static String reachDepth2() {
        return """
                WITH d1 AS (
                  SELECT e.source_node_id AS id FROM knowledge_edges e WHERE e.status = 'active' AND e.target_node_id = :center
                  UNION
                  SELECT e.target_node_id AS id FROM knowledge_edges e WHERE e.status = 'active' AND e.source_node_id = :center
                ), reach AS (
                  SELECT :center AS id
                  UNION SELECT id FROM d1
                  UNION
                  SELECT e.source_node_id FROM knowledge_edges e JOIN d1 ON e.target_node_id = d1.id WHERE e.status = 'active'
                  UNION
                  SELECT e.target_node_id FROM knowledge_edges e JOIN d1 ON e.source_node_id = d1.id WHERE e.status = 'active'
                )
                """;
    }

    private void existsOrThrow(long nodeId) {
        Boolean exists = jdbc.sql("SELECT EXISTS(SELECT 1 FROM knowledge_nodes WHERE id = :id)")
                .param("id", nodeId)
                .query((rs, i) -> rs.getBoolean(1))
                .single();
        if (!Boolean.TRUE.equals(exists)) {
            throw ApiException.notFound("节点不存在: " + nodeId);
        }
    }

    public static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
