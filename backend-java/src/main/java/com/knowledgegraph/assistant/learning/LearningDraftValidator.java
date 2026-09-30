package com.knowledgegraph.assistant.learning;

import com.fasterxml.jackson.databind.JsonNode;
import com.knowledgegraph.assistant.contract.AssistantContracts.*;
import com.knowledgegraph.assistant.research.ResearchReadRepository;
import com.knowledgegraph.assistant.source.SourceLocator;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Deterministic validation and diffing of bounded model suggestions; never writes a path or assessment. */
final class LearningDraftValidator {
    record Draft(Map<String, Object> data, List<EvidenceRef> evidence, List<String> limitations) {}
    private LearningDraftValidator() {}

    static void pathShape(JsonNode args) {
        LearningJson.fields(args, "targetLibraryId", "mode", "goal", "difficulty", "days",
                "minutesPerDay", "basePathId", "baseSnapshotFingerprint", "steps");
        LearningJson.id(args, "targetLibraryId");
        String mode = LearningJson.text(args, "mode", 3, 7);
        if (!Set.of("new", "adjust", "replace").contains(mode)) throw new IllegalArgumentException("INVALID_MODE");
        LearningJson.text(args, "goal", 1, 500);
        if (!Set.of("beginner", "intermediate", "advanced", "unknown")
                .contains(LearningJson.text(args, "difficulty", 6, 12))) throw new IllegalArgumentException("INVALID_DIFFICULTY");
        LearningJson.integer(args, "days", 1, 365, 1);
        LearningJson.integer(args, "minutesPerDay", 1, 600, 1);
        if (mode.equals("new")) {
            if (args.has("basePathId") || args.has("baseSnapshotFingerprint"))
                throw new IllegalArgumentException("UNEXPECTED_BASE");
        } else {
            LearningJson.id(args, "basePathId");
            LearningJson.text(args, "baseSnapshotFingerprint", 64, 64);
        }
        JsonNode steps = args.get("steps");
        if (steps == null || !steps.isArray() || steps.size() < 2 || steps.size() > 8)
            throw new IllegalArgumentException("INVALID_STEP_COUNT");
        Set<String> localIds = new HashSet<>();
        Set<Long> nodeIds = new HashSet<>();
        for (JsonNode step : steps) {
            LearningJson.fields(step, "localId", "nodeId", "objective", "reason", "reasonKind",
                    "relationEdgeId", "estimatedMinutes", "sourceIds");
            String localId = LearningJson.text(step, "localId", 7, 46);
            if (!localId.matches("draft:[A-Za-z0-9_-]{1,40}") || !localIds.add(localId))
                throw new IllegalArgumentException("INVALID_DRAFT_STEP_ID");
            if (!nodeIds.add(LearningJson.id(step, "nodeId"))) throw new IllegalArgumentException("DUPLICATE_NODE");
            LearningJson.text(step, "objective", 1, 300);
            LearningJson.text(step, "reason", 1, 500);
            String basis = LearningJson.text(step, "reasonKind", 9, 14);
            if (!Set.of("inference", "graph_relation").contains(basis))
                throw new IllegalArgumentException("INVALID_REASON_KIND");
            if (basis.equals("graph_relation")) LearningJson.id(step, "relationEdgeId");
            else if (step.has("relationEdgeId")) throw new IllegalArgumentException("UNEXPECTED_EDGE");
            LearningJson.integer(step, "estimatedMinutes", 1, 240, 0);
            LearningJson.strings(step, "sourceIds", 4, 256);
        }
    }

