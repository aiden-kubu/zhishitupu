package com.knowledgegraph.assistant.assessment;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowledgegraph.assistant.contract.AssistantContracts.*;
import com.knowledgegraph.assistant.learning.LearningReadRepository;
import com.knowledgegraph.assistant.research.ResearchReadRepository;
import com.knowledgegraph.assistant.source.SourceLocator;
import com.knowledgegraph.assistant.tool.AssistantTool;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Isolated assessment reads and unexecuted suggestions; deliberately not registered with Spring. */
public final class AssessmentAgentToolProvider {
    private static final String CONTEXT_SCHEMA = """
            {"type":"object","properties":{"pathId":{"type":"integer","minimum":1},"stepId":{"type":"integer","minimum":1},"snapshotFingerprint":{"type":"string","pattern":"^[0-9a-f]{64}$"}},"required":[],"additionalProperties":false}
            """;
    private static final String HISTORY_SCHEMA = """
            {"type":"object","properties":{"pathId":{"type":"integer","minimum":1},"stepId":{"type":"integer","minimum":1},"limit":{"type":"integer","minimum":1,"maximum":20},"beforeAttemptId":{"type":"integer","minimum":1}},"required":["pathId"],"additionalProperties":false}
            """;
    private static final String VALID_SCHEMA = """
            {"type":"object","properties":{"pathId":{"type":"integer","minimum":1},"stepId":{"type":"integer","minimum":1}},"required":[],"additionalProperties":false}
            """;
    private static final String DRAFT_SCHEMA = """
            {"type":"object","properties":{"pathId":{"type":"integer","minimum":1},"stepId":{"type":"integer","minimum":1},"expectedSnapshotFingerprint":{"type":"string","pattern":"^[0-9a-f]{64}$"},"items":{"type":"array","minItems":1,"maxItems":5,"items":{"type":"object","properties":{"localId":{"type":"string","pattern":"^draft:[A-Za-z0-9_-]{1,40}$"},"prompt":{"type":"string","minLength":1,"maxLength":800},"topic":{"type":"string","minLength":1,"maxLength":200},"options":{"type":"array","minItems":2,"maxItems":4,"items":{"type":"object","properties":{"id":{"type":"string","minLength":1,"maxLength":46},"text":{"type":"string","minLength":1,"maxLength":300}},"required":["id","text"],"additionalProperties":false}},"referenceOptionId":{"type":"string","minLength":1,"maxLength":46},"explanation":{"type":"string","minLength":1,"maxLength":1000},"sourceIds":{"type":"array","maxItems":4,"items":{"type":"string","minLength":1,"maxLength":256}},"basedOnPracticeRef":{"type":"string","minLength":1,"maxLength":256}},"required":["localId","prompt","topic","options","referenceOptionId","explanation"],"additionalProperties":false}}},"required":["pathId","stepId","expectedSnapshotFingerprint","items"],"additionalProperties":false}
            """;
    private final LearningReadRepository learning;
    private final AssessmentReadRepository history;
    private final ResearchReadRepository research;
    private final SourceLocator sources;
    private final AssessmentContextReader host;
    private final ObjectMapper json = new ObjectMapper();

    public AssessmentAgentToolProvider(LearningReadRepository learning, AssessmentReadRepository history,
                                       ResearchReadRepository research, SourceLocator sources,
                                       AssessmentContextReader host) {
        this.learning = learning; this.history = history; this.research = research;
        this.sources = sources; this.host = host;
    }

    public List<AssistantTool> tools() {
        return List.of(tool("assessment.read_context", ToolEffect.READ_ONLY, CONTEXT_SCHEMA, this::context),
                tool("assessment.read_legacy_history", ToolEffect.READ_ONLY, HISTORY_SCHEMA, this::legacy),
                tool("assessment.read_valid_results", ToolEffect.READ_ONLY, VALID_SCHEMA, this::valid),
                tool("assessment.prepare_draft", ToolEffect.DRAFT_ONLY, DRAFT_SCHEMA, this::draft));
    }

