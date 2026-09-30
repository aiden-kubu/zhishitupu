package com.knowledgegraph.assistant.research;

import com.knowledgegraph.assistant.contract.AssistantContracts.Scope;

import java.util.List;
import java.util.Optional;

/** Read-only graph and source metadata. Implementations must filter scope before LIMIT/COUNT. */
public interface ResearchReadRepository {
    record Window<T>(List<T> items, boolean hasMore) {
        public Window { items = List.copyOf(items); }
    }
    record Node(long id, String name, String nameEn, String type, String definition, String status,
                String updatedAt, String origin, String reviewStatus, List<Long> libraryIds,
                List<String> aliases, String matchedBy, boolean sourceEvidenceAvailable) {
        public Node { libraryIds = List.copyOf(libraryIds); aliases = List.copyOf(aliases); }
        public Node(long id, String name, String nameEn, String type, String definition, String status,
                    String updatedAt, String origin, String reviewStatus, List<Long> libraryIds,
                    List<String> aliases, String matchedBy) {
            this(id, name, nameEn, type, definition, status, updatedAt, origin, reviewStatus,
                    libraryIds, aliases, matchedBy, false);
        }
    }
    record Edge(long id, long sourceNodeId, long targetNodeId, String relationType, String status,
                String updatedAt, String origin, String reviewStatus, boolean sourceEvidenceAvailable) {}
    record SourceMeta(long documentId, long chunkId, long unitId, int unitIndex, String unitType,
                      String sourceLocator, String contentHash, String documentStatus,
                      String lifecycleStatus, String unitStatus) {
        public boolean formalAvailable() {
            return "COMPLETED".equals(documentStatus)
                    && (lifecycleStatus == null || "active".equals(lifecycleStatus))
                    && ("READY".equals(unitStatus) || "OCR_OK".equals(unitStatus));
        }
    }
    record Candidate(long documentId, long chunkId, String contentHash, int matchScore) {}

    Window<Node> searchNodes(Scope scope, String query, int limit);
    Optional<Node> node(Scope scope, long nodeId);
    Window<Edge> incidentEdges(Scope scope, long nodeId, int limit);
    Window<Edge> outgoingEdges(Scope scope, long nodeId, int limit);
    Window<Long> matchingDocuments(Scope scope, List<String> terms, int limit);
    Window<Candidate> candidates(Scope scope, long documentId, List<String> terms, int limit);
    Optional<SourceMeta> sourceMeta(Scope scope, long documentId, long chunkId);
    boolean libraryExistsInScope(Scope scope, long libraryId);
}
