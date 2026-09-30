package com.knowledgegraph.assistant.i2;

import com.knowledgegraph.assistant.contract.AssistantContracts.Scope;
import com.knowledgegraph.assistant.research.JdbcResearchReadRepository;
import com.knowledgegraph.assistant.research.ResearchReadRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterUtils;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DelegatingDataSource;

import javax.sql.DataSource;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.LongStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Approved I2-R4 integration evidence only; production repository and SQL are unchanged. */
@EnabledIfEnvironmentVariable(named="KG_I2_AUTHORIZED", matches="8c6cfcb3-ba49-11f1-bf64-005056c00001")
class I2ResearchEdgeVerificationTest extends I2MySqlSupport {
    private static final Set<Long> SCALE_DOCS = LongStream.range(300000, 302000).boxed().collect(java.util.stream.Collectors.toUnmodifiableSet());
    private static final Set<Long> MIXED_DOCS;
    static { var docs = new java.util.HashSet<>(SCALE_DOCS); docs.add(7201L); docs.add(7202L); MIXED_DOCS = Set.copyOf(docs); }
    private static final Scope R1A = scope(Set.of(7910L), Set.of(), Set.of());
    private static Scope scaleDocumentScope(){return scope(Set.of(7901L),SCALE_DOCS,Set.of());}
    private static Scope mixedDocumentScope(){return scope(Set.of(7101L,7901L),MIXED_DOCS,Set.of());}
    private record Case(String id, boolean outgoing, Scope scope, long nodeId, int limit, List<Long> ids,
                        boolean hasMore, Boolean hasSource, String status) {}
    private static final class Captured { String template; final Map<String,Object> parameters=new LinkedHashMap<>(); }
    private static final class Trace {
        String sql; Long sessionMs; Integer timeoutSeconds; int executions;
        final Map<Integer,Map<String,Object>> bindings=new TreeMap<>();
    }

    private static Case approved(String id) {
        return switch (id == null ? "" : id) {
            case "V01" -> new Case(id,true,AB,7702,10,List.of(),false,null,null);
            case "V02" -> new Case(id,true,AB,7701,10,List.of(7801L),false,true,"active");
            case "V03" -> new Case(id,false,AB,7701,10,List.of(7801L),false,true,"active");
            case "V04" -> new Case(id,false,LIB,7701,10,List.of(7801L,7802L),false,null,null);
            case "V05" -> new Case(id,true,scope(Set.of(),Set.of(7201L,7202L),Set.of()),7701,10,List.of(7801L),false,true,"active");
            case "V06" -> new Case(id,true,A,7701,10,List.of(7801L),false,true,"active");
            case "V07" -> new Case(id,true,scope(Set.of(7101L),Set.of(7202L),Set.of()),7701,10,List.of(7801L),false,true,"active");
            case "V08" -> new Case(id,true,scope(Set.of(7101L),Set.of(7201L,7202L),Set.of(7701L)),7701,10,List.of(),false,null,null);
            case "V09" -> new Case(id,true,scope(Set.of(7101L),Set.of(7201L,7202L),Set.of(7701L,7702L)),7701,10,List.of(7801L),false,true,"active");
            case "V10" -> new Case(id,true,scope(Set.of(7102L),Set.of(7201L,7202L),Set.of()),7701,10,List.of(),false,null,null);
            case "V11" -> new Case(id,false,R1A,900001,2,List.of(910001L,910002L),true,null,null);
            case "V12" -> new Case(id,true,R1A,900001,10,List.of(910001L,910002L,910004L,910006L),false,null,null);
            case "V13" -> new Case(id,false,Scope.project(),7701,10,List.of(7801L,7802L,7803L),false,null,null);
            case "V14" -> new Case(id,true,scaleDocumentScope(),100000,10,List.of(),false,null,null);
            case "V15" -> new Case(id,true,mixedDocumentScope(),7701,10,List.of(7801L),false,true,"active");
            case "V16" -> new Case(id,false,mixedDocumentScope(),7701,10,List.of(7801L),false,true,"active");
            case "V17" -> new Case(id,true,AB,7701,1,List.of(7801L),false,true,"active");
            default -> throw new IllegalArgumentException("Unapproved matrix case: " + id);
        };
    }

