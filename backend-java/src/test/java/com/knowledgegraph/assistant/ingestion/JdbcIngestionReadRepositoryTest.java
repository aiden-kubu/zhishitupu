package com.knowledgegraph.assistant.ingestion;

import com.knowledgegraph.assistant.contract.AssistantContracts.Scope;
import com.knowledgegraph.assistant.contract.AssistantContracts.ScopeMode;
import com.knowledgegraph.assistant.source.JdbcSourceLocator;
import com.knowledgegraph.settings.SettingsService;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.sql.ResultSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** SQL shape and mapping tests only: no connection or MySQL compatibility claim. */
class JdbcIngestionReadRepositoryTest {
    @Test void listQueriesBindBothSelectedDimensionsBeforeLimitAndBoundOffset() {
        JdbcClient jdbc = mock(JdbcClient.class);
        JdbcClient.StatementSpec query = mock(JdbcClient.StatementSpec.class, RETURNS_SELF);
        @SuppressWarnings("unchecked") JdbcClient.MappedQuerySpec<Long> numbers = mock(JdbcClient.MappedQuerySpec.class);
        when(jdbc.sql(anyString())).thenReturn(query);
        when(query.query(Long.class)).thenReturn(numbers);
        when(numbers.single()).thenReturn(8L);
        when(numbers.list()).thenReturn(List.of());
        var repository = new JdbcIngestionReadRepository(jdbc, mock(SettingsService.class));
        Scope selected = new Scope(ScopeMode.SELECTED, Set.of(3L), Set.of(10L), Set.of());

        var page = repository.libraries(selected, 2, 5);
        assertEquals(8, page.total());
        assertTrue(page.items().isEmpty());
        verify(jdbc, times(2)).sql(argThat(sql -> sql.contains("l.id IN (:libraryIds)")
                && sql.contains("d.id IN (:documentIds)") && !sql.contains("IN (3)") && !sql.contains("IN (10)")));
        verify(query, times(2)).param("libraryIds", Set.of(3L));
        verify(query, times(2)).param("documentIds", Set.of(10L));
        verify(query).param("limit", 5);
        verify(query).param("offset", 5L);
    }

    @Test void documentAndJobListsApplyLibraryMembershipAndDocumentScopeBeforePaging() {
        JdbcClient jdbc = mock(JdbcClient.class);
        JdbcClient.StatementSpec query = mock(JdbcClient.StatementSpec.class, RETURNS_SELF);
        @SuppressWarnings("unchecked") JdbcClient.MappedQuerySpec<Long> numbers = mock(JdbcClient.MappedQuerySpec.class);
        @SuppressWarnings("unchecked") JdbcClient.MappedQuerySpec<IngestionReadRepository.Job> jobs = mock(JdbcClient.MappedQuerySpec.class);
        when(jdbc.sql(anyString())).thenReturn(query);
        when(query.query(Long.class)).thenReturn(numbers);
        when(query.query(any(RowMapper.class))).thenReturn((JdbcClient.MappedQuerySpec) jobs);
        when(numbers.single()).thenReturn(0L);
        when(numbers.list()).thenReturn(List.of());
        when(jobs.list()).thenReturn(List.of());
        var repository = new JdbcIngestionReadRepository(jdbc, mock(SettingsService.class));
        Scope selected = new Scope(ScopeMode.SELECTED, Set.of(3L), Set.of(10L), Set.of());

        repository.documents(selected, "FAILED", 1, 20);
        repository.jobs(selected, "FAILED", 1, 20);
        verify(jdbc, times(4)).sql(argThat(sql -> sql.contains("d.id IN (:documentIds)")
                && sql.contains("a.library_id IN (:libraryIds)")
                && sql.contains("status=:status")));
        verify(query, times(4)).param("status", "FAILED");
        assertThrows(IllegalArgumentException.class, () -> repository.jobs(
                new Scope(ScopeMode.SELECTED, Set.of(), Set.of(), Set.of(5L)), null, 1, 20));
    }

