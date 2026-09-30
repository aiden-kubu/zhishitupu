package com.knowledgegraph.assistant.runtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.knowledgegraph.assistant.contract.AssistantContracts.*;
import com.knowledgegraph.assistant.tool.AssistantTool;
import com.knowledgegraph.assistant.tool.ToolRegistry;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/** All providers and tools are local fixtures. No Spring context, DB, HTTP, credentials or paid model. */
class AssistantRuntimeTest {
    private static final Scope PROJECT = Scope.project();
    private static final Limits LIMITS = new Limits(24, 12, Duration.ofSeconds(30));
    private static final CompletionPolicy OPTIONAL = new CompletionPolicy(0, false, false);
    private static final CompletionPolicy EVIDENCED = new CompletionPolicy(1, true, false);

    @Test void independentResearchAgentsExchangeOnlyExplicitArtifactsThenHandOffToLearning() throws Exception {
        CountDownLatch bothResearchers = new CountDownLatch(2);
        AtomicInteger modelCalls = new AtomicInteger();
        AssistantTool read = tool(ToolEffect.READ_ONLY, (context, args) -> {
            bothResearchers.countDown();
            assertTrue(bothResearchers.await(3, TimeUnit.SECONDS), "independent researchers must run concurrently");
            return evidence(context.scope(), context.taskId(), ToolStatus.OK);
        });
        DecisionProvider provider = context -> {
            modelCalls.incrementAndGet();
            String id = context.task().taskId();
            if (id.equals("bookA") || id.equals("bookB")) {
                assertTrue(context.inputs().isEmpty());
                if (context.observations().isEmpty()) return call("read");
                assertEquals(1, context.observations().size());
                assertEquals(id, context.observations().get(0).result().evidence().get(0).sourceId());
                return complete("completed", id);
            }
            if (id.equals("compare")) {
                assertEquals(List.of("bookA", "bookB"), context.inputs().stream().map(Artifact::producerTaskId).toList());
                assertTrue(context.observations().isEmpty(), "sibling transcripts must not be shared");
                return complete("completed", "bookA", "bookB");
            }
            assertEquals(Role.LEARNING_COACH, context.task().role());
            assertEquals(List.of("compare"), context.inputs().stream().map(Artifact::producerTaskId).toList());
            assertEquals(2, context.inputs().get(0).evidence().size());
            return complete("completed", "bookA", "bookB");
        };
        try (var runtime = new AssistantRuntime(Map.of(Role.GRAPH_RESEARCH, provider, Role.LEARNING_COACH, provider), List.of(read), 2)) {
            var handle = runtime.start(plan(
                    task("bookA", Role.GRAPH_RESEARCH, scope(1), Set.of(), Set.of("read"), EVIDENCED),
                    task("bookB", Role.GRAPH_RESEARCH, scope(2), Set.of(), Set.of("read"), EVIDENCED),
                    task("compare", Role.GRAPH_RESEARCH, PROJECT, Set.of("bookA", "bookB"), Set.of(), OPTIONAL),
                    task("learn", Role.LEARNING_COACH, PROJECT, Set.of("compare"), Set.of(), OPTIONAL)));
            RunSnapshot result = await(handle);
            assertEquals(RunStatus.COMPLETED, result.status());
            assertEquals(new Usage(6, 2), result.usage());
            assertEquals(6, modelCalls.get());
            assertFalse(result.recoverableAfterRestart());
            assertFalse(result.tasks().get(3).artifact().executionReceipt());
            assertFalse(handle.cancel());
            assertEquals(RunStatus.COMPLETED, handle.snapshot().status());
        }
    }

    @Test void failureBlocksDependentsButPreservesIndependentResult() throws Exception {
        DecisionProvider provider = context -> {
            if (context.task().taskId().equals("bad")) throw new IllegalStateException("sensitive-provider-secret");
            assertEquals("good", context.task().taskId());
            return complete("completed");
        };
        try (var runtime = runtime(provider)) {
            RunSnapshot result = await(runtime.start(plan(simple("bad"), simple("good"), dependent("child", "bad", false))));
            assertEquals(RunStatus.PARTIAL, result.status());
            assertEquals(TaskStatus.FAILED, result.tasks().get(0).status());
            assertEquals("MODEL_FAILED", result.tasks().get(0).errorCode());
            assertEquals(TaskStatus.COMPLETED, result.tasks().get(1).status());
            assertEquals(TaskStatus.BLOCKED, result.tasks().get(2).status());
            assertFalse(result.toString().contains("sensitive-provider-secret"));
        }
    }

