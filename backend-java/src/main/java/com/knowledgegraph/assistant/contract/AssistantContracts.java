package com.knowledgegraph.assistant.contract;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/** Shared, immutable W1 contracts. Domain payloads are JSON data, never execution receipts. */
public final class AssistantContracts {
    private AssistantContracts() {}

    public interface WireEnum {
        @JsonValue default String wireValue() { return ((Enum<?>) this).name().toLowerCase(Locale.ROOT); }
    }
    public enum Role implements WireEnum {
        INGESTION, QUALITY_GOVERNANCE, GRAPH_RESEARCH, LEARNING_COACH, ASSESSMENT_ANALYSIS
    }
    public enum ScopeMode implements WireEnum { PROJECT, SELECTED }
    public enum Outcome implements WireEnum { COMPLETED, PARTIAL, NEEDS_INPUT, FAILED }
    public enum TaskStatus implements WireEnum { PENDING, RUNNING, COMPLETED, PARTIAL, NEEDS_INPUT, FAILED, BLOCKED, CANCELLED }
    public enum RunStatus implements WireEnum { RUNNING, CANCELLING, COMPLETED, PARTIAL, NEEDS_INPUT, FAILED, CANCELLED }
    public enum ToolEffect implements WireEnum { READ_ONLY, DRAFT_ONLY, WRITE }
    public enum ToolStatus implements WireEnum { OK, PARTIAL, ERROR }
    public enum EvidenceKind implements WireEnum { SOURCE, SYSTEM_STATE, GRAPH_FACT }

    /** Selected dimensions are conjunctive. Empty dimensions do not mean an empty result set. */
    public record Scope(ScopeMode mode, Set<Long> libraryIds, Set<Long> documentIds, Set<Long> nodeIds) {
        public Scope {
            Objects.requireNonNull(mode, "scope mode");
            libraryIds = ids(libraryIds); documentIds = ids(documentIds); nodeIds = ids(nodeIds);
            boolean empty = libraryIds.isEmpty() && documentIds.isEmpty() && nodeIds.isEmpty();
            if ((mode == ScopeMode.PROJECT) != empty) throw new IllegalArgumentException("Scope must be explicit");
        }
        public static Scope project() { return new Scope(ScopeMode.PROJECT, Set.of(), Set.of(), Set.of()); }
        public boolean isWithin(Scope parent) {
            if (parent.mode == ScopeMode.PROJECT) return true;
            return mode == ScopeMode.SELECTED && constrainedWithin(libraryIds, parent.libraryIds)
                    && constrainedWithin(documentIds, parent.documentIds) && constrainedWithin(nodeIds, parent.nodeIds);
        }
        private static boolean constrainedWithin(Set<Long> child, Set<Long> parent) {
            return parent.isEmpty() || (!child.isEmpty() && parent.containsAll(child));
        }
    }

    public record ObjectRef(String kind, String id, String version) {
        public ObjectRef { kind = text(kind, 80); id = text(id, 128); version = nullableText(version, 256); }
    }
    public record EvidenceRef(String sourceId, EvidenceKind kind, ObjectRef object, Scope scope,
                              String locator, String excerpt) {
        public EvidenceRef {
            sourceId = text(sourceId, 256); Objects.requireNonNull(kind); Objects.requireNonNull(object);
            Objects.requireNonNull(scope); locator = nullableText(locator, 512); excerpt = nullableText(excerpt, 2048);
        }
    }

    /** W1 proposals cannot carry an execution authorization or claim a write happened. */
    public record ActionProposal(String actionType, List<ObjectRef> targets, String parametersJson,
                                 String impact, List<String> preconditions) {
        public ActionProposal {
            actionType = text(actionType, 100); targets = List.copyOf(targets);
            parametersJson = text(parametersJson, 8000); impact = text(impact, 2000);
            preconditions = strings(preconditions, 30, 1000);
            if (targets.size() > 100) throw new IllegalArgumentException("Too many proposal targets");
        }
        @JsonProperty public boolean executionAllowed() { return false; }
    }

    public record Limits(int modelCalls, int toolCalls, Duration timeout) {
        public Limits {
            Objects.requireNonNull(timeout);
            if (modelCalls < 0 || modelCalls > 256 || toolCalls < 0 || toolCalls > 256
                    || timeout.isNegative() || timeout.isZero() || timeout.compareTo(Duration.ofHours(1)) > 0) {
                throw new IllegalArgumentException("Invalid assistant limits");
            }
        }
    }
    public record CompletionPolicy(int minSuccessfulTools, boolean requireFreshEvidence, boolean allowPartialDependencies,
                                   boolean requireMatchingScope) {
        public CompletionPolicy(int minSuccessfulTools, boolean requireFreshEvidence, boolean allowPartialDependencies) {
            this(minSuccessfulTools, requireFreshEvidence, allowPartialDependencies, false);
        }
        public CompletionPolicy {
            if (minSuccessfulTools < 0 || minSuccessfulTools > 256) throw new IllegalArgumentException("Invalid completion policy");
        }
    }
    public record AgentTask(String taskId, Role role, String objective, Scope scope, Set<String> dependencyIds,
                            Set<String> permittedTools, String inputJson, Limits limits, CompletionPolicy completion) {
        public AgentTask {
            taskId = identifier(taskId); Objects.requireNonNull(role); objective = text(objective, 4000);
            Objects.requireNonNull(scope); dependencyIds = Set.copyOf(dependencyIds); permittedTools = Set.copyOf(permittedTools);
            dependencyIds.forEach(AssistantContracts::identifier); permittedTools.forEach(AssistantContracts::identifier);
            inputJson = text(inputJson, 16000); Objects.requireNonNull(limits); Objects.requireNonNull(completion);
            if (dependencyIds.size() > 32 || permittedTools.size() > 64 || completion.minSuccessfulTools > limits.toolCalls) {
                throw new IllegalArgumentException("Task exceeds supported limits");
            }
        }
    }
    public record RunPlan(String goal, Scope scope, List<AgentTask> tasks, Limits limits) {
        public RunPlan {
            goal = text(goal, 4000); Objects.requireNonNull(scope); tasks = List.copyOf(tasks); Objects.requireNonNull(limits);
            if (tasks.isEmpty() || tasks.size() > 32) throw new IllegalArgumentException("A run needs 1 to 32 tasks");
        }
    }

