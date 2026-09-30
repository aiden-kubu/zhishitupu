package com.knowledgegraph.assistant.quality;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowledgegraph.assistant.contract.AssistantContracts;
import com.knowledgegraph.assistant.contract.AssistantContracts.*;
import com.knowledgegraph.assistant.tool.AssistantTool;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Four deterministic, read-only quality tools. No Spring/production registration is performed here. */
public final class QualityAgentToolProvider {
    private static final int MAX_PAGE = 1000;
    private static final int MAX_PAGE_SIZE = 20;
    private static final int MAX_REFS = 10;
    private static final int MAX_CLAIMS = 10;
    private final QualityReadRepository repository;
    private final QualityEvidenceVerifier verifier;
    private final QualityArtifactReader artifacts;
    private final ObjectMapper mapper;
    private final Clock clock;

    public QualityAgentToolProvider(QualityReadRepository repository, QualityEvidenceVerifier verifier,
                                    QualityArtifactReader artifacts, ObjectMapper mapper, Clock clock) {
        this.repository = repository;
        this.verifier = verifier;
        this.artifacts = artifacts;
        this.mapper = mapper;
        this.clock = clock;
    }

    public List<AssistantTool> tools() {
        return List.of(tool("quality.get_review_snapshot", "Read one job and a bounded candidate page", REVIEW_SCHEMA),
                tool("quality.read_candidate_sources", "Re-read candidate source chunks and compare hashes", SOURCE_SCHEMA),
                tool("quality.get_insight_snapshot", "Read scoped graph and pending-review statistics", INSIGHT_SCHEMA),
                tool("quality.verify_artifact_claims", "Check current direct-dependency claim references", CLAIM_SCHEMA));
    }

    private AssistantTool tool(String name, String description, String schema) {
        return new AssistantTool() {
            private final ToolDescriptor descriptor = new ToolDescriptor(name, Set.of(Role.QUALITY_GOVERNANCE),
                    ToolEffect.READ_ONLY, description, schema);
            @Override public ToolDescriptor descriptor() { return descriptor; }
            @Override public void validateArguments(JsonNode args, Scope scope) { validate(name, args, scope); }
            @Override public ToolResult execute(Context context, JsonNode args) {
                validate(name, args, context.scope());
                return switch (name) {
                    case "quality.get_review_snapshot" -> review(context, args);
                    case "quality.read_candidate_sources" -> sources(context, args);
                    case "quality.get_insight_snapshot" -> insight(context, args);
                    case "quality.verify_artifact_claims" -> claims(context, args);
                    default -> throw new IllegalStateException("Unknown quality tool");
                };
            }
        };
    }

    private void validate(String name, JsonNode args, Scope scope) {
        if (args == null || !args.isObject()) throw new IllegalArgumentException("OBJECT_REQUIRED");
        switch (name) {
            case "quality.get_review_snapshot" -> {
                only(args, "jobId", "candidateKind", "page", "pageSize");
                positive(args, "jobId"); kind(args); bounded(args, "page", MAX_PAGE); bounded(args, "pageSize", MAX_PAGE_SIZE);
                noNodeScope(scope);
            }
            case "quality.read_candidate_sources" -> {
                only(args, "jobId", "candidateKind", "candidateId", "expectedRefs", "maxChars");
                positive(args, "jobId"); positive(args, "candidateId"); kind(args);
                bounded(args, "maxChars", 1200);
                if (args.path("maxChars").intValue() < 200) throw new IllegalArgumentException("MAX_CHARS_TOO_SMALL");
                JsonNode refs = args.path("expectedRefs");
                if (!refs.isArray() || refs.isEmpty() || refs.size() > MAX_REFS) throw new IllegalArgumentException("INVALID_REFS");
                Set<Long> ids = new HashSet<>();
                for (JsonNode ref : refs) {
                    only(ref, "chunkId", "expectedHash");
                    long id = positive(ref, "chunkId");
                    if (!ids.add(id)) throw new IllegalArgumentException("DUPLICATE_REF");
                    hash(ref, "expectedHash");
                }
                noNodeScope(scope);
            }
            case "quality.get_insight_snapshot" -> {
                only(args, "isolatedLimit", "topLimit");
                bounded(args, "isolatedLimit", 100); bounded(args, "topLimit", 10);
                if (!scope.documentIds().isEmpty() || !scope.nodeIds().isEmpty()
                        || (scope.mode() == ScopeMode.SELECTED && scope.libraryIds().isEmpty())) {
                    throw new IllegalArgumentException("UNSUPPORTED_SCOPE");
                }
            }
            case "quality.verify_artifact_claims" -> {
                only(args, "artifactId", "claimIds");
                identifier(args, "artifactId");
                JsonNode ids = args.path("claimIds");
                if (!ids.isArray() || ids.isEmpty() || ids.size() > MAX_CLAIMS) throw new IllegalArgumentException("INVALID_CLAIMS");
                Set<String> unique = new HashSet<>();
                for (JsonNode id : ids) {
                    if (!id.isTextual() || !unique.add(AssistantContracts.identifier(id.textValue())))
                        throw new IllegalArgumentException("INVALID_CLAIM_ID");
                }
                noNodeScope(scope);
            }
            default -> throw new IllegalArgumentException("UNKNOWN_TOOL");
        }
    }

