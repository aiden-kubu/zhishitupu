package com.knowledgegraph.assistant.runtime;

import com.knowledgegraph.assistant.contract.AssistantContracts.*;

import java.time.Instant;
import java.util.List;
import java.util.function.BooleanSupplier;

/** Adapter boundary; production adapters must enforce request timeouts and never invoke tools themselves. */
@FunctionalInterface
public interface DecisionProvider {
    String decide(Context context) throws Exception;

    record Context(String runId, AgentTask task, List<Artifact> inputs, List<ToolObservation> observations,
                   List<ToolDescriptor> availableTools, List<String> protocolErrors, Usage usage,
                   Instant deadline, BooleanSupplier cancellationRequested) {
        public Context {
            inputs = List.copyOf(inputs); observations = List.copyOf(observations);
            availableTools = List.copyOf(availableTools); protocolErrors = List.copyOf(protocolErrors);
        }
    }
}
