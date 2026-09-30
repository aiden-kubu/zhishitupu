package com.knowledgegraph.assistant.i2;

import com.fasterxml.jackson.databind.JsonNode;
import com.knowledgegraph.assistant.contract.AssistantContracts.Scope;
import com.knowledgegraph.assistant.quality.JdbcQualityReadRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.util.*;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Integration-only R2 evidence. Runs the repository's exact COUNT under frozen reader budgets. */
@EnabledIfEnvironmentVariable(named="KG_I2_AUTHORIZED", matches="8c6cfcb3-ba49-11f1-bf64-005056c00001")
class I2QualityIsolatedCountTest extends I2MySqlSupport {
    private record Case(String name, Set<Long> libraries, long expected) {}
    private static void evidence(String kind, Object value) throws Exception {
        System.out.println("R2_EVIDENCE " + JSON.writeValueAsString(Map.of("kind",kind,"value",value)));
    }
    @SuppressWarnings({"unchecked","rawtypes"})
    private static String currentCountSql(Scope scope) {
        JdbcClient capture = mock(JdbcClient.class);
        JdbcClient.StatementSpec statement = mock(JdbcClient.StatementSpec.class, RETURNS_SELF);
        JdbcClient.MappedQuerySpec<Long> numbers = mock(JdbcClient.MappedQuerySpec.class);
        JdbcClient.MappedQuerySpec<Object> rows = mock(JdbcClient.MappedQuerySpec.class);
        List<String> queries = new ArrayList<>();
        when(capture.sql(anyString())).thenAnswer(c -> { queries.add(c.getArgument(0)); return statement; });
        when(statement.query(Long.class)).thenReturn(numbers);
        when(numbers.single()).thenReturn(0L);
        when(statement.query(any(RowMapper.class))).thenReturn((JdbcClient.MappedQuerySpec)rows);
        when(rows.list()).thenReturn(List.of());
        new JdbcQualityReadRepository(capture,JSON).insight(scope,10,10);
        assertEquals(10,queries.size());
        String count = queries.get(8);
        assertTrue(count.contains("LEFT JOIN incident_nodes"));
        assertFalse(count.contains("LIMIT"));
        return count;
    }
    @Test void realCountPlansAndIndependentSemantics() throws Exception {
        // Independent row oracle, not a rewritten domain adapter. Never used to serve business results.
        Set<Long> allNodes = new HashSet<>(jdbc.sql("SELECT id FROM knowledge_nodes").query(Long.class).list());
        List<long[]> membership = jdbc.sql("SELECT library_id,node_id FROM library_nodes")
                .query((rs,i)->new long[]{rs.getLong(1),rs.getLong(2)}).list();
        List<long[]> activeEdges = jdbc.sql("SELECT source_node_id,target_node_id FROM knowledge_edges WHERE status='active'")
                .query((rs,i)->new long[]{rs.getLong(1),rs.getLong(2)}).list();
        List<Case> cases = List.of(
                new Case("library_7910",Set.of(7910L),1),
                new Case("union_shared_7910_7911",Set.of(7910L,7911L),1),
                new Case("self_reverse_parallel_7911",Set.of(7911L),0),
                new Case("outside_only_7912",Set.of(7912L),1),
                new Case("empty_7913",Set.of(7913L),0),
                new Case("scale_7901",Set.of(7901L),0),
                new Case("project",Set.of(),3));
        for (Case c : cases) {
            Set<Long> visible = new HashSet<>();
            if (c.libraries().isEmpty()) visible.addAll(allNodes);
            else for (long[] m : membership) if (c.libraries().contains(m[0]) && allNodes.contains(m[1])) visible.add(m[1]);
            Set<Long> isolated = new HashSet<>(visible);
            for (long[] edge : activeEdges) {
                if (visible.contains(edge[0]) && visible.contains(edge[1])) {
                    isolated.remove(edge[0]); isolated.remove(edge[1]);
                }
            }
            assertEquals(c.expected(),isolated.size(),"Retained fixture oracle: "+c.name());
            Scope selected = c.libraries().isEmpty() ? Scope.project() : scope(c.libraries(),Set.of(),Set.of());
            String sql = currentCountSql(selected);
            if (c.name().equals("scale_7901") || c.name().equals("project")) {
                var statement = jdbc.sql("EXPLAIN FORMAT=JSON "+sql);
                if (!c.libraries().isEmpty()) statement.param("libraryIds",c.libraries());
                JsonNode plan = JSON.readTree(statement.query(String.class).single());
                evidence("isolated_count_plan",Map.of("case",c.name(),"sql",sql,"libraries",c.libraries(),"plan",plan));
                assertFalse(plan.findValues("dependent").stream().anyMatch(JsonNode::asBoolean),
                        "COUNT must not retain a dependent per-node edge scan");
            }
            var statement = jdbc.sql(sql);
            if (!c.libraries().isEmpty()) statement.param("libraryIds",c.libraries());
            long start=System.nanoTime();
            try {
                long actual=statement.query(Long.class).single();
                evidence("isolated_count",Map.of("case",c.name(),"expected",c.expected(),"oracle",isolated.size(),
                        "actual",actual,"elapsedMs",TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-start),"sql",sql));
                assertEquals(isolated.size(),actual,c.name());
            } catch (Exception | AssertionError error) {
                evidence("isolated_count_error",Map.of("case",c.name(),"sql",sql,"error",String.valueOf(error.getMessage()),
                        "elapsedMs",TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-start)));
                throw error;
            }
        }
    }
}
