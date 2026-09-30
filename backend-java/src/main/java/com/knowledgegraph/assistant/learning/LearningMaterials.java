package com.knowledgegraph.assistant.learning;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowledgegraph.assistant.contract.AssistantContracts.*;
import com.knowledgegraph.assistant.research.ResearchArtifacts;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Validates host-held direct inputs. No model data field can establish quality or source authority. */
final class LearningMaterials {
    record Pointer(long documentId, long chunkId, String hash, EvidenceRef original) {}
    record Bundle(List<Map<String, Object>> inputs, List<Map<String, Object>> qualityChecks,
                  List<Map<String, Object>> failedObservations, Map<String, Pointer> pointers,
                  Map<String, EvidenceRef> observedEvidence, List<String> limitations,
                  int independentSources) {}
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Set<String> LEARNING_TOOLS = Set.of("learning.read_current_path", "learning.read_graph_scope",
            "learning.read_evidence", "learning.read_learning_inputs", "learning.read_assessment_summary",
            "learning.draft_path", "learning.draft_explanation", "learning.draft_practice");
    private LearningMaterials() {}

    static Bundle inspect(LearningContextReader.Snapshot snapshot, Scope scope) {
        if (snapshot.artifacts().size() > 8 || snapshot.qualityObservations().size() > 8
                || snapshot.toolObservations().size() > 32) throw new IllegalArgumentException("MATERIAL_LIMIT");
        Map<String, Pointer> pointers = new LinkedHashMap<>();
        Map<String, EvidenceRef> observed = new LinkedHashMap<>();
        Map<String, Artifact> artifacts = new LinkedHashMap<>();
        Map<String, Map<String, List<String>>> claimsByArtifact = new HashMap<>();
        List<Map<String, Object>> inputs = new ArrayList<>();
        List<Map<String, Object>> qualityRows = new ArrayList<>();
        List<Map<String, Object>> failures = new ArrayList<>();
        Set<String> limitations = new LinkedHashSet<>();
        for (Artifact artifact : snapshot.artifacts()) {
            if (!snapshot.directDependencyTaskIds().contains(artifact.producerTaskId())
                    || snapshot.producerRoles().get(artifact.producerTaskId()) != Role.GRAPH_RESEARCH
                    || !artifact.authorizedScope().isWithin(scope)
                    || artifacts.putIfAbsent(artifact.artifactId(), artifact) != null)
                throw new IllegalArgumentException("NON_DIRECT_MATERIAL");
            if (artifact.outcome() != Outcome.COMPLETED && artifact.outcome() != Outcome.PARTIAL)
                throw new IllegalArgumentException("MATERIAL_NOT_AVAILABLE");
            JsonNode data = parse(artifact.dataJson());
            String kind = data.path("kind").asText("");
            Map<String, List<String>> claims = new LinkedHashMap<>();
            if ("document_research".equals(kind)) {
                if (artifact.authorizedScope().documentIds().size() != 1)
                    throw new IllegalArgumentException("STUDY_SCOPE_MISMATCH");
                long documentId = artifact.authorizedScope().documentIds().iterator().next();
                ResearchArtifacts.Study study = ResearchArtifacts.study(artifact, documentId);
                for (ResearchArtifacts.Claim claim : study.claims()) claims.put(claim.claimId(), claim.sourceIds());
                if (study.truncated() || !study.reportedCompleteCoverage()) limitations.add("RESEARCH_COVERAGE_INCOMPLETE");
            } else if ("document_comparison".equals(kind)) {
                JsonNode positions = data.path("positions");
                if (!positions.isArray() || positions.size() != 2
                        || !"not_evaluated".equals(data.path("semanticSupport").asText())
                        || data.path("winner").isValueNode() && !data.path("winner").isNull())
                    throw new IllegalArgumentException("INVALID_COMPARISON");
                Set<Long> documents = new HashSet<>();
                for (JsonNode position : positions) {
                    long documentId = positive(position.path("documentId"));
                    if (!artifact.authorizedScope().documentIds().contains(documentId)
                            || !documents.add(documentId)) throw new IllegalArgumentException("COMPARISON_SCOPE_MISMATCH");
                    JsonNode rows = position.path("claims");
                    if (!rows.isArray() || rows.size() > 20) throw new IllegalArgumentException("INVALID_COMPARISON");
                    for (JsonNode row : rows) {
                        String claimId = text(row.path("claimId"), 128);
                        strings(row.path("sourceIds"), 20);
                        if (claims.putIfAbsent(documentId + ":" + claimId,
                                strings(row.path("sourceIds"), 20)) != null)
                            throw new IllegalArgumentException("DUPLICATE_COMPARISON_CLAIM");
                    }
                }
                if (!documents.equals(artifact.authorizedScope().documentIds()))
                    throw new IllegalArgumentException("COMPARISON_SCOPE_MISMATCH");
                List<String> checked = strings(data.path("checkedSourceIds"), 8);
                Set<String> evidenceIds = artifact.evidence().stream().filter(ref -> ref.kind() == EvidenceKind.SOURCE)
                        .map(EvidenceRef::sourceId).collect(java.util.stream.Collectors.toSet());
                if (!evidenceIds.equals(Set.copyOf(checked)))
                    throw new IllegalArgumentException("COMPARISON_CHECKED_SOURCE_MISMATCH");
                limitations.add("SEMANTIC_COMPARISON_NOT_EVALUATED");
                limitations.add("FULL_CORPUS_COVERAGE_NOT_VERIFIED");
            } else throw new IllegalArgumentException("UNSUPPORTED_MATERIAL_KIND");
            for (EvidenceRef ref : artifact.evidence()) {
                if (!ref.scope().isWithin(artifact.authorizedScope())) throw new IllegalArgumentException("SOURCE_SCOPE_MISMATCH");
                EvidenceRef old = observed.putIfAbsent(ref.sourceId(), ref);
                if (old != null && !old.equals(ref)) throw new IllegalArgumentException("EVIDENCE_ID_CONFLICT");
                if (ref.kind() == EvidenceKind.SOURCE) {
                    Pointer pointer = pointer(ref);
                    if (!artifact.authorizedScope().documentIds().contains(pointer.documentId()))
                        throw new IllegalArgumentException("SOURCE_SCOPE_MISMATCH");
                    pointers.put(ref.sourceId(), pointer);
                }
            }
            if ("document_comparison".equals(kind)) {
                for (JsonNode position : data.path("positions")) {
                    long documentId = position.path("documentId").longValue();
                    for (JsonNode row : position.path("claims")) {
                        for (String sourceId : strings(row.path("sourceIds"), 20)) {
                            Pointer pointer = pointers.get(sourceId);
                            if (pointer != null && pointer.documentId() != documentId)
                                throw new IllegalArgumentException("COMPARISON_SOURCE_DOCUMENT_MISMATCH");
                        }
                    }
                }
            }
            for (List<String> sourceIds : claims.values()) {
                for (String sourceId : sourceIds) if (!observed.containsKey(sourceId)) {
                    if ("document_research".equals(kind)) throw new IllegalArgumentException("UNKNOWN_CLAIM_SOURCE");
                    limitations.add("COMPARISON_CLAIM_SOURCE_UNCHECKED");
                }
            }
            claimsByArtifact.put(artifact.artifactId(), claims);
            if (artifact.outcome() == Outcome.PARTIAL) limitations.add("PARTIAL_RESEARCH_DEPENDENCY");
            limitations.addAll(artifact.limitations());
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("artifactId", artifact.artifactId());
            item.put("producerTaskId", artifact.producerTaskId());
            item.put("kind", kind);
            item.put("outcome", artifact.outcome().wireValue());
            item.put("claims", data.path("claims").isArray() ? data.path("claims") : data.path("positions"));
            item.put("claimCount", claims.size());
            item.put("sourceIds", artifact.evidence().stream().filter(ref -> ref.kind() == EvidenceKind.SOURCE)
                    .map(EvidenceRef::sourceId).toList());
            item.put("limitations", artifact.limitations());
            inputs.add(item);
        }
        for (ToolObservation observation : snapshot.toolObservations()) {
            if (!LEARNING_TOOLS.contains(observation.toolName()) || observation.result() == null
                    || observation.toolCallId() == null || observation.toolCallId().isBlank()
                    || observation.observedAt() == null
                    || !observation.result().effectiveScope().isWithin(scope))
                throw new IllegalArgumentException("UNTRUSTED_TOOL_OBSERVATION");
            if (observation.result().status() == ToolStatus.ERROR) {
                // The host authenticates this current-task observation. A failed read supplies no evidence,
                // but it must not poison later, independent reads or informal drafts.
                failures.add(Map.of("toolCallId", observation.toolCallId(), "toolName", observation.toolName(),
                        "errorCode", observation.result().errorCode(),
                        "observedAt", observation.observedAt().toString()));
                limitations.add("PREVIOUS_LEARNING_TOOL_ERROR");
                continue;
            }
            if (observation.result().status() == ToolStatus.PARTIAL)
                limitations.add("PREVIOUS_LEARNING_TOOL_PARTIAL");
            limitations.addAll(observation.result().limitations());
            for (EvidenceRef ref : observation.result().evidence()) {
                if (!ref.scope().isWithin(scope)) throw new IllegalArgumentException("SOURCE_SCOPE_MISMATCH");
                EvidenceRef old = observed.putIfAbsent(ref.sourceId(), ref);
                if (old != null && !old.equals(ref)) throw new IllegalArgumentException("EVIDENCE_ID_CONFLICT");
                if (ref.kind() == EvidenceKind.SOURCE) pointers.put(ref.sourceId(), pointer(ref));
            }
        }
        Map<String, Set<String>> checkedClaimsByArtifact = new LinkedHashMap<>();
        for (LearningContextReader.QualityObservation quality : snapshot.qualityObservations()) {
            if (!snapshot.directDependencyTaskIds().contains(quality.qualityTaskId())
                    || snapshot.producerRoles().get(quality.qualityTaskId()) != Role.QUALITY_GOVERNANCE)
                throw new IllegalArgumentException("NON_DIRECT_QUALITY");
            Artifact target = artifacts.get(quality.artifactId());
            if (target == null || !target.producerTaskId().equals(quality.producerTaskId()))
                throw new IllegalArgumentException("QUALITY_TARGET_MISMATCH");
            Map<String, List<String>> claims = claimsByArtifact.get(target.artifactId());
            Set<String> checkedClaims = checkedClaimsByArtifact.computeIfAbsent(target.artifactId(), ignored -> new LinkedHashSet<>());
            int current = 0;
            for (LearningContextReader.ClaimCheck check : quality.checks()) {
                List<String> sources = claims.get(check.claimId());
                if (sources == null || !"not_evaluated".equals(check.semanticSupport()))
                    throw new IllegalArgumentException("QUALITY_CLAIM_MISMATCH");
                if (!checkedClaims.add(check.claimId()))
                    throw new IllegalArgumentException("DUPLICATE_QUALITY_CLAIM");
                if (check.referenceStatus() == null || check.referenceStatus().isBlank()
                        || check.referenceStatus().length() > 40)
                    throw new IllegalArgumentException("QUALITY_REFERENCE_STATUS_INVALID");
                Set<String> checkedSources = new HashSet<>();
                for (LearningContextReader.SourceCheck source : check.sources()) {
                    Pointer pointer = pointers.get(source.originalSourceId());
                    if (pointer == null || !sources.contains(source.originalSourceId())
                            || !checkedSources.add(source.originalSourceId())
                            || pointer.chunkId() != source.chunkId()
                            || !pointer.hash().equals(source.expectedHash()))
                        throw new IllegalArgumentException("QUALITY_SOURCE_MISMATCH");
                    if ("CURRENT".equals(source.status())) {
                        if (!source.expectedHash().equals(source.actualHash()))
                            throw new IllegalArgumentException("QUALITY_SOURCE_MISMATCH");
                        current++;
                    } else limitations.add("QUALITY_SOURCE_NOT_CURRENT");
                }
            }
            if (quality.requestedClaims() < 0 || quality.currentClaims() < 0
                    || quality.currentClaims() > quality.requestedClaims()
                    || quality.currentClaims() > quality.checks().size()
                    || quality.requestedClaims() < quality.checks().size()
                    || quality.completeCoverage() && (quality.checks().size() != quality.requestedClaims()
                    || quality.currentClaims() != quality.requestedClaims()))
                throw new IllegalArgumentException("QUALITY_COVERAGE_MISMATCH");
            if (!quality.completeCoverage()) limitations.add("QUALITY_REQUEST_COVERAGE_INCOMPLETE");
            if (current == 0) limitations.add("QUALITY_CURRENT_SOURCE_UNAVAILABLE");
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("qualityTaskId", quality.qualityTaskId());
            item.put("artifactId", quality.artifactId());
            item.put("producerTaskId", quality.producerTaskId());
            item.put("checks", quality.checks());
            item.put("requestedClaims", quality.requestedClaims());
            item.put("currentClaims", quality.currentClaims());
            item.put("requestCompleteCoverage", quality.completeCoverage());
            item.put("completeCoverage", quality.completeCoverage()); // 02's request-local meaning
            item.put("observedAt", quality.observedAt() == null ? null : quality.observedAt().toString());
            item.put("semanticSupport", "not_evaluated");
            qualityRows.add(item);
        }
        for (Artifact artifact : artifacts.values()) {
            Set<String> covered = checkedClaimsByArtifact.getOrDefault(artifact.artifactId(), Set.of());
            List<String> missing = claimsByArtifact.get(artifact.artifactId()).keySet().stream()
                    .filter(claimId -> !covered.contains(claimId)).sorted().toList();
            if (!missing.isEmpty()) limitations.add("QUALITY_ARTIFACT_CLAIMS_UNCOVERED");
            for (Map<String, Object> row : qualityRows) if (artifact.artifactId().equals(row.get("artifactId"))) {
                row.put("artifactClaimCoverage", missing.isEmpty());
                row.put("coveredClaimIds", covered.stream().sorted().toList());
                row.put("uncoveredClaimIds", missing);
            }
        }
        if (snapshot.qualityObservations().isEmpty()) limitations.add("QUALITY_OBSERVATION_UNAVAILABLE");
        Set<String> physical = new HashSet<>();
        pointers.values().forEach(pointer -> physical.add(pointer.documentId() + ":" + pointer.chunkId() + ":" + pointer.hash()));
        return new Bundle(List.copyOf(inputs), List.copyOf(qualityRows), List.copyOf(failures),
                Map.copyOf(pointers), Map.copyOf(observed),
                List.copyOf(limitations), physical.size());
    }

