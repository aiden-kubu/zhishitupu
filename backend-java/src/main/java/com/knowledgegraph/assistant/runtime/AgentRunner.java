package com.knowledgegraph.assistant.runtime;

import com.knowledgegraph.assistant.contract.AssistantContracts.*;
import com.knowledgegraph.assistant.tool.AssistantTool;
import com.knowledgegraph.assistant.tool.ToolRegistry;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** One isolated decision/tool loop per task; shared state is limited to the run budget. */
final class AgentRunner {
    private final ToolRegistry tools;
    private final DecisionParser parser;
    private final Clock clock;
    private final Map<Role, ProposalValidator> proposalValidators;

    AgentRunner(ToolRegistry tools, DecisionParser parser, Clock clock, Map<Role, ProposalValidator> proposalValidators) {
        this.tools = tools; this.parser = parser; this.clock = clock; this.proposalValidators = proposalValidators;
    }

    TaskSnapshot run(String runId, AgentTask task, List<Artifact> inputs, DecisionProvider provider,
                     RunBudget budget, RunBudget.TaskMeter meter, Instant startedAt) {
        List<ToolObservation> observations = new ArrayList<>();
        List<String> protocolErrors = new ArrayList<>();
        Map<String, EvidenceRef> known = new LinkedHashMap<>();
        Set<String> fresh = new LinkedHashSet<>();
        Set<String> inheritedLimitations = new LinkedHashSet<>();
        Set<Scope> observedScopes = new LinkedHashSet<>();
        boolean degraded = inputs.stream().anyMatch(input -> input.outcome() != Outcome.COMPLETED);
        int successfulTools = 0;
        try {
            for (Artifact input : inputs) {
                mergeEvidence(known, input.evidence());
                inheritedLimitations.addAll(input.limitations());
                observedScopes.addAll(input.observedScopes());
            }
            if (degraded) inheritedLimitations.add("PARTIAL_DEPENDENCY");
            List<ToolDescriptor> available = tools.available(task);
            while (true) {
                budget.check(meter);
                if (task.toString().length() + inputs.toString().length() + observations.toString().length()
                        + available.toString().length() > 128000) throw new RunBudget.Stopped("CONTEXT_SIZE_LIMIT");
                budget.model(meter);
                String raw;
                try {
                    raw = provider.decide(new DecisionProvider.Context(runId, task, inputs, observations, available,
                            protocolErrors, budget.usage(meter), meter.deadline, budget::cancelled));
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                    throw new RunBudget.Stopped("MODEL_INTERRUPTED");
                } catch (Exception ignored) {
                    budget.check(meter);
                    throw new RunBudget.Stopped("MODEL_FAILED");
                }
                budget.check(meter);
                DecisionParser.Decision decision;
                try {
                    decision = parser.parse(raw);
                } catch (IllegalArgumentException ignored) {
                    repair(protocolErrors, "INVALID_DECISION");
                    continue;
                }
                if (decision instanceof DecisionParser.CallTool call) {
                    budget.tool(meter);
                    ToolResult result = tools.invoke(task, call.name(), call.arguments(), new AssistantTool.Context(
                            runId, task.taskId(), task.role(), task.scope(), meter.deadline, budget::cancelled));
                    budget.check(meter);
                    try {
                        mergeEvidence(known, result.evidence());
                    } catch (RunBudget.Stopped ignored) {
                        result = ToolResult.error(task.scope(), "EVIDENCE_ID_CONFLICT");
                    }
                    if (result.status() != ToolStatus.ERROR) {
                        successfulTools++;
                        observedScopes.add(result.effectiveScope());
                        for (EvidenceRef ref : result.evidence()) fresh.add(ref.sourceId());
                    }
                    if (result.status() != ToolStatus.OK) degraded = true;
                    inheritedLimitations.addAll(result.limitations());
                    if (result.errorCode() != null) inheritedLimitations.add(result.errorCode());
                    observations.add(new ToolObservation(UUID.randomUUID().toString(), call.name(), clock.instant(), result));
                    continue;
                }
                DecisionParser.Finish finish = (DecisionParser.Finish) decision;
                boolean success = finish.outcome() == Outcome.COMPLETED || finish.outcome() == Outcome.PARTIAL;
                boolean missingEvidence = !known.keySet().containsAll(finish.evidenceIds());
                boolean missingWork = success && (successfulTools < task.completion().minSuccessfulTools()
                        || (task.completion().requireFreshEvidence() && finish.evidenceIds().stream().noneMatch(fresh::contains)));
                if (missingEvidence || missingWork) {
                    repair(protocolErrors, missingEvidence ? "UNKNOWN_EVIDENCE_REFERENCE" : "COMPLETION_POLICY_UNMET");
                    continue;
                }
                List<EvidenceRef> references = finish.evidenceIds().stream().map(known::get).toList();
                if (!validProposals(task, finish.proposals(), references)) {
                    repair(protocolErrors, "INVALID_ACTION_PROPOSAL");
                    continue;
                }
                if (success && task.completion().requireMatchingScope() && !observedScopes.contains(task.scope())) {
                    degraded = true;
                    inheritedLimitations.add("REQUESTED_SCOPE_NOT_MATCHED");
                }
                Outcome outcome = finish.outcome() == Outcome.COMPLETED && degraded ? Outcome.PARTIAL : finish.outcome();
                Set<String> limitations = new LinkedHashSet<>(finish.limitations());
                limitations.addAll(inheritedLimitations);
                if (degraded) limitations.add("UPSTREAM_OR_TOOL_RESULT_INCOMPLETE");
                if (limitations.size() > 64) throw new RunBudget.Stopped("LIMITATIONS_SIZE_LIMIT");
                Artifact artifact = new Artifact(UUID.randomUUID().toString(), task.taskId(), task.scope(), List.copyOf(observedScopes), outcome,
                        finish.summary(), finish.dataJson(), references,
                        finish.proposals(), List.copyOf(limitations), clock.instant());
                budget.check(meter);
                return new TaskSnapshot(task.taskId(), task.role(), TaskStatus.valueOf(outcome.name()), artifact,
                        outcome == Outcome.FAILED ? "AGENT_REPORTED_FAILURE" : null, budget.usage(meter), startedAt, clock.instant());
            }
        } catch (RunBudget.Stopped stopped) {
            return failed(task, budget, meter, startedAt, stopped.code);
        } catch (RuntimeException ignored) {
            return failed(task, budget, meter, startedAt, "INTERNAL_TASK_FAILURE");
        }
    }

