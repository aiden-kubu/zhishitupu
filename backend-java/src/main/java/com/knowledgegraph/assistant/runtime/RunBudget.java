package com.knowledgegraph.assistant.runtime;

import com.knowledgegraph.assistant.contract.AssistantContracts.*;

import java.time.Clock;
import java.time.Instant;

/** Admission is atomic across all tasks in one run. Counts attempted calls, including failed calls. */
final class RunBudget {
    static final class TaskMeter {
        final Limits limits;
        final Instant deadline;
        int modelCalls;
        int toolCalls;
        TaskMeter(Limits limits, Instant deadline) { this.limits = limits; this.deadline = deadline; }
    }
    static final class Stopped extends RuntimeException {
        final String code;
        Stopped(String code) { super(code); this.code = code; }
    }

    private final Limits limits;
    private final Clock clock;
    private final Instant deadline;
    private int modelCalls;
    private int toolCalls;
    private boolean cancelled;

    RunBudget(Limits limits, Clock clock, Instant createdAt) {
        this.limits = limits; this.clock = clock; this.deadline = createdAt.plus(limits.timeout());
    }

    synchronized TaskMeter start(Limits taskLimits) {
        Instant taskDeadline = clock.instant().plus(taskLimits.timeout());
        return new TaskMeter(taskLimits, taskDeadline.isBefore(deadline) ? taskDeadline : deadline);
    }
    synchronized void check(TaskMeter task) {
        if (cancelled) throw new Stopped("CANCELLED");
        if (!clock.instant().isBefore(task.deadline)) throw new Stopped("DEADLINE_EXCEEDED");
    }
    synchronized void model(TaskMeter task) {
        check(task);
        if (modelCalls >= limits.modelCalls() || task.modelCalls >= task.limits.modelCalls()) throw new Stopped("MODEL_CALL_LIMIT");
        modelCalls++; task.modelCalls++;
    }
    synchronized void tool(TaskMeter task) {
        check(task);
        if (toolCalls >= limits.toolCalls() || task.toolCalls >= task.limits.toolCalls()) throw new Stopped("TOOL_CALL_LIMIT");
        toolCalls++; task.toolCalls++;
    }
    synchronized Usage usage() { return new Usage(modelCalls, toolCalls); }
    synchronized Usage usage(TaskMeter task) { return new Usage(task.modelCalls, task.toolCalls); }
    synchronized void cancel() { cancelled = true; }
    synchronized boolean cancelled() { return cancelled; }
    Instant deadline() { return deadline; }
}
