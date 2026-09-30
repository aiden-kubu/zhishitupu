package com.knowledgegraph.assistant.learning;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowledgegraph.assistant.contract.AssistantContracts.Scope;
import com.knowledgegraph.assistant.research.ResearchReadRepository;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

/** Scoped learning facts; no use of the legacy save/current service or its write methods. */
public final class JdbcLearningReadRepository implements LearningReadRepository {
    private final JdbcClient jdbc;
    private final ResearchReadRepository research;
    private final ObjectMapper json = new ObjectMapper();

    public JdbcLearningReadRepository(JdbcClient jdbc, ResearchReadRepository research) {
        this.jdbc = jdbc;
        this.research = research;
    }

    @Override public Path currentPath(Scope scope) {
        record Header(long id, long libraryId, String goal, String difficulty, String status, String updatedAt) {}
        Optional<Header> header = jdbc.sql("""
                SELECT id,library_id,goal,difficulty,status,updated_at FROM learning_paths
                WHERE profile_key='local-default' LIMIT 1
                """).query((rs, i) -> new Header(rs.getLong(1), rs.getLong(2), rs.getString(3),
                rs.getString(4), rs.getString(5), instant(rs.getTimestamp(6)))).optional();
        if (header.isEmpty()) return Path.none();
        Header h = header.get();
        if (!libraryVisible(scope, h.libraryId())) return Path.unavailable();
        List<Step> steps = jdbc.sql("""
                SELECT id,step_key,node_id,position_index,title,estimated_minutes,status,visited_at,
                       assessment_score,updated_at
                FROM learning_path_steps WHERE path_id=:pathId
                ORDER BY position_index,id LIMIT 9
                """).param("pathId", h.id()).query((rs, i) -> new Step(rs.getLong(1), rs.getString(2),
                rs.getLong(3), rs.getInt(4), rs.getString(5), rs.getInt(6), rs.getString(7),
                instant(rs.getTimestamp(8)), rs.getObject(9, Integer.class), instant(rs.getTimestamp(10)))).list();
        if (steps.isEmpty() || steps.size() > 8) return Path.unavailable();
        for (int i = 0; i < steps.size(); i++) {
            Step step = steps.get(i);
            if (step.stepId() <= 0 || step.nodeId() <= 0 || step.stepKey() == null
                    || step.position() != i || step.estimatedMinutes() <= 0
                    || research.node(scope, step.nodeId()).isEmpty()) return Path.unavailable();
            for (int j = 0; j < i; j++) {
                if (step.stepKey().equals(steps.get(j).stepKey()) || step.nodeId() == steps.get(j).nodeId())
                    return Path.unavailable();
            }
            // A path library must remain the actual owner of every step, even under a broader host scope.
            if (research.node(new com.knowledgegraph.assistant.contract.AssistantContracts.Scope(
                    com.knowledgegraph.assistant.contract.AssistantContracts.ScopeMode.SELECTED,
                    java.util.Set.of(h.libraryId()), scope.documentIds(), scope.nodeIds()), step.nodeId()).isEmpty())
                return Path.unavailable();
        }
        return new Path(PathState.AVAILABLE, h.id(), h.libraryId(), h.goal(), h.difficulty(), h.status(),
                h.updatedAt(), fingerprint(h, steps), steps);
    }

    @Override public Graph graph(Scope scope, int maxNodes, int maxEdges) {
        if (maxNodes < 1 || maxNodes > 50 || maxEdges < 1 || maxEdges > 100)
            throw new IllegalArgumentException("INVALID_GRAPH_LIMIT");
        String sql = "SELECT n.id FROM knowledge_nodes n WHERE 1=1" + nodeScope("n", scope)
                + " ORDER BY n.id LIMIT :maxRows";
        List<Long> ids = bind(jdbc.sql(sql), scope).param("maxRows", maxNodes + 1).query(Long.class).list();
        List<ResearchReadRepository.Node> nodes = new ArrayList<>();
        for (long id : ids.subList(0, Math.min(maxNodes, ids.size()))) {
            research.node(scope, id).ifPresent(nodes::add);
        }
        boolean nodesTruncated = ids.size() > maxNodes || nodes.size() < Math.min(maxNodes, ids.size());
        if (nodes.isEmpty()) return new Graph(List.of(), List.of(), nodesTruncated, false);
        List<Long> visible = nodes.stream().map(ResearchReadRepository.Node::id).toList();
        String edgeSql = """
                SELECT e.id,e.source_node_id,e.target_node_id,e.relation_type,e.status,e.updated_at,
                       e.properties_json,
                       EXISTS(SELECT 1 FROM edge_evidence ee JOIN document_chunks ec ON ec.id=ee.chunk_id
                              JOIN document_units eu ON eu.id=ec.unit_id JOIN documents ed ON ed.id=eu.document_id
                              LEFT JOIN document_metadata em ON em.document_id=ed.id
                              WHERE ee.edge_id=e.id AND ed.status='COMPLETED'
                                AND eu.status IN ('READY','OCR_OK')
                                AND (em.lifecycle_status IS NULL OR em.lifecycle_status='active')
                """ + documentScope("ed", scope) + """
                       ) AS has_source
                FROM knowledge_edges e JOIN knowledge_nodes s ON s.id=e.source_node_id
                JOIN knowledge_nodes t ON t.id=e.target_node_id
                WHERE e.source_node_id IN (:visibleIds) AND e.target_node_id IN (:visibleIds)
                """ + nodeScope("s", scope) + nodeScope("t", scope) + edgeDocumentScope(scope)
                + " ORDER BY e.id LIMIT :maxEdges";
        List<ResearchReadRepository.Edge> edges = bind(jdbc.sql(edgeSql), scope)
                .param("visibleIds", visible).param("maxEdges", maxEdges + 1)
                .query((rs, i) -> edge(rs)).list();
        return new Graph(nodes, edges.subList(0, Math.min(edges.size(), maxEdges)),
                nodesTruncated, edges.size() > maxEdges);
    }