    private interface Body { ToolResult run(AssistantTool.Context ctx, JsonNode args); }
    private AssistantTool tool(String name, ToolEffect effect, String schema, Body body) {
        ToolDescriptor descriptor = new ToolDescriptor(name, Set.of(Role.ASSESSMENT_ANALYSIS), effect,
                "Scoped assessment facts or unsaved candidate questions", schema.strip());
        return new AssistantTool() {
            public ToolDescriptor descriptor() { return descriptor; }
            public void validateArguments(JsonNode args, Scope scope) { validate(name, args); }
            public ToolResult execute(Context ctx, JsonNode args) {
                if (ctx.role() != Role.ASSESSMENT_ANALYSIS) return ToolResult.error(ctx.scope(), "ROLE_NOT_ALLOWED");
                try {
                    validate(name, args);
                    if (ctx.cancellationRequested().getAsBoolean()) return ToolResult.error(ctx.scope(), "CANCELLED");
                    if (Instant.now().isAfter(ctx.deadline())) return ToolResult.error(ctx.scope(), "DEADLINE_EXCEEDED");
                    return body.run(ctx, args);
                } catch (IllegalArgumentException ex) { return ToolResult.error(ctx.scope(), code(ex)); }
            }
        };
    }
    private static String code(IllegalArgumentException ex) {
        String message = ex.getMessage();
        return message != null && message.matches("[A-Z][A-Z0-9_]{1,90}") ? message : "INVALID_ASSESSMENT_INPUT";
    }
    private static void validate(String name, JsonNode args) {
        switch (name) {
            case "assessment.read_context" -> {
                AssessmentJson.fields(args, "pathId", "stepId", "snapshotFingerprint");
                AssessmentJson.optionalId(args, "pathId"); AssessmentJson.optionalId(args, "stepId");
                if (args.has("stepId") && !args.has("pathId")) throw new IllegalArgumentException("PATH_ID_REQUIRED");
                if (args.has("snapshotFingerprint")) {
                    if (!args.has("pathId")) throw new IllegalArgumentException("PATH_ID_REQUIRED");
                    if (!AssessmentJson.text(args, "snapshotFingerprint", 64, 64).matches("[0-9a-f]{64}"))
                        throw new IllegalArgumentException("INVALID_FINGERPRINT");
                }
            }
            case "assessment.read_legacy_history" -> {
                AssessmentJson.fields(args, "pathId", "stepId", "limit", "beforeAttemptId");
                AssessmentJson.id(args, "pathId"); AssessmentJson.optionalId(args, "stepId");
                AssessmentJson.optionalId(args, "beforeAttemptId");
                AssessmentJson.integer(args, "limit", 1, 20, 10);
            }
            case "assessment.read_valid_results" -> {
                AssessmentJson.fields(args, "pathId", "stepId");
                AssessmentJson.optionalId(args, "pathId"); AssessmentJson.optionalId(args, "stepId");
                if (args.has("stepId") && !args.has("pathId")) throw new IllegalArgumentException("PATH_ID_REQUIRED");
            }
            case "assessment.prepare_draft" -> AssessmentDraftValidator.shape(args);
            default -> throw new IllegalArgumentException("UNKNOWN_ASSESSMENT_TOOL");
        }
    }

