package com.knowledgegraph.assistant.research;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowledgegraph.assistant.contract.AssistantContracts.Scope;
import com.knowledgegraph.assistant.contract.AssistantContracts.ScopeMode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ResearchDraftValidatorTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Scope SCOPE = new Scope(ScopeMode.SELECTED, Set.of(1L), Set.of(), Set.of());

    @Test void validatesLocalAndExistingEndpointsWithoutWriting() throws Exception {
        var repo = new Repo();
        var value = JSON.readTree("{\"targetLibraryId\":1,\"nodes\":[{\"localId\":\"draft:x\",\"name\":\"X\",\"definition\":\"提议\"}],"
                + "\"edges\":[{\"source\":\"node:1\",\"target\":\"draft:x\",\"relationType\":\"关联\"}]}");
        var result = ResearchDraftValidator.draft(value, SCOPE, repo);
        assertEquals(1, repo.nodeChecks);
        assertEquals(false, result.get("saved"));
        assertEquals(false, result.get("executionAllowed"));
        assertEquals(true, result.get("recommendationOnly"));
        assertNull(((List<?>) result.get("edges")).isEmpty() ? null : ((java.util.Map<?, ?>) ((List<?>) result.get("edges")).get(0)).get("officialEdgeId"));
    }

    @Test void rejectsOutOfScopeLibraryForgedIdAndUnknownEndpoint() throws Exception {
        var repo = new Repo();
        for (String json : List.of(
                "{\"targetLibraryId\":2,\"nodes\":[{\"localId\":\"draft:x\",\"name\":\"X\",\"definition\":\"Y\"}],\"edges\":[]}",
                "{\"targetLibraryId\":1,\"nodes\":[{\"localId\":\"node:9\",\"name\":\"X\",\"definition\":\"Y\"}],\"edges\":[]}",
                "{\"targetLibraryId\":1,\"nodes\":[],\"edges\":[{\"source\":\"node:999\",\"target\":\"node:1\",\"relationType\":\"关联\"}]}",
                "{\"targetLibraryId\":1,\"nodes\":[{\"localId\":\"draft:x\",\"name\":\"X\",\"definition\":\"Y\",\"officialNodeId\":9}],\"edges\":[]}")) {
            assertThrows(IllegalArgumentException.class,
                    () -> ResearchDraftValidator.draft(JSON.readTree(json), SCOPE, repo));
        }
    }

    private static final class Repo implements ResearchReadRepository {
        int nodeChecks;
        @Override public boolean libraryExistsInScope(Scope scope, long libraryId) {
            return libraryId == 1 && scope.libraryIds().contains(libraryId);
        }
        @Override public Optional<Node> node(Scope scope, long nodeId) {
            nodeChecks++;
            return nodeId == 1 && scope.libraryIds().equals(Set.of(1L))
                    ? Optional.of(new Node(1, "X", null, "concept", null, "active", "v1", null, null,
                    List.of(1L), List.of(), null)) : Optional.empty();
        }
        @Override public Window<Node> searchNodes(Scope scope, String query, int limit) { throw new AssertionError(); }
        @Override public Window<Edge> incidentEdges(Scope scope, long nodeId, int limit) { throw new AssertionError(); }
        @Override public Window<Edge> outgoingEdges(Scope scope, long nodeId, int limit) { throw new AssertionError(); }
        @Override public Window<Long> matchingDocuments(Scope scope, List<String> terms, int limit) { throw new AssertionError(); }
        @Override public Window<Candidate> candidates(Scope scope, long documentId, List<String> terms, int limit) { throw new AssertionError(); }
        @Override public Optional<SourceMeta> sourceMeta(Scope scope, long documentId, long chunkId) { throw new AssertionError(); }
    }
}