    @Override public Optional<ResearchReadRepository.Node> node(Scope scope, long nodeId) {
        return research.node(scope, nodeId);
    }

    @Override public Optional<ResearchReadRepository.Edge> edge(Scope scope, long edgeId) {
        String sql = """
                SELECT e.id,e.source_node_id,e.target_node_id,e.relation_type,e.status,e.updated_at,
                       e.properties_json,
                       EXISTS(SELECT 1 FROM edge_evidence ee JOIN document_chunks ec ON ec.id=ee.chunk_id
                              JOIN document_units eu ON eu.id=ec.unit_id JOIN documents ed ON ed.id=eu.document_id
                              LEFT JOIN document_metadata em ON em.document_id=ed.id
                              WHERE ee.edge_id=e.id AND ed.status='COMPLETED'
                                AND eu.status IN ('READY','OCR_OK')
                                AND (em.lifecycle_status IS NULL OR em.lifecycle_status='active')
                """ + documentScope("ed", scope) + """
                       ) AS has_source
                FROM knowledge_edges e JOIN knowledge_nodes s ON s.id=e.source_node_id
                JOIN knowledge_nodes t ON t.id=e.target_node_id
                WHERE e.id=:edgeId
                """ + nodeScope("s", scope) + nodeScope("t", scope) + edgeDocumentScope(scope);
        return bind(jdbc.sql(sql), scope).param("edgeId", edgeId)
                .query((rs, i) -> edge(rs)).optional();
    }

    @Override public boolean libraryVisible(Scope scope, long libraryId) {
        if (libraryId <= 0 || !scope.libraryIds().isEmpty() && !scope.libraryIds().contains(libraryId))
            return false;
        String sql = "SELECT EXISTS(SELECT 1 FROM knowledge_libraries k WHERE k.id=:libraryId AND k.status='active'";
        if (!scope.documentIds().isEmpty()) {
            sql += " AND EXISTS(SELECT 1 FROM documents d WHERE d.id IN (:documentIds)"
                    + " AND (d.library_id=k.id OR EXISTS(SELECT 1 FROM document_topic_assignments a"
                    + " WHERE a.document_id=d.id AND a.library_id=k.id)))";
        }
        if (!scope.nodeIds().isEmpty()) {
            sql += " AND EXISTS(SELECT 1 FROM library_nodes ln WHERE ln.library_id=k.id"
                    + " AND ln.node_id IN (:nodeIds))";
        }
        return bind(jdbc.sql(sql + ")"), scope).param("libraryId", libraryId).query(Boolean.class).single();
    }

    @Override public boolean nodeSourceLinked(Scope scope, long nodeId, long documentId, long chunkId) {
        String sql = """
                SELECT EXISTS(SELECT 1 FROM node_evidence ne
                JOIN knowledge_nodes n ON n.id=ne.node_id
                JOIN document_chunks c ON c.id=ne.chunk_id
                JOIN document_units u ON u.id=c.unit_id
                JOIN documents d ON d.id=u.document_id
                LEFT JOIN document_metadata m ON m.document_id=d.id
                WHERE ne.node_id=:nodeId AND d.id=:documentId AND c.id=:chunkId
                  AND d.status='COMPLETED' AND u.status IN ('READY','OCR_OK')
                  AND (m.lifecycle_status IS NULL OR m.lifecycle_status='active')
                """ + nodeScope("n", scope) + documentScope("d", scope) + ")";
        return bind(jdbc.sql(sql), scope).param("nodeId", nodeId)
                .param("documentId", documentId).param("chunkId", chunkId).query(Boolean.class).single();
    }