    @Test void partialDependenciesRequireOptInAndCannotBecomeUnqualifiedSuccess() throws Exception {
        try (var runtime = runtime(context -> complete(context.task().taskId().equals("source") ? "partial" : "completed"))) {
            RunSnapshot result = await(runtime.start(plan(simple("source"), dependent("strict", "source", false),
                    dependent("accept", "source", true))));
            assertEquals(TaskStatus.PARTIAL, result.tasks().get(0).status());
            assertEquals(TaskStatus.BLOCKED, result.tasks().get(1).status());
            assertEquals(TaskStatus.PARTIAL, result.tasks().get(2).status());
            assertTrue(result.tasks().get(2).artifact().limitations().contains("PARTIAL_DEPENDENCY"));
        }
    }

    @Test void needsInputBlocksDependentsAndSurfacesRunState() throws Exception {
        try (var runtime = runtime(context -> "{\"type\":\"needs_input\",\"summary\":\"请选择目标\",\"data\":{}}")) {
            RunSnapshot result = await(runtime.start(plan(simple("question"), dependent("child", "question", true))));
            assertEquals(RunStatus.NEEDS_INPUT, result.status());
            assertEquals(TaskStatus.BLOCKED, result.tasks().get(1).status());
        }
    }

    @Test void fabricatedEvidenceFailsAfterOneRepair() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        try (var runtime = runtime(context -> {
            if (calls.getAndIncrement() == 1) assertEquals(List.of("UNKNOWN_EVIDENCE_REFERENCE"), context.protocolErrors());
            return complete("completed", "invented");
        })) {
            var result = await(runtime.start(plan(simple("task"))));
            assertEquals("DECISION_PROTOCOL_FAILED", result.tasks().get(0).errorCode());
            assertNull(result.tasks().get(0).artifact());
            assertEquals(2, result.usage().modelCalls());
        }
    }

    @Test void repairCanRecoverMalformedDecisionWithoutExecutingFreeText() throws Exception {
        try (var runtime = runtime(context -> context.protocolErrors().isEmpty() ? "please run delete()" : complete("completed"))) {
            var result = await(runtime.start(plan(simple("task"))));
            assertEquals(RunStatus.COMPLETED, result.status());
            assertEquals(new Usage(2, 0), result.usage());
        }
    }

    @Test void completionPolicyRejectsSkippingRequiredWork() throws Exception {
        AssistantTool read = tool(ToolEffect.READ_ONLY, (context, args) -> evidence(context.scope(), "src", ToolStatus.OK));
        try (var runtime = runtime(context -> complete("completed"), read)) {
            var result = await(runtime.start(plan(task("task", Role.GRAPH_RESEARCH, PROJECT, Set.of(), Set.of("read"), EVIDENCED))));
            assertEquals("DECISION_PROTOCOL_FAILED", result.tasks().get(0).errorCode());
            assertEquals(0, result.usage().toolCalls());
        }
    }

    @Test void unknownToolIsNotExecutedAndCannotBeHiddenBySuccess() throws Exception {
        AtomicInteger actualCalls = new AtomicInteger();
        AssistantTool read = tool(ToolEffect.READ_ONLY, (context, args) -> {
            actualCalls.incrementAndGet(); return evidence(context.scope(), "src", ToolStatus.OK);
        });
        try (var runtime = runtime(context -> context.observations().isEmpty() ? call("delete.all") : complete("completed"), read)) {
            var result = await(runtime.start(plan(simple("task"))));
            assertEquals(0, actualCalls.get());
            assertEquals(TaskStatus.PARTIAL, result.tasks().get(0).status());
            assertTrue(result.tasks().get(0).artifact().limitations().contains("TOOL_NOT_ALLOWED"));
        }
    }

    @Test void resultOutsideFixedScopeIsRejectedAndCannotSupplyEvidence() throws Exception {
        AssistantTool read = tool(ToolEffect.READ_ONLY, (context, args) -> evidence(scope(2), "outside", ToolStatus.OK));
        DecisionProvider provider = context -> {
            if (context.observations().isEmpty()) return call("read");
            assertEquals(ToolStatus.ERROR, context.observations().get(0).result().status());
            assertTrue(context.observations().get(0).result().evidence().isEmpty());
            return complete("completed", "outside");
        };
        try (var runtime = runtime(provider, read)) {
            var result = await(runtime.start(plan(task("task", Role.GRAPH_RESEARCH, scope(1), Set.of(), Set.of("read"), OPTIONAL))));
            assertEquals(TaskStatus.FAILED, result.tasks().get(0).status());
        }
    }

    @Test void sourceIdCannotBeReboundToDifferentEvidence() throws Exception {
        AtomicInteger tools = new AtomicInteger();
        AssistantTool read = tool(ToolEffect.READ_ONLY, (context, args) -> {
            int revision = tools.incrementAndGet();
            return new ToolResult(ToolStatus.OK, PROJECT, "{}", List.of(new EvidenceRef("src", EvidenceKind.SOURCE,
                    new ObjectRef("document", "1", "v" + revision), PROJECT, "p1", "source")), List.of(), null);
        });
        try (var runtime = runtime(context -> context.observations().size() < 2 ? call("read") : complete("completed", "src"), read)) {
            var result = await(runtime.start(plan(task("task", Role.GRAPH_RESEARCH, PROJECT, Set.of(), Set.of("read"), EVIDENCED))));
            assertEquals(TaskStatus.PARTIAL, result.tasks().get(0).status());
            assertEquals("v1", result.tasks().get(0).artifact().evidence().get(0).object().version());
            assertTrue(result.tasks().get(0).artifact().limitations().contains("EVIDENCE_ID_CONFLICT"));
        }
    }

    @Test void partialToolResultsRetainLimitations() throws Exception {
        AssistantTool read = tool(ToolEffect.READ_ONLY, (context, args) -> evidence(PROJECT, "src", ToolStatus.PARTIAL));
        try (var runtime = runtime(context -> context.observations().isEmpty() ? call("read") : complete("completed", "src"), read)) {
            var result = await(runtime.start(plan(task("task", Role.GRAPH_RESEARCH, PROJECT, Set.of(), Set.of("read"), EVIDENCED))));
            assertEquals(TaskStatus.PARTIAL, result.tasks().get(0).status());
            assertTrue(result.tasks().get(0).artifact().limitations().contains("TRUNCATED"));
        }
    }

    @Test void cancellationWaitsForInflightProviderAndPreventsNextToolOrDependent() throws Exception {
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();
        AtomicInteger tools = new AtomicInteger();
        AssistantTool read = tool(ToolEffect.READ_ONLY, (context, args) -> { tools.incrementAndGet(); return evidence(PROJECT, "src", ToolStatus.OK); });
        DecisionProvider provider = context -> {
            calls.incrementAndGet(); entered.countDown(); assertTrue(release.await(3, TimeUnit.SECONDS)); return call("read");
        };
        try (var runtime = runtime(provider, read)) {
            var handle = runtime.start(plan(task("source", Role.GRAPH_RESEARCH, PROJECT, Set.of(), Set.of("read"), EVIDENCED),
                    dependent("child", "source", false)));
            try {
                assertTrue(entered.await(3, TimeUnit.SECONDS));
                assertTrue(handle.cancel());
                assertEquals(RunStatus.CANCELLING, handle.snapshot().status());
                assertFalse(handle.completion().toCompletableFuture().isDone());
            } finally { release.countDown(); }
            var result = await(handle);
            assertEquals(RunStatus.CANCELLED, result.status());
            assertTrue(result.tasks().stream().allMatch(task -> task.status() == TaskStatus.CANCELLED));
            assertEquals(1, calls.get());
            assertEquals(0, tools.get());
        }
    }

    @Test void cancellationDoesNotDiscardAlreadyCompletedSiblingArtifact() throws Exception {
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        try (var runtime = new AssistantRuntime(Map.of(Role.GRAPH_RESEARCH, context -> {
            if (context.task().taskId().equals("slow")) { entered.countDown(); assertTrue(release.await(3, TimeUnit.SECONDS)); }
            return complete("completed");
        }), List.of(), 1)) {
            var handle = runtime.start(plan(simple("fast"), simple("slow")));
            try { assertTrue(entered.await(3, TimeUnit.SECONDS)); handle.cancel(); }
            finally { release.countDown(); }
            var result = await(handle);
            assertEquals(TaskStatus.COMPLETED, result.tasks().get(0).status());
            assertNotNull(result.tasks().get(0).artifact());
            assertEquals(TaskStatus.CANCELLED, result.tasks().get(1).status());
        }
    }

    @Test void globalCallBudgetIsAtomicAcrossIndependentTasks() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        List<AgentTask> tasks = new ArrayList<>();
        for (int i = 0; i < 20; i++) tasks.add(simple("task" + i));
        try (var runtime = new AssistantRuntime(Map.of(Role.GRAPH_RESEARCH, context -> {
            calls.incrementAndGet(); return complete("completed");
        }), List.of(), 8)) {
            var result = await(runtime.start(new RunPlan("bounded", PROJECT, tasks, new Limits(3, 0, Duration.ofSeconds(30)))));
            assertEquals(3, calls.get());
            assertEquals(3, result.usage().modelCalls());
            assertEquals(3, result.tasks().stream().filter(task -> task.status() == TaskStatus.COMPLETED).count());
            assertEquals(RunStatus.PARTIAL, result.status());
        }
    }

    @Test void perTaskToolBudgetStopsRepeatedToolRequests() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        AssistantTool read = tool(ToolEffect.READ_ONLY, (context, args) -> { calls.incrementAndGet(); return evidence(PROJECT, "src", ToolStatus.OK); });
        AgentTask task = new AgentTask("task", Role.GRAPH_RESEARCH, "read", PROJECT, Set.of(), Set.of("read"), "{}",
                new Limits(5, 1, Duration.ofSeconds(30)), OPTIONAL);
        try (var runtime = runtime(context -> call("read"), read)) {
            var result = await(runtime.start(plan(task)));
            assertEquals("TOOL_CALL_LIMIT", result.tasks().get(0).errorCode());
            assertEquals(1, calls.get());
            assertEquals(new Usage(2, 1), result.usage());
        }
    }

    @Test void lateModelResultCannotCompleteAfterDeadline() throws Exception {
        MutableClock clock = new MutableClock();
        try (var runtime = new AssistantRuntime(Map.of(Role.GRAPH_RESEARCH, context -> {
            clock.advance(Duration.ofMinutes(2)); return complete("completed");
        }), List.of(), 1, clock)) {
            var result = await(runtime.start(plan(simple("task"))));
            assertEquals("DEADLINE_EXCEEDED", result.tasks().get(0).errorCode());
            assertNull(result.tasks().get(0).artifact());
        }
    }

    @Test void rejectsCyclesDuplicateIdsUnknownDependenciesAndUnauthorizedScopesBeforeAnyModelCall() {
        AtomicInteger calls = new AtomicInteger();
        try (var runtime = runtime(context -> { calls.incrementAndGet(); return complete("completed"); })) {
            assertThrows(IllegalArgumentException.class, () -> runtime.start(plan(simple("x"), simple("x"))));
            assertThrows(IllegalArgumentException.class, () -> runtime.start(plan(dependent("x", "missing", false))));
            assertThrows(IllegalArgumentException.class, () -> runtime.start(plan(dependent("x", "y", false), dependent("y", "x", false))));
            assertThrows(IllegalArgumentException.class, () -> runtime.start(new RunPlan("scope", scope(1), List.of(simple("x")), LIMITS)));
            assertThrows(IllegalArgumentException.class, () -> runtime.start(plan(simple("project"),
                    task("narrow", Role.GRAPH_RESEARCH, scope(1), Set.of("project"), Set.of(), OPTIONAL))));
            assertEquals(0, calls.get());
        }
    }

    @Test void rejectsWriteToolsAndRolePermissionMismatch() {
        assertThrows(IllegalArgumentException.class, () -> new ToolRegistry(List.of(tool(ToolEffect.WRITE, (context, args) -> null))));
        AssistantTool read = tool(ToolEffect.READ_ONLY, (context, args) -> null);
        try (var runtime = new AssistantRuntime(Map.of(Role.LEARNING_COACH, context -> complete("completed")), List.of(read), 1)) {
            assertThrows(IllegalArgumentException.class, () -> runtime.start(plan(
                    task("task", Role.LEARNING_COACH, PROJECT, Set.of(), Set.of("read"), OPTIONAL))));
        }
    }

    @Test void scopeCollectionsAreImmutableAndSelectedDimensionsCannotBeWidened() {
        Scope parent = new Scope(ScopeMode.SELECTED, Set.of(1L), Set.of(10L), Set.of());
        assertFalse(scope(1).isWithin(parent));
        assertFalse(PROJECT.isWithin(parent));
        assertTrue(new Scope(ScopeMode.SELECTED, Set.of(1L), Set.of(10L), Set.of(99L)).isWithin(parent));
        assertThrows(UnsupportedOperationException.class, () -> parent.libraryIds().add(2L));
        assertThrows(IllegalArgumentException.class, () -> new Scope(ScopeMode.SELECTED, Set.of(), Set.of(), Set.of()));
    }

    @Test void closeSettlesQueuedTasksAndRejectsNewRuns() throws Exception {
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        var runtime = new AssistantRuntime(Map.of(Role.GRAPH_RESEARCH, context -> {
            entered.countDown(); assertTrue(release.await(3, TimeUnit.SECONDS)); return complete("completed");
        }), List.of(), 1);
        var handle = runtime.start(plan(simple("active"), simple("queued")));
        try { assertTrue(entered.await(3, TimeUnit.SECONDS)); runtime.close(); }
        finally { release.countDown(); runtime.close(); }
        assertEquals(RunStatus.CANCELLED, await(handle).status());
        assertThrows(IllegalStateException.class, () -> runtime.start(plan(simple("later"))));
    }

    @Test void authorizedScopeIsNotReportedAsObservedCoverage() throws Exception {
        Scope selected = new Scope(ScopeMode.SELECTED, Set.of(1L, 2L), Set.of(), Set.of());
        AssistantTool read = tool(ToolEffect.READ_ONLY, (context, args) -> evidence(scope(1), "src", ToolStatus.OK));
        var policy = new CompletionPolicy(1, true, false, true);
        try (var runtime = runtime(context -> context.observations().isEmpty() ? call("read") : complete("completed", "src"), read)) {
            var result = await(runtime.start(plan(task("source", Role.GRAPH_RESEARCH, selected, Set.of(), Set.of("read"), policy),
                    dependent("compare", "source", false))));
            Artifact artifact = result.tasks().get(0).artifact();
            assertEquals(selected, artifact.authorizedScope());
            assertEquals(List.of(scope(1)), artifact.observedScopes());
            assertFalse(artifact.scopeMatched());
            assertEquals(Outcome.PARTIAL, artifact.outcome());
            assertTrue(artifact.limitations().contains("REQUESTED_SCOPE_NOT_MATCHED"));
            assertEquals(TaskStatus.BLOCKED, result.tasks().get(1).status());
        }
    }

    @Test void aPureDraftNeverClaimsObservedProjectCoverage() throws Exception {
        try (var runtime = runtime(context -> complete("completed"))) {
            Artifact result = await(runtime.start(plan(simple("draft")))).tasks().get(0).artifact();
            assertEquals(PROJECT, result.authorizedScope());
            assertTrue(result.observedScopes().isEmpty());
            assertFalse(result.scopeMatched());
        }
    }

    @Test void actionProposalsDefaultToDeniedUntilDomainValidationIsRegistered() throws Exception {
        try (var runtime = runtime(context -> proposal("2"))) {
            var result = await(runtime.start(plan(task("task", Role.GRAPH_RESEARCH, scope(1), Set.of(), Set.of(), OPTIONAL))));
            assertEquals("DECISION_PROTOCOL_FAILED", result.tasks().get(0).errorCode());
            assertNull(result.tasks().get(0).artifact());
        }
    }

    @Test void domainValidatorRejectsOutOfScopeDraftAndValidatedDraftStillCannotExecute() throws Exception {
        ProposalValidator validator = (task, proposal, evidence) -> {
            if (!proposal.actionType().equals("library.inspect") || proposal.targets().stream().anyMatch(target ->
                    !target.kind().equals("library") || !task.scope().libraryIds().contains(Long.parseLong(target.id())))) {
                throw new IllegalArgumentException("OUT_OF_SCOPE");
            }
        };
        for (String target : List.of("1", "2")) {
            try (var runtime = new AssistantRuntime(Map.of(Role.GRAPH_RESEARCH, context -> proposal(target)), List.of(),
                    Map.of(Role.GRAPH_RESEARCH, validator), 1)) {
                var result = await(runtime.start(plan(task("task", Role.GRAPH_RESEARCH, scope(1), Set.of(), Set.of(), OPTIONAL))));
                if (target.equals("1")) {
                    assertEquals(TaskStatus.COMPLETED, result.tasks().get(0).status());
                    assertFalse(result.tasks().get(0).artifact().actionProposals().get(0).executionAllowed());
                } else {
                    assertEquals(TaskStatus.FAILED, result.tasks().get(0).status());
                    assertNull(result.tasks().get(0).artifact());
                }
            }
        }
    }

    @Test void cancellationRacingFinalResultHasOneConsistentOutcome() throws Exception {
        AtomicReference<CountDownLatch> ready = new AtomicReference<>();
        try (var runtime = new AssistantRuntime(Map.of(Role.GRAPH_RESEARCH, context -> {
            assertTrue(ready.get().await(3, TimeUnit.SECONDS)); return complete("completed");
        }), List.of(), 1)) {
            for (int i = 0; i < 150; i++) {
                CountDownLatch gate = new CountDownLatch(1); ready.set(gate);
                var handle = runtime.start(plan(simple("task")));
                var cancellation = CompletableFuture.supplyAsync(() -> {
                    try { assertTrue(gate.await(3, TimeUnit.SECONDS)); }
                    catch (InterruptedException exception) { throw new AssertionError(exception); }
                    return handle.cancel();
                });
                gate.countDown();
                boolean accepted = cancellation.get(4, TimeUnit.SECONDS);
                var result = await(handle);
                assertEquals(accepted ? RunStatus.CANCELLED : RunStatus.COMPLETED, result.status());
                assertEquals(accepted ? TaskStatus.CANCELLED : TaskStatus.COMPLETED, result.tasks().get(0).status());
            }
        }
    }

    @Test void argumentValidationExpiryPreventsStartingTheTool() throws Exception {
        MutableClock clock = new MutableClock();
        AtomicInteger executions = new AtomicInteger();
        AssistantTool read = new AssistantTool() {
            @Override public ToolDescriptor descriptor() {
                return new ToolDescriptor("read", Set.of(Role.GRAPH_RESEARCH), ToolEffect.READ_ONLY, "fixture", "{}");
            }
            @Override public void validateArguments(JsonNode arguments, Scope scope) { clock.advance(Duration.ofMinutes(2)); }
            @Override public ToolResult execute(Context context, JsonNode arguments) {
                executions.incrementAndGet(); return evidence(PROJECT, "src", ToolStatus.OK);
            }
        };
        try (var runtime = new AssistantRuntime(Map.of(Role.GRAPH_RESEARCH, context -> call("read")), List.of(read), 1, clock)) {
            var result = await(runtime.start(plan(task("task", Role.GRAPH_RESEARCH, PROJECT, Set.of(), Set.of("read"), EVIDENCED))));
            assertEquals("DEADLINE_EXCEEDED", result.tasks().get(0).errorCode());
            assertEquals(0, executions.get());
        }
    }

    @Test void pendingRunsAreBoundedAndCancellationReleasesCapacity() throws Exception {
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        try (var runtime = new AssistantRuntime(Map.of(Role.GRAPH_RESEARCH, context -> {
            entered.countDown(); assertTrue(release.await(3, TimeUnit.SECONDS)); return complete("completed");
        }), List.of(), 1)) {
            List<AssistantRuntime.RunHandle> handles = new ArrayList<>();
            try {
                for (int i = 0; i < 8; i++) handles.add(runtime.start(plan(simple("task"))));
                assertTrue(entered.await(3, TimeUnit.SECONDS));
                assertThrows(IllegalStateException.class, () -> runtime.start(plan(simple("ninth"))));
                handles.forEach(AssistantRuntime.RunHandle::cancel);
            } finally { release.countDown(); }
            for (var handle : handles) assertEquals(RunStatus.CANCELLED, await(handle).status());
            assertEquals(RunStatus.COMPLETED, await(runtime.start(plan(simple("later")))).status());
        }
    }

    @Test void queueSaturationSettlesRejectedTasksWithoutHangingTheRun() throws Exception {
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        List<AgentTask> tasks = new ArrayList<>();
        for (int i = 0; i < 32; i++) tasks.add(simple("task" + i));
        RunPlan plan = new RunPlan("queue", PROJECT, tasks, new Limits(64, 0, Duration.ofSeconds(30)));
        try (var runtime = new AssistantRuntime(Map.of(Role.GRAPH_RESEARCH, context -> {
            entered.countDown(); assertTrue(release.await(3, TimeUnit.SECONDS)); return complete("completed");
        }), List.of(), 1)) {
            List<AssistantRuntime.RunHandle> handles = new ArrayList<>();
            try {
                handles.add(runtime.start(plan)); assertTrue(entered.await(3, TimeUnit.SECONDS));
                handles.add(runtime.start(plan)); handles.add(runtime.start(plan));
                assertTrue(handles.get(2).snapshot().tasks().stream().anyMatch(task -> "QUEUE_FULL".equals(task.errorCode())));
            } finally { release.countDown(); }
            for (var handle : handles) assertTrue(await(handle).tasks().stream().allMatch(task -> task.finishedAt() != null));
        }
    }

    private static String proposal(String target) {
        return """
                {"type":"complete","outcome":"completed","summary":"draft","data":{},
                 "actionProposals":[{"actionType":"library.inspect","targets":[{"kind":"library","id":"%s"}],
                  "parameters":{},"impact":"read-only recommendation","preconditions":[]}]}
                """.formatted(target);
    }

    private static AssistantRuntime runtime(DecisionProvider provider, AssistantTool... tools) {
        return new AssistantRuntime(Map.of(Role.GRAPH_RESEARCH, provider), List.of(tools), 3);
    }
    private static RunPlan plan(AgentTask... tasks) { return new RunPlan("test workflow", PROJECT, List.of(tasks), LIMITS); }
    private static AgentTask simple(String id) { return task(id, Role.GRAPH_RESEARCH, PROJECT, Set.of(), Set.of(), OPTIONAL); }
    private static AgentTask dependent(String id, String dependency, boolean partial) {
        return task(id, Role.GRAPH_RESEARCH, PROJECT, Set.of(dependency), Set.of(), new CompletionPolicy(0, false, partial));
    }
    private static AgentTask task(String id, Role role, Scope scope, Set<String> dependencies, Set<String> tools, CompletionPolicy policy) {
        return new AgentTask(id, role, "task " + id, scope, dependencies, tools, "{}", LIMITS, policy);
    }
    private static Scope scope(long id) { return new Scope(ScopeMode.SELECTED, Set.of(id), Set.of(), Set.of()); }
    private static String call(String name) { return "{\"type\":\"call_tool\",\"name\":\"" + name + "\",\"arguments\":{}}"; }
    private static String complete(String outcome, String... ids) {
        return "{\"type\":\"complete\",\"outcome\":\"" + outcome + "\",\"summary\":\"result\",\"data\":{},\"evidenceIds\":["
                + Arrays.stream(ids).map(id -> "\"" + id + "\"").collect(Collectors.joining(",")) + "]}";
    }
    private static ToolResult evidence(Scope scope, String sourceId, ToolStatus status) {
        return new ToolResult(status, scope, "{}", List.of(new EvidenceRef(sourceId, EvidenceKind.SOURCE,
                new ObjectRef("document", sourceId, "v1"), scope, "page:1", "trusted excerpt")),
                status == ToolStatus.PARTIAL ? List.of("TRUNCATED") : List.of(), null);
    }
    private static RunSnapshot await(AssistantRuntime.RunHandle handle) throws Exception {
        return handle.completion().toCompletableFuture().get(6, TimeUnit.SECONDS);
    }
    @FunctionalInterface private interface Operation { ToolResult apply(AssistantTool.Context context, JsonNode args) throws Exception; }
    private static AssistantTool tool(ToolEffect effect, Operation operation) {
        return new AssistantTool() {
            @Override public ToolDescriptor descriptor() {
                return new ToolDescriptor("read", Set.of(Role.GRAPH_RESEARCH), effect, "fixture read", "{\"type\":\"object\"}");
            }
            @Override public void validateArguments(JsonNode args, Scope scope) {
                if (!args.isObject() || !args.isEmpty()) throw new IllegalArgumentException("No fixture arguments expected");
            }
            @Override public ToolResult execute(Context context, JsonNode args) throws Exception { return operation.apply(context, args); }
        };
    }
    private static final class MutableClock extends Clock {
        private final AtomicReference<Instant> instant = new AtomicReference<>(Instant.parse("2026-09-23T00:00:00Z"));
        void advance(Duration duration) { instant.updateAndGet(value -> value.plus(duration)); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return instant.get(); }
    }
}