    private static void noNodeScope(Scope scope) {
        if (!scope.nodeIds().isEmpty()) throw new IllegalArgumentException("NODE_SCOPE_UNSUPPORTED");
    }
    private static void only(JsonNode value, String... allowed) {
        if (value == null || !value.isObject()) throw new IllegalArgumentException("OBJECT_REQUIRED");
        Set<String> names = Set.of(allowed);
        value.fieldNames().forEachRemaining(key -> { if (!names.contains(key)) throw new IllegalArgumentException("EXTRA_FIELD"); });
        for (String key : allowed) if (!value.has(key)) throw new IllegalArgumentException("MISSING_FIELD");
    }
    private static long positive(JsonNode args, String key) {
        JsonNode n = args.path(key);
        if (!n.isIntegralNumber() || !n.canConvertToLong() || n.longValue() <= 0)
            throw new IllegalArgumentException("INVALID_ID");
        return n.longValue();
    }
    private static int bounded(JsonNode args, String key, int max) {
        JsonNode n = args.path(key);
        if (!n.isInt() || n.intValue() < 1 || n.intValue() > max) throw new IllegalArgumentException("INVALID_BOUND");
        return n.intValue();
    }
    private static String kind(JsonNode args) {
        String value = args.path("candidateKind").asText("");
        if (!Set.of("entity", "relation").contains(value)) throw new IllegalArgumentException("INVALID_KIND");
        return value;
    }
    private static String hash(JsonNode args, String key) {
        JsonNode n = args.path(key);
        if (!n.isTextual() || !n.textValue().matches("(?i)[0-9a-f]{64}"))
            throw new IllegalArgumentException("INVALID_HASH");
        return n.textValue();
    }
    private static String identifier(JsonNode args, String key) {
        JsonNode n = args.path(key);
        if (!n.isTextual()) throw new IllegalArgumentException("INVALID_IDENTIFIER");
        return AssistantContracts.identifier(n.textValue());
    }

