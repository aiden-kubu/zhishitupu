package com.knowledgegraph.assistant.research;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowledgegraph.assistant.contract.AssistantContracts.Scope;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Query scopes are composed from fixed SQL fragments; user text is always bound. */
public final class JdbcResearchReadRepository implements ResearchReadRepository {
    private final JdbcClient jdbc;
    private final ObjectMapper mapper = new ObjectMapper();

    public JdbcResearchReadRepository(JdbcClient jdbc) { this.jdbc = jdbc; }

    @Override public Window<Node> searchNodes(Scope scope, String query, int limit) {
        String sql = """
                SELECT n.id FROM knowledge_nodes n WHERE
                (LOWER(n.canonical_name)=:q OR LOWER(COALESCE(n.name_en,''))=:q
                 OR LOWER(n.canonical_name) LIKE :contains OR LOWER(COALESCE(n.name_en,'')) LIKE :contains
                 OR LOWER(COALESCE(n.definition,'')) LIKE :contains
                 OR EXISTS (SELECT 1 FROM node_aliases a WHERE a.node_id=n.id AND a.normalized_alias LIKE :contains))
                """ + nodeScope("n", scope) + """
                 ORDER BY CASE WHEN LOWER(n.canonical_name)=:q OR LOWER(COALESCE(n.name_en,''))=:q THEN 1
                               WHEN LOWER(n.canonical_name) LIKE :prefix OR LOWER(COALESCE(n.name_en,'')) LIKE :prefix THEN 2
                               WHEN EXISTS (SELECT 1 FROM node_aliases a WHERE a.node_id=n.id AND a.normalized_alias LIKE :contains) THEN 3
                               WHEN LOWER(n.canonical_name) LIKE :contains OR LOWER(COALESCE(n.name_en,'')) LIKE :contains THEN 4
                               ELSE 5 END, n.id LIMIT :maxRows
                """;
        List<Long> ids = bind(jdbc.sql(sql), scope).param("q", query.toLowerCase())
                .param("prefix", query.toLowerCase() + "%").param("contains", "%" + query.toLowerCase() + "%")
                .param("maxRows", limit + 1).query(Long.class).list();
        List<Node> nodes = new ArrayList<>();
        for (long id : ids.subList(0, Math.min(ids.size(), limit))) {
            node(scope, id).ifPresent(nodes::add);
        }
        return new Window<>(nodes, ids.size() > limit);
    }

    @Override public Optional<Node> node(Scope scope, long nodeId) {
        String sql = """
                SELECT n.id,n.canonical_name,n.name_en,n.node_type,n.definition,n.status,n.updated_at,n.properties_json,
                EXISTS(SELECT 1 FROM node_evidence ne JOIN document_chunks nc ON nc.id=ne.chunk_id
                       JOIN document_units nu ON nu.id=nc.unit_id JOIN documents nd ON nd.id=nu.document_id
                       LEFT JOIN document_metadata nm ON nm.document_id=nd.id
                       WHERE ne.node_id=n.id AND nd.status='COMPLETED' AND nu.status IN ('READY','OCR_OK')
                         AND (nm.lifecycle_status IS NULL OR nm.lifecycle_status='active')
                """ + documentScope("nd", scope) + ") AS has_source FROM knowledge_nodes n WHERE n.id=:nodeId"
                + nodeScope("n", scope);
        Optional<Node> base = bind(jdbc.sql(sql), scope).param("nodeId", nodeId)
                .query((rs, i) -> mapNode(rs)).optional();
        if (base.isEmpty()) return Optional.empty();
        Node n = base.get();
        String libsSql = "SELECT ln.library_id FROM library_nodes ln WHERE ln.node_id=:nodeId";
        if (!scope.libraryIds().isEmpty()) libsSql += " AND ln.library_id IN (:libraryIds)";
        // A document-only scope does not authorize disclosure of every other library membership.
        List<Long> libs = scope.libraryIds().isEmpty() && !scope.documentIds().isEmpty() ? List.of()
                : bind(jdbc.sql(libsSql + " ORDER BY ln.library_id LIMIT 100"), scope)
                    .param("nodeId", nodeId).query(Long.class).list();
        List<String> aliases = jdbc.sql("SELECT alias FROM node_aliases WHERE node_id=:nodeId ORDER BY id LIMIT 20")
                .param("nodeId", nodeId).query(String.class).list();
        return Optional.of(new Node(n.id(), n.name(), n.nameEn(), n.type(), n.definition(), n.status(),
                n.updatedAt(), n.origin(), n.reviewStatus(), libs, aliases, null, n.sourceEvidenceAvailable()));
    }

