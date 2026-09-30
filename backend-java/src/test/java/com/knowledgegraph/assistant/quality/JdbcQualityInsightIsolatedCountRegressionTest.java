package com.knowledgegraph.assistant.quality;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowledgegraph.assistant.contract.AssistantContracts.Scope;
import com.knowledgegraph.assistant.contract.AssistantContracts.ScopeMode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** SQL shape, binding, mapping and failure checks only; no fake SQL engine or MySQL performance claim. */
class JdbcQualityInsightIsolatedCountRegressionTest {
    private static final String INCIDENT = "incident_nodes AS (SELECT source_node_id AS node_id FROM visible_edges"
            + " UNION SELECT target_node_id AS node_id FROM visible_edges)";
    private static final String ANTI_JOIN = "SELECT COUNT(*) FROM visible_nodes n"
            + " LEFT JOIN incident_nodes i ON i.node_id=n.id WHERE i.node_id IS NULL";
    private final JdbcClient jdbc = mock(JdbcClient.class);
    private final JdbcQualityReadRepository repository = new JdbcQualityReadRepository(jdbc, new ObjectMapper());
    private final List<Query> queries = new ArrayList<>();
    private long isolatedValue;
    private RuntimeException isolatedFailure;

    private static final class Query {
        final String sql;
        final Map<String, Object> params = new LinkedHashMap<>();
        Query(String sql) { this.sql = sql; }
    }

