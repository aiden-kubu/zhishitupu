package com.knowledgegraph.assistant.research;

import com.fasterxml.jackson.databind.JsonNode;
import com.knowledgegraph.assistant.contract.AssistantContracts.Scope;
import com.knowledgegraph.assistant.contract.AssistantContracts.ScopeMode;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Validates existing endpoints and local draft IDs without creating graph records. */
final class ResearchDraftValidator {
    private ResearchDraftValidator() {}

    static void validateShape(JsonNode args, Scope scope, ResearchReadRepository repo) {
        ResearchAgentToolProvider.fields(args, "targetLibraryId", "nodes", "edges");
        long targetLibraryId = ResearchAgentToolProvider.positive(args, "targetLibraryId");
        if (!repo.libraryExistsInScope(scope, targetLibraryId)) throw new IllegalArgumentException("LIBRARY_OUT_OF_SCOPE");
        JsonNode nodes = args.get("nodes");
        JsonNode edges = args.get("edges");
        if (nodes == null || !nodes.isArray() || nodes.size() > 10 || edges == null || !edges.isArray()
                || edges.size() > 10 || nodes.isEmpty() && edges.isEmpty()) throw new IllegalArgumentException("INVALID_DRAFT_SIZE");
        Set<String> localIds = new HashSet<>();
        for (JsonNode node : nodes) {
            ResearchAgentToolProvider.fields(node, "localId", "name", "definition");
            String id = ResearchAgentToolProvider.requiredString(node, "localId", 7, 46);
            if (!id.matches("draft:[A-Za-z0-9_-]{1,40}") || !localIds.add(id)) throw new IllegalArgumentException("INVALID_DRAFT_ID");
            ResearchAgentToolProvider.requiredString(node, "name", 1, 200);
            ResearchAgentToolProvider.requiredString(node, "definition", 1, 1000);
        }
        Scope targetScope = new Scope(ScopeMode.SELECTED, Set.of(targetLibraryId),
                scope.documentIds(), scope.nodeIds());
        for (JsonNode edge : edges) {
            ResearchAgentToolProvider.fields(edge, "source", "target", "relationType");
            endpoint(ResearchAgentToolProvider.requiredString(edge, "source", 1, 80), localIds, targetScope, repo);
            endpoint(ResearchAgentToolProvider.requiredString(edge, "target", 1, 80), localIds, targetScope, repo);
            ResearchAgentToolProvider.requiredString(edge, "relationType", 1, 100);
        }
    }

    private static void endpoint(String value, Set<String> localIds, Scope targetScope, ResearchReadRepository repo) {
        if (value.startsWith("draft:")) {
            if (!localIds.contains(value)) throw new IllegalArgumentException("UNKNOWN_LOCAL_NODE");
            return;
        }
        if (!value.matches("node:[1-9][0-9]{0,18}")) throw new IllegalArgumentException("INVALID_ENDPOINT");
        long id;
        try { id = Long.parseLong(value.substring(5)); }
        catch (NumberFormatException ignored) { throw new IllegalArgumentException("INVALID_ENDPOINT"); }
        if (repo.node(targetScope, id).isEmpty()) throw new IllegalArgumentException("ENDPOINT_OUT_OF_SCOPE");
    }

    static Map<String, Object> draft(JsonNode args, Scope scope, ResearchReadRepository repo) {
        validateShape(args, scope, repo);
        List<Map<String, Object>> nodes = new ArrayList<>();
        for (JsonNode node : args.get("nodes")) nodes.add(ResearchAgentToolProvider.map(
                "localId", node.get("localId").textValue(), "name", node.get("name").textValue(),
                "definition", node.get("definition").textValue(), "officialNodeId", null));
        List<Map<String, Object>> edges = new ArrayList<>();
        for (JsonNode edge : args.get("edges")) edges.add(ResearchAgentToolProvider.map(
                "source", edge.get("source").textValue(), "target", edge.get("target").textValue(),
                "relationType", edge.get("relationType").textValue(), "officialEdgeId", null));
        return ResearchAgentToolProvider.map("targetLibraryId", args.get("targetLibraryId").longValue(),
                "nodes", nodes, "edges", edges, "recommendationOnly", true,
                "executionAllowed", false, "saved", false, "humanVerified", false,
                "semanticSupport", "not_evaluated", "sourceStatus", "model_suggestion_unverified");
    }
}