    private ToolResult review(AssistantTool.Context context, JsonNode args) {
        Scope scope = context.scope();
        long jobId = positive(args, "jobId");
        Optional<QualityReadRepository.Job> found = repository.job(scope, jobId);
        if (found.isEmpty()) return ToolResult.error(scope, "JOB_UNAVAILABLE");
        QualityReadRepository.Job job = found.get();
        QualityReadRepository.Page<QualityReadRepository.Candidate> page = repository.candidates(scope, jobId,
                kind(args), bounded(args, "page", MAX_PAGE), bounded(args, "pageSize", MAX_PAGE_SIZE));
        List<Map<String, Object>> items = new ArrayList<>();
        for (QualityReadRepository.Candidate candidate : page.items()) items.add(candidateView(scope, job, candidate));
        boolean evidenceIncomplete = items.stream().anyMatch(item -> Boolean.TRUE.equals(item.get("evidenceMissing"))
                || Boolean.TRUE.equals(item.get("evidenceIdsExcluded"))
                || Boolean.TRUE.equals(item.get("evidenceIdsTruncated")));
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("job", Map.of("id", job.id(), "documentId", job.documentId(), "stage", job.stage(),
                "status", job.status(), "documentStatus", job.documentStatus()));
        data.put("candidateKind", kind(args)); data.put("items", items);
        data.put("total", page.total()); data.put("page", page.page()); data.put("pageSize", page.pageSize());
        data.put("hasMore", page.hasMore()); data.put("completeCoverage", page.completeCoverage());
        data.put("truncated", !page.completeCoverage() || items.stream()
                .anyMatch(item -> Boolean.TRUE.equals(item.get("evidenceIdsTruncated"))));
        data.put("evidenceIncomplete", evidenceIncomplete);
        data.put("sort", "candidate_id_asc");
        data.put("observedAt", clock.instant().toString()); data.put("effectiveScope", scope);
        long entityCount = kind(args).equals("entity") ? page.total()
                : repository.candidates(scope, jobId, "entity", 1, 1).total();
        data.put("autoImportPossible", entityCount > 0
                && Set.of("AWAITING_REVIEW", "FAILED", "CANCELLED").contains(job.status()));
        data.put("autoImportRequiresModel", true);
        data.put("actualImport", Map.of("jobCompleted", "COMPLETED".equals(job.status()),
                "perCandidateOutcome", "unknown", "outcomeIncomplete", true));
        data.put("recommendationOnly", true); data.put("executionAllowed", false);
        List<String> limitations = new ArrayList<>();
        limitations.add("NO_PER_CANDIDATE_IMPORT_RECEIPT");
        if (!page.completeCoverage()) limitations.add("CANDIDATE_PAGE_INCOMPLETE");
        if (evidenceIncomplete) limitations.add("CANDIDATE_EVIDENCE_INCOMPLETE");
        return result(page.completeCoverage() && !evidenceIncomplete ? ToolStatus.OK : ToolStatus.PARTIAL,
                scope, data, List.of(), limitations, null);
    }