    @Test void executeOneApprovedMatrixCaseAndExplain() throws Exception {
        String id=System.getenv("KG_I2_VERIFY_CASE"), dir=System.getenv("KG_I2_VERIFY_EVIDENCE_DIR");
        assertNotNull(dir); Path output=Path.of(dir,id+".json");
        assertTrue(Files.isDirectory(output.getParent())); assertFalse(Files.exists(output));
        Case c;
        try{c=approved(id);}catch(IllegalArgumentException ex){
            if(!Set.of("V14","V15","V16").contains(id))throw ex;
            Set<Long> plannedDocs=id.equals("V14")?SCALE_DOCS:MIXED_DOCS;
            Map<String,Object> blocked=new LinkedHashMap<>();blocked.put("case",id);blocked.put("status","blocked_scope_contract_limit");
            blocked.put("businessStatus","not_executed");blocked.put("explainStatus","not_executed");
            blocked.put("businessExecuteCount",0);blocked.put("explainExecuteCount",0);blocked.put("passed",false);blocked.put("safetyFailure",false);
            blocked.put("plannedDocumentCount",plannedDocs.size());blocked.put("plannedDocumentIds",plannedDocs.stream().sorted().toList());
            blocked.put("scopeIdLimit",1000);blocked.put("reason",ex.toString());blocked.put("parametersReduced",false);
            Files.writeString(output,JSON.writerWithDefaultPrettyPrinter().writeValueAsString(blocked),StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE);
            fail("Approved scope exceeds frozen 1000-ID contract; invocation and EXPLAIN not executed");return;
        }
        Map<String,Object> evidence=new LinkedHashMap<>(); evidence.put("case",id);
        evidence.put("startedAt",Instant.now().toString()); evidence.put("nodeId",c.nodeId);
        evidence.put("limit",c.limit); evidence.put("scope",scopeRecord(c.scope));
        evidence.put("expectedIds",c.ids); evidence.put("expectedHasMore",c.hasMore);
        boolean passed=true, safetyFailure=false;
        evidence.put("businessExecuteCount",1);
        long businessStarted=System.nanoTime();
        try {
            long started=System.nanoTime();
            ResearchReadRepository.Window<ResearchReadRepository.Edge> result = call(c);
            evidence.put("businessElapsedMs",elapsed(started));
            List<Long> ids=result.items().stream().map(ResearchReadRepository.Edge::id).toList();
            evidence.put("actualIds",ids); evidence.put("actualHasMore",result.hasMore());
            evidence.put("actualEdges",result.items().stream().map(e -> Map.of("id",e.id(),"source",e.sourceNodeId(),
                    "target",e.targetNodeId(),"status",e.status(),"hasSource",e.sourceEvidenceAvailable(),"relation",e.relationType())).toList());
            assertEquals(c.ids,ids,"Complete edge order/IDs"); assertEquals(c.hasMore,result.hasMore(),"hasMore");
            if(c.hasSource!=null) for(var edge:result.items()) assertEquals(c.hasSource,edge.sourceEvidenceAvailable(),"has_source");
            if(c.status!=null) for(var edge:result.items()) assertEquals(c.status,edge.status(),"status");
            if(id.equals("V02")||id.equals("V03")||id.equals("V15")||id.equals("V16")) {
                var edge=result.items().get(0); assertEquals(7701,edge.sourceNodeId()); assertEquals(7702,edge.targetNodeId());
            }
            if(id.equals("V04")){assertEquals("inactive",result.items().get(1).status());assertFalse(result.items().get(1).sourceEvidenceAvailable());}
            if(id.equals("V12")) checkV12(result);
            evidence.put("businessStatus","passed");
        } catch(Throwable ex) { passed=false; safetyFailure=isSafetyFailure(ex); evidence.put("businessStatus","failed"); evidence.put("businessError",ex.toString()); evidence.put("businessElapsedMs",elapsed(businessStarted)); }
        Trace trace=new Trace();
        try {
            if(safetyFailure) throw new IllegalStateException("EXPLAIN not executed after safety failure");
            Captured captured=capture(c);
            evidence.put("sqlTemplate",captured.template);
            evidence.put("namedParameters",printable(captured.parameters));
            MapSqlParameterSource params=new MapSqlParameterSource(); captured.parameters.forEach(params::addValue);
            String expected="EXPLAIN FORMAT=JSON "+NamedParameterUtils.substituteNamedParameters(
                    NamedParameterUtils.parseSqlStatement(captured.template),params);
            JdbcTemplate explainTemplate=new JdbcTemplate(recordingDataSource(trace)); explainTemplate.setQueryTimeout(2);
            JdbcClient.StatementSpec statement=JdbcClient.create(explainTemplate).sql("EXPLAIN FORMAT=JSON "+captured.template);
            for(var entry:captured.parameters.entrySet()) statement=statement.param(entry.getKey(),entry.getValue());
            long started=System.nanoTime();
            try { String plan=statement.query(String.class).single(); evidence.put("plan",JSON.readTree(plan)); evidence.put("explainStatus","plan_captured"); }
            catch(Throwable ex) { passed=false; safetyFailure |= isSafetyFailure(ex); evidence.put("explainStatus","error"); evidence.put("explainError",ex.toString());
                for(Throwable t=ex;t!=null;t=t.getCause()) if(t instanceof SQLException sql){evidence.put("sqlState",sql.getSQLState());evidence.put("vendorCode",sql.getErrorCode());break;} }
            evidence.put("explainElapsedMs",elapsed(started));
            assertEquals(1,trace.executions,"Exactly one EXPLAIN");
            assertEquals(1500L,trace.sessionMs); assertEquals(2,trace.timeoutSeconds);
            assertEquals(canonical(expected),canonical(trace.sql),"Expanded SQL equals captured repository SQL");
            assertEquals(trace.sql.chars().filter(ch->ch=='?').count(),trace.bindings.size(),"All bindings observed");
        } catch(Throwable ex) { passed=false; safetyFailure |= isSafetyFailure(ex); evidence.put("traceError",ex.toString()); }
        evidence.put("preparedSql",trace.sql); evidence.put("preparedSqlSha256",trace.sql==null?null:sha256(trace.sql));
        evidence.put("orderedBindings",new ArrayList<>(trace.bindings.values())); evidence.put("explainExecuteCount",trace.executions);
        evidence.put("sessionMaxExecutionTimeMs",trace.sessionMs); evidence.put("jdbcTimeoutSeconds",trace.timeoutSeconds);
        evidence.put("safetyFailure",safetyFailure); evidence.put("passed",passed); evidence.put("finishedAt",Instant.now().toString());
        Files.writeString(output,JSON.writerWithDefaultPrettyPrinter().writeValueAsString(evidence),StandardCharsets.UTF_8,
                StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE);
        assertTrue(passed,"Case " + id + " failed; inspect evidence");
    }