    private ToolResult context(AssistantTool.Context ctx, JsonNode args) {
        Scope scope = ctx.scope();
        LearningReadRepository.Path path = learning.currentPath(scope);
        if (path.state() == LearningReadRepository.PathState.UNAVAILABLE) {
            if (args.has("pathId")) return ToolResult.error(scope, "PATH_UNAVAILABLE");
            return result(ToolStatus.PARTIAL, scope, map("state", "unavailable", "draftReady", false,
                    "formalAvailable", false, "reason", "path_scope_or_shape_insufficient"), List.of(),
                    List.of("PATH_SCOPE_OR_SHAPE_INSUFFICIENT"));
        }
        if (path.state() == LearningReadRepository.PathState.NONE) {
            if (args.has("pathId")) return ToolResult.error(scope, "STALE_PATH_BASELINE");
            return result(ToolStatus.PARTIAL, scope, map("state", "none", "profile", "local-default",
                    "draftReady", false, "formalAvailable", false, "formalRevision", null), List.of(),
                    List.of("NO_CURRENT_PATH", "FORMAL_SCORING_UNAVAILABLE"));
        }
        baseline(path, args, false);
        List<Map<String, Object>> steps = new ArrayList<>();
        List<LearningReadRepository.Step> current = new ArrayList<>();
        boolean invalid = false;
        for (var step : path.steps()) {
            if ("current".equals(step.status())) current.add(step);
            if (!Set.of("current", "completed", "locked").contains(step.status())) invalid = true;
            steps.add(map("stepId", step.stepId(), "stepKey", step.stepKey(), "nodeId", step.nodeId(),
                    "position", step.position(), "title", step.title(), "status", step.status(),
                    "visitedAt", step.visitedAt(), "legacyAssessmentScore", step.legacyAssessmentScore(),
                    "scoreValidity", "legacy_unverified", "updatedAt", step.updatedAt()));
        }
        String state = invalid ? "invalid_step_status" : current.isEmpty() ? "no_current_step"
                : current.size() > 1 ? "multiple_current_steps" : "available";
        LearningReadRepository.Step activeStep = "available".equals(state) ? current.get(0) : null;
        if (activeStep != null && learning.node(scope, activeStep.nodeId())
                .filter(n -> "active".equals(n.status())).isEmpty()) {
            state = "current_node_unavailable"; activeStep = null;
        }
        if (args.has("stepId") && (activeStep == null || activeStep.stepId() != args.get("stepId").longValue()))
            return ToolResult.error(scope, "STALE_STEP_BASELINE");
        Map<String, Object> data = map("state", state, "profile", "local-default", "profileIsolation", "local_shared",
                "pathId", path.pathId(), "libraryId", path.libraryId(), "goal", path.goal(),
                "difficulty", path.difficulty(), "pathStatus", path.status(), "updatedAt", path.updatedAt(),
                "steps", steps, "currentStepId", activeStep == null ? null : activeStep.stepId(),
                "currentStepKey", activeStep == null ? null : activeStep.stepKey(),
                "currentNodeId", activeStep == null ? null : activeStep.nodeId(),
                "draftReady", activeStep != null, "snapshotFingerprint", path.snapshotFingerprint(),
                "fingerprintKind", "read_observation_not_write_lock", "formatVersion", 2,
                "formalRevision", null, "formalAvailable", false, "observedAt", Instant.now().toString());
        List<String> limits = new ArrayList<>(List.of("FORMAL_SCORING_UNAVAILABLE", "NO_ATOMIC_SNAPSHOT"));
        if (activeStep == null) limits.add(state.toUpperCase(java.util.Locale.ROOT));
        Optional<AssessmentMaterials.Bundle> materials = materials(ctx);
        if (materials.isPresent()) {
            var bundle = materials.get();
            List<Map<String, Object>> practices = new ArrayList<>();
            for (var entry : bundle.practices().entrySet()) {
                var p = entry.getValue();
                if (activeStep != null && p.nodeId() == activeStep.nodeId())
                    practices.add(map("practiceRef", entry.getKey(), "artifactId", p.artifactId(),
                            "producerTaskId", p.producerTaskId(),
                            "toolCallId", p.toolCallId(), "localId", p.localId(), "prompt", p.prompt(),
                            "referenceAnswer", p.referenceAnswer(), "sourceIds", p.sourceIds(),
                            "mode", "informal_practice", "formalEligible", false));
            }
            data.put("learningPractices", practices);
            data.put("failedObservations", bundle.failedObservations());
            limits.addAll(bundle.limitations());
        } else {
            data.put("learningPractices", List.of()); data.put("failedObservations", List.of());
            limits.add("MATERIAL_HOST_UNAVAILABLE");
        }
        LearningReadRepository.Path after = learning.currentPath(scope);
        if (after.state() != LearningReadRepository.PathState.AVAILABLE
                || !after.pathId().equals(path.pathId())
                || !after.snapshotFingerprint().equals(path.snapshotFingerprint()))
            return ToolResult.error(scope, "STALE_PATH_BASELINE");
        return result(ToolStatus.PARTIAL, scope, data, List.of(AssessmentEvidence.path(path, scope)), limits);
    }

