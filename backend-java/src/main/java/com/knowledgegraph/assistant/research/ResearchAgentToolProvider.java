package com.knowledgegraph.assistant.research;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowledgegraph.assistant.contract.AssistantContracts.*;
import com.knowledgegraph.assistant.source.SourceLocator;
import com.knowledgegraph.assistant.tool.AssistantTool;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Seven bounded, role-specific tools. No Spring registration or model invocation. */
public final class ResearchAgentToolProvider {
    private static final Pattern TERM = Pattern.compile("[A-Za-z][A-Za-z0-9_+#.-]*|[\\p{IsHan}]{2,}");
    private static final Set<String> STOP = Set.of("是什么", "什么是", "请解释", "为什么", "如何", "怎么", "对比", "比较");
    private static final int MAX_DOCUMENT_WINDOW = 4;
    private static final int MAX_CANDIDATE_WINDOW = 16;
    private final ResearchReadRepository repo;
    private final SourceLocator sources;
    private final ObjectMapper mapper = new ObjectMapper();

    public ResearchAgentToolProvider(ResearchReadRepository repo, SourceLocator sources) {
        this.repo = repo; this.sources = sources;
    }

    public List<AssistantTool> tools() {
        return List.of(
                tool("research.search_nodes", ToolEffect.READ_ONLY,
                        """ 
                        {"type":"object","properties":{"query":{"type":"string","minLength":2,"maxLength":200},"limit":{"type":"integer","minimum":1,"maximum":20}},"required":["query"],"additionalProperties":false}
                        """, this::searchNodes),
                tool("research.retrieve_chunks", ToolEffect.READ_ONLY,
                        """
                        {"type":"object","properties":{"query":{"type":"string","minLength":2,"maxLength":200},"maxSources":{"type":"integer","minimum":1,"maximum":8},"maxChars":{"type":"integer","minimum":200,"maximum":8000}},"required":["query"],"additionalProperties":false}
                        """, this::retrieveChunks),
                tool("research.read_source", ToolEffect.READ_ONLY,
                        """
                        {"type":"object","properties":{"documentId":{"type":"integer","minimum":1},"chunkId":{"type":"integer","minimum":1},"expectedContentHash":{"type":"string","minLength":1,"maxLength":128},"maxChars":{"type":"integer","minimum":200,"maximum":4000}},"required":["documentId","chunkId","expectedContentHash"],"additionalProperties":false}
                        """, this::readSource),
                tool("research.get_node", ToolEffect.READ_ONLY,
                        """
                        {"type":"object","properties":{"nodeId":{"type":"integer","minimum":1}},"required":["nodeId"],"additionalProperties":false}
                        """, this::getNode),
                tool("research.get_neighbors", ToolEffect.READ_ONLY,
                        """
                        {"type":"object","properties":{"nodeId":{"type":"integer","minimum":1},"depth":{"type":"integer","minimum":1,"maximum":2},"maxNodes":{"type":"integer","minimum":1,"maximum":50},"maxEdges":{"type":"integer","minimum":1,"maximum":100}},"required":["nodeId","depth"],"additionalProperties":false}
                        """, this::getNeighbors),
                tool("research.find_paths", ToolEffect.READ_ONLY,
                        """
                        {"type":"object","properties":{"sourceNodeId":{"type":"integer","minimum":1},"targetNodeId":{"type":"integer","minimum":1},"maxDepth":{"type":"integer","minimum":1,"maximum":2},"maxPaths":{"type":"integer","minimum":1,"maximum":10}},"required":["sourceNodeId","targetNodeId","maxDepth"],"additionalProperties":false}
                        """, this::findPaths),
                tool("research.draft_expansion", ToolEffect.DRAFT_ONLY,
                        """
                        {"type":"object","properties":{"targetLibraryId":{"type":"integer","minimum":1},"nodes":{"type":"array","maxItems":10,"items":{"type":"object","properties":{"localId":{"type":"string","pattern":"^draft:[A-Za-z0-9_-]{1,40}$"},"name":{"type":"string","minLength":1,"maxLength":200},"definition":{"type":"string","minLength":1,"maxLength":1000}},"required":["localId","name","definition"],"additionalProperties":false}},"edges":{"type":"array","maxItems":10,"items":{"type":"object","properties":{"source":{"type":"string","minLength":1,"maxLength":80},"target":{"type":"string","minLength":1,"maxLength":80},"relationType":{"type":"string","minLength":1,"maxLength":100}},"required":["source","target","relationType"],"additionalProperties":false}}},"required":["targetLibraryId","nodes","edges"],"additionalProperties":false}
                        """, this::draftExpansion));
    }