    @Test void stableChunkReadMapsRealIdAndHashAndBindsDocumentOwnership() throws Exception {
        JdbcClient jdbc = mock(JdbcClient.class);
        JdbcClient.StatementSpec query = mock(JdbcClient.StatementSpec.class, RETURNS_SELF);
        when(jdbc.sql(anyString())).thenReturn(query);
        ResultSet rs = mock(ResultSet.class);
        when(rs.getLong(1)).thenReturn(908L);
        when(rs.getLong(2)).thenReturn(77L);
        when(rs.getInt(3)).thenReturn(4);
        when(rs.getString(4)).thenReturn("a".repeat(64));
        when(rs.getInt(5)).thenReturn(11);
        when(rs.getInt(6)).thenReturn(17);
        when(rs.getString(7)).thenReturn("原文超过长度");
        when(query.query(any(RowMapper.class))).thenAnswer(call -> {
            @SuppressWarnings("unchecked") RowMapper<Object> mapper = call.getArgument(0);
            @SuppressWarnings("unchecked") JdbcClient.MappedQuerySpec<Object> mapped = mock(JdbcClient.MappedQuerySpec.class);
            Object value = mapper.mapRow(rs, 0);
            when(mapped.optional()).thenReturn(Optional.of(value));
            return mapped;
        });

        Scope scope = new Scope(ScopeMode.SELECTED, Set.of(3L), Set.of(10L), Set.of());
        var chunk = new JdbcSourceLocator(jdbc).chunk(scope, 10, 908, 2).orElseThrow();
        assertEquals(908, chunk.chunkId());
        assertEquals(77, chunk.unitId());
        assertEquals(4, chunk.chunkIndex());
        assertEquals("a".repeat(64), chunk.contentHash());
        assertEquals("原文", chunk.content());
        assertTrue(chunk.contentTruncated());
        verify(jdbc).sql(argThat(sql -> sql.contains("u.document_id=:documentId") && sql.contains("c.id=:chunkId")
                && sql.contains("a.library_id IN (:libraryIds)") && sql.contains("d.id IN (:documentIds)")));
        verify(query).param("documentId", 10L);
        verify(query).param("chunkId", 908L);
        verify(query).param("libraryIds", Set.of(3L));
        verify(query).param("documentIds", Set.of(10L));
    }

    @Test void organizationFiltersSelectedDestinationsBeforeLimit() {
        JdbcClient jdbc = mock(JdbcClient.class);
        JdbcClient.StatementSpec query = mock(JdbcClient.StatementSpec.class, RETURNS_SELF);
        @SuppressWarnings("unchecked") JdbcClient.MappedQuerySpec<Long> count = mock(JdbcClient.MappedQuerySpec.class);
        @SuppressWarnings("unchecked") JdbcClient.MappedQuerySpec<IngestionReadRepository.Destination> rows = mock(JdbcClient.MappedQuerySpec.class);
        when(jdbc.sql(anyString())).thenReturn(query);
        when(query.query(Long.class)).thenReturn(count);
        when(query.query(any(RowMapper.class))).thenReturn((JdbcClient.MappedQuerySpec) rows);
        when(count.single()).thenReturn(0L);
        when(rows.list()).thenReturn(List.of());
        var repository = new JdbcIngestionReadRepository(jdbc, mock(SettingsService.class));
        Scope selected = new Scope(ScopeMode.SELECTED, Set.of(3L), Set.of(10L), Set.of());

        var result = repository.destinations(selected, 10L);
        assertEquals(0, result.total());
        verify(jdbc, times(2)).sql(argThat(sql -> sql.contains("a.document_id=:id")
                && sql.contains("l.id IN (:libraryIds)") && sql.contains("d.id IN (:documentIds)")));
        verify(jdbc).sql(argThat(sql -> sql.contains("l.id IN (:libraryIds)") && sql.contains("LIMIT 100")));
        verify(query, times(2)).param("libraryIds", Set.of(3L));
        verify(query, times(2)).param("documentIds", Set.of(10L));
    }
}
