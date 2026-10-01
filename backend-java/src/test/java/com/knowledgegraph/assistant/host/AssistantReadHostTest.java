package com.knowledgegraph.assistant.host;

import com.fasterxml.jackson.databind.*;
import com.knowledgegraph.assistant.contract.AssistantContracts.*;
import com.knowledgegraph.assistant.runtime.DecisionProvider;
import com.knowledgegraph.assistant.research.ResearchReadRepository;
import com.knowledgegraph.common.ApiException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static com.knowledgegraph.assistant.host.HostTestFixture.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AssistantReadHostTest {
    HostTestFixture f;
    @BeforeEach void setup(){f=new HostTestFixture();}
    @AfterEach void close(){f.close();}
    JsonNode response(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request,int status,String schema)throws Exception {
        MvcResult result=f.mvc.perform(request).andExpect(status().is(status)).andExpect(header().string("Cache-Control","no-store")).andReturn();
        JsonNode json=HostJson.JSON.readTree(result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
        if(schema!=null)new ApprovedSchema().check(schema,json);return json;
    }
    @Test void allTenEndpointsAgainstFrozenSchemasAndOriginalRuntime()throws Exception {
        response(get("/api/assistant/capabilities"),200,"CapabilitiesEnvelope");
        response(get("/api/assistant/templates"),200,"TemplateListEnvelope");
        JsonNode libraries=response(get("/api/assistant/scope/libraries"),200,"LibraryPageEnvelope");
        assertEquals(Long.toString(LIB),libraries.at("/data/items/0/libraryId").asText());
        JsonNode documents=response(get("/api/assistant/scope/documents").param("libraryIds",Long.toString(LIB)),200,"DocumentPageEnvelope");
        assertEquals(Long.toString(A),documents.at("/data/items/0/documentId").asText());
        JsonNode created=response(post("/api/assistant/runs").contentType(MediaType.APPLICATION_JSON).content(create(A)),202,"RunEnvelope");
        String run=created.at("/data/runId").asText();var entry=f.host.store.get(run);
        entry.handle.completion().toCompletableFuture().get(10,TimeUnit.SECONDS);
        JsonNode actual=response(get("/api/assistant/runs/{id}",run),200,"RunEnvelope");
        assertEquals("partial",actual.at("/data/status").asText());
        assertEquals(0,actual.at("/data/usage/modelCalls").asInt());
        assertTrue(actual.at("/data/usage/decisionCalls").asInt()>0);
        assertEquals(4,actual.at("/data/tasks").size());
        for(TaskSnapshot task:entry.snapshot().tasks()) {
            assertNotNull(task.artifact(),task.toString());
            JsonNode artifact=response(get("/api/assistant/runs/{r}/artifacts/{a}",run,task.artifact().artifactId()),200,"ArtifactEnvelope");
            assertEquals(task.artifact().outcome().wireValue(),artifact.at("/data/outcome").asText());
            assertFalse(artifact.toString().contains("storage_path"));
        }
        Artifact study=entry.snapshot().tasks().get(2).artifact(),source=entry.snapshot().tasks().get(1).artifact(),citation=entry.snapshot().tasks().get(3).artifact();
        JsonNode graph=response(get("/api/assistant/runs/{r}/artifacts/{a}/graph",run,study.artifactId()),200,"GraphEnvelope");
        assertEquals(Long.toString(N1),graph.at("/data/edges/0/sourceNodeId").asText());assertEquals(Long.toString(N2),graph.at("/data/edges/0/targetNodeId").asText());
        assertEquals(Long.toString(EDGE),graph.at("/data/paths/0/0").asText());
        JsonNode citationJson=f.host.artifact(run,citation.artifactId());assertEquals("CURRENT",citationJson.at("/payload/checks/0/referenceStatus").asText());
        assertEquals("not_evaluated",citationJson.at("/payload/semanticSupport").asText());
        verify(f.quality,atLeastOnce()).chunkDocumentId(A+100);
        EvidenceRef pointer=source.evidence().stream().filter(e->e.kind()==EvidenceKind.SOURCE).findFirst().orElseThrow();
        response(post("/api/assistant/runs/{r}/artifacts/{a}/source-reread",run,source.artifactId()).contentType(MediaType.APPLICATION_JSON)
                .content(HostJson.obj("sourceId",pointer.sourceId(),"expectedContentHash",HASH).toString()),200,"RereadEnvelope");
        JsonNode cancelled=response(post("/api/assistant/runs/{r}/cancel",run),200,"CancelEnvelope");assertFalse(cancelled.at("/data/accepted").asBoolean());
    }
    @ParameterizedTest @ValueSource(strings={"{}","[]","null","{\"templateId\":\"x\"}",
            "{\"templateId\":\"source_graph_citation_v1\",\"templateId\":\"source_graph_citation_v1\",\"selection\":{},\"query\":\"graph\"}",
            "{\"templateId\":\"source_graph_citation_v1\",\"selection\":{\"libraryIds\":[\"1\"],\"documentIds\":[1]},\"query\":\"graph\"}",
            "{\"templateId\":\"source_graph_citation_v1\",\"selection\":{\"libraryIds\":[\"1\",\"1\"],\"documentIds\":[\"2\"]},\"query\":\"graph\"}"})
    void malformedBodiesRejected(String body)throws Exception {response(post("/api/assistant/runs").contentType(MediaType.APPLICATION_JSON).content(body),400,"Error");verifyNoInteractions(f.research,f.quality,f.source);}
    @Test void clientAuthorityAndDagRejected()throws Exception {var root=HostJson.parse(create(A),65536).deepCopy();((com.fasterxml.jackson.databind.node.ObjectNode)root).put("owner","attacker");
        response(post("/api/assistant/runs").contentType(MediaType.APPLICATION_JSON).content(root.toString()),400,"Error");}
    @Test void scope1000BoundaryAndLongOverflow(){var ids=HostJson.JSON.createArrayNode();for(int i=1;i<=1000;i++)ids.add(Integer.toString(i));
        assertEquals(1000,HostJson.ids(ids,1000).size());ids.add("1001");assertThrows(ApiException.class,()->HostJson.ids(ids,1000));
        assertEquals(Long.MAX_VALUE,HostJson.id(Long.toString(Long.MAX_VALUE)));assertThrows(ApiException.class,()->HostJson.id("9223372036854775808"));assertThrows(ApiException.class,()->HostJson.id("01"));}
    @Test void scopeBeyondServerGrantRejectedBeforeRepository()throws Exception {response(get("/api/assistant/scope/documents").param("libraryIds","7"),403,"Error");verify(f.ingestion,never()).documents(any(),any(),anyInt(),anyInt());}
    @Test void twoDocumentsPartialAndFailureStaySeparate()throws Exception {
        when(f.research.candidates(any(),eq(B),anyList(),anyInt())).thenThrow(new IllegalStateException("password SQL storage_path"));
        var e=f.completed(A,B);assertEquals(8,e.snapshot().tasks().size());assertEquals(RunStatus.PARTIAL,e.snapshot().status());
        List<TaskSnapshot> b=e.snapshot().tasks().stream().filter(t->e.plan.tasks().stream().anyMatch(p->p.taskId().equals(t.taskId())&&p.scope().documentIds().contains(B))).toList();
        assertEquals(TaskStatus.FAILED,b.get(1).status());assertEquals(TaskStatus.BLOCKED,b.get(2).status());assertEquals(TaskStatus.BLOCKED,b.get(3).status());
        assertTrue(e.snapshot().tasks().stream().anyMatch(t->t.taskId().endsWith(":verify")&&t.artifact()!=null));assertFalse(f.host.run(e.runId).toString().contains("password"));
    }
    @Test void graphEmptyIsObservedAndMetadataGraphUnavailable()throws Exception {
        when(f.research.searchNodes(any(),anyString(),anyInt())).thenReturn(new ResearchReadRepository.Window<>(List.of(),false));
        var e=f.completed(A);Artifact graph=e.snapshot().tasks().get(2).artifact();assertTrue(f.host.graph(e.runId,graph.artifactId()).path("empty").asBoolean());
        response(get("/api/assistant/runs/{r}/artifacts/{a}/graph",e.runId,e.snapshot().tasks().get(0).artifact().artifactId()),409,"Error");
    }
    @Test void singleHitUsesIncidentEdgesWithoutReversingStoredDirection()throws Exception {
        when(f.research.searchNodes(any(),anyString(),anyInt())).thenReturn(new ResearchReadRepository.Window<>(List.of(node(N2)),false));
        var e=f.completed(A);JsonNode g=f.host.graph(e.runId,e.snapshot().tasks().get(2).artifact().artifactId());
        assertEquals(Long.toString(N1),g.at("/edges/0/sourceNodeId").asText());assertEquals(Long.toString(N2),g.at("/edges/0/targetNodeId").asText());assertTrue(g.path("noPathWithinDepth").isNull());
    }
    @Test void revokedScopeOwnerAndCrossRunArtifactFailClosed()throws Exception {
        var e=f.completed(A);var other=f.completed(A);String artifact=e.snapshot().tasks().get(1).artifact().artifactId();
        response(get("/api/assistant/runs/{r}/artifacts/{a}",other.runId,artifact),404,"Error");
        f.allowed.set(false);response(get("/api/assistant/runs/{r}",e.runId),403,"Error");
        f.allowed.set(true);f.owner.set("another-owner");response(get("/api/assistant/runs/{r}",e.runId),404,"Error");
        f.owner.set("local-owner");f.verified.set(false);response(get("/api/assistant/runs/{r}",e.runId),503,"Error");
    }
    @Test void directReaderRejectsCrossRunAndForgedOriginal()throws Exception {
        var e=f.completed(A);var other=f.completed(B);var consumer=e.plan.tasks().get(3);var c=e.contexts.get(consumer.taskId());
        Artifact input=c.inputs().get(0);assertTrue(new HostQualityArtifactReader(f.host.store).directArtifact(e.runId,consumer.taskId(),input.artifactId()).isPresent());
        assertTrue(new HostQualityArtifactReader(f.host.store).directArtifact(other.runId,consumer.taskId(),input.artifactId()).isEmpty());
        Artifact forged=new Artifact(UUID.randomUUID().toString(),input.producerTaskId(),input.authorizedScope(),input.observedScopes(),input.outcome(),input.summary(),input.dataJson(),input.evidence(),input.actionProposals(),input.limitations(),input.createdAt());
        assertThrows(IllegalArgumentException.class,()->f.host.store.capture(new DecisionProvider.Context(e.runId,consumer,List.of(forged),c.observations(),List.of(),List.of(),new Usage(0,0),Instant.now().plusSeconds(5),()->false)));
    }
    @Test void rereadRejectsHashesPointersAndOwnershipWithoutBody()throws Exception {
        var e=f.completed(A);Artifact a=e.snapshot().tasks().get(1).artifact();EvidenceRef ref=a.evidence().stream().filter(r->r.kind()==EvidenceKind.SOURCE).findFirst().orElseThrow();
        String url="/api/assistant/runs/"+e.runId+"/artifacts/"+a.artifactId()+"/source-reread";
        clearInvocations(f.source);
        response(post(url).contentType(MediaType.APPLICATION_JSON).content(HostJson.obj("sourceId",ref.sourceId(),"expectedContentHash","b".repeat(64)).toString()),409,"Error");
        verifyNoInteractions(f.source);
        response(post(url).contentType(MediaType.APPLICATION_JSON).content(HostJson.obj("sourceId","fabricated","expectedContentHash",HASH).toString()),404,"Error");
        when(f.research.sourceMeta(any(),eq(A),eq(A+100))).thenReturn(Optional.empty());
        JsonNode error=response(post(url).contentType(MediaType.APPLICATION_JSON).content(HostJson.obj("sourceId",ref.sourceId(),"expectedContentHash",HASH).toString()),404,"Error");assertFalse(error.has("data"));
    }
    @Test void expiresAtReturns410AndTombstonesAreBounded()throws Exception {
        var e=f.completed(A);f.now.set(e.expiresAt.plusMillis(1));response(get("/api/assistant/runs/{r}",e.runId),410,"Error");
        f.now.set(e.expiresAt.plusSeconds(1801));response(get("/api/assistant/runs/{r}",e.runId),404,"Error");
    }
    @Test void cancelRetainsPreviouslyPublishedArtifacts()throws Exception {
        CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
        when(f.research.candidates(any(),eq(A),anyList(),anyInt())).thenAnswer(i->{entered.countDown();release.await(5,TimeUnit.SECONDS);return new ResearchReadRepository.Window<>(List.of(),false);});
        String run=f.host.create(create(A)).path("runId").asText();assertTrue(entered.await(5,TimeUnit.SECONDS));var e=f.host.store.get(run);
        assertNotNull(e.snapshot().tasks().get(0).artifact());assertTrue(f.host.cancel(run).path("accepted").asBoolean());release.countDown();
        e.handle.completion().toCompletableFuture().get(5,TimeUnit.SECONDS);assertEquals(RunStatus.CANCELLED,e.snapshot().status());assertNotNull(e.snapshot().tasks().get(0).artifact());assertNull(e.snapshot().tasks().get(1).artifact());
    }
    @Test void retainedCapacityDoesNotEvictTerminalResults()throws Exception {
        try(var small=new HostTestFixture(new AssistantReadHostProperties(true,true,false,1,1,Duration.ofSeconds(1800),2L*1024*1024))) {
            var e=small.completed(A);ApiException ex=assertThrows(ApiException.class,()->small.host.create(create(A)));assertEquals(429,ex.getHttpStatus());assertNotNull(small.host.run(e.runId));
        }
    }
    @Test void memoryLimitCancelsWithoutPretendingComplete()throws Exception {
        try(var small=new HostTestFixture(new AssistantReadHostProperties(true,true,false,1,1,Duration.ofSeconds(1800),1024))) {
            var e=small.completed(A);assertTrue(e.tooLarge);assertNotEquals(RunStatus.COMPLETED,e.snapshot().status());assertTrue(small.host.run(e.runId).path("limitations").toString().contains("HOST_CONTENT_MEMORY_LIMIT"));
        }
    }
    @ParameterizedTest @ValueSource(strings={"page=0","page=1001","pageSize=21","owner=client","page=1&page=2"})
    void badQueries(String query)throws Exception {response(get("/api/assistant/scope/libraries?"+query),400,"Error");}
    @Test void methodMediaSizeAndSensitiveErrors()throws Exception {
        response(delete("/api/assistant/runs"),405,"Error");response(post("/api/assistant/runs").contentType(MediaType.TEXT_PLAIN).content(create(A)),415,"Error");
        response(post("/api/assistant/runs").contentType(MediaType.APPLICATION_JSON).content(" ".repeat(65537)),413,"Error");
        when(f.ingestion.libraries(any(),anyInt(),anyInt())).thenThrow(new RuntimeException("jdbc:mysql://secret/password storage_path"));
        JsonNode json=response(get("/api/assistant/scope/libraries"),500,"Error");assertFalse(json.toString().contains("secret"));
    }
    @Test void loopbackAndSameOriginOnly()throws Exception {
        response(get("/api/assistant/capabilities").with(r->{r.setRemoteAddr("192.0.2.1");return r;}),401,"Error");
        response(get("/api/assistant/capabilities").header("Origin","https://attacker.invalid"),401,"Error");
        response(get("/api/assistant/capabilities").header("X-Forwarded-For","127.0.0.1"),401,"Error");
    }
    @Test void defaultAndModelEnabledNeverConstructReader()throws Exception {
        java.util.concurrent.atomic.AtomicInteger factories=new java.util.concurrent.atomic.AtomicInteger();
        for(AssistantReadHostProperties p:List.of(AssistantReadHostProperties.disabled(),new AssistantReadHostProperties(true,true,true,8,32,Duration.ofSeconds(1800),2L*1024*1024)))
            try(var host=new AssistantReadHostService(p,()->null,Clock.systemUTC(),(io,s,c)->{factories.incrementAndGet();throw new AssertionError("reader must not be created");})) {
                assertFalse(host.capabilities().path("databaseIdentityVerified").asBoolean());
                var mvc=HostTestFixture.mvc(host);mvc.perform(get("/api/assistant/scope/libraries")).andExpect(status().is(503)).andExpect(header().string("Cache-Control","no-store"));
                mvc.perform(post("/api/settings").contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isNotFound());
            }
        assertEquals(0,factories.get());
    }
    @Test void missingAccessAndReaderEvidenceFailClosed() {
        var p=new AssistantReadHostProperties(true,true,false,8,32,Duration.ofSeconds(1800),2L*1024*1024);
        try(var noContext=new AssistantReadHostService(p,()->null,Clock.systemUTC(),null)) {assertEquals(401,assertThrows(ApiException.class,()->noContext.libraries(1,20)).getHttpStatus());}
        try(var noIdentity=new AssistantReadHostService(p,()->new AssistantReadAccessContext("owner",f.grant,s->true),Clock.systemUTC(),null)) {
            assertEquals("READ_IDENTITY_UNVERIFIED",assertThrows(ApiException.class,()->noIdentity.libraries(1,20)).getCode());
        }
    }
    @Test void invalidUtf8IsRejectedBeforeHostWork()throws Exception {
        response(post("/api/assistant/runs").contentType(MediaType.APPLICATION_JSON).content(new byte[]{(byte)0xc3,(byte)0x28}),400,"Error");
        verify(f.ingestion,never()).document(anyLong());
    }
    @Test void documentMoveInvalidatesSelectionBeforeDomainSourceWork()throws Exception {
        when(f.ingestion.documentLibraries(A)).thenReturn(Set.of(7L));
        response(post("/api/assistant/runs").contentType(MediaType.APPLICATION_JSON).content(create(A)),409,"Error");
        verifyNoInteractions(f.research,f.quality,f.source);
    }
    @Test void permissionRevokedDuringReadReturnsNoBody()throws Exception {
        var e=f.completed(A);var a=e.snapshot().tasks().get(1).artifact();var ref=a.evidence().stream().filter(r->r.kind()==EvidenceKind.SOURCE).findFirst().orElseThrow();
        when(f.source.chunk(any(),eq(A),eq(A+100),anyInt())).thenAnswer(i->{f.allowed.set(false);return Optional.of(new com.knowledgegraph.assistant.source.SourceLocator.Chunk(A+100,A+200,0,HASH,0,20,"must not leak body",false));});
        JsonNode error=response(post("/api/assistant/runs/{r}/artifacts/{a}/source-reread",e.runId,a.artifactId()).contentType(MediaType.APPLICATION_JSON)
                .content(HostJson.obj("sourceId",ref.sourceId(),"expectedContentHash",HASH).toString()),403,"Error");assertFalse(error.toString().contains("must not leak"));
    }
    @Test void sourceChangesBetweenMetadataReadsReturnsNoBody()throws Exception {
        var e=f.completed(A);var a=e.snapshot().tasks().get(1).artifact();var ref=a.evidence().stream().filter(r->r.kind()==EvidenceKind.SOURCE).findFirst().orElseThrow();
        var before=new ResearchReadRepository.SourceMeta(A,A+100,A+200,1,"text","unit:1",HASH,"COMPLETED","active","READY");
        var after=new ResearchReadRepository.SourceMeta(A,A+100,A+200,1,"text","unit:1","b".repeat(64),"COMPLETED","active","READY");
        when(f.research.sourceMeta(any(),eq(A),eq(A+100))).thenReturn(Optional.of(before),Optional.of(after));
        JsonNode error=response(post("/api/assistant/runs/{r}/artifacts/{a}/source-reread",e.runId,a.artifactId()).contentType(MediaType.APPLICATION_JSON)
                .content(HostJson.obj("sourceId",ref.sourceId(),"expectedContentHash",HASH).toString()),409,"Error");assertFalse(error.has("data"));
    }
    @Test void emptySourcesProduceExplicitUncheckedCoverage()throws Exception {
        when(f.research.candidates(any(),anyLong(),anyList(),anyInt())).thenReturn(new ResearchReadRepository.Window<>(List.of(),false));
        var e=f.completed(A);for(var t:e.snapshot().tasks())new ApprovedSchema().check("Artifact",f.host.artifact(e.runId,t.artifact().artifactId()));
        var citation=f.host.artifact(e.runId,e.snapshot().tasks().get(3).artifact().artifactId());
        assertEquals(0,citation.at("/payload/requestedClaims").asInt());assertFalse(citation.at("/payload/completeCoverage").asBoolean());assertTrue(citation.path("limitations").toString().contains("NO_ELIGIBLE_SOURCE_CLAIMS"));
    }
}