    @Override public Window<Edge> incidentEdges(Scope scope, long nodeId, int limit) {
        return edges(scope, "(e.source_node_id=:nodeId OR e.target_node_id=:nodeId)", nodeId, limit);
    }

    @Override public Window<Edge> outgoingEdges(Scope scope, long nodeId, int limit) {
        return edges(scope, "e.source_node_id=:nodeId", nodeId, limit);
    }

    private Window<Edge> edges(Scope scope, String direction, long nodeId, int limit) {
        String projection = """
                SELECT e.id,e.source_node_id,e.target_node_id,e.relation_type,e.status,e.updated_at,e.properties_json,
                EXISTS(SELECT 1 FROM edge_evidence ee JOIN document_chunks ec ON ec.id=ee.chunk_id
                       JOIN document_units eu ON eu.id=ec.unit_id JOIN documents ed ON ed.id=eu.document_id
                       LEFT JOIN document_metadata em ON em.document_id=ed.id
                       WHERE ee.edge_id=e.id AND ed.status='COMPLETED' AND eu.status IN ('READY','OCR_OK')
                         AND (em.lifecycle_status IS NULL OR em.lifecycle_status='active')
                """ + documentScope("ed", scope) + ") AS has_source FROM knowledge_edges e ";
        String joins = "JOIN knowledge_nodes s ON s.id=e.source_node_id JOIN knowledge_nodes t ON t.id=e.target_node_id ";
        String sql;
        if (scope.documentIds().isEmpty()) {
            sql = projection + joins + "WHERE " + direction + nodeScope("s", scope) + nodeScope("t", scope)
                    + edgeDocumentScope(scope) + " ORDER BY e.id LIMIT :maxRows";
        } else {
            // DISTINCT keeps one ID per qualifying entity and prevents the CTEs from merging into
            // the three correlated document-evidence predicates of the outer edge query.
            String scoped = "WITH scoped_nodes AS (SELECT DISTINCT n.id FROM knowledge_nodes n WHERE 1=1"
                    + nodeScope("n", scope) + "), scoped_edges AS (SELECT DISTINCT e.id FROM knowledge_edges e WHERE 1=1"
                    + edgeDocumentScope(scope) + ") ";
            sql = scoped + projection + joins
                    + "JOIN scoped_nodes sn ON sn.id=s.id JOIN scoped_nodes tn ON tn.id=t.id "
                    + "JOIN scoped_edges se ON se.id=e.id WHERE " + direction + " ORDER BY e.id LIMIT :maxRows";
        }
        List<Edge> rows = bind(jdbc.sql(sql), scope).param("nodeId", nodeId).param("maxRows", limit + 1)
                .query((rs, i) -> mapEdge(rs)).list();
        return window(rows, limit);
    }

    @Override public Window<Long> matchingDocuments(Scope scope, List<String> terms, int limit) {
        requireSourceScope(scope);
        String sql = """
                SELECT d.id FROM documents d JOIN document_units u ON u.document_id=d.id
                JOIN document_chunks c ON c.unit_id=u.id
                LEFT JOIN document_metadata m ON m.document_id=d.id
                WHERE d.status='COMPLETED' AND u.status IN ('READY','OCR_OK')
                  AND (m.lifecycle_status IS NULL OR m.lifecycle_status='active')
                """ + documentScope("d", scope) + " AND (" + termWhere(terms) + ")"
                + " GROUP BY d.id ORDER BY MIN(c.id),d.id LIMIT :maxRows";
        List<Long> rows = bind(bindTerms(jdbc.sql(sql), terms), scope).param("maxRows", limit + 1)
                .query(Long.class).list();
        return window(rows, limit);
    }

