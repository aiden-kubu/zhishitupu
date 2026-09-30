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
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class JdbcResearchReadRepositoryTest {
    private static final Scope SCOPE = new Scope(ScopeMode.SELECTED, Set.of(4L), Set.of(11L, 12L), Set.of());

    @Test void matchingDocumentsFiltersLibraryAndDocumentBeforeLimitWithBoundTerms() {
        JdbcClient jdbc = mock(JdbcClient.class);
        JdbcClient.StatementSpec spec = mock(JdbcClient.StatementSpec.class, RETURNS_SELF);
        @SuppressWarnings("unchecked") JdbcClient.MappedQuerySpec<Long> mapped = mock(JdbcClient.MappedQuerySpec.class);
        List<String> sql = new ArrayList<>();
        when(jdbc.sql(anyString())).thenAnswer(call -> { sql.add(call.getArgument(0)); return spec; });
        when(spec.query(Long.class)).thenReturn(mapped);
        when(mapped.list()).thenReturn(List.of(11L, 12L));
        var result = new JdbcResearchReadRepository(jdbc).matchingDocuments(SCOPE, List.of("tcp", "ip"), 2);
        assertEquals(List.of(11L, 12L), result.items());
        assertFalse(result.hasMore());
        String query = sql.get(0);
        assertTrue(query.indexOf("d.id IN (:documentIds)") < query.indexOf("LIMIT :maxRows"));
        assertTrue(query.indexOf("d.library_id IN (:libraryIds)") < query.indexOf("LIMIT :maxRows"));
        assertTrue(query.contains("document_topic_assignments"));
        assertTrue(query.contains("GROUP BY d.id"));
        assertTrue(query.contains("u.status IN ('READY','OCR_OK')"));
        assertFalse(query.contains("tcp"));
        verify(spec).param("term0", "tcp");
        verify(spec).param("term1", "ip");
        verify(spec).param("maxRows", 3);
    }

    @Test void sourceMetadataUsesExactIdsAndActualMapperFields() throws Exception {
        JdbcClient jdbc = mock(JdbcClient.class);
        JdbcClient.StatementSpec spec = mock(JdbcClient.StatementSpec.class, RETURNS_SELF);
        List<String> sql = new ArrayList<>();
        when(jdbc.sql(anyString())).thenAnswer(call -> { sql.add(call.getArgument(0)); return spec; });
        @SuppressWarnings("unchecked") JdbcClient.MappedQuerySpec<ResearchReadRepository.SourceMeta> mapped = mock(JdbcClient.MappedQuerySpec.class);
        ResultSet row = mock(ResultSet.class);
        when(row.getLong(1)).thenReturn(11L);
        when(row.getLong(2)).thenReturn(101L);
        when(row.getLong(3)).thenReturn(1001L);
        when(row.getInt(4)).thenReturn(7);
        when(row.getString(5)).thenReturn("page");
        when(row.getString(6)).thenReturn("第7页");
        when(row.getString(7)).thenReturn("hash101");
        when(row.getString(8)).thenReturn("COMPLETED");
        when(row.getString(9)).thenReturn("active");
        when(row.getString(10)).thenReturn("OCR_OK");
        when(spec.query(any(RowMapper.class))).thenAnswer(call -> {
            @SuppressWarnings("unchecked") RowMapper<ResearchReadRepository.SourceMeta> mapper = call.getArgument(0);
            ResearchReadRepository.SourceMeta value = mapper.mapRow(row, 0);
            when(mapped.optional()).thenReturn(Optional.of(value));
            return mapped;
        });
        var meta = new JdbcResearchReadRepository(jdbc).sourceMeta(SCOPE, 11, 101).orElseThrow();
        assertEquals(1001, meta.unitId());
        assertEquals(7, meta.unitIndex());
        assertEquals("第7页", meta.sourceLocator());
        assertTrue(meta.formalAvailable());
        assertTrue(sql.get(0).contains("d.id=:documentId AND c.id=:chunkId"));
        assertTrue(sql.get(0).contains("d.id IN (:documentIds)"));
        verify(spec).param("documentId", 11L);
        verify(spec).param("chunkId", 101L);
    }

    @Test void formalAvailabilityRejectsAllNonFormalStatesAndNodeScopeFailsClosed() {
        assertFalse(meta("PROCESSING", "active", "READY").formalAvailable());
        assertFalse(meta("COMPLETED", "archived", "READY").formalAvailable());
        assertFalse(meta("COMPLETED", "active", "PENDING").formalAvailable());
        assertTrue(meta("COMPLETED", null, "READY").formalAvailable());
        Scope nodeScope = new Scope(ScopeMode.SELECTED, Set.of(), Set.of(), Set.of(1L));
        JdbcClient jdbc = mock(JdbcClient.class);
        var repo = new JdbcResearchReadRepository(jdbc);
        assertThrows(IllegalArgumentException.class,
                () -> repo.matchingDocuments(nodeScope, List.of("tcp"), 4));
        verifyNoInteractions(jdbc);
    }

    @Test void edgeQueryChecksBothEndpointsAndRealEvidenceBeforeLimit() {
        JdbcClient jdbc = mock(JdbcClient.class);
        JdbcClient.StatementSpec spec = mock(JdbcClient.StatementSpec.class, RETURNS_SELF);
        @SuppressWarnings("unchecked") JdbcClient.MappedQuerySpec<ResearchReadRepository.Edge> mapped = mock(JdbcClient.MappedQuerySpec.class);
        List<String> sql = new ArrayList<>();
        when(jdbc.sql(anyString())).thenAnswer(call -> { sql.add(call.getArgument(0)); return spec; });
        when(spec.query(any(RowMapper.class))).thenReturn(mapped);
        when(mapped.list()).thenReturn(List.of());
        Scope narrow = new Scope(ScopeMode.SELECTED, Set.of(4L), Set.of(11L), Set.of(1L, 2L));
        var result = new JdbcResearchReadRepository(jdbc).incidentEdges(narrow, 1, 10);
        assertTrue(result.items().isEmpty());
        String query = sql.get(0);
        assertTrue(query.contains("e.source_node_id=:nodeId OR e.target_node_id=:nodeId"));
        assertTrue(query.contains("SELECT DISTINCT n.id FROM knowledge_nodes n"));
        assertTrue(query.contains("n.id IN (:nodeIds)"));
        assertTrue(query.contains("JOIN scoped_nodes sn ON sn.id=s.id"));
        assertTrue(query.contains("JOIN scoped_nodes tn ON tn.id=t.id"));
        assertTrue(query.contains("SELECT DISTINCT e.id FROM knowledge_edges e"));
        assertTrue(query.contains("JOIN scoped_edges se ON se.id=e.id"));
        assertTrue(query.contains("ee.edge_id=e.id"));
        assertTrue(query.contains("ed.id IN (:documentIds)"));
        assertTrue(query.indexOf("ed.id IN (:documentIds)") < query.lastIndexOf("LIMIT :maxRows"));
        assertTrue(query.contains("nd.id IN (:documentIds)"));
        assertTrue(query.contains("ne.node_id=n.id"));
        assertTrue(query.contains("EXISTS(SELECT 1 FROM edge_evidence ee"));
        verify(spec).param("nodeId", 1L);
        verify(spec).param("maxRows", 11);
    }

    @Test void nodeMapperExposesWhitelistedReviewAndScopedSourceFlag() throws Exception {
        JdbcClient jdbc = mock(JdbcClient.class);
        JdbcClient.StatementSpec spec = mock(JdbcClient.StatementSpec.class, RETURNS_SELF);
        @SuppressWarnings("unchecked") JdbcClient.MappedQuerySpec<ResearchReadRepository.Node> mappedNode = mock(JdbcClient.MappedQuerySpec.class);
        @SuppressWarnings("unchecked") JdbcClient.MappedQuerySpec<Long> mappedLong = mock(JdbcClient.MappedQuerySpec.class);
        @SuppressWarnings("unchecked") JdbcClient.MappedQuerySpec<String> mappedString = mock(JdbcClient.MappedQuerySpec.class);
        List<String> sql = new ArrayList<>();
        when(jdbc.sql(anyString())).thenAnswer(call -> { sql.add(call.getArgument(0)); return spec; });
        when(spec.query(Long.class)).thenReturn(mappedLong);
        when(spec.query(String.class)).thenReturn(mappedString);
        when(mappedLong.list()).thenReturn(List.of(4L));
        when(mappedString.list()).thenReturn(List.of("别名"));
        ResultSet row = mock(ResultSet.class);
        when(row.getLong(1)).thenReturn(1L);
        when(row.getString(2)).thenReturn("TCP");
        when(row.getString(4)).thenReturn("concept");
        when(row.getString(5)).thenReturn("定义");
        when(row.getString(6)).thenReturn("active");
        when(row.getTimestamp(7)).thenReturn(Timestamp.from(Instant.parse("2026-09-24T00:00:00Z")));
        when(row.getString(8)).thenReturn("{\"origin\":\"import\",\"reviewStatus\":\"APPROVED\",\"secret\":\"omit\"}");
        when(row.getBoolean(9)).thenReturn(true);
        when(spec.query(any(RowMapper.class))).thenAnswer(call -> {
            @SuppressWarnings("unchecked") RowMapper<ResearchReadRepository.Node> mapper = call.getArgument(0);
            ResearchReadRepository.Node value = mapper.mapRow(row, 0);
            when(mappedNode.optional()).thenReturn(Optional.of(value));
            return mappedNode;
        });
        var node = new JdbcResearchReadRepository(jdbc).node(SCOPE, 1).orElseThrow();
        assertEquals("import", node.origin());
        assertEquals("APPROVED", node.reviewStatus());
        assertEquals(List.of(4L), node.libraryIds());
        assertEquals(List.of("别名"), node.aliases());
        assertTrue(node.sourceEvidenceAvailable());
        assertTrue(sql.get(0).contains("ne.node_id=n.id"));
        assertTrue(sql.get(0).contains("nd.id IN (:documentIds)"));
        assertFalse(node.toString().contains("secret"));
    }

    private static ResearchReadRepository.SourceMeta meta(String doc, String life, String unit) {
        return new ResearchReadRepository.SourceMeta(11, 101, 1001, 1, "page", "p1", "h", doc, life, unit);
    }
}
