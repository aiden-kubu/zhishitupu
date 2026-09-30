package com.knowledgegraph.assistant.assessment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowledgegraph.assistant.contract.AssistantContracts.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Checks host-held identities before considering any learning suggestion or source pointer. */
final class AssessmentMaterials {
    record Pointer(long documentId, long chunkId, String hash, EvidenceRef original) {}
    record Practice(String artifactId, String producerTaskId, String toolCallId, String localId, long nodeId,
                    String prompt, String referenceAnswer, List<String> sourceIds) {}
    record Bundle(Map<String, Pointer> pointers, Map<String, Practice> practices,
                  List<Map<String, Object>> failedObservations, List<String> limitations) {}

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Set<String> LEARNING_TOOLS = Set.of("learning.read_current_path", "learning.read_graph_scope",
            "learning.read_evidence", "learning.read_learning_inputs", "learning.read_assessment_summary",
            "learning.draft_path", "learning.draft_explanation", "learning.draft_practice");
    private static final Set<String> RESEARCH_TOOLS = Set.of("research.search_nodes", "research.retrieve_chunks",
            "research.read_source", "research.get_node", "research.get_neighbors", "research.find_paths",
            "research.draft_expansion");
    private static final Set<String> ASSESSMENT_TOOLS = Set.of("assessment.read_context",
            "assessment.read_legacy_history", "assessment.read_valid_results", "assessment.prepare_draft");
    private AssessmentMaterials() {}

    static Bundle inspect(AssessmentContextReader.Snapshot snapshot, Scope scope, String consumerTaskId) {
        if (snapshot.artifacts().size() > 8 || snapshot.capturedObservations().size() > 32)
            throw new IllegalArgumentException("MATERIAL_LIMIT");
        Map<String, EvidenceRef> evidence = new LinkedHashMap<>();
        Map<String, Pointer> pointers = new LinkedHashMap<>();
        Map<String, Artifact> artifactsByProducer = new HashMap<>();
        List<Map<String, Object>> failures = new ArrayList<>();
        Set<String> limitations = new LinkedHashSet<>();
        for (Artifact artifact : snapshot.artifacts()) {
            Role role = snapshot.producerRoles().get(artifact.producerTaskId());
            if (!snapshot.directDependencyTaskIds().contains(artifact.producerTaskId())
                    || role == null || role == Role.ASSESSMENT_ANALYSIS
                    || !artifact.authorizedScope().isWithin(scope)
                    || artifact.outcome() != Outcome.COMPLETED && artifact.outcome() != Outcome.PARTIAL
                    || artifactsByProducer.putIfAbsent(artifact.producerTaskId(), artifact) != null)
                throw new IllegalArgumentException("NON_DIRECT_MATERIAL");
            for (EvidenceRef ref : artifact.evidence()) addEvidence(evidence, pointers, ref, scope);
            if (artifact.outcome() == Outcome.PARTIAL) limitations.add("PARTIAL_DIRECT_DEPENDENCY");
            limitations.addAll(artifact.limitations());
        }
        for (AssessmentContextReader.CapturedObservation captured : snapshot.capturedObservations()) {
            ToolObservation observation = captured.observation();
            boolean own = consumerTaskId.equals(captured.producerTaskId());
            Role role = captured.producerRole();
            Set<String> allowed = role == Role.LEARNING_COACH ? LEARNING_TOOLS
                    : role == Role.GRAPH_RESEARCH ? RESEARCH_TOOLS
                    : role == Role.ASSESSMENT_ANALYSIS && own ? ASSESSMENT_TOOLS : Set.of();
            if (observation == null || observation.result() == null || observation.toolCallId() == null
                    || observation.toolCallId().isBlank() || observation.observedAt() == null
                    || !allowed.contains(observation.toolName())
                    || !observation.result().effectiveScope().isWithin(scope)
                    || !own && (!snapshot.directDependencyTaskIds().contains(captured.producerTaskId())
                        || snapshot.producerRoles().get(captured.producerTaskId()) != role
                        || !artifactsByProducer.containsKey(captured.producerTaskId())))
                throw new IllegalArgumentException("UNTRUSTED_TOOL_OBSERVATION");
            if (observation.result().status() == ToolStatus.ERROR) {
                failures.add(Map.of("producerTaskId", captured.producerTaskId(),
                        "toolCallId", observation.toolCallId(), "toolName", observation.toolName(),
                        "errorCode", observation.result().errorCode(), "observedAt", observation.observedAt().toString()));
                limitations.add("PREVIOUS_TRUSTED_TOOL_ERROR");
                continue;
            }
            if (observation.result().status() == ToolStatus.PARTIAL) limitations.add("PREVIOUS_TOOL_PARTIAL");
            limitations.addAll(observation.result().limitations());
            for (EvidenceRef ref : observation.result().evidence()) addEvidence(evidence, pointers, ref, scope);
        }
        Map<String, Practice> practices = new LinkedHashMap<>();
        for (AssessmentContextReader.CapturedObservation captured : snapshot.capturedObservations()) {
            ToolObservation observation = captured.observation();
            if (captured.producerRole() != Role.LEARNING_COACH
                    || !"learning.draft_practice".equals(observation.toolName())
                    || observation.result().status() == ToolStatus.ERROR) continue;
            JsonNode data = parse(observation.result().dataJson());
            if (!"practice_set".equals(data.path("kind").asText())
                    || !"informal_practice".equals(data.path("mode").asText())
                    || !data.path("saved").isBoolean() || data.path("saved").booleanValue()
                    || !data.path("executionAllowed").isBoolean() || data.path("executionAllowed").booleanValue()
                    || !data.path("recommendationOnly").asBoolean()
                    || !data.path("progressChanged").isBoolean() || data.path("progressChanged").booleanValue()
                    || !data.path("formalAssessmentAvailable").isBoolean()
                    || data.path("formalAssessmentAvailable").booleanValue())
                throw new IllegalArgumentException("INVALID_LEARNING_PRACTICE");
            long nodeId = positive(data.path("nodeId"));
            JsonNode items = data.path("items");
            if (!items.isArray() || items.isEmpty() || items.size() > 5)
                throw new IllegalArgumentException("INVALID_LEARNING_PRACTICE");
            for (JsonNode item : items) {
                String localId = checkedText(item.path("localId"), 46);
                if (!localId.matches("draft:[A-Za-z0-9_-]{1,40}"))
                    throw new IllegalArgumentException("INVALID_LEARNING_PRACTICE");
                String prompt = checkedText(item.path("prompt"), 800);
                String answer = checkedText(item.path("referenceAnswer"), 800);
                JsonNode ids = item.path("sourceIds");
                if (!ids.isArray() || ids.size() > 4) throw new IllegalArgumentException("INVALID_LEARNING_PRACTICE");
                List<String> sourceIds = new ArrayList<>();
                for (JsonNode id : ids) {
                    String value = checkedText(id, 256);
                    if (!evidence.containsKey(value)) throw new IllegalArgumentException("UNTRUSTED_PRACTICE_SOURCE");
                    sourceIds.add(value);
                }
                if (new HashSet<>(sourceIds).size() != sourceIds.size())
                    throw new IllegalArgumentException("INVALID_LEARNING_PRACTICE");
                String key = captured.producerTaskId() + ":" + observation.toolCallId() + ":" + localId;
                Artifact producerArtifact = artifactsByProducer.get(captured.producerTaskId());
                if (practices.putIfAbsent(key, new Practice(producerArtifact.artifactId(), captured.producerTaskId(),
                        observation.toolCallId(), localId, nodeId, prompt, answer, List.copyOf(sourceIds))) != null)
                    throw new IllegalArgumentException("DUPLICATE_PRACTICE");
            }
        }
        limitations.add("QUALITY_OBSERVATION_UNAVAILABLE");
        return new Bundle(Map.copyOf(pointers), Map.copyOf(practices), List.copyOf(failures),
                List.copyOf(limitations));
    }

