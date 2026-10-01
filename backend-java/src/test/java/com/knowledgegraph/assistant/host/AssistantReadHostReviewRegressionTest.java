package com.knowledgegraph.assistant.host;

import com.fasterxml.jackson.databind.JsonNode;
import com.knowledgegraph.assistant.contract.AssistantContracts.*;
import com.knowledgegraph.assistant.ingestion.IngestionReadRepository;
import com.knowledgegraph.assistant.research.ResearchReadRepository;
import com.knowledgegraph.common.ApiException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static com.knowledgegraph.assistant.host.HostTestFixture.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Review regressions first executed against preserved implementation, then the same fixed code. */
class AssistantReadHostReviewRegressionTest {
    HostTestFixture f;
    @BeforeEach void setup(){f=new HostTestFixture();}
    @AfterEach void close(){f.close();}
    JsonNode response(MockHttpServletRequestBuilder request,int expected,String schema)throws Exception {
        var r=f.mvc.perform(request).andExpect(status().is(expected)).andExpect(header().string("Cache-Control","no-store")).andReturn();
        JsonNode json=HostJson.JSON.readTree(r.getResponse().getContentAsString(StandardCharsets.UTF_8));
        new ApprovedSchema().check(schema,json);return json;
    }
    void metadata(boolean file,String processing,String lifecycle,List<String> tags) {
        var d=document(A);when(f.ingestion.document(A)).thenReturn(Optional.of(new IngestionReadRepository.Document(d.id(),d.libraryId(),d.originalName(),d.title(),processing,lifecycle,d.extension(),d.sizeBytes(),d.sha256(),d.updatedAt(),tags,file)));
        when(f.ingestion.documents(any(),any(),anyInt(),anyInt())).thenAnswer(i->new IngestionReadRepository.Page<>(List.of(f.ingestion.document(A).orElseThrow()),1,i.getArgument(2),i.getArgument(3)));
    }
    @Test void absentOriginalFileStillUsesFormalChunksAndKeepsFileFact()throws Exception {
        metadata(false,"COMPLETED","active",List.of());
        JsonNode options=response(get("/api/assistant/scope/documents").param("libraryIds",Long.toString(LIB)),200,"DocumentPageEnvelope");
        assertTrue(options.at("/data/items/0/selectable").asBoolean());assertTrue(options.at("/data/items/0/reasonCodes").isEmpty());
        var e=f.completed(A);assertEquals(RunStatus.PARTIAL,e.snapshot().status());
        for(var t:e.snapshot().tasks())assertNotNull(t.artifact(),t.toString());
        JsonNode meta=f.host.artifact(e.runId,e.snapshot().tasks().get(0).artifact().artifactId());assertFalse(meta.at("/payload/sourceFilePresent").asBoolean());
        JsonNode source=f.host.artifact(e.runId,e.snapshot().tasks().get(1).artifact().artifactId());assertEquals(1,source.at("/payload/items").size());
        JsonNode citation=f.host.artifact(e.runId,e.snapshot().tasks().get(3).artifact().artifactId());assertEquals("CURRENT",citation.at("/payload/checks/0/referenceStatus").asText());
        var a=e.snapshot().tasks().get(1).artifact();var ref=a.evidence().stream().filter(r->r.kind()==EvidenceKind.SOURCE).findFirst().orElseThrow();
        response(reread(e,a,ref),200,"RereadEnvelope");
    }
    @ParameterizedTest @ValueSource(strings={"processing","inactive"})
    void sourceFileAbsenceDoesNotRelaxOtherSelectionConditions(String reason)throws Exception {
        metadata(false,reason.equals("processing")?"PROCESSING":"COMPLETED",reason.equals("inactive")?"archived":"active",List.of());
        response(post("/api/assistant/runs").contentType(MediaType.APPLICATION_JSON).content(create(A)),409,"Error");
        verifyNoInteractions(f.research,f.source,f.quality);
    }
    @Test void formalSourceUnavailableStillProducesRealCoverageGap()throws Exception {
        metadata(false,"COMPLETED","active",List.of());
        when(f.research.sourceMeta(any(),eq(A),eq(A+100))).thenReturn(Optional.of(meta(HASH,"FAILED")));
        var e=f.completed(A);assertEquals(RunStatus.PARTIAL,e.snapshot().status());
        JsonNode source=f.host.artifact(e.runId,e.snapshot().tasks().get(1).artifact().artifactId());
        assertTrue(source.at("/payload/items").isEmpty());assertTrue(source.path("limitations").toString().contains("SOURCE_CHANGED_OR_UNAVAILABLE"));
        JsonNode citation=f.host.artifact(e.runId,e.snapshot().tasks().get(3).artifact().artifactId());assertEquals(0,citation.at("/payload/requestedClaims").asInt());
    }
    static ResearchReadRepository.SourceMeta meta(String hash,String unit) {
        return new ResearchReadRepository.SourceMeta(A,A+100,A+200,1,"text","unit:1",hash,"COMPLETED","active",unit);
    }
    MockHttpServletRequestBuilder reread(AssistantRunStore.Entry e,Artifact a,EvidenceRef ref) {
        return post("/api/assistant/runs/{r}/artifacts/{a}/source-reread",e.runId,a.artifactId()).contentType(MediaType.APPLICATION_JSON)
                .content(HostJson.obj("sourceId",ref.sourceId(),"expectedContentHash",HASH).toString());
    }
    @ParameterizedTest @ValueSource(strings={"missing_before","unavailable_before","missing_after","unavailable_after","missing_chunk","stale_before","stale_after"})
    void domainRereadErrorsKeepExactHttpMappingAndNoBody(String variant)throws Exception {
        var e=f.completed(A);var a=e.snapshot().tasks().get(1).artifact();var ref=a.evidence().stream().filter(r->r.kind()==EvidenceKind.SOURCE).findFirst().orElseThrow();
        clearInvocations(f.source);int expected=409;String code="STALE_SOURCE";
        switch(variant) {
            case "missing_before"->{when(f.research.sourceMeta(any(),eq(A),eq(A+100))).thenReturn(Optional.empty());expected=404;code="NOT_FOUND";}
            case "unavailable_before"->{when(f.research.sourceMeta(any(),eq(A),eq(A+100))).thenReturn(Optional.of(meta(HASH,"FAILED")));code="SOURCE_UNAVAILABLE";}
            case "missing_after"->{when(f.research.sourceMeta(any(),eq(A),eq(A+100))).thenReturn(Optional.of(meta(HASH,"READY")),Optional.empty());expected=503;code="SOURCE_CHANGED_OR_UNAVAILABLE";}
            case "unavailable_after"->{when(f.research.sourceMeta(any(),eq(A),eq(A+100))).thenReturn(Optional.of(meta(HASH,"READY")),Optional.of(meta(HASH,"FAILED")));expected=503;code="SOURCE_CHANGED_OR_UNAVAILABLE";}
            case "missing_chunk"->{when(f.source.chunk(any(),eq(A),eq(A+100),anyInt())).thenReturn(Optional.empty());expected=503;code="SOURCE_CHANGED_OR_UNAVAILABLE";}
            case "stale_before"->when(f.research.sourceMeta(any(),eq(A),eq(A+100))).thenReturn(Optional.of(meta("b".repeat(64),"READY")));
            case "stale_after"->when(f.research.sourceMeta(any(),eq(A),eq(A+100))).thenReturn(Optional.of(meta(HASH,"READY")),Optional.of(meta("b".repeat(64),"READY")));
            default->throw new AssertionError(variant);
        }
        JsonNode error=response(reread(e,a,ref),expected,"Error");assertEquals(code,error.path("code").asText());assertFalse(error.has("data"));assertFalse(error.toString().contains("graph source quotation"));
        if(variant.endsWith("before"))verifyNoInteractions(f.source);
    }
    @Test void chineseToolAndArtifactStayWithinCoreCharactersBeyondHostByteThresholds()throws Exception {
        metadata(true,"COMPLETED","active",Collections.nCopies(11,"中".repeat(1000)));
        var e=f.completed(A);for(var t:e.snapshot().tasks())assertNotNull(t.artifact(),t.toString());
        var captured=e.observations(e.plan.tasks().get(0).taskId()).get(0).result();
        assertTrue(captured.dataJson().length()<=32000);assertTrue(captured.dataJson().getBytes(StandardCharsets.UTF_8).length>32000);
        var a=e.snapshot().tasks().get(0).artifact();assertTrue(a.dataJson().length()<=16000);assertTrue(a.dataJson().getBytes(StandardCharsets.UTF_8).length>16000);
        response(get("/api/assistant/runs/{r}/artifacts/{a}",e.runId,a.artifactId()),200,"ArtifactEnvelope");
        assertEquals(0,f.host.run(e.runId).at("/usage/modelCalls").asInt());
    }
    @Test void exact32000CharacterToolResultIsNotA32000ByteRequest() {
        String data=HostJson.obj("text","中".repeat(32000-HostJson.obj("text","").toString().length())).toString();assertEquals(32000,data.length());
        var result=new ToolResult(ToolStatus.OK,f.grant,data,List.of(),List.of(),null);
        var observation=new ToolObservation("read-1","research.read_source",Instant.now(),result);
        assertEquals(data,DeterministicTemplateDecisionProvider.data(observation).toString());
        assertThrows(IllegalArgumentException.class,()->new ToolResult(ToolStatus.OK,f.grant,data+" ",List.of(),List.of(),null));
    }
    @Test void invalidInternalJsonIsServerFailureInsteadOfClientInvalidArgument() {
        var result=new ToolResult(ToolStatus.OK,f.grant,"[]",List.of(),List.of(),null);
        ApiException ex=assertThrows(ApiException.class,()->DeterministicTemplateDecisionProvider.data(new ToolObservation("read-1","research.read_source",Instant.now(),result)));
        assertEquals(500,ex.getHttpStatus());assertEquals("INTERNAL_ERROR",ex.getCode());
    }
    @Test void httpByteBoundsAreUnchangedForChineseBodies()throws Exception {
        String near=HostJson.obj("text","中".repeat(22000)).toString();assertTrue(near.length()<65536);assertTrue(near.getBytes(StandardCharsets.UTF_8).length>65536);
        response(post("/api/assistant/runs").contentType(MediaType.APPLICATION_JSON).content(near),413,"Error");
        String reread=HostJson.obj("sourceId","中".repeat(1400),"expectedContentHash",HASH).toString();assertTrue(reread.length()<4096);assertTrue(reread.getBytes(StandardCharsets.UTF_8).length>4096);
        response(post("/api/assistant/runs/r/artifacts/a/source-reread").contentType(MediaType.APPLICATION_JSON).content(reread),413,"Error");
    }
    @ParameterizedTest @ValueSource(booleans={true,false})
    void unconnectedCapabilitiesAreUnavailableWithContractReasons(boolean enabled)throws Exception {
        Map<String,String> reasons=Map.of("learning_preview","NOT_IN_STAGE1_TEMPLATE","assessment_preview","NOT_IN_STAGE1_TEMPLATE","formal_assessment","FORMAL_SCORING_UNAVAILABLE","real_model","REAL_MODEL_NOT_CONNECTED");
        try(var closed=AssistantReadHostService.disabled()) {
            JsonNode caps=enabled?f.host.capabilities():closed.capabilities();new ApprovedSchema().check("Capabilities",caps);
            for(JsonNode c:caps.path("capabilities")) {
                if(reasons.containsKey(c.path("id").asText())) {assertEquals("unavailable",c.path("status").asText());assertEquals(reasons.get(c.path("id").asText()),c.path("reasonCodes").get(0).asText());}
                else assertEquals(enabled?"available":"disabled",c.path("status").asText());
                assertFalse(c.path("formalAvailable").asBoolean());
            }
        }
    }
    @ParameterizedTest @ValueSource(strings={"AB","BA"})
    void requestOrderDefinesTwoActualTaskChains(String order)throws Exception {
        long[] docs=order.equals("AB")?new long[]{A,B}:new long[]{B,A};
        var e=f.completed(docs);assertEquals(8,e.plan.tasks().size());
        for(int i=0;i<8;i++) {
            assertTrue(e.plan.tasks().get(i).taskId().contains(":"+(i/4+1)+":"));
            assertEquals(Set.of(docs[i/4]),e.plan.tasks().get(i).scope().documentIds());assertNotNull(e.snapshot().tasks().get(i).artifact());
        }
    }
    @ParameterizedTest @ValueSource(strings={"host_off","database_off","identity_missing"})
    void readSwitchOffIsDistinctFromUnverifiedWiring(String condition)throws Exception {
        var properties=new AssistantReadHostProperties(!condition.equals("host_off"),!condition.equals("database_off"),false,8,32,Duration.ofSeconds(1800),2L*1024*1024);
        try(var host=new AssistantReadHostService(properties,()->new AssistantReadAccessContext("owner",f.grant,s->true),Clock.systemUTC(),null)) {
            JsonNode caps=host.capabilities();new ApprovedSchema().check("Capabilities",caps);assertFalse(caps.path("databaseIdentityVerified").asBoolean());
            for(int i=0;i<4;i++)assertEquals(condition.equals("identity_missing")?"unavailable":"disabled",caps.path("capabilities").get(i).path("status").asText());
            assertThrows(ApiException.class,()->host.create(create(A)));
        }
    }
    @Test void actualRuntimeUsesOneWorkerAndBothIndependentChainsSettle()throws Exception {
        Object runtime=ReflectionTestUtils.getField(f.host,"runtime");var workers=(ThreadPoolExecutor)ReflectionTestUtils.getField(runtime,"workers");
        assertNotNull(workers);assertEquals(1,workers.getCorePoolSize());var e=f.completed(B,A);assertEquals(8,e.snapshot().tasks().size());
        assertTrue(e.snapshot().tasks().stream().allMatch(t->t.finishedAt()!=null));
    }
    @ParameterizedTest @ValueSource(strings={"/capabilities","/templates","/scope/libraries","/scope/documents?libraryIds=9223372036854775807","/runs/missing","/runs/missing/artifacts/missing","/runs/missing/artifacts/missing/graph"})
    void everyGetRejectsNonEmptyBodyBeforeHandler(String route)throws Exception {
        JsonNode error=response(get("/api/assistant"+route).content(" "),400,"Error");assertEquals("INVALID_ARGUMENT",error.path("code").asText());
        verify(f.ingestion,never()).libraries(any(),anyInt(),anyInt());verify(f.ingestion,never()).documents(any(),any(),anyInt(),anyInt());
    }
}
