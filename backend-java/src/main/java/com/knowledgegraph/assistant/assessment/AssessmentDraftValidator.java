package com.knowledgegraph.assistant.assessment;

import com.fasterxml.jackson.databind.JsonNode;
import com.knowledgegraph.assistant.contract.AssistantContracts.*;
import com.knowledgegraph.assistant.learning.LearningReadRepository;
import com.knowledgegraph.assistant.research.ResearchReadRepository;
import com.knowledgegraph.assistant.source.SourceLocator;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Validates bounded suggestions. Source identity checks do not establish semantic correctness. */
final class AssessmentDraftValidator {
    record Draft(Map<String, Object> data, List<EvidenceRef> evidence, List<String> limitations) {}
    private AssessmentDraftValidator() {}

    static void shape(JsonNode args) {
        AssessmentJson.fields(args, "pathId", "stepId", "expectedSnapshotFingerprint", "items");
        AssessmentJson.id(args, "pathId"); AssessmentJson.id(args, "stepId");
        String fingerprint = AssessmentJson.text(args, "expectedSnapshotFingerprint", 64, 64);
        if (!fingerprint.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("INVALID_FINGERPRINT");
        JsonNode items = args.get("items");
        if (items == null || !items.isArray() || items.isEmpty() || items.size() > 5)
            throw new IllegalArgumentException("INVALID_QUESTION_COUNT");
        Set<String> itemIds = new HashSet<>();
        for (JsonNode item : items) {
            AssessmentJson.fields(item, "localId", "prompt", "topic", "options", "referenceOptionId",
                    "explanation", "sourceIds", "basedOnPracticeRef");
            String localId = AssessmentJson.text(item, "localId", 7, 46);
            if (!localId.matches("draft:[A-Za-z0-9_-]{1,40}") || !itemIds.add(localId))
                throw new IllegalArgumentException("INVALID_DRAFT_ID");
            AssessmentJson.text(item, "prompt", 1, 800);
            AssessmentJson.text(item, "topic", 1, 200);
            AssessmentJson.text(item, "explanation", 1, 1000);
            String answer = AssessmentJson.text(item, "referenceOptionId", 1, 46);
            if (item.has("basedOnPracticeRef")) AssessmentJson.text(item, "basedOnPracticeRef", 1, 256);
            JsonNode options = item.get("options");
            if (options == null || !options.isArray() || options.size() < 2 || options.size() > 4)
                throw new IllegalArgumentException("INVALID_OPTIONS");
            Set<String> optionIds = new HashSet<>();
            Set<String> optionTexts = new HashSet<>();
            for (JsonNode option : options) {
                AssessmentJson.fields(option, "id", "text");
                String id = AssessmentJson.text(option, "id", 1, 46);
                if (!id.matches("[A-Za-z0-9_-]{1,46}") || !optionIds.add(id)
                        || !optionTexts.add(AssessmentJson.text(option, "text", 1, 300)))
                    throw new IllegalArgumentException("DUPLICATE_OR_INVALID_OPTION");
            }
            if (!optionIds.contains(answer)) throw new IllegalArgumentException("REFERENCE_OPTION_MISSING");
            AssessmentJson.strings(item, "sourceIds", 4, 256);
        }
    }

    static Draft prepare(JsonNode args, Scope scope, LearningReadRepository learning,
                         ResearchReadRepository research, SourceLocator sources,
                         AssessmentMaterials.Bundle materials) {
        shape(args);
        LearningReadRepository.Path path = learning.currentPath(scope);
        if (path.state() != LearningReadRepository.PathState.AVAILABLE)
            throw new IllegalArgumentException("PATH_UNAVAILABLE");
        long pathId = args.get("pathId").longValue(), stepId = args.get("stepId").longValue();
        String expected = args.get("expectedSnapshotFingerprint").textValue();
        if (path.pathId() != pathId || !expected.equals(path.snapshotFingerprint()))
            throw new IllegalArgumentException("STALE_PATH_BASELINE");
        List<LearningReadRepository.Step> current = path.steps().stream()
                .filter(step -> "current".equals(step.status())).toList();
        if (current.size() != 1 || path.steps().stream()
                .anyMatch(step -> !Set.of("completed", "current", "locked").contains(step.status())))
            throw new IllegalArgumentException("CURRENT_STEP_UNAVAILABLE");
        LearningReadRepository.Step step = current.get(0);
        if (step.stepId() != stepId) throw new IllegalArgumentException("STALE_STEP_BASELINE");
        ResearchReadRepository.Node node = learning.node(scope, step.nodeId())
                .orElseThrow(() -> new IllegalArgumentException("STEP_NODE_UNAVAILABLE"));
        if (!"active".equals(node.status())) throw new IllegalArgumentException("STEP_NODE_UNAVAILABLE");
        List<Map<String, Object>> items = new ArrayList<>();
        Map<String, EvidenceRef> evidence = new LinkedHashMap<>();
        evidence.put(AssessmentEvidence.path(path, scope).sourceId(), AssessmentEvidence.path(path, scope));
        Set<String> limits = new LinkedHashSet<>();
        limits.add("ANSWER_VALIDITY_UNVERIFIED");
        limits.add("SEMANTIC_SUPPORT_NOT_EVALUATED");
        limits.add("FORMAL_SCORING_UNAVAILABLE");
        int missingEvidence = 0;
        for (JsonNode item : args.get("items")) {
            List<String> sourceIds = AssessmentJson.strings(item, "sourceIds", 4, 256);
            if (sourceIds.isEmpty()) {
                missingEvidence++;
                limits.add("QUESTION_SOURCE_MISSING");
            } else {
                if (!scope.nodeIds().isEmpty()) throw new IllegalArgumentException("NODE_SCOPE_UNSUPPORTED");
                if (materials == null) throw new IllegalArgumentException("MATERIAL_HOST_UNAVAILABLE");
                for (String sourceId : sourceIds) {
                    AssessmentMaterials.Pointer pointer = materials.pointers().get(sourceId);
                    if (pointer == null) throw new IllegalArgumentException("UNTRUSTED_SOURCE_ID");
                    if (!learning.nodeSourceLinked(scope, step.nodeId(), pointer.documentId(), pointer.chunkId()))
                        throw new IllegalArgumentException("NODE_SOURCE_NOT_LINKED");
                    var before = research.sourceMeta(scope, pointer.documentId(), pointer.chunkId());
                    if (before.isEmpty() || !before.get().formalAvailable()
                            || !pointer.hash().equals(before.get().contentHash()))
                        throw new IllegalArgumentException("STALE_OR_UNAVAILABLE_SOURCE");
                    var body = sources.chunk(scope, pointer.documentId(), pointer.chunkId(), 4000);
                    var after = research.sourceMeta(scope, pointer.documentId(), pointer.chunkId());
                    if (body.isEmpty() || after.isEmpty() || !after.get().formalAvailable()
                            || !pointer.hash().equals(body.get().contentHash())
                            || !pointer.hash().equals(after.get().contentHash())
                            || body.get().unitId() != after.get().unitId() || body.get().contentTruncated())
                        throw new IllegalArgumentException("STALE_OR_TRUNCATED_SOURCE");
                    EvidenceRef original = pointer.original();
                    EvidenceRef previous = evidence.putIfAbsent(original.sourceId(), original);
                    if (previous != null && !previous.equals(original))
                        throw new IllegalArgumentException("EVIDENCE_ID_CONFLICT");
                }
            }
            String practiceRef = item.has("basedOnPracticeRef") ? item.get("basedOnPracticeRef").textValue() : null;
            if (practiceRef != null) {
                AssessmentMaterials.Practice practice = materials == null ? null : materials.practices().get(practiceRef);
                if (practice == null || practice.nodeId() != step.nodeId())
                    throw new IllegalArgumentException("UNTRUSTED_LEARNING_PRACTICE");
            }
            AssessmentMaterials.Practice practice = practiceRef == null ? null : materials.practices().get(practiceRef);
            List<Map<String, Object>> options = new ArrayList<>();
            for (JsonNode option : item.get("options"))
                options.add(map("id", option.get("id").textValue(), "text", option.get("text").textValue()));
            items.add(map("localId", item.get("localId").textValue(),
                    "prompt", item.get("prompt").textValue(), "topic", item.get("topic").textValue(),
                    "options", options, "referenceOptionId", item.get("referenceOptionId").textValue(),
                    "explanation", item.get("explanation").textValue(), "sourceIds", sourceIds,
                    "basedOnPracticeRef", practiceRef,
                    "practiceOrigin", practice == null ? null : map("artifactId", practice.artifactId(),
                            "producerTaskId", practice.producerTaskId(), "toolCallId", practice.toolCallId(),
                            "localId", practice.localId(), "mode", "informal_practice", "formalEligible", false),
                    "semanticSupport", "not_evaluated",
                    "answerValidity", "unverified", "formalEligible", false,
                    "evidenceGap", sourceIds.isEmpty() ? "source_missing" : null));
        }
        LearningReadRepository.Path after = learning.currentPath(scope);
        if (after.state() != LearningReadRepository.PathState.AVAILABLE || after.pathId() != pathId
                || !expected.equals(after.snapshotFingerprint()))
            throw new IllegalArgumentException("STALE_PATH_BASELINE");
        if (materials != null) limits.addAll(materials.limitations());
        return new Draft(map("kind", "assessment_draft", "status", "unexecuted_draft",
                "basePathRef", map("pathId", pathId, "stepId", stepId, "stepKey", step.stepKey(),
                        "snapshotFingerprint", expected, "formalRevision", null),
                "targetNodeId", step.nodeId(), "items", items,
                "questionCount", items.size(), "questionsMissingEvidence", missingEvidence,
                "saved", false, "executionAllowed", false, "recommendationOnly", true,
                "progressChanged", false, "formalAssessmentAvailable", false,
                "assessmentId", null, "attemptId", null, "score", null, "passed", null,
                "mastery", null), List.copyOf(evidence.values()), List.copyOf(limits));
    }

    private static Map<String, Object> map(Object... values) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int i = 0; i < values.length; i += 2) result.put((String) values[i], values[i + 1]);
        return result;
    }
}