    private AssistantTool tool(String name, ToolEffect effect, String schema, Body body) {
        ToolDescriptor descriptor = new ToolDescriptor(name, Set.of(Role.GRAPH_RESEARCH), effect,
                "Scoped research data or an unexecuted draft", schema.strip());
        return new AssistantTool() {
            @Override public ToolDescriptor descriptor() { return descriptor; }
            @Override public void validateArguments(JsonNode args, Scope scope) { validate(name, args, scope); }
            @Override public ToolResult execute(Context context, JsonNode args) { return body.run(context, args); }
        };
    }
    private interface Body { ToolResult run(AssistantTool.Context context, JsonNode args); }

    private void validate(String name, JsonNode args, Scope scope) {
        switch (name) {
            case "research.search_nodes" -> { fields(args, "query", "limit"); requiredString(args, "query", 2, 200); integer(args, "limit", 1, 20, 10); }
            case "research.retrieve_chunks" -> { fields(args, "query", "maxSources", "maxChars"); requiredString(args, "query", 2, 200); integer(args, "maxSources", 1, 8, 8); integer(args, "maxChars", 200, 8000, 8000); }
            case "research.read_source" -> { fields(args, "documentId", "chunkId", "expectedContentHash", "maxChars"); positive(args, "documentId"); positive(args, "chunkId"); requiredString(args, "expectedContentHash", 1, 128); integer(args, "maxChars", 200, 4000, 4000); }
            case "research.get_node" -> { fields(args, "nodeId"); positive(args, "nodeId"); }
            case "research.get_neighbors" -> { fields(args, "nodeId", "depth", "maxNodes", "maxEdges"); positive(args, "nodeId"); requiredInt(args, "depth", 1, 2); integer(args, "maxNodes", 1, 50, 50); integer(args, "maxEdges", 1, 100, 100); }
            case "research.find_paths" -> { fields(args, "sourceNodeId", "targetNodeId", "maxDepth", "maxPaths"); positive(args, "sourceNodeId"); positive(args, "targetNodeId"); requiredInt(args, "maxDepth", 1, 2); integer(args, "maxPaths", 1, 10, 10); }
            case "research.draft_expansion" -> ResearchDraftValidator.validateShape(args, scope, repo);
            default -> throw new IllegalArgumentException("Unknown tool");
        }
    }

    private ToolResult searchNodes(AssistantTool.Context ctx, JsonNode args) {
        Scope scope = ctx.scope();
        String query = args.get("query").textValue().trim();
        int limit = integer(args, "limit", 1, 20, 10);
        ResearchReadRepository.Window<ResearchReadRepository.Node> window = repo.searchNodes(scope, query, limit);
        List<Map<String, Object>> items = new ArrayList<>();
        for (ResearchReadRepository.Node n : window.items()) items.add(nodeData(n, query));
        return result(window.hasMore() ? ToolStatus.PARTIAL : ToolStatus.OK, scope,
                map("items", items, "limit", limit, "hasMore", window.hasMore(),
                        "completeCoverage", !window.hasMore(), "sort", "match-tier-then-node-id"),
                List.of(), window.hasMore() ? List.of("NODE_RESULT_WINDOW_TRUNCATED") : List.of());
    }

