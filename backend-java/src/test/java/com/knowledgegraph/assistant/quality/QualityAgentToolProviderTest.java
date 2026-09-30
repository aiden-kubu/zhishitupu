package com.knowledgegraph.assistant.quality;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowledgegraph.assistant.contract.AssistantContracts.*;
import com.knowledgegraph.assistant.runtime.AssistantRuntime;
import com.knowledgegraph.assistant.runtime.DecisionProvider;
import com.knowledgegraph.assistant.source.SourceLocator;
import com.knowledgegraph.assistant.tool.AssistantTool;
import com.knowledgegraph.assistant.tool.ToolRegistry;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class QualityAgentToolProviderTest {
    private static final String HASH = "a".repeat(64);
    private static final Scope PROJECT = Scope.project();
    private final ObjectMapper mapper = new ObjectMapper();
    private final QualityReadRepository repository = mock(QualityReadRepository.class);
    private final SourceLocator locator = mock(SourceLocator.class);
    private final QualityArtifactReader artifacts = mock(QualityArtifactReader.class);
    private final QualityAgentToolProvider provider = new QualityAgentToolProvider(repository,
            new QualityEvidenceVerifier(repository, locator), artifacts, mapper, Clock.systemUTC());

    @Test void fourToolsHaveRealSchemasAndRejectExtraFieldsAndUnsupportedScopes() throws Exception {
        List<AssistantTool> tools = provider.tools();
        assertEquals(Set.of("quality.get_review_snapshot", "quality.read_candidate_sources",
                "quality.get_insight_snapshot", "quality.verify_artifact_claims"),
                tools.stream().map(t -> t.descriptor().name()).collect(java.util.stream.Collectors.toSet()));
        for (AssistantTool tool : tools) {
            ToolDescriptor d = tool.descriptor();
            assertEquals(ToolEffect.READ_ONLY, d.effect());
            assertEquals(Set.of(Role.QUALITY_GOVERNANCE), d.roles());
            JsonNode schema = mapper.readTree(d.argumentsSchemaJson());
            assertFalse(schema.path("additionalProperties").booleanValue());
            assertTrue(schema.path("required").size() > 0);
            schema.path("required").forEach(field -> assertTrue(schema.path("properties").has(field.asText())));
        }
        var review = named("quality.get_review_snapshot");
        review.validateArguments(json("{" + "\"jobId\":1,\"candidateKind\":\"entity\",\"page\":1,\"pageSize\":20}"), PROJECT);
        assertThrows(IllegalArgumentException.class, () -> review.validateArguments(json("{" +
                "\"jobId\":1,\"candidateKind\":\"entity\",\"page\":1,\"pageSize\":20,\"scope\":\"project\"}"), PROJECT));
        assertThrows(IllegalArgumentException.class, () -> review.validateArguments(json("{" +
                "\"jobId\":0,\"candidateKind\":\"entity\",\"page\":1,\"pageSize\":20}"), PROJECT));
        Scope nodes = new Scope(ScopeMode.SELECTED, Set.of(), Set.of(), Set.of(9L));
        assertThrows(IllegalArgumentException.class, () -> review.validateArguments(json("{" +
                "\"jobId\":1,\"candidateKind\":\"entity\",\"page\":1,\"pageSize\":20}"), nodes));
        var insight = named("quality.get_insight_snapshot");
        assertThrows(IllegalArgumentException.class, () -> insight.validateArguments(json("{" +
                "\"isolatedLimit\":10,\"topLimit\":5}"),
                new Scope(ScopeMode.SELECTED, Set.of(), Set.of(7L), Set.of())));
        assertThrows(IllegalArgumentException.class, () -> named("quality.read_candidate_sources")
                .validateArguments(json("{\"jobId\":1,\"candidateKind\":\"entity\",\"candidateId\":2,"
                        + "\"maxChars\":600,\"expectedRefs\":[{\"chunkId\":7,\"expectedHash\":\"short\"}]}"), PROJECT));
        assertThrows(IllegalArgumentException.class, () -> named("quality.verify_artifact_claims")
                .validateArguments(json("{\"artifactId\":\"a\",\"claimIds\":[\"x\",\"x\"]}"), PROJECT));
    }

    @Test void reviewSeparatesFourStatesAndDoesNotInferPerCandidateImport() throws Exception {
        var candidate = new QualityReadRepository.Candidate(11, 2, "entity", "名称", "a", null, null, null,
                "EDITED", List.of(7L), 45L, null,
                new QualityReadRepository.AiDecision(true, "AI 通过", "test-model", "2026-09-24"));
        when(repository.job(PROJECT, 2)).thenReturn(Optional.of(new QualityReadRepository.Job(2, 1,
                "COMPLETED", "COMPLETED", "COMPLETED")));
        when(repository.candidates(PROJECT, 2, "entity", 1, 20)).thenReturn(
                new QualityReadRepository.Page<>(List.of(candidate), 1, 1, 20));
        when(repository.chunkHash(PROJECT, 1, 7)).thenReturn(Optional.of(HASH));
        when(repository.nodeMatch(PROJECT, 45)).thenReturn(Optional.empty());
        ToolResult result = named("quality.get_review_snapshot").execute(context(PROJECT),
                json("{" + "\"jobId\":2,\"candidateKind\":\"entity\",\"page\":1,\"pageSize\":20}"));
        JsonNode item = mapper.readTree(result.dataJson()).path("items").get(0);
        assertEquals(ToolStatus.OK, result.status());
        assertEquals("EDITED", item.path("reviewStatus").asText());
        assertTrue(item.path("aiDecision").path("approved").asBoolean());
        assertEquals("unavailable", item.path("humanDecision").path("status").asText());
        assertEquals("unknown", item.path("importState").path("candidateOutcome").asText());
        assertEquals("unavailable_or_out_of_scope", item.path("matchedObject").path("status").asText());
        assertFalse(mapper.readTree(result.dataJson()).path("executionAllowed").asBoolean());
    }

    @Test void reviewDoesNotRevealWrongDocumentEvidenceIds() throws Exception {
        var candidate = new QualityReadRepository.Candidate(11, 2, "entity", "A", "a", null, null, null,
                "PENDING", List.of(7L, 8L), null, null, null);
        when(repository.job(PROJECT, 2)).thenReturn(Optional.of(new QualityReadRepository.Job(2, 1,
                "FAILED", "FAILED", "FAILED")));
        when(repository.candidates(PROJECT, 2, "entity", 1, 20)).thenReturn(
                new QualityReadRepository.Page<>(List.of(candidate), 1, 1, 20));
        when(repository.chunkHash(PROJECT, 1, 7)).thenReturn(Optional.of(HASH));
        ToolResult result = named("quality.get_review_snapshot").execute(context(PROJECT),
                json("{\"jobId\":2,\"candidateKind\":\"entity\",\"page\":1,\"pageSize\":20}"));
        JsonNode row = mapper.readTree(result.dataJson()).path("items").get(0);
        assertEquals(1, row.path("evidenceChunkIds").size());
        assertEquals(7, row.path("evidenceChunkIds").get(0).asLong());
        assertEquals(HASH, row.path("evidenceRefs").get(0).path("expectedHash").asText());
        assertTrue(row.path("evidenceIdsExcluded").asBoolean());
        assertFalse(result.dataJson().contains("\"chunkId\":8"));
        verify(repository).chunkHash(PROJECT, 1, 8);
    }

    @Test void snapshotSuppliesOwnedHashForNormalSourceReadWithoutCallerPreloadingIt() throws Exception {
        var candidate = new QualityReadRepository.Candidate(11, 2, "entity", "A", "a", null, null, null,
                "PENDING", List.of(7L), null, null, null);
        when(repository.job(PROJECT, 2)).thenReturn(Optional.of(new QualityReadRepository.Job(2, 1,
                "FAILED", "FAILED", "FAILED")));
        when(repository.candidates(PROJECT, 2, "entity", 1, 20)).thenReturn(
                new QualityReadRepository.Page<>(List.of(candidate), 1, 1, 20));
        when(repository.candidate(PROJECT, 2, "entity", 11)).thenReturn(Optional.of(candidate));
        when(repository.chunkHash(PROJECT, 1, 7)).thenReturn(Optional.of(HASH));
        when(repository.chunkDocumentId(7)).thenReturn(Optional.of(1L));
        when(repository.unitMeta(PROJECT, 1, 5)).thenReturn(Optional.of(
                new QualityReadRepository.UnitMeta(1, 5, 3, "第3页")));
        when(locator.chunk(PROJECT, 1, 7, 600)).thenReturn(Optional.of(
                new SourceLocator.Chunk(7, 5, 0, HASH, 0, 2, "正文", false)));

        ToolResult snapshot = named("quality.get_review_snapshot").execute(context(PROJECT),
                json("{\"jobId\":2,\"candidateKind\":\"entity\",\"page\":1,\"pageSize\":20}"));
        assertEquals(ToolStatus.OK, snapshot.status());
        JsonNode row = mapper.readTree(snapshot.dataJson()).path("items").get(0);
        JsonNode observedRef = row.path("evidenceRefs").get(0);
        String request = "{\"jobId\":2,\"candidateKind\":\"entity\",\"candidateId\":"
                + row.path("id").asLong() + ",\"maxChars\":600,\"expectedRefs\":["
                + observedRef.toString() + "]}";
        ToolResult source = named("quality.read_candidate_sources").execute(context(PROJECT), json(request));
        JsonNode data = mapper.readTree(source.dataJson());
        assertEquals(ToolStatus.OK, source.status());
        assertEquals("CURRENT", data.path("checks").get(0).path("status").asText());
        assertTrue(data.path("candidateEvidenceCompleteCoverage").asBoolean());
        assertEquals(1, source.evidence().size());
    }

    @Test void readingOneOfTwoCandidateSourcesCoversRequestButNotCandidate() throws Exception {
        stubTwoCandidateSources();
        ToolResult result = named("quality.read_candidate_sources").execute(context(PROJECT), json("{" +
                "\"jobId\":2,\"candidateKind\":\"entity\",\"candidateId\":11,\"maxChars\":600," +
                "\"expectedRefs\":[{\"chunkId\":7,\"expectedHash\":\"" + HASH + "\"}]}"));
        JsonNode data = mapper.readTree(result.dataJson());
        assertEquals(ToolStatus.PARTIAL, result.status());
        assertEquals(2, data.path("candidateEvidenceTotal").asInt());
        assertEquals(1, data.path("checkedCount").asInt());
        assertEquals(1, data.path("candidateEvidenceChecked").asInt());
        assertEquals(1, data.path("candidateEvidenceUnchecked").asInt());
        assertTrue(data.path("requestCompleteCoverage").asBoolean());
        assertFalse(data.path("candidateEvidenceCompleteCoverage").asBoolean());
        assertFalse(data.path("completeCoverage").asBoolean());
        assertTrue(result.limitations().contains("CANDIDATE_EVIDENCE_UNCHECKED"));
        verify(repository, never()).chunkDocumentId(8);
    }

    @Test void readingAllCandidateSourcesReportsFullCandidateCoverage() throws Exception {
        stubTwoCandidateSources();
        ToolResult result = named("quality.read_candidate_sources").execute(context(PROJECT), json("{" +
                "\"jobId\":2,\"candidateKind\":\"entity\",\"candidateId\":11,\"maxChars\":600," +
                "\"expectedRefs\":[{\"chunkId\":7,\"expectedHash\":\"" + HASH + "\"}," +
                "{\"chunkId\":8,\"expectedHash\":\"" + "b".repeat(64) + "\"}]}"));
        JsonNode data = mapper.readTree(result.dataJson());
        assertEquals(ToolStatus.OK, result.status());
        assertEquals(2, data.path("candidateEvidenceTotal").asInt());
        assertEquals(2, data.path("checkedCount").asInt());
        assertEquals(2, data.path("candidateEvidenceChecked").asInt());
        assertEquals(0, data.path("candidateEvidenceUnchecked").asInt());
        assertTrue(data.path("requestCompleteCoverage").asBoolean());
        assertTrue(data.path("candidateEvidenceCompleteCoverage").asBoolean());
        assertTrue(data.path("completeCoverage").asBoolean());
        assertEquals(2, result.evidence().size());
    }

    private void stubTwoCandidateSources() {
        when(repository.job(PROJECT, 2)).thenReturn(Optional.of(new QualityReadRepository.Job(2, 1,
                "FAILED", "FAILED", "FAILED")));
        when(repository.candidate(PROJECT, 2, "entity", 11)).thenReturn(Optional.of(
                new QualityReadRepository.Candidate(11, 2, "entity", "A", "a", null, null, null,
                        "PENDING", List.of(7L, 8L), null, null, null)));
        when(repository.chunkDocumentId(7)).thenReturn(Optional.of(1L));
        when(repository.chunkDocumentId(8)).thenReturn(Optional.of(1L));
        when(repository.unitMeta(PROJECT, 1, 5)).thenReturn(Optional.of(
                new QualityReadRepository.UnitMeta(1, 5, 3, "第3页")));
        when(locator.chunk(PROJECT, 1, 7, 600)).thenReturn(Optional.of(
                new SourceLocator.Chunk(7, 5, 0, HASH, 0, 2, "甲", false)));
        when(locator.chunk(PROJECT, 1, 8, 600)).thenReturn(Optional.of(
                new SourceLocator.Chunk(8, 5, 1, "b".repeat(64), 2, 4, "乙", false)));
    }

    @Test void relationMatchChecksBothEndpointsAndDirectionAndLastPageIsPartial() throws Exception {
        var relation = new QualityReadRepository.Candidate(12, 2, "relation", null, null, "a", "b", "先修",
                "ACCEPTED", List.of(), null, 90L, null);
        var source = new QualityReadRepository.Candidate(11, 2, "entity", "A", "a", null, null, null,
                "ACCEPTED", List.of(), 45L, null, null);
        var target = new QualityReadRepository.Candidate(13, 2, "entity", "B", "b", null, null, null,
                "ACCEPTED", List.of(), 46L, null, null);
        when(repository.job(PROJECT, 2)).thenReturn(Optional.of(new QualityReadRepository.Job(2, 1,
                "COMPLETED", "COMPLETED", "COMPLETED")));
        when(repository.candidates(PROJECT, 2, "relation", 2, 1)).thenReturn(
                new QualityReadRepository.Page<>(List.of(relation), 2, 2, 1));
        when(repository.candidates(PROJECT, 2, "entity", 1, 1)).thenReturn(
                new QualityReadRepository.Page<>(List.of(source), 2, 1, 1));
        when(repository.edgeMatch(PROJECT, 90)).thenReturn(Optional.of(new QualityReadRepository.EdgeMatch(90, 46, 45,
                "先修", "active")));
        when(repository.entityByTempKey(PROJECT, 2, "a")).thenReturn(Optional.of(source));
        when(repository.entityByTempKey(PROJECT, 2, "b")).thenReturn(Optional.of(target));
        when(repository.nodeMatch(PROJECT, 45)).thenReturn(Optional.of(new QualityReadRepository.NodeMatch(45, "A", "active")));
        when(repository.nodeMatch(PROJECT, 46)).thenReturn(Optional.of(new QualityReadRepository.NodeMatch(46, "B", "active")));
        ToolResult result = named("quality.get_review_snapshot").execute(context(PROJECT),
                json("{" + "\"jobId\":2,\"candidateKind\":\"relation\",\"page\":2,\"pageSize\":1}"));
        JsonNode body = mapper.readTree(result.dataJson());
        assertEquals(ToolStatus.PARTIAL, result.status());
        assertFalse(body.path("hasMore").asBoolean());
        assertFalse(body.path("completeCoverage").asBoolean());
        assertEquals("direction_or_type_mismatch", body.path("items").get(0).path("matchedObject").path("status").asText());
    }

    @Test void candidateSourcesNeverUpgradeStaleOrForgedReferences() throws Exception {
        when(repository.job(PROJECT, 2)).thenReturn(Optional.of(new QualityReadRepository.Job(2, 1,
                "FAILED", "FAILED", "FAILED")));
        when(repository.candidate(PROJECT, 2, "entity", 11)).thenReturn(Optional.of(
                new QualityReadRepository.Candidate(11, 2, "entity", "A", "a", null, null, null,
                        "PENDING", List.of(7L), null, null, null)));
        when(repository.chunkDocumentId(7)).thenReturn(Optional.of(1L));
        when(repository.unitMeta(PROJECT, 1, 5)).thenReturn(Optional.of(new QualityReadRepository.UnitMeta(1, 5, 3, "第3页")));
        when(locator.chunk(PROJECT, 1, 7, 600)).thenReturn(Optional.of(
                new SourceLocator.Chunk(7, 5, 0, "b".repeat(64), 0, 3, "新内容", false)));
        ToolResult result = named("quality.read_candidate_sources").execute(context(PROJECT), json("{" +
                "\"jobId\":2,\"candidateKind\":\"entity\",\"candidateId\":11,\"maxChars\":600," +
                "\"expectedRefs\":[{\"chunkId\":7,\"expectedHash\":\"" + HASH + "\"}]}"));
        assertEquals(ToolStatus.ERROR, result.status());
        assertTrue(result.evidence().isEmpty());
        assertEquals("STALE", mapper.readTree(result.dataJson()).path("checks").get(0).path("status").asText());
        verify(locator).chunk(PROJECT, 1, 7, 600);

        ToolResult forged = named("quality.read_candidate_sources").execute(context(PROJECT), json("{" +
                "\"jobId\":2,\"candidateKind\":\"entity\",\"candidateId\":11,\"maxChars\":600," +
                "\"expectedRefs\":[{\"chunkId\":8,\"expectedHash\":\"" + HASH + "\"}]}"));
        assertEquals(ToolStatus.ERROR, forged.status());
        assertEquals("NOT_CANDIDATE_EVIDENCE", mapper.readTree(forged.dataJson()).path("checks").get(0).path("status").asText());
        verify(repository, never()).chunkDocumentId(8);
    }

    @Test void truncatedSourceRemainsPartialAndNeverClaimsFullCoverage() throws Exception {
        when(repository.job(PROJECT, 2)).thenReturn(Optional.of(new QualityReadRepository.Job(2, 1,
                "FAILED", "FAILED", "FAILED")));
        when(repository.candidate(PROJECT, 2, "entity", 11)).thenReturn(Optional.of(
                new QualityReadRepository.Candidate(11, 2, "entity", "A", "a", null, null, null,
                        "PENDING", List.of(7L), null, null, null)));
        when(repository.chunkDocumentId(7)).thenReturn(Optional.of(1L));
        when(repository.unitMeta(PROJECT, 1, 5)).thenReturn(Optional.of(new QualityReadRepository.UnitMeta(1, 5, 3, "第3页")));
        when(locator.chunk(PROJECT, 1, 7, 600)).thenReturn(Optional.of(
                new SourceLocator.Chunk(7, 5, 0, HASH, 0, 1200, "正文".repeat(300), true)));
        ToolResult result = named("quality.read_candidate_sources").execute(context(PROJECT), json("{" +
                "\"jobId\":2,\"candidateKind\":\"entity\",\"candidateId\":11,\"maxChars\":600," +
                "\"expectedRefs\":[{\"chunkId\":7,\"expectedHash\":\"" + HASH + "\"}]}"));
        assertEquals(ToolStatus.PARTIAL, result.status());
        assertFalse(mapper.readTree(result.dataJson()).path("completeCoverage").asBoolean());
        assertEquals("TRUNCATED", mapper.readTree(result.dataJson()).path("checks").get(0).path("status").asText());
    }

    @Test void insightUsesHostScopeAndReportsCoverageWithoutInventingTrend() throws Exception {
        Scope selected = new Scope(ScopeMode.SELECTED, Set.of(3L), Set.of(), Set.of());
        when(repository.insight(selected, 1, 1)).thenReturn(new QualityReadRepository.Insight(3, 2, 1, 4,
                List.of(new QualityReadRepository.TypeCount("concept", 3)),
                List.of(new QualityReadRepository.NodeItem(10, "A", "concept", 2)), 2,
                List.of(new QualityReadRepository.NodeItem(11, "B", "concept", 0))));
        ToolResult result = named("quality.get_insight_snapshot").execute(context(selected),
                json("{\"isolatedLimit\":1,\"topLimit\":1}"));
        JsonNode data = mapper.readTree(result.dataJson());
        assertEquals(ToolStatus.PARTIAL, result.status());
        assertEquals(2, data.path("isolated").path("total").asInt());
        assertTrue(data.path("isolated").path("truncated").asBoolean());
        assertFalse(data.path("trendAvailable").asBoolean());
        assertEquals(3, data.path("effectiveScope").path("libraryIds").get(0).asLong());
        verify(repository).insight(selected, 1, 1);
    }

    @Test void directDependencyReferencesAreReReadButSemanticSupportIsNotAsserted() throws Exception {
        when(artifacts.directArtifact("run", "task", "artifact:1")).thenReturn(Optional.of(
                new QualityArtifactReader.DirectArtifact("artifact:1", "research", List.of(
                        new QualityArtifactReader.Claim("claim:1", List.of(
                                new QualityArtifactReader.SourcePointer("research:source:7", 1, 7, HASH)))))));
        when(repository.chunkDocumentId(7)).thenReturn(Optional.of(1L));
        when(repository.unitMeta(PROJECT, 1, 5)).thenReturn(Optional.of(new QualityReadRepository.UnitMeta(1, 5, 3, "第3页")));
        when(locator.chunk(PROJECT, 1, 7, 600)).thenReturn(Optional.of(
                new SourceLocator.Chunk(7, 5, 0, HASH, 0, 2, "正文", false)));
        ToolResult result = named("quality.verify_artifact_claims").execute(context(PROJECT),
                json("{\"artifactId\":\"artifact:1\",\"claimIds\":[\"claim:1\"]}"));
        JsonNode claim = mapper.readTree(result.dataJson()).path("checks").get(0);
        assertEquals(ToolStatus.OK, result.status());
        assertEquals("CURRENT", claim.path("referenceStatus").asText());
        assertEquals("not_evaluated", claim.path("semanticSupport").asText());
        assertTrue(result.evidence().get(0).sourceId().startsWith("quality:verified:"));
        assertEquals("research:source:7", claim.path("sources").get(0).path("originalSourceId").asText());

        ToolResult unavailable = named("quality.verify_artifact_claims").execute(context(PROJECT),
                json("{\"artifactId\":\"other-run\",\"claimIds\":[\"claim:1\"]}"));
        assertEquals(ToolStatus.ERROR, unavailable.status());
        assertEquals("DIRECT_ARTIFACT_UNAVAILABLE", unavailable.errorCode());
        verify(artifacts).directArtifact("run", "task", "artifact:1");
    }

    @Test void unknownClaimAndForgedPointerCannotBecomeVerifiedEvidence() throws Exception {
        when(artifacts.directArtifact("run", "task", "artifact:1")).thenReturn(Optional.of(
                new QualityArtifactReader.DirectArtifact("artifact:1", "research", List.of(
                        new QualityArtifactReader.Claim("claim:1", List.of(
                                new QualityArtifactReader.SourcePointer("research:source:7", 1, 7, HASH)))))));
        when(repository.chunkDocumentId(7)).thenReturn(Optional.of(99L));
        ToolResult result = named("quality.verify_artifact_claims").execute(context(PROJECT),
                json("{\"artifactId\":\"artifact:1\",\"claimIds\":[\"claim:1\",\"unknown\"]}"));
        assertEquals(ToolStatus.ERROR, result.status());
        assertTrue(result.evidence().isEmpty());
        JsonNode checks = mapper.readTree(result.dataJson()).path("checks");
        assertEquals("WRONG_DOCUMENT", checks.get(0).path("sources").get(0).path("status").asText());
        assertEquals("UNKNOWN_CLAIM", checks.get(1).path("referenceStatus").asText());
        verifyNoInteractions(locator);
    }

    @Test void oversizedResultFailsWithinSharedDataBudget() throws Exception {
        when(repository.insight(PROJECT, 1, 1)).thenReturn(new QualityReadRepository.Insight(1, 0, 0, 0,
                List.of(new QualityReadRepository.TypeCount("x".repeat(31_000), 1)),
                List.of(new QualityReadRepository.NodeItem(1, "A", "concept", 0)), 0, List.of()));
        ToolResult result = named("quality.get_insight_snapshot").execute(context(PROJECT),
                json("{\"isolatedLimit\":1,\"topLimit\":1}"));
        assertEquals(ToolStatus.ERROR, result.status());
        assertEquals("QUALITY_RESULT_LIMIT", result.errorCode());
    }

    @Test void partialQualityToolDowngradesRunnerCompletion() throws Exception {
        when(repository.insight(PROJECT, 1, 1)).thenReturn(new QualityReadRepository.Insight(2, 0, 0, 0,
                List.of(), List.of(new QualityReadRepository.NodeItem(1, "A", "concept", 0)), 0, List.of()));
        AtomicInteger decisions = new AtomicInteger();
        DecisionProvider fake = context -> decisions.getAndIncrement() == 0
                ? "{\"type\":\"call_tool\",\"name\":\"quality.get_insight_snapshot\",\"arguments\":{\"isolatedLimit\":1,\"topLimit\":1}}"
                : "{\"type\":\"complete\",\"outcome\":\"completed\",\"summary\":\"partial\",\"data\":{},\"evidenceIds\":[]}";
        AgentTask task = new AgentTask("quality-task", Role.QUALITY_GOVERNANCE, "inspect project", PROJECT,
                Set.of(), Set.of("quality.get_insight_snapshot"), "{}", new Limits(3, 2, Duration.ofSeconds(5)),
                new CompletionPolicy(1, false, false));
        try (AssistantRuntime runtime = new AssistantRuntime(Map.of(Role.QUALITY_GOVERNANCE, fake), provider.tools(), 1)) {
            RunSnapshot snapshot = runtime.start(new RunPlan("inspect", PROJECT, List.of(task),
                    new Limits(3, 2, Duration.ofSeconds(5)))).completion().toCompletableFuture().get(5, TimeUnit.SECONDS);
            assertEquals(TaskStatus.PARTIAL, snapshot.tasks().get(0).status());
            assertTrue(snapshot.tasks().get(0).artifact().limitations().contains("LIST_COVERAGE_INCOMPLETE"));
        }
    }

    @Test void fakeRunnerCanCallQualityToolWithoutAnyWriteOrReceipt() throws Exception {
        when(repository.insight(PROJECT, 1, 1)).thenReturn(new QualityReadRepository.Insight(0, 0, 0, 0,
                List.of(), List.of(), 0, List.of()));
        AtomicInteger decisions = new AtomicInteger();
        DecisionProvider fake = context -> decisions.getAndIncrement() == 0
                ? "{\"type\":\"call_tool\",\"name\":\"quality.get_insight_snapshot\",\"arguments\":{\"isolatedLimit\":1,\"topLimit\":1}}"
                : "{\"type\":\"complete\",\"outcome\":\"completed\",\"summary\":\"empty project\",\"data\":{},\"evidenceIds\":[]}";
        AgentTask task = new AgentTask("quality-task", Role.QUALITY_GOVERNANCE, "inspect project", PROJECT,
                Set.of(), Set.of("quality.get_insight_snapshot"), "{}", new Limits(3, 2, Duration.ofSeconds(5)),
                new CompletionPolicy(1, false, false));
        try (AssistantRuntime runtime = new AssistantRuntime(Map.of(Role.QUALITY_GOVERNANCE, fake),
                provider.tools(), 1)) {
            RunSnapshot snapshot = runtime.start(new RunPlan("inspect", PROJECT, List.of(task),
                    new Limits(3, 2, Duration.ofSeconds(5)))).completion().toCompletableFuture().get(5, TimeUnit.SECONDS);
            assertEquals(TaskStatus.COMPLETED, snapshot.tasks().get(0).status());
            assertFalse(snapshot.tasks().get(0).artifact().executionReceipt());
            assertTrue(snapshot.tasks().get(0).artifact().actionProposals().isEmpty());
        }
        verifyNoInteractions(artifacts, locator);
    }

    private AssistantTool named(String name) {
        return provider.tools().stream().filter(t -> t.descriptor().name().equals(name)).findFirst().orElseThrow();
    }
    private AssistantTool.Context context(Scope scope) {
        return new AssistantTool.Context("run", "task", Role.QUALITY_GOVERNANCE, scope,
                Instant.now().plusSeconds(30), () -> false);
    }
    private JsonNode json(String source) {
        try { return mapper.readTree(source); }
        catch (Exception ex) { throw new IllegalArgumentException(ex); }
    }
}
