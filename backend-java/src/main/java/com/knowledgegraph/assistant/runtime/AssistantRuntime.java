package com.knowledgegraph.assistant.runtime;

import com.knowledgegraph.assistant.contract.AssistantContracts.*;
import com.knowledgegraph.assistant.tool.AssistantTool;
import com.knowledgegraph.assistant.tool.ToolRegistry;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Bounded, in-memory W1 runtime. Not a Spring service or a public execution endpoint yet.
 * Independent tasks may run concurrently; dependents receive only explicit artifact inputs.
 * Cancellation is cooperative: adapters must enforce deadlines on blocking I/O themselves.
 */
public final class AssistantRuntime implements AutoCloseable {
    private final Map<Role, DecisionProvider> providers;
    private final ToolRegistry tools;
    private final DecisionParser parser = new DecisionParser();
    private final Clock clock;
    private final AgentRunner runner;
    private final ThreadPoolExecutor workers;
    private final Map<String, RunHandle> active = new ConcurrentHashMap<>();
    private boolean closed;

    public AssistantRuntime(Map<Role, DecisionProvider> providers, List<AssistantTool> tools, int parallelism) {
        this(providers, tools, Map.of(), parallelism, Clock.systemUTC());
    }

    public AssistantRuntime(Map<Role, DecisionProvider> providers, List<AssistantTool> tools,
                            Map<Role, ProposalValidator> proposalValidators, int parallelism) {
        this(providers, tools, proposalValidators, parallelism, Clock.systemUTC());
    }

    AssistantRuntime(Map<Role, DecisionProvider> providers, List<AssistantTool> tools, int parallelism, Clock clock) {
        this(providers, tools, Map.of(), parallelism, clock);
    }

    private AssistantRuntime(Map<Role, DecisionProvider> providers, List<AssistantTool> tools,
                             Map<Role, ProposalValidator> proposalValidators, int parallelism, Clock clock) {
        if (parallelism < 1 || parallelism > 8) throw new IllegalArgumentException("Parallelism must be 1 to 8");
        this.providers = Map.copyOf(providers); this.tools = new ToolRegistry(tools, clock); this.clock = clock;
        runner = new AgentRunner(this.tools, parser, clock, Map.copyOf(proposalValidators));
        AtomicInteger threadIds = new AtomicInteger();
        workers = new ThreadPoolExecutor(parallelism, parallelism, 0, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(64), runnable -> {
                    Thread thread = new Thread(runnable, "assistant-worker-" + threadIds.incrementAndGet());
                    thread.setDaemon(true);
                    return thread;
                }, new ThreadPoolExecutor.AbortPolicy());
    }

    public synchronized RunHandle start(RunPlan plan) {
        if (closed) throw new IllegalStateException("Assistant runtime is closed");
        if (active.size() >= 8) throw new IllegalStateException("Too many active assistant runs");
        validate(plan);
        RunHandle handle = new RunHandle(plan);
        active.put(handle.runId, handle);
        handle.launch();
        return handle;
    }

    private void validate(RunPlan plan) {
        Map<String, AgentTask> tasks = new LinkedHashMap<>();
        for (AgentTask task : plan.tasks()) {
            if (tasks.putIfAbsent(task.taskId(), task) != null) throw new IllegalArgumentException("Duplicate task ID");
            if (!task.scope().isWithin(plan.scope())) throw new IllegalArgumentException("Task scope exceeds run scope");
            if (!providers.containsKey(task.role())) throw new IllegalArgumentException("Missing decision provider");
            if (!parser.json(task.inputJson(), 16000).isObject()) throw new IllegalArgumentException("Task input must be an object");
            tools.validate(task);
        }
        Map<String, Integer> degrees = new LinkedHashMap<>();
        Map<String, List<String>> children = new LinkedHashMap<>();
        for (AgentTask task : plan.tasks()) {
            degrees.put(task.taskId(), task.dependencyIds().size());
            for (String id : task.dependencyIds()) {
                AgentTask dependency = tasks.get(id);
                if (dependency == null || id.equals(task.taskId())) throw new IllegalArgumentException("Invalid dependency");
                if (!dependency.scope().isWithin(task.scope())) throw new IllegalArgumentException("Dependency exceeds consumer scope");
                children.computeIfAbsent(id, ignored -> new ArrayList<>()).add(task.taskId());
            }
        }
        ArrayDeque<String> ready = new ArrayDeque<>();
        degrees.forEach((id, degree) -> { if (degree == 0) ready.add(id); });
        int visited = 0;
        while (!ready.isEmpty()) {
            String id = ready.remove(); visited++;
            for (String child : children.getOrDefault(id, List.of())) {
                if (degrees.compute(child, (key, degree) -> degree - 1) == 0) ready.add(child);
            }
        }
        if (visited != tasks.size()) throw new IllegalArgumentException("Dependency cycle");
    }

    @Override public void close() {
        List<RunHandle> pending;
        synchronized (this) { closed = true; pending = List.copyOf(active.values()); }
        pending.forEach(RunHandle::cancel);
        // Do not discard queued jobs: they must settle their task futures as cancelled.
        workers.shutdown();
    }

    public final class RunHandle {
        private final String runId = UUID.randomUUID().toString();
        private final RunPlan plan;
        private final Instant createdAt = clock.instant();
        private final RunBudget budget;
        private final Map<String, Slot> slots = new LinkedHashMap<>();
        private final CompletableFuture<RunSnapshot> completion = new CompletableFuture<>();
        private boolean finished;