    @BeforeEach void captureJdbcWithoutConnecting() {
        when(jdbc.sql(anyString())).thenAnswer(call -> statement(call.getArgument(0)));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private JdbcClient.StatementSpec statement(String sql) {
        Query query = new Query(sql);
        queries.add(query);
        JdbcClient.StatementSpec statement = mock(JdbcClient.StatementSpec.class, RETURNS_SELF);
        when(statement.param(anyString(), any())).thenAnswer(call -> {
            query.params.put(call.getArgument(0), call.getArgument(1));
            return statement;
        });
        JdbcClient.MappedQuerySpec<Long> numbers = mock(JdbcClient.MappedQuerySpec.class);
        when(statement.query(Long.class)).thenReturn(numbers);
        if (sql.contains(INCIDENT) && isolatedFailure != null) {
            when(numbers.single()).thenThrow(isolatedFailure);
        } else {
            when(numbers.single()).thenReturn(sql.contains(INCIDENT) ? isolatedValue : 0L);
        }
        JdbcClient.MappedQuerySpec<Object> rows = mock(JdbcClient.MappedQuerySpec.class);
        when(statement.query(any(RowMapper.class))).thenReturn((JdbcClient.MappedQuerySpec) rows);
        when(rows.list()).thenReturn(List.of());
        return statement;
    }

    private Query isolatedCount(Scope scope, int isolatedLimit, int topLimit) {
        repository.insight(scope, isolatedLimit, topLimit);
        List<Query> matching = queries.stream().filter(q -> q.sql.contains(ANTI_JOIN)).toList();
        assertEquals(1, matching.size(), "Only the isolated total uses the unbounded COUNT anti-join");
        return matching.get(0);
    }

    @Test void countAggregatesScopedIncidentNodesBeforeOneAntiJoin() {
        Query count = isolatedCount(Scope.project(), 1, 1);
        assertTrue(count.sql.contains(", " + INCIDENT));
        assertTrue(count.sql.endsWith(ANTI_JOIN));
        assertTrue(count.sql.contains("JOIN visible_nodes s ON s.id=e.source_node_id"));
        assertTrue(count.sql.contains("JOIN visible_nodes t ON t.id=e.target_node_id WHERE e.status='active'"));
        assertFalse(count.sql.contains("UNION ALL"), "Connected nodes must be distinct, including self-loops and parallel edges");
        assertFalse(count.sql.contains("e.source_node_id=n.id OR e.target_node_id=n.id"));
        assertFalse(count.sql.contains("LIMIT"), "The total is independent of both list limits");
        assertEquals(Map.of(), count.params);
    }

    @Test void selectedLibraryUnionIsScopedBeforeCountingAndKeepsLargeBoundIds() {
        Scope selected = new Scope(ScopeMode.SELECTED,
                Set.of(4_294_967_301L, 4_294_967_302L), Set.of(), Set.of());
        Query count = isolatedCount(selected, 2, 3);
        assertTrue(count.sql.contains("SELECT n.id,n.canonical_name,n.node_type FROM knowledge_nodes n WHERE 1=1"
                + " AND EXISTS (SELECT 1 FROM library_nodes ln WHERE ln.node_id=n.id"
                + " AND ln.library_id IN (:libraryIds))"));
        assertTrue(count.sql.indexOf("visible_edges AS") < count.sql.indexOf(INCIDENT));
        assertFalse(count.sql.contains("JOIN library_nodes"), "Shared library membership must not multiply nodes");
        assertEquals(Map.of("libraryIds", selected.libraryIds()), count.params);
        assertFalse(count.sql.contains("4294967301"), "Large IDs must remain bound values");
    }

    @Test void ninthCountRemainsUnboundedAndTenthListKeepsItsOwnLimit() {
        isolatedCount(Scope.project(), 2, 3);
        assertEquals(10, queries.size());
        assertTrue(queries.get(7).sql.endsWith("ORDER BY degree DESC,n.id LIMIT :limit"));
        assertTrue(queries.get(8).sql.contains(INCIDENT));
        assertEquals(Map.of(), queries.get(8).params);
        assertEquals("WITH visible_nodes AS (SELECT n.id,n.canonical_name,n.node_type"
                + " FROM knowledge_nodes n WHERE 1=1), visible_edges AS (SELECT e.id,e.source_node_id,e.target_node_id"
                + " FROM knowledge_edges e JOIN visible_nodes s ON s.id=e.source_node_id"
                + " JOIN visible_nodes t ON t.id=e.target_node_id WHERE e.status='active') "
                + ", incident_nodes AS (SELECT source_node_id AS node_id FROM visible_edges"
                + " UNION SELECT target_node_id AS node_id FROM visible_edges) "
                + "SELECT n.id,n.canonical_name,n.node_type,0"
                + " FROM visible_nodes n LEFT JOIN incident_nodes i ON i.node_id=n.id"
                + " WHERE i.node_id IS NULL ORDER BY n.id LIMIT :limit",
                queries.get(9).sql);
        assertEquals(Map.of("limit", 2), queries.get(9).params);
        assertEquals(Map.of("limit", 3), queries.get(7).params);
    }

    @Test void countReadsLongResultIndependentOfTruncatedList() {
        isolatedValue = 3_000_000_001L;
        QualityReadRepository.Insight result = repository.insight(Scope.project(), 1, 1);
        assertEquals(3_000_000_001L, result.isolatedTotal());
        assertTrue(result.isolated().isEmpty(), "A mock empty list must not become the total");
        assertFalse(queries.get(8).sql.contains("LIMIT"));
    }

    @Test void emptyResultMapsZeroWithoutInventingRows() {
        QualityReadRepository.Insight result = repository.insight(Scope.project(), 1, 1);
        assertEquals(0L, result.isolatedTotal());
        assertTrue(result.isolated().isEmpty());
    }

    @Test void countFailurePropagatesBeforeTheListQuery() {
        isolatedFailure = new IllegalStateException("synthetic isolated count failure");
        assertSame(isolatedFailure, assertThrows(IllegalStateException.class,
                () -> repository.insight(Scope.project(), 2, 3)));
        assertEquals(9, queries.size());
        assertTrue(queries.get(8).sql.contains(INCIDENT));
        assertTrue(queries.stream().noneMatch(q -> q.sql.contains("ORDER BY n.id LIMIT :limit")));
    }

    @ParameterizedTest @MethodSource("unsupportedScopes")
    void unsupportedDimensionsStillRejectBeforeJdbc(Scope scope) {
        assertEquals("UNSUPPORTED_SCOPE", assertThrows(IllegalArgumentException.class,
                () -> repository.insight(scope, 2, 3)).getMessage());
        verifyNoInteractions(jdbc);
    }

    private static Stream<Scope> unsupportedScopes() {
        return Stream.of(
                new Scope(ScopeMode.SELECTED, Set.of(), Set.of(7L), Set.of()),
                new Scope(ScopeMode.SELECTED, Set.of(), Set.of(), Set.of(9L)),
                new Scope(ScopeMode.SELECTED, Set.of(3L), Set.of(7L), Set.of()),
                new Scope(ScopeMode.SELECTED, Set.of(3L), Set.of(), Set.of(9L)));
    }
}