    static Pointer pointer(EvidenceRef ref) {
        if (ref.kind() != EvidenceKind.SOURCE || !"document_chunk".equals(ref.object().kind())
                || ref.object().version() == null || ref.locator() == null)
            throw new IllegalArgumentException("INVALID_SOURCE_POINTER");
        long document = 0;
        for (String part : ref.locator().split(";")) if (part.startsWith("document=")) {
            try { document = Long.parseLong(part.substring(9)); }
            catch (NumberFormatException ignored) { throw new IllegalArgumentException("INVALID_SOURCE_POINTER"); }
        }
        long chunk;
        try { chunk = Long.parseLong(ref.object().id()); }
        catch (NumberFormatException ignored) { throw new IllegalArgumentException("INVALID_SOURCE_POINTER"); }
        if (document <= 0 || chunk <= 0) throw new IllegalArgumentException("INVALID_SOURCE_POINTER");
        String hash = ref.object().version();
        String suffix = ":" + hash + ":scope:" + AssessmentEvidence.scopeFingerprint(ref.scope());
        String researchId = "research:chunk:" + document + ":" + chunk + suffix;
        String learningId = "learning:chunk:" + document + ":" + chunk + suffix;
        if (!ref.sourceId().equals(researchId) && !ref.sourceId().equals(learningId))
            throw new IllegalArgumentException("INVALID_SOURCE_POINTER");
        return new Pointer(document, chunk, hash, ref);
    }

    private static void addEvidence(Map<String, EvidenceRef> evidence, Map<String, Pointer> pointers,
                                    EvidenceRef ref, Scope scope) {
        if (!ref.scope().isWithin(scope)) throw new IllegalArgumentException("SOURCE_SCOPE_MISMATCH");
        EvidenceRef old = evidence.putIfAbsent(ref.sourceId(), ref);
        if (old != null && !old.equals(ref)) throw new IllegalArgumentException("EVIDENCE_ID_CONFLICT");
        if (ref.kind() == EvidenceKind.SOURCE) pointers.put(ref.sourceId(), pointer(ref));
    }

    private static JsonNode parse(String text) {
        try { return JSON.readTree(text); }
        catch (Exception ignored) { throw new IllegalArgumentException("INVALID_LEARNING_PRACTICE"); }
    }
    private static long positive(JsonNode value) {
        if (!value.isIntegralNumber() || !value.canConvertToLong() || value.longValue() <= 0)
            throw new IllegalArgumentException("INVALID_LEARNING_PRACTICE");
        return value.longValue();
    }
    private static String checkedText(JsonNode value, int maximum) {
        if (!value.isTextual() || value.textValue().isBlank() || value.textValue().length() > maximum)
            throw new IllegalArgumentException("INVALID_LEARNING_PRACTICE");
        return value.textValue();
    }
}
