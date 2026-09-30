package com.knowledgegraph.assistant.ingestion;

import com.knowledgegraph.assistant.contract.AssistantContracts.Scope;
import com.knowledgegraph.assistant.contract.AssistantContracts.ScopeMode;
import com.knowledgegraph.settings.SettingsService;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** SQL adapter: every selected dimension is applied before paging; all values are bound parameters. */
public final class JdbcIngestionReadRepository implements IngestionReadRepository {
    private final JdbcClient jdbc;
    private final SettingsService settings;

    public JdbcIngestionReadRepository(JdbcClient jdbc, SettingsService settings) {
        this.jdbc = jdbc;
        this.settings = settings;
    }

    private record Filter(String sql, Scope scope) {
        JdbcClient.StatementSpec bind(JdbcClient.StatementSpec query) {
            if (scope.mode() == ScopeMode.SELECTED && !scope.libraryIds().isEmpty())
                query = query.param("libraryIds", scope.libraryIds());
            if (scope.mode() == ScopeMode.SELECTED && !scope.documentIds().isEmpty())
                query = query.param("documentIds", scope.documentIds());
            return query;
        }
    }

    private static Filter filter(Scope scope, String object) {
        if (!scope.nodeIds().isEmpty()) throw new IllegalArgumentException("NODE_SCOPE_UNSUPPORTED");
        StringBuilder sql = new StringBuilder();
        if (!scope.libraryIds().isEmpty()) {
            if (object.equals("library")) sql.append(" AND l.id IN (:libraryIds)");
            else sql.append(" AND (d.library_id IN (:libraryIds) OR EXISTS (SELECT 1 FROM document_topic_assignments a WHERE a.document_id=d.id AND a.library_id IN (:libraryIds)))");
        }
        if (!scope.documentIds().isEmpty()) {
            if (object.equals("library")) sql.append(" AND EXISTS (SELECT 1 FROM documents d WHERE d.id IN (:documentIds) AND (d.library_id=l.id OR EXISTS (SELECT 1 FROM document_topic_assignments a WHERE a.document_id=d.id AND a.library_id=l.id)))");
            else sql.append(" AND d.id IN (:documentIds)");
        }
        return new Filter(sql.toString(), scope);
    }

    @Override public Page<Library> libraries(Scope scope, int page, int size) {
        Filter f = filter(scope, "library");
        long total = f.bind(jdbc.sql("SELECT COUNT(*) FROM knowledge_libraries l WHERE l.status <> 'archived'" + f.sql()))
                .query(Long.class).single();
        List<Long> ids = f.bind(jdbc.sql("SELECT l.id FROM knowledge_libraries l WHERE l.status <> 'archived'" + f.sql()
                        + " ORDER BY l.id LIMIT :limit OFFSET :offset"))
                .param("limit", size).param("offset", (long) (page - 1) * size).query(Long.class).list();
        return new Page<>(ids.stream().map(id -> library(id).orElseThrow()).toList(), total, page, size);
    }

    @Override public Optional<Library> library(long id) {
        return jdbc.sql("""
                SELECT l.id,l.name,l.type,l.description,l.status,l.updated_at,
                  (SELECT COUNT(*) FROM documents d WHERE d.library_id=l.id OR EXISTS
                     (SELECT 1 FROM document_topic_assignments a WHERE a.document_id=d.id AND a.library_id=l.id)) AS document_count,
                  (SELECT COUNT(*) FROM library_nodes n WHERE n.library_id=l.id) AS node_count,
                  (SELECT COUNT(*) FROM knowledge_edges e WHERE e.status='active' AND EXISTS
                     (SELECT 1 FROM library_nodes n WHERE n.library_id=l.id AND n.node_id=e.source_node_id) AND EXISTS
                     (SELECT 1 FROM library_nodes n WHERE n.library_id=l.id AND n.node_id=e.target_node_id)) AS edge_count
                FROM knowledge_libraries l WHERE l.id=:id
                """).param("id", id).query((rs, i) -> new Library(rs.getLong("id"), rs.getString("name"),
                rs.getString("type"), rs.getString("description"), rs.getString("status"), date(rs, "updated_at"),
                rs.getLong("document_count"), rs.getLong("node_count"), rs.getLong("edge_count"),
                jdbc.sql("SELECT alias FROM library_aliases WHERE library_id=:id ORDER BY id")
                        .param("id", id).query(String.class).list())).optional();
    }

