package com.knowledgegraph.assistant.learning;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowledgegraph.assistant.contract.AssistantContracts.*;
import com.knowledgegraph.assistant.research.ResearchReadRepository;
import com.knowledgegraph.assistant.source.SourceLocator;
import com.knowledgegraph.assistant.tool.AssistantTool;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Learning-only, unregistered W1 tools. No legacy learning write service is reachable here. */
public final class LearningAgentToolProvider {
    private static final String EMPTY = """
            {"type":"object","properties":{},"required":[],"additionalProperties":false}
            """;
    private final LearningReadRepository repo;
    private final ResearchReadRepository research;
    private final SourceLocator sources;
    private final LearningContextReader host;
    private final ObjectMapper json = new ObjectMapper();

    public LearningAgentToolProvider(LearningReadRepository repo, ResearchReadRepository research,
                                     SourceLocator sources, LearningContextReader host) {
        this.repo = repo; this.research = research; this.sources = sources; this.host = host;
    }
    public List<AssistantTool> tools() {
        return List.of(
                tool("learning.read_current_path", ToolEffect.READ_ONLY, EMPTY, this::currentPath),
                tool("learning.read_graph_scope", ToolEffect.READ_ONLY,
                        """
                        {"type":"object","properties":{"maxNodes":{"type":"integer","minimum":1,"maximum":50},"maxEdges":{"type":"integer","minimum":1,"maximum":100}},"required":[],"additionalProperties":false}
                        """, this::graph),
                tool("learning.read_evidence", ToolEffect.READ_ONLY,
                        """
                        {"type":"object","properties":{"documentId":{"type":"integer","minimum":1},"chunkId":{"type":"integer","minimum":1},"expectedContentHash":{"type":"string","minLength":1,"maxLength":128},"maxChars":{"type":"integer","minimum":1,"maximum":4000}},"required":["documentId","chunkId","expectedContentHash"],"additionalProperties":false}
                        """, this::evidence),
                tool("learning.read_learning_inputs", ToolEffect.READ_ONLY, EMPTY, this::inputs),
                tool("learning.read_assessment_summary", ToolEffect.READ_ONLY, EMPTY, this::assessment),
                tool("learning.draft_path", ToolEffect.DRAFT_ONLY, """
                        {"type":"object","properties":{"targetLibraryId":{"type":"integer","minimum":1},"mode":{"enum":["new","adjust","replace"]},"goal":{"type":"string","minLength":1,"maxLength":500},"difficulty":{"enum":["beginner","intermediate","advanced","unknown"]},"days":{"type":"integer","minimum":1,"maximum":365},"minutesPerDay":{"type":"integer","minimum":1,"maximum":600},"basePathId":{"type":"integer","minimum":1},"baseSnapshotFingerprint":{"type":"string","minLength":64,"maxLength":64},"steps":{"type":"array","minItems":2,"maxItems":8,"items":{"type":"object","properties":{"localId":{"type":"string","pattern":"^draft:[A-Za-z0-9_-]{1,40}$"},"nodeId":{"type":"integer","minimum":1},"objective":{"type":"string","minLength":1,"maxLength":300},"reason":{"type":"string","minLength":1,"maxLength":500},"reasonKind":{"enum":["inference","graph_relation"]},"relationEdgeId":{"type":"integer","minimum":1},"estimatedMinutes":{"type":"integer","minimum":1,"maximum":240},"sourceIds":{"type":"array","maxItems":4,"items":{"type":"string","minLength":1,"maxLength":256}}},"required":["localId","nodeId","objective","reason","reasonKind","estimatedMinutes"],"additionalProperties":false}}},"required":["targetLibraryId","mode","goal","difficulty","steps"],"additionalProperties":false}
                        """, this::draftPath),
                tool("learning.draft_explanation", ToolEffect.DRAFT_ONLY, """
                        {"type":"object","properties":{"nodeId":{"type":"integer","minimum":1},"blocks":{"type":"array","minItems":1,"maxItems":8,"items":{"type":"object","properties":{"kind":{"enum":["source_fact","system_fact","graph_fact","model_explanation","recommendation"]},"text":{"type":"string","minLength":1,"maxLength":1000},"sourceIds":{"type":"array","maxItems":4,"items":{"type":"string","minLength":1,"maxLength":256}}},"required":["kind","text"],"additionalProperties":false}}},"required":["nodeId","blocks"],"additionalProperties":false}
                        """, this::draftExplanation),
                tool("learning.draft_practice", ToolEffect.DRAFT_ONLY, """
                        {"type":"object","properties":{"nodeId":{"type":"integer","minimum":1},"items":{"type":"array","minItems":1,"maxItems":5,"items":{"type":"object","properties":{"localId":{"type":"string","pattern":"^draft:[A-Za-z0-9_-]{1,40}$"},"prompt":{"type":"string","minLength":1,"maxLength":800},"answer":{"type":"string","minLength":1,"maxLength":800},"explanation":{"type":"string","minLength":1,"maxLength":1000},"hints":{"type":"array","maxItems":3,"items":{"type":"string","minLength":1,"maxLength":300}},"sourceIds":{"type":"array","maxItems":4,"items":{"type":"string","minLength":1,"maxLength":256}}},"required":["localId","prompt","answer","explanation"],"additionalProperties":false}}},"required":["nodeId","items"],"additionalProperties":false}
                        """, this::draftPractice));
    }
    private interface Body { ToolResult run(AssistantTool.Context context, JsonNode args); }
    private AssistantTool tool(String name, ToolEffect effect, String schema, Body body) {
        ToolDescriptor descriptor = new ToolDescriptor(name, Set.of(Role.LEARNING_COACH), effect,
                "Scoped learning facts or an unsaved, non-assessment draft", schema.strip());
        return new AssistantTool() {
            public ToolDescriptor descriptor() { return descriptor; }
            public void validateArguments(JsonNode args, Scope scope) { validate(name, args, scope); }
            public ToolResult execute(Context context, JsonNode args) {
                try {
                    validate(name, args, context.scope());
                    if (context.cancellationRequested().getAsBoolean()) return ToolResult.error(context.scope(), "CANCELLED");
                    if (java.time.Instant.now().isAfter(context.deadline())) return ToolResult.error(context.scope(), "DEADLINE_EXCEEDED");
                    return body.run(context, args);
                } catch (IllegalArgumentException ex) { return ToolResult.error(context.scope(), code(ex)); }
            }
        };
    }
    private static String code(IllegalArgumentException ex) {
        String message = ex.getMessage();
        return message != null && message.matches("[A-Z][A-Z0-9_]{1,90}") ? message : "INVALID_LEARNING_INPUT";
    }
    private static void validate(String name, JsonNode args, Scope scope) {
        switch (name) {
            case "learning.read_current_path", "learning.read_learning_inputs", "learning.read_assessment_summary" -> LearningJson.fields(args);
            case "learning.read_graph_scope" -> {
                LearningJson.fields(args, "maxNodes", "maxEdges");
                LearningJson.integer(args, "maxNodes", 1, 50, 20);
                LearningJson.integer(args, "maxEdges", 1, 100, 40);
            }
            case "learning.read_evidence" -> {
                LearningJson.fields(args, "documentId", "chunkId", "expectedContentHash", "maxChars");
                LearningJson.id(args, "documentId"); LearningJson.id(args, "chunkId");
                LearningJson.text(args, "expectedContentHash", 1, 128);
                LearningJson.integer(args, "maxChars", 1, 4000, 4000);
                if (!scope.nodeIds().isEmpty()) throw new IllegalArgumentException("NODE_SCOPE_UNSUPPORTED");
            }
            case "learning.draft_path" -> LearningDraftValidator.pathShape(args);
            case "learning.draft_explanation" -> LearningDraftValidator.explanationShape(args);
            case "learning.draft_practice" -> LearningDraftValidator.practiceShape(args);
            default -> throw new IllegalArgumentException("UNKNOWN_LEARNING_TOOL");
        }
    }
    private ToolResult currentPath(AssistantTool.Context ctx, JsonNode args) {
        LearningReadRepository.Path path = repo.currentPath(ctx.scope());
        if (path.state() == LearningReadRepository.PathState.UNAVAILABLE)
            return result(ToolStatus.PARTIAL, ctx.scope(), map("state", "unavailable", "reason", "PATH_SCOPE_OR_SHAPE_INSUFFICIENT"),
                    List.of(), List.of("PATH_SCOPE_OR_SHAPE_INSUFFICIENT"));
        if (path.state() == LearningReadRepository.PathState.NONE)
            return result(ToolStatus.OK, ctx.scope(), map("state", "none", "profile", "local-default", "formalRevision", null), List.of(), List.of());
        List<Map<String, Object>> steps = new ArrayList<>();
        for (var step : path.steps()) steps.add(map("stepId", step.stepId(), "stepKey", step.stepKey(),
                "nodeId", step.nodeId(), "position", step.position(), "title", step.title(),
                "estimatedMinutes", step.estimatedMinutes(), "status", step.status(),
                "visitedAt", step.visitedAt(), "legacyAssessmentScore", step.legacyAssessmentScore(),
                "scoreValidity", "unverified_legacy_record", "updatedAt", step.updatedAt()));
        return result(ToolStatus.OK, ctx.scope(), map("state", "available", "profile", "local-default",
                "pathId", path.pathId(), "libraryId", path.libraryId(), "goal", path.goal(),
                "difficulty", path.difficulty(), "status", path.status(), "updatedAt", path.updatedAt(),
                "steps", steps, "snapshotFingerprint", path.snapshotFingerprint(),
                "fingerprintKind", "snapshot_not_write_lock", "formalRevision", null,
                "formatVersion", 2), List.of(), List.of("FORMAL_ASSESSMENT_UNAVAILABLE"));
    }
    private ToolResult graph(AssistantTool.Context ctx, JsonNode args) {
        int maxNodes = LearningJson.integer(args, "maxNodes", 1, 50, 20);
        int maxEdges = LearningJson.integer(args, "maxEdges", 1, 100, 40);
        var graph = repo.graph(ctx.scope(), maxNodes, maxEdges);
        List<Map<String, Object>> nodes = new ArrayList<>();
        List<EvidenceRef> refs = new ArrayList<>();
        for (var n : graph.nodes()) {
            nodes.add(map("nodeId", n.id(), "name", n.name(), "type", n.type(),
                    "definition", n.definition(), "status", n.status(), "origin", n.origin(),
                    "reviewStatus", n.reviewStatus(), "libraryIds", n.libraryIds(),
                    "sourceEvidenceAvailable", n.sourceEvidenceAvailable()));
            refs.add(LearningEvidence.node(n, ctx.scope()));
        }
        List<Map<String, Object>> edges = new ArrayList<>();
        for (var e : graph.edges()) edges.add(map("edgeId", e.id(), "sourceNodeId", e.sourceNodeId(),
                "targetNodeId", e.targetNodeId(), "relationType", e.relationType(), "status", e.status(),
                "origin", e.origin(), "reviewStatus", e.reviewStatus(),
                "sourceEvidenceAvailable", e.sourceEvidenceAvailable(), "prerequisiteSemantics", "not_established"));
        List<String> limits = new ArrayList<>();
        limits.add("VISIBLE_WINDOW_NOT_FULL_CORPUS");
        if (graph.nodesTruncated()) limits.add("NODE_WINDOW_TRUNCATED");
        if (graph.edgesTruncated()) limits.add("EDGE_WINDOW_TRUNCATED");
        for (var edge : graph.edges()) {
            if (refs.size() == 64) break;
            refs.add(LearningEvidence.edge(edge, ctx.scope()));
        }
        if (refs.size() < graph.nodes().size() + graph.edges().size())
            limits.add("EVIDENCE_REF_WINDOW_TRUNCATED");
        return result(graph.nodesTruncated() || graph.edgesTruncated() ? ToolStatus.PARTIAL : ToolStatus.OK,
                ctx.scope(), map("nodes", nodes, "edges", edges, "maxNodes", maxNodes,
                        "maxEdges", maxEdges, "nodesTruncated", graph.nodesTruncated(),
                        "edgesTruncated", graph.edgesTruncated(), "completeCoverage", false), refs, limits);
    }
    private ToolResult evidence(AssistantTool.Context ctx, JsonNode args) {
        var bundle = materials(ctx).orElse(null);
        if (bundle == null) return ToolResult.error(ctx.scope(), "MATERIAL_HOST_UNAVAILABLE");
        long doc = LearningJson.id(args, "documentId"), chunk = LearningJson.id(args, "chunkId");
        String hash = args.get("expectedContentHash").textValue();
        LearningMaterials.Pointer pointer = bundle.pointers().values().stream()
                .filter(p -> p.documentId() == doc && p.chunkId() == chunk && p.hash().equals(hash))
                .findFirst().orElse(null);
        if (pointer == null) return ToolResult.error(ctx.scope(), "UNTRUSTED_SOURCE_POINTER");
        var before = research.sourceMeta(ctx.scope(), doc, chunk);
        if (before.isEmpty() || !before.get().formalAvailable() || !hash.equals(before.get().contentHash()))
            return ToolResult.error(ctx.scope(), "STALE_OR_UNAVAILABLE_SOURCE");
        int maxChars = LearningJson.integer(args, "maxChars", 1, 4000, 4000);
        var body = sources.chunk(ctx.scope(), doc, chunk, maxChars);
        var after = research.sourceMeta(ctx.scope(), doc, chunk);
        if (body.isEmpty() || after.isEmpty() || !after.get().formalAvailable()
                || !hash.equals(body.get().contentHash()) || !hash.equals(after.get().contentHash())
                || body.get().unitId() != after.get().unitId())
            return ToolResult.error(ctx.scope(), "STALE_OR_UNAVAILABLE_SOURCE");
        EvidenceRef ref = LearningEvidence.source(after.get(), body.get().content(), ctx.scope());
        return result(body.get().contentTruncated() ? ToolStatus.PARTIAL : ToolStatus.OK, ctx.scope(),
                map("documentId", doc, "chunkId", chunk, "contentHash", hash,
                        "content", body.get().content(), "sourceId", ref.sourceId(),
                        "bodyTruncated", body.get().contentTruncated(), "formalOnly", true,
                        "semanticSupport", "not_evaluated"), List.of(ref),
                body.get().contentTruncated() ? List.of("SOURCE_BODY_TRUNCATED") : List.of());
    }
    private ToolResult inputs(AssistantTool.Context ctx, JsonNode args) {
        var bundle = materials(ctx);
        if (bundle.isEmpty()) return result(ToolStatus.PARTIAL, ctx.scope(),
                map("state", "unavailable", "inputs", List.of(), "qualityVerified", false),
                List.of(), List.of("MATERIAL_HOST_UNAVAILABLE"));
        return result(bundle.get().limitations().isEmpty() ? ToolStatus.OK : ToolStatus.PARTIAL,
                ctx.scope(), map("state", "available", "inputs", bundle.get().inputs(),
                        "qualityChecks", bundle.get().qualityChecks(),
                        "failedObservations", bundle.get().failedObservations(),
                        "qualityObservationAvailable", !bundle.get().limitations().contains("QUALITY_OBSERVATION_UNAVAILABLE"),
                        "independentSourceCount", bundle.get().independentSources(),
                        "semanticSupport", "not_evaluated"), List.of(), bundle.get().limitations());
    }
    private ToolResult assessment(AssistantTool.Context ctx, JsonNode args) {
        return result(ToolStatus.PARTIAL, ctx.scope(), map("formalAvailable", false,
                "validResults", List.of(), "formalWeakTopics", List.of(), "mastery", null,
                "reason", "ASSESSMENT_ROLE_NOT_CONNECTED"), List.of(),
                List.of("FORMAL_ASSESSMENT_UNAVAILABLE"));
    }
    private ToolResult draftPath(AssistantTool.Context ctx, JsonNode args) {
        return draft(ctx, LearningDraftValidator.path(args, ctx.scope(), repo, research, sources, materials(ctx).orElse(null)));
    }
    private ToolResult draftExplanation(AssistantTool.Context ctx, JsonNode args) {
        return draft(ctx, LearningDraftValidator.explanation(args, ctx.scope(), repo, research, sources, materials(ctx).orElse(null)));
    }
    private ToolResult draftPractice(AssistantTool.Context ctx, JsonNode args) {
        return draft(ctx, LearningDraftValidator.practice(args, ctx.scope(), repo, research, sources, materials(ctx).orElse(null)));
    }
    private ToolResult draft(AssistantTool.Context ctx, LearningDraftValidator.Draft draft) {
        return result(ToolStatus.OK, ctx.scope(), draft.data(), draft.evidence(), draft.limitations());
    }
    private Optional<LearningMaterials.Bundle> materials(AssistantTool.Context ctx) {
        return host.snapshot(ctx.runId(), ctx.taskId()).map(snapshot -> LearningMaterials.inspect(snapshot, ctx.scope()));
    }
    private ToolResult result(ToolStatus status, Scope scope, Object data,
                              List<EvidenceRef> evidence, List<String> limitations) {
        try {
            String raw = json.writeValueAsString(data);
            if (raw.length() > 32000) return ToolResult.error(scope, "LEARNING_OUTPUT_BUDGET");
            List<EvidenceRef> limited = evidence.size() > 64 ? evidence.subList(0, 64) : evidence;
            return new ToolResult(status, scope, raw, limited, limitations, null);
        } catch (JsonProcessingException ex) { return ToolResult.error(scope, "LEARNING_SERIALIZATION_FAILURE"); }
    }
    private static Map<String, Object> map(Object... values) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int i = 0; i < values.length; i += 2) result.put((String) values[i], values[i + 1]);
        return result;
    }
}
