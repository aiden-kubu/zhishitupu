package com.knowledgegraph.assistant.learning;

import com.knowledgegraph.assistant.contract.AssistantContracts.*;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class LearningArtifactHandoffTest {
    private static final Scope A = new Scope(ScopeMode.SELECTED, Set.of(1L), Set.of(11L), Set.of());
    private static final Scope B = new Scope(ScopeMode.SELECTED, Set.of(1L), Set.of(12L), Set.of());
    private static final Scope AB = new Scope(ScopeMode.SELECTED, Set.of(1L), Set.of(11L, 12L), Set.of());
    static EvidenceRef source(long document, long chunk, String hash, Scope scope) {
        String canonical = scope.mode() + "|libraries=" + ids(scope.libraryIds())
                + "|documents=" + ids(scope.documentIds()) + "|nodes=" + ids(scope.nodeIds());
        String fingerprint;
        try { fingerprint = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(canonical.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception ex) { throw new IllegalStateException(ex); }
        String id = "research:chunk:" + document + ":" + chunk + ":" + hash + ":scope:" + fingerprint;
        return new EvidenceRef(id, EvidenceKind.SOURCE,
                new ObjectRef("document_chunk", Long.toString(chunk), hash), scope,
                "document=" + document + ";unit=1;chunk=" + chunk, "source");
    }
    private static String ids(Set<Long> values) {
        return values.stream().sorted().map(String::valueOf).collect(java.util.stream.Collectors.joining(","));
    }
    static Artifact study(String artifactId, String producer, long document, Scope scope, EvidenceRef... refs) {
        String ids = java.util.Arrays.stream(refs).map(r -> "\"" + r.sourceId() + "\"")
                .collect(java.util.stream.Collectors.joining(","));
        return new Artifact(artifactId, producer, scope, List.of(scope), Outcome.PARTIAL, "study",
                "{\"kind\":\"document_research\",\"documentId\":" + document
                        + ",\"claims\":[{\"claimId\":\"c1\",\"text\":\"claim\",\"kind\":\"source_fact\",\"sourceIds\":[" + ids
                        + "]}],\"coverage\":{\"completeCoverage\":false,\"truncated\":false}}",
                List.of(refs), List.of(), List.of("RESEARCH_PARTIAL"), Instant.parse("2026-09-25T00:00:00Z"));
    }
    static Artifact twoClaimStudy(EvidenceRef first, EvidenceRef second) {
        return new Artifact("two", "research", A, List.of(A), Outcome.PARTIAL, "two claims",
                "{\"kind\":\"document_research\",\"documentId\":11,\"claims\":["
                        + "{\"claimId\":\"c1\",\"text\":\"first\",\"kind\":\"source_fact\",\"sourceIds\":[\"" + first.sourceId() + "\"]},"
                        + "{\"claimId\":\"c2\",\"text\":\"second\",\"kind\":\"source_fact\",\"sourceIds\":[\"" + second.sourceId()
                        + "\"]}],\"coverage\":{\"completeCoverage\":false,\"truncated\":false}}",
                List.of(first, second), List.of(), List.of("RESEARCH_PARTIAL"), Instant.now());
    }
    static LearningContextReader.QualityObservation quality(String task, String claimId,
                                                                      EvidenceRef source, boolean complete,
                                                                      int requested, int current) {
        var check = new LearningContextReader.ClaimCheck(claimId, "CURRENT", "not_evaluated",
                List.of(new LearningContextReader.SourceCheck(source.sourceId(),
                        Long.parseLong(source.object().id()), source.object().version(),
                        source.object().version(), "CURRENT")));
        return new LearningContextReader.QualityObservation(task, "two", "research",
                List.of(check), requested, current, complete, Instant.now());
    }
    private static LearningContextReader.Snapshot snapshot(Set<String> direct, Map<String, Role> roles,
                                                             List<Artifact> artifacts,
                                                             List<LearningContextReader.QualityObservation> quality) {
        return new LearningContextReader.Snapshot(direct, roles, artifacts, List.of(), quality);
    }

    @Test void directResearchAndCapturedQualityObservationRemainPartialAndSemanticUnknown() {
        var ref = source(11, 101, "h", A);
        var artifact = study("a", "research", 11, A, ref);
        var check = new LearningContextReader.ClaimCheck("c1", "CURRENT", "not_evaluated",
                List.of(new LearningContextReader.SourceCheck(ref.sourceId(), 101, "h", "h", "CURRENT")));
        var quality = new LearningContextReader.QualityObservation("quality", "a", "research",
                List.of(check), 1, 1, true, Instant.parse("2026-09-25T00:00:00Z"));
        var bundle = LearningMaterials.inspect(snapshot(Set.of("research", "quality"),
                Map.of("research", Role.GRAPH_RESEARCH, "quality", Role.QUALITY_GOVERNANCE),
                List.of(artifact), List.of(quality)), A);
        assertEquals(1, bundle.pointers().size());
        assertEquals(1, bundle.qualityChecks().size());
        assertTrue(bundle.limitations().contains("PARTIAL_RESEARCH_DEPENDENCY"));
        assertEquals("not_evaluated", bundle.qualityChecks().get(0).get("semanticSupport"));
        assertEquals(1, bundle.independentSources());
    }

    @Test void samePhysicalSourceDifferentScopeCountsOnceButDifferentDocumentDoesNotCollapse() {
        var a = source(11, 101, "same", A);
        var ab = source(11, 101, "same", AB);
        var b = source(12, 102, "same", B);
        Artifact first = study("a", "ra", 11, A, a);
        Artifact second = study("b", "rb", 12, B, b);
        // A second scoped observation may be a captured tool result from the current learning task.
        ToolResult observed = new ToolResult(ToolStatus.OK, AB, "{}", List.of(ab), List.of(), null);
        var snapshot = new LearningContextReader.Snapshot(Set.of("ra", "rb"),
                Map.of("ra", Role.GRAPH_RESEARCH, "rb", Role.GRAPH_RESEARCH), List.of(first, second),
                List.of(new ToolObservation("call", "learning.read_evidence", Instant.now(), observed)), List.of());
        assertEquals(2, LearningMaterials.inspect(snapshot, AB).independentSources());
    }

    @Test void rejectsNonDirectMaterialAndForgedQualityClaimOrHash() {
        var ref = source(11, 101, "h", A);
        var artifact = study("a", "research", 11, A, ref);
        assertEquals("NON_DIRECT_MATERIAL", assertThrows(IllegalArgumentException.class,
                () -> LearningMaterials.inspect(snapshot(Set.of(), Map.of("research", Role.GRAPH_RESEARCH),
                        List.of(artifact), List.of()), A)).getMessage());
        var unknown = new LearningContextReader.ClaimCheck("invented", "CURRENT", "not_evaluated", List.of());
        var quality = new LearningContextReader.QualityObservation("quality", "a", "research",
                List.of(unknown), 1, 1, true, Instant.now());
        assertEquals("QUALITY_CLAIM_MISMATCH", assertThrows(IllegalArgumentException.class,
                () -> LearningMaterials.inspect(snapshot(Set.of("research", "quality"),
                        Map.of("research", Role.GRAPH_RESEARCH, "quality", Role.QUALITY_GOVERNANCE),
                        List.of(artifact), List.of(quality)), A)).getMessage());
    }

    @Test void comparisonKeepsUncheckedClaimsSeparateFromActuallyCheckedSources() {
        var checked = source(11, 101, "h11", A);
        Artifact comparison = new Artifact("compare-a", "compare", AB, List.of(AB), Outcome.PARTIAL,
                "comparison", "{\"kind\":\"document_comparison\",\"semanticSupport\":\"not_evaluated\","
                + "\"winner\":null,\"checkedSourceIds\":[\"" + checked.sourceId() + "\"],\"positions\":["
                + "{\"documentId\":11,\"claims\":[{\"claimId\":\"a\",\"sourceIds\":[\"" + checked.sourceId() + "\"]}]},"
                + "{\"documentId\":12,\"claims\":[{\"claimId\":\"b\",\"sourceIds\":[\"research:unchecked\"]}]}]}",
                List.of(checked), List.of(), List.of("UNRESOLVED_CONFLICT"), Instant.now());
        var bundle = LearningMaterials.inspect(snapshot(Set.of("compare"),
                Map.of("compare", Role.GRAPH_RESEARCH), List.of(comparison), List.of()), AB);
        assertTrue(bundle.limitations().contains("COMPARISON_CLAIM_SOURCE_UNCHECKED"));
        assertTrue(bundle.limitations().contains("UNRESOLVED_CONFLICT"));
        assertFalse(bundle.pointers().containsKey("research:unchecked"));
    }

    @Test void completeSingleClaimRequestDoesNotClaimWholeArtifactCoverage() {
        var first = source(11, 101, "h1", A);
        var second = source(11, 102, "h2", A);
        var bundle = LearningMaterials.inspect(snapshot(Set.of("research", "quality-1"),
                Map.of("research", Role.GRAPH_RESEARCH, "quality-1", Role.QUALITY_GOVERNANCE),
                List.of(twoClaimStudy(first, second)), List.of(quality("quality-1", "c1", first, true, 1, 1))), A);
        assertEquals(1, bundle.qualityChecks().size());
        assertEquals(true, bundle.qualityChecks().get(0).get("requestCompleteCoverage"));
        assertEquals(false, bundle.qualityChecks().get(0).get("artifactClaimCoverage"));
        assertEquals(List.of("c2"), bundle.qualityChecks().get(0).get("uncoveredClaimIds"));
        assertTrue(bundle.limitations().contains("QUALITY_ARTIFACT_CLAIMS_UNCOVERED"));
        assertEquals(2, bundle.pointers().size());
    }

    @Test void disjointQualityBatchesCoverWholeArtifactWithoutConflatingRequestCoverage() {
        var first = source(11, 101, "h1", A);
        var second = source(11, 102, "h2", A);
        var bundle = LearningMaterials.inspect(snapshot(Set.of("research", "quality-1", "quality-2"),
                Map.of("research", Role.GRAPH_RESEARCH, "quality-1", Role.QUALITY_GOVERNANCE,
                        "quality-2", Role.QUALITY_GOVERNANCE), List.of(twoClaimStudy(first, second)),
                List.of(quality("quality-1", "c1", first, true, 1, 1),
                        quality("quality-2", "c2", second, true, 1, 1))), A);
        assertEquals(2, bundle.qualityChecks().size());
        assertTrue(bundle.qualityChecks().stream().allMatch(row -> Boolean.TRUE.equals(row.get("artifactClaimCoverage"))));
        assertFalse(bundle.limitations().contains("QUALITY_ARTIFACT_CLAIMS_UNCOVERED"));
    }

    @Test void duplicateUnknownAndFalselyCompleteQualityChecksStillReject() {
        var first = source(11, 101, "h1", A);
        var second = source(11, 102, "h2", A);
        var artifact = twoClaimStudy(first, second);
        var roles = Map.of("research", Role.GRAPH_RESEARCH, "quality-1", Role.QUALITY_GOVERNANCE,
                "quality-2", Role.QUALITY_GOVERNANCE);
        assertEquals("DUPLICATE_QUALITY_CLAIM", assertThrows(IllegalArgumentException.class,
                () -> LearningMaterials.inspect(snapshot(roles.keySet(), roles, List.of(artifact),
                        List.of(quality("quality-1", "c1", first, true, 1, 1),
                                quality("quality-2", "c1", first, true, 1, 1))), A)).getMessage());
        assertEquals("QUALITY_CLAIM_MISMATCH", assertThrows(IllegalArgumentException.class,
                () -> LearningMaterials.inspect(snapshot(roles.keySet(), roles, List.of(artifact),
                        List.of(quality("quality-1", "unknown", first, true, 1, 1))), A)).getMessage());
        assertEquals("QUALITY_COVERAGE_MISMATCH", assertThrows(IllegalArgumentException.class,
                () -> LearningMaterials.inspect(snapshot(roles.keySet(), roles, List.of(artifact),
                        List.of(quality("quality-1", "c1", first, true, 2, 1))), A)).getMessage());
    }

    @Test void failedObservationIsRecordedWithoutBecomingEvidenceButForgedObservationStillRejects() {
        var failed = new ToolObservation("failed-call", "learning.read_evidence", Instant.now(),
                ToolResult.error(A, "UNTRUSTED_SOURCE_POINTER"));
        var snapshot = new LearningContextReader.Snapshot(Set.of(), Map.of(), List.of(), List.of(failed), List.of());
        var bundle = LearningMaterials.inspect(snapshot, A);
        assertEquals(1, bundle.failedObservations().size());
        assertTrue(bundle.pointers().isEmpty());
        assertTrue(bundle.limitations().contains("PREVIOUS_LEARNING_TOOL_ERROR"));
        var outside = new ToolObservation("outside", "learning.read_evidence", Instant.now(),
                ToolResult.error(B, "UNTRUSTED_SOURCE_POINTER"));
        assertEquals("UNTRUSTED_TOOL_OBSERVATION", assertThrows(IllegalArgumentException.class,
                () -> LearningMaterials.inspect(new LearningContextReader.Snapshot(Set.of(), Map.of(), List.of(),
                        List.of(outside), List.of()), A)).getMessage());
        var forged = new ToolObservation("forged", "learning.fake_write", Instant.now(),
                ToolResult.error(A, "UNTRUSTED_SOURCE_POINTER"));
        assertEquals("UNTRUSTED_TOOL_OBSERVATION", assertThrows(IllegalArgumentException.class,
                () -> LearningMaterials.inspect(new LearningContextReader.Snapshot(Set.of(), Map.of(), List.of(),
                        List.of(forged), List.of()), A)).getMessage());
    }
}
