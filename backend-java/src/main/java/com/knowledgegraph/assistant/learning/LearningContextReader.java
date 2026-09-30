package com.knowledgegraph.assistant.learning;

import com.knowledgegraph.assistant.contract.AssistantContracts.Artifact;
import com.knowledgegraph.assistant.contract.AssistantContracts.Role;
import com.knowledgegraph.assistant.contract.AssistantContracts.ToolObservation;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Host-held read boundary. A production implementation is outside W1.
 * Implementations must authenticate runId/consumerTaskId and expose only original direct dependencies
 * and captured quality-tool observations, never model-supplied summaries or arbitrary artifact IDs.
 */
public interface LearningContextReader {
    record SourceCheck(String originalSourceId, long chunkId, String expectedHash,
                       String actualHash, String status) {}
    record ClaimCheck(String claimId, String referenceStatus, String semanticSupport,
                      List<SourceCheck> sources) {
        public ClaimCheck { sources = List.copyOf(sources); }
    }
    record QualityObservation(String qualityTaskId, String artifactId, String producerTaskId, List<ClaimCheck> checks,
                              int requestedClaims, int currentClaims, boolean completeCoverage,
                              Instant observedAt) {
        public QualityObservation { checks = List.copyOf(checks); }
    }
    record Snapshot(Set<String> directDependencyTaskIds, Map<String, Role> producerRoles,
                    List<Artifact> artifacts,
                    List<ToolObservation> toolObservations, List<QualityObservation> qualityObservations) {
        public Snapshot {
            directDependencyTaskIds = Set.copyOf(directDependencyTaskIds);
            producerRoles = Map.copyOf(producerRoles);
            artifacts = List.copyOf(artifacts);
            toolObservations = List.copyOf(toolObservations);
            qualityObservations = List.copyOf(qualityObservations);
        }
    }
    Optional<Snapshot> snapshot(String runId, String consumerTaskId);
}
