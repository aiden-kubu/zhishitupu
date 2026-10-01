package com.knowledgegraph.assistant.host;

import com.knowledgegraph.assistant.contract.AssistantContracts.*;
import com.knowledgegraph.assistant.ingestion.IngestionReadRepository;
import com.knowledgegraph.assistant.research.ResearchReadRepository;
import com.knowledgegraph.assistant.quality.QualityReadRepository;
import com.knowledgegraph.assistant.source.SourceLocator;
import com.knowledgegraph.common.*;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** Test-only data, never reachable from the production launcher/composition. */
final class HostTestFixture implements AutoCloseable {
    static final long LIB=Long.MAX_VALUE,A=9007199254740993L,B=A+1,N1=A+500,N2=A+501,EDGE=A+600;
    static final String HASH="a".repeat(64);
    final IngestionReadRepository ingestion=mock(IngestionReadRepository.class);
    final ResearchReadRepository research=mock(ResearchReadRepository.class);
    final QualityReadRepository quality=mock(QualityReadRepository.class);
    final SourceLocator source=mock(SourceLocator.class);
    final AtomicBoolean allowed=new AtomicBoolean(true),verified=new AtomicBoolean(true);
    final AtomicReference<String> owner=new AtomicReference<>("local-owner");
    final AtomicReference<Instant> now=new AtomicReference<>(Instant.now());
    final Clock clock=new Clock(){public ZoneId getZone(){return ZoneOffset.UTC;}public Clock withZone(ZoneId z){return this;}public Instant instant(){return now.get();}};
    final Scope grant=new Scope(ScopeMode.SELECTED,Set.of(LIB),Set.of(),Set.of());
    final AssistantReadHostService host;
    final MockMvc mvc;
    HostTestFixture() {this(new AssistantReadHostProperties(true,true,false,8,32,Duration.ofSeconds(1800),2L*1024*1024));}
    HostTestFixture(AssistantReadHostProperties properties) {
        when(ingestion.documentLibraries(anyLong())).thenReturn(Set.of(LIB));
        when(ingestion.document(anyLong())).thenAnswer(i->{long id=i.getArgument(0);return id==A||id==B?Optional.of(document(id)):Optional.empty();});
        when(ingestion.libraries(any(),anyInt(),anyInt())).thenAnswer(i->new IngestionReadRepository.Page<>(List.of(
                new IngestionReadRepository.Library(LIB,"测试库","topic",null,"active",LocalDateTime.of(2026,9,30,12,0),2,2,1,List.of())),1L,i.getArgument(1),i.getArgument(2)));
        when(ingestion.documents(any(),any(),anyInt(),anyInt())).thenAnswer(i->new IngestionReadRepository.Page<>(List.of(document(A),document(B)),2L,i.getArgument(2),i.getArgument(3)));
        when(research.matchingDocuments(any(),anyList(),anyInt())).thenAnswer(i->new ResearchReadRepository.Window<>(List.copyOf(((Scope)i.getArgument(0)).documentIds()),false));
        when(research.candidates(any(),anyLong(),anyList(),anyInt())).thenAnswer(i->{long d=i.getArgument(1);return new ResearchReadRepository.Window<>(List.of(new ResearchReadRepository.Candidate(d,d+100,HASH,1)),false);});
        when(research.sourceMeta(any(),anyLong(),anyLong())).thenAnswer(i->{long d=i.getArgument(1),c=i.getArgument(2);return c==d+100?Optional.of(new ResearchReadRepository.SourceMeta(d,c,d+200,1,"text","unit:1",HASH,"COMPLETED","active","READY")):Optional.empty();});
        when(source.chunk(any(),anyLong(),anyLong(),anyInt())).thenAnswer(i->{long d=i.getArgument(1),c=i.getArgument(2);return c==d+100?Optional.of(new SourceLocator.Chunk(c,d+200,0,HASH,0,28,"graph source quotation " + d,false)):Optional.empty();});
        when(quality.chunkDocumentId(anyLong())).thenAnswer(i->Optional.of((long)i.getArgument(0)-100));
        when(quality.unitMeta(any(),anyLong(),anyLong())).thenAnswer(i->Optional.of(new QualityReadRepository.UnitMeta(i.getArgument(1),i.getArgument(2),1,"unit:1")));
        when(research.searchNodes(any(),anyString(),anyInt())).thenReturn(new ResearchReadRepository.Window<>(List.of(node(N1),node(N2)),false));
        when(research.node(any(),anyLong())).thenAnswer(i->{long id=i.getArgument(1);return id==N1||id==N2?Optional.of(node(id)):Optional.empty();});
        when(research.outgoingEdges(any(),anyLong(),anyInt())).thenAnswer(i->new ResearchReadRepository.Window<>((long)i.getArgument(1)==N1?List.of(edge()):List.of(),false));
        when(research.incidentEdges(any(),anyLong(),anyInt())).thenReturn(new ResearchReadRepository.Window<>(List.of(edge()),false));
        host=new AssistantReadHostService(properties,()->new AssistantReadAccessContext(owner.get(),grant,s->allowed.get()),clock,
                (io,store,c)->AssistantReadWiring.domains(ingestion,research,quality,source,io,store,c,s->verified.get()));
        mvc=mvc(host);
    }
    static MockMvc mvc(AssistantReadHostService host) {
        return MockMvcBuilders.standaloneSetup(new AssistantReadHostController(host)).setControllerAdvice(new AssistantReadHostErrors())
                .addFilters(new RequestIdFilter(),new AssistantReadBoundaryFilter()).build();
    }
    static IngestionReadRepository.Document document(long id) {return new IngestionReadRepository.Document(id,LIB,"fixture.txt","测试资料 "+id,"COMPLETED","active","txt",28,HASH,LocalDateTime.of(2026,9,30,12,0),List.of(),true);}
    static ResearchReadRepository.Node node(long id) {return new ResearchReadRepository.Node(id,"graph "+id,null,"concept","test definition","active","2026-09-30T12:00:00","manual","accepted",List.of(LIB),List.of(),"name",true);}
    static ResearchReadRepository.Edge edge() {return new ResearchReadRepository.Edge(EDGE,N1,N2,"related","active","2026-09-30T12:00:00","manual","accepted",true);}
    static String create(long... docs) {return HostJson.obj("templateId",AssistantTemplatePlanner.TEMPLATE,"selection",HostJson.obj("libraryIds",List.of(Long.toString(LIB)),"documentIds",Arrays.stream(docs).mapToObj(Long::toString).toList()),"query","graph","goal","仅显示此目标").toString();}
    AssistantRunStore.Entry completed(long... docs)throws Exception {
        String id=host.create(create(docs)).path("runId").asText();var e=host.store.get(id);
        e.handle.completion().toCompletableFuture().get(10,java.util.concurrent.TimeUnit.SECONDS);return host.store.get(id);
    }
    @Override public void close(){host.close();}
}
