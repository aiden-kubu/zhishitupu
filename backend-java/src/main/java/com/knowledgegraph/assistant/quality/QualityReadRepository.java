package com.knowledgegraph.assistant.quality;

import com.knowledgegraph.assistant.contract.AssistantContracts.Scope;

import java.util.List;
import java.util.Optional;

/** Read boundary for quality tools. Implementations must apply scope before LIMIT and aggregation. */
public interface QualityReadRepository {
    record Job(long id, long documentId, String stage, String status, String documentStatus) {}
    record AiDecision(Boolean approved, String reason, String model, String reviewedAt) {}
    record Candidate(long id, long jobId, String kind, String name, String tempKey,
                     String sourceTempKey, String targetTempKey, String relationType,
                     String reviewStatus, List<Long> evidenceChunkIds, Long matchedNodeId,
                     Long matchedEdgeId, AiDecision aiDecision) {}
    record Page<T>(List<T> items, long total, int page, int pageSize) {
        public Page { items = List.copyOf(items); }
        public boolean hasMore() { return (long) page * pageSize < total; }
        public boolean completeCoverage() { return page == 1 && items.size() == total; }
    }
    record UnitMeta(long documentId, long unitId, int unitIndex, String locator) {}
    record NodeMatch(long id, String name, String status) {}
    record EdgeMatch(long id, long sourceNodeId, long targetNodeId, String relationType, String status) {}
    record TypeCount(String type, long count) {}
    record NodeItem(long id, String name, String type, long degree) {}
    record Insight(long nodeCount, long edgeCount, long documentCount, long pendingReviewCount,
                   List<TypeCount> types, long typeTotal, List<NodeItem> topDegree, long isolatedTotal,
                   List<NodeItem> isolated) {
        public Insight(long nodeCount, long edgeCount, long documentCount, long pendingReviewCount,
                       List<TypeCount> types, List<NodeItem> topDegree, long isolatedTotal,
                       List<NodeItem> isolated) {
            this(nodeCount, edgeCount, documentCount, pendingReviewCount, types, types.size(),
                    topDegree, isolatedTotal, isolated);
        }
    }

    Optional<Job> job(Scope scope, long jobId);
    Page<Candidate> candidates(Scope scope, long jobId, String kind, int page, int pageSize);
    Optional<Candidate> candidate(Scope scope, long jobId, String kind, long candidateId);
    Optional<Candidate> entityByTempKey(Scope scope, long jobId, String tempKey);
    Optional<Long> chunkDocumentId(long chunkId);
    /** Hash metadata only, after proving both document ownership and the host scope. */
    Optional<String> chunkHash(Scope scope, long documentId, long chunkId);
    Optional<UnitMeta> unitMeta(Scope scope, long documentId, long unitId);
    Optional<NodeMatch> nodeMatch(Scope scope, long nodeId);
    Optional<EdgeMatch> edgeMatch(Scope scope, long edgeId);
    Insight insight(Scope scope, int isolatedLimit, int topLimit);
}
