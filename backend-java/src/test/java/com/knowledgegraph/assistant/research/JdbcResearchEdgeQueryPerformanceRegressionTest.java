package com.knowledgegraph.assistant.research;

import com.knowledgegraph.assistant.contract.AssistantContracts.Scope;
import com.knowledgegraph.assistant.contract.AssistantContracts.ScopeMode;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Offline SQL-shape and mapping checks; no MySQL performance claim is made here. */
class JdbcResearchEdgeQueryPerformanceRegressionTest {
    private static final Scope DOCUMENTS = new Scope(ScopeMode.SELECTED, Set.of(4L),
            Set.of(11L, 12L), Set.of(1L, 2L));

    @Test void documentScopeKeepsThreeFormalPredicatesInDistinctSetsBeforePaging() {
        Fixture f = new Fixture();
        f.repository.outgoingEdges(DOCUMENTS, 1L, 10);
        String sql = f.sql.get(0);
        assertTrue(sql.startsWith("WITH scoped_nodes AS (SELECT DISTINCT n.id FROM knowledge_nodes n WHERE 1=1"));
        assertTrue(sql.contains("scoped_edges AS (SELECT DISTINCT e.id FROM knowledge_edges e WHERE 1=1"));
        assertTrue(sql.contains("ne.node_id=n.id"));
        assertTrue(sql.contains("ee.edge_id=e.id"));
        assertTrue(sql.contains("n.id IN (:nodeIds)"));
        assertTrue(sql.contains("ln.node_id=n.id AND ln.library_id IN (:libraryIds)"));
        assertTrue(sql.contains("nd.id IN (:documentIds)"));
        assertTrue(sql.contains("ed.id IN (:documentIds)"));
        assertTrue(sql.contains("nd.status='COMPLETED'"));
        assertTrue(sql.contains("ed.status='COMPLETED'"));
        assertTrue(sql.contains("nu.status IN ('READY','OCR_OK')"));
        assertTrue(sql.contains("eu.status IN ('READY','OCR_OK')"));
        assertTrue(sql.contains("nm.lifecycle_status='active'"));
        assertTrue(sql.contains("em.lifecycle_status='active'"));
        assertTrue(sql.contains("document_topic_assignments"));
        assertTrue(sql.contains("JOIN scoped_nodes sn ON sn.id=s.id JOIN scoped_nodes tn ON tn.id=t.id"));
        assertTrue(sql.contains("JOIN scoped_edges se ON se.id=e.id WHERE e.source_node_id=:nodeId"));
        assertTrue(sql.contains("EXISTS(SELECT 1 FROM edge_evidence ee")); // real has_source
        assertEquals(1, occurrences(sql, "LIMIT :maxRows"));
        assertTrue(sql.indexOf("JOIN scoped_edges se") < sql.indexOf("ORDER BY e.id LIMIT :maxRows"));
        verify(f.spec).param("nodeId", 1L);
        verify(f.spec).param("maxRows", 11);
    }

    @Test void incidentAndDocumentOnlyScopeDoNotConflateEndpointEvidenceOrDirection() {
        Fixture f = new Fixture();
        Scope documentOnly = new Scope(ScopeMode.SELECTED, Set.of(), Set.of(11L, 12L), Set.of());
        f.repository.incidentEdges(documentOnly, 1L, 10);
        String sql = f.sql.get(0);
        assertTrue(sql.contains("JOIN scoped_nodes sn ON sn.id=s.id JOIN scoped_nodes tn ON tn.id=t.id"));
        assertTrue(sql.contains("(e.source_node_id=:nodeId OR e.target_node_id=:nodeId)"));
        assertEquals(1, occurrences(sql, "WITH scoped_nodes"));
        assertFalse(sql.contains("library_id IN (:libraryIds)"));
        assertFalse(sql.contains("sn.document_id=tn.document_id"));
        assertTrue(sql.contains("nd.id IN (:documentIds)"));
        assertTrue(sql.contains("ed.id IN (:documentIds)"));
    }

