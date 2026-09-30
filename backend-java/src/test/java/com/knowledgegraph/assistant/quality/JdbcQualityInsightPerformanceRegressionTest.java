package com.knowledgegraph.assistant.quality;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowledgegraph.assistant.contract.AssistantContracts.Scope;
import com.knowledgegraph.assistant.contract.AssistantContracts.ScopeMode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Offline SQL, binding and mapping guards. A mocked JdbcClient cannot prove MySQL runtime or speed. */
class JdbcQualityInsightPerformanceRegressionTest {
    private static final Scope SELECTED = new Scope(ScopeMode.SELECTED,
            Set.of(7910L, 4_294_967_301L), Set.of(), Set.of());
    private final JdbcClient jdbc = mock(JdbcClient.class);
    private final JdbcQualityReadRepository repository = new JdbcQualityReadRepository(jdbc, new ObjectMapper());
    private final List<Query> queries = new ArrayList<>();
    private final Map<Integer, Long> scalarResults = new LinkedHashMap<>();
    private final Map<Integer, List<Object[]>> rowResults = new LinkedHashMap<>();
    private int failAt = -1;
    private final IllegalStateException failure = new IllegalStateException("synthetic JDBC failure");

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
        int index = queries.size();
        Query query = new Query(sql);
        queries.add(query);
        JdbcClient.StatementSpec statement = mock(JdbcClient.StatementSpec.class, RETURNS_SELF);
        when(statement.param(anyString(), any())).thenAnswer(call -> {
            query.params.put(call.getArgument(0), call.getArgument(1));
            return statement;
        });
        JdbcClient.MappedQuerySpec<Long> scalar = mock(JdbcClient.MappedQuerySpec.class);
        when(statement.query(Long.class)).thenReturn(scalar);
        when(scalar.single()).thenAnswer(ignored -> {
            if (index == failAt) throw failure;
            return scalarResults.getOrDefault(index, 0L);
        });
        when(statement.query(any(RowMapper.class))).thenAnswer(call -> {
            RowMapper<?> mapper = call.getArgument(0);
            JdbcClient.MappedQuerySpec<Object> mapped = mock(JdbcClient.MappedQuerySpec.class);
            when(mapped.list()).thenAnswer(ignored -> {
                if (index == failAt) throw failure;
                List<Object> values = new ArrayList<>();
                for (Object[] columns : rowResults.getOrDefault(index, List.of())) {
                    ResultSet rs = mock(ResultSet.class);
                    when(rs.getLong(anyInt())).thenAnswer(read -> ((Number) columns[read.getArgument(0, Integer.class) - 1]).longValue());
                    when(rs.getString(anyInt())).thenAnswer(read -> (String) columns[read.getArgument(0, Integer.class) - 1]);
                    values.add(mapper.mapRow(rs, values.size()));
                }
                return values;
            });
            return mapped;
        });
        return statement;
    }

    private QualityReadRepository.Insight insight(Scope scope, int isolatedLimit, int topLimit) {
        QualityReadRepository.Insight result = repository.insight(scope, isolatedLimit, topLimit);
        assertEquals(10, queries.size(), "Q01-Q10 remain separately observable");
        return result;
    }

    @Test void allTenQueryResponsibilitiesAndLongResultsRemainSeparate() {
        scalarResults.put(0, 10_011L);
        scalarResults.put(1, 30_009L);
        scalarResults.put(2, 2_000L);
        scalarResults.put(3, 3L);
        scalarResults.put(4, 4L);
        scalarResults.put(5, 60L);
        scalarResults.put(8, 3_000_000_001L);
        rowResults.put(6, List.of(new Object[]{"concept", 100L}, new Object[]{null, 1L}));
        rowResults.put(7, List.<Object[]>of(new Object[]{4_294_967_301L, "shared", "concept", 5_000_000_000L}));
        rowResults.put(9, List.<Object[]>of(new Object[]{4_294_967_302L, "isolated", "concept", 0L}));

        var result = insight(Scope.project(), 1, 1);
        assertEquals(10_011L, result.nodeCount());
        assertEquals(30_009L, result.edgeCount());
        assertEquals(2_000L, result.documentCount());
        assertEquals(7L, result.pendingReviewCount(), "Entity and relation candidates add without cross-table deduplication");
        assertEquals(60L, result.typeTotal(), "Q06 is not inferred from the 50-row Q07 list");
        assertEquals(2, result.types().size());
        assertNull(result.types().get(1).type());
        assertEquals(4_294_967_301L, result.topDegree().get(0).id());
        assertEquals(5_000_000_000L, result.topDegree().get(0).degree());
        assertEquals(3_000_000_001L, result.isolatedTotal(), "Q09 is not inferred from the truncated Q10 list");
        assertEquals(new QualityReadRepository.NodeItem(4_294_967_302L, "isolated", "concept", 0L),
                result.isolated().get(0));

        assertTrue(queries.get(0).sql.endsWith("SELECT COUNT(*) FROM visible_nodes"));
        assertTrue(queries.get(1).sql.endsWith("SELECT COUNT(*) FROM visible_edges"));
        assertTrue(queries.get(2).sql.contains("SELECT COUNT(*) FROM documents d"));
        assertTrue(queries.get(3).sql.contains("FROM entity_candidates c"));
        assertTrue(queries.get(4).sql.contains("FROM relation_candidates c"));
        assertTrue(queries.get(5).sql.contains("SELECT COUNT(*) FROM (SELECT node_type FROM visible_nodes GROUP BY node_type)"));
        assertTrue(queries.get(6).sql.endsWith("GROUP BY node_type ORDER BY COUNT(*) DESC,node_type LIMIT :limit"));
        assertTrue(queries.get(7).sql.endsWith("ORDER BY degree DESC,n.id LIMIT :limit"));
        assertTrue(queries.get(8).sql.contains("SELECT COUNT(*) FROM visible_nodes n LEFT JOIN incident_nodes i"));
        assertTrue(queries.get(9).sql.endsWith("WHERE i.node_id IS NULL ORDER BY n.id LIMIT :limit"));
    }

    @Test void selectedScopeIsBoundBeforeEveryAggregateSortAndLimit() {
        insight(SELECTED, 4, 7);
        for (int i = 0; i < 10; i++) {
            assertEquals(SELECTED.libraryIds(), queries.get(i).params.get("libraryIds"), "Q" + (i + 1));
            if (i == 2 || i == 3 || i == 4) {
                assertTrue(queries.get(i).sql.contains("document_topic_assignments"), "Q" + (i + 1));
                assertTrue(queries.get(i).sql.contains("d.library_id IN (:libraryIds)"));
            } else {
                assertTrue(queries.get(i).sql.contains("EXISTS (SELECT 1 FROM library_nodes ln"), "Q" + (i + 1));
                assertFalse(queries.get(i).sql.contains("JOIN library_nodes"), "Shared memberships must not multiply rows");
            }
        }
        assertEquals(Map.of("libraryIds", SELECTED.libraryIds(), "limit", 50), queries.get(6).params);
        assertEquals(Map.of("libraryIds", SELECTED.libraryIds(), "limit", 7), queries.get(7).params);
        assertEquals(Map.of("libraryIds", SELECTED.libraryIds()), queries.get(8).params);
        assertEquals(Map.of("libraryIds", SELECTED.libraryIds(), "limit", 4), queries.get(9).params);
        assertTrue(queries.get(9).sql.indexOf("ln.library_id IN (:libraryIds)")
                < queries.get(9).sql.indexOf("LIMIT :limit"));
        assertFalse(queries.stream().anyMatch(q -> q.sql.contains("4294967301")));
    }

    @Test void isolatedListAggregatesVisibleActiveEndpointsBeforeAntiJoin() {
        insight(SELECTED, 2, 3);
        String count = queries.get(8).sql;
        String list = queries.get(9).sql;
        String incident = "incident_nodes AS (SELECT source_node_id AS node_id FROM visible_edges"
                + " UNION SELECT target_node_id AS node_id FROM visible_edges)";
        assertTrue(count.contains(incident));
        assertTrue(list.contains(incident));
        assertTrue(list.contains("JOIN visible_nodes s ON s.id=e.source_node_id"));
        assertTrue(list.contains("JOIN visible_nodes t ON t.id=e.target_node_id WHERE e.status='active'"));
        assertTrue(list.indexOf("visible_edges AS") < list.indexOf(incident));
        assertTrue(list.indexOf(incident) < list.indexOf("LEFT JOIN incident_nodes i"));
        assertTrue(list.contains("FROM visible_nodes n LEFT JOIN incident_nodes i ON i.node_id=n.id"));
        assertFalse(list.contains("NOT EXISTS (SELECT 1 FROM visible_edges"));
        assertFalse(list.contains("e.source_node_id=n.id OR e.target_node_id=n.id"));
        assertFalse(list.contains("UNION ALL SELECT target_node_id AS node_id"),
                "Self-loops and parallel edges must not multiply incident nodes");
        assertFalse(count.contains("LIMIT"));
        assertEquals(1, list.split("LIMIT", -1).length - 1);
        assertEquals(Map.of("libraryIds", SELECTED.libraryIds(), "limit", 2), queries.get(9).params);
    }

    @ParameterizedTest @ValueSource(ints = {1, 10, 100})
    void displayLimitsDoNotRestrictTheIsolatedTotal(int limit) {
        scalarResults.put(8, 101L);
        var result = insight(Scope.project(), limit, 1);
        assertEquals(101L, result.isolatedTotal());
        assertFalse(queries.get(8).sql.contains("LIMIT"));
        assertEquals(Map.of("limit", limit), queries.get(9).params);
        assertEquals(Map.of("limit", 1), queries.get(7).params);
    }

    @ParameterizedTest @MethodSource("queryPositions")
    void jdbcFailureAtAnyQueryPropagatesWithoutRunningLaterQueries(int index) {
        failAt = index;
        assertSame(failure, assertThrows(IllegalStateException.class,
                () -> repository.insight(Scope.project(), 2, 3)));
        assertEquals(index + 1, queries.size(), "No later query after Q" + (index + 1));
    }

    private static Stream<Integer> queryPositions() {
        return IntStream.range(0, 10).boxed();
    }

    @ParameterizedTest @MethodSource("invalidScopes")
    void unsupportedScopesStillFailBeforeAnySql(Scope scope) {
        assertEquals("UNSUPPORTED_SCOPE", assertThrows(IllegalArgumentException.class,
                () -> repository.insight(scope, 2, 3)).getMessage());
        verifyNoInteractions(jdbc);
    }

    private static Stream<Scope> invalidScopes() {
        return Stream.of(
                new Scope(ScopeMode.SELECTED, Set.of(), Set.of(8L), Set.of()),
                new Scope(ScopeMode.SELECTED, Set.of(7L), Set.of(8L), Set.of()),
                new Scope(ScopeMode.SELECTED, Set.of(7L), Set.of(), Set.of(9L)));
    }
}