    public record ToolDescriptor(String name, Set<Role> roles, ToolEffect effect, String description, String argumentsSchemaJson) {
        public ToolDescriptor {
            name = identifier(name); roles = Set.copyOf(roles); Objects.requireNonNull(effect);
            description = text(description, 2000); argumentsSchemaJson = text(argumentsSchemaJson, 8000);
            if (roles.isEmpty()) throw new IllegalArgumentException("Tool requires an allowed role");
        }
    }
    public record ToolResult(ToolStatus status, Scope effectiveScope, String dataJson,
                             List<EvidenceRef> evidence, List<String> limitations, String errorCode) {
        public ToolResult {
            Objects.requireNonNull(status); Objects.requireNonNull(effectiveScope); dataJson = text(dataJson, 32000);
            evidence = List.copyOf(evidence); limitations = strings(limitations, 40, 1000);
            errorCode = nullableText(errorCode, 100);
            if (evidence.size() > 64 || (status == ToolStatus.ERROR) != (errorCode != null)
                    || (status == ToolStatus.ERROR && !evidence.isEmpty())) {
                throw new IllegalArgumentException("Invalid tool result");
            }
        }
        public static ToolResult error(Scope scope, String code) {
            return new ToolResult(ToolStatus.ERROR, scope, "{}", List.of(), List.of(), code);
        }
    }
    public record ToolObservation(String toolCallId, String toolName, Instant observedAt, ToolResult result) {}
    public record Usage(int modelCalls, int toolCalls) {}
    /** Authorized scope is a ceiling, not proof of coverage. Observed scopes come only from trusted tool results. */
    public record Artifact(String artifactId, String producerTaskId, Scope authorizedScope, List<Scope> observedScopes, Outcome outcome,
                           String summary, String dataJson, List<EvidenceRef> evidence,
                           List<ActionProposal> actionProposals, List<String> limitations, Instant createdAt) {
        public Artifact {
            artifactId = identifier(artifactId); producerTaskId = identifier(producerTaskId);
            Objects.requireNonNull(authorizedScope); observedScopes = List.copyOf(observedScopes);
            if (observedScopes.size() > 256 || observedScopes.stream().anyMatch(scope -> !scope.isWithin(authorizedScope))) {
                throw new IllegalArgumentException("Invalid observed scopes");
            }
            Objects.requireNonNull(outcome); summary = text(summary, 4000);
            dataJson = text(dataJson, 16000); evidence = List.copyOf(evidence); actionProposals = List.copyOf(actionProposals);
            limitations = strings(limitations, 64, 1000); Objects.requireNonNull(createdAt);
            if (evidence.size() > 256 || actionProposals.size() > 10) throw new IllegalArgumentException("Artifact too large");
        }
        @JsonProperty public boolean executionReceipt() { return false; }
        /** Exact scope match is not proof of exhaustive retrieval or claim accuracy. */
        @JsonProperty public boolean scopeMatched() { return observedScopes.contains(authorizedScope); }
    }
    public record TaskSnapshot(String taskId, Role role, TaskStatus status, Artifact artifact,
                               String errorCode, Usage usage, Instant startedAt, Instant finishedAt) {}
    public record RunSnapshot(String runId, RunStatus status, Scope authorizedScope, List<TaskSnapshot> tasks,
                              Usage usage, Instant createdAt, Instant deadline, boolean cancellationRequested) {
        public RunSnapshot { tasks = List.copyOf(tasks); }
        @JsonProperty public boolean recoverableAfterRestart() { return false; }
    }

    public static String identifier(String value) {
        if (value == null || !value.matches("[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}")) throw new IllegalArgumentException("Invalid identifier");
        return value;
    }
    public static String text(String value, int max) {
        if (value == null || value.isBlank() || value.length() > max) throw new IllegalArgumentException("Invalid text size");
        return value;
    }
    private static String nullableText(String value, int max) { return value == null ? null : text(value, max); }
    private static Set<Long> ids(Set<Long> values) {
        Set<Long> result = Set.copyOf(values);
        if (result.size() > 1000 || result.stream().anyMatch(id -> id <= 0)) throw new IllegalArgumentException("Invalid scope IDs");
        return result;
    }
    private static List<String> strings(List<String> values, int count, int length) {
        List<String> result = List.copyOf(values);
        if (result.size() > count) throw new IllegalArgumentException("Too many values");
        result.forEach(value -> text(value, length));
        return result;
    }
}
