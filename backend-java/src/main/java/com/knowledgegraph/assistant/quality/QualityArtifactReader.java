package com.knowledgegraph.assistant.quality;

import java.util.List;
import java.util.Optional;

/** Host-owned boundary: only current task's explicit direct dependency artifacts may resolve.
 * Claims and source pointers must be parsed from the original host-held Artifact and its evidence,
 * never from model-supplied summaries or manually reconstructed references.
 */
public interface QualityArtifactReader {
    record SourcePointer(String sourceId, long documentId, long chunkId, String expectedHash) {}
    record Claim(String claimId, List<SourcePointer> sources) {
        public Claim { sources = List.copyOf(sources); }
    }
    record DirectArtifact(String artifactId, String producerTaskId, List<Claim> claims) {
        public DirectArtifact { claims = List.copyOf(claims); }
    }

    /** Implementations must check runId, consumerTaskId and direct dependency membership before returning data. */
    Optional<DirectArtifact> directArtifact(String runId, String consumerTaskId, String artifactId);
}