    private boolean validProposals(AgentTask task, List<ActionProposal> proposals, List<EvidenceRef> evidence) {
        if (proposals.isEmpty()) return true;
        ProposalValidator validator = proposalValidators.get(task.role());
        if (validator == null) return false;
        try {
            for (ActionProposal proposal : proposals) validator.validate(task, proposal, evidence);
            return true;
        } catch (RuntimeException ignored) { return false; }
    }

    private TaskSnapshot failed(AgentTask task, RunBudget budget, RunBudget.TaskMeter meter, Instant start, String code) {
        return new TaskSnapshot(task.taskId(), task.role(), code.equals("CANCELLED") ? TaskStatus.CANCELLED : TaskStatus.FAILED,
                null, code, budget.usage(meter), start, clock.instant());
    }
    private static void repair(List<String> errors, String code) {
        errors.add(code);
        if (errors.size() >= 2) throw new RunBudget.Stopped("DECISION_PROTOCOL_FAILED");
    }
    private static void mergeEvidence(Map<String, EvidenceRef> known, List<EvidenceRef> incoming) {
        // Validate the entire batch before changing the known-evidence set.
        Map<String, EvidenceRef> batch = new LinkedHashMap<>();
        for (EvidenceRef ref : incoming) {
            EvidenceRef previous = known.get(ref.sourceId());
            EvidenceRef duplicate = batch.putIfAbsent(ref.sourceId(), ref);
            if ((previous != null && !previous.equals(ref)) || (duplicate != null && !duplicate.equals(ref))) {
                throw new RunBudget.Stopped("EVIDENCE_ID_CONFLICT");
            }
        }
        known.putAll(batch);
    }
}