    static Draft path(JsonNode args, Scope scope, LearningReadRepository repo,
                      ResearchReadRepository research, SourceLocator sources,
                      LearningMaterials.Bundle materials) {
        pathShape(args);
        long libraryId = LearningJson.id(args, "targetLibraryId");
        if (!repo.libraryVisible(scope, libraryId)) throw new IllegalArgumentException("LIBRARY_OUT_OF_SCOPE");
        Scope target = new Scope(ScopeMode.SELECTED, Set.of(libraryId), scope.documentIds(), scope.nodeIds());
        String mode = args.get("mode").textValue();
        LearningReadRepository.Path current = repo.currentPath(scope);
        if (current.state() == LearningReadRepository.PathState.UNAVAILABLE)
            throw new IllegalArgumentException("PATH_SCOPE_INSUFFICIENT");
        if (mode.equals("new") && current.state() != LearningReadRepository.PathState.NONE)
            throw new IllegalArgumentException("PATH_ALREADY_EXISTS");
        if (!mode.equals("new")) {
            if (current.state() != LearningReadRepository.PathState.AVAILABLE)
                throw new IllegalArgumentException("BASE_PATH_UNAVAILABLE");
            if (current.pathId() != LearningJson.id(args, "basePathId")
                    || !current.snapshotFingerprint().equals(args.get("baseSnapshotFingerprint").textValue()))
                throw new IllegalArgumentException("STALE_PATH_BASELINE");
            if (mode.equals("adjust") && current.libraryId() != libraryId)
                throw new IllegalArgumentException("ADJUST_LIBRARY_MISMATCH");
        }
        Set<String> limitations = new LinkedHashSet<>();
        List<EvidenceRef> refs = new ArrayList<>();
        List<Map<String, Object>> steps = new ArrayList<>();
        List<Long> proposedNodes = new ArrayList<>();
        JsonNode proposals = args.get("steps");
        for (int i = 0; i < proposals.size(); i++) {
            JsonNode step = proposals.get(i);
            long nodeId = step.get("nodeId").longValue();
            ResearchReadRepository.Node node = repo.node(target, nodeId)
                    .orElseThrow(() -> new IllegalArgumentException("NODE_OUT_OF_SCOPE"));
            if (!"active".equals(node.status())) throw new IllegalArgumentException("INACTIVE_NODE");
            proposedNodes.add(nodeId);
            String basis = step.get("reasonKind").textValue();
            Map<String, Object> relation = null;
            if (basis.equals("graph_relation")) {
                if (i == 0) throw new IllegalArgumentException("FIRST_STEP_HAS_RELATION");
                long edgeId = step.get("relationEdgeId").longValue();
                ResearchReadRepository.Edge edge = repo.edge(target, edgeId)
                        .orElseThrow(() -> new IllegalArgumentException("RELATION_OUT_OF_SCOPE"));
                if (!"active".equals(edge.status()) || edge.sourceNodeId() != proposedNodes.get(i - 1)
                        || edge.targetNodeId() != nodeId)
                    throw new IllegalArgumentException("RELATION_DIRECTION_MISMATCH");
                EvidenceRef ref = LearningEvidence.edge(edge, target);
                refs.add(ref);
                relation = map("edgeId", edge.id(), "sourceNodeId", edge.sourceNodeId(),
                        "targetNodeId", edge.targetNodeId(), "relationType", edge.relationType(),
                        "sourceId", ref.sourceId(), "prerequisiteSemantics", "not_established");
                limitations.add("GRAPH_RELATION_NOT_PROVEN_PREREQUISITE");
            }
            List<String> sourceIds = LearningJson.strings(step, "sourceIds", 4, 256);
            refs.addAll(verifySources(scope, nodeId, sourceIds, repo, research, sources, materials));
            if (sourceIds.isEmpty()) limitations.add("STEP_SOURCE_NOT_PROVEN");
            steps.add(map("localId", step.get("localId").textValue(), "position", i,
                    "nodeId", nodeId, "title", node.name(),
                    "objective", step.get("objective").textValue(), "reason", step.get("reason").textValue(),
                    "reasonKind", basis, "relation", relation,
                    "estimatedMinutes", step.get("estimatedMinutes").intValue(),
                    "sourceIds", sourceIds, "officialStepId", null, "status", "draft_unsaved"));
        }
        List<Map<String, Object>> diff = new ArrayList<>();
        if (current.state() == LearningReadRepository.PathState.AVAILABLE) {
            Set<Long> proposed = new HashSet<>(proposedNodes);
            for (LearningReadRepository.Step old : current.steps()) {
                int nextPosition = proposedNodes.indexOf(old.nodeId());
                diff.add(map("baseStepId", old.stepId(), "baseStepKey", old.stepKey(),
                        "nodeId", old.nodeId(), "change", nextPosition < 0 ? "removed"
                                : nextPosition == old.position() ? "kept" : "reordered",
                        "wasCompletedInLegacyRecord", "completed".equals(old.status()),
                        "completedStepImpact", "completed".equals(old.status())
                                && (nextPosition < 0 || nextPosition != old.position())
                                ? "requires_formal_review" : "none"));
            }
        }
        List<Long> added = proposedNodes.stream().filter(id -> current.steps().stream()
                .noneMatch(step -> step.nodeId() == id)).toList();
        int totalMinutes = steps.stream().mapToInt(step -> (Integer) step.get("estimatedMinutes")).sum();
        limitations.add("TIME_IS_ESTIMATE");
        limitations.add("PATH_HISTORY_NOT_MODIFIED");
        return new Draft(map("kind", "path_draft", "mode", mode, "targetLibraryId", libraryId,
                "goal", args.get("goal").textValue(), "difficulty", args.get("difficulty").textValue(),
                "timeBudget", args.has("days") && args.has("minutesPerDay")
                        ? map("days", args.get("days").intValue(), "minutesPerDay", args.get("minutesPerDay").intValue())
                        : map("status", "unknown"),
                "estimatedTotalMinutes", totalMinutes, "steps", steps,
                "basePathRef", current.state() == LearningReadRepository.PathState.AVAILABLE
                        ? map("pathId", current.pathId(), "snapshotFingerprint", current.snapshotFingerprint(),
                        "revision", null, "fingerprintKind", "snapshot_not_write_lock") : null,
                "diff", map("existingSteps", diff, "addedNodeIds", added,
                        "completeBaseline", current.state() != LearningReadRepository.PathState.UNAVAILABLE),
                "saved", false, "executionAllowed", false, "recommendationOnly", true,
                "progressChanged", false, "formalRevision", null),
                distinct(refs), List.copyOf(limitations));
    }

