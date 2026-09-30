package com.knowledgegraph.assistant.research;

import com.knowledgegraph.assistant.contract.AssistantContracts.*;
import com.knowledgegraph.assistant.runtime.AssistantRuntime;
import com.knowledgegraph.assistant.runtime.DecisionProvider;
import com.knowledgegraph.assistant.source.SourceLocator;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

class ResearchComparisonTest {
    private static final Scope A = new Scope(ScopeMode.SELECTED, Set.of(1L), Set.of(11L), Set.of());
    private static final Scope B = new Scope(ScopeMode.SELECTED, Set.of(1L), Set.of(12L), Set.of());
    private static final Scope AB = new Scope(ScopeMode.SELECTED, Set.of(1L), Set.of(11L, 12L), Set.of());

    @Test void comparisonRereadsBothOriginalSourcesAndNeverClaimsSemanticWinner() {
        var source = new FakeSource();
        var result = ResearchArtifacts.compare(consumer(), List.of(study("bookA", A, 11, 101, "h101", Outcome.COMPLETED),
                study("bookB", B, 12, 102, "h102", Outcome.COMPLETED)), 11, 12, new FakeRepo(), source);
        assertEquals(2, source.calls);
        assertEquals(2, result.evidence().size());
        assertEquals(Outcome.PARTIAL, result.outcome());
        assertNull(result.data().get("winner"));
        assertEquals("not_evaluated", result.data().get("semanticSupport"));
        assertTrue(result.limitations().contains("SEMANTIC_COMPARISON_NOT_EVALUATED"));
        assertTrue(result.limitations().contains("FULL_CORPUS_COVERAGE_NOT_VERIFIED"));
    }

    @Test void isolatedRuntimeRunsABIndependentlyThenPassesOnlyDirectArtifacts() throws Exception {
        CountDownLatch both = new CountDownLatch(2);
        DecisionProvider provider = context -> {
            String id = context.task().taskId();
            if (id.equals("bookA") || id.equals("bookB")) {
                assertTrue(context.inputs().isEmpty());
                both.countDown();
                assertTrue(both.await(3, TimeUnit.SECONDS));
                long doc = id.equals("bookA") ? 11 : 12;
                return completion("completed", "{\"kind\":\"document_research\",\"documentId\":" + doc
                        + ",\"claims\":[],\"coverage\":{\"completeCoverage\":false,\"truncated\":false}}");
            }
            assertEquals("compare", id);
            assertEquals(Set.of("bookA", "bookB"), context.inputs().stream().map(Artifact::producerTaskId)
                    .collect(java.util.stream.Collectors.toSet()));
            assertTrue(context.observations().isEmpty());
            var comparison = ResearchArtifacts.compare(context.task(), context.inputs(), 11, 12,
                    new FakeRepo(), new FakeSource());
            assertEquals(Outcome.PARTIAL, comparison.outcome());
            return completion("partial", "{\"kind\":\"document_comparison\",\"completeCoverage\":false}");
        };
        try (var runtime = new AssistantRuntime(java.util.Map.of(Role.GRAPH_RESEARCH, provider), List.of(), 2)) {
            var result = runtime.start(plan()).completion().toCompletableFuture().get(6, TimeUnit.SECONDS);
            assertEquals(TaskStatus.COMPLETED, result.tasks().get(0).status());
            assertEquals(TaskStatus.COMPLETED, result.tasks().get(1).status());
            assertEquals(TaskStatus.PARTIAL, result.tasks().get(2).status());
        }
    }

    @Test void failedAStillPreservesBAndBlocksComparison() throws Exception {
        DecisionProvider provider = context -> {
            if (context.task().taskId().equals("bookA")) throw new IllegalStateException("fixture failure");
            if (context.task().taskId().equals("bookB")) return completion("completed", "{}");
            fail("blocked comparison must never be invoked");
            return "";
        };
        try (var runtime = new AssistantRuntime(java.util.Map.of(Role.GRAPH_RESEARCH, provider), List.of(), 2)) {
            var result = runtime.start(plan()).completion().toCompletableFuture().get(6, TimeUnit.SECONDS);
            assertEquals(TaskStatus.FAILED, result.tasks().get(0).status());
            assertEquals(TaskStatus.COMPLETED, result.tasks().get(1).status());
            assertEquals(TaskStatus.BLOCKED, result.tasks().get(2).status());
        }
    }