    private ToolResult legacy(AssistantTool.Context ctx, JsonNode args) {
        Scope scope = ctx.scope();
        LearningReadRepository.Path path = learning.currentPath(scope);
        if (path.state() != LearningReadRepository.PathState.AVAILABLE) return ToolResult.error(scope, "PATH_UNAVAILABLE");
        baseline(path, args, true);
        Long stepId = AssessmentJson.optionalId(args, "stepId");
        int limit = AssessmentJson.integer(args, "limit", 1, 20, 10);
        Long cursor = AssessmentJson.optionalId(args, "beforeAttemptId");
        var page = history.legacyAttempts(scope, path.pathId(), stepId, limit, cursor);
        if (page.rows().size() > limit) return ToolResult.error(scope, "HISTORY_PAGE_OVERFLOW");
        List<Map<String, Object>> rows = new ArrayList<>();
        List<EvidenceRef> evidence = new ArrayList<>();
        evidence.add(AssessmentEvidence.path(path, scope));
        List<String> limits = new ArrayList<>(List.of("CLIENT_SUBMITTED_TOTALS_UNVERIFIED",
                "ITEM_RESPONSES_UNAVAILABLE", "NO_ATOMIC_SNAPSHOT"));
        long previous = cursor == null ? Long.MAX_VALUE : cursor;
        for (var row : page.rows()) {
            if (row.pathId() != path.pathId() || row.attemptId() <= 0 || row.attemptId() >= previous)
                return ToolResult.error(scope, "HISTORY_ROW_MISMATCH");
            previous = row.attemptId();
            if (stepId != null && !stepId.equals(row.stepId())) return ToolResult.error(scope, "HISTORY_ROW_MISMATCH");
            if (row.stepId() != null && (row.actualStepPathId() == null
                    || row.actualStepPathId().longValue() != path.pathId().longValue()
                    || path.steps().stream().noneMatch(s -> s.stepId() == row.stepId())))
                return ToolResult.error(scope, "LEGACY_STEP_OWNERSHIP_MISMATCH");
            List<String> anomalies = new ArrayList<>();
            if (row.score() < 0 || row.score() > 100 || row.correctCount() < 0 || row.totalCount() < 0
                    || row.correctCount() > row.totalCount()) anomalies.add("INVALID_CLIENT_TOTALS");
            List<String> weak = new ArrayList<>();
            if (row.weakTopicsJson() != null) {
                if (row.weakTopicsJson().length() > 4000) anomalies.add("WEAK_TOPICS_TOO_LONG");
                else try {
                    JsonNode node = json.readTree(row.weakTopicsJson());
                    if (!node.isArray() || node.size() > 20) anomalies.add("INVALID_WEAK_TOPICS");
                    else for (JsonNode item : node) {
                        if (!item.isTextual() || item.textValue().isBlank() || item.textValue().length() > 200) {
                            anomalies.add("INVALID_WEAK_TOPICS"); weak.clear(); break;
                        }
                        weak.add(item.textValue());
                    }
                } catch (JsonProcessingException ex) { anomalies.add("INVALID_WEAK_TOPICS"); }
            }
            if (row.createdAt() == null) anomalies.add("MISSING_CREATED_AT");
            if (!anomalies.isEmpty()) limits.add("ANOMALOUS_LEGACY_RECORD");
            rows.add(map("attemptId", row.attemptId(), "pathId", row.pathId(), "stepId", row.stepId(),
                    "stageAttribution", row.stepId() == null ? "unknown" : "recorded_step",
                    "score", row.score(), "correctCount", row.correctCount(), "totalCount", row.totalCount(),
                    "legacyClientWeakTopics", weak, "createdAt", row.createdAt() == null ? null : row.createdAt().toString(),
                    "validity", "legacy_unverified", "anomalies", anomalies,
                    "canRecalculateFromItems", false, "formalEligible", false));
            evidence.add(AssessmentEvidence.legacyAttempt(row, scope));
        }
        LearningReadRepository.Path after = learning.currentPath(scope);
        if (after.state() != LearningReadRepository.PathState.AVAILABLE || after.pathId() == null
                || after.pathId().longValue() != path.pathId().longValue()
                || !after.snapshotFingerprint().equals(path.snapshotFingerprint()))
            return ToolResult.error(scope, "STALE_PATH_BASELINE");
        if (page.hasMore()) limits.add("HISTORY_WINDOW_TRUNCATED");
        if (cursor != null) limits.add("PRIOR_PAGES_NOT_INCLUDED");
        return result(limits.contains("ANOMALOUS_LEGACY_RECORD") || page.hasMore() || cursor != null
                ? ToolStatus.PARTIAL : ToolStatus.OK, scope,
                map("pathId", path.pathId(), "stepIdFilter", stepId, "records", rows,
                        "pageComplete", true, "hasMore", page.hasMore(),
                        "nextCursor", page.hasMore() && !page.rows().isEmpty()
                                ? page.rows().get(page.rows().size()-1).attemptId() : null,
                        "completeCoverage", cursor == null && !page.hasMore(),
                        "coverageAppliesTo", stepId == null ? "path" : "filtered_step",
                        "snapshotFingerprint", path.snapshotFingerprint(), "formalAvailable", false), evidence, limits);
    }

