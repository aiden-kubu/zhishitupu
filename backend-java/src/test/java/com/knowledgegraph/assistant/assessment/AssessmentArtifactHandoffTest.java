package com.knowledgegraph.assistant.assessment;

import com.knowledgegraph.assistant.contract.AssistantContracts.*;
import com.knowledgegraph.assistant.learning.LearningAgentToolProvider;
import com.knowledgegraph.assistant.learning.LearningContextReader;
import com.knowledgegraph.assistant.learning.LearningReadRepository;
import com.knowledgegraph.assistant.research.ResearchReadRepository;
import com.knowledgegraph.assistant.runtime.AssistantRuntime;
import com.knowledgegraph.assistant.runtime.DecisionProvider;
import com.knowledgegraph.assistant.source.SourceLocator;
import com.knowledgegraph.assistant.tool.AssistantTool;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static com.knowledgegraph.assistant.assessment.AssessmentAgentToolProviderTest.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AssessmentArtifactHandoffTest {
    private static final Limits LIMITS = new Limits(12, 6, Duration.ofSeconds(20));
    private static String call(String name, String args) {
        return "{\"type\":\"call_tool\",\"name\":\"" + name + "\",\"arguments\":" + args + "}";
    }
    private static String finish(String data) {
        return "{\"type\":\"complete\",\"outcome\":\"completed\",\"summary\":\"fixture\",\"data\":"
                + data + ",\"evidenceIds\":[]}";
    }
    private static Artifact artifact(String producer) {
        return new Artifact("artifact", producer, SCOPE, List.of(SCOPE), Outcome.COMPLETED,
                "fixture", "{}", List.of(), List.of(), List.of(), Instant.now());
    }
    private static AssessmentContextReader.Snapshot snapshot(Artifact input, List<ToolObservation> observations) {
        var captured = observations.stream().map(o -> new AssessmentContextReader.CapturedObservation(
                input.producerTaskId(), Role.LEARNING_COACH, o)).toList();
        return new AssessmentContextReader.Snapshot(Set.of(input.producerTaskId()),
                Map.of(input.producerTaskId(), Role.LEARNING_COACH), List.of(input), captured);
    }

    @Test void actualRunnerHandsOriginalInformalPracticeToTwoUnexecutedAssessmentQuestions() throws Exception {
        var learning = mock(LearningReadRepository.class);
        when(learning.currentPath(SCOPE)).thenReturn(path(FP, "current"));
        when(learning.node(SCOPE, 1)).thenReturn(Optional.of(node()));
        AtomicReference<List<ToolObservation>> captured = new AtomicReference<>(List.of());
        AtomicReference<Artifact> original = new AtomicReference<>();
        AssessmentContextReader host = (run, consumer) -> {
            if (!"assess".equals(consumer) || original.get() == null) return Optional.empty();
            return Optional.of(snapshot(original.get(), captured.get()));
        };
        var learningHost = (LearningContextReader) (run, consumer) -> Optional.empty();
        var learningProvider = new LearningAgentToolProvider(learning, mock(ResearchReadRepository.class),
                mock(SourceLocator.class), learningHost);
        var assessmentProvider = new AssessmentAgentToolProvider(learning, mock(AssessmentReadRepository.class),
                mock(ResearchReadRepository.class), mock(SourceLocator.class), host);
        List<AssistantTool> tools = new ArrayList<>(learningProvider.tools());
        tools.addAll(assessmentProvider.tools());
        DecisionProvider coach = context -> {
            if (context.observations().isEmpty()) return call("learning.draft_practice", """
                    {"nodeId":1,"items":[
                      {"localId":"draft:p1","prompt":"Explain A","answer":"A","explanation":"learning suggestion"},
                      {"localId":"draft:p2","prompt":"Explain B","answer":"B","explanation":"learning suggestion"}]}
                    """);
            captured.set(List.copyOf(context.observations()));
            assertEquals(ToolStatus.OK, captured.get().get(0).result().status());
            return finish(captured.get().get(0).result().dataJson());
        };
        DecisionProvider assessor = context -> {
            original.compareAndSet(null, context.inputs().get(0));
            if (context.observations().isEmpty()) return call("assessment.read_context", "{}");
            if (context.observations().size() == 1) {
                var read = parse(context.observations().get(0).result().dataJson());
                assertEquals(2, read.path("learningPractices").size());
                assertEquals("informal_practice", read.path("learningPractices").get(0).path("mode").asText());
                String firstRef = read.path("learningPractices").get(0).path("practiceRef").asText();
                String secondRef = read.path("learningPractices").get(1).path("practiceRef").asText();
                String args = "{\"pathId\":91,\"stepId\":101,\"expectedSnapshotFingerprint\":\"" + FP
                        + "\",\"items\":["
                        + "{\"localId\":\"draft:q1\",\"prompt\":\"Choose A\",\"topic\":\"A\",\"options\":[{\"id\":\"a\",\"text\":\"A\"},{\"id\":\"b\",\"text\":\"B\"}],\"referenceOptionId\":\"a\",\"explanation\":\"candidate\",\"basedOnPracticeRef\":\"" + firstRef + "\"},"
                        + "{\"localId\":\"draft:q2\",\"prompt\":\"Choose B\",\"topic\":\"B\",\"options\":[{\"id\":\"a\",\"text\":\"A\"},{\"id\":\"b\",\"text\":\"B\"}],\"referenceOptionId\":\"b\",\"explanation\":\"candidate\",\"basedOnPracticeRef\":\"" + secondRef + "\"}]}";
                return call("assessment.prepare_draft", args);
            }
            assertEquals(ToolStatus.PARTIAL, context.observations().get(1).result().status());
            return finish(context.observations().get(1).result().dataJson());
        };
        AgentTask learnTask = new AgentTask("learn", Role.LEARNING_COACH, "informal exercise", SCOPE,
                Set.of(), Set.of("learning.draft_practice"), "{}", LIMITS,
                new CompletionPolicy(1, false, false));
        AgentTask assessTask = new AgentTask("assess", Role.ASSESSMENT_ANALYSIS, "assessment draft", SCOPE,
                Set.of("learn"), Set.of("assessment.read_context", "assessment.prepare_draft"), "{}", LIMITS,
                new CompletionPolicy(2, false, true));
        try (var runtime = new AssistantRuntime(Map.of(Role.LEARNING_COACH, coach,
                Role.ASSESSMENT_ANALYSIS, assessor), tools, 2)) {
            var run = new RunPlan("isolated handoff", SCOPE, List.of(learnTask, assessTask), LIMITS);
            var result = runtime.start(run).completion().toCompletableFuture().get(8, TimeUnit.SECONDS);
            assertEquals(TaskStatus.COMPLETED, result.tasks().get(0).status());
            assertEquals(TaskStatus.PARTIAL, result.tasks().get(1).status());
            var body = parse(result.tasks().get(1).artifact().dataJson());
            assertEquals(2, body.path("items").size());
            assertEquals("unexecuted_draft", body.path("status").asText());
            assertFalse(body.path("saved").asBoolean());
            assertFalse(body.path("executionAllowed").asBoolean());
            assertFalse(body.path("formalAssessmentAvailable").asBoolean());
            assertEquals("unverified", body.path("items").get(0).path("answerValidity").asText());
            assertEquals("unverified", body.path("items").get(1).path("answerValidity").asText());
            assertEquals("learn", body.path("items").get(0).path("practiceOrigin").path("producerTaskId").asText());
            assertEquals(original.get().artifactId(), body.path("items").get(0).path("practiceOrigin").path("artifactId").asText());
            verify(learning, never()).nodeSourceLinked(SCOPE, 1, 11, 101);
        }
    }

    @Test void trustedFailureRetainsReasonWithoutEvidenceAndLaterIndependentPracticeSurvives() {
        var error = new ToolObservation("bad", "learning.read_evidence", Instant.now(),
                ToolResult.error(SCOPE, "SOURCE_UNAVAILABLE"));
        var practice = new ToolObservation("good", "learning.draft_practice", Instant.now(),
                new ToolResult(ToolStatus.OK, SCOPE,
                        "{\"kind\":\"practice_set\",\"mode\":\"informal_practice\",\"nodeId\":1,\"items\":[{\"localId\":\"draft:p\",\"prompt\":\"P\",\"referenceAnswer\":\"A\",\"sourceIds\":[]}],\"saved\":false,\"executionAllowed\":false,\"recommendationOnly\":true,\"progressChanged\":false,\"formalAssessmentAvailable\":false}",
                        List.of(), List.of(), null));
        var bundle = AssessmentMaterials.inspect(snapshot(artifact("learn"), List.of(error, practice)), SCOPE, "assess");
        assertEquals("SOURCE_UNAVAILABLE", bundle.failedObservations().get(0).get("errorCode"));
        assertTrue(bundle.pointers().isEmpty());
        assertEquals(1, bundle.practices().size());
        assertTrue(bundle.limitations().contains("PREVIOUS_TRUSTED_TOOL_ERROR"));
        var forged = new AssessmentContextReader.CapturedObservation("learn", Role.LEARNING_COACH,
                new ToolObservation("fake", "learning.delete_path", Instant.now(), ToolResult.error(SCOPE, "NO")));
        var snap = new AssessmentContextReader.Snapshot(Set.of("learn"), Map.of("learn", Role.LEARNING_COACH),
                List.of(artifact("learn")), List.of(forged));
        assertEquals("UNTRUSTED_TOOL_OBSERVATION", assertThrows(IllegalArgumentException.class,
                () -> AssessmentMaterials.inspect(snap, SCOPE, "assess")).getMessage());
        var nonDirect = new AssessmentContextReader.Snapshot(Set.of(), Map.of("learn", Role.LEARNING_COACH),
                List.of(artifact("learn")), List.of());
        assertEquals("NON_DIRECT_MATERIAL", assertThrows(IllegalArgumentException.class,
                () -> AssessmentMaterials.inspect(nonDirect, SCOPE, "assess")).getMessage());
    }
}