    @Test void runnerMergesSingleDocumentSourceWithABRereadWithoutScopeCollision() throws Exception {
        var tools = new ResearchAgentToolProvider(new FakeRepo(), new FakeSource()).tools();
        DecisionProvider provider = context -> {
            String task = context.task().taskId();
            if (task.equals("bookB")) return completion("completed", "{}");
            if (context.observations().isEmpty()) return call("research.read_source",
                    "{\"documentId\":11,\"chunkId\":101,\"expectedContentHash\":\"h101\"}");
            EvidenceRef current = context.observations().get(0).result().evidence().get(0);
            if (task.equals("bookA")) return completionWithEvidence("completed", "{}", current.sourceId());
            assertEquals("compare", task);
            EvidenceRef inherited = context.inputs().get(0).evidence().get(0);
            assertEquals(inherited.object(), current.object());
            assertNotEquals(inherited.scope(), current.scope());
            assertNotEquals(inherited.sourceId(), current.sourceId());
            return completionWithEvidence("completed", "{}", inherited.sourceId(), current.sourceId());
        };
        AgentTask a = task("bookA", A, Set.of(), Set.of("research.read_source"));
        AgentTask b = task("bookB", B, Set.of(), Set.of());
        AgentTask compare = task("compare", AB, Set.of("bookA", "bookB"), Set.of("research.read_source"));
        try (var runtime = new AssistantRuntime(Map.of(Role.GRAPH_RESEARCH, provider), tools, 2)) {
            var result = runtime.start(new RunPlan("reread", AB, List.of(a, b, compare), limits()))
                    .completion().toCompletableFuture().get(6, TimeUnit.SECONDS);
            assertEquals(TaskStatus.COMPLETED, result.tasks().get(2).status());
            assertEquals(2, result.tasks().get(2).artifact().evidence().size());
            assertFalse(result.tasks().get(2).artifact().limitations().contains("EVIDENCE_ID_CONFLICT"));
        }
    }

    @Test void runnerMergesSharedNodeAndEdgeFromIndependentDocumentScopes() throws Exception {
        var tools = new ResearchAgentToolProvider(new FakeRepo(), new FakeSource()).tools();
        DecisionProvider provider = context -> {
            if (context.task().taskId().equals("compare")) {
                assertEquals(2, context.inputs().size());
                List<String> refs = context.inputs().stream().flatMap(input -> input.evidence().stream())
                        .map(EvidenceRef::sourceId).toList();
                assertEquals(4, refs.size());
                assertEquals(4, Set.copyOf(refs).size());
                return completionWithEvidence("completed", "{}", refs.toArray(String[]::new));
            }
            if (context.observations().isEmpty()) return call("research.get_node", "{\"nodeId\":1}");
            if (context.observations().size() == 1)
                return call("research.get_neighbors", "{\"nodeId\":1,\"depth\":1}");
            List<String> refs = context.observations().stream()
                    .flatMap(observation -> observation.result().evidence().stream())
                    .map(EvidenceRef::sourceId).toList();
            return completionWithEvidence("completed", "{}", refs.toArray(String[]::new));
        };
        AgentTask a = task("bookA", A, Set.of(), Set.of("research.get_node", "research.get_neighbors"));
        AgentTask b = task("bookB", B, Set.of(), Set.of("research.get_node", "research.get_neighbors"));
        AgentTask compare = task("compare", AB, Set.of("bookA", "bookB"), Set.of());
        try (var runtime = new AssistantRuntime(Map.of(Role.GRAPH_RESEARCH, provider), tools, 2)) {
            var result = runtime.start(new RunPlan("shared graph", AB, List.of(a, b, compare), limits()))
                    .completion().toCompletableFuture().get(6, TimeUnit.SECONDS);
            assertEquals(TaskStatus.COMPLETED, result.tasks().get(2).status());
            assertEquals(4, result.tasks().get(2).artifact().evidence().size());
        }
    }