    private ToolResult retrieveChunks(AssistantTool.Context ctx, JsonNode args) {
        Scope scope = ctx.scope();
        if (!scope.nodeIds().isEmpty()) return ToolResult.error(scope, "NODE_SCOPE_UNSUPPORTED");
        String query = args.get("query").textValue().trim();
        if (query.matches(".*(它|两者|继续比较|继续讲).*")) return ToolResult.error(scope, "REFERENT_UNRESOLVED");
        List<String> terms = terms(query);
        if (terms.isEmpty()) return ToolResult.error(scope, "QUERY_NEEDS_TERMS");
        if (scope.documentIds().size() > MAX_DOCUMENT_WINDOW) return ToolResult.error(scope, "DOCUMENT_WINDOW_LIMIT");
        int maxSources = integer(args, "maxSources", 1, 8, 8);
        int maxChars = integer(args, "maxChars", 200, 8000, 8000);
        ResearchReadRepository.Window<Long> documents = repo.matchingDocuments(scope, terms, MAX_DOCUMENT_WINDOW);
        Map<Long, ResearchReadRepository.Window<ResearchReadRepository.Candidate>> candidates = new LinkedHashMap<>();
        for (long docId : documents.items()) candidates.put(docId,
                repo.candidates(scope, docId, terms, MAX_CANDIDATE_WINDOW));
        List<Map<String, Object>> items = new ArrayList<>();
        List<EvidenceRef> evidence = new ArrayList<>();
        Set<Long> returnedDocs = new LinkedHashSet<>();
        Set<String> limitations = new LinkedHashSet<>();
        if (documents.hasMore()) limitations.add("DOCUMENT_WINDOW_TRUNCATED");
        if (candidates.values().stream().anyMatch(ResearchReadRepository.Window::hasMore)) limitations.add("CANDIDATE_WINDOW_TRUNCATED");
        // Reserve a share for every matched document before reading any body; a long A cannot starve B.
        int activeDocuments = (int) candidates.values().stream().filter(window -> !window.items().isEmpty()).count();
        Map<Long, Integer> charQuota = new LinkedHashMap<>();
        Map<Long, Integer> usedByDocument = new LinkedHashMap<>();
        int shareIndex = 0;
        for (var entry : candidates.entrySet()) {
            if (entry.getValue().items().isEmpty()) continue;
            charQuota.put(entry.getKey(), maxChars / activeDocuments
                    + (shareIndex < maxChars % activeDocuments ? 1 : 0));
            usedByDocument.put(entry.getKey(), 0);
            shareIndex++;
        }
        int usedChars = 0;
        int rounds = 0;
        int visitedCandidates = 0;
        while (rounds < MAX_CANDIDATE_WINDOW && items.size() < maxSources && usedChars < maxChars) {
            boolean hadCandidate = false;
            for (var entry : candidates.entrySet()) {
                if (rounds >= entry.getValue().items().size()) continue;
                hadCandidate = true;
                if (items.size() >= maxSources || usedChars >= maxChars) break;
                long documentId = entry.getKey();
                int remainingShare = charQuota.get(documentId) - usedByDocument.get(documentId);
                if (remainingShare <= 0) { limitations.add("DOCUMENT_CHAR_QUOTA_EXHAUSTED"); continue; }
                var candidate = entry.getValue().items().get(rounds);
                visitedCandidates++;
                var body = sources.chunk(scope, candidate.documentId(), candidate.chunkId(), 4000);
                var meta = repo.sourceMeta(scope, candidate.documentId(), candidate.chunkId());
                if (body.isEmpty() || meta.isEmpty() || !meta.get().formalAvailable()
                        || !candidate.contentHash().equals(body.get().contentHash())
                        || !candidate.contentHash().equals(meta.get().contentHash())
                        || body.get().unitId() != meta.get().unitId()) {
                    limitations.add("SOURCE_CHANGED_OR_UNAVAILABLE"); continue;
                }
                String full = body.get().content();
                if (full.isEmpty()) { limitations.add("EMPTY_SOURCE_BODY"); continue; }
                String content = full.substring(0, Math.min(full.length(), remainingShare));
                boolean truncated = body.get().contentTruncated() || content.length() < full.length();
                if (truncated) limitations.add("SOURCE_BODY_TRUNCATED");
                EvidenceRef ref = ResearchEvidence.source(meta.get(), full, scope);
                evidence.add(ref); returnedDocs.add(candidate.documentId());
                items.add(map("documentId", candidate.documentId(), "chunkId", candidate.chunkId(),
                        "contentHash", candidate.contentHash(), "sourceId", ref.sourceId(),
                        "unitId", meta.get().unitId(), "unitIndex", meta.get().unitIndex(),
                        "unitType", meta.get().unitType(), "sourceLocator", meta.get().sourceLocator(),
                        "content", content, "bodyTruncated", truncated, "matchScore", candidate.matchScore()));
                usedChars += content.length();
                usedByDocument.put(documentId, usedByDocument.get(documentId) + content.length());
            }
            if (!hadCandidate) break;
            rounds++;
        }
        boolean sourceLimitReached = visitedCandidates < candidates.values().stream()
                .mapToInt(window -> window.items().size()).sum();
        if (sourceLimitReached) limitations.add("RESULT_LIMIT_REACHED");
        Set<Long> unmatchedDocs = new LinkedHashSet<>(scope.documentIds());
        unmatchedDocs.removeAll(returnedDocs);
        if (!unmatchedDocs.isEmpty()) limitations.add("REQUESTED_DOCUMENTS_UNCOVERED");
        if (items.isEmpty()) limitations.add("NO_FORMAL_SOURCE_MATCH");
        limitations.add("FULL_CORPUS_COVERAGE_NOT_VERIFIED");
        boolean incomplete = !limitations.isEmpty();
        return result(incomplete ? ToolStatus.PARTIAL : ToolStatus.OK, scope,
                map("items", items, "queryTerms", terms, "requestedDocumentIds", scope.documentIds(),
                        "matchingDocumentWindow", documents.items(), "returnedDocumentIds", returnedDocs,
                        "uncoveredRequestedDocumentIds", unmatchedDocs, "documentWindowLimit", MAX_DOCUMENT_WINDOW,
                        "candidateWindowPerDocument", MAX_CANDIDATE_WINDOW, "maxSources", maxSources,
                        "documentCharQuota", charQuota, "usedCharsByDocument", usedByDocument,
                        "usedChars", usedChars, "hasMore", sourceLimitReached || documents.hasMore()
                                || candidates.values().stream().anyMatch(ResearchReadRepository.Window::hasMore),
                        "queryWindowExhausted", !sourceLimitReached && !documents.hasMore()
                                && candidates.values().stream().noneMatch(ResearchReadRepository.Window::hasMore),
                        "completeCoverage", false, "coverageKind", "bounded_query_candidates"),
                evidence, List.copyOf(limitations));
    }