    static void explanationShape(JsonNode args) {
        LearningJson.fields(args, "nodeId", "blocks");
        LearningJson.id(args, "nodeId");
        JsonNode blocks = args.get("blocks");
        if (blocks == null || !blocks.isArray() || blocks.isEmpty() || blocks.size() > 8)
            throw new IllegalArgumentException("INVALID_BLOCK_COUNT");
        for (JsonNode block : blocks) {
            LearningJson.fields(block, "kind", "text", "sourceIds");
            if (!Set.of("source_fact", "system_fact", "graph_fact", "model_explanation", "recommendation")
                    .contains(LearningJson.text(block, "kind", 10, 20)))
                throw new IllegalArgumentException("INVALID_BLOCK_KIND");
            LearningJson.text(block, "text", 1, 1000);
            LearningJson.strings(block, "sourceIds", 4, 256);
        }
    }

    static Draft explanation(JsonNode args, Scope scope, LearningReadRepository repo,
                             ResearchReadRepository research, SourceLocator sources,
                             LearningMaterials.Bundle materials) {
        explanationShape(args);
        long nodeId = args.get("nodeId").longValue();
        ResearchReadRepository.Node node = repo.node(scope, nodeId)
                .orElseThrow(() -> new IllegalArgumentException("NODE_OUT_OF_SCOPE"));
        if (!"active".equals(node.status())) throw new IllegalArgumentException("INACTIVE_NODE");
        List<EvidenceRef> refs = new ArrayList<>();
        List<Map<String, Object>> blocks = new ArrayList<>();
        Set<String> limits = new LinkedHashSet<>();
        for (JsonNode block : args.get("blocks")) {
            String kind = block.get("kind").textValue();
            List<String> ids = LearningJson.strings(block, "sourceIds", 4, 256);
            if (kind.equals("source_fact")) {
                if (ids.isEmpty()) throw new IllegalArgumentException("SOURCE_FACT_NEEDS_EVIDENCE");
                refs.addAll(verifySources(scope, nodeId, ids, repo, research, sources, materials));
                limits.add("SEMANTIC_SUPPORT_NOT_EVALUATED");
            } else if (kind.equals("graph_fact") || kind.equals("system_fact")) {
                if (!ids.isEmpty()) throw new IllegalArgumentException("GRAPH_FACT_USES_REAL_NODE");
                refs.add(LearningEvidence.node(node, scope));
                if (!node.sourceEvidenceAvailable()) limits.add("NODE_SOURCE_NOT_AVAILABLE_IN_SCOPE");
            } else {
                if (!ids.isEmpty()) throw new IllegalArgumentException("MODEL_EXPLANATION_CANNOT_CITE");
                limits.add("MODEL_TEXT_REQUIRES_HUMAN_REVIEW");
            }
            blocks.add(map("kind", kind, "text", block.get("text").textValue(),
                    "sourceIds", kind.equals("graph_fact") || kind.equals("system_fact")
                            ? List.of(LearningEvidence.node(node, scope).sourceId()) : ids,
                    "semanticSupport", "not_evaluated"));
        }
        return new Draft(map("kind", "layered_explanation", "nodeId", nodeId,
                "nodeStatus", node.status(), "blocks", blocks, "saved", false,
                "recommendationOnly", true, "humanVerified", false),
                distinct(refs), List.copyOf(limits));
    }