    @Test void runnerStillRejectsSameIdWithChangedContentAndRetainsFirstEvidence() throws Exception {
        AtomicInteger reads = new AtomicInteger();
        FakeRepo changing = new FakeRepo() {
            @Override public Optional<Node> node(Scope scope, long nodeId) {
                Node original = super.node(scope, nodeId).orElseThrow();
                return Optional.of(new Node(original.id(), reads.incrementAndGet() == 1 ? "first" : "changed",
                        original.nameEn(), original.type(), original.definition(), original.status(),
                        original.updatedAt(), original.origin(), original.reviewStatus(), original.libraryIds(),
                        original.aliases(), null, true));
            }
        };
        var tools = new ResearchAgentToolProvider(changing, new FakeSource()).tools();
        DecisionProvider provider = context -> {
            if (context.observations().size() < 2) return call("research.get_node", "{\"nodeId\":1}");
            assertEquals("EVIDENCE_ID_CONFLICT", context.observations().get(1).result().errorCode());
            String firstId = context.observations().get(0).result().evidence().get(0).sourceId();
            return completionWithEvidence("completed", "{}", firstId);
        };
        AgentTask single = task("bookA", A, Set.of(), Set.of("research.get_node"));
        try (var runtime = new AssistantRuntime(Map.of(Role.GRAPH_RESEARCH, provider), tools, 1)) {
            var result = runtime.start(new RunPlan("conflict", A, List.of(single), limits()))
                    .completion().toCompletableFuture().get(6, TimeUnit.SECONDS);
            assertEquals(TaskStatus.PARTIAL, result.tasks().get(0).status());
            assertEquals(1, result.tasks().get(0).artifact().evidence().size());
            assertTrue(result.tasks().get(0).artifact().limitations().contains("EVIDENCE_ID_CONFLICT"));
        }
    }

    @Test void comparisonRejectsMissingDuplicateAndNonDirectInputs() {
        var a = study("bookA", A, 11, 101, "h101", Outcome.COMPLETED);
        var b = study("bookB", B, 12, 102, "h102", Outcome.COMPLETED);
        assertThrows(IllegalArgumentException.class, () -> ResearchArtifacts.compare(consumer(), List.of(a),
                11, 12, new FakeRepo(), new FakeSource()));
        assertThrows(IllegalArgumentException.class, () -> ResearchArtifacts.compare(consumer(), List.of(a, a),
                11, 12, new FakeRepo(), new FakeSource()));
        var forged = new Artifact("forged", "other", B, List.of(B), Outcome.COMPLETED, "study", b.dataJson(),
                b.evidence(), List.of(), List.of(), Instant.now());
        assertThrows(IllegalArgumentException.class, () -> ResearchArtifacts.compare(consumer(), List.of(a, forged),
                11, 12, new FakeRepo(), new FakeSource()));
        assertThrows(IllegalArgumentException.class, () -> ResearchArtifacts.compare(consumer(), List.of(a, aForB()),
                11, 12, new FakeRepo(), new FakeSource()));
    }

    @Test void partialAndStaleSourcePreserveGap() {
        var source = new FakeSource();
        source.hashB = "new-hash";
        var result = ResearchArtifacts.compare(consumer(), List.of(study("bookA", A, 11, 101, "h101", Outcome.PARTIAL),
                study("bookB", B, 12, 102, "h102", Outcome.COMPLETED)), 11, 12, new FakeRepo(), source);
        assertEquals(Outcome.PARTIAL, result.outcome());
        assertTrue(result.limitations().contains("DOCUMENT_11_INCOMPLETE"));
        assertTrue(result.limitations().contains("DOCUMENT_12_KEY_SOURCE_UNVERIFIED"));
        assertEquals(1, result.evidence().size());
    }

