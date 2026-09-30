package com.knowledgegraph.assistant.i2;

import com.fasterxml.jackson.databind.JsonNode;
import com.knowledgegraph.assistant.contract.AssistantContracts.Scope;
import com.knowledgegraph.assistant.quality.JdbcQualityReadRepository;
import com.knowledgegraph.assistant.quality.QualityReadRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.LongStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** R1-only real reader JDBC checks. Frozen connection budgets come from I2MySqlSupport. */
@EnabledIfEnvironmentVariable(named="KG_I2_AUTHORIZED", matches="8c6cfcb3-ba49-11f1-bf64-005056c00001")
class I2QualityInsightRankingTest extends I2MySqlSupport {
    private static final Scope SCALE = scope(Set.of(7901L), Set.of(), Set.of());
    private static final List<Long> FIRST_TEN = LongStream.range(100000,100010).boxed().toList();
    private record Degree(long id, long degree) {}

    private static void evidence(String kind, Object value) throws Exception {
        System.out.println("R1_EVIDENCE " + JSON.writeValueAsString(Map.of("kind",kind,"value",value)));
    }

    /** Capture the current repository SQL without executing it; execute that exact SQL below on MySQL. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static String currentRankSql() {
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
        new JdbcQualityReadRepository(capture,JSON).insight(SCALE,10,10);
        List<String> ranking = queries.stream().filter(s -> s.endsWith("ORDER BY degree DESC,n.id LIMIT :limit")).toList();
        assertEquals(1,ranking.size());
        return ranking.get(0);
    }

    @Test void realRankingPlanAndScaleTop() throws Exception {
        assertEquals(10000L,jdbc.sql("SELECT COUNT(*) FROM library_nodes WHERE library_id=7901").query(Long.class).single());
        assertEquals(2000,ingestion.documents(SCALE,null,1,10).total());
        assertEquals(20000L,jdbc.sql("SELECT COUNT(*) FROM document_chunks c JOIN document_units u ON u.id=c.unit_id JOIN documents d ON d.id=u.document_id WHERE d.library_id=7901").query(Long.class).single());
        String sql = currentRankSql();
        assertFalse(sql.contains("(SELECT COUNT(*) FROM visible_edges"));
        String planText = jdbc.sql("EXPLAIN FORMAT=JSON " + sql).param("libraryIds",Set.of(7901L))
                .param("limit",10).query(String.class).single();
        JsonNode plan = JSON.readTree(planText);
        evidence("ranking_plan",Map.of("sql",sql,"libraryIds",List.of(7901),"limit",10,"plan",plan));
        assertFalse(plan.findValues("dependent").stream().anyMatch(JsonNode::asBoolean),
                "New degree plan must not retain a dependent per-node edge scan");
        long start = System.nanoTime();
        List<Degree> actual = jdbc.sql(sql).param("libraryIds",Set.of(7901L)).param("limit",10)
                .query((rs,i) -> new Degree(rs.getLong(1),rs.getLong(4))).list();
        evidence("scale_direct_ranking",Map.of("rows",actual,"elapsedMs",elapsed(start),"readerOnly",true));
        assertEquals(FIRST_TEN,actual.stream().map(Degree::id).toList());
        assertTrue(actual.stream().allMatch(d -> d.degree()==6));
    }

    @Test void realSemanticMatrixAndProjectOracle() throws Exception {
        semantic("library_7910",Set.of(7910L),10,4,5,1,List.of(
                new Degree(900001,4),new Degree(900002,4),new Degree(900005,1),new Degree(900006,0)));
        semantic("union_7910_7911",Set.of(7910L,7911L),10,5,6,1,List.of(
                new Degree(900001,5),new Degree(900002,4),new Degree(900003,1),new Degree(900005,1),new Degree(900006,0)));
        semantic("library_7911",Set.of(7911L),10,2,2,0,List.of(new Degree(900001,2),new Degree(900003,1)));
        semantic("outside_library_7912",Set.of(7912L),10,1,0,1,List.of(new Degree(900004,0)));
        semantic("empty_7913",Set.of(7913L),10,0,0,0,List.of());
        semantic("top_limit_one",Set.of(7910L),1,4,5,1,List.of(new Degree(900001,4)));
        semantic("top_limit_three",Set.of(7910L,7911L),3,5,6,1,List.of(
                new Degree(900001,5),new Degree(900002,4),new Degree(900003,1)));
        // Independent oracle: read actual node/edge rows and count each incident edge once in Java.
        Map<Long,Long> degree = new HashMap<>();
        jdbc.sql("SELECT id FROM knowledge_nodes").query(Long.class).list().forEach(id -> degree.put(id,0L));
        List<long[]> edges = jdbc.sql("SELECT source_node_id,target_node_id FROM knowledge_edges WHERE status='active'")
                .query((rs,i) -> new long[]{rs.getLong(1),rs.getLong(2)}).list();
        long visibleEdges = 0;
        for (long[] edge : edges) {
            if (!degree.containsKey(edge[0]) || !degree.containsKey(edge[1])) continue;
            visibleEdges++;
            degree.compute(edge[0],(k,v) -> v+1);
            if (edge[1]!=edge[0]) degree.compute(edge[1],(k,v) -> v+1);
        }
        List<Degree> expected = degree.entrySet().stream().map(e -> new Degree(e.getKey(),e.getValue()))
                .sorted(Comparator.comparingLong(Degree::degree).reversed().thenComparingLong(Degree::id)).limit(10).toList();
        long isolated = degree.values().stream().filter(d -> d==0).count();
        evidence("project_oracle",Map.of("nodeCount",degree.size(),"edgeCount",visibleEdges,"isolated",isolated,"top",expected));
        check("project",Scope.project(),10,degree.size(),visibleEdges,isolated,expected);
        assertThrows(IllegalArgumentException.class,() -> quality.insight(A,10,10));
    }

    private static void semantic(String label, Set<Long> libraries, int limit, long nodes, long edges,
                                 long isolated, List<Degree> expected) throws Exception {
        check(label,scope(libraries,Set.of(),Set.of()),limit,nodes,edges,isolated,expected);
    }

    private static void check(String label, Scope scope, int limit, long nodes, long edges,
                              long isolated, List<Degree> expected) throws Exception {
        var result = quality.insight(scope,10,limit);
        var actual = degrees(result);
        evidence("semantic",Map.of("case",label,"nodeCount",result.nodeCount(),"edgeCount",result.edgeCount(),
                "isolated",result.isolatedTotal(),"expectedTop",expected,"actualTop",actual));
        assertEquals(nodes,result.nodeCount(),label); assertEquals(edges,result.edgeCount(),label);
        assertEquals(isolated,result.isolatedTotal(),label); assertEquals(expected,actual,label);
        if (label.equals("library_7910")) assertEquals(List.of(900006L),result.isolated().stream().map(QualityReadRepository.NodeItem::id).toList());
    }

    private static List<Degree> degrees(QualityReadRepository.Insight result) {
        return result.topDegree().stream().map(n -> new Degree(n.id(),n.degree())).toList();
    }

    @Test void fullScaleSingleAndFourConcurrentSamples() throws Exception {
        List<Long> single = new ArrayList<>();
        for (int i=1;i<=20;i++) single.add(measure("single",i));
        var executor = Executors.newFixedThreadPool(4);
        var completion = new ExecutorCompletionService<Long>(executor);
        List<Future<Long>> futures = new ArrayList<>();
        List<Long> concurrent = new ArrayList<>();
        int next = 1;
        try {
            for (;next<=4;next++) { final int sample=next; futures.add(completion.submit(() -> measure("concurrent4",sample))); }
            for (int done=0;done<20;done++) {
                concurrent.add(completion.take().get());
                if (next<=20) { final int sample=next++; futures.add(completion.submit(() -> measure("concurrent4",sample))); }
            }
        } finally {
            futures.forEach(f -> { if (!f.isDone()) f.cancel(true); });
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(10,TimeUnit.SECONDS),"Reader workers must settle before snapshot");
        }
        long single95=p95(single), concurrent95=p95(concurrent);
        evidence("scale_p95",Map.of("singleCount",single.size(),"concurrentCount",concurrent.size(),
                "singleP95Ms",single95,"concurrentP95Ms",concurrent95,"rank",19,"limitMs",3000,"warmupDiscarded",0));
        assertTrue(single95<=3000); assertTrue(concurrent95<=3000);
    }

    private static long measure(String mode, int sample) throws Exception {
        long start=System.nanoTime();
        try {
            var result=quality.insight(SCALE,10,10);
            assertEquals(10000,result.nodeCount()); assertEquals(30000,result.edgeCount()); assertEquals(0,result.isolatedTotal());
            assertEquals(FIRST_TEN,result.topDegree().stream().map(QualityReadRepository.NodeItem::id).toList());
            assertTrue(result.topDegree().stream().allMatch(n -> n.degree()==6));
            assertEquals(10,research.searchNodes(SCALE,"I2PERF",10).items().size());
            long ms=elapsed(start);
            evidence("scale_sample",Map.of("mode",mode,"sample",sample,"elapsedMs",ms,"status","ok"));
            return ms;
        } catch (Exception | AssertionError error) {
            evidence("scale_sample",Map.of("mode",mode,"sample",sample,"elapsedMs",elapsed(start),
                    "status","error","error",String.valueOf(error.getMessage())));
            throw error;
        }
    }

    private static long elapsed(long start) { return TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-start); }
    private static long p95(List<Long> samples) { assertEquals(20,samples.size()); return samples.stream().sorted().toList().get(18); }
}
