package com.knowledgegraph.assistant.research;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowledgegraph.assistant.contract.AssistantContracts.*;
import com.knowledgegraph.assistant.quality.QualityArtifactReader;
import com.knowledgegraph.assistant.source.SourceLocator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Validates host-held direct artifacts and derives bounded handoff data; no global artifact lookup. */
public final class ResearchArtifacts {
    private static final ObjectMapper JSON = new ObjectMapper();
    private ResearchArtifacts() {}

    public record Claim(String claimId, String text, String kind, List<String> sourceIds) {
        public Claim { sourceIds = List.copyOf(sourceIds); }
    }
    public record Study(long documentId, List<Claim> claims, boolean reportedCompleteCoverage,
                        boolean truncated, List<String> limitations) {
        public Study { claims = List.copyOf(claims); limitations = List.copyOf(limitations); }
    }
    public record Comparison(Map<String, Object> data, Outcome outcome,
                             List<EvidenceRef> evidence, List<String> limitations) {
        public Comparison { data = Collections.unmodifiableMap(new LinkedHashMap<>(data));
            evidence = List.copyOf(evidence); limitations = List.copyOf(limitations); }
    }
    public record QualityHandoff(List<QualityArtifactReader.DirectArtifact> eligibleBatches,
                                 List<String> deferredClaimIds, List<String> needsSourceBatching) {
        public QualityHandoff {
            eligibleBatches = List.copyOf(eligibleBatches);
            deferredClaimIds = List.copyOf(deferredClaimIds);
            needsSourceBatching = List.copyOf(needsSourceBatching);
        }
    }

    /** The JSON is a model/host proposal. Every pointer is checked against the original Artifact.evidence. */
    public static Study study(Artifact artifact, long expectedDocumentId) {
        if (artifact.outcome() != Outcome.COMPLETED && artifact.outcome() != Outcome.PARTIAL)
            throw new IllegalArgumentException("STUDY_NOT_AVAILABLE");
        if (!artifact.authorizedScope().documentIds().equals(Set.of(expectedDocumentId))
                || !artifact.authorizedScope().nodeIds().isEmpty())
            throw new IllegalArgumentException("STUDY_SCOPE_MISMATCH");
        JsonNode root = json(artifact.dataJson());
        if (!"document_research".equals(text(root, "kind", 40))
                || number(root, "documentId") != expectedDocumentId) throw new IllegalArgumentException("STUDY_ID_MISMATCH");
        JsonNode claimRows = root.get("claims");
        if (claimRows == null || !claimRows.isArray() || claimRows.size() > 20)
            throw new IllegalArgumentException("INVALID_CLAIMS");
        Map<String, EvidenceRef> known = knownEvidence(artifact);
        for (EvidenceRef ref : known.values()) {
            if (!ref.scope().isWithin(artifact.authorizedScope()))
                throw new IllegalArgumentException("EVIDENCE_SCOPE_MISMATCH");
            if (ref.kind() == EvidenceKind.SOURCE) sourcePointer(ref, expectedDocumentId);
        }
        List<Claim> claims = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (JsonNode row : claimRows) {
            String id = text(row, "claimId", 128);
            String content = text(row, "text", 1000);
            String kind = text(row, "kind", 40);
            if (!Set.of("source_fact", "graph_fact", "model_explanation", "conflict", "gap").contains(kind)
                    || !ids.add(id)) throw new IllegalArgumentException("INVALID_CLAIM_KIND_OR_ID");
            JsonNode refs = row.get("sourceIds");
            if (refs == null || !refs.isArray() || refs.size() > 20) throw new IllegalArgumentException("INVALID_CLAIM_SOURCES");
            List<String> sourceIds = new ArrayList<>();
            Set<String> seenSources = new HashSet<>();
            for (JsonNode source : refs) {
                if (!source.isTextual() || !seenSources.add(source.textValue())) throw new IllegalArgumentException("DUPLICATE_SOURCE");
                sourceIds.add(source.textValue());
                EvidenceRef evidence = known.get(source.textValue());
                if (evidence == null) throw new IllegalArgumentException("UNKNOWN_SOURCE_ID");
                if (kind.equals("source_fact")) sourcePointer(evidence, expectedDocumentId);
            }
            if (kind.equals("source_fact") && sourceIds.isEmpty()
                    || kind.equals("model_explanation") && !sourceIds.isEmpty())
                throw new IllegalArgumentException("CLAIM_EVIDENCE_MISMATCH");
            claims.add(new Claim(id, content, kind, sourceIds));
        }
        JsonNode coverage = root.get("coverage");
        if (coverage == null || !coverage.isObject() || !coverage.path("completeCoverage").isBoolean()
                || !coverage.path("truncated").isBoolean()) throw new IllegalArgumentException("INVALID_COVERAGE");
        boolean complete = coverage.get("completeCoverage").booleanValue()
                && artifact.observedScopes().contains(artifact.authorizedScope());
        boolean truncated = coverage.get("truncated").booleanValue();
        List<String> limitations = artifact.limitations();
        return new Study(expectedDocumentId, claims, complete, truncated, limitations);
    }

