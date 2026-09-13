package com.knowledgegraph.insight;

import com.knowledgegraph.insight.dto.InsightSummary;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 统计服务（§13 InsightService）。
 */
@Service
public class InsightService {

    private final JdbcClient jdbc;

    public InsightService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public InsightSummary summary() {
        long nodeCount = count("SELECT COUNT(*) FROM knowledge_nodes");
        long edgeCount = count("SELECT COUNT(*) FROM knowledge_edges WHERE status = 'active'");
        long documentCount = count("SELECT COUNT(*) FROM documents");
        long pendingReviewCount = count("SELECT COUNT(*) FROM entity_candidates WHERE review_status = 'PENDING'")
                + count("SELECT COUNT(*) FROM relation_candidates WHERE review_status = 'PENDING'");

        List<InsightSummary.TypeCount> distribution = jdbc.sql(
                        "SELECT node_type AS type, COUNT(*) AS cnt FROM knowledge_nodes GROUP BY node_type ORDER BY cnt DESC, type")
                .query((rs, i) -> new InsightSummary.TypeCount(rs.getString("type"), rs.getLong("cnt")))
                .list();

        List<InsightSummary.DegreeRank> topDegree = jdbc.sql("""
                        SELECT kn.id, kn.canonical_name AS name, kn.node_type AS type,
                               (SELECT COUNT(*) FROM knowledge_edges e
                                 WHERE e.status = 'active'
                                   AND (e.source_node_id = kn.id OR e.target_node_id = kn.id)) AS degree
                        FROM knowledge_nodes kn
                        ORDER BY degree DESC, kn.id ASC
                        LIMIT 10
                        """)
                .query((rs, i) -> new InsightSummary.DegreeRank(
                        rs.getLong("id"), rs.getString("name"), rs.getString("type"), rs.getInt("degree")))
                .list();

        List<InsightSummary.IsolatedNode> isolated = jdbc.sql("""
                        SELECT id, name, type FROM (
                          SELECT kn.id, kn.canonical_name AS name, kn.node_type AS type,
                                 (SELECT COUNT(*) FROM knowledge_edges e
                                   WHERE e.status = 'active'
                                     AND (e.source_node_id = kn.id OR e.target_node_id = kn.id)) AS degree
                          FROM knowledge_nodes kn
                        ) t
                        WHERE t.degree = 0
                        ORDER BY t.id
                        LIMIT 100
                        """)
                .query((rs, i) -> new InsightSummary.IsolatedNode(rs.getLong("id"), rs.getString("name"), rs.getString("type")))
                .list();

        return new InsightSummary(nodeCount, edgeCount, documentCount, pendingReviewCount,
                distribution, topDegree, isolated);
    }

    private long count(String sql) {
        Long result = jdbc.sql(sql).query((rs, i) -> rs.getLong(1)).single();
        return result == null ? 0 : result;
    }
}
