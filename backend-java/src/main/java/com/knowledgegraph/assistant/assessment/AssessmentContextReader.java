package com.knowledgegraph.assistant.assessment;

import com.knowledgegraph.assistant.contract.AssistantContracts.Artifact;
import com.knowledgegraph.assistant.contract.AssistantContracts.Role;
import com.knowledgegraph.assistant.contract.AssistantContracts.ToolObservation;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Host-owned boundary. No production implementation or global artifact lookup is provided in W1. */
public interface AssessmentContextReader {
    record CapturedObservation(String producerTaskId, Role producerRole, ToolObservation observation) {}
    record Snapshot(Set<String> directDependencyTaskIds, Map<String, Role> producerRoles,
                    List<Artifact> artifacts, List<CapturedObservation> capturedObservations) {
        public Snapshot {
            directDependencyTaskIds = Set.copyOf(directDependencyTaskIds);
            producerRoles = Map.copyOf(producerRoles);
            artifacts = List.copyOf(artifacts);
            capturedObservations = List.copyOf(capturedObservations);
        }
    }

    /** Must authenticate both IDs and return only original direct dependencies and captured observations. */
    Optional<Snapshot> snapshot(String runId, String consumerTaskId);
}