    static Pointer pointer(EvidenceRef ref) {
        if (ref.kind() != EvidenceKind.SOURCE || !"document_chunk".equals(ref.object().kind())
                || ref.object().version() == null || ref.locator() == null)
            throw new IllegalArgumentException("INVALID_SOURCE_POINTER");
        long document = 0;
        for (String part : ref.locator().split(";")) {
            if (part.startsWith("document=")) {
                try { document = Long.parseLong(part.substring("document=".length())); }
                catch (NumberFormatException ignored) { throw new IllegalArgumentException("INVALID_SOURCE_POINTER"); }
            }
        }
        long chunk;
        try { chunk = Long.parseLong(ref.object().id()); }
        catch (NumberFormatException ignored) { throw new IllegalArgumentException("INVALID_SOURCE_POINTER"); }
        if (document <= 0 || chunk <= 0) throw new IllegalArgumentException("INVALID_SOURCE_POINTER");
        return new Pointer(document, chunk, ref.object().version(), ref);
    }
    private static JsonNode parse(String raw) {
        try {
            JsonNode value = JSON.readTree(raw);
            if (!value.isObject()) throw new IllegalArgumentException("INVALID_MATERIAL");
            return value;
        } catch (Exception ignored) { throw new IllegalArgumentException("INVALID_MATERIAL"); }
    }
    private static long positive(JsonNode value) {
        if (!value.isIntegralNumber() || !value.canConvertToLong() || value.longValue() <= 0)
            throw new IllegalArgumentException("INVALID_MATERIAL_ID");
        return value.longValue();
    }
    private static String text(JsonNode value, int max) {
        if (!value.isTextual() || value.textValue().isBlank() || value.textValue().length() > max)
            throw new IllegalArgumentException("INVALID_MATERIAL_TEXT");
        return value.textValue();
    }
    private static List<String> strings(JsonNode value, int max) {
        if (!value.isArray() || value.size() > max) throw new IllegalArgumentException("INVALID_MATERIAL_LIST");
        List<String> result = new ArrayList<>();
        for (JsonNode item : value) result.add(text(item, 256));
        if (new HashSet<>(result).size() != result.size()) throw new IllegalArgumentException("DUPLICATE_MATERIAL_SOURCE");
        return result;
    }
}