        private RunHandle(RunPlan plan) {
            this.plan = plan; budget = new RunBudget(plan.limits(), clock, createdAt);
            plan.tasks().forEach(task -> slots.put(task.taskId(), new Slot(task)));
        }

        private void launch() {
            CompletableFuture.allOf(slots.values().stream().map(slot -> slot.done).toArray(CompletableFuture[]::new))
                    .thenRun(this::finishRun);
            for (Slot slot : slots.values()) {
                CompletableFuture.allOf(slot.task.dependencyIds().stream().map(id -> slots.get(id).done)
                        .toArray(CompletableFuture[]::new)).thenRun(() -> enqueue(slot));
            }
        }

        private void enqueue(Slot slot) {
            if (budget.cancelled()) { settle(slot, TaskStatus.CANCELLED, "CANCELLED"); return; }
            List<Artifact> inputs = new ArrayList<>();
            for (String dependencyId : slot.task.dependencyIds().stream().sorted().toList()) {
                TaskSnapshot dependency = slots.get(dependencyId).snapshot;
                if (dependency.status() != TaskStatus.COMPLETED && !(dependency.status() == TaskStatus.PARTIAL
                        && slot.task.completion().allowPartialDependencies())) {
                    settle(slot, TaskStatus.BLOCKED, "DEPENDENCY_NOT_SATISFIED"); return;
                }
                inputs.add(dependency.artifact());
            }
            try {
                workers.execute(() -> {
                    Instant startedAt = clock.instant();
                    slot.meter = budget.start(slot.task.limits());
                    slot.snapshot = new TaskSnapshot(slot.task.taskId(), slot.task.role(), TaskStatus.RUNNING,
                            null, null, budget.usage(slot.meter), startedAt, null);
                    TaskSnapshot result = runner.run(runId, slot.task, List.copyOf(inputs), providers.get(slot.task.role()),
                            budget, slot.meter, startedAt);
                    publish(slot, result);
                });
            } catch (RejectedExecutionException ignored) {
                settle(slot, budget.cancelled() ? TaskStatus.CANCELLED : TaskStatus.FAILED,
                        budget.cancelled() ? "CANCELLED" : "QUEUE_FULL");
            }
        }

        private void settle(Slot slot, TaskStatus status, String code) {
            publish(slot, new TaskSnapshot(slot.task.taskId(), slot.task.role(), status, null, code,
                    new Usage(0, 0), null, clock.instant()));
        }

        private void publish(Slot slot, TaskSnapshot result) {
            synchronized (this) {
                // Cancellation and accepting a final result share the same linearization point.
                slot.snapshot = budget.cancelled()
                        ? new TaskSnapshot(result.taskId(), result.role(), TaskStatus.CANCELLED, null, "CANCELLED",
                            result.usage(), result.startedAt(), clock.instant()) : result;
            }
            // Never run user completion callbacks while holding the handle monitor.
            slot.done.complete(null);
        }

        /** An already completed run is never relabelled cancelled. In-flight adapters may still be returning. */
        public synchronized boolean cancel() {
            if (finished || slots.values().stream().allMatch(slot -> slot.snapshot.finishedAt() != null)) return false;
            budget.cancel();
            return true;
        }

        public CompletionStage<RunSnapshot> completion() { return completion.minimalCompletionStage(); }

        public synchronized RunSnapshot snapshot() {
            List<TaskSnapshot> snapshots = slots.values().stream().map(slot -> {
                TaskSnapshot value = slot.snapshot;
                RunBudget.TaskMeter meter = slot.meter;
                if (meter == null || value.status() != TaskStatus.RUNNING) return value;
                return new TaskSnapshot(value.taskId(), value.role(), value.status(), value.artifact(), value.errorCode(),
                        budget.usage(meter), value.startedAt(), value.finishedAt());
            }).toList();
            RunStatus status;
            if (!finished) status = budget.cancelled() ? RunStatus.CANCELLING : RunStatus.RUNNING;
            else if (budget.cancelled()) status = RunStatus.CANCELLED;
            else if (snapshots.stream().allMatch(task -> task.status() == TaskStatus.COMPLETED)) status = RunStatus.COMPLETED;
            else if (snapshots.stream().anyMatch(task -> task.status() == TaskStatus.NEEDS_INPUT)) status = RunStatus.NEEDS_INPUT;
            else if (snapshots.stream().anyMatch(task -> Set.of(TaskStatus.COMPLETED, TaskStatus.PARTIAL).contains(task.status()))) status = RunStatus.PARTIAL;
            else status = RunStatus.FAILED;
            return new RunSnapshot(runId, status, plan.scope(), snapshots, budget.usage(), createdAt,
                    budget.deadline(), budget.cancelled());
        }

        private void finishRun() {
            RunSnapshot result;
            synchronized (this) { finished = true; result = snapshot(); }
            active.remove(runId);
            completion.complete(result);
        }

        private final class Slot {
            final AgentTask task;
            final CompletableFuture<Void> done = new CompletableFuture<>();
            volatile TaskSnapshot snapshot;
            volatile RunBudget.TaskMeter meter;
            Slot(AgentTask task) {
                this.task = task;
                snapshot = new TaskSnapshot(task.taskId(), task.role(), TaskStatus.PENDING, null, null,
                        new Usage(0, 0), null, null);
            }
        }
    }
}
