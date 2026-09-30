package com.knowledgegraph.assistant.quality;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowledgegraph.assistant.contract.AssistantContracts.Scope;
import com.knowledgegraph.assistant.contract.AssistantContracts.ScopeMode;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** SQL-shape and parameter-binding checks only; these do not assert MySQL execution compatibility. */
class JdbcQualityReadRepositoryTest {
    private final JdbcClient jdbc = mock(JdbcClient.class);
    private final JdbcClient.StatementSpec statement = mock(JdbcClient.StatementSpec.class, RETURNS_SELF);
    @SuppressWarnings("unchecked") private final JdbcClient.MappedQuerySpec<Object> rows = mock(JdbcClient.MappedQuerySpec.class);
    @SuppressWarnings("unchecked") private final JdbcClient.MappedQuerySpec<Long> longs = mock(JdbcClient.MappedQuerySpec.class);
    @SuppressWarnings("unchecked") private final JdbcClient.MappedQuerySpec<String> strings = mock(JdbcClient.MappedQuerySpec.class);
    private final List<String> sql = new ArrayList<>();
    private final JdbcQualityReadRepository repository = new JdbcQualityReadRepository(jdbc, new ObjectMapper());

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void setup() {
        when(jdbc.sql(anyString())).thenAnswer(call -> {
            sql.add(call.getArgument(0));
            return statement;
        });
        when(statement.query(Long.class)).thenReturn(longs);
        when(longs.single()).thenReturn(0L);
        when(statement.query(String.class)).thenReturn(strings);
        when(statement.query(any(RowMapper.class))).thenReturn((JdbcClient.MappedQuerySpec) rows);
        when(rows.list()).thenReturn(List.of());
    }

    @Test void candidateScopeIsBoundBeforeBothCountAndPageLimit() {
        setup();
        Scope scope = new Scope(ScopeMode.SELECTED, Set.of(3L, 4L), Set.of(7L), Set.of());
        QualityReadRepository.Page<QualityReadRepository.Candidate> page = repository.candidates(scope, 2,
                "entity", 1, 20);
        assertEquals(0, page.total());
        assertEquals(2, sql.size());
        assertTrue(sql.get(0).contains("c.job_id=:jobId"));
        assertTrue(sql.get(0).contains("d.id IN (:documentIds)"));
        assertTrue(sql.get(0).contains("document_topic_assignments"));
        assertTrue(sql.get(1).indexOf("d.id IN (:documentIds)") < sql.get(1).indexOf("LIMIT :limit"));
        verify(statement, atLeastOnce()).param(eq("libraryIds"), eq(scope.libraryIds()));
        verify(statement, atLeastOnce()).param(eq("documentIds"), eq(scope.documentIds()));
        verify(statement).param("limit", 20);
        verify(statement).param("offset", 0L);
    }

    @Test void insightUsesDistinctVisibleNodesAndTwoVisibleEndpointsForActiveEdges() {
        setup();
        Scope scope = new Scope(ScopeMode.SELECTED, Set.of(3L, 4L), Set.of(), Set.of());
        QualityReadRepository.Insight result = repository.insight(scope, 2, 2);
        assertEquals(0, result.nodeCount());
        assertTrue(sql.stream().anyMatch(q -> q.contains("SELECT COUNT(*) FROM visible_nodes")));
        assertTrue(sql.stream().anyMatch(q -> q.contains("JOIN visible_nodes s ON s.id=e.source_node_id")
                && q.contains("JOIN visible_nodes t ON t.id=e.target_node_id")
                && q.contains("e.status='active'")));
        assertTrue(sql.stream().anyMatch(q -> q.contains("document_topic_assignments")
                && q.contains("SELECT COUNT(*) FROM documents")));
        assertTrue(sql.stream().anyMatch(q -> q.contains("c.review_status='PENDING'")
                && q.contains("j.status IN ('AWAITING_REVIEW','AI_REVIEWING','FAILED','CANCELLED')")));
        assertTrue(sql.stream().anyMatch(q -> q.contains("ORDER BY n.id LIMIT :limit")));
        assertTrue(sql.stream().filter(q -> q.contains("LIMIT :limit")).allMatch(q ->
                q.indexOf("ln.library_id IN (:libraryIds)") < q.indexOf("LIMIT :limit")));
        verify(statement, atLeastOnce()).param(eq("libraryIds"), eq(scope.libraryIds()));
        verify(statement, times(2)).param("limit", 2);
        verify(statement).param("limit", 50);
    }

    @Test void unsupportedInsightDimensionsAreRejectedBeforeAnySql() {
        Scope documents = new Scope(ScopeMode.SELECTED, Set.of(), Set.of(7L), Set.of());
        assertThrows(IllegalArgumentException.class, () -> repository.insight(documents, 10, 5));
        verifyNoInteractions(jdbc);
    }

    @Test void snapshotHashMetadataRequiresRealDocumentOwnershipAndSelectedScope() {
        setup();
        Scope scope = new Scope(ScopeMode.SELECTED, Set.of(3L), Set.of(7L), Set.of());
        when(strings.optional()).thenReturn(Optional.of("a".repeat(64)));
        assertEquals(Optional.of("a".repeat(64)), repository.chunkHash(scope, 7, 9));
        assertEquals(1, sql.size());
        assertTrue(sql.get(0).contains("c.content_hash"));
        assertTrue(sql.get(0).contains("c.id=:chunkId AND u.document_id=:documentId"));
        assertTrue(sql.get(0).contains("d.id IN (:documentIds)"));
        assertTrue(sql.get(0).contains("document_topic_assignments"));
        verify(statement).param("chunkId", 9L);
        verify(statement).param("documentId", 7L);
        verify(statement).param("libraryIds", scope.libraryIds());
        verify(statement).param("documentIds", scope.documentIds());
    }
}