    @Test void qualityHandoffRejectsFabricatedOrCrossDocumentSourceAndRetainsOverLimitClaims() {
        var artifact = study("bookA", A, 11, 101, "h101", Outcome.COMPLETED);
        var handoff = ResearchArtifacts.qualityHandoff(artifact, 11, Set.of("bookA"));
        assertEquals("claim-11", handoff.eligibleBatches().get(0).claims().get(0).claimId());
        assertEquals(101, handoff.eligibleBatches().get(0).claims().get(0).sources().get(0).chunkId());
        assertEquals("h101", handoff.eligibleBatches().get(0).claims().get(0).sources().get(0).expectedHash());
        assertEquals(1, handoff.eligibleBatches().size());
        assertThrows(IllegalArgumentException.class,
                () -> ResearchArtifacts.qualityHandoff(artifact, 11, Set.of("other")));
        var forged = new Artifact("bad", "bookA", A, List.of(A), Outcome.COMPLETED, "study",
                artifact.dataJson().replace("research:chunk:11:101:h101", "invented"), artifact.evidence(),
                List.of(), List.of(), Instant.now());
        assertThrows(IllegalArgumentException.class,
                () -> ResearchArtifacts.qualityHandoff(forged, 11, Set.of("bookA")));
        var wrongEvidence = new Artifact("cross", "bookA", A, List.of(A), Outcome.COMPLETED, "study",
                artifact.dataJson(), List.of(ResearchEvidence.source(meta(12, 101, "h101"), "body", B)),
                List.of(), List.of(), Instant.now());
        assertThrows(IllegalArgumentException.class,
                () -> ResearchArtifacts.qualityHandoff(wrongEvidence, 11, Set.of("bookA")));
    }

    @Test void qualityHandoffCreatesTenByTwoBatchesAndDefersOversizedClaim() {
        List<EvidenceRef> refs = IntStream.rangeClosed(101, 103)
                .mapToObj(chunk -> ResearchEvidence.source(meta(11, chunk, "h" + chunk), "body", A)).toList();
        String sourceIds = refs.stream().map(ref -> "\"" + ref.sourceId() + "\"")
                .reduce((left, right) -> left + "," + right).orElseThrow();
        StringBuilder claims = new StringBuilder("{\"claimId\":\"too-many-sources\",\"text\":\"待核验\","
                + "\"kind\":\"source_fact\",\"sourceIds\":[" + sourceIds + "]}");
        for (int i = 1; i <= 11; i++) claims.append(",{\"claimId\":\"c").append(i)
                .append("\",\"text\":\"待核验\",\"kind\":\"source_fact\",\"sourceIds\":[\"")
                .append(refs.get(0).sourceId()).append("\"]}");
        String json = "{\"kind\":\"document_research\",\"documentId\":11,\"claims\":[" + claims
                + "],\"coverage\":{\"completeCoverage\":false,\"truncated\":true}}";
        var artifact = new Artifact("many", "bookA", A, List.of(A), Outcome.PARTIAL, "research", json,
                refs, List.of(), List.of(), Instant.now());
        var handoff = ResearchArtifacts.qualityHandoff(artifact, 11, Set.of("bookA"));
        assertEquals(List.of("too-many-sources"), handoff.deferredClaimIds());
        assertEquals(2, handoff.eligibleBatches().size());
        assertEquals(10, handoff.eligibleBatches().get(0).claims().size());
        assertEquals(1, handoff.eligibleBatches().get(1).claims().size());
        assertTrue(handoff.eligibleBatches().stream().flatMap(batch -> batch.claims().stream())
                .allMatch(claim -> claim.sources().size() <= 2));
    }

