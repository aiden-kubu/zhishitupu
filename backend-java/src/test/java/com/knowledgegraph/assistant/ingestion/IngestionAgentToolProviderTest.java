package com.knowledgegraph.assistant.ingestion;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowledgegraph.assistant.contract.AssistantContracts.*;
import com.knowledgegraph.assistant.runtime.AssistantRuntime;
import com.knowledgegraph.assistant.runtime.DecisionProvider;
import com.knowledgegraph.assistant.source.SourceLocator;
import com.knowledgegraph.assistant.tool.AssistantTool;
import com.knowledgegraph.assistant.tool.ToolRegistry;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class IngestionAgentToolProviderTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final FakeRepository repo = new FakeRepository();
    private final FakeSource source = new FakeSource();
    private final IngestionAgentToolProvider provider = new IngestionAgentToolProvider(repo, source, mapper);
    private final ToolRegistry registry = new ToolRegistry(provider.tools());
    private final Scope project = Scope.project();
    private final Scope selected = new Scope(ScopeMode.SELECTED, Set.of(1L), Set.of(10L), Set.of());

    @Test void emptyProjectAndImportPreparationAreReadOnly() throws Exception {
        assertEquals(10, provider.tools().size());
        assertTrue(provider.tools().stream().allMatch(t -> t.descriptor().effect() == ToolEffect.READ_ONLY));
        ToolResult list = invoke("ingestion.list_documents", project, "{}");
        assertEquals(ToolStatus.OK, list.status());
        assertEquals(0, mapper.readTree(list.dataJson()).path("total").asInt());
        ToolResult importPlan = invoke("ingestion.prepare_import", project, "{}");
        assertFalse(mapper.readTree(importPlan.dataJson()).path("executionAllowed").asBoolean());
        assertEquals("/processing", mapper.readTree(importPlan.dataJson()).path("navigationTarget").asText());
        assertEquals(0, repo.writes.get());
    }

    @Test void everyToolPublishesItsRealArgumentSchema() throws Exception {
        Map<String, Set<String>> expected = Map.ofEntries(
                Map.entry("list_libraries", Set.of("page", "pageSize")),
                Map.entry("get_library", Set.of("libraryId")),
                Map.entry("list_documents", Set.of("status", "page", "pageSize")),
                Map.entry("get_document", Set.of("documentId")),
                Map.entry("get_document_unit", Set.of("documentId", "unitIndex", "chunkId", "expectedContentHash")),
                Map.entry("list_jobs", Set.of("status", "page", "pageSize")),
                Map.entry("get_job", Set.of("jobId")),
                Map.entry("get_organization", Set.of("documentId")),
                Map.entry("assess_recovery", Set.of("jobId")),
                Map.entry("prepare_import", Set.of("targetLibraryId")));
        for (AssistantTool tool : provider.tools()) {
            String suffix = tool.descriptor().name().substring("ingestion.".length());
            JsonNode schema = mapper.readTree(tool.descriptor().argumentsSchemaJson());
            assertFalse(schema.path("additionalProperties").asBoolean());
            Set<String> actual = new java.util.HashSet<>();
            schema.path("properties").fieldNames().forEachRemaining(actual::add);
            assertEquals(expected.get(suffix), actual, suffix);
            assertTrue(schema.path("required").isArray());
        }
        JsonNode sourceSchema = mapper.readTree(provider.tools().stream()
                .filter(t -> t.descriptor().name().equals("ingestion.get_document_unit"))
                .findFirst().orElseThrow().descriptor().argumentsSchemaJson());
        assertEquals(1, sourceSchema.path("properties").path("documentId").path("minimum").asInt());
        assertEquals(1000000, sourceSchema.path("properties").path("unitIndex").path("maximum").asInt());
        assertTrue(sourceSchema.path("required").toString().contains("documentId"));
        assertTrue(sourceSchema.path("required").toString().contains("unitIndex"));
        assertEquals("expectedContentHash", sourceSchema.path("dependentRequired").path("chunkId").get(0).asText());
        JsonNode listSchema = mapper.readTree(provider.tools().get(0).descriptor().argumentsSchemaJson());
        assertEquals(20, listSchema.path("properties").path("pageSize").path("maximum").asInt());
    }

    @Test void selectedScopeVerifiesAllDimensionsAndNeverFallsBackToProject() throws Exception {
        repo.documents = List.of(document(10, 1, "FAILED", true), document(20, 2, "COMPLETED", true));
        repo.libraries = List.of(library(1), library(2));
        repo.memberships = Map.of(10L, Set.of(1L, 2L), 20L, Set.of(2L));
        ToolResult scoped = invoke("ingestion.get_document", selected, "{\"documentId\":10}");
        assertEquals(ToolStatus.OK, scoped.status());
        assertEquals(1, mapper.readTree(scoped.dataJson()).path("libraryIds").size());
        assertEquals(ToolStatus.ERROR, invoke("ingestion.get_document", selected, "{\"documentId\":20}").status());
        assertEquals(ToolStatus.ERROR, invoke("ingestion.get_library", selected, "{\"libraryId\":2}").status());
        Scope nodes = new Scope(ScopeMode.SELECTED, Set.of(), Set.of(), Set.of(4L));
        assertEquals("INVALID_TOOL_ARGUMENTS", invoke("ingestion.list_documents", nodes, "{}").errorCode());
        assertEquals(0, repo.writes.get());
    }

    @Test void multiLibraryDocumentDoesNotRevealPrimaryLibraryOutsideSelectedScope() throws Exception {
        repo.documents = List.of(document(10, 1, "COMPLETED", true));
        repo.memberships = Map.of(10L, Set.of(1L, 2L));
        Scope onlySecondLibrary = new Scope(ScopeMode.SELECTED, Set.of(2L), Set.of(10L), Set.of());
        JsonNode direct = mapper.readTree(invoke("ingestion.get_document", onlySecondLibrary,
                "{\"documentId\":10}").dataJson());
        assertFalse(direct.has("libraryId"));
        assertTrue(direct.path("primaryLibraryOutsideSelectedScope").asBoolean());
        assertEquals(2, direct.path("libraryIds").get(0).asLong());
        assertFalse(direct.toString().contains("\"libraryId\":1"));
        JsonNode listed = mapper.readTree(invoke("ingestion.list_documents", onlySecondLibrary, "{}").dataJson());
        assertFalse(listed.path("items").get(0).has("libraryId"));
        assertEquals(2, listed.path("items").get(0).path("libraryIds").get(0).asLong());
    }

    @Test void paginationAndArgumentWhitelistAreEnforcedBeforeRepositoryCall() throws Exception {
        repo.libraries = List.of(library(1), library(2));
        assertEquals("INVALID_TOOL_ARGUMENTS", invoke("ingestion.list_libraries", project,
                "{\"pageSize\":21}").errorCode());
        assertEquals("INVALID_TOOL_ARGUMENTS", invoke("ingestion.list_libraries", project,
                "{\"unknown\":1}").errorCode());
        assertEquals(ToolStatus.OK, invoke("ingestion.list_documents", project,
                "{\"status\":\"PROCESSING\"}").status());
        ToolResult first = invoke("ingestion.list_libraries", project, "{\"pageSize\":1}");
        assertEquals(ToolStatus.PARTIAL, first.status());
        assertEquals(1, mapper.readTree(first.dataJson()).path("items").size());
        assertTrue(first.limitations().contains("PAGINATED_RESULT"));
    }

    @Test void lastPageIsNotMisreportedAsFullCoverage() throws Exception {
        repo.libraries = List.of(library(1), library(2));
        ToolResult lastPage = invoke("ingestion.list_libraries", project, "{\"page\":2,\"pageSize\":1}");
        JsonNode data = mapper.readTree(lastPage.dataJson());
        assertEquals(ToolStatus.PARTIAL, lastPage.status());
        assertFalse(data.path("hasMore").asBoolean());
        assertFalse(data.path("completeCoverage").asBoolean());
        assertTrue(data.path("truncated").asBoolean());
        assertTrue(lastPage.limitations().contains("PAGINATED_RESULT"));
        ToolResult firstAndAll = invoke("ingestion.list_libraries", project, "{\"page\":1,\"pageSize\":2}");
        assertEquals(ToolStatus.OK, firstAndAll.status());
        assertTrue(mapper.readTree(firstAndAll.dataJson()).path("completeCoverage").asBoolean());
    }

    @Test void sourceUsesRealChunkIdAndRejectsStaleHashOrWrongUnit() throws Exception {
        repo.documents = List.of(document(10, 1, "COMPLETED", true));
        repo.memberships = Map.of(10L, Set.of(1L));
        String hash = "a".repeat(64);
        source.unit = new SourceLocator.Unit(10, 77, 3, "page", "第3页", "READY",
                List.of(new SourceLocator.Chunk(908, 77, 0, hash, 0, 6, "原文片段", false)), 2, true);
        ToolResult list = invoke("ingestion.get_document_unit", selected, "{\"documentId\":10,\"unitIndex\":3}");
        assertEquals(ToolStatus.PARTIAL, list.status());
        assertEquals(908, mapper.readTree(list.dataJson()).path("chunks").get(0).path("chunkId").asLong());
        assertTrue(list.limitations().contains("CHUNKS_TRUNCATED"));
        ToolResult exact = invoke("ingestion.get_document_unit", selected,
                "{\"documentId\":10,\"unitIndex\":3,\"chunkId\":908,\"expectedContentHash\":\"" + hash + "\"}");
        assertEquals(ToolStatus.OK, exact.status());
        assertEquals(hash, exact.evidence().get(0).object().version());
        ToolResult stale = invoke("ingestion.get_document_unit", selected,
                "{\"documentId\":10,\"unitIndex\":3,\"chunkId\":908,\"expectedContentHash\":\"" + "b".repeat(64) + "\"}");
        assertEquals("STALE_SOURCE", stale.errorCode());
        source.unit = new SourceLocator.Unit(10, 77, 3, "page", "第3页", "READY",
                List.of(new SourceLocator.Chunk(908, 77, 0, hash, 0, 2400, "字".repeat(2400), false)), 1, false);
        ToolResult longChunk = invoke("ingestion.get_document_unit", selected,
                "{\"documentId\":10,\"unitIndex\":3,\"chunkId\":908,\"expectedContentHash\":\"" + hash + "\"}");
        assertEquals(ToolStatus.PARTIAL, longChunk.status());
        assertEquals(2048, longChunk.evidence().get(0).excerpt().length());
        assertTrue(longChunk.limitations().contains("EVIDENCE_EXCERPT_TRUNCATED"));
        source.chunkUnitId = 999;
        assertEquals("NOT_FOUND", invoke("ingestion.get_document_unit", selected,
                "{\"documentId\":10,\"unitIndex\":3,\"chunkId\":908,\"expectedContentHash\":\"" + hash + "\"}").errorCode());
    }

    @Test void recoveryStateMatrixDisclosesTheFullChainAndDoesNotCallWrites() throws Exception {
        repo.documents = List.of(document(10, 1, "FAILED", true));
        repo.memberships = Map.of(10L, Set.of(1L));
        for (var row : List.of(
                new Object[] {"UPLOADED", 0L, 0, null, "start_process"},
                new Object[] {"PARSING", 0L, 0, null, "wait"},
                new Object[] {"AWAITING_REVIEW", 2L, 0, null, "continue_ai_review"},
                new Object[] {"FAILED", 2L, 0, null, "continue_ai_review"},
                new Object[] {"CANCELLED", 0L, 2, null, "retry_job"},
                new Object[] {"FAILED", 0L, 0, "DOCUMENT_OCR_REQUIRED", "open_settings"},
                new Object[] {"COMPLETED", 0L, 0, null, "none"})) {
            String status = (String) row[0]; long count = (long) row[1]; int batches = (int) row[2];
            repo.chunks = batches > 0;
            repo.jobs = List.of(job(status, count, batches, (String) row[3], false));
            ToolResult result = invoke("ingestion.assess_recovery", selected, "{\"jobId\":100}");
            JsonNode data = mapper.readTree(result.dataJson());
            assertEquals(row[4], data.path("suggestedAction").asText());
            assertFalse(data.path("executionAllowed").asBoolean());
            if (Set.of("start_process", "retry_job", "continue_ai_review").contains(row[4])) {
                assertTrue(data.path("mayAutoReview").asBoolean());
                assertTrue(data.path("mayWriteGraph").asBoolean());
            }
        }
        repo.documents = List.of(document(10, 1, "FAILED", false));
        repo.chunks = false;
        repo.jobs = List.of(job("FAILED", 0, 0, null, false));
        assertEquals("not_recoverable", mapper.readTree(invoke("ingestion.assess_recovery", selected,
                "{\"jobId\":100}").dataJson()).path("classification").asText());
        repo.chunks = true;
        JsonNode resumableWithoutFile = mapper.readTree(invoke("ingestion.assess_recovery", selected,
                "{\"jobId\":100}").dataJson());
        assertEquals("extraction_recoverable", resumableWithoutFile.path("classification").asText());
        assertEquals("extraction_from_checkpoint_then_review_and_auto_import",
                resumableWithoutFile.path("resumeScope").asText());
        repo.ocrGaps = true;
        JsonNode withOcrGaps = mapper.readTree(invoke("ingestion.assess_recovery", selected,
                "{\"jobId\":100}").dataJson());
        assertEquals("not_recoverable", withOcrGaps.path("classification").asText());
        assertNotEquals(resumableWithoutFile.path("stateToken"), withOcrGaps.path("stateToken"));
        repo.jobs = List.of(job("FAILED", 2, 0, null, false, false));
        JsonNode relationOnly = mapper.readTree(invoke("ingestion.assess_recovery", selected,
                "{\"jobId\":100}").dataJson());
        assertEquals("candidate_state_invalid", relationOnly.path("classification").asText());
        assertEquals("none", relationOnly.path("suggestedAction").asText());
        assertEquals(0, repo.writes.get());
    }

    @Test void jobErrorsAreRedactedAndOrganizationCountsAreNotCalledNodes() throws Exception {
        repo.documents = List.of(document(10, 1, "FAILED", true));
        repo.memberships = Map.of(10L, Set.of(1L));
        repo.jobs = List.of(job("FAILED", 0, 0, "LLM_UNREACHABLE", false));
        var result = invoke("ingestion.get_job", selected, "{\"jobId\":100}");
        assertFalse(result.dataJson().contains("SECRET_CONNECTION_STRING"));
        repo.destinations = List.of(new IngestionReadRepository.Destination(1, "课程库", 3),
                new IngestionReadRepository.Destination(2, "其他库", 4));
        var organization = invoke("ingestion.get_organization", selected, "{\"documentId\":10}");
        assertEquals(1, mapper.readTree(organization.dataJson()).path("destinations").size());
        assertEquals(3, mapper.readTree(organization.dataJson()).path("destinations").get(0).path("assignedCandidateKeys").asInt());
        assertFalse(organization.dataJson().contains("nodeCount"));
    }

    @Test void proposalValidatorRejectsWrongScopeAndAllowsOnlyUnexecutedDrafts() throws Exception {
        repo.documents = List.of(document(10, 1, "FAILED", true));
        repo.memberships = Map.of(10L, Set.of(1L));
        repo.jobs = List.of(job("FAILED", 0, 0, null, false));
        IngestionProposalValidator validator = new IngestionProposalValidator(repo, mapper);
        AgentTask task = task(selected, Set.of());
        var metadata = new ActionProposal("ingestion.update_document_metadata",
                List.of(new ObjectRef("document", "10", null)), "{\"title\":\"新版教材\"}",
                "修改展示标题", List.of("REFRESH_DOCUMENT_BEFORE_EXECUTION"));
        validator.validate(task, metadata, List.of());
        assertFalse(metadata.executionAllowed());
        assertThrows(IllegalArgumentException.class, () -> validator.validate(task,
                new ActionProposal("ingestion.update_document_metadata", List.of(new ObjectRef("document", "20", null)),
                        "{\"title\":\"越界\"}", "修改标题", List.of("REFRESH_DOCUMENT_BEFORE_EXECUTION")), List.of()));
        assertThrows(IllegalArgumentException.class, () -> validator.validate(task,
                new ActionProposal("ingestion.update_document_metadata", List.of(new ObjectRef("document", "10", null)),
                        "{\"humanChecked\":true}", "人工已核验", List.of("REFRESH_DOCUMENT_BEFORE_EXECUTION")), List.of()));
        var assessment = new IngestionRecoveryAdvisor().assess(repo.jobs.get(0), repo.documents.get(0), false, false, false);
        var validJob = new ActionProposal("ingestion.retry_job", List.of(new ObjectRef("ingestion_job", "100", null)),
                "{\"stateToken\":\"" + assessment.stateToken() + "\"}", assessment.requiredImpact(),
                List.of("REFRESH_JOB_AND_DOCUMENT_BEFORE_EXECUTION"));
        validator.validate(task, validJob, List.of());
        assertFalse(validJob.executionAllowed());
        assertTrue(assessment.requiredImpact().contains("可能自动复审"));
        assertTrue(assessment.requiredImpact().contains("可能写入图谱并入库"));
        assertThrows(IllegalArgumentException.class, () -> validator.validate(task,
                new ActionProposal("ingestion.retry_job", validJob.targets(), validJob.parametersJson(),
                        "仅重试解析", validJob.preconditions()), List.of()));
        assertThrows(IllegalArgumentException.class, () -> validator.validate(task,
                new ActionProposal("ingestion.retry_job", validJob.targets(), validJob.parametersJson(),
                        "不会自动复审，也不会入库", validJob.preconditions()), List.of()));
        assertEquals(0, repo.writes.get());
    }

    @Test void proposalImpactRejectsNegationEvenWhenItContainsTheRequiredKeywords() {
        repo.documents = List.of(document(10, 1, "FAILED", true));
        repo.memberships = Map.of(10L, Set.of(1L));
        repo.jobs = List.of(job("FAILED", 0, 0, null, false));
        var assessment = new IngestionRecoveryAdvisor().assess(repo.jobs.get(0), repo.documents.get(0), false, false, false);
        var validator = new IngestionProposalValidator(repo, mapper);
        var target = List.of(new ObjectRef("ingestion_job", "100", null));
        String parameters = "{\"stateToken\":\"" + assessment.stateToken() + "\"}";
        var preconditions = List.of("REFRESH_JOB_AND_DOCUMENT_BEFORE_EXECUTION");
        validator.validate(task(selected, Set.of()), new ActionProposal("ingestion.retry_job", target,
                parameters, assessment.requiredImpact(), preconditions), List.of());
        assertThrows(IllegalArgumentException.class, () -> validator.validate(task(selected, Set.of()),
                new ActionProposal("ingestion.retry_job", target, parameters,
                        "不会自动复审，也不会入库", preconditions), List.of()));
        assertEquals(0, repo.writes.get());
    }

    @Test void runnerCallsRealAdapterWithIsolatedFakeRepository() throws Exception {
        repo.documents = List.of(document(10, 1, "COMPLETED", true));
        repo.memberships = Map.of(10L, Set.of(1L));
        AtomicInteger decisions = new AtomicInteger();
        DecisionProvider decision = context -> decisions.getAndIncrement() == 0
                ? "{\"type\":\"call_tool\",\"name\":\"ingestion.get_document\",\"arguments\":{\"documentId\":10}}"
                : "{\"type\":\"complete\",\"outcome\":\"completed\",\"summary\":\"已查到资料\",\"data\":{},\"evidenceIds\":[\"document:10:2026-09-24T00:00\"]}";
        try (var runtime = new AssistantRuntime(Map.of(Role.INGESTION, decision), provider.tools(), 1)) {
            AgentTask task = task(selected, Set.of("ingestion.get_document"));
            RunPlan plan = new RunPlan("inspect", selected, List.of(task), new Limits(3, 2, Duration.ofSeconds(5)));
            var snapshot = runtime.start(plan).completion().toCompletableFuture().get(5, TimeUnit.SECONDS);
            assertEquals(TaskStatus.COMPLETED, snapshot.tasks().get(0).status());
            assertEquals(1, snapshot.tasks().get(0).usage().toolCalls());
            assertEquals(0, repo.writes.get());
        }
    }

    private ToolResult invoke(String name, Scope scope, String json) throws Exception {
        AgentTask task = task(scope, Set.of(name));
        return registry.invoke(task, name, mapper.readTree(json), new AssistantTool.Context("run", "task",
                Role.INGESTION, scope, Instant.now().plusSeconds(10), () -> false));
    }
    private static AgentTask task(Scope scope, Set<String> tools) {
        return new AgentTask("task", Role.INGESTION, "inspect", scope, Set.of(), tools, "{}",
                new Limits(3, 3, Duration.ofSeconds(10)), new CompletionPolicy(0, false, false));
    }
    private static IngestionReadRepository.Library library(long id) {
        return new IngestionReadRepository.Library(id, "库" + id, "topic", "", "active",
                LocalDateTime.parse("2026-09-24T00:00:00"), 0, 0, 0, List.of());
    }
    private static IngestionReadRepository.Document document(long id, long libraryId, String status, boolean file) {
        return new IngestionReadRepository.Document(id, libraryId, "source.pdf", "标题", status, "active", "pdf", 100,
                "a".repeat(64), LocalDateTime.parse("2026-09-24T00:00:00"), List.of(), file);
    }
    private static IngestionReadRepository.Job job(String status, long count, int batches, String code, boolean extractable) {
        return job(status, count, batches, code, extractable, count > 0);
    }
    private static IngestionReadRepository.Job job(String status, long count, int batches, String code,
                                                    boolean extractable, boolean reviewable) {
        return new IngestionReadRepository.Job(100, 10, "source.pdf", status, status, 0, 0, 0,
                code, "SECRET_CONNECTION_STRING", 0, LocalDateTime.parse("2026-09-24T00:00:00"),
                null, count, batches, extractable, reviewable, null);
    }
    private static final class FakeSource implements SourceLocator {
        Unit unit; long chunkUnitId = 77;
        @Override public Optional<Unit> unit(Scope scope, long documentId, int unitIndex, int maxChunks, int maxCharsPerChunk) {
            return unit != null && unit.documentId() == documentId && unit.unitIndex() == unitIndex ? Optional.of(unit) : Optional.empty();
        }
        @Override public Optional<Chunk> chunk(Scope scope, long documentId, long chunkId, int maxChars) {
            return unit == null ? Optional.empty() : unit.chunks().stream().filter(c -> c.chunkId() == chunkId)
                    .map(c -> new Chunk(c.chunkId(), chunkUnitId, c.chunkIndex(), c.contentHash(), c.startOffset(),
                            c.endOffset(), c.content(), c.contentTruncated())).findFirst();
        }
    }
    private static final class FakeRepository implements IngestionReadRepository {
        List<Library> libraries = List.of(); List<Document> documents = List.of(); List<Job> jobs = List.of();
        List<Destination> destinations = List.of(); Map<Long, Set<Long>> memberships = Map.of();
        boolean chunks; boolean ocrGaps;
        AtomicInteger writes = new AtomicInteger();
        @Override public Page<Library> libraries(Scope scope, int page, int size) {
            var rows = libraries.stream().filter(l -> scope.libraryIds().isEmpty() || scope.libraryIds().contains(l.id()))
                    .filter(l -> scope.documentIds().isEmpty() || scope.documentIds().stream()
                            .anyMatch(id -> documentLibraries(id).contains(l.id()))).toList();
            return page(rows, page, size);
        }
        @Override public Optional<Library> library(long id) { return libraries.stream().filter(l -> l.id() == id).findFirst(); }
        @Override public Page<Document> documents(Scope scope, String status, int page, int size) {
            var rows = documents.stream().filter(d -> scope.documentIds().isEmpty() || scope.documentIds().contains(d.id()))
                    .filter(d -> scope.libraryIds().isEmpty() || !java.util.Collections.disjoint(scope.libraryIds(), documentLibraries(d.id())))
                    .filter(d -> status == null || status.equals(d.status())).toList();
            return page(rows, page, size);
        }
        @Override public Optional<Document> document(long id) { return documents.stream().filter(d -> d.id() == id).findFirst(); }
        @Override public Set<Long> documentLibraries(long id) { return memberships.getOrDefault(id, Set.of()); }
        @Override public Page<Job> jobs(Scope scope, String status, int page, int size) {
            var rows = jobs.stream().filter(j -> status == null || status.equals(j.status()))
                    .filter(j -> scope.documentIds().isEmpty() || scope.documentIds().contains(j.documentId()))
                    .filter(j -> scope.libraryIds().isEmpty() || !java.util.Collections.disjoint(scope.libraryIds(), documentLibraries(j.documentId()))).toList();
            return page(rows, page, size);
        }
        @Override public Optional<Job> job(long id) { return jobs.stream().filter(j -> j.id() == id).findFirst(); }
        @Override public boolean hasChunks(long documentId) { return chunks; }
        @Override public boolean hasOcrGaps(long documentId) { return ocrGaps; }
        @Override public Page<Destination> destinations(Scope scope, long documentId) {
            var rows = destinations.stream().filter(d -> scope.libraryIds().isEmpty()
                    || scope.libraryIds().contains(d.libraryId())).toList();
            return page(rows, 1, 100);
        }
        @Override public int maxUploadSizeMb() { return 200; }
        @Override public boolean visionConfigured() { return false; }
        private static <T> Page<T> page(List<T> rows, int page, int size) {
            int start = Math.min(rows.size(), (page - 1) * size);
            return new Page<>(new ArrayList<>(rows.subList(start, Math.min(rows.size(), start + size))), rows.size(), page, size);
        }
    }
}