    @Override public Page<Document> documents(Scope scope, String status, int page, int size) {
        Filter f = filter(scope, "document");
        String where = " WHERE 1=1" + f.sql() + (status == null ? "" : " AND d.status=:status");
        JdbcClient.StatementSpec count = f.bind(jdbc.sql("SELECT COUNT(*) FROM documents d" + where));
        JdbcClient.StatementSpec ids = f.bind(jdbc.sql("SELECT d.id FROM documents d" + where
                + " ORDER BY d.id DESC LIMIT :limit OFFSET :offset"));
        if (status != null) { count = count.param("status", status); ids = ids.param("status", status); }
        long total = count.query(Long.class).single();
        List<Long> pageIds = ids.param("limit", size).param("offset", (long) (page - 1) * size).query(Long.class).list();
        return new Page<>(pageIds.stream().map(id -> document(id).orElseThrow()).toList(), total, page, size);
    }

    @Override public Optional<Document> document(long id) {
        return jdbc.sql("""
                SELECT d.id,d.library_id,d.original_name,d.status,d.extension,d.size_bytes,d.sha256,d.storage_path,
                       GREATEST(d.updated_at,COALESCE(m.updated_at,d.updated_at)) AS updated_at,
                       COALESCE(m.title,d.original_name) AS title,
                       COALESCE(m.lifecycle_status,'active') AS lifecycle_status
                FROM documents d LEFT JOIN document_metadata m ON m.document_id=d.id WHERE d.id=:id
                """).param("id", id).query((rs, i) -> new Document(rs.getLong("id"), rs.getLong("library_id"),
                rs.getString("original_name"), rs.getString("title"), rs.getString("status"),
                rs.getString("lifecycle_status"), rs.getString("extension"), rs.getLong("size_bytes"),
                rs.getString("sha256"), date(rs, "updated_at"),
                jdbc.sql("SELECT tag FROM document_tags WHERE document_id=:id ORDER BY id")
                        .param("id", id).query(String.class).list(), present(rs.getString("storage_path")))).optional();
    }

    @Override public Set<Long> documentLibraries(long documentId) {
        return Set.copyOf(jdbc.sql("""
                SELECT library_id FROM documents WHERE id=:id
                UNION SELECT library_id FROM document_topic_assignments WHERE document_id=:id
                """).param("id", documentId).query(Long.class).list());
    }

    private static final String JOB_SELECT = """
            SELECT j.id,j.document_id,d.original_name AS document_name,j.stage,j.status,j.progress,
                   j.processed_units,j.total_units,j.error_code,j.error_message,j.retry_count,j.created_at,j.finished_at,
                   (SELECT COUNT(*) FROM entity_candidates e WHERE e.job_id=j.id)
                     +(SELECT COUNT(*) FROM relation_candidates r WHERE r.job_id=j.id) AS candidate_count,
                   (SELECT COUNT(*) FROM extraction_batch_results b WHERE b.job_id=j.id) AS staged_batches,
                   EXISTS(SELECT 1 FROM entity_candidates e WHERE e.job_id=j.id) AS reviewable,
                   (SELECT COUNT(*) FROM document_chunks c JOIN document_units u ON u.id=c.unit_id WHERE u.document_id=d.id)>0
                     AND (SELECT COUNT(*) FROM entity_candidates e WHERE e.job_id=j.id)
                       +(SELECT COUNT(*) FROM relation_candidates r WHERE r.job_id=j.id)=0
                     AND j.status IN ('COMPLETED','FAILED') AS extractable,
                   NULLIF(JSON_UNQUOTE(JSON_EXTRACT(m.verification_json,'$.ingestion.warning')),'') AS source_warning
            FROM ingestion_jobs j JOIN documents d ON d.id=j.document_id
            LEFT JOIN document_metadata m ON m.document_id=d.id
            """;