    private ToolResult readSource(AssistantTool.Context ctx, JsonNode args) {
        Scope scope = ctx.scope();
        if (!scope.nodeIds().isEmpty()) return ToolResult.error(scope, "NODE_SCOPE_UNSUPPORTED");
        long documentId = args.get("documentId").longValue();
        long chunkId = args.get("chunkId").longValue();
        String expected = args.get("expectedContentHash").textValue();
        int maxChars = integer(args, "maxChars", 200, 4000, 4000);
        var before = repo.sourceMeta(scope, documentId, chunkId);
        if (before.isEmpty()) return ToolResult.error(scope, "SOURCE_NOT_FOUND_OR_OUT_OF_SCOPE");
        if (!before.get().formalAvailable()) return ToolResult.error(scope, "SOURCE_UNAVAILABLE");
        if (!expected.equals(before.get().contentHash())) return ToolResult.error(scope, "STALE_SOURCE");
        var chunk = sources.chunk(scope, documentId, chunkId, 4000);
        var after = repo.sourceMeta(scope, documentId, chunkId);
        if (chunk.isEmpty() || after.isEmpty() || !after.get().formalAvailable())
            return ToolResult.error(scope, "SOURCE_CHANGED_OR_UNAVAILABLE");
        if (!expected.equals(chunk.get().contentHash()) || !expected.equals(after.get().contentHash())
                || chunk.get().chunkId() != chunkId || chunk.get().unitId() != after.get().unitId())
            return ToolResult.error(scope, "STALE_SOURCE");
        String full = chunk.get().content();
        String content = full.substring(0, Math.min(full.length(), maxChars));
        boolean truncated = chunk.get().contentTruncated() || content.length() < full.length();
        EvidenceRef ref = ResearchEvidence.source(after.get(), full, scope);
        return result(truncated ? ToolStatus.PARTIAL : ToolStatus.OK, scope,
                map("documentId", documentId, "chunkId", chunkId, "contentHash", expected,
                        "sourceId", ref.sourceId(), "unitId", after.get().unitId(),
                        "unitIndex", after.get().unitIndex(), "unitType", after.get().unitType(),
                        "sourceLocator", after.get().sourceLocator(), "chunkIndex", chunk.get().chunkIndex(),
                        "startOffset", chunk.get().startOffset(), "endOffset", chunk.get().endOffset(),
                        "content", content, "bodyTruncated", truncated, "formalAvailable", true),
                List.of(ref), truncated ? List.of("SOURCE_BODY_TRUNCATED") : List.of());
    }

