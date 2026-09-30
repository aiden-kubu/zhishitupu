package com.knowledgegraph.assistant.i2;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.IntStream;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="KG_I2_AUTHORIZED",matches="8c6cfcb3-ba49-11f1-bf64-005056c00001")
class I2ScaleQueryTest extends I2MySqlSupport {
    private static final com.knowledgegraph.assistant.contract.AssistantContracts.Scope SCALE=scope(Set.of(7901L),Set.of(),Set.of());
    @Test void scaledFixtureQueryWindowsAndScope() {
        assertEquals(10000,jdbc.sql("SELECT COUNT(*) FROM library_nodes WHERE library_id=7901").query(Integer.class).single());
        assertEquals(2000,ingestion.documents(SCALE,null,1,10).total());
        assertTrue(research.searchNodes(SCALE,"I2PERF",10).hasMore());
        assertTrue(research.matchingDocuments(SCALE,List.of("i2perf"),4).hasMore());
        assertEquals(4,learning.graph(SCALE,4,10).nodes().size());
        assertTrue(research.node(LIB,100000).isEmpty());
        assertTrue(source.chunk(LIB,300000,500000,4000).isEmpty());
    }
    @Test void scaledInsightAndSearchSingleAndFourConcurrentBudgets() throws Exception {
        List<Long> single=new ArrayList<>();for(int i=0;i<20;i++)single.add(measure());
        var executor=Executors.newFixedThreadPool(4);List<Long> parallel=new ArrayList<>();
        try { for(var f:executor.invokeAll(IntStream.range(0,20).<Callable<Long>>mapToObj(i->I2ScaleQueryTest::measure).toList())) parallel.add(f.get(10,TimeUnit.SECONDS)); }
        finally { executor.shutdownNow(); }
        Collections.sort(single);Collections.sort(parallel);
        System.out.println("I2_SCALE_PERF nodes=10000 edges=30000 docs=2000 chunks=20000 count=20 singleP95Ms="+single.get(18)+" concurrency=4 parallelP95Ms="+parallel.get(18));
        assertTrue(single.get(18)<=3000);assertTrue(parallel.get(18)<=3000);
    }
    private static long measure() {
        long start=System.nanoTime();var stats=quality.insight(SCALE,10,10);
        assertEquals(10000,stats.nodeCount());assertEquals(30000,stats.edgeCount());assertEquals(0,stats.isolatedTotal());
        assertEquals(10,research.searchNodes(SCALE,"I2PERF",10).items().size());
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-start);
    }
}