    /** Called only with the current consumer task's original direct dependency Artifacts. */
    public static Comparison compare(AgentTask consumer, List<Artifact> directInputs,
                                     long documentA, long documentB,
                                     ResearchReadRepository repo, SourceLocator sources) {
        if (documentA <= 0 || documentB <= 0 || documentA == documentB
                || !consumer.scope().documentIds().equals(Set.of(documentA, documentB))
                || !consumer.scope().nodeIds().isEmpty()) throw new IllegalArgumentException("INVALID_COMPARISON_SCOPE");
        if (directInputs.size() != 2 || consumer.dependencyIds().size() != 2)
            throw new IllegalArgumentException("MISSING_DIRECT_DEPENDENCY");
        Map<Long, Study> studies = new LinkedHashMap<>();
        Map<Long, Artifact> artifacts = new LinkedHashMap<>();
        Set<String> producers = new HashSet<>();
        for (Artifact input : directInputs) {
            if (!consumer.dependencyIds().contains(input.producerTaskId()) || !producers.add(input.producerTaskId())
                    || !input.authorizedScope().isWithin(consumer.scope()))
                throw new IllegalArgumentException("NON_DIRECT_OR_DUPLICATE_ARTIFACT");
            long documentId = input.authorizedScope().documentIds().stream().findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("MISSING_DOCUMENT_SCOPE"));
            if (documentId != documentA && documentId != documentB || studies.containsKey(documentId))
                throw new IllegalArgumentException("DUPLICATE_OR_UNEXPECTED_DOCUMENT");
            studies.put(documentId, study(input, documentId)); artifacts.put(documentId, input);
        }
        if (!studies.keySet().equals(Set.of(documentA, documentB))) throw new IllegalArgumentException("MISSING_DOCUMENT");
        List<EvidenceRef> checked = new ArrayList<>();
        Set<String> limitations = new LinkedHashSet<>();
        List<Map<String, Object>> positions = new ArrayList<>();
        for (long documentId : List.of(documentA, documentB)) {
            Study study = studies.get(documentId);
            Artifact input = artifacts.get(documentId);
            if (input.outcome() != Outcome.COMPLETED || !study.reportedCompleteCoverage() || study.truncated())
                limitations.add("DOCUMENT_" + documentId + "_INCOMPLETE");
            EvidenceRef key = keySource(study, input, documentId);
            if (key == null || !reread(consumer.scope(), key, documentId, repo, sources))
                limitations.add("DOCUMENT_" + documentId + "_KEY_SOURCE_UNVERIFIED");
            else checked.add(key);
            positions.add(ResearchAgentToolProvider.map("documentId", documentId,
                    "producerTaskId", input.producerTaskId(), "artifactId", input.artifactId(),
                    "claims", study.claims().stream().map(c -> ResearchAgentToolProvider.map(
                            "claimId", c.claimId(), "text", c.text(), "kind", c.kind(),
                            "sourceIds", c.sourceIds(), "semanticSupport", "not_evaluated")).toList(),
                    "reportedCompleteCoverage", study.reportedCompleteCoverage(), "truncated", study.truncated()));
            limitations.addAll(study.limitations());
        }
        if (checked.size() != 2) limitations.add("COMPARISON_SOURCE_COVERAGE_INCOMPLETE");
        // Artifact coverage and claim semantics are self-reported; neither is independently verified here.
        limitations.add("FULL_CORPUS_COVERAGE_NOT_VERIFIED");
        limitations.add("SEMANTIC_COMPARISON_NOT_EVALUATED");
        Map<String, Object> data = ResearchAgentToolProvider.map("kind", "document_comparison",
                "expectedDocumentIds", List.of(documentA, documentB), "positions", positions,
                "checkedSourceIds", checked.stream().map(EvidenceRef::sourceId).toList(),
                "semanticSupport", "not_evaluated", "winner", null,
                "completeCoverage", limitations.isEmpty(), "limitations", List.copyOf(limitations));
        return new Comparison(data, Outcome.PARTIAL,
                checked, List.copyOf(limitations));
    }

    /** Explicit batches for 02: at most ten claims and two sources per claim; all original claims remain visible. */
    public static QualityHandoff qualityHandoff(Artifact artifact, long expectedDocumentId,
                                                Set<String> consumerDirectDependencyTaskIds) {
        if (!consumerDirectDependencyTaskIds.contains(artifact.producerTaskId()))
            throw new IllegalArgumentException("NON_DIRECT_ARTIFACT");
        Study study = study(artifact, expectedDocumentId);
        Map<String, EvidenceRef> evidence = knownEvidence(artifact);
        List<QualityArtifactReader.Claim> claims = new ArrayList<>();
        List<String> needsSourceBatching = new ArrayList<>();
        for (Claim claim : study.claims()) {
            List<QualityArtifactReader.SourcePointer> pointers = new ArrayList<>();
            for (String id : claim.sourceIds()) {
                EvidenceRef ref = evidence.get(id);
                if (ref.kind() != EvidenceKind.SOURCE) continue;
                pointers.add(sourcePointer(ref, expectedDocumentId));
            }
            if (pointers.size() > 2) needsSourceBatching.add(claim.claimId());
            claims.add(new QualityArtifactReader.Claim(claim.claimId(), pointers));
        }
        List<QualityArtifactReader.DirectArtifact> batches = new ArrayList<>();
        List<QualityArtifactReader.Claim> pending = new ArrayList<>();
        for (QualityArtifactReader.Claim claim : claims) {
            if (claim.sources().size() > 2) continue;
            pending.add(claim);
            if (pending.size() == 10) {
                batches.add(new QualityArtifactReader.DirectArtifact(artifact.artifactId(),
                        artifact.producerTaskId(), pending));
                pending.clear();
            }
        }
        if (!pending.isEmpty()) batches.add(new QualityArtifactReader.DirectArtifact(artifact.artifactId(),
                artifact.producerTaskId(), pending));
        return new QualityHandoff(batches, needsSourceBatching, needsSourceBatching);
    }

    private static EvidenceRef keySource(Study study, Artifact artifact, long docId) {
        Map<String, EvidenceRef> evidence = knownEvidence(artifact);
        for (Claim claim : study.claims()) if (claim.kind().equals("source_fact"))
            for (String id : claim.sourceIds()) {
                EvidenceRef ref = evidence.get(id);
                sourcePointer(ref, docId);
                return ref;
            }
        return null;
    }
    private static boolean reread(Scope scope, EvidenceRef ref, long docId,
                                  ResearchReadRepository repo, SourceLocator sources) {
        try {
            var pointer = sourcePointer(ref, docId);
            var meta = repo.sourceMeta(scope, docId, pointer.chunkId());
            if (meta.isEmpty() || !meta.get().formalAvailable()
                    || !pointer.expectedHash().equals(meta.get().contentHash())) return false;
            var chunk = sources.chunk(scope, docId, pointer.chunkId(), 4000);
            return chunk.isPresent() && !chunk.get().contentTruncated()
                    && pointer.expectedHash().equals(chunk.get().contentHash())
                    && chunk.get().unitId() == meta.get().unitId();
        } catch (RuntimeException ignored) { return false; }
    }
    private static QualityArtifactReader.SourcePointer sourcePointer(EvidenceRef ref, long expectedDocumentId) {
        if (ref == null || ref.kind() != EvidenceKind.SOURCE || ref.object() == null
                || !"document_chunk".equals(ref.object().kind())) throw new IllegalArgumentException("NOT_A_SOURCE_POINTER");
        String prefix = "research:chunk:" + expectedDocumentId + ":";
        String suffix = ":" + ref.object().version() + ":scope:" + ResearchEvidence.scopeFingerprint(ref.scope());
        if (!ref.sourceId().startsWith(prefix) || ref.object().version() == null
                || !ref.sourceId().endsWith(suffix)
                || !ref.scope().documentIds().contains(expectedDocumentId))
            throw new IllegalArgumentException("SOURCE_DOCUMENT_MISMATCH");
        String chunkIdText = ref.sourceId().substring(prefix.length(),
                ref.sourceId().length() - suffix.length());
        if (!chunkIdText.equals(ref.object().id())) throw new IllegalArgumentException("SOURCE_ID_MISMATCH");
        long chunkId;
        try { chunkId = Long.parseLong(chunkIdText); }
        catch (NumberFormatException ignored) { throw new IllegalArgumentException("INVALID_CHUNK_ID"); }
        if (chunkId <= 0) throw new IllegalArgumentException("INVALID_CHUNK_ID");
        return new QualityArtifactReader.SourcePointer(ref.sourceId(), expectedDocumentId,
                chunkId, ref.object().version());
    }
    private static Map<String, EvidenceRef> knownEvidence(Artifact artifact) {
        Map<String, EvidenceRef> result = new HashMap<>();
        for (EvidenceRef ref : artifact.evidence()) {
            if (result.putIfAbsent(ref.sourceId(), ref) != null) throw new IllegalArgumentException("DUPLICATE_ARTIFACT_EVIDENCE");
        }
        return result;
    }
    private static JsonNode json(String value) {
        try {
            JsonNode node = JSON.readTree(value);
            if (!node.isObject()) throw new IllegalArgumentException("INVALID_STUDY_JSON");
            return node;
        } catch (Exception ignored) { throw new IllegalArgumentException("INVALID_STUDY_JSON"); }
    }
    private static String text(JsonNode row, String key, int max) {
        JsonNode value = row == null ? null : row.get(key);
        if (value == null || !value.isTextual() || value.textValue().isBlank() || value.textValue().length() > max)
            throw new IllegalArgumentException("INVALID_STUDY_FIELD");
        return value.textValue();
    }
    private static long number(JsonNode row, String key) {
        JsonNode value = row == null ? null : row.get(key);
        if (value == null || !value.isIntegralNumber() || !value.canConvertToLong())
            throw new IllegalArgumentException("INVALID_STUDY_ID");
        return value.longValue();
    }
}
