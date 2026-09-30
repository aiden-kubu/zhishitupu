package com.knowledgegraph.assistant.i2;

import com.knowledgegraph.assistant.contract.AssistantContracts.Scope;
import com.knowledgegraph.assistant.quality.JdbcQualityReadRepository;
import com.knowledgegraph.assistant.quality.QualityReadRepository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** R3 integration-only oracle/diagnostics. No production adapter or changed domain SQL. */
@EnabledIfEnvironmentVariable(named="KG_I2_AUTHORIZED", matches="8c6cfcb3-ba49-11f1-bf64-005056c00001")
class I2QualityInsightWholeTest extends I2MySqlSupport {
    private record Node(long id,String name,String type) {}
    private record Edge(long source,long target,String status) {}
    private record Job(long document,String status) {}
    private record Candidate(long job,String status) {}
    private record Selection(String name,Scope scope,int isolatedLimit,int topLimit) {}
    private record Oracle(Insight insight,long entityPending,long relationPending) {}
    private static final class Query {
        final String sql; final Map<String,Object> parameters=new LinkedHashMap<>();
        Query(String sql){this.sql=sql;}
    }
    private final Map<Long,Node> nodes=new LinkedHashMap<>();
    private final List<long[]> memberships=new ArrayList<>(), assignments=new ArrayList<>();
    private final List<Edge> edges=new ArrayList<>();
    private final Map<Long,Long> documents=new LinkedHashMap<>();
    private final Map<Long,Job> jobs=new LinkedHashMap<>();
    private final List<Candidate> entities=new ArrayList<>(),relations=new ArrayList<>();
    private final Map<String,Integer> typeOrder=new HashMap<>();