    private static String nodeScope(String alias, Scope scope) {
        String sql = "";
        if (!scope.nodeIds().isEmpty()) sql += " AND " + alias + ".id IN (:nodeIds)";
        if (!scope.libraryIds().isEmpty()) sql += " AND EXISTS(SELECT 1 FROM library_nodes ln WHERE ln.node_id="
                + alias + ".id AND ln.library_id IN (:libraryIds))";
        if (!scope.documentIds().isEmpty()) sql += " AND EXISTS(SELECT 1 FROM node_evidence ne"
                + " JOIN document_chunks c ON c.id=ne.chunk_id JOIN document_units u ON u.id=c.unit_id"
                + " JOIN documents d ON d.id=u.document_id LEFT JOIN document_metadata m ON m.document_id=d.id"
                + " WHERE ne.node_id=" + alias + ".id AND d.id IN (:documentIds)"
                + " AND d.status='COMPLETED' AND u.status IN ('READY','OCR_OK')"
                + " AND (m.lifecycle_status IS NULL OR m.lifecycle_status='active')"
                + documentScope("d", scope) + ")";
        return sql;
    }
    private static String edgeDocumentScope(Scope scope) {
        if (scope.documentIds().isEmpty()) return "";
        return " AND EXISTS(SELECT 1 FROM edge_evidence ee"
                + " JOIN document_chunks c ON c.id=ee.chunk_id JOIN document_units u ON u.id=c.unit_id"
                + " JOIN documents d ON d.id=u.document_id LEFT JOIN document_metadata m ON m.document_id=d.id"
                + " WHERE ee.edge_id=e.id AND d.id IN (:documentIds)"
                + " AND d.status='COMPLETED' AND u.status IN ('READY','OCR_OK')"
                + " AND (m.lifecycle_status IS NULL OR m.lifecycle_status='active')"
                + documentScope("d", scope) + ")";
    }
    private static String documentScope(String alias, Scope scope) {
        String sql = "";
        if (!scope.libraryIds().isEmpty()) sql += " AND (" + alias + ".library_id IN (:libraryIds)"
                + " OR EXISTS(SELECT 1 FROM document_topic_assignments a WHERE a.document_id="
                + alias + ".id AND a.library_id IN (:libraryIds)))";
        if (!scope.documentIds().isEmpty()) sql += " AND " + alias + ".id IN (:documentIds)";
        return sql;
    }
    private static JdbcClient.StatementSpec bind(JdbcClient.StatementSpec spec, Scope scope) {
        if (!scope.libraryIds().isEmpty()) spec = spec.param("libraryIds", scope.libraryIds());
        if (!scope.documentIds().isEmpty()) spec = spec.param("documentIds", scope.documentIds());
        if (!scope.nodeIds().isEmpty()) spec = spec.param("nodeIds", scope.nodeIds());
        return spec;
    }
    private ResearchReadRepository.Edge edge(ResultSet rs) throws SQLException {
        JsonNode properties;
        try { properties = rs.getString(7) == null ? json.createObjectNode() : json.readTree(rs.getString(7)); }
        catch (Exception ignored) { properties = json.createObjectNode(); }
        return new ResearchReadRepository.Edge(rs.getLong(1), rs.getLong(2), rs.getLong(3),
                rs.getString(4), rs.getString(5), instant(rs.getTimestamp(6)),
                property(properties, "origin"), property(properties, "reviewStatus"), rs.getBoolean(8));
    }
    private static String property(JsonNode value, String key) {
        JsonNode item = value.get(key);
        return item != null && item.isTextual() ? item.textValue() : null;
    }
    private static String instant(Timestamp value) { return value == null ? null : value.toInstant().toString(); }
    private static String fingerprint(Object header, List<Step> steps) {
        StringBuilder canonical = new StringBuilder();
        field(canonical, header.toString());
        for (Step step : steps) {
            field(canonical, Long.toString(step.stepId())); field(canonical, step.stepKey());
            field(canonical, Long.toString(step.nodeId())); field(canonical, Integer.toString(step.position()));
            field(canonical, step.title()); field(canonical, Integer.toString(step.estimatedMinutes()));
            field(canonical, step.status()); field(canonical, step.visitedAt());
            field(canonical, String.valueOf(step.legacyAssessmentScore())); field(canonical, step.updatedAt());
        }
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    private static void field(StringBuilder target, String value) {
        String text = value == null ? "<null>" : value;
        target.append(text.length()).append(':').append(text);
    }
}