    @Test void sampleNonemptyMixedDocumentBranch() throws Exception {
        String dir=System.getenv("KG_I2_VERIFY_EVIDENCE_DIR"); assertNotNull(dir);
        Path output=Path.of(dir,"NEW-PERFORMANCE.json"); assertFalse(Files.exists(output));
        List<Map<String,Object>> samples=java.util.Collections.synchronizedList(new ArrayList<>());
        List<Long> single=new ArrayList<>(), concurrent=new ArrayList<>(); Throwable failure=null;
        try {
            for(int i=1;i<=20;i++) single.add(measure("single",i,samples));
            var workers=Executors.newFixedThreadPool(4); var completed=new ExecutorCompletionService<Long>(workers);
            List<Future<Long>> futures=new ArrayList<>(); int next=1;
            try {
                for(;next<=4;next++){final int n=next;futures.add(completed.submit(()->measure("concurrent4",n,samples)));}
                for(int done=0;done<20;done++){concurrent.add(completed.take().get());if(next<=20){final int n=next++;futures.add(completed.submit(()->measure("concurrent4",n,samples)));}}
            } finally {
                for(var future:futures) if(!future.isDone()) future.cancel(true);
                workers.shutdownNow(); assertTrue(workers.awaitTermination(10,TimeUnit.SECONDS),"Workers must settle");
            }
        } catch(Throwable ex) { failure=ex; }
        Map<String,Object> evidence=new LinkedHashMap<>(); evidence.put("samples",samples);
        evidence.put("singleComplete",single.size());evidence.put("concurrentComplete",concurrent.size());
        evidence.put("warmupDiscarded",0);evidence.put("rank",19);evidence.put("limitMs",3000);
        if(single.size()==20 && concurrent.size()==20){evidence.put("singleP95Ms",p95(single));evidence.put("concurrentP95Ms",p95(concurrent));
            if(p95(single)>3000||p95(concurrent)>3000) failure=new AssertionError("New branch p95 exceeded 3000 ms");}
        if(failure!=null) evidence.put("error",failure.toString()); evidence.put("passed",failure==null);
        Files.writeString(output,JSON.writerWithDefaultPrettyPrinter().writeValueAsString(evidence),StandardCharsets.UTF_8,
                StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE);
        if(failure!=null) fail("New branch sample failed; inspect evidence: " + failure);
    }

