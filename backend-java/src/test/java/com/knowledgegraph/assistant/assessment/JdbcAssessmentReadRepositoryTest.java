package com.knowledgegraph.assistant.assessment;

import com.knowledgegraph.assistant.contract.AssistantContracts.*;
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

class JdbcAssessmentReadRepositoryTest {
    private static final Scope SCOPE = new Scope(ScopeMode.SELECTED, Set.of(1L), Set.of(11L), Set.of(1L));

    @Test void filtersAllScopeDimensionsPathStepAndCursorBeforeLimit() {
        JdbcClient jdbc = mock(JdbcClient.class);
        JdbcClient.StatementSpec spec = mock(JdbcClient.StatementSpec.class, RETURNS_SELF);
        @SuppressWarnings("unchecked") JdbcClient.MappedQuerySpec<AssessmentReadRepository.LegacyAttempt> mapped =
                mock(JdbcClient.MappedQuerySpec.class);
        List<String> sql = new ArrayList<>();
        when(jdbc.sql(anyString())).thenAnswer(call -> { sql.add(call.getArgument(0)); return spec; });
        when(spec.query(any(RowMapper.class))).thenReturn(mapped);
        when(mapped.list()).thenReturn(List.of());
        var result = new JdbcAssessmentReadRepository(jdbc).legacyAttempts(SCOPE, 91, 101L, 5, 500L);
        assertTrue(result.rows().isEmpty());
        assertFalse(result.hasMore());
        String query = sql.get(0);
        for (String clause : List.of("p.profile_key='local-default'", "a.path_id=:pathId",
                "a.step_id=:stepId", "a.id<:beforeAttemptId", "p.library_id IN (:libraryIds)",
                "d.id IN (:documentIds)", "visible_step.node_id IN (:nodeIds)"))
            assertTrue(query.contains(clause) && query.indexOf(clause) < query.indexOf("LIMIT :maxRows"), clause);
        verify(spec).param("pathId", 91L);
        verify(spec).param("stepId", 101L);
        verify(spec).param("beforeAttemptId", 500L);
        verify(spec).param("libraryIds", SCOPE.libraryIds());
        verify(spec).param("documentIds", SCOPE.documentIds());
        verify(spec).param("nodeIds", SCOPE.nodeIds());
        verify(spec).param("maxRows", 6);
    }

    @Test void actualMapperPreservesNullableStepAndClientTotals() throws Exception {
        JdbcClient jdbc = mock(JdbcClient.class);
        JdbcClient.StatementSpec spec = mock(JdbcClient.StatementSpec.class, RETURNS_SELF);
        @SuppressWarnings("unchecked") JdbcClient.MappedQuerySpec<AssessmentReadRepository.LegacyAttempt> mapped =
                mock(JdbcClient.MappedQuerySpec.class);
        when(jdbc.sql(anyString())).thenReturn(spec);
        when(spec.query(any(RowMapper.class))).thenAnswer(call -> {
            RowMapper<AssessmentReadRepository.LegacyAttempt> mapper = call.getArgument(0);
            ResultSet rs = mock(ResultSet.class);
            when(rs.getLong(1)).thenReturn(55L);
            when(rs.getLong(2)).thenReturn(91L);
            when(rs.getObject(3, Long.class)).thenReturn(null);
            when(rs.getObject(4, Long.class)).thenReturn(null);
            when(rs.getInt(5)).thenReturn(99);
            when(rs.getInt(6)).thenReturn(2);
            when(rs.getInt(7)).thenReturn(2);
            when(rs.getString(8)).thenReturn("[\"client\"]");
            when(rs.getTimestamp(9)).thenReturn(Timestamp.from(Instant.parse("2026-09-26T00:00:00Z")));
            doReturn(List.of(mapper.mapRow(rs, 0))).when(mapped).list();
            return mapped;
        });
        var row = new JdbcAssessmentReadRepository(jdbc).legacyAttempts(Scope.project(), 91, null, 10, null)
                .rows().get(0);
        assertNull(row.stepId());
        assertEquals(99, row.score());
        assertEquals("[\"client\"]", row.weakTopicsJson());
        assertEquals(Instant.parse("2026-09-26T00:00:00Z"), row.createdAt());
    }

    @Test void limitPlusOneSignalsMoreWithoutLeakingExtraRow() {
        JdbcClient jdbc = mock(JdbcClient.class);
        JdbcClient.StatementSpec spec = mock(JdbcClient.StatementSpec.class, RETURNS_SELF);
        @SuppressWarnings("unchecked") JdbcClient.MappedQuerySpec<AssessmentReadRepository.LegacyAttempt> mapped =
                mock(JdbcClient.MappedQuerySpec.class);
        when(jdbc.sql(anyString())).thenReturn(spec);
        when(spec.query(any(RowMapper.class))).thenReturn(mapped);
        when(mapped.list()).thenReturn(List.of(
                new AssessmentReadRepository.LegacyAttempt(2, 91, null, null, 1, 0, 1, "[]", Instant.now()),
                new AssessmentReadRepository.LegacyAttempt(1, 91, null, null, 1, 0, 1, "[]", Instant.now())));
        var page = new JdbcAssessmentReadRepository(jdbc).legacyAttempts(Scope.project(), 91, null, 1, null);
        assertEquals(1, page.rows().size());
        assertEquals(2, page.rows().get(0).attemptId());
        assertTrue(page.hasMore());
    }
}
