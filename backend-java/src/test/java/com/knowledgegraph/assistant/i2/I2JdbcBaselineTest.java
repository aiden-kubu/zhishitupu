package com.knowledgegraph.assistant.i2;

import com.knowledgegraph.assistant.contract.AssistantContracts.*;
import com.knowledgegraph.assistant.ingestion.*;
import com.knowledgegraph.assistant.learning.LearningReadRepository;
import com.knowledgegraph.assistant.quality.QualityAgentToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="KG_I2_AUTHORIZED", matches="8c6cfcb3-ba49-11f1-bf64-005056c00001")
class I2JdbcBaselineTest extends I2MySqlSupport {
    @Test void ingestionLibrariesAndMetadata() {
        var p=ingestion.libraries(LIB,1,1); assertEquals(1,p.total()); assertEquals(7101,p.items().get(0).id());
        var l=ingestion.library(7101).orElseThrow(); assertEquals(5,l.documentCount()); assertEquals(4,l.nodeCount()); assertEquals(1,l.edgeCount()); assertEquals(List.of("I2 acceptance"),l.aliases());
        assertTrue(ingestion.libraries(DISJOINT,1,20).items().isEmpty());
    }
    @Test void ingestionDocumentsFilterBeforePaging() {
        var p=ingestion.documents(LIB,null,1,2); assertEquals(5,p.total()); assertEquals(List.of(7206L,7204L),p.items().stream().map(IngestionReadRepository.Document::id).toList());
        assertEquals(List.of(7203L,7202L),ingestion.documents(LIB,null,2,2).items().stream().map(IngestionReadRepository.Document::id).toList());
        assertTrue(ingestion.documents(DISJOINT,null,1,10).items().isEmpty()); assertEquals(2,ingestion.documents(AB,"COMPLETED",1,10).total());
    }
    @Test void documentFilePresenceMetadataAndMultiLibrary() {
        assertTrue(ingestion.document(7201).orElseThrow().filePresent()); assertFalse(ingestion.document(7203).orElseThrow().filePresent());
        assertEquals(List.of("合成验收"),ingestion.document(7201).orElseThrow().tags());
        assertEquals("archived",ingestion.document(7204).orElseThrow().lifecycleStatus());
        assertEquals(Set.of(7101L,7102L),ingestion.documentLibraries(7206));
        assertEquals(7101,ingestion.destinations(LIB,7206).items().get(0).libraryId()); assertTrue(ingestion.destinations(A,7206).items().isEmpty());
    }
    @Test void multiLibraryToolHidesOutsidePrimaryId() throws Exception {
        var result=call(new IngestionAgentToolProvider(ingestion,source,JSON).tools(),"ingestion.get_document",Role.INGESTION,LIB,"{\"documentId\":7206}");
        assertNotEquals(ToolStatus.ERROR,result.status()); assertFalse(result.dataJson().contains("7102"));
    }
    @Test void jobsDerivedFlagsAndFailureAreRealSql() {
        assertEquals(3,ingestion.jobs(LIB,null,1,20).total()); assertEquals(1,ingestion.jobs(LIB,"FAILED",1,20).total());
        var a=ingestion.job(7501).orElseThrow(); assertEquals(3,a.candidateCount()); assertTrue(a.reviewable()); assertFalse(a.extractable());
        assertTrue(ingestion.job(7502).orElseThrow().extractable()); assertEquals("I1_SOURCE_MISSING",ingestion.job(7503).orElseThrow().errorCode());
        assertTrue(ingestion.hasChunks(7201)); assertFalse(ingestion.hasChunks(7203)); assertFalse(ingestion.hasOcrGaps(7201));
        assertEquals(123,ingestion.maxUploadSizeMb()); assertFalse(ingestion.visionConfigured());
    }
    @Test void sourceUnitPagingTruncationAndUtf8Hashes() throws Exception {
        var u=source.unit(A,7201,1,1,12).orElseThrow(); assertEquals(2,u.totalChunks()); assertTrue(u.truncated()); assertTrue(u.chunks().get(0).contentTruncated());
        for(var c:fixture.path("chunks")) {
            long documentId=c.path("documentId").asLong(), chunkId=c.path("id").asLong();
            var actual=source.chunk(Scope.project(),documentId,chunkId,4000).orElseThrow();
            String digest=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(actual.content().getBytes(StandardCharsets.UTF_8)));
            assertEquals(c.path("contentHash").asText(),digest); assertEquals(digest,actual.contentHash());
        }
    }
    @Test void sourceOwnershipAndUnsupportedNodeScope() {
        assertTrue(source.chunk(LIB,7205,7404,4000).isEmpty()); assertTrue(source.chunk(AB,7201,7403,4000).isEmpty()); assertTrue(source.unit(DISJOINT,7205,1,10,4000).isEmpty());
        assertThrows(IllegalArgumentException.class,()->source.chunk(NODE,7201,7401,4000));
    }
    @Test void qualityJobsCandidatesPaginationAndAiState() {
        assertEquals(7201,quality.job(A,7501).orElseThrow().documentId()); assertTrue(quality.job(DISJOINT,7501).isEmpty());
        var page=quality.candidates(A,7501,"entity",1,1); assertEquals(2,page.total()); assertTrue(page.hasMore()); assertFalse(page.completeCoverage());
        assertEquals(7602,quality.candidates(A,7501,"entity",2,1).items().get(0).id());
        var c=quality.candidate(A,7501,"entity",7601).orElseThrow(); assertEquals("APPROVED",c.reviewStatus()); assertEquals(true,c.aiDecision().approved()); assertEquals(List.of(7401L,7402L),c.evidenceChunkIds());
        assertEquals(7601,quality.entityByTempKey(A,7501,"i1-a").orElseThrow().id()); assertEquals(7801L,quality.candidate(A,7501,"relation",7603).orElseThrow().matchedEdgeId());
        assertTrue(quality.candidates(DISJOINT,7501,"entity",1,20).items().isEmpty());
    }
    @Test void qualitySourceMetadataAndGraphMatches() {
        assertEquals(7201L,quality.chunkDocumentId(7401).orElseThrow()); assertEquals(hash(7401),quality.chunkHash(A,7201,7401).orElseThrow());
        assertTrue(quality.chunkHash(A,7202,7403).isEmpty()); assertTrue(quality.chunkHash(A,7201,7403).isEmpty());
        assertEquals("I1:A/unit-1",quality.unitMeta(A,7201,7301).orElseThrow().locator());
        assertEquals(7701,quality.nodeMatch(A,7701).orElseThrow().id()); assertTrue(quality.nodeMatch(LIB,7704).isEmpty());
        assertEquals(7702,quality.edgeMatch(AB,7801).orElseThrow().targetNodeId()); assertTrue(quality.edgeMatch(LIB,7803).isEmpty());
    }
    @Test void qualityInsightUsesScopedActiveEdges() {
        var i=quality.insight(LIB,10,10); assertEquals(4,i.nodeCount()); assertEquals(1,i.edgeCount()); assertEquals(5,i.documentCount()); assertEquals(2,i.isolatedTotal());
        assertEquals(0,i.pendingReviewCount()); assertThrows(IllegalArgumentException.class,()->quality.insight(A,10,10));
    }
    @Test void qualitySnapshotReturnsCurrentSourceHashes() throws Exception {
        var tools=new QualityAgentToolProvider(quality,new com.knowledgegraph.assistant.quality.QualityEvidenceVerifier(quality,source),(r,t,a)->Optional.empty(),JSON,java.time.Clock.systemUTC()).tools();
        var result=call(tools,"quality.get_review_snapshot",Role.QUALITY_GOVERNANCE,A,"{\"jobId\":7501,\"candidateKind\":\"entity\",\"page\":1,\"pageSize\":20}");
        assertNotEquals(ToolStatus.ERROR,result.status()); assertTrue(result.dataJson().contains(hash(7401))); assertTrue(result.dataJson().contains(hash(7402)));
    }
    @Test void researchScopedSearchAliasesAndNoSqlInjection() {
        assertEquals(List.of(7701L),research.searchNodes(LIB,"节点甲",10).items().stream().map(n->n.id()).toList());
        assertEquals(7701,research.searchNodes(LIB,"alpha",10).items().get(0).id());
        assertTrue(research.searchNodes(LIB,"' OR 1=1 --",10).items().isEmpty()); assertTrue(research.searchNodes(DISJOINT,"节点",10).items().isEmpty());
        assertTrue(research.searchNodes(LIB,"节点",1).hasMore());
    }
    @Test void researchNodeDimensionsAndSourceAvailability() {
        assertTrue(research.node(A,7701).orElseThrow().sourceEvidenceAvailable()); assertFalse(research.node(LIB,7705).orElseThrow().sourceEvidenceAvailable());
        assertTrue(research.node(A,7705).isEmpty()); assertTrue(research.node(LIB,7704).isEmpty()); assertTrue(research.node(NODE,7702).isEmpty());
        assertEquals("inactive",research.node(LIB,7703).orElseThrow().status());
        assertTrue(research.node(scope(Set.of(),Set.of(7201L),Set.of()),7701).orElseThrow().libraryIds().isEmpty());
    }
    @Test void researchEdgesKeepDirectionAndBothEndpointScope() {
        assertEquals(2,research.incidentEdges(LIB,7701,10).items().size());
        assertTrue(research.outgoingEdges(AB,7702,10).items().isEmpty());
        var edge=research.outgoingEdges(AB,7701,10).items().get(0); assertEquals(7801,edge.id()); assertEquals(7701,edge.sourceNodeId()); assertEquals(7702,edge.targetNodeId());
        assertEquals(List.of(7801L),research.incidentEdges(AB,7701,10).items().stream().map(e->e.id()).toList());
    }
    @Test void researchRetrievalFiltersArchivedAndOutsideBeforeLimit() {
        assertEquals(List.of(7201L,7202L),research.matchingDocuments(LIB,List.of("验收连接"),10).items());
        assertTrue(research.matchingDocuments(LIB,List.of("验收连接"),1).hasMore());
        assertTrue(research.candidates(LIB,7204,List.of("验收连接"),10).items().isEmpty());
        assertTrue(research.candidates(LIB,7205,List.of("验收连接"),10).items().isEmpty());
        var c=research.candidates(A,7201,List.of("节点"),1); assertTrue(c.hasMore()); assertEquals(7401,c.items().get(0).chunkId());
        assertEquals(hash(7401),research.sourceMeta(A,7201,7401).orElseThrow().contentHash());
        assertFalse(research.sourceMeta(LIB,7204,7405).orElseThrow().formalAvailable());
        assertTrue(research.libraryExistsInScope(A,7101)); assertFalse(research.libraryExistsInScope(A,7102));
    }
    @Test void readSourceRejectsStaleHashArchivedAndWrongOwner() throws Exception {
        assertEquals(ToolStatus.OK,researchTool("read_source",A,"{\"documentId\":7201,\"chunkId\":7401,\"expectedContentHash\":\""+hash(7401)+"\"}").status());
        assertEquals(ToolStatus.ERROR,researchTool("read_source",A,"{\"documentId\":7201,\"chunkId\":7401,\"expectedContentHash\":\""+"0".repeat(64)+"\"}").status());
        assertEquals(ToolStatus.ERROR,researchTool("read_source",LIB,"{\"documentId\":7204,\"chunkId\":7405,\"expectedContentHash\":\""+hash(7405)+"\"}").status());
        assertEquals(ToolStatus.ERROR,researchTool("read_source",AB,"{\"documentId\":7201,\"chunkId\":7403,\"expectedContentHash\":\""+hash(7403)+"\"}").status());
    }
    @Test void learningPathFingerprintHighIdsAndScope() {
        var a=learning.currentPath(LIB); var b=learning.currentPath(LIB);
        assertEquals(LearningReadRepository.PathState.AVAILABLE,a.state()); assertEquals(1000L,a.pathId()); assertEquals(a.snapshotFingerprint(),b.snapshotFingerprint());
        assertEquals(List.of(8101L,8102L),a.steps().stream().map(s->s.stepId()).toList()); assertEquals("locked",a.steps().get(1).status()); assertEquals(90,a.steps().get(0).legacyAssessmentScore());
        assertEquals(LearningReadRepository.PathState.UNAVAILABLE,learning.currentPath(DISJOINT).state());
        assertEquals(LearningReadRepository.PathState.UNAVAILABLE,learning.currentPath(NODE).state());
    }
    @Test void learningGraphAndLinksAreScoped() {
        var graph=learning.graph(LIB,50,100); assertEquals(4,graph.nodes().size()); assertEquals(2,graph.edges().size());
        assertTrue(learning.graph(LIB,1,1).nodesTruncated()); assertEquals(7701,learning.node(A,7701).orElseThrow().id());
        assertEquals(7801,learning.edge(A,7801).orElseThrow().id()); assertTrue(learning.edge(LIB,7803).isEmpty());
        assertTrue(learning.libraryVisible(A,7101)); assertFalse(learning.libraryVisible(DISJOINT,7101));
        assertTrue(learning.nodeSourceLinked(A,7701,7201,7401)); assertFalse(learning.nodeSourceLinked(A,7701,7205,7404));
    }
    @Test void legacyHistoryCursorNullStepAndActualOwner() {
        var first=assessment.legacyAttempts(LIB,1000,null,1,null); assertTrue(first.hasMore()); assertEquals(9102,first.rows().get(0).attemptId());
        assertEquals(1000L,first.rows().get(0).actualStepPathId());
        var second=assessment.legacyAttempts(LIB,1000,null,1,9102L); assertFalse(second.hasMore()); assertEquals(9101,second.rows().get(0).attemptId()); assertNull(second.rows().get(0).stepId());
        assertEquals(1,assessment.legacyAttempts(LIB,1000,8101L,20,null).rows().size()); assertTrue(assessment.legacyAttempts(DISJOINT,1000,null,20,null).rows().isEmpty());
    }
    @Test void assessmentContextAndRepeatedHighIdBaseline() throws Exception {
        var a=assessmentTool("read_context","{\"pathId\":1000,\"stepId\":8101}"); assertNotEquals(ToolStatus.ERROR,a.status());
        var data=JSON.readTree(a.dataJson()); assertFalse(data.path("formalAvailable").asBoolean()); assertTrue(data.path("draftReady").asBoolean());
        var repeated=assessmentTool("read_context","{\"pathId\":1000,\"stepId\":8101,\"snapshotFingerprint\":\""+data.path("snapshotFingerprint").asText()+"\"}"); assertNotEquals(ToolStatus.ERROR,repeated.status());
        assertEquals(ToolStatus.ERROR,assessmentTool("read_context","{\"pathId\":1002}").status());
    }
    @Test void formalUnavailableAndLegacyNeverBecomeValid() throws Exception {
        var valid=JSON.readTree(assessmentTool("read_valid_results","{\"pathId\":1000}").dataJson()); assertFalse(valid.path("formalAvailable").asBoolean());
        var history=assessmentTool("read_legacy_history","{\"pathId\":1000,\"limit\":1}"); assertNotEquals(ToolStatus.ERROR,history.status()); assertTrue(history.dataJson().contains("legacy_unverified"));
    }
    @Test void missingRowsReturnEmptyWithoutBroadeningScope() {
        assertTrue(ingestion.library(999999).isEmpty()); assertTrue(ingestion.document(999999).isEmpty()); assertTrue(ingestion.job(999999).isEmpty());
        assertTrue(source.chunk(A,7201,999999,4000).isEmpty()); assertTrue(quality.entityByTempKey(A,7501,"missing").isEmpty());
        assertTrue(research.node(LIB,999999).isEmpty()); assertTrue(assessment.legacyAttempts(LIB,999999,null,20,null).rows().isEmpty());
    }
}