    static void practiceShape(JsonNode args) {
        LearningJson.fields(args, "nodeId", "items");
        LearningJson.id(args, "nodeId");
        JsonNode items = args.get("items");
        if (items == null || !items.isArray() || items.isEmpty() || items.size() > 5)
            throw new IllegalArgumentException("INVALID_PRACTICE_COUNT");
        Set<String> ids = new HashSet<>();
        for (JsonNode item : items) {
            LearningJson.fields(item, "localId", "prompt", "answer", "explanation", "hints", "sourceIds");
            String id = LearningJson.text(item, "localId", 7, 46);
            if (!id.matches("draft:[A-Za-z0-9_-]{1,40}") || !ids.add(id))
                throw new IllegalArgumentException("INVALID_PRACTICE_ID");
            LearningJson.text(item, "prompt", 1, 800);
            LearningJson.text(item, "answer", 1, 800);
            LearningJson.text(item, "explanation", 1, 1000);
            LearningJson.strings(item, "hints", 3, 300);
            LearningJson.strings(item, "sourceIds", 4, 256);
        }
    }
    static Draft practice(JsonNode args, Scope scope, LearningReadRepository repo,
                          ResearchReadRepository research, SourceLocator sources,
                          LearningMaterials.Bundle materials) {
        practiceShape(args);
        long nodeId = args.get("nodeId").longValue();
        ResearchReadRepository.Node node = repo.node(scope, nodeId)
                .orElseThrow(() -> new IllegalArgumentException("NODE_OUT_OF_SCOPE"));
        if (!"active".equals(node.status())) throw new IllegalArgumentException("INACTIVE_NODE");
        List<EvidenceRef> refs = new ArrayList<>();
        List<Map<String, Object>> items = new ArrayList<>();
        Set<String> limits = new LinkedHashSet<>();
        for (JsonNode item : args.get("items")) {
            List<String> ids = LearningJson.strings(item, "sourceIds", 4, 256);
            refs.addAll(verifySources(scope, nodeId, ids, repo, research, sources, materials));
            if (ids.isEmpty()) limits.add("PRACTICE_ANSWER_UNVERIFIED_SUGGESTION");
            items.add(map("localId", item.get("localId").textValue(), "prompt", item.get("prompt").textValue(),
                    "referenceAnswer", item.get("answer").textValue(),
                    "explanation", item.get("explanation").textValue(),
                    "hints", LearningJson.strings(item, "hints", 3, 300),
                    "sourceIds", ids, "semanticSupport", "not_evaluated"));
        }
        limits.add("INFORMAL_PRACTICE_ONLY");
        return new Draft(map("kind", "practice_set", "mode", "informal_practice",
                "nodeId", nodeId, "items", items, "saved", false, "executionAllowed", false,
                "recommendationOnly", true, "progressChanged", false,
                "formalAssessmentAvailable", false), distinct(refs), List.copyOf(limits));
    }

    private static List<EvidenceRef> verifySources(Scope scope, long nodeId, List<String> ids,
            LearningReadRepository repo, ResearchReadRepository research, SourceLocator sources,
            LearningMaterials.Bundle materials) {
        if (ids.isEmpty()) return List.of();
        if (!scope.nodeIds().isEmpty()) throw new IllegalArgumentException("NODE_SCOPE_UNSUPPORTED");
        if (materials == null) throw new IllegalArgumentException("MATERIAL_HOST_UNAVAILABLE");
        List<EvidenceRef> verified = new ArrayList<>();
        for (String id : ids) {
            LearningMaterials.Pointer pointer = materials.pointers().get(id);
            if (pointer == null) throw new IllegalArgumentException("UNTRUSTED_SOURCE_ID");
            if (!repo.nodeSourceLinked(scope, nodeId, pointer.documentId(), pointer.chunkId()))
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
                    || body.get().unitId() != after.get().unitId()
                    || body.get().contentTruncated())
                throw new IllegalArgumentException("STALE_OR_TRUNCATED_SOURCE");
            verified.add(pointer.original());
        }
        return verified;
    }
    private static List<EvidenceRef> distinct(List<EvidenceRef> values) {
        Map<String, EvidenceRef> refs = new LinkedHashMap<>();
        for (EvidenceRef value : values) {
            EvidenceRef old = refs.putIfAbsent(value.sourceId(), value);
            if (old != null && !old.equals(value)) throw new IllegalArgumentException("EVIDENCE_ID_CONFLICT");
        }
        return List.copyOf(refs.values());
    }
    private static Map<String, Object> map(Object... values) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int i = 0; i < values.length; i += 2) result.put((String) values[i], values[i + 1]);
        return result;
    }
}