    private ToolResult getNode(AssistantTool.Context ctx, JsonNode args) {
        Scope scope = ctx.scope();
        var node = repo.node(scope, args.get("nodeId").longValue());
        if (node.isEmpty()) return ToolResult.error(scope, "NODE_NOT_FOUND_OR_OUT_OF_SCOPE");
        List<String> limitations = new ArrayList<>(graphLimitations(node.get().origin(), node.get().reviewStatus()));
        if (!node.get().sourceEvidenceAvailable()) limitations.add("NODE_SOURCE_NOT_AVAILABLE_IN_SCOPE");
        return result(limitations.isEmpty() ? ToolStatus.OK : ToolStatus.PARTIAL, scope,
                map("node", nodeData(node.get(), null), "versionKind", "updatedAt_not_strong_version"),
                List.of(ResearchEvidence.node(node.get(), scope)), limitations);
    }

    private ToolResult getNeighbors(AssistantTool.Context ctx, JsonNode args) {
        Scope scope = ctx.scope();
        long center = args.get("nodeId").longValue();
        int depth = args.get("depth").intValue();
        int maxNodes = integer(args, "maxNodes", 1, 50, 50);
        int maxEdges = integer(args, "maxEdges", 1, 100, 100);
        var centerNode = repo.node(scope, center);
        if (centerNode.isEmpty()) return ToolResult.error(scope, "NODE_NOT_FOUND_OR_OUT_OF_SCOPE");
        Map<Long, ResearchReadRepository.Node> nodes = new LinkedHashMap<>();
        nodes.put(center, centerNode.get());
        Map<Long, ResearchReadRepository.Edge> edges = new LinkedHashMap<>();
        List<Long> frontier = List.of(center);
        boolean truncated = false;
        for (int hop = 0; hop < depth; hop++) {
            List<Long> next = new ArrayList<>();
            for (long nodeId : frontier) {
                var window = repo.incidentEdges(scope, nodeId, maxEdges);
                truncated |= window.hasMore();
                for (var edge : window.items()) {
                    if (edges.containsKey(edge.id())) continue;
                    long other = edge.sourceNodeId() == nodeId ? edge.targetNodeId() : edge.sourceNodeId();
                    var otherNode = repo.node(scope, other);
                    if (otherNode.isEmpty()) { truncated = true; continue; }
                    if (!nodes.containsKey(other) && nodes.size() >= maxNodes || edges.size() >= maxEdges) {
                        truncated = true; continue;
                    }
                    if (nodes.putIfAbsent(other, otherNode.get()) == null) next.add(other);
                    edges.put(edge.id(), edge);
                }
            }
            frontier = next;
            if (frontier.isEmpty()) break;
        }
        List<EvidenceRef> evidence = edges.values().stream().limit(64).map(edge -> ResearchEvidence.edge(edge, scope)).toList();
        List<String> limitations = new ArrayList<>();
        if (truncated) limitations.add("GRAPH_WINDOW_TRUNCATED");
        if (edges.size() > evidence.size()) limitations.add("GRAPH_EVIDENCE_REF_LIMIT");
        if (edges.values().stream().anyMatch(edge -> !edge.sourceEvidenceAvailable()
                || !graphLimitations(edge.origin(), edge.reviewStatus()).isEmpty())) limitations.add("GRAPH_SOURCE_OR_REVIEW_UNVERIFIED");
        return result(limitations.isEmpty() ? ToolStatus.OK : ToolStatus.PARTIAL, scope,
                map("centerNodeId", center, "depth", depth,
                        "nodes", nodes.values().stream().map(n -> nodeData(n, null)).toList(),
                        "edges", edges.values().stream().map(ResearchAgentToolProvider::edgeData).toList(),
                        "returnedNodes", nodes.size(), "returnedEdges", edges.size(), "truncated", truncated,
                        "completeCoverage", !truncated, "direction", "stored_source_to_target"),
                evidence, limitations);
    }