    private static void evidence(String kind,Object value) throws Exception {
        System.out.println("R3_EVIDENCE "+JSON.writeValueAsString(Map.of("kind",kind,"value",value)));
    }
    private void readIndependentRows() {
        // ORDER BY type only establishes the existing database collation tie order, not the aggregate.
        for(Node n:jdbc.sql("SELECT id,canonical_name,node_type FROM knowledge_nodes ORDER BY node_type,id")
                .query((rs,i)->new Node(rs.getLong(1),rs.getString(2),rs.getString(3))).list()) {
            nodes.put(n.id(),n); if(!typeOrder.containsKey(n.type())) typeOrder.put(n.type(),typeOrder.size());
        }
        memberships.addAll(jdbc.sql("SELECT library_id,node_id FROM library_nodes")
                .query((rs,i)->new long[]{rs.getLong(1),rs.getLong(2)}).list());
        edges.addAll(jdbc.sql("SELECT source_node_id,target_node_id,status FROM knowledge_edges")
                .query((rs,i)->new Edge(rs.getLong(1),rs.getLong(2),rs.getString(3))).list());
        jdbc.sql("SELECT id,library_id FROM documents").query((rs,i)-> {
            long id=rs.getLong(1); Object lib=rs.getObject(2);
            documents.put(id,lib==null?null:((Number)lib).longValue());return id;
        }).list();
        assignments.addAll(jdbc.sql("SELECT library_id,document_id FROM document_topic_assignments")
                .query((rs,i)->new long[]{rs.getLong(1),rs.getLong(2)}).list());
        jdbc.sql("SELECT id,document_id,status FROM ingestion_jobs").query((rs,i)-> {
            jobs.put(rs.getLong(1),new Job(rs.getLong(2),rs.getString(3)));return rs.getLong(1);
        }).list();
        entities.addAll(jdbc.sql("SELECT job_id,review_status FROM entity_candidates")
                .query((rs,i)->new Candidate(rs.getLong(1),rs.getString(2))).list());
        relations.addAll(jdbc.sql("SELECT job_id,review_status FROM relation_candidates")
                .query((rs,i)->new Candidate(rs.getLong(1),rs.getString(2))).list());
    }
    private Oracle oracle(Selection s) {
        Set<Long> visible=new HashSet<>(),visibleDocs=new HashSet<>();
        Set<Long> libraries=s.scope().libraryIds();
        if(libraries.isEmpty()){visible.addAll(nodes.keySet());visibleDocs.addAll(documents.keySet());}
        else {
            for(long[] m:memberships) if(libraries.contains(m[0]) && nodes.containsKey(m[1])) visible.add(m[1]);
            documents.forEach((id,lib)->{if(lib!=null && libraries.contains(lib)) visibleDocs.add(id);});
            for(long[] a:assignments) if(libraries.contains(a[0]) && documents.containsKey(a[1])) visibleDocs.add(a[1]);
        }
        Map<Long,Long> degrees=new HashMap<>();
        visible.forEach(id->degrees.put(id,0L));
        long edgeCount=0;
        for(Edge e:edges) {
            if(!"active".equals(e.status()) || !visible.contains(e.source()) || !visible.contains(e.target())) continue;
            edgeCount++;degrees.compute(e.source(),(id,v)->v+1);
            if(e.source()!=e.target()) degrees.compute(e.target(),(id,v)->v+1);
        }
        List<NodeItem> all=degrees.entrySet().stream().map(e-> {
            Node n=nodes.get(e.getKey());return new NodeItem(n.id(),n.name(),n.type(),e.getValue());
        }).toList();
        List<NodeItem> top=all.stream().sorted(Comparator.comparingLong(NodeItem::degree).reversed()
                .thenComparingLong(NodeItem::id)).limit(s.topLimit()).toList();
        List<NodeItem> isolated=all.stream().filter(n->n.degree()==0)
                .sorted(Comparator.comparingLong(NodeItem::id)).toList();
        Map<String,Long> counts=new HashMap<>();
        visible.forEach(id->counts.merge(nodes.get(id).type(),1L,Long::sum));
        List<TypeCount> types=counts.entrySet().stream().map(e->new TypeCount(e.getKey(),e.getValue()))
                .sorted(Comparator.comparingLong(TypeCount::count).reversed()
                        .thenComparingInt(t->typeOrder.get(t.type()))).limit(50).toList();
        long ep=pending(entities,visibleDocs),rp=pending(relations,visibleDocs);
        return new Oracle(new Insight(visible.size(),edgeCount,visibleDocs.size(),ep+rp,types,counts.size(),top,
                isolated.size(),isolated.stream().limit(s.isolatedLimit()).toList()),ep,rp);
    }
    private long pending(List<Candidate> candidates,Set<Long> visibleDocs) {
        Set<String> statuses=Set.of("AWAITING_REVIEW","AI_REVIEWING","FAILED","CANCELLED");
        return candidates.stream().filter(c-> {
            Job j=jobs.get(c.job());return "PENDING".equals(c.status()) && j!=null &&
                    visibleDocs.contains(j.document()) && j.status()!=null && statuses.contains(j.status());
        }).count();
    }
    @SuppressWarnings({"unchecked","rawtypes"})
    private static List<Query> capture(Selection selection) {
        List<Query> queries=new ArrayList<>();
        JdbcClient capture=mock(JdbcClient.class);
        when(capture.sql(anyString())).thenAnswer(call-> {
            Query q=new Query(call.getArgument(0));queries.add(q);
            JdbcClient.StatementSpec statement=mock(JdbcClient.StatementSpec.class,RETURNS_SELF);
            when(statement.param(anyString(),any())).thenAnswer(p->{q.parameters.put(p.getArgument(0),p.getArgument(1));return statement;});
            JdbcClient.MappedQuerySpec<Long> scalar=mock(JdbcClient.MappedQuerySpec.class);
            when(statement.query(Long.class)).thenReturn(scalar);when(scalar.single()).thenReturn(0L);
            JdbcClient.MappedQuerySpec<Object> rows=mock(JdbcClient.MappedQuerySpec.class);
            when(statement.query(any(RowMapper.class))).thenReturn((JdbcClient.MappedQuerySpec)rows);
            when(rows.list()).thenReturn(List.of());return statement;
        });
        new JdbcQualityReadRepository(capture,JSON).insight(selection.scope(),selection.isolatedLimit(),selection.topLimit());
        assertEquals(10,queries.size(),"Accepted R3 query mapping changed");return queries;
    }
    private static JdbcClient.StatementSpec statement(String prefix,Query q) {
        var statement=jdbc.sql(prefix+q.sql);q.parameters.forEach(statement::param);return statement;
    }
    private static List<List<Object>> rows(long value){return List.of(Arrays.asList(value));}
    private static List<List<Object>> nodeRows(List<NodeItem> values) {
        return values.stream().map(n->Arrays.<Object>asList(n.id(),n.name(),n.type(),n.degree())).toList();
    }
    private static List<List<Object>> expected(int index,Oracle o) {
        Insight i=o.insight();
        return switch(index) {
            case 0 -> rows(i.nodeCount());case 1 -> rows(i.edgeCount());case 2 -> rows(i.documentCount());
            case 3 -> rows(o.entityPending());case 4 -> rows(o.relationPending());case 5 -> rows(i.typeTotal());
            case 6 -> i.types().stream().map(t->Arrays.<Object>asList(t.type(),t.count())).toList();
            case 7 -> nodeRows(i.topDegree());case 8 -> rows(i.isolatedTotal());case 9 -> nodeRows(i.isolated());
            default -> throw new IllegalArgumentException();
        };
    }
    private static Map<String,Object> error(Throwable error) {
        Map<String,Object> result=new LinkedHashMap<>();result.put("type",error.getClass().getName());
        result.put("message",String.valueOf(error.getMessage()));
        for(Throwable cause=error;cause!=null;cause=cause.getCause()) if(cause instanceof SQLException sql) {
            result.put("sqlState",sql.getSQLState());result.put("vendorCode",sql.getErrorCode());break;
        }
        return result;
    }
    @Test void allTenQueriesAndFullInsightMatchIndependentRows() throws Exception {
        readIndependentRows();
        List<Selection> cases=List.of(
                new Selection("project",Scope.project(),10,10),
                new Selection("scale_7901",scope(Set.of(7901L),Set.of(),Set.of()),10,10),
                new Selection("library_7910",scope(Set.of(7910L),Set.of(),Set.of()),10,10),
                new Selection("union_7910_7911",scope(Set.of(7910L,7911L),Set.of(),Set.of()),10,10),
                new Selection("library_7911",scope(Set.of(7911L),Set.of(),Set.of()),10,10),
                new Selection("outside_7912",scope(Set.of(7912L),Set.of(),Set.of()),10,10),
                new Selection("empty_7913",scope(Set.of(7913L),Set.of(),Set.of()),10,10),
                new Selection("baseline_7101",LIB,10,10),
                new Selection("project_limits_one",Scope.project(),1,1),
                new Selection("union_limits_one_three",scope(Set.of(7910L,7911L),Set.of(),Set.of()),1,3));
        List<String> issues=new ArrayList<>();
        for(Selection s:cases) {
            Oracle oracle=oracle(s);
            evidence("oracle",Map.of("case",s.name(),"insight",oracle.insight(),"entityPending",oracle.entityPending(),"relationPending",oracle.relationPending()));
            List<Query> queries=capture(s);boolean safeToRunFull=true;
            for(int index=0;index<queries.size();index++) {
                Query q=queries.get(index);String key=s.name()+"/Q"+String.format("%02d",index+1);
                long started=System.nanoTime();String step="explain";
                try {
                    long planStart=System.nanoTime();
                    String plan=statement("EXPLAIN FORMAT=JSON ",q).query(String.class).single();
                    evidence("query_plan",Map.of("key",key,"sql",q.sql,"parameters",q.parameters,"plan",JSON.readTree(plan),
                            "explainMs",TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-planStart)));
                    step="execute";started=System.nanoTime();
                    List<List<Object>> actual=statement("",q).query((rs,row)-> {
                        List<Object> columns=new ArrayList<>();
                        for(int col=1;col<=rs.getMetaData().getColumnCount();col++) {
                            Object value=rs.getObject(col);columns.add(value instanceof Number n?n.longValue():value);
                        }return columns;
                    }).list();
                    long ms=TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-started);
                    evidence("query_result",Map.of("key",key,"expected",expected(index,oracle),"actual",actual,"elapsedMs",ms));
                    assertEquals(expected(index,oracle),actual,key);
                } catch(Exception | AssertionError failure) {
                    safeToRunFull=false;issues.add(key+": "+failure.getMessage());
                    evidence("query_error",Map.of("key",key,"step",step,"elapsedMs",TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-started),"error",error(failure)));
                    // Each JDBC call is bounded and owns its connection; collect remaining independent query evidence.
                }
            }
            if(!safeToRunFull) {evidence("full_skipped",Map.of("case",s.name(),"reason","At least one query/plan failed; do not replay a known failing full call"));continue;}
            long start=System.nanoTime();
            try {
                Insight actual=quality.insight(s.scope(),s.isolatedLimit(),s.topLimit());
                evidence("full_insight",Map.of("case",s.name(),"expected",oracle.insight(),"actual",actual,
                        "elapsedMs",TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-start)));
                assertEquals(oracle.insight(),actual,s.name());
            } catch(Exception | AssertionError failure) {
                issues.add(s.name()+"/full: "+failure.getMessage());
                evidence("full_error",Map.of("case",s.name(),"error",error(failure)));
            }
        }
        evidence("whole_summary",Map.of("scopeCases",cases.size(),"queryChecks",cases.size()*10,"issues",issues));
        assertTrue(issues.isEmpty(),String.join("\n",issues));
    }
}