    @Override public Window<Candidate> candidates(Scope scope, long documentId, List<String> terms, int limit) {
        requireSourceScope(scope);
        String sql = """
                SELECT d.id,c.id,c.content_hash,
                """ + termScore(terms) + " AS match_score " + """
                FROM documents d JOIN document_units u ON u.document_id=d.id
                JOIN document_chunks c ON c.unit_id=u.id
                LEFT JOIN document_metadata m ON m.document_id=d.id
                WHERE d.id=:documentId AND d.status='COMPLETED' AND u.status IN ('READY','OCR_OK')
                  AND (m.lifecycle_status IS NULL OR m.lifecycle_status='active')
                """ + documentScope("d", scope) + " AND (" + termWhere(terms) + ")"
                + " ORDER BY match_score DESC,c.id LIMIT :maxRows";
        List<Candidate> rows = bind(bindTerms(jdbc.sql(sql), terms), scope)
                .param("documentId", documentId).param("maxRows", limit + 1)
                .query((rs, i) -> new Candidate(rs.getLong(1), rs.getLong(2), rs.getString(3), rs.getInt(4))).list();
        return window(rows, limit);
    }

    @Override public Optional<SourceMeta> sourceMeta(Scope scope, long documentId, long chunkId) {
        requireSourceScope(scope);
        String sql = """
                SELECT d.id,c.id,u.id,u.unit_index,u.unit_type,u.source_locator,c.content_hash,
                       d.status,m.lifecycle_status,u.status
                FROM document_chunks c JOIN document_units u ON u.id=c.unit_id
                JOIN documents d ON d.id=u.document_id
                LEFT JOIN document_metadata m ON m.document_id=d.id
                WHERE d.id=:documentId AND c.id=:chunkId
                """ + documentScope("d", scope);
        return bind(jdbc.sql(sql), scope).param("documentId", documentId).param("chunkId", chunkId)
                .query((rs, i) -> new SourceMeta(rs.getLong(1), rs.getLong(2), rs.getLong(3), rs.getInt(4),
                        rs.getString(5), rs.getString(6), rs.getString(7), rs.getString(8),
                        rs.getString(9), rs.getString(10))).optional();
    }

    @Override public boolean libraryExistsInScope(Scope scope, long libraryId) {
        if (!scope.nodeIds().isEmpty()) return false;
        if (!scope.libraryIds().isEmpty() && !scope.libraryIds().contains(libraryId)) return false;
        String sql = "SELECT EXISTS(SELECT 1 FROM knowledge_libraries k WHERE k.id=:libraryId";
        if (!scope.documentIds().isEmpty()) sql += " AND EXISTS (SELECT 1 FROM documents d WHERE d.id IN (:documentIds) "
                + "AND (d.library_id=k.id OR EXISTS (SELECT 1 FROM document_topic_assignments a "
                + "WHERE a.document_id=d.id AND a.library_id=k.id)))";
        return bind(jdbc.sql(sql + ")"), scope).param("libraryId", libraryId).query(Boolean.class).single();
    }

    private String nodeScope(String alias, Scope scope) {
        String sql = "";
        if (!scope.nodeIds().isEmpty()) sql += " AND " + alias + ".id IN (:nodeIds)";
        if (!scope.libraryIds().isEmpty()) sql += " AND EXISTS (SELECT 1 FROM library_nodes ln WHERE ln.node_id="
                + alias + ".id AND ln.library_id IN (:libraryIds))";
        if (!scope.documentIds().isEmpty()) sql += " AND EXISTS (SELECT 1 FROM node_evidence ne "
                + "JOIN document_chunks nc ON nc.id=ne.chunk_id JOIN document_units nu ON nu.id=nc.unit_id "
                + "JOIN documents nd ON nd.id=nu.document_id LEFT JOIN document_metadata nm ON nm.document_id=nd.id "
                + "WHERE ne.node_id=" + alias + ".id AND nd.id IN (:documentIds) AND nd.status='COMPLETED' "
                + "AND nu.status IN ('READY','OCR_OK') AND (nm.lifecycle_status IS NULL OR nm.lifecycle_status='active')"
                + documentScope("nd", scope) + ")";
        return sql;
    }