    private ToolResult findPaths(AssistantTool.Context ctx, JsonNode args) {
        Scope scope = ctx.scope();
        long source = args.get("sourceNodeId").longValue();
        long target = args.get("targetNodeId").longValue();
        int depth = args.get("maxDepth").intValue();
        int maxPaths = integer(args, "maxPaths", 1, 10, 10);
        if (source == target) return ToolResult.error(scope, "DISTINCT_ENDPOINTS_REQUIRED");
        if (repo.node(scope, source).isEmpty() || repo.node(scope, target).isEmpty())
            return ToolResult.error(scope, "NODE_NOT_FOUND_OR_OUT_OF_SCOPE");
        List<List<ResearchReadRepository.Edge>> paths = new ArrayList<>();
        Map<Long, ResearchReadRepository.Edge> cited = new LinkedHashMap<>();
        var first = repo.outgoingEdges(scope, source, 100);
        boolean truncated = first.hasMore();
        for (var edge : first.items()) {
            if (edge.targetNodeId() == target) { paths.add(List.of(edge)); cited.put(edge.id(), edge); }
            if (paths.size() >= maxPaths) { truncated = true; break; }
        }
        if (depth == 2 && paths.size() < maxPaths) {
            int explored = 0;
            for (var firstEdge : first.items()) {
                long middle = firstEdge.targetNodeId();
                if (middle == source || middle == target) continue;
                if (++explored > 50) { truncated = true; break; }
                var second = repo.outgoingEdges(scope, middle, 100);
                truncated |= second.hasMore();
                for (var secondEdge : second.items()) {
                    if (secondEdge.targetNodeId() != target || firstEdge.id() == secondEdge.id()) continue;
                    paths.add(List.of(firstEdge, secondEdge));
                    cited.put(firstEdge.id(), firstEdge); cited.put(secondEdge.id(), secondEdge);
                    if (paths.size() >= maxPaths) { truncated = true; break; }
                }
                if (paths.size() >= maxPaths) break;
            }
        }
        List<String> limitations = new ArrayList<>();
        if (truncated) limitations.add("PATH_WINDOW_TRUNCATED");
        if (cited.values().stream().anyMatch(edge -> !edge.sourceEvidenceAvailable()
                || !graphLimitations(edge.origin(), edge.reviewStatus()).isEmpty())) limitations.add("GRAPH_SOURCE_OR_REVIEW_UNVERIFIED");
        return result(limitations.isEmpty() ? ToolStatus.OK : ToolStatus.PARTIAL, scope,
                map("sourceNodeId", source, "targetNodeId", target, "maxDepth", depth,
                        "paths", paths.stream().map(path -> path.stream().map(ResearchAgentToolProvider::edgeData).toList()).toList(),
                        "truncated", truncated, "noPathWithinDepth", paths.isEmpty() && !truncated,
                        "direction", "stored_source_to_target"),
                cited.values().stream().map(edge -> ResearchEvidence.edge(edge, scope)).toList(), limitations);
    }

    private ToolResult draftExpansion(AssistantTool.Context ctx, JsonNode args) {
        return result(ToolStatus.OK, ctx.scope(), ResearchDraftValidator.draft(args, ctx.scope(), repo),
                List.of(), List.of("DRAFT_NOT_REVIEWED_OR_SAVED"));
    }

    private ToolResult result(ToolStatus status, Scope scope, Map<String, ?> payload,
                              List<EvidenceRef> evidence, List<String> limitations) {
        try {
            String json = mapper.writeValueAsString(payload);
            if (json.length() > 30000 || evidence.size() > 64) return ToolResult.error(scope, "RESEARCH_RESULT_LIMIT");
            return new ToolResult(status, scope, json, evidence, limitations, null);
        } catch (JsonProcessingException ignored) {
            return ToolResult.error(scope, "RESEARCH_RESULT_LIMIT");
        }
    }

