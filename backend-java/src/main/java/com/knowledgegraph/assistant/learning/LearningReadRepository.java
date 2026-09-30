package com.knowledgegraph.assistant.learning;

import com.knowledgegraph.assistant.contract.AssistantContracts.Scope;
import com.knowledgegraph.assistant.research.ResearchReadRepository;

import java.util.List;
import java.util.Optional;

/** Learning-only reads. Every implementation must apply host scope before LIMIT. */
public interface LearningReadRepository {
    enum PathState { NONE, AVAILABLE, UNAVAILABLE }
    record Step(long stepId, String stepKey, long nodeId, int position, String title,
                int estimatedMinutes, String status, String visitedAt, Integer legacyAssessmentScore,
                String updatedAt) {}
    record Path(PathState state, Long pathId, Long libraryId, String goal, String difficulty,
                String status, String updatedAt, String snapshotFingerprint, List<Step> steps) {
        public Path { steps = List.copyOf(steps); }
        public static Path none() { return new Path(PathState.NONE, null, null, null, null, null, null, null, List.of()); }
        public static Path unavailable() {
            return new Path(PathState.UNAVAILABLE, null, null, null, null, null, null, null, List.of());
        }
    }
    record Graph(List<ResearchReadRepository.Node> nodes, List<ResearchReadRepository.Edge> edges,
                 boolean nodesTruncated, boolean edgesTruncated) {
        public Graph { nodes = List.copyOf(nodes); edges = List.copyOf(edges); }
    }

    Path currentPath(Scope scope);
    Graph graph(Scope scope, int maxNodes, int maxEdges);
    Optional<ResearchReadRepository.Node> node(Scope scope, long nodeId);
    Optional<ResearchReadRepository.Edge> edge(Scope scope, long edgeId);
    boolean libraryVisible(Scope scope, long libraryId);
    boolean nodeSourceLinked(Scope scope, long nodeId, long documentId, long chunkId);
}
