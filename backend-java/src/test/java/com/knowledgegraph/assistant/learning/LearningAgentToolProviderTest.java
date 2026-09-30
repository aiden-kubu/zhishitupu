package com.knowledgegraph.assistant.learning;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowledgegraph.assistant.contract.AssistantContracts.*;
import com.knowledgegraph.assistant.research.ResearchReadRepository;
import com.knowledgegraph.assistant.runtime.AssistantRuntime;
import com.knowledgegraph.assistant.runtime.DecisionProvider;
import com.knowledgegraph.assistant.source.SourceLocator;
import com.knowledgegraph.assistant.tool.AssistantTool;
import com.knowledgegraph.assistant.tool.ToolRegistry;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class LearningAgentToolProviderTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Scope SCOPE = new Scope(ScopeMode.SELECTED, Set.of(1L), Set.of(), Set.of());
    private final LearningReadRepository repo = mock(LearningReadRepository.class);
    private final ResearchReadRepository research = mock(ResearchReadRepository.class);
    private final SourceLocator source = mock(SourceLocator.class);
    private final LearningContextReader host = mock(LearningContextReader.class);

    private List<AssistantTool> tools() { return new LearningAgentToolProvider(repo, research, source, host).tools(); }
    private ToolResult invoke(String name, Scope scope, String arguments) throws Exception {
        AgentTask task = new AgentTask("learn", Role.LEARNING_COACH, "draft", scope, Set.of(), Set.of(name), "{}",
                new Limits(12, 12, Duration.ofMinutes(1)), new CompletionPolicy(0, false, false));
        return new ToolRegistry(tools()).invoke(task, name, JSON.readTree(arguments),
                new AssistantTool.Context("run", "learn", Role.LEARNING_COACH, scope,
                        Instant.now().plusSeconds(30), () -> false));
    }
    static ResearchReadRepository.Node node(long id) {
        return new ResearchReadRepository.Node(id, "节点" + id, null, "concept", "定义", "active",
                "2026-09-26T00:00:00Z", "manual", "pending", List.of(1L), List.of(), null, false);
    }

    @Test void eightClosedRoleSpecificToolsAndNoWrites() throws Exception {
        var tools = tools();
        assertEquals(8, tools.size());
        assertEquals(5, tools.stream().filter(t -> t.descriptor().effect() == ToolEffect.READ_ONLY).count());
        assertEquals(3, tools.stream().filter(t -> t.descriptor().effect() == ToolEffect.DRAFT_ONLY).count());
        for (var tool : tools) {
            assertEquals(Set.of(Role.LEARNING_COACH), tool.descriptor().roles());
            assertFalse(JSON.readTree(tool.descriptor().argumentsSchemaJson()).get("additionalProperties").asBoolean());
        }
        assertEquals("INVALID_TOOL_ARGUMENTS", invoke("learning.read_current_path", SCOPE,
                "{\"profileKey\":\"other\"}").errorCode());
        AgentTask researchTask = new AgentTask("r", Role.GRAPH_RESEARCH, "read", SCOPE,
                Set.of(), Set.of("learning.read_current_path"), "{}", new Limits(1, 1, Duration.ofMinutes(1)),
                new CompletionPolicy(0, false, false));
        assertFalse(new ToolRegistry(tools).allowed(researchTask, "learning.read_current_path"));
    }

    @Test void emptyPathAndFormalAssessmentAreDistinctFromOldScores() throws Exception {
        when(repo.currentPath(SCOPE)).thenReturn(LearningReadRepository.Path.none());
        assertEquals("none", JSON.readTree(invoke("learning.read_current_path", SCOPE, "{}").dataJson())
                .get("state").asText());
        var path = new LearningReadRepository.Path(LearningReadRepository.PathState.AVAILABLE, 91L, 1L,
                "学习", "beginner", "active", "u", "f".repeat(64),
                List.of(new LearningReadRepository.Step(101, "s1", 1, 0, "节点1", 20,
                        "completed", "v", 98, "u")));
        when(repo.currentPath(SCOPE)).thenReturn(path);
        var data = JSON.readTree(invoke("learning.read_current_path", SCOPE, "{}").dataJson());
        assertEquals(91, data.get("pathId").asLong());
        assertEquals(101, data.get("steps").get(0).get("stepId").asLong());
        assertEquals("unverified_legacy_record", data.get("steps").get(0).get("scoreValidity").asText());
        assertTrue(data.get("formalRevision").isNull());
        var assessment = JSON.readTree(invoke("learning.read_assessment_summary", SCOPE, "{}").dataJson());
        assertFalse(assessment.get("formalAvailable").asBoolean());
        assertEquals(0, assessment.get("validResults").size());
    }

    @Test void graphIsBoundedDirectionalAndNotPrerequisiteProof() throws Exception {
        var edge = new ResearchReadRepository.Edge(7, 1, 2, "RELATED", "active", "u", "manual", "pending", false);
        when(repo.graph(SCOPE, 2, 2)).thenReturn(new LearningReadRepository.Graph(
                List.of(node(1), node(2)), List.of(edge), true, false));
        ToolResult result = invoke("learning.read_graph_scope", SCOPE, "{\"maxNodes\":2,\"maxEdges\":2}");
        assertEquals(ToolStatus.PARTIAL, result.status());
        var data = JSON.readTree(result.dataJson());
        assertEquals(1, data.get("edges").get(0).get("sourceNodeId").asInt());
        assertEquals(2, data.get("edges").get(0).get("targetNodeId").asInt());
        assertEquals("not_established", data.get("edges").get(0).get("prerequisiteSemantics").asText());
        assertFalse(data.get("completeCoverage").asBoolean());
    }

    @Test void sourceRereadRequiresHostPointerAndRejectsNodeScope() throws Exception {
        when(host.snapshot("run", "learn")).thenReturn(Optional.empty());
        assertEquals("MATERIAL_HOST_UNAVAILABLE", invoke("learning.read_evidence", SCOPE,
                "{\"documentId\":11,\"chunkId\":101,\"expectedContentHash\":\"h\"}").errorCode());
        Scope nodeScope = new Scope(ScopeMode.SELECTED, Set.of(1L), Set.of(11L), Set.of(1L));
        assertEquals("INVALID_TOOL_ARGUMENTS", invoke("learning.read_evidence", nodeScope,
                "{\"documentId\":11,\"chunkId\":101,\"expectedContentHash\":\"h\"}").errorCode());
        verifyNoInteractions(source);
    }

    @Test void trustedPointerRereadsExactFormalChunkAndRejectsChangedHash() throws Exception {
        Scope documentScope = new Scope(ScopeMode.SELECTED, Set.of(1L), Set.of(11L), Set.of());
        EvidenceRef pointer = LearningArtifactHandoffTest.source(11, 101, "h101", documentScope);
        Artifact study = LearningArtifactHandoffTest.study("artifact", "research", 11, documentScope, pointer);
        when(host.snapshot("run", "learn")).thenReturn(Optional.of(new LearningContextReader.Snapshot(
                Set.of("research"), Map.of("research", Role.GRAPH_RESEARCH), List.of(study), List.of(), List.of())));
        var meta = new ResearchReadRepository.SourceMeta(11, 101, 1001, 0, "page", "page=1",
                "h101", "COMPLETED", "active", "READY");
        when(research.sourceMeta(SCOPE, 11, 101)).thenReturn(Optional.of(meta));
        when(source.chunk(SCOPE, 11, 101, 4000)).thenReturn(Optional.of(
                new SourceLocator.Chunk(101, 1001, 0, "h101", 0, 4, "body", false)));
        String args = "{\"documentId\":11,\"chunkId\":101,\"expectedContentHash\":\"h101\"}";
        var result = invoke("learning.read_evidence", SCOPE, args);
        assertEquals(ToolStatus.OK, result.status());
        assertEquals("body", JSON.readTree(result.dataJson()).get("content").asText());
        assertEquals(1, result.evidence().size());
        when(research.sourceMeta(SCOPE, 11, 101)).thenReturn(Optional.of(new ResearchReadRepository.SourceMeta(
                11, 101, 1001, 0, "page", "page=1", "changed", "COMPLETED", "active", "READY")));
        assertEquals("STALE_OR_UNAVAILABLE_SOURCE", invoke("learning.read_evidence", SCOPE, args).errorCode());
    }

    @Test void learningInputsExposeOriginalClaimsAndCapturedQualityWithoutSemanticUpgrade() throws Exception {
        Scope documentScope = new Scope(ScopeMode.SELECTED, Set.of(1L), Set.of(11L), Set.of());
        EvidenceRef pointer = LearningArtifactHandoffTest.source(11, 101, "h101", documentScope);
        Artifact study = LearningArtifactHandoffTest.study("artifact", "research", 11, documentScope, pointer);
        var check = new LearningContextReader.ClaimCheck("c1", "CURRENT", "not_evaluated",
                List.of(new LearningContextReader.SourceCheck(pointer.sourceId(), 101, "h101", "h101", "CURRENT")));
        var quality = new LearningContextReader.QualityObservation("quality", "artifact", "research",
                List.of(check), 1, 1, true, Instant.parse("2026-09-25T00:00:00Z"));
        when(host.snapshot("run", "learn")).thenReturn(Optional.of(new LearningContextReader.Snapshot(
                Set.of("research", "quality"), Map.of("research", Role.GRAPH_RESEARCH,
                "quality", Role.QUALITY_GOVERNANCE), List.of(study), List.of(), List.of(quality))));
        var response = invoke("learning.read_learning_inputs", SCOPE, "{}");
        assertEquals(ToolStatus.PARTIAL, response.status());
        var data = JSON.readTree(response.dataJson());
        assertEquals("source_fact", data.get("inputs").get(0).get("claims").get(0).get("kind").asText());
        assertEquals(1, data.get("qualityChecks").size());
        assertEquals("not_evaluated", data.get("qualityChecks").get(0).get("semanticSupport").asText());
        assertEquals(1, data.get("independentSourceCount").asInt());
    }

    @Test void subsetQualityReadReturnsPartialWithExplicitUncoveredClaim() throws Exception {
        Scope documentScope = new Scope(ScopeMode.SELECTED, Set.of(1L), Set.of(11L), Set.of());
        var first = LearningArtifactHandoffTest.source(11, 101, "h1", documentScope);
        var second = LearningArtifactHandoffTest.source(11, 102, "h2", documentScope);
        when(host.snapshot("run", "learn")).thenReturn(Optional.of(new LearningContextReader.Snapshot(
                Set.of("research", "quality-1"), Map.of("research", Role.GRAPH_RESEARCH,
                "quality-1", Role.QUALITY_GOVERNANCE),
                List.of(LearningArtifactHandoffTest.twoClaimStudy(first, second)), List.of(),
                List.of(LearningArtifactHandoffTest.quality("quality-1", "c1", first, true, 1, 1)))));
        ToolResult result = invoke("learning.read_learning_inputs", SCOPE, "{}");
        assertEquals(ToolStatus.PARTIAL, result.status());
        assertTrue(result.limitations().contains("QUALITY_ARTIFACT_CLAIMS_UNCOVERED"));
        var quality = JSON.readTree(result.dataJson()).get("qualityChecks").get(0);
        assertTrue(quality.get("requestCompleteCoverage").asBoolean());
        assertFalse(quality.get("artifactClaimCoverage").asBoolean());
        assertEquals("c2", quality.get("uncoveredClaimIds").get(0).asText());
    }

    @Test void actualRunnerProducesPathDiffExplanationAndTwoInformalQuestions() throws Exception {
        when(repo.libraryVisible(SCOPE, 1)).thenReturn(true);
        when(repo.currentPath(SCOPE)).thenReturn(LearningReadRepository.Path.none());
        when(repo.node(any(), eq(1L))).thenReturn(Optional.of(node(1)));
        when(repo.node(any(), eq(2L))).thenReturn(Optional.of(node(2)));
        when(host.snapshot(anyString(), eq("learn"))).thenReturn(Optional.empty());
        String path = """
                {"targetLibraryId":1,"mode":"new","goal":"理解概念","difficulty":"beginner","steps":[
                  {"localId":"draft:s1","nodeId":1,"objective":"学习1","reason":"基础建议","reasonKind":"inference","estimatedMinutes":20},
                  {"localId":"draft:s2","nodeId":2,"objective":"学习2","reason":"接续建议","reasonKind":"inference","estimatedMinutes":25}]}
                """;
        String explanation = """
                {"nodeId":1,"blocks":[{"kind":"system_fact","text":"节点存在"},
                 {"kind":"model_explanation","text":"分层理解建议"}]}
                """;
        String practice = """
                {"nodeId":1,"items":[{"localId":"draft:q1","prompt":"问题一","answer":"答案一","explanation":"解释一"},
                 {"localId":"draft:q2","prompt":"问题二","answer":"答案二","explanation":"解释二"}]}
                """;
        DecisionProvider decision = ctx -> {
            int completed = ctx.observations().size();
            if (completed < 3) {
                String name = List.of("learning.draft_path", "learning.draft_explanation", "learning.draft_practice").get(completed);
                String arguments = List.of(path, explanation, practice).get(completed);
                return "{\"type\":\"call_tool\",\"name\":\"" + name + "\",\"arguments\":" + arguments + "}";
            }
            for (var observation : ctx.observations()) assertEquals(ToolStatus.OK, observation.result().status());
            var data = JSON.createObjectNode();
            data.set("path", JSON.readTree(ctx.observations().get(0).result().dataJson()));
            data.set("explanation", JSON.readTree(ctx.observations().get(1).result().dataJson()));
            data.set("practice", JSON.readTree(ctx.observations().get(2).result().dataJson()));
            String evidenceId = ctx.observations().get(1).result().evidence().get(0).sourceId();
            return "{\"type\":\"complete\",\"outcome\":\"partial\",\"summary\":\"未保存学习草稿\",\"data\":"
                    + data + ",\"evidenceIds\":[\"" + evidenceId + "\"]}";
        };
        AgentTask task = new AgentTask("learn", Role.LEARNING_COACH, "draft", SCOPE, Set.of(),
                Set.of("learning.draft_path", "learning.draft_explanation", "learning.draft_practice"), "{}",
                new Limits(8, 4, Duration.ofSeconds(20)), new CompletionPolicy(3, false, false));
        try (var runtime = new AssistantRuntime(Map.of(Role.LEARNING_COACH, decision), tools(), 1)) {
            var run = runtime.start(new RunPlan("learning", SCOPE, List.of(task), new Limits(8, 4, Duration.ofSeconds(20))))
                    .completion().toCompletableFuture().get(6, TimeUnit.SECONDS);
            assertEquals(TaskStatus.PARTIAL, run.tasks().get(0).status());
            var output = JSON.readTree(run.tasks().get(0).artifact().dataJson());
            assertFalse(output.get("path").get("saved").asBoolean());
            assertTrue(output.get("path").get("diff").get("completeBaseline").asBoolean());
            assertEquals(2, output.get("explanation").get("blocks").size());
            assertEquals(2, output.get("practice").get("items").size());
            assertEquals("informal_practice", output.get("practice").get("mode").asText());
        }
    }

    @Test void runnerMergesDirectResearchSourceAndLearningRereadWithoutChangingOriginalRef() throws Exception {
        Scope documentScope = new Scope(ScopeMode.SELECTED, Set.of(1L), Set.of(11L), Set.of());
        EvidenceRef inherited = LearningArtifactHandoffTest.source(11, 101, "h101", documentScope);
        AssistantTool researchRead = new AssistantTool() {
            public ToolDescriptor descriptor() { return new ToolDescriptor("research.fake_read", Set.of(Role.GRAPH_RESEARCH),
                    ToolEffect.READ_ONLY, "fake source", "{\"type\":\"object\",\"properties\":{},\"additionalProperties\":false}"); }
            public void validateArguments(com.fasterxml.jackson.databind.JsonNode args, Scope scope) { }
            public ToolResult execute(Context context, com.fasterxml.jackson.databind.JsonNode args) {
                return new ToolResult(ToolStatus.OK, context.scope(), "{}", List.of(inherited), List.of(), null);
            }
        };
        AtomicReference<Artifact> direct = new AtomicReference<>();
        when(host.snapshot(anyString(), eq("learn"))).thenAnswer(call -> {
            Artifact artifact = direct.get();
            return artifact == null ? Optional.empty() : Optional.of(new LearningContextReader.Snapshot(
                    Set.of("research"), Map.of("research", Role.GRAPH_RESEARCH),
                    List.of(artifact), List.of(), List.of()));
        });
        var meta = new ResearchReadRepository.SourceMeta(11, 101, 1001, 0, "page", "page=1",
                "h101", "COMPLETED", "active", "READY");
        when(research.sourceMeta(SCOPE, 11, 101)).thenReturn(Optional.of(meta));
        when(source.chunk(SCOPE, 11, 101, 4000)).thenReturn(Optional.of(
                new SourceLocator.Chunk(101, 1001, 0, "h101", 0, 4, "body", false)));
        DecisionProvider researchDecision = ctx -> {
            if (ctx.observations().isEmpty()) return "{\"type\":\"call_tool\",\"name\":\"research.fake_read\",\"arguments\":{}}";
            return "{\"type\":\"complete\",\"outcome\":\"partial\",\"summary\":\"研究材料\",\"data\":"
                    + "{\"kind\":\"document_research\",\"documentId\":11,\"claims\":[{\"claimId\":\"c1\",\"text\":\"claim\",\"kind\":\"source_fact\",\"sourceIds\":[\""
                    + inherited.sourceId() + "\"]}],\"coverage\":{\"completeCoverage\":false,\"truncated\":false}},"
                    + "\"evidenceIds\":[\"" + inherited.sourceId() + "\"]}";
        };
        DecisionProvider learningDecision = ctx -> {
            direct.set(ctx.inputs().get(0));
            if (ctx.observations().isEmpty()) return "{\"type\":\"call_tool\",\"name\":\"learning.read_evidence\","
                    + "\"arguments\":{\"documentId\":11,\"chunkId\":101,\"expectedContentHash\":\"h101\"}}";
            ToolResult read = ctx.observations().get(0).result();
            assertEquals(ToolStatus.OK, read.status());
            String newId = read.evidence().get(0).sourceId();
            assertNotEquals(inherited.sourceId(), newId);
            assertEquals(inherited.object(), read.evidence().get(0).object());
            return "{\"type\":\"complete\",\"outcome\":\"completed\",\"summary\":\"材料已重读\","
                    + "\"data\":{},\"evidenceIds\":[\"" + inherited.sourceId() + "\",\"" + newId + "\"]}";
        };
        AgentTask researchTask = new AgentTask("research", Role.GRAPH_RESEARCH, "study", documentScope,
                Set.of(), Set.of("research.fake_read"), "{}", new Limits(3, 2, Duration.ofSeconds(20)),
                new CompletionPolicy(1, true, false));
        AgentTask learningTask = new AgentTask("learn", Role.LEARNING_COACH, "read", SCOPE,
                Set.of("research"), Set.of("learning.read_evidence"), "{}",
                new Limits(3, 2, Duration.ofSeconds(20)), new CompletionPolicy(1, true, true));
        var allTools = new java.util.ArrayList<>(tools()); allTools.add(researchRead);
        try (var runtime = new AssistantRuntime(Map.of(Role.GRAPH_RESEARCH, researchDecision,
                Role.LEARNING_COACH, learningDecision), allTools, 2)) {
            var run = runtime.start(new RunPlan("handoff", SCOPE, List.of(researchTask, learningTask),
                    new Limits(6, 4, Duration.ofSeconds(20)))).completion().toCompletableFuture().get(6, TimeUnit.SECONDS);
            assertEquals(TaskStatus.PARTIAL, run.tasks().get(1).status());
            assertEquals(2, run.tasks().get(1).artifact().evidence().size());
            assertTrue(run.tasks().get(1).artifact().limitations().contains("PARTIAL_DEPENDENCY"));
        }
    }

    @Test void runnerKeepsFailedEvidenceReadButAllowsLaterInformalPractice() throws Exception {
        when(repo.node(SCOPE, 1)).thenReturn(Optional.of(node(1)));
        AtomicReference<List<ToolObservation>> history = new AtomicReference<>(List.of());
        when(host.snapshot(anyString(), eq("learn"))).thenAnswer(call -> Optional.of(
                new LearningContextReader.Snapshot(Set.of(), Map.of(), List.of(), history.get(), List.of())));
        DecisionProvider decision = ctx -> {
            history.set(ctx.observations());
            if (ctx.observations().isEmpty()) return """
                    {"type":"call_tool","name":"learning.read_evidence","arguments":
                    {"documentId":11,"chunkId":101,"expectedContentHash":"missing"}}
                    """;
            if (ctx.observations().size() == 1) {
                assertEquals("UNTRUSTED_SOURCE_POINTER", ctx.observations().get(0).result().errorCode());
                return """
                        {"type":"call_tool","name":"learning.read_learning_inputs","arguments":{}}
                        """;
            }
            if (ctx.observations().size() == 2) {
                assertEquals(ToolStatus.PARTIAL, ctx.observations().get(1).result().status());
                assertTrue(JSON.readTree(ctx.observations().get(1).result().dataJson())
                        .get("failedObservations").isArray());
                return """
                        {"type":"call_tool","name":"learning.draft_practice","arguments":
                        {"nodeId":1,"items":[{"localId":"draft:q1","prompt":"问题","answer":"参考答案","explanation":"自查解释"}]}}
                        """;
            }
            assertEquals(ToolStatus.OK, ctx.observations().get(2).result().status());
            var practice = JSON.readTree(ctx.observations().get(2).result().dataJson());
            assertEquals("informal_practice", practice.get("mode").asText());
            return "{\"type\":\"complete\",\"outcome\":\"completed\",\"summary\":\"非正式练习\",\"data\":"
                    + practice + "}";
        };
        AgentTask task = new AgentTask("learn", Role.LEARNING_COACH, "practice", SCOPE, Set.of(),
                Set.of("learning.read_evidence", "learning.read_learning_inputs", "learning.draft_practice"),
                "{}", new Limits(6, 4, Duration.ofSeconds(20)), new CompletionPolicy(1, false, false));
        try (var runtime = new AssistantRuntime(Map.of(Role.LEARNING_COACH, decision), tools(), 1)) {
            var run = runtime.start(new RunPlan("recover", SCOPE, List.of(task),
                    new Limits(6, 4, Duration.ofSeconds(20)))).completion().toCompletableFuture().get(6, TimeUnit.SECONDS);
            assertEquals(TaskStatus.PARTIAL, run.tasks().get(0).status());
            assertTrue(run.tasks().get(0).artifact().limitations().contains("UNTRUSTED_SOURCE_POINTER"));
            assertFalse(JSON.readTree(run.tasks().get(0).artifact().dataJson()).get("saved").asBoolean());
            assertEquals(3, run.tasks().get(0).usage().toolCalls());
        }
    }
}
