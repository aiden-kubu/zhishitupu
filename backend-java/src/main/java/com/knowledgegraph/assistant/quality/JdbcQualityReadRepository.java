package com.knowledgegraph.assistant.quality;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowledgegraph.assistant.contract.AssistantContracts.Scope;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Parameterized, read-only queries; no existing Review/Insight write path is invoked. */
public final class JdbcQualityReadRepository implements QualityReadRepository {
    private final JdbcClient jdbc;
    private final ObjectMapper mapper;

    public JdbcQualityReadRepository(JdbcClient jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    private static void reviewScope(Scope scope) {
        if (!scope.nodeIds().isEmpty()) throw new IllegalArgumentException("NODE_SCOPE_UNSUPPORTED");
    }

    private static void insightScope(Scope scope) {
        if (!scope.documentIds().isEmpty() || !scope.nodeIds().isEmpty()
                || (scope.mode().name().equals("SELECTED") && scope.libraryIds().isEmpty())) {
            throw new IllegalArgumentException("UNSUPPORTED_SCOPE");
        }
    }

    private static String documentFilter(Scope scope, String alias) {
        reviewScope(scope);
        String sql = "";
        if (!scope.libraryIds().isEmpty()) sql += " AND (" + alias + ".library_id IN (:libraryIds) OR EXISTS "
                + "(SELECT 1 FROM document_topic_assignments a WHERE a.document_id=" + alias
                + ".id AND a.library_id IN (:libraryIds)))";
        if (!scope.documentIds().isEmpty()) sql += " AND " + alias + ".id IN (:documentIds)";
        return sql;
    }

    private static JdbcClient.StatementSpec bind(JdbcClient.StatementSpec statement, Scope scope) {
        if (!scope.libraryIds().isEmpty()) statement = statement.param("libraryIds", scope.libraryIds());
        if (!scope.documentIds().isEmpty()) statement = statement.param("documentIds", scope.documentIds());
        return statement;
    }

    @Override public Optional<Job> job(Scope scope, long jobId) {
        return bind(jdbc.sql("""
                SELECT j.id,j.document_id,j.stage,j.status,d.status AS document_status
                FROM ingestion_jobs j JOIN documents d ON d.id=j.document_id
                WHERE j.id=:jobId
                """ + documentFilter(scope, "d")), scope).param("jobId", jobId)
                .query((rs, i) -> new Job(rs.getLong(1), rs.getLong(2), rs.getString(3),
                        rs.getString(4), rs.getString(5))).optional();
    }

    private static String table(String kind) {
        return switch (kind) {
            case "entity" -> "entity_candidates";
            case "relation" -> "relation_candidates";
            default -> throw new IllegalArgumentException("INVALID_CANDIDATE_KIND");
        };
    }

    private static String projection(String kind) {
        return kind.equals("entity")
                ? "c.name,c.temp_key,NULL AS source_temp_key,NULL AS target_temp_key,NULL AS relation_type,c.matched_node_id,NULL AS matched_edge_id"
                : "NULL AS name,NULL AS temp_key,c.source_temp_key,c.target_temp_key,c.relation_type,NULL AS matched_node_id,c.matched_edge_id";
    }

    private String candidateSql(Scope scope, String kind, boolean withId, boolean count) {
        return "SELECT " + (count ? "COUNT(*)" : "c.id,c.job_id," + projection(kind)
                + ",c.review_status,c.evidence_chunk_ids_json,c.edited_payload_json")
                + " FROM " + table(kind) + " c JOIN ingestion_jobs j ON j.id=c.job_id"
                + " JOIN documents d ON d.id=j.document_id WHERE c.job_id=:jobId"
                + (withId ? " AND c.id=:candidateId" : "") + documentFilter(scope, "d");
    }

    @Override public Page<Candidate> candidates(Scope scope, long jobId, String kind, int page, int pageSize) {
        String countSql = candidateSql(scope, kind, false, true);
        long total = bind(jdbc.sql(countSql), scope).param("jobId", jobId).query(Long.class).single();
        List<Candidate> items = bind(jdbc.sql(candidateSql(scope, kind, false, false)
                + " ORDER BY c.id LIMIT :limit OFFSET :offset"), scope).param("jobId", jobId)
                .param("limit", pageSize).param("offset", (long) (page - 1) * pageSize)
                .query((rs, i) -> candidate(rs, kind)).list();
        return new Page<>(items, total, page, pageSize);
    }

    @Override public Optional<Candidate> candidate(Scope scope, long jobId, String kind, long candidateId) {
        return bind(jdbc.sql(candidateSql(scope, kind, true, false)), scope).param("jobId", jobId)
                .param("candidateId", candidateId).query((rs, i) -> candidate(rs, kind)).optional();
    }

    @Override public Optional<Candidate> entityByTempKey(Scope scope, long jobId, String tempKey) {
        return bind(jdbc.sql(candidateSql(scope, "entity", false, false) + " AND c.temp_key=:tempKey"), scope)
                .param("jobId", jobId).param("tempKey", tempKey)
                .query((rs, i) -> candidate(rs, "entity")).optional();
    }

    private Candidate candidate(ResultSet rs, String kind) throws SQLException {
        String raw = rs.getString("evidence_chunk_ids_json");
        List<Long> ids = new ArrayList<>();
        try {
            JsonNode evidence = raw == null ? null : mapper.readTree(raw);
            if (evidence != null && evidence.isArray()) for (JsonNode id : evidence) {
                if (id.isIntegralNumber() && id.canConvertToLong() && id.longValue() > 0) ids.add(id.longValue());
            }
        } catch (Exception ignored) { /* malformed source IDs remain unavailable */ }
        AiDecision decision = null;
        JsonNode edited = null;
        try {
            edited = mapper.readTree(rs.getString("edited_payload_json"));
            JsonNode audit = edited == null ? null : edited.path("aiReview");
            if (audit != null && audit.isObject() && audit.path("approved").isBoolean()) {
                decision = new AiDecision(audit.path("approved").booleanValue(), audit.path("reason").asText(),
                        audit.path("model").asText(), audit.path("reviewedAt").asText());
            }
        } catch (Exception ignored) { /* no trustworthy AI decision */ }
        String reviewStatus = rs.getString("review_status");
        boolean useEdit = "EDITED".equals(reviewStatus) && edited != null && edited.isObject();
        String name = useEdit ? edited.path("name").asText(rs.getString("name")) : rs.getString("name");
        String source = useEdit ? edited.path("sourceTempKey").asText(rs.getString("source_temp_key"))
                : rs.getString("source_temp_key");
        String target = useEdit ? edited.path("targetTempKey").asText(rs.getString("target_temp_key"))
                : rs.getString("target_temp_key");
        String relation = useEdit ? edited.path("relationType").asText(rs.getString("relation_type"))
                : rs.getString("relation_type");
        return new Candidate(rs.getLong("id"), rs.getLong("job_id"), kind, name,
                rs.getString("temp_key"), source, target, relation, reviewStatus, List.copyOf(ids),
                nullableLong(rs, "matched_node_id"), nullableLong(rs, "matched_edge_id"), decision);
    }

    private static Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    @Override public Optional<Long> chunkDocumentId(long chunkId) {
        return jdbc.sql("""
                SELECT u.document_id FROM document_chunks c JOIN document_units u ON u.id=c.unit_id
                WHERE c.id=:chunkId
                """).param("chunkId", chunkId).query(Long.class).optional();
    }

    @Override public Optional<String> chunkHash(Scope scope, long documentId, long chunkId) {
        return bind(jdbc.sql("""
                SELECT c.content_hash FROM document_chunks c
                JOIN document_units u ON u.id=c.unit_id JOIN documents d ON d.id=u.document_id
                WHERE c.id=:chunkId AND u.document_id=:documentId
                """ + documentFilter(scope, "d")), scope).param("chunkId", chunkId)
                .param("documentId", documentId).query(String.class).optional();
    }

    @Override public Optional<UnitMeta> unitMeta(Scope scope, long documentId, long unitId) {
        return bind(jdbc.sql("""
                SELECT u.document_id,u.id,u.unit_index,u.source_locator
                FROM document_units u JOIN documents d ON d.id=u.document_id
                WHERE u.id=:unitId AND u.document_id=:documentId
                """ + documentFilter(scope, "d")), scope).param("unitId", unitId)
                .param("documentId", documentId).query((rs, i) -> new UnitMeta(rs.getLong(1),
                        rs.getLong(2), rs.getInt(3), rs.getString(4))).optional();
    }

    private static String visibleNode(Scope scope, String node) {
        String where = "";
        if (!scope.libraryIds().isEmpty()) where += " AND EXISTS (SELECT 1 FROM library_nodes ln WHERE ln.node_id="
                + node + ".id AND ln.library_id IN (:libraryIds))";
        if (!scope.documentIds().isEmpty()) where += " AND EXISTS (SELECT 1 FROM node_evidence ne"
                + " JOIN document_chunks c ON c.id=ne.chunk_id JOIN document_units u ON u.id=c.unit_id"
                + " WHERE ne.node_id=" + node + ".id AND u.document_id IN (:documentIds))";
        return where;
    }

    @Override public Optional<NodeMatch> nodeMatch(Scope scope, long nodeId) {
        reviewScope(scope);
        return bind(jdbc.sql("SELECT n.id,n.canonical_name,n.status FROM knowledge_nodes n WHERE n.id=:nodeId"
                + visibleNode(scope, "n")), scope).param("nodeId", nodeId)
                .query((rs, i) -> new NodeMatch(rs.getLong(1), rs.getString(2), rs.getString(3))).optional();
    }

    @Override public Optional<EdgeMatch> edgeMatch(Scope scope, long edgeId) {
        reviewScope(scope);
        String docFilter = scope.documentIds().isEmpty() ? "" : " AND EXISTS (SELECT 1 FROM edge_evidence ee"
                + " JOIN document_chunks c ON c.id=ee.chunk_id JOIN document_units u ON u.id=c.unit_id"
                + " WHERE ee.edge_id=e.id AND u.document_id IN (:documentIds))";
        return bind(jdbc.sql("""
                SELECT e.id,e.source_node_id,e.target_node_id,e.relation_type,e.status
                FROM knowledge_edges e JOIN knowledge_nodes s ON s.id=e.source_node_id
                JOIN knowledge_nodes t ON t.id=e.target_node_id WHERE e.id=:edgeId
                """ + visibleNode(scope, "s") + visibleNode(scope, "t") + docFilter), scope)
                .param("edgeId", edgeId).query((rs, i) -> new EdgeMatch(rs.getLong(1), rs.getLong(2),
                        rs.getLong(3), rs.getString(4), rs.getString(5))).optional();
    }

    private static String nodeCte(Scope scope) {
        return "WITH visible_nodes AS (SELECT n.id,n.canonical_name,n.node_type FROM knowledge_nodes n WHERE 1=1"
                + visibleNode(scope, "n") + "), visible_edges AS (SELECT e.id,e.source_node_id,e.target_node_id"
                + " FROM knowledge_edges e JOIN visible_nodes s ON s.id=e.source_node_id"
                + " JOIN visible_nodes t ON t.id=e.target_node_id WHERE e.status='active') ";
    }

    private long scopedCount(Scope scope, String sql) {
        return bind(jdbc.sql(sql), scope).query(Long.class).single();
    }

    @Override public Insight insight(Scope scope, int isolatedLimit, int topLimit) {
        insightScope(scope);
        String cte = nodeCte(scope);
        long nodes = scopedCount(scope, cte + "SELECT COUNT(*) FROM visible_nodes");
        long edges = scopedCount(scope, cte + "SELECT COUNT(*) FROM visible_edges");
        long documents = scopedCount(scope, "SELECT COUNT(*) FROM documents d WHERE 1=1" + documentFilter(scope, "d"));
        String pendingBase = " FROM %s c JOIN ingestion_jobs j ON j.id=c.job_id"
                + " JOIN documents d ON d.id=j.document_id WHERE c.review_status='PENDING'"
                + " AND j.status IN ('AWAITING_REVIEW','AI_REVIEWING','FAILED','CANCELLED')"
                + documentFilter(scope, "d");
        long pending = scopedCount(scope, "SELECT COUNT(*)" + pendingBase.formatted("entity_candidates"))
                + scopedCount(scope, "SELECT COUNT(*)" + pendingBase.formatted("relation_candidates"));
        long typeTotal = scopedCount(scope, cte + "SELECT COUNT(*) FROM"
                + " (SELECT node_type FROM visible_nodes GROUP BY node_type) grouped_types");
        List<TypeCount> types = bind(jdbc.sql(cte + "SELECT node_type,COUNT(*) FROM visible_nodes"
                + " GROUP BY node_type ORDER BY COUNT(*) DESC,node_type LIMIT :limit"), scope)
                .param("limit", 50)
                .query((rs, i) -> new TypeCount(rs.getString(1), rs.getLong(2))).list();
        // Aggregate scoped edge endpoints before ranking; a self-loop contributes only its source.
        String degrees = ", incident_endpoints AS (SELECT source_node_id AS node_id FROM visible_edges"
                + " UNION ALL SELECT target_node_id AS node_id FROM visible_edges"
                + " WHERE target_node_id<>source_node_id), node_degrees AS (SELECT node_id,COUNT(*) AS degree"
                + " FROM incident_endpoints GROUP BY node_id) ";
        List<NodeItem> top = bind(jdbc.sql(cte + degrees
                + "SELECT n.id,n.canonical_name,n.node_type,COALESCE(d.degree,0) AS degree"
                + " FROM visible_nodes n LEFT JOIN node_degrees d ON d.node_id=n.id"
                + " ORDER BY degree DESC,n.id LIMIT :limit"), scope)
                .param("limit", topLimit).query((rs, i) -> new NodeItem(rs.getLong(1), rs.getString(2),
                        rs.getString(3), rs.getLong(4))).list();
        String incidentNodes = ", incident_nodes AS (SELECT source_node_id AS node_id FROM visible_edges"
                + " UNION SELECT target_node_id AS node_id FROM visible_edges) ";
        String isolatedCount = incidentNodes
                + "SELECT COUNT(*) FROM visible_nodes n LEFT JOIN incident_nodes i ON i.node_id=n.id"
                + " WHERE i.node_id IS NULL";
        long isolatedTotal = scopedCount(scope, cte + isolatedCount);
        List<NodeItem> isolated = bind(jdbc.sql(cte + incidentNodes
                + "SELECT n.id,n.canonical_name,n.node_type,0"
                + " FROM visible_nodes n LEFT JOIN incident_nodes i ON i.node_id=n.id"
                + " WHERE i.node_id IS NULL ORDER BY n.id LIMIT :limit"), scope).param("limit", isolatedLimit)
                .query((rs, i) -> new NodeItem(rs.getLong(1), rs.getString(2), rs.getString(3),
                        rs.getLong(4))).list();
        return new Insight(nodes, edges, documents, pending, types, typeTotal, top, isolatedTotal, isolated);
    }
}