    static Map<String, Object> nodeData(ResearchReadRepository.Node n, String query) {
        String definition = n.definition() == null ? null
                : n.definition().substring(0, Math.min(400, n.definition().length()));
        return map("nodeId", n.id(), "name", n.name(), "nameEn", n.nameEn(), "type", n.type(),
                "definition", definition, "definitionTruncated", n.definition() != null && n.definition().length() > 400,
                "status", n.status(), "updatedAt", n.updatedAt(), "versionKind", "updatedAt_not_strong_version",
                "libraryIds", n.libraryIds(), "aliases", n.aliases(),
                "sourceEvidenceAvailable", n.sourceEvidenceAvailable(),
                "origin", unknown(n.origin()), "reviewStatus", unknown(n.reviewStatus()),
                "matchedBy", query == null ? null : matchedBy(n, query));
    }
    private static String matchedBy(ResearchReadRepository.Node n, String q) {
        String query = q.toLowerCase(Locale.ROOT);
        if (n.name() != null && n.name().toLowerCase(Locale.ROOT).equals(query)
                || n.nameEn() != null && n.nameEn().toLowerCase(Locale.ROOT).equals(query)) return "exact_name";
        if (n.name() != null && n.name().toLowerCase(Locale.ROOT).startsWith(query)
                || n.nameEn() != null && n.nameEn().toLowerCase(Locale.ROOT).startsWith(query)) return "name_prefix";
        if (n.aliases().stream().anyMatch(a -> a.toLowerCase(Locale.ROOT).contains(query))) return "alias";
        if (n.name() != null && n.name().toLowerCase(Locale.ROOT).contains(query)
                || n.nameEn() != null && n.nameEn().toLowerCase(Locale.ROOT).contains(query)) return "name_contains";
        return "definition";
    }
    private static Map<String, Object> edgeData(ResearchReadRepository.Edge e) {
        return map("edgeId", e.id(), "sourceNodeId", e.sourceNodeId(), "targetNodeId", e.targetNodeId(),
                "relationType", e.relationType(), "status", e.status(), "updatedAt", e.updatedAt(),
                "origin", unknown(e.origin()), "reviewStatus", unknown(e.reviewStatus()),
                "sourceEvidenceAvailable", e.sourceEvidenceAvailable());
    }
    private static List<String> graphLimitations(String origin, String reviewStatus) {
        return origin == null || reviewStatus == null || !"APPROVED".equalsIgnoreCase(reviewStatus)
                ? List.of("GRAPH_FACT_NOT_HUMAN_VERIFIED") : List.of();
    }
    private static String unknown(String value) { return value == null || value.isBlank() ? "unknown" : value; }
    static Map<String, Object> map(Object... keyValues) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) map.put((String) keyValues[i], keyValues[i + 1]);
        return map;
    }
    private static List<String> terms(String query) {
        Matcher matcher = TERM.matcher(query.toLowerCase(Locale.ROOT));
        Set<String> found = new LinkedHashSet<>();
        while (matcher.find() && found.size() < 4) if (!STOP.contains(matcher.group())) found.add(matcher.group());
        return List.copyOf(found);
    }
    static void fields(JsonNode args, String... permitted) {
        if (args == null || !args.isObject()) throw new IllegalArgumentException("INVALID_ARGUMENTS");
        Set<String> allowed = Set.of(permitted);
        args.fieldNames().forEachRemaining(field -> { if (!allowed.contains(field)) throw new IllegalArgumentException("UNKNOWN_FIELD"); });
    }
    static long positive(JsonNode args, String key) {
        JsonNode value = args.get(key);
        if (value == null || !value.isIntegralNumber() || !value.canConvertToLong() || value.longValue() <= 0)
            throw new IllegalArgumentException("INVALID_ID");
        return value.longValue();
    }
    static int requiredInt(JsonNode args, String key, int min, int max) {
        JsonNode value = args.get(key);
        if (value == null || !value.isIntegralNumber() || !value.canConvertToInt()
                || value.intValue() < min || value.intValue() > max) throw new IllegalArgumentException("INVALID_INTEGER");
        return value.intValue();
    }
    static int integer(JsonNode args, String key, int min, int max, int fallback) {
        return args.has(key) ? requiredInt(args, key, min, max) : fallback;
    }
    static String requiredString(JsonNode args, String key, int min, int max) {
        JsonNode value = args.get(key);
        if (value == null || !value.isTextual() || value.textValue().length() < min
                || value.textValue().length() > max || value.textValue().isBlank())
            throw new IllegalArgumentException("INVALID_TEXT");
        return value.textValue();
    }
}