    private ToolResult valid(AssistantTool.Context ctx, JsonNode args) {
        Scope scope = ctx.scope();
        LearningReadRepository.Path path = learning.currentPath(scope);
        if (path.state() == LearningReadRepository.PathState.UNAVAILABLE)
            return ToolResult.error(scope, "PATH_UNAVAILABLE");
        if (args.has("pathId")) {
            if (path.state() != LearningReadRepository.PathState.AVAILABLE
                    || path.pathId() != args.get("pathId").longValue())
                return ToolResult.error(scope, "PATH_NOT_IN_SCOPE");
            if (args.has("stepId") && path.steps().stream()
                    .noneMatch(s -> s.stepId() == args.get("stepId").longValue()))
                return ToolResult.error(scope, "STEP_NOT_IN_PATH");
        }
        return result(ToolStatus.PARTIAL, scope, map("formalAvailable", false,
                "reason", "formal_scoring_unavailable", "validResults", List.of(),
                "weaknessItems", List.of(), "mastery", null, "validPassingAttemptId", null,
                "pathId", path.pathId(), "stepIdFilter", AssessmentJson.optionalId(args, "stepId")),
                List.of(), List.of("FORMAL_SCORING_UNAVAILABLE"));
    }

    private ToolResult draft(AssistantTool.Context ctx, JsonNode args) {
        var prepared = AssessmentDraftValidator.prepare(args, ctx.scope(), learning, research,
                sources, materials(ctx).orElse(null));
        return result(ToolStatus.PARTIAL, ctx.scope(), prepared.data(), prepared.evidence(), prepared.limitations());
    }

    private Optional<AssessmentMaterials.Bundle> materials(AssistantTool.Context ctx) {
        if (host == null) return Optional.empty();
        return host.snapshot(ctx.runId(), ctx.taskId()).map(s -> AssessmentMaterials.inspect(s, ctx.scope(), ctx.taskId()));
    }
    private static void baseline(LearningReadRepository.Path path, JsonNode args, boolean requirePath) {
        if (requirePath || args.has("pathId")) {
            if (path.pathId() != args.get("pathId").longValue())
                throw new IllegalArgumentException("STALE_PATH_BASELINE");
        }
        if (args.has("snapshotFingerprint") && !path.snapshotFingerprint().equals(args.get("snapshotFingerprint").textValue()))
            throw new IllegalArgumentException("STALE_PATH_BASELINE");
        if (args.has("stepId") && path.steps().stream()
                .noneMatch(s -> s.stepId() == args.get("stepId").longValue()))
            throw new IllegalArgumentException("STEP_NOT_IN_PATH");
    }
    private ToolResult result(ToolStatus status, Scope scope, Object data,
                              List<EvidenceRef> evidence, List<String> limitations) {
        try {
            String raw = json.writeValueAsString(data);
            if (raw.length() > 32000 || evidence.size() > 64) return ToolResult.error(scope, "ASSESSMENT_OUTPUT_BUDGET");
            return new ToolResult(status, scope, raw, evidence, List.copyOf(new LinkedHashSet<>(limitations)), null);
        } catch (JsonProcessingException ex) { return ToolResult.error(scope, "ASSESSMENT_SERIALIZATION_FAILURE"); }
    }
    private static Map<String, Object> map(Object... values) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int i = 0; i < values.length; i += 2) result.put((String) values[i], values[i+1]);
        return result;
    }
}