    private String edgeDocumentScope(Scope scope) {
        if (scope.documentIds().isEmpty()) return "";
        return " AND EXISTS (SELECT 1 FROM edge_evidence ee JOIN document_chunks ec ON ec.id=ee.chunk_id "
                + "JOIN document_units eu ON eu.id=ec.unit_id JOIN documents ed ON ed.id=eu.document_id "
                + "LEFT JOIN document_metadata em ON em.document_id=ed.id WHERE ee.edge_id=e.id "
                + "AND ed.id IN (:documentIds) AND ed.status='COMPLETED' AND eu.status IN ('READY','OCR_OK') "
                + "AND (em.lifecycle_status IS NULL OR em.lifecycle_status='active')"
                + documentScope("ed", scope) + ")";
    }

    private static String documentScope(String alias, Scope scope) {
        String sql = "";
        if (!scope.libraryIds().isEmpty()) sql += " AND (" + alias + ".library_id IN (:libraryIds) OR EXISTS "
                + "(SELECT 1 FROM document_topic_assignments a WHERE a.document_id=" + alias
                + ".id AND a.library_id IN (:libraryIds)))";
        if (!scope.documentIds().isEmpty()) sql += " AND " + alias + ".id IN (:documentIds)";
        return sql;
    }

    private static void requireSourceScope(Scope scope) {
        if (!scope.nodeIds().isEmpty()) throw new IllegalArgumentException("NODE_SCOPE_UNSUPPORTED");
    }

    private static String termWhere(List<String> terms) {
        List<String> parts = new ArrayList<>();
        for (int i = 0; i < terms.size(); i++) parts.add("LOCATE(:term" + i + ",LOWER(c.content))>0");
        return String.join(" OR ", parts);
    }
    private static String termScore(List<String> terms) {
        List<String> parts = new ArrayList<>();
        for (int i = 0; i < terms.size(); i++) parts.add("IF(LOCATE(:term" + i + ",LOWER(c.content))>0,1,0)");
        return String.join("+", parts);
    }
    private static JdbcClient.StatementSpec bindTerms(JdbcClient.StatementSpec spec, List<String> terms) {
        for (int i = 0; i < terms.size(); i++) spec = spec.param("term" + i, terms.get(i));
        return spec;
    }
    private static JdbcClient.StatementSpec bind(JdbcClient.StatementSpec spec, Scope scope) {
        if (!scope.libraryIds().isEmpty()) spec = spec.param("libraryIds", scope.libraryIds());
        if (!scope.documentIds().isEmpty()) spec = spec.param("documentIds", scope.documentIds());
        if (!scope.nodeIds().isEmpty()) spec = spec.param("nodeIds", scope.nodeIds());
        return spec;
    }
    private static <T> Window<T> window(List<T> rows, int limit) {
        return new Window<>(rows.subList(0, Math.min(rows.size(), limit)), rows.size() > limit);
    }
    private Node mapNode(ResultSet rs) throws SQLException {
        JsonNode properties = properties(rs.getString(8));
        return new Node(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5),
                rs.getString(6), rs.getTimestamp(7).toInstant().toString(), property(properties, "origin"),
                property(properties, "reviewStatus"), List.of(), List.of(), null, rs.getBoolean(9));
    }
    private Edge mapEdge(ResultSet rs) throws SQLException {
        JsonNode properties = properties(rs.getString(7));
        return new Edge(rs.getLong(1), rs.getLong(2), rs.getLong(3), rs.getString(4), rs.getString(5),
                rs.getTimestamp(6).toInstant().toString(), property(properties, "origin"),
                property(properties, "reviewStatus"), rs.getBoolean(8));
    }
    private JsonNode properties(String json) {
        try { return json == null ? mapper.createObjectNode() : mapper.readTree(json); }
        catch (Exception ignored) { return mapper.createObjectNode(); }
    }
    private static String property(JsonNode node, String name) {
        JsonNode value = node.get(name);
        return value != null && value.isTextual() ? value.textValue() : null;
    }
}