    @Test void scopesWithoutDocumentsKeepOriginalSingleQueryAndEvidenceProjection() {
        Fixture library = new Fixture();
        library.repository.incidentEdges(new Scope(ScopeMode.SELECTED, Set.of(4L), Set.of(), Set.of()), 1L, 10);
        String sql = library.sql.get(0);
        assertFalse(sql.startsWith("WITH "));
        assertTrue(sql.contains("ln.node_id=s.id"));
        assertTrue(sql.contains("ln.node_id=t.id"));
        assertTrue(sql.contains("EXISTS(SELECT 1 FROM edge_evidence ee"));
        assertFalse(sql.contains("JOIN scoped_edges"));
        assertTrue(sql.contains("(e.source_node_id=:nodeId OR e.target_node_id=:nodeId)"));

        Fixture project = new Fixture();
        project.repository.outgoingEdges(Scope.project(), 1L, 10);
        assertFalse(project.sql.get(0).startsWith("WITH "));
        assertFalse(project.sql.get(0).contains("library_id IN (:libraryIds)"));
        assertFalse(project.sql.get(0).contains("id IN (:documentIds)"));
        assertTrue(project.sql.get(0).contains("WHERE e.source_node_id=:nodeId ORDER BY e.id LIMIT :maxRows"));
    }

    @Test void mappedDirectionFieldsAndLimitPlusOneAreRetained() throws Exception {
        Fixture f = new Fixture();
        when(f.spec.query(any(RowMapper.class))).thenAnswer(call -> {
            @SuppressWarnings("unchecked") RowMapper<ResearchReadRepository.Edge> mapper = call.getArgument(0);
            List<ResearchReadRepository.Edge> rows = new ArrayList<>();
            for (long id = 1; id <= 11; id++) {
                ResultSet rs = mock(ResultSet.class);
                when(rs.getLong(1)).thenReturn(3_000_000_000L + id);
                when(rs.getLong(2)).thenReturn(1L);
                when(rs.getLong(3)).thenReturn(100L + id);
                when(rs.getString(4)).thenReturn("related");
                when(rs.getString(5)).thenReturn("ARCHIVED");
                when(rs.getTimestamp(6)).thenReturn(Timestamp.from(Instant.parse("2026-09-24T00:00:00Z")));
                when(rs.getString(7)).thenReturn("{\"origin\":\"import\",\"reviewStatus\":\"APPROVED\"}");
                when(rs.getBoolean(8)).thenReturn(true);
                rows.add(mapper.mapRow(rs, (int) id - 1));
            }
            when(f.mapped.list()).thenReturn(rows);
            return f.mapped;
        });
        var result = f.repository.outgoingEdges(DOCUMENTS, 1L, 10);
        assertEquals(10, result.items().size());
        assertTrue(result.hasMore());
        assertEquals(1L, result.items().get(0).sourceNodeId());
        assertEquals(101L, result.items().get(0).targetNodeId());
        assertEquals("ARCHIVED", result.items().get(0).status());
        assertTrue(result.items().get(0).sourceEvidenceAvailable());
        assertEquals(3_000_000_010L, result.items().get(9).id());
    }

    @Test void queryTimeoutIsPropagatedRatherThanConvertedToEmptyWindow() {
        Fixture f = new Fixture();
        RuntimeException timeout = new RuntimeException("3024");
        when(f.mapped.list()).thenThrow(timeout);
        assertSame(timeout, assertThrows(RuntimeException.class,
                () -> f.repository.outgoingEdges(DOCUMENTS, 1L, 10)));
    }

    private static int occurrences(String text, String part) {
        return (text.length() - text.replace(part, "").length()) / part.length();
    }

    private static final class Fixture {
        final JdbcClient jdbc = mock(JdbcClient.class);
        final JdbcClient.StatementSpec spec = mock(JdbcClient.StatementSpec.class, RETURNS_SELF);
        @SuppressWarnings("unchecked")
        final JdbcClient.MappedQuerySpec<ResearchReadRepository.Edge> mapped = mock(JdbcClient.MappedQuerySpec.class);
        final List<String> sql = new ArrayList<>();
        final JdbcResearchReadRepository repository = new JdbcResearchReadRepository(jdbc);

        Fixture() {
            when(jdbc.sql(anyString())).thenAnswer(call -> { sql.add(call.getArgument(0)); return spec; });
            when(spec.query(any(RowMapper.class))).thenReturn(mapped);
            when(mapped.list()).thenReturn(List.of());
        }
    }
}