    private static AgentTask consumer() {
        return new AgentTask("compare", Role.GRAPH_RESEARCH, "compare", AB, Set.of("bookA", "bookB"), Set.of(),
                "{}", new Limits(2, 2, Duration.ofMinutes(1)), new CompletionPolicy(0, false, true));
    }
    private static RunPlan plan() {
        Limits limits = new Limits(8, 0, Duration.ofSeconds(30));
        AgentTask a = new AgentTask("bookA", Role.GRAPH_RESEARCH, "study A", A, Set.of(), Set.of(), "{}",
                limits, new CompletionPolicy(0, false, false));
        AgentTask b = new AgentTask("bookB", Role.GRAPH_RESEARCH, "study B", B, Set.of(), Set.of(), "{}",
                limits, new CompletionPolicy(0, false, false));
        return new RunPlan("compare A B", AB, List.of(a, b, consumer()), limits);
    }
    private static String completion(String outcome, String data) {
        return "{\"type\":\"complete\",\"outcome\":\"" + outcome
                + "\",\"summary\":\"fixture\",\"data\":" + data + "}";
    }
    private static String completionWithEvidence(String outcome, String data, String... ids) {
        return "{\"type\":\"complete\",\"outcome\":\"" + outcome
                + "\",\"summary\":\"fixture\",\"data\":" + data + ",\"evidenceIds\":["
                + java.util.Arrays.stream(ids).map(id -> "\"" + id + "\"")
                    .collect(java.util.stream.Collectors.joining(",")) + "]}";
    }
    private static String call(String name, String arguments) {
        return "{\"type\":\"call_tool\",\"name\":\"" + name + "\",\"arguments\":" + arguments + "}";
    }
    private static Limits limits() { return new Limits(16, 12, Duration.ofSeconds(30)); }
    private static AgentTask task(String id, Scope scope, Set<String> dependencies, Set<String> tools) {
        return new AgentTask(id, Role.GRAPH_RESEARCH, "fixture", scope, dependencies, tools, "{}", limits(),
                new CompletionPolicy(0, false, false));
    }
    private static Artifact aForB() {
        Artifact b = study("bookA", B, 12, 102, "h102", Outcome.COMPLETED);
        return b;
    }
    private static Artifact study(String producer, Scope scope, long doc, long chunk, String hash, Outcome outcome) {
        var ref = ResearchEvidence.source(meta(doc, chunk, hash), "TCP 来源正文", scope);
        String json = "{\"kind\":\"document_research\",\"documentId\":" + doc
                + ",\"claims\":[{\"claimId\":\"claim-" + doc
                + "\",\"text\":\"待判断事实\",\"kind\":\"source_fact\",\"sourceIds\":[\""
                + ref.sourceId() + "\"]}],\"coverage\":{\"completeCoverage\":true,\"truncated\":false}}";
        return new Artifact("artifact-" + producer + "-" + doc, producer, scope, List.of(scope), outcome,
                "research", json, List.of(ref), List.of(), List.of(), Instant.now());
    }
    private static ResearchReadRepository.SourceMeta meta(long doc, long chunk, String hash) {
        return new ResearchReadRepository.SourceMeta(doc, chunk, chunk + 1000, 1, "page", "第1页", hash,
                "COMPLETED", "active", "READY");
    }
    private static final class FakeSource implements SourceLocator {
        int calls;
        String hashB = "h102";
        @Override public Optional<Chunk> chunk(Scope scope, long documentId, long chunkId, int maxChars) {
            calls++;
            return Optional.of(new Chunk(chunkId, chunkId + 1000, 1, documentId == 11 ? "h101" : hashB,
                    0, 8, "TCP 来源正文", false));
        }
        @Override public Optional<Unit> unit(Scope scope, long documentId, int unitIndex, int maxChunks, int maxCharsPerChunk) {
            return Optional.empty();
        }
    }
    private static class FakeRepo implements ResearchReadRepository {
        @Override public Optional<SourceMeta> sourceMeta(Scope scope, long documentId, long chunkId) {
            return Optional.of(meta(documentId, chunkId, documentId == 11 ? "h101" : "h102"));
        }
        @Override public Window<Node> searchNodes(Scope scope, String query, int limit) { throw new AssertionError(); }
        @Override public Optional<Node> node(Scope scope, long nodeId) {
            return nodeId == 1 || nodeId == 2 ? Optional.of(new Node(nodeId, "shared-node-" + nodeId,
                    null, "concept", "definition", "active", "2026-09-24T00:00:00Z", "import", "APPROVED",
                    List.of(1L), List.of(), null, true)) : Optional.empty();
        }
        @Override public Window<Edge> incidentEdges(Scope scope, long nodeId, int limit) {
            return new Window<>(nodeId == 1 || nodeId == 2 ? List.of(new Edge(21, 1, 2, "depends",
                    "active", "2026-09-24T00:00:00Z", "import", "APPROVED", true)) : List.of(), false);
        }
        @Override public Window<Edge> outgoingEdges(Scope scope, long nodeId, int limit) { throw new AssertionError(); }
        @Override public Window<Long> matchingDocuments(Scope scope, List<String> terms, int limit) { throw new AssertionError(); }
        @Override public Window<Candidate> candidates(Scope scope, long documentId, List<String> terms, int limit) { throw new AssertionError(); }
        @Override public boolean libraryExistsInScope(Scope scope, long libraryId) { throw new AssertionError(); }
    }
}