    @Override public Page<Job> jobs(Scope scope, String status, int page, int size) {
        Filter f = filter(scope, "job");
        String where = " WHERE 1=1" + f.sql() + (status == null ? "" : " AND j.status=:status");
        JdbcClient.StatementSpec count = f.bind(jdbc.sql("SELECT COUNT(*) FROM ingestion_jobs j JOIN documents d ON d.id=j.document_id" + where));
        JdbcClient.StatementSpec rows = f.bind(jdbc.sql(JOB_SELECT + where + " ORDER BY j.id DESC LIMIT :limit OFFSET :offset"));
        if (status != null) { count = count.param("status", status); rows = rows.param("status", status); }
        long total = count.query(Long.class).single();
        return new Page<>(rows.param("limit", size).param("offset", (long) (page - 1) * size)
                .query(this::jobRow).list(), total, page, size);
    }

    @Override public Optional<Job> job(long id) {
        return jdbc.sql(JOB_SELECT + " WHERE j.id=:id").param("id", id).query(this::jobRow).optional();
    }

    @Override public boolean hasChunks(long documentId) {
        return jdbc.sql("""
                SELECT EXISTS(SELECT 1 FROM document_chunks c JOIN document_units u ON u.id=c.unit_id
                              WHERE u.document_id=:id)
                """).param("id", documentId).query(Boolean.class).single();
    }

    @Override public boolean hasOcrGaps(long documentId) {
        return jdbc.sql("""
                SELECT EXISTS(SELECT 1 FROM document_units u WHERE u.document_id=:id
                              AND u.status IN ('NEEDS_OCR','OCR_SKIPPED','OCR_FAILED'))
                """).param("id", documentId).query(Boolean.class).single();
    }

    private Job jobRow(ResultSet rs, int index) throws SQLException {
        return new Job(rs.getLong("id"), rs.getLong("document_id"), rs.getString("document_name"),
                rs.getString("stage"), rs.getString("status"), rs.getInt("progress"),
                rs.getInt("processed_units"), rs.getInt("total_units"), rs.getString("error_code"),
                rs.getString("error_message"), rs.getInt("retry_count"), date(rs, "created_at"),
                date(rs, "finished_at"), rs.getLong("candidate_count"), rs.getInt("staged_batches"),
                rs.getBoolean("extractable"), rs.getBoolean("reviewable"), rs.getString("source_warning"));
    }

    @Override public Page<Destination> destinations(Scope scope, long documentId) {
        if (!scope.nodeIds().isEmpty()) throw new IllegalArgumentException("NODE_SCOPE_UNSUPPORTED");
        String from = " FROM document_topic_assignments a JOIN knowledge_libraries l ON l.id=a.library_id"
                + " JOIN documents d ON d.id=a.document_id WHERE a.document_id=:id"
                + (scope.libraryIds().isEmpty() ? "" : " AND l.id IN (:libraryIds)")
                + (scope.documentIds().isEmpty() ? "" : " AND d.id IN (:documentIds)");
        JdbcClient.StatementSpec count = jdbc.sql("SELECT COUNT(DISTINCT l.id)" + from).param("id", documentId);
        JdbcClient.StatementSpec rows = jdbc.sql("SELECT l.id,l.name,COUNT(DISTINCT a.temp_key) AS assigned_keys"
                + from + " GROUP BY l.id,l.name ORDER BY l.id LIMIT 100").param("id", documentId);
        if (!scope.libraryIds().isEmpty()) {
            count = count.param("libraryIds", scope.libraryIds());
            rows = rows.param("libraryIds", scope.libraryIds());
        }
        if (!scope.documentIds().isEmpty()) {
            count = count.param("documentIds", scope.documentIds());
            rows = rows.param("documentIds", scope.documentIds());
        }
        long total = count.query(Long.class).single();
        return new Page<>(rows.query((rs, i) -> new Destination(rs.getLong(1), rs.getString(2), rs.getInt(3))).list(),
                total, 1, 100);
    }

    @Override public int maxUploadSizeMb() { return settings.maxUploadSizeMb(); }

    @Override public boolean visionConfigured() {
        return jdbc.sql("SELECT EXISTS(SELECT 1 FROM llm_profiles WHERE enabled=1 AND vision=1 AND encrypted_api_key IS NOT NULL)")
                .query(Boolean.class).single();
    }

    private static LocalDateTime date(ResultSet rs, String column) throws SQLException {
        var value = rs.getTimestamp(column);
        return value == null ? null : value.toLocalDateTime();
    }

    private static boolean present(String trustedPath) {
        if (trustedPath == null || trustedPath.isBlank()) return false;
        try { return Files.isRegularFile(Path.of(trustedPath)); }
        catch (RuntimeException ignored) { return false; }
    }
}