    private static long measure(String mode,int number,List<Map<String,Object>> samples) throws Exception {
        return measure(mode,number,samples,mixedDocumentScope());
    }
    private static long measure(String mode,int number,List<Map<String,Object>> samples,Scope sampleScope) throws Exception {
        String startedAt=Instant.now().toString(); long start=System.nanoTime(); Long groupMs=null; Throwable failure=null;
        Map<String,Object> outgoing=new LinkedHashMap<>(),incident=new LinkedHashMap<>();
        outgoing.put("status","not_executed");outgoing.put("elapsedMs",null);
        incident.put("status","not_executed");incident.put("elapsedMs",null);
        try { sampleCall(true,outgoing,sampleScope);sampleCall(false,incident,sampleScope); }
        catch(Throwable ex){failure=ex;}
        finally {
            groupMs=elapsed(start);
            Map<String,Object> record=new LinkedHashMap<>();record.put("mode",mode);record.put("sample",number);
            record.put("startedAt",startedAt);record.put("finishedAt",Instant.now().toString());record.put("outgoing",outgoing);
            record.put("incident",incident);record.put("groupMs",groupMs);record.put("status",failure==null?"ok":"error");
            record.put("error",failure==null?null:failure.toString());record.put("safetyFailure",failure!=null&&isSafetyFailure(failure));
            record.put("scope",scopeRecord(sampleScope));record.put("expectedIds",List.of(7801L));samples.add(record);
        }
        if(failure instanceof Error error)throw error;if(failure instanceof Exception ex)throw ex;
        return groupMs;
    }
    private static void sampleCall(boolean outgoing,Map<String,Object> evidence,Scope sampleScope){
        evidence.put("status","started");long start=System.nanoTime();
        try{var result=outgoing?research.outgoingEdges(sampleScope,7701,10):research.incidentEdges(sampleScope,7701,10);
            evidence.put("actualIds",result.items().stream().map(ResearchReadRepository.Edge::id).toList());
            evidence.put("actualHasMore",result.hasMore());evidence.put("actualEdges",result.items());
            checkSample(result);evidence.put("status","passed");
        }catch(RuntimeException|Error ex){evidence.put("status","failed");evidence.put("error",ex.toString());throw ex;}
        finally{evidence.put("elapsedMs",elapsed(start));}
    }
    private static void checkV12(ResearchReadRepository.Window<ResearchReadRepository.Edge> result){
        assertEquals(List.of(910001L,910002L,910004L,910006L),result.items().stream().map(ResearchReadRepository.Edge::id).toList());
        var loop=result.items().get(0);assertEquals(900001,loop.sourceNodeId());assertEquals(900001,loop.targetNodeId());
        assertEquals("SELF",loop.relationType());
        var inactive=result.items().get(3);assertEquals(900001,inactive.sourceNodeId());assertEquals(900002,inactive.targetNodeId());
        assertEquals("inactive",inactive.status());assertEquals("INACTIVE",inactive.relationType());
        assertEquals("R",result.items().get(1).relationType());assertEquals("R2",result.items().get(2).relationType());
    }
    private static boolean isSafetyFailure(Throwable ex){
        for(Throwable t=ex;t!=null;t=t.getCause()){
            if(t instanceof SQLException sql){int code=sql.getErrorCode();String state=sql.getSQLState();
                if(code==1044||code==1045||code==1142||code==1143||(state!=null&&state.startsWith("28")))return true;}
            if(String.valueOf(t.getMessage()).contains("Session budget changed"))return true;
        }return false;
    }
    /** Offline self-check entry; it does not invoke the inherited database lifecycle. */
    public static void main(String[] args)throws Exception{
        var loop=new ResearchReadRepository.Edge(910001,900001,900001,"SELF","active","",null,null,false);
        var one=new ResearchReadRepository.Edge(910002,900001,900002,"R","active","",null,null,false);
        var two=new ResearchReadRepository.Edge(910004,900001,900002,"R2","active","",null,null,false);
        var inactive=new ResearchReadRepository.Edge(910006,900001,900002,"INACTIVE","inactive","",null,null,false);
        checkV12(new ResearchReadRepository.Window<>(List.of(loop,one,two,inactive),false));
        var wrong=new ResearchReadRepository.Edge(910006,900001,900001,"INACTIVE","inactive","",null,null,false);
        assertThrows(AssertionError.class,()->checkV12(new ResearchReadRepository.Window<>(List.of(loop,one,two,wrong),false)));
        var edge=new ResearchReadRepository.Edge(7801,7701,7702,"R","active","",null,null,true);
        var window=new ResearchReadRepository.Window<>(List.of(edge),false);
        research=mock(JdbcResearchReadRepository.class);when(research.outgoingEdges(any(),eq(7701L),eq(10))).thenReturn(window);
        when(research.incidentEdges(any(),eq(7701L),eq(10))).thenReturn(window);
        List<Map<String,Object>> samples=new ArrayList<>();long returned=measure("selfcheck",1,samples,AB);
        assertEquals(returned,samples.get(0).get("groupMs"));
        when(research.outgoingEdges(any(),eq(7701L),eq(10))).thenThrow(new IllegalStateException("ordinary fixture failure"));
        assertThrows(IllegalStateException.class,()->measure("selfcheck",2,samples,AB));
        var notExecuted=(Map<?,?>)samples.get(1).get("incident");assertEquals("not_executed",notExecuted.get("status"));assertNull(notExecuted.get("elapsedMs"));
        List<Long> times=LongStream.rangeClosed(1,20).boxed().toList();assertEquals(19,p95(times));
        assertThrows(IllegalArgumentException.class,()->approved("V14"));assertThrows(IllegalArgumentException.class,()->approved("V15"));assertThrows(IllegalArgumentException.class,()->approved("V16"));
        System.out.println("OFFLINE_SELF_CHECK "+JSON.writeValueAsString(Map.of("v12CorrectAccepted",true,"v12WrongRejected",true,"timingIdentical",true,"notExecutedNull",true,"p95Rank19",true,"scopeLimitRecorded",true,"databaseUsed",false)));
    }
    private static void checkSample(ResearchReadRepository.Window<ResearchReadRepository.Edge> window){
        assertEquals(List.of(7801L),window.items().stream().map(ResearchReadRepository.Edge::id).toList());
        assertFalse(window.hasMore());var edge=window.items().get(0);assertEquals(7701,edge.sourceNodeId());
        assertEquals(7702,edge.targetNodeId());assertTrue(edge.sourceEvidenceAvailable());
    }
    private static long p95(List<Long> samples){return samples.stream().sorted().toList().get(18);}
    private static long elapsed(long start){return TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-start);}
    private static ResearchReadRepository.Window<ResearchReadRepository.Edge> call(Case c){
        return c.outgoing ? research.outgoingEdges(c.scope,c.nodeId,c.limit) : research.incidentEdges(c.scope,c.nodeId,c.limit);
    }
    @SuppressWarnings({"unchecked","rawtypes"})
    private static Captured capture(Case c){
        Captured result=new Captured();JdbcClient mockJdbc=mock(JdbcClient.class);
        JdbcClient.StatementSpec statement=mock(JdbcClient.StatementSpec.class,RETURNS_SELF);
        JdbcClient.MappedQuerySpec<Object> rows=mock(JdbcClient.MappedQuerySpec.class);
        when(mockJdbc.sql(anyString())).thenAnswer(inv->{assertNull(result.template,"One business statement");result.template=inv.getArgument(0);return statement;});
        when(statement.param(anyString(),any())).thenAnswer(inv->{result.parameters.put(inv.getArgument(0),inv.getArgument(1));return statement;});
        when(statement.query(any(org.springframework.jdbc.core.RowMapper.class))).thenReturn((JdbcClient.MappedQuerySpec)rows);
        when(rows.list()).thenReturn(List.of());
        var repository=new JdbcResearchReadRepository(mockJdbc);
        if(c.outgoing) repository.outgoingEdges(c.scope,c.nodeId,c.limit);else repository.incidentEdges(c.scope,c.nodeId,c.limit);
        assertNotNull(result.template);return result;
    }
    private static DataSource recordingDataSource(Trace trace){
        return new DelegatingDataSource(dataSource){
            @Override public Connection getConnection() throws SQLException {
                Connection original=super.getConnection();
                try(var s=original.createStatement();var rs=s.executeQuery("SELECT @@session.max_execution_time")){
                    if(!rs.next())throw new SQLException("Session budget unavailable");trace.sessionMs=rs.getLong(1);
                    if(trace.sessionMs!=1500L)throw new SQLException("Session budget changed");
                }catch(SQLException ex){original.close();throw ex;}
                return (Connection)Proxy.newProxyInstance(Connection.class.getClassLoader(),new Class<?>[]{Connection.class},(p,m,a)->{
                    if(m.getName().equals("prepareStatement") && a!=null && a.length>0 && a[0] instanceof String sql){
                        if(!sql.startsWith("EXPLAIN FORMAT=JSON SELECT ")&&!sql.startsWith("EXPLAIN FORMAT=JSON WITH "))throw new SQLException("Only EXPLAIN FORMAT=JSON allowed");
                        if(trace.sql!=null)throw new SQLException("Second EXPLAIN forbidden");trace.sql=sql;
                        PreparedStatement prepared=(PreparedStatement)invoke(original,m,a);
                        return Proxy.newProxyInstance(PreparedStatement.class.getClassLoader(),new Class<?>[]{PreparedStatement.class},(q,n,b)->{
                            if(n.getName().equals("setQueryTimeout"))trace.timeoutSeconds=(Integer)b[0];
                            if(b!=null&&b.length>=2&&b[0] instanceof Integer&&Set.of("setObject","setLong","setInt","setString","setNull","setBoolean","setDate","setTime","setTimestamp","setBigDecimal","setShort","setByte","setFloat","setDouble","setBytes").contains(n.getName())){
                                Object value=b[1];if(value instanceof byte[] bytes)value=Arrays.toString(bytes);
                                Map<String,Object> binding=new LinkedHashMap<>();binding.put("index",b[0]);binding.put("method",n.getName());
                                binding.put("type",value==null?"NULL":value.getClass().getName());binding.put("value",value);
                                trace.bindings.put((Integer)b[0],binding);
                            }
                            if(n.getName().equals("executeQuery"))trace.executions++;
                            return invoke(prepared,n,b);
                        });
                    }
                    return invoke(original,m,a);
                });
            }
            @Override public Connection getConnection(String user,String password)throws SQLException{throw new SQLException("Explicit credential forbidden");}
        };
    }
    private static Object invoke(Object target,Method method,Object[] args)throws Throwable{
        try{return method.invoke(target,args);}catch(InvocationTargetException ex){throw ex.getCause();}
    }
    private static String canonical(String s){return s==null?null:s.replaceAll("\\s+"," ").trim();}
    private static String sha256(String text){try{return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));}catch(Exception ex){throw new IllegalStateException(ex);}}
    private static Map<String,Object> printable(Map<String,Object> parameters){Map<String,Object> map=new LinkedHashMap<>();parameters.forEach((key,value)->map.put(key,value instanceof Set<?> s?s.stream().map(Object::toString).sorted().toList():value));return map;}
    private static Map<String,Object> scopeRecord(Scope s){Map<String,Object> map=new LinkedHashMap<>();map.put("mode",s.mode().name());map.put("libraries",s.libraryIds().stream().sorted().toList());map.put("documents",s.documentIds().stream().sorted().toList());map.put("nodes",s.nodeIds().stream().sorted().toList());return map;}
}