    private Map<String, Object> candidateView(Scope scope, QualityReadRepository.Job job,
                                               QualityReadRepository.Candidate c) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("id", c.id()); value.put("kind", c.kind()); value.put("reviewStatus", c.reviewStatus());
        value.put("name", shortText(c.name(), 200)); value.put("sourceTempKey", shortText(c.sourceTempKey(), 50));
        value.put("targetTempKey", shortText(c.targetTempKey(), 50));
        value.put("relationType", shortText(c.relationType(), 100));
        if (c.kind().equals("relation")) {
            value.put("sourceName", repository.entityByTempKey(scope, job.id(), c.sourceTempKey())
                    .map(source -> shortText(source.name(), 200)).orElse(""));
            value.put("targetName", repository.entityByTempKey(scope, job.id(), c.targetTempKey())
                    .map(target -> shortText(target.name(), 200)).orElse(""));
        }
        List<Map<String, Object>> evidenceRefs = new ArrayList<>();
        for (long id : c.evidenceChunkIds().stream().limit(20).toList()) {
            repository.chunkHash(scope, job.documentId(), id)
                    .filter(hash -> hash.matches("(?i)[0-9a-f]{64}"))
                    .ifPresent(hash -> evidenceRefs.add(Map.of("chunkId", id, "expectedHash", hash)));
        }
        List<Long> visibleEvidenceIds = evidenceRefs.stream()
                .map(ref -> (Long) ref.get("chunkId")).toList();
        value.put("evidenceChunkIds", visibleEvidenceIds);
        value.put("evidenceRefs", evidenceRefs);
        value.put("evidenceIdsTruncated", c.evidenceChunkIds().size() > 20);
        value.put("evidenceIdsExcluded", c.evidenceChunkIds().size() > visibleEvidenceIds.size());
        value.put("evidenceMissing", c.evidenceChunkIds().isEmpty());
        value.put("aiDecision", c.aiDecision() == null ? Map.of("status", "unavailable") :
                Map.of("status", "available", "approved", c.aiDecision().approved(),
                        "reason", shortText(c.aiDecision().reason(), 200), "model", shortText(c.aiDecision().model(), 100),
                        "reviewedAt", shortText(c.aiDecision().reviewedAt(), 80)));
        value.put("humanDecision", Map.of("status", "unavailable"));
        value.put("importState", Map.of("jobCompleted", "COMPLETED".equals(job.status()),
                "candidateOutcome", "unknown", "outcomeIncomplete", true));
        value.put("matchedObject", match(scope, job.id(), c));
        return value;
    }

    private Map<String, Object> match(Scope scope, long jobId, QualityReadRepository.Candidate c) {
        if (c.kind().equals("entity")) {
            if (c.matchedNodeId() == null) return Map.of("status", "not_recorded");
            return repository.nodeMatch(scope, c.matchedNodeId())
                    .<Map<String, Object>>map(n -> Map.of("status", "visible_match", "nodeId", n.id(),
                            "name", shortText(n.name(), 200), "nodeStatus", n.status()))
                    .orElse(Map.of("status", "unavailable_or_out_of_scope"));
        }
        if (c.matchedEdgeId() == null) return Map.of("status", "not_recorded");
        Optional<QualityReadRepository.EdgeMatch> edge = repository.edgeMatch(scope, c.matchedEdgeId());
        if (edge.isEmpty()) return Map.of("status", "unavailable_or_out_of_scope");
        Optional<QualityReadRepository.Candidate> source = repository.entityByTempKey(scope, jobId, c.sourceTempKey());
        Optional<QualityReadRepository.Candidate> target = repository.entityByTempKey(scope, jobId, c.targetTempKey());
        if (source.isEmpty() || target.isEmpty() || source.get().matchedNodeId() == null
                || target.get().matchedNodeId() == null) return Map.of("status", "endpoints_unverified");
        if (repository.nodeMatch(scope, source.get().matchedNodeId()).isEmpty()
                || repository.nodeMatch(scope, target.get().matchedNodeId()).isEmpty())
            return Map.of("status", "endpoints_unavailable_or_out_of_scope");
        QualityReadRepository.EdgeMatch e = edge.get();
        if (e.sourceNodeId() != source.get().matchedNodeId() || e.targetNodeId() != target.get().matchedNodeId()
                || !e.relationType().equals(c.relationType())) return Map.of("status", "direction_or_type_mismatch");
        return Map.of("status", "active".equals(e.status()) ? "visible_match" : "inactive_match",
                "edgeId", e.id(), "edgeStatus", e.status());
    }

    private ToolResult sources(AssistantTool.Context context, JsonNode args) {
        Scope scope = context.scope();
        long jobId = positive(args, "jobId");
        Optional<QualityReadRepository.Job> job = repository.job(scope, jobId);
        if (job.isEmpty()) return ToolResult.error(scope, "JOB_UNAVAILABLE");
        Optional<QualityReadRepository.Candidate> candidate = repository.candidate(scope, jobId, kind(args),
                positive(args, "candidateId"));
        if (candidate.isEmpty()) return ToolResult.error(scope, "CANDIDATE_UNAVAILABLE");
        List<Map<String, Object>> checks = new ArrayList<>();
        List<EvidenceRef> evidence = new ArrayList<>();
        int current = 0;
        boolean incomplete = false;
        Set<Long> candidateEvidenceIds = new HashSet<>(candidate.get().evidenceChunkIds());
        Set<Long> checkedCandidateIds = new HashSet<>();
        for (JsonNode ref : args.path("expectedRefs")) {
            long chunkId = positive(ref, "chunkId");
            String expected = hash(ref, "expectedHash");
            if (!candidateEvidenceIds.contains(chunkId)) {
                checks.add(Map.of("chunkId", chunkId, "status", "NOT_CANDIDATE_EVIDENCE"));
                incomplete = true;
                continue;
            }
            checkedCandidateIds.add(chunkId);
            String sourceId = "quality:chunk:" + chunkId + ":" + expected.toLowerCase();
            QualityEvidenceVerifier.SourceCheck checked = verifier.check(scope, job.get().documentId(), chunkId,
                    expected, sourceId, bounded(args, "maxChars", 1200));
            checks.add(sourceView(checked, true));
            if (checked.evidence() != null) evidence.add(checked.evidence());
            if (checked.status().equals("CURRENT")) current++; else incomplete = true;
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("jobId", jobId); data.put("candidateKind", kind(args));
        data.put("candidateId", candidate.get().id()); data.put("documentId", job.get().documentId());
        int unchecked = candidateEvidenceIds.size() - checkedCandidateIds.size();
        boolean requestComplete = !incomplete;
        boolean candidateComplete = requestComplete && unchecked == 0;
        data.put("checks", checks); data.put("requestedCount", args.path("expectedRefs").size());
        data.put("checkedCount", checks.size()); data.put("currentCount", current);
        data.put("candidateEvidenceTotal", candidateEvidenceIds.size());
        data.put("candidateEvidenceChecked", checkedCandidateIds.size());
        data.put("candidateEvidenceUnchecked", unchecked);
        data.put("requestCompleteCoverage", requestComplete);
        data.put("candidateEvidenceCompleteCoverage", candidateComplete);
        data.put("completeCoverage", candidateComplete);
        data.put("semanticSupport", "not_evaluated"); data.put("observedAt", clock.instant().toString());
        ToolStatus status = evidence.isEmpty() ? ToolStatus.ERROR : candidateComplete ? ToolStatus.OK : ToolStatus.PARTIAL;
        List<String> limitations = new ArrayList<>();
        if (incomplete) limitations.add("SOURCE_CHECK_INCOMPLETE");
        if (unchecked > 0) limitations.add("CANDIDATE_EVIDENCE_UNCHECKED");
        return result(status, scope, data, evidence, limitations,
                status == ToolStatus.ERROR ? "SOURCE_UNAVAILABLE" : null);
    }

    private static Map<String, Object> sourceView(QualityEvidenceVerifier.SourceCheck check, boolean includeContent) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("chunkId", check.chunkId()); value.put("status", check.status());
        value.put("expectedHash", check.expectedHash()); value.put("actualHash", check.actualHash());
        value.put("unitIndex", check.unitIndex()); value.put("locator", check.locator());
        value.put("content", includeContent && check.verifiedIdentity() ? check.content() : null);
        value.put("truncated", check.truncated());
        return value;
    }

    private ToolResult insight(AssistantTool.Context context, JsonNode args) {
        Scope scope = context.scope();
        int isolatedLimit = bounded(args, "isolatedLimit", 100);
        int topLimit = bounded(args, "topLimit", 10);
        QualityReadRepository.Insight stats = repository.insight(scope, isolatedLimit, topLimit);
        boolean isolatedTruncated = stats.isolatedTotal() > stats.isolated().size();
        boolean topTruncated = stats.nodeCount() > stats.topDegree().size();
        boolean typeTruncated = stats.typeTotal() > stats.types().size();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("effectiveScope", scope); data.put("observedAt", clock.instant().toString());
        data.put("nodeCount", stats.nodeCount()); data.put("edgeCount", stats.edgeCount());
        data.put("documentCount", stats.documentCount()); data.put("pendingReviewCount", stats.pendingReviewCount());
        data.put("nodeTypeDistribution", Map.of("items", stats.types(), "total", stats.typeTotal(),
                "limit", 50, "sort", "count_desc_then_type", "truncated", typeTruncated,
                "completeCoverage", !typeTruncated));
        data.put("topDegree", Map.of("items", stats.topDegree(), "total", stats.nodeCount(), "limit", topLimit,
                "sort", "active_edge_degree_desc_then_node_id_asc", "truncated", topTruncated,
                "completeCoverage", !topTruncated));
        data.put("isolated", Map.of("items", stats.isolated(), "total", stats.isolatedTotal(), "limit", isolatedLimit,
                "sort", "node_id_asc", "truncated", isolatedTruncated,
                "completeCoverage", !isolatedTruncated));
        data.put("countDefinitions", Map.of("nodes", "all statuses in visible node set",
                "edges", "active directed edges whose endpoints are both visible",
                "isolated", "visible nodes with no active edge to another visible node",
                "documents", "primary library or topic assignment in selected libraries",
                "pendingReview", "PENDING candidates in existing document jobs with resumable statuses"));
        data.put("trendAvailable", false); data.put("consistentTransactionSnapshot", false);
        return result(isolatedTruncated || topTruncated || typeTruncated ? ToolStatus.PARTIAL : ToolStatus.OK, scope, data,
                List.of(), isolatedTruncated || topTruncated || typeTruncated ? List.of("LIST_COVERAGE_INCOMPLETE", "NO_HISTORY")
                        : List.of("NO_HISTORY"), null);
    }

    private ToolResult claims(AssistantTool.Context context, JsonNode args) {
        Scope scope = context.scope();
        String artifactId = identifier(args, "artifactId");
        Optional<QualityArtifactReader.DirectArtifact> found = artifacts.directArtifact(context.runId(),
                context.taskId(), artifactId);
        if (found.isEmpty() || !artifactId.equals(found.get().artifactId()))
            return ToolResult.error(scope, "DIRECT_ARTIFACT_UNAVAILABLE");
        Map<String, QualityArtifactReader.Claim> byId = new LinkedHashMap<>();
        for (QualityArtifactReader.Claim claim : found.get().claims()) {
            if (claim.claimId() == null || byId.putIfAbsent(claim.claimId(), claim) != null)
                return ToolResult.error(scope, "DIRECT_ARTIFACT_INVALID");
        }
        List<Map<String, Object>> checks = new ArrayList<>();
        Map<String, EvidenceRef> evidence = new LinkedHashMap<>();
        boolean incomplete = false;
        int currentClaims = 0;
        int totalRefs = 0;
        for (JsonNode requested : args.path("claimIds")) {
            String claimId = requested.textValue();
            QualityArtifactReader.Claim claim = byId.get(claimId);
            if (claim == null) {
                checks.add(Map.of("claimId", claimId, "referenceStatus", "UNKNOWN_CLAIM",
                        "semanticSupport", "not_evaluated")); incomplete = true; continue;
            }
            List<Map<String, Object>> refs = new ArrayList<>();
            int current = 0;
            if (claim.sources().isEmpty() || claim.sources().size() > 2 || totalRefs + claim.sources().size() > 20) {
                checks.add(Map.of("claimId", claimId, "referenceStatus", "INVALID_SOURCE_COUNT",
                        "semanticSupport", "not_evaluated")); incomplete = true; continue;
            }
            totalRefs += claim.sources().size();
            for (QualityArtifactReader.SourcePointer pointer : claim.sources()) {
                QualityEvidenceVerifier.SourceCheck checked;
                try {
                    if (pointer.documentId() <= 0 || pointer.chunkId() <= 0
                            || pointer.sourceId() == null || pointer.sourceId().length() > 256
                            || pointer.expectedHash() == null || !pointer.expectedHash().matches("(?i)[0-9a-f]{64}"))
                        throw new IllegalArgumentException("INVALID_POINTER");
                    checked = verifier.check(scope, pointer.documentId(), pointer.chunkId(), pointer.expectedHash(),
                            verifiedSourceId(pointer), 600);
                } catch (IllegalArgumentException ex) {
                    refs.add(Map.of("status", "INVALID_POINTER")); incomplete = true; continue;
                }
                Map<String, Object> refView = sourceView(checked, false);
                refView.put("originalSourceId", pointer.sourceId());
                refs.add(refView);
                if (checked.evidence() != null) evidence.putIfAbsent(checked.evidence().sourceId(), checked.evidence());
                if (checked.status().equals("CURRENT")) current++; else incomplete = true;
            }
            boolean allCurrent = current == claim.sources().size();
            if (allCurrent) currentClaims++;
            checks.add(Map.of("claimId", claimId, "referenceStatus", allCurrent ? "CURRENT" : "UNVERIFIED",
                    "semanticSupport", "not_evaluated", "sources", refs));
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("artifactId", artifactId); data.put("producerTaskId", found.get().producerTaskId());
        data.put("checks", checks); data.put("currentClaims", currentClaims);
        data.put("requestedClaims", args.path("claimIds").size()); data.put("completeCoverage", !incomplete);
        data.put("semanticSupport", "not_evaluated"); data.put("observedAt", clock.instant().toString());
        ToolStatus status = evidence.isEmpty() ? ToolStatus.ERROR : incomplete ? ToolStatus.PARTIAL : ToolStatus.OK;
        return result(status, scope, data, List.copyOf(evidence.values()),
                incomplete ? List.of("CLAIM_REFERENCES_INCOMPLETE") : List.of(),
                status == ToolStatus.ERROR ? "CLAIMS_UNVERIFIED" : null);
    }

    private static String verifiedSourceId(QualityArtifactReader.SourcePointer pointer) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String identity = pointer.sourceId() + "\n" + pointer.documentId() + "\n"
                    + pointer.chunkId() + "\n" + pointer.expectedHash().toLowerCase();
            return "quality:verified:" + java.util.HexFormat.of().formatHex(
                    digest.digest(identity.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }

    private ToolResult result(ToolStatus status, Scope scope, Map<String, Object> data,
                              List<EvidenceRef> evidence, List<String> limitations, String error) {
        try {
            String json = mapper.writeValueAsString(data);
            if (json.length() > 30_000) return ToolResult.error(scope, "QUALITY_RESULT_LIMIT");
            return new ToolResult(status, scope, json, status == ToolStatus.ERROR ? List.of() : evidence,
                    limitations, error);
        } catch (JsonProcessingException ignored) {
            return ToolResult.error(scope, "QUALITY_SERIALIZATION_FAILED");
        }
    }

    private static String shortText(String text, int limit) {
        if (text == null) return "";
        return text.substring(0, Math.min(text.length(), limit));
    }

    private static final String REVIEW_SCHEMA = """
            {"type":"object","additionalProperties":false,"required":["jobId","candidateKind","page","pageSize"],
             "properties":{"jobId":{"type":"integer","minimum":1},"candidateKind":{"type":"string","enum":["entity","relation"]},
             "page":{"type":"integer","minimum":1,"maximum":1000},"pageSize":{"type":"integer","minimum":1,"maximum":20}}}
            """;
    private static final String SOURCE_SCHEMA = """
            {"type":"object","additionalProperties":false,"required":["jobId","candidateKind","candidateId","expectedRefs","maxChars"],
             "properties":{"jobId":{"type":"integer","minimum":1},"candidateKind":{"type":"string","enum":["entity","relation"]},
             "candidateId":{"type":"integer","minimum":1},"maxChars":{"type":"integer","minimum":200,"maximum":1200},
             "expectedRefs":{"type":"array","minItems":1,"maxItems":10,"items":{"type":"object","additionalProperties":false,
             "required":["chunkId","expectedHash"],"properties":{"chunkId":{"type":"integer","minimum":1},
             "expectedHash":{"type":"string","pattern":"^[0-9a-fA-F]{64}$"}}}}}}
            """;
    private static final String INSIGHT_SCHEMA = """
            {"type":"object","additionalProperties":false,"required":["isolatedLimit","topLimit"],
             "properties":{"isolatedLimit":{"type":"integer","minimum":1,"maximum":100},
             "topLimit":{"type":"integer","minimum":1,"maximum":10}}}
            """;
    private static final String CLAIM_SCHEMA = """
            {"type":"object","additionalProperties":false,"required":["artifactId","claimIds"],
             "properties":{"artifactId":{"type":"string","pattern":"^[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}$"},
             "claimIds":{"type":"array","minItems":1,"maxItems":10,"uniqueItems":true,
             "items":{"type":"string","pattern":"^[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}$"}}}}
            """;
}
