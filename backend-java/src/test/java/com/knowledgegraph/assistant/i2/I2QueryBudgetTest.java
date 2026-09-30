package com.knowledgegraph.assistant.i2;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.sql.SQLException;
import java.sql.SQLTimeoutException;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.IntStream;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="KG_I2_AUTHORIZED",matches="8c6cfcb3-ba49-11f1-bf64-005056c00001")
class I2QueryBudgetTest extends I2MySqlSupport {
    @Test void serverTimeoutReallyCancelsStatementAndKeepsConnectionUsable() throws Exception {
        try(var c=dataSource.getConnection();var s=c.createStatement()) {
            String tables=String.join(" CROSS JOIN ",IntStream.range(0,12).mapToObj(i->"knowledge_nodes n"+i).toList());
            long start=System.nanoTime();
            SQLException error=assertThrows(SQLException.class,()->s.executeQuery("SELECT /*+ MAX_EXECUTION_TIME(200) */ SUM(n0.id+n11.id) FROM "+tables));
            long elapsed=TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-start);
            assertEquals(3024,error.getErrorCode()); assertTrue(elapsed<3000,"Server did not cancel within budget");
            try(var result=s.executeQuery("SELECT 1")) { assertTrue(result.next()); assertEquals(1,result.getInt(1)); }
            System.out.println("I2_SERVER_TIMEOUT error=3024 elapsedMs="+elapsed+" sameConnectionReusable=true");
        }
    }
    @Test void jdbcTimeoutInterruptsServerQuery() throws Exception {
        try(var c=dataSource.getConnection();var s=c.prepareStatement("SELECT SLEEP(5) /* I2_JDBC_TIMEOUT */")) {
            s.setQueryTimeout(1); long start=System.nanoTime();
            SQLException error=assertThrows(SQLException.class,s::executeQuery);
            long elapsed=TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-start);
            assertTrue(error instanceof SQLTimeoutException || error.getClass().getSimpleName().contains("Timeout"),"Must be JDBC timeout, not another SQL failure");
            assertTrue(elapsed<3500); try(var check=c.createStatement();var r=check.executeQuery("SELECT 1")) { assertTrue(r.next()); }
            System.out.println("I2_JDBC_TIMEOUT elapsedMs="+elapsed+" sameConnectionReusable=true");
        }
    }
    @Test void smallFixtureSingleAndFourConcurrentReadBudgets() throws Exception {
        List<Long> single=new ArrayList<>(); for(int i=0;i<20;i++) single.add(measure());
        var executor=Executors.newFixedThreadPool(4);
        List<Long> parallel=new ArrayList<>();
        try { var tasks=IntStream.range(0,20).<Callable<Long>>mapToObj(i->I2QueryBudgetTest::measure).toList(); for(var f:executor.invokeAll(tasks)) parallel.add(f.get(10,TimeUnit.SECONDS)); }
        finally { executor.shutdownNow(); }
        Collections.sort(single);Collections.sort(parallel);
        long p95=single.get(18), parallel95=parallel.get(18);
        System.out.println("I2_SMALL_PERF count=20 singleP95Ms="+p95+" concurrency=4 parallelP95Ms="+parallel95);
        assertTrue(p95<=1000);assertTrue(parallel95<=1000);
    }
    private static long measure() {
        long start=System.nanoTime(); assertEquals(4,quality.insight(LIB,10,10).nodeCount());
        assertEquals(7701,research.searchNodes(A,"节点甲",10).items().get(0).id());
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-start);
    }
}
