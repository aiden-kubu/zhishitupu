package com.knowledgegraph.assistant.source;

import com.knowledgegraph.assistant.contract.AssistantContracts.Scope;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.util.List;
import java.util.Optional;

/** Parameterized source queries repeat host-supplied scope constraints in the final SQL. */
public final class JdbcSourceLocator implements SourceLocator {
    private final JdbcClient jdbc;
    public JdbcSourceLocator(JdbcClient jdbc) { this.jdbc = jdbc; }

    @Override public Optional<Unit> unit(Scope scope, long documentId, int unitIndex, int maxChunks, int maxCharsPerChunk) {
        if (documentId <= 0 || unitIndex <= 0 || maxChunks < 1 || maxChunks > 100
                || maxCharsPerChunk < 1 || maxCharsPerChunk > 4000) throw new IllegalArgumentException("INVALID_SOURCE_LIMIT");
        record Header(long id, String type, String locator, String status) {}
        Optional<Header> header = bind(jdbc.sql("""
                SELECT u.id,u.unit_type,u.source_locator,u.status FROM document_units u
                JOIN documents d ON d.id=u.document_id
                WHERE u.document_id=:documentId AND u.unit_index=:unitIndex
                """ + scopeSql(scope)), scope).param("documentId", documentId).param("unitIndex", unitIndex)
                .query((rs, i) -> new Header(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4))).optional();
        if (header.isEmpty()) return Optional.empty();
        Header h = header.get();
        int total = bind(jdbc.sql("""
                SELECT COUNT(*) FROM document_chunks c JOIN document_units u ON u.id=c.unit_id
                JOIN documents d ON d.id=u.document_id WHERE u.id=:unitId AND d.id=:documentId
                """ + scopeSql(scope)), scope).param("unitId", h.id()).param("documentId", documentId)
                .query(Integer.class).single();
        List<Chunk> chunks = bind(jdbc.sql("""
                SELECT c.id,c.unit_id,c.chunk_index,c.content_hash,c.start_offset,c.end_offset,c.content
                FROM document_chunks c JOIN document_units u ON u.id=c.unit_id
                JOIN documents d ON d.id=u.document_id WHERE u.id=:unitId AND d.id=:documentId
                """ + scopeSql(scope) + " ORDER BY c.chunk_index,c.id LIMIT :limit"), scope)
                .param("unitId", h.id()).param("documentId", documentId).param("limit", maxChunks)
                .query((rs, i) -> view(rs.getLong(1), rs.getLong(2), rs.getInt(3), rs.getString(4), rs.getInt(5),
                        rs.getInt(6), rs.getString(7), maxCharsPerChunk)).list();
        return Optional.of(new Unit(documentId, h.id(), unitIndex, h.type(), h.locator(), h.status(),
                chunks, total, total > chunks.size()));
    }

    @Override public Optional<Chunk> chunk(Scope scope, long documentId, long chunkId, int maxChars) {
        if (documentId <= 0 || chunkId <= 0 || maxChars < 1 || maxChars > 4000)
            throw new IllegalArgumentException("INVALID_SOURCE_LIMIT");
        return bind(jdbc.sql("""
                SELECT c.id,c.unit_id,c.chunk_index,c.content_hash,c.start_offset,c.end_offset,c.content
                FROM document_chunks c JOIN document_units u ON u.id=c.unit_id
                JOIN documents d ON d.id=u.document_id
                WHERE u.document_id=:documentId AND c.id=:chunkId
                """ + scopeSql(scope)), scope).param("documentId", documentId).param("chunkId", chunkId)
                .query((rs, i) -> view(rs.getLong(1), rs.getLong(2), rs.getInt(3), rs.getString(4), rs.getInt(5),
                        rs.getInt(6), rs.getString(7), maxChars)).optional();
    }

    private static Chunk view(long id, long unitId, int index, String hash, int start, int end, String content, int maxChars) {
        String value = content == null ? "" : content;
        return new Chunk(id, unitId, index, hash, start, end,
                value.substring(0, Math.min(value.length(), maxChars)), value.length() > maxChars);
    }

    private static String scopeSql(Scope scope) {
        if (!scope.nodeIds().isEmpty()) throw new IllegalArgumentException("NODE_SCOPE_UNSUPPORTED");
        String sql = "";
        if (!scope.libraryIds().isEmpty()) sql += " AND (d.library_id IN (:libraryIds) OR EXISTS "
                + "(SELECT 1 FROM document_topic_assignments a WHERE a.document_id=d.id AND a.library_id IN (:libraryIds)))";
        if (!scope.documentIds().isEmpty()) sql += " AND d.id IN (:documentIds)";
        return sql;
    }

    private static JdbcClient.StatementSpec bind(JdbcClient.StatementSpec statement, Scope scope) {
        if (!scope.libraryIds().isEmpty()) statement = statement.param("libraryIds", scope.libraryIds());
        if (!scope.documentIds().isEmpty()) statement = statement.param("documentIds", scope.documentIds());
        return statement;
    }
}
