package com.knowledgegraph.assistant.learning;

import com.knowledgegraph.assistant.contract.AssistantContracts.*;
import com.knowledgegraph.assistant.research.ResearchReadRepository;
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

class JdbcLearningReadRepositoryTest {
    private static final Scope SCOPE = new Scope(ScopeMode.SELECTED, Set.of(1L), Set.of(11L), Set.of(1L, 2L));

    @Test void graphFiltersAllDimensionsBeforeLimitAndBindsParameters() {
        JdbcClient jdbc = mock(JdbcClient.class);
        JdbcClient.StatementSpec spec = mock(JdbcClient.StatementSpec.class, RETURNS_SELF);
        @SuppressWarnings("unchecked") JdbcClient.MappedQuerySpec<Long> mapped = mock(JdbcClient.MappedQuerySpec.class);
        List<String> sql = new ArrayList<>();
        when(jdbc.sql(anyString())).thenAnswer(call -> { sql.add(call.getArgument(0)); return spec; });
        when(spec.query(Long.class)).thenReturn(mapped);
        when(mapped.list()).thenReturn(List.of());
        var graph = new JdbcLearningReadRepository(jdbc, mock(ResearchReadRepository.class)).graph(SCOPE, 2, 3);
        assertTrue(graph.nodes().isEmpty());
        assertTrue(sql.get(0).contains("n.id IN (:nodeIds)"));
        assertTrue(sql.get(0).contains("ln.library_id IN (:libraryIds)"));
        assertTrue(sql.get(0).contains("d.id IN (:documentIds)"));
        assertTrue(sql.get(0).indexOf("d.id IN (:documentIds)") < sql.get(0).indexOf("LIMIT :maxRows"));
        verify(spec).param("libraryIds", SCOPE.libraryIds());
        verify(spec).param("documentIds", SCOPE.documentIds());
        verify(spec).param("nodeIds", SCOPE.nodeIds());
        verify(spec).param("maxRows", 3);
    }

    @Test void nodeSourceLinkRequiresExactTripleAndFormalStates() {
        JdbcClient jdbc = mock(JdbcClient.class);
        JdbcClient.StatementSpec spec = mock(JdbcClient.StatementSpec.class, RETURNS_SELF);
        @SuppressWarnings("unchecked") JdbcClient.MappedQuerySpec<Boolean> mapped = mock(JdbcClient.MappedQuerySpec.class);
        List<String> sql = new ArrayList<>();
        when(jdbc.sql(anyString())).thenAnswer(call -> { sql.add(call.getArgument(0)); return spec; });
        when(spec.query(Boolean.class)).thenReturn(mapped);
        when(mapped.single()).thenReturn(true);
        assertTrue(new JdbcLearningReadRepository(jdbc, mock(ResearchReadRepository.class))
                .nodeSourceLinked(SCOPE, 1, 11, 101));
        assertTrue(sql.get(0).contains("ne.node_id=:nodeId AND d.id=:documentId AND c.id=:chunkId"));
        assertTrue(sql.get(0).contains("d.status='COMPLETED'"));
        assertTrue(sql.get(0).contains("n.id IN (:nodeIds)"));
        verify(spec).param("chunkId", 101L);
    }

    @Test void actualPathAndStepMapperFingerprintChangesWithStepStatus() throws Exception {
        JdbcClient jdbc = mock(JdbcClient.class);
        ResearchReadRepository research = mock(ResearchReadRepository.class);
        JdbcClient.StatementSpec spec = mock(JdbcClient.StatementSpec.class, RETURNS_SELF);
        @SuppressWarnings("unchecked") JdbcClient.MappedQuerySpec<Object> mapped = mock(JdbcClient.MappedQuerySpec.class);
        when(jdbc.sql(anyString())).thenReturn(spec);
        when(spec.query(any(RowMapper.class))).thenReturn(mapped);
        when(spec.query(Boolean.class)).thenReturn(mock(JdbcClient.MappedQuerySpec.class));
        @SuppressWarnings("unchecked") JdbcClient.MappedQuerySpec<Boolean> booleanMapped =
                (JdbcClient.MappedQuerySpec<Boolean>) spec.query(Boolean.class);
        when(booleanMapped.single()).thenReturn(true);
        ResultSet header = mock(ResultSet.class);
        when(header.getLong(1)).thenReturn(91L);
        when(header.getLong(2)).thenReturn(1L);
        when(header.getString(3)).thenReturn("goal");
        when(header.getString(4)).thenReturn("beginner");
        when(header.getString(5)).thenReturn("active");
        when(header.getTimestamp(6)).thenReturn(Timestamp.from(Instant.parse("2026-09-25T00:00:00Z")));
        ResultSet step = mock(ResultSet.class);
        when(step.getLong(1)).thenReturn(101L);
        when(step.getString(2)).thenReturn("step1");
        when(step.getLong(3)).thenReturn(1L);
        when(step.getInt(4)).thenReturn(0);
        when(step.getString(5)).thenReturn("node");
        when(step.getInt(6)).thenReturn(20);
        when(step.getString(7)).thenReturn("current", "completed");
        when(step.getTimestamp(10)).thenReturn(Timestamp.from(Instant.parse("2026-09-25T00:01:00Z")));
        when(mapped.optional()).thenAnswer(call -> Optional.of(((RowMapper<?>) currentMapper[0]).mapRow(header, 0)));
        when(mapped.list()).thenAnswer(call -> List.of(((RowMapper<?>) currentMapper[0]).mapRow(step, 0)));
        when(spec.query(any(RowMapper.class))).thenAnswer(call -> { currentMapper[0] = call.getArgument(0); return mapped; });
        when(research.node(any(), eq(1L))).thenReturn(Optional.of(LearningAgentToolProviderTest.node(1)));
        var repository = new JdbcLearningReadRepository(jdbc, research);
        var first = repository.currentPath(SCOPE);
        var second = repository.currentPath(SCOPE);
        assertEquals(LearningReadRepository.PathState.AVAILABLE, first.state());
        assertEquals(91, first.pathId());
        assertEquals(101, first.steps().get(0).stepId());
        assertNotEquals(first.snapshotFingerprint(), second.snapshotFingerprint());
    }
    private final RowMapper<?>[] currentMapper = new RowMapper<?>[1];
}
