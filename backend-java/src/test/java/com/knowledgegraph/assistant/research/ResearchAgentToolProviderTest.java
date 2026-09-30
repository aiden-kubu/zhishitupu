package com.knowledgegraph.assistant.research;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowledgegraph.assistant.contract.AssistantContracts.*;
import com.knowledgegraph.assistant.source.SourceLocator;
import com.knowledgegraph.assistant.tool.AssistantTool;
import com.knowledgegraph.assistant.tool.ToolRegistry;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ResearchAgentToolProviderTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Scope AB = new Scope(ScopeMode.SELECTED, Set.of(1L), Set.of(11L, 12L), Set.of());
    private static final Scope A = new Scope(ScopeMode.SELECTED, Set.of(1L), Set.of(11L), Set.of());

    @Test void sevenToolsHaveRoleEffectsAndClosedSchemas() throws Exception {
        var tools = new ResearchAgentToolProvider(new FakeRepo(), new FakeSource()).tools();
        assertEquals(7, tools.size());
        assertEquals(6, tools.stream().filter(t -> t.descriptor().effect() == ToolEffect.READ_ONLY).count());
        assertEquals(1, tools.stream().filter(t -> t.descriptor().effect() == ToolEffect.DRAFT_ONLY).count());
        for (AssistantTool tool : tools) {
            assertEquals(Set.of(Role.GRAPH_RESEARCH), tool.descriptor().roles());
            JsonNode schema = JSON.readTree(tool.descriptor().argumentsSchemaJson());
            assertFalse(schema.path("additionalProperties").booleanValue());
            assertTrue(schema.path("properties").isObject());
            assertTrue(schema.path("required").isArray());
        }
        ToolResult bad = invoke(tools, "research.search_nodes", A, "{\"query\":\"TCP\",\"libraryIds\":[2]}");
        assertEquals("INVALID_TOOL_ARGUMENTS", bad.errorCode());
    }

    @Test void retrievalProvidesRealHashForImmediateRereadAndStableEvidence() throws Exception {
        FakeRepo repo = new FakeRepo();
        FakeSource source = new FakeSource();
        var tools = new ResearchAgentToolProvider(repo, source).tools();
        ToolResult retrieved = invoke(tools, "research.retrieve_chunks", AB,
                "{\"query\":\"TCP\",\"maxSources\":2,\"maxChars\":2000}");
        assertEquals(ToolStatus.PARTIAL, retrieved.status()); // two selected documents, bounded source count
        JsonNode items = JSON.readTree(retrieved.dataJson()).get("items");
        assertEquals(2, items.size());
        assertFalse(JSON.readTree(retrieved.dataJson()).get("completeCoverage").booleanValue());
        assertTrue(JSON.readTree(retrieved.dataJson()).get("queryWindowExhausted").booleanValue());
        assertEquals(Set.of(11L, 12L), Set.of(items.get(0).get("documentId").asLong(), items.get(1).get("documentId").asLong()));
        assertEquals(2, retrieved.evidence().size());
        JsonNode first = items.get(0);
        String readArgs = "{\"documentId\":" + first.get("documentId").asLong()
                + ",\"chunkId\":" + first.get("chunkId").asLong()
                + ",\"expectedContentHash\":\"" + first.get("contentHash").asText() + "\"}";
        ToolResult reread = invoke(tools, "research.read_source", AB, readArgs);
        assertEquals(ToolStatus.OK, reread.status());
        assertEquals(retrieved.evidence().get(0), reread.evidence().get(0));
        assertEquals(3, source.calls);
        source.hashes.put(first.get("chunkId").asLong(), "changed");
        assertEquals("STALE_SOURCE", invoke(tools, "research.read_source", AB, readArgs).errorCode());
    }

    @Test void sourceStatusAndNodeScopeFailClosed() throws Exception {
        FakeRepo repo = new FakeRepo();
        var tools = new ResearchAgentToolProvider(repo, new FakeSource()).tools();
        Scope nodeScope = new Scope(ScopeMode.SELECTED, Set.of(), Set.of(), Set.of(1L));
        assertEquals("NODE_SCOPE_UNSUPPORTED", invoke(tools, "research.retrieve_chunks", nodeScope,
                "{\"query\":\"TCP\"}").errorCode());
        assertEquals("NODE_SCOPE_UNSUPPORTED", invoke(tools, "research.read_source", nodeScope,
                "{\"documentId\":11,\"chunkId\":101,\"expectedContentHash\":\"h101\"}").errorCode());
        repo.status.put(101L, "archived");
        assertEquals("SOURCE_UNAVAILABLE", invoke(tools, "research.read_source", A,
                "{\"documentId\":11,\"chunkId\":101,\"expectedContentHash\":\"h101\"}").errorCode());
        assertEquals("REFERENT_UNRESOLVED", invoke(tools, "research.retrieve_chunks", A,
                "{\"query\":\"继续比较它\"}").errorCode());
        repo.status.put(101L, "active");
        repo.unitStatus.put(101L, "PENDING");
        assertEquals("SOURCE_UNAVAILABLE", invoke(tools, "research.read_source", A,
                "{\"documentId\":11,\"chunkId\":101,\"expectedContentHash\":\"h101\"}").errorCode());
    }

    @Test void longABBodiesReceiveReservedCharacterSharesAndStableExcerpts() throws Exception {
        FakeSource source = new FakeSource();
        source.bodies.put(101L, "TCP " + "A".repeat(3000));
        source.bodies.put(102L, "TCP " + "B".repeat(3000));
        var tools = new ResearchAgentToolProvider(new FakeRepo(), source).tools();
        ToolResult retrieved = invoke(tools, "research.retrieve_chunks", AB,
                "{\"query\":\"TCP\",\"maxSources\":2,\"maxChars\":2000}");
        JsonNode data = JSON.readTree(retrieved.dataJson());
        assertEquals(ToolStatus.PARTIAL, retrieved.status());
        assertEquals(2, data.get("items").size());
        assertEquals(Set.of(11L, 12L), Set.of(data.get("items").get(0).get("documentId").asLong(),
                data.get("items").get(1).get("documentId").asLong()));
        assertEquals(1000, data.get("items").get(0).get("content").asText().length());
        assertEquals(1000, data.get("items").get(1).get("content").asText().length());
        assertTrue(data.get("items").get(0).get("bodyTruncated").booleanValue());
        assertTrue(data.get("items").get(1).get("bodyTruncated").booleanValue());
        assertEquals(2, retrieved.evidence().size());
        ToolResult reread = invoke(tools, "research.read_source", AB,
                "{\"documentId\":11,\"chunkId\":101,\"expectedContentHash\":\"h101\"}");
        assertEquals(retrieved.evidence().get(0), reread.evidence().get(0));
    }

    @Test void insufficientSourceSlotsReportUncoveredDocumentWithoutBorrowingScope() throws Exception {
        FakeSource source = new FakeSource();
        source.bodies.put(101L, "TCP " + "A".repeat(3000));
        source.bodies.put(102L, "TCP " + "B".repeat(3000));
        ToolResult result = invoke(new ResearchAgentToolProvider(new FakeRepo(), source).tools(),
                "research.retrieve_chunks", AB, "{\"query\":\"TCP\",\"maxSources\":1,\"maxChars\":200}");
        JsonNode data = JSON.readTree(result.dataJson());
        assertEquals(ToolStatus.PARTIAL, result.status());
        assertEquals(1, data.get("items").size());
        assertEquals(100, data.get("items").get(0).get("content").asText().length());
        assertEquals(12, data.get("uncoveredRequestedDocumentIds").get(0).asLong());
        assertTrue(result.limitations().contains("REQUESTED_DOCUMENTS_UNCOVERED"));
        assertTrue(result.limitations().contains("RESULT_LIMIT_REACHED"));
    }

    @Test void graphKeepsIncomingOutgoingDirectionAndDirectedPath() throws Exception {
        var tools = new ResearchAgentToolProvider(new FakeRepo(), new FakeSource()).tools();
        Scope project = Scope.project();
        ToolResult neighbors = invoke(tools, "research.get_neighbors", project,
                "{\"nodeId\":2,\"depth\":1,\"maxNodes\":3,\"maxEdges\":5}");
        JsonNode edges = JSON.readTree(neighbors.dataJson()).get("edges");
        assertEquals(2, edges.size());
        assertEquals(1, edges.get(0).get("sourceNodeId").asLong());
        assertEquals(2, edges.get(0).get("targetNodeId").asLong());
        assertEquals(2, edges.get(1).get("sourceNodeId").asLong());
        assertEquals(3, edges.get(1).get("targetNodeId").asLong());
        ToolResult paths = invoke(tools, "research.find_paths", project,
                "{\"sourceNodeId\":1,\"targetNodeId\":3,\"maxDepth\":2}");
        JsonNode path = JSON.readTree(paths.dataJson()).get("paths").get(0);
        assertEquals(2, path.size());
        assertEquals(path.get(0).get("targetNodeId").asLong(), path.get(1).get("sourceNodeId").asLong());
        ToolResult reverse = invoke(tools, "research.find_paths", project,
                "{\"sourceNodeId\":3,\"targetNodeId\":1,\"maxDepth\":2}");
        assertTrue(JSON.readTree(reverse.dataJson()).get("noPathWithinDepth").booleanValue());
        Scope singleNode = new Scope(ScopeMode.SELECTED, Set.of(), Set.of(), Set.of(2L));
        ToolResult isolated = invoke(tools, "research.get_neighbors", singleNode,
                "{\"nodeId\":2,\"depth\":2}");
        assertEquals(0, JSON.readTree(isolated.dataJson()).get("edges").size());
        assertEquals("NODE_NOT_FOUND_OR_OUT_OF_SCOPE", invoke(tools, "research.find_paths", singleNode,
                "{\"sourceNodeId\":2,\"targetNodeId\":3,\"maxDepth\":2}").errorCode());
    }

    @Test void draftUsesOnlyLocalIdsAndNeverSaves() throws Exception {
        var tools = new ResearchAgentToolProvider(new FakeRepo(), new FakeSource()).tools();
        ToolResult valid = invoke(tools, "research.draft_expansion", A,
                "{\"targetLibraryId\":1,\"nodes\":[{\"localId\":\"draft:new\",\"name\":\"新概念\",\"definition\":\"待核验定义\"}],\"edges\":[{\"source\":\"node:1\",\"target\":\"draft:new\",\"relationType\":\"相关\"}]}");
        assertEquals(ToolStatus.OK, valid.status());
        JsonNode data = JSON.readTree(valid.dataJson());
        assertFalse(data.get("saved").booleanValue());
        assertFalse(data.get("executionAllowed").booleanValue());
        assertNull(data.get("nodes").get(0).get("officialNodeId").textValue());
        assertTrue(valid.evidence().isEmpty());
        assertEquals("INVALID_TOOL_ARGUMENTS", invoke(tools, "research.draft_expansion", A,
                "{\"targetLibraryId\":2,\"nodes\":[{\"localId\":\"draft:x\",\"name\":\"X\",\"definition\":\"Y\"}],\"edges\":[]}").errorCode());
        assertEquals("INVALID_TOOL_ARGUMENTS", invoke(tools, "research.draft_expansion", A,
                "{\"targetLibraryId\":1,\"nodes\":[],\"edges\":[{\"source\":\"node:999\",\"target\":\"node:1\",\"relationType\":\"相关\"}]}").errorCode());
    }

    private static ToolResult invoke(List<AssistantTool> tools, String name, Scope scope, String json) throws Exception {
        AgentTask task = new AgentTask("research", Role.GRAPH_RESEARCH, "read", scope, Set.of(), Set.of(name), "{}",
                new Limits(2, 2, Duration.ofMinutes(1)), new CompletionPolicy(0, false, false));
        return new ToolRegistry(tools).invoke(task, name, JSON.readTree(json),
                new AssistantTool.Context("run", "research", Role.GRAPH_RESEARCH, scope,
                        Instant.now().plusSeconds(60), () -> false));
    }

    private static final class FakeSource implements SourceLocator {
        final Map<Long, String> hashes = new HashMap<>(Map.of(101L, "h101", 102L, "h102"));
        final Map<Long, String> bodies = new HashMap<>();
        int calls;
        @Override public Optional<Chunk> chunk(Scope scope, long documentId, long chunkId, int maxChars) {
            calls++;
            if (!scope.nodeIds().isEmpty()) throw new IllegalArgumentException("NODE_SCOPE_UNSUPPORTED");
            if (scope.documentIds().size() > 0 && !scope.documentIds().contains(documentId)) return Optional.empty();
            String hash = hashes.get(chunkId);
            String full = bodies.getOrDefault(chunkId, "TCP 来源正文 " + documentId);
            return hash == null ? Optional.empty() : Optional.of(new Chunk(chunkId, chunkId + 1000, 1, hash,
                    0, full.length(), full.substring(0, Math.min(full.length(), maxChars)), full.length() > maxChars));
        }
        @Override public Optional<Unit> unit(Scope scope, long documentId, int unitIndex, int maxChunks, int maxCharsPerChunk) {
            return Optional.empty();
        }
    }

    private static final class FakeRepo implements ResearchReadRepository {
        final Map<Long, String> status = new HashMap<>();
        final Map<Long, String> unitStatus = new HashMap<>();
        final List<Node> nodes = List.of(
                new Node(1, "A", null, "concept", "定义", "active", "v1", "import", "APPROVED", List.of(1L), List.of(), null),
                new Node(2, "B", null, "concept", "定义", "active", "v2", "import", "APPROVED", List.of(1L), List.of(), null),
                new Node(3, "C", null, "concept", "定义", "active", "v3", "import", "APPROVED", List.of(1L), List.of(), null));
        final List<Edge> edges = List.of(new Edge(21, 1, 2, "前置", "active", "e21", "import", "APPROVED", true),
                new Edge(22, 2, 3, "依赖", "active", "e22", "import", "APPROVED", true));
        @Override public Window<Node> searchNodes(Scope scope, String query, int limit) { return new Window<>(nodes.subList(0, Math.min(limit, nodes.size())), nodes.size() > limit); }
        @Override public Optional<Node> node(Scope scope, long nodeId) {
            return nodes.stream().filter(n -> n.id() == nodeId && (scope.nodeIds().isEmpty() || scope.nodeIds().contains(nodeId))).findFirst();
        }
        @Override public Window<Edge> incidentEdges(Scope scope, long nodeId, int limit) {
            return edgeWindow(scope, nodeId, limit, false);
        }
        @Override public Window<Edge> outgoingEdges(Scope scope, long nodeId, int limit) {
            return edgeWindow(scope, nodeId, limit, true);
        }
        private Window<Edge> edgeWindow(Scope scope, long nodeId, int limit, boolean outgoing) {
            List<Edge> visible = edges.stream().filter(e -> outgoing ? e.sourceNodeId() == nodeId
                    : e.sourceNodeId() == nodeId || e.targetNodeId() == nodeId)
                    .filter(e -> scope.nodeIds().isEmpty() || scope.nodeIds().contains(e.sourceNodeId())
                            && scope.nodeIds().contains(e.targetNodeId())).toList();
            return new Window<>(visible.subList(0, Math.min(limit, visible.size())), visible.size() > limit);
        }
        @Override public Window<Long> matchingDocuments(Scope scope, List<String> terms, int limit) {
            List<Long> docs = List.of(11L, 12L).stream().filter(d -> scope.documentIds().isEmpty()
                    || scope.documentIds().contains(d)).toList();
            return new Window<>(docs.subList(0, Math.min(limit, docs.size())), docs.size() > limit);
        }
        @Override public Window<Candidate> candidates(Scope scope, long documentId, List<String> terms, int limit) {
            return new Window<>(List.of(new Candidate(documentId, documentId == 11 ? 101 : 102,
                    documentId == 11 ? "h101" : "h102", 1)), false);
        }
        @Override public Optional<SourceMeta> sourceMeta(Scope scope, long documentId, long chunkId) {
            if (scope.documentIds().size() > 0 && !scope.documentIds().contains(documentId)) return Optional.empty();
            if (documentId == 11 && chunkId != 101 || documentId == 12 && chunkId != 102) return Optional.empty();
            return Optional.of(new SourceMeta(documentId, chunkId, chunkId + 1000, 1, "page", "第 1 页",
                    documentId == 11 ? "h101" : "h102", "COMPLETED", status.getOrDefault(chunkId, "active"),
                    unitStatus.getOrDefault(chunkId, "READY")));
        }
        @Override public boolean libraryExistsInScope(Scope scope, long libraryId) {
            return libraryId == 1 && (scope.libraryIds().isEmpty() || scope.libraryIds().contains(libraryId));
        }
    }
}
